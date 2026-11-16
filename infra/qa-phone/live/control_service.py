"""Loopback control boundary used by the authenticated Windows QA relay."""
import argparse
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import signal
import threading
from windows_runtime import Runtime


def serve(config, directory):
    token = config['token']
    origin = config['origin']
    runtime = Runtime(config, directory)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def respond(self, status, value):
            body = json.dumps(value, ensure_ascii=False).encode('utf8')
            self.send_response(status)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def authorized(self):
            return (self.client_address[0] == '127.0.0.1' and self.headers.get('Host') == origin.removeprefix('http://')
                    and self.headers.get('Origin') == origin
                    and hmac.compare_digest(self.headers.get('X-QA-Token', ''), token))

        def do_GET(self):
            if not self.authorized():
                self.respond(403, {'error': 'forbidden'})
            elif self.path == '/status':
                identity = self.headers.get('X-QA-Device', config['identity'])
                if identity not in runtime.devices:
                    self.respond(403, {'error': 'registered device required'})
                else:
                    self.respond(200, runtime.status(identity))
            else:
                self.respond(404, {'error': 'not found'})

        def do_POST(self):
            if not self.authorized():
                self.respond(403, {'error': 'forbidden'})
                return
            if self.path == '/shutdown':
                self.respond(202, {'accepted': True})
                threading.Thread(target=server.shutdown, daemon=True).start()
                return
            if self.path != '/control' or self.headers.get('Content-Type') != 'application/json':
                self.respond(400, {'error': 'invalid request'})
                return
            try:
                length = int(self.headers.get('Content-Length', '-1'))
                if not 0 < length <= 4096 or self.headers.get('Transfer-Encoding'):
                    raise ValueError('invalid length')
                body = json.loads(self.rfile.read(length))
                identity = self.headers.get('X-QA-Device', config['identity'])
                self.respond(202, runtime.command(body, identity))
            except BlockingIOError:
                self.respond(409, {'error': 'environment busy'})
            except PermissionError:
                self.respond(403, {'error': 'run owner required'})
            except ValueError:
                self.respond(400, {'error': 'invalid action'})
            except Exception:
                self.respond(503, {'error': 'QA service unavailable'})

    server = ThreadingHTTPServer(('127.0.0.1', int(origin.rsplit(':', 1)[1])), Handler)
    server.daemon_threads = True
    worker = threading.Thread(target=runtime.prepare, daemon=True)
    worker.start()
    def stop(*_args):
        threading.Thread(target=server.shutdown, daemon=True).start()
    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGTERM, stop)
    try:
        server.serve_forever()
    finally:
        runtime.close()
        server.server_close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, required=True)
    options = parser.parse_args()
    serve(json.loads(options.config.read_text(encoding='utf8')), options.runtime)
