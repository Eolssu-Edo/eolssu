"""Instrument interpretation regression cases; raw CAT stays outside Git."""
import unittest
from test_pipeline import score
from playback.score import compile_score


class InstrumentTests(unittest.TestCase):
    def test_yoseong_requires_contextual_mode(self):
        value = score()
        self.assertIsNone(compile_score(value,"sanjo-daegeum")["events"][0]["yoseong"])
        value["notes"][0]["yoseong"] = {}
        with self.assertRaisesRegex(ValueError,"mode"):
            compile_score(value,"sanjo-daegeum")
        value["notes"][0]["yoseong"] = {"mode":"pitch","cents":33}
        self.assertEqual(compile_score(value,"sanjo-daegeum")["events"][0]["yoseong"]["cents"],33)

    def test_sounding_melody_is_not_transposed_by_instrument_name(self):
        value = score([{"id":"a","pitch":82,"duration":1}])
        self.assertEqual(compile_score(value,"sanjo-daegeum")["events"][0]["pitch"],82)

    def test_unknown_tube_range_is_exposed_before_sample_validation(self):
        value = score([{"id":"a","pitch":95,"duration":1}])
        result = compile_score(value,"sanjo-daegeum")
        self.assertEqual(result["events"][0]["pitch"],95)
        self.assertTrue(any("관종별 실연 음역이 미확정" in w for w in result["warnings"]))
        self.assertTrue(any("편곡 가능성을 검증한 결과가 아" in w for w in result["warnings"]))
