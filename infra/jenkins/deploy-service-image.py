#!/usr/bin/python3 -I
"""edo1 전용: 검증한 omr·playback 이미지를 운영 서비스로 교체하고 실패/중단 시 이전 이미지로 되돌린다(S15P31E102-951).

  deploy-service-image omr|playback SHA
  deploy-service-image omr|playback --recover

교체 트랜잭션(태그 이동, 재생성, 검증, 되돌리기, journal)은 ai와 같아 `deploy-ai`의 클래스를 상속한다.
더하는 것은 진행 중 작업 대기다. backend는 omr·playback 연결이 끊긴 순간의 변환·음원 작업을 재시도 없이
FAILED로 끝내므로, 대기열(QUEUED·RUNNING)이 빈 것을 배포 lock 안에서 확인한 직후에만 교체한다.
비어 있지 않으면 lock을 놓고 기다렸다가 다시 시도해, 기다리는 동안 backend·frontend·ai 배포를 막지 않는다.
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

# isolated mode에서 작업 디렉터리 대신 root가 관리하는 인접 도구만 읽는다(deploy-ai는 다시 deploy-backend를 읽는다).
support_file = Path(__file__).with_name('deploy-ai.py')
if not support_file.exists():
    support_file = Path(__file__).with_name('deploy-ai')
loader = importlib.machinery.SourceFileLoader('ai_support', str(support_file))
spec = importlib.util.spec_from_loader(loader.name, loader)
ai = importlib.util.module_from_spec(spec)
loader.exec_module(ai)
support = ai.support
DeployError, run, phase = ai.DeployError, ai.run, ai.phase

LOCK = Path('/run/lock/eolssu-backend-deploy.lock')
# 변환은 쪽마다 수십 초, 최대 10쪽이다. 음원은 90초 제한이다. 이보다 오래 차 있으면 사람이 본다.
IDLE_WAIT_SECONDS = 20 * 60
IDLE_POLL_SECONDS = 15
TIMESTAMP = re.compile(r'\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(\.\d{1,6})?')


class Busy(DeployError):
    """교체 직전 대기열에 작업이 있다. 아무것도 바꾸지 않았으므로 lock을 놓고 다시 시도한다."""


class ServiceDeployer(ai.AiDeployer):
    # 대기열에 남은 작업 수와, 교체 뒤 연결 실패로 끝난 작업 수를 세는 SQL. {since}는 검사한 UTC 시각이다.
    pending_sql = ''
    failed_sql = ''

    def warm_up(self):
        """배포 뒤 실행 이미지 안에서 서비스가 쓰는 외부 도구·자산을 확인한다. 기본은 없음."""

    # --- DB, kept small so tests can replace them ---------------------------------------------------------
    def query(self, sql):
        backend = support.Deployer(self.root)
        return backend.query_database(backend.database()['name'], sql).strip()

    def database_now(self):
        value = self.query('SELECT UTC_TIMESTAMP(6)')
        if not TIMESTAMP.fullmatch(value):
            raise DeployError('unexpected database time')
        return value

    def pending_jobs(self):
        value = self.query(self.pending_sql)
        if not value.isdigit():
            raise DeployError('cannot count running jobs')
        return int(value)

    def failed_since(self, since):
        if not TIMESTAMP.fullmatch(since):
            raise DeployError('unexpected database time')
        value = self.query(self.failed_sql.format(since=since))
        return int(value) if value.isdigit() else None

    # --- transaction ------------------------------------------------------------------------------------
    def promote(self, sha, image_id, source, protected):
        # 앞선 검사가 끝난 직후, 상태를 남기기 전에 마지막으로 본다. 여기서 멈추면 바뀐 것이 없다.
        pending = self.pending_jobs()
        if pending:
            raise Busy(f'{pending} {self.service} job(s) queued or running')
        since = self.database_now()
        super().promote(sha, image_id, source, protected)
        # 재생성하는 동안(수십 초) 새로 들어온 작업은 연결 실패로 끝날 수 있다. 되돌릴 일은 아니므로 기록·보고만 한다.
        saved = self.journal()
        saved['switched_at'] = since
        saved['failed_during_switch'] = self.failed_since(since)
        self.save(saved)


class OmrDeployer(ServiceDeployer):
    service = 'omr'
    container = 'eolssu-runtime-omr-1'
    runtime_tag = 'eolssu-omr:runtime'
    rollback_tag = 'eolssu-omr:rollback'
    source_label = 'com.eolssu.omr.source'
    state_file = 'omr-deployment.json'
    contract_path = '/v1/recognitions'
    contract_code = '422'
    healthy_status = 'ok'
    warm_up_phase = 'Check health and recognition tools'
    pending_sql = "SELECT COUNT(*) FROM sheet_conversions WHERE status IN ('QUEUED','RUNNING')"
    failed_sql = ("SELECT COUNT(*) FROM sheet_conversions WHERE status='FAILED' AND failure_cause='SERVER_ERROR' "
                  "AND finished_at >= '{since}'")

    def warm_up(self):
        # 실행 사용자로 Audiveris·정간보 변환기·스캔 모델을 읽고 실행할 수 있는지 본다(이미지 빌드 대상 scanner).
        run('docker', 'exec', self.container, 'sh', '-c',
            'test -x "$AUDIVERIS_CMD" && test -r "$JGB_GENERATOR_DIR/tools/import-ngc-omr.mjs" '
            '&& test -r "$JEONGGANBO_OMR_DIR/jngbomr/__init__.py" && node --version >/dev/null', timeout=30)


class PlaybackDeployer(ServiceDeployer):
    service = 'playback'
    container = 'eolssu-runtime-playback-1'
    runtime_tag = 'eolssu-playback:runtime'
    rollback_tag = 'eolssu-playback:rollback'
    source_label = 'com.eolssu.playback.source'
    state_file = 'playback-deployment.json'
    # Compose에서 playback은 프로필로만 켜진다(S15P31E102-481).
    compose_options = ('--profile', 'playback')
    contract_path = '/v1/render'
    contract_code = '400'
    healthy_status = 'UP'
    warm_up_phase = 'Check health and audio encoder'
    pending_sql = "SELECT COUNT(*) FROM sheet_audios WHERE status IN ('QUEUED','RUNNING')"
    failed_sql = ("SELECT COUNT(*) FROM sheet_audios WHERE status='FAILED' AND failure_code='SERVER_ERROR' "
                  "AND finished_at >= '{since}'")

    def warm_up(self):
        # 서비스는 자산·장구 샘플이 없으면 시작을 거부한다. 여기서는 생성기가 쓰는 인코더를 실행 사용자로 찾는다.
        run('docker', 'exec', self.container, 'python', '-c',
            'from data.src.playback.audio import resolve_encoder; resolve_encoder()', timeout=30)


DEPLOYERS = {'omr': OmrDeployer, 'playback': PlaybackDeployer}


def deploy_when_idle(deployer, sha, lock_path=LOCK, seconds=IDLE_WAIT_SECONDS, poll=IDLE_POLL_SECONDS,
                     sleep=time.sleep, clock=time.monotonic):
    """대기열이 빌 때까지 lock 밖에서 기다리고, lock 안에서 다시 확인한 직후 교체한다."""
    deadline = clock() + seconds
    with phase('Wait for queued and running jobs'):
        while deployer.pending_jobs():
            if clock() >= deadline:
                raise DeployError(f'{deployer.service} jobs did not finish in time; nothing was changed')
            sleep(poll)
    while True:
        with open(lock_path, 'a') as lock:
            with phase('Wait for shared deployment lock'):
                support.acquire_lock(lock)
            try:
                deployer.deploy(sha)
                return
            except Busy as busy:
                # phase 밖에서 멈췄으므로 Jenkins 단계는 실패로 표시되지 않는다. lock을 놓고 다시 기다린다.
                print(json.dumps({'event': 'jobs_pending', 'service': deployer.service, 'reason': str(busy)}),
                      flush=True)
        if clock() >= deadline:
            raise DeployError(f'{deployer.service} jobs kept arriving; nothing was changed')
        sleep(poll)


def main():
    os.environ.clear()
    os.environ.update({'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'HOME': '/root', 'LANG': 'C.UTF-8'})
    os.umask(0o077)
    if os.geteuid() != 0 or len(sys.argv) != 3 or sys.argv[1] not in DEPLOYERS:
        raise DeployError('run as root with omr|playback and one commit SHA argument or --recover')
    if sys.argv[2] != '--recover':
        support.validate_sha(sys.argv[2])
    deployer = DEPLOYERS[sys.argv[1]]()

    def interrupted(signum, frame):
        raise DeployError(deployer.service + ' deployment interrupted')
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    if sys.argv[2] == '--recover':
        with open(LOCK, 'a') as lock:
            with phase('Wait for shared deployment lock'):
                support.acquire_lock(lock)
            deployer.recover()
        return
    deploy_when_idle(deployer, sys.argv[2])


if __name__ == '__main__':
    try:
        main()
    except (DeployError, OSError, ValueError) as error:
        print(json.dumps({'status': 'failed', 'reason': str(error) if isinstance(error, DeployError) else type(error).__name__}),
              file=sys.stderr)
        sys.exit(1)
