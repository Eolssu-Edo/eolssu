"""score.py validation and event compilation from the common score format."""
import unittest

from data.src.playback.score import compile_score
from data.src.playback.tests._helpers import converted, document, score


class ScoreTests(unittest.TestCase):
    def test_written_octave_applied_once(self):
        value = score([{"id":"a","pitch":60,"duration":1}])
        value["context"].update(pitch_mode="written", notation_octave_shift=1)
        self.assertEqual(compile_score(value,"danso")["events"][0]["pitch"],72)
        value["context"]["pitch_mode"] = "sounding"
        with self.assertRaisesRegex(ValueError,"again"):
            compile_score(value,"danso")

    def test_yul_requires_reference_and_respects_register(self):
        value = score([{"id":"a","pitch":{"yul":"黃","register":1},"duration":1}])
        value["context"].update(pitch_mode="yul", huangjong_midi=63)
        self.assertEqual(compile_score(value,"danso")["events"][0]["pitch"],75)
        del value["context"]["huangjong_midi"]
        with self.assertRaisesRegex(ValueError,"huangjong"):
            compile_score(value,"danso")

    def test_unresolved_or_oversized_ornament_rejected(self):
        for ornament in ({}, {"kind":"unknown"}, {"kind":"upper-return","pitch":72,"duration":"1"}):
            value = score()
            value["notes"][0]["ornament"] = ornament
            with self.assertRaises(ValueError):
                compile_score(value,"danso")

    def test_range_checked_after_ornament_and_no_automatic_fold(self):
        value = score()
        value["notes"][0]["ornament"]={"kind":"upper-return","pitch":100}
        with self.assertRaisesRegex(ValueError,"outside"):
            compile_score(value,"danso")

    def test_explicit_danso_yoseong_uses_amplitude_default(self):
        value = score()
        value["notes"][0]["yoseong"]={}
        self.assertEqual(compile_score(value,"danso")["events"][0]["yoseong"]["mode"],"amplitude")

    def test_bad_input_cannot_be_silently_ignored(self):
        for field, value in (("unknown",1),("duration",0),("velocity",0),("rest","yes"),("breath_seconds",2)):
            source=score();source["notes"][0][field]=value
            with self.assertRaises(ValueError):
                compile_score(source,"danso")
        source=score();source["notes"]*=2
        with self.assertRaisesRegex(ValueError,"Unique"):
            compile_score(source,"danso")

    def test_unclosed_tie_rejected(self):
        value=score();value["notes"][0]["tie"]="start"
        with self.assertRaisesRegex(ValueError,"Unclosed"):
            compile_score(value,"danso")

    def test_tie_cannot_silently_drop_new_slur_or_velocity(self):
        for extra in ({"slur":"new"},{"velocity":1}):
            value=score([{"id":"a","pitch":69,"duration":1,"tie":"start"},
                         {"id":"b","pitch":69,"duration":1,"tie":"stop",**extra}])
            with self.assertRaisesRegex(ValueError,"inside a tie"):
                compile_score(value,"danso")

    def test_delayed_curve_with_ir_ornament_fails_before_midi_generation(self):
        score, _ = converted(document())
        score["notes"][0].update(ornament={"kind": "grace-before", "pitch": 77},
                                 pitch_curve={"end_cents": -50, "tail_fraction": .3,
                                              "maximum_seconds": .2, "delay_seconds": 1 / 16})
        with self.assertRaisesRegex(ValueError, "split ornament and delayed"):
            compile_score(score, "danso")


if __name__ == "__main__":
    unittest.main()
