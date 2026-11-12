from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import sqlite3
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import threading

from test_relay import relay

STREAM = 'a' * 32
RUN = 'b' * 32
VERSION = 'qa-20261110.123456-abcd1234'


def event(seq, **fields):
    return {'seq': seq, 'event': {
        'kind': 'http_attempt', 'ms': 20, 'run_id': RUN,
        'version_name': VERSION, 'version_code': 42, **fields,
    }}


class AppLogTests(unittest.TestCase):
    def test_evaluation_timing_accepts_only_a_diagnostic_uuid(self):
        event = {
            'kind': 'milestone', 'ms': 400, 'run_id': 'a' * 32,
            'version_name': 'qa-20261111.162704-866d9064', 'version_code': 1,
            'target': 'analysis', 'phase': 'recording_upload', 'duration_ms': 125,
            'evaluation_timing_id': 'b23ba41a-7e5e-4938-8b3b-93ce5ac25c95',
        }
        self.assertIn(event['evaluation_timing_id'], relay.encode_applog_event(event))
        self.assertIn('"evaluation_submission":2', relay.encode_applog_event({
            **event, 'phase': 'analysis_ready', 'evaluation_submission': 2,
        }))
        self.assertIn('already_pending', relay.encode_applog_event({
            **event, 'phase': 'analysis_retry', 'outcome': 'already_pending',
        }))
        for invalid in ('1234', 'private@example.test', event['evaluation_timing_id'] + '\n'):
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                relay.encode_applog_event({**event, 'evaluation_timing_id': invalid})
        with self.assertRaises(ValueError):
            relay.encode_applog_event({**event, 'phase': 'score_drawn'})
        with self.assertRaises(ValueError):
            relay.encode_applog_event({**event, 'recording_url': 'private'})

    def setUp(self):
        tmp = TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.database = Path(tmp.name) / 'events.sqlite3'
        self.ack = 0
        self.fail_ack = False
        self.rows = [event(1), event(2, kind='data_load', source='retained')]
        self.calls = []

    def device(self, _token, request_id, op, *, timeout, **fields):
        self.calls.append((op, fields))
        result = {'request_id': request_id, 'op': op, 'stream_id': STREAM}
        if op == 'begin':
            return dict(result, ack=self.ack, end=2)
        if op == 'read':
            return dict(result, events=[self.rows[fields['after']]])
        if self.fail_ack:
            raise TimeoutError()
        with sqlite3.connect(self.database) as db:
            stored = db.execute(
                'SELECT count(*) FROM applog_events WHERE stream_id=? AND seq<=?',
                (STREAM, fields['through']),
            ).fetchone()[0]
        self.assertEqual(stored, fields['through'])
        self.ack = fields['through']
        return dict(result, ack=self.ack)

    def collect(self, request_id='c' * 32):
        return relay.collect_applog('tunnel-token', request_id, self.database, tunnel=self.device)

    def test_only_deletes_committed_rows_and_resumes_after_ack_loss(self):
        self.fail_ack = True
        self.assertIn('저장 1', self.collect())
        self.assertEqual(self.ack, 0)
        self.fail_ack = False
        report = self.collect('d' * 32)
        self.assertIn('삭제 확인 2', report)
        self.assertIn('재사용 1', report)
        self.assertEqual(self.ack, 2)
        with sqlite3.connect(self.database) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM applog_events').fetchone()[0], 2)

    def test_refuses_bad_payload_or_discontinuous_sequence_before_ack(self):
        for index, row in enumerate((event(1, target='/api/users/1234'), event(2), event(1, user_id='private'))):
            with self.subTest(row=row):
                self.rows[0] = row
                self.calls.clear()
                self.assertIn('수집 중단', self.collect(str(index) * 32))
                self.assertNotIn('ack', [op for op, _ in self.calls])
                self.assertEqual([op for op, _ in self.calls], ['begin', 'read'])

    def test_refuses_to_ack_a_changed_retransmission(self):
        self.fail_ack = True
        self.collect()
        self.fail_ack = False
        self.rows[0] = event(1, ms=21)
        self.assertIn('수집 중단', self.collect('d' * 32))
        self.assertEqual(self.ack, 0)

    def test_command_requires_separate_access_and_rejects_arguments(self):
        config = {
            'tokens': {'/edo1-server': 'old'},
            'channel_ids': ['shared'], 'mattermost_origin': 'https://meeting.ssafy.com',
            'applog': {'token': 'new', 'team_id': 'team', 'channel_ids': ['qa'], 'user_ids': ['user']},
        }
        form = {
            'command': '/applog', 'token': 'new', 'team_id': 'team',
            'channel_id': 'qa', 'user_id': 'user',
            'response_url': 'https://meeting.ssafy.com/hooks/commands/' + 'e' * 26,
        }
        self.assertEqual(relay.authorize(form, config)['mode'], 'applog')
        for change in (
            {'token': 'old'}, {'team_id': 'other'}, {'channel_id': 'shared'},
            {'user_id': 'other'}, {'text': 'tablet'}, {'target': 'other'},
        ):
            with self.subTest(change=change), self.assertRaises(ValueError):
                relay.authorize(dict(form, **change), config)

    def test_prune_then_retransmit_unacknowledged_row(self):
        self.fail_ack = True
        self.collect()
        with sqlite3.connect(self.database) as db:
            db.execute('UPDATE applog_events SET received_at=0')
        relay.prune_applog(self.database)
        with sqlite3.connect(self.database) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM applog_events').fetchone()[0], 0)
        self.fail_ack = False
        self.assertIn('삭제 확인 2', self.collect('d' * 32))

    def test_result_is_private_and_uses_separate_slot(self):
        app = relay.App({'applog': {'tunnel_token': 'tunnel'}})
        self.addCleanup(app.pool.shutdown)
        self.addCleanup(app.applog_pool.shutdown)
        for _ in range(3):
            self.assertTrue(app.slots.acquire(blocking=False))
        delivered = threading.Event()
        with patch.object(relay, 'collect_applog', return_value='summary'), \
                patch.object(relay, 'post_callback', side_effect=lambda *args, **kwargs: delivered.set()) as callback:
            self.assertIn('수집을 시작', app.submit_applog({'response_url': 'fixture'}, 'request'))
            self.assertTrue(delivered.wait(2))
            callback.assert_called_once_with('fixture', 'summary', response_type='ephemeral')

    def test_http_boundary_sends_only_fixed_target_and_rejects_oversize_reply(self):
        observations = []

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_POST(self):
                payload = self.rfile.read(int(self.headers['Content-Length']))
                observations.append((self.path, self.headers['Authorization'], json.loads(payload)))
                body = json.dumps({'request_id': 'c' * 32, 'op': 'begin', 'stream_id': STREAM,
                                   'ack': 0, 'end': 2}).encode()
                if len(observations) == 2:
                    body += b' ' * relay.APPLOG_RESPONSE_LIMIT
                self.send_response(200)
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        with ThreadingHTTPServer(('127.0.0.1', 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever)
            thread.start()
            connect = relay.http.client.HTTPConnection
            try:
                with patch.object(relay.http.client, 'HTTPConnection',
                                  side_effect=lambda _host, _port, **kwargs: connect(
                                      *server.server_address, **kwargs)) as boundary:
                    self.assertEqual(relay.request_applog('tunnel', 'c' * 32, 'begin', timeout=1)['end'], 2)
                    boundary.assert_called_once_with('127.0.0.1', 27890, timeout=1)
                    with self.assertRaisesRegex(ValueError, 'oversize'):
                        relay.request_applog('tunnel', 'c' * 32, 'begin', timeout=1)
            finally:
                server.shutdown()
                thread.join(2)
        self.assertEqual(observations[0], ('/_internal/qa-perf/snapshot', 'Bearer tunnel',
                                          {'op': 'begin', 'request_id': 'c' * 32}))


if __name__ == '__main__':
    unittest.main()
