import copy
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import wave

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from export_dataset import export_dataset, read_wave


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class ExportTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.dataset = self.root / "data/datasets/example"
        self.dataset.mkdir(parents=True)
        self.raw = self.root / "raw"
        self.raw.mkdir()
        # Known samples, no pitch inference or fixtures from the instrument under test.
        self.samples = np.column_stack([np.arange(8000) % 1000, -(np.arange(8000) % 400)]).astype("<i2")
        with wave.open(str(self.raw / "audio.wav"), "wb") as stream:
            stream.setnchannels(2)
            stream.setsampwidth(2)
            stream.setframerate(8000)
            stream.writeframes(self.samples.tobytes())
        (self.raw / "score.pdf").write_bytes(b"%PDF-1.4\nunchanged score fixture\n%%EOF")
        self.sources = {"schemaVersion": 1, "datasetId": "example", "instrument": "검증 악기",
                        "pathBase": "repository_root", "sources": [
            {"id": "tone", "kind": "audio", "title": "원음", "path": "raw/audio.wav",
             "sha256": digest(self.raw / "audio.wav"), "license": "test", "sourceUrl": "https://example.org/audio", "attribution": "fixture"},
            {"id": "score", "kind": "score", "title": "악보", "path": "raw/score.pdf",
             "sha256": digest(self.raw / "score.pdf"), "pdfPageCount": 10,
             "license": "test", "sourceUrl": "https://example.org/score", "attribution": "fixture"}]}
        self.labels = {"schemaVersion": 1, "datasetId": "example",
                       "sourceHashes": {"tone": self.sources["sources"][0]["sha256"]}, "labels": [
            {"id": "first", "sourceId": "tone", "startSeconds": .1, "endSeconds": .2,
             "category": "기본음계", "technique": "기본음", "register": "저취", "yulmyeong": "황",
             "displayLabel": "저취 황", "file": "기본음/first.wav", "labelStatus": "inferred"},
            {"id": "repeat", "sourceId": "tone", "startSeconds": .3, "endSeconds": .5,
             "category": "시김새", "technique": "요성", "register": "저취", "yulmyeong": "황",
             "displayLabel": "저취 황", "file": "시김새/repeat.wav", "labelStatus": "inferred"}]}
        self.performances = {"schemaVersion": 1, "datasetId": "example",
                             "sourceHashes": {s["id"]: s["sha256"] for s in self.sources["sources"]},
                             "performances": [{"id": "piece", "title": "곡",
            "audio": {"sourceId": "tone", "file": "연주/원음.wav"}, "scores": [
                {"sourceId": "score", "file": "연주/악보.pdf", "notation": "staff", "pdfPages": [2],
                 "coverage": "성악용", "samePerformanceEditionVerified": False},
                {"sourceId": "score", "file": "연주/악보.pdf", "notation": "jeongganbo", "pdfPages": [3],
                 "coverage": "단소용 일부", "samePerformanceEditionVerified": False}]}]}
        self.save()

    def save(self):
        for name in ("sources", "labels", "performances"):
            (self.dataset / f"{name}.json").write_text(json.dumps(getattr(self, name), ensure_ascii=False), encoding="utf-8")

    def run_export(self, name="export", **kwargs):
        return export_dataset(self.dataset, self.root / name, root=self.root, **kwargs)

    def test_common_format_handles_other_instrument_names_and_literal_labels(self):
        for index, instrument in enumerate(("sogeum", "danso", "jeongak-daegeum")):
            for doc in (self.sources, self.labels, self.performances):
                doc["datasetId"] = instrument
            self.labels["labels"][0]["register"] = "직접 지정한 취법"
            self.labels["labels"][0]["yulmyeong"] = "직접 지정한 율명"
            self.save()
            result = self.run_export(f"export-{index}", label_ids=["first"])
            self.assertEqual(result["datasetId"], instrument)
            self.assertEqual(len(result["labels"]), 1)
            label = result["labels"][0]
            self.assertEqual((label["register"], label["yulmyeong"]), ("직접 지정한 취법", "직접 지정한 율명"))
            self.assertEqual((label["sourceStartFrame"], label["sourceEndFrameExclusive"]), (800, 1600))
            with wave.open(str(self.root / f"export-{index}" / label["file"])) as stream:
                actual = np.frombuffer(stream.readframes(stream.getnframes()), dtype="<i2")
            expected = np.rint(self.samples[800:1600].astype(float).mean(axis=1) / 32768 * 32767).astype("<i2")
            np.testing.assert_array_equal(actual, expected)

    def test_editing_label_changes_exact_interval_without_analysis(self):
        self.labels["labels"][0].update(startSeconds=.123, endSeconds=.456)
        self.save()
        result = self.run_export(label_ids=["first"])
        self.assertEqual(result["labels"][0]["frames"], 2664)
        self.assertEqual(result["labels"][0]["sourceStartFrame"], 984)

    def test_full_export_retains_repeat_and_copies_performance_and_shared_pdf_unchanged(self):
        result = self.run_export()
        self.assertEqual([v["id"] for v in result["labels"]], ["first", "repeat"])
        self.assertEqual(digest(self.root / "export/연주/원음.wav"), digest(self.raw / "audio.wav"))
        self.assertEqual(digest(self.root / "export/연주/악보.pdf"), digest(self.raw / "score.pdf"))
        self.assertEqual(len(list((self.root / "export").rglob("*.pdf"))), 1)
        self.assertFalse(result["performances"][0]["scores"][1]["samePerformanceEditionVerified"])
        self.assertEqual(result["performances"][0]["scores"][1]["coverage"], "단소용 일부")

    def test_selective_export_does_not_require_unselected_score_or_alter_full_export(self):
        self.run_export("full")
        before = {p.relative_to(self.root / "full"): digest(p) for p in (self.root / "full").rglob("*") if p.is_file()}
        (self.raw / "score.pdf").unlink()
        result = self.run_export("selection", technique="기본음", register="저취", yulmyeong="황")
        self.assertEqual([v["id"] for v in result["labels"]], ["first"])
        self.assertEqual(result["performances"], [])
        after = {p.relative_to(self.root / "full"): digest(p) for p in (self.root / "full").rglob("*") if p.is_file()}
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
            self.assertFalse((self.root / "export").exists())

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
            self.assertFalse((self.root / "export").exists())

    def test_invalid_ids_ranges_pages_and_collisions_fail_before_output(self):
        originals = copy.deepcopy((self.sources, self.labels, self.performances))
        mutations = [
            lambda: self.labels["labels"][0].update(sourceId="absent"),
            lambda: self.labels["labels"][1].update(id="first"),
            lambda: self.labels["labels"][0].update(startSeconds=-.1),
            lambda: self.labels["labels"][0].update(endSeconds=1.1),
            lambda: self.labels["labels"][0].update(startSeconds=float("nan")),
            lambda: self.labels["labels"][0].update(endSeconds=.1),
            lambda: self.performances["performances"][0]["scores"][0].update(pdfPages=[11]),
            lambda: self.labels["labels"][1].update(file="기본음/FIRST.wav"),
            lambda: self.labels["labels"][1].update(file="기본음/first.wav/child.wav"),
            lambda: self.labels.update(datasetId="wrong-dataset"),
        ]
        for mutate in mutations:
            self.sources, self.labels, self.performances = copy.deepcopy(originals)
            mutate()
            self.save()
            with self.subTest(mutate=mutate), self.assertRaises((ValueError, OSError)):
                self.run_export()
            self.assertFalse((self.root / "export").exists())

    def test_unsafe_paths_and_reserved_output_names_are_rejected(self):
        for name in ("../outside.wav", "C:/outside.wav", "/outside.wav", "a\\..\\outside.wav",
                     "catalog.json", "CON.wav", "a./b.wav", "a:b.wav"):
            self.labels["labels"][0]["file"] = name
            self.save()
            with self.subTest(path=name), self.assertRaises(ValueError):
                self.run_export()
            self.assertFalse((self.root / "export").exists())

    def test_missing_selected_input_leaves_no_partial_output(self):
        (self.raw / "score.pdf").unlink()
        with self.assertRaises((ValueError, OSError)):
            self.run_export()
        self.assertFalse((self.root / "export").exists())
        self.assertEqual(list(self.root.glob(".dataset-export-*")), [])

    def test_cli_uses_the_three_manifests_and_selects_one_label(self):
        script = Path(__file__).resolve().parents[1] / "src/export_dataset.py"
        result = subprocess.run([sys.executable, str(script), "--dataset", str(self.dataset), "--root", str(self.root),
                                 "--output-dir", str(self.root / "cli"), "--label", "repeat"],
                                capture_output=True, text=True, encoding="utf-8")
        self.assertEqual(result.returncode, 0, result.stderr)
        result = json.loads((self.root / "cli/catalog.json").read_text(encoding="utf-8"))
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
        source.update(path="raw/float.wav", sha256=digest(path))
        self.labels["sourceHashes"]["tone"] = digest(path)
        self.performances["sourceHashes"]["tone"] = digest(path)
        self.labels["labels"] = [self.labels["labels"][0]]
        self.labels["labels"][0].update(startSeconds=0, endSeconds=4 / 44100)
        self.save()
        result = self.run_export(label_ids=["first"])
        self.assertLess(result["labels"][0]["gain"], 1)
        with wave.open(str(self.root / "export/기본음/first.wav")) as stream:
            self.assertEqual(stream.getframerate(), 44100)
            pcm = np.frombuffer(stream.readframes(4), dtype="<i2")
        self.assertGreater(pcm[0], 32000)
        self.assertLess(pcm[1], -32000)
        self.assertEqual(path.read_bytes(), raw)


if __name__ == "__main__":
    unittest.main()
