#!/usr/bin/python3 -I
"""edo1 전용: 검증한 웹 이미지의 불변 릴리스를 배포하고 실패 시 복구한다."""
import hashlib
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import signal
import sys
import time
import urllib.parse
import urllib.request

# isolated mode에서 작업 디렉터리 대신 root가 관리하는 인접 도구만 읽는다.
support_file = Path(__file__).with_name('deploy-backend.py')
if not support_file.exists():
    support_file = Path(__file__).with_name('deploy-backend')
loader = importlib.machinery.SourceFileLoader('backend_support', str(support_file))
spec = importlib.util.spec_from_loader(loader.name, loader)
support = importlib.util.module_from_spec(spec)
loader.exec_module(support)
DeployError, run, atomic, digest = support.DeployError, support.run, support.atomic, support.digest
MARKER = '__eolssu_release.json'


def manifest(web):
    files = {}
    total = 0
    for path in sorted(web.rglob('*')):
        if path.is_symlink():
            raise DeployError('web release contains a symlink')
        if path.is_dir():
            continue
        if not path.is_file() or path.relative_to(web).parts[0] == '.well-known':
            raise DeployError('web release contains an unsupported file')
        total += path.stat().st_size
        if total > 256 * 1024 * 1024 or len(files) >= 5000:
            raise DeployError('web release exceeds the deployment limit')
        if path.name != MARKER:
            files[path.relative_to(web).as_posix()] = digest(path)
    if not {'index.html', 'webApp.js'} <= files.keys() or not any(p.endswith('.wasm') for p in files):
        raise DeployError('web release is missing index, JS or Wasm')
    return files


def manifest_digest(files):
    return hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()


def verify_assets(base, files):
    deadline = time.monotonic() + 60
    for name, expected in files.items():
        if time.monotonic() >= deadline:
            raise DeployError('web asset verification deadline exceeded')
        request = urllib.request.Request(base + '/' + urllib.parse.quote(name, safe='/'),
                                         headers={'Cache-Control': 'no-cache'})
        with urllib.request.urlopen(request, timeout=10) as response:
            if response.status != 200 or hashlib.sha256(response.read(256 * 1024 * 1024 + 1)).hexdigest() != expected:
                raise DeployError('served web asset differs from candidate')
            if name.endswith('.wasm') and response.headers.get_content_type() != 'application/wasm':
                raise DeployError('Wasm MIME type is incorrect')


class FrontendDeployer:
    def __init__(self, root=Path('/opt/eolssu')):
        self.root = root
        self.env = root / '.env'
        self.state = root / 'frontend-deployment.json'

    def save(self, data):
        data['updated_at'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        atomic(self.state, json.dumps(data, sort_keys=True) + '\n')
        print(json.dumps({k: v for k, v in data.items() if k != 'previous_web'}), flush=True)

    def current_web(self):
        entries = re.findall(r'^WEB_DIST_DIR=(.+)$', self.env.read_text(), re.M)
        if len(entries) != 1:
            raise DeployError('WEB_DIST_DIR must occur exactly once')
        web = Path(entries[0])
        if not web.is_dir() or not web.resolve().is_relative_to((self.root / 'releases').resolve()):
            raise DeployError('web release missing or outside managed releases')
        return web

    def select(self, web):
        text, count = re.subn(r'^WEB_DIST_DIR=.+$', lambda _: 'WEB_DIST_DIR=' + str(web), self.env.read_text(), flags=re.M)
        if count != 1:
            raise DeployError('WEB_DIST_DIR must occur exactly once')
        atomic(self.env, text)

    def activate(self):
        run('docker', 'compose', '--env-file', str(self.env), '-f', str(self.root / 'config/compose.yml'),
            '-f', str(self.root / 'config/compose.tls.yml'), 'up', '-d', '--no-deps',
            '--force-recreate', '--pull', 'never', '--wait', '--wait-timeout', '60', 'frontend', timeout=90)

    def healthy(self):
        web = self.current_web()
        verify_assets('http://127.0.0.1', manifest(web))
        details = json.loads(run('docker', 'inspect', 'eolssu-runtime-frontend-1'))[0]
        mounts = {m['Destination']: m['Source'] for m in details['Mounts']}
        if mounts.get('/usr/share/nginx/html') != str(web) or not details['State']['Running']:
            raise DeployError('running frontend does not match selected release')
        if mounts.get('/usr/share/nginx/html/.well-known') != str(self.root / 'acme/.well-known'):
            raise DeployError('stable ACME webroot is not mounted')
        if (web / MARKER).exists():
            with urllib.request.urlopen('http://127.0.0.1/' + MARKER, timeout=5) as response:
                if json.load(response) != json.loads((web / MARKER).read_text()):
                    raise DeployError('served web release marker is incorrect')
        run('curl', '--fail', '--silent', '--show-error', '--max-time', '10',
            '--resolve', 'k15e102.p.ssafy.io:443:127.0.0.1', '--output', '/dev/null',
            'https://k15e102.p.ssafy.io/')
        support.probe('http://127.0.0.1/api/infra-smoke', 404, '/api/infra-smoke', seconds=10)

    def protected(self):
        values = json.loads(run('docker', 'inspect', 'eolssu-runtime-backend-1', 'eolssu-runtime-mysql-1'))
        if values[1]['State'].get('Health', {}).get('Status') != 'healthy':
            raise DeployError('MySQL is not healthy')
        return {'backend': values[0]['Id'], 'mysql': values[1]['Id'],
                'volumes': [m['Name'] for m in values[1]['Mounts'] if m['Type'] == 'volume']}

    def unchanged(self, before):
        if self.protected() != before:
            raise DeployError('backend/MySQL identity changed during frontend deployment')

    def recover(self):
        if not self.state.exists():
            return
        saved = json.loads(self.state.read_text())
        if saved['status'] not in ('deploying', 'rollback_failed'):
            return
        try:
            old = Path(saved['previous_web'])
            if not old.is_dir() or not old.resolve().is_relative_to((self.root / 'releases').resolve()):
                raise DeployError('rollback web release missing')
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
            raise DeployError('frontend rollback failed; restore the previous release before retrying') from None

    def already_deployed(self, sha, image_id):
        if not self.state.exists():
            return False
        saved = json.loads(self.state.read_text())
        if saved.get('status') == 'deployed' and saved.get('sha') == sha and saved.get('image_id') == image_id:
            if manifest_digest(manifest(self.current_web())) != saved['manifest_sha256']:
                raise DeployError('deployed web release content changed')
            self.healthy()
            print(json.dumps({'status': 'already_deployed', 'sha': sha}), flush=True)
            return True
        return False

    def promote(self, sha, web, image_id, protected):
        self.save({'status': 'deploying', 'sha': sha, 'previous_web': str(self.current_web()),
                   'web': str(web), 'manifest_sha256': manifest_digest(manifest(web)),
                   'image_id': image_id, 'protected': protected})
        try:
            self.select(web)
            self.activate()
            self.healthy()
            self.unchanged(protected)
        except Exception:
            self.recover()
            raise DeployError('frontend deployment failed; previous release restored') from None
        saved = json.loads(self.state.read_text())
        saved['status'] = 'deployed'
        self.save(saved)

    def latest(self, sha):
        support.Deployer().latest(sha)

    def deploy(self, sha):
        support.validate_sha(sha)
        self.recover()
        self.latest(sha)
        image = json.loads(run('docker', 'image', 'inspect', 'eolssu-frontend:ci-' + sha))[0]
        if image['Config'].get('Labels', {}).get('org.opencontainers.image.revision') != sha:
            raise DeployError('frontend image revision does not match candidate')
        if self.already_deployed(sha, image['Id']):
            return
        protected = self.protected()
        name = 'eolssu-web-stage-' + sha[:12]
        for old in run('docker', 'ps', '-aq', '--filter', 'label=com.eolssu.stage=frontend').split():
            run('docker', 'rm', '-f', old)
        for old in run('docker', 'network', 'ls', '-q', '--filter', 'label=com.eolssu.stage=frontend').split():
            run('docker', 'network', 'rm', old)
        run('docker', 'network', 'create', '--internal', '--label', 'com.eolssu.stage=frontend', name)
        try:
            run('docker', 'run', '-d', '--name', name, '--label', 'com.eolssu.stage=frontend', '--network', name, image['Id'])
            address = json.loads(run('docker', 'inspect', name))[0]['NetworkSettings']['Networks'][name]['IPAddress']
            support.probe('http://' + address, 200, seconds=20)
            release = self.root / 'releases' / sha
            release.mkdir(mode=0o755, exist_ok=True)
            candidate = release / 'web.new'
            if candidate.exists():
                shutil.rmtree(candidate)
            candidate.mkdir(mode=0o755)
            run('docker', 'cp', name + ':/usr/share/nginx/html/.', str(candidate))
            files = manifest(candidate)
            verify_assets('http://' + address, files)
            web = release / 'web'
            if web.exists():
                if manifest(web) != files:
                    raise DeployError('immutable web release exists with different content')
                shutil.rmtree(candidate)
            else:
                os.replace(candidate, web)
            (web / '.well-known').mkdir(mode=0o755, exist_ok=True)
            (web / MARKER).write_text(json.dumps({'sha': sha, 'manifest_sha256': manifest_digest(files)}) + '\n')
            (web / MARKER).chmod(0o644)
            self.unchanged(protected)
            self.latest(sha)
            self.promote(sha, web, image['Id'], protected)
        finally:
            run('docker', 'rm', '-f', name)
            run('docker', 'network', 'rm', name)


def main():
    os.environ.clear()
    os.environ.update({'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'HOME': '/root', 'LANG': 'C.UTF-8'})
    os.umask(0o022)
    if os.geteuid() != 0 or len(sys.argv) != 2:
        raise DeployError('run as root with one commit SHA argument')
    support.validate_sha(sys.argv[1])
    with open('/run/lock/eolssu-backend-deploy.lock', 'a') as lock:
        support.acquire_lock(lock)
        def interrupted(signum, frame):
            raise DeployError('frontend deployment interrupted')
        signal.signal(signal.SIGTERM, interrupted)
        signal.signal(signal.SIGINT, interrupted)
        FrontendDeployer().deploy(sys.argv[1])


if __name__ == '__main__':
    try:
        main()
    except (DeployError, OSError, ValueError) as error:
        print(json.dumps({'status': 'failed', 'reason': str(error) if isinstance(error, DeployError) else type(error).__name__}), file=sys.stderr)
        sys.exit(1)
