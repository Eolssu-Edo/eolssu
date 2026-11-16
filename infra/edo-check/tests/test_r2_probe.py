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


class SheetStorageTests(unittest.TestCase):
    kind = 'sheet'
    bucket = 'eolssu-sheet-audio'
    prefix = 'sheet-audio'

    def cycle(self):
        return probe.storage_cycle(self.client, self.bucket, R2_HOST, self.kind)

    def setUp(self):
        self.client = Mock()
        self.client.put_object.return_value = {'ResponseMetadata': {'HTTPStatusCode': 200}}
        self.client.delete_object.return_value = {'ResponseMetadata': {'HTTPStatusCode': 204}}
        gone = RuntimeError('provider error with private metadata')
        gone.response = {'ResponseMetadata': {'HTTPStatusCode': 404}}
        self.client.head_object.side_effect = gone

    def test_writes_checks_signed_download_and_verifies_removal(self):
        response = Mock(status=200)
        connection = Mock()
        connection.getresponse.return_value = response

        def signed_url(operation, Params, ExpiresIn):
            self.assertEqual(operation, 'get_object')
            self.assertEqual(Params['Bucket'], self.bucket)
            self.assertLessEqual(ExpiresIn, 90)
            return (f"https://{R2_HOST}/{Params['Bucket']}/{Params['Key']}"
                    f"?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature={SIGNATURE}")

        self.client.generate_presigned_url.side_effect = signed_url
        response.read.side_effect = lambda _: self.client.put_object.call_args.kwargs['Body']
        with patch.object(probe.http.client, 'HTTPSConnection', return_value=connection):
            result = self.cycle()
        self.assertEqual(result, 'put_signed_get_delete_ok')
        key = self.client.put_object.call_args.kwargs['Key']
        self.assertRegex(key, '^' + self.prefix + r'/verification/edo-check/[0-9a-f]{32}\.txt$')
        connection.request.assert_called_once()
        self.assertEqual(connection.request.call_args.args[0], 'GET')
        self.assertEqual(connection.request.call_args.kwargs, {})
        self.client.delete_object.assert_called_once_with(Bucket=self.bucket, Key=key)
        self.client.head_object.assert_called_once_with(Bucket=self.bucket, Key=key)
        self.client.get_object.assert_not_called()

    def test_invalid_signature_target_is_never_contacted_and_the_object_is_deleted(self):
        self.client.generate_presigned_url.return_value = 'https://example.invalid/?X-Amz-Signature=hidden'
        with patch.object(probe.http.client, 'HTTPSConnection') as connect:
            self.assertEqual(self.cycle(), self.kind + '_download')
        connect.assert_not_called()
        self.client.delete_object.assert_called_once()

    def test_conflict_never_deletes_and_uncertain_write_checks_its_own_key(self):
        conflict = RuntimeError('private provider detail')
        for code in (409, 412):
            conflict.response = {'ResponseMetadata': {'HTTPStatusCode': code}}
            self.client.put_object.side_effect = conflict
            self.assertEqual(self.cycle(), self.kind + '_conflict')
        self.client.delete_object.assert_not_called()
        self.client.generate_presigned_url.assert_not_called()
        self.client.put_object.side_effect = TimeoutError('private provider detail')
        self.assertEqual(self.cycle(), self.kind + '_put_uncertain')
        self.client.delete_object.assert_called_once_with(**{
            key: self.client.put_object.call_args.kwargs[key] for key in ('Bucket', 'Key')
        })

    def test_uncertain_put_with_cleanup_failure_keeps_both_results(self):
        self.client.put_object.side_effect = TimeoutError('private provider detail')
        self.client.delete_object.side_effect = TimeoutError('private provider detail')
        self.assertEqual(self.cycle(), self.kind + '_put_uncertain_delete')
        self.client.head_object.assert_not_called()

    def test_requires_absence_after_delete(self):
        with patch.object(probe, 'signed_download_matches', return_value=True):
            self.client.head_object.side_effect = None
            self.client.head_object.return_value = {'ResponseMetadata': {'HTTPStatusCode': 200}}
            self.assertEqual(self.cycle(), self.kind + '_delete')
            for code in (403, 500):
                error = RuntimeError('private provider detail')
                error.response = {'ResponseMetadata': {'HTTPStatusCode': code}}
                self.client.head_object.side_effect = error
                self.assertEqual(self.cycle(), self.kind + '_delete')

    def test_failed_download_still_checks_cleanup_and_preserves_both_failures(self):
        self.client.generate_presigned_url.return_value = (
            f'https://{R2_HOST}/{self.bucket}/wrong-target'
            f'?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature={SIGNATURE}')
        self.client.delete_object.side_effect = TimeoutError('private provider detail')
        with patch.object(probe.http.client, 'HTTPSConnection') as connection:
            self.assertEqual(self.cycle(), self.kind + '_download_delete')
        connection.assert_not_called()
        self.client.head_object.assert_not_called()

    def test_download_rejects_http_errors_and_different_or_extra_bytes(self):
        def signed_url(operation, Params, ExpiresIn):
            return (f"https://{R2_HOST}/{Params['Bucket']}/{Params['Key']}"
                    f"?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature={SIGNATURE}")

        self.client.generate_presigned_url.side_effect = signed_url
        for status, suffix in ((403, b''), (404, b''), (200, b'extra'), (200, None)):
            connection = Mock()
            response = connection.getresponse.return_value
            response.status = status
            def downloaded_body(_):
                payload = self.client.put_object.call_args.kwargs['Body']
                return b'x' * len(payload) if suffix is None else payload + suffix

            response.read.side_effect = downloaded_body
            with self.subTest(status=status, suffix=suffix), patch.object(
                    probe.http.client, 'HTTPSConnection', return_value=connection):
                self.assertEqual(self.cycle(), self.kind + '_download')
        self.assertEqual(self.client.delete_object.call_count, 4)


class RecordingStorageTests(SheetStorageTests):
    kind = 'recording'
    bucket = 'eolssu-recordings'
    prefix = 'recordings'


class ActiveStorageSettingsTests(unittest.TestCase):
    def test_active_recording_settings_are_selected_without_exposing_values(self):
        settings = {
            'ENDPOINT': f'https://{R2_HOST}', 'BUCKET': 'eolssu-recordings',
            'ACCESS_KEY_ID': 'private-access-key', 'SECRET_ACCESS_KEY': 'private-secret-key',
            'REGION': 'auto',
        }
        entries = ['RECORDING_STORAGE_' + name + '=' + value for name, value in settings.items()]
        entries.append('SHEET_STORAGE_SECRET_ACCESS_KEY=other-private-secret')
        outputs = [Mock(stdout='eolssu-runtime-backend-green-1\n'), Mock(stdout=json.dumps(entries))]
        with patch.object(probe.subprocess, 'run', side_effect=outputs):
            self.assertEqual(probe.active_storage_settings('RECORDING_STORAGE_', probe.BUCKET), settings)

    def test_recording_cli_uses_active_configuration_and_prints_only_allowlisted_results(self):
        settings = {'ENDPOINT': f'https://{R2_HOST}', 'BUCKET': 'eolssu-recordings'}
        output = io.StringIO()
        with patch.object(probe.sys, 'argv', ['r2_probe.py', 'recording']), \
                patch.object(probe, 'active_storage_settings', return_value=settings) as active, \
                patch.object(probe, 'write_client') as client, \
                patch.object(probe, 'storage_cycle', return_value='put_signed_get_delete_ok') as cycle, \
                patch('sys.stdout', output):
            self.assertEqual(probe.main(), 0)
        active.assert_called_once_with('RECORDING_STORAGE_', probe.BUCKET)
        cycle.assert_called_once_with(client.return_value, probe.BUCKET, R2_HOST, 'recording')
        result = json.loads(output.getvalue())
        self.assertEqual(set(result), {'status', 'detail', 'elapsed_ms'})
        self.assertEqual(result['status'], 'ok')

    def test_private_configuration_exceptions_are_reduced_to_a_fixed_failure(self):
        for kind in ('sheet', 'recording'):
            with self.subTest(kind=kind), patch.object(probe, 'active_storage_settings', side_effect=ValueError(
                    'private-access-key private-secret-key ' + SIGNED_LOCATION)):
                result = probe.check_storage(kind)
            self.assertEqual(result['detail'], kind + '_configuration')
            self.assertEqual(result['status'], 'failed')
            self.assertNotIn('private', json.dumps(result))
            self.assertNotIn('X-Amz', json.dumps(result))
