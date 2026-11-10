from datetime import datetime, timezone
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch


spec = importlib.util.spec_from_file_location(
    'r2_probe', Path(__file__).resolve().parents[1] / 'r2_probe.py',
)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

ASSET_ID = 'c64c09876e71be58f9ac1e457e42f5666e9b81e9cad0aa26806708725c3c0e86'
OBJECT_HASH = 'e6a9a9eb2fe9e0ee2506ec91aa0efe5323f022b9fa036a5c87e96285d986a81e'
OBJECT_PREFIX = bytes.fromhex('0000001c667479704d344120000002004d34412069736f6d69736f3200000662')
R2_HOST = '1' * 32 + '.r2.cloudflarestorage.com'
OBJECT_PATH = f'/eolssu-recordings/reference-audio/v1/danso/{ASSET_ID}/{OBJECT_HASH}.m4a'
SIGNATURE = 'b' * 64
SIGNED_LOCATION = (
    f'https://{R2_HOST}{OBJECT_PATH}?X-Amz-Algorithm=AWS4-HMAC-SHA256'
    f'&X-Amz-Signature={SIGNATURE}'
)


class RangeReadTests(unittest.TestCase):
    def connection(self, status, headers=None, body=b''):
        response = Mock(status=status)
        response.getheader.side_effect = (headers or {}).get
        response.read = io.BytesIO(body).read
        conn = Mock()
        conn.getresponse.return_value = response
        return conn

    def test_backend_signed_range_get_verifies_object_and_returns_sanitized_status(self):
        backend = self.connection(302, {'Location': SIGNED_LOCATION})
        r2 = self.connection(206, {
            'Content-Range': 'bytes 0-31/68500',
            'Content-Type': 'audio/mp4',
        }, OBJECT_PREFIX)
        with patch.object(probe.http.client, 'HTTPSConnection', side_effect=(backend, r2)) as connect:
            result = probe.read_probe()
        backend.request.assert_called_once_with('GET', f'/api/reference-audios/{ASSET_ID}/play')
        self.assertEqual(connect.call_args.args[0], R2_HOST)
        self.assertEqual(r2.request.call_args.kwargs['headers']['Range'], 'bytes=0-31')
        self.assertEqual(r2.request.call_args.args[0], 'GET')
        self.assertEqual(result['status'], 'ok')
        self.assertEqual(result['detail'], 'range_ok')
        self.assertNotIn(SIGNATURE, json.dumps(result))

    def test_rejects_redirects_outside_exact_r2_object(self):
        for location in (
            SIGNED_LOCATION.replace(R2_HOST, R2_HOST + '.example.com'),
            SIGNED_LOCATION.replace(OBJECT_HASH, '0' * 64),
            SIGNED_LOCATION.replace('https://', 'http://'),
            SIGNED_LOCATION.replace('X-Amz-Signature=', 'unused='),
        ):
            with self.subTest(location=location), patch.object(
                probe.http.client, 'HTTPSConnection',
                return_value=self.connection(302, {'Location': location}),
            ) as connect:
                self.assertEqual(probe.read_probe()['detail'], 'invalid_redirect')
                connect.assert_called_once()

    def test_rejects_wrong_range_or_body(self):
        for status, content_range, body, detail in (
            (200, None, OBJECT_PREFIX, 'r2_http_200'),
            (206, 'bytes 0-31/68500', b'x' * 32, 'invalid_range'),
            (206, 'bytes 0-31/68501', OBJECT_PREFIX, 'invalid_range'),
        ):
            with self.subTest(status=status, content_range=content_range), patch.object(
                probe.http.client, 'HTTPSConnection',
                side_effect=(
                    self.connection(302, {'Location': SIGNED_LOCATION}),
                    self.connection(status, {'Content-Range': content_range, 'Content-Type': 'audio/mp4'}, body),
                ),
            ):
                self.assertEqual(probe.read_probe()['detail'], detail)


class SyntheticWriteTests(unittest.TestCase):
    def setUp(self):
        self.client = Mock()
        self.client.put_object.return_value = {'ResponseMetadata': {'HTTPStatusCode': 200}}
        self.client.delete_object.return_value = {'ResponseMetadata': {'HTTPStatusCode': 204}}

    def test_reads_identical_payload_then_deletes_only_the_created_key(self):
        def read_back(**kwargs):
            payload = self.client.put_object.call_args.kwargs['Body']
            return {'ResponseMetadata': {'HTTPStatusCode': 200}, 'Body': io.BytesIO(payload),
                    'ContentLength': len(payload)}

        self.client.get_object.side_effect = read_back
        self.assertEqual(probe.write_cycle(self.client, 'test-bucket'), 'put_get_delete_ok')
        key = self.client.put_object.call_args.kwargs['Key']
        self.assertRegex(key, r'^recordings/verification/edo-check/[0-9a-f]{32}\.txt$')
        self.client.get_object.assert_called_once_with(Bucket='test-bucket', Key=key)
        self.client.delete_object.assert_called_once_with(Bucket='test-bucket', Key=key)

    def test_never_deletes_on_conflict_or_unconfirmed_put(self):
        conflict = RuntimeError('sensitive body')
        conflict.response = {'ResponseMetadata': {'HTTPStatusCode': 412}}
        for error, detail in ((conflict, 'write_put_conflict'), (TimeoutError(), 'write_put')):
            with self.subTest(detail=detail):
                self.client.put_object.side_effect = error
                self.assertEqual(probe.write_cycle(self.client, 'test-bucket'), detail)
        self.client.get_object.assert_not_called()
        self.client.delete_object.assert_not_called()

    def test_confirmed_put_is_cleaned_up_when_read_fails(self):
        self.client.get_object.side_effect = TimeoutError()
        self.assertEqual(probe.write_cycle(self.client, 'test-bucket'), 'write_read')
        self.client.delete_object.assert_called_once()
        self.client.delete_object.side_effect = TimeoutError()
        self.assertEqual(probe.write_cycle(self.client, 'test-bucket'), 'write_delete')

    def test_failed_cycle_records_new_attempt_and_preserves_last_success(self):
        with tempfile.TemporaryDirectory() as directory:
            status = Path(directory) / 'write.json'
            prior_success = '2026-11-10T08:00:01+00:00'
            status.write_text(json.dumps({'last_success_at': prior_success}))
            now = datetime(2026, 11, 10, 14, tzinfo=timezone.utc)
            probe.save_write_result(status, now, 'write_read')
            self.assertEqual(json.loads(status.read_text()), {
                'last_attempt_at': now.isoformat(), 'last_success_at': prior_success,
                'status': 'failed', 'detail': 'write_read',
            })
            self.assertEqual(status.stat().st_mode & 0o777, 0o600)
