"""Publish only reviewed Airplane56 v2, archiving v1 in the same transaction.

Upload is separate from apply. Apply requires the deployed V38 archive table,
the shared deployment lock, a verified upload receipt and quiesced ingress.
The other three Airplane rows and every old audio/session remain unchanged.
"""
import argparse
from copy import deepcopy
from datetime import datetime, timezone
import json
import os
from pathlib import Path

import publish_score_tempo as base
from enable_scoring import mysql, quote
from stage_model_audio import create_client, require, safe_file, sha, upload_objects

TASK = 'S15P31E102-707'
SCHEMA = 'eolssu-airplane-revision-release/v1'
UPLOAD_SCHEMA = 'eolssu-airplane-revision-upload/v1'
SOURCE = 'mr203-fixed-jeongak-daegeum-006-p1'
JGB = 'b8960383360b471a20214c05b2a37b3f511911aaa38f8db358e5e03a28a77e02'
IDS = [56, 132, 133, 134]
AUDIO_FIELDS = base.AUDIO_FIELDS
ARCHIVE_COLUMNS = dict(base.SONG_COLUMNS, id='song_id')
TIMES = ('created_at', 'updated_at')
COPY_COLUMNS = ('score_version', 'instrument_id', 'jangdan_id', 'title', 'description', 'difficulty',
                'default_bpm', 'score_json', 'umulsai_text', 'note_count', 'source', 'is_scorable',
                'status', 'created_at', 'updated_at', 'deleted_at')
deployment_lock = base.deployment_lock


def read_json(path):
    return json.loads(path.read_bytes())


def load_release(root):
    raw = safe_file(root, 'release.json').read_bytes()
    release = json.loads(raw)
    require(set(release) == {'task', 'schema', 'entries', 'preserve'} and
            release['task'] == TASK and release['schema'] == SCHEMA, 'Wrong Airplane release')
    require(len(release['entries']) == 1, 'Only one Jeongak revision is allowed')
    entry = release['entries'][0]
    require(set(entry) == {'songId', 'beforeSong', 'beforeSongTimes', 'beforeAudio', 'targetBpm',
                          'scoreFile', 'audioFile', 'performanceFile', 'audioMetadata'}, 'Entry fields differ')
    before, old = entry['beforeSong'], entry['beforeAudio']
    require(set(before) == set(base.SONG_COLUMNS) and
            set(entry['beforeSongTimes']) == set(TIMES) and
            all(isinstance(v, str) and v.strip() for v in entry['beforeSongTimes'].values()), 'Before snapshot fields differ')
    require(set(old) == set(AUDIO_FIELDS) | {'songId', 'version', 'created_at', 'updated_at'}, 'Old audio fields differ')
    original = before['score']
    require(entry['songId'] == before['id'] == old['songId'] == 56 and
            before['version'] == old['version'] == original['score']['scoreVersion'] == 1 and
            before['bpm'] == old['bpm'] == 90 and entry['targetBpm'] == 77 and
            before['source'] == original['score']['sourceId'] == SOURCE and
            original['score']['instrument'] == 'JEONGAK_DAEGEUM' and
            original['provenance']['jgbSha256'] == JGB and
            original['music']['ticksPerBeat'] == 4 and original['music']['ticksPerJeonggan'] == 6 and
            original['music']['bpmUnit'] == old['bpm_unit'] == 'QUARTER' and
            before['notes'] == 25 and before['scorable'] == 1 and before['deleted'] is None and
            before['status'] == old['status'] == 'PUBLISHED', 'Original56 identity or publication differs')
    directory = 'cases/jeongak-daegeum-006-p1/'
    expected_paths = dict(scoreFile=directory+'source.common.json', audioFile=directory+'jeongak-daegeum.m4a',
                          performanceFile=directory+'performance.common.json')
    prefix = 'model-audio/drafts/' + sha(raw) + '/'
    bodies, uploads = {}, []
    for role, path in expected_paths.items():
        item = entry[role]
        require(set(item) == {'path', 'sha256', 'bytes'} and item['path'] == path, 'Unexpected catalog payload path')
        body = safe_file(root, path).read_bytes()
        require(sha(body) == item['sha256'] and len(body) == item['bytes'], 'Payload hash/size mismatch')
        bodies[role] = body
        uploads.append(dict(item, key=prefix+path))
    target, perf = json.loads(bodies['scoreFile']), json.loads(bodies['performanceFile'])
    expected = deepcopy(original)
    expected['score']['scoreVersion'] = 2
    expected['music']['ticksPerBeat'] = 6
    require(target == expected, 'Only reviewed quarter notation and next version may change')
    require(perf['schema'] == 'common-score-performance/v1' and perf['policy'] == 'educational-preview/v1' and
            perf['scoreVersion'] == 2 and perf['sourceId'] == SOURCE and perf['instrument'] == 'JEONGAK_DAEGEUM' and
            perf['sourceSha256'] == sha(bodies['scoreFile']) and perf['audioSha256'] == sha(bodies['audioFile']) and
            perf['bpm'] == 77 and perf['bpmUnit'] == 'QUARTER', 'Performance binding differs')
    end = base.validate_performance_timing(target, perf, 77)
    meta = entry['audioMetadata']
    require(set(meta) == set(AUDIO_FIELDS)-{'object_key','performance_object_key'} and
            meta['bpm'] == 77 and meta['bpm_unit'] == 'QUARTER' and meta['status'] == 'PUBLISHED' and
            meta['source_common_sha256'] == sha(bodies['scoreFile']) and meta['sha256'] == sha(bodies['audioFile']) and
            meta['content_type'] == 'audio/mp4' and type(meta['duration_ms']) is int and meta['duration_ms'] >= end and
            meta['start_offset_ms'] == 0 and isinstance(meta['generation_metadata'], dict), 'New audio metadata differs')
    preserve = release['preserve']
    require(set(preserve) == {'songs', 'audios'} and set(preserve['songs']) == {'132','133','134'} and
            set(preserve['audios']) == {'132','133','134'} and
            all(preserve['songs'][str(sid)]['id'] == sid for sid in IDS[1:]), 'Preserved scope differs')
    checked = dict(entry, targetSong=dict(before, version=2, bpm=77, score=target),
                   targetAudio=dict(meta, object_key=prefix+expected_paths['audioFile'],
                                    performance_object_key=prefix+expected_paths['performanceFile']))
    uploads.append(dict(path='release.json', sha256=sha(raw), bytes=len(raw), key=prefix+'release.json'))
    return [checked], uploads, sha(raw), preserve


def inventory(runner=mysql):
    songs, audios = base.inventory(IDS, runner)
    exists = int(runner("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='song_score_versions';")) == 1
    times = dict(zip(TIMES, runner("SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f'),DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s.%f') FROM songs WHERE id=56;").split('\t')))
    archives = {}
    if exists:
        fields = ','.join(f"'{key}',{column}" if key != 'scorable' else "'scorable',IF(is_scorable,1,0)"
                          for key, column in ARCHIVE_COLUMNS.items())
        rows = runner(f"SELECT JSON_OBJECT({fields},'created_at',DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f'),'updated_at',DATE_FORMAT(updated_at,'%Y-%m-%d %H:%i:%s.%f')) FROM song_score_versions WHERE song_id=56;")
        archives = {r['version']: r for r in map(json.loads, rows.splitlines())}
    return songs, audios, archives, times, exists


def check_state(entries, current, preserve):
    songs, audios, archives, times, exists = current
    require(set(songs) == set(IDS), 'Missing Airplane songs')
    for sid in IDS[1:]:
        require(songs[sid] == preserve['songs'][str(sid)] and
                [v for (s,_),v in audios.items() if s == sid] == preserve['audios'][str(sid)], 'Other Airplane rows changed')
    e = entries[0]
    require(audios.get((56,1)) == e['beforeAudio'] and all(v in (1,2) for s,v in audios if s == 56), 'Old/future audio differs')
    expected_archive = dict(e['beforeSong'], **e['beforeSongTimes'])
    archive = archives.get(1)
    require(archive is None or archive == expected_archive, 'Immutable v1 archive differs')
    require(not set(archives)-{1}, 'Unexpected archived version')
    new = audios.get((56,2))
    if songs[56] == e['beforeSong'] and new is None:
        require(times == e['beforeSongTimes'], 'Original timestamps changed')
        return [56]
    require(exists and archive == expected_archive and songs[56] == e['targetSong'] and new is not None and
            all(new[k] == e['targetAudio'][k] for k in AUDIO_FIELDS), 'Revision drift/partial application')
    return []


def build_sql(entries, pending):
    sql = ['CREATE TEMPORARY TABLE _airplane707_assert (ok INT NOT NULL CHECK(ok=1));',
           'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;',
           "INSERT INTO _airplane707_assert SELECT IF(COUNT(*)=3,1,0) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('songs','song_model_audios','song_score_versions') AND engine='InnoDB';"]
    for e in entries:
        if e['songId'] not in pending:
            continue
        require(e['songId'] == 56, 'Only56 may be published')
        guard = base.row_guard(base.SONG_COLUMNS, e['beforeSong'])
        guard += ' AND ' + base.row_guard({k:k for k in TIMES}, e['beforeSongTimes'])
        archive_guard = base.row_guard(ARCHIVE_COLUMNS, e['beforeSong'])
        archive_guard += ' AND ' + base.row_guard({k:k for k in TIMES}, e['beforeSongTimes'])
        audio_guard = base.row_guard(dict(songId='song_id',version='score_version',**{k:k for k in (*AUDIO_FIELDS,*TIMES)}), e['beforeAudio'])
        sql += ['SELECT id FROM songs WHERE id=56 FOR UPDATE;',
                'SELECT song_id FROM song_model_audios WHERE song_id=56 FOR UPDATE;',
                'SELECT song_id FROM song_score_versions WHERE song_id=56 FOR UPDATE;',
                "INSERT INTO _airplane707_assert SELECT IF(COUNT(*)=0,1,0) FROM practice_sessions WHERE song_id=56 AND status IN ('RECORDING','PENDING');",
                'INSERT INTO _airplane707_assert SELECT COUNT(*) FROM songs WHERE '+guard+';',
                'INSERT INTO _airplane707_assert SELECT COUNT(*) FROM song_model_audios WHERE '+audio_guard+';',
                'INSERT INTO _airplane707_assert SELECT IF(COUNT(*)=0,1,0) FROM song_model_audios WHERE song_id=56 AND score_version=2;',
                'INSERT INTO song_score_versions(song_id,'+','.join(COPY_COLUMNS)+') SELECT id,'+','.join(COPY_COLUMNS)+
                ' FROM songs WHERE '+guard+' AND NOT EXISTS(SELECT 1 FROM song_score_versions WHERE song_id=56 AND score_version=1);',
                'INSERT INTO _airplane707_assert SELECT COUNT(*) FROM song_score_versions WHERE '+archive_guard+';',
                'INSERT INTO song_model_audios(song_id,score_version,'+','.join(AUDIO_FIELDS)+') VALUES(56,2,'+
                ','.join(base.literal(e['targetAudio'][k]) for k in AUDIO_FIELDS)+');',
                'INSERT INTO _airplane707_assert VALUES(ROW_COUNT());',
                'UPDATE songs SET score_json='+base.literal(e['targetSong']['score'])+',score_version=2,default_bpm=77 WHERE '+guard+';',
                'INSERT INTO _airplane707_assert VALUES(ROW_COUNT());']
    return '\n'.join(sql+['COMMIT;'])


def run(root, receipt, *, apply=False, upload_only=False, upload_receipt=None, traffic_quiesced=False,
        env_file='/opt/eolssu/.env', runner=mysql):
    require(not (apply and upload_only), 'Choose upload or apply')
    require(not apply or traffic_quiesced is True, 'Apply requires drained session ingress')
    root, receipt = Path(root), Path(receipt)
    require(not receipt.resolve().is_relative_to(root.resolve()), 'Receipt must stay outside immutable bundle')
    report = dict(task=TASK, schema=UPLOAD_SCHEMA if upload_only else SCHEMA, status='started')
    fd = os.open(receipt, os.O_WRONLY|os.O_CREAT|os.O_EXCL, 0o600)
    with os.fdopen(fd,'w',encoding='utf-8') as stream:
        def save():
            report['checkedAtUtc'] = datetime.now(timezone.utc).isoformat()
            stream.seek(0); json.dump(report,stream,ensure_ascii=False,indent=2)
            stream.write('\n'); stream.truncate(); stream.flush(); os.fsync(stream.fileno())
        try:
            entries, objects, digest, preserve = load_release(root)
            report['releaseSha256'] = digest
            if upload_only:
                client, bucket = create_client(env_file)
                report['objects'] = [dict(o,status='validated-local') for o in objects]
                upload_objects(root,client,bucket,report,workers=2)
                for item in objects:
                    meta = client.head_object(Bucket=bucket,Key=item['key'])
                    require('Expiration' not in meta and meta.get('ContentType') == ('audio/mp4' if item['path'].endswith('.m4a') else 'application/json'), 'Remote MIME/expiry differs')
                report['status'] = 'uploaded'
                return report
            before = inventory(runner)
            pending = check_state(entries,before,preserve)
            active = base.active_sessions([56],runner)
            report.update(status='validated-dry-run',pending=pending,activeSessions=active,archiveMigrationReady=before[-1])
            if not apply or not pending:
                return report
            require(before[-1], 'Deploy V38 archive migration before apply')
            require(active == 0, 'Active sessions block publication')
            require(upload_receipt is not None, 'Verified upload receipt required')
            uploaded = read_json(Path(upload_receipt))
            require(uploaded.get('schema') == UPLOAD_SCHEMA, 'Wrong upload profile')
            compatible = dict(uploaded,schema=base.UPLOAD_SCHEMA)
            base.validate_upload_receipt(compatible,objects,digest)
            with deployment_lock():
                require(inventory(runner) == before and base.active_sessions([56],runner) == 0, 'Catalog/sessions changed')
                base.backup(receipt)
                require(inventory(runner) == before, 'Catalog changed during backup')
                report['status'] = 'apply-outcome-unknown'; save()
                runner(build_sql(entries,pending))
                report['status'] = 'committed-readback-pending'; save()
                require(check_state(entries,inventory(runner),preserve) == [], 'Incomplete readback')
                report.update(status='applied',updated=pending,pending=[])
            return report
        except BaseException as error:
            report['errorType'] = type(error).__name__
            raise
        finally:
            save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle',type=Path,required=True)
    parser.add_argument('--receipt',type=Path,required=True)
    parser.add_argument('--upload-only',action='store_true')
    args = parser.parse_args()
    result = run(args.bundle,args.receipt,upload_only=args.upload_only)
    print(json.dumps({k:v for k,v in result.items() if k!='objects'},ensure_ascii=False))


if __name__ == '__main__':
    main()
