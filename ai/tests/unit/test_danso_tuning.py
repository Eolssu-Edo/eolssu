"""단소 단음 조율 흔들림 측정의 계산을 작은 f0 배열로 검사한다. 실제 녹음은 쓰지 않는다."""

import numpy as np
import pytest

from ai_service.pipelines.scoring import HWANG_HZ
from evaluation.danso_tuning import semitone_of, summarize


def tone(semitone, cents, frames=100):
    return np.full(frames, HWANG_HZ * 2 ** ((semitone * 100 + cents) / 1200))


def test_semitone_follows_register_and_pitch_table():
    assert semitone_of("평취", "중") == 5
    assert semitone_of("평취", "황") == 12  # 황·태는 평취에서 이미 청(옥타브 1)
    assert semitone_of("역취", "중") == 17
    assert semitone_of("역취", "태") == 26


def test_offset_is_median_of_middle_frames_and_residual_is_per_note():
    # 두 음 모두 +10센트, 세 번째 음만 +40센트. 보정량은 프레임 중앙값이라 +10이다.
    notes = [(5, tone(5, 10)), (7, tone(7, 10)), (10, tone(10, 40))]
    result = summarize(notes)
    assert result["offset"] == pytest.approx(10, abs=0.01)
    assert [round(n["residual"], 2) for n in result["notes"]] == [0.0, 0.0, 30.0]


def test_edges_unvoiced_and_octave_jumps_are_reported_not_measured():
    f0 = tone(12, 0)
    f0[:20] = 2 * f0[:20]  # 시작 20%가 한 옥타브 위로 잡힘(가운데 60% 밖)
    f0[95:] = np.nan
    note = summarize([(12, f0)])["notes"][0]
    assert note["cents"] == pytest.approx(0, abs=0.01)
    assert note["octave_ratio"] == pytest.approx(20 / 95)
    assert note["voiced"] == 95
