import hashlib
import json
import unittest

import test_import_reference_audio as fixtures
import link_reference_audio_pitches as links


class PitchMappingTests(unittest.TestCase):
    setUp = fixtures.ReferenceAudioImportTests.setUp
    manifest = fixtures.ReferenceAudioImportTests.manifest
    def mapping_file(self, rows=None):
        row = {"labelId": self.asset["labelId"]}
        row.update(yul="중", octave=0)
        path = self.root / "mapping.json"
        path.write_text(json.dumps({"schemaVersion": 2, "datasetId": "danso", "manifestSha256": hashlib.sha256(self.manifest().read_bytes()).hexdigest(), "basis": "Nominal label; docs/knowledge/data/danso.md and data/reference/pitches.json", "mappings": rows or [row]}, ensure_ascii=False), encoding="utf-8")
        return path

    def test_explicit_mapping_matches_verified_file_and_preserves_inferred_status(self):
        manifest = self.manifest()
        mappings = links.load_mappings(self.mapping_file(), manifest)
        self.assertEqual(("중", 0), (mappings[0]["yul"], mappings[0]["octave"]))
        self.assertEqual("inferred", self.asset["review"])
        self.assertFalse(self.asset["eligibleForNoteScoring"])

    def test_duplicate_wrong_hash_or_nonbasic_mapping_is_rejected(self):
        manifest = self.manifest()
        row = json.loads(self.mapping_file().read_text(encoding="utf-8"))["mappings"][0]
        for rows in ([row, row], [dict(row, sha256="0" * 64)], [dict(row, category="시김새")]):
            with self.subTest(rows=rows), self.assertRaises(ValueError):
                links.load_mappings(self.mapping_file(rows), manifest)

    def test_mapping_target_requires_valid_natural_pitch_identity_and_source(self):
        manifest = self.manifest()
        row = json.loads(self.mapping_file().read_text(encoding="utf-8"))["mappings"][0]
        for extra in ({"yul": "unknown"}, {"octave": True}, {"octave": 3}):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                links.load_mappings(self.mapping_file([dict(row, **extra)]), manifest)

        path = self.mapping_file()
        document = json.loads(path.read_text(encoding="utf-8"))
        document["basis"] = " "
        path.write_text(json.dumps(document),encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "basis/source"):
            links.load_mappings(path, manifest)

    def test_sigimsae_is_rejected_even_when_its_identity_and_file_match(self):
        self.asset["category"] = "시김새"
        self.asset["metadata"]["label"]["category"] = "시김새"
        manifest = self.manifest()
        with self.assertRaisesRegex(ValueError, "category differs"):
            links.load_mappings(self.mapping_file(), manifest)


if __name__ == "__main__":
    unittest.main()
