#!/usr/bin/env python3
"""Project-owned log/request retention and structured administrator data-access records."""
import argparse
from datetime import datetime, timedelta, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import uuid

LOGS = Path('/var/log/eolssu/ordinary')
STATE = Path('/var/lib/eolssu-privacy')
LOG_NAME = re.compile(r'eolssu-(frontend|backend|ai|mysql|playback|omr)\.(\d{4}-\d{2}-\d{2})\.log')
AUDIT_NAME = re.compile(r'access\.(\d{4}-\d{2}-\d{2})\.jsonl')


def year_later(day):
    try:
        return day.replace(year=day.year + 1)
    except ValueError:
        return day.replace(year=day.year + 1, day=28)


def cleanup(logs=LOGS, state=STATE, now=None):
    now = now or datetime.now(timezone.utc)
    deleted = dict(logs=0, audits=0, completed_requests=0)
    if logs.is_symlink() or state.is_symlink():
        raise ValueError('privacy root must not be a symlink')
    for path in logs.glob('*.log'):
        match = LOG_NAME.fullmatch(path.name)
        if not match:
            continue
        if path.is_symlink():
            raise ValueError('symlink log rejected')
        day = datetime.strptime(match[2], '%Y-%m-%d').date()
        # Daily buckets and an hourly job: one-day margin keeps the maximum below 14 days.
        if now.date() - day >= timedelta(days=13):
            path.unlink(); deleted['logs'] += 1
    audit = state / 'audit'
    if audit.is_symlink():
        raise ValueError('symlink audit directory rejected')
    for path in audit.glob('*.jsonl'):
        match = AUDIT_NAME.fullmatch(path.name)
        if not match:
            continue
        if path.is_symlink():
            raise ValueError('symlink audit rejected')
        day = datetime.strptime(match[1], '%Y-%m-%d').date()
        if now.date() > year_later(day) + timedelta(days=1):
            path.unlink(); deleted['audits'] += 1
    requests = state / 'requests'
    if requests.is_symlink():
        raise ValueError('symlink request directory rejected')
    for folder in requests.glob('*'):
        if folder.is_symlink() or not folder.is_dir():
            raise ValueError('invalid request directory')
        if str(uuid.UUID(folder.name)) != folder.name:
            raise ValueError('invalid request identifier')
        metadata = folder / 'request.json'
        if metadata.is_symlink():
            raise ValueError('symlink request metadata rejected')
        data = json.loads(metadata.read_text())
        completed = data.get('completedAt')
        if completed is None:
            continue
        when = datetime.fromisoformat(completed.replace('Z', '+00:00'))
        if when.tzinfo is None:
            raise ValueError('request completion timestamp requires timezone')
        if when + timedelta(days=90) <= now:
            if folder.resolve().parent != requests.resolve():
                raise ValueError('request path escaped root')
            if any(path.is_symlink() for path in folder.rglob('*')):
                raise ValueError('request attachment symlink rejected')
            shutil.rmtree(folder)
            deleted['completed_requests'] += 1
    return deleted


def audit_record(actor, source, subject, operation, reason, state=STATE, now=None):
    if operation not in ('READ', 'UPDATE', 'DELETE', 'EXPORT'):
        raise ValueError('invalid data operation')
    if not all(re.fullmatch(r'[A-Za-z0-9_.:@/-]{1,120}', item) for item in (actor, source, subject)):
        raise ValueError('audit identifiers must be bounded identifiers, not payloads')
    if not reason or len(reason) > 300 or '\n' in reason:
        raise ValueError('reason is required; omit personal payloads and secrets')
    now = now or datetime.now(timezone.utc)
    directory = state / 'audit'
    if state.is_symlink() or directory.is_symlink():
        raise ValueError('audit path must not be a symlink')
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    path = directory / ('access.' + now.date().isoformat() + '.jsonl')
    fd = os.open(path, os.O_APPEND | os.O_CREAT | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'a') as output:
        fcntl.flock(output, fcntl.LOCK_EX)
        output.write(json.dumps(dict(at=now.isoformat(), actor=actor, source=source, subject=subject, operation=operation, reason=reason), ensure_ascii=False) + '\n')
        output.flush(); os.fsync(output.fileno())


def main():
    if os.geteuid() != 0:
        raise SystemExit('requires root')
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    sub.add_parser('cleanup')
    record = sub.add_parser('audit')
    for field in ('actor', 'source', 'subject', 'operation', 'reason'):
        record.add_argument('--' + field, required=True)
    args = parser.parse_args()
    if args.action == 'audit':
        audit_record(args.actor, args.source, args.subject, args.operation, args.reason)
        print('audit_recorded=True')
    else:
        print(json.dumps(cleanup()))


if __name__ == '__main__':
    main()
