"""합성 음계로 채점의 만점·오류 판정을 검사한다(계획 합격 기준 1~4)."""

from dataclasses import replace

import numpy as np
import pytest

from ai_service.pipelines.scoring import (
    HOP_S, HWANG_HZ, Note, combine, frame_rms, lift_below_range, pyin_f0, reference_hz, score, total_score,
)
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


def test_note_scores_rebuild_the_item_scores():
    # 음이 틀리고(PITCH) 빠진(MISSED) 연주도 음표별 값을 합치면 항목 점수와 정확히 같아야 한다.
    audio = synth(SCALE, shift={1: 2}, drop=(3,))
    result = run(audio)
    assert combine([n.pitch_score for n in result.notes], [n.weight for n in result.notes]) == result.pitch
    assert combine([n.timing_score for n in result.notes], [n.weight for n in result.notes]) == result.timing
    assert combine([n.duration_score for n in result.notes], [n.weight for n in result.notes]) == result.duration
    missed = next(n for n in result.notes if n.verdict == "MISSED")
    assert (missed.pitch_score, missed.timing_score, missed.duration_score) == (0.0, 0.0, 0.0)
    wrong = next(n for n in result.notes if n.verdict == "PITCH")
    assert wrong.timing_score == wrong.duration_score == 0.0


def test_shared_vector_keeps_the_rounding_of_note_by_note_addition():
    # 백엔드 SectionComparisonTests의 공통 벡터와 같은 값이다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
    # 세 항목 모두 평균이 x.5인데 차례 합의 끝자리 오차로 정확한 합·역순 합과 반올림이 갈린다(62, 40, 44가 나오면 틀렸다).
    pitch = [49.0, 90.0, 61.0, None, 58.3, 81.3, 96.8, 1.1]
    timing = [25.0, 48.9, 59.0, 42.7, 71.3, 34.1, 33.7, 9.3]
    duration = [70.8, 48.1, 62.0, 18.1, 15.1, 55.6, 65.6, 12.7]
    weights = [1.0] * 8
    assert [combine(part, weights) for part in (pitch, timing, duration)] == [63, 41, 43]
    # 52.5는 짝수 쪽으로 간다.
    assert total_score(63, 41, 43, {"pitch": 0.5, "timing": 0.25, "duration": 0.25}) == 52
    # 차례 합은 끝자리 오차로 4.500000000000001(5)이고 역순·보정 합산은 4.5(짝수 쪽 4)가 된다.
    assert total_score(6, 66, 1, {"pitch": 0.05, "timing": 0.05, "duration": 0.9}) == 5


def test_sigimsae_note_has_no_pitch_score_even_when_missed():
    notes = [replace(n, sigimsae=i == 3) for i, n in enumerate(SCALE)]
    audio = synth(SCALE, drop=(3,))
    result = score(pyin_f0(audio, SR), notes, BPM, REF_HZ, rms=frame_rms(audio, SR))
    assert result.notes[3].verdict == "MISSED" and result.notes[3].pitch_score is None
    assert result.notes[3].timing_score == 0.0
    assert combine([n.pitch_score for n in result.notes], [n.weight for n in result.notes]) == result.pitch


def test_combine_skips_null_scores_and_rounds_half_to_even():
    assert combine([None, 80.0, 90.0], [1.0, 1.0, 1.0]) == 85
    assert combine([100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0], [1.0] * 8) == 12  # 12.5는 짝수 쪽
    assert combine([None], [1.0]) == 100  # 음정을 볼 음이 없는 구간


def test_changed_notes_are_pitch_errors():
    assert verdicts(run(synth(SCALE, shift={3: 1, 7: -1}))) == {3: "PITCH", 7: "PITCH"}


def test_wrong_pitch_notes_get_no_timing_or_duration_credit():
    # 높이가 틀린 음은 제때·제 길이로 불어도 그 음을 연주한 것이 아니다. 틀린 곡이 박자·길이 부분 점수로 남지 않게 한다(S15P31E102-353).
    result = run(synth(SCALE, shift={3: 1, 7: -1}))
    expected = round(100 * (len(SCALE) - 2) / len(SCALE))
    assert (result.timing, result.duration) == (expected, expected)
    assert result.total == round((result.pitch + result.timing + result.duration) / 3)


def test_slower_playing_keeps_full_duration_score():
    # 길이는 악보 BPM의 길이가 아니라 다음 음이 실제로 시작할 때까지의 자리와 비교한다. 끝 음은 연주 빠르기로 늘린 악보 길이다.
    # 악보 길이와 비교하면 30% 느린 연주는 음마다 43% 길어 길이 점수가 30점 안팎이었다(S15P31E102-910).
    result = run(synth(SCALE, bpm=BPM * 0.7))
    assert verdicts(result) == {}
    assert result.duration == 100
    assert all(abs(n.duration_error_ms) <= 30 for n in result.notes)


def test_note_cut_short_loses_only_its_duration_score():
    # 4번 음 자리의 뒤 절반을 비운다. 박자는 그대로라 박자 오류가 아니고 그 음의 길이 점수만 깎인다.
    audio = synth(SCALE)
    beat = 60 / BPM
    env = np.ones(audio.size)
    env[int((4 + 0.5) * beat * SR) : int(5 * beat * SR)] = 0.0
    audio = audio * np.convolve(env, np.ones(160) / 160, mode="same")  # 10ms 페이드
    result = run(audio)
    assert verdicts(result) == {}
    cut = result.notes[4]
    assert cut.duration_error_ms < -120
    assert cut.duration_score < 80
    assert all(n.duration_score == 100 for n in result.notes if n.idx != 4)


def cut_tail(audio, k, bpm, keep=0.5):
    """k번 음 자리의 뒤쪽을 비운다(10ms 페이드). 그 음만 keep만큼 분 연주다."""
    beat = 60 / bpm
    env = np.ones(audio.size)
    env[int((k + keep) * beat * SR) : int((k + 1) * beat * SR)] = 0.0
    return audio * np.convolve(env, np.ones(160) / 160, mode="same")


def test_long_note_played_half_is_length_error():
    # 자리 0.67초인 음을 절반만 불면 길이 오류다(S15P31E102-912). 박자는 맞아 TIMING이 아니다.
    bpm = BPM * 0.75
    audio = cut_tail(synth(SCALE, bpm=bpm), 4, bpm)
    result = score(pyin_f0(audio, SR), SCALE, bpm, REF_HZ, rms=frame_rms(audio, SR))
    assert verdicts(result) == {4: "LENGTH"}
    assert result.notes[4].duration_score < 50


def test_length_error_skips_short_and_first_notes():
    # 자리 0.5초 음은 음높이 도구의 끊김 오차(약 70ms)에 묻혀 판정하지 않는다. 첫 음은 녹음 앞 소리로 자리가 틀어져 뺀다.
    short = cut_tail(synth(SCALE), 4, BPM)
    assert verdicts(run(short)) == {}
    bpm = BPM * 0.75
    first = cut_tail(synth(SCALE, bpm=bpm), 0, bpm)
    assert "LENGTH" not in verdicts(score(pyin_f0(first, SR), SCALE, bpm, REF_HZ, rms=frame_rms(first, SR))).values()


def test_late_note_is_timing_error():
    assert verdicts(run(synth(SCALE, delay={4: 0.15}))) == {4: "TIMING"}


def test_gradually_slower_playing_is_not_timing_error():
    # 메트로놈 없이 분 입문자처럼 전체가 점점 늦어지면 음마다 늦음이 쌓인다. 앞뒤 음과 같이 늦으면 박자 오류가 아니다(S15P31E102-158).
    assert verdicts(run(synth(SCALE, bpm=BPM * 0.85))) == {}


def test_several_late_notes_in_a_row_are_timing_errors():
    # 박이 고른 연주에서 붙은 음 몇 개가 함께 늦었다가 돌아오면 각각 박자 오류다. 늦음이 흔들림 값을 키워 허용을 넓히면 안 된다.
    assert verdicts(run(synth(SCALE, delay={3: 0.15, 4: 0.2, 5: 0.3}))) == {3: "TIMING", 4: "TIMING", 5: "TIMING"}


def test_whole_section_shifted_late_flags_only_its_first_note():
    # 한 번 밀린 박이 끝까지 가면(쉼을 건너뛴 연주 등) 밀리기 시작한 음만 박자 오류다. 뒤는 새 박에 맞춰 분 것으로 본다.
    assert verdicts(run(synth(SCALE, delay={i: 0.3 for i in range(4, 11)}))) == {4: "TIMING"}


def test_late_note_next_to_missed_note_is_still_timing_error():
    # 놓친 음은 앞뒤 음 기준을 구할 때 빠진다.
    # 바로 다음 음을 늦추면 합성음에서 비운 음의 높이가 늦춘 만큼 이어져 놓침이 아니게 되므로 한 칸 띄운다.
    assert verdicts(run(synth(SCALE, drop=(3,), delay={5: 0.15}))) == {3: "MISSED", 5: "TIMING"}


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
    result = score(pyin_f0(audio, SR), notes, BPM, REF_HZ, rms=frame_rms(audio, SR))
    assert verdicts(result) == {3: "TIMING"}
    # 높이로 경계를 못 찾는 같은 높이 음도 녹음 기준 시작(3박 × 0.5초 + 0.15초)을 음량으로 잡는다.
    assert abs(result.notes[3].recording_offset_ms - 1650) <= 30


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


def test_detected_semitone_is_played_pitch_after_applied_tuning():
    # S15P31E102-482 D-1: 채점에 적용한 조율 보정을 뺀 뒤 가장 가까운 반음. 60센트 낮게 분 곡은 보정(-60)을 빼면 정답 반음이다.
    expected = [n.semitone for n in SCALE]
    flat = score(f0_frames(SCALE) * 2 ** (-60 / 1200), SCALE, BPM, REF_HZ)
    assert not flat.tuning_out_of_range
    assert [n.detected_semitone for n in flat.notes] == expected
    assert all(type(n.detected_semitone) is int and type(n.recording_offset_ms) is int for n in flat.notes)  # BE는 Int로 읽는다
    # 상한을 넘은 어긋남(-120센트)은 보정하지 않으므로 그대로 반올림한 반음(한 반음 아래)이다.
    far = score(f0_frames(SCALE) * 2 ** (-120 / 1200), SCALE, BPM, REF_HZ)
    assert far.tuning_out_of_range
    assert [n.detected_semitone for n in far.notes] == [s - 1 for s in expected]
    # 옥타브 위로 분 곡은 정답보다 12 큰 반음이다.
    assert [n.detected_semitone for n in score(f0_frames(SCALE, shift=12), SCALE, BPM, REF_HZ).notes] == [s + 12 for s in expected]


def test_recording_offset_counts_from_the_start_of_the_recording():
    # D-2: 업로드 녹음 0ms 기준 실제 소리 시작. 앞에 0.5초 무음을 붙이면 모든 음이 500ms 늦게 잡힌다.
    starts = [round(1000 * 60 / BPM * k) for k in range(len(SCALE))]
    assert [n.recording_offset_ms for n in score(f0_frames(SCALE), SCALE, BPM, REF_HZ).notes] == starts
    lead = np.concatenate([np.full(50, np.nan), f0_frames(SCALE)])
    assert [n.recording_offset_ms for n in score(lead, SCALE, BPM, REF_HZ).notes] == [s + 500 for s in starts]


def test_missed_note_has_no_detected_pitch_or_offset():
    result = score(f0_frames(SCALE, played=set(range(len(SCALE))) - {5}), SCALE, BPM, REF_HZ)
    assert result.notes[5].verdict == "MISSED"
    assert (result.notes[5].detected_semitone, result.notes[5].recording_offset_ms) == (None, None)
    assert all(n.detected_semitone is not None and n.recording_offset_ms is not None for n in result.notes if n.idx != 5)


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
    # 쉼을 빼고 이어 붙이면 쉼 뒤 음의 기대 시각이 한 박(0.5초) 이상 앞당겨져 onset_error_ms가 그만큼 커진다.
    # 판정은 앞뒤 음 기준이라 밀림이 이웃에 얼마나 있느냐에 따라 달라진다(S15P31E102-158). 한 번 밀린 경우는
    # test_whole_section_shifted_late_flags_only_its_first_note가 본다.
    plain = [Note(n.semitone, n.beats) for n in RESTED]
    result = score_rested(synth(RESTED), plain)
    assert all(n.onset_error_ms >= 450 for n in result.notes[2:])


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


def test_values_far_below_the_song_are_doubled():
    # 곡 최저음 반음 12(청황 약 622Hz)의 200센트 아래(약 554Hz)보다 낮으면 악기가 낼 수 없는 높이로 보고 두 배로 고친다.
    floor = HWANG_HZ * 2 ** (10 / 12)
    f0 = np.array([np.nan, 311.13, 392.0, floor * 0.99, floor * 1.01, 700.0, 150.0])
    lifted = lift_below_range(f0, lowest_semitone=12)
    assert np.isnan(lifted[0])
    assert lifted[1:4] == pytest.approx([622.26, 784.0, floor * 1.98])
    assert lifted[4:6] == pytest.approx([floor * 1.01, 700.0])  # 하한 이상은 그대로
    assert np.isnan(lifted[6])  # 두 배(300Hz)로도 하한 아래면 무음
    assert f0[1] == 311.13  # 입력 배열은 바꾸지 않는다
