"""프론트 릴리스 선택·복구·불변 아티팩트 계약을 검사한다."""
import importlib.util
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deploy_frontend', Path(__file__).parents[1] / 'deploy-frontend.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class FrontendTransactionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.old = self.root / 'releases/old/web'
        self.new = self.root / ('releases/' + 'a' * 40 + '/web')
        for path in (self.old, self.new):
            path.mkdir(parents=True)
            for name, content in [('index.html', '<script src="webApp.js"></script>'),
                                  ('webApp.js', 'console.log("web")'), ('app.wasm', 'fixture wasm')]:
                (path / name).write_text(content)
        self.env = self.root / '.env'
        self.original = f'WEB_DIST_DIR={self.old}\nBACKEND_JAR=/preserve/app.jar\nMYSQL_PASSWORD=keep-me\n'
        self.env.write_text(self.original)
        self.d = deploy.FrontendDeployer(self.root)
        self.activations = []
        self.d.activate = lambda: self.activations.append(self.d.current_web())
        self.d.healthy = lambda: None
        self.d.unchanged = lambda before: None

    def promote(self):
        self.d.promote('a' * 40, self.new, 'sha256:image', {})

    def test_pending_backend_drain_prevents_frontend_recreation(self):
        (self.root / 'deployment.json').write_text(json.dumps({'status': 'deployed', 'retire_slot': 'backend'}))
        with self.assertRaisesRegex(deploy.DeployError, 'drain the backend deployment'):
            self.d.deploy('a' * 40)
        self.assertEqual(self.activations, [])

    def test_success_preserves_other_env_and_same_release_does_not_restart(self):
        self.promote()
        self.assertEqual(self.d.current_web(), self.new)
        self.assertIn('BACKEND_JAR=/preserve/app.jar\nMYSQL_PASSWORD=keep-me\n', self.env.read_text())
        self.assertTrue(self.d.already_deployed('a' * 40, 'sha256:image'))
        self.assertEqual(self.activations, [self.new])
        self.assertEqual(self.env.stat().st_mode & 0o777, 0o600)

    def test_activation_failure_restores_previous_web(self):
        def activate():
            self.activations.append(self.d.current_web())
            if self.d.current_web() == self.new:
                raise deploy.DeployError('candidate failure')
        self.d.activate = activate
        with self.assertRaises(deploy.DeployError):
            self.promote()
        self.assertEqual(self.env.read_text(), self.original)
        self.assertEqual(self.activations, [self.new, self.old])
        self.assertEqual(json.loads(self.d.state.read_text())['status'], 'rolled_back')

    def test_crash_after_env_change_recovers_first(self):
        self.d.save({'status': 'deploying', 'sha': 'a' * 40, 'previous_web': str(self.old)})
        self.d.select(self.new)
        self.d.recover()
        self.assertEqual(self.env.read_text(), self.original)
        self.assertEqual(self.activations, [self.old])

    def test_failed_rollback_can_be_resumed(self):
        self.d.activate = lambda: (_ for _ in ()).throw(deploy.DeployError('unavailable'))
        with self.assertRaises(deploy.DeployError):
            self.promote()
        self.assertEqual(json.loads(self.d.state.read_text())['status'], 'rollback_failed')
        self.d.activate = lambda: None
        self.d.recover()
        self.assertEqual(json.loads(self.d.state.read_text())['status'], 'rolled_back')

    def test_modified_deployed_asset_is_rejected(self):
        self.promote()
        (self.new / 'webApp.js').write_text('changed')
        with self.assertRaises(deploy.DeployError):
            self.d.already_deployed('a' * 40, 'sha256:image')

    def test_symlink_or_missing_bundle_is_rejected(self):
        (self.new / 'escape').symlink_to('/etc/passwd')
        with self.assertRaises(deploy.DeployError):
            deploy.manifest(self.new)
        (self.new / 'escape').unlink()
        (self.new / 'webApp.js').unlink()
        with self.assertRaises(deploy.DeployError):
            deploy.manifest(self.new)

    def test_order_check_uses_frontend_active_release_not_backend_or_failed_journal(self):
        self.d.state.write_text(json.dumps({'status': 'rolled_back', 'sha': 'f' * 40}))
        with patch.object(deploy.support, 'check_release_order') as check:
            self.d.check_order('b' * 40)
        check.assert_called_once_with('b' * 40, 'old')

    def test_wrong_sha_rejected_before_external_effect(self):
        for value in ['develop', '../escape', 'a' * 39, 'a' * 40 + ';id']:
            with self.assertRaises(deploy.DeployError):
                self.d.deploy(value)


class CandidateHttpTests(unittest.TestCase):
    def setUp(self):
        self.assets = {'/index.html': b'web index', '/webApp.js': b'actual JS', '/app.wasm': b'wasm data'}
        self.wasm_type = 'application/wasm'
        owner = self
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                body = owner.assets.get(self.path, owner.assets['/index.html'])
                self.send_response(200)
                self.send_header('Content-Type', owner.wasm_type if self.path.endswith('.wasm') else 'text/plain')
                self.end_headers()
                self.wfile.write(body)
            def log_message(self, *args):
                pass
        self.expected = {p[1:]: hashlib.sha256(data).hexdigest() for p, data in self.assets.items()}
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=lambda: self.server.serve_forever(poll_interval=0.01), daemon=True)
        self.thread.start()
        self.addCleanup(self.close)
        self.url = 'http://127.0.0.1:' + str(self.server.server_port)

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def test_all_candidate_files_match(self):
        deploy.verify_assets(self.url, self.expected)

    def test_spa_fallback_does_not_hide_missing_bundle(self):
        del self.assets['/webApp.js']
        with self.assertRaises(deploy.DeployError):
            deploy.verify_assets(self.url, self.expected)

    def test_wrong_wasm_mime_is_rejected(self):
        self.wasm_type = 'text/html'
        with self.assertRaises(deploy.DeployError):
            deploy.verify_assets(self.url, self.expected)


if __name__ == '__main__':
    unittest.main()
