"""합성 음계로 채점의 만점·오류 판정을 검사한다(계획 합격 기준 1~4)."""

import numpy as np
import pytest

from ai_service.pipelines.scoring import HOP_S, HWANG_HZ, Note, frame_rms, pyin_f0, reference_hz, score
from evaluation.synthetic import BPM, REF_HZ, SCALE, SR, synth


def run(audio):
    return score(pyin_f0(audio, SR), SCALE, BPM, REF_HZ, rms=frame_rms(audio, SR))


def verdicts(result):
    return {n.idx: n.verdict for n in result.notes if n.verdict != "CORRECT"}


def test_reference_from_basic_tone():
    tone = synth([Note(0, 4)])
    assert reference_hz(pyin_f0(tone, SR)) == pytest.approx(REF_HZ, rel=0.003)


def test_semitone_0_is_hwang_e_flat_4():
    assert HWANG_HZ == pytest.approx(440 * 2 ** (-6 / 12), abs=0.01)  # E♭4는 A4에서 6반음 아래
    # 합성 도구를 거치지 않은 415.30Hz(A♭4) 순음은 율명 표의 중(반음 5)이다.
    tone = 0.3 * np.sin(2 * np.pi * 415.30 * np.arange(SR * 2) / SR)
    result = score(pyin_f0(tone, SR), [Note(5, 4)], BPM, HWANG_HZ)
    assert result.notes[0].verdict == "CORRECT"
    assert abs(result.notes[0].pitch_error_cent) < 5


def test_beat_unit_only_rescales_bpm():
    # beats와 bpm은 같은 박 단위면 된다. 한 박을 셋으로 나눠 적고 bpm을 세 배로 해도 기대 시각은 같다.
    # 허용 범위 onset_beat는 박 단위라 박을 쪼개면 좁아진다(판정은 같지 않을 수 있다).
    audio = synth(SCALE, delay={4: 0.05})
    f0, rms = pyin_f0(audio, SR), frame_rms(audio, SR)
    thirds = [Note(n.semitone, n.beats * 3) for n in SCALE]
    a, b = score(f0, SCALE, BPM, REF_HZ, rms=rms), score(f0, thirds, BPM * 3, REF_HZ, rms=rms)
    assert [n.onset_error_ms for n in a.notes] == [n.onset_error_ms for n in b.notes]
    assert (a.notes[4].verdict, b.notes[4].verdict) == ("CORRECT", "TIMING")


def test_perfect_scale_scores_100():
    result = run(synth(SCALE))
    assert result.status == "DONE"
    assert verdicts(result) == {}
    assert result.total == 100


def test_changed_notes_are_pitch_errors():
    assert verdicts(run(synth(SCALE, shift={3: 1, 7: -1}))) == {3: "PITCH", 7: "PITCH"}


def test_late_note_is_timing_error():
    assert verdicts(run(synth(SCALE, delay={4: 0.15}))) == {4: "TIMING"}


def test_short_octave_up_between_lower_notes_is_octave_error():
    # 앞뒤 음 사이에 짧게 낀 옥타브 위 음을 pYIN이 원래 옥타브로 잡던 문제(S15P31E102-251).
    notes = [Note(10, 1.25), Note(12, 0.5), Note(14, 1.25)]
    audio = synth(notes, shift={1: 12}, gap=0.04)
    result = score(pyin_f0(audio, SR), notes, BPM, REF_HZ, rms=frame_rms(audio, SR))
    assert (result.notes[1].verdict, result.notes[1].octave_suspect) == ("PITCH", True)


def test_octave_error_frames_stay_with_their_note():
    # 정렬이 옥타브 어긋난 음을 한 프레임으로 줄이고 이웃 음에 넘기던 아리랑 구간(S15P31E102-251).
    notes = [Note(12, 1 / 3), Note(10, 2 / 3), Note(12, 1 / 3), Note(14, 1), Note(12, 1 / 3)]
    audio = synth(notes, shift={2: 12}, gap=0.04, bpm=96)
    result = score(pyin_f0(audio, SR), notes, 96, REF_HZ, rms=frame_rms(audio, SR))
    assert verdicts(result) == {2: "PITCH"}
    assert result.notes[2].octave_suspect


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


NEIGHBORS = [Note(s, 1) for s in (0, 1, 3, 5, 7, 5, 3, 1, 0)]  # 반음 이웃이 있어 상한이 100 - 50 - 10 = 40센트


def score_neighbors(audio):
    return score(pyin_f0(audio, SR), NEIGHBORS, BPM, REF_HZ, rms=frame_rms(audio, SR))


def test_tuning_limit_shrinks_with_semitone_neighbors():
    # 상한을 넘는 어긋남은 보정하지 않고 조율 범위 초과로 알린다.
    assert score_neighbors(synth(NEIGHBORS, cents=-60)).tuning_out_of_range


def test_whole_song_a_semitone_off_is_not_corrected_to_correct():
    # 곡 전체를 반음 높게(5센트 낮게) 불면 보정량이 95센트로 잡힌다. 상한이 100이면 이를 빼 주어 모든 음이 맞게 된다.
    result = score_neighbors(synth(NEIGHBORS, shift={i: 1 for i in range(len(NEIGHBORS))}, cents=-5))
    assert result.tuning_out_of_range
    assert all(n.verdict == "PITCH" for n in result.notes)


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


def test_synth_makes_no_rest_from_rounding_of_thirds():
    # 1/3박 음을 이어 적으면 시작 박과 앞 음 끝이 소수점 오차만큼 다르다. 이것을 쉼으로 보면 늦게 분 음 앞이 끊긴다.
    plain = [Note((0, 2, 5)[i % 3], 1 / 3) for i in range(24)]
    timed = [Note(n.semitone, n.beats, start_beat=i / 3) for i, n in enumerate(plain)]
    a, b = synth(timed, delay={5: 0.1}), synth(plain, delay={5: 0.1})
    n = min(a.size, b.size)  # 길이와 끝 페이드는 반올림으로 한 표본쯤 다를 수 있다
    assert np.abs(a[:n] - b[:n]).max() < 0.01  # 가짜 쉼이 생기면 소리 크기(0.3)만큼 차이 난다


def test_mixed_or_overlapping_start_beats_are_rejected():
    f0 = f0_frames(SCALE)
    with pytest.raises(ValueError):
        score(f0, [Note(0, 1, start_beat=0), Note(2, 1)], BPM, REF_HZ)
    with pytest.raises(ValueError):
        score(f0, [Note(0, 2, start_beat=0), Note(2, 1, start_beat=1)], BPM, REF_HZ)
