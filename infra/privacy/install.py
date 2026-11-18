#!/usr/bin/env python3
"""Install scoped log retention on the runtime host; --apply is required for service changes."""
import argparse
import grp
import json
import os
from pathlib import Path
import pwd
import socket
import subprocess
import time
import uuid

HERE = Path(__file__).resolve().parent
TARGET = Path('/opt/eolssu-privacy')
CONFIG = Path('/etc/rsyslog.d/30-eolssu-logs.conf')


def command(args, timeout=30):
    result = subprocess.run(args, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('command failed: ' + Path(args[0]).name)
    return result.stdout


def private_directory(path, uid=0, gid=0):
    if path.is_symlink() or (path.exists() and not path.is_dir()):
        raise ValueError('unexpected installation path')
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    path.chmod(0o700)
    os.chown(path, uid, gid)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    for name in ('retention.py', '30-eolssu-logs.conf', 'eolssu-privacy-retention.service', 'eolssu-privacy-retention.timer'):
        if not (HERE / name).is_file():
            raise ValueError('missing reviewed source file')
    if not args.apply:
        print('sources_ready=True; no host changes')
        return
    if os.geteuid() != 0:
        raise ValueError('requires root')
    marker = TARGET / '.owner'
    if TARGET.is_symlink() or (TARGET.exists() and (not marker.is_file() or marker.read_text() != 'S15P31E102-300')):
        raise ValueError('unowned installation directory')
    if CONFIG.is_symlink() or (CONFIG.exists() and not marker.exists()):
        raise ValueError('unowned rsyslog configuration')
    command(['rsyslogd', '-N1'])
    uid, gid = pwd.getpwnam('syslog').pw_uid, grp.getgrnam('adm').gr_gid
    private_directory(TARGET)
    marker.write_text('S15P31E102-300')
    private_directory(Path('/var/log/eolssu'), uid, gid)
    private_directory(Path('/var/log/eolssu/ordinary'), uid, gid)
    for path in ('/var/lib/eolssu-privacy', '/var/lib/eolssu-privacy/audit', '/var/lib/eolssu-privacy/requests'):
        private_directory(Path(path))
    script = TARGET / 'retention.py'
    script.write_bytes((HERE / 'retention.py').read_bytes()); script.chmod(0o755)
    previous = CONFIG.read_bytes() if CONFIG.exists() else None
    try:
        CONFIG.write_bytes((HERE / '30-eolssu-logs.conf').read_bytes()); CONFIG.chmod(0o644)
        command(['rsyslogd', '-N1'])
        command(['systemctl', 'restart', 'rsyslog'])
        with socket.create_connection(('127.0.0.1', 1514), timeout=5):
            pass
        listening = command(['ss', '-ltnH', 'sport', '=', ':1514']).decode()
        if not listening.strip() or any(line.split()[3] != '127.0.0.1:1514' for line in listening.splitlines()):
            raise RuntimeError('collector must bind only to IPv4 loopback')
        probe = 'eolssu-log-probe-300-' + uuid.uuid4().hex
        command(['docker', 'run', '--rm', '--pull=never', '--log-driver=syslog',
            '--log-opt', 'syslog-address=tcp://127.0.0.1:1514', '--log-opt', 'syslog-format=rfc5424micro',
            '--log-opt', 'cache-disabled=true', '--log-opt', 'tag=eolssu-backend',
            'nginx:1.30.6-alpine', '/bin/sh', '-c', 'printf "%s\\n" ' + probe])
        deadline = time.monotonic() + 5
        while not any(probe in path.read_text() for path in Path('/var/log/eolssu/ordinary').glob('eolssu-backend.*.log')):
            if time.monotonic() >= deadline:
                raise RuntimeError('scoped log delivery not observed')
            time.sleep(0.05)
    except Exception:
        if previous is None:
            CONFIG.unlink(missing_ok=True)
        else:
            CONFIG.write_bytes(previous)
        command(['systemctl', 'restart', 'rsyslog'])
        raise
    for name in ('eolssu-privacy-retention.service', 'eolssu-privacy-retention.timer'):
        path = Path('/etc/systemd/system') / name
        if path.is_symlink():
            raise ValueError('unexpected service unit symlink')
        path.write_bytes((HERE / name).read_bytes()); path.chmod(0o644)
    command(['systemd-analyze', 'verify', '/etc/systemd/system/eolssu-privacy-retention.service', '/etc/systemd/system/eolssu-privacy-retention.timer'])
    command(['systemctl', 'daemon-reload'])
    command(['systemctl', 'enable', '--now', 'eolssu-privacy-retention.timer'])
    print(json.dumps(dict(collector_loopback=True, synthetic_log_received=True, timer_enabled=True,
        application_containers_recreated=False)))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Privacy installation stopped (' + type(error).__name__ + '); verify retained services before continuing.')
        raise SystemExit(1)
