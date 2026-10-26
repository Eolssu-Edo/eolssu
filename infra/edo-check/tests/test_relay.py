import importlib.util
import http.client
import json
from pathlib import Path
import threading
import unittest
from unittest.mock import Mock, patch
from urllib.parse import urlencode


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parents[1] / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


relay = load('relay')


class RequestTests(unittest.TestCase):
    def setUp(self):
        self.config = {'tokens': {'/edo1-server': 'test-secret'}, 'team_id': 'team',
                       'channel_ids': ['cicd'], 'mattermost_origin': 'https://meeting.ssafy.com'}
        self.form = {'command': '/edo1-server', 'token': 'test-secret', 'team_id': 'team',
                     'channel_id': 'cicd', 'user_id': 'user', 'text': '',
                     'response_url': 'https://meeting.ssafy.com/hooks/commands/' + 'a' * 26}

    def test_accepts_only_command_bound_token_and_channel(self):
        self.assertEqual(relay.authorize(self.form, self.config)['mode'], 'server')
        for change in ({'token': 'wrong'}, {'token': '잘못된 토큰'}, {'command': '/edo1-health'},
                       {'channel_id': 'other'}, {'team_id': 'other'}, {'text': '; reboot'}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                relay.authorize(dict(self.form, **change), self.config)

    def test_callback_cannot_target_another_origin_or_path(self):
        for url in ('http://meeting.ssafy.com/hooks/commands/' + 'a' * 26,
                    'https://meeting.ssafy.com.evil.test/hooks/commands/' + 'a' * 26,
                    'https://meeting.ssafy.com@127.0.0.1/hooks/commands/' + 'a' * 26,
                    'https://meeting.ssafy.com:444/hooks/commands/' + 'a' * 26,
                    'https://meeting.ssafy.com/api/v4/users',
                    self.form['response_url'] + '?redirect=https://example.com'):
            with self.subTest(url=url), self.assertRaises(ValueError):
                relay.authorize(dict(self.form, response_url=url), self.config)

    def test_report_separates_issues_and_prevents_unintended_mentions(self):
        result = {'mode': 'server', 'columns': ['대상', '상태'],
                  'rows': [['edo1|host', '@channel\n주의']], 'issues': ['디스크 사용률 87%']}
        text = relay.build_report(result, 'request123')
        self.assertIn('| **대상** | **상태** |', text)
        self.assertIn('edo1\\|host', text)
        self.assertNotIn('@channel', text)
        self.assertIn('다음 조치', text)

    def test_health_summary_cannot_claim_success_when_a_probe_failed(self):
        result = {'mode': 'health', 'columns': ['대상', '위치', '판정', '응답'],
                  'rows': [['API', '외부', '실패', 'HTTP 503']], 'issues': []}
        text = relay.build_report(result, 'req')
        self.assertIn('장애 1', text)
        self.assertIn('API: HTTP 503', text)
        self.assertNotIn('이상 없음', text)

    def test_compact_server_report_keeps_details_and_readable_sections(self):
        result = {'mode': 'server', 'columns': [str(i) for i in range(10)],
                  'rows': [[f'value{i}' for i in range(10)]], 'issues': ['주의 하나', '주의 둘']}
        text = relay.build_report(result, 'req')
        self.assertIn('| **주의** | 주의 하나 |', text)
        self.assertIn('| **주의** | 주의 둘 |', text)
        self.assertIn('| **항목** | **이번 요청의 측정값** | **해석** |', text)
        for i in range(10):
            self.assertIn(f'value{i}', text)
        self.assertNotIn('<br>', text)

    def test_server_details_stay_in_measurement_table(self):
        result = {'mode': 'server', 'columns': [f'항목{i}' for i in range(10)],
                  'rows': [[f'값{i}; 추가{i}' for i in range(10)]], 'issues': []}
        text = relay.build_report(result, 'req')
        table_lines = [line for line in text.splitlines() if line.startswith('|')]
        for i in range(10):
            self.assertTrue(any(f'값{i}' in line for line in table_lines))
            self.assertTrue(any(f'추가{i}' in line for line in table_lines))
        self.assertEqual(sum(line.startswith('| ---') for line in table_lines), 1)

    def test_container_details_have_a_separate_table(self):
        result = {'mode': 'containers', 'columns': [f'항목{i}' for i in range(11)],
                  'rows': [[f'값{i}' for i in range(11)]], 'issues': []}
        text = relay.build_report(result, 'req')
        table_lines = [line for line in text.splitlines() if line.startswith('|')]
        for i in range(11):
            self.assertTrue(any(f'값{i}' in line for line in table_lines))
        self.assertIn('**데이터 저장공간**', text)
        self.assertIn('**실행 세부 정보**', text)
        self.assertNotIn('<br>', text)

    def test_capture_time_is_inline_code_to_prevent_emoji_conversion(self):
        for stamp in ('2026-10-23 16:17:18 KST', '2026-10-23 16:17:18 `KST`'):
            result = {'mode': 'server', 'columns': ['대상', '상태'],
                      'rows': [['edo1', '정상']], 'captured_at': stamp}
            with self.subTest(stamp=stamp):
                text = relay.build_report(result, 'req')
                self.assertIn('수집 시각: `2026-10-23 16:17:18 KST`', text)

    def test_callback_rejects_private_dns_before_connecting(self):
        connection = relay.PublicHTTPS('meeting.ssafy.com', timeout=1)
        with patch.object(relay.socket, 'getaddrinfo', return_value=[(2, 1, 6, '', ('127.0.0.1', 443))]), \
                patch.object(relay.socket, 'create_connection') as connect:
            with self.assertRaises(ValueError):
                connection.connect()
            connect.assert_not_called()

    def test_health_gaps_are_not_counted_as_outages_and_have_next_step(self):
        result = {'mode': 'health', 'columns': ['대상','방식','판정','측정','소요','상세'],
                  'rows': [['eolssu-runtime-backend-1','Docker','미검증','실행 중 · 건강 검사 없음','—','준비 상태 검사 필요']],
                  'captured_at': '2026-10-26 14:06:58 KST'}
        text = relay.build_report(result, 'req')
        self.assertIn('장애 0', text)
        self.assertIn('미검증 1', text)
        self.assertIn('준비 상태 검사 필요', text)
        self.assertIn('자동 갱신되지', text)
        self.assertNotIn('**실행 중 · 건강 검사 없음**', text)

    def test_api_html_200_is_not_reported_as_healthy(self):
        response = Mock(status=200)
        response.read.return_value = b'<html>SPA fallback</html>'
        connection = Mock()
        connection.getresponse.return_value = response
        with patch.object(relay.http.client, 'HTTPSConnection', return_value=connection), \
                patch.object(relay.socket, 'create_connection', side_effect=OSError):
            rows = relay.external_health('service.example')
        api = next(row for row in rows if row[0] == 'API health')
        self.assertNotEqual(api[2], '정상')
        self.assertIn('JSON', api[3])

    def test_missing_service_is_counted_once_and_container_gaps_cannot_look_normal(self):
        missing = 'eolssu-runtime-backend-1'
        report = {'mode': 'health', 'columns': ['대상','방식','판정','측정','소요','상세'],
                  'rows': [[missing,'Docker','실패','필수 컨테이너 누락','—','확인']],
                  'issues': [missing+' 필수 컨테이너 누락']}
        self.assertIn('장애 1', relay.build_report(report, 'req'))
        report = {'mode': 'containers', 'columns': [str(i) for i in range(11)],
                  'rows': [['demo','running/미설정','0.1%/미설정','100B/미설정','1B/미설정 · 로그 2B','없음','1시간/0회','0/아니오','1/없음','미측정 · IO 미측정','image']],
                  'facts': [{'name':'demo','ram_bytes':100,'host_ram':1000,'ram_limit':0,'cpu_percent':0.1}]}
        text = relay.build_report(report, 'req')
        self.assertIn('미검증 1', text)
        self.assertIn('미측정 1', text)
        self.assertNotIn('검사한 범위 정상', text)

    def test_collector_log_gap_has_one_owner_in_summary(self):
        report = {'mode': 'containers', 'columns': [str(i) for i in range(11)],
                  'rows': [['demo','running/healthy','0.1%/미설정','100B/미설정','1B/미설정 · 로그 미측정 (local 로그 경로 없음)','없음','1시간/0회','0/아니오','1/없음','1B/1B · IO 1B/1B','image']],
                  'facts': [{'name':'demo','ram_bytes':100,'host_ram':1000,'ram_limit':0,'cpu_percent':0.1}],
                  'issues': ['demo 로그 미측정 (local 로그 경로 없음)']}
        self.assertIn('미측정 1', relay.build_report(report, 'req'))


class HTTPTests(unittest.TestCase):
    def setUp(self):
        RequestTests.setUp(self)
        self.app = relay.App(self.config)
        self.app.pool.shutdown()
        self.app.pool = Mock()
        self.server = relay.ThreadingHTTPServer(('127.0.0.1', 0), relay.Handler)
        self.server.app = self.app
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def post(self, body):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=2)
        connection.request('POST', relay.ENDPOINT, body, {'Content-Type': 'application/x-www-form-urlencoded'})
        response = connection.getresponse()
        payload = json.loads(response.read())
        connection.close()
        return response.status, payload

    def test_real_http_accepts_once_and_deduplicates(self):
        for _ in range(2):
            code, body = self.post(urlencode(self.form))
            self.assertEqual(code, 200)
            self.assertEqual(body['response_type'], 'ephemeral')
        self.assertEqual(self.app.pool.submit.call_count, 1)

    def test_real_http_rejects_duplicate_fields_and_wrong_channel_before_execution(self):
        for body in (urlencode(dict(self.form, channel_id='other')),
                     urlencode(dict(self.form, token='잘못된 토큰')),
                     urlencode(self.form) + '&command=/edo1-health'):
            code, payload = self.post(body)
            self.assertEqual(code, 200)
            self.assertIn('허용되지', payload['text'])
        self.app.pool.submit.assert_not_called()


class FailureTests(unittest.TestCase):
    def test_ssh_failure_and_timeout_deliver_explicit_failure_and_release_capacity(self):
        config = {'known_hosts': '/test/hosts', 'identity_file': '/test/key',
                  'ssh_target': 'edo-check@example.test'}
        request = {'mode': 'server', 'response_url': 'https://meeting.ssafy.com/hooks/commands/' + 'a' * 26}
        for failure, expected in ((relay.subprocess.TimeoutExpired('ssh', 90), '시간 초과'),
                                  (relay.subprocess.CalledProcessError(255, 'ssh'), '점검 실패')):
            with self.subTest(expected=expected):
                app = relay.App(config)
                app.pool.shutdown()
                app.slots = Mock()
                with patch.object(relay.subprocess, 'run', side_effect=failure), \
                        patch.object(relay, 'post_callback') as callback, \
                        patch.object(relay.logging, 'error'):
                    app.run(request, 'failure123')
                callback.assert_called_once()
                self.assertEqual(callback.call_args[0][0], request['response_url'])
                self.assertIn(expected, callback.call_args[0][1])
                self.assertIn('failure123', callback.call_args[0][1])
                app.slots.release.assert_called_once()


if __name__ == '__main__':
    unittest.main()
