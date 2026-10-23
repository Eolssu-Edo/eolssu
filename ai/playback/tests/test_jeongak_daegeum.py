"""Instrument interpretation regression cases; raw CAT stays outside Git."""
import unittest
from test_pipeline import score
from playback.score import compile_score


class InstrumentTests(unittest.TestCase):
    def test_yoseong_requires_contextual_mode(self):
        value = score()
        self.assertIsNone(compile_score(value,"jeongak-daegeum")["events"][0]["yoseong"])
        value["notes"][0]["yoseong"] = {}
        with self.assertRaisesRegex(ValueError,"mode"):
            compile_score(value,"jeongak-daegeum")
        value["notes"][0]["yoseong"] = {"mode":"pitch","cents":33}
        self.assertEqual(compile_score(value,"jeongak-daegeum")["events"][0]["yoseong"]["cents"],33)

    def test_sounding_melody_is_not_transposed_by_instrument_name(self):
        value = score([{"id":"a","pitch":82,"duration":1}])
        self.assertEqual(compile_score(value,"jeongak-daegeum")["events"][0]["pitch"],82)

    def test_style_limit_warns_and_technical_limit_fails(self):
        value = score([{"id":"a","pitch":89,"duration":1}])
        result = compile_score(value,"jeongak-daegeum")
        self.assertEqual(result["events"][0]["pitch"],89)
        self.assertTrue(any("전통 관행 음역 밖" in w for w in result["warnings"]))
        value["notes"][0]["pitch"] = 93
        with self.assertRaisesRegex(ValueError,"outside"):
            compile_score(value,"jeongak-daegeum")
