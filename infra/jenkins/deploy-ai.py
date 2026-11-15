#!/usr/bin/python3 -I
"""edo1 전용: 검증한 ai 이미지를 운영 ai 서비스로 교체하고 실패/중단 시 이전 이미지로 되돌린다(S15P31E102-244).

운영 Compose는 `eolssu-ai:runtime` 태그를 쓴다. 이 도구는 그 태그를 검증한 `eolssu-ai:ci-<SHA>` 이미지로 옮기고
ai만 다시 만든다. 이전 이미지는 ID로 journal에 남기고 `eolssu-ai:rollback` 태그로도 붙잡아 둔다.
ai는 DB·설정을 바꾸지 않으므로 백엔드·프론트 배포와 달리 사전·사후 DB 백업을 하지 않는다.
"""
import importlib.machinery
import importlib.util
import json
import os
from pathlib import Path
import re
import signal
import sys
import time

# isolated mode에서 작업 디렉터리 대신 root가 관리하는 인접 도구만 읽는다.
support_file = Path(__file__).with_name('deploy-backend.py')
if not support_file.exists():
    support_file = Path(__file__).with_name('deploy-backend')
loader = importlib.machinery.SourceFileLoader('backend_support', str(support_file))
spec = importlib.util.spec_from_loader(loader.name, loader)
support = importlib.util.module_from_spec(spec)
loader.exec_module(support)
DeployError, run, atomic, phase = support.DeployError, support.run, support.atomic, support.phase

REPOSITORY = 'eolssu-ai'
RUNTIME_TAG = REPOSITORY + ':runtime'
ROLLBACK_TAG = REPOSITORY + ':rollback'
CONTAINER = 'eolssu-runtime-ai-1'
REVISION_LABEL = 'org.opencontainers.image.revision'
SOURCE_LABEL = 'com.eolssu.ai.source'
IMAGE_ID = re.compile('sha256:[0-9a-f]{64}')
HEALTH_SCRIPT = ("import json, urllib.request; "
                 "print(json.dumps(json.load(urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2))))")


def candidate_image(sha):
    support.validate_sha(sha)
    return f'{REPOSITORY}:ci-{sha}'


class AiDeployer:
    def __init__(self, root=Path('/opt/eolssu')):
        self.root = root
        self.env = root / '.env'
        self.compose = root / 'config/compose.yml'
        self.state = root / 'ai-deployment.json'

    def save(self, data):
        data['updated_at'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        atomic(self.state, json.dumps(data, sort_keys=True) + '\n')
        print(json.dumps(data), flush=True)

    def journal(self):
        return json.loads(self.state.read_text()) if self.state.exists() else {}

    # --- Docker access, kept small so tests can replace them ---------------------------------------------
    def image_id(self, reference):
        value = run('docker', 'image', 'inspect', '--format', '{{.Id}}', reference)
        if not IMAGE_ID.fullmatch(value):
            raise DeployError('unexpected image id')
        return value

    def image_labels(self, reference):
        return json.loads(run('docker', 'image', 'inspect', '--format', '{{json .Config.Labels}}', reference)) or {}

    def running_image(self):
        details = json.loads(run('docker', 'inspect', CONTAINER))[0]
        if not details['State']['Running']:
            raise DeployError('ai container is not running')
        return details['Image']

    def tag(self, image_id, reference):
        if not IMAGE_ID.fullmatch(image_id):
            raise DeployError('refusing to tag an unknown image')
        run('docker', 'tag', image_id, reference)

    def activate(self):
        run('docker', 'compose', '--env-file', str(self.env), '-f', str(self.compose), 'up', '-d', '--no-deps',
            '--force-recreate', '--pull', 'never', '--wait', '--wait-timeout', '120', 'ai', timeout=180)

    def health(self):
        return json.loads(run('docker', 'exec', CONTAINER, 'python', '-c', HEALTH_SCRIPT, timeout=10))

    def contract_status(self):
        """백엔드 컨테이너 안에서 내부망 `http://ai:8000`을 부른다. 빈 요청은 계약 위반 422가 정상이다."""
        backend = support.slot_name(support.Deployer(self.root).configured_slot())
        return run('docker', 'exec', backend, 'curl', '--silent', '--output', '/dev/null', '--max-time', '10',
                   '--write-out', '%{http_code}', '--request', 'POST', '--header', 'Content-Type: application/json',
                   '--data', '{}', 'http://ai:8000/v1/analyses', timeout=15)

    def warm_up(self):
        report = json.loads(run('docker', 'exec', CONTAINER, 'python', '-m', 'ai_service.warmup', timeout=300)
                            .splitlines()[-1])
        if report.get('status') != 'ok':
            raise DeployError('synthetic scoring failed in the running ai container')
        return report

    def protected(self):
        backend = support.Deployer(self.root)
        slot = support.slot_name(backend.configured_slot())
        values = json.loads(run('docker', 'inspect', slot, support.FRONTEND, 'eolssu-runtime-mysql-1'))
        if values[2]['State'].get('Health', {}).get('Status') != 'healthy':
            raise DeployError('MySQL is not healthy')
        return {'backend': values[0]['Id'], 'frontend': values[1]['Id'], 'mysql': values[2]['Id'],
                'volumes': [m['Name'] for m in values[2]['Mounts'] if m['Type'] == 'volume']}

    # --- checks -----------------------------------------------------------------------------------------
    def unchanged(self, before):
        if self.protected() != before:
            raise DeployError('backend/frontend/MySQL identity changed during ai deployment')

    def verify(self, image_id, *, warm):
        """실행 중인 ai가 고른 이미지이고 healthy이며 백엔드에서 닿는지 본다. warm이면 채점 경로도 한 번 돈다."""
        if self.running_image() != image_id or self.image_id(RUNTIME_TAG) != image_id:
            raise DeployError('running ai does not match the selected image')
        # 예전 수동 빌드 이미지에는 /health가 없다. 되돌릴 때는 Compose healthcheck와 계약 응답만 본다.
        if warm:
            health = self.health()
            if health.get('status') != 'ok':
                raise DeployError('ai /health is not ok')
        if self.contract_status() != '422':
            raise DeployError('backend cannot reach the ai analysis contract')
        if warm:
            self.warm_up()

    def blocked_by_other_deployments(self):
        backend = support.Deployer(self.root)
        if backend.state.exists():
            journal = json.loads(backend.state.read_text())
            if journal['status'] in ('preparing', 'deploying', 'rollback_failed') or journal.get('retire_slot'):
                raise DeployError('recover or drain the backend deployment before changing ai')
        if backend.route.is_file() and not backend.route_matches(backend.current_slot()):
            raise DeployError('recover the backend route before changing ai')

    # --- transaction ------------------------------------------------------------------------------------
    def recover(self):
        saved = self.journal()
        if saved.get('status') not in ('deploying', 'rollback_failed'):
            return
        try:
            previous = saved['previous_image']
            self.tag(previous, RUNTIME_TAG)
            self.activate()
            self.verify(previous, warm=False)
            self.unchanged(saved['protected'])
            saved['status'] = 'rolled_back'
            self.save(saved)
        except Exception:
            saved['status'] = 'rollback_failed'
            self.save(saved)
            raise DeployError('ai rollback failed; restore the previous image before retrying') from None

    def deployed_sha(self):
        saved = self.journal()
        if saved.get('status') == 'deployed':
            return saved['sha']
        if saved.get('status') == 'rolled_back':
            return saved.get('previous_sha') or ''
        return ''

    def check_order(self, sha):
        support.check_release_order(sha, self.deployed_sha())

    def promote(self, sha, image_id, source, protected):
        previous = self.running_image()
        if self.image_id(RUNTIME_TAG) != previous:
            raise DeployError('eolssu-ai:runtime does not match the running container; recreate or retag first')
        self.tag(previous, ROLLBACK_TAG)
        saved = {'status': 'deploying', 'sha': sha, 'image_id': image_id, 'source': source,
                 'previous_image': previous, 'previous_sha': self.deployed_sha(), 'protected': protected}
        self.save(saved)
        try:
            with phase('Select candidate image'):
                self.tag(image_id, RUNTIME_TAG)
            with phase('Recreate ai service'):
                self.activate()
            with phase('Verify running image and backend reachability'):
                self.verify(image_id, warm=False)
            with phase('Check health and warm up scoring path'):
                self.verify(image_id, warm=True)
            with phase('Verify protected services unchanged'):
                self.unchanged(protected)
        except Exception:
            with phase('Rollback previous image'):
                self.recover()
            raise DeployError('ai deployment failed; previous image restored') from None
        saved['status'] = 'deployed'
        self.save(saved)

    def deploy(self, sha):
        image = candidate_image(sha)
        self.blocked_by_other_deployments()
        with phase('Recover interrupted deployment'):
            self.recover()
        with phase('Check deployment order'):
            self.check_order(sha)
        with phase('Validate candidate image'):
            image_id = self.image_id(image)
            labels = self.image_labels(image_id)
            if labels.get(REVISION_LABEL) != sha or not re.fullmatch('[0-9a-f]{64}', labels.get(SOURCE_LABEL, '')):
                raise DeployError('ai image labels do not match candidate')
        with phase('Check identical release'):
            if self.running_image() == image_id and self.image_id(RUNTIME_TAG) == image_id:
                self.verify(image_id, warm=False)
                print(json.dumps({'status': 'already_deployed', 'sha': sha}), flush=True)
                return
        with phase('Snapshot protected services'):
            protected = self.protected()
        self.promote(sha, image_id, labels[SOURCE_LABEL], protected)


def main():
    os.environ.clear()
    os.environ.update({'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'HOME': '/root', 'LANG': 'C.UTF-8'})
    os.umask(0o077)
    if os.geteuid() != 0 or len(sys.argv) != 2:
        raise DeployError('run as root with one commit SHA argument or --recover')
    if sys.argv[1] != '--recover':
        support.validate_sha(sys.argv[1])
    with open('/run/lock/eolssu-backend-deploy.lock', 'a') as lock:
        with phase('Wait for shared deployment lock'):
            support.acquire_lock(lock)

        def interrupted(signum, frame):
            raise DeployError('ai deployment interrupted')
        signal.signal(signal.SIGTERM, interrupted)
        signal.signal(signal.SIGINT, interrupted)
        if sys.argv[1] == '--recover':
            AiDeployer().recover()
            return
        AiDeployer().deploy(sys.argv[1])


if __name__ == '__main__':
    try:
        main()
    except (DeployError, OSError, ValueError) as error:
        print(json.dumps({'status': 'failed', 'reason': str(error) if isinstance(error, DeployError) else type(error).__name__}),
              file=sys.stderr)
        sys.exit(1)
