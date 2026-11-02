"""Opt-in tests against a task-owned disposable Docker MySQL, never a service DB."""
import copy
import hashlib
import os
from pathlib import Path
import subprocess
import unittest

import test_import_reference_audio as fixtures
import link_reference_audio_pitches as links

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
        cls.pitch_schema = default.with_name("V14__reference_audio_pitch_relation.sql").read_text(encoding="utf-8")
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
        self.run_sql("""DROP TABLE IF EXISTS reference_audio_assets;
            DROP TABLE IF EXISTS pitches;
            CREATE TABLE pitches(id BIGINT PRIMARY KEY,yul VARCHAR(10),octave SMALLINT,UNIQUE(yul,octave));
            INSERT INTO pitches VALUES(71,'중',0),(72,'중',1);
            """ + self.schema + self.pitch_schema)

    def mapping(self, asset=None, **changes):
        asset = asset or self.asset
        return {**{key: asset[key] for key in links.IDENTITY}, "yul": "중", "octave": 0,
                "basis": "nominal label from fixture; not verified pitch", **changes}

    def another_asset(self):
        row = copy.deepcopy(self.asset)
        row["labelId"] = "second-basic-label"
        row["assetId"] = hashlib.sha256(b"danso\nsecond-basic-label").hexdigest()
        row["metadata"]["label"]["id"] = row["labelId"]
        row["objectKey"] = f"reference-audio/v1/danso/{row['assetId']}/{row['sha256']}.m4a"
        return row

    def test_mapping_rerun_and_plain_import_preserve_link_and_all_statuses(self):
        self.run_sql(module.render_sql([self.asset]))
        self.run_sql("UPDATE reference_audio_assets SET status='PUBLISHED';")
        sql = links.render_sql([self.mapping()])
        self.run_sql(sql)
        snapshot = self.run_sql("SELECT * FROM reference_audio_assets;").stdout
        self.run_sql(sql)
        self.run_sql(module.render_sql([self.asset]))
        self.assertEqual(snapshot, self.run_sql("SELECT * FROM reference_audio_assets;").stdout)
        self.assertEqual("71\tPUBLISHED\tinferred\t0", self.run_sql(
            "SELECT pitch_id,status,review_status,eligible_for_note_scoring FROM reference_audio_assets;").stdout.strip())

    def test_conflicting_mapping_aborts_entire_batch_even_with_force(self):
        added = self.another_asset()
        self.run_sql(module.render_sql([self.asset, added]))
        self.run_sql(links.render_sql([self.mapping()]))
        for conflicting in (self.mapping(octave=1), self.mapping(basis="different source")):
            sql = links.render_sql([conflicting, self.mapping(added)])
            for force in (False, True):
                result = self.run_sql(sql, force=force, check=False)
                self.assertIn("Check constraint", result.stderr)
                self.assertEqual("1\t71", self.run_sql(
                    "SELECT COUNT(pitch_id),MIN(pitch_id) FROM reference_audio_assets;").stdout.strip())

    def test_missing_pitch_missing_asset_or_database_file_change_aborts_whole_mapping(self):
        added = self.another_asset()
        self.run_sql(module.render_sql([self.asset, added]))
        for invalid in (self.mapping(octave=2), self.mapping(assetId="0" * 64), self.mapping(sha256="0" * 64)):
            result = self.run_sql(links.render_sql([invalid, self.mapping(added)]), check=False)
            self.assertIn("Check constraint", result.stderr)
            self.assertEqual("0", self.run_sql("SELECT COUNT(pitch_id) FROM reference_audio_assets;").stdout.strip())

    def test_foreign_key_and_basic_only_provenance_pair_are_enforced(self):
        self.run_sql(module.render_sql([self.asset]))
        for update in ("pitch_id=999,pitch_mapping_basis='orphan'", "pitch_id=71",
                       "pitch_mapping_basis='without pitch'", "pitch_id=71,pitch_mapping_basis=' '",
                       "pitch_id=71,pitch_mapping_basis='ornament',category='시김새'"):
            self.assertNotEqual(0,self.run_sql("UPDATE reference_audio_assets SET " + update,check=False).returncode)

    def test_upgrade_keeps_existing_published_assets_unlinked(self):
        self.run_sql("DROP TABLE reference_audio_assets;\n" + self.schema)
        self.run_sql(module.render_sql([self.asset]))
        self.run_sql("UPDATE reference_audio_assets SET status='PUBLISHED';")
        self.run_sql(self.pitch_schema)
        self.assertEqual("1\t0\t0\tPUBLISHED\tinferred\t0", self.run_sql(
            "SELECT COUNT(*),COUNT(pitch_id),COUNT(pitch_mapping_basis),MIN(status),MIN(review_status),SUM(eligible_for_note_scoring) FROM reference_audio_assets;").stdout.strip())

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
