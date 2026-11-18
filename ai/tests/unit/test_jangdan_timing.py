"""Catalog timing regressions without PDF/model dependencies."""
from fractions import Fraction

import pytest

from omr_service.jeongganbo_text import JANGDANS, beats_per_jeonggan, gak_cycles, usual_beats


def test_basic_four_beat_is_not_danmori_pattern():
    assert JANGDANS["4박 기본 반주"]["pattern"]["beats"] == ["덩", "덕", "궁", "덕"]
    assert len(JANGDANS["단모리"]["pattern"]["beats"]) == 8
    assert beats_per_jeonggan("4박 기본 반주", 4) == Fraction(1)
    assert beats_per_jeonggan("단모리", 4) == Fraction(1)
    assert beats_per_jeonggan("세령산", 10) == Fraction(3, 2)


def test_every_registered_pattern_has_explicit_positive_cell_duration():
    assert len(JANGDANS) == 12
    for row in JANGDANS.values():
        assert row["pattern_cell_eighths"] in (1, 2, 3)
        assert beats_per_jeonggan(row["name"], len(row["pattern"]["beats"])) > 0


@pytest.mark.parametrize("jangdan, per_gak, cycles, beats", [
    # 제공 곡(jangdan-playback.json)의 ticksPerJeonggan/ticksPerBeat와 같은 정간당 박(S15P31E102-1076)
    ("세마치", 12, 4, Fraction(1)),  # 아리랑·진도아리랑 12정간 각
    ("굿거리", 8, 2, Fraction(1)),  # 박연폭포 8정간 각
    ("자진모리", 8, 2, Fraction(1)),
    ("세마치", 6, 2, Fraction(1)),
    # 장단 한 번인 기존 각은 그대로다
    ("세마치", 3, 1, Fraction(1)), ("세마치", 9, 1, Fraction(1, 3)), ("굿거리", 4, 1, Fraction(1)),
    ("굿거리", 12, 1, Fraction(1, 3)), ("자진모리", 12, 1, Fraction(1, 3)), ("중모리", 12, 1, Fraction(1)),
    ("타령", 12, 1, Fraction(1, 2)), ("단모리", 8, 1, Fraction(1, 2)), ("4박 기본 반주", 12, 1, Fraction(1, 3)),
    ("세령산", 10, 1, Fraction(3, 2)),
])
def test_gak_with_several_cycles_keeps_cycle_beat(jangdan, per_gak, cycles, beats):
    assert gak_cycles(jangdan, per_gak) == cycles
    assert beats_per_jeonggan(jangdan, per_gak) == beats
    assert usual_beats(jangdan, beats)


@pytest.mark.parametrize("jangdan, per_gak, beats", [("세마치", 2, Fraction(3, 2)), ("굿거리", 5, Fraction(4, 5))])
def test_gak_matching_no_cycle_count_stays_one_cycle_and_is_unusual(jangdan, per_gak, beats):
    assert gak_cycles(jangdan, per_gak) == 1
    assert beats_per_jeonggan(jangdan, per_gak) == beats
    assert not usual_beats(jangdan, beats)
