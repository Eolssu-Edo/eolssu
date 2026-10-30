import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import gzip
from datetime import datetime, timezone, timedelta

spec = importlib.util.spec_from_file_location('backup', Path(__file__).parents[1] / 'backup.py')
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)


def bundle(path, name='files/opt/eolssu/.env', content=b'fixture-secret', digest=None):
    manifest = {'version': 1, 'kind': 'config', 'snapshot_at': '2026-10-25T01:00:00Z',
                'files': {name: {'sha256': digest or hashlib.sha256(content).hexdigest(),
                                 'size': len(content), 'mode': 0o600, 'uid': 0, 'gid': 0}}}
    with tarfile.open(path, 'w:gz') as tar:
        for n, data in [('bundle.json', json.dumps(manifest).encode()), (name, content)]:
            item = tarfile.TarInfo(n)
            item.size = len(data)
            tar.addfile(item, io.BytesIO(data))


class BackupTests(unittest.TestCase):
    def test_latest_db_backup_is_not_exempt_from_thirty_day_cap(self):
        with tempfile.TemporaryDirectory() as folder:
            previous = backup.STORE
            backup.STORE = Path(folder)
            try:
                root = backup.STORE / 'db'; root.mkdir()
                ident = '20260101T010000Z-012345abcdef'
                body = b'ciphertext'
                for age in (31, 29.5):
                    (root / (ident + '.cms')).write_bytes(body)
                    (root / (ident + '.json')).write_text(json.dumps({'sha256': hashlib.sha256(body).hexdigest(), 'snapshot_at': (datetime.now(timezone.utc) - timedelta(days=age)).isoformat()}))
                    backup.prune()
                    self.assertFalse((root / (ident + '.cms')).exists())
                    self.assertFalse((root / (ident + '.json')).exists())
            finally:
                backup.STORE = previous

    def test_users_precede_definers_and_table_grants_follow_schema(self):
        accounts = (b"CREATE USER 'reader'@'%' IDENTIFIED BY 'fixture';\n"
                    b"GRANT SELECT ON `eolssu`.`songs` TO 'reader'@'%';\n"
                    b"CREATE USER 'definer'@'%';\n"
                    b"GRANT SELECT ON `eolssu`.* TO 'definer'@'%';\n")
        schema = b'CREATE TABLE songs (id INT); CREATE DEFINER=definer VIEW v AS SELECT * FROM songs;'
        state, phases, report = set(), [], {}
        def import_sql(data):
            phases.append(report['phase'])
            if b'CREATE TABLE' in data:
                self.assertIn('users', state)
                state.add('schema')
            elif b'GRANT ' in data:
                self.assertIn('schema', state, 'MySQL 1146: table does not exist')
            else:
                self.assertNotIn(b'GRANT ', data)
                self.assertEqual(data.count(b'CREATE USER '), 2)
                state.add('users')
        backup.restore_database(import_sql, accounts, schema, report)
        self.assertEqual(phases, ['accounts-create', 'database-import', 'accounts-grants'])

    def test_grant_import_failure_is_not_ignored(self):
        report = {}
        def import_sql(data):
            if b'GRANT ' in data: raise RuntimeError('fixture grant rejected')
        with self.assertRaisesRegex(RuntimeError, 'grant rejected'):
            backup.restore_database(import_sql, b"CREATE USER 'fixture';\nGRANT SELECT ON missing TO fixture;\n", b'schema', report)
        self.assertEqual(report['phase'], 'accounts-grants')

    def test_oversized_pax_header_is_rejected_before_tar_parser_reads_it(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'oversized.tgz'
            header = tarfile.TarInfo('pax'); header.type = tarfile.XHDTYPE; header.size = 1000000000
            with gzip.open(path, 'wb') as f: f.write(header.tobuf())
            with self.assertRaises(ValueError): backup.validate_bundle(path, 'config')

    def test_verified_archive_restores_only_into_chosen_directory(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d); bundle(p / 'bundle.tgz')
            result = backup.validate_bundle(p / 'bundle.tgz', 'config', p / 'restore')
            self.assertEqual(result['kind'], 'config')
            self.assertEqual((p / 'restore/files/opt/eolssu/.env').read_bytes(), b'fixture-secret')

    def test_traversal_and_corruption_are_rejected_before_restore(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)
            for name, digest in [('../../escape', None), ('files/opt/eolssu/.env', '0' * 64)]:
                bundle(p / 'bundle.tgz', name=name, digest=digest)
                with self.assertRaises(ValueError):
                    backup.validate_bundle(p / 'bundle.tgz', 'config', p / 'restore')
            self.assertFalse((p.parent / 'escape').exists())

    def test_snapshot_comparison_detects_changed_values_not_just_row_count(self):
        one = "USE `eolssu`;\nCREATE TABLE `notes` (\n);\nINSERT INTO `notes` VALUES (1,'A');\n"
        two = one.replace("'A'", "'B'")
        first = backup.data_fingerprint(one)
        self.assertEqual(first['eolssu.notes']['rows'], 1)
        self.assertNotEqual(first, backup.data_fingerprint(two))

    def test_sql_keywords_inside_data_do_not_remove_a_table_from_verification(self):
        sql = "USE `eolssu`;\nCREATE TABLE `notes` (\n);\nINSERT INTO `notes` VALUES (1,'VIEW `notes`');\n"
        self.assertEqual(backup.data_fingerprint(sql)['eolssu.notes']['rows'], 1)

    def test_completed_backup_replay_preserves_original_and_rejects_conflict(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d); incoming = p / 'incoming'; incoming.write_bytes(b'ciphertext')
            digest = hashlib.sha256(b'ciphertext').hexdigest()
            run_id = '20261025T010000Z-012345abcdef'
            one = backup.commit_backup(p / 'store', 'db', run_id, incoming, digest)
            incoming.write_bytes(b'ciphertext')
            two = backup.commit_backup(p / 'store', 'db', run_id, incoming, digest)
            self.assertEqual(one, two)
            incoming.write_bytes(b'other')
            with self.assertRaises(ValueError):
                backup.commit_backup(p / 'store', 'db', run_id, incoming, hashlib.sha256(b'other').hexdigest())
            self.assertEqual(one.read_bytes(), b'ciphertext')

    def test_invalid_transfer_never_becomes_a_successful_backup(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d); incoming = p / 'incoming'; incoming.write_bytes(b'truncated')
            with self.assertRaises(ValueError):
                backup.commit_backup(p / 'store', 'db', '20261025T010000Z-012345abcdef', incoming, '0' * 64)
            self.assertFalse(list((p / 'store').rglob('*.cms')))

    def test_dump_covers_only_service_database_after_metabase_removal(self):
        # metabase DB는 S15P31E102-202에서 지웠다. 없는 DB를 덤프하면 백업 전체가 실패한다.
        calls = []
        previous = backup.command; backup.command = lambda args, **kw: calls.append(args) or b''
        try:
            backup.dump_db()
            backup.dump_db('fixture-mysql', 'fixture-password')
        finally:
            backup.command = previous
        for args in calls:
            self.assertEqual(args[args.index('--databases') + 1:], ['eolssu'])

    def test_retention_resumes_after_cipher_deleted_before_receipt(self):
        with tempfile.TemporaryDirectory() as d:
            previous = backup.STORE; backup.STORE = Path(d)
            try:
                root = Path(d) / 'config'; root.mkdir()
                old = '20260101T010000Z-012345abcdef'; latest = '20261025T010000Z-012345abcdef'
                for ident, age in [(old, 100), (latest, 0)]:
                    record = {'sha256': hashlib.sha256(b'ciphertext').hexdigest(),
                              'snapshot_at': (datetime.now(timezone.utc) - timedelta(days=age)).isoformat()}
                    (root / (ident + '.json')).write_text(json.dumps(record))
                (root / (latest + '.cms')).write_bytes(b'ciphertext')
                backup.prune()
                self.assertFalse((root / (old + '.json')).exists())
                self.assertTrue((root / (latest + '.cms')).exists())
            finally:
                backup.STORE = previous


if __name__ == '__main__':
    unittest.main()
