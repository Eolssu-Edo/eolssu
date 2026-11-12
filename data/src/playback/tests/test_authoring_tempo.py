"""Authored timing examples, independent of CAT samples and recognition output."""
import copy
import math
import unittest
import hashlib
import json
import tempfile
from pathlib import Path
from data.src.playback.render_request import estimate
from data.src.playback.service import GenerationError, PlaybackService
import sys

from data.src.playback.common_score import convert_common_score
from data.src.playback.render_request import jangdan_events
from data.src.playback.tests._helpers import common_notes


class AuthoringTempoTests(unittest.TestCase):
    def estimate(self, request):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'request.json').write_text(json.dumps(request), encoding='utf-8')
            estimate(directory)
            return json.loads((root / 'result.json').read_text(encoding='utf-8'))

    def test_public_estimate_accepts_161_and_rejects_final_subframe_notes_with_reason(self):
        request = self.request()
        request.update(withJangdan=False, bpm=161)
        service = PlaybackService([sys.executable, '-m', 'data.src.playback.render_request'], timeout_seconds=10)
        self.assertGreater(service.estimate(request)['durationMs'], 0)
        request['bpm'] = 2_000_000
        result = self.estimate(request)
        self.assertEqual(result['errorCode'], 'TEMPO_UNRENDERABLE')
        self.assertEqual(result['details']['reason'], 'PCM_NOTE_TOO_SHORT')
        self.assertEqual(result['details']['bpmUnit'], 'QUARTER')
        self.assertTrue(result['details']['eventIds'])

    def test_numeric_companion_reaches_estimate_and_stale_hash_is_rejected(self):
        request = self.request()
        request['withJangdan'] = False
        digest = hashlib.sha256(json.dumps(request['score'], ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        request['expressions'] = {'schema': 'common-score-expressions/v1', 'sourceSha256': digest,
            'placements': [], 'performancePlan': {'schema': 'danso-performance/v1', 'input_sha256': digest,
                'tempos': [{'gak': 1, 'cell': 3, 'bpm': 30}]}}
        self.assertEqual(self.estimate(request)['durationMs'], 6550)
        request['expressions']['sourceSha256'] = 'f' * 64
        self.assertEqual(self.estimate(request)['errorCode'], 'INVALID_REQUEST')

    def test_subtick_numeric_tempo_reports_the_original_event_and_plan_adjustment(self):
        request = self.request()
        request['withJangdan'] = False
        digest = hashlib.sha256(json.dumps(request['score'], ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        request['expressions'] = {'schema': 'common-score-expressions/v1', 'sourceSha256': digest,
            'placements': [], 'performancePlan': {'schema': 'danso-performance/v1', 'input_sha256': digest,
                'tempos': [{'gak': 1, 'cell': 1, 'bpm': 2_000_000}]}}
        result = self.estimate(request)
        self.assertEqual(result['errorCode'], 'TEMPO_UNRENDERABLE')
        self.assertEqual(result['details']['reason'], 'MIDI_NOTE_TOO_SHORT')
        self.assertEqual(result['details']['eventIds'], [request['score']['events'][0]['id']])
        self.assertEqual(result['details']['adjustmentTarget'], 'TEMPO_PLAN')
        self.assertNotIn('maximumBpm', result['details'])
        service = PlaybackService([sys.executable, '-m', 'data.src.playback.render_request'], timeout_seconds=10)
        with self.assertRaises(GenerationError) as caught:
            service.estimate(request)
        self.assertEqual(caught.exception.status, 400)
        self.assertEqual(caught.exception.code, result['errorCode'])
        self.assertEqual(caught.exception.details, result['details'])

    def request(self):
        source = common_notes((('황', 1),) * 4)
        source['music']['jangdan'] = '4박 기본 반주'
        return {'score': source, 'instrument': 'danso', 'bpm': 60, 'withJangdan': True,
                'jangdan': {'name': '4박 기본 반주', 'bpmUnit': 'QUARTER', 'patternCellEighths': 2,
                            'pattern': {'beats': ['덩', '덕', '궁', '덕'], 'group': [1, 1, 1, 1]}}}

    def mapping(self, request, expressions=None):
        return convert_common_score(request['score'], bpm=request['bpm'], instrument='danso',
                                    input_sha256='a' * 64, notation_policy='educational-preview/v1',
                                    expressions=expressions)[1]

    def test_accompaniment_follows_tempo_cues_and_has_no_extra_end_hit(self):
        request = self.request()
        request['score']['events'][1]['sigimsae'] = ['조금느리게']
        request['score']['events'][2]['sigimsae'] = ['본래속도로']
        mapping = self.mapping(request)
        hits = jangdan_events(request, 4.25, timing=mapping['timingMap'])
        self.assertEqual([(round(at * 1000), stroke) for at, stroke, _ in hits],
                         [(0, '덩'), (1000, '덕'), (2250, '쿵'), (3250, '덕')])

    def test_half_beat_stroke_inside_acceleration_uses_integral_not_linear_interpolation(self):
        request = self.request()
        request['jangdan']['patternCellEighths'] = 1
        request['jangdan']['pattern'] = {'beats': ['덩', '덕'], 'group': [1, 1]}
        expressions = {'schema': 'common-score-expressions/v1', 'sourceSha256': 'a' * 64,
                       'placements': [], 'performancePlan': {'schema': 'danso-performance/v1',
                       'input_sha256': 'a' * 64, 'tempos': [{'gak': 1, 'cell': 1, 'bpm': 120,
                                                          'end_gak': 2, 'end_cell': 1}]}}
        mapping = self.mapping(request, expressions)
        hits = jangdan_events(request, 4 * math.log(2), timing=mapping['timingMap'])
        self.assertAlmostEqual(hits[1][0], 4 * math.log(1.125), places=4)
        self.assertEqual(len(hits), 8)

    def test_first_rest_and_repetition_keep_zero_origin_and_repeat_each_written_stroke(self):
        request = self.request()
        request['score'] = common_notes((None, ('황', 1), ('황', 1), ('황', 1)))
        request['score']['events'][1]['sigimsae'] = ['반복표']
        mapping = self.mapping(request)
        hits = jangdan_events(request, 8, timing=mapping['timingMap'])
        self.assertEqual([at for at, _, _ in hits], list(range(8)))
