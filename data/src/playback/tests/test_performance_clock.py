"""Optional intra-slot clock metadata for local accompaniment and seeking."""
import math
import unittest

from data.src.playback.tests import test_authoring_tempo as fixtures
from data.src.playback.tests.test_common_preview import from_melody
from data.src.playback.common_score import convert_common_score
from data.src.playback.score import PPQN
from data.tests.test_playback_import import bundle, source as jgb_source
from data.src.playback.tempo_repeat import PerformanceClock


class PerformanceClockMetadataTests(unittest.TestCase):
    def test_ramp_segments_expose_endpoint_tempo_ratio_on_each_repeat(self):
        helper = fixtures.AuthoringTempoTests()
        request = helper.request()
        expressions = {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a' * 64,
                       'placements': [], 'performancePlan': {'schema': 'danso-performance/v1',
                       'input_sha256': 'a' * 64, 'tempos': [{'gak': 1, 'cell': 1, 'bpm': 120,
                           'end_gak': 2, 'end_cell': 1}], 'repeats': [{'start_gak': 1, 'end_gak': 1, 'times': 2}]}}
        timeline = helper.mapping(request, expressions)['performanceTimeline']
        self.assertEqual([segment['tempoRatio'] for segment in timeline['segments']], [1.25, 1.2, 7 / 6, 8 / 7] * 2)

    def test_constant_tempo_keeps_existing_sidecar_shape(self):
        helper = fixtures.AuthoringTempoTests()
        timeline = helper.mapping(helper.request())['performanceTimeline']
        self.assertTrue(all('tempoRatio' not in segment for segment in timeline['segments']))

    def test_imported_balanced_lengths_use_weighted_clock_bounds_for_constant_and_ramp(self):
        for target in (None, 120):
            with self.subTest(target=target):
                result = bundle(jgb_source('청황{덧길이표} 청태{반길이표}'))
                config = result['expressions']
                config['performancePlan'] = {'schema': 'danso-performance/v1', 'input_sha256': config['sourceSha256'],
                    'tempos': [] if target is None else [{'gak': 1, 'cell': 1, 'bpm': target, 'end_gak': 2, 'end_cell': 1}]}
                _, mapping = convert_common_score(result['common'], bpm=60, instrument='danso',
                    input_sha256=config['sourceSha256'], notation_policy='educational-preview/v1', expressions=config)
                first, second = mapping['performanceTimeline']['segments']
                self.assertEqual((first['startTick'], first['endTick'], second['endTick']), (0, 1, 2))
                self.assertEqual((first['accompanimentStartTick'], first['accompanimentEndTick'], second['accompanimentEndTick']), (0, 1.5, 2))
                self.assertEqual(first.get('tempoRatio', 1), 1 if target is None else 1.75)
                self.assertAlmostEqual(second.get('tempoRatio', 1), 1 if target is None else 8/7)

    def test_a_slot_crossing_a_tempo_boundary_cannot_claim_one_ramp(self):
        clock = PerformanceClock([(0, 1, 0, 1)], [(0, .5, 60, 60), (.5, 1, 30, 30)], 60)
        with self.assertRaisesRegex(ValueError, '표시 시간 구간 안에 빠르기 경계'):
            clock.tempo_ratio(0, 1)

    def test_length_marks_and_held_units_keep_the_exact_written_clock_on_repeats(self):
        source = from_melody('청중 -{늘임표} -|청중{덧길이표}|청중{반길이표}|청중')
        placements = [
            {'eventId': source['events'][index]['id'], 'cellId': f'c0-j{index}',
             'row': row, 'col': 0, 'symbols': [symbol]}
            for index, row, symbol in [(0, 1, '늘임표'), (1, 0, '덧길이표'), (2, 0, '반길이표')]
        ]
        expressions = {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a' * 64,
                       'placements': placements, 'performancePlan': {'schema': 'danso-performance/v1',
                       'input_sha256': 'a' * 64, 'tempos': [{'gak': 1, 'cell': 1, 'bpm': 120,
                           'end_gak': 2, 'end_cell': 1}], 'repeats': [{'start_gak': 1, 'end_gak': 1, 'times': 2}]}}
        _, mapping = convert_common_score(source, bpm=60, instrument='danso', input_sha256='a' * 64,
                                          notation_policy='educational-preview/v1', expressions=expressions)
        timing = mapping['timingMap']
        clock = PerformanceClock(timing['spans'], timing['tempos'], timing['baseBpm'])
        segments = mapping['performanceTimeline']['segments']
        self.assertEqual([s['startTick'] for s in segments], [0, 1/3, 2/3, 1, 2, 3] * 2)
        self.assertTrue(any(b - a != wb - wa for a, b, wa, wb in timing['spans']))
        for occurrence, repeated in zip(timing['passes'], (segments[:6], segments[6:])):
            for segment in repeated:
                a, b = segment['startTick'], segment['endTick']
                ratio = segment['tempoRatio']
                self.assertAlmostEqual(ratio, (60 + 15 * b) / (60 + 15 * a))
                # Same inverse used by FE seeking, compared with the melody clock owner.
                fraction = .5
                local_ms = segment['startMs'] + (segment['endMs'] - segment['startMs']) * math.log1p((ratio - 1) * fraction) / math.log(ratio)
                performed = clock.warp(clock.source_position(a + (b - a) * fraction))
                expected_ms = (occurrence['offset'] + performed) * timing['quarterSeconds'] * 1000
                # Sidecar endpoints retain the renderer's MIDI rounding (one tick bound).
                self.assertAlmostEqual(local_ms, expected_ms, delta=1000 * timing['quarterSeconds'] / PPQN)
