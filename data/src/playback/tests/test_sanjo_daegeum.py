"""Instrument interpretation regression cases; raw CAT stays outside Git."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from data.src.playback.tests.test_pipeline import score
from data.src.playback.jgb import convert_jgb
from data.src.playback.midi import read_midi, write_midi
from data.src.playback.score import compile_score


FIXTURE = Path(__file__).parents[1] / "examples/sanjo-authored.jgb.json"


def authored_jgb():
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


def convert(value):
    return convert_jgb(value, source_name=FIXTURE.name, instrument="sanjo-daegeum")


def played(value):
    performance = compile_score(value, "sanjo-daegeum")
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / "sanjo.mid"
        write_midi(performance, path)
        return read_midi(path)


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
        self.assertTrue(any("실연 음역이 미확정" in w for w in result["warnings"]))
        self.assertTrue(any("실제 운지·호흡·연주 품질을 검증한 결과가 아" in w for w in result["warnings"]))

    def test_authored_jgb_keeps_source_timing_and_explicit_effects(self):
        source = authored_jgb()
        original = deepcopy(source)
        value, mapping = convert(source)
        result = played(value)
        self.assertEqual(source, original)
        self.assertEqual([n["pitch"] for n in result["notes"]], [63, 65, 70, 68, 70])
        self.assertAlmostEqual(result["notes"][-1]["start"], 3, places=5)
        self.assertAlmostEqual(result["notes"][-1]["end"], 3.83, places=5)
        self.assertTrue(any(abs(cents) > 10 for time, cents in result["bends"] if .3 < time < .8))
        self.assertTrue(any(cents < -35 for time, cents in result["bends"] if 1.95 < time < 2))
        self.assertEqual(mapping["expression_policy"]["name"], "sanjo-authored-fixture-v1")

    def test_marked_effect_cannot_inherit_danso_policy(self):
        source = authored_jgb()
        del source["playback"]["expression_policy"]
        with self.assertRaises(ValueError):
            convert(source)

    def test_relative_ornament_requires_declared_scale(self):
        source = authored_jgb()
        del source["playback"]["relative_scale"]
        with self.assertRaises(ValueError):
            convert(source)

    def test_unknown_daegeum_subtype_is_not_sanjo(self):
        source = authored_jgb()
        source["parts"][0]["instrument"] = "daegeum"
        with self.assertRaises(ValueError):
            convert(source)

    def test_unmarked_notes_have_no_added_danso_pitch_motion(self):
        source = authored_jgb()
        source["parts"][0]["melody"] = "황 | 태 | 중 | 임"
        source.pop("playback")
        result = played(convert(source)[0])
        self.assertTrue(result["bends"])
        self.assertTrue(all(cents == 0 for _, cents in result["bends"]))
