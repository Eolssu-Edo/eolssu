import copy
import json
import math
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import load_model_audio as loader


class LoadTests(unittest.TestCase):
    def test_reference_reuse_accepts_published_expanded_instrument_without_changing_identity(self):
        reference = {"code": "SOGEUM", "name": "소금", "lowSemitone": 7, "highSemitone": 31,
                     "pitchToleranceCent": 50, "isActive": False}
        model = {"rows": [], "references": {"pitches": [], "jangdans": [], "instruments": [reference]}}
        observed = dict(reference, lowSemitone=5, highSemitone=33, isActive=True)
        inventory = {"pitches": [], "jangdans": [], "instruments": [{"code": "DANSO"}, observed], "songs": [], "audios": []}
        self.assertEqual(loader.verify_inventory(model, inventory), {})
        for changed in (dict(observed, lowSemitone=8), dict(observed, highSemitone=30),
                        dict(observed, name="다른 악기"), dict(observed, pitchToleranceCent=0)):
            inventory["instruments"][1] = changed
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                loader.verify_inventory(model, inventory)

    def test_metadata_normalization_accepts_mysql_double_roundtrip_only_at_numeric_leaves(self):
        original = {"seconds": 0.15789473684210525, "review": False, "level": 2}
        self.assertTrue(loader.same_json(original, dict(original, seconds=0.15789473684210523)))
        two_ulps = math.nextafter(math.nextafter(original["seconds"], math.inf), math.inf)
        self.assertTrue(loader.same_json(original, dict(original, seconds=two_ulps)))
        self.assertFalse(loader.same_json(original, dict(original, seconds=math.nextafter(two_ulps, math.inf))))
        for changed in (dict(original, seconds=0.16), dict(original, review=0), dict(original, level=2.0), dict(original, seconds=float('nan'))):
            self.assertFalse(loader.same_json(original, changed))

    def test_inventory_rejects_duplicate_identity_changed_score_and_dangling_audio_before_writes(self):
        row = {"source": "mr203-fixed-danso-001-p1", "title": "청황'", "description": None, "instrument_code": "DANSO", "jangdan_name": None,
               "difficulty": 2, "default_bpm": 90, "score_json": {"score": {"sourceId": "mr203-fixed-danso-001-p1"}},
               "score_version": 1, "note_count": 5, "object_key": "model-audio/drafts/" + "a" * 64 + "/cases/danso-001-p1/danso.m4a",
               "performance_object_key": "model-audio/drafts/" + "a" * 64 + "/cases/danso-001-p1/performance.common.json",
               "source_common_sha256": "c" * 64, "generation_metadata": {"review": {"expertPerformanceApproved": False}},
               "bpm_unit": "QUARTER", "duration_ms": 1234, "start_offset_ms": 0, "content_type": "audio/mp4", "sha256": "b" * 64}
        model = {"rows": [row], "references": {"pitches": [], "instruments": [], "jangdans": []}}
        song = {k: row[k] for k in ("source", "title", "description", "instrument_code", "jangdan_name", "difficulty", "default_bpm", "score_json", "score_version", "note_count")}
        song.update(id=77, jangdan_id=None, umulsai_text=None, is_scorable=0, status="DRAFT", deleted_at=None)
        audio = {k: row[k] for k in ("score_version", "object_key", "performance_object_key", "source_common_sha256", "generation_metadata", "bpm_unit", "duration_ms", "start_offset_ms", "content_type", "sha256")}
        audio.update(song_id=77, bpm=90, status="DRAFT")
        inventory = {"pitches": [], "instruments": [{"code": "DANSO"}], "jangdans": [], "songs": [song], "audios": [audio]}
        self.assertEqual(loader.verify_inventory(model, inventory, complete=True), {row["source"]: 77})
        for kind in ("duplicate", "score", "missing-audio", "audio-sha", "difficulty", "published"):
            changed = copy.deepcopy(inventory)
            if kind == "duplicate": changed["songs"].append(copy.deepcopy(song))
            elif kind == "score": changed["songs"][0]["score_json"]["events"] = []
            elif kind == "missing-audio": changed["audios"] = []
            elif kind == "audio-sha": changed["audios"][0]["sha256"] = "c" * 64
            elif kind == "difficulty": changed["songs"][0]["difficulty"] = 3
            else: changed["songs"][0]["status"] = "PUBLISHED"
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                loader.verify_inventory(model, changed)
        model["references"]["jangdans"] = [{"name": "중모리", "bpmUnit": "QUARTER"}]
        row["jangdan_name"] = song["jangdan_name"] = "중모리"
        song["jangdan_id"] = 3
        inventory["jangdans"] = [{"id": 3, "name": "중모리", "bpmUnit": "QUARTER"}]
        self.assertEqual(loader.verify_inventory(model, inventory, complete=True), {row["source"]: 77})
        inventory["jangdans"][0]["bpmUnit"] = "DOTTED_QUARTER"
        with self.assertRaises(ValueError):
            loader.verify_inventory(model, inventory)

    def test_receipt_requires_exact_verified_objects_and_no_draft_upload_status(self):
        checksum = "a" * 64
        prefix = "model-audio/drafts/" + checksum + "/"
        item = {"path": "manifest.json", "role": "batch_commit_marker", "bytes": 8, "sha256": checksum}
        receipt = {"status": "uploaded", "phase": "complete", "manifestSha256": checksum, "prefix": prefix,
                   "new": 1, "reused": 0, "objects": [dict(item, key=prefix + item["path"], status="new")]}
        self.assertEqual(loader.validate_receipt(checksum, [item], receipt), prefix)
        for changed in (dict(receipt, status="validated-local"), dict(receipt, manifestSha256="b" * 64),
                        dict(receipt, objects=receipt["objects"] * 2), dict(receipt, new=0)):
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                loader.validate_receipt(checksum, [item], changed)
        changed = copy.deepcopy(receipt)
        changed["objects"][0]["status"] = "uploaded-unverified"
        with self.assertRaises(ValueError):
            loader.validate_receipt(checksum, [item], changed)

    def test_sql_text_encoding_preserves_unicode_quotes_backslashes_without_sql_interpolation(self):
        original = "청황'\\\n); DROP TABLE songs; --"
        literal = loader.quote(original)
        self.assertTrue(literal.startswith("CONVERT(X'") and literal.endswith("' USING utf8mb4)"))
        self.assertEqual(bytes.fromhex(literal[len("CONVERT(X'"):-len("' USING utf8mb4)")]).decode(), original)
        self.assertNotIn("DROP TABLE", literal)

    def test_provisional_difficulty_is_bound_to_source_audio_tempo_and_reviewed_three_levels(self):
        case = {"caseId": "one", "sourceId": "mr203-fixed-one", "sourceCommonSha256": "a" * 64, "defaultBpm": 90, "noteCount": 5}
        row = {"caseId": "one", "sourceId": case["sourceId"], "inputSha256": case["sourceCommonSha256"], "bpm": 90, "difficulty": 2,
               "evidence": {"policyVersion": "score-difficulty/provisional-fe3/v1", "level": 2, "measurements": {"noteCount": 5}}}
        document = {"schema": "model-audio-difficulty-batch/v1", "policyVersion": row["evidence"]["policyVersion"],
                    "manifestSha256": "b" * 64, "count": 1, "cases": [row]}
        self.assertEqual(loader.validate_difficulty([case], "b" * 64, document), {"one": row})
        for key, bad in (("inputSha256", "c" * 64), ("sourceId", "other"), ("difficulty", 4), ("difficulty", True), ("bpm", 91)):
            changed = copy.deepcopy(document)
            changed["cases"][0][key] = bad
            with self.subTest(key=key, bad=bad), self.assertRaises(ValueError):
                loader.validate_difficulty([case], "b" * 64, changed)

    def test_transport_failure_records_unknown_apply_outcome_without_claiming_rollback_or_retrying(self):
        model = {"manifestSha256": "a", "difficultySha256": "b", "referencePlanSha256": "c", "rows": [None]}
        calls = []
        def interrupted(statement):
            calls.append(statement)
            raise TimeoutError("SECRET MUST NOT APPEAR")
        with tempfile.TemporaryDirectory() as temporary:
            receipt = Path(temporary) / "receipt.json"
            with patch.object(loader, "inventory", return_value={}), patch.object(loader, "verify_inventory", return_value={}), \
                    patch.object(loader, "build_sql", return_value="TRANSACTION"):
                with self.assertRaises(TimeoutError):
                    loader.run(model, receipt, apply=True, runner=interrupted)
            actual = json.loads(receipt.read_text())
            self.assertEqual(actual["status"], "apply-outcome-unknown")
            self.assertEqual(calls, ["TRANSACTION"])
            self.assertNotIn("SECRET", receipt.read_text())

    def test_mysql_runner_disables_reconnect_and_option_files_without_password_arguments(self):
        inspect = [{"Config": {"Env": ["DB_URL=jdbc:mysql://mysql:3306/eolssu?charset=utf8mb4", "DB_PASSWORD=secret"]}}]
        responses = [SimpleNamespace(stdout=json.dumps(inspect).encode()), SimpleNamespace(returncode=0, stdout=b"{}")]
        with patch.object(loader.subprocess, "run", side_effect=responses) as invoked:
            self.assertEqual(loader.mysql("SELECT 1;"), "{}")
        command = invoked.call_args_list[1].args[0][-1]
        self.assertIn("exec mysql --no-defaults --skip-reconnect", command)
        self.assertIn("--database=eolssu", command)
        self.assertNotIn("secret", command)
        self.assertNotIn("--force", command)


if __name__ == "__main__":
    unittest.main()
