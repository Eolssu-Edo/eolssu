"""Actual boto3 PUTs against a loopback-only S3 HTTP fixture; no real credentials."""
import base64
from contextlib import redirect_stdout
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
import io
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from urllib.parse import unquote

from test_upload import module


@unittest.skipUnless(importlib.util.find_spec('boto3'), 'boto3 required for local HTTP integration')
class HttpTests(unittest.TestCase):
    def test_cli_upload_retry_and_overwrite_use_actual_signed_conditional_puts(self):
        objects, requests = {}, []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_PUT(self):
                key = unquote(self.path)
                body = self.rfile.read(int(self.headers['Content-Length']))
                requests.append((key, dict(self.headers), body))
                if key in objects and self.headers.get('If-None-Match') == '*':
                    response = b'<Error><Code>PreconditionFailed</Code></Error>'
                    self.send_response(412)
                    self.send_header('Content-Type', 'application/xml')
                else:
                    objects[key] = body
                    response = b''
                    self.send_response(200)
                    self.send_header('ETag', '"fixture-etag"')
                self.send_header('Content-Length', str(len(response)))
                self.end_headers()
                self.wfile.write(response)

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            settings = dict(ENDPOINT=f'http://127.0.0.1:{server.server_port}', BUCKET='bucket',
                            ACCESS_KEY_ID='fixture-key', SECRET_ACCESS_KEY='fixture-secret', REGION='auto')
            client = module.make_client(settings)
            with tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                (root / 'nested').mkdir()
                source = root / 'nested' / '한글 소리.m4a'
                source.write_bytes(b'original-audio')
                env = root.parent / (root.name + '.env')
                try:
                    # A valid production-shaped endpoint is parsed; only the factory boundary is redirected to loopback.
                    env.write_text('\n'.join(module.PREFIX + k + '=' + ('https://fixture.r2.cloudflarestorage.com' if k == 'ENDPOINT' else v)
                                             for k, v in settings.items()))
                    args = [str(root), 'dev-assets/team/', '--env-file', str(env)]
                    out = io.StringIO()
                    with patch.object(module, 'make_client', return_value=client), patch.object(client, 'close'), redirect_stdout(out):
                        self.assertEqual(module.main(args), 0)
                        source.write_bytes(b'replacement-audio')
                        self.assertEqual(module.main(args), 0)
                        key = '/bucket/dev-assets/team/nested/한글 소리.m4a'
                        self.assertEqual(objects[key], b'original-audio')
                        self.assertEqual(module.main(args + ['--overwrite']), 0)
                        self.assertEqual(objects[key], b'replacement-audio')
                    self.assertIn('SKIP existing', out.getvalue())
                    self.assertNotIn('fixture-secret', out.getvalue())
                finally:
                    env.unlink(missing_ok=True)
            self.assertEqual(len(requests), 3)
            for _, headers, body in requests:
                lower = {key.lower(): value for key, value in headers.items()}
                self.assertTrue(lower['authorization'].startswith('AWS4-HMAC-SHA256 '))
                self.assertEqual(lower['content-type'], 'audio/mp4')
                self.assertEqual(lower['content-md5'], base64.b64encode(hashlib.md5(body).digest()).decode())
                self.assertEqual(lower['x-amz-meta-sha256'], hashlib.sha256(body).hexdigest())
            self.assertEqual(requests[0][1].get('If-None-Match'), '*')
            self.assertNotIn('If-None-Match', requests[-1][1])
            client.close()
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=5)
