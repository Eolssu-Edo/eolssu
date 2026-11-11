import copy
import hashlib
import os
import unittest
from unittest.mock import patch
import tempfile
import json
import time
from pathlib import Path
import numpy as np
import soundfile as sf

from data.src.playback.tests._helpers import common_notes, common_small_score, HAVE_ENCODER, sampler
from data.src.playback.common_score import convert_common_score
from data.src.playback.midi import read_midi, write_midi
from data.src.playback.render_request import prepare_score, jangdan_events, load_janggu, estimate, render
from data.src.playback.score import compile_score
from data.src.playback.tests._helpers import score, synthetic_sampler


class RenderRequestTests(unittest.TestCase):
    def test_four_quarter_accompaniment_uses_score_tempo_and_preserves_each_stroke(self):
        request = {'score': common_small_score(), 'bpm': 160,
                   'jangdan': {'name': '4박 기본 반주', 'bpmUnit': 'QUARTER',
                               'defaultBpm': 160,
                               'pattern': {'beats': ['덩', '덕', '궁', '덕'], 'group': [1, 1, 1, 1]}}}
        events = jangdan_events(request, 3)
        self.assertEqual([(at, stroke) for at, stroke, _ in events],
                         [(0, '덩'), (.375, '덕'), (.75, '쿵'), (1.125, '덕'),
                          (1.5, '덩'), (1.875, '덕'), (2.25, '쿵'), (2.625, '덕')])
        request['bpm'] = 80
        self.assertEqual([(at, stroke) for at, stroke, _ in jangdan_events(request, 3)],
                         [(0, '덩'), (.75, '덕'), (1.5, '쿵'), (2.25, '덕')])

    @unittest.skipUnless(HAVE_ENCODER, "M4A requires ffmpeg and ffprobe")
    def test_render_binds_repeat_and_tempo_cues_to_final_m4a_and_written_score(self):
        score = common_small_score()
        score['events'][1]['sigimsae'] = ['반복표', '조금느리게', '덧길이표']
        score['events'][2]['sigimsae'] = ['본래속도로', '반길이표']
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / 'assets.json'
            assets.write_text('{"sogeum":"unused.zip"}', encoding='utf-8')
            source_hash = 'b' * 64
            request = {'score': score, 'instrument': 'sogeum', 'bpm': 60, 'withJangdan': False,
                       'performanceSourceSha256': source_hash}
            (root / 'request.json').write_text(json.dumps(request), encoding='utf-8')
            with patch.dict(os.environ, {'PLAYBACK_ASSETS_JSON': str(assets)}), \
                    patch('data.src.playback.__main__.CatSampler', return_value=sampler()):
                render(directory)
            performance = json.loads((root / 'performance.common.json').read_text(encoding='utf-8'))
            self.assertEqual(performance['sourceSha256'], source_hash)
            self.assertEqual(performance['instrument'], 'DANSO')
            self.assertEqual(performance['scoreVersion'], score['score']['scoreVersion'])
            self.assertEqual(performance['audioSha256'], hashlib.sha256((root / 'audio.m4a').read_bytes()).hexdigest())
            self.assertEqual([s['startTick'] for s in performance['segments']], [0, 1, 2, 3] * 2)
            self.assertEqual([s['endMs'] for s in performance['segments']],
                             [1000, 2875, 3375, 4375, 5375, 7250, 7750, 8750])

    def test_estimate_uses_export_tail_and_rejects_ten_minutes_before_rendering(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)
            def call(beats):
                request={'score':common_notes(tuple(('황',1) for _ in range(beats))),
                         'instrument':'danso','bpm':60,'withJangdan':False}
                (path/'request.json').write_text(json.dumps(request),encoding='utf-8')
                estimate(directory)
                return json.loads((path/'result.json').read_text())
            self.assertEqual(call(2),{'durationMs':2550})
            self.assertEqual(call(599),{'durationMs':599550})
            self.assertEqual(call(600),{'errorCode':'EXPORT_TOO_LONG'})

    def test_estimate_of_longest_score_stays_inside_service_deadline(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)
            request={'score':common_notes(tuple(('황',1) for _ in range(599))),
                     'instrument':'danso','bpm':60,'withJangdan':False}
            (path/'request.json').write_text(json.dumps(request),encoding='utf-8')
            started=time.monotonic()
            estimate(directory)
            # The service allows 10s on the production 1 CPU, so keep a wide margin.
            self.assertLess(time.monotonic()-started,3)

    def test_estimate_matches_exported_midi_length_at_uneven_tempo(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)
            request={'score':common_notes((('황',1),('태',1),('황',1))),
                     'instrument':'danso','bpm':97,'withJangdan':False}
            (path/'request.json').write_text(json.dumps(request),encoding='utf-8')
            estimate(directory)
            source=prepare_score(request['score'],'danso')
            value,_=convert_common_score(source,bpm=97,instrument='danso',input_sha256='0'*64,
                                         notation_policy='educational-preview/v1')
            midi=path/'expected.mid'
            write_midi(compile_score(value,'danso',max_duration_seconds=600,synthesis_extension=True),midi)
            expected=read_midi(midi)['seconds']*1000
            self.assertLessEqual(abs(json.loads((path/'result.json').read_text())['durationMs']-expected),1)

    def test_synthesis_extension_preserves_out_of_range_pitch_without_changing_default_policy(self):
        value=score()
        value['notes'][0]['pitch']=60
        with self.assertRaises(ValueError):
            compile_score(value,'danso')
        performance=compile_score(value,'danso',synthesis_extension=True)
        self.assertEqual(performance['events'][0]['pitch'],60)
        sampler=synthetic_sampler()
        sampler.synthesis_extension=True
        sampler.zones=[{'low':66,'high':70,'root':68}]
        self.assertEqual(sampler.zone(60)['root'],68)
        self.assertEqual(sampler.zones[0]['low'],66)
    def test_recorded_janggu_hits_use_prepared_pcm_without_cropping_twice(self):
        with tempfile.TemporaryDirectory() as directory:
            for name in ('deong','gi','kung','deok','da','deo'):
                sf.write(Path(directory)/f'{name}.wav',np.full(100,.2),44100,subtype='PCM_16')
            hits=load_janggu(directory)
            self.assertEqual(set(hits), {'덩','기','쿵','덕','다','더'})
            self.assertEqual(len(hits['덩']),100)
            self.assertAlmostEqual(float(hits['덩'][0]),.2,places=3)
    def test_instrument_change_preserves_pitch_and_original_document(self):
        source = common_notes()
        original = copy.deepcopy(source)
        changed = prepare_score(source, 'sogeum')
        self.assertEqual(source, original)
        self.assertEqual(changed['score']['instrument'], 'SOGEUM')
        self.assertEqual(changed['music']['hwangHz'], source['music']['hwangHz'])
        self.assertEqual(changed['events'], source['events'])

    def test_jangdan_repeats_at_score_cycle_not_catalog_default_bpm(self):
        request = {'score': common_notes(), 'bpm': 60, 'jangdan': {
            'name': '중모리', 'bpmUnit':'QUARTER',
            'pattern': {'beats': ['덩', '덕'], 'group': [2]}}}
        events = jangdan_events(request, 5)
        self.assertEqual([(round(at, 3), stroke) for at, stroke, _ in events],
                         [(0, '덩'), (1, '덕'), (2, '덩'), (3, '덕'), (4, '덩')])
