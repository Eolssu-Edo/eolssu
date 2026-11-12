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
                "15/8", "40/11", "90/11", "5/2", "3", "10/3", "2", "15/2", "15/2", "100/7", "4", "3/2",
            )],
            [duration for duration, _ in plans],
        )
        self.assertEqual(
            [(Fraction(30, 11), "덕"), (Fraction(285, 88), "기")],
            [(time, stroke) for time, stroke, _ in plans[2][1] if Fraction(30, 11) <= time < Fraction(75, 22)],
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
