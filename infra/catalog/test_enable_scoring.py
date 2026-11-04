import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('tool', Path(__file__).with_name('enable_scoring.py'))
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'data/src/scores'))
from score_data import convert_answer  # noqa: E402


def score(source, ids=('a', 'b', 'c')):
    return {'score': {'sourceId': source, 'scoreVersion': 1}, 'provenance': {'review': 'inferred', 'sourceId': source},
            'events': [{'id': i, 'kind': 'NOTE', 'breathAfter': False} for i in ids]}


class FakeDb:
    """운영 songs 4행을 흉내 낸다. UPDATE 묶음을 받으면 적용된 상태로 바꾼다."""

    def __init__(self, plan, rows):
        self.plan, self.rows, self.batches = plan, rows, []

    def __call__(self, sql):
        if sql.startswith('SELECT'):
            return '\n'.join(json.dumps(r, ensure_ascii=False) for r in self.rows)
        self.batches.append(sql)
        for row in self.rows:
            item = next(p for p in self.plan if p['songId'] == row['id'])
            if '_target' in item:
                row['score'], row['scoreVersion'] = item['_target'], item['targetScoreVersion']
            else:
                row['score'] = tool.patch(row['score'], item['breathEventIds'])
            row['isScorable'] = 1
        return ''


def fake_plan_and_rows():
    plan, rows = [], []
    for song_id, source in [(97, 'src-a'), (92, 'src-b')]:
        current = score(source)
        plan.append({'songId': song_id, 'source': source, 'scoreVersion': 1, 'answer': 'x', 'breathEventIds': ['b'],
                     'currentSha256': tool.canonical_sha(current), 'targetSha256': tool.canonical_sha(tool.patch(current, ['b']))})
        rows.append({'id': song_id, 'source': source, 'scoreVersion': 1, 'isScorable': 0, 'status': 'PUBLISHED',
                     'deleted': 0, 'score': current})
    return plan, rows


class PlanTests(unittest.TestCase):
    def test_load_plan_from_json(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'plan.json'
            expected, _ = fake_plan_and_rows()
            path.write_text(json.dumps(expected), encoding='utf-8')
            self.assertEqual(tool.load_plan(path), expected)

    def test_plan_rejects_invalid_or_duplicate_ids(self):
        plan, _ = fake_plan_and_rows()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'plan.json'
            for invalid in ([], [dict(plan[0], songId='1 OR 1=1')], [plan[0], plan[0]],
                            [dict(plan[0], scoreVersion='1')]):
                path.write_text(json.dumps(invalid), encoding='utf-8')
                with self.assertRaises(ValueError):
                    tool.load_plan(path)

    def test_plan_breaths_are_the_reviewed_answer_breaths(self):
        # 교과서 우선, 없으면 표준악보집. 운영 곡과 정답의 짝(이벤트 일치는 2026-11-03 운영 읽기로 확인)
        self.assertEqual({p['songId']: p['answer'] for p in tool.load_plan(ROOT / 'infra/catalog/scoring-plans/sogeum.json')},
                         {96: 'school-textbook-sogeum-p15', 93: 'school-textbook-sogeum-p16',
                          95: 'ngc-standard-2022-p193', 91: 'ngc-standard-2022-p196',
                          89: 'ngc-standard-2022-p188', 90: 'ngc-standard-2022-p191',
                          94: 'ngc-standard-2022-p199', 102: 'school-textbook-sogeum-p14-exercise-1',
                          103: 'school-textbook-sogeum-p14-exercise-2', 105: 'ngc-standard-2022-p202-part-2',
                          106: 'ngc-standard-2022-p187', 98: 'ngc-standard-2022-p189',
                          99: 'ngc-standard-2022-p198', 100: 'ngc-standard-2022-p197',
                          101: 'ngc-standard-2022-p195', 108: 'ngc-standard-2022-p200',
                          110: 'ngc-standard-2022-p201', 111: 'ngc-standard-2022-p190'})
        for item in tool.load_plan(ROOT / 'infra/catalog/scoring-plans/sogeum.json'):
            answer = convert_answer(item['answer'])
            self.assertEqual(answer['provenance']['review'], 'reviewed', item['answer'])
            self.assertEqual(item['breathEventIds'], [e['id'] for e in answer['events'] if e['breathAfter']], item['answer'])

    def test_danso_plan_breaths_are_the_reviewed_answer_breaths(self):
        # S15P31E102-404. 339 적재 단소 곡과 정답의 짝(이벤트 일치는 2026-11-04 공개 곡 상세로 확인).
        # 30은 단소2, 31은 단소1이다. 북천이 단소1(25)은 운영 음이 원본과 달라 뺐다(451).
        plan = tool.load_plan(ROOT / 'infra/catalog/scoring-plans/danso.json')
        self.assertEqual({p['songId']: p['answer'] for p in plan}, {
            5: 'ngc-silgi-2016-18705', 6: 'ngc-standard-2022-p165', 7: 'ngc-silgi-2016-18796',
            8: 'ngc-silgi-2016-18875', 9: 'ngc-standard-2022-p169', 10: 'school-textbook-danso-p14-exercise-1',
            11: 'school-textbook-danso-p14-exercise-2', 12: 'ngc-standard-2022-p163', 13: 'ngc-standard-2022-p164',
            14: 'ngc-standard-2022-p170', 18: 'ngc-standard-2022-p162', 21: 'ngc-silgi-2016-18730',
            22: 'ngc-silgi-2016-18747', 23: 'ngc-standard-2022-p181', 24: 'ngc-standard-2022-p175',
            26: 'ngc-standard-2022-p182-part-2', 27: 'ngc-standard-2022-p161', 30: 'ngc-standard-2022-p183-part-2',
            31: 'ngc-standard-2022-p183-part-1', 33: 'ngc-standard-2022-p178', 34: 'ngc-silgi-2016-18868',
            35: 'ngc-standard-2022-p180', 38: 'ngc-standard-2022-p166', 39: 'ngc-standard-2022-p168',
            40: 'ngc-silgi-2016-19006', 41: 'ngc-standard-2022-p176', 42: 'ngc-silgi-2016-18947',
            46: 'ngc-standard-2022-p179', 47: 'ngc-standard-2022-p167', 48: 'ngc-silgi-2016-18924'})
        for item in plan:
            answer = convert_answer(item['answer'])
            self.assertEqual(answer['provenance']['review'], 'reviewed', item['answer'])
            self.assertEqual(item['breathEventIds'], [e['id'] for e in answer['events'] if e['breathAfter']], item['answer'])


class PatchTests(unittest.TestCase):
    def test_patch_sets_only_listed_breaths_and_review(self):
        before = score('s')
        after = tool.patch(before, ['b'])
        self.assertEqual([e['breathAfter'] for e in after['events']], [False, True, False])
        self.assertEqual(after['provenance']['review'], 'reviewed')
        self.assertEqual(before['provenance']['review'], 'inferred')  # 원본은 바꾸지 않는다

    def test_patch_refuses_unknown_event(self):
        with self.assertRaises(ValueError):
            tool.patch(score('s'), ['zz'])

    def test_patch_replaces_existing_breath(self):
        marked = score('s')
        marked['events'][0]['breathAfter'] = True
        after = tool.patch(marked, ['b'])
        self.assertEqual([e['breathAfter'] for e in after['events']], [False, True, False])
        self.assertTrue(marked['events'][0]['breathAfter'])

    def test_canonical_sha_ignores_key_order(self):
        a = {'x': 1, 'y': [1, 2]}
        self.assertEqual(tool.canonical_sha(a), tool.canonical_sha(json.loads('{"y":[1,2],"x":1}')))
        self.assertEqual(tool.canonical_sha(a), hashlib.sha256(b'{"x":1,"y":[1,2]}').hexdigest())


def production_and_answer():
    production = {'schemaVersion': 1, 'score': {'title': '곡', 'instrument': 'DANSO', 'part': 0, 'sourceId': 'mr-1', 'scoreVersion': 1},
                  'music': {'bpmUnit': 'QUARTER', 'hwangHz': 311.1269837220809, 'jangdan': '굿거리', 'ticksPerBeat': 8, 'ticksPerJeonggan': 12},
                  'provenance': {'review': 'inferred', 'sourceId': 'mr-1', 'converter': 'jgb-playback-import/v1', 'jgbSha256': '0' * 64},
                  'events': [{'id': 'e0', 'kind': 'NOTE', 'yul': '태', 'startTick': 0, 'durationTick': 12, 'breathAfter': False}],
                  'columns': [{'index': 0, 'cells': [{'id': 'c0-j0', 'units': [{'symbol': '청태'}]}]}]}
    answer = {'schemaVersion': 1, 'score': {'title': '곡', 'instrument': 'DANSO', 'part': 0, 'sourceId': 'ngc-x', 'scoreVersion': 1},
              'music': {'bpmUnit': 'QUARTER', 'hwangHz': 311.13, 'jangdan': '굿거리', 'ticksPerBeat': 4, 'ticksPerJeonggan': 6},
              'provenance': {'review': 'reviewed', 'sourceId': 'ngc-x', 'converter': 'score_data@1', 'jgbSha256': '1' * 64},
              'events': [{'id': 'e0', 'kind': 'NOTE', 'yul': '임', 'startTick': 0, 'durationTick': 6, 'breathAfter': True}],
              'columns': [{'index': 0, 'cells': [{'id': 'c0-j0', 'units': [{'symbol': '청임'}]}]}]}
    return production, answer


class TargetScoreTests(unittest.TestCase):
    def test_target_keeps_service_identity_and_takes_answer_notes(self):
        production, answer = production_and_answer()
        before = copy.deepcopy(production)
        target = tool.target_score(production, answer)
        self.assertEqual((target['events'], target['columns']), (answer['events'], answer['columns']))
        self.assertEqual(target['score'], dict(production['score'], scoreVersion=2))
        self.assertEqual(target['music'], dict(production['music'], ticksPerBeat=4, ticksPerJeonggan=6))
        self.assertEqual(target['provenance'], {'review': 'reviewed', 'sourceId': 'mr-1', 'converter': 'score_data@1',
                                                'jgbSha256': '1' * 64})
        self.assertEqual(production, before)  # 입력은 바꾸지 않는다

    def test_target_refuses_a_different_beat_meaning(self):
        for change in ({'bpmUnit': 'DOTTED_QUARTER'}, {'ticksPerJeonggan': 4}):
            production, answer = production_and_answer()
            answer['music'].update(change)
            with self.assertRaises(ValueError):
                tool.target_score(production, answer)


def replacement_plan(directory):
    """숨표만 바꾸는 97과 악보를 통째로 바꾸는 25를 담은 계획 파일. 목표 악보는 계획 옆 폴더에 둔다."""
    plan, rows = fake_plan_and_rows()
    plan, rows = plan[:1], rows[:1]
    current = score('mr-25', ('a', 'b'))
    target = dict(score('mr-25', ('a', 'x', 'y')), score={'sourceId': 'mr-25', 'scoreVersion': 2})
    target['provenance']['review'] = 'reviewed'
    target['events'][1]['breathAfter'] = True
    (directory / 'danso-scores').mkdir()
    (directory / 'danso-scores/25.json').write_text(json.dumps(target, ensure_ascii=False), encoding='utf-8')
    plan.append({'songId': 25, 'source': 'mr-25', 'scoreVersion': 1, 'answer': 'x', 'breathEventIds': ['x'],
                 'targetScore': 'danso-scores/25.json', 'targetScoreVersion': 2,
                 'currentSha256': tool.canonical_sha(current), 'targetSha256': tool.canonical_sha(target)})
    rows.append({'id': 25, 'source': 'mr-25', 'scoreVersion': 1, 'isScorable': 0, 'status': 'PUBLISHED',
                 'deleted': 0, 'score': current})
    (directory / 'plan.json').write_text(json.dumps(plan), encoding='utf-8')
    return directory / 'plan.json', rows, target


class ReplacementTests(unittest.TestCase):
    def test_plan_loads_the_target_score_beside_it(self):
        with tempfile.TemporaryDirectory() as directory:
            path, _, target = replacement_plan(Path(directory))
            plan = tool.load_plan(path)
            self.assertEqual(plan[1]['_target'], target)
            self.assertNotIn('_target', plan[0])

    def test_plan_refuses_a_bad_target(self):
        cases = {
            'changed file': lambda item, d: (d / 'danso-scores/25.json').write_text('{}', encoding='utf-8'),
            'version jump': lambda item, d: item.update(targetScoreVersion=3),
            'outside folder': lambda item, d: item.update(targetScore='../25.json'),
            'breath mismatch': lambda item, d: item.update(breathEventIds=[]),
        }
        for name, breaker in cases.items():
            with tempfile.TemporaryDirectory() as directory, self.subTest(name):
                path, _, _ = replacement_plan(Path(directory))
                raw = json.loads(path.read_text(encoding='utf-8'))
                breaker(raw[1], Path(directory))
                path.write_text(json.dumps(raw), encoding='utf-8')
                with self.assertRaises(ValueError):
                    tool.load_plan(path)

    def test_replacement_state_moves_from_old_version_to_new(self):
        with tempfile.TemporaryDirectory() as directory:
            path, rows, target = replacement_plan(Path(directory))
            item, row = tool.load_plan(path)[1], rows[1]
            self.assertEqual(tool.state(item, row), 'pending')
            self.assertEqual(tool.state(item, dict(row, score=target, scoreVersion=2, isScorable=1)), 'done')
            for broken in (dict(row, scoreVersion=2), dict(row, score=target, isScorable=1),
                           dict(row, score=target, scoreVersion=3, isScorable=1)):
                with self.assertRaises(ValueError):
                    tool.state(item, broken)

    def test_apply_replaces_score_bumps_version_and_counts_notes(self):
        with tempfile.TemporaryDirectory() as directory:
            path, rows, target = replacement_plan(Path(directory))
            plan = tool.load_plan(path)
            db = FakeDb(plan, rows)
            report = tool.run(plan, Path(directory) / 'r1.json', apply=True, runner=db)
            self.assertEqual(report['updated'], [97, 25])
            (sql,) = db.batches
            (replace,) = [s for s in sql.split('\n') if 'WHERE id=25' in s]
            self.assertIn('score_version=2, note_count=3', replace)
            self.assertIn('AND score_version=1 AND', replace)
            (breath,) = [s for s in sql.split('\n') if 'WHERE id=97' in s]
            self.assertNotIn('score_version=', breath.split('WHERE')[0])
            self.assertEqual((rows[1]['scoreVersion'], rows[1]['score']), (2, target))
            again = tool.run(plan, Path(directory) / 'r2.json', apply=True, runner=db)
            self.assertEqual((again['updated'], len(db.batches)), ([], 1))


class StateTests(unittest.TestCase):
    def test_state_is_pending_done_or_refused(self):
        plan, rows = fake_plan_and_rows()
        item, row = plan[0], rows[0]
        self.assertEqual(tool.state(item, row), 'pending')
        done = dict(row, isScorable=1, score=tool.patch(row['score'], ['b']))
        self.assertEqual(tool.state(item, done), 'done')
        for broken in (dict(row, source='other'), dict(row, scoreVersion=2), dict(row, deleted=1),
                       dict(row, score=score('src-a', ('a', 'b', 'd'))), dict(row, isScorable=1)):
            with self.assertRaises(ValueError):
                tool.state(item, broken)


class ApplyTests(unittest.TestCase):
    def run_tool(self, db, plan, apply):
        receipt = Path(tempfile.mkdtemp()) / 'receipt.json'
        report = tool.run(plan, receipt, apply=apply, runner=db)
        return report, json.loads(receipt.read_text(encoding='utf-8'))

    def test_dry_run_reads_only(self):
        plan, rows = fake_plan_and_rows()
        db = FakeDb(plan, rows)
        report, saved = self.run_tool(db, plan, apply=False)
        self.assertEqual((report['status'], report['pending']), ('validated-dry-run', [97, 92]))
        self.assertEqual(saved['status'], 'validated-dry-run')
        self.assertEqual(db.batches, [])

    def test_apply_writes_one_guarded_transaction_and_reads_back(self):
        plan, rows = fake_plan_and_rows()
        db = FakeDb(plan, rows)
        report, _ = self.run_tool(db, plan, apply=True)
        self.assertEqual((report['status'], report['updated']), ('applied', [97, 92]))
        (sql,) = db.batches
        self.assertTrue(sql.startswith('CREATE TEMPORARY TABLE') and sql.rstrip().endswith('COMMIT;'))
        self.assertEqual(sql.count('UPDATE songs SET'), 2)
        self.assertEqual(sql.count('ROW_COUNT()'), 2)
        self.assertIn('is_scorable=FALSE', sql)
        self.assertNotIn('INSERT INTO songs', sql)
        self.assertNotIn('DELETE', sql)
        report, _ = self.run_tool(db, plan, apply=True)  # 다시 실행해도 쓰지 않는다
        self.assertEqual((report['status'], report['updated']), ('applied', []))
        self.assertEqual(len(db.batches), 1)

    def test_existing_receipt_is_not_overwritten(self):
        plan, rows = fake_plan_and_rows()
        db = FakeDb(plan, rows)
        receipt = Path(tempfile.mkdtemp()) / 'receipt.json'
        receipt.write_text('earlier', encoding='utf-8')
        with self.assertRaises(FileExistsError):
            tool.run(plan, receipt, apply=True, runner=db)
        self.assertEqual((receipt.read_text(encoding='utf-8'), db.batches), ('earlier', []))

    def test_failed_write_leaves_outcome_unknown_in_receipt(self):
        plan, rows = fake_plan_and_rows()
        db = FakeDb(plan, rows)

        def failing(sql):
            if sql.startswith('SELECT'):
                return db(sql)
            raise RuntimeError('timeout')
        receipt = Path(tempfile.mkdtemp()) / 'receipt.json'
        with self.assertRaises(RuntimeError):
            tool.run(plan, receipt, apply=True, runner=failing)
        saved = json.loads(receipt.read_text(encoding='utf-8'))
        self.assertEqual((saved['status'], saved['errorType']), ('apply-outcome-unknown', 'RuntimeError'))

    def test_changed_row_stops_before_any_write(self):
        plan, rows = fake_plan_and_rows()
        rows[1]['score'] = score('src-b', ('a', 'b', 'z'))
        db = FakeDb(plan, rows)
        with self.assertRaises(ValueError):
            self.run_tool(db, plan, apply=True)
        self.assertEqual(db.batches, [])


if __name__ == '__main__':
    unittest.main()
