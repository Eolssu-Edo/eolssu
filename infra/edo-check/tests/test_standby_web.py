import subprocess
import unittest
from unittest.mock import Mock, patch

from test_relay import load, relay

collector = load('collector')
STANDBY_WEB = 'eolssu-standby-579-frontend-1'


class StandbyWebTests(unittest.TestCase):
    def setUp(self):
        self.container = {
            'item': {'Names': ['/' + STANDBY_WEB], 'State': 'running', 'SizeRw': 0},
            'detail': {'Name': '/' + STANDBY_WEB,
                       'State': {'Status': 'running', 'Running': True, 'StartedAt': '2026-11-10T00:00:00Z'},
                       'NetworkSettings': {'Ports': {'8443/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '18443'}]}},
                       'HostConfig': {'LogConfig': {'Type': 'none'}}, 'Config': {'Image': 'web'}},
            'stats': {},
        }

    def render(self, mode, exit_code=0, output='200 text/html'):
        connection = Mock()
        connection.getresponse.return_value = Mock(status=200, read=Mock(return_value=b'{"status":"ok"}'))
        with patch.object(collector, 'snapshot', return_value=({}, [self.container], {}, [])), \
             patch.object(collector, 'edo2_host_health', return_value=[]), \
             patch.object(collector, 'backup_health', return_value=[]), \
             patch.object(collector.http.client, 'HTTPConnection', return_value=connection), \
             patch.object(collector, 'run', return_value=subprocess.CompletedProcess([], exit_code, output, '')) as run:
            result = collector.main(mode, 'edo2')
        return relay.build_report(result, 'fixture').replace('\u2060', '').replace('\u00a0', ' '), run

    def test_direct_tls_probe_supplies_readiness_in_both_reports(self):
        for mode, label in (('health', '대기 웹 정상'), ('containers', '실행 · HTTPS 검사 통과')):
            with self.subTest(mode=mode):
                report, run = self.render(mode)
                self.assertIn(label, report)
                self.assertNotIn('대기 웹 준비 상태', report)
                args = run.call_args.args[0]
                self.assertIn('https://localhost:18443/index.html', args)
                self.assertIn('--cacert', args)
                self.assertNotIn('--insecure', args)

    def test_invalid_http_or_tls_response_fails_and_ca_access_is_unmeasured(self):
        for code, output, expected in ((0, '404 text/html', '장애 1'), (0, '200 text/plain', '장애 1'),
                                       (60, '000', '장애 1'), (77, '000', '미측정 1')):
            with self.subTest(code=code, output=output):
                report, _ = self.render('health', code, output)
                self.assertIn(expected, report)
                self.assertNotIn('대기 웹 정상', report)

    def test_docker_failure_is_preserved_and_probe_checks_the_published_port(self):
        state = self.container['detail']['State']
        state['Health'] = {'Status': 'unhealthy'}
        report, run = self.render('health')
        self.assertIn('장애 1', report)
        self.assertIn('unhealthy', report)
        run.assert_not_called()

        del state['Health']
        self.container['detail']['NetworkSettings']['Ports'] = {}
        report, run = self.render('health')
        self.assertIn('미측정 1', report)
        self.assertNotIn('대기 웹 정상', report)
        run.assert_not_called()
