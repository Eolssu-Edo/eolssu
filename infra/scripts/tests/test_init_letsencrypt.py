"""TLS 초기화의 공개 실행 경로와 실패 시 운영 설정 보존을 검사한다."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('tls_init', Path(__file__).parents[1] / 'init-letsencrypt.py')
tls = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tls)


class EntryPointTests(unittest.TestCase):
    def test_documented_shell_entrypoint_exists_and_explains_usage(self):
        script = Path(__file__).parents[1] / 'init-letsencrypt.sh'
        result = subprocess.run([str(script), '--help'], text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('k15e102.p.ssafy.io', result.stdout)


class InitializationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'config').mkdir()
        (self.root / 'config/compose.yml').write_text('original runtime')
        (self.root / '.env').write_text('MYSQL_PASSWORD=preserve-me\n')
        self.webroot = self.root / 'web'
        self.webroot.mkdir()
        self.certs = self.root / 'certificates'
        self.live = self.certs / 'live/test.example.com'
        self.calls = []
        self.should_fail = lambda args: False
        def invoke(*args):
            self.calls.append(args)
            if self.should_fail(args):
                raise RuntimeError('fixture failure')
            if args[:2] == ('docker', 'inspect'):
                return tls.json.dumps([{'Mounts': [{'Destination': '/usr/share/nginx/html',
                    'Source': str(self.webroot)}], 'Config': {'Image': 'nginx:fixture'}}])
            if args[0] == 'certbot':
                self.live.mkdir(parents=True, exist_ok=True)
                for name in ('privkey.pem', 'fullchain.pem'):
                    (self.live / name).write_text('fixture certificate')
            return ''
        self.runner = patch.object(tls, 'run', side_effect=invoke)
        self.runner.start()
        self.addCleanup(self.runner.stop)

    def initialize(self):
        return tls.initialize('test.example.com', root=self.root, certificates=self.certs)

    def test_success_preserves_env_and_only_recreates_frontend(self):
        self.initialize()
        self.assertEqual((self.root / '.env').read_text(), 'MYSQL_PASSWORD=preserve-me\n')
        self.assertEqual((self.root / 'config/compose.yml').read_text(), 'original runtime')
        changes = [c for c in self.calls if 'up' in c]
        self.assertEqual(len(changes), 1)
        self.assertEqual(changes[0][-6:], ('up', '-d', '--no-deps', '--pull', 'never', 'frontend'))
        self.assertIn('--keep-until-expiring', next(c for c in self.calls if c[0] == 'certbot'))
        self.assertIn('test.example.com', (self.root / 'config/nginx.tls.conf').read_text())
        self.assertTrue((self.certs / 'renewal-hooks/deploy/eolssu-nginx').stat().st_mode & 0o111)

    def test_certificate_failure_does_not_change_runtime(self):
        self.should_fail = lambda args: args[0] == 'certbot'
        with self.assertRaises(RuntimeError):
            self.initialize()
        self.assertFalse((self.root / 'config/compose.tls.yml').exists())
        self.assertFalse(any('up' in c for c in self.calls))

    def test_invalid_nginx_does_not_change_runtime(self):
        self.should_fail = lambda args: args[:2] == ('docker', 'run')
        with self.assertRaises(RuntimeError):
            self.initialize()
        self.assertFalse((self.root / 'config/nginx.tls.conf').exists())
        self.assertFalse(any('up' in c for c in self.calls))

    def test_https_failure_restores_existing_configuration(self):
        for name in ('nginx.tls.conf', 'compose.tls.yml'):
            (self.root / 'config' / name).write_text('previous ' + name)
        self.should_fail = lambda args: args[0] == 'curl'
        with self.assertRaises(RuntimeError):
            self.initialize()
        for name in ('nginx.tls.conf', 'compose.tls.yml'):
            self.assertEqual((self.root / 'config' / name).read_text(), 'previous ' + name)
        self.assertEqual(len([c for c in self.calls if 'up' in c]), 2)

    def test_first_install_failure_restores_http_configuration(self):
        self.should_fail = lambda args: args[0] == 'curl'
        with self.assertRaises(RuntimeError):
            self.initialize()
        self.assertFalse((self.root / 'config/compose.tls.yml').exists())
        rollback = [c for c in self.calls if 'up' in c][-1]
        self.assertEqual(rollback.count('-f'), 1)

    def test_domain_rejects_path_and_config_injection(self):
        for value in ('../escape', 'example.com;id', 'example.com\nssl_verify off;', '-bad.example.com'):
            with self.assertRaises(ValueError):
                tls.initialize(value, root=self.root, certificates=self.certs)
        self.assertEqual(self.calls, [])


if __name__ == '__main__':
    unittest.main()
