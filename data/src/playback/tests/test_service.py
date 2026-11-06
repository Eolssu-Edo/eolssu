"""HTTP boundary tests use a disposable renderer process, never CAT/network assets."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile
import io
import http.client
import threading
from http.server import ThreadingHTTPServer

from data.src.playback.service import GenerationError, PlaybackService, handler


class PlaybackServiceTests(unittest.TestCase):
    def test_bundle_contains_only_public_pair_and_duration(self):
        with tempfile.TemporaryDirectory() as root:
            script = Path(root) / 'render.py'
            script.write_text('''import json, pathlib, sys
p=pathlib.Path(sys.argv[-1]); request=json.loads((p/'request.json').read_text())
assert request['instrument']=='danso'
(p/'audio.m4a').write_bytes(b'fixed-m4a')
(p/'score.mid').write_bytes(b'fixed-midi')
(p/'result.json').write_text(json.dumps({'durationMs': 2250}))
(p/'private.json').write_text('not a public artifact')
''', encoding='utf-8')
            service = PlaybackService([sys.executable, str(script)], timeout_seconds=10)
            result = service.generate({'score': {'schemaVersion': 1}, 'instrument': 'danso',
                                       'bpm': 60, 'withJangdan': False})
            with zipfile.ZipFile(io.BytesIO(result)) as bundle:
                self.assertEqual(set(bundle.namelist()), {'audio.m4a', 'score.mid', 'result.json'})
                self.assertEqual(bundle.read('audio.m4a'), b'fixed-m4a')
                self.assertEqual(json.loads(bundle.read('result.json')), {'durationMs': 2250})

    def test_overlapping_estimates_wait_their_turn_instead_of_failing(self):
        with tempfile.TemporaryDirectory() as root:
            script = Path(root) / 'estimate.py'
            script.write_text('''import json, pathlib, sys, time
time.sleep(.4)
(pathlib.Path(sys.argv[1])/'result.json').write_text(json.dumps({'durationMs': 2250}))
''', encoding='utf-8')
            service = PlaybackService([sys.executable, str(script)], timeout_seconds=10)
            request = {'score': {'schemaVersion': 1}, 'instrument': 'danso', 'bpm': 60, 'withJangdan': False}
            results = []

            def call():
                try:
                    results.append(service.estimate(request))
                except GenerationError as error:
                    results.append(error.code)

            threads = [threading.Thread(target=call) for _ in range(2)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()
            self.assertEqual(results, [{'durationMs': 2250}] * 2)

    def test_rejects_client_paths_wrong_types_and_unknown_instrument(self):
        service = PlaybackService([sys.executable, '-c', 'raise RuntimeError()'], timeout_seconds=10)
        valid = {'score': {'schemaVersion': 1}, 'instrument': 'danso', 'bpm': 60, 'withJangdan': False}
        for changed in ({'instrument': '../../file'}, {'bpm': True}, {'withJangdan': 'false'},
                        {'assets': '/etc/passwd'}, {'score': []}, {'instrument': {}}):
            with self.subTest(changed=changed), self.assertRaises(GenerationError) as caught:
                service.generate({**valid, **changed})
            self.assertEqual(caught.exception.code, 'INVALID_REQUEST')

    def test_failed_renderer_does_not_return_partial_pair_or_private_error(self):
        service = PlaybackService([sys.executable, '-c', 'raise RuntimeError("private")'], timeout_seconds=10)
        with self.assertRaises(GenerationError) as caught:
            service.generate({'score': {}, 'instrument': 'danso', 'bpm': 60, 'withJangdan': False})
        self.assertEqual(caught.exception.code, 'SERVER_ERROR')
        self.assertNotIn('private', str(caught.exception))

    def test_render_answers_busy_while_another_render_holds_the_slot(self):
        service = PlaybackService([sys.executable, '-c', 'raise SystemExit(0)'], timeout_seconds=10)
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler(service))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.assertTrue(service.slot.acquire(blocking=False))
        try:
            body = json.dumps({'score': {}, 'instrument': 'danso', 'bpm': 60, 'withJangdan': False})
            connection = http.client.HTTPConnection('127.0.0.1', server.server_address[1], timeout=10)
            with self.assertLogs('playback', 'WARNING'):
                connection.request('POST', '/v1/render', body, {'Content-Type': 'application/json'})
                response = connection.getresponse()
                self.assertEqual(response.status, 409)
                self.assertEqual(json.loads(response.read()), {'errorCode': 'AUDIO_IN_PROGRESS'})
            connection.close()
        finally:
            service.slot.release()
            server.shutdown()
            server.server_close()

    def test_failed_renderer_logs_only_its_status_and_public_code(self):
        service = PlaybackService([sys.executable, '-c', 'raise SystemExit(7)'], timeout_seconds=10)
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler(service))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            body = json.dumps({'score': {'title': 'private-title'}, 'instrument': 'danso', 'bpm': 60, 'withJangdan': False})
            connection = http.client.HTTPConnection('127.0.0.1', server.server_address[1], timeout=10)
            with self.assertLogs('playback', 'WARNING') as logs:
                connection.request('POST', '/v1/render', body, {'Content-Type': 'application/json'})
                self.assertEqual(connection.getresponse().status, 500)
            connection.close()
        finally:
            server.shutdown()
            server.server_close()
        written = '\n'.join(logs.output)
        self.assertIn('status 7', written)
        self.assertIn('SERVER_ERROR', written)
        self.assertNotIn('private-title', written)

    def test_actual_http_rejects_bad_json_and_returns_public_error(self):
        service = PlaybackService([sys.executable, '-c', 'raise RuntimeError()'], timeout_seconds=10)
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler(service))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=2)
            client.request('POST', '/v1/render', b'{broken', {'Content-Type': 'application/json'})
            response = client.getresponse()
            self.assertEqual(response.status, 400)
            self.assertEqual(json.loads(response.read()), {'errorCode': 'INVALID_REQUEST'})
            self.assertEqual(response.getheader('Cache-Control'), 'no-store')
            client.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_deadlocked_renderer_is_terminated_with_timeout(self):
        service=PlaybackService([sys.executable,'-c','import threading; threading.Event().wait()'],timeout_seconds=.1)
        with self.assertRaises(GenerationError) as caught:
            service.generate({'score':{},'instrument':'danso','bpm':60,'withJangdan':False})
        self.assertEqual(caught.exception.code,'TIMEOUT')


if __name__ == '__main__':
    unittest.main()
