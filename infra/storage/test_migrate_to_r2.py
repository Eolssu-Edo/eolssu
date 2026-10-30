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
        self.exists = True
    def head_bucket(self, Bucket):
        if not self.exists:
            raise Missing()
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

    def copied(self, referenced):
        _, manifest = to_r2.migrate(self.bucket, 'b', self.root, referenced, apply=True, now=NOW)
        return {item['key'] for item in manifest}

    def test_inventory_is_read_only_and_copy_keeps_original_upload_time_without_expired_files(self):
        uploaded = NOW - timedelta(days=4)
        recording(self.root, KEY, b'live', uploaded)
        recording(self.root, OTHER, b'old', NOW - timedelta(days=30))
        report, _ = to_r2.migrate(self.bucket, 'b', self.root, {KEY, OTHER}, now=NOW)
        self.assertEqual((report['files'], report['expired'], report['copied']), (2, 1, 0))
        self.assertEqual(self.bucket.puts, 0)
        report, manifest = to_r2.migrate(self.bucket, 'b', self.root, {KEY, OTHER}, apply=True, now=NOW)
        self.assertEqual((report['copied'], report['expired']), (1, 1))
        body, metadata = self.bucket.objects[KEY]
        self.assertEqual(body, b'live')
        self.assertEqual(datetime.fromisoformat(metadata['original-uploaded-at']), uploaded)
        self.assertEqual(metadata['sha256'], manifest[0]['sha256'])
        self.assertNotIn(OTHER, self.bucket.objects)
        # A rerun verifies the same copy instead of uploading it again.
        report, _ = to_r2.migrate(self.bucket, 'b', self.root, {KEY, OTHER}, apply=True, now=NOW)
        self.assertEqual((report['copied'], report['verified_existing'], self.bucket.puts), (0, 1, 1))

    def test_recordings_deleted_in_the_database_are_never_copied_abroad(self):
        # A withdrawal purge between the switch and the copy deletes the R2 key (absent) and the row, not the disk file.
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        recording(self.root, OTHER, b'withdrawn', NOW - timedelta(days=1))
        report, manifest = to_r2.migrate(self.bucket, 'b', self.root, {KEY}, apply=True, now=NOW)
        self.assertEqual((report['copied'], report['unreferenced']), (1, 1))
        self.assertEqual([item['key'] for item in manifest], [KEY])
        self.assertNotIn(OTHER, self.bucket.objects)

    def test_missing_bucket_stops_before_any_upload_or_deletion(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        self.bucket.exists = False
        # The read-only inventory runs before the switch, so it must already surface a missing bucket or HeadBucket denial.
        with self.assertRaises(Missing):
            to_r2.migrate(self.bucket, 'b', self.root, {KEY}, now=NOW)
        with self.assertRaises(Missing):
            to_r2.migrate(self.bucket, 'b', self.root, {KEY}, apply=True, now=NOW)
        with self.assertRaises(Missing):
            to_r2.remove_local(self.bucket, 'b', self.root, {KEY}, set(), now=NOW, delete=True)
        self.assertTrue((self.root / KEY).exists())
        self.assertEqual(self.bucket.puts, 0)

    def test_different_existing_object_and_unexpected_files_stop_before_any_upload(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        self.bucket.objects[KEY] = (b'evil', {'sha256': '0' * 64})
        with self.assertRaises(ValueError):
            to_r2.migrate(self.bucket, 'b', self.root, {KEY}, apply=True, now=NOW)
        self.bucket.objects.clear()
        (self.root / 'recordings' / 'notes.txt').write_text('x')
        with self.assertRaises(ValueError):
            to_r2.migrate(self.bucket, 'b', self.root, {KEY}, apply=True, now=NOW)
        self.assertEqual(self.bucket.puts, 0)

    def test_upload_temporaries_are_ignored_by_the_copy(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        (self.root / 'recordings' / '1' / '.upload-1.tmp').write_bytes(b'partial')
        report, _ = to_r2.migrate(self.bucket, 'b', self.root, {KEY}, apply=True, now=NOW)
        self.assertEqual((report['files'], report['copied']), (1, 1))

    def test_purge_deletes_only_migrated_objects_thirty_days_after_original_upload(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=29, hours=23))
        self.copied({KEY})
        self.bucket.objects[OTHER] = (b'new', {})  # uploaded by the backend; R2's lifecycle rule owns it
        report = to_r2.purge_expired(self.bucket, 'b', now=NOW, delete=True)
        self.assertEqual((report['tracked'], report['expired'], self.bucket.deletes), (1, 0, []))
        later = NOW + timedelta(hours=1)
        report = to_r2.purge_expired(self.bucket, 'b', now=later)
        self.assertEqual((report['expired'], report['deleted'], self.bucket.deletes), (1, 0, []))
        report = to_r2.purge_expired(self.bucket, 'b', now=later, delete=True)
        self.assertEqual((report['deleted'], self.bucket.deletes), (1, [KEY]))
        self.assertIn(OTHER, self.bucket.objects)

    def test_purge_treats_a_timezone_less_marker_as_utc(self):
        self.bucket.objects[KEY] = (b'live', {'original-uploaded-at': (NOW - timedelta(days=31)).replace(tzinfo=None).isoformat()})
        report = to_r2.purge_expired(self.bucket, 'b', now=NOW, delete=True)
        self.assertEqual((report['deleted'], self.bucket.deletes), (1, [KEY]))

    def test_local_removal_requires_every_referenced_file_copied_and_verified_in_r2(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        expired = recording(self.root, OTHER, b'old', NOW - timedelta(days=31))
        temporary = self.root / 'recordings' / '1' / '.upload-1.tmp'
        temporary.write_bytes(b'partial')
        with self.assertRaises(ValueError):
            to_r2.remove_local(self.bucket, 'b', self.root, {KEY}, set(), now=NOW, delete=True)
        self.assertTrue((self.root / KEY).exists() and expired.exists() and temporary.exists())
        copied = self.copied({KEY})
        report = to_r2.remove_local(self.bucket, 'b', self.root, {KEY}, copied, now=NOW)
        self.assertEqual((report['verified'], report['expired'], report['temporaries'], report['deleted']), (1, 1, 1, 0))
        self.assertTrue((self.root / KEY).exists())
        self.bucket.objects[KEY] = (b'evil', {'sha256': '0' * 64})
        with self.assertRaises(ValueError):
            to_r2.remove_local(self.bucket, 'b', self.root, {KEY}, copied, now=NOW, delete=True)
        self.bucket.objects.clear()
        self.copied({KEY})
        report = to_r2.remove_local(self.bucket, 'b', self.root, {KEY}, copied, now=NOW, delete=True)
        self.assertEqual(report['deleted'], 3)
        self.assertFalse((self.root / KEY).exists() or expired.exists() or temporary.exists())
        self.assertEqual(self.bucket.deletes, [])  # disk cleanup never deletes R2 objects, so a rerun is harmless

    def test_r2_absence_is_trusted_only_when_the_database_no_longer_references_the_recording(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        copied = self.copied({KEY})
        self.bucket.objects.clear()  # e.g. a different bucket configured, or a withdrawal purge after the switch
        with self.assertRaises(ValueError):
            to_r2.remove_local(self.bucket, 'b', self.root, {KEY}, copied, now=NOW, delete=True)
        self.assertTrue((self.root / KEY).exists())
        report = to_r2.remove_local(self.bucket, 'b', self.root, set(), copied, now=NOW, delete=True)
        self.assertEqual((report['unreferenced'], report['deleted']), (1, 1))
        self.assertFalse((self.root / KEY).exists())

    def test_database_references_exclude_deleted_records_and_reject_unexpected_values(self):
        queries = []
        keys = to_r2.referenced_keys(lambda sql: queries.append(sql) or KEY + '\n' + OTHER + '\n')
        self.assertEqual(keys, {KEY, OTHER})
        self.assertIn('record_deleted_at IS NULL', queries[0])
        self.assertEqual(to_r2.referenced_keys(lambda _: ''), set())
        with self.assertRaises(ValueError):
            to_r2.referenced_keys(lambda _: '../escape')
        # The production query reuses the terms tool's MySQL helper rather than a second copy of it.
        self.assertTrue(callable(to_r2.terms_tool().mysql))

    def test_manifest_keeps_completed_copies_when_a_later_object_fails(self):
        recording(self.root, KEY, b'live', NOW - timedelta(days=1))
        recording(self.root, OTHER, b'next', NOW - timedelta(days=1))
        self.bucket.objects[OTHER] = (b'evil', {'sha256': '0' * 64})
        manifest = []
        with self.assertRaises(ValueError):
            to_r2.migrate(self.bucket, 'b', self.root, {KEY, OTHER}, apply=True, now=NOW, manifest=manifest)
        self.assertEqual([item['key'] for item in manifest], [KEY])


if __name__ == '__main__':
    unittest.main()
