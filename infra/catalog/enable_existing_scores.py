#!/usr/bin/env python3
"""S15P31E102-704: enable 36 existing scores without editing score or review data.

Default is read-only. The operator must hold the shared backend deployment lock
and take a backup before --apply. After a timeout, inspect a fresh dry run first.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys

from enable_scoring import canonical_sha, mysql, quote

EXPECTED_IDS = frozenset([36] + [i for i in range(51, 89) if i not in (62, 63, 65)])
SONG_HASH = "SHA2(CAST(JSON_ARRAY(id,instrument_id,jangdan_id,title,description,difficulty,default_bpm,score_json,score_version,umulsai_text,note_count,source,status,created_at,deleted_at) AS CHAR),256)"
AUDIO_HASH = "SHA2(CAST(JSON_ARRAY(song_id,score_version,audio_url,object_key,performance_object_key,source_common_sha256,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status,generation_metadata,created_at,updated_at) AS CHAR),256)"


def load_plan(path):
    plan = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(plan, list) or len(plan) != len(EXPECTED_IDS):
        raise ValueError('plan must contain exactly the 36 songs assigned to 704')
    ids = []
    for item in plan:
        if not isinstance(item, dict):
            raise ValueError('invalid plan item')
        for key in ('songId', 'scoreVersion', 'noteCount', 'defaultBpm', 'instrumentId'):
            if type(item.get(key)) is not int or item[key] <= 0:
                raise ValueError('invalid ' + key)
        if not isinstance(item.get('source'), str) or not item['source']:
            raise ValueError('missing source')
        if not isinstance(item.get('scoreSha256'), str) or not re.fullmatch('[0-9a-f]{64}', item['scoreSha256']):
            raise ValueError('invalid score hash')
        ids.append(item['songId'])
    if set(ids) != EXPECTED_IDS:
        raise ValueError('plan contains missing, duplicate or unapproved song IDs')
    return sorted(plan, key=lambda item: item['songId'])


def inventory(plan, runner):
    ids = ','.join(str(item['songId']) for item in plan)
    result = runner("SELECT JSON_OBJECT('kind','song','id',id,'source',source,'scoreVersion',score_version,"
                    "'noteCount',note_count,'defaultBpm',default_bpm,'instrumentId',instrument_id,"
                    "'isScorable',IF(is_scorable,1,0),'status',status,'deleted',IF(deleted_at IS NULL,0,1),"
                    f"'stableHash',{SONG_HASH},'updatedAt',updated_at,'score',score_json) "
                    f"FROM songs WHERE id IN ({ids}) ORDER BY id;\n"
                    "SELECT JSON_OBJECT('kind','audio','id',song_id,'version',score_version,'status',status,"
                    f"'bpm',bpm,'bpmUnit',bpm_unit,'rowHash',{AUDIO_HASH}) "
                    f"FROM song_model_audios WHERE song_id IN ({ids}) ORDER BY song_id,score_version;")
    rows, audios = {}, {}
    for row in map(json.loads, result.splitlines()):
        if row.pop('kind') == 'song':
            if row['id'] in rows:
                raise ValueError('duplicate inventory row')
            rows[row['id']] = row
        else:
            audios.setdefault(row['id'], []).append(row)
    if set(rows) != {item['songId'] for item in plan}:
        raise ValueError('planned songs are missing')
    for song_id, row in rows.items():
        row['audios'] = audios.get(song_id, [])
    return rows


def state(item, row):
    for key, actual in (('songId', row['id']), ('source', row['source']),
                        ('scoreVersion', row['scoreVersion']), ('noteCount', row['noteCount']),
                        ('defaultBpm', row['defaultBpm']), ('instrumentId', row['instrumentId'])):
        if item[key] != actual:
            raise ValueError(f"song {item['songId']} identity changed: {key}")
    if row['deleted'] or row['status'] != 'PUBLISHED' or row['isScorable'] not in (0, 1):
        raise ValueError(f"song {item['songId']} is not a published live song")
    score = row['score']
    if canonical_sha(score) != item['scoreSha256'] or score['score']['sourceId'] != item['source'] \
            or score['score']['scoreVersion'] != item['scoreVersion']:
        raise ValueError(f"song {item['songId']} score differs from the plan")
    if not any(a['version'] == row['scoreVersion'] and a['status'] == 'PUBLISHED'
               and a['bpm'] == row['defaultBpm'] and a['bpmUnit'] == score['music']['bpmUnit']
               for a in row['audios']):
        raise ValueError(f"song {item['songId']} has no matching published model audio")
    return 'done' if row['isScorable'] else 'pending'


def build_sql(rows):
    if set(rows) != EXPECTED_IDS:
        raise ValueError('transaction must include the exact approved 36 songs')
    ids = ','.join(str(song_id) for song_id in sorted(rows))
    sql = ['CREATE TEMPORARY TABLE _existing_score_assert (ok INT NOT NULL CHECK (ok=1));',
           'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;',
           f'SELECT id FROM songs WHERE id IN ({ids}) ORDER BY id FOR UPDATE;',
           f'SELECT song_id FROM song_model_audios WHERE song_id IN ({ids}) ORDER BY song_id,score_version FOR UPDATE;']
    for song_id, row in sorted(rows.items()):
        # Hash covers every song column except the permitted flag/timestamp. Audio
        # count plus every version's full-row hash also detects insertions/deletions.
        sql.append(f"INSERT INTO _existing_score_assert SELECT IF(COUNT(*)=1,1,0) FROM songs WHERE id={song_id} "
                   f"AND {SONG_HASH}={quote(row['stableHash'])} AND is_scorable={row['isScorable']} "
                   f"AND updated_at={quote(row['updatedAt'])};")
        sql.append(f"INSERT INTO _existing_score_assert SELECT IF(COUNT(*)={len(row['audios'])},1,0) "
                   f"FROM song_model_audios WHERE song_id={song_id};")
        for audio in row['audios']:
            sql.append(f"INSERT INTO _existing_score_assert SELECT IF(COUNT(*)=1,1,0) FROM song_model_audios "
                       f"WHERE song_id={song_id} AND score_version={audio['version']} AND {AUDIO_HASH}={quote(audio['rowHash'])};")
        if not row['isScorable']:
            sql.append(f'UPDATE songs SET is_scorable=TRUE WHERE id={song_id} AND is_scorable=FALSE;')
            sql.append('INSERT INTO _existing_score_assert VALUES (ROW_COUNT());')
    sql.append('COMMIT;')
    return '\n'.join(sql)


def run(plan, receipt, *, apply=False, runner=mysql):
    report = {'task': 'S15P31E102-704', 'status': 'inventory', 'pending': [], 'updated': []}
    fd = os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        try:
            before = inventory(plan, runner)
            report['pending'] = [item['songId'] for item in plan if state(item, before[item['songId']]) == 'pending']
            report['status'] = 'validated-dry-run'
            report['scoreSha256'] = {item['songId']: item['scoreSha256'] for item in plan}
            if apply:
                if report['pending']:
                    report['status'] = 'apply-outcome-unknown'
                    runner(build_sql(before))
                report['status'] = 'committed-readback-pending'
                after = inventory(plan, runner)
                for item in plan:
                    song_id = item['songId']
                    if state(item, after[song_id]) != 'done':
                        raise RuntimeError('readback scoring flag differs')
                    for field in ('stableHash', 'score', 'audios'):
                        if before[song_id][field] != after[song_id][field]:
                            raise RuntimeError('readback detected an unrelated change')
                    if before[song_id]['isScorable'] and before[song_id]['updatedAt'] != after[song_id]['updatedAt']:
                        raise RuntimeError('already enabled song timestamp changed')
                report.update(status='applied', updated=report['pending'], pending=[])
        except BaseException as error:
            report['errorType'] = type(error).__name__
            raise
        finally:
            report['checkedAtUtc'] = datetime.now(timezone.utc).isoformat()
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write('\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True, help='new result file, never overwritten')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        report = run(load_plan(args.plan), args.receipt, apply=args.apply)
    except Exception as error:
        print(f'Stopped ({type(error).__name__}); inspect receipt and a new dry run before retrying', file=sys.stderr)
        return 1
    print(json.dumps({key: report[key] for key in ('status', 'pending', 'updated')}))
    return 0


if __name__ == '__main__':
    sys.exit(main())
