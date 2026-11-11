import copy
from contextlib import nullcontext
import json
import os
from pathlib import Path
import sys
import subprocess
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import publish_score_tempo as tool


def fixture(root, sid=54):
    score = {'schemaVersion': 1, 'score': {'sourceId': f'src-{sid}', 'scoreVersion': 2, 'instrument': 'JEONGAK_DAEGEUM'},
             'music': {'jangdan': '', 'bpmUnit': 'QUARTER', 'ticksPerBeat': 4, 'ticksPerJeonggan': 2},
             'events': [{'id': 'e0', 'kind': 'NOTE', 'startTick': 0, 'durationTick': 8}],
             'columns': [], 'provenance': {'review': 'inferred'}}
    before = dict(id=sid, source=f'src-{sid}', version=2, bpm=30, scorable=1, notes=1,
                  status='PUBLISHED', deleted=None, jangdan=None, score=score, title='뱃노래',
                  difficulty=2, instrumentId=2, umulsai=None, description=None)
    old = dict(songId=sid, version=2, object_key=f'old/{sid}/audio', performance_object_key=f'old/{sid}/performance',
               source_common_sha256='a'*64, bpm=30, bpm_unit='QUARTER', duration_ms=4550,
               start_offset_ms=0, content_type='audio/mp4', sha256='b'*64, status='PUBLISHED',
               generation_metadata={'original': True}, created_at='2026-11-01 00:00:00', updated_at='2026-11-01 00:00:00')
    target = copy.deepcopy(score)
    target['score']['scoreVersion'] = 3
    target['music']['jangdan'] = '굿거리'
    body = json.dumps(target, ensure_ascii=False).encode()
    audio = b'test audio bytes'
    perf = dict(schema='common-score-performance/v1', policy='educational-preview/v1',
                sourceId=f'src-{sid}', instrument='JEONGAK_DAEGEUM', scoreVersion=3,
                sourceSha256=tool.sha(body), audioSha256=tool.sha(audio), bpm=99, bpmUnit='QUARTER',
                durationMs=1212.1212, segments=[dict(eventId='e0', startMs=0, endMs=1212.1212)])
    files = {}
    for key, path, content in [('scoreFile',f'{sid}/source.json',body), ('audioFile',f'{sid}/audio.m4a',audio),
                               ('performanceFile',f'{sid}/performance.json',json.dumps(perf).encode())]:
        p = root/path
        p.parent.mkdir(exist_ok=True)
        p.write_bytes(content)
        files[key] = dict(path=path, sha256=tool.sha(content), bytes=len(content))
    meta = {k: v for k, v in old.items() if k in tool.AUDIO_FIELDS and k not in ('object_key','performance_object_key')}
    meta.update(bpm=99, duration_ms=1762, source_common_sha256=tool.sha(body), sha256=tool.sha(audio))
    entry = dict(songId=sid, beforeSong=before, beforeAudio=old, targetBpm=99, audioMetadata=meta, **files)
    (root/'release.json').write_text(json.dumps(dict(schema=tool.SCHEMA,task=tool.TASK,entries=[entry]),ensure_ascii=False),encoding='utf-8')
    return entry


class ReleaseGuards(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.original = fixture(self.root)

    def test_tempo_and_jangdan_only_preserve_scoring_and_history(self):
        entries, objects, digest = tool.load_release(self.root)
        e = entries[0]
        self.assertEqual((3,99,1),tuple(e['targetSong'][k] for k in ('version','bpm','scorable')))
        self.assertEqual(self.original['beforeSong']['score']['events'], e['targetSong']['score']['events'])
        current = ({54: e['beforeSong']}, {(54,2):e['beforeAudio']})
        self.assertEqual([54], tool.check_state(entries,current))
        after = ({54:e['targetSong']},{(54,2):e['beforeAudio'],(54,3):dict(e['targetAudio'],songId=54,version=3,created_at='now',updated_at='now')})
        self.assertEqual([],tool.check_state(entries,after))
        self.assertEqual(4,len(objects))

    def test_source_audio_or_event_drift_is_rejected(self):
        entries, _, _ = tool.load_release(self.root)
        for section, key, value in [('song','bpm',60),('song','scorable',0),('audio','sha256','c'*64)]:
            songs, audios = {54:copy.deepcopy(entries[0]['beforeSong'])},{(54,2):copy.deepcopy(entries[0]['beforeAudio'])}
            (songs[54] if section=='song' else audios[(54,2)])[key] = value
            with self.subTest(section=section,key=key),self.assertRaises(ValueError):
                tool.check_state(entries,(songs,audios))
        entry = self.original
        p=self.root/entry['scoreFile']['path']
        score=json.loads(p.read_bytes()); score['events'][0]['durationTick']=9
        raw=json.dumps(score).encode(); p.write_bytes(raw)
        release=json.loads((self.root/'release.json').read_bytes())
        release['entries'][0]['scoreFile'].update(sha256=tool.sha(raw),bytes=len(raw))
        (self.root/'release.json').write_text(json.dumps(release),encoding='utf-8')
        with self.assertRaises(ValueError): tool.load_release(self.root)

    def test_mismatched_upload_receipt_cannot_authorize_db_write(self):
        _, objects, digest = tool.load_release(self.root)
        receipt=dict(schema=tool.UPLOAD_SCHEMA,releaseSha256=digest,status='uploaded',objects=[dict(o,status='new') for o in objects])
        tool.validate_upload_receipt(receipt,objects,digest)
        receipt['objects'][0]['sha256']='0'*64
        with self.assertRaises(ValueError): tool.validate_upload_receipt(receipt,objects,digest)

    def test_notation_correction_preserves_speed_and_rejects_other_changes(self):
        entry = copy.deepcopy(self.original)
        entry['targetBpm'] = 60
        entry['beatNotationCorrection'] = {'basis':'paired staff notation: one cell is one quarter'}
        target = copy.deepcopy(entry['beforeSong']['score'])
        target['score']['scoreVersion'] = 3
        target['music']['ticksPerBeat'] = 2
        tool.validate_score_change(entry,target)
        entry['targetBpm'] = 61
        with self.assertRaises(ValueError): tool.validate_score_change(entry,target)

    def test_airplane_scope_is_explicitly_rejected(self):
        entry=fixture(self.root,56)
        with self.assertRaisesRegex(ValueError,'Airplane'): tool.load_release(self.root)

    def test_hidden_source_preserves_hidden_and_draft_status(self):
        release=json.loads((self.root/'release.json').read_bytes())
        entry=release['entries'][0]
        entry['beforeSong']['status']='HIDDEN'
        entry['beforeAudio']['status']=entry['audioMetadata']['status']='DRAFT'
        (self.root/'release.json').write_text(json.dumps(release),encoding='utf-8')
        entries,_,_=tool.load_release(self.root)
        self.assertEqual('HIDDEN',entries[0]['targetSong']['status'])
        self.assertEqual('DRAFT',entries[0]['targetAudio']['status'])

    def test_disconnected_transaction_records_unknown_before_execution(self):
        entries,objects,digest=tool.load_release(self.root)
        with tempfile.TemporaryDirectory() as receipts:
            receipt=Path(receipts)/'apply.json'
            upload=Path(receipts)/'upload.json'
            upload.write_text(json.dumps(dict(schema=tool.UPLOAD_SCHEMA,releaseSha256=digest,status='uploaded',
                                             objects=[dict(o,status='new') for o in objects])),encoding='utf-8')
            def runner(sql):
                if sql.startswith('CREATE TEMPORARY'):
                    self.assertEqual('apply-outcome-unknown',json.loads(receipt.read_bytes())['status'])
                    raise RuntimeError('connection lost')
                if 'FROM practice_sessions' in sql: return '0'
                if 'FROM song_model_audios' in sql: return json.dumps(entries[0]['beforeAudio'])
                return json.dumps(entries[0]['beforeSong'])
            with patch.object(tool,'deployment_lock',return_value=nullcontext()),patch.object(tool,'backup'):
                with self.assertRaises(RuntimeError):
                    tool.run(self.root,receipt,apply=True,upload_receipt=upload,runner=runner)
            self.assertEqual('apply-outcome-unknown',json.loads(receipt.read_bytes())['status'])

    def test_transaction_guards_old_rows_and_inserts_new_version(self):
        entries,_,_=tool.load_release(self.root)
        sql=tool.build_sql(entries,[54])
        self.assertEqual(1,sql.count('START TRANSACTION;'))
        self.assertEqual(1,sql.count('COMMIT;'))
        self.assertIn('INSERT INTO song_model_audios',sql)
        self.assertNotIn('UPDATE song_model_audios',sql)
        self.assertNotIn('DELETE ',sql)
        self.assertIn('score_version <=> 2',sql)
        self.assertIn('default_bpm <=> 30',sql)
        self.assertIn('default_bpm=99',sql)
        self.assertIn('is_scorable <=> 1',sql)
        self.assertIn('FOR UPDATE',sql)


@unittest.skipUnless(os.environ.get('TEMPO_TEST_MYSQL_CONTAINER'), 'Disposable MySQL not selected')
class MySqlCorrection(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container=os.environ['TEMPO_TEST_MYSQL_CONTAINER']
        if cls.container != 'eolssu-tempo-728-test': raise ValueError('Only isolated task MySQL allowed')
        cls.sql('CREATE DATABASE IF NOT EXISTS tempo728_test CHARACTER SET utf8mb4;',database=False)

    @classmethod
    def sql(cls,sql,*,database=True):
        args=['docker','exec','-i','-e','MYSQL_PWD='+os.environ['TEMPO_TEST_MYSQL_PASSWORD'],cls.container,
              'mysql','--no-defaults','--skip-reconnect','--default-character-set=utf8mb4','-uroot','--batch','--raw','--skip-column-names']
        if database: args += ['--database=tempo728_test']
        result=subprocess.run(args,input=sql.encode(),capture_output=True,timeout=30)
        if result.returncode: raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout.decode().strip()

    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        root=Path(self.temp.name)
        a,b=fixture(root,54),fixture(root,55)
        (root/'release.json').write_text(json.dumps(dict(schema=tool.SCHEMA,task=tool.TASK,entries=[a,b])),encoding='utf-8')
        self.entries,_,_=tool.load_release(root)
        self.sql('DROP TABLE IF EXISTS practice_sessions; DROP TABLE IF EXISTS song_model_audios; DROP TABLE IF EXISTS songs;'
                 'CREATE TABLE songs (id BIGINT PRIMARY KEY,source VARCHAR(255),score_version INT,default_bpm INT,is_scorable BOOLEAN,'
                 'note_count INT,status VARCHAR(20),deleted_at DATETIME(6),jangdan_id BIGINT,score_json JSON,title VARCHAR(100),'
                 'difficulty INT,instrument_id BIGINT,umulsai_text TEXT,description TEXT) ENGINE=InnoDB;'
                 'CREATE TABLE song_model_audios (song_id BIGINT,score_version INT,object_key VARCHAR(512),performance_object_key VARCHAR(512),'
                 'source_common_sha256 CHAR(64),bpm INT,bpm_unit VARCHAR(32),duration_ms INT,start_offset_ms INT,content_type VARCHAR(64),'
                 'sha256 CHAR(64),status VARCHAR(20),generation_metadata JSON,created_at DATETIME(6),updated_at DATETIME(6),'
                 'PRIMARY KEY(song_id,score_version),FOREIGN KEY(song_id) REFERENCES songs(id)) ENGINE=InnoDB;'
                 'CREATE TABLE practice_sessions (id BIGINT PRIMARY KEY,song_id BIGINT,status VARCHAR(20),'
                 'FOREIGN KEY(song_id) REFERENCES songs(id), INDEX(song_id)) ENGINE=InnoDB;')
        for e in self.entries:
            before=e['beforeSong']
            self.sql('INSERT INTO songs('+','.join(tool.SONG_COLUMNS.values())+') VALUES('+','.join(tool.literal(before[k]) for k in tool.SONG_COLUMNS)+');')
            old=e['beforeAudio']
            fields=dict(songId='song_id',version='score_version',**{k:k for k in (*tool.AUDIO_FIELDS,'created_at','updated_at')})
            self.sql('INSERT INTO song_model_audios('+','.join(fields.values())+') VALUES('+','.join(tool.literal(old[k]) for k in fields)+');')
        # Use the DB's DATETIME(6) serialization for exact old-audio snapshots.
        _,audios=tool.inventory([54,55],self.sql)
        for e in self.entries: e['beforeAudio']=audios[(e['songId'],2)]

    def test_atomic_apply_retains_old_audio_and_completed_sessions(self):
        self.sql("INSERT INTO practice_sessions VALUES(1,54,'COMPLETED');")
        self.assertEqual([54,55],tool.check_state(self.entries,tool.inventory([54,55],self.sql)))
        self.sql(tool.build_sql(self.entries,[54,55]))
        state=tool.inventory([54,55],self.sql)
        self.assertEqual([],tool.check_state(self.entries,state))
        self.assertEqual(4,len(state[1]))
        self.assertEqual('COMPLETED',self.sql('SELECT status FROM practice_sessions WHERE id=1;'))

    def test_second_song_drift_rolls_back_first_song_and_audio(self):
        self.sql("UPDATE songs SET title='concurrent edit' WHERE id=55;")
        with self.assertRaises(RuntimeError): self.sql(tool.build_sql(self.entries,[54,55]))
        songs,audios=tool.inventory([54,55],self.sql)
        self.assertEqual(30,songs[54]['bpm'])
        self.assertEqual(2,len(audios))

    def test_active_session_added_after_preflight_aborts_all(self):
        self.sql("INSERT INTO practice_sessions VALUES(1,55,'RECORDING');")
        with self.assertRaises(RuntimeError): self.sql(tool.build_sql(self.entries,[54,55]))
        songs,audios=tool.inventory([54,55],self.sql)
        self.assertEqual(30,songs[54]['bpm'])
        self.assertEqual(2,len(audios))


if __name__=='__main__': unittest.main()
