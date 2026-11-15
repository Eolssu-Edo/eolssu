"""운영 TLS 설정의 iOS QA IPA 제공(/ipas/)과 블로그 편집 프록시 밖 경로 차단 계약. RUN_NGINX_TESTS=1로 실행한다.

블로그 편집 프록시(172.18.0.1:18121)는 테스트에 띄우지 않으므로 업스트림이 필요 없는 404 경로만 확인한다.
"""
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
DOMAIN = 'ipa.example.test'


@unittest.skipUnless(os.environ.get('RUN_NGINX_TESTS') == '1', 'requires Docker and OpenSSL')
class NginxBlogEditorAndIpaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # macOS의 Docker VM에서도 공유되는 체크아웃 아래에 fixture를 둔다.
        temp = tempfile.TemporaryDirectory(prefix='.nginx-test-', dir=ROOT)
        cls.addClassCleanup(temp.cleanup)
        root = Path(temp.name)
        conf, web, ipas = root / 'conf', root / 'web', root / 'ipas'
        live = root / 'certs' / 'live' / DOMAIN
        for directory in (conf, web, ipas / 'build-1', live):
            directory.mkdir(parents=True)
        (web / 'index.html').write_text('fixture index')
        (ipas / 'index.html').write_text('fixture ipa index')
        (ipas / 'build-1' / 'manifest.plist').write_text('<plist/>')
        (ipas / 'build-1' / 'app.ipa').write_bytes(b'PK fixture ipa')
        runtime = ROOT / 'infra/runtime'
        (conf / 'default.conf').write_bytes((runtime / 'nginx.conf').read_bytes())
        (conf / 'tls.conf').write_text((runtime / 'nginx.tls.conf.template').read_text(encoding='utf-8')
                                     .replace('__TLS_DOMAIN__', DOMAIN))
        cls.certificate = live / 'fullchain.pem'
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
                        '-subj', '/CN=' + DOMAIN, '-addext', 'subjectAltName=DNS:' + DOMAIN + ',IP:127.0.0.1',
                        '-keyout', str(live / 'privkey.pem'), '-out', str(cls.certificate)],
                       check=True, capture_output=True)
        cls.container = 'eolssu-nginx-ipa-test-' + uuid.uuid4().hex[:12]
        cls.addClassCleanup(subprocess.run, ['docker', 'rm', '-f', cls.container], capture_output=True, check=False)
        # 운영 compose.yml과 같은 위치에 IPA 폴더를 읽기 전용으로 붙인다.
        subprocess.run(['docker', 'run', '--detach', '--name', cls.container, '--publish', '127.0.0.1::443',
                        '--mount', f'type=bind,src={conf},dst=/etc/nginx/conf.d,readonly',
                        '--mount', f'type=bind,src={web},dst=/usr/share/nginx/html,readonly',
                        '--mount', f'type=bind,src={ipas},dst=/usr/share/nginx/ipas,readonly',
                        '--mount', f'type=bind,src={root / "certs"},dst=/etc/letsencrypt,readonly',
                        'nginx:1.30.6-alpine'], check=True, capture_output=True)
        ports = json.loads(subprocess.check_output(
            ['docker', 'inspect', '--format', '{{json .NetworkSettings.Ports}}', cls.container]))
        cls.port = int(ports['443/tcp'][0]['HostPort'])
        deadline = time.monotonic() + 10
        while True:
            try:
                cls.get('/')
                break
            except OSError:
                if time.monotonic() >= deadline:
                    raise
                time.sleep(0.05)

    @classmethod
    def get(cls, path):
        connection = http.client.HTTPSConnection('127.0.0.1', cls.port, timeout=2,
            context=ssl.create_default_context(cafile=str(cls.certificate)))
        try:
            connection.request('GET', path, headers={'Host': DOMAIN})
            response = connection.getresponse()
            return response.status, {k.lower(): v for k, v in response.getheaders()}, response.read()
        finally:
            connection.close()

    def test_ipa_index_is_served_without_cache_or_indexing(self):
        status, headers, body = self.get('/ipas/')
        self.assertEqual((status, body), (200, b'fixture ipa index'))
        self.assertTrue(headers['content-type'].startswith('text/html'))
        self.assertEqual(headers.get('cache-control'), 'no-store')
        self.assertEqual(headers.get('x-content-type-options'), 'nosniff')
        self.assertEqual(headers.get('x-robots-tag'), 'noindex')

    def test_ipa_manifest_and_package_use_ota_content_types(self):
        for path, content_type, body in [('/ipas/build-1/manifest.plist', 'application/xml', b'<plist/>'),
                                         ('/ipas/build-1/app.ipa', 'application/octet-stream', b'PK fixture ipa')]:
            with self.subTest(path=path):
                status, headers, data = self.get(path)
                self.assertEqual((status, data), (200, body))
                self.assertEqual(headers['content-type'], content_type)

    def test_ipa_build_folder_is_not_listed_and_missing_file_is_not_the_spa(self):
        self.assertEqual(self.get('/ipas/build-1/')[0], 403)
        status, _, body = self.get('/ipas/missing.ipa')
        self.assertEqual(status, 404)
        self.assertNotEqual(body, b'fixture index')

    def test_blog_editor_paths_outside_the_proxy_are_404_not_the_spa(self):
        for path in ['/blog-editor', '/blog-editor/', '/blog-editor/unknown', '/blog-editor/config/extra',
                     '/blog-editor/session/', '/blog-editor/admin']:
            with self.subTest(path=path):
                status, _, body = self.get(path)
                self.assertEqual(status, 404)
                self.assertNotEqual(body, b'fixture index')

    def test_nginx_configuration_is_valid(self):
        subprocess.run(['docker', 'exec', self.container, 'nginx', '-t'], check=True, capture_output=True)


if __name__ == '__main__':
    unittest.main()
