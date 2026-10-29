"""expressions.py: notation policies, ornament timing, breaths and profiles."""
from copy import deepcopy
from fractions import Fraction
import unittest

from data.src.playback.expressions import (
    SCHEMA, SCALES, adaptive_breath, adaptive_grace, apply_effects, expression_for,
    fit_breath_to_duration, main_note_effects, performed_duration,
    resolve_expression_profile, resolve_ornament_pitches, expression_policy, relative_scale,
)


def profile(kind, **policy):
    return {"schema": SCHEMA, "policies": {kind: {"basis": "고정 검사 선택값", **policy}}}


class ExpressionTests(unittest.TestCase):
    def test_sanjo_policy_does_not_supply_default_motion_and_rejects_other_schema(self):
        chosen = expression_policy({}, "sanjo-daegeum")
        self.assertEqual(chosen["name"], "sanjo-daegeum-explicit-effects-v1")
        for kind in ("vibrato", "fall", "push", "unwind"):
            self.assertNotIn(kind, chosen)
        authored = {"schema": "sanjo-daegeum-expression-profile/v1", "policies": {
            "bend-down": {"basis": "검사 작성값", "pitch_curve": {"points": [[0, 0], [.1, -100], [1, 0]]}}}}
        before = deepcopy(authored)
        result = resolve_expression_profile(authored, "sanjo-daegeum")
        self.assertEqual(expression_for(result, "bend-down")["pitch_curve"]["points"], [[0, 0], [.1, -100], [1, 0]])
        self.assertEqual(authored, before)
        with self.assertRaisesRegex(ValueError, "schema"):
            resolve_expression_profile(authored, "sogeum")

    def test_sogeum_explicit_effects_keep_directions_delay_and_input_independent(self):
        authored = {"playback": {"expression_policy": {
            "name": "test", "basis": "고정 검사 선택값", "status": "audition-policy-not-performance-standard",
            "unwind": {"yoseong": {"mode": "pitch", "hz": 3, "cents": 60, "delay_seconds": .2},
                       "pitch_curve": {"points": [[0, 0], [1, -150]], "delay_seconds": .1}}}}}
        before = deepcopy(authored)
        chosen = expression_policy(authored, "sogeum")
        note = {}
        apply_effects(note, {"unwind": True}, "검사", delay=1, policy=chosen)
        self.assertAlmostEqual(note["yoseong"]["delay_seconds"], 1.2)
        self.assertAlmostEqual(note["pitch_curve"]["delay_seconds"], 1.1)
        note["pitch_curve"]["points"][1][1] = 999
        self.assertEqual(authored, before)
        self.assertEqual(chosen["unwind"]["pitch_curve"]["points"][1][1], -150)
        for invalid in ({"fall": {"end_cents": 50, "tail_fraction": .3, "maximum_seconds": .2}},
                        {"vibrato": {"mode": "pitch", "hz": 4, "delay_seconds": 0}},
                        {"vibrato": {"mode": "pitch", "hz": 4, "cents": 0, "delay_seconds": 0}},
                        {"unwind": {"yoseong": {}, "pitch_curve": {"points": [[0, 0], [1, -50]]}}}):
            doc = deepcopy(before)
            doc["playback"]["expression_policy"].update(invalid)
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                expression_policy(doc, "sogeum")

    def test_sogeum_profile_rejects_wrong_instrument_and_scale_missing_basis(self):
        source = {"schema": "sogeum-expression-profile/v1", "policies": {
            "shake": {"basis": "연습용", "yoseong": {"mode": "mixed", "hz": 3, "cents": 40,
                                                     "depth": .1, "delay_seconds": .2}}}}
        chosen = resolve_expression_profile(source, "sogeum")
        self.assertEqual(expression_for(chosen, "shake")["yoseong"]["cents"], 40)
        with self.assertRaisesRegex(ValueError, "schema"):
            resolve_expression_profile(source)
        for value in ({"pitch_classes": [0, 2]}, {"pitch_classes": [2, 0], "basis": "검사"},
                      {"pitch_classes": [0, 2, 2], "basis": "검사"}):
            with self.subTest(value=value), self.assertRaises(ValueError):
                relative_scale({"playback": {"relative_scale": value}})

    def test_builtin_effects_keep_delay_scope_conflicts_and_policy_isolation(self):
        selected = resolve_expression_profile()
        source = deepcopy(selected)
        note = {}
        apply_effects(note, {"unwind": True}, "고정 위치", delay=2, profile=selected)
        self.assertEqual(note["yoseong"]["mode"], "pitch")
        self.assertEqual(note["pitch_curve"]["end_cents"], -50)
        self.assertEqual(note["pitch_curve"]["delay_seconds"], 2)
        note["yoseong"]["cents"] = 999
        other = {}
        apply_effects(other, {"unwind": True}, "고정 위치", profile=selected)
        self.assertEqual(other["yoseong"]["cents"], 25)
        self.assertEqual(selected, source)
        with self.assertRaisesRegex(ValueError, "농음과 풀어내림"):
            apply_effects({}, {"vibrato": True, "unwind": True}, "고정 위치")
        with self.assertRaisesRegex(ValueError, "범위를 분리"):
            apply_effects(other, {"fall": True}, "고정 위치")
        group = {"vibrato": True, "fall": True}
        self.assertEqual(main_note_effects(group, 1, 3), {"vibrato": True})
        self.assertEqual(main_note_effects(group, 3, 3), group)

    def test_ornament_pitches_and_short_timing_preserve_main_and_special_roll(self):
        for scale in (SCALES["hwang-pyeong"], SCALES["jung-pyeong"], None):
            main, before, after = resolve_ornament_pitches(
                {"symbols": ["roll"], "grace": (0, 1)}, 82, 63, scale, "고정 위치")
            self.assertEqual((main, before, after), ([82], [82, 85], []))
        with self.assertRaisesRegex(ValueError, "교육 예시"):
            resolve_ornament_pitches({"symbols": ["roll"]}, 75, 63, None, "고정 위치")
        self.assertEqual(resolve_ornament_pitches(
            {"symbols": ["느니르"], "grace": (-1,), "post_grace": (-1,)},
            75, 63, SCALES["jung-pyeong"], "고정 위치"), ([75], [73], [73]))
        each = adaptive_grace(Fraction(1, 18), 2, 1, "고정 위치")
        self.assertEqual(each, Fraction(1, 72))
        self.assertEqual(Fraction(1, 18) - each * 2, Fraction(1, 36))
        self.assertEqual(performed_duration(Fraction(1, 18), {"fermata": True}), Fraction(1, 9))

    def test_breath_policy_and_retiming_reserve_sound_and_silence(self):
        long = adaptive_breath(1, 60)
        self.assertEqual(long, {"breath_seconds": .12, "release_seconds": .05})
        fit_breath_to_duration(long, .05)
        self.assertAlmostEqual(long["breath_seconds"] + long["release_seconds"], .025)
        self.assertGreater(long["breath_seconds"], 0)
        short = adaptive_breath(Fraction(1, 18), 200)
        self.assertLessEqual(short["breath_seconds"] + short["release_seconds"], 1 / 120)
        unchanged = {"breath_seconds": .12, "release_seconds": .05}
        fit_breath_to_duration(unchanged, 2)
        self.assertEqual(unchanged, {"breath_seconds": .12, "release_seconds": .05})

    def test_builtin_accent_is_declared_and_other_gestures_require_a_profile(self):
        resolved = resolve_expression_profile()
        self.assertEqual(expression_for(resolved, "accent"), {"velocity": 110, "expression": [127, 90, 90]})
        self.assertIn("not-performance-standard", resolved["policies"]["accent"]["status"])
        for kind in ("shake", "shake-rep", "bend-down", "bend-up", "flow-rep", "push-rep"):
            with self.subTest(kind=kind), self.assertRaisesRegex(ValueError, "expression-profile"):
                expression_for(resolved, kind)

    def test_explicit_deep_yoseong_keeps_input_and_returned_policies_independent(self):
        source = profile("shake", yoseong={"mode": "pitch", "hz": 3, "cents": 300, "delay_seconds": 0})
        original = deepcopy(source)
        resolved = resolve_expression_profile(source)
        self.assertEqual(source, original)
        note = expression_for(resolved, "shake")
        self.assertEqual(note["yoseong"]["cents"], 300)
        note["yoseong"]["cents"] = 1
        self.assertEqual(expression_for(resolved, "shake")["yoseong"]["cents"], 300)

    def test_direction_and_repetition_match_explicit_curve_shape(self):
        for kind, points in (("bend-down", [[0, 0], [.5, -300], [1, 0]]),
                             ("bend-up", [[0, 0], [1, 300]]),
                             ("flow-rep", [[0, 0], [.3, -100], [.6, 0], [1, -200]]),
                             ("push-rep", [[0, 0], [.3, 100], [.6, 0], [1, 200]])):
            with self.subTest(kind=kind):
                resolved = resolve_expression_profile(profile(kind, pitch_curve={"points": points}))
                self.assertEqual(expression_for(resolved, kind)["pitch_curve"]["points"], points)
        for kind, points in (("bend-down", [[0, 0], [1, 100]]),
                             ("bend-up", [[0, 0], [1, -100]]),
                             ("flow-rep", [[0, 0], [.5, -50], [1, -100]]),
                             ("push-rep", [[0, 0], [1, 100]])):
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                resolve_expression_profile(profile(kind, pitch_curve={"points": points}))

    def test_unverified_aliases_and_incomplete_or_unknown_fields_are_rejected(self):
        invalid = [profile("geokeum", velocity=110), profile("haseong", pitch_curve={"points": [[0, 0], [1, -100]]}),
                   profile("shake", yoseong={"mode": "pitch", "hz": 3, "delay_seconds": 0}),
                   profile("shake", yoseong={"mode": "pitch", "hz": 3, "cents": 300}),
                   profile("shake", yoseong={"mode": "pitch", "hz": 3, "cents": 0, "delay_seconds": 0}),
                   profile("accent", velocity=True), profile("accent", velocity=100, unknown=True),
                   profile("accent", basis=" ", velocity=100),
                   profile("accent", pitch_curve={"points": [[0, 0], [1, 100]]})]
        for value in invalid:
            with self.subTest(value=value), self.assertRaises(ValueError):
                resolve_expression_profile(value)


if __name__ == "__main__":
    unittest.main()
