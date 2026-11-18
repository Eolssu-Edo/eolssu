"""Share one isolated stack; keep observers, transports and run owners per registered device."""
import asyncio
from contextlib import AsyncExitStack
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import threading
import time
import traceback
from types import SimpleNamespace
from uuid import uuid4
import zipfile

import httpx
import db_evidence
from live_observer import Observer, QA_PACKAGE, now
from execution_evidence import ExecutionEvidence
import scenarios

SOURCE = Path(__file__).resolve().parent
SIZES = {6, 150, 300, 500, 750, 1000}


def execute(args, *, content=None, timeout=20, env=None, binary=False):
    result = subprocess.run([str(arg) for arg in args], input=content, capture_output=True,
                            timeout=timeout, env=env, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
    if result.returncode:
        raise RuntimeError('QA command failed')
    return result.stdout if binary else result.stdout.decode('utf8', 'replace').strip()


def atomic_json(path, value):
    temporary = path.with_suffix('.next')
    temporary.write_text(json.dumps(value, ensure_ascii=False), encoding='utf8')
    temporary.replace(path)


class DeviceObserver(Observer):
    def __init__(self, runtime):
        super().__init__()
        self.runtime = runtime

    def start_device(self):
        cfg = self.runtime.config
        serial = self.runtime.device_serial()
        return self.start(self.runtime.work, cfg['adb'], cfg.get('adbPort', 5037), serial,
                          expected_identity=cfg['identity'], capture_frames=False, proxy_port=cfg.get('proxyPort', 18180))

    def _command(self, *args, timeout=8, binary=False):
        return self.runtime.adb(*args, timeout=timeout, binary=binary)

    def _package_loop(self):
        while not self._stop.is_set():
            try:
                connected = self._command('shell', 'getprop', 'ro.serialno') == self.runtime.config['identity']
                with self._lock:
                    self._device['connected'] = connected
                self._read_package_version()
            except (OSError, RuntimeError, subprocess.SubprocessError):
                with self._lock:
                    self._device['connected'] = False
            self._stop.wait(3)

    def _query(self, sql):
        return self.runtime.query(sql)

    def verify_database(self):
        self.runtime.verify_container('db')
        if self._query('SELECT DATABASE()') != 'eolssu_qa':
            raise RuntimeError('Owned database differs')


class Runtime:
    def __init__(self, config, directory):
        self.config = config
        self.devices = config.get('devices', {config['identity']: {'serial': config['serial'], 'device': config.get('device', {})}})
        if not 1 <= len(self.devices) <= 6 or config['identity'] not in self.devices or any(not re.fullmatch(r'[A-Za-z0-9_-]{4,80}', key) for key in self.devices):
            raise ValueError('Invalid registered devices')
        self.transport_lock = threading.Lock()
        self.transport_verified = 0
        self.directory = Path(directory).resolve()
        self.directory.mkdir(parents=True, exist_ok=True)
        self.work = self.directory.parent
        self.owner = config['runId']
        self.prefix = 'eolssu-qa936-' + self.owner[:8]
        self.lock = threading.RLock()
        self.cancel = threading.Event()
        self.observer = None
        self.observations = {}
        self.launches = set()
        self.prepare_lock = threading.Lock()
        self.audit = None
        self.logs = []
        self.state = 'preparing'
        self.mode = None
        self.controller = None
        self.scenario = None
        self.load = None
        self.scenario_identity = None
        self.load_identity = None
        self.history_path = self.work / 'history.json'
        self.history = json.loads(self.history_path.read_text(encoding='utf8')) if self.history_path.exists() else []
        self.input_mode = 'microphone'
        self.credentials = []
        self.environment = {}
        self.threads = []
        self.stack_ready = False
        self.preserve_existing = False
        self.lease = None
        self.last_use = time.monotonic()
        self.last_error = None

    def docker(self, *args, **kwargs):
        return execute(['docker', *args], **kwargs)

    def name(self, role):
        return self.prefix + '-' + role

    def verify_container(self, role):
        value = self.docker('inspect', '--format', '{{index .Config.Labels "eolssu.qa.owner"}}', self.name(role))
        if value != self.owner:
            raise RuntimeError('Container ownership differs')

    def query(self, sql, database=True):
        self.verify_container('db')
        args = ['exec', '-i', self.name('db'), 'mysql', '--defaults-extra-file=/tmp/qa-client.cnf', '-N', '-B']
        if database:
            args.append('eolssu_qa')
        return self.docker(*args, content=sql.encode('utf8'))

    def wait(self, predicate, seconds=90):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                if predicate():
                    return
            except (RuntimeError, OSError, httpx.HTTPError):
                pass
            if self.cancel.wait(.5):
                raise RuntimeError('Cancelled')
        raise RuntimeError('QA startup timed out')

    def capture_log(self, role, filename):
        stream = (self.directory / filename).open('ab')
        process = subprocess.Popen(['docker', 'logs', '-f', '--since', '1s', self.name(role)], stdout=stream, stderr=subprocess.STDOUT,
                                   creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        self.logs.append((process, stream))

    def prepare(self):
        try:
            if self.adopt_stack():
                return
            db_password = secrets.token_hex(24)
            self.docker('network', 'create', '--label', 'eolssu.qa.owner=' + self.owner, self.prefix)
            db_env = self.work / 'db.env'
            db_env.write_text(f'MYSQL_ROOT_PASSWORD={db_password}\nMYSQL_DATABASE=eolssu_qa\nMYSQL_USER=eolssu_qa\nMYSQL_PASSWORD={db_password}\n', encoding='utf8')
            self.docker('run', '-d', '--name', self.name('db'), '--network', self.prefix,
                        '--label', 'eolssu.qa.owner=' + self.owner, '--memory', '768m', '--env-file', db_env,
                        'mysql:8.4', '--max-connections=180', '--innodb-buffer-pool-size=128M', timeout=90)
            client_config = f'[client]\nuser=root\npassword={db_password}\ndefault-character-set=utf8mb4\n'
            self.wait(lambda: self.docker('exec', '-i', self.name('db'), 'sh', '-c',
                      'umask 077; cat > /tmp/qa-client.cnf', content=client_config.encode()) == '', 20)
            self.wait(lambda: self.query('SELECT 1', database=False) == '1')
            mounts = ['--mount', f'type=bind,source={SOURCE},target=/qa,readonly',
                      '--mount', f'type=bind,source={self.directory},target=/runtime']
            self.docker('run', '-d', '--name', self.name('ai'), '--network', self.prefix,
                        '--label', 'eolssu.qa.owner=' + self.owner, '--memory', '3072m', '--cpus', '2',
                        '-e', 'PYTHONPATH=/qa', '-e', 'OPENBLAS_NUM_THREADS=1', '-e', 'NUMBA_NUM_THREADS=1',
                        *mounts, self.config['aiImage'], 'uvicorn', 'qa_ai:app', '--host', '0.0.0.0', '--port', '8000')
            self.capture_log('ai', 'ai-18201.log')
            self.environment = {
                'SPRING_PROFILES_ACTIVE': 'db', 'SERVER_ADDRESS': '0.0.0.0', 'SERVER_PORT': '18181',
                'DB_URL': f'jdbc:mysql://{self.name("db")}:3306/eolssu_qa?allowPublicKeyRetrieval=true&sslMode=DISABLED',
                'DB_USERNAME': 'eolssu_qa', 'DB_PASSWORD': db_password,
                'AUTH_CODE_HMAC_SECRET': secrets.token_hex(32), 'AUTH_JWT_SECRET': secrets.token_hex(32),
                'AUTH_MAIL_MODE': 'memory', 'AUTH_COOKIE_SECURE': 'false',
                'RECORDING_STORAGE_TYPE': 'local', 'RECORDING_STORAGE_LOCAL_DIRECTORY': '/runtime/recordings',
                'RECORDING_STORAGE_LOCAL_READ_BASE_URL': f'http://{self.name("backend")}:18181/api/recordings/read',
                'RECORDING_STORAGE_LOCAL_SIGNING_SECRET': secrets.token_hex(32),
                'ANALYSIS_AI_BASE_URL': f'http://{self.name("ai")}:8000',
                'EOLSSU_ANALYSIS_WORKERS': '1', 'EOLSSU_ANALYSIS_QUEUE_CAPACITY': '1024',
                'EOLSSU_ANALYSIS_TIME_LIMIT': '60',
            }
            env_path = self.work / 'backend.env'
            env_path.write_text(''.join(f'{key}={value}\n' for key, value in self.environment.items()), encoding='utf8')
            self.docker('run', '-d', '--name', self.name('backend'), '--network', self.prefix,
                        '--label', 'eolssu.qa.owner=' + self.owner, '--memory', '1280m', '--env-file', env_path,
                        '-p', '127.0.0.1:18181:18181', '--mount', f'type=bind,source={self.config["jar"]},target=/app/backend.jar,readonly',
                        '--mount', f'type=bind,source={self.directory},target=/runtime', self.config['backendImage'],
                        'java', '-Xmx768m', '-XX:ActiveProcessorCount=2', '-Deolssu.qa.live=true',
                        '-Djava.io.tmpdir=/runtime', '-jar', '/app/backend.jar')
            self.capture_log('backend', 'backend.log')
            self.wait(lambda: httpx.get('http://127.0.0.1:18181/api/terms', timeout=2, trust_env=False).status_code == 200, 120)
            self.seed_accounts(1000)
            self.stack_ready = True
            self.state = 'ready'
            self.lease = threading.Timer(1800, self.expire)
            self.lease.daemon = True
            self.lease.start()
        except Exception as error:
            self.state = 'failed'
            atomic_json(self.work / 'startup-failure.json', {'type': type(error).__name__, 'at': now()})

    def adopt_stack(self):
        existing = self.docker('ps', '-a', '--filter', 'label=eolssu.qa.owner=' + self.owner, '--format', '{{.Names}}').splitlines()
        if not existing:
            return False
        self.preserve_existing = True
        for role in ('backend', 'ai', 'db'):
            self.verify_container(role)
            if self.docker('inspect', '--format', '{{.State.Running}}', self.name(role)) != 'true':
                raise RuntimeError('Owned QA stack is not running; data was preserved')
        if self.docker('network', 'inspect', '--format', '{{index .Labels "eolssu.qa.owner"}}', self.prefix) != self.owner:
            raise RuntimeError('Network ownership differs')
        credentials = json.loads((self.work / 'credentials.json').read_text(encoding='utf8'))
        if not credentials or any(not re.fullmatch(r'qa936-\d{4}@eolssu\.test', item.get('email', '')) or not isinstance(item.get('password'), str) for item in credentials):
            raise RuntimeError('Owned QA credentials missing')
        if self.query('SELECT DATABASE()') != 'eolssu_qa':
            raise RuntimeError('Owned database differs')
        self.environment = dict(line.split('=', 1) for line in (self.work / 'backend.env').read_text(encoding='utf8').splitlines() if '=' in line)
        self.credentials = credentials
        self.capture_log('ai', 'ai-18201.log')
        self.capture_log('backend', 'backend.log')
        self.stack_ready, self.state = True, 'ready'
        self.preserve_existing = False
        self.lease = threading.Timer(1800, self.expire)
        self.lease.daemon = True
        self.lease.start()
        return True

    def seed_accounts(self, count):
        library = self.work / 'hash-libs'
        library.mkdir(exist_ok=True)
        with zipfile.ZipFile(self.config['jar']) as jar:
            for name in jar.namelist():
                if name.startswith('BOOT-INF/lib/') and any(Path(name).name.startswith(prefix) for prefix in ('spring-security-crypto-', 'bcprov-', 'commons-logging-', 'spring-core-')):
                    (library / Path(name).name).write_bytes(jar.read(name))
        password = 'Aa1!' + secrets.token_urlsafe(24)
        secret_file = self.work / 'hash-password'
        secret_file.write_text(password, encoding='utf8')
        try:
            hashed = execute(['java', '-cp', str(library / '*'), SOURCE / 'HashQaPassword.java', secret_file])
        finally:
            secret_file.unlink(missing_ok=True)
        if not hashed.startswith('{argon2}$argon2id$') or "'" in hashed:
            raise RuntimeError('Password hashing failed')
        self.credentials = [{'email': f'qa936-{index:04}@eolssu.test', 'password': password} for index in range(1, count + 1)]
        rows = ','.join(f"('{row['email']}','{hashed}','QA{index:04}',1,'HANGUL','ACTIVE')" for index, row in enumerate(self.credentials, 1))
        self.query('INSERT INTO users(email,password_hash,nickname,instrument_id,notation_mode,status) VALUES ' + rows + ';')
        atomic_json(self.work / 'credentials.json', self.credentials)

    def device_serial(self):
        cfg = self.config
        prefix = [cfg['adb'], '-P', cfg.get('adbPort', 5037)]
        with self.transport_lock:
            if time.monotonic() - self.transport_verified < 1:
                return cfg['serial']
            listing = execute([*prefix, 'devices'], timeout=4)
            candidates = [row.split()[0] for row in listing.splitlines()[1:] if len(row.split()) >= 2 and row.split()[1] == 'device']
            candidates.sort(key=lambda value: (value != cfg['identity'], value != cfg['serial']))
            for serial in candidates:
                try:
                    if execute([*prefix, '-s', serial, 'shell', 'getprop', 'ro.serialno'], timeout=3) != cfg['identity']:
                        continue
                    cfg['serial'] = serial
                    self.transport_verified = time.monotonic()
                    if self.observer:
                        self.observer._adb = [str(value) for value in [*prefix, '-s', serial]]
                    return serial
                except (RuntimeError, OSError, subprocess.SubprocessError):
                    continue
            raise RuntimeError('Registered Android is disconnected')

    def adb(self, *args, content=None, timeout=20, binary=False):
        cfg = self.config
        serial = self.device_serial()
        return execute([cfg['adb'], '-P', cfg.get('adbPort', 5037), '-s', serial, *args], content=content, timeout=timeout, binary=binary)

    def observe(self):
        description = self.adb('shell', 'dumpsys', 'package', QA_PACKAGE)
        scenarios.require(re.search(r'(?m)^\s*versionName=[A-Za-z0-9._+-]+-qa283-live[ \t\r]*$', description),
                          '관측용 QA APK가 필요합니다. 최신 일반 QA APK와 구분해 관측용 빌드를 설치하세요.')
        self.observer = DeviceObserver(self)
        self.observer.start_device()
        if self.cancel.is_set():
            raise RuntimeError('Cancelled')
        self.adb('reverse', 'tcp:18180', 'tcp:' + str(self.config.get('proxyPort', 18180)))
        self.adb('shell', 'am', 'force-stop', QA_PACKAGE)
        self.fixture('microphone')
        if self.cancel.is_set():
            raise RuntimeError('Cancelled')
        self.adb('shell', 'am', 'start', '-n', QA_PACKAGE + '/com.ssafy.eolssu.MainActivity')

    def fixture(self, mode):
        if mode == 'synthetic':
            body = (SOURCE / 'fixtures/arirang-input.pcm').read_bytes()
            if not 0 < len(body) <= 1_920_000 or len(body) % 2:
                raise ValueError('Invalid QA PCM')
            self.adb('shell', 'run-as', QA_PACKAGE, 'sh', '-c',
                     "'mkdir -p files; cat > files/qa-live-input.pcm.next; mv files/qa-live-input.pcm.next files/qa-live-input.pcm'", content=body)
            if self.observer:
                self.observer.emit('observer', 'fixture_applied', target='recording_input', bytes=len(body))
        elif mode == 'microphone':
            self.adb('shell', 'run-as', QA_PACKAGE, 'rm', '-f', 'files/qa-live-input.pcm')
        else:
            raise ValueError('Invalid input mode')
        self.input_mode = mode

    def account(self, field):
        if not self.observer or self.mode != 'observe' or field not in {'email', 'password'}:
            raise ValueError('Observation required')
        self.observer.input('text', text=self.credentials[0][field], replace=True)

    def save_history(self, name, status, started, result):
        run = {'id': str(uuid4()), 'name': name, 'status': status, 'startedAt': started, 'finishedAt': now(), 'environmentId': self.owner,
               'device': self.config.get('device', {}).get('name'), 'deviceIdentity': self.config['identity'], 'result': result}
        self.history.append(run)
        self.history = self.history[-50:]
        atomic_json(self.history_path, self.history)

    def command(self, body, identity=None):
        action, caller = body.get('action'), body.get('sessionId')
        identity = identity or self.config['identity']
        if identity not in self.devices:
            raise PermissionError('Registered device required')
        if not isinstance(caller, str) or not re.fullmatch(r'[0-9a-f-]{36}', caller):
            raise ValueError('QA session required')
        if action == 'launch':
            with self.lock:
                if self.mode in {'scenario', 'run'} or self.state == 'preparing' or identity in self.launches:
                    raise BlockingIOError('QA environment busy')
                self.launches.add(identity)
            try:
                context = Observation(self, identity, caller, 18280 + sorted(self.devices).index(identity))
                description = context.adb('shell', 'dumpsys', 'package', QA_PACKAGE)
                if not re.search(r'\bversionCode=\d+', description):
                    raise RuntimeError('QA application not installed')
                output = context.adb('shell', 'am', 'start', '-n', QA_PACKAGE + '/com.ssafy.eolssu.MainActivity', timeout=12)
                if re.search(r'Error:|Exception|does not exist', output, re.I):
                    raise RuntimeError('QA application did not start')
                return {'started': True}
            finally:
                with self.lock:
                    self.launches.discard(identity)
                    self.last_use = time.monotonic()
        with self.lock:
            context = self.observations.get(identity)
            if action == 'start':
                if self.mode in {'scenario', 'run'} or self.state == 'preparing' or identity in self.launches:
                    raise BlockingIOError('QA environment busy')
                if context and context.state in {'starting', 'observing', 'stopping'}:
                    if context.controller != caller:
                        raise PermissionError('Observation belongs to another QA session')
                    if context.state != 'observing':
                        raise BlockingIOError('Device is already starting')
                    context.state, context.error = 'starting', None
                else:
                    context = Observation(self, identity, caller, 18280 + sorted(self.devices).index(identity))
                self.observations[identity] = context
                self.mode, self.state = 'observe', 'observing'
                self.last_use = time.monotonic()
                thread = threading.Thread(target=self.run_observation, args=(context,), daemon=True)
                self.threads.append(thread)
                thread.start()
                return {'accepted': True}
            if action in {'stop', 'cancel'}:
                if action == 'stop' and context:
                    if context.controller != caller:
                        raise PermissionError('Run belongs to another QA session')
                    if context.state not in {'starting', 'observing', 'stopping'}:
                        return {'accepted': True}
                    starting = context.state in {'starting', 'stopping'}
                    context.cancel.set()
                    if context.observer:
                        context.observer.stop()
                    context.state = 'stopping' if starting else 'stopped'
                    self.sync_observations()
                    return {'accepted': True}
                if self.mode == 'observe' and (not context or action == 'cancel'):
                    raise PermissionError('Observation belongs to another QA session')
                if self.controller and self.controller != caller:
                    raise PermissionError('Run belongs to another QA session')
                self.cancel.set()
                if self.mode == 'observe':
                    self.observer.stop()
                    self.observer = None
                    self.mode = None
                    self.state = 'ready'
                    self.last_use = time.monotonic()
                return {'accepted': True}
            if self.state == 'preparing':
                raise RuntimeError('QA environment unavailable')
            if action in {'fixture', 'email', 'password'}:
                if context:
                    if context.state != 'observing' or context.controller != caller:
                        raise PermissionError('Observation belongs to another QA session')
                    context.fixture(body.get('input')) if action == 'fixture' else context.account(action)
                    return {'accepted': True}
                if self.mode != 'observe' or self.controller != caller or identity != self.config['identity']:
                    raise PermissionError('Observation belongs to another QA session')
                if action == 'fixture':
                    self.fixture(body.get('input'))
                else:
                    self.account(action)
                return {'accepted': True}
            if self.mode or self.launches:
                raise BlockingIOError('QA environment busy')
            if action not in {'start', 'scenario', 'run'}:
                raise ValueError('Unsupported action')
            if action == 'scenario' and body.get('scenario') not in scenarios.SCENARIOS:
                raise ValueError('Unsupported scenario')
            if action == 'run' and (type(body.get('size')) is not int or body['size'] not in SIZES):
                raise ValueError('Unsupported size')
            if action == 'scenario':
                (self.work / 'scenario-progress.json').unlink(missing_ok=True)
                self.scenario_identity = identity
            if action == 'run':
                self.load_identity = identity
                self.load = {'status': 'running', 'total': body['size'], 'success': 0, 'failed': 0, 'within5': 0, 'results': [], 'startedAt': now()}
            if action in {'scenario', 'run'}:
                self.audit = None
            self.config.update(identity=identity, **self.devices[identity])
            self.transport_verified = 0
            self.cancel.clear()
            self.controller = caller
            self.last_error = None
            self.mode = 'observe' if action == 'start' else action
            self.state = 'starting' if action == 'start' else 'running'
            if action == 'scenario':
                self.scenario = {'name': body['scenario'], 'status': 'running', 'checks': []}
            thread = threading.Thread(target=self.run_command, args=(dict(body),), daemon=True)
            self.threads.append(thread)
            thread.start()
            return {'accepted': True}

    def sync_observations(self):
        if self.mode in {'scenario', 'run'}:
            return
        active = any(context.state in {'starting', 'observing', 'stopping'} for context in self.observations.values())
        self.mode = 'observe' if active else None
        self.state = 'observing' if active else 'ready'
        self.last_use = time.monotonic()

    def run_observation(self, context):
        try:
            with self.prepare_lock:
                if not self.stack_ready:
                    self.prepare()
                if not self.stack_ready:
                    raise RuntimeError('QA environment unavailable')
            if context.cancel.is_set():
                return
            if context.observer:
                context.adb('shell', 'am', 'force-stop', QA_PACKAGE)
                context.fixture('microphone')
                if context.cancel.is_set():
                    return
                context.adb('shell', 'am', 'start', '-n', QA_PACKAGE + '/com.ssafy.eolssu.MainActivity')
            else:
                context.observe()
            with self.lock:
                if context.cancel.is_set():
                    context.observer.stop()
                    context.state = 'stopped'
                else:
                    context.state = 'observing'
        except Exception as error:
            if context.observer:
                context.observer.stop()
            context.error = scenarios.safe_failure(error)
            context.state = 'stopped' if context.cancel.is_set() else 'failed'
        finally:
            with self.lock:
                if context.cancel.is_set():
                    context.state = 'stopped'
                self.sync_observations()

    def run_command(self, body):
        began, action = now(), body['action']
        if action in {'scenario', 'run'}:
            self.audit = None
        try:
            if not self.stack_ready:
                self.cancel.clear()
                self.state = 'preparing'
                self.prepare()
                if not self.stack_ready:
                    raise RuntimeError('QA startup failed')
            if action == 'start':
                self.observe()
                self.state = 'observing'
                return
            self.audit = ExecutionEvidence(self)
            if action == 'scenario':
                result = asyncio.run(self.run_scenario(body['scenario']))
                self.save_history(body['scenario'], 'pass', began, result)
            else:
                result = asyncio.run(self.run_load(body['size']))
                self.save_history(f"동시 제출 {body['size']}", result['status'], began, result)
        except Exception as error:
            result = {'error': scenarios.safe_failure(error)}
            if self.audit and action in {'scenario', 'run'}:
                result['logs'] = self.audit.snapshot()
            atomic_json(self.work / 'execution-failure.json', {'type': type(error).__name__, 'at': now(),
                        'frames': [{'file': Path(frame.filename).name, 'line': frame.lineno, 'function': frame.name}
                                   for frame in traceback.extract_tb(error.__traceback__)]})
            self.last_error = result['error']
            if action == 'scenario':
                self.scenario = {'name': body['scenario'], 'status': 'fail', 'checks': [{'name': result['error'], 'passed': False}], 'logs': result.get('logs', [])}
            elif action == 'run' and self.load:
                self.load.update(status='cancelled' if self.cancel.is_set() else 'fail', logs=result.get('logs', []))
            self.save_history(body.get('scenario', action), 'cancelled' if self.cancel.is_set() else 'failed', began, result)
            if self.observer:
                self.observer.stop()
                self.observer = None
        self.mode = None
        self.state = 'ready'
        self.last_use = time.monotonic()

    async def run_scenario(self, name):
        progress_path = self.work / 'scenario-progress.json'
        progress = scenarios.Progress(name, progress_path)
        self.scenario = {'name': name, 'status': 'running', 'checks': []}
        for path in (self.directory / 'retry-first-attempt.json', self.directory / 'retry-first-attempt.json.consumed'):
            path.unlink(missing_ok=True)
        gate = self.directory / 'retry-first-attempt.json'
        os.environ['EOLSSU_QA_RETRY_GATE'] = str(gate)
        evidence = SimpleNamespace(query=self.query, stage_database=lambda ids: self.audit.database(db_evidence.stage_database(ids, self.query)))
        def checked_id(value):
            scenarios.require(isinstance(value, str) and re.fullmatch(r'[1-9][0-9]{0,18}', value), 'Invalid API ID')
            return value
        async def request_id(request):
            if self.cancel.is_set():
                raise RuntimeError('Cancelled')
            await self.audit.request(request)
        traced_http = SimpleNamespace(AsyncClient=lambda **kwargs: httpx.AsyncClient(event_hooks={'request': [request_id], 'response': [self.audit.response]}, **kwargs))
        async with AsyncExitStack() as stack:
            qa = scenarios.QA(stack, self.work, traced_http, SimpleNamespace(checked_id=checked_id), evidence, self.credentials[:2])
            result = await scenarios.SCENARIOS[name](qa, progress, (SOURCE / 'fixtures/arirang-baseline.m4a').read_bytes(), SOURCE / 'fixtures')
        result['logs'] = self.audit.snapshot()
        progress.finish('pass', result)
        self.scenario = {'name': name, 'status': 'pass', 'checks': [{'name': row['label'], 'status': row['status'], 'passed': row['status'] == 'pass', 'value': row['detail']} for row in progress.document['steps']], 'result': result, 'logs': result['logs']}
        return progress.document

    async def run_load(self, size):
        started = now()
        self.load = {'status': 'preparing', 'startedAt': started, 'total': size, 'success': 0, 'failed': 0, 'within5': 0,
                     'measurement': '업로드 HTTP 송신 시작부터 본인 평가 조회와 DB 저장 확인까지. 분모는 전체 제출 대상이며 실패도 포함합니다. 서버 채점 마감 60초.'}
        results = []
        gate = asyncio.Event()
        audio = (SOURCE / 'fixtures/arirang-full.m4a').read_bytes()
        async def request_id(request):
            if self.cancel.is_set():
                raise RuntimeError('Cancelled')
            await self.audit.request(request)
        async def submit(index):
            row = {'label': index + 1, 'status': 'preparing'}
            results.append(row)
            try:
                async with httpx.AsyncClient(base_url='http://127.0.0.1:18181', timeout=20, trust_env=False,
                                             event_hooks={'request': [request_id], 'response': [self.audit.response]}) as client:
                    login = scenarios.details(await client.post('/api/auth/sign-in', json=self.credentials[index]), 200)
                    song = scenarios.details(await client.get('/api/songs/1'), 200)
                    session = scenarios.details(await client.post('/api/practice-sessions', headers={'Idempotency-Key': str(uuid4())}, json={
                        'songId': '1', 'bpm': 96, 'bpmUnit': 'DOTTED_QUARTER', 'startNoteIdx': 0,
                        'endNoteIdx': song['noteCount'] - 1, 'expectedScoreVersion': song['scoreVersion'],
                    }), 201)
                    sid = session['sessionId']
                    row.update(status='ready', sessionId=sid)
                    await gate.wait()
                    if self.cancel.is_set():
                        raise RuntimeError('Cancelled')
                    began = time.monotonic()
                    response = await client.post(f'/api/practice-sessions/{sid}/recording',
                        headers={'X-Evaluation-Timing-Id': str(uuid4())}, files={'audio': ('practice.m4a', audio, 'audio/mp4')}, timeout=30)
                    row['uploadHttpStatus'] = response.status_code
                    scenarios.details(response, 202)
                    row['status'] = 'pending'
                    deadline = time.monotonic() + 75
                    while time.monotonic() < deadline:
                        state = scenarios.details(await client.get(f'/api/practice-sessions/{sid}', timeout=10), 200)
                        if state['status'] == 'FAILED':
                            raise RuntimeError('Analysis failed')
                        if state['status'] == 'DONE':
                            result = scenarios.details(await client.get(f'/api/practice-sessions/{sid}/evaluation'), 200)
                            saved = self.audit.database(db_evidence.stage_database({sid}, self.query))['sessions'].get(sid, {})
                            scenarios.require(saved.get('status') == 'DONE' and saved.get('evaluations') == 1
                                              and saved.get('notes') == song['noteCount']
                                              and result['evaluationId'] is not None, 'Stored evaluation differs')
                            elapsed = round((time.monotonic() - began) * 1000, 3)
                            row.update(status='verified', evaluationId=result['evaluationId'], elapsedMs=elapsed)
                            self.load['success'] += 1
                            self.load['within5'] += int(elapsed <= 5000)
                            return
                        await asyncio.sleep(.6)
                    raise RuntimeError('Result deadline exceeded')
            except Exception:
                row['status'] = 'cancelled' if self.cancel.is_set() else 'failed'
                self.load['failed'] += 1
        tasks = [asyncio.create_task(submit(index)) for index in range(size)]
        # Release once every slot either has an owned session or has failed preparation.
        while any(row['status'] == 'preparing' for row in results) or len(results) < size:
            await asyncio.sleep(.1)
        self.load['status'] = 'running'
        gate.set()
        await asyncio.gather(*tasks)
        self.load.update(status='cancelled' if self.cancel.is_set() else 'pass' if self.load['success'] == size else 'fail',
                         finishedAt=now(), results=results, logs=self.audit.snapshot())
        return dict(self.load)

    def status(self, identity=None):
        with self.lock:
            identity = identity or self.config['identity']
            if identity not in self.devices:
                raise PermissionError('Registered device required')
            context = self.observations.get(identity)
            observation = context.observer.status() if context and context.observer else self.observer.status() if self.observer and identity == self.config['identity'] else {}
            selected = bool(context and context.state in {'starting', 'observing'} or self.observer and identity == self.config['identity']) if self.mode == 'observe' else identity == self.config['identity']
            if self.mode == 'scenario':
                try:
                    progress = json.loads((self.work / 'scenario-progress.json').read_text(encoding='utf8'))
                    self.scenario = {'name': progress['scenario'], 'status': progress['status'], 'checks': [{'name': row['label'], 'status': row['status'], 'passed': row['status'] == 'pass', 'value': row['detail']} for row in progress['steps']], 'result': progress['result']}
                except (OSError, ValueError, KeyError):
                    pass
            if self.audit and self.mode in {'scenario', 'run'}:
                target = self.scenario if self.mode == 'scenario' else self.load
                if target is not None:
                    target['logs'] = self.audit.snapshot()
            state = (context.state if context else 'ready') if self.mode == 'observe' else self.state
            history = [run for run in self.history if run.get('deviceIdentity') == identity or not run.get('deviceIdentity') and run.get('device') == self.devices[identity].get('device', {}).get('name')]
            return {**observation, 'state': state, 'mode': self.mode or ('launch' if self.launches else None),
                    'observingSelected': selected,
                    'api': 'http://127.0.0.1:18180', 'lastEventAt': observation.get('lastEventAt'),
                    'device': observation.get('device', self.devices[identity].get('device', {})),
                    'events': observation.get('events', []),
                    'activeDevice': self.devices[identity].get('device', {}).get('name') if selected and self.mode else None,
                    'message': context.error if context and self.mode not in {'scenario', 'run'} else self.last_error if selected else None,
                    'inputMode': context.input_mode if context else self.input_mode,
                    'scenario': self.scenario if self.scenario_identity in {None, identity} else None,
                    'load': self.load if self.load_identity in {None, identity} else None,
                    'history': history}

    def close(self):
        self.cancel.set()
        self.stack_ready = False
        if self.lease:
            self.lease.cancel()
        if self.observer:
            self.observer.stop()
        for context in self.observations.values():
            context.cancel.set()
            if context.observer:
                context.observer.stop()
            context.state = 'stopped'
        for process, stream in self.logs:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)
            stream.close()
        self.logs.clear()
        if self.preserve_existing:
            return
        for role in ('backend', 'ai', 'db'):
            try:
                self.verify_container(role)
                self.docker('rm', '-f', '-v', self.name(role))
            except RuntimeError:
                pass
        try:
            owner = self.docker('network', 'inspect', '--format', '{{index .Labels "eolssu.qa.owner"}}', self.prefix)
            if owner == self.owner:
                self.docker('network', 'rm', self.prefix)
        except RuntimeError:
            pass
        self.credentials.clear()
        for name in ('db.env', 'backend.env', 'credentials.json', 'hash-password'):
            (self.work / name).unlink(missing_ok=True)
        recordings = self.directory / 'recordings'
        if recordings.is_dir() and recordings.resolve().parent == self.directory:
            shutil.rmtree(recordings)

    def expire(self):
        with self.lock:
            remaining = 1800 - (time.monotonic() - self.last_use)
            if self.mode or self.launches or remaining > 0:
                self.lease = threading.Timer(max(30, remaining), self.expire)
                self.lease.daemon = True
                self.lease.start()
                return
            self.close()
            self.state = 'stopped'
            self.mode = None


class Observation:
    """Device-local context. Container ownership and database access stay with Runtime."""
    def __init__(self, runtime, identity, controller, proxy_port):
        self.runtime = runtime
        self.config = {**runtime.config, **runtime.devices[identity], 'identity': identity, 'proxyPort': proxy_port}
        self.work = runtime.work
        self.controller = controller
        self.state = 'starting'
        self.mode = 'observe'
        self.error = None
        self.observer = None
        self.input_mode = 'microphone'
        self.transport_lock = threading.Lock()
        self.transport_verified = 0
        self.cancel = threading.Event()

    @property
    def credentials(self):
        return self.runtime.credentials

    def query(self, *args, **kwargs):
        return self.runtime.query(*args, **kwargs)

    def verify_container(self, role):
        return self.runtime.verify_container(role)

    def device_serial(self):
        return Runtime.device_serial(self)

    def adb(self, *args, **kwargs):
        return Runtime.adb(self, *args, **kwargs)

    def observe(self):
        return Runtime.observe(self)

    def fixture(self, mode):
        return Runtime.fixture(self, mode)

    def account(self, field):
        return Runtime.account(self, field)
