#!/usr/bin/env python3
"""S15P31E102-1078: fill songs.sigimsae_placements_json for published songs.

Common score v1 keeps sigimsae per event, so a symbol written on a held (`-`) slot is
shown on the event's first slot before playback. The published model performance keeps
the written slot of every symbol. `prepare` reads the public API and writes a plan that
lists only songs whose slots differ from the first-slot layout. The default run on the
server is read-only; `--apply` writes the plan in one transaction. Hold the shared backend
deployment lock and take a DB backup before --apply. After a timeout, inspect a fresh dry
run first. The score, version and model audio are never changed.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys
import urllib.request

from enable_scoring import canonical, canonical_sha, mysql, quote

TASK = 'S15P31E102-1078'
SCORE_TEXT_HASH = 'SHA2(CAST(score_json AS CHAR),256)'


def display_slots(score):
    """(cellId, row, col, eventId) in display order: column, cell, then row and col inside the cell."""
    slots = []
    for column in score['columns']:
        for cell in column['cells']:
            for unit in sorted(cell['units'], key=lambda u: (u['row'], u['col'])):
                slots.append((cell['id'], unit['row'], unit['col'], unit['eventId']))
    return slots


def validate(score, placements):
    """Every slot exists once and, per event, its slots' symbols in display order equal event.sigimsae."""
    slots = display_slots(score)
    known = {slot[:3] for slot in slots}
    by_slot = {}
    for item in placements:
        if not isinstance(item, dict) or set(item) != {'cellId', 'row', 'col', 'symbols'}:
            raise ValueError('invalid placement shape')
        key = (item['cellId'], item['row'], item['col'])
        symbols = item['symbols']
        if not isinstance(symbols, list) or not symbols or \
                not all(isinstance(s, str) and s.strip() for s in symbols):
            raise ValueError(f'invalid placement symbols at {key}')
        if key not in known or key in by_slot:
            raise ValueError(f'unknown or duplicate placement slot {key}')
        by_slot[key] = symbols
    placed = {}
    for cell_id, row, col, event_id in slots:
        placed.setdefault(event_id, []).extend(by_slot.get((cell_id, row, col), []))
    for event in score['events']:
        if placed.get(event['id'], []) != event['sigimsae']:
            raise ValueError(f"placements do not reproduce event {event['id']} sigimsae")
    return placements


def first_slot_placements(score):
    """The layout clients already use without stored slots: all symbols on the event's own slot."""
    events = {event['id']: event for event in score['events']}
    return [{'cellId': cell_id, 'row': row, 'col': col, 'symbols': events[event_id]['sigimsae']}
            for cell_id, row, col, event_id in display_slots(score)
            if event_id == f'{cell_id}-r{row}-u{col}' and events[event_id]['sigimsae']]


def performance_placements(score, performance):
    """Written slots from a published performance; repeated occurrences must agree."""
    by_slot = {}
    for segment in performance['segments']:
        key = (segment['cellId'], segment['row'], segment['col'])
        symbols = list(segment['sigimsae'])
        if by_slot.setdefault(key, symbols) != symbols:
            raise ValueError(f'repeated slot {key} changed its symbols')
    placements = [{'cellId': c, 'row': r, 'col': u, 'symbols': by_slot[(c, r, u)]}
                  for c, r, u, _ in display_slots(score) if by_slot.get((c, r, u))]
    return validate(score, placements)


def fetch_json(url):
    request = urllib.request.Request(url, headers={'Accept': 'application/json', 'User-Agent': 'eolssu-catalog-tool'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode('utf-8'))


def result(body):
    if not body.get('isSuccess'):
        raise ValueError('unsuccessful API response')
    return body['result']


def prepare(base, fetch=fetch_json):
    """Plan items for published songs whose performance places a symbol off the event's first slot."""
    plan, skipped = [], {}
    for song in result(fetch(f'{base}/api/songs'))['items']:
        song_id = int(song['songId'])
        score_item = result(fetch(f'{base}/api/songs/{song_id}/score'))
        score = score_item['score']
        if not any(event['sigimsae'] for event in score['events']):
            continue
        try:
            audio = result(fetch(f'{base}/api/songs/{song_id}/model-audio'))
        except Exception:  # 404 or unavailable model audio: no written slots to copy
            skipped[song_id] = 'no-model-audio'
            continue
        if not audio.get('performanceUrl') or audio.get('scoreVersion') != score_item['scoreVersion']:
            skipped[song_id] = 'no-matching-performance'
            continue
        performance = fetch(base + audio['performanceUrl'])
        if performance.get('scoreVersion') != score_item['scoreVersion'] or \
                performance.get('sourceSha256') != audio.get('sourceCommonSha256'):
            skipped[song_id] = 'performance-version-mismatch'
            continue
        placements = performance_placements(score, performance)
        if canonical(placements) == canonical(first_slot_placements(score)):
            continue
        plan.append({'songId': song_id, 'title': song['title'], 'scoreVersion': score_item['scoreVersion'],
                     'scoreSha256': canonical_sha(score),
                     'performanceSourceSha256': audio['sourceCommonSha256'], 'placements': placements})
    return sorted(plan, key=lambda item: item['songId']), skipped


def load_plan(path):
    plan = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(plan, list) or not plan:
        raise ValueError('plan must be a non-empty list')
    ids = []
    for item in plan:
        if not isinstance(item, dict):
            raise ValueError('invalid plan item')
        for key in ('songId', 'scoreVersion'):
            if type(item.get(key)) is not int or item[key] <= 0:
                raise ValueError('invalid ' + key)
        if not isinstance(item.get('scoreSha256'), str) or not re.fullmatch('[0-9a-f]{64}', item['scoreSha256']):
            raise ValueError('invalid score hash')
        if not isinstance(item.get('placements'), list) or not item['placements']:
            raise ValueError('missing placements')
        ids.append(item['songId'])
    if len(ids) != len(set(ids)):
        raise ValueError('duplicate song IDs')
    return sorted(plan, key=lambda item: item['songId'])


def inventory(plan, runner):
    ids = ','.join(str(item['songId']) for item in plan)
    output = runner("SELECT JSON_OBJECT('id',id,'scoreVersion',score_version,'status',status,"
                    "'deleted',IF(deleted_at IS NULL,0,1),'score',score_json,'placements',sigimsae_placements_json,"
                    f"'scoreTextHash',{SCORE_TEXT_HASH}) FROM songs WHERE id IN ({ids}) ORDER BY id;")
    rows = {}
    for row in map(json.loads, output.splitlines()):
        if row['id'] in rows:
            raise ValueError('duplicate inventory row')
        rows[row['id']] = row
    if set(rows) != {item['songId'] for item in plan}:
        raise ValueError('planned songs are missing')
    return rows


def state(item, row):
    song_id = item['songId']
    if row['deleted'] or row['status'] != 'PUBLISHED':
        raise ValueError(f'song {song_id} is not a published live song')
    if row['scoreVersion'] != item['scoreVersion'] or canonical_sha(row['score']) != item['scoreSha256']:
        raise ValueError(f'song {song_id} score differs from the plan')
    validate(row['score'], item['placements'])
    if row['placements'] is None:
        return 'pending'
    if canonical(row['placements']) == canonical(item['placements']):
        return 'done'
    raise ValueError(f'song {song_id} already has different placements')


def build_sql(plan, rows, pending):
    ids = ','.join(str(song_id) for song_id in sorted(pending))
    sql = ['CREATE TEMPORARY TABLE _sigimsae_placement_assert (ok INT NOT NULL CHECK (ok=1));',
           'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;',
           f'SELECT id FROM songs WHERE id IN ({ids}) ORDER BY id FOR UPDATE;']
    for item in plan:
        song_id = item['songId']
        if song_id not in pending:
            continue
        value = json.dumps(item['placements'], ensure_ascii=False, separators=(',', ':'))
        sql.append(f"UPDATE songs SET sigimsae_placements_json=CAST({quote(value)} AS JSON) WHERE id={song_id} "
                   f"AND score_version={item['scoreVersion']} AND status='PUBLISHED' AND deleted_at IS NULL "
                   f"AND {SCORE_TEXT_HASH}={quote(rows[song_id]['scoreTextHash'])} AND sigimsae_placements_json IS NULL;")
        sql.append('INSERT INTO _sigimsae_placement_assert VALUES (ROW_COUNT());')
    sql.append('COMMIT;')
    return '\n'.join(sql)


def run(plan, receipt, *, apply=False, runner=mysql):
    report = {'task': TASK, 'status': 'inventory', 'pending': [], 'updated': [], 'done': []}
    fd = os.open(receipt, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        try:
            before = inventory(plan, runner)
            states = {item['songId']: state(item, before[item['songId']]) for item in plan}
            report['pending'] = [song_id for song_id, value in states.items() if value == 'pending']
            report['done'] = [song_id for song_id, value in states.items() if value == 'done']
            report['status'] = 'validated-dry-run'
            if apply:
                if report['pending']:
                    report['status'] = 'apply-outcome-unknown'
                    runner(build_sql(plan, before, set(report['pending'])))
                report['status'] = 'committed-readback-pending'
                after = inventory(plan, runner)
                for item in plan:
                    song_id = item['songId']
                    if state(item, after[song_id]) != 'done':
                        raise RuntimeError('readback placements differ')
                    if before[song_id]['scoreTextHash'] != after[song_id]['scoreTextHash']:
                        raise RuntimeError('readback detected a score change')
                report.update(status='applied', updated=report['pending'], pending=[],
                              done=sorted(report['done'] + report['pending']))
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
    commands = parser.add_subparsers(dest='command')
    prep = commands.add_parser('prepare', help='read the public API and write a new plan file (no DB access)')
    prep.add_argument('--base-url', required=True, help='e.g. https://k15e102.p.ssafy.io')
    prep.add_argument('--output', type=Path, required=True, help='new plan file, never overwritten')
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--receipt', type=Path, help='new result file, never overwritten')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if args.command == 'prepare':
        plan, skipped = prepare(args.base_url.rstrip('/'))
        with open(args.output, 'x', encoding='utf-8') as stream:
            json.dump(plan, stream, ensure_ascii=False, indent=2)
            stream.write('\n')
        print(json.dumps({'planned': [item['songId'] for item in plan], 'skipped': skipped}, ensure_ascii=False))
        return 0
    if not args.plan or not args.receipt:
        parser.error('--plan and --receipt are required')
    try:
        report = run(load_plan(args.plan), args.receipt, apply=args.apply)
    except Exception as error:
        print(f'Stopped ({type(error).__name__}); inspect receipt and a new dry run before retrying', file=sys.stderr)
        return 1
    print(json.dumps({key: report[key] for key in ('status', 'pending', 'updated', 'done')}))
    return 0


if __name__ == '__main__':
    sys.exit(main())
