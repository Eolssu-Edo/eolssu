from fractions import Fraction
import json
from pathlib import Path
import unittest

from data.src.audio.jangdan_audio import rhythm_plan


class JangdanAudioTest(unittest.TestCase):
    def test_catalog_cycles_and_compound_strokes(self):
        reference = Path(__file__).resolve().parents[1] / "reference" / "jangdans.json"
        rows = json.loads(reference.read_text(encoding="utf-8"))["rows"]
        plans = [rhythm_plan(row) for row in rows]
        self.assertEqual(
            [Fraction(value) for value in (
                # S15P31E102-1073에서 낮춘 기본 BPM 기준이다. 배포된 미리듣기 음원은 아직 이전 BPM으로 만든 것이다.
                "180/77", "240/53", "72/7", "240/77", "15/4", "25/6", "5/2", "9", "9", "18", "5", "15/8",
            )],
            [duration for duration, _ in plans],
        )
        self.assertEqual(
            [(Fraction(24, 7), "덕"), (Fraction(57, 14), "기")],
            [(time, stroke) for time, stroke, _ in plans[2][1] if Fraction(24, 7) <= time < Fraction(30, 7)],
        )
        self.assertEqual(plans[7], plans[8])

    def test_duration_comes_from_catalog_field_not_name(self):
        row = {"name": "이름에 의존하지 않는 패턴", "bpm_unit": "QUARTER", "default_bpm": 60,
               "pattern_cell_eighths": 2, "pattern": {"beats": ["덩", "덕", "궁", "덕"], "group": [1, 1, 1, 1]}}
        duration, events = rhythm_plan(row)
        self.assertEqual(duration, 4)
        self.assertEqual([at for at, _, _ in events], [0, 1, 2, 3])
        row["pattern_cell_eighths"] = 1
        self.assertEqual(rhythm_plan(row)[0], 2)
