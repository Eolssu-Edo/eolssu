import copy
import json
from pathlib import Path
import tempfile
import unittest

import publish_sigimsae_placements as tool
from enable_scoring import canonical_sha

SCORE = {
    'schemaVersion': 1, 'score': {'scoreVersion': 3},
    'columns': [{'index': 0, 'cells': [
        {'id': 'c0-j0', 'index': 0, 'units': [{'row': 0, 'col': 0, 'symbol': '임', 'eventId': 'c0-j0-r0-u0'}]},
        {'id': 'c0-j1', 'index': 1, 'units': [{'row': 0, 'col': 0, 'symbol': '-', 'eventId': 'c0-j0-r0-u0'}]},
        {'id': 'c0-j2', 'index': 2, 'units': [{'row': 0, 'col': 0, 'symbol': '중', 'eventId': 'c0-j2-r0-u0'}]},
    ]}],
    'events': [
        {'id': 'c0-j0-r0-u0', 'sigimsae': ['니레', '농음']},
        {'id': 'c0-j2-r0-u0', 'sigimsae': []},
    ],
}
HELD = [{'cellId': 'c0-j0', 'row': 0, 'col': 0, 'symbols': ['니레']},
        {'cellId': 'c0-j1', 'row': 0, 'col': 0, 'symbols': ['농음']}]


def performance(slots, version=3, source='a' * 64):
    segments = [{'cellId': c, 'row': r, 'col': u, 'sigimsae': s} for c, r, u, s in slots]
    return {'scoreVersion': version, 'sourceSha256': source, 'segments': segments + segments}


def api(score=SCORE, perf=None, audio=True):
    perf = perf or performance([('c0-j0', 0, 0, ['니레']), ('c0-j1', 0, 0, ['농음']), ('c0-j2', 0, 0, [])])
    responses = {
        '/api/songs': {'items': [{'songId': '7', 'title': '산조'}]},
        '/api/songs/7/score': {'scoreVersion': 3, 'score': score},
        '/api/songs/7/model-audio': {'scoreVersion': 3, 'performanceUrl': '/api/songs/7/model-audio/performance',
                                     'sourceCommonSha256': 'a' * 64},
    }

    def fetch(url):
        path = url.removeprefix('https://example.invalid')
        if path == '/api/songs/7/model-audio/performance':
            return perf
        if path == '/api/songs/7/model-audio' and not audio:
            raise OSError('404')
        return {'isSuccess': True, 'result': responses[path]}
    return fetch


class FakeDb:
    def __init__(self, placements=None, score=SCORE):
        self.row = {'id': 7, 'scoreVersion': 3, 'status': 'PUBLISHED', 'deleted': 0, 'score': score,
                    'placements': placements, 'scoreTextHash': 'f' * 64}
        self.writes = []

    def __call__(self, sql):
        if sql.startswith('SELECT JSON_OBJECT'):
            return json.dumps(self.row, ensure_ascii=False)
        self.writes.append(sql)
        value = sql.split("CAST('", 1)[1].split("' AS JSON)", 1)[0]
        self.row['placements'] = json.loads(value)
        return ''


class PrepareTests(unittest.TestCase):
    def test_plans_only_songs_whose_performance_moves_a_symbol_to_a_held_slot(self):
        plan, skipped = tool.prepare('https://example.invalid', api())
        self.assertEqual({}, skipped)
        self.assertEqual([7], [item['songId'] for item in plan])
        self.assertEqual(HELD, plan[0]['placements'])
        self.assertEqual(canonical_sha(SCORE), plan[0]['scoreSha256'])

    def test_first_slot_layout_is_not_planned(self):
        same = performance([('c0-j0', 0, 0, ['니레', '농음']), ('c0-j1', 0, 0, []), ('c0-j2', 0, 0, [])])
        self.assertEqual([], tool.prepare('https://example.invalid', api(perf=same))[0])

    def test_songs_without_matching_performance_are_skipped(self):
        self.assertEqual({7: 'no-model-audio'}, tool.prepare('https://example.invalid', api(audio=False))[1])
        stale = performance([('c0-j0', 0, 0, ['니레'])], version=2)
        self.assertEqual({7: 'performance-version-mismatch'}, tool.prepare('https://example.invalid', api(perf=stale))[1])

    def test_inconsistent_performance_stops_prepare(self):
        moved = performance([('c0-j0', 0, 0, ['농음']), ('c0-j1', 0, 0, ['니레'])])
        with self.assertRaises(ValueError):
            tool.prepare('https://example.invalid', api(perf=moved))
        changed = performance([('c0-j0', 0, 0, ['니레']), ('c0-j1', 0, 0, ['농음'])])
        changed['segments'][-1] = dict(changed['segments'][-1], sigimsae=[])
        with self.assertRaises(ValueError):
            tool.prepare('https://example.invalid', api(perf=changed))


class ValidateTests(unittest.TestCase):
    def test_rejects_unknown_duplicate_blank_or_incomplete_slots(self):
        for bad in (
            HELD[:1],
            HELD + [{'cellId': 'c9-j0', 'row': 0, 'col': 0, 'symbols': ['x']}],
            HELD + [HELD[1]],
            [HELD[0], dict(HELD[1], symbols=[' '])],
            [HELD[0], dict(HELD[1], symbols=[])],
            [HELD[0], dict(HELD[1], extra=True)],
            list(reversed([dict(HELD[0], symbols=['농음']), dict(HELD[1], symbols=['니레'])])),
        ):
            with self.assertRaises(ValueError):
                tool.validate(SCORE, bad)
        self.assertEqual(HELD, tool.validate(SCORE, HELD))


class ApplyTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.plan = [{'songId': 7, 'scoreVersion': 3, 'scoreSha256': canonical_sha(SCORE), 'placements': HELD}]

    def receipt(self, name):
        return Path(self.folder.name) / name

    def test_dry_run_reads_only_and_apply_writes_once_then_reruns_as_noop(self):
        db = FakeDb()
        report = tool.run(self.plan, self.receipt('check.json'), runner=db)
        self.assertEqual(('validated-dry-run', [7]), (report['status'], report['pending']))
        self.assertEqual([], db.writes)
        report = tool.run(self.plan, self.receipt('apply.json'), apply=True, runner=db)
        self.assertEqual(('applied', [7], [7]), (report['status'], report['updated'], report['done']))
        self.assertEqual(1, len(db.writes))
        sql = db.writes[0]
        for guard in ('START TRANSACTION;', 'FOR UPDATE', 'sigimsae_placements_json IS NULL', 'score_version=3',
                      "SHA2(CAST(score_json AS CHAR),256)='" + 'f' * 64 + "'", 'ROW_COUNT()', 'COMMIT;'):
            self.assertIn(guard, sql)
        self.assertNotIn('score_json=', sql.replace('sigimsae_placements_json=', ''))
        again = tool.run(self.plan, self.receipt('again.json'), apply=True, runner=db)
        self.assertEqual(([], [7]), (again['updated'], again['done']))
        self.assertEqual(1, len(db.writes))

    def test_changed_score_or_other_placements_stop_before_writing(self):
        changed = copy.deepcopy(SCORE)
        changed['events'][0]['sigimsae'] = ['니레']
        for db in (FakeDb(score=changed), FakeDb(placements=[dict(HELD[0], symbols=['니레', '농음'])])):
            with self.assertRaises(ValueError):
                tool.run(self.plan, self.receipt(f'stop-{id(db)}.json'), apply=True, runner=db)
            self.assertEqual([], db.writes)
        receipt = json.loads(self.receipt(f'stop-{id(db)}.json').read_text(encoding='utf-8'))
        self.assertEqual('ValueError', receipt['errorType'])

    def test_receipt_is_never_overwritten(self):
        path = self.receipt('used.json')
        path.write_text('{}', encoding='utf-8')
        with self.assertRaises(FileExistsError):
            tool.run(self.plan, path, runner=FakeDb())

    def test_plan_requires_ids_hashes_and_placements(self):
        for bad in ([], [dict(self.plan[0], songId=0)], [dict(self.plan[0], scoreSha256='x')],
                    [dict(self.plan[0], placements=[])], self.plan + self.plan):
            path = self.receipt(f'plan-{len(str(bad))}-{id(bad)}.json')
            path.write_text(json.dumps(bad), encoding='utf-8')
            with self.assertRaises(ValueError):
                tool.load_plan(path)


if __name__ == '__main__':
    unittest.main()
