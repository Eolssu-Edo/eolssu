"""Publish audited tempo/name corrections with immutable next-version audio.

Dry-run is the default. Apply takes the shared deployment lock itself, verifies
an upload receipt and takes the existing prechange backup. Historical audio and
completed sessions are retained. Only RECORDING/PENDING sessions block a change.
Apply also requires session creation/retry traffic to be blocked and in-flight
requests drained by the operator; row locks cannot prevent stale pre-read inserts.
"""
import argparse
from contextlib import contextmanager
from copy import deepcopy
from datetime import datetime, timezone
from fractions import Fraction
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys

from enable_scoring import mysql, quote
from stage_model_audio import create_client, require, safe_file, sha, upload_objects

TASK = 'S15P31E102-728'
# 1019 reuses this release format to move two songs to per-jeonggan tempo units.
TASKS = {TASK, 'S15P31E102-1019'}
SCHEMA = 'eolssu-score-tempo-release/v1'
UPLOAD_SCHEMA = 'eolssu-score-tempo-upload/v1'
EXCLUDED_SONG_IDS = {56, 132, 133, 134}  # User-owned concurrent Airplane task.
AUDIO_FIELDS = ('object_key', 'performance_object_key', 'source_common_sha256', 'bpm', 'bpm_unit',
                'duration_ms', 'start_offset_ms', 'content_type', 'sha256', 'status', 'generation_metadata')
SONG_COLUMNS = dict(id='id', source='source', version='score_version', bpm='default_bpm',
                    scorable='is_scorable', notes='note_count', status='status', deleted='deleted_at',
                    jangdan='jangdan_id', score='score_json', title='title', difficulty='difficulty',
                    instrumentId='instrument_id', umulsai='umulsai_text', description='description')


def read_json(path):
    return json.loads(path.read_bytes())


def ids_sql(ids):
    require(ids and all(type(sid) is int and sid > 0 for sid in ids) and len(ids) == len(set(ids)), 'Invalid song IDs')
    return ','.join(map(str, sorted(ids)))


def inventory(ids, runner=mysql):
    selected = ids_sql(ids)
    fields = ','.join(f"'{key}',{column}" if key != 'scorable' else "'scorable',IF(is_scorable,1,0)"
                      for key, column in SONG_COLUMNS.items())
    songs = {r['id']: r for r in map(json.loads, runner(f'SELECT JSON_OBJECT({fields}) FROM songs WHERE id IN ({selected}) ORDER BY id;').splitlines())}
    pairs = ','.join(f"'{field}',{field}" for field in AUDIO_FIELDS)
    audios = {(r['songId'], r['version']): r for r in map(json.loads, runner(
        f"SELECT JSON_OBJECT('songId',song_id,'version',score_version,{pairs},'created_at',created_at,'updated_at',updated_at) "
        f'FROM song_model_audios WHERE song_id IN ({selected}) ORDER BY song_id,score_version;').splitlines())}
    return songs, audios


def active_sessions(ids, runner=mysql):
    return int(runner(f"SELECT COUNT(*) FROM practice_sessions WHERE song_id IN ({ids_sql(ids)}) AND status IN ('RECORDING','PENDING');"))


def validate_score_change(entry, target):
    before = entry['beforeSong']
    original = before['score']
    expected = deepcopy(original)
    expected['score']['scoreVersion'] = before['version'] + 1
    require(isinstance(target['music']['jangdan'], str) and len(target['music']['jangdan']) <= 30, 'Invalid jangdan name')
    expected['music']['jangdan'] = target['music']['jangdan']
    correction = entry.get('beatNotationCorrection')
    if correction is not None:
        require(isinstance(correction, dict) and set(correction) == {'basis'} and
                isinstance(correction['basis'], str) and correction['basis'].strip(), 'Notation correction needs independent evidence')
        tpb = target['music']['ticksPerBeat']
        require(type(tpb) is int and tpb > 0 and tpb != original['music']['ticksPerBeat'], 'No beat notation correction')
        require(entry['targetBpm'] * tpb == before['bpm'] * original['music']['ticksPerBeat'], 'Notation correction changes actual speed')
        expected['music']['ticksPerBeat'] = tpb
        # A per-jeonggan unit is only a label when one beat is exactly one jeonggan (S15P31E102-1019).
        unit = target['music'].get('bpmUnit')
        if unit != original['music']['bpmUnit']:
            require(unit == 'JEONGGAN' and tpb == original['music']['ticksPerJeonggan'], 'Unit change must make one beat one jeonggan')
            expected['music']['bpmUnit'] = unit
    require(target == expected, 'Unapproved score content or time-grid change')


def finite_number(value):
    return type(value) in (int,float) and math.isfinite(value)


def written_slots(source):
    """Enumerate the existing v1 grid using rational row/unit subdivisions."""
    width=source['music']['ticksPerJeonggan']
    require(type(width) is int and width>0,'Invalid source grid width')
    offset,slots=0,[]
    for ci,column in enumerate(source['columns']):
        require(column['index']==ci and column['cells'],'Invalid source column')
        for ji,cell in enumerate(column['cells']):
            require(cell['id']==f'c{ci}-j{ji}' and cell['index']==ji and cell['units'],'Invalid source cell')
            rows={}
            for unit in cell['units']:
                require(type(unit['row']) is int and type(unit['col']) is int,'Invalid source slot indices')
                rows.setdefault(unit['row'],[]).append(unit)
            require(list(rows)==list(range(len(rows))),'Noncontiguous source rows')
            for row,units in rows.items():
                require([u['col'] for u in units]==list(range(len(units))),'Noncontiguous source units')
                duration=Fraction(width,len(rows)*len(units))
                for unit in units:
                    start=offset+Fraction(width*row,len(rows))+duration*unit['col']
                    slots.append(dict(eventId=unit['eventId'],cellId=cell['id'],row=row,col=unit['col'],startTick=start,endTick=start+duration))
            offset+=width
    require(slots,'Missing source slots')
    events,cursor={},0
    for event in source['events']:
        require(isinstance(event['id'],str) and event['id'] not in events and
                type(event['startTick']) is int and type(event['durationTick']) is int and
                event['startTick']==cursor and event['durationTick']>0,'Invalid source event coverage')
        events[event['id']]=event
        cursor+=event['durationTick']
    require(cursor==offset,'Source grid and event extent differ')
    cursor=Fraction(0)
    covered=set()
    for slot in slots:
        event=events.get(slot['eventId'])
        require(event is not None and slot['startTick']==cursor and
                event['startTick']<=slot['startTick']<slot['endTick']<=event['startTick']+event['durationTick'],
                'Source slot and event coverage differ')
        covered.add(slot['eventId']); cursor=slot['endTick']
    require(covered==set(events),'Source events missing from grid')
    return slots


def validate_performance_timing(source,perf,bpm):
    """This correction release is one written pass at a constant target BPM."""
    slots=written_slots(source)
    segments=perf['segments']
    require(isinstance(segments,list) and len(segments)==len(slots),'Performance must cover every written slot once')
    tpb=source['music']['ticksPerBeat']
    require(type(tpb) is int and tpb>0,'Invalid source beat resolution')
    ms_per_tick=Fraction(60000,bpm*tpb)
    fields={'eventId','cellId','row','col','startTick','endTick','occurrenceId','startMs','endMs','sigimsae'}
    seen,end=set(),0
    annotations={event['id']:[] for event in source['events']}
    for slot,segment in zip(slots,segments):
        require(isinstance(segment,dict) and set(segment)==fields,'Performance segment fields differ')
        occurrence=segment['occurrenceId']
        require(isinstance(occurrence,str) and occurrence.strip() and occurrence not in seen,'Duplicate or empty occurrenceId')
        seen.add(occurrence)
        for key in ('eventId','cellId','row','col'):
            require(segment[key]==slot[key] and (key not in ('row','col') or type(segment[key]) is int),'Performance written position differs')
        for key in ('startTick','endTick'):
            require(finite_number(segment[key]) and abs(segment[key]-float(slot[key]))<=1e-6,'Performance tick position differs')
        start,finish=segment['startMs'],segment['endMs']
        require(finite_number(start) and finite_number(finish) and start>=0 and finish>start and abs(start-end)<=1e-6,
                'Invalid performance timeline')
        # The MIDI tempo uses integer microseconds; tolerate <= 1 ms accumulated
        # rounding, never an arbitrary scale or expressive tempo/length change.
        require(abs(start-float(slot['startTick']*ms_per_tick))<=1 and
                abs(finish-float(slot['endTick']*ms_per_tick))<=1,'Performance time differs from score ticks and target BPM')
        marks=segment['sigimsae']
        require(isinstance(marks,list) and all(isinstance(m,str) and m for m in marks),'Invalid performance annotations')
        annotations[slot['eventId']].extend(marks)
        end=finish
    require(finite_number(perf['durationMs']) and abs(end-perf['durationMs'])<=1e-6,'Performance duration differs')
    for event in source['events']:
        require(annotations[event['id']]==event['sigimsae'],'Performance source annotations differ')
    return end


def load_release(root):
    raw = safe_file(root, 'release.json').read_bytes()
    release = json.loads(raw)
    require(set(release) == {'task','schema','entries'} and release['task'] in TASKS and release['schema'] == SCHEMA, 'Wrong release')
    entries = release['entries']
    require(isinstance(entries, list), 'Entries must be a list')
    ids_sql([e['songId'] for e in entries])
    require(not EXCLUDED_SONG_IDS.intersection(e['songId'] for e in entries), 'Airplane songs are excluded from this task')
    prefix = 'model-audio/drafts/' + sha(raw) + '/'
    checked, uploads, paths = [], [], set()
    for entry in entries:
        require(set(entry) in ({'songId','beforeSong','beforeAudio','targetBpm','scoreFile','audioFile','performanceFile','audioMetadata'},
                              {'songId','beforeSong','beforeAudio','targetBpm','scoreFile','audioFile','performanceFile','audioMetadata','beatNotationCorrection'}), 'Unknown entry fields')
        before, old, sid = entry['beforeSong'], entry['beforeAudio'], entry['songId']
        require(set(before) == set(SONG_COLUMNS), 'Original song snapshot fields differ')
        require(set(old) == set(AUDIO_FIELDS) | {'songId','version','created_at','updated_at'}, 'Original audio snapshot fields differ')
        require(before['id'] == old['songId'] == sid and type(before['version']) is int and before['version'] > 0 and
                before['version'] == old['version'] == before['score']['score']['scoreVersion'], 'Original identity differs')
        require(before['source'] == before['score']['score']['sourceId'] and before['bpm'] == old['bpm'] and
                old['bpm_unit'] == before['score']['music']['bpmUnit'], 'Original score/audio binding differs')
        require((before['status'], old['status']) in (('PUBLISHED','PUBLISHED'),('HIDDEN','DRAFT')) and
                before['deleted'] is None and before['scorable'] in (0,1), 'Original publication state differs')
        require(type(entry['targetBpm']) is int and 30 <= entry['targetBpm'] <= 400, 'Invalid target BPM')
        # Match the deployed V15 CHECK before uploading any immutable object.
        # Preserve the existing source case, not the numeric catalog song ID.
        instrument=before['score']['score']['instrument'].lower().replace('_','-')
        case=before['source'].removeprefix('mr203-fixed-')
        require(before['source']=='mr203-fixed-'+case and
                re.fullmatch(re.escape(instrument)+r'-[0-9]{3}-p[0-9]+',case) is not None and
                instrument in ('danso','sogeum','jeongak-daegeum','sanjo-daegeum'), 'Unsupported source catalog path identity')
        directory='cases/'+case+'/'
        require(entry['audioFile']['path']==directory+instrument+'.m4a' and
                entry['performanceFile']['path']==directory+'performance.common.json', 'Payload catalog path differs from source/instrument or deployed constraints')
        bodies = {}
        for key in ('scoreFile','audioFile','performanceFile'):
            item = entry[key]
            require(set(item) == {'path','sha256','bytes'} and item['path'] not in paths and item['path'] != 'release.json', 'Duplicate payload path')
            require(item['path'].endswith('.m4a' if key == 'audioFile' else '.json'), 'Payload extension differs')
            paths.add(item['path'])
            body = safe_file(root, item['path']).read_bytes()
            require(type(item['bytes']) is int and len(body) == item['bytes'] and sha(body) == item['sha256'], 'Payload bytes differ')
            bodies[key] = body
            uploads.append(dict(item, key=prefix + item['path']))
        target, perf = json.loads(bodies['scoreFile']), json.loads(bodies['performanceFile'])
        validate_score_change(entry, target)
        version = before['version'] + 1
        require(entry['targetBpm'] != before['bpm'] or target['music'] != before['score']['music'], 'Empty correction')
        require(perf['schema'] == 'common-score-performance/v1' and perf['policy'] == 'educational-preview/v1' and
                perf['scoreVersion'] == version and perf['sourceSha256'] == sha(bodies['scoreFile']) and
                perf['audioSha256'] == sha(bodies['audioFile']) and perf['sourceId'] == target['score']['sourceId'] and
                perf['instrument'] == target['score']['instrument'] and perf['bpm'] == entry['targetBpm'] and
                perf['bpmUnit'] == target['music']['bpmUnit'], 'Performance identity differs')
        end = validate_performance_timing(target,perf,entry['targetBpm'])
        wanted = deepcopy(before)
        wanted.update(score=target, version=version, bpm=entry['targetBpm'])
        audio = dict(entry['audioMetadata'])
        require(set(audio) == set(AUDIO_FIELDS)-{'object_key','performance_object_key'}, 'Audio metadata fields differ')
        require(audio['bpm'] == entry['targetBpm'] and audio['bpm_unit'] == target['music']['bpmUnit'] and
                audio['source_common_sha256'] == sha(bodies['scoreFile']) and audio['sha256'] == sha(bodies['audioFile']) and
                audio['status'] == old['status'] and audio['content_type'] == 'audio/mp4' and
                type(audio['duration_ms']) is int and audio['duration_ms'] >= end and
                type(audio['start_offset_ms']) is int and audio['start_offset_ms'] == 0 and
                isinstance(audio['generation_metadata'], dict), 'Audio metadata differs')
        audio.update(object_key=prefix+entry['audioFile']['path'], performance_object_key=prefix+entry['performanceFile']['path'])
        checked.append(dict(entry,targetSong=wanted,targetAudio=audio))
    uploads.append(dict(path='release.json',sha256=sha(raw),bytes=len(raw),key=prefix+'release.json'))
    return checked, uploads, sha(raw)


def check_state(entries, current):
    songs, audios = current
    ids = {e['songId'] for e in entries}
    require(set(songs) == ids, 'Missing songs')
    pending = []
    for entry in entries:
        sid, old_v = entry['songId'], entry['beforeSong']['version']
        require(audios.get((sid,old_v)) == entry['beforeAudio'], f'Previous audio changed: {sid}')
        require(all(version <= old_v + 1 for song,version in audios if song == sid), f'Unexpected future audio: {sid}')
        new_audio = audios.get((sid,old_v+1))
        if songs[sid] == entry['beforeSong'] and new_audio is None:
            pending.append(sid)
        else:
            require(songs[sid] == entry['targetSong'] and new_audio is not None and
                    all(new_audio[k] == entry['targetAudio'][k] for k in AUDIO_FIELDS), f'Catalog drift: {sid}')
    require(not pending or set(pending) == ids, 'Partial application requires review')
    return pending


def literal(value):
    if value is None: return 'NULL'
    if type(value) is int: return str(value)
    if isinstance(value, dict): return 'CAST('+quote(json.dumps(value,ensure_ascii=False))+' AS JSON)'
    require(isinstance(value,str), 'Unexpected SQL value')
    return quote(value)


def row_guard(fields, values):
    return ' AND '.join((f'BINARY {column} <=> BINARY ' if isinstance(values[key],str) and column not in ('created_at','updated_at','deleted_at') else f'{column} <=> ')+literal(values[key])
                        for key,column in fields.items())


def build_sql(entries, pending):
    sql = ['CREATE TEMPORARY TABLE _tempo728_assert (ok INT NOT NULL CHECK(ok=1));',
           'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;']
    sql.append("INSERT INTO _tempo728_assert SELECT IF(COUNT(*)=2,1,0) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('songs','song_model_audios') AND engine='InnoDB';")
    for e in entries:
        sid=e['songId']
        if sid not in pending: continue
        before,after=e['beforeSong'],e['targetSong']
        old_v,new_v=before['version'],after['version']
        sql += [f'SELECT id FROM songs WHERE id={sid} FOR UPDATE;',
                f'SELECT song_id FROM song_model_audios WHERE song_id={sid} FOR UPDATE;',
                f"INSERT INTO _tempo728_assert SELECT IF(COUNT(*)=0,1,0) FROM practice_sessions WHERE song_id={sid} AND status IN ('RECORDING','PENDING');",
                f'INSERT INTO _tempo728_assert SELECT IF(COUNT(*)=0,1,0) FROM song_model_audios WHERE song_id={sid} AND score_version={new_v};']
        guard=row_guard(SONG_COLUMNS,before)
        sql.append(f'INSERT INTO _tempo728_assert SELECT COUNT(*) FROM songs WHERE {guard};')
        audio_guard=row_guard(dict(songId='song_id',version='score_version',**{k:k for k in (*AUDIO_FIELDS,'created_at','updated_at')}),e['beforeAudio'])
        sql.append(f'INSERT INTO _tempo728_assert SELECT COUNT(*) FROM song_model_audios WHERE {audio_guard};')
        audio=e['targetAudio']
        sql += [f"INSERT INTO song_model_audios(song_id,score_version,{','.join(AUDIO_FIELDS)}) VALUES({sid},{new_v},"+', '.join(literal(audio[k]) for k in AUDIO_FIELDS)+');',
                'INSERT INTO _tempo728_assert VALUES(ROW_COUNT());',
                f"UPDATE songs SET score_json={literal(after['score'])},score_version={new_v},default_bpm={after['bpm']} WHERE {guard};",
                'INSERT INTO _tempo728_assert VALUES(ROW_COUNT());']
    return '\n'.join(sql+['COMMIT;'])


def validate_upload_receipt(report, objects, digest):
    require(report.get('schema')==UPLOAD_SCHEMA and report.get('releaseSha256')==digest and report.get('status')=='uploaded', 'Wrong upload receipt')
    actual=report.get('objects',[])
    require(len(actual)==len(objects), 'Missing uploaded objects')
    for expected,found in zip(objects,actual):
        require(all(found.get(k)==v for k,v in expected.items()) and found.get('status') in ('new','reused','reused-race'), 'Unverified uploaded object')


@contextmanager
def deployment_lock():
    import fcntl
    with open('/run/lock/eolssu-backend-deploy.lock','a') as handle:
        fcntl.flock(handle.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        try: yield
        finally: fcntl.flock(handle.fileno(),fcntl.LOCK_UN)


def backup(receipt):
    result=subprocess.run(['/opt/eolssu-backup/backup.py','send','prechange'],check=True,capture_output=True,timeout=600)
    fd=os.open(receipt.with_suffix('.backup.log'),os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'wb') as out: out.write(result.stdout)


def _apply(entries, objects, digest, upload_receipt, report, receipt, runner, checkpoint):
    validate_upload_receipt(read_json(upload_receipt),objects,digest)
    ids=[e['songId'] for e in entries]
    with deployment_lock():
        before=inventory(ids,runner)
        pending=check_state(entries,before)
        if not pending:
            report.update(status='already-applied',pending=[])
            return
        require(active_sessions(ids,runner)==0,'Active RECORDING/PENDING sessions block correction')
        backup(receipt)
        require(inventory(ids,runner)==before,'Catalog changed during backup')
        report['status']='apply-outcome-unknown'
        checkpoint()
        runner(build_sql(entries,pending))
        report['status']='committed-readback-pending'
        checkpoint()
        require(check_state(entries,inventory(ids,runner))==[],'Incomplete readback')
        report.update(status='applied',updated=pending,pending=[])


def run(root,receipt,*,apply=False,upload_only=False,upload_receipt=None,traffic_quiesced=False,env_file='/opt/eolssu/.env',runner=mysql):
    require(not(apply and upload_only),'Choose upload or apply')
    require(not apply or traffic_quiesced is True,'Apply requires quiesced session creation/retry traffic and drained in-flight requests')
    root,receipt=Path(root),Path(receipt)
    require(not receipt.resolve().is_relative_to(root.resolve()),'Receipt must stay outside bundle')
    report=dict(task=TASK,status='started',trafficQuiesced=traffic_quiesced)
    fd=os.open(receipt,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w',encoding='utf-8') as stream:
        def checkpoint():
            report['checkedAtUtc']=datetime.now(timezone.utc).isoformat()
            stream.seek(0)
            json.dump(report,stream,ensure_ascii=False,indent=2)
            stream.write('\n'); stream.truncate(); stream.flush(); os.fsync(stream.fileno())
        try:
            entries,objects,digest=load_release(root)
            report.update(schema=UPLOAD_SCHEMA if upload_only else SCHEMA,releaseSha256=digest)
            if upload_only:
                client,bucket=create_client(env_file)
                report['objects']=[dict(o,status='validated-local') for o in objects]
                upload_objects(root,client,bucket,report,workers=2)
                for item in objects:
                    meta=client.head_object(Bucket=bucket,Key=item['key'])
                    require('Expiration' not in meta and meta.get('ContentType')==('audio/mp4' if item['path'].endswith('.m4a') else 'application/json'),'Remote MIME/expiry differs')
                report['status']='uploaded'
            else:
                ids=[e['songId'] for e in entries]
                pending=check_state(entries,inventory(ids,runner))
                count=active_sessions(ids,runner)
                report.update(status='validated-dry-run',pending=pending,activeSessions=count)
                require(not pending or count==0,'Active RECORDING/PENDING sessions block correction')
                if apply:
                    require(upload_receipt is not None,'Apply needs verified upload receipt')
                    _apply(entries,objects,digest,Path(upload_receipt),report,receipt,runner,checkpoint)
            return report
        except BaseException as error:
            report['errorType']=type(error).__name__
            raise
        finally:
            checkpoint()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle',type=Path)
    parser.add_argument('--receipt',type=Path)
    parser.add_argument('--song-id',type=int,action='append',help='Read-only snapshots (instead of --bundle)')
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--upload-only',action='store_true')
    parser.add_argument('--upload-receipt',type=Path)
    parser.add_argument('--traffic-quiesced',action='store_true',help='Operator confirms session creation/retry blocked and in-flight requests drained until apply/readback completes')
    parser.add_argument('--env-file',default='/opt/eolssu/.env')
    args=parser.parse_args()
    try:
        if args.song_id:
            require(not(args.bundle or args.apply or args.upload_only),'Snapshot mode is read-only')
            songs,audios=inventory(args.song_id)
            print(json.dumps(dict(songs=list(songs.values()),audios=list(audios.values()),activeSessions=active_sessions(args.song_id)),ensure_ascii=False))
        else:
            require(args.bundle is not None and args.receipt is not None,'Bundle and receipt required')
            result=run(args.bundle,args.receipt,apply=args.apply,upload_only=args.upload_only,upload_receipt=args.upload_receipt,traffic_quiesced=args.traffic_quiesced,env_file=args.env_file)
            print(json.dumps({k:v for k,v in result.items() if k!='objects'},ensure_ascii=False))
    except Exception as error:
        print('Tempo publication stopped ('+type(error).__name__+'); inspect receipt and current rows before retrying',file=sys.stderr)
        return 1
    return 0


if __name__=='__main__': sys.exit(main())
