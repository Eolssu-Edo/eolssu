import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from contextlib import nullcontext
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parent))
import publish_airplane_revision as tool
import publish_score_tempo as base


def entry():
    # Fixed publication fixture: this BE/ops test does not depend on the later DATA release.
    target = dict(schemaVersion=1,
        score=dict(title='게시 검사 곡', sourceId=tool.SOURCE, instrument='JEONGAK_DAEGEUM', scoreVersion=2),
        music=dict(hwangHz=311.13, jangdan='', bpmUnit='QUARTER', ticksPerBeat=6, ticksPerJeonggan=6),
        columns=[dict(index=0, cells=[dict(id=f'c0-j{i}', index=i, units=[
            dict(row=0, col=0, symbol='황', eventId=f'e{i}')]) for i in range(25)])],
        events=[dict(id=f'e{i}', kind='NOTE', yul='황', octave=0, startTick=i*6, durationTick=6,
                     cellId=f'c0-j{i}', breathAfter=False, sigimsae=[]) for i in range(25)],
        provenance=dict(jgbSha256=tool.JGB))
    original = copy.deepcopy(target)
    original['score']['scoreVersion'] = 1
    original['music']['ticksPerBeat'] = 4
    before = dict(id=56, source=original['score']['sourceId'], version=1, bpm=90, scorable=1, notes=25,
                  status='PUBLISHED', deleted=None, jangdan=None, score=original, title=original['score']['title'],
                  difficulty=1, instrumentId=3, umulsai=None, description='CAT credit')
    audio = {k: None for k in base.AUDIO_FIELDS}
    audio.update(object_key='model-audio/drafts/'+'a'*64+'/cases/jeongak-daegeum-006-p1/jeongak-daegeum.m4a',
                 performance_object_key='model-audio/drafts/'+'a'*64+'/cases/jeongak-daegeum-006-p1/performance.common.json',
                 source_common_sha256='b'*64, bpm=90, bpm_unit='QUARTER', duration_ms=32550, start_offset_ms=0,
                 content_type='audio/mp4', sha256='c'*64, status='PUBLISHED', generation_metadata={})
    old = dict(audio, songId=56, version=1, created_at='2026-11-10 00:00:00.000000', updated_at='2026-11-10 00:00:00.000000')
    after = dict(before, version=2, bpm=77, score=target)
    return dict(songId=56, beforeSong=before, beforeSongTimes=dict(created_at='2026-11-01 00:00:00.000000',updated_at='2026-11-01 00:00:00.000000'),
                beforeAudio=old, targetSong=after,
                targetAudio=dict(audio, bpm=77, source_common_sha256='d'*64, sha256='e'*64))


def bundle(root):
    e = entry()
    target = e['targetSong']['score']
    score = json.dumps(target,ensure_ascii=False).encode()
    audio = b'fixture-only; actual AAC decoding is verified by the renderer'
    segments = []
    # Reuse the shared written-slot enumerator only to construct a valid input fixture.
    for index,slot in enumerate(base.written_slots(target)):
        segments.append(dict(slot,occurrenceId=f'slot-{index}',
            startTick=float(slot['startTick']),endTick=float(slot['endTick']),
            startMs=float(slot['startTick'])*60000/77/6,endMs=float(slot['endTick'])*60000/77/6,sigimsae=[]))
    perf = dict(schema='common-score-performance/v1',policy='educational-preview/v1',
        sourceId=tool.SOURCE,instrument='JEONGAK_DAEGEUM',scoreVersion=2,sourceSha256=tool.sha(score),
        audioSha256=tool.sha(audio),bpm=77,bpmUnit='QUARTER',durationMs=segments[-1]['endMs'],segments=segments)
    descriptors = {}
    for role,name,raw in [('scoreFile','source.common.json',score),('audioFile','jeongak-daegeum.m4a',audio),
                          ('performanceFile','performance.common.json',json.dumps(perf).encode())]:
        path = 'cases/jeongak-daegeum-006-p1/'+name
        (root/path).parent.mkdir(parents=True,exist_ok=True)
        (root/path).write_bytes(raw)
        descriptors[role] = dict(path=path,sha256=tool.sha(raw),bytes=len(raw))
    metadata = {k:v for k,v in e['targetAudio'].items() if k not in ('object_key','performance_object_key')}
    metadata.update(source_common_sha256=tool.sha(score),sha256=tool.sha(audio),duration_ms=25485)
    release = dict(task=tool.TASK,schema=tool.SCHEMA,
        entries=[dict(songId=56,beforeSong=e['beforeSong'],beforeSongTimes=e['beforeSongTimes'],beforeAudio=e['beforeAudio'],
                      targetBpm=77,audioMetadata=metadata,**descriptors)],
        preserve=dict(songs={str(i):dict(id=i) for i in (132,133,134)},audios={str(i):[] for i in (132,133,134)}))
    (root/'release.json').write_text(json.dumps(release,ensure_ascii=False),encoding='utf-8')
    return release


class ArchiveTransaction(unittest.TestCase):
    def test_archive_is_captured_before_current_score_changes_in_same_transaction(self):
        sql = tool.build_sql([entry()], [56])
        self.assertTrue('INSERT INTO song_score_versions' in sql, 'old revision must be archived')
        self.assertLess(sql.index('INSERT INTO song_score_versions'), sql.index('UPDATE songs SET'))
        self.assertEqual(1, sql.count('START TRANSACTION'))
        self.assertEqual(1, sql.count('COMMIT;'))
        self.assertNotIn('DELETE FROM', sql)
        self.assertNotIn('UPDATE practice_sessions', sql)

    def test_archive_uses_old_metadata_and_refuses_an_existing_different_snapshot(self):
        e = entry()
        sql = tool.build_sql([e], [56])
        self.assertIn('NOT EXISTS(SELECT 1 FROM song_score_versions WHERE song_id=56 AND score_version=1)', sql)
        self.assertIn('SELECT COUNT(*) FROM song_score_versions WHERE', sql)
        self.assertIn('created_at', sql)
        self.assertNotIn('ON DUPLICATE KEY UPDATE', sql)
        self.assertNotIn('REPLACE INTO', sql)

    def test_only_56_is_allowed(self):
        e = entry()
        e['songId'] = 57
        with self.assertRaises(ValueError):
            tool.build_sql([e], [57])

    def test_preserved_three_and_old_audio_must_match_before_and_after(self):
        e = entry()
        songs = {56:e['beforeSong'], **{i:dict(id=i,version=1,bpm=77) for i in (132,133,134)}}
        audios = {(56,1):e['beforeAudio'], **{(i,1):dict(songId=i,version=1) for i in (132,133,134)}}
        preserve = dict(songs={str(i):songs[i] for i in (132,133,134)}, audios={str(i):[audios[(i,1)]] for i in (132,133,134)})
        state = (songs,audios,{},e['beforeSongTimes'],False)
        self.assertEqual([56], tool.check_state([e],state,preserve))
        songs[56] = e['targetSong']
        audios[(56,2)] = e['targetAudio']
        archive = dict(e['beforeSong'],**e['beforeSongTimes'])
        state = (songs,audios,{1:archive},e['beforeSongTimes'],True)
        self.assertEqual([],tool.check_state([e],state,preserve))
        with self.assertRaises(ValueError):
            tool.check_state([e],(songs,audios,{},e['beforeSongTimes'],True),preserve)
        changed = copy.deepcopy(state)
        changed[0][132]['bpm'] = 78
        with self.assertRaises(ValueError):
            tool.check_state([e],changed,preserve)
        changed = copy.deepcopy(state)
        changed[1][(56,1)]['sha256'] = 'f'*64
        with self.assertRaises(ValueError):
            tool.check_state([e],changed,preserve)

    def test_missing_verified_upload_and_ingress_never_apply(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                tool.run(Path(directory),Path(directory)/'receipt.json',apply=True)


class ReleaseInputs(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.release = bundle(self.root)

    def save(self):
        (self.root/'release.json').write_text(json.dumps(self.release,ensure_ascii=False),encoding='utf-8')

    def test_reviewed_quarter_and_tempo_change_is_accepted_and_source_case_is_preserved(self):
        checked,objects,digest,_ = tool.load_release(self.root)
        self.assertEqual((2,77,6),(checked[0]['targetSong']['version'],checked[0]['targetSong']['bpm'],
                         checked[0]['targetSong']['score']['music']['ticksPerBeat']))
        self.assertEqual(4,len(objects))
        self.assertIn('/cases/jeongak-daegeum-006-p1/',checked[0]['targetAudio']['object_key'])
        self.assertTrue(all(o['key'].startswith('model-audio/drafts/'+digest+'/') for o in objects))

    def test_rehashed_pitch_change_is_still_rejected(self):
        descriptor = self.release['entries'][0]['scoreFile']
        target = json.loads((self.root/descriptor['path']).read_bytes())
        target['events'][0]['octave'] += 1
        raw = json.dumps(target).encode()
        (self.root/descriptor['path']).write_bytes(raw)
        descriptor.update(sha256=tool.sha(raw),bytes=len(raw))
        self.save()
        with self.assertRaisesRegex(ValueError,'Only reviewed quarter'):
            tool.load_release(self.root)

    def test_stale_blob_or_wrong_deployed_case_path_is_rejected_before_upload(self):
        descriptor = self.release['entries'][0]['audioFile']
        (self.root/descriptor['path']).write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError,'hash/size'):
            tool.load_release(self.root)
        descriptor['path'] = 'cases/jeongak-daegeum-056-p1/jeongak-daegeum.m4a'
        self.save()
        with self.assertRaisesRegex(ValueError,'catalog payload path'):
            tool.load_release(self.root)

    def test_missing_preserved_song_is_rejected(self):
        del self.release['preserve']['songs']['132']
        self.save()
        with self.assertRaisesRegex(ValueError,'Preserved scope'):
            tool.load_release(self.root)

    def test_default_dry_run_can_wait_for_archive_migration_without_writing(self):
        e = entry()
        songs = {56:e['beforeSong'],**{i:dict(id=i) for i in (132,133,134)}}
        audios = {(56,1):e['beforeAudio']}
        state = (songs,audios,{},e['beforeSongTimes'],False)
        calls = []
        def runner(sql):
            calls.append(sql)
            if 'practice_sessions' in sql: return '0'
            raise AssertionError('unexpected database mutation')
        from unittest.mock import patch
        receipt = self.root.parent/(self.root.name+'-receipt.json')
        self.addCleanup(lambda:receipt.unlink(missing_ok=True))
        with patch.object(tool,'inventory',return_value=state):
            report = tool.run(self.root,receipt,runner=runner)
        self.assertEqual('validated-dry-run',report['status'])
        self.assertFalse(report['archiveMigrationReady'])
        self.assertEqual([56],report['pending'])
        self.assertEqual(1,len(calls))


class IngressProfile(unittest.TestCase):
    def test_airplane_profile_is_used_and_owned_gate_is_restored_after_apply_failure(self):
        import publish_score_tempo_quiesced as gate
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            route = folder/'active.conf'
            route.write_bytes(b'unchanged route')
            target = folder/'gate.conf'
            original_lock = lambda:nullcontext()
            profile = SimpleNamespace(TASK=tool.TASK,deployment_lock=original_lock)
            def apply(*args,**kwargs):
                self.assertTrue(kwargs['traffic_quiesced'])
                with profile.deployment_lock():
                    self.assertTrue(target.is_file())
                    raise RuntimeError('deliberate apply failure')
            profile.run = apply
            def status(path,method='GET'):
                return 503 if target.exists() and path.startswith('/api/practice-sessions') else 200
            argv = ['guard','--bundle',str(folder/'bundle'),'--receipt',str(folder/'receipt.json'),
                    '--upload-receipt',str(folder/'upload.json')]
            with patch.object(gate,'GATE',target),patch.object(gate,'workers',return_value={1}),\
                 patch.object(gate,'reload_and_drain'),patch.object(gate,'drain_backend_posts',return_value={'activePostRequests':0}),\
                 patch.object(gate,'status',side_effect=status),patch.object(sys,'argv',argv):
                with self.assertRaisesRegex(RuntimeError,'deliberate apply failure'):
                    gate.main(profile)
            self.assertFalse(target.exists())
            self.assertEqual(b'unchanged route',route.read_bytes())
            self.assertIs(profile.deployment_lock,original_lock)
            self.assertEqual('restored',json.loads((folder/'receipt.gate.json').read_text())['state'])


if __name__ == '__main__':
    unittest.main()
