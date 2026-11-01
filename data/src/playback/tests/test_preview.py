"""Explicit educational choices must not change source-evidence playback."""
from copy import deepcopy
from fractions import Fraction
from pathlib import Path
import re
import unittest

from data.src.playback.jgb import convert_jgb
from data.src.playback import expressions, preview
from data.src.playback.score import compile_score
from data.src.playback.tests._helpers import document


class PreviewTests(unittest.TestCase):
    def test_unresolved_figure_requires_opt_in_and_records_approximation(self):
        source = document("청중{느니라} | 청중", joPreset="all")
        with self.assertRaisesRegex(ValueError, "미확정"):
            convert_jgb(source, source_name="test.jgb.json")
        source["playback"] = {"notation_policy": "educational-preview/v1"}
        before = deepcopy(source)
        score, mapping = convert_jgb(source, source_name="test.jgb.json")
        self.assertEqual(source, before)
        self.assertGreater(len(score["notes"]), 2)
        self.assertEqual(mapping["notation_policy"]["mode"], "educational-preview/v1")
        self.assertTrue(mapping["notation_policy"]["default_scale_applied"])
        self.assertIn("느니라", [x["symbol"] for x in mapping["notation_policy"]["approximations"]])

    def prepared(self, mark, instrument="danso"):
        source = document(f"청중{{{mark}}} | 청중", joPreset="all")
        source["parts"][0]["instrument"] = instrument
        source["playback"] = {"notation_policy": preview.MODE}
        return source

    def test_known_guide_and_registry_marks_have_preview_for_each_instrument(self):
        guide = Path(__file__).resolve().parents[4] / "frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/Sigimsae.kt"
        text = guide.read_text(encoding="utf-8")
        guide_names = set(re.findall(r'SigimsaeSymbol\(Res\.drawable\.\w+, "([^"]+)"', text))
        guide_names |= {"추성", "미는표", "퇴성", "흘림표", "요성·농음", "니레", "니라", "니로", "노니로", "끊는표", "늘임표"}
        self.assertEqual(len(guide_names), 46)
        names = guide_names | expressions.UNRESOLVED | set(expressions.EFFECT_MARKS) | set(expressions.PROFILE_MARKS)
        names |= set(expressions.LENGTH_MARKS) | set(preview.EXTRA_MARKS) | set(preview.ALIASES) | {"원문점"}
        for table in expressions.SYMBOL_TABLES.values():
            names.update(table)
        for instrument in expressions.SYMBOL_TABLES:
            for name in sorted(names - preview.TIMELINE_MARKS):
                with self.subTest(instrument=instrument, name=name):
                    source = self.prepared(name, instrument)
                    if name in expressions.LENGTH_MARKS:
                        other = "반길이표" if expressions.LENGTH_MARKS[name] > 1 else "덧길이표"
                        source["parts"][0]["melody"] = f"청중{{{name}}} 청중{{{other}}} | 청중"
                    score, mapping = convert_jgb(source, source_name="matrix.jgb.json", instrument=instrument, inline_relative=True)
                    compiled = compile_score(score, instrument)
                    self.assertTrue(compiled["events"])
                    self.assertIn(name, [item["symbol"] for item in mapping["notation_policy"]["approximations"]])

    def test_explicit_profiles_and_effects_override_defaults(self):
        source = self.prepared("농음표", "sogeum")
        source["playback"]["expression_policy"] = {"name": "authored", "basis": "fixed test",
            "status": "audition-policy-not-performance-standard",
            "vibrato": {"mode": "pitch", "hz": 2, "cents": 75, "delay_seconds": .1}}
        score, mapping = convert_jgb(source, source_name="test", instrument="sogeum")
        self.assertEqual(score["notes"][0]["yoseong"]["cents"], 75)
        self.assertEqual(mapping["expression_policy"]["name"], "authored")
        source = self.prepared("요성표")
        profile = {"schema": "danso-expression-profile/v1", "policies": {"shake": {
            "basis": "authored fixture", "yoseong": {"mode": "pitch", "hz": 2, "cents": 75, "delay_seconds": 0}}}}
        score, _ = convert_jgb(source, source_name="test", expression_profile=profile, inline_relative=True)
        self.assertEqual(score["notes"][0]["yoseong"]["hz"], 2)

    def test_inline_relative_uses_written_anchor_and_preserves_slot(self):
        source = self.prepared("니느라니")
        score, _ = convert_jgb(source, source_name="test", inline_relative=True)
        self.assertEqual([n["pitch"] for n in score["notes"]], [82, 80, 82, 80, 80])
        self.assertEqual(sum(Fraction(n["duration"]) for n in score["notes"]), 3)
        with self.assertRaisesRegex(ValueError, "좌우 분할"):
            convert_jgb(source, source_name="test")

    def test_instrument_figures_take_precedence_over_preview_fallback(self):
        for instrument, expected in (("danso", [78, 75, 80]), ("sogeum", [78, 75, 73]),
                                     ("jeongak-daegeum", [78, 75, 73])):
            score, mapping = convert_jgb(self.prepared("네로나", instrument), source_name="test",
                                        instrument=instrument, inline_relative=True)
            self.assertEqual([n["pitch"] for n in score["notes"][:3]], expected)
            self.assertEqual(mapping["notation_policy"]["approximations"][0]["status"], "existing-instrument-policy")

    def test_unknown_typo_and_timeline_without_scope_still_fail(self):
        for mark, message in (("니레오타", "미지원"), ("반복표", "반복 범위"), ("도돌이표", "반복 범위")):
            with self.subTest(mark=mark), self.assertRaisesRegex(ValueError, message):
                convert_jgb(self.prepared(mark), source_name="test", inline_relative=True)

    def test_hold_delay_and_geokeum_timbre_limit_are_visible(self):
        source = self.prepared("깊은요성")
        source["parts"][0]["melody"] = "청중 | -{깊은요성}"
        score, _ = convert_jgb(source, source_name="test")
        self.assertEqual(score["notes"][0]["yoseong"]["delay_seconds"], 1)
        score, mapping = convert_jgb(self.prepared("격음"), source_name="test")
        self.assertEqual(score["notes"][0]["velocity"], 116)
        self.assertIn("no dedicated", mapping["notation_policy"]["approximations"][0]["timbre_limit"])

    def test_conflicting_effects_and_wrong_instrument_profile_are_not_hidden(self):
        source = self.prepared("깊은요성}{농음표")
        with self.assertRaisesRegex(ValueError, "겹친"):
            convert_jgb(source, source_name="test")
        with self.assertRaisesRegex(ValueError, "schema"):
            convert_jgb(self.prepared("요성표"), source_name="test",
                        expression_profile={"schema": "sogeum-expression-profile/v1", "policies": {}})

    def test_validated_common_layout_uses_exact_cell_duration(self):
        source = self.prepared("니레")
        source["parts"][0]["melody"] = "청중청중청중청중 | 청중 청중 청중 청중"
        with self.assertRaisesRegex(ValueError, "좌우 분할"):
            convert_jgb(source, source_name="test")
        score, mapping = convert_jgb(source, source_name="test", inline_relative=True,
                                     cell_quarters=Fraction(5, 2))
        self.assertEqual(len(score["notes"]), 8)
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]], [Fraction(5, 8)] * 8)
        self.assertEqual(score["quarter_bpm"], 150)
        self.assertEqual(mapping["quarter_ratio"], "5/2")
        self.assertEqual(mapping["staff_unit"], "common-explicit")
        for value in (0, "1/0", True):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, "cell_quarters"):
                convert_jgb(source, source_name="test", inline_relative=True, cell_quarters=value)

    def test_private_unit_boundaries_do_not_merge_register_prefixes(self):
        source = self.prepared("니레")
        source["parts"][0]["melody"] = "중;청황 | 청중{니레@50,0,0};청중"
        with self.assertRaisesRegex(ValueError, "읽을 수 없는"):
            convert_jgb(source, source_name="test")
        score, mapping = convert_jgb(source, source_name="test", inline_relative=True, cell_quarters=1)
        self.assertEqual([n["pitch"] for n in score["notes"][:2]], [68, 75])
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"][:2]], [Fraction(1, 2)] * 2)
        self.assertEqual([mapping["notes"][n["id"]][0]["unit"] for n in score["notes"][:2]], [1, 2])
        self.assertEqual(mapping["notes"][score["notes"][2]["id"]][0]["symbols"], ["니레"])
        for melody in (";중|청중", "중;|청중", "중;;청황|청중"):
            source["parts"][0]["melody"] = melody
            with self.subTest(melody=melody), self.assertRaisesRegex(ValueError, "unit"):
                convert_jgb(source, source_name="test", inline_relative=True)


if __name__ == "__main__":
    unittest.main()
