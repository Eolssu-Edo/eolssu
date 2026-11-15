#!/usr/bin/env python3
"""Local stand-in for the AI service's POST /v1/analyses.

It downloads the recording from the signed audioUrl (like the real service),
waits --delay seconds to imitate analysis time, and answers DONE with every
requested note judged CORRECT. The backend still runs its real executor
(2 workers by default) and 60s deadline, so queueing under a burst is real;
only the analysis cost is replaced by a fixed wait.

Usage:
  python3 infra/loadtest/fake_ai.py --port 18000 --delay 4.0
"""
from __future__ import annotations

import argparse
import json
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def done_body(request: dict) -> dict:
    notes = [{"idx": note["idx"], "verdict": "CORRECT"} for note in request.get("notes", [])]
    return {
        "status": "DONE",
        "modelVersion": "loadtest-stub",
        "total": 90,
        "pitch": 90,
        "timing": 90,
        "duration": 90,
        "notes": notes,
    }


class Stats:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.active = 0
        self.peak = 0
        self.total = 0

    def enter(self) -> None:
        with self.lock:
            self.active += 1
            self.total += 1
            self.peak = max(self.peak, self.active)

    def leave(self) -> None:
        with self.lock:
            self.active -= 1


def handler(delay: float, fetch_audio: bool, stats: Stats) -> type[BaseHTTPRequestHandler]:
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, format: str, *args) -> None:  # noqa: A002 - stdlib signature
            return

        def reply(self, status: int, body: dict) -> None:
            data = json.dumps(body).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self) -> None:  # noqa: N802 - stdlib name
            if self.path == "/stats":
                self.reply(200, {"active": stats.active, "peak": stats.peak, "total": stats.total})
            else:
                self.reply(404, {"error": "not found"})

        def do_POST(self) -> None:  # noqa: N802 - stdlib name
            if self.path != "/v1/analyses":
                self.reply(404, {"error": "not found"})
                return
            length = int(self.headers.get("Content-Length", "0"))
            request = json.loads(self.rfile.read(length) or b"{}")
            stats.enter()
            try:
                if fetch_audio:
                    with urllib.request.urlopen(request["audioUrl"], timeout=30) as response:
                        response.read()
                time.sleep(delay)
                self.reply(200, done_body(request))
            except Exception:  # noqa: BLE001 - the real service answers 502 when audio cannot be read
                self.reply(502, {"error": "audio download failed"})
            finally:
                stats.leave()

    return Handler


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18000)
    parser.add_argument("--delay", type=float, default=4.0, help="seconds per analysis")
    parser.add_argument("--no-fetch-audio", action="store_true", help="skip downloading audioUrl")
    args = parser.parse_args()
    if args.host not in ("127.0.0.1", "localhost", "::1"):
        parser.error("fake AI only binds to localhost")
    server = ThreadingHTTPServer((args.host, args.port), handler(args.delay, not args.no_fetch_audio, Stats()))
    print(f"fake-ai on {args.host}:{args.port} delay={args.delay}s", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
