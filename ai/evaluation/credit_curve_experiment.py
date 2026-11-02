"""저장된 353 실험의 음정·박자 오차에 부분 점수 후보를 적용한다."""

import argparse
import json
from pathlib import Path


def current_credit(ratio):
    return max(0.0, min(1.0, 2.0 - abs(ratio)))


def candidate_credit(ratio, edge_penalty):
    if not 0 <= edge_penalty <= 1:
        raise ValueError("허용 경계 감점은 0~1이어야 한다")
    ratio = abs(ratio)
    if ratio <= 1:
        return 1 - edge_penalty * ratio
    return max(0.0, (1 - edge_penalty) * (2 - ratio))


def score_axis(ratios, credit):
    return round(100 * sum(credit(r) if r is not None else 0 for r in ratios) / len(ratios))


def compare(run):
    baseline = run['rows']['4.0']
    notes = baseline['notes']
    # 이 입력은 모두 시김새가 없는 전곡이다. 놓친 음은 두 항목에서 0점이다.
    pitch = [None if n['pitch_error_cent'] is None else n['pitch_error_cent'] / 50 for n in notes]
    timing = [None if n['effective_onset_ms'] is None else n['effective_onset_ms'] / baseline['tol_ms'] for n in notes]
    if score_axis(pitch, current_credit) != baseline['pitch'] or score_axis(timing, current_credit) != baseline['timing']:
        raise ValueError(f"{run['name']}: 기존 점수 재계산 불일치")
    rows = []
    for edge_penalty in (0.1, 0.2):
        p = score_axis(pitch, lambda ratio: candidate_credit(ratio, edge_penalty))
        t = score_axis(timing, lambda ratio: candidate_credit(ratio, edge_penalty))
        for axis, pitch_score, timing_score in (
            ('pitch', p, baseline['timing']),
            ('timing', baseline['pitch'], t),
            ('both', p, t),
        ):
            rows.append({'edge_penalty': edge_penalty, 'changed': axis, 'pitch': pitch_score,
                         'timing': timing_score, 'duration': baseline['duration'],
                         'total': round((pitch_score + timing_score + baseline['duration']) / 3)})
    return {'name': run['name'], 'audio_sha256': run['audio_sha256'], 'bpm': run['bpm'],
            'baseline': {key: baseline[key] for key in ('total', 'pitch', 'timing', 'duration')}, 'rows': rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path, help='compare_onset_spread가 저장한 결과')
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    runs = json.loads(args.input.read_text(encoding='utf-8'))
    expected = {
        '81', '78', 'p01-danso-arirang-20261029-t1',
        'p02-sogeum-arirang-20261029-t1', 'p02-sogeum-doraji-20261029-t1',
    }
    if len(runs) != 5 or {run['name'] for run in runs} != expected:
        raise ValueError('시김새가 없는 다섯 입력 조건이 필요하다')
    results = [compare(run) for run in runs]
    args.out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    for result in results:
        print(json.dumps(result, ensure_ascii=True))


if __name__ == '__main__':
    main()
