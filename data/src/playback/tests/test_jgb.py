"""Authored tiny JGB cases; no real score, CAT bank, Node or network required."""
from copy import deepcopy
from fractions import Fraction
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from data.src.playback.jgb import convert_jgb
from data.src.playback.score import compile_score
from data.src.playback.midi import read_midi, write_midi
from data.src.playback.__main__ import run
from data.src.playback.tests.test_pipeline import synthetic_sampler


def document(melody="청황 | 청태", version=2, **controls):
    doc = {"v": version, "controls": {"title": "고정 검사", "beats": "2", "gakBeats": "",
           "tempoBpm": "60", "hwangPitch": "63", "staffUnit": "dotted",
           "joPreset": "jung-pyeong", **controls}}
    if version == 1:
        doc["melody"] = melody
    else:
        doc["parts"] = [{"id": "part-17", "name": "단소", "instrument": "danso", "melody": melody}]
    return doc


def converted(doc, **options):
    return convert_jgb(doc, source_name="synthetic.jgb.json", **options)


def midi(score):
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp)/"test.mid"
        write_midi(compile_score(score, "danso"), path)
        return read_midi(path)


class JgbTests(unittest.TestCase):
    def test_versions_have_the_same_midi_and_keep_source_untouched(self):
        docs = [document(version=v) for v in (1, 2)]
        before = deepcopy(docs)
        a, b = [converted(d)[0] for d in docs]
        self.assertEqual(midi(a), midi(b))
        self.assertEqual(docs, before)
        self.assertEqual(a["source"]["review_status"], "source-review-unverified")
        self.assertEqual([n["pitch"] for n in a["notes"]], [75, 77])

    def test_quarter_unit_and_speed_preserve_explicit_jeonggan_time(self):
        for unit, ratio in (("dotted", Fraction(3, 2)), ("plain", Fraction(1)), ("eighth", Fraction(1, 2))):
            for speed in (.5, 1, 2):
                with self.subTest(unit=unit, speed=speed):
                    score, mapping = converted(document(staffUnit=unit), speed=speed)
                    self.assertEqual(Fraction(mapping["total_quarters"]), 2 * ratio)
                    notes = midi(score)["notes"]
                    self.assertAlmostEqual(notes[1]["start"], 1 / speed, places=5)
                    self.assertAlmostEqual(notes[1]["end"], 2 / speed, places=5)
        old = document(version=1)
        del old["controls"]["staffUnit"]
        score, mapping = converted(old)
        self.assertEqual(mapping["quarter_ratio"], "3/2")
        self.assertTrue(any("구형" in a for a in score["assumptions"]))
        old["controls"]["beats"] = 12
        old["melody"] = " | ".join(["청황"] * 12)
        score, mapping = converted(old)
        self.assertEqual(mapping["quarter_ratio"], "1/2")
        self.assertEqual(score["quarter_bpm"], 30)
        self.assertAlmostEqual(midi(score)["notes"][-1]["end"], 12, places=5)
        old["controls"]["staffUnit"] = "auto"
        self.assertEqual(converted(old)[1]["quarter_ratio"], "1/2")

    def test_hold_stops_at_gak_boundary_and_repeated_pitch_rearticulates(self):
        score, mapping = converted(document("청황 | -\n- | 청황\n청황 | 쉼"))
        notes = midi(score)["notes"]
        self.assertEqual([(n["pitch"], round(n["start"], 5), round(n["end"], 5)) for n in notes],
                         [(75, 0, 2), (75, 3, 4), (75, 4, 5)])
        self.assertEqual([x["cell"] for x in mapping["notes"]["p1.g1.c1.r1.u1"]], [1, 2])
        self.assertEqual(mapping["notes"]["p1.g2.c1.r1.u1"][0]["kind"], "hold")

    def test_rest_then_hold_stays_silent(self):
        score, _ = converted(document("청황 | 쉼 | - | 청태", beats=4))
        notes = midi(score)["notes"]
        self.assertAlmostEqual(notes[1]["start"], 3, places=5)
        self.assertAlmostEqual(notes[0]["end"], 1, places=5)

    def test_subdivisions_and_variable_gak_length(self):
        score, mapping = converted(document("청황청태 임 | 청중\n청태", gakBeats="2:1"))
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]],
                         [Fraction(3, 8), Fraction(3, 8), Fraction(3, 4), Fraction(3, 2), Fraction(3, 2)])
        self.assertEqual(mapping["cells"], 3)
        self.assertAlmostEqual(midi(score)["notes"][-1]["end"], 3, places=5)

    def test_source_offsets_and_ids_survive_crlf_empty_cells_and_double_bars(self):
        melody = " 청황 | \r\n청태 | 임||\r\n청황 | -\r\n"
        score, mapping = converted(document(melody))
        self.assertEqual(mapping["gaks"], 3)
        self.assertEqual(mapping["part_id"], "part-17")
        positions = [p for ps in mapping["notes"].values() for p in ps]
        self.assertEqual({(p["gak"], p["cell"]) for p in positions},
                         {(g, c) for g in range(1, 4) for c in (1, 2)})
        for p in positions:
            self.assertEqual(melody[p["cell_start"]:p["cell_end"]], p["text"])
        for event in compile_score(score, "danso")["events"]:
            self.assertTrue(all(nid in mapping["notes"] for nid in event["parent_ids"]))

    def test_graces_and_relative_sequences_use_scale_and_preserve_time(self):
        score, mapping = converted(document("청황{니레} | {니나}"))
        perf = compile_score(score, "danso")
        self.assertEqual([n["pitch"] for n in score["notes"]], [77, 75, 77, 75])
        self.assertEqual([Fraction(n["duration"]) for n in score["notes"]],
                         [Fraction(1, 16), Fraction(23, 16), Fraction(3, 4), Fraction(3, 4)])
        self.assertEqual([n["velocity"] for n in perf["events"]], [80, 90, 90, 90])
        self.assertEqual([n["legato"] for n in perf["events"]], [False, True, False, True])
        self.assertEqual(mapping["total_quarters"], "3")
        plain, _ = converted(document("청황{nire@120,1,-2} | {nina-dur}"))
        self.assertEqual(midi(plain), midi(score))

    def test_attached_sequence_and_multiple_graces_keep_their_slot(self):
        score, _ = converted(document("청황{나니나} | 청황{노니로}"))
        self.assertEqual([n["pitch"] for n in score["notes"]], [75, 77, 75, 75, 77, 75])
        self.assertEqual(sum(Fraction(n["duration"]) for n in score["notes"]), 3)
        self.assertAlmostEqual(midi(score)["notes"][-1]["end"], 2, places=5)

    def test_breath_is_applied_at_end_of_cell_after_hold(self):
        score, mapping = converted(document("청황< - | 청태"))
        self.assertEqual(len(score["notes"]), 2)
        self.assertEqual(score["notes"][0]["breath_seconds"], .12)
        self.assertTrue(mapping["notes"][score["notes"][0]["id"]][-1]["breath"])
        notes = midi(score)["notes"]
        self.assertAlmostEqual(notes[0]["end"], .83, places=4)
        self.assertAlmostEqual(notes[1]["start"], 1, places=5)
        with self.assertRaisesRegex(ValueError, "숨표 뒤"):
            converted(document("청황< | -"))
        with self.assertRaisesRegex(ValueError, "소진"):
            converted(document("청황 청태 임< | 청태"), bpm=240)

    def test_part_selection_does_not_follow_active_editor_or_mix_voices(self):
        doc = document()
        doc["parts"].append({"id": 7, "name": "둘째", "instrument": "danso", "melody": "임 | 중"})
        doc["activePart"] = 1
        with self.assertRaisesRegex(ValueError, "--part"):
            converted(doc)
        score, mapping = converted(doc, part=2)
        self.assertEqual([n["pitch"] for n in score["notes"]], [70, 68])
        self.assertEqual(mapping["part_id"], 7)
        self.assertTrue(all(nid.startswith("p2.") for nid in mapping["notes"]))

    def test_missing_tempo_and_reference_need_explicit_overrides(self):
        doc = document()
        del doc["controls"]["tempoBpm"]
        del doc["controls"]["hwangPitch"]
        doc["controls"]["tempoBpmGak"] = 999
        with self.assertRaisesRegex(ValueError, "BPM"):
            converted(doc)
        score, mapping = converted(doc, bpm=80, hwang_midi=62)
        self.assertEqual(score["quarter_bpm"], 120)
        self.assertEqual(score["notes"][0]["pitch"], 74)
        self.assertEqual(mapping["huangjong_midi"], 62)

    def test_ambiguous_or_unsupported_notation_fails_with_position(self):
        for melody in ("청황{흘림표} | 청태", "청황{농음표} | 청태", "청황{반길이표} | 청태",
                       "청황{알수없음} | 청태", "潢 | 청태", "{노} | 청태", "청황{니레 | 청태",
                       "청황청태 | 청태", "청황 청태 임 중 | 청태", "청황 | 청태 | 임"):
            with self.subTest(melody=melody), self.assertRaisesRegex(ValueError, "각"):
                converted(document(melody))
        with self.assertRaisesRegex(ValueError, "--jo"):
            converted(document("청황{니레} | 청태", joPreset="all"))
        score, _ = converted(document("청황{니레} | 청태", joPreset="all"), jo="jung-pyeong")
        self.assertEqual(score["notes"][0]["pitch"], 77)

    def test_invalid_settings_and_unrepresentable_timing_fail(self):
        for controls in ({"tempoBpm": "nan"}, {"hwangPitch": True}, {"beats": "2.5"},
                         {"gakBeats": "2:1"}, {"gakBeats": "1:2,1:2"}, {"staffUnit": "mystery"}):
            with self.subTest(controls=controls), self.assertRaises(ValueError):
                converted(document(**controls))
        with self.assertRaisesRegex(ValueError, "Unrepresentable"):
            converted(document("청황청태임 청황청태임 청황청태임 | 청태", staffUnit="plain"))
        with self.assertRaisesRegex(ValueError, "outside"):
            compile_score(converted(document("배황 | 청태"))[0], "danso")
        with self.assertRaisesRegex(ValueError, "버전"):
            converted(document(version=3))

    def test_source_and_asset_mismatch_do_not_silently_drop_sound(self):
        doc = document()
        doc["parts"][0]["instrument"] = "sogeum"
        with self.assertRaisesRegex(ValueError, "단소"):
            converted(doc)
        doc = document()
        doc["parts"][0]["muted"] = True
        with self.assertRaisesRegex(ValueError, "음소거"):
            converted(doc)
        doc = document(wantJangdan=True, playJanggu=True)
        doc["jangdan"] = "덩 | 궁"
        with self.assertRaisesRegex(ValueError, "장구"):
            converted(doc)
        doc = document(version=1, wantJangdan=True)
        doc["jangdan"] = "덩 | 궁"
        with self.assertRaisesRegex(ValueError, "장구"):
            converted(doc)
        with self.assertRaisesRegex(ValueError, "true/false"):
            converted(document(playJanggu="false"))
        with self.assertRaisesRegex(ValueError, "꺼져"):
            converted(document("청황{니레} | 청태", playSigimsae=False))

    def test_cli_pipeline_keeps_original_bytes_and_resolvable_source_map(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root/"input.jgb.json"
            payload = b"\xef\xbb\xbf" + json.dumps(document("청황 | -"), ensure_ascii=False).encode("utf-8")
            source.write_bytes(payload)
            assets = root/"assets.json"
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            fake = synthetic_sampler()
            fake.manifest, fake.close = {"fixture": "synthetic"}, lambda: None
            with patch("data.src.playback.__main__.CatSampler", return_value=fake):
                result = run(source, assets, root/"out", ["danso"])
            self.assertEqual(source.read_bytes(), payload)
            self.assertEqual((root/"out/source.jgb.json").read_bytes(), payload)
            self.assertEqual(result["input_sha256"], hashlib.sha256(payload).hexdigest())
            mapping = json.loads((root/"out/source-map.json").read_text(encoding="utf-8"))
            report = json.loads((root/"out/danso.json").read_text(encoding="utf-8"))
            self.assertEqual(len(mapping["notes"][report["events"][0]["parent_ids"][0]]), 2)
            self.assertEqual(result["jgb"]["part_id"], "part-17")

    def test_bad_jgb_preserves_previous_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root/"bad.jgb.json"
            source.write_text(json.dumps(document("청황{흘림표} | 청태")), encoding="utf-8")
            out = root/"out"
            out.mkdir()
            (out/"danso.wav").write_bytes(b"previous")
            with self.assertRaisesRegex(ValueError, "미지원"):
                run(source, root/"missing-assets.json", out, ["danso"])
            self.assertEqual((out/"danso.wav").read_bytes(), b"previous")
            self.assertEqual(len(list(out.iterdir())), 1)

    def test_hash_describes_captured_input_even_if_original_changes_during_render(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = root/"input.jgb.json", root/"assets.json"
            payload = json.dumps(document()).encode("utf-8")
            source.write_bytes(payload)
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            fake = synthetic_sampler()
            render = fake.render
            def render_with_external_edit(playback):
                source.write_bytes(b"external edit")
                return render(playback)
            fake.render = render_with_external_edit
            fake.manifest, fake.close = {"fixture": "synthetic"}, lambda: None
            with patch("data.src.playback.__main__.CatSampler", return_value=fake):
                result = run(source, assets, root/"out", ["danso"])
            self.assertEqual((root/"out/source.jgb.json").read_bytes(), payload)
            self.assertEqual(result["input_sha256"], hashlib.sha256(payload).hexdigest())
            self.assertEqual(result["jgb"]["input_sha256"], result["input_sha256"])

    def test_output_cannot_overwrite_original_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets = root/"score.json", root/"assets.json"
            payload = json.dumps(document()).encode("utf-8")
            source.write_bytes(payload)
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "겹칩니다"):
                run(source, assets, root, ["danso"])
            self.assertEqual(source.read_bytes(), payload)

    def test_ir_cannot_leave_an_obsolete_jgb_map_beside_new_audio(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, assets, out = root/"score.json", root/"assets.json", root/"out"
            source.write_text(json.dumps(converted(document())[0]), encoding="utf-8")
            assets.write_text('{"danso":"unused.zip"}', encoding="utf-8")
            out.mkdir()
            (out/"source-map.json").write_bytes(b"previous map")
            with self.assertRaisesRegex(ValueError, "이전 JGB"):
                run(source, assets, out, ["danso"])
            self.assertEqual((out/"source-map.json").read_bytes(), b"previous map")


if __name__ == "__main__":
    unittest.main()
