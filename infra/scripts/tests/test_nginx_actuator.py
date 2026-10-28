"""백엔드로 넘기는 Nginx 설정 3개가 점검 주소(actuator) 중 readiness만 넘기는지 확인한다. RUN_NGINX_TESTS=1로 실행한다."""
import http.client
import json
import os
from pathlib import Path
import ssl
import subprocess
import tempfile
import time
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[3]
DOMAIN = 'actuator.example.test'
FAKE_BACKEND = 'server { listen 8080; location / { return 200 "backend $request_uri"; } }\n'
PASSED = ['/api/actuator/health/readiness', '/api/infra-smoke']
BLOCKED = ['/api/actuator', '/api/actuator/', '/api/actuator/health', '/api/actuator/health/liveness',
           '/api/actuator/prometheus', '/api/actuator/env',
           # 우회 시도: Nginx가 경로를 정규화한 뒤 고르므로 모두 막혀야 한다
           '/api//actuator/prometheus', '/api/%61ctuator/prometheus',
           '/api/actuator/health/readiness/../../prometheus', '/api/actuator/health/readiness/',
           # Spring은 경로 조각의 ';' 뒤를 떼어 내므로 Nginx가 다른 경로로 보는 요청이 백엔드에서 actuator가 된다
           '/api;x/actuator/prometheus', '/api;/actuator/health', '/ap%69;x/actuator/prometheus',
           '/api/;x/actuator/prometheus', '/api/actuator/health/readiness;x', '/api/%3B/actuator/prometheus']


@unittest.skipUnless(os.environ.get('RUN_NGINX_TESTS') == '1', 'requires Docker and OpenSSL')
class NginxActuatorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        temp = tempfile.TemporaryDirectory(prefix='.nginx-test-', dir=ROOT)
        cls.addClassCleanup(temp.cleanup)
        root = Path(temp.name)
        tag = uuid.uuid4().hex[:12]
        network = 'eolssu-actuator-test-' + tag
        subprocess.run(['docker', 'network', 'create', network], check=True, capture_output=True)
        cls.addClassCleanup(subprocess.run, ['docker', 'network', 'rm', network], capture_output=True, check=False)

        backend = root / 'backend'
        backend.mkdir()
        (backend / 'default.conf').write_text(FAKE_BACKEND)
        cls.start(network, 'backend-' + tag, backend, alias='backend')

        live = root / 'certs' / 'live' / DOMAIN
        live.mkdir(parents=True)
        cls.certificate = live / 'fullchain.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=' + DOMAIN, '-addext', 'subjectAltName=DNS:' + DOMAIN + ',IP:127.0.0.1',
                        '-keyout', str(live / 'privkey.pem'), '-out', str(cls.certificate)],
                       check=True, capture_output=True)
        configs = {
            'runtime': (ROOT / 'infra/runtime/nginx.conf').read_text(encoding='utf-8'),
            'runtime-tls': (ROOT / 'infra/runtime/nginx.tls.conf.template').read_text(encoding='utf-8').replace('__TLS_DOMAIN__', DOMAIN),
            'dev': (ROOT / 'infra/nginx/default.conf').read_text(encoding='utf-8'),
        }
        cls.ports = {}
        for name, text in configs.items():
            conf = root / name
            conf.mkdir()
            (conf / 'default.conf').write_text(text)
            cls.ports[name] = cls.start(network, f'{name}-{tag}', conf, certs=root / 'certs')
        for name in configs:
            cls.wait(name)

    @classmethod
    def start(cls, network, name, conf, alias=None, certs=None):
        container = 'eolssu-actuator-test-' + name
        cls.addClassCleanup(subprocess.run, ['docker', 'rm', '-f', container], capture_output=True, check=False)
        command = ['docker', 'run', '--detach', '--name', container, '--network', network,
                   '--mount', f'type=bind,src={conf},dst=/etc/nginx/conf.d,readonly']
        if alias:
            command += ['--network-alias', alias]
        else:
            command += ['--publish', '127.0.0.1::80', '--publish', '127.0.0.1::443',
                        '--mount', f'type=bind,src={certs},dst=/etc/letsencrypt,readonly']
        subprocess.run(command + ['nginx:1.30.6-alpine'], check=True, capture_output=True)
        if alias:
            return None
        ports = json.loads(subprocess.check_output(
            ['docker', 'inspect', '--format', '{{json .NetworkSettings.Ports}}', container]))
        return {p: int(ports[f'{p}/tcp'][0]['HostPort']) for p in (80, 443)}

    @classmethod
    def wait(cls, name):
        deadline = time.monotonic() + 10
        while True:
            try:
                return cls.request(name, '/api/infra-smoke')
            except OSError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.05)

    @classmethod
    def request(cls, name, path):
        if name == 'runtime-tls':
            connection = http.client.HTTPSConnection('127.0.0.1', cls.ports[name][443], timeout=2,
                                                     context=ssl.create_default_context(cafile=str(cls.certificate)))
        else:
            connection = http.client.HTTPConnection('127.0.0.1', cls.ports[name][80], timeout=2)
        try:
            connection.request('GET', path, headers={'Host': DOMAIN})
            response = connection.getresponse()
            return response.status, response.read().decode()
        finally:
            connection.close()

    def test_readiness_and_ordinary_api_reach_backend(self):
        for name in self.ports:
            for path in PASSED:
                with self.subTest(config=name, path=path):
                    self.assertEqual(self.request(name, path), (200, 'backend ' + path))

    def test_other_actuator_paths_never_reach_backend(self):
        for name in self.ports:
            for path in BLOCKED:
                with self.subTest(config=name, path=path):
                    status, body = self.request(name, path)
                    self.assertEqual(status, 404)
                    self.assertNotIn('backend', body)


if __name__ == '__main__':
    unittest.main()
