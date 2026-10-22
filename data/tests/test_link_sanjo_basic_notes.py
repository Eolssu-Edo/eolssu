import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/"src"))
from link_sanjo_basic_notes import align_to_hypothesis, group_repeated_regions


class NoteLinkTests(unittest.TestCase):
    def test_short_interruption_groups_same_pitch_but_keeps_next_note(self):
        rows = [{"region_id": "a", "start_seconds": 0, "end_seconds": 3, "median_hz": 320},
                {"region_id": "b", "start_seconds": 3.3, "end_seconds": 3.8, "median_hz": 322},
                {"region_id": "c", "start_seconds": 4, "end_seconds": 6, "median_hz": 350}]
        groups = group_repeated_regions(rows)
        self.assertEqual([g["regionIds"] for g in groups], [["a", "b"], ["c"]])
        self.assertEqual(groups[0]["estimatedF0Hz"], 320)

    def test_incomplete_reference_cannot_be_forced_into_hypothesis(self):
        with self.assertRaisesRegex(ValueError, "18 reference"):
            align_to_hypothesis([], [])

    def test_out_of_range_or_reversed_basic_tones_are_rejected(self):
        groups = [{"estimatedF0Hz": 220*2**(i/12), "regionIds": [str(i)]} for i in range(18)]
        with self.assertRaisesRegex(ValueError, "ordered"):
            align_to_hypothesis([{"segmentId": "x", "estimatedF0Hz": 1000}], groups)
        with self.assertRaisesRegex(ValueError, "ordered"):
            align_to_hypothesis([{"segmentId": "x", "estimatedF0Hz": 440},
                                 {"segmentId": "y", "estimatedF0Hz": 220}], groups)


if __name__ == "__main__":
    unittest.main()
