"""Bounded internal playback transport. Rendering runs in a killable child process."""
import argparse
import io
import json
import logging
import os
import re
import signal
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import zipfile

MAX_REQUEST = 6 * 1024 * 1024
MAX_AUDIO = 15 * 1024 * 1024
MAX_MIDI = 1024 * 1024
MAX_PERFORMANCE = 2 * 1024 * 1024
PERFORMANCE_SOURCE_HEADER = 'X-Eolssu-Performance-Source-Sha256'
ESTIMATE_WAIT_SECONDS = 5
LOG = logging.getLogger('playback')
INSTRUMENTS = {'danso', 'sogeum', 'jeongak-daegeum', 'sanjo-daegeum'}


class GenerationError(Exception):
    def __init__(self, code, status=500):
        super().__init__(code)
        self.code, self.status = code, status


class PlaybackService:
    def __init__(self, command, *, timeout_seconds):
        if not 0 < timeout_seconds <= 3600:
            raise ValueError('An explicitly measured generation timeout is required')
        self.command = list(command)
        self.timeout = timeout_seconds
        self.slot = threading.Lock()
        self.estimate_slot = threading.Lock()

    def generate(self, request, performance_source_sha256=None):
        return self._run(request, estimate=False, performance_source_sha256=performance_source_sha256)

    def estimate(self, request):
        return self._run(request, estimate=True)

    def _run(self, request, *, estimate, performance_source_sha256=None):
        if performance_source_sha256 is not None and not re.fullmatch(r'[a-f0-9]{64}', performance_source_sha256):
            raise GenerationError('INVALID_REQUEST', 400)
        if (not isinstance(request, dict) or set(request) - {'score', 'instrument', 'bpm', 'withJangdan', 'jangdan'}
                or not isinstance(request.get('score'), dict)
                or not isinstance(request.get('instrument'), str) or request['instrument'] not in INSTRUMENTS
                or type(request.get('bpm')) is not int or not 30 <= request['bpm'] <= 160
                or type(request.get('withJangdan')) is not bool
                or request['withJangdan'] and not isinstance(request.get('jangdan'), dict)):
            raise GenerationError('INVALID_REQUEST', 400)
        slot = self.estimate_slot if estimate else self.slot
        # Estimates belong to request handling, so concurrent members take turns.
        # Backend deadline 15s = this wait + the 10s estimate child limit.
        if not (slot.acquire(timeout=ESTIMATE_WAIT_SECONDS) if estimate else slot.acquire(blocking=False)):
            raise GenerationError('AUDIO_IN_PROGRESS', 409)
        try:
            with tempfile.TemporaryDirectory(prefix='sheet-audio-') as directory:
                root = Path(directory)
                if performance_source_sha256 is not None:
                    request = {**request, 'performanceSourceSha256': performance_source_sha256}
                (root / 'request.json').write_text(json.dumps(request, ensure_ascii=False, allow_nan=False), encoding='utf-8')
                try:
                    # The command comes only from server configuration. Never use a shell
                    # or expose renderer stderr (which may contain asset paths/input).
                    with subprocess.Popen([*self.command, directory, *(['--estimate'] if estimate else [])], stdin=subprocess.DEVNULL,
                                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                          start_new_session=os.name != 'nt') as child:
                        try:
                            status = child.wait(timeout=min(10,self.timeout) if estimate else self.timeout)
                            if status != 0:
                                LOG.warning('renderer exited with status %s', status)
                                raise GenerationError('SERVER_ERROR')
                        except subprocess.TimeoutExpired:
                            if os.name == 'nt':
                                subprocess.run(['taskkill', '/PID', str(child.pid), '/T', '/F'],
                                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
                            else:
                                try:
                                    os.killpg(child.pid, signal.SIGKILL)
                                except ProcessLookupError:
                                    pass
                            child.kill()
                            child.wait()
                            raise
                except subprocess.TimeoutExpired:
                    raise GenerationError('TIMEOUT', 504) from None
                except (OSError, subprocess.CalledProcessError):
                    raise GenerationError('SERVER_ERROR') from None
                artifacts = [('result.json',4096)] if estimate else [('audio.m4a', MAX_AUDIO), ('score.mid', MAX_MIDI), ('result.json', 4096)]
                for name, limit in artifacts:
                    path = root / name
                    if not path.is_file() or path.is_symlink() or path.stat().st_size == 0:
                        raise GenerationError('SERVER_ERROR')
                    if path.stat().st_size > limit:
                        raise GenerationError('TOO_LARGE', 422)
                try:
                    result = json.loads((root / 'result.json').read_text(encoding='utf-8'))
                    if estimate and result.get('errorCode') in ('EXPORT_TOO_LONG','INVALID_REQUEST'):
                        raise GenerationError(result['errorCode'],400)
                    if (set(result) != {'durationMs'} or type(result['durationMs']) is not int
                            or not 0 < result['durationMs'] <= 600000):
                        raise ValueError()
                except (ValueError, TypeError, KeyError):
                    raise GenerationError('SERVER_ERROR') from None
                if estimate:
                    return result
                if performance_source_sha256 is not None:
                    performance = root / 'performance.common.json'
                    if (performance.is_file() and not performance.is_symlink()
                            and 0 < performance.stat().st_size <= MAX_PERFORMANCE):
                        artifacts.append((performance.name, MAX_PERFORMANCE))
                output = io.BytesIO()
                with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_STORED) as bundle:
                    for name, _ in artifacts:
                        bundle.write(root / name, arcname=name)
                return output.getvalue()
        finally:
            slot.release()


def handler(service):
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def do_GET(self):
            self.reply(200, {'status': 'UP'}) if self.path == '/health' else self.reply(404, {'errorCode':'RESOURCE_NOT_FOUND'})

        def do_POST(self):
            if self.path not in ('/v1/render','/v1/estimate'):
                self.reply(404, {'errorCode': 'RESOURCE_NOT_FOUND'})
                return
            try:
                length = int(self.headers.get('Content-Length', '-1'))
                if (not 0 < length <= MAX_REQUEST or self.headers.get('Transfer-Encoding')
                        or self.headers.get_content_type() != 'application/json'):
                    raise GenerationError('INVALID_REQUEST', 400)
                raw = self.rfile.read(length)
                if len(raw) != length:
                    raise GenerationError('INVALID_REQUEST', 400)
                request = json.loads(raw, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
                if self.path == '/v1/estimate':
                    self.reply(200,service.estimate(request))
                    return
                payload = service.generate(request, self.headers.get(PERFORMANCE_SOURCE_HEADER))
                self.send_response(200)
                self.send_header('Content-Type', 'application/zip')
                self.send_header('Content-Length', str(len(payload)))
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                self.wfile.write(payload)
            except GenerationError as error:
                # Codes only: scores, request paths and asset locations stay out of logs.
                LOG.warning('request failed: %s (%s)', error.code, error.status)
                self.reply(error.status, {'errorCode': error.code})
            except (ValueError, UnicodeError):
                self.reply(400, {'errorCode': 'INVALID_REQUEST'})
            except (OSError, TimeoutError):
                self.close_connection = True

        def reply(self, status, value):
            payload = json.dumps(value).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(payload)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *_):
            pass  # Do not log score bodies, request paths or asset locations.

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8000)
    parser.add_argument('--timeout-seconds', type=float, default=float(os.environ.get('PLAYBACK_TIMEOUT_SECONDS', '90')))
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(name)s %(message)s')
    # Fail before accepting traffic if the mounted assets or codec tools are absent.
    assets_path = Path(os.environ['PLAYBACK_ASSETS_JSON'])
    assets = json.loads(assets_path.read_text(encoding='utf-8-sig'))
    for instrument in INSTRUMENTS:
        path = Path(assets[instrument])
        if not path.is_absolute():
            path = assets_path.parent / path
        if not path.is_file() or not zipfile.is_zipfile(path):
            raise ValueError('Required CAT archive is unavailable')
    from .render_request import load_janggu
    from .audio import resolve_encoder
    load_janggu(os.environ['PLAYBACK_JANGGU_SAMPLES'])
    resolve_encoder()
    service = PlaybackService([sys.executable, '-m', 'data.src.playback.render_request'], timeout_seconds=args.timeout_seconds)
    ThreadingHTTPServer(('0.0.0.0', args.port), handler(service)).serve_forever()


if __name__ == '__main__':
    main()
