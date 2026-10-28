"""tempo_repeat.py: explicit source-bound tempo plans and whole-section repeats."""
from copy import deepcopy
from fractions import Fraction
import math
import unittest

from data.src.playback.tempo_repeat import apply_tempo_repeat
from data.src.playback.score import PPQN, compile_score
from data.src.playback.tests._helpers import converted, document, midi


HASH = "a" * 64


def plan(**values):
    return {"schema": "danso-performance/v1", "input_sha256": HASH, **values}


class TempoRepeatTests(unittest.TestCase):
    def test_immediate_tempo_changes_note_time_and_preserves_inputs(self):
        value, mapping = converted(document())
        selected = plan(tempos=[{"gak": 1, "cell": 2, "bpm": 120}])
        before = deepcopy((value, mapping, selected))
        result, positions = apply_tempo_repeat(value, mapping, selected, HASH)
        self.assertEqual(result["quarter_bpm"], 90)
        self.assertEqual([Fraction(n["duration"]) for n in result["notes"]], [Fraction(3, 2), Fraction(3, 4)])
        self.assertAlmostEqual(midi(result)["notes"][-1]["end"], 1.5, places=5)
        self.assertEqual(positions["total_quarters"], "9/4")
        self.assertEqual((value, mapping, selected), before)

    def test_tempo_inside_held_note_and_delayed_expression_is_integrated(self):
        value, mapping = converted(document("청황 | - | - | 청태", beats=4))
        value["notes"][0]["yoseong"] = {"mode": "pitch", "delay_seconds": 2, "cents": 25}
        value["notes"][0]["pitch_curve"] = {"end_cents": -50, "tail_fraction": .3,
                                            "maximum_seconds": .2, "delay_seconds": 2}
        selected = plan(tempos=[{"gak": 1, "cell": 2, "bpm": 120}])
        result, positions = apply_tempo_repeat(value, mapping, selected, HASH)
        self.assertEqual(Fraction(result["notes"][0]["duration"]), 3)
        self.assertEqual(result["notes"][0]["yoseong"]["delay_seconds"], 1.5)
        self.assertEqual(result["notes"][0]["pitch_curve"]["delay_seconds"], 1.5)
        self.assertAlmostEqual(midi(result)["notes"][-1]["start"], 2, places=5)
        self.assertEqual([loc["cell"] for loc in positions["notes"][result["notes"][0]["id"]]], [1, 2, 3])

    def test_gradual_tempo_uses_reciprocal_integral_not_average_bpm(self):
        value, mapping = converted(document("청황 | 청태 | 청임 | 청남", beats=4))
        mapping["tempo_cues"] = [{"gak": 1, "cell": 1, "symbol": "점점속하게"}]
        selected = plan(tempos=[{"gak": 1, "cell": 1, "bpm": 120, "end_gak": 2, "end_cell": 1}])
        result, positions = apply_tempo_repeat(value, mapping, selected, HASH)
        expected_seconds = 4 * math.log(2)
        actual_quarters = sum(Fraction(n["duration"]) for n in result["notes"])
        self.assertAlmostEqual(float(actual_quarters) * 2 / 3, expected_seconds, delta=1 / PPQN)
        durations = [float(Fraction(n["duration"])) for n in result["notes"]]
        self.assertTrue(all(a > b for a, b in zip(durations, durations[1:])))
        self.assertEqual(positions["performance"]["resolved_tempos"][0]["source_cue"], "점점속하게")
        self.assertEqual(compile_score(result, "danso")["end_tick"], round(expected_seconds * 1.5 * PPQN))

    def test_fermata_and_global_speed_remain_independent_of_written_ramp(self):
        for speed in (.5, 1, 2):
            value, mapping = converted(document("청황{늘임표} | 청태"), speed=speed)
            result, _ = apply_tempo_repeat(value, mapping,
                                         plan(tempos=[{"gak": 1, "cell": 2, "bpm": 120}]), HASH)
            self.assertAlmostEqual(midi(result)["notes"][-1]["end"], 2.5 / speed, places=5)

    def test_repeats_keep_source_positions_and_have_unique_ids(self):
        value, mapping = converted(document("청황 | 청태\n청임{나니나} | 청남\n청태 | 청황"))
        selected = plan(tempos=[{"gak": 2, "cell": 1, "bpm": 120}],
                        repeats=[{"start_gak": 2, "end_gak": 2, "times": 3}])
        result, positions = apply_tempo_repeat(value, mapping, selected, HASH)
        self.assertEqual(len(result["notes"]), 16)
        self.assertEqual(len({n["id"] for n in result["notes"]}), 16)
        for note in result["notes"]:
            if ".repeat" in note["id"]:
                source_id = note["id"].split(".repeat")[0]
                copied = positions["notes"][note["id"]][0]
                original = mapping["notes"][source_id][0]
                self.assertEqual((copied["gak"], copied["cell"], copied["cell_start"], copied["text"]),
                                 (original["gak"], original["cell"], original["cell_start"], original["text"]))
                if note.get("slur"):
                    self.assertTrue(note["slur"].endswith(note["id"].split(".repeat")[-1]))
        self.assertAlmostEqual(midi(result)["notes"][-1]["end"], 6, places=5)

    def test_original_tempo_cue_restores_base_and_other_cues_require_numbers(self):
        value, mapping = converted(document("청황 | 청태 | 청임", beats=3))
        mapping["tempo_cues"] = [{"gak": 1, "cell": 2, "symbol": "조금속하게"},
                                 {"gak": 1, "cell": 3, "symbol": "본래속도로"}]
        with self.assertRaisesRegex(ValueError, "명시한 BPM"):
            apply_tempo_repeat(value, mapping, plan(), HASH)
        result, positions = apply_tempo_repeat(value, mapping,
                                             plan(tempos=[{"gak": 1, "cell": 2, "bpm": 120}]), HASH)
        self.assertEqual([Fraction(n["duration"]) for n in result["notes"]], [Fraction(3, 2), Fraction(3, 4), Fraction(3, 2)])
        self.assertEqual(positions["performance"]["resolved_tempos"][1]["bpm"], 60)
        with self.assertRaisesRegex(ValueError, "높은 목표"):
            apply_tempo_repeat(value, mapping, plan(tempos=[{"gak": 1, "cell": 2, "bpm": 30}]), HASH)

    def test_shortened_breath_remains_bounded_and_curve_points_are_preserved(self):
        value, mapping = converted(document("청황< | 청태"))
        value["notes"][1]["pitch_curve"] = {"points": [[0, 0], [.5, -100], [1, 0]], "delay_seconds": .1}
        result, positions = apply_tempo_repeat(value, mapping, plan(tempos=[{"gak": 1, "cell": 1, "bpm": 1200}]), HASH)
        note = result["notes"][0]
        self.assertLessEqual(note["breath_seconds"] + note["release_seconds"], .0250001)
        loc = positions["notes"][note["id"]][0]
        self.assertEqual(loc["source_breath_timing"]["breath_seconds"], .12)
        self.assertNotIn("breath_timing", loc)
        self.assertEqual(result["notes"][1]["pitch_curve"]["points"], [[0, 0], [.5, -100], [1, 0]])
        self.assertAlmostEqual(result["notes"][1]["pitch_curve"]["delay_seconds"], .005)

    def test_invalid_plan_hash_shape_positions_and_overlap_are_rejected(self):
        value, mapping = converted(document("청황 | 청태\n청임 | 청남"))
        invalid = [plan(input_sha256="b" * 64), plan(extra=True), plan(tempos={}),
                   plan(tempos=[{"gak": 9, "cell": 1, "bpm": 60}]),
                   plan(tempos=[{"gak": 1, "cell": 1, "bpm": True}]),
                   plan(tempos=[{"gak": 1, "cell": 1, "bpm": 0}]),
                   plan(tempos=[{"gak": 1, "cell": 1, "bpm": 120, "end_gak": 3, "end_cell": 1},
                                {"gak": 2, "cell": 1, "bpm": 90}]),
                   plan(repeats=[{"start_gak": 1, "end_gak": 2, "times": 2},
                                 {"start_gak": 2, "end_gak": 2, "times": 2}]),
                   plan(repeats=[{"start_gak": 1, "end_gak": 1, "times": 9}])]
        for selected in invalid:
            with self.subTest(plan=selected), self.assertRaises(ValueError):
                apply_tempo_repeat(value, mapping, selected, HASH)

    def test_repeat_boundary_rejects_held_note_and_slur_cuts(self):
        value, mapping = converted(document("청황 | 청태\n청임 | 청남"))
        value["notes"][1]["slur"] = value["notes"][2]["slur"] = "cross-gak"
        with self.assertRaisesRegex(ValueError, "꾸밈음/붙임줄"):
            apply_tempo_repeat(value, mapping, plan(repeats=[{"start_gak": 2, "end_gak": 2, "times": 2}]), HASH)
        value["notes"][1].pop("slur")
        value["notes"][2].pop("slur")
        value["notes"][1]["duration"] = "3"
        del value["notes"][2]
        with self.assertRaisesRegex(ValueError, "지속음/꾸밈음 내부"):
            apply_tempo_repeat(value, mapping, plan(repeats=[{"start_gak": 2, "end_gak": 2, "times": 2}]), HASH)

    def test_gradual_cue_requires_endpoint_and_plan_is_not_applied_twice(self):
        value, mapping = converted(document())
        mapping["tempo_cues"] = [{"gak": 1, "cell": 1, "symbol": "점점느리게"}]
        with self.assertRaisesRegex(ValueError, "끝 위치"):
            apply_tempo_repeat(value, mapping, plan(tempos=[{"gak": 1, "cell": 1, "bpm": 30}]), HASH)
        selected = plan(tempos=[{"gak": 1, "cell": 1, "bpm": 30, "end_gak": 2, "end_cell": 1}])
        result, positions = apply_tempo_repeat(value, mapping, selected, HASH)
        with self.assertRaisesRegex(ValueError, "두 번"):
            apply_tempo_repeat(result, positions, selected, HASH)

    def test_empty_plan_preserves_timing_and_quantization_rejects_lost_notes(self):
        value, mapping = converted(document("청황{느니르} | 청태"))
        result, _ = apply_tempo_repeat(value, mapping, plan(), HASH)
        self.assertEqual(value["notes"], result["notes"])
        with self.assertRaisesRegex(ValueError, "시간 해상도"):
            apply_tempo_repeat(value, mapping, plan(tempos=[{"gak": 1, "cell": 1, "bpm": 1e12}]), HASH)


if __name__ == "__main__":
    unittest.main()
