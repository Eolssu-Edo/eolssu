"""격리 Nginx의 HTTP 전환·ACME·내부 상태 검사 계약. RUN_NGINX_TESTS=1로 실행한다."""
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


@unittest.skipUnless(os.environ.get('RUN_NGINX_TESTS') == '1', 'requires Docker and OpenSSL')
class NginxRedirectTests(unittest.TestCase):
    domain = 'redirect.example.test'

    @classmethod
    def setUpClass(cls):
        # macOS의 Docker VM에서도 공유되는 체크아웃 아래에 fixture를 둔다.
        temp = tempfile.TemporaryDirectory(prefix='.nginx-test-', dir=Path(__file__).resolve().parents[3])
        cls.addClassCleanup(temp.cleanup)
        root = Path(temp.name)
        conf, web, certs = (root / name for name in ('conf', 'web', 'certs'))
        live = certs / 'live' / cls.domain
        for directory in (conf, web, live):
            directory.mkdir(parents=True)
        (web / 'index.html').write_text('fixture index')
        challenge = web / '.well-known/acme-challenge'
        challenge.mkdir(parents=True)
        (challenge / 'fixture-token').write_text('fixture authorization')
        runtime = Path(__file__).resolve().parents[2] / 'runtime'
        (conf / 'default.conf').write_bytes((runtime / 'nginx.conf').read_bytes())
        (conf / 'tls.conf').write_text((runtime / 'nginx.tls.conf.template').read_text(encoding='utf-8')
                                     .replace('__TLS_DOMAIN__', cls.domain))
        cls.certificate = live / 'fullchain.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=' + cls.domain,
                        '-addext', 'subjectAltName=DNS:' + cls.domain + ',IP:127.0.0.1',
                        '-keyout', str(live / 'privkey.pem'), '-out', str(cls.certificate)],
                       check=True, capture_output=True)
        cls.container = 'eolssu-nginx-test-' + uuid.uuid4().hex[:12]
        cls.addClassCleanup(subprocess.run, ['docker', 'rm', '-f', cls.container],
                            capture_output=True, check=False)
        subprocess.run(['docker', 'run', '--detach', '--name', cls.container,
                        '--publish', '127.0.0.1::80', '--publish', '127.0.0.1::443',
                        '--mount', f'type=bind,src={conf},dst=/etc/nginx/conf.d,readonly',
                        '--mount', f'type=bind,src={web},dst=/usr/share/nginx/html,readonly',
                        '--mount', f'type=bind,src={certs},dst=/etc/letsencrypt,readonly',
                        'nginx:1.30.6-alpine'], check=True, capture_output=True)
        ports = json.loads(subprocess.check_output(
            ['docker', 'inspect', '--format', '{{json .NetworkSettings.Ports}}', cls.container]))
        cls.http_port = int(ports['80/tcp'][0]['HostPort'])
        cls.https_port = int(ports['443/tcp'][0]['HostPort'])
        deadline = time.monotonic() + 10
        while True:
            try:
                cls.request('/', host='127.0.0.1')
                cls.request('/', https=True)
                break
            except OSError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.05)

    @classmethod
    def request(cls, path, host=None, method='GET', https=False):
        if https:
            connection = http.client.HTTPSConnection('127.0.0.1', cls.https_port, timeout=2,
                context=ssl.create_default_context(cafile=str(cls.certificate)))
        else:
            connection = http.client.HTTPConnection('127.0.0.1', cls.http_port, timeout=2)
        try:
            connection.request(method, path, headers={'Host': host or cls.domain})
            response = connection.getresponse()
            return response.status, response.getheader('Location'), response.read()
        finally:
            connection.close()

    def test_public_requests_keep_path_query_and_method_preserving_status(self):
        for method, path in [('GET', '/'), ('HEAD', '/nested?x=1&y=two%20words'),
                             ('POST', '/api/infra-smoke?source=http')]:
            with self.subTest(method=method, path=path):
                status, location, _ = self.request(path, method=method)
                self.assertEqual(status, 308)
                self.assertEqual(location, 'https://' + self.domain + path)

    def test_acme_token_is_served_without_redirect(self):
        self.assertEqual(self.request('/.well-known/acme-challenge/fixture-token'),
                         (200, None, b'fixture authorization'))

    def test_missing_acme_token_is_not_the_spa_or_a_redirect(self):
        status, location, _ = self.request('/.well-known/acme-challenge/missing')
        self.assertEqual((status, location), (404, None))

    def test_loopback_health_probe_stays_http(self):
        self.assertEqual(self.request('/', host='127.0.0.1'), (200, None, b'fixture index'))

    def test_unrelated_host_is_not_used_as_redirect_target(self):
        _, location, _ = self.request('/', host='untrusted.example')
        self.assertIsNone(location)

    def test_https_serves_frontend_with_validated_fixture_certificate(self):
        self.assertEqual(self.request('/', https=True), (200, None, b'fixture index'))

    def test_nginx_configuration_is_valid(self):
        subprocess.run(['docker', 'exec', self.container, 'nginx', '-t'],
                       check=True, capture_output=True)


if __name__ == '__main__':
    unittest.main()
