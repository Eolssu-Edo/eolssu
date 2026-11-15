#!/usr/bin/env python3
"""Compare server-installed files and inline Jenkins jobs with the repository (read-only).

Usage: python3 -I -B infra/drift/check.py [--host edo1|edo2] [--manifest PATH]
Runs only `sudo -n sha256sum` / `sudo -n cat <job config.xml>` over SSH. Never writes to a server.
Exit code: 0 all match, 1 any drift or missing file, 2 usage/connection error.
"""
import argparse
import hashlib
import json
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SSH = ['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', '-o', 'ClearAllForwardings=yes']
JOBS = '/var/lib/jenkins/jobs'


def normalize(data, mode):
    """'text' ignores CRLF and trailing blank lines; 'exact' compares bytes."""
    if mode == 'text':
        return data.replace(b'\r\n', b'\n').rstrip(b'\n') + b'\n'
    return data


def expected(entry, root=ROOT):
    data = (root / entry['source']).read_bytes()
    for key, value in entry.get('render', {}).items():
        data = data.replace(key.encode(), value.encode())
    return normalize(data, entry.get('compare', 'exact'))


def ssh(host, command, run=subprocess.run):
    r = run(SSH + [host, command], capture_output=True, timeout=60)
    return r.returncode, r.stdout, r.stderr


def server_hashes(host, paths, run=subprocess.run):
    """Map path -> sha256 for files that exist; missing paths are absent from the result."""
    command = 'sudo -n sha256sum -- ' + ' '.join(shlex.quote(p) for p in paths) + ' 2>/dev/null; true'
    code, out, err = ssh(host, command, run)
    if code != 0:
        raise RuntimeError(f'{host}: ssh failed: {err.decode(errors="replace").strip()[:200]}')
    hashes = {}
    for line in out.decode().splitlines():
        digest, _, path = line.partition('  ')
        if len(digest) == 64:
            hashes[path] = digest
    return hashes


def server_file(host, path, run=subprocess.run):
    code, out, _ = ssh(host, 'sudo -n cat -- ' + shlex.quote(path), run)
    return out if code == 0 else None


def job_script(config_xml):
    node = ET.fromstring(config_xml).find('.//definition/script')
    return None if node is None else (node.text or '').encode()


def check(manifest, host=None, run=subprocess.run, root=ROOT):
    rows = []
    for name, entries in manifest['hosts'].items():
        if host and name != host:
            continue
        files = [e for e in entries if 'path' in e]
        hashes = server_hashes(name, [e['path'] for e in files], run)
        for e in files:
            want = expected(e, root)
            got = hashes.get(e['path'])
            if got is None:
                status = 'missing'
            elif e.get('compare', 'exact') == 'exact':
                status = 'match' if got == hashlib.sha256(want).hexdigest() else 'drift'
            else:
                data = server_file(name, e['path'], run)
                status = 'match' if data is not None and normalize(data, 'text') == want else 'drift'
            rows.append((name, e['path'], e['source'], status, e.get('note', '')))
        for e in (x for x in entries if 'job' in x):
            raw = server_file(name, f"{JOBS}/{e['job']}/config.xml", run)
            script = job_script(raw) if raw else None
            if script is None:
                status = 'missing'
            else:
                status = 'match' if normalize(script, 'text') == normalize(expected(e, root), 'text') else 'drift'
            rows.append((name, 'jenkins:' + e['job'], e['source'], status, e.get('note', '')))
    return rows


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument('--host', choices=['edo1', 'edo2'])
    p.add_argument('--manifest', default=str(Path(__file__).with_name('manifest.json')))
    a = p.parse_args(argv)
    try:
        rows = check(json.loads(Path(a.manifest).read_text()), a.host)
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f'error: {error}', file=sys.stderr)
        return 2
    labels = {'match': '일치', 'drift': '불일치', 'missing': '서버 없음'}
    for host, path, source, status, note in rows:
        print(f'{labels[status]}\t{host}\t{path}\t{source}' + (f'\t{note}' if note and status != 'match' else ''))
    bad = [r for r in rows if r[3] != 'match']
    print(f'# {len(rows)}개 중 일치 {len(rows) - len(bad)}, 불일치·없음 {len(bad)}')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
