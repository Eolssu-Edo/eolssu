import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import threading
from types import SimpleNamespace
import unittest


spec = importlib.util.spec_from_file_location("stage_audio", Path(__file__).with_name("stage_model_audio.py"))
stage_audio = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage_audio)


def digest(body):
    return hashlib.sha256(body).hexdigest()


def encoded(value):
    return json.dumps(value, ensure_ascii=False).encode()


def bundle(root):
    cases = []
    counts = {"danso": 46, "sogeum": 23, "jeongak-daegeum": 28, "sanjo-daegeum": 10}
    for instrument, count in counts.items():
        for index in range(count):
            case_id = f"{instrument}-{index:03d}-p1"
            provenance = {"sourceId": case_id, "review": "inferred", "converter": "jgb-playback-import/v1"}
            common = encoded({"score": {"sourceId": case_id, "instrument": instrument.upper().replace("-", "_"),
                                        "scoreVersion": 1, "title": case_id},
                              "provenance": provenance, "events": [{"kind": "NOTE"}]})
            audio = (case_id + " fake audio").encode()
            payloads = {
                "source.common.json": ("canonical_common_score", common),
                "expressions.common.json": ("common_expression_companion", encoded({"sourceSha256": digest(common)})),
                "performance.common.json": ("audio_bound_performance_timeline", encoded({"sourceSha256": digest(common), "audioSha256": digest(audio)})),
                "manifest.json": ("original_generation_manifest", encoded({"input_sha256": digest(common)})),
                "source-map.json": ("common_source_mapping", b"{}"),
                "score.json": ("internal_performed_score", b"{}"),
                instrument + ".m4a": ("rendered_model_audio", audio),
                instrument + ".mid": ("generated_midi", b"midi"),
                instrument + ".json": ("render_and_audio_validation_report", b"{}"),
                "ffprobe.json": ("actual_audio_probe", b"{}"),
            }
            files = []
            for name, (role, body) in payloads.items():
                relative = f"cases/{case_id}/{name}"
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(body)
                files.append({"role": role, "path": relative, "sha256": digest(body), "bytes": len(body)})
            cases.append({"caseId": case_id, "catalogStatus": "DRAFT", "serverSongId": None,
                          "sourceId": case_id, "instrument": instrument, "scoreVersion": 1, "title": case_id,
                          "noteCount": 1, "sourceCommonSha256": digest(common), "provenance": provenance,
                          "rights": {"status": "UNCONFIRMED", "serviceDatasetApproved": False, "rawLibraryIncluded": False},
                          "review": {"source": "inferred", "expertPerformanceApproved": False, "scoringApproved": False},
                          "audio": {"sha256": digest(audio), "startOffsetMs": 0}, "files": files})
    manifest = {"schema": "eolssu-draft-model-audio-batch/v1", "task": "S15P31E102-339", "catalogStatus": "DRAFT",
                "rights": {"status": "UNCONFIRMED", "sourceReview": "inferred", "serviceDatasetApproved": False,
                           "expertPerformanceApproved": False, "rawCatLibraryIncluded": False, "rawJgbSourceIncluded": False},
                "counts": {"cases": 107, "byInstrument": counts, "payloadFiles": 1070}, "cases": cases}
    (root / "manifest.json").write_bytes(encoded(manifest))
    return manifest


class StorageError(Exception):
    def __init__(self, code):
        self.response = {"Error": {"Code": str(code)}, "ResponseMetadata": {"HTTPStatusCode": int(code)}}


class Events:
    def register(self, name, handler, **kwargs):
        assert name == "before-call.s3.PutObject"
        self.handler = handler


class Bucket:
    """Minimal immutable-object store; applies the actual registered request hook."""
    def __init__(self):
        self.objects = {}
        self.puts = []
        self.headers = []
        self.calls = []
        self.meta = SimpleNamespace(events=Events())
        self.lock = threading.Lock()
        self.fail_after = None
        self.race_body = None
        self.corrupt_new = False

    def head_object(self, *, Bucket, Key):
        self.calls.append(("HEAD", Key))
        if Key not in self.objects:
            raise StorageError(404)
        return {"ContentLength": len(self.objects[Key])}

    def get_object(self, *, Bucket, Key):
        self.calls.append(("GET", Key))
        if Key not in self.objects:
            raise StorageError(404)
        return {"ContentLength": len(self.objects[Key]), "Body": io.BytesIO(self.objects[Key])}

    def put_object(self, *, Bucket, Key, Body, ContentType, Metadata):
        params = {"headers": {}}
        self.meta.events.handler(params=params)
        assert params["headers"] == {"If-None-Match": "*"}
        with self.lock:
            self.calls.append(("PUT", Key))
            self.headers.append(params["headers"])
            if self.fail_after is not None and len(self.puts) >= self.fail_after:
                raise StorageError(503)
            if self.race_body is not None:
                self.objects[Key] = Body if self.race_body == "same" else b"conflicting concurrent upload"
                self.race_body = None
            if Key in self.objects:
                raise StorageError(412)
            self.objects[Key] = bytes(Body) if not self.corrupt_new else b"x" * len(Body)
            self.puts.append(Key)
        assert Metadata["sha256"] == digest(Body)


class StageTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.bundle = self.root / "bundle"
        self.bundle.mkdir()
        self.manifest = bundle(self.bundle)

    def test_default_validates_locally_without_credentials_or_remote_access(self):
        class ForbiddenClient:
            def __getattr__(self, name):
                raise AssertionError("Default mode touched remote client")
        receipt = self.root / "receipt.json"
        result = stage_audio.stage(self.bundle, receipt, client=ForbiddenClient(), env_file=self.root / "absent.env")
        self.assertEqual(result["status"], "validated-local")
        self.assertEqual(len(result["objects"]), 1071)
        self.assertEqual(result["new"], 0)
        self.assertEqual(result["prefix"], "model-audio/drafts/" + digest((self.bundle / "manifest.json").read_bytes()) + "/")
        self.assertEqual(json.loads(receipt.read_text()), result)

    def test_conditional_upload_verifies_bytes_commits_manifest_last_and_rerun_puts_nothing(self):
        store = Bucket()
        result = stage_audio.stage(self.bundle, self.root / "first.json", upload=True, client=store, bucket="test", workers=4)
        self.assertEqual((result["status"], result["new"], result["reused"]), ("uploaded", 1071, 0))
        self.assertEqual(store.puts[-1], result["prefix"] + "manifest.json")
        self.assertTrue(all(key.startswith(result["prefix"]) and not key.startswith("recordings/") for _, key in store.calls))
        first_put = next(i for i, call in enumerate(store.calls) if call[0] == "PUT")
        self.assertEqual(sum(op == "HEAD" for op, _ in store.calls[:first_put]), 1071)
        self.assertEqual(sum(op == "GET" for op, _ in store.calls), 1071)
        again = stage_audio.stage(self.bundle, self.root / "again.json", upload=True, client=store, bucket="test")
        self.assertEqual((again["new"], again["reused"], len(store.puts)), (0, 1071, 1071))

    def test_local_corruption_unsafe_paths_unlisted_files_and_approval_flips_never_touch_remote(self):
        store = Bucket()
        manifest_path = self.bundle / "manifest.json"
        original_manifest = manifest_path.read_bytes()
        original_payload = self.bundle / self.manifest["cases"][0]["files"][0]["path"]
        original_bytes = original_payload.read_bytes()
        mutations = ("corrupt", "traversal", "unlisted", "approved", "published", "existing-song")
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                changed = json.loads(original_manifest)
                if mutation == "corrupt":
                    original_payload.write_bytes(original_bytes + b" ")
                elif mutation == "traversal":
                    changed["cases"][0]["files"][0]["path"] = "cases/../../recordings/private.m4a"
                elif mutation == "unlisted":
                    (self.bundle / "unexpected.json").write_text("{}")
                elif mutation == "approved":
                    changed["cases"][0]["rights"]["serviceDatasetApproved"] = True
                elif mutation == "published":
                    changed["catalogStatus"] = "PUBLISHED"
                else:
                    changed["cases"][0]["serverSongId"] = "1"
                if mutation != "corrupt":
                    manifest_path.write_bytes(encoded(changed))
                with self.assertRaises(ValueError):
                    stage_audio.stage(self.bundle, self.root / (mutation + ".json"), upload=True, client=store, bucket="test")
                self.assertEqual(store.calls, [])
                original_payload.write_bytes(original_bytes)
                manifest_path.write_bytes(original_manifest)
                (self.bundle / "unexpected.json").unlink(missing_ok=True)

    def test_existing_remote_conflict_stops_before_any_put(self):
        store = Bucket()
        checksum = digest((self.bundle / "manifest.json").read_bytes())
        item = self.manifest["cases"][-1]["files"][0]
        key = f"model-audio/drafts/{checksum}/" + item["path"]
        store.objects[key] = b"x" * item["bytes"]  # Same size: HEAD cannot expose this conflict.
        with self.assertRaisesRegex(ValueError, "hash conflicts"):
            stage_audio.stage(self.bundle, self.root / "conflict.json", upload=True, client=store, bucket="test")
        self.assertIn(("GET", key), store.calls)
        self.assertEqual(store.puts, [])
        self.assertEqual(json.loads((self.root / "conflict.json").read_text())["phase"], "remote-inventory")

    def test_interrupted_upload_leaves_no_commit_marker_and_rerun_resumes_without_overwrite(self):
        store = Bucket()
        store.fail_after = 2
        with self.assertRaises(StorageError):
            stage_audio.stage(self.bundle, self.root / "partial.json", upload=True, client=store, bucket="test", workers=1)
        partial = json.loads((self.root / "partial.json").read_text())
        self.assertEqual((partial["status"], partial["new"]), ("failed", 2))
        self.assertNotIn(partial["prefix"] + "manifest.json", store.objects)
        before = dict(store.objects)
        store.fail_after = None
        complete = stage_audio.stage(self.bundle, self.root / "resume.json", upload=True, client=store, bucket="test", workers=1)
        self.assertEqual((complete["new"], complete["reused"]), (1069, 2))
        self.assertEqual({key: store.objects[key] for key in before}, before)
        self.assertEqual(store.puts[-1], partial["prefix"] + "manifest.json")

    def test_conditional_race_reuses_only_identical_bytes_and_failed_readback_never_commits(self):
        same = Bucket()
        same.race_body = "same"
        report = stage_audio.stage(self.bundle, self.root / "race-same.json", upload=True, client=same, bucket="test", workers=1)
        self.assertEqual((report["new"], report["reused"]), (1070, 1))
        for condition in ("different", "corrupt-readback"):
            with self.subTest(condition=condition):
                store = Bucket()
                store.race_body = "different" if condition == "different" else None
                store.corrupt_new = condition == "corrupt-readback"
                receipt = self.root / (condition + ".json")
                with self.assertRaises(ValueError):
                    stage_audio.stage(self.bundle, receipt, upload=True, client=store, bucket="test", workers=1)
                failed = json.loads(receipt.read_text())
                self.assertEqual(failed["status"], "failed")
                self.assertNotIn(failed["prefix"] + "manifest.json", store.objects)


if __name__ == "__main__":
    unittest.main()
