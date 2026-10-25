import copy
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src/catalog"))
from source_catalog import load_source_catalog

class SourceCatalogTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.catalog = self.root / "data/catalog/example"
        self.catalog.mkdir(parents=True)
        raw = self.root / "data/raw/bundle.bin"
        raw.parent.mkdir()
        raw.write_bytes(b"synthetic original bundle")
        self.document = {"schemaVersion": 2, "datasetId": "example", "pathBase": "repository_root", "sources": [
            {"id": "bundle", "kind": "bundle", "title": "bundle", "rawPath": "data/raw/bundle.bin",
             "bytes": raw.stat().st_size, "sha256": hashlib.sha256(raw.read_bytes()).hexdigest(),
             "license": None, "sourceUrl": None, "attribution": None, "notes": ["unverified source"]}]}
        # Run the real CLI from an isolated checkout layout, with its local module.
        scripts = self.root / "data/src/catalog"
        scripts.mkdir(parents=True)
        for path in (Path(__file__).resolve().parents[1] / "src/catalog").glob("*.py"):
            shutil.copyfile(path, scripts / path.name)
        self.script = scripts / "verify_sources.py"

    def save(self):
        (self.catalog / "sources.json").write_text(json.dumps(self.document), encoding="utf-8")

    def run_verify(self, *args):
        self.save()
        return subprocess.run([sys.executable, "-X", "utf8", str(self.script), "--dataset", "example", *args],
                              capture_output=True, text=True, encoding="utf-8")

    def test_verify_cli_keeps_legacy_nulls_and_rejects_unknown_profile(self):
        legacy = self.run_verify()
        self.assertEqual(legacy.returncode, 0, legacy.stderr)
        self.assertIn("일치 1 / 1", legacy.stdout)
        self.document.update(schemaVersion=3, sourceProfiles={})
        self.document["sources"][0]["profile"] = "missing"
        invalid = self.run_verify()
        self.assertNotEqual(invalid.returncode, 0)
        self.assertIn("profile", invalid.stderr.lower())

    def test_profile_override_is_shallow_null_preserving_and_independently_owned(self):
        source = self.document["sources"][0]
        source.update(profile="common", redistribution={"conditions": ["individual"]})
        self.document.update(schemaVersion=3, sourceProfiles={"common": {
            "license": "profile license", "redistribution": {"conditions": ["common"], "original": "allowed"},
            "notes": ["common notes"]}})
        for name in ("second", "third"):
            self.document["sources"].append({"id": name, "profile": "common"})
        self.save()
        resolved = load_source_catalog(self.catalog / "sources.json")
        first, second, third = resolved["sources"]
        self.assertNotIn("sourceProfiles", resolved)
        self.assertTrue(all("profile" not in row for row in resolved["sources"]))
        self.assertIsNone(first["license"])
        self.assertEqual(first["redistribution"], {"conditions": ["individual"]})
        self.assertEqual(first["notes"], ["unverified source"])
        second["redistribution"]["conditions"].append("changed")
        second["notes"].append("changed")
        self.assertEqual(third["redistribution"], {"conditions": ["common"], "original": "allowed"})
        self.assertEqual(third["notes"], ["common notes"])
        self.assertEqual(load_source_catalog(self.catalog / "sources.json")["sources"][1]["notes"], ["common notes"])

    def test_verify_cli_accepts_v2_v3_and_reports_missing_size_and_hash_failures(self):
        for version in (2, 3):
            self.document["schemaVersion"] = version
            if version == 3:
                self.document["sourceProfiles"] = {"bundle": {"kind": "bundle", "license": "common"}}
                self.document["sources"][0].update(profile="bundle")
            for fault in (None, "missing", "size", "hash"):
                root = self.root / f"raw-v{version}-{fault}"
                raw = root / "data/raw/bundle.bin"
                raw.parent.mkdir(parents=True)
                if fault != "missing":
                    value = (self.root / "data/raw/bundle.bin").read_bytes()
                    raw.write_bytes(value + b"!" if fault == "size" else b"x" * len(value) if fault == "hash" else value)
                run = self.run_verify("--root", str(root))
                with self.subTest(version=version, fault=fault):
                    self.assertEqual(run.returncode, 0 if fault is None else 1, run.stderr)
                    self.assertIn({None: "일치 1 / 1", "missing": "없음", "size": "크기 다름", "hash": "해시 다름"}[fault], run.stdout)

    def test_invalid_profile_shapes_references_and_unique_fields_are_rejected(self):
        self.document.update(schemaVersion=3, sourceProfiles={"common": {"license": "test"}})
        self.document["sources"][0]["profile"] = "common"
        valid = copy.deepcopy(self.document)
        cases = []
        for value in (None, [], "wrong"):
            cases.append({**valid, "sourceProfiles": value})
            cases.append({**valid, "sourceProfiles": {"common": value}})
        missing = copy.deepcopy(valid)
        missing.pop("sourceProfiles")
        cases.extend([missing, {**valid, "sourceProfiles": {"": {}}}])
        for value in (None, "", "  ", 1, True, [], {}, "absent"):
            invalid = copy.deepcopy(valid)
            invalid["sources"][0]["profile"] = value
            cases.append(invalid)
        for field in ("id", "title", "sha256", "bytes", "rawPath", "annotationPath", "sourceItemId",
                      "durationSeconds", "sampleRateHz", "pdfPageCount", "profile", "sourceProfiles", "unknown"):
            invalid = copy.deepcopy(valid)
            # Unused profiles must not conceal invalid fields either.
            invalid["sourceProfiles"]["unused"] = {field: "hidden"}
            cases.append(invalid)
        for index, invalid in enumerate(cases):
            self.document = invalid
            with self.subTest(index=index):
                run = self.run_verify()
                self.assertEqual(run.returncode, 1, run.stdout)
                self.assertIn("profile", run.stderr.lower())

    def test_old_versions_cannot_hide_profiles_and_versions_are_strict(self):
        valid = copy.deepcopy(self.document)
        invalids = [{**valid, "schemaVersion": version} for version in (None, True, 2.0, "3", 1, 4)]
        invalids.append({**valid, "sourceProfiles": {}})
        invalid = copy.deepcopy(valid)
        invalid["sources"][0]["profile"] = None
        invalids.append(invalid)
        for index, invalid in enumerate(invalids):
            self.document = invalid
            with self.subTest(index=index):
                run = self.run_verify()
                self.assertEqual(run.returncode, 1, run.stdout)
                self.assertIn("schemaVersion", run.stderr)

    def test_duplicate_profile_names_and_malformed_catalogs_fail(self):
        for raw in ('[]', '{"schemaVersion":3,"sources":[null],"sourceProfiles":{}}',
                    '{"schemaVersion":3,"sources":[],"sourceProfiles":{"same":{},"same":{}}}'):
            (self.catalog / "sources.json").write_text(raw, encoding="utf-8")
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                load_source_catalog(self.catalog / "sources.json")


if __name__ == "__main__":
    unittest.main()
