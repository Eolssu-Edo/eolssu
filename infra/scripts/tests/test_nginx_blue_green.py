"""Real HTTPS requests through the production Nginx configs during a slot change."""
import concurrent.futures
import hashlib
import http.client
import importlib.util
import os
from pathlib import Path
import ssl
import subprocess
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import uuid

ROOT = Path(__file__).resolve().parents[3]
DOMAIN = 'blue-green.example.test'
spec = importlib.util.spec_from_file_location('deploy_backend', ROOT / 'infra/jenkins/deploy-backend.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)

UPSTREAM = '''from hashlib import sha256
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys
import time

slot = sys.argv[1]
gate = Path('/fixture')
class Handler(BaseHTTPRequestHandler):
    def reply(self, text):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(text.encode())
    def do_GET(self):
        self.reply(slot)
    def do_POST(self):
        body = self.rfile.read(int(self.headers['Content-Length']))
        (gate / 'entered').write_text(slot)
        until = time.monotonic() + 20
        while not (gate / 'release').exists() and time.monotonic() < until:
            time.sleep(0.02)
        self.reply(slot + ':' + sha256(body).hexdigest())

ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
'''


@unittest.skipUnless(os.environ.get('RUN_NGINX_TESTS') == '1', 'requires Docker and OpenSSL')
class BlueGreenNginxTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix='.blue-green-test-', dir=ROOT)
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.deployer = deploy.Deployer(self.root)
        route = self.deployer.route.parent
        route.mkdir(parents=True)
        self.deployer.route.write_text('set $backend http://backend:8080;\n')
        fixture = self.root / 'fixture'
        fixture.mkdir()
        self.fixture = fixture
        (fixture / 'upstream.py').write_text(UPSTREAM)

        tag = 'eolssu-blue-green-test-' + uuid.uuid4().hex[:12]
        self.docker('network', 'create', tag)
        self.addCleanup(self.remove, 'network', 'rm', tag)
        for slot in deploy.SLOTS:
            name = tag + '-' + slot
            self.addCleanup(self.remove, 'rm', '-f', name)
            self.docker('run', '-d', '--pull', 'never', '--network', tag, '--network-alias', slot,
                        '--name', name, '--mount', f'type=bind,src={fixture},dst=/fixture',
                        'python:3.11.16-slim-trixie', 'python', '/fixture/upstream.py', slot)

        config = self.root / 'nginx'
        config.mkdir()
        runtime = ROOT / 'infra/runtime'
        (config / 'default.conf').write_bytes((runtime / 'nginx.conf').read_bytes())
        (config / 'tls.conf').write_text((runtime / 'nginx.tls.conf.template').read_text().replace('__TLS_DOMAIN__', DOMAIN))
        certs = self.root / 'certs'
        live = certs / 'live' / DOMAIN
        live.mkdir(parents=True)
        certificate = live / 'fullchain.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=' + DOMAIN, '-addext', 'subjectAltName=IP:127.0.0.1',
                        '-keyout', str(live / 'privkey.pem'), '-out', str(certificate)],
                       check=True, capture_output=True)
        self.ssl = ssl.create_default_context(cafile=str(certificate))
        front = tag + '-frontend'
        self.addCleanup(self.remove, 'rm', '-f', front)
        self.docker('run', '-d', '--pull', 'never', '--name', front, '--network', tag, '-p', '127.0.0.1::443',
                    '--mount', f'type=bind,src={config},dst=/etc/nginx/conf.d,readonly',
                    '--mount', f'type=bind,src={route},dst=/etc/nginx/backend-route,readonly',
                    '--mount', f'type=bind,src={certs},dst=/etc/letsencrypt,readonly',
                    'nginx:1.30.6-alpine')
        self.port = int(self.docker('port', front, '443/tcp').rsplit(':', 1)[1])
        self.patch_front(front)
        self.wait_for(lambda: self.request('/api/release') == (200, b'backend'))

    def patch_front(self, front):
        patcher = patch.object(deploy, 'FRONTEND', front)
        patcher.start()
        self.addCleanup(patcher.stop)

    @staticmethod
    def docker(*args):
        return subprocess.check_output(['docker', *args], text=True, timeout=20).strip()

    @staticmethod
    def remove(*args):
        subprocess.run(['docker', *args], check=True, capture_output=True, timeout=20)

    def request(self, path, body=None):
        connection = http.client.HTTPSConnection('127.0.0.1', self.port, timeout=25, context=self.ssl)
        try:
            connection.request('GET' if body is None else 'POST', path, body=body, headers={'Host': DOMAIN})
            response = connection.getresponse()
            self.assertIsNone(response.getheader('X-Eolssu-Backend-Probe'))
            return response.status, response.read()
        finally:
            connection.close()

    def wait_for(self, check):
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            try:
                if check():
                    return
            except OSError:
                pass
            time.sleep(0.05)
        self.fail('condition did not become true')

    def test_https_traffic_and_inflight_upload_survive_cutover_and_rollback(self):
        body = b'upload-fixture' * 100_000
        seen = []
        failures = []
        stop = threading.Event()

        def continuous_requests():
            while not stop.is_set():
                try:
                    status, version = self.request('/api/release')
                    if status == 200:
                        seen.append(version)
                    else:
                        failures.append(status)
                except OSError as error:
                    failures.append(type(error).__name__)
                stop.wait(0.01)

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            ongoing = pool.submit(continuous_requests)
            upload = pool.submit(self.request, '/api/upload', body)
            try:
                self.wait_for(lambda: (self.fixture / 'entered').exists() and
                              (self.fixture / 'entered').read_text() == 'backend')
                old_workers = self.deployer.worker_pids()
                self.deployer.switch_route('backend-green')
                self.wait_for(lambda: b'backend-green' in seen)
                self.assertEqual(self.request(deploy.READINESS_PATH), (200, b'backend-green'))
                local = self.docker('exec', deploy.FRONTEND, 'sh', '-c',
                                    'wget -S -O- -T 3 --no-check-certificate --header="Host: $1" "$2" 2>&1',
                                    'probe', DOMAIN, 'https://127.0.0.1' + deploy.READINESS_PATH)
                self.assertIn('X-Eolssu-Backend-Probe: http://backend-green:8080', local)
                self.assertTrue(old_workers & self.deployer.worker_pids())
                (self.fixture / 'release').touch()
                self.assertEqual(upload.result(timeout=15), (200, b'backend:' + hashlib.sha256(body).hexdigest().encode()))
                self.wait_for(lambda: not old_workers & self.deployer.worker_pids())
                self.deployer.switch_route('backend')
                self.wait_for(lambda: seen[-1:] == [b'backend'])
            finally:
                (self.fixture / 'release').touch()
                stop.set()
                ongoing.result(timeout=25)
        self.assertFalse(failures)
        self.assertIn(b'backend', seen)
        self.assertIn(b'backend-green', seen)
        print(f'{len(seen)} consecutive HTTPS requests, {len(failures)} failures, upload sha256 matched')
