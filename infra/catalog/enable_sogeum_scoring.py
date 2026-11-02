#!/usr/bin/env python3
"""Enable scoring for the four reviewed sogeum songs loaded by S15P31E102-339. Default is read-only.

S15P31E102-387. Each song keeps its 339 score (same events and ticks as the reviewed answer); only the
reviewed breath marks and provenance.review change, and is_scorable becomes TRUE. songs.updated_at also
changes (ON UPDATE CURRENT_TIMESTAMP). score_version stays, because model audio is served only for the
current score_version and breath marks change no timing. If a run times out, the server may still commit:
rerun without --apply to read the actual state before anything else.
Publication (status) stays with infra/catalog/publish_model_audio.py; run this after it.
"""
import argparse
import copy
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

# songId/source/scoreVersion/currentSha256: 운영 DB 읽기(2026-11-02). breathEventIds: 저장소 검수 정답(answer)의 숨표.
PLAN = [
    {'songId': 97, 'source': 'mr203-fixed-sogeum-009-p1', 'scoreVersion': 1, 'answer': 'ngc-standard-2022-p194',
     'breathEventIds': ['c0-j2-r2-u0', 'c4-j2-r2-u0', 'c8-j2-r0-u0', 'c12-j2-r2-u0'],
     'currentSha256': 'e9f41653b96da1b97b8590ec06a2d5e28d65749cb0b2344cf8ed648a64617e60',
     'targetSha256': 'c0e7dc0f450a461ca55b7864cdd07ddc6f2c5ee07f4e32d09841978fadb44212'},
    {'songId': 92, 'source': 'mr203-fixed-sogeum-004-p1', 'scoreVersion': 1, 'answer': 'ngc-standard-2022-p192',
     'breathEventIds': ['c0-j2-r0-u0', 'c2-j2-r2-u0', 'c4-j1-r0-u0', 'c8-j1-r0-u0', 'c10-j2-r2-u0', 'c12-j2-r0-u0',
                        'c16-j2-r0-u0', 'c18-j2-r2-u0', 'c20-j2-r0-u0', 'c22-j2-r2-u0', 'c24-j2-r0-u0'],
     'currentSha256': 'fcca1214890cf15146e7a3cbf3fb3c1f6e567354d7eb5bda2104f9c113d5043b',
     'targetSha256': '9c79d1f014f99e59dd9f9e70809cd654b75381db3cb18f853ac973b2ac146355'},
    {'songId': 95, 'source': 'mr203-fixed-sogeum-007-p1', 'scoreVersion': 1, 'answer': 'ngc-standard-2022-p193',
     'breathEventIds': ['c0-j2-r0-u0', 'c2-j2-r2-u0', 'c4-j2-r1-u0', 'c6-j2-r2-u0', 'c8-j2-r0-u0', 'c10-j2-r2-u0',
                        'c12-j2-r1-u0'],
     'currentSha256': 'b034cd9ec11071fd45915a2c4cca4fc7017b2899e4417155bf46f8ffa26a4a45',
     'targetSha256': 'cad9a1de2c264257bd2ea620adbbd486837d32def35c49cd36593fe3d6b7ec91'},
    {'songId': 91, 'source': 'mr203-fixed-sogeum-003-p1', 'scoreVersion': 1, 'answer': 'ngc-standard-2022-p196',
     'breathEventIds': [],
     'currentSha256': '8466aba277f6662fd65278efaeb72d4c78c3d7e4d953167c487c911cc9332a3c',
     'targetSha256': '1062cfa2bceab4ef971e1033f0d5f7ff0a0e3759acb8742426c4dac5f5a57b4e'},
]


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'))


def canonical_sha(value):
    return hashlib.sha256(canonical(value).encode('utf-8')).hexdigest()


def quote(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"


def patch(score, breath_ids):
    """검수 숨표와 review만 바꾼 새 악보. 이미 숨표가 있거나 모르는 이벤트면 멈춘다."""
    ids = {e['id'] for e in score['events']}
    if not set(breath_ids) <= ids or any(e['breathAfter'] for e in score['events']):
        raise ValueError('score events differ from the reviewed plan')
    result = copy.deepcopy(score)
    for event in result['events']:
        event['breathAfter'] = event['id'] in breath_ids
    result['provenance']['review'] = 'reviewed'
    return result


def state(item, row):
    if (row['id'], row['source'], row['scoreVersion'], row['deleted']) != (item['songId'], item['source'], item['scoreVersion'], 0) \
            or row['score']['score']['sourceId'] != item['source']:
        raise ValueError(f"song {item['songId']} identity changed")
    digest = canonical_sha(row['score'])
    if digest == item['currentSha256'] and row['isScorable'] == 0:
        return 'pending'
    if digest == item['targetSha256'] and row['isScorable'] == 1:
        return 'done'
    raise ValueError(f"song {item['songId']} score or scoring state differs from the plan")


def inventory(plan, runner):
    ids = ','.join(str(item['songId']) for item in plan)
    sql = ("SELECT JSON_OBJECT('id',id,'source',source,'scoreVersion',score_version,'isScorable',IF(is_scorable,1,0),"
           "'status',status,'deleted',IF(deleted_at IS NULL,0,1),'score',score_json) FROM songs WHERE id IN (" + ids + ") ORDER BY id;")
    rows = {row['id']: row for row in map(json.loads, runner(sql).splitlines())}
    if set(rows) != {item['songId'] for item in plan}:
        raise ValueError('planned songs are missing')
    return rows


def build_sql(pending):
    """한 트랜잭션. 행마다 바뀐 수가 1이 아니면 CHECK 오류로 COMMIT 전에 멈춘다."""
    sql = ['CREATE TEMPORARY TABLE _sogeum_scoring_assert (ok INT NOT NULL CHECK (ok=1));',
           'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;']
    for item, current, target in pending:
        sql.append(f"UPDATE songs SET score_json=CAST({quote(canonical(target))} AS JSON), is_scorable=TRUE "
                   f"WHERE id={item['songId']} AND BINARY source=BINARY {quote(item['source'])} AND score_version={item['scoreVersion']} "
                   f"AND is_scorable=FALSE AND deleted_at IS NULL AND score_json=CAST({quote(canonical(current))} AS JSON);")
        sql.append('INSERT INTO _sogeum_scoring_assert VALUES (ROW_COUNT());')
    sql.append('COMMIT;')
    return '\n'.join(sql)


def mysql(sql):
    # infra/terms/publish.py와 같은 컨테이너 찾기. 비밀번호는 MySQL 컨테이너 환경에만 있다.
    inspected = subprocess.run(['docker', 'inspect', 'eolssu-runtime-backend-1'], capture_output=True, timeout=15, check=True)
    environment = dict(item.split('=', 1) for item in json.loads(inspected.stdout)[0]['Config']['Env'] if '=' in item)
    matched = re.fullmatch(r'jdbc:mysql://[^/]+/([A-Za-z0-9_]+)(?:\?.*)?', environment.get('DB_URL', ''))
    if not matched:
        raise ValueError('active backend database could not be resolved')
    command = ['docker', 'exec', '-i', 'eolssu-runtime-mysql-1', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --no-defaults --skip-reconnect --default-character-set=utf8mb4 --user=root --database='
               + matched.group(1) + ' --batch --raw --skip-column-names']
    result = subprocess.run(command, input=sql.encode('utf-8'), capture_output=True, timeout=60)
    if result.returncode:
        raise RuntimeError('database batch failed; read back before retrying')
    return result.stdout.decode('utf-8').strip()


def run(plan, receipt, *, apply=False, runner=mysql):
    report = {'status': 'inventory', 'pending': [], 'updated': [], 'songStatus': {}}
    fd = os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        try:
            rows = inventory(plan, runner)
            pending = []
            for item in plan:
                row = rows[item['songId']]
                report['songStatus'][item['songId']] = row['status']
                if state(item, row) == 'pending':
                    target = patch(row['score'], item['breathEventIds'])
                    if canonical_sha(target) != item['targetSha256']:
                        raise ValueError(f"song {item['songId']} patched score differs from the plan")
                    pending.append((item, row['score'], target))
            report.update(status='validated-dry-run', pending=[item['songId'] for item, _, _ in pending])
            if apply:
                if pending:
                    report['status'] = 'apply-outcome-unknown'
                    runner(build_sql(pending))
                report['status'] = 'committed-readback-pending'
                after = inventory(plan, runner)
                if any(state(item, after[item['songId']]) != 'done' for item in plan):
                    raise RuntimeError('read-back differs from the plan')
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
    parser.add_argument('--receipt', type=Path, required=True, help='new file for the result record')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        report = run(PLAN, args.receipt, apply=args.apply)
    except Exception as error:
        print('Sogeum scoring stopped (' + type(error).__name__ + '); inspect the receipt and current rows before retrying', file=sys.stderr)
        return 1
    print(json.dumps({key: report[key] for key in ('status', 'pending', 'updated', 'songStatus')}))
    return 0


if __name__ == '__main__':
    sys.exit(main())
