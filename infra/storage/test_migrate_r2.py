import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

spec = importlib.util.spec_from_file_location('migration', Path(__file__).with_name('migrate_r2.py'))
migration = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migration)
KEY = 'recordings/1/12345678-1234-1234-1234-123456789abc.m4a'


class Body(io.BytesIO):
    def __enter__(self):
        # botocore StreamingBody returns its underlying HTTP response here.
        return object()
    def iter_chunks(self, chunk_size):
        while chunk := self.read(chunk_size):
            yield chunk


class Client:
    def __init__(self, objects):
        self.objects = objects
        self.reads = 0
    def get_paginator(self, _):
        return self
    def paginate(self, **_):
        return [{'Contents': self.objects}]
    def get_object(self, **_):
        self.reads += 1
        return dict(ContentLength=4, LastModified=self.objects[0]['LastModified'].replace(microsecond=0), Body=Body(b'test'))


class MigrationTests(unittest.TestCase):
    def test_inventory_is_read_only_and_copy_preserves_upload_time_and_retries(self):
        now = datetime.now(timezone.utc)
        uploaded = now - timedelta(days=4)
        client = Client([dict(Key=KEY, Size=4, LastModified=uploaded, ETag='fixture')])
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / 'new'
            report, _ = migration.migrate(client, 'fixture', root, now=now)
            self.assertEqual(report['objects'], 1)
            self.assertEqual(client.reads, 0)
            self.assertFalse(root.exists())
            report, manifest = migration.migrate(client, 'fixture', root, True, now)
            self.assertEqual(report['copied'], 1)
            self.assertEqual((root / KEY).read_bytes(), b'test')
            self.assertAlmostEqual((root / KEY).stat().st_mtime, uploaded.timestamp(), places=3)
            self.assertEqual(len(manifest[0]['sha256']), 64)
            report, _ = migration.migrate(client, 'fixture', root, True, now)
            self.assertEqual(report['verified_existing'], 1)
            (root / KEY).write_bytes(b'evil')
            with self.assertRaises(ValueError):
                migration.migrate(client, 'fixture', root, True, now)
    def test_invalid_key_aborts_before_copy_and_expired_objects_are_not_copied(self):
        now = datetime.now(timezone.utc)
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / 'new'
            client = Client([dict(Key='../escape', Size=4, LastModified=now, ETag='fixture')])
            with self.assertRaises(ValueError):
                migration.migrate(client, 'fixture', root, True, now)
            self.assertEqual(client.reads, 0)
            self.assertFalse(root.exists())
            client.objects = [dict(Key=KEY, Size=4, LastModified=now - timedelta(days=30), ETag='fixture')]
            report, _ = migration.migrate(client, 'fixture', root, True, now)
            self.assertEqual(report['expired'], 1)
            self.assertEqual(client.reads, 0)


if __name__ == '__main__':
    unittest.main()
