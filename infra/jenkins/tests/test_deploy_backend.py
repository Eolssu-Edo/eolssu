"""배포 실패·중단 시 실제 env/상태 파일의 복구 계약을 검사한다."""
import importlib.util
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
