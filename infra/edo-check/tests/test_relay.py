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

    def test_report_has_one_table_and_no_unintended_mentions(self):
        result = {'mode': 'server', 'columns': ['대상', '상태'],
                  'rows': [['edo1|host', '@channel\n주의']], 'issues': ['디스크 사용률 87%']}
        text = relay.build_report(result, 'request123')
        self.assertIn('| 대상 | 상태 |', text)
        self.assertIn('edo1\\|host', text)
        self.assertNotIn('@channel', text)
        self.assertEqual(text.count('| --- | --- |'), 1)

    def test_health_summary_cannot_claim_success_when_a_probe_failed(self):
        result = {'mode': 'health', 'columns': ['대상', '위치', '판정', '응답'],
                  'rows': [['API', '외부', '실패', 'HTTP 503']], 'issues': []}
        text = relay.build_report(result, 'req')
        self.assertIn('API: 실패', text)
        self.assertNotIn('이상 없음', text)

    def test_compact_server_report_keeps_details_and_readable_sections(self):
        result = {'mode': 'server', 'columns': [str(i) for i in range(10)],
                  'rows': [[f'value{i}' for i in range(10)]], 'issues': ['주의 하나', '주의 둘']}
        text = relay.build_report(result, 'req')
        self.assertIn('**확인 사항**\n- 주의 하나\n- 주의 둘', text)
        self.assertIn('| **항목** | **측정값** |', text)
        for i in range(10):
            self.assertIn(f'value{i}', text)
        self.assertNotIn('<br>', text)

    def test_callback_rejects_private_dns_before_connecting(self):
        connection = relay.PublicHTTPS('meeting.ssafy.com', timeout=1)
        with patch.object(relay.socket, 'getaddrinfo', return_value=[(2, 1, 6, '', ('127.0.0.1', 443))]), \
                patch.object(relay.socket, 'create_connection') as connect:
            with self.assertRaises(ValueError):
                connection.connect()
            connect.assert_not_called()


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
