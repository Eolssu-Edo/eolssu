#!/usr/bin/python3 -I
"""고정 경로·전용 SSH 수신·암호화·격리 복원. 비밀값은 출력하지 않는다."""
import contextlib
import fcntl
import hashlib
import gzip
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta

STATE = Path('/var/lib/eolssu-backup')
STORE = Path('/var/lib/eolssu-backups')
KEYS = Path('/var/lib/eolssu-backup-keys')
KINDS = ('db', 'config', 'jenkins', 'grafana', 'controller-key')
RUN = re.compile(r'\d{8}T\d{6}Z-[a-f0-9]{12}')
MYSQL = 'eolssu-runtime-mysql-1'
DUMP = ['--default-character-set=utf8mb4', '--single-transaction', '--quick', '--skip-extended-insert',
        '--complete-insert', '--hex-blob', '--order-by-primary', '--skip-comments', '--skip-dump-date',
        '--skip-add-locks', '--skip-disable-keys', '--skip-lock-tables', '--no-tablespaces', '--set-gtid-purged=OFF']

def now():
    return datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')

def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for b in iter(lambda: f.read(1048576), b''):
            h.update(b)
    return h.hexdigest()

def command(args, data=None, env=None, timeout=600):
    r = subprocess.run(args, input=data, capture_output=True, env=env, timeout=timeout)
    if r.returncode:
        sql_error = re.search(rb'ERROR (\d+) \(([A-Z0-9]+)\)', r.stderr)
        detail = ' sql_error=' + sql_error[1].decode() if sql_error else ''
        raise RuntimeError('operation failed: ' + Path(args[0]).name + detail)
    return r.stdout

def atomic(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as f:
        temp = Path(f.name)
        f.write(json.dumps(value, sort_keys=True).encode() + b'\n'); f.flush(); os.fsync(f.fileno())
    os.chmod(temp, 0o600); os.replace(temp, path)
    fd = os.open(path.parent, os.O_RDONLY)
    try: os.fsync(fd)
    finally: os.close(fd)

@contextlib.contextmanager
def locked(path):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with path.open('a') as f:
        deadline = time.monotonic() + 120
        while True:
            try:
                fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB); break
            except BlockingIOError:
                if time.monotonic() > deadline: raise RuntimeError('lock timeout')
                time.sleep(0.2)
        yield

def data_fingerprint(sql, tables=()):
    rows = {n: [] for n in tables}; database = None
    for line in sql.splitlines():
        use = re.match(r'USE `([^`]+)`;', line)
        if use: database = use[1]
        table = re.match(r'CREATE TABLE `([^`]+)`', line)
        insert = re.match(r'INSERT INTO `([^`]+)`', line)
        if table and database: rows.setdefault(database + '.' + table[1], [])
        if insert and database: rows.setdefault(database + '.' + insert[1], []).append(line)
        view = re.match(r'(?:/\*!\d+ )?VIEW `([^`]+)`', line)
        if view and database: rows.pop(database + '.' + view[1], None)
    return {n: {'rows': len(v), 'data_sha256': hashlib.sha256('\n'.join(sorted(v)).encode()).hexdigest()}
            for n, v in sorted(rows.items())}

def safe_name(name):
    p = PurePosixPath(name)
    if not name or p.is_absolute() or '..' in p.parts or str(p) != name:
        raise ValueError('invalid archive path')
    return p

@contextlib.contextmanager
def checked_tar(path):
    # Bound gzip expansion and raw extension headers before tarfile can allocate PAX data.
    with tempfile.TemporaryFile() as raw:
        total = 0
        with gzip.open(path, 'rb') as source:
            for chunk in iter(lambda: source.read(1048576), b''):
                total += len(chunk)
                if total > 2 * 1024**3 + 16 * 1024**2: raise ValueError('expanded archive too large')
                raw.write(chunk)
        raw.seek(0); headers = 0
        while True:
            block = raw.read(512)
            if not block or block == b'\0' * 512: break
            headers += 1
            if len(block) != 512 or headers > 20000 or block[156:157] not in (b'0', b'\0', b'x'):
                raise ValueError('unsupported archive header')
            try: size = int(block[124:136].strip(b'\0 ') or b'0', 8)
            except ValueError: raise ValueError('invalid archive size') from None
            if size < 0 or size > 2 * 1024**3: raise ValueError('invalid archive size')
            if block[156:157] == b'x':
                if size > 65536: raise ValueError('oversized extended header')
                data = raw.read(size)
                for record in data.splitlines():
                    if b' ' not in record or record.split(b' ', 1)[1].split(b'=', 1)[0] not in (
                            b'path', b'mtime', b'atime', b'ctime', b'uid', b'gid', b'uname', b'gname', b'size'):
                        raise ValueError('unsupported extended metadata')
                raw.seek((-size) % 512, 1)
            else: raw.seek((size + 511) // 512 * 512, 1)
            if raw.tell() > total: raise ValueError('truncated archive')
        raw.seek(0)
        with tarfile.open(fileobj=raw, mode='r:') as tar: yield tar

def validate_bundle(path, kind, output=None):
    if kind not in KINDS: raise ValueError('invalid kind')
    with checked_tar(path) as tar:
        iterator = iter(tar); first = next(iterator, None)
        if first is None or first.name != 'bundle.json':
            raise ValueError('invalid manifest')
        if not first.isfile() or first.size > 8388608: raise ValueError('invalid manifest')
        manifest = json.load(tar.extractfile(first)); expected = manifest['files']; members = [first]
        if manifest.get('version') != 1 or manifest.get('kind') != kind: raise ValueError('kind mismatch')
        seen = set(); total = 0
        for m in iterator:
            if len(members) >= 10000: raise ValueError('too many archive members')
            members.append(m)
            safe_name(m.name)
            if m.name in seen or m.name not in expected or not m.isfile(): raise ValueError('unexpected member')
            total += m.size
            if m.size < 0 or total > 2 * 1024**3 or m.size != expected[m.name]['size']: raise ValueError('size mismatch')
            h = hashlib.sha256()
            with tar.extractfile(m) as f:
                for b in iter(lambda: f.read(1048576), b''): h.update(b)
            if h.hexdigest() != expected[m.name]['sha256']: raise ValueError('content mismatch')
            seen.add(m.name)
        if seen != set(expected): raise ValueError('incomplete bundle')
        if output is not None:
            if output.exists(): raise ValueError('restore destination exists')
            output.mkdir(mode=0o700, parents=True)
            for m in members[1:]:
                p = output.joinpath(*safe_name(m.name).parts); p.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                with tar.extractfile(m) as source, p.open('xb') as dest: shutil.copyfileobj(source, dest)
                os.chmod(p, expected[m.name]['mode'] & 0o777)
                if os.geteuid() == 0: os.chown(p, expected[m.name]['uid'], expected[m.name]['gid'])
    return manifest

def commit_backup(root, kind, run_id, incoming, expected_sha):
    if kind not in KINDS or not RUN.fullmatch(run_id) or not re.fullmatch('[a-f0-9]{64}', expected_sha):
        raise ValueError('invalid transfer identity')
    if incoming.is_symlink() or sha(incoming) != expected_sha: raise ValueError('checksum mismatch')
    directory = root / kind; directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    if directory.is_symlink(): raise ValueError('invalid storage directory')
    target = directory / (run_id + '.cms')
    try: os.link(incoming, target)
    except FileExistsError:
        if target.is_symlink() or sha(target) != expected_sha: raise ValueError('immutable backup conflict')
    os.chmod(target, 0o600); incoming.unlink()
    with target.open('rb') as f: os.fsync(f.fileno())
    return target

def mysql_query(query, container=MYSQL, password=None):
    if password is None:
        return command(['docker', 'exec', container, 'sh', '-c',
                        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --raw --skip-column-names --execute="$1"', 'sh', query]).decode()
    return command(['docker', 'exec', '-e', 'MYSQL_PWD', container, 'mysql', '-uroot', '--protocol=TCP', '--host=127.0.0.1', '--batch', '--raw',
                    '--skip-column-names', '--execute', query], env=dict(os.environ, MYSQL_PWD=password)).decode()

def dump_db(container=MYSQL, password=None, data_only=False):
    flags = DUMP + (['--no-create-info'] if data_only else ['--routines', '--events', '--triggers'])
    if password is None:
        return command(['docker', 'exec', container, 'sh', '-c',
                        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot "$@"', 'sh',
                        *flags, '--databases', 'eolssu'])
    return command(['docker', 'exec', '-e', 'MYSQL_PWD', container, 'mysqldump', '-uroot', *flags,
                    '--databases', 'eolssu'], env=dict(os.environ, MYSQL_PWD=password))

def environment(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)

def pack_bundle(kind, directory):
    files = {}; generated = directory / 'generated'; generated.mkdir()
    manifest = {'version': 1, 'kind': kind, 'snapshot_at': now(), 'files': {}}
    def add(p, name=None, allowed_root=None):
        p = Path(p)
        if not p.is_file(): raise ValueError('required file missing')
        if p.is_symlink() and (allowed_root is None or not p.resolve().is_relative_to(allowed_root)):
            raise ValueError('source symlink outside allowed root')
        name = name or 'files/' + str(p).lstrip('/'); safe_name(name); files[name] = p
    def tree(p, allowed_root=None):
        for q in sorted(Path(p).rglob('*')):
            if q.is_file(): add(q, allowed_root=allowed_root)
    if kind == 'db':
        sql = dump_db(); p = generated / 'dump.sql'; p.write_bytes(sql); add(p, 'dump.sql')
        manifest['data'] = data_fingerprint(sql.decode())
        manifest['mysql_version'] = mysql_query('SELECT @@version').strip()
        manifest['schema_objects'] = mysql_query("SELECT TABLE_SCHEMA,TABLE_NAME,TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'eolssu' ORDER BY 1,2").splitlines()
        accounts = []
        for row in mysql_query("SELECT User,Host FROM mysql.user WHERE User NOT IN ('root') AND User NOT LIKE 'mysql.%' ORDER BY 1,2").splitlines():
            user, host = row.split('\t'); ident = "'" + user.replace("'", "''") + "'@'" + host.replace("'", "''") + "'"
            accounts.append(mysql_query('SHOW CREATE USER ' + ident).strip().split('\t', 1)[-1] + ';')
            accounts.extend(line + ';' for line in mysql_query('SHOW GRANTS FOR ' + ident).splitlines())
        p = generated / 'accounts.sql'; p.write_text('\n'.join(accounts) + '\n'); add(p, 'accounts.sql')
    elif kind == 'config':
        for p in ['/opt/eolssu/.env', '/opt/eolssu/deployment.json', '/opt/eolssu/frontend-deployment.json']: add(p)
        for p in ['/opt/eolssu/config', '/opt/eolssu/bin', '/etc/letsencrypt', '/home/ubuntu/eolssu-monitoring', '/opt/eolssu-backup', '/etc/eolssu-backup']:
            tree(p, Path('/etc/letsencrypt') if p == '/etc/letsencrypt' else None)
        env = environment(Path('/opt/eolssu/.env')); releases = [Path(env['BACKEND_JAR']), Path(env['WEB_DIST_DIR'])]
        for state, key in [('deployment.json', 'previous_jar'), ('frontend-deployment.json', 'previous_web')]:
            old = json.loads((Path('/opt/eolssu') / state).read_text()).get(key)
            if old: releases.append(Path(old))
        for p in releases:
            if not p.resolve().is_relative_to('/opt/eolssu/releases'): raise ValueError('release outside managed root')
            tree(p) if p.is_dir() else add(p)
        for root in ['/etc/systemd/system', '/etc/sudoers.d']:
            for p in Path(root).glob('*eolssu*'):
                if p.is_file() and not p.is_symlink(): add(p)
        manifest['releases'] = {k: env[k] for k in ['BACKEND_JAR', 'WEB_DIST_DIR']}
    elif kind in ('jenkins', 'controller-key'):
        home = Path('/var/lib/jenkins')
        if kind == 'controller-key': add(home / 'secrets/master.key')
        else:
            manifest['controller_key_sha256'] = sha(home / 'secrets/master.key')
            for pattern in ['*.xml', 'identity.key.enc', 'jobs/*/config.xml', 'nodes/*/config.xml', 'users/*/config.xml', 'plugins/*.jpi', 'plugins/*.hpi', 'secrets/*']:
                for p in home.glob(pattern):
                    if p.is_file() and p.name != 'master.key': add(p)
            tree('/opt/eolssu/jenkins')
    elif kind == 'grafana':
        info = json.loads(command(['docker', 'inspect', 'eolssu-monitoring-grafana-1']))[0]
        root = Path(next(m['Source'] for m in info['Mounts'] if m['Destination'] == '/var/lib/grafana'))
        source = sqlite3.connect('file:' + str(root / 'grafana.db') + '?mode=ro', uri=True)
        target = sqlite3.connect(generated / 'grafana.db')
        try: source.backup(target)
        finally: target.close(); source.close()
        for p in root.rglob('*'):
            if p.is_file() and p.name not in ('grafana.db', 'grafana.db-wal', 'grafana.db-shm'):
                add(p, 'grafana/' + str(p.relative_to(root)))
        add(generated / 'grafana.db', 'grafana/grafana.db')
    else: raise ValueError('invalid kind')
    for name, p in files.items():
        st = p.stat(); manifest['files'][name] = {'sha256': sha(p), 'size': st.st_size,
                                               'mode': st.st_mode & 0o777, 'uid': st.st_uid, 'gid': st.st_gid}
    archive = directory / 'bundle.tgz'
    with tarfile.open(archive, 'w:gz', dereference=True) as tar:
        data = json.dumps(manifest, sort_keys=True).encode(); item = tarfile.TarInfo('bundle.json')
        item.size = len(data); item.mode = 0o600; tar.addfile(item, io.BytesIO(data))
        for name, p in files.items(): tar.add(p, arcname=name, recursive=False)
    validate_bundle(archive, kind)
    if kind in ('config', 'jenkins', 'controller-key') and any(sha(p) != manifest['files'][n]['sha256'] for n, p in files.items()):
        raise RuntimeError('configuration changed during backup')
    if kind == 'jenkins' and sha(Path('/var/lib/jenkins/secrets/master.key')) != manifest['controller_key_sha256']:
        raise RuntimeError('controller key changed during backup')
    return archive, manifest

def encrypt(source, dest):
    command(['openssl', 'cms', '-encrypt', '-binary', '-aes-256-cbc', '-outform', 'DER',
             '-in', str(source), '-out', str(dest), '/etc/eolssu-backup/recipient.pem'])

def decrypt(source, dest):
    command(['openssl', 'cms', '-decrypt', '-binary', '-inform', 'DER', '-in', str(source),
             '-inkey', str(KEYS / 'restore-key.pem'), '-out', str(dest)])

def transfer(kind, run_id, encrypted):
    digest = sha(encrypted)
    with encrypted.open('rb') as stream:
        r = subprocess.run(['ssh', '-F', '/etc/eolssu-backup/ssh_config', 'backup-store',
                            'put ' + kind + ' ' + run_id + ' ' + digest], stdin=stream, capture_output=True, timeout=600)
    if r.returncode: raise RuntimeError('external transfer failed')
    result = json.loads(r.stdout)
    if result.get('sha256') != digest or result.get('run_id') != run_id: raise RuntimeError('acknowledgement mismatch')
    return result

def send(kind):
    if kind not in KINDS: raise ValueError('invalid kind')
    out = STATE / 'outgoing'; out.mkdir(parents=True, exist_ok=True, mode=0o700)
    for p in sorted(out.glob(kind + '-*.cms')):
        result = transfer(kind, p.name[len(kind) + 1:-4], p)
        atomic(STATE / ('last-' + kind + '.json'), result); cache_backup(p, kind)
    run_id = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + secrets.token_hex(6)
    with tempfile.TemporaryDirectory(dir=STATE, prefix='source-') as d:
        archive, _ = pack_bundle(kind, Path(d)); pending = out / (kind + '-' + run_id + '.partial')
        encrypt(archive, pending)
        with pending.open('rb') as f: os.fsync(f.fileno())
        encrypted = pending.with_suffix('.cms'); pending.rename(encrypted)
        result = transfer(kind, run_id, encrypted)
        atomic(STATE / ('last-' + kind + '.json'), result); cache_backup(encrypted, kind)
    print(json.dumps(result), flush=True)

def cache_backup(path, kind):
    cache = STATE / 'cache' / kind; cache.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.replace(path, cache / path.name)
    for p in cache.glob('*.cms'):
        if p.is_symlink(): raise ValueError('invalid cache file')
        if time.time() - p.stat().st_mtime > 48 * 3600: p.unlink()

def receive(original):
    fields = original.split()
    if len(fields) != 4 or fields[0] != 'put': raise ValueError('unsupported receiver command')
    _, kind, run_id, digest = fields
    if kind not in KINDS or not RUN.fullmatch(run_id) or not re.fullmatch('[a-f0-9]{64}', digest):
        raise ValueError('invalid receiver command')
    STORE.mkdir(parents=True, exist_ok=True, mode=0o700)
    with locked(STORE / '.receive.lock'), tempfile.TemporaryDirectory(dir=STORE, prefix='.incoming-') as d:
        d = Path(d); incoming = d / 'cipher.cms'; total = 0
        with incoming.open('xb') as f:
            for b in iter(lambda: sys.stdin.buffer.read(1048576), b''):
                total += len(b)
                if total > 1024**3: raise ValueError('transfer too large')
                f.write(b)
        if sha(incoming) != digest: raise ValueError('transfer checksum mismatch')
        plain = d / 'plain.tgz'; decrypt(incoming, plain); manifest = validate_bundle(plain, kind)
        root = KEYS / 'controller' if kind == 'controller-key' else STORE
        target = commit_backup(root, kind, run_id, incoming, digest)
        record = {'kind': kind, 'run_id': run_id, 'sha256': digest, 'bytes': target.stat().st_size,
                  'snapshot_at': manifest['snapshot_at'], 'received_at': now()}
        atomic(target.with_suffix('.json'), record); print(json.dumps(record), flush=True)

def latest(kind):
    root = KEYS / 'controller' if kind == 'controller-key' else STORE
    records = sorted((root / kind).glob('*.json'))
    if not records: raise RuntimeError('no completed backup: ' + kind)
    record = json.loads(records[-1].read_text()); path = records[-1].with_suffix('.cms')
    if path.is_symlink() or sha(path) != record['sha256']: raise ValueError('stored checksum mismatch')
    return path, record

def status():
    result = {}; stale = []
    for kind in KINDS:
        path, record = latest(kind)
        age = (datetime.now(timezone.utc) - datetime.fromisoformat(record['snapshot_at'].replace('Z', '+00:00'))).total_seconds()
        result[kind] = record | {'snapshot_age_seconds': round(age)}
        if age > (5400 if kind == 'db' else 27 * 3600): stale.append(kind)
    restore_file = STATE / 'restore-last.json'
    restore = json.loads(restore_file.read_text()) if restore_file.exists() else {}
    if restore.get('status') != 'passed' or (datetime.now(timezone.utc) - datetime.fromisoformat(restore['finished_at'].replace('Z', '+00:00'))).total_seconds() > 8 * 86400:
        stale.append('restore-test')
    print(json.dumps({'checked_at': now(), 'backups': result, 'restore': restore, 'stale': stale}))
    if stale: raise RuntimeError('stale external backup')

def prune():
    with locked(STORE / '.receive.lock'):
        current = datetime.now(timezone.utc)
        for kind in KINDS:
            if kind == 'controller-key': continue
            records = sorted((STORE / kind).glob('*.json')); daily = {}
            for p in records: daily.setdefault(p.stem[:8], p.stem)
            # 개인정보 사본은 최신 성공본이어도 보유 상한을 넘겨 남기지 않는다(Task 300).
            for p in records:
                if p.is_symlink() or not RUN.fullmatch(p.stem): raise ValueError('invalid retention target')
                record = json.loads(p.read_text()); age = current - datetime.fromisoformat(record['snapshot_at'].replace('Z', '+00:00'))
                # DB 정리는 하루 한 번이므로 29일에서 정리해 최대 30일 상한을 지킨다.
                keep = age < timedelta(days=14 if kind == 'jenkins' else 29 if kind == 'db' else 30)
                if kind == 'db' and age >= timedelta(days=7): keep = keep and daily[p.stem[:8]] == p.stem
                if not keep:
                    cipher = p.with_suffix('.cms')
                    if cipher.is_symlink(): raise ValueError('retention ownership mismatch')
                    if cipher.exists():
                        if sha(cipher) != record['sha256']: raise ValueError('retention ownership mismatch')
                        cipher.unlink()
                    p.unlink()
    print(json.dumps({'retention_checked_at': now()}))

def restore_database(import_sql, accounts, sql, report):
    # SHOW CREATE USER/SHOW GRANTS are stored as one statement per line.
    # Definers must exist for schema import; table grants need the schema first.
    lines = accounts.splitlines(keepends=True)
    users = b''.join(line for line in lines if line.startswith(b'CREATE USER '))
    grants = b''.join(line for line in lines if not line.startswith(b'CREATE USER '))
    report['phase'] = 'accounts-create'
    import_sql(users)
    report['phase'] = 'database-import'
    import_sql(sql)
    report['phase'] = 'accounts-grants'
    import_sql(grants)


def restore_test():
    started = time.monotonic(); report = {'started_at': now(), 'status': 'running', 'backups': {}}
    run_id = secrets.token_hex(8); containers = []; network = None
    label = 'com.eolssu.backup.restore=' + run_id
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    with locked(STORE / '.restore.lock'), tempfile.TemporaryDirectory(dir=STATE, prefix='restore-') as d:
        d = Path(d); manifests = {}
        try:
            for kind in KINDS:
                cipher, record = latest(kind); plain = d / (kind + '.tgz'); decrypt(cipher, plain)
                manifests[kind] = validate_bundle(plain, kind, d / kind)
                report['backups'][kind] = record['run_id']
            report['phase'] = 'mysql-start'
            sql = (d / 'db/dump.sql').read_bytes(); password = secrets.token_urlsafe(32)
            network = command(['docker', 'network', 'create', '--internal', '--label', label,
                               'eolssu-restore-' + run_id]).decode().strip()
            def start(role, image, args):
                result = command(['docker', 'run', '-d', '--name', 'eolssu-restore-' + run_id + '-' + role,
                                  '--label', label, '--network', network, '--network-alias', role,
                                  '--memory', '1536m' if role == 'jenkins' else '1024m', '--cpus', '1',
                                  '--log-opt', 'max-size=5m', '--log-opt', 'max-file=1', *args, image],
                                 env=dict(os.environ, MYSQL_ROOT_PASSWORD=password))
                cid = result.decode().strip(); containers.append(cid); return cid
            def wait(check, seconds=180):
                end = time.monotonic() + seconds
                while True:
                    try: return check()
                    except Exception:
                        if time.monotonic() > end: raise RuntimeError('isolated service readiness failed') from None
                        time.sleep(1)
            def truth(value):
                if not value: raise ValueError('restored service mismatch')
                return value
            db = start('mysql', 'mysql:8.4.13', ['-e', 'MYSQL_ROOT_PASSWORD', '-v', str(d / 'mysql-data') + ':/var/lib/mysql'])
            wait(lambda: truth(mysql_query('SELECT 1', db, password).strip() == '1'))
            def import_sql(data):
                command(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', db, 'mysql', '-uroot', '--protocol=TCP', '--host=127.0.0.1'],
                        data=data, env=dict(os.environ, MYSQL_PWD=password))
            restore_database(import_sql, (d / 'db/accounts.sql').read_bytes(), sql, report)
            report['phase'] = 'data-comparison'
            truth(mysql_query('SELECT @@version', db, password).strip() == manifests['db']['mysql_version'])
            objects = mysql_query("SELECT TABLE_SCHEMA,TABLE_NAME,TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'eolssu' ORDER BY 1,2", db, password).splitlines()
            truth(objects == manifests['db']['schema_objects'])
            tables = [row.split('\t')[0] + '.' + row.split('\t')[1] for row in objects if row.endswith('BASE TABLE')]
            actual = data_fingerprint(dump_db(db, password, True).decode(), tables)
            truth(actual == manifests['db']['data'])
            report['mysql'] = {'objects': len(objects), 'tables': len(tables),
                               'rows': sum(x['rows'] for x in actual.values()), 'all_data_hashes_match': True}
            report['phase'] = 'web-api'
            config = d / 'config/files'; env = environment(config / 'opt/eolssu/.env')
            # Original absolute paths are mapped only inside the verified disposable tree.
            def restored(name):
                p = PurePosixPath(name)
                if not p.is_absolute() or '..' in p.parts: raise ValueError('invalid restored path')
                result = config.joinpath(*p.parts[1:])
                if not result.exists(): raise ValueError('restored file missing')
                return result
            jar = restored(env['BACKEND_JAR']); web = restored(env['WEB_DIST_DIR'])
            for p in [web, *[p for p in web.rglob('*') if p.is_dir()]]: p.chmod(0o755)
            backend = command(['docker', 'run', '-d', '--name', 'eolssu-restore-' + run_id + '-backend', '--label', label,
                               '--network', network, '--network-alias', 'backend', '--memory', '1024m', '--cpus', '1',
                               '--user', '10001:10001', '-e', 'SERVER_PORT=8080', '-v', str(jar) + ':/app/app.jar:ro',
                               'eclipse-temurin:21-jre', 'java', '-jar', '/app/app.jar']).decode().strip()
            containers.append(backend)
            frontend = start('frontend', 'nginx:1.30.6-alpine', ['-v', str(web) + ':/usr/share/nginx/html:ro',
                              '-v', str(config / 'opt/eolssu/config/nginx.conf') + ':/etc/nginx/conf.d/default.conf:ro'])
            wait(lambda: truth(command(['docker', 'exec', frontend, 'wget', '-q', '-O-', 'http://127.0.0.1/']) == (web / 'index.html').read_bytes()))
            def api_probe():
                info = json.loads(command(['docker', 'inspect', frontend]))[0]
                address = next(iter(info['NetworkSettings']['Networks'].values()))['IPAddress']
                # 등록 안 된 /api 경로는 인증 때문에 401이다. Nginx를 거친 공개 readiness로 백엔드 기동을 본다.
                try: response = urllib.request.urlopen('http://' + address + '/api/actuator/health/readiness', timeout=5)
                except urllib.error.HTTPError as error: response = error
                with response: truth(response.status == 200 and json.load(response) == {'status': 'UP'})
                return True
            wait(api_probe); report['web_api'] = {'web_200_and_exact_content': True, 'api_readiness_up': True}
            report['phase'] = 'grafana'
            grafana = d / 'grafana/grafana'
            con = sqlite3.connect(grafana / 'grafana.db')
            truth(con.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'); con.close()
            for p in [grafana, *grafana.rglob('*')]:
                os.chown(p, 472, 0)
                if p.is_dir(): p.chmod(0o755)
            graph = start('grafana', 'grafana/grafana:11.3.0', ['-v', str(grafana) + ':/var/lib/grafana'])
            wait(lambda: truth(json.loads(command(['docker', 'exec', graph, 'wget', '-q', '-O-', 'http://127.0.0.1:3000/api/health']))['database'] == 'ok'))
            report['grafana'] = {'sqlite_integrity': True, 'http_database_ok': True}
            report['phase'] = 'jenkins'
            home = d / 'jenkins/files/var/lib/jenkins'
            key = d / 'controller-key/files/var/lib/jenkins/secrets/master.key'
            truth(sha(key) == manifests['jenkins']['controller_key_sha256'])
            shutil.copyfile(key, home / 'secrets/master.key'); (home / 'secrets/master.key').chmod(0o600)
            for p in [home, *home.rglob('*')]:
                os.chown(p, 111, 113)
                if p.is_dir(): p.chmod(0o755)
            admin = d / 'jenkins/files/opt/eolssu/jenkins'
            controller = start('jenkins', 'jenkins/jenkins:2.580.1-jdk21', ['--user', '111:113', '-e', 'JENKINS_HOME=/var/lib/jenkins',
                               '-e', 'COPY_REFERENCE_FILE_LOG=/var/lib/jenkins/copy_reference_file.log',
                               '-e', 'JAVA_OPTS=-Djenkins.install.runSetupWizard=false', '-v', str(home) + ':/var/lib/jenkins',
                               '-v', str(admin) + ':/restore-admin:ro'])
            groovy = rb"""import jenkins.model.Jenkins
import hudson.util.Secret
assert Jenkins.get().numExecutors == 0
def xml = new File(Jenkins.get().rootDir,'credentials.xml').text
def values = (xml =~ /<password>([^<]+)<\/password>/).collect { it[1] }
assert values.size() > 0
assert values.every { Secret.decrypt(it) != null }
println('RESTORE_OK')
"""
            cli = ['docker', 'exec', '-i', '--user', '0', controller, 'java', '-jar', '/restore-admin/jenkins-cli.jar',
                   '-s', 'http://127.0.0.1:8080/', '-auth', '@/restore-admin/admin-cli.auth', 'groovy', '=']
            wait(lambda: truth(b'RESTORE_OK' in command(cli, data=groovy, timeout=20)), seconds=240)
            report['jenkins'] = {'authenticated_admin_cli': True, 'executor_zero': True, 'credentials_decrypted': True, 'separate_key_restored': True}
            report['status'] = 'passed'
        except Exception:
            report['status'] = 'failed'
            raise
        finally:
            cleanup_errors = []
            for cid in reversed(containers):
                try:
                    info = json.loads(command(['docker', 'inspect', cid]))[0]
                    if info['Config']['Labels'].get('com.eolssu.backup.restore') != run_id: raise ValueError('cleanup owner mismatch')
                    command(['docker', 'rm', '-f', '-v', cid])
                except Exception: cleanup_errors.append(cid[:12])
            if network:
                try: command(['docker', 'network', 'rm', network])
                except Exception: cleanup_errors.append('network')
            report.update(finished_at=now(), seconds=round(time.monotonic() - started, 2), cleanup_errors=cleanup_errors)
            if cleanup_errors: report['status'] = 'failed'
            atomic(STATE / 'restore-last.json', report)
            print(json.dumps(report), flush=True)
            if cleanup_errors: raise RuntimeError('restore cleanup failed')

def main():
    os.umask(0o077)
    if os.geteuid() != 0: raise ValueError('root required')
    os.environ.clear(); os.environ.update(PATH='/usr/sbin:/usr/bin:/sbin:/bin', HOME='/root', LANG='C.UTF-8')
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    action = sys.argv[1] if len(sys.argv) > 1 else ''
    if action == 'receive' and len(sys.argv) == 3: receive(sys.argv[2])
    elif action == 'send' and len(sys.argv) == 3:
        mode = sys.argv[2]
        with contextlib.ExitStack() as stack:
            # 배포 도구는 이미 배포 lock을 잡고 호출한다. postmigration은 스키마를 바꾼 배포의 사후 DB·설정 사본이다.
            if mode not in ('prechange', 'postchange', 'postmigration'): stack.enter_context(locked(Path('/run/lock/eolssu-backend-deploy.lock')))
            stack.enter_context(locked(STATE / 'source.lock'))
            for kind in (('db', 'config') if mode in ('prechange', 'postmigration') else ('config',) if mode == 'postchange' else (mode,)): send(kind)
    elif action == 'status' and len(sys.argv) == 2: status()
    elif action == 'prune' and len(sys.argv) == 2: prune()
    elif action == 'restore-test' and len(sys.argv) == 2: restore_test()
    else: raise ValueError('unsupported operation')

if __name__ == '__main__':
    try: main()
    except Exception as error:
        print(json.dumps({'status': 'failed', 'error_type': type(error).__name__,
                          'reason': str(error) if isinstance(error, RuntimeError) else 'validation failed', 'at': now()}), file=sys.stderr)
        sys.exit(1)
