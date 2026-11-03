"""배포 실패·중단 시 실제 env/상태 파일의 복구 계약을 검사한다."""
import importlib.util
import contextlib
import io
from unittest.mock import patch
import fcntl
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import signal
import subprocess
import sys
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('deploy_backend', Path(__file__).parents[1] / 'deploy-backend.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class ReleaseOrderTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.repo = self.root / 'repo'
        self.remote = self.root / 'remote.git'
        self.git('init', '--bare', str(self.remote))
        self.git('clone', str(self.remote), str(self.repo))
        self.git('-C', str(self.repo), 'checkout', '-b', 'develop')
        self.first = self.commit('first')
        self.second = self.commit('second')
        self.third = self.commit('third')
        self.git('-C', str(self.repo), 'push', 'origin', 'develop')

    def git(self, *args):
        return subprocess.run(['git', *args], check=True, text=True, capture_output=True).stdout.strip()

    def commit(self, value):
        (self.repo / 'file').write_text(value)
        self.git('-C', str(self.repo), 'add', 'file')
        self.git('-C', str(self.repo), '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.test',
                 'commit', '-m', value)
        return self.git('-C', str(self.repo), 'rev-parse', 'HEAD')

    def check(self, candidate, current):
        deploy.check_release_order(candidate, current, repository=self.repo, git_prefix=())

    def test_build_revision_can_lag_develop_head(self):
        self.check(self.second, self.first)

    def test_older_candidate_cannot_replace_deployed_revision(self):
        with self.assertRaisesRegex(deploy.DeployError, 'older than'):
            self.check(self.first, self.second)

    def test_same_revision_and_initial_release_are_allowed(self):
        self.check(self.second, self.second)
        self.check(self.first, 'bootstrap')

    def test_candidate_outside_develop_is_rejected(self):
        self.git('-C', str(self.repo), 'checkout', '-b', 'other', self.first)
        other = self.commit('other')
        with self.assertRaisesRegex(deploy.DeployError, 'develop history'):
            self.check(other, self.first)

    def test_unknown_commit_does_not_change_runtime(self):
        with self.assertRaises(deploy.DeployError):
            self.check('f' * 40, self.first)

    def test_diverged_deployed_history_is_not_silently_overwritten(self):
        self.git('-C', str(self.repo), 'checkout', '-b', 'other', self.first)
        other = self.commit('other')
        with self.assertRaisesRegex(deploy.DeployError, 'diverged'):
            self.check(self.second, other)

    def test_backend_uses_active_release_after_rollback_not_failed_journal_sha(self):
        active = self.root / 'releases' / self.first / 'app.jar'
        active.parent.mkdir(parents=True)
        active.write_bytes(b'active')
        (self.root / '.env').write_text(f'BACKEND_JAR={active}\n')
        d = deploy.Deployer(self.root)
        d.state.write_text(json.dumps({'status': 'rolled_back', 'sha': self.third}))
        original = deploy.check_release_order
        with patch.object(deploy, 'check_release_order',
                          side_effect=lambda sha, current: original(sha, current, self.repo, ())):
            d.check_order(self.second)
            active_next = active.parent.parent / self.second / 'app.jar'
            active_next.parent.mkdir()
            active_next.write_bytes(b'next')
            (self.root / '.env').write_text(f'BACKEND_JAR={active_next}\n')
            with self.assertRaisesRegex(deploy.DeployError, 'older than'):
                d.check_order(self.first)


class PhaseTests(unittest.TestCase):
    def test_failed_phase_is_reported_without_exception_secrets(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            with self.assertRaises(ValueError):
                with deploy.phase('Candidate health'):
                    raise ValueError('secret-value')
        events = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertEqual([e['event'] for e in events], ['phase_started', 'phase_failed'])
        self.assertEqual(events[-1]['phase'], 'Candidate health')
        self.assertNotIn('secret-value', output.getvalue())


class ProbeTests(unittest.TestCase):
    """배포 검사는 인증과 무관한 공개 health 경로의 상태 본문으로 판단한다."""

    def serve(self, status, body):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(status)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(body.encode())

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return f'http://127.0.0.1:{server.server_port}/api/actuator/health/liveness'

    def test_accepts_only_status_up(self):
        deploy.probe(self.serve(200, '{"status":"UP"}'), 200, {'status': 'UP'}, seconds=1)
        for status, body in [(200, '{"status":"DOWN"}'), (401, '{"result":{"errorCode":"UNAUTHENTICATED"}}'), (200, 'not json')]:
            with self.assertRaises(deploy.DeployError):
                deploy.probe(self.serve(status, body), 200, {'status': 'UP'}, seconds=1)

    def test_runtime_check_uses_public_readiness_not_an_unregistered_api_path(self):
        urls = []
        original = deploy.probe
        deploy.probe = lambda url, *args, **kwargs: urls.append(url)
        self.addCleanup(setattr, deploy, 'probe', original)
        d = deploy.Deployer(Path('/nonexistent'))
        with self.assertRaises(Exception):
            d.healthy()  # docker inspect는 이 환경에 없다. 그 전에 부른 검사 주소만 본다.
        self.assertIn('http://127.0.0.1/api/actuator/health/readiness', urls)
        self.assertFalse([u for u in urls if 'infra-smoke' in u])

    def test_candidate_check_uses_liveness(self):
        self.assertEqual('http://10.0.0.2:8080/api/actuator/health/liveness', deploy.candidate_health_url('10.0.0.2'))


class LockTests(unittest.TestCase):
    def test_free_lock_cancels_timeout(self):
        previous = signal.getsignal(signal.SIGALRM)
        with tempfile.TemporaryFile() as lock:
            deploy.acquire_lock(lock, seconds=1)
            self.assertEqual(signal.alarm(0), 0)
            self.assertEqual(signal.getsignal(signal.SIGALRM), previous)

    def test_contended_lock_has_bounded_wait(self):
        with tempfile.NamedTemporaryFile() as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            program = '''import importlib.util,sys
spec=importlib.util.spec_from_file_location('deploy',sys.argv[1]);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
with open(sys.argv[2],'a') as lock:
 try:m.acquire_lock(lock,seconds=1)
 except m.DeployError:print('LOCK_TIMEOUT');sys.exit(3)
'''
            result = subprocess.run([sys.executable, '-B', '-c', program, str(Path(__file__).parents[1] / 'deploy-backend.py'), lock.name],
                                    capture_output=True, text=True, timeout=5)
            self.assertEqual(result.returncode, 3, result.stderr)
            self.assertEqual(result.stdout.strip(), 'LOCK_TIMEOUT')


class TransactionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.old = self.root / 'releases/old/app.jar'
        self.new = self.root / ('releases/' + 'a' * 40 + '/app.jar')
        for path, content in [(self.old, b'old jar'), (self.new, b'new jar')]:
            path.parent.mkdir(parents=True)
            path.write_bytes(content)
        self.env = self.root / '.env'
        self.original = f'BACKEND_JAR={self.old}\nMYSQL_PASSWORD=preserve-this-value\n# keep comment\n'
        self.env.write_text(self.original)
        self.env.chmod(0o600)
        self.d = deploy.Deployer(self.root)
        self.activations = []
        self.d.activate = lambda: self.activations.append(self.d.current_jar())
        self.d.healthy = lambda: None
        self.d.unchanged = lambda before: None

    def test_success_and_same_release_do_not_restart_twice(self):
        self.d.promote('a' * 40, self.new, 'sha256:image', {})
        self.assertEqual(self.d.current_jar(), self.new)
        self.assertTrue(self.d.already_deployed('a' * 40, 'sha256:image'))
        self.assertEqual(self.activations, [self.new])
        self.assertIn('MYSQL_PASSWORD=preserve-this-value\n# keep comment\n', self.env.read_text())
        self.assertEqual(self.env.stat().st_mode & 0o777, 0o600)

    def test_failed_activation_restores_previous_jar(self):
        def activate():
            self.activations.append(self.d.current_jar())
            if self.d.current_jar() == self.new:
                raise deploy.DeployError('candidate failed')
        self.d.activate = activate
        with self.assertRaises(deploy.DeployError):
            self.d.promote('a' * 40, self.new, 'sha256:image', {})
        self.assertEqual(self.env.read_text(), self.original)
        self.assertEqual(self.activations, [self.new, self.old])
        self.assertEqual(json.loads(self.d.state.read_text())['status'], 'rolled_back')

    def test_crash_after_env_switch_recovers_before_next_deploy(self):
        self.d.save({'status': 'deploying', 'previous_jar': str(self.old), 'sha': 'a' * 40})
        self.d.select(self.new)
        self.d.recover()
        self.assertEqual(self.env.read_text(), self.original)
        self.assertEqual(self.activations, [self.old])

    def test_failed_rollback_remains_recoverable(self):
        self.d.activate = lambda: (_ for _ in ()).throw(deploy.DeployError('unavailable'))
        with self.assertRaises(deploy.DeployError):
            self.d.promote('a' * 40, self.new, 'sha256:image', {})
        self.assertEqual(json.loads(self.d.state.read_text())['status'], 'rollback_failed')
        self.d.activate = lambda: None
        self.d.recover()
        self.assertEqual(json.loads(self.d.state.read_text())['status'], 'rolled_back')

    def test_malformed_sha_rejected(self):
        for value in ['main', '../etc/passwd', 'a' * 39, 'a' * 40 + ';id']:
            with self.assertRaises(deploy.DeployError):
                deploy.validate_sha(value)


MIGRATIONS = Path(__file__).parents[3] / 'backend/src/main/resources/db/migration'
OPS_HISTORY = Path(__file__).parent / 'fixtures/ops-flyway-history-20261103.tsv'


def jar_with(path, files):
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('BOOT-INF/classes/application.yml', 'spring: {}\n')
        archive.writestr(deploy.MIGRATION_DIR, '')
        for name, body in files.items():
            archive.writestr(deploy.MIGRATION_DIR + name, body)
    return path


class MigrationSqlTests(unittest.TestCase):
    def test_checksum_matches_flyway_history_recorded_in_operation(self):
        rows = [line.split('\t') for line in OPS_HISTORY.read_text().splitlines()]
        self.assertEqual(len(rows), 18)
        for version, script, checksum in rows:
            with self.subTest(script=script):
                self.assertEqual(deploy.flyway_checksum((MIGRATIONS / script).read_bytes()), int(checksum))

    def test_checksum_ignores_bom_and_line_endings(self):
        self.assertEqual(deploy.flyway_checksum(b'a;\nb;\n'), deploy.flyway_checksum(b'\xef\xbb\xbfa;\r\nb;'))
        self.assertNotEqual(deploy.flyway_checksum(b'a;\nb;\n'), deploy.flyway_checksum(b'a;\nb; \n'))

    def test_only_existing_migration_that_dropped_columns_needs_review(self):
        flagged = [p.name for p in sorted(MIGRATIONS.glob('V*.sql'))
                   if deploy.DESTRUCTIVE_SQL.search(deploy.sql_code(p.read_text()))]
        self.assertEqual(flagged, ['V14__learning_tracks_and_progress.sql'])

    def test_destructive_statements_outside_comments_and_strings(self):
        risky = ['ALTER TABLE t DROP COLUMN a;', 'ALTER TABLE t DROP a;', 'ALTER TABLE t DROP `a`;', 'DROP TABLE t;',
                 'RENAME TABLE a TO b;', 'ALTER TABLE t RENAME COLUMN a TO b;', 'ALTER TABLE t CHANGE a b INT;',
                 'TRUNCATE t;', 'DELETE FROM t WHERE id = 1;']
        safe = ['DROP TEMPORARY TABLE x;', 'ALTER TABLE t DROP INDEX i, DROP CHECK c, DROP FOREIGN KEY f;',
                'ALTER TABLE t RENAME INDEX a TO b;', "INSERT INTO t VALUES ('drop table x', 'it''s delete');",
                '-- delete from t\nSELECT 1; # truncate t\n/* drop table t */',
                'ALTER TABLE t ADD CONSTRAINT f FOREIGN KEY (a) REFERENCES u (id) ON DELETE CASCADE;',
                'ALTER TABLE t ADD COLUMN change_count INT;']
        for sql in risky:
            self.assertTrue(deploy.DESTRUCTIVE_SQL.search(deploy.sql_code(sql)), sql)
        for sql in safe:
            self.assertFalse(deploy.DESTRUCTIVE_SQL.search(deploy.sql_code(sql)), sql)

    def test_candidate_rejects_duplicate_versions_and_unexpected_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            duplicate = jar_with(Path(tmp) / 'dup.jar', {'V18__builtin_fingering_patterns.sql': 'SELECT 1;',
                                                          'V18__user_sheet_drafts.sql': 'SELECT 2;'})
            with self.assertRaisesRegex(deploy.DeployError, 'duplicate Flyway version in candidate: V18'):
                deploy.candidate_migrations(duplicate)
            for name in ['V1_missing_separator.sql', 'R__repeatable.sql', 'V01__leading_zero.sql', 'V2__Upper.sql']:
                with self.assertRaisesRegex(deploy.DeployError, 'unexpected file'):
                    deploy.candidate_migrations(jar_with(Path(tmp) / 'bad.jar', {name: 'SELECT 1;'}))


class FakeDocker:
    """배포 도구가 부르는 docker 명령의 대역. DB 이력과 컨테이너 상태만 흉내 낸다."""

    def __init__(self, history, leftover='', exit_code='0', wait_timeout=False):
        self.history = dict(history)
        self.leftover = leftover
        self.exit_code = exit_code
        self.wait_timeout = wait_timeout
        self.calls = []
        self.envs = []
        self.target = None
        self.pending = {}

    def __call__(self, *args, timeout=60, env=None):
        self.calls.append(args)
        if args[:2] == ('docker', 'ps'):
            return self.leftover
        if args[:2] == ('docker', 'exec'):
            return '\n'.join(f'{v}\t{s}\t{c}\t{ok}' for v, (s, c, ok) in sorted(self.history.items()))
        if args[:2] == ('docker', 'inspect'):
            return json.dumps([{'Config': {'Image': 'runtime:1', 'Env': [
                'DB_URL=jdbc:mysql://mysql:3306/eolssu?connectTimeout=2000&socketTimeout=2000',
                'DB_USERNAME=eolssu', 'DB_PASSWORD=secret-value', 'AUTH_JWT_SECRET=not-forwarded']},
                'NetworkSettings': {'Networks': {deploy.RUNTIME_NETWORK: {}}}}])
        if args[:3] == ('docker', 'run', '-d'):
            self.envs.append(env)
            self.target = int(next(a for a in args if a.startswith('--spring.flyway.target=')).split('=')[1])
            return 'container-id'
        if args[:2] == ('docker', 'wait'):
            if self.wait_timeout:
                raise deploy.DeployError('command unavailable or timed out: docker')
            if self.exit_code == '0':
                for version, (script, checksum) in self.pending.items():
                    self.history[version] = (script, checksum, '1')
            elif self.pending:
                version, (script, checksum) = min(self.pending.items())
                self.history[version] = (script, checksum, '0')
            return self.exit_code
        if args[:2] == ('docker', 'logs'):
            return 'Successfully applied migrations'
        return ''


class MigrationFixture(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.files = {p.name: p.read_bytes() for p in MIGRATIONS.glob('V*.sql')}
        self.applied = {int(v): (s, int(c), '1') for v, s, c in
                        (line.split('\t') for line in OPS_HISTORY.read_text().splitlines())}
        self.release = self.root / 'releases' / ('a' * 40)
        self.release.mkdir(parents=True)
        self.d = deploy.Deployer(self.root)

    def jar(self, extra=None):
        return jar_with(self.release / 'app.jar', {**self.files, **(extra or {})})

    def plan(self, docker, jar):
        with patch.object(deploy, 'run', docker):
            return self.d.migration_plan(jar, 'eolssu')


class MigrationPlanTests(MigrationFixture):
    def test_operation_history_matches_develop_without_pending_migrations(self):
        self.assertEqual(self.plan(FakeDocker(self.applied), self.jar()), [])

    def test_new_additive_migration_is_pending(self):
        jar = self.jar({'V19__user_sheet_drafts.sql': b'ALTER TABLE user_sheets ADD COLUMN draft_json JSON NULL;\n'})
        self.assertEqual(self.plan(FakeDocker(self.applied), jar), [(19, 'V19__user_sheet_drafts.sql')])

    def test_history_problems_stop_before_switching(self):
        cases = {
            'failed Flyway migration': {**self.applied, 18: (self.applied[18][0], self.applied[18][1], '0')},
            'candidate lacks': {**self.applied, 19: ('V19__other_branch.sql', 1, '1')},
            'was edited': {**self.applied, 17: (self.applied[17][0], 123, '1')},
            'older than applied': {v: r for v, r in self.applied.items() if v != 16},
        }
        for message, history in cases.items():
            with self.subTest(message=message), self.assertRaisesRegex(deploy.DeployError, message):
                self.plan(FakeDocker(history), self.jar())

    def test_leftover_migration_container_blocks_every_deploy(self):
        with self.assertRaisesRegex(deploy.DeployError, 'earlier migration container'):
            self.plan(FakeDocker(self.applied, leftover='abc123'), self.jar())

    def test_destructive_migration_needs_digest_approval(self):
        body = b'ALTER TABLE song_model_audios DROP COLUMN audio_url;\n'
        jar = self.jar({'V19__drop_audio_url.sql': body})
        with self.assertRaisesRegex(deploy.DeployError, 'approve its SHA-256'):
            self.plan(FakeDocker(self.applied), jar)
        approvals = self.root / 'migration-approvals'
        approvals.mkdir()
        (approvals / 'V19__drop_audio_url.sql').write_text('0' * 64 + '\n')
        with self.assertRaisesRegex(deploy.DeployError, 'approve its SHA-256'):
            self.plan(FakeDocker(self.applied), jar)
        (approvals / 'V19__drop_audio_url.sql').write_text(hashlib.sha256(body).hexdigest() + '\n')
        self.assertEqual(self.plan(FakeDocker(self.applied), jar), [(19, 'V19__drop_audio_url.sql')])


class MigrationApplyTests(MigrationFixture):
    def apply(self, docker, pending):
        docker.pending = {v: (s, 1) for v, s in pending}
        jar = self.jar()
        with patch.object(deploy, 'run', docker):
            database = self.d.database()
            self.d.migrate('a' * 40, jar, database, pending)
        return jar

    def test_success_applies_up_to_the_candidate_target_without_secrets_on_the_command_line(self):
        docker = FakeDocker(self.applied)
        self.apply(docker, [(19, 'V19__a.sql'), (20, 'V20__b.sql')])
        self.assertEqual(docker.target, 20)
        command = next(c for c in docker.calls if c[:3] == ('docker', 'run', '-d'))
        self.assertNotIn('secret-value', ' '.join(command))
        self.assertIn('--spring.profiles.active=db', command)
        self.assertEqual(docker.envs[0]['DB_PASSWORD'], 'secret-value')
        self.assertNotIn('AUTH_JWT_SECRET', docker.envs[0])
        self.assertIn('socketTimeout=300000', docker.envs[0]['DB_URL'])
        self.assertIn(('docker', 'rm', 'eolssu-migrate-' + 'a' * 12), docker.calls)
        log = self.release / 'migration.log'
        self.assertEqual(log.stat().st_mode & 0o777, 0o600)

    def test_failed_migration_keeps_container_and_log(self):
        docker = FakeDocker(self.applied, exit_code='1')
        with self.assertRaisesRegex(deploy.DeployError, 'migration failed'):
            self.apply(docker, [(19, 'V19__a.sql')])
        self.assertFalse([c for c in docker.calls if c[:2] == ('docker', 'rm')])
        self.assertTrue((self.release / 'migration.log').exists())

    def test_timeout_does_not_kill_a_running_migration(self):
        docker = FakeDocker(self.applied, wait_timeout=True)
        with self.assertRaisesRegex(deploy.DeployError, 'did not finish'):
            self.apply(docker, [(19, 'V19__a.sql')])
        self.assertFalse([c for c in docker.calls if c[1] in ('rm', 'stop', 'kill')])


class DeployMigrationOrderTests(unittest.TestCase):
    """마이그레이션은 후보 검증 뒤, 교체 직전에만 실행된다."""

    def drive(self, plan):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        d = deploy.Deployer(Path(tmp.name))
        (d.root / 'releases').mkdir()
        events = []
        sha = 'b' * 40

        def run(*args, **kwargs):
            if args[:3] == ('docker', 'image', 'inspect'):
                return json.dumps([{'Id': 'sha256:image', 'Config': {'Labels': {'org.opencontainers.image.revision': sha}}}])
            if args[:2] == ('docker', 'inspect'):
                return json.dumps([{'NetworkSettings': {'Networks': {args[2]: {'IPAddress': '10.0.0.2'}}}}])
            if args[:2] == ('docker', 'cp'):
                Path(args[3]).write_bytes(b'jar')
            return ''

        def planned(jar, name):
            events.append('plan')
            if isinstance(plan, Exception):
                raise plan
            return plan

        with patch.object(deploy, 'run', run), patch.object(deploy, 'probe', lambda *a, **k: None), \
             patch.object(d, 'recover', lambda: None), patch.object(d, 'check_order', lambda sha: events.append('order')), \
             patch.object(d, 'already_deployed', lambda *a: False), patch.object(d, 'protected', lambda: {}), \
             patch.object(d, 'unchanged', lambda before: None), patch.object(d, 'database', lambda: {'name': 'eolssu'}), \
             patch.object(d, 'migration_plan', planned), \
             patch.object(d, 'migrate', lambda *a: events.append('migrate')), \
             patch.object(d, 'promote', lambda *a: events.append('promote')), contextlib.redirect_stdout(io.StringIO()):
            try:
                d.deploy(sha)
            except deploy.DeployError:
                pass
        return events, d.migrated

    def test_pending_migration_runs_after_candidate_checks_and_before_switch(self):
        self.assertEqual(self.drive([(19, 'V19__a.sql')]), (['order', 'order', 'plan', 'migrate', 'promote'], True))

    def test_no_pending_migration_switches_without_running_flyway(self):
        self.assertEqual(self.drive([]), (['order', 'order', 'plan', 'promote'], False))

    def test_rejected_plan_keeps_running_release(self):
        self.assertEqual(self.drive(deploy.DeployError('needs review')), (['order', 'order', 'plan'], False))


if __name__ == '__main__':
    unittest.main()
