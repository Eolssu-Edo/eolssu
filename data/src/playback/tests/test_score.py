"""score.py validation and event compilation from the common score format."""
import unittest

from data.src.playback.score import MIDI_TEMPO_MAX, compile_score, midi_tempo
from data.src.playback.tests._helpers import converted, document, score


class ScoreTests(unittest.TestCase):
    def test_jeongak_range_and_explicit_yoseong_mode_keep_bank_limit_distinct(self):
        value = score()
        for pitch in (58, 87, 88, 89, 90, 92):
            value["notes"][0]["pitch"] = pitch
            perf = compile_score(value, "jeongak-daegeum")
            self.assertEqual(perf["events"][0]["pitch"], pitch)
            self.assertEqual(any("전통 관행 음역 밖" in w for w in perf["warnings"]), pitch > 87)
            self.assertTrue(any("WAV 67개" in w for w in perf["warnings"]))
        for pitch in (57, 93):
            value["notes"][0]["pitch"] = pitch
            with self.assertRaisesRegex(ValueError, "outside"):
                compile_score(value, "jeongak-daegeum")
        value["notes"][0].update(pitch=75, yoseong={"hz": 3, "cents": 40})
        with self.assertRaises(ValueError):
            compile_score(value, "jeongak-daegeum")
        value["notes"][0]["yoseong"]["mode"] = "pitch"
        self.assertEqual(compile_score(value, "jeongak-daegeum")["events"][0]["yoseong"]["mode"], "pitch")

    def test_yoseong_delay_must_reach_sounding_note_after_tie_and_ornament_expansion(self):
        effect = {"mode": "pitch", "hz": 3, "cents": 60, "delay_seconds": 2}
        value = score([{"id": "a", "pitch": 75, "duration": 1, "yoseong": effect}])
        with self.assertRaisesRegex(ValueError, "yoseong starts after"):
            compile_score(value, "sogeum")
        value["notes"][0]["tie"] = "start"
        value["notes"].append({"id": "b", "pitch": 75, "duration": 2, "tie": "stop"})
        self.assertEqual(len(compile_score(value, "sogeum")["events"]), 1)
        value["notes"] = [{"id": "a", "pitch": 75, "duration": 3, "yoseong": effect,
                           "ornament": {"kind": "grace-before", "pitch": 77}}]
        self.assertEqual(len(compile_score(value, "sogeum")["events"]), 2)

    def test_sogeum_written_octave_and_range_do_not_fold_sounding_notes(self):
        value = score([{"id": "a", "pitch": 60, "duration": 1}])
        value["context"].update(pitch_mode="written", notation_octave_shift=1)
        self.assertEqual(compile_score(value, "sogeum")["events"][0]["pitch"], 72)
        value["context"].update(pitch_mode="sounding", notation_octave_shift=0)
        for pitch in (70, 72, 92, 93, 96):
            value["notes"][0]["pitch"] = pitch
            perf = compile_score(value, "sogeum")
            self.assertEqual(perf["events"][0]["pitch"], pitch)
            self.assertEqual(any("전통 관행 음역 밖" in w for w in perf["warnings"]), pitch > 92)
        for pitch in (69, 97):
            value["notes"][0]["pitch"] = pitch
            with self.assertRaisesRegex(ValueError, "outside"):
                compile_score(value, "sogeum")

    def test_piecewise_pitch_curves_require_ordered_bounded_complete_points(self):
        curves = [
            {"points": []}, {"points": [[0, 0], [0, -100], [1, 0]]},
            {"points": [[.1, 0], [1, -100]]}, {"points": [[0, 0], [.9, -100]]},
            {"points": [[0, 0], [1, -1201]]}, {"points": [[0, 0], [1, float("nan")]]},
            {"points": [[0, 0], [1, -100]], "end_cents": -100},
            {"points": [[0, 100], [1, 0]], "delay_seconds": .1},
            {"points": [[0, 0], [1, -100]], "delay_seconds": 1},
        ]
        for curve in curves:
            with self.subTest(curve=curve), self.assertRaises(ValueError):
                compile_score(score([{"id": "a", "pitch": 75, "duration": 1, "pitch_curve": curve}]), "danso")

    def test_explicit_wide_yoseong_and_curve_survive_compilation(self):
        value = score([{"id": "a", "pitch": 75, "duration": 2,
                        "yoseong": {"mode": "pitch", "hz": 3, "cents": 300},
                        "pitch_curve": {"points": [[0, 0], [.5, -300], [1, 0]]}}])
        event = compile_score(value, "danso")["events"][0]
        self.assertEqual(event["yoseong"]["cents"], 300)
        self.assertEqual(event["pitch_curve"]["points"], [[0, 0], [.5, -300], [1, 0]])

    def test_tempo_checks_encoded_midi_limit_instead_of_musical_band(self):
        for bpm in (9.5, 405, 4800, 60_000_000 / MIDI_TEMPO_MAX):
            with self.subTest(bpm=bpm):
                value = score()
                value["quarter_bpm"] = bpm
                self.assertEqual(compile_score(value, "danso")["tempo_us"], round(60_000_000 / bpm))
        for bpm in (0, -1, True, float("nan"), float("inf"), 60_000_001):
            with self.subTest(bpm=bpm), self.assertRaises(ValueError):
                midi_tempo(bpm)
        for bpm in (3, 5e-324):
            with self.subTest(bpm=bpm), self.assertRaisesRegex(ValueError, "MIDI Set Tempo"):
                midi_tempo(bpm)

    def test_duration_resource_limit_is_explicit_and_configurable(self):
        value = score([{"id": "a", "pitch": 69, "duration": 602}])
        with self.assertRaisesRegex(ValueError, "resource limit of 600"):
            compile_score(value, "danso")
        perf = compile_score(value, "danso", max_duration_seconds=1200)
        self.assertEqual(perf["end_tick"] / perf["ppqn"], 602)
        for limit in (0, -1, True, float("nan"), float("inf")):
            with self.subTest(limit=limit), self.assertRaises(ValueError):
                compile_score(value, "danso", max_duration_seconds=limit)

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
