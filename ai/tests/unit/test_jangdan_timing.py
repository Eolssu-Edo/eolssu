"""Catalog timing regressions without PDF/model dependencies."""
from fractions import Fraction

from omr_service.jeongganbo_text import JANGDANS, beats_per_jeonggan


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
