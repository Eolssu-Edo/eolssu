"""Official teaching figures and playback policy boundaries, with authored scores."""
from copy import deepcopy
from fractions import Fraction
import json
from pathlib import Path
import tempfile
import unittest
import numpy as np

from data.src.playback.__main__ import run
from data.src.playback.score import compile_score
from data.src.playback.tests.test_jgb import document, converted, midi
from data.src.playback.tests.test_pipeline import synthetic_sampler
from data.src.playback.sampler import FS


class DansoNotationTests(unittest.TestCase):
    def test_official_twelve_attached_figures(self):
        # National Gugak Center's 12 figures, long middle anchored at MIDI 75.
        cases = {
            "니레": [77, 75], "니라": [80, 75], "노네": [73, 75], "너녜": [70, 75],
            "노니로": [75, 77, 75], "네로네": [75, 73, 75], "나니나": [75, 77, 75],
            "나느나": [75, 73, 75], "느니르": [73, 75, 73], "나니로": [73, 77, 75],
            "로니로": [70, 77, 75], "느로니르": [75, 73, 77, 75],
        }
        for name, pitches in cases.items():
            with self.subTest(name=name):
                score, mapping = converted(document(f"청황{{{name}}} | 청태"))
                self.assertEqual([n["pitch"] for n in score["notes"][:-1]], pitches)
                self.assertEqual(sum(Fraction(n["duration"]) for n in score["notes"][:-1]), Fraction(3, 2))
                self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 1, places=5)
                self.assertEqual(mapping["written_total_quarters"], "3")
        quick, _ = converted(document("청황{노니로} | 청태"))
        equal, _ = converted(document("청황{나니나} | 청태"))
        self.assertEqual([Fraction(n["duration"]) for n in quick["notes"][:2]], [Fraction(1, 16)] * 2)
        self.assertEqual([Fraction(n["duration"]) for n in equal["notes"][:3]], [Fraction(1, 2)] * 3)

    def test_official_twelve_substitute_figures(self):
        cases = {
            "노": [73], "로": [70], "니": [77], "리": [80], "니나": [77, 75],
            "느니": [77, 80], "노라": [73, 70], "니로나": [77, 75, 73],
            "니레나": [80, 77, 75], "느나르나니": [77, 80, 77, 75, 80],
            "니느라니": [77, 75, 77, 75], "느나니나": [73, 75, 77, 75],
        }
        for name, pitches in cases.items():
            with self.subTest(name=name):
                score, mapping = converted(document(f"청황 | {{{name}}}"))
                self.assertEqual([n["pitch"] for n in score["notes"][1:]], pitches)
                self.assertEqual([Fraction(n["duration"]) for n in score["notes"][1:]],
                                 [Fraction(3, 2 * len(pitches))] * len(pitches))
                self.assertIn("nineurani", mapping["symbol_policy"]["upstream_differences"])
                self.assertEqual(len(midi(score)["notes"]), len(pitches) + 1)

    def test_new_aliases_produce_identical_midi(self):
        pairs = [("나니로", "naniro", True), ("나니르", "naniro", True),
                 ("느로니르", "neuronireu", True), ("니루-니", "niruni", True),
                 ("니레나", "nirena", False), ("니느라니", "nineurani", False),
                 ("느나니나", "neunanina", False), ("느니르나니", "neunareunani", False),
                 ("풀어내리는표", "vib-long", True), ("미는표", "push", True),
                 ("끊는표", "staccato", True), ("늘임표", "fermata", True)]
        for a, b, attached in pairs:
            with self.subTest(a=a):
                template = "청황{%s} | 청태" if attached else "청황 | {%s}"
                self.assertEqual(midi(converted(document(template % a))[0]),
                                 midi(converted(document(template % b))[0]))
        a, _ = converted(document("청황{덧길이표} 청태{반길이표} | 청황"))
        b, _ = converted(document("청황{len-double} 청태{len-half} | 청황"))
        self.assertEqual(midi(a), midi(b))

    def test_post_grace_moves_after_sustained_main_and_preserves_reference(self):
        doc = document("청황{느니르} | - | {니}", beats=3)
        original = deepcopy(doc)
        score, mapping = converted(doc)
        self.assertEqual(doc, original)
        self.assertEqual([n["pitch"] for n in score["notes"]], [73, 75, 73, 77])
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]],
                         [Fraction(1, 16), Fraction(23, 8), Fraction(1, 16), Fraction(3, 2)])
        self.assertEqual([p["cell"] for p in mapping["notes"][score["notes"][1]["id"]]], [1, 2])
        self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 2, places=5)
        score, _ = converted(document("청황{느니르} | -{농음표} | 청태", beats=3))
        self.assertTrue(all(v == 1 for t, v in midi(score)["cc11"] if t < 1))
        self.assertTrue(any(v < 1 for t, v in midi(score)["cc11"] if 1 < t < 1.9))
        with self.assertRaisesRegex(ValueError, "숨표"):
            converted(document("청황{느니르}< | -"))

    def test_post_grace_never_crosses_rest_or_gak(self):
        for text in ("청황{느니르} | 쉼", "청황{느니르} | 청태\n- | 청태"):
            score, _ = converted(document(text))
            self.assertEqual(Fraction(score["notes"][2]["duration"]), Fraction(1, 16))

    def test_unwind_is_distinct_pitch_vibrato_plus_fall_and_push_is_upward(self):
        plain, _ = converted(document("청황 | 청태"))
        decorated, mapping = converted(document("청황{풀어내림표} | 청태{미는표}"))
        a, b = midi(plain), midi(decorated)
        self.assertEqual(a["notes"], b["notes"])
        self.assertEqual(decorated["notes"][0]["yoseong"]["mode"], "pitch")
        self.assertTrue(any(v < -35 for t, v in b["bends"] if .95 < t < 1))
        self.assertTrue(any(v > 35 for t, v in b["bends"] if 1.95 < t < 2))
        fall = midi(converted(document("청황{흘림표} | 청태"))[0])
        self.assertNotEqual(b["bends"], fall["bends"])
        self.assertEqual(mapping["expression_policy"]["status"], "audition-policy-not-performance-standard")

    def test_unwind_on_hold_does_not_bend_before_mark(self):
        score, _ = converted(document("청황 | -{풀어내림표} | 청태", beats=3))
        plain, _ = converted(document("청황 | - | 청태", beats=3))
        # The separate automatic tremble is disabled when an explicit pitch
        # yoseong exists; the marked effect itself must not begin before 1s.
        self.assertTrue(all(abs(v) < .001 for t, v in midi(score)["bends"] if t < 1))
        self.assertEqual(midi(score)["notes"], midi(plain)["notes"])

    def test_staccato_breath_share_release_budget_and_leave_actual_silence(self):
        for bpm in (60, 400):
            for mark in ("{끊는표}", "{끊는표}<"):
                with self.subTest(bpm=bpm, mark=mark):
                    score, _ = converted(document(f"청황{mark} | 청태", staffUnit="eighth"), bpm=bpm)
                    wire = midi(score)
                    n, following = wire["notes"]
                    self.assertAlmostEqual(n["end"] + n["release_seconds"], 30 / bpm, delta=.0001)
                    self.assertAlmostEqual(following["start"], 60 / bpm, places=5)
                    pcm, _ = synthetic_sampler().render(wire)
                    self.assertEqual(np.count_nonzero(pcm[round((30 / bpm + .001) * FS):round(60 / bpm * FS)]), 0)

    def test_fermata_records_written_and_performed_times_including_rest(self):
        score, mapping = converted(document("청황{늘임표} | 쉼{늘임표} | 청태", beats=3))
        self.assertEqual(mapping["written_total_quarters"], "9/2")
        self.assertEqual(mapping["total_quarters"], "15/2")
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]], [3, 3, Fraction(3, 2)])
        self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 4, places=5)
        last = mapping["notes"][score["notes"][-1]["id"]][0]
        self.assertEqual((last["written_start_quarters"], last["start_quarters"]), ("3", "6"))
        with self.assertRaisesRegex(ValueError, "샘플|exhaust|sample",):
            synthetic_sampler(length=.2).render(midi(score))

    def test_short_staccato_silence_cannot_leak_previous_note_tails(self):
        for text in ("청임 청중{끊는표} | 청태", "청임 청중{끊는표}< | 청태",
                     "청황청태청임 청황청태청임 청황청태청중{끊는표} | 청태"):
            with self.subTest(text=text):
                score, _ = converted(document(text, staffUnit="eighth"), bpm=400)
                wire = midi(score)
                last, following = wire["notes"][-2:]
                pcm, trace = synthetic_sampler().render(wire)
                stop = last["end"] + last["release_seconds"]
                self.assertEqual(np.count_nonzero(pcm[round((stop + .001) * FS):round(following["start"] * FS)]), 0)
                self.assertTrue(all(n["end"] + n["rendered_release_seconds"] <= stop + 1 / FS for n in trace[:-1]))

    def test_known_unresolved_figures_are_not_silently_skipped(self):
        for mark in ("격음표", "겹흘림표", "겹미는표", "요성표", "겹요성표", "하성", "느니라", "노라느니", "반복표"):
            with self.subTest(mark=mark), self.assertRaisesRegex(ValueError, "1각 · 1정간.*미확정"):
                converted(document(f"청황{{{mark}}} | 청태"))
        for jo in ("hwang-pyeong", "jung-pyeong", "hwang-gyemyeon", ""):
            score, _ = converted(document("청임{떠이어표} | 청태", joPreset=jo))
            self.assertEqual([n["pitch"] for n in score["notes"][:3]], [82, 85, 82])
        with self.assertRaisesRegex(ValueError, "교육 예시"):
            converted(document("청황{떠이어표} | 청태"))

    def test_ambiguous_combinations_and_disabled_effects_fail(self):
        for text in ("청황{풀어내림표}{농음표} | 청태", "청황{흘림표}{미는표} | 청태",
                     "청황{흘림표} | -{풀어내림표}", "청황{끊는표} | -",
                     "청황{니레}{끊는표} | 청태", "쉼{풀어내림표} | 청태"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                converted(document(text))
        for mark in ("풀어내림표", "미는표", "끊는표", "늘임표"):
            with self.subTest(mark=mark), self.assertRaisesRegex(ValueError, "꺼져"):
                converted(document(f"청황{{{mark}}} | 청태", playSigimsae=False))
        score, _ = converted(document())
        for gate in (0, -1, 2, True, float("nan")):
            score["notes"][0]["gate_fraction"] = gate
            with self.subTest(gate=gate), self.assertRaises(ValueError):
                compile_score(score, "danso")
        score["notes"][0].update(gate_fraction=.5, tie="start")
        with self.assertRaisesRegex(ValueError, "gate and tie"):
            compile_score(score, "danso")

    def test_delayed_curve_with_ir_ornament_fails_before_midi_generation(self):
        score, _ = converted(document())
        score["notes"][0].update(ornament={"kind": "grace-before", "pitch": 77},
                                 pitch_curve={"end_cents": -50, "tail_fraction": .3,
                                              "maximum_seconds": .2, "delay_seconds": 1 / 16})
        with self.assertRaisesRegex(ValueError, "split ornament and delayed"):
            compile_score(score, "danso")

    def test_tempo_requires_explicit_playback_selection_not_a_number_or_jangdan(self):
        for version in (1, 2):
            for flag in (None, False):
                doc = document(version=version)
                doc["controls"].update(tempoBpmUserSet=True, tempoBpmGak="60", jangdan="굿거리")
                doc.pop("tempoBpmUserSet")
                if flag is not None:
                    doc["tempoBpmUserSet"] = flag
                with self.subTest(version=version, flag=flag):
                    with self.assertRaisesRegex(ValueError, "빠르기 선택"):
                        converted(doc, speed=.5)
                    score, mapping = converted(doc, bpm=60)
                    self.assertEqual(mapping["tempo_selection"]["kind"], "cli-override")
                    self.assertEqual(mapping["tempo_selection"]["musicological_basis"], "not-verified")
                    self.assertAlmostEqual(midi(score)["notes"][-1]["start"], 1, places=5)
        for flag in ("true", 1, [], {}):
            doc = document()
            doc["tempoBpmUserSet"] = flag
            with self.subTest(flag=flag), self.assertRaisesRegex(ValueError, "true/false"):
                converted(doc)

    def test_unconfirmed_tempo_fails_before_assets_or_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, out = Path(tmp) / "a.jgb.json", Path(tmp) / "out"
            doc = document()
            doc.pop("tempoBpmUserSet")
            payload = json.dumps(doc).encode()
            source.write_bytes(payload)
            with self.assertRaisesRegex(ValueError, "빠르기 선택"):
                run(source, Path(tmp) / "missing-assets.json", out, ["danso"], audio_format="wav")
            self.assertEqual(source.read_bytes(), payload)
            self.assertFalse(out.exists())


if __name__ == "__main__":
    unittest.main()
