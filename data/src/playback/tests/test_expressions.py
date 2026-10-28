"""expressions.py keeps numeric audition choices explicit and validates identity."""
from copy import deepcopy
import unittest

from data.src.playback.expressions import SCHEMA, expression_for, resolve_expression_profile


def profile(kind, **policy):
    return {"schema": SCHEMA, "policies": {kind: {"basis": "고정 검사 선택값", **policy}}}


class ExpressionTests(unittest.TestCase):
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
