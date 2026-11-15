"""악보 기반 대금 연주 채점.

녹음의 음높이 곡선(f0)과 정답 음표를 DTW로 맞춘 뒤 음표마다 음정·박자·길이를 판정한다.
음높이 단위는 기준음 대비 센트이고, 서비스 기준음은 HWANG_HZ다.
"""

from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field

import librosa
import numpy as np

HOP_S = 0.01  # f0 프레임 간격(초)
HWANG_HZ = 311.13  # 반음 0인 황 = E♭4. 반음 번호는 pitches.semitone(옥타브 × 12 + 율 순서)
_UNVOICED_COST = 300.0  # 소리 유무가 어긋난 프레임의 DTW 비용(센트)
_STRETCH_COST = 50.0  # DTW가 음표를 늘이거나 줄일 때 프레임마다 더하는 비용
_SUBHARMONIC_RATIO = 0.1  # A(f0)/A(2f0)가 이보다 작으면 f0를 2f0로 고친다
_ONSET_WINDOW = 3  # 박자 판정에서 기준으로 삼는 앞뒤 음 수
# 흔들림으로 허용을 넓히려면 소리 낸 음이 이만큼 있어야 한다. 음이 적으면 늦은 음 하나가 이웃 차이 여러 개를 흔들어 흔들림이 부푼다.
_SPREAD_MIN_NOTES = 12


def _align(expected: np.ndarray, detected: np.ndarray) -> np.ndarray:
    """기본 정렬은 기존 전체 DTW다. 평가에서는 비용 표 생성 전에 이 부분만 교체한다."""
    # ponytail: 전체 DTW라 메모리가 프레임 수의 제곱. 긴 녹음 대안은 S15P31E102-286에서 비교 중이다.
    cost =np.abs(expected[:, None] - detected[None, :])
    # 옥타브 어긋남은 소리 유무가 어긋난 만큼만 비싸게 둔다. 1200센트 그대로면 정렬이 그 음을
    # 한 프레임으로 줄이고 이웃 음에 넘긴다(S15P31E102-251). 판정은 원래 음높이로 한다.
    cost = np.minimum(cost, np.abs(cost - 1200.0) + _UNVOICED_COST)
    cost[np.isnan(cost)] = _UNVOICED_COST
    cost[np.isnan(expected)[:, None] & np.isnan(detected)[None, :]] = 0.0
    _, path = librosa.sequence.dtw(
        C=cost,
        step_sizes_sigma=np.array([[1, 1], [0, 1], [1, 0]]),
        weights_add=np.array([0.0, _STRETCH_COST, _STRETCH_COST]),
    )
    return path


@dataclass(frozen=True)
class Note:
    semitone: int  # 기준음에서 몇 반음 위인지
    beats: float
    sigimsae: bool = False  # True면 음정 판정에서 제외
    idx: int | None = None  # 요청의 음 번호. 없으면 목록 순번
    start_beat: float | None = None  # 곡 처음부터 센 시작 박. 없으면 앞 음에 바로 이어 붙인다


@dataclass(frozen=True)
class Tolerance:
    """허용 범위. 기본값은 녹음으로 검증하기 전의 제안 값이다."""

    pitch_cent: float = 50.0
    onset_beat: float = 0.125
    duration_ratio: float = 0.25  # 길이 허용은 자리(score 참고)의 이 비율과 duration_floor_s 중 큰 쪽이다
    # 음높이 도구가 끊김 앞뒤를 70ms쯤 소리로 이어 잡아, 짧은 음은 비율 허용만으로는 측정 오차보다 좁다.
    # 120ms는 아리랑 실제 연주 4개와 일부러 짧게 분 음으로 고른 값이다(S15P31E102-910).
    duration_floor_s: float = 0.12
    # 길이 오류(LENGTH) 판정: 자리가 length_error_min_s초 이상인 음에서 길이 오차가 자리의 이 비율(과 duration_floor_s 중
    # 큰 쪽)을 넘으면 오류다. 길이 점수가 절반 아래로 떨어지기 시작하는 지점과 비슷하다. 실제 녹음으로 고른 제안 값이다.
    length_error_ratio: float = 0.3
    length_error_min_s: float = 0.6
    max_tuning_cent: float = 100.0  # 곡에 반음 이웃이 있으면 score가 더 낮춘다
    # 박자 허용은 onset_beat 박과 (이 연주의 박자 흔들림 × onset_spread) 중 큰 쪽이다. 흔들림이 큰 연주일수록 후하다.
    onset_spread: float = 4.0


# 점수 합산 규칙 버전(evaluations.scoring_version). 음표별 부분 점수·weight·항목 가중치의 뜻이나 합치는 공식을 바꾸면 올린다.
# 2: 길이 부분 점수를 악보 길이 대신 자리(다음 음의 실제 시작까지)와 비교(S15P31E102-910)
SCORING_VERSION = "note-mean-2"
# 총점의 항목 가중치. 음표 weight는 모두 1이다(현재 채점이 음표를 균등 평균한다).
SCORE_WEIGHTS = {"pitch": 1 / 3, "timing": 1 / 3, "duration": 1 / 3}


@dataclass
class NoteResult:
    idx: int  # 요청의 음 번호(Note.idx). 없으면 정답 음표 순번
    verdict: str  # CORRECT, PITCH, TIMING, LENGTH, MISSED
    pitch_error_cent: float | None = None
    onset_error_ms: float | None = None
    duration_error_ms: float | None = None  # 울린 길이 - 자리(score 참고). 음수면 짧게 불었다
    octave_suspect: bool = False  # 정답과 옥타브 단위로 어긋남. 음높이 도구 오류일 수 있다
    detected_semitone: int | None = None  # 적용한 조율 보정을 뺀 뒤 가장 가까운 반음(pitches.semitone 눈금). MISSED면 None
    recording_offset_ms: int | None = None  # 녹음 0ms 기준 실제 소리 시작(10ms 단위). MISSED면 None
    pitch_score: float | None = None  # 0~100. 시김새 음은 음정을 보지 않아 None
    timing_score: float = 0.0  # 0~100. MISSED·PITCH는 0
    duration_score: float = 0.0  # 0~100. MISSED·PITCH는 0
    weight: float = 1.0  # 합산 가중치


@dataclass
class ScoreResult:
    status: str  # DONE, FAILED
    fail_reason: str | None = None  # NO_SOUND, TOO_SHORT
    total: int = 0
    pitch: int = 0
    timing: int = 0
    duration: int = 0
    tuning_offset_cent: float = 0.0
    tuning_out_of_range: bool = False
    notes: list[NoteResult] = field(default_factory=list)


def pyin_f0(
    audio: np.ndarray, sr: int, fmin: float = 100.0, fmax: float = 2500.0, frame_length: int = 1024
) -> np.ndarray:
    """pYIN으로 HOP_S 간격의 f0(Hz)를 구한다. 소리가 없는 프레임은 NaN."""
    f0, voiced, _ = librosa.pyin(
        audio, fmin=fmin, fmax=fmax, sr=sr, frame_length=frame_length, hop_length=round(sr * HOP_S)
    )
    return _undo_subharmonic(audio, sr, np.where(voiced, f0, np.nan))


def _undo_subharmonic(audio: np.ndarray, sr: int, f0: np.ndarray, n_fft: int = 4096) -> np.ndarray:
    """f0 자리에 소리가 거의 없고 2f0에만 있으면 2f0로 고친다.

    2f로 반복되는 소리는 f로도 반복되므로, pYIN은 앞뒤 음과 이어지는 쪽을 골라 짧은 옥타브 위 음을
    원래 옥타브로 잡는다(S15P31E102-251). 실제 단소 단음의 A(f)/A(2f)는 하위 1%가 0.82였다.
    """
    hop = round(sr * HOP_S)
    padded = np.pad(audio, n_fft // 2)  # pYIN처럼 프레임 i의 가운데를 i * hop에 둔다
    window = np.hanning(n_fft)
    out = f0.copy()
    voiced = np.flatnonzero(~np.isnan(f0))
    voiced = voiced[voiced * hop + n_fft <= padded.size]
    for start in range(0, voiced.size, 256):  # 256프레임씩 묶어 메모리를 수십 MB 안에 둔다
        i = voiced[start : start + 256]
        spectrum = np.abs(np.fft.rfft(padded[i[:, None] * hop + np.arange(n_fft)] * window, axis=1))
        rows = np.arange(i.size)[:, None]

        def near(hz):  # 주파수에 가장 가까운 칸과 양옆 한 칸 중 최대 크기
            k = np.rint(hz * n_fft / sr).astype(int)[:, None] + np.arange(-1, 2)
            return spectrum[rows, np.clip(k, 0, spectrum.shape[1] - 1)].max(axis=1)

        fix = i[near(f0[i]) < _SUBHARMONIC_RATIO * near(2 * f0[i])]
        out[fix] = 2 * f0[fix]
    return out


def crepe_f0(
    audio: np.ndarray, sr: int, fmin: float = 100.0, fmax: float = 2000.0, model: str = "full", periodicity: float = 0.21
) -> np.ndarray:
    """torchcrepe로 HOP_S 간격의 f0(Hz)를 구한다. 주기성이 낮은 프레임은 NaN. eval 의존성 그룹이 필요하다."""
    import torch
    import torchcrepe

    pitch, period = torchcrepe.predict(
        torch.tensor(audio, dtype=torch.float32)[None],
        sr,
        hop_length=round(sr * HOP_S),
        fmin=fmin,
        fmax=fmax,
        model=model,
        return_periodicity=True,
        batch_size=512,
    )
    period = torchcrepe.filter.median(period, 3)
    return torchcrepe.threshold.At(periodicity)(pitch, period)[0].numpy()


def lift_below_range(f0: np.ndarray, lowest_semitone: int) -> np.ndarray:
    """곡 최저음보다 200센트 넘게 낮은 값을 두 배로 고친다. 두 배로도 낮으면 무음(NaN)으로 둔다.

    대금을 한 옥타브 낮게 불거나 장단 소리 때문에 한 옥타브 아래로 읽힌 값을 곡 음역으로 돌린다(S15P31E102-848).
    200센트는 앱 실시간 판정이 악기가 낼 수 없는 높이로 보는 기준과 같다(S15P31E102-556).
    """
    floor = HWANG_HZ * 2 ** ((lowest_semitone - 2) / 12)
    lifted = np.where(f0 < floor, 2 * f0, f0)
    return np.where(lifted < floor, np.nan, lifted)


def frame_rms(audio: np.ndarray, sr: int) -> np.ndarray:
    """HOP_S 간격의 음량(RMS). 같은 높이 음의 경계를 찾는 데 쓴다. 20ms 창이라 짧게 끊은 소리도 보인다."""
    hop = round(sr * HOP_S)
    return librosa.feature.rms(y=audio, frame_length=2 * hop, hop_length=hop)[0]


def reference_hz(f0: np.ndarray) -> float:
    """기본음 녹음의 f0에서 기준음 주파수를 잰다."""
    voiced = f0[~np.isnan(f0)]
    if voiced.size == 0:
        raise ValueError("기본음 녹음에 소리가 없다")
    return float(np.median(voiced))


def _credit(error: float, tol: float) -> float:
    """허용 범위 안이면 1, 두 배에서 0이 되도록 줄인다."""
    return float(np.clip(2.0 - abs(error) / tol, 0.0, 1.0))


def combine(scores: Sequence[float | None], weights: Sequence[float]) -> int:
    """음표별 부분 점수를 항목 점수로 합친다. None은 뺀다. 합칠 음이 없으면 만점이다(시김새뿐인 음정).

    백엔드가 구간 점수를 같은 공식·순서로 계산한다(EVA-04). 곡 전체 합산이 응답 점수와 소수점 끝까지 같도록
    sum()(3.12부터 보정 합산)이나 np.mean 대신 차례로 더한다. round는 0.5에서 짝수 쪽으로 간다.
    """
    total = weight_sum = 0.0
    for value, weight in zip(scores, weights):
        if value is not None:
            total += value * weight
            weight_sum += weight
    return round(total / weight_sum) if weight_sum else 100


def total_score(pitch: int, timing: int, duration: int, weights: Mapping[str, float] = SCORE_WEIGHTS) -> int:
    """항목 점수를 총점으로 합친다. 백엔드가 구간 총점을 같은 순서로 계산한다(EVA-04)."""
    return round(pitch * weights["pitch"] + timing * weights["timing"] + duration * weights["duration"])


def score(
    f0: np.ndarray,
    notes: list[Note],
    bpm: float,
    ref_hz: float,
    tol: Tolerance = Tolerance(),
    rms: np.ndarray | None = None,
) -> ScoreResult:
    """rms(frame_rms)가 없으면 같은 높이로 이어지는 음의 시작은 정렬 구간의 처음으로 본다."""
    beat_s = 60.0 / bpm
    lengths = np.array([n.beats for n in notes]) * beat_s
    starts = np.concatenate([[0.0], np.cumsum(lengths)[:-1]])
    given = [n.start_beat is not None for n in notes]
    if any(given):
        if not all(given):
            raise ValueError("start_beat는 모든 음표에 있거나 모두 없어야 한다")
        # 녹음은 첫 음에서 시작하므로 첫 음 시작 박을 0으로 본다. 음 사이 빈 시간은 쉼이다.
        starts = (np.array([n.start_beat for n in notes]) - notes[0].start_beat) * beat_s
        if np.any(starts[1:] < starts[:-1] + lengths[:-1] - 1e-9):
            raise ValueError("음표가 앞 음표와 겹치거나 순서가 거꾸로다")
    expected_s = starts[-1] + lengths[-1]

    if np.all(np.isnan(f0)):
        return ScoreResult("FAILED", "NO_SOUND")
    if len(f0) * HOP_S < expected_s * 0.5:
        return ScoreResult("FAILED", "TOO_SHORT")

    detected = 1200.0 * np.log2(f0 / ref_hz)
    frame_t = np.arange(round(expected_s / HOP_S)) * HOP_S
    frame_note = np.searchsorted(starts, frame_t, side="right") - 1
    rest = frame_t >= starts[frame_note] + lengths[frame_note] - 1e-9
    frame_note[rest] = -1  # 쉼 프레임은 어느 음표에도 속하지 않는다
    expected = np.where(rest, np.nan, 100.0 * np.array([n.semitone for n in notes])[frame_note])

    path = _align(expected, detected)

    # 음표별로 정렬된 녹음 프레임 중 소리가 있는 가운데 60%를 음정 측정에 쓴다.
    measured = []
    for k in range(len(notes)):
        frames = np.unique(path[frame_note[path[:, 0]] == k, 1])
        voiced = frames[~np.isnan(detected[frames])]
        middle = voiced[int(len(voiced) * 0.2) : max(int(len(voiced) * 0.8), 1)]
        measured.append((frames, voiced, middle))

    diffs = [
        detected[mid] - 100.0 * n.semitone
        for n, (_, _, mid) in zip(notes, measured)
        if not n.sigimsae and mid.size
    ]
    offset = float(np.median(np.concatenate(diffs))) if diffs else 0.0
    # 보정 상한 + 음정 허용 범위가 곡에서 가장 가까운 두 음 간격보다 작아야 틀린 음을 이웃 음으로 맞춰 주지 않는다.
    # ponytail: 임시 식(여유 10센트). S15P31E102-158 정상 녹음의 보정량 분포로 다시 정한다.
    gaps = np.diff(np.unique([n.semitone for n in notes])) * 100.0
    max_tuning = min(tol.max_tuning_cent, gaps.min() - tol.pitch_cent - 10.0) if gaps.size else tol.max_tuning_cent
    out_of_range = abs(offset) > max_tuning
    applied = 0.0 if out_of_range else offset

    # 놓치지 않은 음: (k, err, onset_err, dur_err, octave). evaluation/score_experiment.py가 이 모양을 읽는다.
    # 길이 오차는 박자 판정에 쓰는 연주 빠르기를 구한 뒤에 채운다.
    played = []
    heard = {}  # k → (인식한 반음, 녹음 기준 시작 프레임)
    last = {}  # k → 정렬 구간의 마지막 소리 프레임
    for k, (n, (frames, voiced, middle)) in enumerate(zip(notes, measured)):
        if voiced.size < 0.3 * lengths[k] / HOP_S:
            continue
        raw = float(np.median(detected[middle]))
        err = raw - applied - 100.0 * n.semitone
        # 음이 바뀐 시점: 정렬 구간에서 앞 음보다 이 음에 더 가까워진 첫 프레임.
        # 떨림이 큰 시김새 음도 앞 음과는 반음 이상 떨어져 있어 시작을 잡을 수 있다.
        onset_frame = voiced[0]
        if k and notes[k - 1].semitone != n.semitone:
            here = np.abs(detected[voiced] - applied - 100.0 * n.semitone)
            before = np.abs(detected[voiced] - applied - 100.0 * notes[k - 1].semitone)
            closer = voiced[here < before]
            onset_frame = closer[0] if closer.size else voiced[0]
        elif k and rms is not None:
            # 앞 음과 높이가 같으면 높이로는 경계를 알 수 없다. 음높이 도구는 짧은 끊김을 무음으로
            # 잡지 못하므로, 음량이 크게 줄었다가 다시 올라온 첫 프레임을 시작으로 본다.
            # ponytail: 끊지 않고 이어 분 같은 음은 구분하지 못한다.
            seg = rms[frames[frames < len(rms)]]
            level = np.median(seg)
            dips = np.flatnonzero(seg < 0.3 * level)
            if dips.size:
                rise = np.flatnonzero((np.arange(seg.size) > dips[0]) & (seg >= 0.5 * level))
                onset_frame = frames[rise[0]] if rise.size else voiced[0]
        onset_err = onset_frame * HOP_S - starts[k]
        octave = abs(err) > 600 and abs(err - 1200 * round(err / 1200)) <= tol.pitch_cent
        played.append((k, err, onset_err, 0.0, octave))
        heard[k] = (round((raw - applied) / 100), int(onset_frame))
        last[k] = int(voiced[-1])

    # 박자는 곡 처음 기준이 아니라 앞뒤 음 기준으로 본다. 메트로놈 없이 빨라지거나 느려지면 늦음이 쌓이지만
    # 앞뒤 음도 같이 늦는다. 연주 전체의 빠르기 차이(늦음이 1초에 늘어나는 양 drift, 이웃 음 사이 기울기의 중앙값)를 빼고,
    # 그 음의 늦음과 앞뒤 _ONSET_WINDOW음(놓친 음 제외)으로 예상한 늦음의 중앙값의 차이(나머지)로 판정한다.
    # 허용은 onset_beat 박과 이 연주의 박자 흔들림 × onset_spread 중 큰 쪽이다. 흔들림은 이웃 음 사이 늦음 차이로 잰다
    # (MAD × 1.4826 ÷ √2, 정규분포면 음 하나의 표준편차). 나머지로 재면 붙은 음 몇 개가 함께 늦을 때 그 늦음이 이웃 음의
    # 나머지에도 번져 흔들림이 커지고 허용이 넓어져 모두 놓친다. 이웃 차이는 늦기 시작한 곳과 돌아온 곳 두 곳만 커진다.
    onsets = np.array([p[2] for p in played])
    times = starts[[p[0] for p in played]]
    drift = float(np.median(np.diff(onsets) / np.diff(times))) if len(onsets) > 1 else 0.0
    resid = np.array([
        o - np.median(np.delete(onsets[lo : i + _ONSET_WINDOW + 1] + drift * (t - times[lo : i + _ONSET_WINDOW + 1]), i - lo))
        if len(onsets) > 1 else 0.0
        for i, (o, t, lo) in enumerate(zip(onsets, times, (max(0, i - _ONSET_WINDOW) for i in range(len(onsets)))))
    ])
    steps = np.diff(onsets) - drift * np.diff(times)
    spread = 1.4826 * float(np.median(np.abs(steps - np.median(steps)))) / np.sqrt(2) if len(onsets) >= _SPREAD_MIN_NOTES else 0.0
    onset_tol_s = max(tol.onset_beat * beat_s, tol.onset_spread * spread)

    # 길이는 악보 BPM으로 계산한 길이가 아니라 이 음이 실제로 차지한 자리와 비교한다(S15P31E102-910).
    # 사람은 긴 음·짧은 음의 비율을 악보와 조금 다르게 불고 빠르기도 바뀌어, 악보 길이와 비교하면 정상 연주도 60점 안팎이었다.
    # 자리: 다음 음이 쉼 없이 이어지고 소리가 났으면 그 음의 실제 시작까지, 아니면(쉼 앞·끝 음·다음 음 놓침)
    # 악보 길이 × 이 연주의 빠르기(1 + drift). 울린 길이는 그 안(쉼 앞·끝 음은 마지막 소리까지)에서 소리가 잡힌 프레임 수다.
    # 중간을 비워 덜 분 만큼 줄고, 소리가 잠깐 흔들려 몇 프레임 빠져도 그만큼만 준다.
    slots = {}
    for i, (k, err, onset_err, _, octave) in enumerate(played):
        start = heard[k][1]
        nxt = heard.get(k + 1)
        if k + 1 < len(notes) and starts[k + 1] - starts[k] - lengths[k] < 1e-6 and nxt and nxt[1] > start:
            end, slots[k] = nxt[1], (nxt[1] - start) * HOP_S
        else:
            end, slots[k] = last[k] + 1, max(lengths[k] * (1.0 + drift), HOP_S)
        sounded = np.count_nonzero(~np.isnan(detected[start:end])) * HOP_S
        played[i] = (k, err, onset_err, sounded - slots[k], octave)

    # 길이 오류(LENGTH)는 측정을 믿을 수 있는 음만 판정한다(S15P31E102-912). 음높이 도구가 끊김 앞뒤 70ms쯤을 소리로
    # 이어 잡아 짧은 음은 덜 분 것이 보이지 않는다. 첫 음은 녹음 앞 소리 때문에 자리가 길게 잡히고, 같은 높이로 이어지는
    # 음은 경계를 음량으로만 찾아 자리가 틀어진다. 시김새 음은 꾸밈이라 길이가 악보와 다르게 나는 것이 보통이다.
    # 악보상 짧은 음은 다음 음이 늦게 들어와 자리만 길어져도 덜 분 것처럼 보이므로, 악보 길이(연주 빠르기 반영)도 함께 본다.
    length_judged = {
        k: k > 0 and not n.sigimsae and slots.get(k, 0.0) >= tol.length_error_min_s
        and lengths[k] * (1.0 + drift) >= tol.length_error_min_s
        and notes[k - 1].semitone != n.semitone and (k + 1 == len(notes) or notes[k + 1].semitone != n.semitone)
        for k, n in enumerate(notes)
    }

    results = []
    by_k = {p[0]: (p, r) for p, r in zip(played, resid)}
    for k, n in enumerate(notes):
        idx = k if n.idx is None else n.idx
        if k not in by_k:
            results.append(NoteResult(idx, "MISSED", pitch_score=None if n.sigimsae else 0.0))
            continue
        (_, err, onset_err, dur_err, octave), r = by_k[k]
        heard_semitone, onset_frame = heard[k]
        # 곡 처음 기준과 앞뒤 음 기준 중 작은 쪽으로 본다. 앞뒤 음의 측정 오차가 더해져 예전보다 엄격해지는 일을 막는다.
        r = min(r, onset_err, key=abs)
        if not n.sigimsae and abs(err) > tol.pitch_cent:
            verdict = "PITCH"
        elif abs(r) > onset_tol_s:
            verdict = "TIMING"
        elif length_judged[k] and abs(dur_err) > max(tol.length_error_ratio * slots[k], tol.duration_floor_s):
            verdict = "LENGTH"
        else:
            verdict = "CORRECT"
        # 높이가 틀린 음은 그 음을 연주한 것이 아니므로 박자·길이 점수도 주지 않는다(놓친 음과 같다).
        wrong = verdict == "PITCH"
        # onset_error_ms는 지금처럼 곡 처음 기준 늦음이다(백엔드 저장값의 뜻 유지). 판정만 나머지로 한다.
        results.append(NoteResult(idx, verdict, round(err, 1), round(onset_err * 1000), round(dur_err * 1000), octave,
                                  heard_semitone, round(onset_frame * HOP_S * 1000),
                                  None if n.sigimsae else 100 * _credit(err, tol.pitch_cent),
                                  0.0 if wrong else 100 * _credit(r, onset_tol_s),
                                  0.0 if wrong else 100 * _credit(dur_err, max(tol.duration_ratio * slots[k], tol.duration_floor_s))))

    # 잡음 몇 프레임만 잡히고 모든 음표가 빠졌으면 0점이 아니라 무음 실패로 본다.
    if all(r.verdict == "MISSED" for r in results):
        return ScoreResult("FAILED", "NO_SOUND")

    weights = [r.weight for r in results]
    pitch = combine([r.pitch_score for r in results], weights)
    timing = combine([r.timing_score for r in results], weights)
    duration = combine([r.duration_score for r in results], weights)
    return ScoreResult(
        "DONE",
        total=total_score(pitch, timing, duration),
        pitch=pitch,
        timing=timing,
        duration=duration,
        tuning_offset_cent=round(offset, 1),
        tuning_out_of_range=out_of_range,
        notes=results,
    )
