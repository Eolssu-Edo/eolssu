#!/usr/bin/env python3
"""Enable scoring for reviewed songs from a plan. Default is read-only.

S15P31E102-387. Each song keeps its 339 score (same events and ticks as the reviewed answer); only the
reviewed breath marks and provenance.review change, and is_scorable becomes TRUE. songs.updated_at also
changes (ON UPDATE CURRENT_TIMESTAMP). score_version stays, because model audio is served only for the
current score_version and breath marks change no timing. A plan item with targetScore (S15P31E102-451) instead
replaces score_json with that reviewed file, sets score_version to targetScoreVersion (+1) and note_count, because its
notes change; the song then has no model audio until one is linked to the new version.
If a run times out, the server may still commit:
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


def load_plan(path):
    plan = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(plan, list) or not plan:
        raise ValueError('plan must contain songs')
    ids = set()
    for item in plan:
        if not isinstance(item, dict):
            raise ValueError('invalid song plan')
        song_id, version = item.get('songId'), item.get('scoreVersion')
        if type(song_id) is not int or song_id <= 0 or song_id in ids or type(version) is not int or version <= 0:
            raise ValueError('invalid or duplicate song id or score version')
        ids.add(song_id)
        if any(not isinstance(item.get(key), str) or not item[key] for key in ('source', 'answer')):
            raise ValueError('missing source or answer')
        if any(not isinstance(item.get(key), str) or not re.fullmatch(r'[0-9a-f]{64}', item[key])
               for key in ('currentSha256', 'targetSha256')):
            raise ValueError('invalid score hash')
        breaths = item.get('breathEventIds')
        if not isinstance(breaths, list) or any(not isinstance(value, str) or not value for value in breaths):
            raise ValueError('invalid breath event ids')
        if 'targetScore' in item:
            item['_target'] = load_target(path.parent, item)
    return plan


def load_target(folder, item):
    """악보를 통째로 바꾸는 항목의 목표 악보(계획 옆 폴더). 계획의 해시·다음 버전·숨표와 같아야 한다."""
    name, version = item['targetScore'], item.get('targetScoreVersion')
    if not isinstance(name, str) or not re.fullmatch(r'[A-Za-z0-9_-]+/[0-9]+\.json', name):
        raise ValueError('invalid target score path')
    if type(version) is not int or version != item['scoreVersion'] + 1:
        raise ValueError('target score version must be the next version')
    target = json.loads((folder / name).read_text(encoding='utf-8'))
    if canonical_sha(target) != item['targetSha256'] or target.get('score', {}).get('scoreVersion') != version \
            or [e['id'] for e in target['events'] if e['breathAfter']] != item['breathEventIds']:
        raise ValueError('target score file differs from the plan')
    return target


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'))


def canonical_sha(value):
    return hashlib.sha256(canonical(value).encode('utf-8')).hexdigest()


def quote(value):
    return "'" + value.replace('\\', '\\\\').replace("'", "''") + "'"


def patch(score, breath_ids):
    """검수 숨표와 review만 바꾼 새 악보. 모르는 이벤트면 멈춘다."""
    ids = {e['id'] for e in score['events']}
    if not set(breath_ids) <= ids:
        raise ValueError('score events differ from the reviewed plan')
    result = copy.deepcopy(score)
    for event in result['events']:
        event['breathAfter'] = event['id'] in breath_ids
    result['provenance']['review'] = 'reviewed'
    return result


def target_score(production, answer):
    """운영 악보의 곡 정보는 두고 음표·정간·틱만 검수한 정답으로 바꾼 새 악보(S15P31E102-451).
    박 단위나 정간당 박이 다르면 박의 뜻이 달라지므로 멈춘다. 틱 수는 정답 것을 쓴다(박은 틱 ÷ ticksPerBeat)."""
    old, new = production['music'], answer['music']
    if old['bpmUnit'] != new['bpmUnit'] or \
            old['ticksPerJeonggan'] * new['ticksPerBeat'] != new['ticksPerJeonggan'] * old['ticksPerBeat']:
        raise ValueError('answer beats differ from the service score')
    result = copy.deepcopy(production)
    result['events'], result['columns'] = copy.deepcopy(answer['events']), copy.deepcopy(answer['columns'])
    result['music'].update(ticksPerBeat=new['ticksPerBeat'], ticksPerJeonggan=new['ticksPerJeonggan'])
    result['score']['scoreVersion'] = production['score']['scoreVersion'] + 1
    result['provenance'].update(review='reviewed', converter=answer['provenance']['converter'],
                                jgbSha256=answer['provenance']['jgbSha256'])
    return result


def state(item, row):
    done_version = item.get('targetScoreVersion', item['scoreVersion'])
    if (row['id'], row['source'], row['deleted']) != (item['songId'], item['source'], 0) \
            or row['scoreVersion'] not in (item['scoreVersion'], done_version) \
            or row['score']['score']['sourceId'] != item['source']:
        raise ValueError(f"song {item['songId']} identity changed")
    digest = canonical_sha(row['score'])
    if digest == item['currentSha256'] and row['isScorable'] == 0 and row['scoreVersion'] == item['scoreVersion']:
        return 'pending'
    if digest == item['targetSha256'] and row['isScorable'] == 1 and row['scoreVersion'] == done_version:
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
        replaced = ''
        if '_target' in item:
            notes = sum(e['kind'] == 'NOTE' for e in target['events'])
            replaced = f"score_version={item['targetScoreVersion']}, note_count={notes}, "
        sql.append(f"UPDATE songs SET score_json=CAST({quote(canonical(target))} AS JSON), {replaced}is_scorable=TRUE "
                   f"WHERE id={item['songId']} AND BINARY source=BINARY {quote(item['source'])} AND score_version={item['scoreVersion']} "
                   f"AND is_scorable=FALSE AND deleted_at IS NULL AND score_json=CAST({quote(canonical(current))} AS JSON);")
        sql.append('INSERT INTO _sogeum_scoring_assert VALUES (ROW_COUNT());')
    sql.append('COMMIT;')
    return '\n'.join(sql)


def mysql(sql):
    # infra/terms/publish.py와 같은 컨테이너 찾기. 비밀번호는 MySQL 컨테이너 환경에만 있다.
    deployer = Path('/opt/eolssu/bin/deploy-backend')
    container = 'eolssu-runtime-backend-1'
    if deployer.is_file():
        container = subprocess.run([str(deployer), '--active-container'], capture_output=True, text=True,
                                   timeout=15, check=True).stdout.strip()
    inspected = subprocess.run(['docker', 'inspect', container], capture_output=True, timeout=15, check=True)
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
                    target = item['_target'] if '_target' in item else patch(row['score'], item['breathEventIds'])
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
    parser.add_argument('--plan', type=Path, required=True, help='reviewed song plan JSON')
    parser.add_argument('--receipt', type=Path, required=True, help='new file for the result record')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        report = run(load_plan(args.plan), args.receipt, apply=args.apply)
    except Exception as error:
        print('Scoring stopped (' + type(error).__name__ + '); inspect the receipt and current rows before retrying', file=sys.stderr)
        return 1
    print(json.dumps({key: report[key] for key in ('status', 'pending', 'updated', 'songStatus')}))
    return 0


if __name__ == '__main__':
    sys.exit(main())
