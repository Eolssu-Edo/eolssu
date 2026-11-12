from datetime import datetime, timezone
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
