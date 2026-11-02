"""Public import behavior with small, source-independent musical expectations."""
from copy import deepcopy
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from data.src.scores.playback_import import import_jgb
from data.src.playback.common_score import convert_common_score
from data.src.playback.score import compile_score


def source(melody, beats=1):
    return {"v": 2, "tempoBpmUserSet": True,
            "controls": {"title": "작은 검증곡", "beats": beats, "tempoBpm": 60,
                         "staffUnit": "plain", "hwangPitch": 63, "joPreset": "jung-pyeong",
                         "playSigimsae": True, "playJanggu": False},
            "parts": [{"instrument": "danso", "melody": melody}]}


def bundle(doc, part=None):
    digest = hashlib.sha256(json.dumps(doc, ensure_ascii=False).encode()).hexdigest()
    return import_jgb(doc, source_name="test.jgb.json", instrument="danso",
                      part=part, input_sha256=digest)


def performed(result):
    return convert_common_score(result["common"], bpm=result["bpm"], instrument="danso",
                                input_sha256=result["expressions"]["sourceSha256"],
                                notation_policy="educational-preview/v1", expressions=result["expressions"])[0]


class PlaybackImportTests(unittest.TestCase):
    def test_independent_figure_keeps_its_internal_slur_after_expansion(self):
        result = bundle(source("청황 {니나}"))
        ids = [e["id"] for e in result["common"]["events"]]
        groups = result["expressions"].get("slurGroups", [])
        self.assertEqual([g["eventIds"] for g in groups], [ids[1:]])
        self.assertTrue(groups[0]["basis"])
        compiled = compile_score(performed(result), "danso")
        self.assertEqual([e["legato"] for e in compiled["events"]], [False, False, True])
        self.assertEqual(result["source_map"]["slurGroups"], groups)
        origin = result["source_map"]["slots"][-1]["source"]
        self.assertEqual(origin["interpretedSlur"]["id"], "p1.g1.c1.r2.u1")
        self.assertEqual(len(origin["interpretedSlur"]["noteIds"]), 2)

    def test_separate_independent_figures_do_not_join_each_other(self):
        result = bundle(source("청황 {니나} {느나}"))
        ids = [e["id"] for e in result["common"]["events"]]
        self.assertEqual([g["eventIds"] for g in result["expressions"]["slurGroups"]], [ids[1:3], ids[3:5]])
        compiled = compile_score(performed(result), "danso")
        self.assertEqual([e["legato"] for e in compiled["events"]], [False, False, True, False, True])

    def test_independent_slur_can_end_in_hold_and_breath_without_extending_the_group(self):
        result = bundle(source("청황 {니나} | -<", beats=2))
        events = result["common"]["events"]
        self.assertEqual([g["eventIds"] for g in result["expressions"]["slurGroups"]], [[e["id"] for e in events[1:]]])
        self.assertTrue(events[-1]["breathAfter"])
        self.assertEqual(len(result["source_map"]["slots"][-1]["generated"]), 1)
        compiled = compile_score(performed(result), "danso")
        self.assertEqual([e["legato"] for e in compiled["events"]], [False, False, True])

    def test_single_note_relative_and_attached_figures_need_no_expanded_slur_group(self):
        for melody in ("청황 {같은음표}", "청황{나니나}", "청황 청태"):
            with self.subTest(melody=melody):
                result = bundle(source(melody))
                self.assertNotIn("slurGroups", result["expressions"])
                self.assertEqual(result["source_map"]["slurGroups"], [])

    def test_final_slur_note_keeps_effect_placed_on_later_hold(self):
        result = bundle(source("청황 {니나} | -{농음표}", beats=2))
        score = performed(result)
        self.assertEqual([e["legato"] for e in compile_score(score, "danso")["events"]], [False, False, True])
        self.assertAlmostEqual(score["notes"][-1]["yoseong"]["delay_seconds"], .25)
        self.assertEqual(result["expressions"]["placements"][0]["cellId"], "c0-j1")
        self.assertEqual(len(result["expressions"]["slurGroups"]), 1)

    def test_null_source_jangdan_is_not_invented(self):
        doc = source("청황")
        doc["playback"] = {"tempo_basis": {"jangdan": None}}
        self.assertEqual(bundle(doc)["common"]["music"]["jangdan"], "")

    def test_independent_figure_keeps_cell_end_breath(self):
        result = bundle(source("청황 청황{니나}<"))
        events = result["common"]["events"]
        self.assertEqual(len(events), 4)
        self.assertEqual([e["breathAfter"] for e in events], [False, False, False, True])

    def test_independent_figure_has_its_own_slot_and_does_not_replace_written_note(self):
        doc = source("청임 청임 청임{니나}")
        before = deepcopy(doc)
        result = bundle(doc)
        events = result["common"]["events"]
        self.assertEqual([(e["yul"], e["octave"]) for e in events],
                         [("임", 1), ("임", 1), ("임", 1), ("무", 1), ("임", 1)])
        tpb = result["common"]["music"]["ticksPerBeat"]
        self.assertEqual([Fraction(e["durationTick"], tpb) for e in events],
                         [Fraction(1, 3), Fraction(1, 3), Fraction(1, 6), Fraction(1, 12), Fraction(1, 12)])
        self.assertEqual(doc, before)
        self.assertTrue(all(not e["sigimsae"] for e in events))
        self.assertEqual(result["common"]["provenance"]["review"], "inferred")

    def test_relative_chain_uses_the_last_performed_pitch(self):
        result = bundle(source("청황{나니나} {니} {니}"))
        self.assertEqual([n["pitch"] for n in performed(result)["notes"]], [75, 77, 75, 77, 80])
        self.assertEqual(result["common"]["events"][0]["sigimsae"], ["나니나"])

    def test_blank_and_explicit_hold_keep_same_gak_note_but_next_gak_rest(self):
        result = bundle(source("청황 | {빈지속} | \r\n | 청태 | 쉼", beats=3))
        music = result["common"]["music"]
        events = result["common"]["events"]
        self.assertEqual([(e["kind"], Fraction(e["durationTick"], music["ticksPerBeat"])) for e in events],
                         [("NOTE", 3), ("REST", 1), ("NOTE", 1), ("REST", 1)])
        self.assertTrue(all(not e["sigimsae"] for e in events))

    def test_variable_gak_lengths_and_original_staff_unit_are_preserved(self):
        for staff, ratio, bpm in (("dotted", Fraction(3, 2), 90), ("eighth", Fraction(1, 2), 30)):
            doc = source("청황\n청태 | 청중", beats=2)
            doc["controls"].update(gakBeats="1:1", staffUnit=staff)
            result = bundle(doc)
            with self.subTest(staff=staff):
                music = result["common"]["music"]
                self.assertEqual(music["bpmUnit"], "QUARTER")
                self.assertEqual(Fraction(music["ticksPerJeonggan"], music["ticksPerBeat"]), ratio)
                self.assertEqual(result["bpm"], bpm)
                self.assertEqual([len(c["cells"]) for c in result["common"]["columns"]], [1, 2])
                self.assertAlmostEqual(music["hwangHz"], 440 * 2 ** ((63 - 69) / 12))

    def test_custom_scale_policy_and_placed_marks_are_not_invented(self):
        doc = source("청황{농음표@1,-2} | {빈지속}", beats=2)
        doc["controls"]["joPreset"] = ""
        doc["playback"] = {
            "relative_scale": {"pitch_classes": [0, 2, 5, 7, 10], "basis": "고정 시험 음계"},
            "expression_policy": {"name": "fixed-test", "basis": "고정 시험 수치",
                                  "status": "audition-policy-not-performance-standard",
                                  "vibrato": {"mode": "pitch", "hz": 3, "cents": 19, "delay_seconds": .2}}}
        result = bundle(doc)
        expressions = result["expressions"]
        self.assertEqual(expressions["relativeScale"], doc["playback"]["relative_scale"])
        self.assertEqual(expressions["expressionPolicy"], doc["playback"]["expression_policy"])
        self.assertEqual(expressions["placements"][0]["symbols"], ["농음표@1,-2"])
        self.assertEqual(performed(result)["notes"][0]["yoseong"], doc["playback"]["expression_policy"]["vibrato"])

    def test_source_map_offsets_slice_original_unicode_and_crlf(self):
        doc = source("청황 | 청태\r\n 청황 청황{니나@1,-2} | {빈지속}", beats=2)
        text = doc["parts"][0]["melody"]
        result = bundle(doc)
        expanded = [s for s in result["source_map"]["slots"] if s["expandedIndependent"]]
        self.assertEqual(len(expanded), 1)
        origin = expanded[0]["source"]
        self.assertEqual(text[origin["cellStart"]:origin["cellEnd"]], origin["cellText"])
        self.assertEqual(text[origin["rowStart"]:origin["rowEnd"]], "청황{니나@1,-2}")
        span = origin["symbolSpans"][0]
        self.assertEqual(text[span["start"]:span["end"]], "{니나@1,-2}")
        events = {e["id"] for e in result["common"]["events"]}
        self.assertTrue(all(g["eventId"] in events for g in expanded[0]["generated"]))

    def test_continuation_is_part_specific_and_only_in_sidecar(self):
        doc = source("청황\n청태")
        doc["parts"].append({"instrument": "danso", "melody": "청황\n-"})
        doc["playback"] = {"gak_continuations": [{"part": 2, "gak": 2, "basis": "두 번째 성부 지속"}]}
        with self.assertRaisesRegex(ValueError, "여러 성부"):
            bundle(doc)
        first, second = bundle(doc, 1), bundle(doc, 2)
        self.assertNotIn("gakContinuations", first["expressions"])
        self.assertEqual(second["expressions"]["gakContinuations"], [{"gak": 2, "basis": "두 번째 성부 지속"}])
        self.assertEqual([e["kind"] for e in second["common"]["events"]], ["NOTE", "REST"])
        self.assertEqual(len(performed(second)["notes"]), 1)
        self.assertEqual(Fraction(performed(second)["notes"][0]["duration"]), 2)
        self.assertNotEqual(first["common"]["score"]["sourceId"], second["common"]["score"]["sourceId"])
        doc["playback"]["gak_continuations"][0]["gak"] = 3
        with self.assertRaisesRegex(ValueError, "존재하지 않는 각"):
            bundle(doc, 1)

    def test_mixed_expansion_does_not_change_adaptive_grace_silently(self):
        with self.assertRaisesRegex(ValueError, "같은 row의 꾸밈"):
            bundle(source("청황 청황{니레}{니나}"))

    def test_postcondition_rejects_unpreserved_grace_with_length_pair(self):
        with self.assertRaisesRegex(ValueError, "성부 1.*명목 시간을 보존하지 못합니다"):
            bundle(source("청황{니레}{덧길이표} 청태{반길이표}"))

    def test_source_metadata_requirements_still_fail_closed(self):
        doc = source("청황")
        del doc["tempoBpmUserSet"]
        with self.assertRaisesRegex(ValueError, "재생 빠르기"):
            bundle(doc)
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            import_jgb(source("청황"), source_name="test", instrument="danso", input_sha256="not-a-hash")

    def test_legacy_v1_document_is_also_supported(self):
        doc = source("청황")
        doc.update(v=1, melody=doc.pop("parts")[0]["melody"])
        self.assertEqual(bundle(doc)["common"]["events"][0]["yul"], "황")

    def test_cli_bundle_hash_original_bytes_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path, out = root / "원문.jgb.json", root / "bundle"
            raw = b"\xef\xbb\xbf" + json.dumps(source("청황"), ensure_ascii=False, indent=2).replace("\n", "\r\n").encode("utf-8")
            path.write_bytes(raw)
            command = [sys.executable, "-B", "-X", "utf8", "-m", "data.src.scores.playback_import", str(path),
                       "--instrument", "danso", "--source-id", "test-import", "--out", str(out)]
            run = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", check=True)
            report = json.loads(run.stdout)
            self.assertEqual(report["bpm"], 60)
            self.assertIn("--common-bpm", report["playback_args"])
            self.assertEqual((out / "source.jgb.json").read_bytes(), raw)
            companion = json.loads((out / "expressions.json").read_text(encoding="utf-8"))
            self.assertEqual(companion["sourceSha256"], hashlib.sha256((out / "common.json").read_bytes()).hexdigest())
            common_before = (out / "common.json").read_bytes()
            again = subprocess.run(command, capture_output=True, text=True, encoding="utf-8")
            self.assertNotEqual(again.returncode, 0)
            self.assertEqual((out / "common.json").read_bytes(), common_before)
            self.assertEqual(path.read_bytes(), raw)


if __name__ == "__main__":
    unittest.main()
