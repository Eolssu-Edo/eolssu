"""합성 음계로 채점의 만점·오류 판정을 검사한다(계획 합격 기준 1~4)."""

import numpy as np
import pytest

from ai_service.pipelines.scoring import HOP_S, Note, frame_rms, pyin_f0, reference_hz, score
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


def test_tuning_limit_stays_100_for_pentatonic_song():
    # 다섯 음 음계는 가장 가까운 두 음이 200센트라 상한이 100 그대로다.
    result = run(synth(SCALE, cents=-80))
    assert not result.tuning_out_of_range
    assert result.total == 100


def test_tuning_limit_shrinks_with_semitone_neighbors():
    # 반음 이웃이 있으면 상한은 100 - 50 - 10 = 40센트. 60센트 어긋남을 보정하면 이웃 음을 잘못 분 것도 맞게 된다.
    notes = [Note(s, 1) for s in (0, 1, 3, 5, 7, 5, 3, 1, 0)]
    audio = synth(notes, cents=-60)
    result = score(pyin_f0(audio, SR), notes, BPM, REF_HZ, rms=frame_rms(audio, SR))
    assert result.tuning_out_of_range


def test_silence_fails_instead_of_zero():
    result = run(np.zeros(SR * 6))
    assert (result.status, result.fail_reason) == ("FAILED", "NO_SOUND")


def f0_frames(notes, shift=0, played=None):
    """음표마다 정확한 높이를 HOP_S 간격으로 채운 f0. played에 없는 음표 구간은 무음(NaN)."""
    frames = []
    for k, n in enumerate(notes):
        hz = REF_HZ * 2 ** ((n.semitone + shift) / 12) if played is None or k in played else np.nan
        frames += [hz] * round(n.beats * 60 / BPM / HOP_S)
    return np.array(frames)


def test_few_noise_frames_fail_as_no_sound():
    f0 = f0_frames(SCALE, played=())
    f0[[40, 41, 42, 300, 301]] = REF_HZ * 1.5  # 잡음이 음높이로 잡힌 몇 프레임
    result = score(f0, SCALE, BPM, REF_HZ)
    assert (result.status, result.fail_reason) == ("FAILED", "NO_SOUND")


def test_one_played_note_is_not_a_failure():
    result = score(f0_frames(SCALE, played={5}), SCALE, BPM, REF_HZ)
    assert result.status == "DONE"
    assert verdicts(result) == {k: "MISSED" for k in range(len(SCALE)) if k != 5}


def test_all_wrong_notes_are_pitch_errors_not_failure():
    # 한 옥타브 높게 불면 조율 보정 범위(±100센트)를 넘어 보정 없이 모든 음표가 음정 오류로 남는다.
    result = score(f0_frames(SCALE, shift=12), SCALE, BPM, REF_HZ)
    assert (result.status, result.fail_reason) == ("DONE", None)
    assert verdicts(result) == {k: "PITCH" for k in range(len(SCALE))}


def test_short_input_fails():
    result = run(synth(SCALE, seconds=1.0))
    assert (result.status, result.fail_reason) == ("FAILED", "TOO_SHORT")


# 12박째와 15박째에 한 박씩 쉬는 연습 구간. 곡 중간에서 시작해 음 번호는 5부터다.
RESTED = [
    Note(s, 1, idx=5 + i, start_beat=b)
    for i, (s, b) in enumerate(zip((0, 2, 5, 7, 5, 2), (10, 11, 13, 14, 16, 17)))
]


def score_rested(audio, notes=RESTED):
    return score(pyin_f0(audio, SR), notes, BPM, REF_HZ, rms=frame_rms(audio, SR))


def test_rests_keep_expected_onsets_and_request_idx():
    result = score_rested(synth(RESTED))
    assert verdicts(result) == {}
    assert [n.idx for n in result.notes] == [5, 6, 7, 8, 9, 10]


def test_without_start_beat_notes_after_rest_look_early():
    # 쉼을 빼고 이어 붙이면 쉼 뒤 음의 기대 시각이 한 박(0.5초) 이상 앞당겨진다.
    plain = [Note(n.semitone, n.beats) for n in RESTED]
    assert verdicts(score_rested(synth(RESTED), plain)) == {k: "TIMING" for k in (2, 3, 4, 5)}


def test_late_note_after_rest_is_timing_error_with_request_idx():
    assert verdicts(score_rested(synth(RESTED, delay={2: 0.15}))) == {7: "TIMING"}


@pytest.mark.parametrize("delay", [-0.4, 0.2])
def test_early_or_late_note_after_long_rest_is_timing_error(delay):
    # 네 박 쉰 뒤의 음. 쉼에 소리가 없는 것을 맞다고 보지 않으면 일찍 분 음은 정답, 늦게 분 음은 누락이 된다.
    notes = [Note(s, 1, start_beat=b) for s, b in zip((0, 2, 5, 7), (0, 1, 6, 7))]
    assert verdicts(score_rested(synth(notes, delay={2: delay}), notes)) == {2: "TIMING"}


def test_mixed_or_overlapping_start_beats_are_rejected():
    f0 = f0_frames(SCALE)
    with pytest.raises(ValueError):
        score(f0, [Note(0, 1, start_beat=0), Note(2, 1)], BPM, REF_HZ)
    with pytest.raises(ValueError):
        score(f0, [Note(0, 2, start_beat=0), Note(2, 1, start_beat=1)], BPM, REF_HZ)
