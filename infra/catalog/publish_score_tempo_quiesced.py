"""Apply Task728 through the existing Nginx ingress after draining old workers.

Run on edo1 as root. No server image/config replacement or persistent route change.
The temporary include is removed in finally; an interrupted process can be
recovered by inspecting the gate receipt and removing ONLY tempo-728-gate.conf,
then nginx -t and nginx -s reload in eolssu-runtime-frontend-1.
"""
import argparse
from contextlib import contextmanager
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request

import publish_score_tempo as publisher

CONTAINER = 'eolssu-runtime-frontend-1'
GATE = Path('/opt/eolssu/config/backend-route/tempo-728-gate.conf')
BODY = b'# Temporary S15P31E102-728 session gate\nif ($uri ~ "^/api/practice-sessions(?:/|$)") { return 503; }\n'
BASE = 'https://k15e102.p.ssafy.io'


def command(*args):
    return subprocess.run(args, capture_output=True, check=True, timeout=20).stdout.decode().strip()


def workers():
    return {line.split()[0] for line in command('docker', 'top', CONTAINER, '-eo', 'pid,ppid,args').splitlines()
            if 'nginx: worker process' in line}


def reload_and_drain(previous):
    command('docker', 'exec', CONTAINER, 'nginx', '-t')
    command('docker', 'exec', CONTAINER, 'nginx', '-s', 'reload')
    until = time.monotonic() + 45
    while previous & workers():
        if time.monotonic() > until:
            raise RuntimeError('old Nginx workers did not drain; no database apply')
        time.sleep(.2)


def status(path, method='GET'):
    request = urllib.request.Request(BASE + path, method=method)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return response.status
    except urllib.error.HTTPError as error:
        return error.code


def drain_backend_posts():
    """Spring's active request observation also covers disconnected handlers.

    Session creation/retry controllers are synchronous. Their POST observation
    ends after their service/insert returns. The metrics GET is not a POST and
    cannot make this count nonzero itself. Absence of this metric fails closed.
    """
    backend = command('/opt/eolssu/bin/deploy-backend', '--active-container')
    until = time.monotonic() + 45
    while True:
        metrics = command('docker', 'exec', backend, 'curl', '-fsS',
                          'http://127.0.0.1:8080/api/actuator/prometheus')
        values = [float(line.rsplit(' ', 1)[1]) for line in metrics.splitlines()
                  if line.startswith('http_server_requests_active_seconds_gcount{')
                  and re.search(r'(?:\{|,)method="POST"(?:,|\})', line)]
        if not values:
            raise RuntimeError('active backend POST metric unavailable')
        if any(not math.isfinite(value) or value < 0 for value in values):
            raise RuntimeError('invalid active backend POST metric')
        if all(value == 0 for value in values):
            return {'backend': backend, 'activePostRequests': 0, 'metricSeries': len(values)}
        if time.monotonic() > until:
            raise RuntimeError('backend POST handlers did not drain; no database apply')
        time.sleep(.2)


def main(publisher=publisher):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--upload-receipt', type=Path, required=True)
    args = parser.parse_args()
    report = {'task': publisher.TASK, 'gatePath': str(GATE), 'state': 'not-enabled'}
    gate_receipt = args.receipt.with_suffix('.gate.json')
    if gate_receipt.exists():
        raise ValueError('use a new receipt')
    original_lock = publisher.deployment_lock

    def save():
        gate_receipt.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')

    @contextmanager
    def guarded_lock():
        with original_lock():
            route = GATE.parent / 'active.conf'
            route_hash = hashlib.sha256(route.read_bytes()).hexdigest()
            old = workers()
            if not old:
                raise RuntimeError('no Nginx workers')
            owned = False
            inode = None
            try:
                # Protect partial writes and receipt failures as well as reload.
                # Exclusive creation never replaces another operator's gate.
                with GATE.open('xb') as stream:
                    owned = True
                    inode = GATE.stat().st_ino
                    stream.write(BODY)
                report.update(state='gate-file-created', activeRouteSha256=route_hash, oldWorkers=sorted(old))
                save()
                reload_and_drain(old)
                probes = {p: status(p, 'POST') for p in
                          ('/api/practice-sessions', '/api/practice-sessions/1/retry')}
                if set(probes.values()) != {503} or status('/api/actuator/health/readiness') != 200:
                    raise RuntimeError('session gate/readiness verification failed')
                backend_drain = drain_backend_posts()
                report.update(state='quiesced', probes=probes, backendDrain=backend_drain)
                save()
                print('Session ingress quiesced; old Nginx requests drained', flush=True)
                yield
            finally:
                if owned:
                    try:
                        if GATE.exists() and (GATE.stat().st_ino != inode or not BODY.startswith(GATE.read_bytes())):
                            raise RuntimeError('gate changed concurrently; inspect before removing')
                        old = workers()
                        GATE.unlink(missing_ok=True)
                        reload_and_drain(old)
                        restored = status('/api/practice-sessions', 'POST')
                        if restored == 503 or status('/api/actuator/health/readiness') != 200:
                            raise RuntimeError('ingress restoration not verified')
                        if hashlib.sha256(route.read_bytes()).hexdigest() != route_hash:
                            raise RuntimeError('active route changed during apply')
                        report.update(state='restored', restoredSessionStatus=restored)
                        save()
                        print('Session ingress restored; active backend route unchanged', flush=True)
                    except BaseException as error:
                        report.update(state='restore-failed', errorType=type(error).__name__, gateFileExists=GATE.exists())
                        save()
                        raise

    publisher.deployment_lock = guarded_lock
    try:
        result = publisher.run(args.bundle, args.receipt, apply=True,
                               upload_receipt=args.upload_receipt, traffic_quiesced=True)
        print(json.dumps(result, ensure_ascii=False))
    finally:
        publisher.deployment_lock = original_lock


if __name__ == '__main__':
    main()
