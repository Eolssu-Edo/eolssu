"""Instrument interpretation regression cases; raw CAT stays outside Git."""
import unittest
from test_pipeline import score
from playback.score import compile_score


class InstrumentTests(unittest.TestCase):
    def test_written_octave_and_sounding_input_stay_distinct(self):
        value = score([{"id":"a","pitch":60,"duration":1}])
        value["context"].update(pitch_mode="written", notation_octave_shift=1)
        self.assertEqual(compile_score(value,"sogeum")["events"][0]["pitch"],72)
        value["notes"][0]["pitch"] = 72
        value["context"].update(pitch_mode="sounding", notation_octave_shift=0)
        self.assertEqual(compile_score(value,"sogeum")["events"][0]["pitch"],72)

    def test_yoseong_only_added_when_explicit(self):
        value = score([{"id":"a","pitch":75,"duration":1}])
        self.assertIsNone(compile_score(value,"sogeum")["events"][0]["yoseong"])
        value["notes"][0]["yoseong"] = {}
        self.assertEqual(compile_score(value,"sogeum")["events"][0]["yoseong"]["mode"],"mixed")

    def test_style_limit_warns_and_technical_limit_fails_without_folding(self):
        value = score([{"id":"a","pitch":93,"duration":1}])
        result = compile_score(value,"sogeum")
        self.assertEqual(result["events"][0]["pitch"],93)
        self.assertTrue(any("전통 관행 음역 밖" in w for w in result["warnings"]))
        value["notes"][0]["pitch"] = 97
        with self.assertRaisesRegex(ValueError,"outside"):
            compile_score(value,"sogeum")
