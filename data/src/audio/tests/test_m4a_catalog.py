import hashlib
import json
import math
import os
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import wave

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from m4a_catalog import convert_catalogs


class M4aCatalogTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.source = self.root / "input"
        self.source.mkdir()
        self.wav = self.source / "tone.wav"
        self.frames = 5773
        self.rate = 48000
        with wave.open(str(self.wav), "wb") as output:
            output.setparams((1, 2, self.rate, 0, "NONE", "not compressed"))
            output.writeframes(b"".join(struct.pack("<h", round(6000 * math.sin(2 * math.pi * 440 * i / self.rate)))
                                       for i in range(self.frames)))
        self.original = self.wav.read_bytes()
        self.label = {
            "id": "source.tone-001", "sourceId": "source", "category": "기본음계",
            "technique": "평취 강", "register": "평취", "yulmyeong": "중",
            "displayLabel": "평취 중", "file": "tone.wav", "review": "inferred",
            "eligibleForNoteScoring": False, "sampleRateHz": self.rate, "channels": 1,
            "frames": self.frames, "format": "WAV_PCM16", "bytes": len(self.original),
            "sha256": hashlib.sha256(self.original).hexdigest(),
        }
        self.catalog_data = {
            "schemaVersion": 2, "datasetId": "danso", "instrument": {"name": "단소"},
            "sources": [{"id": "source", "license": "KOGL-1", "attribution": "합성 테스트",
                         "sourceUrl": "https://example.test/source"}], "labels": [self.label],
        }
        self.catalog = self.source / "catalog.json"
        self.save_catalog()

    def save_catalog(self):
        self.catalog.write_text(json.dumps(self.catalog_data, ensure_ascii=False), encoding="utf-8")

    def convert(self, output="output"):
        return convert_catalogs([self.catalog], self.root / output,
                                ffmpeg=os.environ.get("FFMPEG_BINARY"),
                                ffprobe=os.environ.get("FFPROBE_BINARY"))

    def test_real_conversion_preserves_identity_provenance_and_source(self):
        result = self.convert()
        asset = result["assets"][0]
        expected_id = hashlib.sha256(b"danso\nsource.tone-001").hexdigest()
        self.assertEqual(asset["assetId"], expected_id)
        self.assertEqual(asset["datasetId"], "danso")
        self.assertEqual(asset["labelId"], self.label["id"])
        self.assertEqual(asset["sampleRateHz"], 48000)
        self.assertEqual(asset["channels"], 1)
        self.assertEqual(asset["review"], "inferred")
        self.assertFalse(asset["eligibleForNoteScoring"])
        self.assertEqual(asset["metadata"]["label"], self.label)
        self.assertEqual(asset["metadata"]["source"], self.catalog_data["sources"][0])
        self.assertEqual(self.wav.read_bytes(), self.original)
        generated = self.root / "output" / asset["file"]
        self.assertEqual(hashlib.sha256(generated.read_bytes()).hexdigest(), asset["sha256"])
        self.assertEqual(asset["objectKey"], f"reference-audio/v1/danso/{expected_id}/{asset['sha256']}.m4a")
        self.assertEqual(asset["contentType"], "audio/mp4")
        self.assertEqual(asset["durationMs"], round(self.frames * 1000 / self.rate))
        self.assertEqual(result, json.loads((self.root / "output/manifest.json").read_text(encoding="utf-8")))

    def test_source_hash_mismatch_does_not_publish_output(self):
        self.label["sha256"] = "0" * 64
        self.save_catalog()
        with self.assertRaisesRegex(ValueError, "hash"):
            self.convert()
        self.assertFalse((self.root / "output").exists())

    def test_missing_attribution_is_rejected_before_conversion(self):
        del self.catalog_data["sources"][0]["attribution"]
        self.save_catalog()
        with self.assertRaisesRegex(ValueError, "attribution"):
            self.convert()
        self.assertFalse((self.root / "output").exists())

    def test_existing_output_is_untouched(self):
        output = self.root / "output"
        output.mkdir()
        sentinel = output / "keep.txt"
        sentinel.write_text("unchanged", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "already exists"):
            self.convert()
        self.assertEqual(sentinel.read_text(encoding="utf-8"), "unchanged")

    def test_duplicate_label_is_rejected(self):
        self.catalog_data["labels"].append(dict(self.label))
        self.save_catalog()
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.convert()

    def test_wav_header_mismatch_is_rejected(self):
        self.label["sampleRateHz"] = 44100
        self.save_catalog()
        with self.assertRaisesRegex(ValueError, "header"):
            self.convert()

    def test_path_escape_is_rejected(self):
        self.label["file"] = "../input/tone.wav"
        self.save_catalog()
        with self.assertRaisesRegex(ValueError, "traversal"):
            self.convert()

    def test_failed_decode_does_not_publish_partial_manifest(self):
        with patch("m4a_catalog.validate_m4a", side_effect=ValueError("corrupt M4A decode")):
            with self.assertRaisesRegex(ValueError, "corrupt"):
                self.convert()
        self.assertFalse((self.root / "output").exists())
        self.assertEqual(self.wav.read_bytes(), self.original)
        self.assertEqual(list(self.root.glob(".reference-audio-*")), [])

    def test_44100_stereo_duration_and_channels_are_preserved(self):
        self.rate = 44100
        frames = 10241
        with wave.open(str(self.wav), "wb") as output:
            output.setparams((2, 2, self.rate, 0, "NONE", "not compressed"))
            output.writeframes(b"".join(struct.pack("<hh", round(4000 * math.sin(2 * math.pi * 440 * i / self.rate)),
                                                   round(4000 * math.sin(2 * math.pi * 660 * i / self.rate)))
                                       for i in range(frames)))
        original = self.wav.read_bytes()
        self.label.update(sampleRateHz=self.rate, channels=2, frames=frames, bytes=len(original),
                          sha256=hashlib.sha256(original).hexdigest())
        self.save_catalog()
        asset = self.convert()["assets"][0]
        self.assertEqual((asset["sampleRateHz"], asset["channels"]), (44100, 2))
        self.assertEqual(asset["durationMs"], round(frames * 1000 / self.rate))
        self.assertEqual(self.wav.read_bytes(), original)

    def test_rerun_into_new_directory_is_deterministic(self):
        first = self.convert("first")
        second = self.convert("second")
        self.assertEqual(first, second)


if __name__ == "__main__":
    unittest.main()
