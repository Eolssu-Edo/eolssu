"""Publish the nine Task699 repairs with their v2 audio atomically.

The caller holds the existing deployment lock. Default execution only checks
local files and the database. --apply backs up before changing any database row.
Old audio rows/objects and existing score views are retained.
"""
import argparse
from copy import deepcopy
from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess

from enable_scoring import mysql, quote
from stage_model_audio import create_client, require, safe_file, sha, upload_objects

IDS = {19, 32, 37, 45, 50, 63, 65, 104, 109}
AUDIO_FIELDS = ('object_key', 'performance_object_key', 'source_common_sha256', 'bpm', 'bpm_unit',
                'duration_ms', 'start_offset_ms', 'content_type', 'sha256', 'status', 'generation_metadata')


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def inventory(runner=mysql):
    ids = ','.join(map(str, sorted(IDS)))
    fields = "'id',id,'source',source,'version',score_version,'bpm',default_bpm,'scorable',IF(is_scorable,1,0),"
    fields += "'notes',note_count,'status',status,'deleted',deleted_at,'jangdan',jangdan_id,'score',score_json,"
    fields += "'title',title,'difficulty',difficulty,'instrumentId',instrument_id,'umulsai',umulsai_text"
    songs = {r['id']: r for r in map(json.loads, runner(f'SELECT JSON_OBJECT({fields}) FROM songs WHERE id IN ({ids}) ORDER BY id;').splitlines())}
    pairs = ','.join(f"'{field}',{field}" for field in AUDIO_FIELDS)
    sql = f"SELECT JSON_OBJECT('songId',song_id,'version',score_version,{pairs},'created_at',created_at,'updated_at',updated_at) FROM song_model_audios WHERE song_id IN ({ids}) ORDER BY song_id,score_version;"
    audios = {(r['songId'], r['version']): r for r in map(json.loads, runner(sql).splitlines())}
    sessions = int(runner(f'SELECT COUNT(*) FROM practice_sessions WHERE song_id IN ({ids});'))
    return songs, audios, sessions


def load_release(root):
    raw = safe_file(root, 'release.json').read_bytes()
    release = json.loads(raw)
    require(release['task'] == 'S15P31E102-699' and release['schema'] == 'eolssu-scoring-repair-release/v1', 'Wrong release')
    entries = release['entries']
    require(len(entries) == len(IDS) and {e['songId'] for e in entries} == IDS, 'Wrong repair scope')
    prefix = 'model-audio/drafts/' + sha(raw) + '/'
    checked, uploads = [], []
    for entry in entries:
        before = entry['beforeSong']
        old_audio = entry['beforeAudio']
        sid = entry['songId']
        require(before['id'] == old_audio['songId'] == sid and before['version'] == old_audio['version'] == 1,
                'Wrong original identity')
        require(before['status'] == old_audio['status'] == 'PUBLISHED' and before['scorable'] == 0 and before['deleted'] is None,
                'Original must be public and unscorable')
        bodies = {}
        for key in ('scoreFile', 'audioFile', 'performanceFile'):
            item = entry[key]
            body = safe_file(root, item['path']).read_bytes()
            require(sha(body) == item['sha256'] and len(body) == item['bytes'], 'Payload hash mismatch')
            bodies[key] = body
            uploads.append(dict(item, key=prefix + item['path']))
        target = json.loads(bodies['scoreFile'])
        perf = json.loads(bodies['performanceFile'])
        old_score = before['score']
        require(target['score'] == dict(old_score['score'], scoreVersion=2), 'Score identity changed')
        require(perf['scoreVersion'] == 2 and perf['sourceSha256'] == sha(bodies['scoreFile'])
                and perf['audioSha256'] == sha(bodies['audioFile']), 'Performance binding differs')
        require(target['music']['bpmUnit'] == old_score['music']['bpmUnit'], 'Beat unit changed')
        require(target['music']['ticksPerJeonggan'] * old_score['music']['ticksPerBeat'] ==
                old_score['music']['ticksPerJeonggan'] * target['music']['ticksPerBeat'], 'Jeonggan length changed')
        wanted_song = deepcopy(before)
        wanted_song.update(score=target, version=2, scorable=1, notes=sum(e['kind'] == 'NOTE' for e in target['events']))
        wanted_audio = dict(entry['audioMetadata'])
        require(set(wanted_audio) == set(AUDIO_FIELDS) - {'object_key', 'performance_object_key'}, 'Audio metadata fields differ')
        require(wanted_audio['bpm'] == before['bpm'] and wanted_audio['bpm_unit'] == target['music']['bpmUnit']
                and wanted_audio['source_common_sha256'] == sha(bodies['scoreFile'])
                and wanted_audio['sha256'] == sha(bodies['audioFile']) and wanted_audio['status'] == 'PUBLISHED'
                and wanted_audio['content_type'] == 'audio/mp4' and wanted_audio['duration_ms'] > 0,
                'Audio metadata differs')
        wanted_audio.update(object_key=prefix + entry['audioFile']['path'],
                            performance_object_key=prefix + entry['performanceFile']['path'])
        checked.append(dict(entry, targetSong=wanted_song, targetAudio=wanted_audio))
    uploads.append(dict(path='release.json', sha256=sha(raw), bytes=len(raw), key=prefix + 'release.json'))
    return checked, uploads


def check_state(entries, current):
    songs, audios, sessions = current
    require(set(songs) == IDS, 'Missing songs')
    pending = []
    for entry in entries:
        sid = entry['songId']
        require(audios.get((sid, 1)) == entry['beforeAudio'], f'Previous audio changed: {sid}')
        require(set(v for s, v in audios if s == sid) <= {1, 2}, f'Unexpected audio version: {sid}')
        v2 = audios.get((sid, 2))
        if songs[sid] == entry['beforeSong'] and v2 is None:
            pending.append(sid)
        else:
            require(songs[sid] == entry['targetSong'] and v2 is not None and
                    all(v2[k] == entry['targetAudio'][k] for k in AUDIO_FIELDS), f'Catalog drift: {sid}')
    require(not pending or sessions == 0, 'Existing sessions require separate migration review')
    require(not pending or set(pending) == IDS, 'Partially applied release requires separate review')
    return pending


def literal(value):
    if value is None:
        return 'NULL'
    return str(value) if type(value) is int else quote(json.dumps(value, ensure_ascii=False) if isinstance(value, dict) else value)


def build_sql(entries, pending):
    sql = ['CREATE TEMPORARY TABLE _repair699_assert (ok INT NOT NULL CHECK(ok=1));',
           'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;']
    for entry in entries:
        sid = entry['songId']
        if sid not in pending:
            continue
        before, after = entry['beforeSong'], entry['targetSong']
        sql.append(f'SELECT id FROM songs WHERE id={sid} FOR UPDATE;')
        sql.append(f'SELECT song_id FROM song_model_audios WHERE song_id={sid} FOR UPDATE;')
        sql.append(f'INSERT INTO _repair699_assert SELECT COUNT(*) FROM song_model_audios WHERE song_id={sid};')
        sql.append(f'INSERT INTO _repair699_assert SELECT IF(COUNT(*)=0,1,0) FROM practice_sessions WHERE song_id={sid};')
        guard = (f"id={sid} AND BINARY source=BINARY {quote(before['source'])} AND score_version=1 AND is_scorable=FALSE "
                 f"AND status='PUBLISHED' AND deleted_at IS NULL AND note_count={before['notes']} AND default_bpm={before['bpm']} "
                 f"AND BINARY title=BINARY {quote(before['title'])} AND difficulty={before['difficulty']} AND instrument_id={before['instrumentId']} "
                 f"AND umulsai_text <=> {'NULL' if before['umulsai'] is None else quote(before['umulsai'])} "
                 f"AND jangdan_id <=> {'NULL' if before['jangdan'] is None else before['jangdan']} "
                 f"AND score_json=CAST({quote(json.dumps(before['score'], ensure_ascii=False))} AS JSON)")
        sql.append(f'INSERT INTO _repair699_assert SELECT COUNT(*) FROM songs WHERE {guard};')
        old = entry['beforeAudio']
        checks = []
        for key in AUDIO_FIELDS:
            value = literal(old[key])
            checks.append(f'{key}<=>CAST({value} AS JSON)' if key == 'generation_metadata' else f'{key}<=>{value}')
        checks.extend(f'{key}={quote(old[key])}' for key in ('created_at', 'updated_at'))
        sql.append(f"INSERT INTO _repair699_assert SELECT COUNT(*) FROM song_model_audios WHERE song_id={sid} AND score_version=1 AND " + ' AND '.join(checks) + ';')
        audio = entry['targetAudio']
        sql.append(f"INSERT INTO song_model_audios(song_id,score_version,{','.join(AUDIO_FIELDS)}) VALUES({sid},2," + ','.join(literal(audio[k]) for k in AUDIO_FIELDS) + ');')
        sql.append('INSERT INTO _repair699_assert VALUES(ROW_COUNT());')
        sql.append(f"UPDATE songs SET score_json=CAST({quote(json.dumps(after['score'], ensure_ascii=False))} AS JSON),score_version=2,note_count={after['notes']},is_scorable=TRUE WHERE {guard};")
        sql.append('INSERT INTO _repair699_assert VALUES(ROW_COUNT());')
    sql.append('COMMIT;')
    return '\n'.join(sql)


def run(root, receipt, *, apply=False, runner=mysql):
    report = dict(task='S15P31E102-699', status='started')
    with receipt.open('x', encoding='utf-8') as stream:
        try:
            entries, objects = load_release(root)
            before = inventory(runner)
            pending = check_state(entries, before)
            report.update(status='checked', pending=pending)
            if not apply or not pending:
                return report
            client, bucket = create_client('/opt/eolssu/.env')
            report['objects'] = objects
            upload_objects(root, client, bucket, report, workers=2)
            for item in objects:
                metadata = client.head_object(Bucket=bucket, Key=item['key'])
                require('Expiration' not in metadata, 'Unexpected object expiry')
                expected_type = 'audio/mp4' if item['path'].endswith('.m4a') else 'application/json'
                require(metadata.get('ContentType') == expected_type, 'Remote content type differs')
            report['status'] = 'uploaded'
            backup = subprocess.run(['/opt/eolssu-backup/backup.py', 'send', 'prechange'],
                                    check=True, capture_output=True, timeout=600)
            receipt.with_suffix('.backup.log').write_bytes(backup.stdout)
            require(inventory(runner) == before, 'Catalog changed during backup')
            report['status'] = 'apply-outcome-unknown'
            runner(build_sql(entries, pending))
            report['status'] = 'committed-readback-pending'
            require(check_state(entries, inventory(runner)) == [], 'Incomplete readback')
            report.update(status='applied', updated=pending, pending=[])
            return report
        except BaseException as error:
            report['errorType'] = type(error).__name__
            raise
        finally:
            report['checkedAtUtc'] = datetime.now(timezone.utc).isoformat()
            json.dump(report, stream, ensure_ascii=False, indent=2)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    result = run(args.bundle, args.receipt, apply=args.apply)
    print(json.dumps({k: v for k, v in result.items() if k != 'objects'}, ensure_ascii=False))
