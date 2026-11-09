import unittest
from unittest.mock import Mock, patch

from test_relay import load, relay

collector = load('collector')


class RetiredAndroidTests(unittest.TestCase):
    def test_a_stopped_android_is_a_failure_even_with_old_exemption_facts(self):
        report = {'target': 'edo2', 'mode': 'containers', 'columns': list(range(11)),
                  'rows': [['eolssu-android-remote', 'exited/unhealthy', '확인 불가/1코어',
                            '확인 불가/1GiB', '1B/미설정 · 로그 1B', '없음', '중지/0회',
                            '143/아니오', '확인 불가/없음', '미측정', 'fixture/android:1']],
                  'facts': [{'name': 'eolssu-android-remote', 'running': False,
                             'expected_stop': 'retired fixture exemption', 'ram_bytes': None,
                             'cpu_percent': None}]}
        text = relay.build_report(report, 'fixture').replace('\u2060', '')
        self.assertIn('장애 확인', text)
        self.assertNotIn('실험 중지', text)

    def test_health_has_no_exception_for_the_retired_android_name(self):
        value = {'item': {'Names': ['/eolssu-android-remote'], 'State': 'exited'},
                 'detail': {'State': {'Status': 'exited', 'Running': False, 'ExitCode': 143,
                                      'OOMKilled': False, 'Health': {'Status': 'healthy'}}}, 'stats': {}}
        connection = Mock()
        connection.getresponse.return_value = Mock(status=200, read=Mock(return_value=b'{"status":"ok"}'))
        with patch.object(collector, 'snapshot', return_value=({}, [value], {}, [])), \
                patch.object(collector, 'edo2_host_health', return_value=[]), \
                patch.object(collector, 'backup_health', return_value=[]), \
                patch.object(collector.http.client, 'HTTPConnection', return_value=connection):
            _, rows, _, _ = collector.health_report('edo2')
        self.assertEqual(next(row for row in rows if row[0] == 'eolssu-android-remote')[2], '실패')
