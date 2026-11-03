#!/usr/bin/python3 -I
"""edo1 전용: 검증한 develop 이미지의 JAR을 배포하고 실패/중단 시 복구한다."""
from contextlib import contextmanager
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
import zipfile
import zlib


class DeployError(Exception):
    pass


@contextmanager
def phase(name):
    print(json.dumps({'event': 'phase_started', 'phase': name}), flush=True)
    try:
        yield
    except Exception:
        print(json.dumps({'event': 'phase_failed', 'phase': name}), flush=True)
        raise
    else:
        print(json.dumps({'event': 'phase_completed', 'phase': name}), flush=True)


def validate_sha(value):
    if not re.fullmatch('[0-9a-f]{40}', value):
        raise DeployError('expected a full lowercase commit SHA')


def acquire_lock(lock, seconds=120):
    def expired(signum, frame):
        raise DeployError('deployment lock wait timed out')
    previous = signal.signal(signal.SIGALRM, expired)
    signal.alarm(seconds)
    try:
        fcntl.flock(lock, fcntl.LOCK_EX)
    finally:
        signal.alarm(0)
        signal.signal(signal.SIGALRM, previous)


def run(*args, timeout=60, env=None):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, env=env)
    except (OSError, subprocess.TimeoutExpired):
        raise DeployError('command unavailable or timed out: ' + args[0]) from None
    if result.returncode:
        # Git/Compose errors can contain credentials or expanded environment values.
        raise DeployError('command failed: ' + args[0])
    return result.stdout.strip()


def check_release_order(sha, deployed_sha, repository=Path('/opt/app'), git_prefix=('sudo', '-u', 'ubuntu')):
    """Call under the shared deployment lock, after recovery, using the active release."""
    validate_sha(sha)
    command = (*git_prefix, 'git', '-C', str(repository))
    run(*command, 'fetch', '--no-tags', 'origin',
        '+refs/heads/develop:refs/remotes/origin/develop', timeout=60)

    def ancestor(older, newer):
        try:
            result = subprocess.run((*command, 'merge-base', '--is-ancestor', older, newer),
                                    capture_output=True, timeout=30)
        except (OSError, subprocess.TimeoutExpired):
            raise DeployError('cannot determine deployment commit order') from None
        if result.returncode not in (0, 1):
            raise DeployError('cannot determine deployment commit order')
        return result.returncode == 0

    if not ancestor(sha, 'refs/remotes/origin/develop'):
        raise DeployError('candidate is not in develop history')
    # Bootstrap releases predate SHA-based release directories.
    if not re.fullmatch('[0-9a-f]{40}', deployed_sha) or deployed_sha == sha:
        return
    if ancestor(sha, deployed_sha):
        raise DeployError('candidate is older than the deployed release')
    if not ancestor(deployed_sha, sha):
        raise DeployError('candidate and deployed release histories have diverged')


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


# ops 프로필은 Flyway를 끈다. 후보 JAR의 마이그레이션은 교체 직전에 일회성 db 프로필 컨테이너로 적용한다.
MIGRATION_DIR = 'BOOT-INF/classes/db/migration/'
MIGRATION_NAME = re.compile(r'V([1-9][0-9]*)__[a-z0-9_]+\.sql')
MIGRATION_LABEL = 'com.eolssu.stage=migration'
MIGRATION_SECONDS = 300
RUNTIME_NETWORK = 'eolssu-runtime_default'
# 스키마는 JAR 롤백으로 되돌아가지 않는다. 이전 릴리스가 읽지 못하게 만드는 문장은 사람이 검토·승인한다.
DESTRUCTIVE_SQL = re.compile(
    r'\bDROP\s+(?!(?:TEMPORARY|INDEX|KEY|CHECK|CONSTRAINT|FOREIGN|PRIMARY|DEFAULT)\b)\w+'
    r'|\bRENAME\s+(?!(?:INDEX|KEY)\b)\w+|\bTRUNCATE\b|\bCHANGE\b'
    r'|\bDELETE\b(?!\s+(?:CASCADE|RESTRICT|SET|NO)\b)', re.I)


def sql_code(text):
    """주석과 문자열을 지운 SQL. 위험 문장 검사가 설명·적재 데이터의 단어에 걸리지 않게 한다."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c in '\'"`':
            j = i + 1
            while j < n:
                if text[j] == '\\' and c != '`':
                    j += 2
                elif text[j] == c and j + 1 < n and text[j + 1] == c:
                    j += 2
                elif text[j] == c:
                    break
                else:
                    j += 1
            out.append(' x ' if c == '`' else ' ')
            i = j + 1
        elif c == '#' or (text.startswith('--', i) and (i + 2 == n or text[i + 2] in ' \t\r\n')):
            j = text.find('\n', i)
            i = n if j < 0 else j
        elif text.startswith('/*', i):
            j = text.find('*/', i + 2)
            i = n if j < 0 else j + 2
            out.append(' ')
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def flyway_checksum(data):
    """Flyway가 flyway_schema_history.checksum에 기록하는 줄 단위 CRC32(부호 있는 int)."""
    lines = re.split(r'\r\n|\r|\n', data.decode('utf-8'))
    if lines and lines[-1] == '':
        lines.pop()
    if lines and lines[0].startswith('﻿'):
        lines[0] = lines[0][1:]
    crc = 0
    for line in lines:
        crc = zlib.crc32(line.encode('utf-8'), crc)
    return crc - (1 << 32) if crc >= 1 << 31 else crc


def candidate_migrations(jar):
    found = {}
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            if not entry.startswith(MIGRATION_DIR) or entry == MIGRATION_DIR:
                continue
            name = entry[len(MIGRATION_DIR):]
            match = MIGRATION_NAME.fullmatch(name)
            if not match:
                raise DeployError('unexpected file in candidate migrations: ' + name)
            version = int(match.group(1))
            if version in found:
                raise DeployError('duplicate Flyway version in candidate: V%d' % version)
            found[version] = (name, archive.read(entry))
    if not found:
        raise DeployError('candidate JAR has no Flyway migrations')
    return found


# 공개 health 경로로 검사한다. 등록 안 된 /api 경로의 404는 인증이 생기면 401이 되어 쓸 수 없다.
RUNTIME_READINESS = 'http://127.0.0.1/api/actuator/health/readiness'
UP = {'status': 'UP'}


def candidate_health_url(address):
    # 후보는 운영 DB 없이 뜨므로 readiness가 아니라 liveness를 본다.
    return 'http://' + address + ':8080/api/actuator/health/liveness'


def probe(url, status, expect=None, seconds=90):
    """status와, 주어지면 JSON 본문에 expect의 키·값이 모두 있을 때까지 기다린다."""
    deadline = time.monotonic() + seconds
    while True:
        try:
            try:
                response = urllib.request.urlopen(url, timeout=2)
            except urllib.error.HTTPError as response_error:
                response = response_error
            with response:
                body = response.read(65536)
                if response.status == status and (expect is None or expect.items() <= json.loads(body).items()):
                    return
        except (OSError, ValueError, AttributeError):
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
        self.migrated = False

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
        probe(RUNTIME_READINESS, 200, UP)
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

    def database(self):
        """실행 중 백엔드의 DB 연결값. 값은 명령줄·로그에 남기지 않고 환경변수로만 넘긴다."""
        backend = json.loads(run('docker', 'inspect', 'eolssu-runtime-backend-1'))[0]
        env = dict(item.split('=', 1) for item in backend['Config']['Env'] if '=' in item)
        match = re.fullmatch(r'(jdbc:mysql://[^/?]+/([A-Za-z0-9_]+))(?:\?.*)?', env.get('DB_URL', ''))
        if not match or not env.get('DB_USERNAME') or not env.get('DB_PASSWORD'):
            raise DeployError('runtime backend database settings are incomplete')
        if RUNTIME_NETWORK not in backend['NetworkSettings']['Networks']:
            raise DeployError('runtime backend is not on the runtime network')
        # 운영 URL의 2초 socketTimeout은 DDL을 끊는다. 대기는 lock_wait_timeout과 컨테이너 시간 제한으로 묶는다.
        url = match.group(1) + '?connectTimeout=5000&socketTimeout=%d' % (MIGRATION_SECONDS * 1000)
        return {'image': backend['Config']['Image'], 'name': match.group(2),
                'env': {'DB_URL': url, 'DB_USERNAME': env['DB_USERNAME'], 'DB_PASSWORD': env['DB_PASSWORD']}}

    def history(self, name):
        rows = run('docker', 'exec', 'eolssu-runtime-mysql-1', 'sh', '-c',
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names -D "$1" -e "$2"',
                   'deploy', name, 'SELECT version, script, checksum, success FROM flyway_schema_history '
                   'WHERE version IS NOT NULL ORDER BY installed_rank')
        applied = {}
        for row in rows.splitlines():
            version, script, checksum, success = row.split('\t')
            if success != '1':
                raise DeployError('failed Flyway migration in history: ' + script)
            applied[int(version)] = (script, int(checksum))
        return applied

    def migration_plan(self, jar, name):
        """적용할 (버전, 파일) 목록. 이력과 후보가 어긋나거나 사람이 볼 변경이면 교체 전에 멈춘다."""
        if run('docker', 'ps', '-aq', '--filter', 'label=' + MIGRATION_LABEL):
            raise DeployError('an earlier migration container remains; inspect and remove it first')
        candidate = candidate_migrations(jar)
        applied = self.history(name)
        for version, (script, checksum) in applied.items():
            if candidate.get(version, ('',))[0] != script:
                raise DeployError('database has a migration the candidate lacks: ' + script)
            if flyway_checksum(candidate[version][1]) != checksum:
                raise DeployError('applied migration was edited in candidate: ' + script)
        pending = sorted(set(candidate) - set(applied))
        if applied and pending and pending[0] < max(applied):
            raise DeployError('pending migration is older than applied history: ' + candidate[pending[0]][0])
        for version in pending:
            script, body = candidate[version]
            approval = self.root / 'migration-approvals' / script
            if DESTRUCTIVE_SQL.search(sql_code(body.decode('utf-8'))) and not (
                    approval.is_file() and approval.read_text().strip() == hashlib.sha256(body).hexdigest()):
                raise DeployError(script + ' drops, renames or deletes data; approve its SHA-256 after manual review')
        return [(version, candidate[version][0]) for version in pending]

    def migrate(self, sha, jar, database, pending):
        container = 'eolssu-migrate-' + sha[:12]
        run('docker', 'run', '-d', '--pull', 'never', '--name', container, '--label', MIGRATION_LABEL,
            '--network', RUNTIME_NETWORK, '--memory', '512m', '--cpus', '1', '--pids-limit', '128',
            '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid,size=64m',
            '--mount', f'type=bind,src={jar},dst=/app/candidate.jar,readonly',
            '-e', 'DB_URL', '-e', 'DB_USERNAME', '-e', 'DB_PASSWORD', '--entrypoint', 'java', database['image'],
            '-Xmx256m', '-jar', '/app/candidate.jar', '--spring.profiles.active=db',
            '--spring.main.web-application-type=none', '--spring.flyway.target=%d' % pending[-1][0],
            '--eolssu.scheduling.enabled=false', '--eolssu.auth.mail-mode=unavailable',
            '--spring.datasource.hikari.connection-timeout=5000',
            "--spring.datasource.hikari.connection-init-sql=SET time_zone = '+00:00', lock_wait_timeout = 5, innodb_lock_wait_timeout = 5",
            env={**os.environ, **database['env']})
        try:
            exit_code = run('docker', 'wait', container, timeout=MIGRATION_SECONDS)
        except DeployError:
            # 끊으면 MySQL DDL이 반쯤 남을 수 있다. 강제 종료하지 않고 남은 컨테이너가 다음 배포를 막게 둔다.
            raise DeployError('migration did not finish in time; inspect container ' + container) from None
        log = jar.parent / 'migration.log'
        atomic(log, run('docker', 'logs', container, timeout=30) + '\n')
        if exit_code != '0':
            raise DeployError('migration failed; %s and %s kept for inspection' % (container, log))
        run('docker', 'rm', container)
        applied = self.history(database['name'])
        if max(applied) != pending[-1][0] or any(version not in applied for version, _ in pending):
            raise DeployError('migration history does not match the candidate after apply')

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
            with phase('Select release'):
                self.select(jar)
            with phase('Recreate runtime service'):
                self.activate()
            with phase('Verify runtime health and mounted release'):
                self.healthy()
            with phase('Verify protected services unchanged'):
                self.unchanged(protected)
        except Exception:
            with phase('Rollback previous release'):
                self.recover()
            raise DeployError('deployment failed; previous release restored') from None
        saved = json.loads(self.state.read_text())
        saved['status'] = 'deployed'
        self.save(saved)

    def check_order(self, sha):
        check_release_order(sha, self.current_jar().parent.name)

    def deploy(self, sha):
        validate_sha(sha)
        with phase('Recover interrupted deployment'):
            self.recover()
        with phase('Check deployment order'):
            self.check_order(sha)
        with phase('Validate candidate image'):
            image = json.loads(run('docker', 'image', 'inspect', 'eolssu-backend:ci-' + sha))[0]
            if image['Config'].get('Labels', {}).get('org.opencontainers.image.revision') != sha:
                raise DeployError('image revision label does not match candidate')
        with phase('Check identical release'):
            if self.already_deployed(sha, image['Id']):
                return
        with phase('Snapshot protected services'):
            protected = self.protected()
        name = 'eolssu-stage-' + sha[:12]
        # Fixed label + deployment lock make leftovers from a killed process safe to reclaim.
        with phase('Prepare candidate network'):
            for old in run('docker', 'ps', '-aq', '--filter', 'label=com.eolssu.stage=backend').split():
                run('docker', 'rm', '-f', old)
            for old in run('docker', 'network', 'ls', '-q', '--filter', 'label=com.eolssu.stage=backend').split():
                run('docker', 'network', 'rm', old)
            run('docker', 'network', 'create', '--internal', '--label', 'com.eolssu.stage=backend', name)
        try:
            with phase('Start candidate and check liveness'):
                run('docker', 'run', '-d', '--name', name, '--label', 'com.eolssu.stage=backend',
                    '--network', name, '--env', 'SERVER_PORT=8080', image['Id'])
                address = json.loads(run('docker', 'inspect', name))[0]['NetworkSettings']['Networks'][name]['IPAddress']
                probe(candidate_health_url(address), 200, UP)
            with phase('Extract immutable JAR'):
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
            with phase('Recheck protected services and order'):
                self.unchanged(protected)
                self.check_order(sha)
            # 교체 직전, 시작 시 prechange 백업을 받은 같은 lock 안에서 적용한다. 실패하면 기존 앱이 그대로 돈다.
            with phase('Check database migrations'):
                database = self.database()
                pending = self.migration_plan(jar, database['name'])
            if pending:
                with phase('Apply database migrations'):
                    self.migrate(sha, jar, database, pending)
                    self.migrated = True
            self.promote(sha, jar, image['Id'], protected)
        finally:
            # Cleanup failure is a failed build; the deployment journal remains authoritative.
            with phase('Clean candidate resources'):
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
        with phase('Wait for shared deployment lock'):
            acquire_lock(lock)
        def interrupted(signum, frame):
            raise DeployError('deployment interrupted')
        signal.signal(signal.SIGTERM, interrupted)
        signal.signal(signal.SIGINT, interrupted)
        with phase('Pre-deployment backup'):
            run('/opt/eolssu-backup/backup.py', 'send', 'prechange', timeout=600)
        deployer = Deployer()
        deployer.deploy(sys.argv[1])
        with phase('Post-deployment backup'):
            # 스키마를 바꾼 배포는 설정과 함께 바뀐 DB도 남긴다.
            run('/opt/eolssu-backup/backup.py', 'send', 'postmigration' if deployer.migrated else 'postchange', timeout=600)


if __name__ == '__main__':
    try:
        main()
    except (DeployError, OSError, ValueError) as error:
        # Do not echo arbitrary exception messages (they may include a secret file's contents).
        print(json.dumps({'status': 'failed', 'reason': str(error) if isinstance(error, DeployError) else type(error).__name__}), file=sys.stderr)
        sys.exit(1)
