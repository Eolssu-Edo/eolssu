"""합성 음계로 채점의 만점·오류 판정을 검사한다(계획 합격 기준 1~4)."""

import numpy as np
import pytest

from ai_service.pipelines.scoring import Note, pyin_f0, reference_hz, score

SR = 16000
BPM = 120
REF_HZ = 311.13  # 합성용 기준음. 실제 기준음은 녹음마다 기본음에서 잰다.
# 기준음에서의 반음 수로 적은 오르내리는 음계
SCALE = [Note(s, 1) for s in (0, 2, 5, 7, 9, 12, 9, 7, 5, 2, 0)]


def synth(notes, cents=0.0, shift=None, delay=None, drop=(), seconds=None):
    """음표를 이어 부는 합성음. shift는 반음 변경, delay는 늦게 시작할 초, drop은 비운 음표."""
    shift, delay = shift or {}, delay or {}
    beat = 60 / BPM
    starts = np.concatenate([[0.0], np.cumsum([n.beats * beat for n in notes])])
    t = np.arange(int(SR * (seconds if seconds is not None else starts[-1]))) / SR
    k = np.clip(np.searchsorted(starts[:-1] + [delay.get(i, 0.0) for i in range(len(notes))], t, side="right") - 1, 0, None)
    semis = np.array([n.semitone + shift.get(i, 0) for i, n in enumerate(notes)], dtype=float)
    freq = REF_HZ * 2 ** ((100 * semis[k] + cents) / 1200)
    amp = np.ones_like(t)
    for i in drop:
        amp[(t >= starts[i]) & (t < starts[i + 1])] = 0.0
    amp = np.convolve(amp, np.ones(160) / 160, mode="same")  # 10ms 페이드로 끊김 잡음 제거
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return 0.3 * amp * (np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.25 * np.sin(3 * phase))


def run(audio):
    return score(pyin_f0(audio, SR), SCALE, BPM, REF_HZ)


def verdicts(result):
    return {n.idx: n.verdict for n in result.notes if n.verdict != "CORRECT"}


def test_reference_from_basic_tone():
    tone = synth([Note(0, 4)])
    assert reference_hz(pyin_f0(tone, SR)) == pytest.approx(REF_HZ, rel=0.003)


def test_perfect_scale_scores_100():
    result = run(synth(SCALE))
    assert result.status == "DONE"
    assert verdicts(result) == {}
    assert result.total == 100


def test_changed_notes_are_pitch_errors():
    assert verdicts(run(synth(SCALE, shift={3: 1, 7: -1}))) == {3: "PITCH", 7: "PITCH"}


def test_late_note_is_timing_error():
    assert verdicts(run(synth(SCALE, delay={4: 0.15}))) == {4: "TIMING"}


def test_skipped_note_is_missed():
    assert verdicts(run(synth(SCALE, drop=(6,)))) == {6: "MISSED"}


def test_small_tuning_offset_is_corrected():
    result = run(synth(SCALE, cents=-30))
    assert result.tuning_offset_cent == pytest.approx(-30, abs=5)
    assert result.total == 100


def test_large_tuning_offset_is_not_corrected():
    result = run(synth(SCALE, cents=-120))
    assert result.tuning_out_of_range
    assert result.total < 100


def test_silence_fails_instead_of_zero():
    result = run(np.zeros(SR * 6))
    assert (result.status, result.fail_reason) == ("FAILED", "NO_SOUND")


def test_short_input_fails():
    result = run(synth(SCALE, seconds=1.0))
    assert (result.status, result.fail_reason) == ("FAILED", "TOO_SHORT")
