#!/usr/bin/python3
"""edo2 watcher: post to Mattermost only when a confirmed operational state changes.

The checks reuse the inspection relay: edo2 probes edo1 over public HTTPS and runs the
existing fixed collector modes over the same restricted SSH keys. Secrets and webhook
URLs are never printed.
"""
import argparse
from datetime import datetime, timedelta, timezone
import fcntl
import importlib.util
import json
import logging
import os
from pathlib import Path
import re
import ssl
import sys
import time
from urllib.parse import urlsplit

HERE = Path(__file__).resolve().parent
KST = timezone(timedelta(hours=9))
LEVELS = {'ok': 0, 'warn': 1, 'crit': 2}
CONFIRM = 2
# source id -> (target, kind, seconds between scheduled runs)
SOURCES = {
    'edo1:external': ('edo1', 'external', 60),
    'edo2:health': ('edo2', 'health', 300),
    'edo1:server': ('edo1', 'server', 900),
    'edo2:server': ('edo2', 'server', 900),
    # edo1 health includes live R2 read/write probes, so it runs less often.
    'edo1:health': ('edo1', 'health', 1800),
}
DISK = re.compile(r'(?P<mount>\S+) 공간 (?P<space>\d+)%·inode (?P<inode>\d+)%')
FAILED_UNITS = re.compile(r'실패 서비스 (?P<count>\d+)개')
DISK_WARN, DISK_CRIT = 85, 95
MAX_ROWS = 20
TEXT_LIMIT = 14000
STATUS = {'실패': 'crit', '주의': 'warn', '정상': 'ok'}
TITLES = {'crit': '장애', 'warn': '주의', 'ok': '복구'}
ICONS = {'crit': '❌', 'warn': '⚠️', 'ok': '✅'}


def load_relay():
    spec = importlib.util.spec_from_file_location('relay', HERE / 'relay.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def signal(key, label, level, evidence, action):
    return {'key': key, 'label': label, 'level': level, 'evidence': evidence, 'action': action}


def table_signals(source, target, rows):
    """Turn collector/relay rows [name, location, status, evidence, elapsed, detail] into signals."""
    signals = []
    for row in rows:
        name, location, status, evidence = (str(value) for value in row[:4])
        action = str(row[5]) if len(row) > 5 else '—'
        signals.append(signal(f'{source}:{name}:{location}', f'{target} {name}',
                              STATUS.get(status), evidence, action))
    return signals


def server_signals(source, target, issues):
    signals = []
    for issue in issues:
        disk = DISK.search(issue)
        if disk:
            percent = max(int(disk['space']), int(disk['inode']))
            level = 'crit' if percent >= DISK_CRIT else 'warn' if percent >= DISK_WARN else 'ok'
            signals.append(signal(f'{source}:disk:{disk["mount"]}', f'{target} 디스크 {disk["mount"]}', level,
                                  f'공간 {disk["space"]}% · inode {disk["inode"]}%',
                                  f'/{target} 서버 표의 저장공간과 회수 가능량 확인; 넓은 prune 금지'))
            continue
        failed = FAILED_UNITS.search(issue)
        if failed:
            signals.append(signal(f'{source}:failed-units', f'{target} systemd 실패 서비스', 'warn',
                                  f'실패 서비스 {failed["count"]}개',
                                  f'{target}에서 systemctl --failed와 해당 unit journal 확인'))
    return signals


def collect(relay, app, config, source):
    """Return (signals, complete). complete=False means absent keys must not be read as recovered."""
    target, kind, _ = SOURCES[source]
    settings = relay.target_settings(config, target)
    collect_key = f'{source}:collect'
    label = f'{target} {"상태" if kind == "health" else "서버"} 점검 수집'
    if kind == 'external':
        return table_signals(source, target, relay.external_health(settings['service_host'], target=target)), True
    try:
        result = app.collect(settings, target, kind)
    except Exception as error:  # SSH, timeout, malformed output: the probe itself failed.
        logging.warning('collect_failed source=%s error=%s', source, type(error).__name__)
        return [signal(collect_key, label, 'crit', 'edo2에서 SSH 수집 실패·시간 초과',
                       f'edo2→{target} SSH 연결과 {target} collector 실행 확인')], False
    ok = signal(collect_key, label, 'ok', '수집 성공', '—')
    if kind == 'health':
        return [ok] + table_signals(source, target, result['rows']), True
    return [ok] + server_signals(source, target, result.get('issues') or []), True


def source_of(key):
    return ':'.join(key.split(':')[:2])


def due_sources(state, now, only=None):
    """Return {source: early}. early=True is a quick re-check of a suspected change.

    At most one early run follows each scheduled run, so a value that flips every run
    cannot turn the 30-minute R2-probing collection into a per-minute one.
    """
    sources = state.setdefault('sources', {})
    pending = {source_of(key) for key, value in state.get('keys', {}).items()
               if value.get('observed') not in (None, value.get('alerted'))}
    due = {}
    for source, (_, _, interval) in SOURCES.items():
        if only and source not in only:
            continue
        record = sources.get(source, {})
        last = record.get('last_run')
        if last is None or now - last >= interval:
            due[source] = False
        elif source in pending and not record.get('early'):
            due[source] = True
    return due


def evaluate(state, results, now):
    """Advance per-key streaks and return confirmed transitions; state is mutated in place."""
    keys = state.setdefault('keys', {})
    events = []
    for source, (signals, complete) in results.items():
        state.setdefault('sources', {})[source] = {'last_run': now}
        seen = set()
        for item in signals:
            seen.add(item['key'])
            if item['level'] is None:  # 미측정·미검증: neither failure nor recovery evidence.
                continue
            events.extend(advance(keys, item, now))
        if complete:
            prefix = source + ':'
            for key, value in list(keys.items()):
                if key.startswith(prefix) and key not in seen and not key.endswith(':collect'):
                    gone = signal(key, value.get('label', key), 'ok', '판정 대상에서 사라짐(정상 범위)', '—')
                    events.extend(advance(keys, gone, now))
                    if keys[key]['alerted'] == 'ok' and keys[key]['observed'] == 'ok':
                        del keys[key]
    return events


def advance(keys, item, now):
    entry = keys.setdefault(item['key'], {'alerted': 'ok', 'observed': None, 'streak': 0, 'since': now})
    entry['label'] = item['label']
    if entry['observed'] == item['level']:
        entry['streak'] += 1
    else:
        entry.update(observed=item['level'], streak=1)
    if entry['streak'] < CONFIRM or entry['observed'] == entry['alerted']:
        return []
    event = dict(item, previous=entry['alerted'], started=entry['since'], at=now)
    entry['alerted'], entry['since'] = entry['observed'], now
    return [event]


def clean(value, limit=160):
    text = re.sub(r'https?://\S+', '[URL 생략]', str(value))
    text = re.sub(r'[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}', '[EMAIL]', text)
    text = text.replace('@', '@\u200b').replace('|', '\\|').replace('\n', ' ').replace('`', "'")
    # Mattermost renders :name: as an emoji, e.g. the :00: in 07:00:00; break the shortcode.
    text = re.sub(r':(?=[A-Za-z0-9_+-]+:)', ':\u2060', text)
    return text[:limit]


def kst(epoch):
    return datetime.fromtimestamp(epoch, KST).strftime('%m-%d %H:%M')


def duration(seconds):
    minutes = int(seconds // 60)
    return f'{minutes // 60}시간 {minutes % 60}분' if minutes >= 60 else f'{minutes}분'


def render(events, mention=''):
    order = sorted(events, key=lambda event: (-LEVELS[event['level']], event['label']))
    counts = {level: sum(event['level'] == level for event in events) for level in ('crit', 'warn', 'ok')}
    worst = 'crit' if counts['crit'] else 'warn' if counts['warn'] else 'ok'
    heading = ' · '.join(f'{TITLES[level]} {counts[level]}' for level in ('crit', 'warn', 'ok') if counts[level])
    lines = [f'### {ICONS[worst]} 운영 감시 · {heading}', '',
             '| 상태 | 대상 | 근거 | 시점 | 확인할 것 |', '| --- | --- | --- | --- | --- |']
    for event in order[:MAX_ROWS]:
        level, previous = event['level'], event['previous']
        if level == 'ok':
            state, when = f'{ICONS["ok"]} 복구', f'{TITLES[previous]} {duration(event["at"] - event["started"])} 뒤 복구'
        elif previous != 'ok':
            state, when = f'{ICONS[level]} {TITLES[level]}', f'{TITLES[previous]}에서 변경 · {kst(event["at"])}'
        else:
            state, when = f'{ICONS[level]} {TITLES[level]}', f'{kst(event["at"])} 확인(2회 연속)'
        lines.append(f'| {state} | {clean(event["label"], 60)} | {clean(event["evidence"])} | {when} | '
                     f'{clean(event["action"] if level != "ok" else "—")} |')
    if len(order) > MAX_ROWS:
        lines.append(f'\n나머지 {len(order) - MAX_ROWS}건은 edo2 journal(edo-check-alert)에서 확인하세요.')
    lines.append('\n상세는 `/edo1`·`/edo2`로 확인합니다. 상태가 바뀔 때만 알립니다.')
    if mention and counts['crit'] and any(event['level'] == 'crit' and event['previous'] != 'crit' for event in events):
        lines.append(mention)
    return '\n'.join(lines)[:TEXT_LIMIT]


class NotDelivered(Exception):
    """The webhook certainly did not accept the post; it is safe to try again later."""


def webhook_destination(url):
    parsed = urlsplit(url)
    if (parsed.scheme != 'https' or parsed.hostname != 'meeting.ssafy.com' or parsed.port not in (None, 443)
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or not re.fullmatch(r'/hooks/[a-z0-9]+', parsed.path)):
        raise ValueError('invalid Mattermost webhook destination')
    return parsed.hostname, parsed.path


def send(relay, url, text, channel=''):
    host, path = webhook_destination(url)
    payload = {'username': 'edo 감시', 'text': text}
    if channel:
        payload['channel'] = channel
    connection = relay.PublicHTTPS(host, 443, timeout=10, context=ssl.create_default_context())
    try:
        try:
            connection.connect()
        except Exception as error:
            raise NotDelivered('connect') from error
        connection.request('POST', path, json.dumps(payload, ensure_ascii=False).encode(),
                           {'Content-Type': 'application/json'})
        response = connection.getresponse()
        response.read(4096)
        if response.status != 200:
            raise NotDelivered('rejected')
    finally:
        connection.close()


def save(path, state):
    temporary = path.with_suffix('.tmp')
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, 'w') as output:
        json.dump(state, output, ensure_ascii=False)
    os.replace(temporary, path)


def load_state(path):
    try:
        state = json.loads(path.read_text())
    except FileNotFoundError:
        return {'keys': {}, 'sources': {}}
    if not isinstance(state, dict):
        raise ValueError('invalid alert state')
    return state


def deliver(state, path, events, sender, mention=''):
    """Persist the transition before sending. Only certain non-delivery is retried."""
    text = render(events, mention)
    state['last_delivery'] = {'at': events[0]['at'], 'events': len(events), 'state': 'sending'}
    save(path, state)
    try:
        sender(text)
        state['last_delivery']['state'] = 'sent'
    except NotDelivered:
        # Undo the confirmed transitions; the next run confirms them again and retries the post.
        for event in events:
            state['keys'][event['key']] = {'label': event['label'], 'alerted': event['previous'],
                                           'observed': event['level'], 'streak': CONFIRM - 1,
                                           'since': event['started']}
            state['sources'].setdefault(source_of(event['key']), {})['early'] = False  # allow the retry run
        state['last_delivery']['state'] = 'failed'
    except Exception:
        state['last_delivery']['state'] = 'uncertain'  # Never auto-repeat an ambiguous post.
    save(path, state)
    return state['last_delivery']['state']


def run(config, state_dir, sender, now, *, only=None, dry_run=False, mention=''):
    relay = load_relay()
    relay.validate_config(config)
    app = relay.App(config)
    path = state_dir / 'state.json'
    state = load_state(path)
    working = json.loads(json.dumps(state)) if dry_run else state
    due = due_sources(working, now, only)
    results = {source: collect(relay, app, config, source) for source in due}
    if dry_run:
        signals = [item for value, _ in results.values() for item in value if item['level'] not in (None, 'ok')]
        return {'due': sorted(results), 'non_ok': signals}
    scheduled = {source: state.get('sources', {}).get(source, {}).get('last_run') for source in due}
    events = evaluate(state, results, now)
    for source, early in due.items():
        # An early re-check does not move the regular schedule.
        state['sources'][source] = {'last_run': scheduled[source] if early else now, 'early': early}
    for event in events:
        print(json.dumps({'event': 'alert_transition', 'key': event['key'], 'from': event['previous'],
                          'to': event['level']}, ensure_ascii=False))
    if not events:
        save(path, state)
        return {'due': sorted(results), 'events': 0, 'delivery': None}
    delivery = deliver(state, path, events, sender, mention)
    print(json.dumps({'event': 'alert_delivery', 'state': delivery, 'events': len(events)}))
    return {'due': sorted(results), 'events': len(events), 'delivery': delivery}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('config')
    parser.add_argument('--dry-run', action='store_true', help='collect and print non-ok signals; no state, no send')
    parser.add_argument('--test-notice', action='store_true', help='send one connection-check message')
    parser.add_argument('--only', action='append', choices=sorted(SOURCES))
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format='%(levelname)s %(message)s')
    config = json.loads(Path(args.config).read_text())
    state_dir = Path(os.environ.get('STATE_DIRECTORY', '/var/lib/edo-check-alert'))
    credentials = Path(os.environ.get('CREDENTIALS_DIRECTORY', '/etc/edo-check-alert'))
    channel, mention = os.environ.get('ALERT_CHANNEL', ''), os.environ.get('ALERT_MENTION', '')
    if mention not in ('', '@here', '@channel'):
        raise ValueError('ALERT_MENTION must be empty, @here or @channel')
    relay = load_relay()

    def sender(text):
        send(relay, (credentials / 'webhook-url').read_text().strip(), text, channel)

    state_dir.mkdir(parents=True, exist_ok=True)
    with (state_dir / 'lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if args.test_notice:
            sender('### ✅ 운영 감시 연결 확인\n\nedo2 edo-check-alert에서 보낸 시험 메시지입니다. 실제 장애가 아닙니다.')
            print(json.dumps({'event': 'alert_test_notice', 'state': 'sent'}))
            return
        result = run(config, state_dir, sender, time.time(), only=args.only, dry_run=args.dry_run, mention=mention)
        if args.dry_run:
            print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'event': 'alert_error', 'type': type(error).__name__}), file=sys.stderr)
        raise SystemExit(1)
