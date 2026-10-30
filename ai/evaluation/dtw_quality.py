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
from evaluation.dtw_candidates import NoPath, band_bounds, band_path, checkpoint_path
from evaluation.error_detection import classify, load_trials, score_notes

ROOT = Path(__file__).resolve().parents[2]
BUDGET = 512 * 1024 * 1024
METHODS = ('reference', 'A', 'B0.5', 'B1.0', 'B2.0')


def note_groups(notes, size):
    """음표를 순서대로 한 번씩 포함하는 별도 비교 구간."""
    for start in range(0, len(notes), size):
        yield notes[start:start + size]


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
        except MemoryError:
            raise  # 메모리 부족 뒤에는 같은 프로세스에서 다른 시험을 안전하게 계속하지 않는다.
        except Exception as error:
            output[name] = {'status': 'ERROR', 'reason': f'{type(error).__name__}: {error}',
                            'score': None, 'path': None, 'estimatedBytes': estimated}
    return output


def synth(notes, bpm, variant):
    """악보 구간에 정확한 음높이를 채운 뒤 변형한다. 숨과 장식은 실제 연주가 아니다."""
    beat_s = 60 / bpm
    starts = [(n.start_beat - notes[0].start_beat) * beat_s for n in notes]
    end = starts[-1] + notes[-1].beats * beat_s
    f0 = np.full(round(end / scoring.HOP_S), np.nan)
    rms = np.zeros(len(f0))
    for note, start in zip(notes, starts):
        first = round(start / scoring.HOP_S)
        stop = round((start + note.beats * beat_s) / scoring.HOP_S)
        f0[first:stop] = scoring.HWANG_HZ * 2 ** (note.semitone / 12)
        rms[first:stop] = 1.0
        if first:
            rms[first:min(first + 2, stop)] = 0.1
    middle = len(notes) // 2
    first = round(starts[middle] / scoring.HOP_S)
    stop = round((starts[middle] + notes[middle].beats * beat_s) / scoring.HOP_S)
    if variant in ('silence_two', 'cut_two') and middle + 1 < len(notes):
        stop = round((starts[middle + 1] + notes[middle + 1].beats * beat_s) / scoring.HOP_S)
    if variant in ('silence', 'silence_two'):
        f0[first:stop] = np.nan
        rms[first:stop] = 0.0
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


def load_recording_case(path, seen_ids=None):
    """정답 기록 하나의 입력 오류를 다른 녹음의 결과와 분리한다."""
    try:
        _, trials = load_trials([path], ROOT)
        trial = trials[0]
        label = trial.label
        if seen_ids is not None:
            if label['recordingId'] in seen_ids:
                raise ValueError(f"duplicate recordingId: {label['recordingId']}")
            seen_ids.add(label['recordingId'])
        results = compare(trial.f0, trial.notes, label['bpm'], trial.rms)
        errors = {e['idx']: e['kind'] for e in label['errors']}
        for result in results.values():
            if result['status'] == 'DONE':
                result['classification'] = classify([scoring.NoteResult(**n) for n in result['score']['notes']], errors)
        return {'recordingId': label['recordingId'], 'labelPath': str(path), 'audioHash': label['audio']['sha256'],
                'inputHash': hashlib.sha256(trial.f0.tobytes()).hexdigest(),
                'rmsHash': None if trial.rms is None else hashlib.sha256(trial.rms.tobytes()).hexdigest(),
                'errors': errors, 'results': results}
    except MemoryError:
        raise
    except Exception as error:
        reason = f'{type(error).__name__}: {error}'
        return {'recordingId': str(path), 'labelPath': str(path),
                'results': {m: {'status': 'INPUT_ERROR', 'reason': reason, 'score': None, 'path': None} for m in METHODS}}


def summarize_cases(cases):
    """음 ID와 성공 여부를 확인하고 비교 가능한 입력과 제외 입력을 분리한다."""
    summary = {m: {'statusCounts': {}, 'failureReasons': {}, 'comparisons': [], 'excluded': []} for m in METHODS}
    for case in cases:
        case_id = case.get('recordingId') or f"{case['source']}:{case['variant']}:{case['notes'][0]['idx']}"
        reference = case['results']['reference']
        for method, target in summary.items():
            if method not in case['results']:
                continue
            result = case['results'][method]
            status = result['status']
            target['statusCounts'][status] = target['statusCounts'].get(status, 0) + 1
            if status != 'DONE':
                reason = result.get('reason') or (result.get('score') or {}).get('fail_reason') or status
                key = f'{status}: {reason}'
                target['failureReasons'][key] = target['failureReasons'].get(key, 0) + 1
            if reference['status'] != 'DONE' or status != 'DONE':
                target['excluded'].append({'caseId': case_id, 'referenceStatus': reference['status'],
                    'candidateStatus': status, 'referenceReason': reference.get('reason') or (reference.get('score') or {}).get('fail_reason'),
                    'candidateReason': result.get('reason') or (result.get('score') or {}).get('fail_reason')})
                continue
            before = {n['idx']: n for n in reference['score']['notes']}
            after = {n['idx']: n for n in result['score']['notes']}
            if len(before) != len(reference['score']['notes']) or len(after) != len(result['score']['notes']) or before.keys() != after.keys():
                target['excluded'].append({'caseId': case_id, 'referenceStatus': 'NOTE_ID_MISMATCH', 'candidateStatus': status})
                continue
            path = reference['path']
            inside = None
            if method.startswith('B') and path:
                n, m = path[0][0] + 1, path[0][1] + 1
                starts, stops = band_bounds(n, m, round(float(method[1:]) / scoring.HOP_S))
                inside = all(starts[i] <= j < stops[i] for i, j in path)
            changed = [{'idx': idx, 'reference': before[idx]['verdict'], 'candidate': after[idx]['verdict']}
                       for idx in before if before[idx]['verdict'] != after[idx]['verdict']]
            measurements = [{'idx': idx, 'reference': before[idx], 'candidate': after[idx]}
                            for idx in before if before[idx] != after[idx]]
            target['comparisons'].append({'caseId': case_id, 'referenceInsideBand': inside,
                'pathChanged': path != result['path'], 'referenceTotal': reference['score']['total'],
                'candidateTotal': result['score']['total'], 'changedNotes': changed, 'changedMeasurements': measurements})
    return summary


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
              'labelHashes': {str(p): hashlib.sha256(p.read_bytes()).hexdigest() if p.is_file() else None for p in args.labels},
              'cases': []}
    for page in (171, 172, 173, 174):
        source = f'ngc-standard-2022-p{page}'
        data = convert_answer(source)
        full_notes = score_notes(data, 0, 10000)
        bpm = 66 if page == 174 else 96
        f0, rms = synth(full_notes, bpm, 'normal')
        report['cases'].append({'source': source, 'provenance': data['provenance'], 'variant': 'full_normal',
            'bpm': bpm, 'inputHash': hashlib.sha256(f0.tobytes()).hexdigest(),
            'rmsHash': hashlib.sha256(rms.tobytes()).hexdigest(),
            'notes': [asdict(n) for n in full_notes], 'results': compare(f0, full_notes, bpm, rms)})
        if page == 171:
            for group in note_groups(full_notes, 31):
                for variant in ('normal', 'silence', 'silence_two', 'cut', 'cut_two', 'pitch',
                                'octave', 'tuning', 'slow', 'fast', 'local_slow', 'ornament'):
                    case_notes = list(group)
                    if variant == 'ornament':
                        middle = len(case_notes) // 2
                        case_notes[middle] = replace(case_notes[middle], sigimsae=True)
                    f0, rms = synth(case_notes, bpm, variant)
                    report['cases'].append({'source': source, 'provenance': data['provenance'],
                        'variant': f'segment_{variant}', 'bpm': bpm,
                        'inputHash': hashlib.sha256(f0.tobytes()).hexdigest(),
                        'rmsHash': hashlib.sha256(rms.tobytes()).hexdigest(),
                        'notes': [asdict(n) for n in case_notes], 'results': compare(f0, case_notes, bpm, rms)})
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
                'inputHash': hashlib.sha256(f0.tobytes()).hexdigest(), 'rmsHash': hashlib.sha256(rms.tobytes()).hexdigest(),
                'notes': [asdict(n) for n in case_notes],
                'results': compare(f0, case_notes, bpm, rms)})
    if args.labels:
        seen_ids = set()
        for path in args.labels:
            report['cases'].append(load_recording_case(path, seen_ids))
    report['summary'] = summarize_cases(report['cases'])
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, allow_nan=False), encoding='utf-8')
    print(f"{args.out}: {len(report['cases'])} cases")


if __name__ == '__main__':
    main()
