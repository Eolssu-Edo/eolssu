"""Isolated preflight tests and opt-in task-owned Docker MySQL transaction tests."""
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import enable_existing_scores as module
from enable_scoring import canonical, canonical_sha, quote

PLAN = Path(__file__).parent / 'scoring-plans/existing-704.json'


def fixture():
    plan, rows = [], {}
    for song_id in sorted(module.EXPECTED_IDS):
        score = {'music': {'bpmUnit': 'QUARTER'}, 'score': {'sourceId': f'source-{song_id}', 'scoreVersion': 1},
                 'events': [{'kind': 'NOTE', 'startTick': 0, 'durationTick': 4}], 'provenance': {'review': 'inferred'}}
        item = dict(songId=song_id, source=f'source-{song_id}', scoreVersion=1, noteCount=1,
                    defaultBpm=90, instrumentId=1, scoreSha256=canonical_sha(score))
        row = dict(id=song_id, source=item['source'], scoreVersion=1, noteCount=1, defaultBpm=90,
                   instrumentId=1, isScorable=0, status='PUBLISHED', deleted=0, score=score,
                   stableHash='a'*64, updatedAt='2026-01-01 00:00:00.000000', audios=[
                       dict(id=song_id, version=1, status='PUBLISHED', bpm=90, bpmUnit='QUARTER', rowHash='b'*64)])
        plan.append(item)
        rows[song_id] = row
    return plan, rows


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.plan, self.rows = fixture()
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.receipt = Path(self.temp.name) / 'receipt.json'

    def inventory_runner(self, sql):
        self.assertTrue(sql.startswith('SELECT JSON_OBJECT'))
        songs, audios = [], []
        for row in self.rows.values():
            song = copy.deepcopy(row)
            audios.extend(dict(kind='audio', **a) for a in song.pop('audios'))
            songs.append(dict(kind='song', **song))
        return '\n'.join(json.dumps(r) for r in songs + audios)

    def test_plan_is_exactly_the_approved_36_and_rejects_scope_changes(self):
        real = module.load_plan(PLAN)
        self.assertEqual(module.EXPECTED_IDS, {i['songId'] for i in real})
        self.assertFalse({19,32,37,45,50,62,63,65,104,109} & module.EXPECTED_IDS)
        for bad in (real[:-1], real + [real[0]], [dict(real[0], songId=62)] + real[1:],
                    [real[1]] + real[1:], [dict(real[0], scoreSha256='x')] + real[1:]):
            path = Path(self.temp.name) / 'plan.json'
            path.write_text(json.dumps(bad), encoding='utf-8')
            with self.assertRaises(ValueError):
                module.load_plan(path)

    def test_dry_run_preserves_inferred_review_and_never_writes(self):
        before = copy.deepcopy(self.rows)
        result = module.run(self.plan, self.receipt, runner=self.inventory_runner)
        self.assertEqual('validated-dry-run', result['status'])
        self.assertEqual(36, len(result['pending']))
        self.assertEqual(before, self.rows)

    def test_changed_score_identity_publication_or_audio_stops_before_apply(self):
        sid = self.plan[0]['songId']
        changes = [dict(source='other'), dict(scoreVersion=2), dict(noteCount=99), dict(defaultBpm=100),
                   dict(instrumentId=2), dict(deleted=1), dict(status='HIDDEN'), dict(audios=[]),
                   dict(audios=[dict(self.rows[sid]['audios'][0], status='DRAFT')]),
                   dict(audios=[dict(self.rows[sid]['audios'][0], bpm=91)]),
                   dict(score={'unexpected':'score'})]
        for index, change in enumerate(changes):
            original = self.rows[sid]
            self.rows[sid] = {**original, **change}
            with self.subTest(change=change), self.assertRaises(ValueError):
                module.run(self.plan, Path(self.temp.name)/f'bad{index}.json', apply=True, runner=self.inventory_runner)
            self.rows[sid] = original

    def test_existing_receipt_is_never_overwritten(self):
        self.receipt.write_text('preserve', encoding='utf-8')
        with self.assertRaises(FileExistsError):
            module.run(self.plan, self.receipt, runner=self.inventory_runner)
        self.assertEqual('preserve', self.receipt.read_text())


@unittest.skipUnless(os.environ.get('EXISTING_SCORE_TEST_MYSQL_CONTAINER'), 'Disposable MySQL not selected')
class MySqlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = os.environ['EXISTING_SCORE_TEST_MYSQL_CONTAINER']
        if not cls.container.startswith('eolssu-existing-score-704-'):
            raise ValueError('Only task-owned eolssu-existing-score-704-* containers are allowed')
        cls.password = os.environ['EXISTING_SCORE_TEST_MYSQL_PASSWORD']
        cls.sql('CREATE DATABASE IF NOT EXISTS existing_score_test CHARACTER SET utf8mb4;', database=False)

    @classmethod
    def sql(cls, sql, *, database=True):
        command = ['docker','exec','-i','-e','MYSQL_PWD='+cls.password,cls.container,'mysql',
                   '--no-defaults','--skip-reconnect','-uroot','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4']
        if database:
            command.append('existing_score_test')
        result = subprocess.run(command, input=sql, text=True, encoding='utf-8', capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr)
        return result.stdout.strip()

    def setUp(self):
        self.plan, self.rows = fixture()
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.sql('''DROP TABLE IF EXISTS song_model_audios; DROP TABLE IF EXISTS songs;
          CREATE TABLE songs (id BIGINT PRIMARY KEY,instrument_id BIGINT,jangdan_id BIGINT NULL,title VARCHAR(100),
          description TEXT,difficulty INT,default_bpm INT,score_json JSON,score_version INT,umulsai_text TEXT,
          note_count INT,source VARCHAR(255),is_scorable BOOLEAN,status VARCHAR(20),
          created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
          ON UPDATE CURRENT_TIMESTAMP(6),deleted_at DATETIME(6) NULL) ENGINE=InnoDB;
          CREATE TABLE song_model_audios (song_id BIGINT,score_version INT,audio_url TEXT,object_key TEXT,
          performance_object_key TEXT,source_common_sha256 CHAR(64),bpm INT,bpm_unit VARCHAR(32),duration_ms INT,
          start_offset_ms INT,content_type VARCHAR(64),sha256 CHAR(64),status VARCHAR(16),generation_metadata JSON,
          created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
          ON UPDATE CURRENT_TIMESTAMP(6),PRIMARY KEY(song_id,score_version),FOREIGN KEY(song_id) REFERENCES songs(id)) ENGINE=InnoDB;''')
        inserts = []
        for sid, row in self.rows.items():
            inserts.append(f"INSERT INTO songs(id,instrument_id,title,difficulty,default_bpm,score_json,score_version,note_count,source,is_scorable,status) VALUES ({sid},1,'한오백년',3,90,CAST({quote(canonical(row['score']))} AS JSON),1,1,{quote(row['source'])},FALSE,'PUBLISHED');")
            # Keep two versions so preservation covers every existing audio row, not only the serving version.
            for version in (1, 2):
                inserts.append(f"INSERT INTO song_model_audios(song_id,score_version,object_key,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status,generation_metadata) VALUES({sid},{version},'existing.m4a',90,'QUARTER',1000,0,'audio/mp4',REPEAT('a',64),'PUBLISHED',JSON_OBJECT('review','inferred'));")
        self.sql('\n'.join(inserts))

    def receipt(self, name):
        return Path(self.temp.name) / (name + '.json')

    def test_apply_preserves_all_score_review_audio_rows_and_rerun_writes_zero(self):
        before = module.inventory(self.plan, self.sql)
        result = module.run(self.plan, self.receipt('apply'), apply=True, runner=self.sql)
        self.assertEqual(36, len(result['updated']))
        after = module.inventory(self.plan, self.sql)
        for sid in before:
            for field in ('stableHash','score','audios'):
                self.assertEqual(before[sid][field], after[sid][field])
        self.assertEqual([], module.run(self.plan, self.receipt('again'), apply=True, runner=self.sql)['updated'])
        self.assertEqual(after, module.inventory(self.plan, self.sql))

    def test_late_song_or_audio_change_rolls_back_every_flag(self):
        for index, mutation in enumerate(("UPDATE songs SET default_bpm=91 WHERE id=88;",
                                         "UPDATE song_model_audios SET object_key='changed' WHERE song_id=88 AND score_version=2;")):
            snapshot = module.inventory(self.plan, self.sql)
            self.sql(mutation)
            with self.assertRaises(RuntimeError):
                self.sql(module.build_sql(snapshot))
            self.assertEqual('0', self.sql('SELECT SUM(is_scorable) FROM songs;'))

    def test_concurrent_flag_change_is_not_silently_overwritten(self):
        snapshot = module.inventory(self.plan, self.sql)
        self.sql('UPDATE songs SET is_scorable=TRUE WHERE id=88;')
        with self.assertRaises(RuntimeError):
            self.sql(module.build_sql(snapshot))
        self.assertEqual('1', self.sql('SELECT SUM(is_scorable) FROM songs;'))

    def test_missing_song_aborts_the_transaction(self):
        snapshot = module.inventory(self.plan, self.sql)
        self.sql('DELETE FROM song_model_audios WHERE song_id=88; DELETE FROM songs WHERE id=88;')
        with self.assertRaises(RuntimeError):
            self.sql(module.build_sql(snapshot))
        self.assertEqual('0', self.sql('SELECT SUM(is_scorable) FROM songs;'))

    def test_inserted_audio_aborts_the_transaction(self):
        snapshot = module.inventory(self.plan, self.sql)
        self.sql("INSERT INTO song_model_audios(song_id,score_version,status) VALUES (88,3,'DRAFT');")
        with self.assertRaises(RuntimeError):
            self.sql(module.build_sql(snapshot))
        self.assertEqual('0', self.sql('SELECT SUM(is_scorable) FROM songs;'))

    def test_partial_rerun_preserves_already_enabled_song_timestamp(self):
        self.sql('UPDATE songs SET is_scorable=TRUE WHERE id=36;')
        before = module.inventory(self.plan, self.sql)[36]
        result = module.run(self.plan, self.receipt('partial'), apply=True, runner=self.sql)
        self.assertEqual(35, len(result['updated']))
        self.assertEqual(before, module.inventory(self.plan, self.sql)[36])


if __name__ == '__main__':
    unittest.main()
