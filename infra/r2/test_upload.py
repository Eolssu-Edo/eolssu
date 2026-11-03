import importlib.util
import io
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('r2_upload', Path(__file__).with_name('upload.py'))
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class UploadTests(unittest.TestCase):
    def test_file_and_nested_folder_preserve_bytes_and_destination(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / '중첩 폴더').mkdir()
            source = root / '중첩 폴더' / '소리.m4a'
            source.write_bytes(b'\x00audio\xff')
            single = module.inventory(source, 'dev-assets/one.m4a')
            tree = module.inventory(root, 'dev-assets/team/')
            self.assertEqual(single[0][1], 'dev-assets/one.m4a')
            self.assertEqual(tree[0][1], 'dev-assets/team/중첩 폴더/소리.m4a')
            class Client:
                def put_object(self, **kwargs):
                    self.request = dict(kwargs, Body=kwargs['Body'].read())
            client = Client()
            module.upload(client, 'bucket', tree, output=io.StringIO())
            self.assertEqual(client.request['Body'], b'\x00audio\xff')
            self.assertEqual(client.request['IfNoneMatch'], '*')
            self.assertEqual(client.request['ContentType'], 'audio/mp4')


class Failure(Exception):
    def __init__(self, code):
        self.response = {'Error': {'Code': code}}
        super().__init__('SECRET: signed-url-and-credentials')


class GuardTests(unittest.TestCase):
    def test_dry_run_env_file_is_literal_and_never_creates_client(self):
        from unittest.mock import patch
        from contextlib import redirect_stdout
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / '한글 file.txt'
            source.write_text('hello')
            env = root / '.env'
            env.write_text('RECORDING_STORAGE_ENDPOINT=https://example.r2.cloudflarestorage.com\n'
                           'RECORDING_STORAGE_BUCKET=bucket\n'
                           'export RECORDING_STORAGE_ACCESS_KEY_ID="key" # comment\n'
                           "RECORDING_STORAGE_SECRET_ACCESS_KEY='$(touch never-run); $SECRET'\n"
                           'UNRELATED="unclosed\n')
            out = io.StringIO()
            with patch.object(module, 'make_client', side_effect=AssertionError('network')), redirect_stdout(out):
                self.assertEqual(module.main([str(source), 'dev-assets/team/', '--env-file', str(env), '--dry-run']), 0)
            self.assertIn('dev-assets/team/한글 file.txt', out.getvalue())
            self.assertNotIn('SECRET', out.getvalue())
            self.assertEqual(module.read_settings(env)['SECRET_ACCESS_KEY'], '$(touch never-run); $SECRET')
            self.assertEqual(module.read_settings(env)['REGION'], 'auto')

    def test_environment_and_explicit_file_are_not_mixed(self):
        from unittest.mock import patch
        complete = {module.PREFIX + field: value for field, value in zip(module.FIELDS,
                    ('https://example.r2.cloudflarestorage.com', 'bucket', 'key', 'secret', 'auto'))}
        with patch.dict(module.os.environ, complete, clear=True):
            self.assertEqual(module.read_settings()['BUCKET'], 'bucket')
            with tempfile.TemporaryDirectory() as temporary:
                env = Path(temporary) / '.env'
                env.write_text('RECORDING_STORAGE_BUCKET=other\n')
                with self.assertRaisesRegex(ValueError, '설정 누락'):
                    module.read_settings(env)

    def test_collision_skips_without_claiming_content_and_overwrite_is_explicit(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'sample.bin'
            source.write_bytes(b'payload')
            class Client:
                def put_object(self, **kwargs):
                    self.request = kwargs
                    if 'IfNoneMatch' in kwargs:
                        raise Failure('PreconditionFailed')
            client, out = Client(), io.StringIO()
            self.assertEqual(module.upload(client, 'bucket', [(source, 'dev-assets/a')], output=out), (0, 1))
            self.assertIn('SKIP existing', out.getvalue())
            self.assertEqual(module.upload(client, 'bucket', [(source, 'dev-assets/a')], overwrite=True, output=out), (1, 0))
            self.assertNotIn('IfNoneMatch', client.request)

    def test_failure_stops_later_files_and_does_not_expose_sdk_exception(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'file'
            source.write_bytes(b'data')
            class Client:
                calls = 0
                def put_object(self, **kwargs):
                    self.calls += 1
                    if self.calls == 2:
                        raise Failure('AccessDenied')
            client, out = Client(), io.StringIO()
            with self.assertRaises(RuntimeError) as caught:
                module.upload(client, 'bucket', [(source, 'dev-assets/' + key) for key in ('a', 'b', 'c')], output=out)
            self.assertEqual(client.calls, 2)
            self.assertIn('UPLOADED', out.getvalue())
            self.assertNotIn('SECRET', str(caught.exception) + out.getvalue())

    def test_rejects_protected_keys_links_secrets_and_oversize_before_upload(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / 'file'
            source.write_bytes(b'data')
            for key in ('recordings/a', 'recordings', '../a', '/a', 'a//b', 'a/../b', 'a\\b', 'a\nb', '가' * 342):
                with self.subTest(key=key), self.assertRaises(ValueError):
                    module.inventory(source, key)
            for name in ('.env', '.env.local'):
                secret = root / name
                secret.write_text('secret')
                with self.assertRaises(ValueError):
                    module.inventory(root, 'dev-assets/team')
                secret.unlink()
            with source.open('wb') as stream:
                stream.truncate(module.MAX_BYTES + 1)
            with self.assertRaisesRegex(ValueError, '5GiB'):
                module.inventory(source, 'dev-assets/file')

    def test_empty_folder_and_missing_source_fail(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(ValueError, '파일이 없습니다'):
                module.inventory(root, 'dev-assets/empty')
            with self.assertRaises(OSError):
                module.inventory(root / 'missing', 'dev-assets/missing')

    def test_main_missing_file_has_nonzero_exit_and_no_traceback(self):
        from unittest.mock import patch
        from contextlib import redirect_stderr
        error = io.StringIO()
        with patch.object(module, 'read_settings', side_effect=OSError('SECRET')), redirect_stderr(error):
            self.assertEqual(module.main(['missing', 'dev-assets/missing']), 1)
        self.assertNotIn('SECRET', error.getvalue())
        self.assertNotIn('Traceback', error.getvalue())

    def test_symlink_inputs_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / 'file'
            source.write_bytes(b'data')
            link = root / 'link'
            try:
                link.symlink_to(source)
            except OSError:
                self.skipTest('symbolic link creation requires OS permission')
            with self.assertRaises(ValueError):
                module.inventory(root, 'dev-assets/team')
            with self.assertRaises(ValueError):
                module.inventory(link, 'dev-assets/link')


if __name__ == '__main__':
    unittest.main()
