import copy
import json
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch

from test_relay import load, relay

collector = load('collector')


class ExpectedStopTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.path = Path(self.folder.name) / 'expected-stops.json'
        self.record = {'target': 'edo2', 'id': 'a' * 64, 'name': 'eolssu-android-remote',
                       'finished_at': '2026-10-29T07:10:00.123Z', 'exit_code': 143,
                       'reason': 'KVM 없음; 실험 중지 (S15P31E102-287)'}
        self.path.write_text(json.dumps([self.record]))
        self.path.chmod(0o600)
        # Filesystem metadata is a boundary fixture: developers need not be root.
        self.metadata = SimpleNamespace(st_uid=0, st_mode=stat.S_IFREG | 0o600, st_size=100)
        original_lstat = Path.lstat
        def lstat(path, *args, **kwargs):
            if path == self.path and path.exists():
                return self.metadata
            return original_lstat(path, *args, **kwargs)
        metadata_patch = patch.object(Path, 'lstat', lstat)
        metadata_patch.start()
        self.addCleanup(metadata_patch.stop)
        self.detail = {'Id': 'a' * 64, 'Name': '/eolssu-android-remote',
                       'State': {'Status': 'exited', 'Running': False, 'OOMKilled': False,
                                 'ExitCode': 143, 'FinishedAt': self.record['finished_at'],
                                 'StartedAt': '2026-10-29T07:00:00Z', 'Health': {'Status': 'unhealthy'}},
                       'Config': {'Image': 'fixture/android:1'}, 'HostConfig': {'Memory': 1024**3}}
        self.patcher = patch.object(collector, 'EXPECTED_STOPS', self.path)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)

    def test_only_the_recorded_stop_event_is_exempt(self):
        self.assertEqual(collector.expected_stop(self.detail, 'edo2'), self.record['reason'])
        self.assertEqual(collector.expected_stop(self.detail, 'edo1'), '')
        for section, key, value in [('', 'Id', 'b' * 64), ('', 'Name', '/other'),
                                    ('State', 'FinishedAt', '2026-10-29T08:10:00Z'),
                                    ('State', 'OOMKilled', True), ('State', 'ExitCode', 137),
                                    ('State', 'Running', True), ('State', 'Status', 'paused')]:
            changed = copy.deepcopy(self.detail)
            (changed[section] if section else changed)[key] = value
            with self.subTest(section=section, key=key):
                self.assertEqual(collector.expected_stop(changed, 'edo2'), '')

    def test_missing_invalid_or_writable_record_does_not_hide_failure(self):
        for key, value in [('st_uid', 1000), ('st_mode', stat.S_IFREG | 0o666),
                           ('st_mode', stat.S_IFLNK | 0o600), ('st_size', 16385)]:
            original = getattr(self.metadata, key)
            setattr(self.metadata, key, value)
            with self.subTest(metadata=key, value=value):
                self.assertEqual(collector.expected_stop(self.detail, 'edo2'), '')
            setattr(self.metadata, key, original)
        self.path.write_text('{broken')
        self.assertEqual(collector.expected_stop(self.detail, 'edo2'), '')
        self.path.unlink()
        self.assertEqual(collector.expected_stop(self.detail, 'edo2'), '')

    def snapshot(self):
        return ({'MemTotal': 2 * 1024**3},
                [{'item': {'Id': 'a' * 64, 'Names': ['/eolssu-android-remote'], 'State': 'exited', 'SizeRw': 1},
                  'detail': self.detail, 'stats': {}}], {}, [])

    def test_container_report_retains_missing_stats_and_explicit_stop_without_outage(self):
        with patch.object(collector, 'snapshot', return_value=self.snapshot()), \
                patch.object(collector, 'log_usage', return_value='1.0KiB'), \
                patch.object(collector, 'mounted_storage', return_value='없음'):
            columns, rows, issues, facts = collector.container_report('edo2')
        self.assertEqual(issues, [])
        self.assertIsNone(facts[0]['cpu_percent'])
        self.assertIsNone(facts[0]['ram_bytes'])
        report = {'target': 'edo2', 'mode': 'containers', 'columns': columns, 'rows': rows, 'facts': facts, 'issues': issues}
        text = relay.build_report(report, 'fixture').replace('\u2060', '')
        self.assertIn('실험 중지', text)
        self.assertIn(self.record['reason'], text)
        self.assertIn('장애 0', text)
        self.assertIn('미측정', text)
        self.assertIn('미측정 (네트워크 통계 없음)', text)
        self.assertIn('143/아니오', text)
        self.assertNotIn('0.0% / ', text)
        # A new stop after a restart must immediately become an outage again.
        facts[0]['expected_stop'] = ''
        changed = relay.build_report(report, 'fixture').replace('\u2060', '')
        self.assertIn('장애 확인', changed)

    def test_health_stop_is_informational_but_oom_is_still_failure(self):
        connection = Mock()
        connection.getresponse.return_value = Mock(status=200, read=Mock(return_value=b'{"status":"ok"}'))
        for oom, expected in [(False, '실험 중지'), (True, '실패')]:
            self.detail['State']['OOMKilled'] = oom
            with self.subTest(oom=oom), patch.object(collector, 'snapshot', return_value=self.snapshot()), \
                    patch.object(collector, 'edo2_host_health', return_value=[]), \
                    patch.object(collector, 'backup_health', return_value=[]), \
                    patch.object(collector.http.client, 'HTTPConnection', return_value=connection):
                columns, rows, issues = collector.health_report('edo2')
                row = next(r for r in rows if r[0] == self.record['name'])
                self.assertEqual(row[2], expected)
                text = relay.build_report({'target': 'edo2', 'mode': 'health', 'columns': columns,
                                           'rows': rows, 'issues': issues}, 'fixture').replace('\u2060', '')
                if not oom:
                    self.assertIn('장애 0', text)
                    self.assertIn('의도한 중지 유지', text)
                else:
                    self.assertIn('장애 확인', text)


if __name__ == '__main__':
    unittest.main()
