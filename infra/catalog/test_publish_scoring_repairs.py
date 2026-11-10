import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).parent))
import publish_scoring_repairs as repair


def fixture():
    entries, songs, audios = [], {}, {}
    for sid in sorted(repair.IDS):
        before = dict(id=sid, source=f'source-{sid}', version=1, bpm=90, scorable=0, notes=1,
                      status='PUBLISHED', deleted=None, jangdan=3 if sid == 19 else None,
                      score={'score': {'scoreVersion': 1}, 'events': [{'kind': 'NOTE'}]},
                      title=f'곡 {sid}', difficulty=1, instrumentId=1, umulsai=None)
        old = dict(songId=sid, version=1, object_key=f'old-{sid}', performance_object_key=f'old-perf-{sid}',
                   source_common_sha256='a' * 64, bpm=90, bpm_unit='QUARTER', duration_ms=1000,
                   start_offset_ms=0, content_type='audio/mp4', sha256='b' * 64, status='PUBLISHED',
                   generation_metadata={'original': True}, created_at='2026-11-01 00:00:00', updated_at='2026-11-01 00:00:00')
        target = copy.deepcopy(before)
        target.update(version=2, scorable=1, score={'score': {'scoreVersion': 2}, 'events': [{'kind': 'NOTE', 'sigimsae': ['니']}]} )
        audio = {k: v for k, v in old.items() if k in repair.AUDIO_FIELDS}
        audio.update(object_key=f'new-{sid}', performance_object_key=f'new-perf-{sid}', source_common_sha256='c' * 64)
        entries.append(dict(songId=sid, beforeSong=before, beforeAudio=old, targetSong=target, targetAudio=audio))
        songs[sid], audios[(sid, 1)] = copy.deepcopy(before), copy.deepcopy(old)
    return entries, (songs, audios, 0)


class PublicationGuards(unittest.TestCase):
    def test_exact_nine_pending_without_changing_input(self):
        entries, state = fixture()
        before = copy.deepcopy(state)
        self.assertEqual(sorted(repair.IDS), repair.check_state(entries, state))
        self.assertEqual(before, state)
        self.assertNotIn(62, repair.IDS)

    def test_completed_release_is_noop_even_with_new_practice_sessions(self):
        entries, (songs, audios, _) = fixture()
        for item in entries:
            sid = item['songId']
            songs[sid] = item['targetSong']
            audios[(sid, 2)] = dict(item['targetAudio'], songId=sid, version=2, created_at='later', updated_at='later')
        self.assertEqual([], repair.check_state(entries, (songs, audios, 7)))

    def test_stale_score_or_audio_or_missing_row_stops(self):
        for change in ('score', 'oldAudio', 'newAudio', 'missingSong'):
            entries, (songs, audios, count) = fixture()
            if change == 'score': songs[19]['score']['events'].append({'kind': 'REST'})
            if change == 'oldAudio': audios[(19, 1)]['sha256'] = 'd' * 64
            if change == 'newAudio': audios[(19, 2)] = dict(entries[0]['targetAudio'], sha256='d' * 64)
            if change == 'missingSong': del songs[19]
            with self.subTest(change=change), self.assertRaises(ValueError):
                repair.check_state(entries, (songs, audios, count))

    def test_existing_practice_blocks_pending_replacement(self):
        entries, (songs, audios, _) = fixture()
        with self.assertRaisesRegex(ValueError, 'sessions'):
            repair.check_state(entries, (songs, audios, 1))

    def test_sql_contains_one_transaction_and_only_selected_rows(self):
        entries, _ = fixture()
        sql = repair.build_sql(entries, [19, 104])
        self.assertEqual(1, sql.count('START TRANSACTION;'))
        self.assertEqual(1, sql.count('COMMIT;'))
        self.assertEqual(2, sql.count('INSERT INTO song_model_audios('))
        self.assertEqual(2, sql.count('UPDATE songs SET'))
        self.assertIn('jangdan_id <=> 3', sql)
        self.assertIn('score_version=1 AND is_scorable=FALSE', sql)
        self.assertNotIn('id=62 ', sql)
        self.assertNotIn('DELETE ', sql)


@unittest.skipUnless(os.environ.get('REPAIR_SCORE_TEST_MYSQL_CONTAINER'), 'Disposable MySQL not selected')
class MySqlPublication(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = os.environ['REPAIR_SCORE_TEST_MYSQL_CONTAINER']
        if not cls.container.startswith('eolssu-existing-score-704-test'):
            raise ValueError('Only the task-owned disposable MySQL container is allowed')
        cls.password = os.environ['REPAIR_SCORE_TEST_MYSQL_PASSWORD']
        cls.sql('CREATE DATABASE IF NOT EXISTS repair_score_699_test CHARACTER SET utf8mb4;', database=False)

    @classmethod
    def sql(cls, sql, *, database=True):
        args = ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + cls.password, cls.container,
                'mysql', '--no-defaults', '--skip-reconnect', '-uroot', '--batch', '--raw',
                '--skip-column-names', '--default-character-set=utf8mb4']
        if database:
            args.append('repair_score_699_test')
        result = subprocess.run(args, input=sql, text=True, encoding='utf-8', capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr)
        return result.stdout.strip()

    def setUp(self):
        self.entries, _ = fixture()
        self.sql('''DROP TABLE IF EXISTS practice_sessions; DROP TABLE IF EXISTS song_model_audios;
          DROP TABLE IF EXISTS songs;
          CREATE TABLE songs (id BIGINT PRIMARY KEY,instrument_id BIGINT,jangdan_id BIGINT NULL,title VARCHAR(100),
          difficulty INT,default_bpm INT,score_json JSON,score_version INT,umulsai_text TEXT,
          note_count INT,source VARCHAR(255),is_scorable BOOLEAN,status VARCHAR(20),
          updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),deleted_at DATETIME(6) NULL) ENGINE=InnoDB;
          CREATE TABLE song_model_audios (song_id BIGINT,score_version INT,object_key TEXT,
          performance_object_key TEXT,source_common_sha256 CHAR(64),bpm INT,bpm_unit VARCHAR(32),duration_ms INT,
          start_offset_ms INT,content_type VARCHAR(64),sha256 CHAR(64),status VARCHAR(16),generation_metadata JSON,
          created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)
          ON UPDATE CURRENT_TIMESTAMP(6),PRIMARY KEY(song_id,score_version),FOREIGN KEY(song_id) REFERENCES songs(id)) ENGINE=InnoDB;
          CREATE TABLE practice_sessions(id BIGINT PRIMARY KEY,song_id BIGINT,FOREIGN KEY(song_id) REFERENCES songs(id)) ENGINE=InnoDB;''')
        sql = []
        for item in self.entries:
            s, a = item['beforeSong'], item['beforeAudio']
            sql.append(f"INSERT INTO songs(id,instrument_id,jangdan_id,title,difficulty,default_bpm,score_json,score_version,note_count,source,is_scorable,status) VALUES ({s['id']},1,{s['jangdan'] or 'NULL'},{repair.quote(s['title'])},1,90,{repair.literal(s['score'])},1,1,{repair.quote(s['source'])},FALSE,'PUBLISHED');")
            fields = repair.AUDIO_FIELDS + ('created_at', 'updated_at')
            sql.append(f"INSERT INTO song_model_audios(song_id,score_version,{','.join(fields)}) VALUES({s['id']},1," + ','.join(repair.literal(a[k]) for k in fields) + ');')
        self.sql('\n'.join(sql))
        _, audios, _ = repair.inventory(self.sql)
        for item in self.entries:
            item['beforeAudio'] = audios[(item['songId'], 1)]

    def test_atomic_apply_preserves_old_audio_and_is_idempotent(self):
        before = repair.inventory(self.sql)
        pending = repair.check_state(self.entries, before)
        self.sql(repair.build_sql(self.entries, pending))
        after = repair.inventory(self.sql)
        self.assertEqual([], repair.check_state(self.entries, after))
        self.assertEqual(18, len(after[1]))
        self.assertTrue(all(after[1][k] == v for k, v in before[1].items()))
        self.sql(repair.build_sql(self.entries, []))
        self.assertEqual(after, repair.inventory(self.sql))

    def test_last_song_metadata_audio_and_practice_conflicts_roll_back_all_nine(self):
        for conflict in ("UPDATE songs SET title='changed' WHERE id=109;",
                         "UPDATE song_model_audios SET sha256=REPEAT('d',64) WHERE song_id=109;",
                         "INSERT INTO song_model_audios(song_id,score_version) VALUES(109,3);",
                         "INSERT INTO practice_sessions VALUES(1,109);"):
            with self.subTest(conflict=conflict):
                self.setUp()
                self.sql(conflict)
                before = repair.inventory(self.sql)
                with self.assertRaises(RuntimeError):
                    self.sql(repair.build_sql(self.entries, sorted(repair.IDS)))
                self.assertEqual(before, repair.inventory(self.sql))


if __name__ == '__main__':
    unittest.main()
