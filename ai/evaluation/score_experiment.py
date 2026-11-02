"""서비스 점수를 기준으로 실험용 부분 점수와 비중을 계산한다."""

from dataclasses import dataclass, replace
import math
import sys

from ai_service.pipelines import scoring


@dataclass(frozen=True)
class Experiment:
    pitch_cent: float = 50
    duration_ratio: float = 0.25
    onset_spread: float = 4
    pitch_penalty: float = 0
    timing_penalty: float = 0
    large_error_exponent: float = 1
    weights: tuple[float, float, float] = (1, 1, 1)

    def validate(self):
        limits = [(self.pitch_cent, 10, 100), (self.duration_ratio, 0.05, 1),
                  (self.onset_spread, 0, 10), (self.pitch_penalty, 0, 1),
                  (self.timing_penalty, 0, 1), (self.large_error_exponent, 1, 4)]
        if any(not math.isfinite(value) or not low <= value <= high for value, low, high in limits):
            raise ValueError('실험 설정이 허용 범위를 벗어났습니다')
        if len(self.weights) != 3 or any(not math.isfinite(w) or not 0 <= w <= 10 for w in self.weights) or not sum(self.weights):
            raise ValueError('점수 비중의 합은 0보다 커야 합니다')


def credit(error, tolerance, penalty, exponent):
    ratio = abs(error) / tolerance
    if ratio <= 1:
        return 1 - penalty * ratio
    return (1 - penalty) * max(0, 2 - ratio) ** exponent


def score_experiment(f0, notes, bpm, ref_hz, spec, rms=None):
    """호출 스레드에서만 실제 채점의 원래 정밀도 측정값을 읽어 재계산한다."""
    spec.validate()
    tolerance = scoring.Tolerance(pitch_cent=spec.pitch_cent, duration_ratio=spec.duration_ratio, onset_spread=spec.onset_spread)
    captured = {}

    def trace(frame, event, arg):
        if frame.f_code is scoring.score.__code__ and event == 'return':
            captured.update({key: frame.f_locals.get(key) for key in ('played', 'resid', 'onset_tol_s')})
        return trace

    previous = sys.gettrace()
    sys.settrace(trace)
    try:
        result = scoring.score(f0, notes, bpm, ref_hz, tolerance, rms)
    finally:
        sys.settrace(previous)
    if result.status != 'DONE':
        return {'result': result, 'onset_tolerance_ms': None}
    if any(captured.get(key) is None for key in ('played', 'resid', 'onset_tol_s')):
        raise RuntimeError('채점 측정값을 확인할 수 없습니다')
    played = {p[0]: (p, r) for p, r in zip(captured['played'], captured['resid'], strict=True)}
    pitch, timing, standard_pitch, standard_timing = [], [], [], []
    for k, note in enumerate(notes):
        if k not in played:
            if not note.sigimsae:
                pitch.append(0.0)
                standard_pitch.append(0.0)
            timing.append(0.0)
            standard_timing.append(0.0)
            continue
        (_, err, onset, _, _), resid = played[k]
        relative = min(resid, onset, key=abs)
        if not note.sigimsae:
            pitch.append(credit(err, spec.pitch_cent, spec.pitch_penalty, spec.large_error_exponent))
            standard_pitch.append(credit(err, spec.pitch_cent, 0, 1))
        timing.append(credit(relative, captured['onset_tol_s'], spec.timing_penalty, spec.large_error_exponent))
        standard_timing.append(credit(relative, captured['onset_tol_s'], 0, 1))

    def axis(values):
        return round(100 * sum(values) / len(values)) if values else 100

    if (axis(standard_pitch), axis(standard_timing)) != (result.pitch, result.timing):
        raise RuntimeError('실제 채점과 측정값 재계산이 일치하지 않습니다')
    p, t = axis(pitch), axis(timing)
    w = spec.weights
    total = round((p * w[0] + t * w[1] + result.duration * w[2]) / sum(w))
    return {'result': replace(result, pitch=p, timing=t, total=total),
            'onset_tolerance_ms': captured['onset_tol_s'] * 1000}
