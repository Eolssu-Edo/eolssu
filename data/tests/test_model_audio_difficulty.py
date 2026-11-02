"""Small hand-derived written-score cases; no audio or producer-derived oracle."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from data.src.catalog.model_audio_difficulty import classify_difficulty, rate_bundle


def score(pitches=(("황", 0), ("태", 0), ("중", 0)), *, split=1, marked=False, rests=0):
    events, cells = [], []
    for j in range(len(pitches) // split + rests):
        units = []
        for col in range(split if j < len(pitches) // split else 1):
            duration = 12 // (split if j < len(pitches) // split else 1)
            eid, cid = f"c0-j{j}-r0-u{col}", f"c0-j{j}"
            pitch = pitches[j * split + col] if j < len(pitches) // split else None
            event = {"id": eid, "kind": "NOTE" if pitch else "REST", "yul": None, "octave": None, "startTick": j * 12 + col * duration,
                     "durationTick": duration, "cellId": cid, "breathAfter": False, "sigimsae": ["요성표"] if marked and pitch else []}
            if pitch:
                event.update(yul=pitch[0], octave=pitch[1])
            units.append({"row": 0, "col": col, "symbol": ("청" * pitch[1] if pitch[1] == 1 else "중청" if pitch[1] == 2 else "") + pitch[0] if pitch else "쉼", "eventId": eid})
            events.append(event)
        cells.append({"id": cid, "index": j, "units": units})
    return {"schemaVersion": 1, "score": {"title": "수기 기대", "instrument": "DANSO", "part": 1, "sourceId": "manual", "scoreVersion": 1},
            "music": {"hwangHz": 311.13, "jangdan": "", "bpmUnit": "QUARTER", "ticksPerBeat": 12, "ticksPerJeonggan": 12},
            "columns": [{"index": 0, "cells": cells}], "events": events,
            "provenance": {"sourceId": "manual", "jgbSha256": "a" * 64, "converter": "manual/v1", "review": "inferred"}}


class DifficultyTests(unittest.TestCase):
    def test_slow_stepwise_is_easier_than_fast_wide_marked_subdivisions(self):
        easy = classify_difficulty(score(), 60)
        hard = classify_difficulty(score((("황", 0), ("황", 2)) * 2, split=4, marked=True), 120)
        self.assertEqual(1, easy["level"])
        self.assertEqual(3, hard["level"])

    def test_medium_and_hard_use_only_existing_three_frontend_labels(self):
        sources = [(score(), 60), (score(), 240), (score((("황", 0), ("황", 2)) * 2, split=4, marked=True), 120)]
        results = [classify_difficulty(s, bpm) for s, bpm in sources]
        self.assertEqual([1, 2, 3], [r["level"] for r in results])
        self.assertEqual(["입문", "초급", "중급"], [r["label"] for r in results])
        self.assertEqual([0, 3, 10], [r["measurements"]["totalPoints"] for r in results])
        self.assertTrue(all("provisional" in r["policyVersion"] for r in results))
        self.assertFalse(any(r["level"] in (4, 5) for r in results))

    def test_rests_do_not_add_attacks_or_dilute_sounding_rate(self):
        plain, padded = classify_difficulty(score(), 60), classify_difficulty(score(rests=2), 60)
        self.assertEqual(plain, padded)
        self.assertEqual(3, padded["measurements"]["noteCount"])
        self.assertEqual(3.0, padded["measurements"]["soundingSeconds"])
        self.assertEqual(1.0, padded["measurements"]["attacksPerSoundingSecond"])

    def test_octaves_and_repeated_annotation_names_count_once_per_note(self):
        source = score((("황", 0), ("황", 2)), marked=True)
        source["events"][0]["sigimsae"] *= 3
        result = classify_difficulty(source, 60)
        self.assertEqual(24, result["measurements"]["pitchRangeSemitones"])
        self.assertEqual(1, result["measurements"]["largeLeapRatio"])
        self.assertEqual(1, result["measurements"]["annotatedNoteRatio"])
        self.assertEqual(2, result["axisPoints"]["pitchMovement"])

    def test_beat_units_use_ticks_per_declared_beat_without_reinterpreting_tempo(self):
        for unit in ("QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT"):
            source = score()
            source["music"]["bpmUnit"] = unit
            self.assertEqual(1.0, classify_difficulty(source, 60)["measurements"]["shortestNoteSeconds"])

    def test_deterministic_and_does_not_modify_original_ids_or_source(self):
        source = score(marked=True, rests=1)
        before = deepcopy(source)
        first = classify_difficulty(source, 90)
        self.assertEqual(first, classify_difficulty(source, 90))
        self.assertEqual(before, source)
        self.assertEqual(json.dumps(before), json.dumps(source))

    def test_malformed_tempo_and_scores_without_notes_are_rejected(self):
        for bpm in (0, -1, True, "60", None, float("nan"), float("inf")):
            with self.subTest(bpm=bpm), self.assertRaisesRegex(ValueError, "bpm"):
                classify_difficulty(score(), bpm)
        for source in (score((), rests=1), score(())):
            with self.assertRaises(ValueError):
                classify_difficulty(source, 60)


class DifficultyBundleTests(unittest.TestCase):
    def prepare(self, directory):
        bundle = Path(directory) / "batch"
        bundle.mkdir()
        path = bundle / "source.common.json"
        raw = json.dumps(score(), ensure_ascii=False).encode()
        path.write_bytes(raw)
        case = {"caseId": "manual-1", "sourceId": "manual", "defaultBpm": 60, "bpmUnit": "QUARTER",
                "files": [{"role": "canonical_common_score", "path": path.name,
                           "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw)}]}
        manifest = {"schema": "eolssu-draft-model-audio-batch/v1", "counts": {"cases": 1}, "cases": [case]}
        (bundle / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        return bundle, path, manifest

    def test_public_cli_records_identity_and_hash_without_mutating_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle, source, _ = self.prepare(directory)
            before = {p.name: p.read_bytes() for p in bundle.iterdir()}
            output = Path(directory) / "difficulty.json"
            command = [sys.executable, "-m", "data.src.catalog.model_audio_difficulty", "--bundle", str(bundle), "--out", str(output)]
            process = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, process.returncode, process.stderr)
            result = json.loads(output.read_bytes())
            self.assertEqual({"1": 1}, result["histogram"])
            self.assertEqual("manual", result["cases"][0]["sourceId"])
            self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), result["cases"][0]["inputSha256"])
            self.assertEqual(before, {p.name: p.read_bytes() for p in bundle.iterdir()})
            rerun = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(0, rerun.returncode)  # Never overwrite an existing evidence file.

    def test_sha_identity_and_path_mismatch_fail_closed(self):
        for change in ("hash", "source", "path", "duplicate"):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                bundle, _, manifest = self.prepare(directory)
                case = manifest["cases"][0]
                if change == "hash":
                    case["files"][0]["sha256"] = "0" * 64
                elif change == "source":
                    case["sourceId"] = "another"
                elif change == "path":
                    case["files"][0]["path"] = "../outside.json"
                else:
                    manifest["cases"].append(deepcopy(case))
                    manifest["counts"]["cases"] = 2
                (bundle / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
                with self.assertRaises(ValueError):
                    rate_bundle(bundle)

    def test_cli_refuses_output_inside_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            bundle, _, _ = self.prepare(directory)
            output = bundle / "difficulty.json"
            process = subprocess.run([sys.executable, "-m", "data.src.catalog.model_audio_difficulty", "--bundle", str(bundle), "--out", str(output)], capture_output=True)
            self.assertNotEqual(0, process.returncode)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
