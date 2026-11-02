"""353 실험: 같은 음높이와 정렬 경로로 박자 흔들림 배수만 비교한다."""

import argparse
import hashlib
import json
import sys
from collections import Counter
from dataclasses import asdict
from pathlib import Path

import numpy as np
from ai_service.pipelines import scoring
from ai_service.pipelines.audio import decode_recording, SAMPLE_RATE
from evaluation.error_detection import score_notes
from evaluation.dtw_performance import limit_commit, LIMIT
from evaluation.dtw_full_performance import limit_working_set, RSS_LIMIT

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--recordings', type=Path, required=True, help='81점과 78점 아리랑 녹음 폴더')
parser.add_argument('--raw-root', type=Path, required=True, help='10월 29일 원본이 있는 저장소 루트')
parser.add_argument('--out', type=Path, required=True, help='Git에 올리지 않을 결과 JSON 경로')
args = parser.parse_args()
job = limit_commit(LIMIT)
limit_working_set(RSS_LIMIT)
repo = Path(__file__).resolve().parents[2]
main = args.raw_root
downloads = args.recordings
labels = [repo / 'data/annotations/recordings' / part / filename for part, filename in (
    ('danso', 'p01-danso-arirang-20261029-t1.json'),
    ('sogeum', 'p02-sogeum-arirang-20261029-t1.json'),
    ('sogeum', 'p02-sogeum-doraji-20261029-t1.json'),
)]
fixture = json.loads((repo / 'data/tests/fixtures/scores/ngc-standard-2022-p173.score.json').read_text(encoding='utf-8'))
cases = [(name, downloads / filename, fixture, 96.0, 0, 60) for name, filename in [
    ('81', '아리랑_단소_2.m4a'), ('78', '아리랑_단소_연습.m4a')]]
expected_hashes = {
    '81': 'ba30948d85b6a54744624f2323dc2c26353e56238112a2c3569e0002d7335657',
    '78': '90b2993f6377ae2c0234853602b612f48b326f25128a8ebf58c2f8897f231b53',
}
for name, path, *_ in cases:
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected_hashes[name]:
        raise ValueError(f'{path}: 녹음 해시 불일치')
for path in labels:
    label = json.loads(path.read_text(encoding='utf-8'))
    audio_path = main / label['audio']['path']
    score_path = repo / label['score']['path']
    if hashlib.sha256(audio_path.read_bytes()).hexdigest() != label['audio']['sha256']:
        raise ValueError(f'{path}: 녹음 해시 불일치')
    if hashlib.sha256(score_path.read_bytes()).hexdigest() != label['score']['sha256']:
        raise ValueError(f'{path}: 악보 해시 불일치')
    cases.append((label['recordingId'], audio_path, json.loads(score_path.read_text(encoding='utf-8')),
                  label['bpm'], label['range']['startNoteIdx'], label['range']['endNoteIdx']))

original_align = scoring._align
reports = []
for name, path, sheet, bpm, first, last in cases:
    notes = score_notes(sheet, first, last)
    audio = decode_recording(path).astype(np.float32) / 32768
    f0 = scoring.pyin_f0(audio, SAMPLE_RATE)
    rms = scoring.frame_rms(audio, SAMPLE_RATE)
    path_hashes = []
    def traced_align(expected, detected):
        aligned = original_align(expected, detected)
        path_hashes.append(hashlib.sha256(aligned.tobytes()).hexdigest())
        return aligned
    scoring._align = traced_align
    rows = {}
    try:
        for mult in (4.0, 3.5, 3.0, 5.0):
            captured = {}
            def trace(frame, event, arg):
                if frame.f_code is scoring.score.__code__ and event == 'return':
                    for key in ('spread', 'onset_tol_s', 'played', 'resid'):
                        captured[key] = frame.f_locals.get(key)
                return trace
            sys.settrace(trace)
            try:
                result = scoring.score(f0, notes, bpm, scoring.HWANG_HZ, scoring.Tolerance(onset_spread=mult), rms)
            finally:
                sys.settrace(None)
            if result.status != 'DONE':
                raise RuntimeError(f'{name}: {result.fail_reason}')
            played = captured['played']
            effective = {notes[p[0]].idx: min(float(r), float(p[2]), key=abs) * 1000 for p, r in zip(played, captured['resid'])}
            rows[str(mult)] = {'total': result.total, 'pitch': result.pitch, 'timing': result.timing,
                               'duration': result.duration, 'valid': len(played),
                               'spread_ms': captured['spread'] * 1000, 'tol_ms': captured['onset_tol_s'] * 1000,
                               'min_ms': 0.125 * 60000 / bpm, 'verdicts': Counter(n.verdict for n in result.notes),
                               'notes': [asdict(n) | {'effective_onset_ms': effective.get(n.idx)} for n in result.notes]}
    finally:
        scoring._align = original_align
    if len(set(path_hashes)) != 1:
        raise RuntimeError(f'{name}: 정렬 경로가 달라짐')
    base_notes = rows['4.0']['notes']
    for row in rows.values():
        row['changes'] = [{'idx': a['idx'], 'from': a['verdict'], 'to': b['verdict'],
                           'effective_onset_ms': b['effective_onset_ms']} for a, b in zip(base_notes, row['notes']) if a['verdict'] != b['verdict']]
    reports.append({'name': name, 'audio_sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'bpm': bpm, 'rows': rows})
    print(f'{name}: 4개 배수 비교 완료', flush=True)

args.out.write_text(json.dumps(reports, ensure_ascii=False, indent=2), encoding='utf-8')
