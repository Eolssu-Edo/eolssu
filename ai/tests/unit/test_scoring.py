"""합성 음계로 채점의 만점·오류 판정을 검사한다(계획 합격 기준 1~4)."""

import numpy as np
import pytest

from ai_service.pipelines.scoring import Note, frame_rms, pyin_f0, reference_hz, score
from evaluation.synthetic import BPM, REF_HZ, SCALE, SR, synth


def run(audio):
    return score(pyin_f0(audio, SR), SCALE, BPM, REF_HZ, rms=frame_rms(audio, SR))


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


def test_sigimsae_note_skips_pitch_and_keeps_onset():
    yoseong = (150, 6, -75)  # 폭 150센트, 6Hz, 중심 75센트 아래로 크게 떨기
    notes = [Note(n.semitone, n.beats, sigimsae=i in (3, 7)) for i, n in enumerate(SCALE)]
    audio = synth(notes, vibrato={3: yoseong, 7: yoseong})
    assert verdicts(score(pyin_f0(audio, SR), notes, BPM, REF_HZ)) == {}


def test_late_repeated_note_is_timing_error():
    notes = [Note(s, 1) for s in (0, 0, 2, 2, 5, 5, 7, 7, 5, 5, 0)]
    audio = synth(notes, gap=0.04, delay={3: 0.15})  # 같은 높이 두 번째 음이 150ms 늦음
    assert verdicts(score(pyin_f0(audio, SR), notes, BPM, REF_HZ, rms=frame_rms(audio, SR))) == {3: "TIMING"}


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
