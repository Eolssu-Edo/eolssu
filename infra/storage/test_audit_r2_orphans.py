import importlib.util
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

spec = importlib.util.spec_from_file_location('audit', Path(__file__).with_name('audit_r2_orphans.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)

NOW = datetime(2026, 11, 24, 3, 0, tzinfo=timezone.utc)
OLD = NOW - timedelta(days=40)
REC = 'recordings/1/12345678-1234-1234-1234-123456789abc.m4a'
REC_ORPHAN = 'recordings/2/12345678-1234-1234-1234-123456789abd.m4a'
REC_MISSING = 'recordings/3/12345678-1234-1234-1234-123456789abe.m4a'
MODEL = 'model-audio/drafts/' + 'a' * 64 + '/cases/danso-001-p1/danso.m4a'
RELEASE = 'model-audio/drafts/' + 'a' * 64 + '/release.json'
SHEET = 'sheet-sources/7/source.pdf'
SHEET_ORPHAN = 'sheet-audio/9/5/gen/audio.m4a'
TEMP_OLD = 'sheet-import-temp/old.pdf'
TEMP_NEW = 'sheet-import-temp/new.pdf'

ENV = {
    'RECORDING_STORAGE_ENDPOINT': 'https://r2.example', 'RECORDING_STORAGE_BUCKET': 'recordings',
    'RECORDING_STORAGE_ACCESS_KEY_ID': 'rid', 'RECORDING_STORAGE_SECRET_ACCESS_KEY': 'rsecret',
    'REFERENCE_AUDIO_STORAGE_ENDPOINT': 'https://r2.example', 'REFERENCE_AUDIO_STORAGE_BUCKET': 'recordings',
    'SHEET_STORAGE_ENDPOINT': 'https://r2.example', 'SHEET_STORAGE_BUCKET': 'sheets',
}


class Bucket:
    """list_objects_v2 paginator only. Any write call fails the test because the attribute does not exist."""
    def __init__(self, objects, page_size=2, denied=False):
        self.objects, self.page_size, self.denied = objects, page_size, denied
    def get_paginator(self, name):
        assert name == 'list_objects_v2'
        return self
    def paginate(self, Bucket):
        if self.denied:
            raise PermissionError('AccessDenied https://signed.example/?X-Amz-Signature=secret')
        items = [{'Key': k, 'Size': s, 'LastModified': m} for k, (s, m) in sorted(self.objects.items())]
        for start in range(0, len(items), self.page_size):
            yield {'Contents': items[start:start + self.page_size]}
        if not items:
            yield {}


def database(rows):
    def query(sql):
        for table, values in rows.items():
            if table in sql:
                return '\n'.join(values)
        return ''
    return query


QUERY = database({
    'recording_url': [REC, REC_MISSING],
    'SELECT object_key FROM song_model_audios': [MODEL],
    'sheet_source_files': [SHEET],
})


def stores(recordings, sheets, denied=()):
    buckets = {'recordings': recordings, 'sheets': sheets}
    def client_for(env, group):
        bucket = env[f'{group}_BUCKET']
        return Bucket(buckets[bucket], denied=group in denied)
    return client_for


class AuditTest(unittest.TestCase):
    def test_classifies_orphans_missing_and_recent_objects(self):
        recordings = {REC: (10, OLD), REC_ORPHAN: (20, OLD), MODEL: (30, OLD), RELEASE: (5, OLD)}
        sheets = {SHEET: (40, OLD), SHEET_ORPHAN: (50, OLD), TEMP_OLD: (60, OLD), TEMP_NEW: (70, NOW - timedelta(hours=1))}
        report, details = audit.run(ENV, QUERY, stores(recordings, sheets), now=NOW)

        self.assertTrue(report['complete'])
        first, second = report['buckets']
        self.assertEqual(first['groups'], ['RECORDING_STORAGE', 'REFERENCE_AUDIO_STORAGE'])
        self.assertEqual(second['groups'], ['SHEET_STORAGE'])
        self.assertEqual(first['prefixes']['recordings/'] | {}, {
            'kind': 'user', 'objects': 2, 'bytes': 30, 'referenced': 1, 'unreferenced': 1, 'unreferenced_bytes': 20,
            'recent_unreferenced': 0, 'oldest_unreferenced': OLD.date().isoformat()})
        # Catalog manifests are not referenced by design: reported as "other", not counted as member orphans.
        self.assertEqual(first['prefixes']['model-audio/']['kind'], 'other')
        self.assertEqual(first['prefixes']['model-audio/']['unreferenced'], 1)
        self.assertEqual(first['user_orphans'], 1)
        self.assertEqual(first['missing_by_prefix'], {'recordings/': 1})
        self.assertEqual(second['prefixes']['sheet-import-temp/']['kind'], 'temporary')
        self.assertEqual(second['prefixes']['sheet-import-temp/']['unreferenced'], 1)
        self.assertEqual(second['prefixes']['sheet-import-temp/']['recent_unreferenced'], 1)
        self.assertEqual(second['user_orphans'], 1)
        self.assertEqual((report['user_orphans'], report['missing']), (2, 1))
        self.assertEqual(details[0]['missing'], [REC_MISSING])
        self.assertEqual({item['key'] for item in details[0]['unreferenced']}, {REC_ORPHAN, RELEASE})

    def test_recent_unreferenced_upload_is_not_an_orphan(self):
        recordings = {REC: (10, OLD), REC_ORPHAN: (20, NOW - timedelta(hours=23))}
        report, _ = audit.run(ENV, QUERY, stores(recordings, {}), now=NOW)
        row = report['buckets'][0]['prefixes']['recordings/']
        self.assertEqual((row['unreferenced'], row['recent_unreferenced']), (0, 1))
        report, _ = audit.run(ENV, QUERY, stores(recordings, {}), now=NOW, grace=timedelta(hours=1))
        self.assertEqual(report['buckets'][0]['prefixes']['recordings/']['unreferenced'], 1)

    def test_old_url_references_are_counted_apart(self):
        query = database({'recording_url': [REC, 'https://old.example/recordings/x.m4a']})
        report, _ = audit.run(ENV, query, stores({REC: (1, OLD)}, {}), now=NOW)
        self.assertEqual((report['buckets'][0]['database_keys'], report['buckets'][0]['non_key_references']), (1, 1))
        self.assertEqual(report['missing'], 0)

    def test_listing_falls_back_to_next_group_and_reports_failure_without_error_text(self):
        report, _ = audit.run(ENV, QUERY, stores({REC: (1, OLD)}, {}, denied={'RECORDING_STORAGE'}), now=NOW)
        self.assertNotIn('error', report['buckets'][0])
        report, _ = audit.run(ENV, QUERY, stores({}, {}, denied={'SHEET_STORAGE'}), now=NOW)
        self.assertFalse(report['complete'])
        self.assertEqual(report['buckets'][1]['error'], 'PermissionError with SHEET_STORAGE credentials')
        self.assertNotIn('secret', json.dumps(report))

    def test_report_holds_no_keys_or_credentials(self):
        report, _ = audit.run(ENV, QUERY, stores({REC_ORPHAN: (1, OLD)}, {SHEET_ORPHAN: (1, OLD)}), now=NOW)
        text = json.dumps(report)
        for value in (REC_ORPHAN, SHEET_ORPHAN, REC_MISSING, 'rsecret', 'rid', 'recordings"', 'sheets"'):
            self.assertNotIn(value, text)

    def test_unconfigured_groups_are_skipped(self):
        env = {k: v for k, v in ENV.items() if not k.startswith('SHEET')}
        self.assertEqual(list(audit.buckets(env).values()), [['RECORDING_STORAGE', 'REFERENCE_AUDIO_STORAGE']])
        self.assertFalse(audit.run({}, QUERY, stores({}, {}), now=NOW)[0]['complete'])

    def test_keys_file_is_new_and_owner_only(self):
        report, details = audit.run(ENV, QUERY, stores({REC_ORPHAN: (1, OLD)}, {}), now=NOW)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'keys.json'
            audit.write_keys(path, report, details)
            self.assertEqual(stat.S_IMODE(os.stat(path).st_mode), 0o600)
            self.assertIn(REC_ORPHAN, path.read_text())
            with self.assertRaises(FileExistsError):
                audit.write_keys(path, report, details)

    def test_env_file_is_read_without_expansion(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'env'
            path.write_text('# c\nexport RECORDING_STORAGE_BUCKET="b"\nX=$HOME\n')
            self.assertEqual(audit.read_env(path), {'RECORDING_STORAGE_BUCKET': 'b', 'X': '$HOME'})


if __name__ == '__main__':
    unittest.main()
