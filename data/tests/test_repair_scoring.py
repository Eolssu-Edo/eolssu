import copy
from fractions import Fraction
from pathlib import Path
import unittest

from data.src.playback.common_score import validate_common
from data.src.scores.repair_scoring import merge_independent_ornaments, replace_with_answer
from infra.catalog.enable_scoring import load_plan


ROOT = Path(__file__).resolve().parents[2]


def score():
    def note(eid, cell, yul, octave, start, duration, marks=(), breath=False):
        return dict(id=eid, cellId=cell, kind='NOTE', yul=yul, octave=octave,
                    startTick=start, durationTick=duration, sigimsae=list(marks), breathAfter=breath)
    def unit(row, col, symbol, eid):
        return dict(row=row, col=col, symbol=symbol, eventId=eid)
    return {
        'schemaVersion': 1,
        'score': dict(title='고정 회귀곡', instrument='DANSO', part=0, sourceId='test-source', scoreVersion=1),
        'music': dict(hwangHz=311.13, jangdan='', bpmUnit='QUARTER', ticksPerBeat=4, ticksPerJeonggan=4),
        'provenance': dict(sourceId='test-source', jgbSha256='a' * 64, review='inferred', converter='test'),
        'columns': [dict(index=0, cells=[
            dict(id='c0-j0', index=0, lyric='앞', units=[unit(0, 0, '임', 'c0-j0-r0-u0')]),
            dict(id='c0-j1', index=1, lyric='뒤', units=[unit(0, 0, '-', 'c0-j0-r0-u0'),
                 unit(1, 0, '청임', 'c0-j1-r1-u0'), unit(1, 1, '청중', 'c0-j1-r1-u1')]),
            dict(id='c0-j2', index=2, units=[unit(0, 0, '무', 'c0-j2-r0-u0')]),
        ])],
        'events': [note('c0-j0-r0-u0', 'c0-j0', '임', 0, 0, 6, ['농음표']),
                   note('c0-j1-r1-u0', 'c0-j1', '임', 1, 6, 1),
                   note('c0-j1-r1-u1', 'c0-j1', '중', 1, 7, 1, breath=True),
                   note('c0-j2-r0-u0', 'c0-j2', '무', 0, 8, 4)],
    }


def slots():
    return [dict(expandedIndependent=True, source=dict(symbols=['니나']), generated=[
        dict(eventId='c0-j1-r1-u0', cellId='c0-j1', row=1, col=0),
        dict(eventId='c0-j1-r1-u1', cellId='c0-j1', row=1, col=1)])]


class OrnamentRepairTests(unittest.TestCase):
    def test_scoring_uses_one_ornament_anchor_and_preserves_display_timing(self):
        before = score()
        original = copy.deepcopy(before)
        after, mapping = merge_independent_ornaments(before, slots())
        self.assertEqual(len(after['events']), 2)
        anchor = after['events'][0]
        self.assertEqual((anchor['startTick'], anchor['durationTick']), (0, 8))
        self.assertEqual(anchor['sigimsae'], ['농음표', '니나'])
        self.assertTrue(anchor['breathAfter'])
        self.assertEqual(after['events'][1], original['events'][3])
        self.assertEqual(mapping['c0-j1-r1-u1'], anchor['id'])
        self.assertEqual(after['columns'][0]['cells'][1]['units'][1],
                         dict(row=1, col=0, symbol='-', eventId=anchor['id']))
        self.assertEqual(after['columns'][0]['cells'][1]['lyric'], '뒤')
        self.assertEqual(after['score']['scoreVersion'], 2)
        self.assertEqual(after['provenance']['review'], 'inferred')
        self.assertEqual(before, original)
        self.assertEqual(validate_common(after, allow_sigimsae=True)[1], 12)

    def test_missing_generated_event_is_rejected(self):
        mapping = slots()
        mapping[0]['generated'][0]['eventId'] = 'c9-j9-r0-u0'
        with self.assertRaisesRegex(ValueError, 'missing'):
            merge_independent_ornaments(score(), mapping)

    def test_first_written_note_cannot_be_treated_as_an_ornament(self):
        mapping = slots()
        mapping[0]['generated'] = [dict(eventId='c0-j0-r0-u0')]
        with self.assertRaisesRegex(ValueError, 'preceding NOTE'):
            merge_independent_ornaments(score(), mapping)

    def test_noncontiguous_source_mapping_is_rejected(self):
        mapping = slots()
        mapping[0]['generated'] = [dict(eventId='c0-j1-r1-u0'), dict(eventId='c0-j2-r0-u0')]
        with self.assertRaisesRegex(ValueError, 'not contiguous'):
            merge_independent_ornaments(score(), mapping)

    def test_rest_anchor_and_breath_inside_phrase_are_not_silently_changed(self):
        before = score()
        before['events'][0].update(kind='REST', yul=None, octave=None, sigimsae=[])
        before['columns'][0]['cells'][0]['units'][0]['symbol'] = '쉼'
        with self.assertRaisesRegex(ValueError, 'preceding NOTE'):
            merge_independent_ornaments(before, slots())
        before = score()
        before['events'][0]['breathAfter'] = True
        with self.assertRaisesRegex(ValueError, 'unexpected breath'):
            merge_independent_ornaments(before, slots())

    def test_overlapping_slots_are_not_applied_twice(self):
        mapping = slots()
        with self.assertRaisesRegex(ValueError, 'overlapping'):
            merge_independent_ornaments(score(), mapping + copy.deepcopy(mapping))

    def test_repeat_application_cannot_advance_an_already_repaired_score(self):
        repaired, _ = merge_independent_ornaments(score(), slots())
        with self.assertRaisesRegex(ValueError, 'missing'):
            merge_independent_ornaments(repaired, slots())

    def test_unreviewed_answer_is_rejected_and_lyrics_are_preserved(self):
        before = score()
        answer = copy.deepcopy(before)
        with self.assertRaisesRegex(ValueError, 'not been reviewed'):
            replace_with_answer(before, answer)
        answer['provenance']['review'] = 'reviewed'
        answer['columns'][0]['cells'][0].pop('lyric')
        after = replace_with_answer(before, answer)
        self.assertEqual(after['columns'][0]['cells'][0]['lyric'], '앞')
        self.assertEqual(after['score']['scoreVersion'], 2)


class ReplacementCatalogTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plan = load_plan(ROOT / 'infra/catalog/scoring-plans/repair-699.json')
        cls.scores = {item['songId']: item['_target'] for item in cls.plan}

    def test_exact_nine_song_scope_and_valid_second_versions(self):
        self.assertEqual(set(self.scores), {19, 32, 37, 45, 50, 63, 65, 104, 109})
        for sid, target in self.scores.items():
            with self.subTest(song=sid):
                validate_common(target, allow_sigimsae=True)
                self.assertEqual(target['score']['scoreVersion'], 2)

    def test_original_bukcheon_missing_hwang_and_lengths_are_restored(self):
        target = self.scores[104]
        events = {event['id']: event for event in target['events']}
        tpb = target['music']['ticksPerBeat']
        expected = {
            'c4-j0-r0-u0': ('고', Fraction(24), Fraction(3, 2)),
            'c4-j1-r0-u0': ('남', Fraction(51, 2), Fraction(3, 2)),
            'c5-j0-r0-u0': ('황', Fraction(30), Fraction(3, 4)),
            'c5-j0-r1-u0': ('고', Fraction(123, 4), Fraction(3, 2)),
        }
        for eid, value in expected.items():
            event = events[eid]
            self.assertEqual((event['yul'], Fraction(event['startTick'], tpb), Fraction(event['durationTick'], tpb)), value)
            self.assertEqual(event['octave'], 1)
        self.assertEqual(sum(e['kind'] == 'NOTE' for e in target['events']), 74)

    def test_ornament_collapsing_keeps_terminal_breaths(self):
        for sid, anchor in ((63, 'c3-j0-r0-u0'), (65, 'c5-j2-r0-u0')):
            with self.subTest(song=sid):
                event = next(e for e in self.scores[sid]['events'] if e['id'] == anchor)
                self.assertTrue(event['breathAfter'])
                self.assertTrue(event['sigimsae'])
                self.assertEqual(self.scores[sid]['provenance']['review'], 'inferred')

    def test_chwita_uses_reviewed_ornament_notes_and_keeps_its_jangdan_label(self):
        target = self.scores[109]
        self.assertEqual(len(target['events']), 49)
        self.assertEqual(sum(e['kind'] == 'NOTE' for e in target['events']), 47)
        self.assertEqual(target['music']['jangdan'], '취타')
        note = next(e for e in target['events'] if e['cellId'] == 'c1-j10')
        self.assertEqual((note['yul'], note['octave'], note['sigimsae']), ('남', 1, ['느니르']))


if __name__ == '__main__':
    unittest.main()
