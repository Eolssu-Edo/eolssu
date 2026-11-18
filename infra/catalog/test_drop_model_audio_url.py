import hashlib
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).parent))
import drop_model_audio_url as drop

SHA = "a" * 40


def state(present=True):
    return {"audioUrlPresent": present, "schema": {"songs": ["id"], "song_model_audios": ["object_key"]},
            "tables": {"songs": {"count": 2, "sha256": "b" * 64},
                       "song_model_audios": {"count": 1, "sha256": "c" * 64}}}


class RemovalTests(unittest.TestCase):
    def test_default_and_already_removed_do_not_backup_or_execute_ddl(self):
        for present in (True, False):
            with self.subTest(present=present), tempfile.TemporaryDirectory() as directory:
                calls = []
                with patch.object(drop, "snapshot", return_value=state(present)):
                    result = drop.run(Path(directory) / "receipt.json", SHA, apply=not present,
                                      runner=lambda sql: calls.append(sql),
                                      release_check=lambda: {"sha": SHA}, backup=lambda: calls.append("backup"))
                self.assertEqual([], calls)
                self.assertEqual("validated-dry-run" if present else "already-removed", result["status"])

    def test_apply_checks_again_after_backup_and_preserves_rows(self):
        with tempfile.TemporaryDirectory() as directory:
            calls = []
            with patch.object(drop, "snapshot", side_effect=[state(), state(), state(False)]):
                result = drop.run(Path(directory) / "receipt.json", SHA, apply=True,
                                  runner=lambda sql: calls.append(sql),
                                  release_check=lambda: calls.append("release") or {"sha": SHA},
                                  backup=lambda: calls.append("backup"))
            self.assertEqual(["release", "backup", "release", drop.DROP_SQL, "release"], calls)
            self.assertEqual("applied", result["status"])
            self.assertEqual(result["before"]["tables"], result["after"]["tables"])

    def test_precondition_and_backup_failure_never_drop(self):
        for stage in ("release", "snapshot", "backup", "changed-after-backup"):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as directory:
                calls = []
                def release():
                    if stage == "release": raise ValueError("wrong release")
                    return {"sha": SHA}
                def backup():
                    if stage == "backup": raise RuntimeError("backup failed")
                changed = state(); changed["tables"]["songs"]["sha256"] = "d" * 64
                snapshots = ValueError("missing V15") if stage == "snapshot" else [state(), changed if stage == "changed-after-backup" else state()]
                path = Path(directory) / "receipt.json"
                with patch.object(drop, "snapshot", side_effect=snapshots), self.assertRaises((ValueError, RuntimeError)):
                    drop.run(path, SHA, apply=True, runner=lambda sql: calls.append(sql), release_check=release, backup=backup)
                self.assertEqual([], calls)
                self.assertNotEqual("applied", json.loads(path.read_text())["status"])

    def test_failed_ddl_and_readback_keep_uncertain_receipt(self):
        for fail in ("ddl", "readback", "changed-row"):
            with self.subTest(fail=fail), tempfile.TemporaryDirectory() as directory:
                def execute(sql):
                    if fail == "ddl": raise RuntimeError("lost connection")
                after = state(False); after["tables"]["songs"]["count"] = 3
                snapshots = [state(), state(), RuntimeError("lost readback") if fail == "readback" else after]
                path = Path(directory) / "receipt.json"
                with patch.object(drop, "snapshot", side_effect=snapshots), self.assertRaises((RuntimeError, ValueError)):
                    drop.run(path, SHA, apply=True, runner=execute, release_check=lambda: {"sha": SHA}, backup=lambda: None)
                self.assertEqual("apply-outcome-unknown" if fail == "ddl" else "applied-readback-pending",
                                 json.loads(path.read_text())["status"])

    def test_existing_receipt_is_not_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "receipt.json"; path.write_text("old")
            with self.assertRaises(FileExistsError):
                drop.run(path, SHA, release_check=lambda: self.fail("must not inspect"), backup=lambda: None)
            self.assertEqual("old", path.read_text())

    def test_release_change_during_backup_never_drops(self):
        with tempfile.TemporaryDirectory() as directory:
            calls = []
            with patch.object(drop, "snapshot", return_value=state()), self.assertRaises(ValueError):
                drop.run(Path(directory) / "receipt.json", SHA, apply=True, runner=lambda sql: calls.append(sql),
                         release_check=iter([{"sha": SHA}, {"sha": "d" * 40}]).__next__, backup=lambda: None)
            self.assertEqual([], calls)

    def test_snapshot_rejects_unconverted_key_or_incomplete_performance_pair(self):
        key = "model-audio/drafts/" + "a" * 64 + "/cases/danso-001-p1/danso.m4a"
        for change in ({}, {"key": "recordings/private.m4a"}, {"sourceHash": "b" * 64},
                       {"performance": key.rsplit("/", 1)[0] + "/performance.common.json"},
                       {"performance": "wrong", "sourceHash": "b" * 64}):
            with self.subTest(change=change):
                metadata = dict(hash="c" * 64, key=key, performance=None, sourceHash=None, **{})
                metadata.update(change)
                columns = [{"table": "songs", "name": "id"}] + [
                    {"table": "song_model_audios", "name": name, "nullable": "NO"}
                    for name in ("song_id", "object_key", "performance_object_key", "source_common_sha256", "generation_metadata", "audio_url")]
                responses = iter([[{"applied": 1}], columns, [{"hash": "d" * 64}], [metadata]])
                runner = lambda _: "\n".join(json.dumps(row) for row in next(responses))
                if change:
                    with self.assertRaises(ValueError): drop.snapshot(runner)
                else:
                    result = drop.snapshot(runner)
                    self.assertTrue(result["audioUrlPresent"])
                    self.assertEqual(1, result["tables"]["song_model_audios"]["count"])

    def test_missing_v15_is_rejected_before_reading_rows(self):
        queries = []
        def runner(sql):
            queries.append(sql)
            return '{"applied":0}'
        with self.assertRaises(ValueError): drop.snapshot(runner)
        self.assertEqual(1, len(queries))

    def test_release_rejects_pending_wrong_sha_changed_jar_old_mapper_and_unhealthy_runtime(self):
        for invalid in (None, "pending", "sha", "jar-hash", "old-mapper", "unhealthy"):
            with self.subTest(invalid=invalid), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); jar = root / "releases" / SHA / "app.jar"; jar.parent.mkdir(parents=True)
                with zipfile.ZipFile(jar, "w") as archive:
                    archive.writestr(drop.MAPPER_PATH, "SELECT audio_url FROM song_model_audios" if invalid == "old-mapper" else "SELECT object_key FROM song_model_audios")
                digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
                journal = {"status": "deploying" if invalid == "pending" else "deployed", "sha": "d" * 40 if invalid == "sha" else SHA,
                           "jar": str(jar), "jar_sha256": "e" * 64 if invalid == "jar-hash" else digest(jar)}
                (root / "deployment.json").write_text(json.dumps(journal))
                def healthy():
                    if invalid == "unhealthy": raise RuntimeError("not ready")
                helper = types.SimpleNamespace(Deployer=lambda _: types.SimpleNamespace(current_jar=lambda: jar, healthy=healthy), digest=digest)
                if invalid is None:
                    self.assertEqual(SHA, drop.verify_release(SHA, helper, root)["sha"])
                else:
                    with self.assertRaises((ValueError, RuntimeError)):
                        drop.verify_release(SHA, helper, root)


if __name__ == "__main__":
    unittest.main()
