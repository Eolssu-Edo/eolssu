"""Local HTTP -> real CAT/MIDI/M4A timing check; requires explicit local assets.

Run from the repository root with `python -m data.tests.runtime.verify_section_tempo
--assets ASSETS_JSON --out NEW_DIRECTORY`. Set FFMPEG_BINARY/FFPROBE_BINARY as needed.
No production services or existing output directories are used.
"""
import argparse
import copy
import hashlib
import http.client
import io
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import threading
import zipfile
from http.server import ThreadingHTTPServer

import numpy as np

from data.src.playback.audio import resolve_encoder
from data.src.playback.midi import read_midi
from data.src.playback.service import PlaybackService, handler
from data.src.playback.verify_common import fundamental, PITCH_LIMIT_CENTS


def verify(assets, out):
    encoder = resolve_encoder()
    os.environ['PLAYBACK_ASSETS_JSON'] = str(assets.resolve())
    out.mkdir(parents=True, exist_ok=False)
    repo = Path(__file__).resolve().parents[3]
    fixture = json.loads((repo / 'frontend/shared/src/androidHostTest/resources/scores/personal-jangdan/step.json').read_text(encoding='utf-8'))
    service = PlaybackService([sys.executable, '-m', 'data.src.playback.render_request'], timeout_seconds=90)
    server = ThreadingHTTPServer(('127.0.0.1', 0), handler(service))
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()

    def post(path, request):
        connection = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=100)
        try:
            connection.request('POST', path, json.dumps(request, ensure_ascii=False).encode(), {
                'Content-Type': 'application/json', 'X-Eolssu-Performance-Source-Sha256': fixture['sourceSha256']})
            response = connection.getresponse()
            payload = response.read()
            assert response.status == 200, (response.status, payload[:500])
            return payload
        finally:
            connection.close()

    receipts = []
    try:
        for name in ('base', 'step', 'explicit-start-ramp'):
            request = {'score': json.loads(fixture['sourceJson']), 'instrument': 'danso', 'bpm': 60, 'withJangdan': False}
            boundaries = [0, 1000, 2000, 3000, 4000]
            if name != 'base':
                request['expressions'] = copy.deepcopy(fixture['expressions'])
                boundaries = [0, 1000, 2000, 4000, 6000]
            if name == 'explicit-start-ramp':
                request['expressions']['performancePlan']['tempos'] = [
                    {'gak': 1, 'cell': 1, 'start_bpm': 30, 'bpm': 120, 'end_gak': 2, 'end_cell': 1}]
                boundaries = [60000 * 4 / 90 * math.log((30 + 90 * i / 4) / 30) for i in range(5)]
            case = out / name
            case.mkdir()
            (case / 'request.json').write_text(json.dumps(request, ensure_ascii=False), encoding='utf-8')
            estimate = json.loads(post('/v1/estimate', request))
            payload = post('/v1/render', request)
            (case / 'result.zip').write_bytes(payload)
            with zipfile.ZipFile(io.BytesIO(payload)) as bundle:
                audio = bundle.read('audio.m4a')
                (case / 'audio.m4a').write_bytes(audio)
                (case / 'score.mid').write_bytes(bundle.read('score.mid'))
                performance = json.loads(bundle.read('performance.common.json'))
                result = json.loads(bundle.read('result.json'))
            assert performance['sourceSha256'] == fixture['sourceSha256']
            assert performance['audioSha256'] == hashlib.sha256(audio).hexdigest()
            actual = [performance['segments'][0]['startMs']] + [s['endMs'] for s in performance['segments']]
            assert len(actual) == len(boundaries) and max(abs(a-b) for a, b in zip(actual, boundaries)) < 1, (name, actual, boundaries)
            assert abs(result['durationMs'] - (boundaries[-1] + 550)) <= 2, (name, result)
            assert abs(estimate['durationMs'] - result['durationMs']) <= 1
            midi = read_midi(case / 'score.mid')
            assert len(midi['notes']) == 3
            assert max(abs(n['start'] * 1000 - at) for n, at in zip(midi['notes'], boundaries[1:4])) < 1
            decoded = subprocess.run([encoder['ffmpeg'], '-v', 'error', '-i', str(case / 'audio.m4a'),
                '-f', 'f32le', '-ac', '1', '-ar', '44100', '-'], check=True, capture_output=True)
            pcm = np.frombuffer(decoded.stdout, dtype='<f4')
            assert -1 < len(pcm) / 44.1 - result['durationMs'] < 25, (name, len(pcm), result)
            def rms(start, stop):
                samples = pcm[round(start * 44.1):round(stop * 44.1)]
                return float(np.sqrt(np.mean(samples * samples)))
            # The fixed input begins with one rest. Probe the decoded file on both
            # sides of its first real note, independently of timeline/MIDI metadata.
            onset = boundaries[1]
            silent, audible = rms(onset - 150, onset - 50), rms(onset + 100, onset + 200)
            assert silent < 1e-4 and audible > 1e-3, (name, silent, audible)
            observed_hz = []
            notes = [event for event in request['score']['events'] if event['kind'] == 'NOTE']
            for index, event in enumerate(notes, 1):
                expected_hz = request['score']['music']['hwangHz'] * 2 ** (event['octave'] + {'황': 0, '태': 2}[event['yul']] / 12)
                edges = []
                for start in (boundaries[index] + 100, boundaries[index + 1] - 200):
                    hz, _ = fundamental(pcm[round(start * 44.1):round((start + 100) * 44.1)], 44100)
                    assert hz is not None and abs(1200 * math.log2(hz / expected_hz)) <= PITCH_LIMIT_CENTS, (name, index, hz, expected_hz)
                    edges.append(hz)
                observed_hz.append(edges)
            receipts.append({'case': name, 'estimateMs': estimate['durationMs'], **result,
                'boundariesMs': actual, 'audioSha256': performance['audioSha256'],
                'sourceSha256': performance['sourceSha256'], 'restRms': silent, 'noteRms': audible,
                'noteEdgeHz': observed_hz})
            print(name, 'HTTP200', result['durationMs'], 'ms; decoded onset verified', flush=True)
        (out / 'receipt.json').write_text(json.dumps({'encoder': encoder, 'cases': receipts}, indent=2), encoding='utf-8')
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    verify(args.assets, args.out)
