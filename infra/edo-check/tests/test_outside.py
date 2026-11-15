from contextlib import redirect_stdout
from datetime import datetime, timezone
from io import StringIO
import json
from pathlib import Path
import ssl
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

from test_relay import load

outside = load('outside')

TERMS = {
    'httpStatus': 'OK',
    'isSuccess': True,
    'message': '약관 목록 조회 성공',
    'code': 200,
    'result': {'items': [{'termId': '1', 'code': 'TERMS', 'title': '이용약관',
                          'required': True, 'version': '2026-10-15',
                          'contentUrl': '/api/terms/documents/current/terms'}]},
}


class OutsideTests(unittest.TestCase):
    def response(self, status, body, content_type='application/json; charset=utf-8'):
        return Mock(status=status, getheader=Mock(return_value=content_type),
                    read=Mock(return_value=body))

    def test_both_targets_use_public_https_and_publish_independent_results(self):
        relay = Mock()
        relay.getresponse.return_value = self.response(502, b'<!doctype html>', 'text/html')
        terms = Mock()
        terms.getresponse.return_value = self.response(200, json.dumps(TERMS).encode())
        with patch.object(outside.http.client, 'HTTPSConnection', side_effect=[relay, terms]) as connect:
            result = outside.collect()
        self.assertEqual([item.args[0] for item in connect.call_args_list],
                         ['15.165.144.7', 'k15e102.p.ssafy.io'])
        self.assertEqual([relay.request.call_args.args, terms.request.call_args.args],
                         [('GET', '/healthz'), ('GET', '/api/terms')])
        self.assertEqual(result['relay'], {'ok': False, 'detail': 'HTTP 502'})
        self.assertTrue(result['terms']['ok'])
        captured = datetime.fromisoformat(result['captured_at'].replace('Z', '+00:00'))
        self.assertEqual(captured.tzinfo, timezone.utc)
        relay.close.assert_called_once()
        terms.close.assert_called_once()

    def test_http_200_requires_correct_json_for_each_target(self):
        invalid_term = {**TERMS, 'result': {'items': [{'termId': 1}]}}
        cases = [
            ('relay', self.response(200, b'{"status":"starting"}')),
            ('terms', self.response(200, json.dumps(invalid_term).encode())),
            ('terms', self.response(200, b'<!doctype html>', 'text/html')),
            ('terms', self.response(200, b'{"result":{"items":[]}}')),
            ('terms', self.response(200, b' ' * 65537)),
        ]
        for target, response in cases:
            with self.subTest(target=target, response=response), \
                    patch.object(outside.http.client, 'HTTPSConnection') as connect:
                connect.return_value.getresponse.return_value = response
                self.assertFalse(outside.probe(target)['ok'])

    def test_public_terms_api_allows_empty_items(self):
        empty = {**TERMS, 'result': {'items': []}}
        with patch.object(outside.http.client, 'HTTPSConnection') as connect:
            connect.return_value.getresponse.return_value = self.response(200, json.dumps(empty).encode())
            self.assertTrue(outside.probe('terms')['ok'])

    def test_tls_failure_has_fixed_detail_and_connection_closes(self):
        with patch.object(outside.http.client, 'HTTPSConnection') as connect:
            connection = connect.return_value
            connection.request.side_effect = ssl.SSLCertVerificationError('untrusted host')
            self.assertEqual(outside.probe('relay'), {'ok': False, 'detail': 'TLS 인증 실패'})
            connection.close.assert_called_once()

    def test_publish_replaces_previous_state(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'latest.json'
            path.write_text('{"old":true}')
            data = {'captured_at': '2026-11-10T10:00:00Z',
                    'relay': {'ok': False, 'detail': 'HTTP 502'},
                    'terms': {'ok': True, 'detail': 'HTTP 200 · 약관 목록 JSON 정상'}}
            outside.publish(path, data)
            self.assertEqual(json.loads(path.read_text()), data)
            self.assertEqual(list(Path(directory).iterdir()), [path])

    def test_run_always_publishes_and_only_logs_per_target_outages_and_recoveries(self):
        healthy = {'relay': {'ok': True, 'detail': 'HTTP 200 · 중계 JSON 정상'},
                   'terms': {'ok': True, 'detail': 'HTTP 200 · 약관 목록 JSON 정상'}}
        relay_failed = {**healthy, 'relay': {'ok': False, 'detail': 'HTTP 502'}}
        both_failed = {**relay_failed, 'terms': {'ok': False, 'detail': 'HTTPS 응답 시간 초과'}}
        relay_recovered = {**both_failed, 'relay': healthy['relay']}
        relay_failed_again = {**both_failed, 'relay': {'ok': False, 'detail': 'TLS 인증 실패'}}
        cases = [
            (healthy, []),
            (healthy, []),
            (relay_failed, [('relay', 'outage_started')]),
            (both_failed, [('terms', 'outage_started')]),
            ({**both_failed, 'relay': relay_failed_again['relay']}, []),
            (relay_recovered, [('relay', 'recovered')]),
            (relay_failed_again, [('relay', 'outage_started')]),
            (healthy, [('relay', 'recovered'), ('terms', 'recovered')]),
        ]
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'latest.json'
            with patch.object(outside, 'STATE_FILE', path):
                for minute, (results, expected) in enumerate(cases):
                    snapshot = {'captured_at': f'2026-11-10T10:{minute:02}:00Z', **results}
                    output = StringIO()
                    with self.subTest(minute=minute), patch.object(outside, 'collect', return_value=snapshot), \
                            redirect_stdout(output):
                        outside.main()
                        self.assertEqual(json.loads(path.read_text()), snapshot)
                        events = [(line[:3], json.loads(line[3:])) for line in output.getvalue().splitlines()]
                        self.assertEqual(events, [
                            ('<5>' if event == 'recovered' else '<4>',
                             {'captured_at': snapshot['captured_at'], 'target': target,
                              'event': event, 'detail': snapshot[target]['detail']})
                            for target, event in expected
                        ])

    def test_first_failure_is_logged_when_previous_state_cannot_be_decoded(self):
        snapshot = {'captured_at': '2026-11-10T10:00:00Z',
                    'relay': {'ok': False, 'detail': 'HTTP 503'},
                    'terms': {'ok': True, 'detail': 'HTTP 200 · 약관 목록 JSON 정상'}}
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'latest.json'
            path.write_bytes(b'{')
            output = StringIO()
            with patch.object(outside, 'STATE_FILE', path), \
                    patch.object(outside, 'collect', return_value=snapshot), redirect_stdout(output):
                outside.main()
            self.assertEqual(json.loads(path.read_text()), snapshot)
            self.assertEqual(len(output.getvalue().splitlines()), 1)
            self.assertEqual(json.loads(output.getvalue()[3:])['target'], 'relay')
            self.assertEqual(json.loads(output.getvalue()[3:])['event'], 'outage_started')
