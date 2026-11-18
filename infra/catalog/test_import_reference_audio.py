import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("import_reference_audio", Path(__file__).with_name("import_reference_audio.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReferenceAudioImportTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        payload = b"verified-converter-output-fixture"
        (self.root / "clip.m4a").write_bytes(payload)
        asset_id = hashlib.sha256(b"danso\nngc-danso-2338.tone-001").hexdigest()
        digest = hashlib.sha256(payload).hexdigest()
        self.asset = {
            "assetId": asset_id, "datasetId": "danso", "labelId": "ngc-danso-2338.tone-001",
            "category": "기본음계", "technique": "평취 강", "register": "평취", "yulmyeong": "중",
            "displayLabel": "평취 중", "file": "clip.m4a",
            "objectKey": f"reference-audio/v1/danso/{asset_id}/{digest}.m4a",
            "contentType": "audio/mp4", "sha256": digest, "bytes": len(payload), "durationMs": 5000,
            "sampleRateHz": 44100, "channels": 1, "review": "inferred", "eligibleForNoteScoring": False,
            "metadata": {},
        }
        label = {key: self.asset[key] for key in ("category", "technique", "register", "yulmyeong", "displayLabel", "review", "eligibleForNoteScoring")}
        label.update(id=self.asset["labelId"], sourceId="source-1", sha256="1" * 64,
                     bytes=441044, frames=220500, sampleRateHz=44100, channels=1)
        self.asset["metadata"] = {
            "label": label,
            "source": {"id": "source-1", "attribution": "국립국악원", "license": "KOGL-1", "sourceUrl": "https://example.test/source"},
            "wav": {key: label[key] for key in ("sha256", "bytes", "frames", "sampleRateHz", "channels")},
            "catalogSha256": "2" * 64,
            "encoding": {"decodedFrames": 220800, "endPaddingFrames": 300},
        }

    def manifest(self, assets=None):
        path = self.root / "manifest.json"
        path.write_text(json.dumps({"schemaVersion": 1, "assets": assets or [self.asset]}, ensure_ascii=False), encoding="utf-8")
        return path

    def test_verified_manifest_generates_draft_idempotent_import(self):
        rows = module.load_assets(self.manifest())
        sql = module.render_sql(rows)
        self.assertIn("START TRANSACTION", sql)
        self.assertIn("CHECK (conflict_count = 0)", sql)
        self.assertIn("WHERE NOT EXISTS", sql)
        self.assertNotIn("UPDATE reference_audio_assets", sql)
        self.assertNotIn("PUBLISHED", sql)
        self.assertEqual(sql, module.render_sql(rows))

    def test_corrupted_output_is_rejected(self):
        path = self.manifest()
        (self.root / "clip.m4a").write_bytes(b"different")
        with self.assertRaisesRegex(ValueError, "file integrity"):
            module.load_assets(path)

    def test_path_escape_is_rejected(self):
        self.asset["file"] = "../clip.m4a"
        with self.assertRaisesRegex(ValueError, "relative file"):
            module.load_assets(self.manifest())

    def test_wrong_identity_or_recording_key_is_rejected(self):
        for field, value in [("assetId", "0" * 64), ("objectKey", "recordings/test.m4a")]:
            with self.subTest(field=field):
                original = self.asset[field]
                self.asset[field] = value
                with self.assertRaises(ValueError):
                    module.load_assets(self.manifest())
                self.asset[field] = original

    def test_duplicate_label_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "duplicate"):
            module.load_assets(self.manifest([self.asset, self.asset.copy()]))

    def test_inferred_cannot_be_scoring_truth(self):
        self.asset["eligibleForNoteScoring"] = True
        with self.assertRaisesRegex(ValueError, "reviewed"):
            module.load_assets(self.manifest())

    def test_invalid_numeric_and_manifest_versions_are_rejected(self):
        self.asset["durationMs"] = True
        with self.assertRaises(ValueError):
            module.load_assets(self.manifest())
        path = self.root / "invalid.json"
        path.write_text('{"schemaVersion":2,"assets":[]}', encoding="utf-8")
        with self.assertRaises(ValueError):
            module.load_assets(path)

    def test_sql_strings_are_encoded_not_interpolated(self):
        self.asset["displayLabel"] = "중'); DROP TABLE pitches; --"
        self.asset["metadata"]["label"]["displayLabel"] = self.asset["displayLabel"]
        sql = module.render_sql(module.load_assets(self.manifest()))
        self.assertNotIn("DROP TABLE pitches", sql)
        self.assertIn(self.asset["displayLabel"].encode().hex(), sql)

    def test_missing_provenance_or_promoted_review_is_rejected(self):
        self.asset["review"] = "reviewed"
        self.asset["eligibleForNoteScoring"] = True
        with self.assertRaisesRegex(ValueError, "original label"):
            module.load_assets(self.manifest())
        self.asset["metadata"] = {"unrelated": 1}
        with self.assertRaisesRegex(ValueError, "provenance"):
            module.load_assets(self.manifest())


if __name__ == "__main__":
    unittest.main()
