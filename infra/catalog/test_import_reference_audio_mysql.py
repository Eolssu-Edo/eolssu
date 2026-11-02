"""Opt-in tests against a task-owned disposable Docker MySQL, never a service DB."""
import copy
import hashlib
import os
from pathlib import Path
import subprocess
import unittest

import test_import_reference_audio as fixtures

module = fixtures.module


@unittest.skipUnless(os.environ.get("REFERENCE_AUDIO_TEST_MYSQL_CONTAINER"), "Disposable MySQL not selected")
class ReferenceAudioMySqlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = os.environ["REFERENCE_AUDIO_TEST_MYSQL_CONTAINER"]
        if not cls.container.startswith("eolssu-reference-audio-"):
            raise ValueError("Only task-owned eolssu-reference-audio-* test containers are allowed")
        cls.password = os.environ["REFERENCE_AUDIO_TEST_MYSQL_PASSWORD"]
        default = Path(__file__).resolve().parents[2] / "backend/src/main/resources/db/migration/V13__reference_audio_assets.sql"
        cls.schema = Path(os.environ.get("REFERENCE_AUDIO_TEST_SCHEMA", str(default))).read_text(encoding="utf-8")
        cls.run_sql("CREATE DATABASE IF NOT EXISTS reference_audio_import_test CHARACTER SET utf8mb4;", database=False)

    @classmethod
    def run_sql(cls, sql, *, database=True, force=False, check=True):
        command = ["docker", "exec", "-i", "-e", "MYSQL_PWD=" + cls.password, cls.container,
                   "mysql", "-uroot", "--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4"]
        if force:
            command.append("--force")
        if database:
            command.append("reference_audio_import_test")
        result = subprocess.run(command, input=sql, text=True, encoding="utf-8", capture_output=True, timeout=30)
        if check and result.returncode:
            raise AssertionError(result.stderr)
        return result

    def setUp(self):
        fixture = fixtures.ReferenceAudioImportTests()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.asset = module.load_assets(fixture.manifest())[0]
        self.run_sql("DROP TABLE IF EXISTS reference_audio_assets;\n" + self.schema)

    def test_exact_rerun_preserves_published_row_and_unicode(self):
        sql = module.render_sql([self.asset])
        self.run_sql(sql)
        self.run_sql("UPDATE reference_audio_assets SET status='PUBLISHED';")
        self.run_sql(sql)
        result = self.run_sql("SELECT COUNT(*),MIN(status),MIN(display_label) FROM reference_audio_assets;")
        self.assertEqual("1\tPUBLISHED\t평취 중", result.stdout.strip())

    def test_conflict_and_new_row_are_all_rejected(self):
        self.run_sql(module.render_sql([self.asset]))
        changed = copy.deepcopy(self.asset)
        changed["displayLabel"] = "conflicting description"
        changed["metadata"]["label"]["displayLabel"] = changed["displayLabel"]
        added = copy.deepcopy(self.asset)
        added["labelId"] = "new-label"
        added["assetId"] = hashlib.sha256(b"danso\nnew-label").hexdigest()
        added["metadata"]["label"]["id"] = added["labelId"]
        added["objectKey"] = f"reference-audio/v1/danso/{added['assetId']}/{added['sha256']}.m4a"
        sql = module.render_sql([changed, added])
        rejected = self.run_sql(sql, check=False)
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("Check constraint", rejected.stderr)
        self.assertEqual("1\t평취 중", self.run_sql("SELECT COUNT(*),MIN(display_label) FROM reference_audio_assets;").stdout.strip())
        # Even an incorrectly configured client that continues after CHECK cannot insert the new row.
        self.run_sql(sql, force=True, check=False)
        self.assertEqual("1\t평취 중", self.run_sql("SELECT COUNT(*),MIN(display_label) FROM reference_audio_assets;").stdout.strip())

    def test_sql_rejects_publishing_rejected_label(self):
        row = copy.deepcopy(self.asset)
        row["review"] = "rejected"
        self.run_sql(module.render_sql([row]))
        result = self.run_sql("UPDATE reference_audio_assets SET status='PUBLISHED';", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("DRAFT", self.run_sql("SELECT status FROM reference_audio_assets;").stdout.strip())


if __name__ == "__main__":
    unittest.main()
