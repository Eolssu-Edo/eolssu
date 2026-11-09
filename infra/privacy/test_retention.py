import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import uuid
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location('retention', Path(__file__).with_name('retention.py'))
retention = importlib.util.module_from_spec(spec)
spec.loader.exec_module(retention)


class RetentionTests(unittest.TestCase):
    def test_only_owned_log_names_expire_and_admin_audit_keeps_a_full_year(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); logs = root / 'logs'; logs.mkdir()
            state = root / 'state'; audit = state / 'audit'; audit.mkdir(parents=True)
            for name in ('eolssu-backend.2026-10-16.log', 'eolssu-ai.2026-10-30.log', 'other-project.2020-01-01.log'):
                (logs / name).write_text('synthetic')
            for name in ('access.2025-10-27.jsonl', 'access.2025-10-30.jsonl'):
                (audit / name).write_text('{}')
            result = retention.cleanup(logs, state, datetime(2026, 10, 30, tzinfo=timezone.utc))
            self.assertEqual(result['logs'], 1)
            self.assertEqual(result['audits'], 1)
            self.assertTrue((logs / 'other-project.2020-01-01.log').exists())
            self.assertTrue((audit / 'access.2025-10-30.jsonl').exists())

    def test_playback_and_omr_logs_follow_the_ordinary_log_boundary(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            logs = root / 'logs'
            logs.mkdir()
            for tag in ('playback', 'omr'):
                for day in ('2026-10-17', '2026-10-18'):
                    (logs / f'eolssu-{tag}.{day}.log').write_text('synthetic')
            result = retention.cleanup(logs, root / 'state', datetime(2026, 10, 30, tzinfo=timezone.utc))
            self.assertEqual(result['logs'], 2)
            self.assertEqual(sorted(p.name for p in logs.iterdir()),
                             ['eolssu-omr.2026-10-18.log', 'eolssu-playback.2026-10-18.log'])

    def test_completed_requests_expire_but_pending_requests_and_unrelated_files_stay(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); state = root / 'state'; requests = state / 'requests'; requests.mkdir(parents=True)
            for completed in ('2026-01-01T00:00:00Z', None):
                request = requests / str(uuid.uuid4()); request.mkdir()
                (request / 'request.json').write_text(json.dumps({'completedAt': completed}))
                (request / 'attachment.txt').write_text('synthetic')
            outside = root / 'outside.txt'; outside.write_text('preserve')
            result = retention.cleanup(root / 'logs', state, datetime(2026, 10, 30, tzinfo=timezone.utc))
            self.assertEqual(result['completed_requests'], 1)
            self.assertEqual(len(list(requests.iterdir())), 1)
            self.assertTrue(outside.exists())

    def test_audit_records_actor_source_subject_and_action_in_private_file(self):
        with tempfile.TemporaryDirectory() as folder:
            state = Path(folder)
            retention.audit_record('operator', '127.0.0.1', 'user:123', 'DELETE', 'fixture request', state,
                datetime(2026, 10, 30, tzinfo=timezone.utc))
            path = state / 'audit/access.2026-10-30.jsonl'
            record = json.loads(path.read_text())
            self.assertEqual(record['subject'], 'user:123')
            self.assertEqual(record['operation'], 'DELETE')
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)

    def test_symlinks_do_not_allow_deleting_other_data(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); logs = root / 'logs'; logs.mkdir()
            outside = root / 'outside'; outside.write_text('preserve')
            (logs / 'eolssu-backend.2020-01-01.log').symlink_to(outside)
            with self.assertRaises(ValueError):
                retention.cleanup(logs, root / 'state', datetime(2026, 10, 30, tzinfo=timezone.utc))
            self.assertTrue(outside.exists())


if __name__ == '__main__':
    unittest.main()
