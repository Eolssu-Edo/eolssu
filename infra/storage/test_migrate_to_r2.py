import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

spec = importlib.util.spec_from_file_location('to_r2', Path(__file__).with_name('migrate_to_r2.py'))
to_r2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(to_r2)
KEY = 'recordings/1/12345678-1234-1234-1234-123456789abc.m4a'
OTHER = 'recordings/2/12345678-1234-1234-1234-123456789abd.m4a'
NOW = datetime(2026, 10, 31, 3, 0, tzinfo=timezone.utc)


class Missing(Exception):
    response = {'Error': {'Code': '404'}}


class Bucket:
    """S3 API subset used by the tool, keeping objects in memory like R2 with lowercase user metadata."""
    def __init__(self):
        self.objects = {}
        self.puts = 0
        self.deletes = []
    def head_object(self, Bucket, Key):
        if Key not in self.objects:
            raise Missing()
        body, metadata = self.objects[Key]
        return {'ContentLength': len(body), 'Metadata': dict(metadata)}
    def put_object(self, Bucket, Key, Body, ContentType, ContentMD5, Metadata):
        assert ContentType == 'audio/mp4' and ContentMD5
        self.puts += 1
        self.objects[Key] = (bytes(Body), {k.lower(): v for k, v in Metadata.items()})
    def get_paginator(self, _):
        return self
    def paginate(self, Bucket, Prefix):
        return [{'Contents': [{'Key': key, 'Size': len(body)} for key, (body, _) in self.objects.items() if key.startswith(Prefix)]}]
    def delete_object(self, Bucket, Key):
        self.deletes.append(Key)
        self.objects.pop(Key, None)


def recording(root, key, body, uploaded):
    path = root / key
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(body)
    os.utime(path, (uploaded.timestamp(), uploaded.timestamp()))
    return path


class MigrateToR2Tests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name) / 'recordings-root'
        self.bucket = Bucket()

    def tearDown(self):
        self.folder.cleanup()

    def test_inventory_is_read_only_and_copy_keeps_original_upload_time_without_expired_files(self):
        uploaded = NOW - timedelta(days=4)
        recording(self.root, KEY, b'live', uploaded)
        recording(self.root, OTHER, b'old', NOW - timedelta(days=30))
        report, _ = to_r2.migrate(self.bucket, 'b', self.root, now=NOW)
        self.assertEqual((report['files'], report['expired'], report['copied']), (2, 1, 0))
        self.assertEqual(self.bucket.puts, 0)
        report, manifest = to_r2.migrate(self.bucket, 'b', self.root, apply=True, now=NOW)
        self.assertEqual((report['copied'], report['expired']), (1, 1))
        body, metadata = self.bucket.objects[KEY]
        self.assertEqual(body, b'live')
        self.assertEqual(datetime.fromisoformat(metadata['original-uploaded-at']), uploaded)
        self.assertEqual(metadata['sha256'], manifest[0]['sha256'])
        self.assertNotIn(OTHER, self.bucket.objects)
        # A rerun verifies the same copy instead of uploading it again.
        report, _ = to_r2.migrate(self.bucket, 'b', self.root, apply=True, now=NOW)
        self.assertEqual((report['copied'], report['verified_existing'], self.bucket.puts), (0, 1, 1))

    def test_different_existing_object_and_unexpected_files_stop_before_any_upload(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        self.bucket.objects[KEY] = (b'evil', {'sha256': '0' * 64})
        with self.assertRaises(ValueError):
            to_r2.migrate(self.bucket, 'b', self.root, apply=True, now=NOW)
        self.bucket.objects.clear()
        (self.root / 'recordings' / 'notes.txt').write_text('x')
        with self.assertRaises(ValueError):
            to_r2.migrate(self.bucket, 'b', self.root, apply=True, now=NOW)
        self.assertEqual(self.bucket.puts, 0)

    def test_upload_temporaries_are_ignored(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        (self.root / 'recordings' / '1' / '.upload-1.tmp').write_bytes(b'partial')
        report, _ = to_r2.migrate(self.bucket, 'b', self.root, apply=True, now=NOW)
        self.assertEqual((report['files'], report['copied']), (1, 1))

    def test_purge_deletes_only_migrated_objects_thirty_days_after_original_upload(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=29, hours=23))
        to_r2.migrate(self.bucket, 'b', self.root, apply=True, now=NOW)
        self.bucket.objects[OTHER] = (b'new', {})  # uploaded by the backend; R2's lifecycle rule owns it
        report = to_r2.purge_expired(self.bucket, 'b', now=NOW, delete=True)
        self.assertEqual((report['tracked'], report['expired'], self.bucket.deletes), (1, 0, []))
        later = NOW + timedelta(hours=1)
        report = to_r2.purge_expired(self.bucket, 'b', now=later)
        self.assertEqual((report['expired'], report['deleted'], self.bucket.deletes), (1, 0, []))
        report = to_r2.purge_expired(self.bucket, 'b', now=later, delete=True)
        self.assertEqual((report['deleted'], self.bucket.deletes), (1, [KEY]))
        self.assertIn(OTHER, self.bucket.objects)

    def test_local_removal_requires_every_live_file_verified_in_r2(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        expired = recording(self.root, OTHER, b'old', NOW - timedelta(days=31))
        with self.assertRaises(ValueError):
            to_r2.remove_local(self.bucket, 'b', self.root, now=NOW, delete=True)
        self.assertTrue((self.root / KEY).exists() and expired.exists())
        to_r2.migrate(self.bucket, 'b', self.root, apply=True, now=NOW)
        report = to_r2.remove_local(self.bucket, 'b', self.root, now=NOW)
        self.assertEqual((report['verified'], report['expired'], report['deleted']), (1, 1, 0))
        self.assertTrue((self.root / KEY).exists())
        report = to_r2.remove_local(self.bucket, 'b', self.root, now=NOW, delete=True)
        self.assertEqual(report['deleted'], 2)
        self.assertFalse((self.root / KEY).exists() or expired.exists())


if __name__ == '__main__':
    unittest.main()
