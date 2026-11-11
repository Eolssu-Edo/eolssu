import subprocess
import unittest
from unittest.mock import Mock, patch

from test_relay import load, relay

collector = load('collector')
TIMER = 'eolssu-replication-watch.timer'
SERVICE = 'eolssu-replication-watch.service'
ROW_NAME = 'MySQL 복제 감시'


class ReplicationWatchTests(unittest.TestCase):
    def setUp(self):
        self.now_us = 100_000_000
        self.timer = {'Id': TIMER, 'LoadState': 'loaded', 'ActiveState': 'active', 'SubState': 'waiting'}
        self.service = {'Id': SERVICE, 'LoadState': 'loaded', 'ActiveState': 'inactive',
                        'Result': 'success', 'ExecMainCode': '1', 'ExecMainStatus': '0',
                        'ExecMainStartTimestampMonotonic': '94500000',
                        'ExecMainExitTimestampMonotonic': '95000000'}

    def report(self, command_failure=None):
        sections = ['\n'.join(f'{key}={value}' for key, value in unit.items())
                    for unit in (self.timer, self.service)]
        fixture = subprocess.CompletedProcess([], 0, '\n\n'.join(sections) + '\n', '')
        commands = []

        def run(args, **kwargs):
            commands.append(args)
            if args[0] == 'systemctl':
                if command_failure is not None:
                    return command_failure
                return fixture
            self.assertIn(args, (['nginx', '-t'], ['ufw', 'status']))
            return subprocess.CompletedProcess([], 0, 'Status: active', '')

        connection = Mock()
        connection.getresponse.return_value = Mock(status=200, read=Mock(return_value=b'{"status":"ok"}'))
        with patch.object(collector, 'systemd_health', return_value=('정상', 'fixture', '—')), \
             patch.object(collector, 'snapshot', return_value=({}, [], {}, [])), \
             patch.object(collector, 'backup_health', return_value=[]), \
             patch.object(collector, 'run', side_effect=run), \
             patch.object(collector.time, 'monotonic_ns', return_value=self.now_us * 1000), \
             patch.object(collector.http.client, 'HTTPConnection', return_value=connection):
            result = collector.main('health', 'edo2')
        rows = [row for row in result['rows'] if row[0] == ROW_NAME]
        self.assertEqual(len(rows), 1, result)
        self.assertEqual(sum(TIMER in args and SERVICE in args for args in commands), 1)
        return rows[0], relay.build_report(result, 'fixture').replace('\u2060', '')

    def test_recent_completed_success_is_reported_as_replication_communication(self):
        row, message = self.report()
        self.assertEqual(row[2], '정상')
        self.assertIn('5.0초 전', row[3])
        self.assertIn('수신·적용', row[3])
        self.assertIn('**복제 통신 정상**', message)
        self.assertIn('장애 0 · 주의 0 · 미검증 0 · 미측정 0', message)

    def test_success_expires_after_freshness_threshold(self):
        self.service.update(ExecMainStartTimestampMonotonic='54800000',
                            ExecMainExitTimestampMonotonic='55000000')
        self.assertEqual(self.report()[0][2], '정상')
        self.now_us += 1
        row, message = self.report()
        self.assertEqual(row[2], '주의')
        self.assertIn('45초', row[3])
        self.assertNotIn('복제 통신 정상', message)

    def test_watch_failure_and_inactive_timer_are_visible(self):
        self.service.update(Result='exit-code', ExecMainStatus='1', ActiveState='failed')
        row, message = self.report()
        self.assertEqual(row[2], '실패')
        self.assertIn('exit-code', row[3])
        self.assertIn(SERVICE, message)
        self.assertNotIn('복제 통신 정상', message)
        self.now_us += 60_000_000
        self.assertEqual(self.report()[0][2], '실패')

        self.timer.update(ActiveState='inactive', SubState='dead')
        row, _ = self.report()
        self.assertEqual(row[2], '실패')
        self.assertIn('예약', row[3])

        self.timer.update(ActiveState='active', SubState='elapsed')
        self.assertEqual(self.report()[0][2], '실패')

        self.service = {'Id': SERVICE, 'LoadState': 'not-found'}
        self.timer['SubState'] = 'waiting'
        self.assertIn('unit 로드 실패', self.report()[0][3])

    def test_in_progress_no_history_and_lookup_failure_are_distinct(self):
        self.service.update(ActiveState='activating')
        row, _ = self.report()
        self.assertEqual(row[2], '미검증')
        self.assertIn('실행 중', row[3])

        self.service.update(ActiveState='inactive', ExecMainCode='0',
                            ExecMainStartTimestampMonotonic='0', ExecMainExitTimestampMonotonic='0')
        row, _ = self.report()
        self.assertEqual(row[2], '미검증')
        self.assertIn('완료 이력 없음', row[3])

        row, message = self.report(subprocess.CompletedProcess([], 1, '', 'Permission denied'))
        self.assertEqual(row[2], '확인 불가')
        self.assertIn('**미측정 1**', message)
        self.assertNotIn('Permission denied', message)

    def test_inconsistent_monotonic_metadata_does_not_pass(self):
        for timestamp in ('200000000', '94000000', 'bad'):
            with self.subTest(timestamp=timestamp):
                self.service['ExecMainExitTimestampMonotonic'] = timestamp
                row, message = self.report()
                self.assertEqual(row[2], '확인 불가')
                self.assertNotIn('복제 통신 정상', message)

        self.service['ExecMainExitTimestampMonotonic'] = '95000000'
        self.service['ActiveState'] = 'maintenance'
        self.assertEqual(self.report()[0][2], '확인 불가')
        self.service.update(ActiveState='inactive', Result='')
        self.assertEqual(self.report()[0][2], '확인 불가')
