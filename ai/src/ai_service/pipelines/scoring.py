"""악보 기반 대금 연주 채점.

녹음의 음높이 곡선(f0)과 정답 음표를 DTW로 맞춘 뒤 음표마다 음정·박자·길이를 판정한다.
음높이 단위는 기본음 녹음에서 잰 기준음 대비 센트다.
"""

from dataclasses import dataclass, field

import librosa
import numpy as np

HOP_S = 0.01  # f0 프레임 간격(초)
_UNVOICED_COST = 300.0  # 소리 유무가 어긋난 프레임의 DTW 비용(센트)
_STRETCH_COST = 50.0  # DTW가 음표를 늘이거나 줄일 때 프레임마다 더하는 비용


@dataclass(frozen=True)
class Note:
    semitone: int  # 기준음에서 몇 반음 위인지
    beats: float
    sigimsae: bool = False  # True면 음정 판정에서 제외


@dataclass(frozen=True)
class Tolerance:
    """허용 범위. 기본값은 녹음으로 검증하기 전의 제안 값이다."""

    pitch_cent: float = 50.0
    onset_beat: float = 0.125
    duration_ratio: float = 0.25
    max_tuning_cent: float = 100.0


@dataclass
class NoteResult:
    idx: int  # 정답 음표 순번
    verdict: str  # CORRECT, PITCH, TIMING, MISSED
    pitch_error_cent: float | None = None
    onset_error_ms: float | None = None
    duration_error_ms: float | None = None
    octave_suspect: bool = False  # 정답과 옥타브 단위로 어긋남. 음높이 도구 오류일 수 있다


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
    return np.where(voiced, f0, np.nan)


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
    expected_s = starts[-1] + lengths[-1]

    if np.all(np.isnan(f0)):
        return ScoreResult("FAILED", "NO_SOUND")
    if len(f0) * HOP_S < expected_s * 0.5:
        return ScoreResult("FAILED", "TOO_SHORT")

    detected = 1200.0 * np.log2(f0 / ref_hz)
    frame_note = np.searchsorted(starts, np.arange(round(expected_s / HOP_S)) * HOP_S, side="right") - 1
    expected = 100.0 * np.array([n.semitone for n in notes])[frame_note]

    # ponytail: 전체 DTW라 메모리가 프레임 수의 제곱. 1분을 넘는 녹음은 band 제약을 넣는다.
    cost = np.abs(expected[:, None] - detected[None, :])
    cost[np.isnan(cost)] = _UNVOICED_COST
    _, path = librosa.sequence.dtw(
        C=cost,
        step_sizes_sigma=np.array([[1, 1], [0, 1], [1, 0]]),
        weights_add=np.array([0.0, _STRETCH_COST, _STRETCH_COST]),
    )

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
    out_of_range = abs(offset) > tol.max_tuning_cent
    applied = 0.0 if out_of_range else offset

    results, pitch_c, timing_c, duration_c = [], [], [], []
    onset_tol_s = tol.onset_beat * beat_s
    for k, (n, (frames, voiced, middle)) in enumerate(zip(notes, measured)):
        if voiced.size < 0.3 * lengths[k] / HOP_S:
            results.append(NoteResult(k, "MISSED"))
            timing_c.append(0.0)
            duration_c.append(0.0)
            if not n.sigimsae:
                pitch_c.append(0.0)
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
        dur_err = (voiced[-1] - onset_frame + 1) * HOP_S - lengths[k]
        octave = abs(err) > 600 and abs(err - 1200 * round(err / 1200)) <= tol.pitch_cent

        if not n.sigimsae and abs(err) > tol.pitch_cent:
            verdict = "PITCH"
        elif abs(onset_err) > onset_tol_s:
            verdict = "TIMING"
        else:
            verdict = "CORRECT"
        results.append(NoteResult(k, verdict, round(err, 1), round(onset_err * 1000), round(dur_err * 1000), octave))
        if not n.sigimsae:
            pitch_c.append(_credit(err, tol.pitch_cent))
        timing_c.append(_credit(onset_err, onset_tol_s))
        duration_c.append(_credit(dur_err, tol.duration_ratio * lengths[k]))

    # 잡음 몇 프레임만 잡히고 모든 음표가 빠졌으면 0점이 아니라 무음 실패로 본다.
    if all(r.verdict == "MISSED" for r in results):
        return ScoreResult("FAILED", "NO_SOUND")

    pitch, timing, duration = (round(100 * float(np.mean(c))) if c else 100 for c in (pitch_c, timing_c, duration_c))
    return ScoreResult(
        "DONE",
        total=round((pitch + timing + duration) / 3),
        pitch=pitch,
        timing=timing,
        duration=duration,
        tuning_offset_cent=round(offset, 1),
        tuning_out_of_range=out_of_range,
        notes=results,
    )
