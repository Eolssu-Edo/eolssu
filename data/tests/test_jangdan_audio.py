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
                "15/8", "40/11", "90/11", "5/2", "3", "10/3", "2", "15/2", "15/2", "100/7",
            )],
            [duration for duration, _ in plans],
        )
        self.assertEqual(
            [(Fraction(30, 11), "덕"), (Fraction(285, 88), "기")],
            [(time, stroke) for time, stroke, _ in plans[2][1] if Fraction(30, 11) <= time < Fraction(75, 22)],
        )
        self.assertEqual(plans[7], plans[8])
