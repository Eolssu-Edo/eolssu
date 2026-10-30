"""동일 f0에 현행과 연구 후보를 적용한다. 단독 프로세스에서만 실행한다."""
import argparse
from dataclasses import asdict, replace
from functools import partial
import hashlib
from importlib.metadata import version
import json
from pathlib import Path
import sys
from unittest.mock import patch

import numpy as np

from ai_service.pipelines import scoring
from evaluation.dtw_candidates import NoPath, band_path, checkpoint_path
from evaluation.error_detection import classify, load_trials, score_notes

ROOT = Path(__file__).resolve().parents[2]
BUDGET = 512 * 1024 * 1024


def compare(f0, notes, bpm, rms=None, budget=BUDGET):
    """실패와 예산 거절에는 음별 판정을 만들지 않는다."""
    seconds = (notes[-1].start_beat - notes[0].start_beat + notes[-1].beats
               if notes[0].start_beat is not None else sum(n.beats for n in notes)) * 60 / bpm
    estimated = 24 * round(seconds / scoring.HOP_S) * len(f0)
    original = scoring._align
    methods = {'reference': original, 'A': checkpoint_path,
               **{f'B{w}': partial(band_path, half_width_frames=round(w / scoring.HOP_S)) for w in (0.5, 1.0, 2.0)}}
    output = {}
    for name, target in methods.items():
        if estimated > budget:
            output[name] = {'status': 'SKIPPED_BUDGET', 'score': None, 'path': None, 'estimatedBytes': estimated}
            continue
        path = None

        def align(expected, detected):
            nonlocal path
            path = target(expected, detected)
            return path

        try:
            with patch.object(scoring, '_align', align):
                result = scoring.score(f0, notes, bpm, scoring.HWANG_HZ, rms=rms)
            output[name] = {'status': result.status, 'score': asdict(result),
                            'path': None if path is None else path.tolist(), 'estimatedBytes': estimated}
        except NoPath as error:
            output[name] = {'status': 'NO_PATH', 'reason': str(error), 'score': None, 'path': None, 'estimatedBytes': estimated}
    return output


def synth(notes, bpm, variant):
    """악보 구간에 정확한 음높이를 채운 뒤 변형한다. 숨과 장식은 실제 연주가 아니다."""
    beat_s = 60 / bpm
    starts = [(n.start_beat - notes[0].start_beat) * beat_s for n in notes]
    end = starts[-1] + notes[-1].beats * beat_s
    f0 = np.full(round(end / scoring.HOP_S), np.nan)
    rms = np.ones(len(f0))
    for note, start in zip(notes, starts):
        first = round(start / scoring.HOP_S)
        stop = round((start + note.beats * beat_s) / scoring.HOP_S)
        f0[first:stop] = scoring.HWANG_HZ * 2 ** (note.semitone / 12)
        if first:
            rms[first:min(first + 2, stop)] = 0.1
    middle = len(notes) // 2
    first = round(starts[middle] / scoring.HOP_S)
    stop = round((starts[middle] + notes[middle].beats * beat_s) / scoring.HOP_S)
    if variant in ('silence_two', 'cut_two') and middle + 1 < len(notes):
        stop = round((starts[middle + 1] + notes[middle + 1].beats * beat_s) / scoring.HOP_S)
    if variant in ('silence', 'silence_two'):
        f0[first:stop] = np.nan
    elif variant in ('cut', 'cut_two'):
        f0 = np.r_[f0[:first], f0[stop:]]
        rms = np.r_[rms[:first], rms[stop:]]
    elif variant == 'pitch':
        f0[first:stop] *= 2 ** (2 / 12)
    elif variant == 'octave':
        f0[first:stop] *= 2
    elif variant == 'tuning':
        f0 *= 2 ** (40 / 1200)
    elif variant in ('slow', 'fast'):
        factor = 1 / 0.75 if variant == 'slow' else 1 / 1.25
        indices = np.minimum((np.arange(round(len(f0) * factor)) / factor).astype(int), len(f0) - 1)
        f0, rms = f0[indices], rms[indices]
    elif variant == 'local_slow':
        indices = np.r_[np.arange(first), first + np.minimum((np.arange(round((len(f0) - first) * 1.25)) / 1.25).astype(int), len(f0) - first - 1)]
        f0, rms = f0[indices], rms[indices]
    elif variant == 'ornament':
        f0[first:stop] *= 2 ** (100 * np.sin(np.arange(stop - first) * 0.2) / 1200)
    return f0, rms


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--labels', type=Path, nargs='*', default=[])
    args = parser.parse_args()
    sys.path.insert(0, str(ROOT / 'data/src/scores'))
    from score_data import convert_answer

    report = {'tolerance': asdict(scoring.Tolerance()), 'refHz': scoring.HWANG_HZ,
              'hopSeconds': scoring.HOP_S, 'budgetBytes': BUDGET,
              'python': sys.version, 'versions': {p: version(p) for p in ('numpy', 'librosa', 'numba')},
              'codeHashes': {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in (
                  'ai/evaluation/dtw_quality.py', 'ai/evaluation/dtw_candidates.py',
                  'ai/src/ai_service/pipelines/scoring.py', 'data/src/scores/score_data.py', 'ai/uv.lock')},
              'labelHashes': {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in args.labels},
              'cases': []}
    for page in (171, 172, 173, 174):
        source = f'ngc-standard-2022-p{page}'
        data = convert_answer(source)
        full_notes = score_notes(data, 0, 10000)
        bpm = 66 if page == 174 else 96
        f0, rms = synth(full_notes, bpm, 'normal')
        report['cases'].append({'source': source, 'provenance': data['provenance'], 'variant': 'full_normal',
            'bpm': bpm, 'inputHash': hashlib.sha256(f0.tobytes()).hexdigest(),
            'notes': [asdict(n) for n in full_notes], 'results': compare(f0, full_notes, bpm, rms)})
        # 길이가 제한된 동일 악보 구간. 곡 전체 검증은 별도로 필요하다.
        notes = [n for n in full_notes if n.start_beat - full_notes[0].start_beat + n.beats <= 8]
        for variant in ('normal', 'silence', 'silence_two', 'cut', 'cut_two', 'pitch', 'octave',
                        'tuning', 'slow', 'fast', 'local_slow', 'ornament'):
            # 원본에 시김새 표시가 없어 별도로 가상의 표시를 추가한다.
            case_notes = list(notes)
            if variant == 'ornament':
                middle = len(case_notes) // 2
                case_notes[middle] = replace(case_notes[middle], sigimsae=True)
            f0, rms = synth(case_notes, bpm, variant)
            report['cases'].append({'source': source, 'provenance': data['provenance'], 'variant': variant, 'bpm': bpm,
                'inputHash': hashlib.sha256(f0.tobytes()).hexdigest(), 'notes': [asdict(n) for n in case_notes],
                'results': compare(f0, case_notes, bpm, rms)})
    if args.labels:
        _, trials = load_trials(args.labels, ROOT)
        for trial in trials:
            label = trial.label
            results = compare(trial.f0, trial.notes, label['bpm'], trial.rms)
            errors = {e['idx']: e['kind'] for e in label['errors']}
            for result in results.values():
                if result['status'] == 'DONE':
                    result['classification'] = classify([scoring.NoteResult(**n) for n in result['score']['notes']], errors)
            report['cases'].append({'recordingId': label['recordingId'], 'audioHash': label['audio']['sha256'],
                'inputHash': hashlib.sha256(trial.f0.tobytes()).hexdigest(), 'errors': errors, 'results': results})
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, allow_nan=False), encoding='utf-8')
    print(f"{args.out}: {len(report['cases'])} cases")


if __name__ == '__main__':
    main()
