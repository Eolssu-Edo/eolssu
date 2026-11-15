"""Exercise failure recovery over HTTP with small disposable renderer processes."""
import concurrent.futures
import http.client
from http.server import ThreadingHTTPServer
import io
import json
import os
from pathlib import Path
import signal
import shutil
import socket
import sys
import tempfile
import threading
import time
import unittest
import zipfile

from data.src.playback.service import PlaybackService, handler


class PlaybackRecoveryTests(unittest.TestCase):
    def test_failed_renderer_terminates_descendants_before_next_success(self):
        self._assert_renderer_failure_recovers('crash')

    def test_timed_out_renderer_terminates_descendants_before_next_success(self):
        self._assert_renderer_failure_recovers('timeout')

    def _assert_renderer_failure_recovers(self, fault):
        descendant_script = '''import json, os, pathlib, socket, sys
held = (pathlib.Path(sys.argv[1]) / 'child-open.bin').open('wb')
listener = socket.socket()
listener.bind(('127.0.0.1', 0))
listener.listen()
print(json.dumps({'port': listener.getsockname()[1]}), flush=True)
while True:
    connection, _ = listener.accept()
    with connection:
        if connection.recv(1) == b'X':
            break
        connection.sendall(b'alive')
'''
        with tempfile.TemporaryDirectory() as fixture:
            marker, release = Path(fixture) / 'ready.json', Path(fixture) / 'release'
            script = f'''import json, os, pathlib, subprocess, sys, threading
p = pathlib.Path(sys.argv[1])
request = json.loads((p / 'request.json').read_text())
if request['score'].get('crash'):
    child = subprocess.Popen([sys.executable, '-c', {descendant_script!r}, str(p)], stdout=subprocess.PIPE, text=True)
    info = json.loads(child.stdout.readline())
    info.update(group=os.getpid(), directory=str(p))
    pathlib.Path({str(marker)!r}).write_text(json.dumps(info))
    if {fault!r} == 'timeout':
        threading.Event().wait()
    while not pathlib.Path({str(release)!r}).exists():
        threading.Event().wait(.01)
    raise SystemExit(7)
(p / 'audio.m4a').write_bytes(b'complete-audio')
(p / 'score.mid').write_bytes(b'complete-midi')
(p / 'result.json').write_text('{{"durationMs":2250}}')
'''
            service = PlaybackService([sys.executable, '-c', script], timeout_seconds=3)
            server = ThreadingHTTPServer(('127.0.0.1', 0), handler(service))
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            valid = {'score': {}, 'instrument': 'danso', 'bpm': 60, 'withJangdan': False}

            def post(request):
                client = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=5)
                try:
                    client.request('POST', '/v1/render', json.dumps(request), {'Content-Type': 'application/json'})
                    response = client.getresponse()
                    return response.status, response.read()
                finally:
                    client.close()

            info = None
            try:
                with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                    future = pool.submit(post, {**valid, 'score': {'crash': True}})
                    deadline = time.monotonic() + 2
                    while not marker.exists() and time.monotonic() < deadline:
                        threading.Event().wait(.01)
                    self.assertTrue(marker.exists(), 'renderer did not start its descendant')
                    info = json.loads(marker.read_text())
                    with socket.create_connection(('127.0.0.1', info['port']), timeout=1) as observer:
                        observer.sendall(b'?')
                        self.assertEqual(observer.recv(5), b'alive')
                    release.touch()
                    status, payload = future.result(timeout=5)
                    expected = (504, 'TIMEOUT') if fault == 'timeout' else (500, 'SERVER_ERROR')
                    self.assertEqual((status, json.loads(payload)), (expected[0], {'errorCode': expected[1]}))
                    self.assertFalse(Path(info['directory']).exists())
                    deadline = time.monotonic() + 1
                    while True:
                        try:
                            with socket.create_connection(('127.0.0.1', info['port']), timeout=.1) as observer:
                                observer.sendall(b'?')
                                if observer.recv(5) != b'alive':
                                    break
                        except OSError:
                            break
                        self.assertLess(time.monotonic(), deadline, 'renderer descendant survived the failed request')
                        threading.Event().wait(.01)
                    recovered_status, recovered_payload = post(valid)
                    self.assertEqual(recovered_status, 200)
                    with zipfile.ZipFile(io.BytesIO(recovered_payload)) as bundle:
                        self.assertEqual(bundle.read('audio.m4a'), b'complete-audio')
            finally:
                release.touch()
                if info is not None:
                    try:
                        with socket.create_connection(('127.0.0.1', info['port']), timeout=1) as observer:
                            observer.sendall(b'X')
                    except OSError:
                        pass
                    if os.name == 'posix':
                        try:
                            os.killpg(info['group'], signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                server.shutdown()
                server.server_close()
                thread.join()
                if info is not None:
                    directory = Path(info['directory']).resolve()
                    self.assertEqual(directory.parent, Path(tempfile.gettempdir()).resolve())
                    self.assertTrue(directory.name.startswith('sheet-audio-'))
                    if directory.exists():
                        shutil.rmtree(directory)

    def test_corrupt_renderer_metadata_is_server_error_and_next_request_succeeds(self):
        script = '''import json, pathlib, sys
p = pathlib.Path(sys.argv[1])
request = json.loads((p / 'request.json').read_text(encoding='utf-8'))
(p / 'audio.m4a').write_bytes(b'complete-audio')
(p / 'score.mid').write_bytes(b'complete-midi')
content = request['score'].get('resultHex')
(p / 'result.json').write_bytes(bytes.fromhex(content) if content else b'{"durationMs":2250}')
'''
        service = PlaybackService([sys.executable, '-c', script], timeout_seconds=5)
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler(service))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        valid = {'score': {}, 'instrument': 'danso', 'bpm': 60, 'withJangdan': False}
        try:
            for endpoint in ('/v1/render', '/v1/estimate'):
                for metadata in (b'{broken', b'[]', b'null', b'"text"', b'\xff'):
                    with self.subTest(endpoint=endpoint, metadata=metadata):
                        client = http.client.HTTPConnection('127.0.0.1', server.server_port, timeout=5)
                        try:
                            client.request('POST', endpoint,
                                           json.dumps({**valid, 'score': {'resultHex': metadata.hex()}}),
                                           {'Content-Type': 'application/json'})
                            response = client.getresponse()
                            self.assertEqual(response.status, 500)
                            self.assertEqual(json.loads(response.read()), {'errorCode': 'SERVER_ERROR'})
                            self.assertEqual(response.getheader('Cache-Control'), 'no-store')

                            # The same caller can retry without a poisoned service slot.
                            # The server uses HTTP/1.0, so HTTPConnection reconnects here.
                            client.request('POST', endpoint, json.dumps(valid), {'Content-Type': 'application/json'})
                            recovered = client.getresponse()
                            self.assertEqual(recovered.status, 200)
                            payload = recovered.read()
                            if endpoint == '/v1/render':
                                with zipfile.ZipFile(io.BytesIO(payload)) as bundle:
                                    self.assertEqual(bundle.read('audio.m4a'), b'complete-audio')
                                    self.assertEqual(json.loads(bundle.read('result.json')), {'durationMs': 2250})
                            else:
                                self.assertEqual(json.loads(payload), {'durationMs': 2250})
                        finally:
                            client.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == '__main__':
    unittest.main()
