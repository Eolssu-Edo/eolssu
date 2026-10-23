import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import wave

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src/audio"))
from export_dataset import export_dataset, read_audio, read_wave


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def link_directory(target, link):
    if sys.platform == "win32":
        import _winapi
        _winapi.CreateJunction(str(target), str(link))
    else:
        os.symlink(target, link, target_is_directory=True)


class ExportTests(unittest.TestCase):
    def test_readable_annotation_folder_preserves_ids_and_exports(self):
        before = self.run_export("before-folder-rename")
        source = self.sources["sources"][0]
        original = self.root / source["annotationPath"]
        original_bytes = original.read_bytes()
        source["annotationPath"] = "data/annotations/audio/tone-기본음+요성/labels.json"
        renamed = self.root / source["annotationPath"]
        renamed.parent.mkdir()
        original.rename(renamed)
        original.parent.rmdir()
        (self.dataset / "sources.json").write_text(json.dumps(self.sources), encoding="utf-8")
        after = self.run_export("after-folder-rename")
        self.assertEqual(original_bytes, renamed.read_bytes())
        self.assertEqual(after["sources"][0]["id"], "tone")
        for key in ("labels", "performances", "comparisonScores", "assets", "audioDecoders"):
            self.assertEqual(before[key], after[key], key)
        self.assertIn("annotations/audio/tone-기본음+요성/labels.json", after["manifestHashes"])
        self.assertNotIn("annotations/audio/tone/labels.json", after["manifestHashes"])
        # The CLI must follow the catalog reference too, without an old folder.
        script = Path(__file__).resolve().parents[1] / "src/audio/export_dataset.py"
        run = subprocess.run([sys.executable, str(script), "--dataset", str(self.dataset),
                              "--root", str(self.root), "--output-dir", str(self.processed / "readable-cli"),
                              "--label", "first"], capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(digest(self.processed / "readable-cli" / after["labels"][0]["file"]),
                         before["labels"][0]["sha256"])

    def test_annotation_path_keeps_source_id_and_audio_directory_shape(self):
        for invalid in (
            "data/annotations/audio/another-기본음/labels.json",
            "data/annotations/audio/tone2-기본음/labels.json",
            "data/annotations/audio/tone-/labels.json",
            "data/annotations/audio/tone-기본음/nested/labels.json",
            "data/annotations/audio/tone-기본음/other.json",
            "data/annotations/scores/tone-기본음/labels.json",
            "data/annotations/audio/tone-기본음/../labels.json",
        ):
            self.sources["sources"][0]["annotationPath"] = invalid
            (self.dataset / "sources.json").write_text(json.dumps(self.sources), encoding="utf-8")
            with self.subTest(path=invalid), self.assertRaises(ValueError):
                self.run_export(label_ids=["first"])
        self.assertFalse((self.processed / "export").exists())

    def test_processed_directory_cannot_redirect_output_into_raw(self):
        before = {p.name: digest(p) for p in self.raw.iterdir()}
        link_directory(self.raw, self.processed)
        with self.assertRaisesRegex(ValueError, "redirect|boundary"):
            self.run_export(label_ids=["first"])
        self.assertEqual(before, {p.name: digest(p) for p in self.raw.iterdir()})

    def test_raw_subdirectory_cannot_redirect_input_into_processed(self):
        self.processed.mkdir()
        shutil.copyfile(self.raw / "audio.wav", self.processed / "derived.wav")
        link_directory(self.processed, self.raw / "redirect")
        self.sources["sources"][0]["rawPath"] = "data/raw/redirect/derived.wav"
        self.save()
        with self.assertRaisesRegex(ValueError, "boundary"):
            self.run_export(label_ids=["first"])
        self.assertFalse((self.processed / "export").exists())

    def test_annotations_cannot_redirect_into_raw(self):
        annotation = self.root / self.sources["sources"][0]["annotationPath"]
        shutil.copyfile(annotation, self.raw / "labels.json")
        annotation.unlink()
        annotation.parent.rmdir()
        link_directory(self.raw, annotation.parent)
        with self.assertRaisesRegex(ValueError, "boundary"):
            self.run_export(label_ids=["first"])

    def test_source_ids_are_case_unique_even_without_shared_techniques(self):
        self.sources["sources"].append({**self.sources["sources"][1], "id": "SCORE"})
        self.save()
        with self.assertRaisesRegex(ValueError, "Case-colliding source"):
            self.run_export(label_ids=["first"])

    def test_declared_audio_annotations_are_required_inputs(self):
        annotation = self.root / self.sources["sources"][0]["annotationPath"]
        annotation.unlink()
        from export_dataset import validate_manifests
        with self.assertRaisesRegex(FileNotFoundError, "annotations.*tone.*labels.json"):
            validate_manifests(self.dataset, self.root)

    def test_annotation_identity_and_raw_paths_are_checked(self):
        path = self.root / self.sources["sources"][0]["annotationPath"]
        original = json.loads(path.read_text(encoding="utf-8"))
        mutations = [
            lambda doc: doc.update(datasetId="another"),
            lambda doc: doc.update(sourceHashes={"tone": "0" * 64}),
            lambda doc: doc["groups"][0].update(sourceId="score"),
        ]
        for mutate in mutations:
            invalid = copy.deepcopy(original)
            mutate(invalid)
            path.write_text(json.dumps(invalid), encoding="utf-8")
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                self.run_export()
        path.write_text(json.dumps(original), encoding="utf-8")
        source = self.sources["sources"][0]
        for raw_path in ("raw/audio.wav", "/data/raw/audio.wav", "data/raw/../../audio.wav"):
            source["rawPath"] = raw_path
            self.save()
            with self.subTest(raw_path=raw_path), self.assertRaises(ValueError):
                self.run_export()
        source["rawPath"] = "data/raw/audio.wav"
        self.save()
        source["annotationPath"] = "data/annotations/audio/another/labels.json"
        (self.dataset / "sources.json").write_text(json.dumps(self.sources), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Annotation path"):
            self.run_export()

    def test_method_review_and_scoring_gate(self):
        baseline = copy.deepcopy(self.labels)
        mutations = [
            lambda: self.labels.update(review="confirmed"),
            lambda: self.labels.update(eligibleForNoteScoring=True),
            lambda: self.labels["groups"][0].update(method="tool:fixture"),
            lambda: self.labels["groups"][0].update(method="old_unversioned_method"),
            lambda: self.labels["groups"][0].update(evidence={"first": {"review": "inferred", "eligibleForNoteScoring": True}}),
        ]
        for mutate in mutations:
            self.labels = copy.deepcopy(baseline)
            mutate()
            self.save()
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                self.run_export(label_ids=["first"])
        self.labels = copy.deepcopy(baseline)
        self.labels.update(review="reviewed", eligibleForNoteScoring=True)
        self.labels["groups"][0]["method"] = "manual"
        self.save()
        label = self.run_export("reviewed", label_ids=["first"])["labels"][0]
        self.assertEqual((label["review"], label["method"], label["eligibleForNoteScoring"]), ("reviewed", "manual", True))
        self.labels.update(review="rejected", eligibleForNoteScoring=False)
        self.save()
        with self.assertRaisesRegex(ValueError, "Rejected annotations"):
            self.run_export(label_ids=["first"])
        self.assertEqual(self.run_export("rejected-full")["labels"], [])

    def test_unverified_scores_are_comparisons_not_confirmed_performance_scores(self):
        score = self.performances["comparisonScores"][0]
        self.performances["performances"][0]["scores"] = [copy.deepcopy(score)]
        self.save()
        with self.assertRaisesRegex(ValueError, "comparisonScores"):
            self.run_export()
        self.performances["performances"][0]["scores"] = []
        score["forPerformanceId"] = "absent"
        self.save()
        with self.assertRaisesRegex(ValueError, "unknown performance"):
            self.run_export()
        score["forPerformanceId"] = "piece"
        self.save()
        result = self.run_export()
        self.assertEqual(result["performances"][0]["scores"], [])
        self.assertEqual(len(result["comparisonScores"]), 2)

    def test_remote_raw_root_keeps_checkout_metadata_and_processed_output(self):
        remote = self.root / "server"
        (remote / "data/raw").mkdir(parents=True)
        shutil.copyfile(self.raw / "audio.wav", remote / "data/raw/audio.wav")
        output = remote / "data/processed/clip"
        result = export_dataset(self.dataset, output, root=remote, label_ids=["first"])
        self.assertEqual(result["labels"][0]["sourceStartFrame"], 800)
        self.assertEqual(set(result["manifestHashes"]), {
            "catalog/example/sources.json", "catalog/example/performances.json",
            "annotations/audio/tone/labels.json"})
        for directory in ("raw", "catalog", "annotations"):
            with self.subTest(directory=directory), self.assertRaisesRegex(ValueError, "data/processed"):
                export_dataset(self.dataset, remote / f"data/{directory}/bad-output", root=remote, label_ids=["first"])

    def test_duplicate_label_ids_across_annotation_files_are_rejected(self):
        source = {**self.sources["sources"][0], "id": "second", "sha256": "1" * 64}
        self.sources["sources"].append(source)
        self.labels["sourceHashes"]["second"] = source["sha256"]
        group = copy.deepcopy(self.labels["groups"][0])
        group["sourceId"] = "second"
        self.labels["groups"].append(group)
        self.save()
        with self.assertRaisesRegex(ValueError, "duplicate label ID"):
            self.run_export(label_ids=["first"])

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.dataset = self.root / "data/catalog/example"
        self.dataset.mkdir(parents=True)
        self.raw = self.root / "data/raw"
        self.processed = self.root / "data/processed"
        self.raw.mkdir()
        # Known samples, no pitch inference or fixtures from the instrument under test.
        self.samples = np.column_stack([np.arange(8000) % 1000, -(np.arange(8000) % 400)]).astype("<i2")
        with wave.open(str(self.raw / "audio.wav"), "wb") as stream:
            stream.setnchannels(2)
            stream.setsampwidth(2)
            stream.setframerate(8000)
            stream.writeframes(self.samples.tobytes())
        (self.raw / "score.pdf").write_bytes(b"%PDF-1.4\nunchanged score fixture\n%%EOF")
        self.sources = {"schemaVersion": 2, "datasetId": "example", "instrument": "검증 악기",
                        "pathBase": "repository_root", "sources": [
            {"id": "tone", "kind": "audio", "title": "원음", "rawPath": "data/raw/audio.wav",
             "sha256": digest(self.raw / "audio.wav"), "license": "test", "sourceUrl": "https://example.org/audio", "attribution": "fixture"},
            {"id": "score", "kind": "score", "title": "악보", "rawPath": "data/raw/score.pdf",
             "sha256": digest(self.raw / "score.pdf"), "pdfPageCount": 10,
             "license": "test", "sourceUrl": "https://example.org/score", "attribution": "fixture"}]}
        self.labels = {"schemaVersion": 3, "datasetId": "example", "review": "inferred",
                       "eligibleForNoteScoring": False,
                       "sourceHashes": {"tone": self.sources["sources"][0]["sha256"]},
                       "columns": ["id", "startSeconds", "endSeconds", "register", "yulmyeong", "labelF0Hz"],
                       "groups": [
            {"sourceId": "tone", "category": "기본음계", "technique": "기본음", "method": "tool:fixture@1",
             "filenamePrefix": "none", "rows": [["first", .1, .2, "저취", "황", 300.0]]},
            {"sourceId": "tone", "category": "시김새", "technique": "요성", "method": "tool:fixture@1",
             "filenamePrefix": "rowNumber3", "rows": [["repeat", .3, .5, "저취", "황", 301.0]]}]}
        self.performances = {"schemaVersion": 2, "datasetId": "example",
                             "sourceHashes": {s["id"]: s["sha256"] for s in self.sources["sources"]},
                             "performances": [{"id": "piece", "title": "곡",
            "audio": {"sourceId": "tone", "file": "연주/원음.wav"}, "scores": [
                {"sourceId": "score", "file": "연주/악보.pdf", "notation": "staff", "pdfPages": [2],
                 "coverage": "성악용", "samePerformanceEditionVerified": False},
                {"sourceId": "score", "file": "연주/악보.pdf", "notation": "jeongganbo", "pdfPages": [3],
                 "coverage": "단소용 일부", "samePerformanceEditionVerified": False}]}]}
        self.performances["comparisonScores"] = [
            {"id": f"comparison-{i}", "forPerformanceId": "piece", **score}
            for i, score in enumerate(self.performances["performances"][0]["scores"])]
        self.performances["performances"][0]["scores"] = []
        self.save()

    def save(self):
        source_ids = {source["id"] for source in self.sources["sources"]}
        for source in self.sources["sources"]:
            groups = [group for group in self.labels["groups"]
                      if group["sourceId"] == source["id"] or
                      (source["id"] == "tone" and group["sourceId"] not in source_ids)]
            if not groups:
                source.pop("annotationPath", None)
                continue
            source["annotationPath"] = f"data/annotations/audio/{source['id']}/labels.json"
            # Keep invalid source IDs in manifests for validation tests without
            # creating unsafe paths or overwriting a case alias in the fixture.
            if "/" in source["id"] or source["id"] in ("CON", "TONE"):
                continue
            document = {**self.labels, "groups": groups,
                        "sourceHashes": {source["id"]: self.labels["sourceHashes"].get(source["id"], "")}}
            path = self.root / source["annotationPath"]
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(document, ensure_ascii=False), encoding="utf-8")
        for name in ("sources", "performances"):
            (self.dataset / f"{name}.json").write_text(json.dumps(getattr(self, name), ensure_ascii=False), encoding="utf-8")

    def run_export(self, name="export", **kwargs):
        return export_dataset(self.dataset, self.processed / name, root=self.root, **kwargs)

    def update_label(self, index, **values):
        row = [row for group in self.labels["groups"] for row in group["rows"]][index]
        for field, value in values.items():
            row[self.labels["columns"].index(field)] = value

    def test_compact_rows_preserve_original_number_when_selecting_a_repeat(self):
        self.labels = {
            "schemaVersion": 3, "datasetId": "example", "review": "inferred",
            "eligibleForNoteScoring": False, "sourceHashes": self.labels["sourceHashes"],
            "columns": ["id", "startSeconds", "endSeconds", "register", "yulmyeong", "labelF0Hz"],
            "groups": [{"sourceId": "tone", "category": "시김새", "technique": "요성",
                        "method": "tool:fixture@1", "filenamePrefix": "rowNumber3", "rows": [
                            ["first", .1, .2, "저취", "황", 300.0],
                            ["repeat", .3, .5, "저취", "황", 301.0]]}]}
        self.save()
        result = self.run_export(label_ids=["repeat"])
        label = result["labels"][0]
        self.assertEqual(label["file"], "시김새/요성/002_저취_황.wav")
        self.assertEqual(label["displayLabel"], "저취 황")
        self.assertEqual(label["sourceStartFrame"], 2400)
        self.assertEqual(label["evidence"]["labelF0Hz"], 301.0)
        self.assertFalse(label["eligibleForNoteScoring"])

    def test_common_format_handles_other_instrument_names_and_literal_labels(self):
        for index, instrument in enumerate(("sogeum", "danso", "jeongak-daegeum")):
            for doc in (self.sources, self.labels, self.performances):
                doc["datasetId"] = instrument
            self.update_label(0, register="직접 지정한 취법", yulmyeong="직접 지정한 율명")
            self.save()
            result = self.run_export(f"export-{index}", label_ids=["first"])
            self.assertEqual(result["datasetId"], instrument)
            self.assertEqual(len(result["labels"]), 1)
            label = result["labels"][0]
            self.assertEqual((label["register"], label["yulmyeong"]), ("직접 지정한 취법", "직접 지정한 율명"))
            self.assertEqual((label["sourceStartFrame"], label["sourceEndFrameExclusive"]), (800, 1600))
            with wave.open(str(self.processed / f"export-{index}" / label["file"])) as stream:
                actual = np.frombuffer(stream.readframes(stream.getnframes()), dtype="<i2")
            expected = np.rint(self.samples[800:1600].astype(float).mean(axis=1) / 32768 * 32767).astype("<i2")
            np.testing.assert_array_equal(actual, expected)

    def test_editing_label_changes_exact_interval_without_analysis(self):
        self.update_label(0, startSeconds=.123, endSeconds=.456)
        self.save()
        result = self.run_export(label_ids=["first"])
        self.assertEqual(result["labels"][0]["frames"], 2664)
        self.assertEqual(result["labels"][0]["sourceStartFrame"], 984)

    def test_full_export_retains_repeat_and_copies_performance_and_shared_pdf_unchanged(self):
        result = self.run_export()
        self.assertEqual([v["id"] for v in result["labels"]], ["first", "repeat"])
        self.assertEqual(digest(self.processed / "export/연주/원음.wav"), digest(self.raw / "audio.wav"))
        self.assertEqual(digest(self.processed / "export/연주/악보.pdf"), digest(self.raw / "score.pdf"))
        self.assertEqual(len(list((self.processed / "export").rglob("*.pdf"))), 1)
        self.assertFalse(result["comparisonScores"][1]["samePerformanceEditionVerified"])
        self.assertEqual(result["comparisonScores"][1]["coverage"], "단소용 일부")

    def test_selective_export_does_not_require_unselected_score_or_alter_full_export(self):
        self.run_export("full")
        before = {p.relative_to(self.processed / "full"): digest(p) for p in (self.processed / "full").rglob("*") if p.is_file()}
        (self.raw / "score.pdf").unlink()
        result = self.run_export("selection", technique="기본음", register="저취", yulmyeong="황")
        self.assertEqual([v["id"] for v in result["labels"]], ["first"])
        self.assertEqual(result["performances"], [])
        after = {p.relative_to(self.processed / "full"): digest(p) for p in (self.processed / "full").rglob("*") if p.is_file()}
        self.assertEqual(before, after)
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.run_export("full", label_ids=["first"])

    def test_performance_only_selection_excludes_clips(self):
        result = self.run_export(performance_ids=["piece"])
        self.assertEqual(result["labels"], [])
        self.assertEqual(len(result["performances"]), 1)

    def test_unknown_or_empty_selection_fails_without_output(self):
        for selector in ({"label_ids": ["absent"]}, {"performance_ids": ["absent"]}, {"yulmyeong": "없는 음"}):
            with self.subTest(selector=selector), self.assertRaises(ValueError):
                self.run_export(**selector)
            self.assertFalse((self.processed / "export").exists())

    def test_wrong_hash_and_stale_label_version_are_rejected_before_writes(self):
        original = copy.deepcopy(self.sources)
        for stale in (False, True):
            self.sources = copy.deepcopy(original)
            if stale:
                self.labels["sourceHashes"]["tone"] = "0" * 64
            else:
                (self.raw / "audio.wav").write_bytes(b"changed")
            self.save()
            with self.assertRaises(ValueError):
                self.run_export(label_ids=["first"])
            self.assertFalse((self.processed / "export").exists())

    def test_invalid_ids_ranges_pages_and_collisions_fail_before_output(self):
        originals = copy.deepcopy((self.sources, self.labels, self.performances))
        mutations = [
            lambda: self.labels["groups"][0].update(sourceId="absent"),
            lambda: self.update_label(1, id="first"),
            lambda: self.update_label(0, startSeconds=-.1),
            lambda: self.update_label(0, endSeconds=1.1),
            lambda: self.update_label(0, startSeconds=float("nan")),
            lambda: self.update_label(0, endSeconds=.1),
            lambda: self.performances["comparisonScores"][0].update(pdfPages=[11]),
            lambda: self.labels["groups"][1].update(category="기본음계", technique="기본음", filenamePrefix="none"),
            lambda: self.performances["performances"][0]["audio"].update(file="기본음계/기본음/저취_황.wav/child.wav"),
            lambda: self.labels.update(datasetId="wrong-dataset"),
        ]
        for mutate in mutations:
            self.sources, self.labels, self.performances = copy.deepcopy(originals)
            mutate()
            self.save()
            with self.subTest(mutate=mutate), self.assertRaises((ValueError, OSError)):
                self.run_export()
            self.assertFalse((self.processed / "export").exists())

    def test_unsafe_paths_and_reserved_output_names_are_rejected(self):
        for name in ("../outside", "C:/outside", "/outside", "a\\..\\outside",
                     "catalog.json", "CON", "a./b", "a:b"):
            self.labels["groups"][0]["category"] = name
            self.save()
            with self.subTest(path=name), self.assertRaises(ValueError):
                self.run_export()
            self.assertFalse((self.processed / "export").exists())

    def test_missing_selected_input_leaves_no_partial_output(self):
        (self.raw / "score.pdf").unlink()
        with self.assertRaises((ValueError, OSError)):
            self.run_export()
        self.assertFalse((self.processed / "export").exists())
        self.assertEqual(list(self.processed.glob(".dataset-export-*")), [])

    def test_cli_uses_the_three_manifests_and_selects_one_label(self):
        script = Path(__file__).resolve().parents[1] / "src/audio/export_dataset.py"
        result = subprocess.run([sys.executable, str(script), "--dataset", str(self.dataset), "--root", str(self.root),
                                 "--output-dir", str(self.processed / "cli"), "--label", "repeat"],
                                capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(result.returncode, 0, result.stderr)
        result = json.loads((self.processed / "cli/catalog.json").read_text(encoding="utf-8"))
        self.assertEqual([v["id"] for v in result["labels"]], ["repeat"])

    def test_float_wave_at_native_rate_supports_headroom_and_odd_chunks(self):
        values = np.array([[1.02, 1.02], [-1.02, -1.02], [0, 0], [.25, .5]], dtype="<f4")
        fmt = struct.pack("<HHIIHH", 3, 2, 44100, 44100 * 8, 8, 32)
        chunks = b"JUNK" + struct.pack("<I", 1) + b"x\0" + b"fmt " + struct.pack("<I", 16) + fmt
        chunks += b"data" + struct.pack("<I", values.nbytes) + values.tobytes()
        raw = b"RIFF" + struct.pack("<I", 4 + len(chunks)) + b"WAVE" + chunks
        path = self.raw / "float.wav"
        path.write_bytes(raw)
        samples, rate = read_wave(path)
        np.testing.assert_array_equal(samples, values.astype(float))
        self.assertEqual(rate, 44100)
        source = self.sources["sources"][0]
        source.update(rawPath="data/raw/float.wav", sha256=digest(path))
        self.labels["sourceHashes"]["tone"] = digest(path)
        self.performances["sourceHashes"]["tone"] = digest(path)
        self.labels["groups"] = [self.labels["groups"][0]]
        self.update_label(0, startSeconds=0, endSeconds=4 / 44100)
        self.save()
        result = self.run_export(label_ids=["first"])
        self.assertLess(result["labels"][0]["gain"], 1)
        with wave.open(str(self.processed / "export/기본음계/기본음/저취_황.wav")) as stream:
            self.assertEqual(stream.getframerate(), 44100)
            pcm = np.frombuffer(stream.readframes(4), dtype="<i2")
        self.assertGreater(pcm[0], 32000)
        self.assertLess(pcm[1], -32000)
        self.assertEqual(path.read_bytes(), raw)

    def test_column_order_is_declared_in_header_not_assumed(self):
        self.labels["columns"].reverse()
        for group in self.labels["groups"]:
            for row in group["rows"]:
                row.reverse()
        self.save()
        label = self.run_export(label_ids=["first"])["labels"][0]
        self.assertEqual((label["sourceStartFrame"], label["sourceEndFrameExclusive"]), (800, 1600))
        self.assertEqual(label["evidence"]["labelF0Hz"], 300.0)

    def test_invalid_compact_columns_rows_and_evidence_are_rejected(self):
        original = copy.deepcopy(self.labels)
        mutations = [
            lambda: self.labels["columns"].pop(),
            lambda: self.labels["columns"].__setitem__(1, "id"),
            lambda: self.labels["columns"].__setitem__(1, "startMillis"),
            lambda: self.labels["groups"][0]["rows"][0].pop(),
            lambda: self.labels["groups"][0]["rows"][0].append("surplus"),
            lambda: self.labels["groups"][0]["rows"].__setitem__(0, {"id": "first"}),
            lambda: self.update_label(0, startSeconds=True),
            lambda: self.update_label(0, startSeconds="0.1"),
            lambda: self.update_label(0, labelF0Hz=float("inf")),
            lambda: self.update_label(0, labelF0Hz=0),
            lambda: self.update_label(0, yulmyeong=""),
            lambda: self.labels.update(eligibleForNoteScoring="false"),
            lambda: self.labels.update(schemaVersion=1),
            lambda: self.labels["groups"][0].update(filenamePrefix="automatic"),
            lambda: self.labels["groups"][0].update(evidence={"missing": {}}),
            lambda: self.labels["groups"][0].update(evidence={"first": {"file": "override.wav"}}),
            lambda: self.labels["groups"][0].update(regsiter="typo"),
        ]
        for mutate in mutations:
            self.labels = copy.deepcopy(original)
            mutate()
            self.save()
            with self.subTest(mutation=mutate), self.assertRaises(ValueError):
                self.run_export()
            self.assertFalse((self.processed / "export").exists())

    def test_compound_label_uses_anchor_and_keeps_only_explicit_review_overrides(self):
        group = self.labels["groups"][0]
        group["gestureDefaults"] = {"repeatedRises": 2, "labelMeaning": "destination", "interpretation": "fixture"}
        group["evidence"] = {"first": {"method": "tool:reviewed_windows@1", "gesture": {
            "lowerAnchor": {"register": "저취", "yulmyeong": "임", "f0Hz": 250.0,
                            "windowsSeconds": [[.11, .13]], "alternatives": []},
            "destination": {"windowsSeconds": [[.15, .19]], "alternatives": []},
            "analysisReview": {"reason": "test review"}}}}
        self.save()
        label = self.run_export(label_ids=["first"])["labels"][0]
        self.assertEqual(label["displayLabel"], "저취 임→저취 황")
        self.assertEqual(label["file"], "기본음계/기본음/저취_임-저취_황.wav")
        self.assertEqual(label["method"], "tool:reviewed_windows@1")
        self.assertEqual(label["gesture"]["destination"]["f0Hz"], 300.0)
        self.assertEqual(label["gesture"]["repeatedRises"], 2)
        self.assertEqual(label["gesture"]["analysisReview"]["reason"], "test review")
        self.assertNotIn("actualFingeringStatus", label)
        self.assertNotIn("referenceFingerings", label["evidence"])

    def test_generated_paths_reject_case_collisions(self):
        self.labels["groups"][0].update(category="tones", technique="plain", filenamePrefix="none")
        self.labels["groups"][1].update(category="TONES", technique="PLAIN", filenamePrefix="none")
        self.update_label(0, register="low", yulmyeong="note")
        self.update_label(1, register="LOW", yulmyeong="NOTE")
        self.save()
        with self.assertRaisesRegex(ValueError, "Duplicate output"):
            self.run_export()

    def test_performance_dataset_can_have_no_label_groups(self):
        self.labels["groups"] = []
        self.labels["sourceHashes"] = {}
        self.save()
        result = self.run_export()
        self.assertEqual(result["labels"], [])
        self.assertEqual(len(result["performances"]), 1)

    def test_mp3_decoded_frame_crop_and_unchanged_performance_copy(self):
        rate = 44100
        time = np.arange(rate) / rate
        original = np.column_stack([.2 * np.sin(2 * np.pi * 440 * time),
                                    .1 * np.sin(2 * np.pi * 660 * time)])
        path = self.raw / "audio.mp3"
        sf.write(path, original, rate, format="MP3", bitrate_mode="CONSTANT")
        decoded, decoded_rate = sf.read(path, always_2d=True)
        self.assertEqual((decoded_rate, len(decoded)), (rate, rate))
        source = self.sources["sources"][0]
        source.update(rawPath="data/raw/audio.mp3", sha256=digest(path), sampleRateHz=rate, channels=2)
        self.labels["sourceHashes"]["tone"] = source["sha256"]
        self.performances["sourceHashes"]["tone"] = source["sha256"]
        self.performances["performances"][0]["audio"]["file"] = "연주/원음.mp3"
        self.save()
        result = self.run_export()
        label = result["labels"][0]
        self.assertEqual((label["sourceStartFrame"], label["sourceEndFrameExclusive"]), (4410, 8820))
        self.assertEqual((label["sampleRateHz"], label["frames"]), (rate, 4410))
        with wave.open(str(self.processed / "export" / label["file"])) as stream:
            actual = np.frombuffer(stream.readframes(stream.getnframes()), dtype="<i2")
        expected = np.rint(decoded[4410:8820].mean(axis=1) * 32767).astype("<i2")
        np.testing.assert_array_equal(actual, expected)
        decoder = result["audioDecoders"]["tone"]
        self.assertEqual(decoder["format"], "MP3")
        self.assertEqual(decoder["soundfileVersion"], sf.__version__)
        self.assertEqual(decoder["libsndfileVersion"], sf.__libsndfile_version__)
        self.assertEqual(digest(self.processed / "export/연주/원음.mp3"), source["sha256"])
        # Exercise the public CLI as well, including the inherited decoder dependency.
        script = Path(__file__).resolve().parents[1] / "src/audio/export_dataset.py"
        run = subprocess.run([sys.executable, str(script), "--dataset", str(self.dataset),
                              "--root", str(self.root), "--output-dir", str(self.processed / "mp3-cli"),
                              "--label", "first"], capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(digest(self.processed / "mp3-cli" / label["file"]), label["sha256"])

    def test_multiple_sources_share_technique_without_filename_collisions(self):
        source = copy.deepcopy(self.sources["sources"][0])
        path = self.raw / "second.wav"
        with wave.open(str(path), "wb") as stream:
            stream.setparams((2, 2, 8000, 0, "NONE", "not compressed"))
            stream.writeframes((-self.samples).tobytes())
        source.update(id="second-source", rawPath="data/raw/second.wav", sha256=digest(path))
        self.sources["sources"].append(source)
        self.labels["sourceHashes"][source["id"]] = source["sha256"]
        group = copy.deepcopy(self.labels["groups"][0])
        group["sourceId"] = source["id"]
        group["rows"][0][0] = "second"
        self.labels["groups"].append(group)
        self.save()
        result = self.run_export()
        paths = {label["id"]: label["file"] for label in result["labels"]}
        self.assertEqual(paths["first"], "기본음계/기본음/tone/저취_황.wav")
        self.assertEqual(paths["second"], "기본음계/기본음/second-source/저취_황.wav")
        self.assertEqual(len(set(paths.values())), 3)
        selected = self.run_export("selected", label_ids=["second"])
        self.assertEqual(selected["labels"][0]["file"], paths["second"])
        self.assertEqual(selected["labels"][0]["sha256"], result["labels"][2]["sha256"])

    def test_audio_content_detection_hash_check_and_invalid_input(self):
        # A misleading extension cannot turn WAV into MP3 or vice versa.
        path = self.raw / "misnamed.mp3"
        path.write_bytes((self.raw / "audio.wav").read_bytes())
        samples, rate, info = read_audio(path, digest(path))
        self.assertEqual((rate, info["format"]), (8000, "WAV"))
        np.testing.assert_array_equal(samples, self.samples.astype(float) / 32768)
        path = self.raw / "misnamed.wav"
        sf.write(path, .2 * np.sin(2 * np.pi * 330 * np.arange(22050) / 22050),
                 22050, format="MP3", bitrate_mode="VARIABLE")
        samples, rate, info = read_audio(path, digest(path))
        self.assertEqual((samples.shape, rate, info["format"]), ((22050, 1), 22050, "MP3"))
        with self.assertRaisesRegex(ValueError, "Source changed"):
            read_audio(path, "0" * 64)
        path.write_bytes(b"ID3not-valid-audio")
        with self.assertRaisesRegex(ValueError, "Cannot decode MP3"):
            read_audio(path, digest(path))
        sf.write(path, np.zeros(100), 8000, format="FLAC")
        with self.assertRaisesRegex(ValueError, "Supported audio"):
            read_audio(path, digest(path))

    def test_source_namespace_rejects_unsafe_and_case_colliding_ids(self):
        for source_id in ("../escape", "CON", "a/b", "TONE"):
            source = {**self.sources["sources"][0], "id": source_id, "sha256": "1" * 64}
            self.sources["sources"] = self.sources["sources"][:2] + [source]
            self.labels["sourceHashes"][source_id] = source["sha256"]
            group = copy.deepcopy(self.labels["groups"][0])
            group["sourceId"] = source_id
            group["rows"][0][0] = "second"
            self.labels["groups"] = self.labels["groups"][:2] + [group]
            self.save()
            with self.subTest(source_id=source_id), self.assertRaises(ValueError):
                self.run_export(label_ids=["first"])
            self.assertFalse((self.processed / "export").exists())

    def test_source_alias_cannot_hide_duplicate_intervals(self):
        self.sources["sources"].append({**self.sources["sources"][0], "id": "alias"})
        self.labels["sourceHashes"]["alias"] = self.labels["sourceHashes"]["tone"]
        group = self.labels["groups"][1]
        group["sourceId"] = "alias"
        self.update_label(1, startSeconds=.1, endSeconds=.2)
        self.save()
        with self.assertRaisesRegex(ValueError, "Duplicate source interval"):
            self.run_export()

    def test_duplicate_source_intervals_fail_even_with_conflicting_names_or_selection(self):
        for yulmyeong in ("황", "태"):
            self.update_label(1, startSeconds=.1, endSeconds=.2, yulmyeong=yulmyeong)
            self.save()
            with self.subTest(yulmyeong=yulmyeong), self.assertRaisesRegex(ValueError, "Duplicate source interval"):
                self.run_export(label_ids=["first"])
            self.assertFalse((self.processed / "export").exists())

    def test_distinct_seconds_that_round_to_identical_frames_are_rejected(self):
        self.update_label(1, startSeconds=.100001, endSeconds=.200001, yulmyeong="태")
        self.save()
        with self.assertRaisesRegex(ValueError, "Duplicate source frame interval"):
            self.run_export(label_ids=["first"])
        self.assertFalse((self.processed / "export").exists())

    def test_analysis_windows_are_finite_ordered_and_inside_clip(self):
        gesture = {
            "lowerAnchor": {"register": "저취", "yulmyeong": "임", "f0Hz": 250,
                            "windowsSeconds": [[.11, .13]]},
            "destination": {"windowsSeconds": [[.15, .19]]},
            "analysisReview": {"rejectedWindow": [.135, .14]}}
        mutations = [
            lambda g: g["lowerAnchor"].update(windowsSeconds="not-a-window-list"),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.12]]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[True, .12]]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.12, float("inf")]]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.12, float("nan")]]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.13, .12]]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.09, .12]]),
            lambda g: g["destination"].update(windowsSeconds=[[999, 1000]]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.12, .13], [.11, .12]]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.11, .125], [.12, .13]]),
            lambda g: g["destination"].update(windowsSeconds=[[.12, .14]]),
            lambda g: g["analysisReview"].update(rejectedWindow=[.09, .12]),
            lambda g: g["analysisReview"].update(rejectedWindow=[.11, .12]),
            lambda g: g["lowerAnchor"].update(windowsSeconds=[[.110001, .110002]]),
        ]
        for mutate in mutations:
            invalid = copy.deepcopy(gesture)
            mutate(invalid)
            self.labels["groups"][0]["evidence"] = {"first": {"gesture": invalid}}
            self.save()
            with self.subTest(mutate=mutate), self.assertRaisesRegex(ValueError, "[Ww]indow"):
                self.run_export(label_ids=["first"])
            self.assertFalse((self.processed / "export").exists())
        self.labels["groups"][0]["evidence"] = {"first": {"gesture": gesture}}
        self.save()
        self.run_export(label_ids=["first"])


if __name__ == "__main__":
    unittest.main()
