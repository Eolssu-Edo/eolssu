"""배포 실패·중단 시 실제 env/상태 파일의 복구 계약을 검사한다."""
import importlib.util
import contextlib
import io
from unittest.mock import patch
import fcntl
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import signal
import subprocess
import sys
import unittest

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


if __name__ == '__main__':
    unittest.main()
