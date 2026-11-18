"""격리 Nginx의 HTTP 전환·ACME·내부 상태 검사와 웹 정적 파일 압축·캐시 계약. RUN_NGINX_TESTS=1로 실행한다."""
import gzip
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
    hashed_wasm = '0123456789abcdef0123.wasm'

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
        # 실제 번들처럼 해시 이름 Wasm, 고정 이름 JS, Compose 글꼴, 이미 압축된 이미지를 둔다.
        cls.assets = {
            '/' + cls.hashed_wasm: b'\0asm' + b'wasm fixture ' * 200,
            '/webApp.js': b'console.log("fixture");\n' * 100,
            '/composeResources/font/fixture.ttf': b'ttf fixture ' * 200,
            '/composeResources/drawable/fixture.webp': b'webp fixture ' * 200,
        }
        for path, data in cls.assets.items():
            (web / path[1:]).parent.mkdir(parents=True, exist_ok=True)
            (web / path[1:]).write_bytes(data)
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

    # HTTP는 IP로 들어오는 기본 서버(nginx.conf), HTTPS는 도메인 서버(nginx.tls.conf)를 확인한다.
    @classmethod
    def fetch(cls, path, https, headers=()):
        if https:
            connection = http.client.HTTPSConnection('127.0.0.1', cls.https_port, timeout=2,
                context=ssl.create_default_context(cafile=str(cls.certificate)))
        else:
            connection = http.client.HTTPConnection('127.0.0.1', cls.http_port, timeout=2)
        try:
            connection.request('GET', path, headers={'Host': cls.domain if https else '127.0.0.1', **dict(headers)})
            response = connection.getresponse()
            return response.status, {k.lower(): v for k, v in response.getheaders()}, response.read()
        finally:
            connection.close()

    def test_static_assets_are_gzipped_only_for_clients_that_accept_it(self):
        for https in (False, True):
            for path, data in self.assets.items():
                with self.subTest(https=https, path=path):
                    status, headers, body = self.fetch(path, https, {'Accept-Encoding': 'gzip'})
                    self.assertEqual(status, 200)
                    if path.endswith('.webp'):
                        # 이미 압축된 이미지는 다시 압축하지 않는다
                        self.assertNotIn('content-encoding', headers)
                        self.assertEqual(body, data)
                    else:
                        self.assertEqual(headers.get('content-encoding'), 'gzip')
                        self.assertIn('Accept-Encoding', headers.get('vary', ''))
                        self.assertEqual(gzip.decompress(body), data)
                    status, headers, body = self.fetch(path, https, {'Accept-Encoding': 'identity'})
                    self.assertNotIn('content-encoding', headers)
                    self.assertEqual(body, data)

    def test_static_assets_keep_browser_mime_types(self):
        expected = {'/' + self.hashed_wasm: 'application/wasm', '/webApp.js': 'application/javascript',
                    '/composeResources/font/fixture.ttf': 'font/ttf', '/composeResources/drawable/fixture.webp': 'image/webp'}
        for https in (False, True):
            for path, content_type in expected.items():
                with self.subTest(https=https, path=path):
                    self.assertEqual(self.fetch(path, https)[1].get('content-type'), content_type)

    def test_only_content_hashed_wasm_is_cached_long_term(self):
        for https in (False, True):
            for path, policy in [('/' + self.hashed_wasm, 'public, max-age=31536000, immutable'),
                                 ('/webApp.js', 'no-cache'), ('/composeResources/font/fixture.ttf', 'no-cache'),
                                 ('/', 'no-cache'), ('/practice/1', 'no-cache'),
                                 # 없는 해시 Wasm은 SPA 대체 문서가 되므로 오래 보관하면 안 된다
                                 ('/ffffffffffffffffffff.wasm', 'no-cache')]:
                with self.subTest(https=https, path=path):
                    self.assertEqual(self.fetch(path, https)[1].get('cache-control'), policy)

    def test_no_cache_assets_revalidate_with_etag(self):
        for https in (False, True):
            with self.subTest(https=https):
                _, headers, _ = self.fetch('/webApp.js', https, {'Accept-Encoding': 'gzip'})
                status, _, body = self.fetch('/webApp.js', https, {'Accept-Encoding': 'gzip',
                                                                   'If-None-Match': headers['etag']})
                self.assertEqual((status, body), (304, b''))

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
