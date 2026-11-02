"""Common v1 identity stays intact while preview performance can change."""
from copy import deepcopy
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from data.src.playback.__main__ import run
from data.src.playback import expressions as expr, preview
from data.src.playback.common_score import convert_common_score
from data.src.playback.score import compile_score
from data.src.playback.tests._helpers import sampler, document, common_small_score as small_score, COMMON_SCORE_FIXTURE as FIXTURE
from data.src.scores.score_data import convert as common_from_jgb


def from_melody(melody):
    return common_from_jgb(document(melody, beats='4'), beats_per_jeonggan=1,
        title='고정 검증', instrument='DANSO', source_id='authored-test', jangdan='검증',
        bpm_unit='QUARTER', review='inferred', jgb_sha256='b'*64)


class CommonPreviewTests(unittest.TestCase):
    def convert(self, source, **kwargs):
        return convert_common_score(source, bpm=60, instrument=source['score']['instrument'].lower().replace('_', '-'),
                                    input_sha256='a'*64, notation_policy=preview.MODE, **kwargs)

    def test_fermata_changes_performance_but_not_source_ticks(self):
        source = small_score()
        source['events'][1]['sigimsae'] = ['늘임표']
        before = deepcopy(source)
        score, mapping = convert_common_score(source, bpm=60, instrument='danso', input_sha256='a'*64,
                                              notation_policy='educational-preview/v1')
        self.assertEqual(source, before)
        self.assertEqual(sum(Fraction(n['duration']) for n in score['notes']), 5)
        timeline = mapping['performanceTimeline']
        self.assertEqual(timeline['durationMs'], 5000)
        self.assertEqual([(x['startMs'], x['endMs']) for x in timeline['segments']],
                         [(0, 1000), (1000, 3000), (3000, 4000), (4000, 5000)])
        self.assertEqual([(x['startTick'], x['endTick']) for x in timeline['segments']],
                         [(0, 1), (1, 2), (2, 3), (3, 4)])
        self.assertEqual(mapping['placement_policy'], 'event-origin-inferred')

    def test_all_registered_marks_are_audible_for_each_instrument(self):
        names = expr.UNRESOLVED | set(expr.EFFECT_MARKS) | set(expr.PROFILE_MARKS)
        names |= set(expr.LENGTH_MARKS) | set(preview.EXTRA_MARKS) | set(preview.ALIASES)
        for table in expr.SYMBOL_TABLES.values():
            names.update(table)
        names |= {'본래속도로', '점점느리게', '점점속하게', '조금느리게', '조금속하게'}
        for instrument in expr.SYMBOL_TABLES:
            for mark in names:
                with self.subTest(instrument=instrument, mark=mark):
                    source = small_score()
                    source['score']['instrument'] = instrument.upper().replace('-', '_')
                    for event, cell in zip(source['events'], source['columns'][0]['cells']):
                        if event['kind'] == 'NOTE':
                            event['octave'] = 1
                            cell['units'][0]['symbol'] = '청중'
                    source['events'][1]['sigimsae'] = [mark]
                    score, mapping = self.convert(source)
                    self.assertTrue(compile_score(score, instrument)['events'])
                    self.assertEqual(mapping['events'][1]['sigimsae'], [mark])
                    self.assertIn(mark, mapping['performanceTimeline']['segments'][1]['sigimsae'])

    def test_instrument_specific_sequences_and_repeat_keep_source_identity(self):
        for instrument, pitches in [('DANSO', [70, 68, 70, 68]), ('JEONGAK_DAEGEUM', [70, 68, 66, 68])]:
            source = small_score()
            source['score']['instrument'] = instrument
            source['events'][1]['sigimsae'] = ['니느라니']
            score, mapping = self.convert(source)
            self.assertEqual([n['pitch'] for n in score['notes'][1:5]], pitches)
            self.assertEqual([Fraction(n['duration']) for n in score['notes'][1:5]], [Fraction(1, 4)]*4)
            self.assertEqual(mapping['events'][1]['noteIdx'], 0)
            decorated = [loc for refs in mapping['expression_source_map']['notes'].values()
                         for loc in refs if loc['gak'] == 1 and loc['cell'] == 2]
            self.assertTrue(decorated)
            self.assertTrue(all(loc['common']['eventId'] == 'c0-j1-r0-u0' and
                                loc['common']['noteIdx'] == 0 for loc in decorated))
        source['events'][1]['sigimsae'] = ['반복표']
        _, mapping = self.convert(source)
        segments = mapping['performanceTimeline']['segments']
        self.assertEqual(len(segments), 8)
        self.assertEqual([s['startTick'] for s in segments], [0, 1, 2, 3]*2)
        self.assertEqual(segments[-1]['endMs'], 8000)

    def test_length_and_tempo_marks_keep_written_positions(self):
        source = small_score()
        source['events'][1]['sigimsae'] = ['덧길이표']
        source['events'][2]['sigimsae'] = ['반길이표']
        _, mapping = self.convert(source)
        segments = mapping['performanceTimeline']['segments']
        self.assertEqual([s['endMs'] for s in segments], [1000, 2500, 3000, 4000])
        source['events'][1]['sigimsae'] = ['조금느리게']
        source['events'][2]['sigimsae'] = ['본래속도로']
        _, mapping = self.convert(source)
        self.assertEqual([s['endMs'] for s in mapping['performanceTimeline']['segments']], [1000, 2250, 3250, 4250])

    def test_cli_binds_timeline_to_original_and_actual_audio(self):
        source = small_score()
        source['events'][1]['sigimsae'] = ['느니라']
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = root/'input.json'
            path.write_text(json.dumps(source), encoding='utf-8')
            assets = root/'assets.json'
            assets.write_text('{"danso":"unused.zip"}', encoding='utf-8')
            with patch('data.src.playback.__main__.CatSampler', return_value=sampler()):
                run(path, assets, root/'out', ['danso'], common_bpm=60,
                    notation_policy=preview.MODE, audio_format='wav')
            timeline = json.loads((root/'out/performance.common.json').read_text(encoding='utf-8'))
            self.assertEqual(timeline['sourceSha256'], hashlib.sha256(path.read_bytes()).hexdigest())
            self.assertEqual(timeline['audioSha256'], hashlib.sha256((root/'out/danso.wav').read_bytes()).hexdigest())
            self.assertEqual((root/'out/source.common.json').read_bytes(), path.read_bytes())

    def test_explicit_hold_effect_keeps_attack_and_fractional_ticks(self):
        for name, field in [('깊은요성', 'yoseong'), ('요성표', 'yoseong'), ('하성', 'pitch_curve')]:
            source = from_melody(f'청중 -{{{name}}} -')
            eid = source['events'][0]['id']
            config = {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a'*64,
                      'placements': [{'eventId': eid, 'cellId': 'c0-j0', 'row': 1, 'col': 0, 'symbols': [name]}]}
            with self.subTest(name=name):
                score, mapping = self.convert(source, expressions=config)
                self.assertEqual(len(score['notes']), 1)
                self.assertAlmostEqual(score['notes'][0][field]['delay_seconds'], 1/3)
                segments = mapping['performanceTimeline']['segments']
                self.assertEqual([s['startTick'] for s in segments], [0, 1/3, 2/3])
                self.assertEqual([s['sigimsae'] for s in segments], [[], [name], []])
                self.assertEqual(mapping['placement_policy'], 'explicit-unit-placement')

    def test_original_numeric_glyph_placement_is_not_a_symbol_typo(self):
        source = from_melody('청중{니레@50,0,0}|청중')
        score, mapping = self.convert(source)
        self.assertEqual([n['pitch'] for n in score['notes']], [82, 80, 80])
        self.assertEqual(mapping['performanceTimeline']['segments'][0]['sigimsae'], ['니레@50,0,0'])

    def test_companion_rejects_wrong_hash_omission_duplicate_and_unknown_field(self):
        source = small_score()
        source['events'][1]['sigimsae'] = ['느니라']
        entry = {'eventId': 'c0-j1-r0-u0', 'cellId': 'c0-j1', 'row': 0, 'col': 0, 'symbols': ['느니라']}
        config = {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a'*64, 'placements': [entry]}
        self.convert(source, expressions=config)
        for change in ({'sourceSha256': 'b'*64}, {'placements': []}, {'placements': [entry, entry]}, {'typo': 1}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.convert(source, expressions=dict(config, **change))

    def test_adjacent_units_cannot_merge_into_another_yul_name(self):
        source = from_melody('중 청황')
        source['columns'][0]['cells'][0]['units'][1].update(row=0, col=1, eventId='c0-j0-r0-u1')
        source['events'][1]['id'] = 'c0-j0-r0-u1'
        score, mapping = self.convert(source)
        self.assertEqual([n['pitch'] for n in score['notes']], [68, 75])
        self.assertEqual([s['eventId'] for s in mapping['performanceTimeline']['segments']],
                         ['c0-j0-r0-u0', 'c0-j0-r0-u1'])

    def test_placements_preserve_symbol_order_independent_of_entry_order(self):
        source = from_melody('청중{니레} -{농음표}')
        entries = [{'eventId': 'c0-j0-r0-u0', 'cellId': 'c0-j0', 'row': row, 'col': 0, 'symbols': [mark]}
                   for row, mark in enumerate(['니레', '농음표'])]
        config = {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a'*64, 'placements': list(reversed(entries))}
        self.convert(source, expressions=config)
        entries[0]['symbols'], entries[1]['symbols'] = entries[1]['symbols'], entries[0]['symbols']
        with self.assertRaisesRegex(ValueError, 'sigimsae'):
            self.convert(source, expressions=config)

    def test_tempo_cue_keeps_written_placement_and_records_cell_policy(self):
        source = from_melody('청중 청중{조금느리게} 청중')
        _, mapping = self.convert(source)
        segments = mapping['performanceTimeline']['segments']
        self.assertEqual([s['sigimsae'] for s in segments], [[], ['조금느리게'], []])
        self.assertEqual(segments[-1]['endMs'], 1250)
        shifted = next(item for item in mapping['placement_assumptions'] if item.get('symbol') == '조금느리게')
        self.assertEqual(shifted['written_position'], ['c0-j0', 1, 0])
        self.assertEqual(shifted['effective_position'], ['c0-j0', 0, 0])

    def test_preview_plain_arirang_matches_original_playback(self):
        source = json.loads(FIXTURE.read_text(encoding='utf-8'))
        old, _ = convert_common_score(source, bpm=96, instrument='danso', input_sha256='a'*64)
        new, mapping = convert_common_score(source, bpm=96, instrument='danso', input_sha256='a'*64,
                                            notation_policy=preview.MODE)
        old_events, new_events = compile_score(old, 'danso')['events'], compile_score(new, 'danso')['events']
        self.assertEqual(len(old_events), len(new_events))
        for a, b in zip(old_events, new_events):
            for key in ['pitch', 'start_tick', 'end_tick', 'velocity']:
                self.assertEqual(a[key], b[key], key)
        self.assertAlmostEqual(mapping['performanceTimeline']['durationMs'], 30000, delta=.1)


if __name__ == '__main__':
    unittest.main()
