#!/usr/bin/python3 -I
"""edo1 전용: 검증한 develop 이미지의 JAR을 배포하고 실패/중단 시 복구한다."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request


class DeployError(Exception):
    pass


def validate_sha(value):
    if not re.fullmatch('[0-9a-f]{40}', value):
        raise DeployError('expected a full lowercase commit SHA')


def run(*args, timeout=60):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise DeployError('command unavailable or timed out: ' + args[0]) from None
    if result.returncode:
        # Git/Compose errors can contain credentials or expanded environment values.
        raise DeployError('command failed: ' + args[0])
    return result.stdout.strip()


def atomic(path, text):
    temp = path.with_name(path.name + '.new')
    with open(temp, 'w', encoding='utf-8', opener=lambda p, f: os.open(p, f, 0o600)) as out:
        os.fchmod(out.fileno(), 0o600)
        out.write(text)
        out.flush()
        os.fsync(out.fileno())
    os.replace(temp, path)
    directory = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest() if hasattr(hashlib, 'file_digest') else hashlib.sha256(stream.read()).hexdigest()


def probe(url, status, path=None, seconds=90):
    deadline = time.monotonic() + seconds
    while True:
        try:
            try:
                response = urllib.request.urlopen(url, timeout=2)
            except urllib.error.HTTPError as response_error:
                response = response_error
            with response:
                body = response.read(65536)
                if response.status == status and (path is None or json.loads(body).get('path') == path):
                    return
        except (OSError, ValueError):
            pass
        if time.monotonic() >= deadline:
            raise DeployError('HTTP readiness failed')
        time.sleep(1)


class Deployer:
    def __init__(self, root=Path('/opt/eolssu')):
        self.root = root
        self.env = root / '.env'
        self.state = root / 'deployment.json'
        self.compose = root / 'config/compose.yml'

    def save(self, data):
        data['updated_at'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        atomic(self.state, json.dumps(data, sort_keys=True) + '\n')
        print(json.dumps({k: v for k, v in data.items() if k != 'previous_jar'}), flush=True)

    def current_jar(self):
        entries = re.findall(r'^BACKEND_JAR=(.+)$', self.env.read_text(), re.M)
        if len(entries) != 1:
            raise DeployError('BACKEND_JAR must occur exactly once')
        jar = Path(entries[0])
        if not jar.resolve().is_relative_to((self.root / 'releases').resolve()) or not jar.is_file():
            raise DeployError('release JAR missing or outside managed releases')
        return jar

    def select(self, jar):
        text, count = re.subn(r'^BACKEND_JAR=.+$', lambda _: 'BACKEND_JAR=' + str(jar), self.env.read_text(), flags=re.M)
        if count != 1:
            raise DeployError('BACKEND_JAR must occur exactly once')
        atomic(self.env, text)

    def activate(self):
        run('docker', 'compose', '--env-file', str(self.env), '-f', str(self.compose),
            'up', '-d', '--no-deps', '--force-recreate', '--pull', 'never', 'backend', timeout=90)

    def healthy(self):
        probe('http://127.0.0.1/api/infra-smoke', 404, '/api/infra-smoke')
        probe('http://127.0.0.1/', 200, seconds=10)
        details = json.loads(run('docker', 'inspect', 'eolssu-runtime-backend-1'))[0]
        mounts = [m['Source'] for m in details['Mounts'] if m['Destination'] == '/app/app.jar']
        if mounts != [str(self.current_jar())] or not details['State']['Running']:
            raise DeployError('running backend does not match selected JAR')

    def protected(self):
        values = json.loads(run('docker', 'inspect', 'eolssu-runtime-frontend-1', 'eolssu-runtime-mysql-1'))
        mysql = values[1]
        if mysql['State'].get('Health', {}).get('Status') != 'healthy':
            raise DeployError('MySQL is not healthy')
        return {'frontend': values[0]['Id'], 'mysql': mysql['Id'],
                'volumes': [m['Name'] for m in mysql['Mounts'] if m['Type'] == 'volume']}

    def unchanged(self, before):
        if self.protected() != before:
            raise DeployError('frontend/MySQL identity changed during deployment')

    def recover(self):
        if not self.state.exists():
            return
        saved = json.loads(self.state.read_text())
        if saved['status'] not in ('deploying', 'rollback_failed'):
            return
        try:
            old = Path(saved['previous_jar'])
            if not old.is_file() or not old.resolve().is_relative_to((self.root / 'releases').resolve()):
                raise DeployError('rollback release missing')
            self.select(old)
            self.activate()
            self.healthy()
            if 'protected' in saved:
                self.unchanged(saved['protected'])
            saved['status'] = 'rolled_back'
            self.save(saved)
        except Exception:
            saved['status'] = 'rollback_failed'
            self.save(saved)
            raise DeployError('rollback failed; restore the previous release before retrying') from None

    def already_deployed(self, sha, image_id):
        if not self.state.exists():
            return False
        saved = json.loads(self.state.read_text())
        if saved.get('status') == 'deployed' and saved.get('sha') == sha and saved.get('image_id') == image_id:
            if digest(self.current_jar()) != saved['jar_sha256']:
                raise DeployError('deployed release content changed')
            self.healthy()
            print(json.dumps({'status': 'already_deployed', 'sha': sha}), flush=True)
            return True
        return False

    def promote(self, sha, jar, image_id, protected):
        self.save({'status': 'deploying', 'sha': sha, 'previous_jar': str(self.current_jar()),
                   'jar': str(jar), 'jar_sha256': digest(jar), 'image_id': image_id, 'protected': protected})
        try:
            self.select(jar)
            self.activate()
            self.healthy()
            self.unchanged(protected)
        except Exception:
            self.recover()
            raise DeployError('deployment failed; previous release restored') from None
        saved = json.loads(self.state.read_text())
        saved['status'] = 'deployed'
        self.save(saved)

    def latest(self, sha):
        result = run('sudo', '-u', 'ubuntu', 'git', '-C', '/opt/app', 'ls-remote',
                     'origin', 'refs/heads/develop', timeout=30)
        if not result or result.split()[0] != sha:
            raise DeployError('candidate is no longer the current develop commit')

    def deploy(self, sha):
        validate_sha(sha)
        self.recover()
        self.latest(sha)
        image = json.loads(run('docker', 'image', 'inspect', 'eolssu-backend:ci-' + sha))[0]
        if image['Config'].get('Labels', {}).get('org.opencontainers.image.revision') != sha:
            raise DeployError('image revision label does not match candidate')
        if self.already_deployed(sha, image['Id']):
            return
        protected = self.protected()
        name = 'eolssu-stage-' + sha[:12]
        # Fixed label + deployment lock make leftovers from a killed process safe to reclaim.
        for old in run('docker', 'ps', '-aq', '--filter', 'label=com.eolssu.stage=backend').split():
            run('docker', 'rm', '-f', old)
        for old in run('docker', 'network', 'ls', '-q', '--filter', 'label=com.eolssu.stage=backend').split():
            run('docker', 'network', 'rm', old)
        run('docker', 'network', 'create', '--internal', '--label', 'com.eolssu.stage=backend', name)
        try:
            run('docker', 'run', '-d', '--name', name, '--label', 'com.eolssu.stage=backend',
                '--network', name, '--env', 'SERVER_PORT=8080', image['Id'])
            address = json.loads(run('docker', 'inspect', name))[0]['NetworkSettings']['Networks'][name]['IPAddress']
            probe('http://' + address + ':8080/api/infra-smoke', 404, '/api/infra-smoke')
            release = self.root / 'releases' / sha
            release.mkdir(mode=0o755, exist_ok=True)
            candidate = release / 'app.jar.new'
            run('docker', 'cp', name + ':/app/app.jar', str(candidate))
            candidate.chmod(0o644)
            jar = release / 'app.jar'
            if jar.exists() and digest(jar) != digest(candidate):
                candidate.unlink()
                raise DeployError('immutable release already exists with different content')
            os.replace(candidate, jar)
            self.unchanged(protected)
            self.latest(sha)
            self.promote(sha, jar, image['Id'], protected)
        finally:
            # Cleanup failure is a failed build; the deployment journal remains authoritative.
            run('docker', 'rm', '-f', name)
            run('docker', 'network', 'rm', name)


def main():
    os.environ.clear()
    os.environ.update({'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'HOME': '/root', 'LANG': 'C.UTF-8'})
    os.umask(0o077)
    if os.geteuid() != 0 or len(sys.argv) != 2:
        raise DeployError('run as root with one commit SHA argument')
    validate_sha(sys.argv[1])
    with open('/run/lock/eolssu-backend-deploy.lock', 'w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise DeployError('another deployment owns the lock') from None
        def interrupted(signum, frame):
            raise DeployError('deployment interrupted')
        signal.signal(signal.SIGTERM, interrupted)
        signal.signal(signal.SIGINT, interrupted)
        Deployer().deploy(sys.argv[1])


if __name__ == '__main__':
    try:
        main()
    except (DeployError, OSError, ValueError) as error:
        # Do not echo arbitrary exception messages (they may include a secret file's contents).
        print(json.dumps({'status': 'failed', 'reason': str(error) if isinstance(error, DeployError) else type(error).__name__}), file=sys.stderr)
        sys.exit(1)
