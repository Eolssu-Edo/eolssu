"""Observe the owned QA emulator and isolated gateway without retaining secrets.

Gateway arrivals, app callbacks, app drawing, AI logs and database reads have
separate sources. No authorization headers, cookies, query strings, request
bodies or audio are retained. All network destinations are loopback.
"""
from collections import OrderedDict, deque
from datetime import datetime, timezone
from http.client import HTTPConnection, HTTPException
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import math
from pathlib import Path
import re
import shlex
import struct
import subprocess
import threading
import time
from urllib.parse import urlsplit
from uuid import UUID

MAX_BODY = 25 * 1024 * 1024
MAX_METADATA = 128 * 1024
CHUNK = 64 * 1024
AVD_NAME = "eolssu-qa-flow"
QA_PACKAGE = "com.ssafy.eolssu.qa"
MYSQL = "/opt/homebrew/opt/mysql@8.4/bin/mysql"
HOP = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
       "te", "trailer", "transfer-encoding", "upgrade", "expect"}
ALIASES = {"request_id": "requestId", "evaluation_timing_id": "evaluationTimingId",
           "session_id": "sessionId", "evaluation_submission": "evaluationSubmission", "duration_ms": "durationMs", "elapsedMs": "durationMs",
           "statusCode": "status", "wallMs": "durationMs", "queue_ms": "queueMs",
           "processing_ms": "processingMs"}
TOKENS = {"kind", "phase", "target", "flow", "outcome", "method", "modelVersion",
          "failReason", "state", "bpmUnit", "scoringVersion"}
NUMBERS = {"durationMs", "queueMs", "processingMs", "threadCpuMs", "ms", "attempt",
           "visit", "flow", "appRun", "evaluationSubmission", "count", "evaluations", "notes", "total", "pitch", "timing",
           "duration", "status", "bytes", "noteCount"}
SOURCES = {"app", "proxy", "backend", "ai", "db", "observer"}


def now():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def uuid_value(value):
    try:
        return str(UUID(str(value))) if len(str(value)) == 36 else None
    except (ValueError, TypeError, AttributeError):
        return None


def clean(fields):
    result = {}
    for key, value in fields.items():
        key = ALIASES.get(key, key)
        if key in {"requestId", "evaluationTimingId"}:
            value = uuid_value(value)
            if value:
                result[key] = value
        elif key in {"sessionId", "evaluationId"}:
            if re.fullmatch(r"[1-9][0-9]{0,18}", str(value)):
                result[key] = str(value)
        elif key in TOKENS and isinstance(value, str):
            if re.fullmatch(r"[A-Za-z0-9_.:-]{1,100}", value):
                result[key] = value
        elif key in NUMBERS and type(value) in (int, float):
            if 0 <= value <= 1e12 and math.isfinite(value):
                result[key] = value
        elif key == "status" and isinstance(value, str) and value in {"RECORDING", "PENDING", "DONE", "FAILED"}:
            result[key] = value
    return result


def target_for(method, path):
    fixed = {"/api/auth/sign-in": "auth_sign_in", "/api/auth/sign-up": "auth_sign_up"}
    if path in fixed:
        return fixed[path]
    if path == "/api/practice-sessions" and method == "POST":
        return "session_create"
    match = re.fullmatch(r"/api/practice-sessions/([1-9][0-9]{0,18})(?:/([a-z-]+))?", path)
    if match:
        return {None: "practice_status", "recording": "recording_upload", "evaluation": "evaluation",
                "retry": "analysis_retry"}.get(match[2], "api")
    if re.fullmatch(r"/api/songs/[1-9][0-9]{0,18}", path):
        return "song_detail"
    if re.fullmatch(r"/api/songs/[1-9][0-9]{0,18}/score", path):
        return "score"
    return "api"


class _Body:
    """Bounded incremental body reader, including chunked Android uploads."""
    def __init__(self, stream, length, chunked):
        self.stream, self.remaining, self.chunked = stream, length, chunked
        self.chunk_remaining = self.total = 0
        self.finished = not chunked and length == 0

    def read(self):
        if self.finished:
            return b""
        if self.chunked and not self.chunk_remaining:
            line = self.stream.readline(128)
            token = line.partition(b";")[0].strip()
            if not line.endswith(b"\r\n") or not re.fullmatch(rb"[0-9a-fA-F]{1,8}", token):
                raise ValueError("Invalid chunk")
            self.chunk_remaining = int(token, 16)
            if not self.chunk_remaining:
                total = 0
                while True:
                    trailer = self.stream.readline(8193)
                    total += len(trailer)
                    if not trailer or total > 16384:
                        raise ValueError("Invalid trailers")
                    if trailer == b"\r\n":
                        break
                self.finished = True
                return b""
        remaining = self.chunk_remaining if self.chunked else self.remaining
        data = self.stream.read(min(CHUNK, remaining))
        if not data:
            raise ValueError("Truncated request")
        self.total += len(data)
        if self.total > MAX_BODY:
            raise ValueError("Body limit exceeded")
        if self.chunked:
            self.chunk_remaining -= len(data)
            if not self.chunk_remaining and self.stream.read(2) != b"\r\n":
                raise ValueError("Invalid chunk terminator")
        else:
            self.remaining -= len(data)
            self.finished = not self.remaining
        return data


class _Server(ThreadingHTTPServer):
    daemon_threads = True
    block_on_close = False
    allow_reuse_address = True

    def __init__(self, address, handler):
        self.slots = threading.BoundedSemaphore(32)
        super().__init__(address, handler)

    def process_request(self, request, address):
        if not self.slots.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, address)
        except BaseException:
            self.slots.release()
            raise

    def process_request_thread(self, request, address):
        try:
            super().process_request_thread(request, address)
        finally:
            self.slots.release()


class Observer:
    def __init__(self, max_events=1600):
        self._lock, self._adb_lock, self._life_lock = threading.RLock(), threading.Lock(), threading.Lock()
        self._events = deque(maxlen=max(100, min(int(max_events), 5000)))
        self._seq = 0
        self._app_pid, self._app_run = None, 0
        self._stop = threading.Event()
        self._threads = []
        self._logcat = self._server = self._adb = None
        self._state, self._error = "stopped", None
        self._started_at = self._frame_at = self._frame = self._size = None
        self._device = {"name": AVD_NAME, "serial": None, "connected": False, "appVersion": None}
        self._requests, self._sessions, self._timings = OrderedDict(), OrderedDict(), OrderedDict()
        self._attempts = OrderedDict()
        self._db_seen, self._tails = {}, {}
        self._api = {"origin": None, "upstream": None, "listening": False}

    def emit(self, source, kind=None, **fields):
        if isinstance(source, dict):
            fields = {**source, **fields}
            source, kind = fields.pop("source", "observer"), fields.pop("kind", kind)
        if not isinstance(source, str) or source not in SOURCES:
            source = "observer"
        safe = clean({**fields, "kind": kind})
        with self._lock:
            self._seq += 1
            event = {"seq": self._seq, "at": now(), "source": source, **safe}
            self._events.append(event)
            rid, sid, tid = (event.get(key) for key in ("requestId", "sessionId", "evaluationTimingId"))
            if rid:
                context = self._requests.setdefault(rid, {})
                context.update({key: event[key] for key in (
                    "requestId", "evaluationTimingId", "sessionId", "flow", "visit", "appRun", "evaluationSubmission",
                ) if key in event})
                self._requests.move_to_end(rid)
                while len(self._requests) > 1024:
                    self._requests.popitem(last=False)
            if sid:
                context = self._sessions.setdefault(sid, {})
                context.update({key: event[key] for key in (
                    "sessionId", "evaluationTimingId", "attempt",
                ) if key in event})
                if rid and event.get("target") in {"recording_upload", "analysis_retry"}:
                    context["requestId"] = rid
                self._sessions.move_to_end(sid)
                while len(self._sessions) > 128:
                    old, _ = self._sessions.popitem(last=False)
                    self._db_seen.pop(old, None)
            if tid and sid:
                context = self._timings.setdefault(tid, {})
                context.update(sessionId=sid, evaluationTimingId=tid)
                if rid and event.get("target") in {"recording_upload", "analysis_retry"}:
                    context["requestId"] = rid
                if "attempt" in event:
                    context["attempt"] = event["attempt"]
                while len(self._timings) > 1024:
                    self._timings.popitem(last=False)
            if sid and "attempt" in event:
                key = (sid, event["attempt"])
                context = self._attempts.setdefault(key, {})
                context.update(sessionId=sid, attempt=event["attempt"])
                if tid:
                    context["evaluationTimingId"] = tid
                while len(self._attempts) > 256:
                    self._attempts.popitem(last=False)
            return dict(event)

    def _context(self, event):
        result = dict(event)
        rid, sid, tid = (event.get(key) for key in ("requestId", "sessionId", "evaluationTimingId"))
        attempt = event.get("attempt")
        if sid and attempt is not None and (sid, attempt) in self._attempts:
            result = {**self._attempts[(sid, attempt)], **result}
            tid = result.get("evaluationTimingId", tid)
        if tid in self._timings:
            result = {**self._timings[tid], **result}
            sid = result.get("sessionId")
        if sid in self._sessions:
            session = self._sessions[sid]
            if attempt is None or session.get("attempt") == attempt:
                result = {**session, **result}
        rid = result.get("requestId", rid)
        if rid in self._requests:
            result = {**self._requests[rid], **result}
        return result

    def status(self, after_seq=0):
        after_seq = max(0, int(after_seq))
        with self._lock:
            return {
                "state": self._state, "running": self._state == "running", "device": dict(self._device),
                "api": dict(self._api), "events": [self._context(e) for e in self._events if e["seq"] > after_seq],
                "eventCursor": self._seq,
                "eventsDropped": max(0, self._events[0]["seq"] - after_seq - 1) if self._events else 0,
                "error": self._error, "startedAt": self._started_at,
                "lastEventAt": self._events[-1]["at"] if self._events else None,
                "frameAt": self._frame_at, "frameSize": list(self._size) if self._size else None,
            }

    def _command(self, *args, timeout=8, binary=False):
        result = subprocess.run([*self._adb, *args], capture_output=True, timeout=timeout, check=True)
        return result.stdout if binary else result.stdout.decode("utf-8", "replace").strip()

    def start(self, workdir, adb, adbport, serial, *, proxy_port=18180, backend_port=18181, expected_identity=None, capture_frames=True):
        with self._life_lock:
            if self._state != "stopped":
                raise RuntimeError("Observer is already started")
            physical = expected_identity is not None
            if physical:
                if not re.fullmatch(r"[A-Za-z0-9_-]{4,80}", expected_identity) or not re.fullmatch(r"[A-Za-z0-9._:-]{4,100}", serial):
                    raise ValueError("An explicitly registered physical device is required")
            elif not re.fullmatch(r"emulator-[0-9]{4,5}", str(serial)):
                raise ValueError("Only the owned QA emulator can be observed")
            if not all(type(p) is int and 1024 <= p <= 65535 for p in (adbport, proxy_port, backend_port)):
                raise ValueError("Invalid local port")
            runtime = Path(workdir).resolve(strict=True) / "runtime"
            if not runtime.is_dir() or runtime.is_symlink():
                raise ValueError("Owned runtime directory is missing")
            self._adb = [str(Path(adb).resolve(strict=True)), "-P", str(adbport), "-s", serial]
            self._runtime, self._backend_port = runtime, backend_port
            self._state, self._error = "starting", None
            try:
                if self._command("get-state") != "device":
                    raise RuntimeError("Owned emulator is disconnected")
                if physical:
                    if self._command("shell", "getprop", "ro.serialno") != expected_identity:
                        raise ValueError("Physical device identity differs from registration")
                    name = self._command("shell", "getprop", "ro.product.model")
                    self._device["os"] = "Android " + self._command("shell", "getprop", "ro.build.version.release")
                else:
                    name = self._command("emu", "avd", "name").splitlines()[0]
                    if name != AVD_NAME:
                        raise ValueError("Emulator identity differs from owned QA device")
                self._stop.clear()
                with self._lock:
                    self._events.clear()
                    self._app_pid = None
                    self._requests.clear()
                    self._sessions.clear()
                    self._timings.clear()
                    self._attempts.clear()
                self._threads, self._tails, self._db_seen = [], {}, {}
                self._frame = self._frame_at = self._size = None
                self._device.update(name=name, serial=serial, connected=True, appVersion=None)
                self._server = _Server(("127.0.0.1", proxy_port), self._handler())
                self._api.update(origin=f"http://127.0.0.1:{proxy_port}",
                                 upstream=f"http://127.0.0.1:{backend_port}", listening=True)
                self._started_at, self._state = now(), "running"
                targets = [self._server.serve_forever, self._logcat_loop, self._tail_loop,
                           self._database_loop, self._package_loop]
                if capture_frames:
                    targets.append(self._frames_loop)
                for target in targets:
                    thread = threading.Thread(target=target, daemon=True)
                    self._threads.append(thread)
                    thread.start()
                self.emit("observer", "started", target="live_app")
                return self.status()
            except Exception:
                self._stop.set()
                self._state, self._error = "error", "실시간 연결을 시작하지 못했습니다."
                self._device["connected"] = False
                if self._server:
                    self._server.server_close()
                    self._server = None
                raise

    def stop(self):
        with self._life_lock:
            if self._state == "stopped":
                return self.status()
            self._state = "stopping"
            self._stop.set()
            if self._logcat and self._logcat.poll() is None:
                self._logcat.terminate()
            if self._server:
                self._server.shutdown()
                self._server.server_close()
                self._server = None
            for thread in self._threads:
                if thread is not threading.current_thread():
                    thread.join(timeout=4)
            if self._logcat and self._logcat.poll() is None:
                self._logcat.kill()
                self._logcat.wait(timeout=3)
            self._device["connected"], self._api["listening"], self._state = False, False, "stopped"
            self.emit("observer", "stopped", target="live_app")
            return self.status()

    def frame(self):
        with self._lock:
            if self._state != "running" or self._frame is None:
                raise RuntimeError("QA frame is not available")
            return self._frame

    def input(self, action, x=None, y=None, text=None, replace=False):
        with self._adb_lock:
            if self._state != "running" or not self._device["connected"]:
                raise RuntimeError("Owned emulator is disconnected")
            if action == "tap":
                if not all(type(v) in (int, float) and math.isfinite(v) and 0 <= v <= 1 for v in (x, y)):
                    raise ValueError("Tap coordinates must be normalized")
                if not self._size:
                    raise RuntimeError("QA frame size is not available")
                width, height = self._size
                self._command("shell", "input", "tap", str(round(x * (width - 1))), str(round(y * (height - 1))))
            elif action == "back":
                self._command("shell", "input", "keyevent", "KEYCODE_BACK")
            elif action == "text":
                if not isinstance(text, str) or not re.fullmatch(r"[A-Za-z0-9 @._!+=%-]{1,128}", text):
                    raise ValueError("Unsupported text input")
                if replace:
                    # Samsung Android 12 keycombination does not set the Compose Ctrl modifier.
                    # Only delete inside the selected field; never navigate to another field.
                    self._command("shell", "input", "keyevent", "123", *("67" for _ in range(128)), timeout=20)
                self._command("shell", "input", "text", shlex.quote(text.replace(" ", "%s")))
            else:
                raise ValueError("Unsupported emulator action")
        self.emit("observer", "input_sent", target=action, phase="action")
        return {"accepted": True, "action": action}

    def _read_package_version(self):
        description = self._command("shell", "dumpsys", "package", QA_PACKAGE, timeout=5)
        match = re.search(r"(?m)^\s*versionName=([A-Za-z0-9][A-Za-z0-9._+-]{0,79})[ \t\r]*$", description)
        version = match[1] if match and match[1] != "null" else None
        with self._lock:
            if not self._stop.is_set():
                self._device["appVersion"] = version

    def _package_loop(self):
        while not self._stop.is_set():
            try:
                self._read_package_version()
            except (OSError, subprocess.SubprocessError):
                pass
            # Installation follows observer startup. Refresh also notices an
            # APK replacement after a previous QA version was already present.
            self._stop.wait(5)

    def _frames_loop(self):
        errors = 0
        while not self._stop.is_set():
            try:
                frame = self._command("exec-out", "screencap", "-p", timeout=6, binary=True)
                if len(frame) < 24 or frame[:8] != b"\x89PNG\r\n\x1a\n" or len(frame) > 8 * 1024 * 1024:
                    raise ValueError("Invalid screenshot")
                size = struct.unpack(">II", frame[16:24])
                if not all(1 <= n <= 10000 for n in size):
                    raise ValueError("Invalid screenshot dimensions")
                with self._lock:
                    self._frame, self._size, self._frame_at = frame, size, now()
                    self._device["connected"] = True
                errors = 0
            except (OSError, ValueError, subprocess.SubprocessError):
                errors += 1
                with self._lock:
                    self._device["connected"] = False
                if errors == 1:
                    self.emit("observer", "frame_unavailable", target="screen")
            self._stop.wait(0.8 if errors == 0 else 2)

    def ingest_app_line(self, line):
        if len(line) > 16384:
            return
        prefix = re.fullmatch(r"[VDIWEF]/EolssuLive\s*\(\s*([1-9][0-9]{0,9})\s*\):\s*(\{.*)", line)
        if prefix:
            pid, payload = int(prefix[1]), prefix[2]
        elif line.startswith("{"):
            # Unprefixed fixtures retain the historical schema. Live logcat
            # always uses brief format and its observed process identity.
            pid, payload = None, line
        else:
            return
        try:
            fields = json.loads(payload)
        except (ValueError, TypeError):
            return
        if (not isinstance(fields, dict) or not isinstance(fields.get("kind"), str)
                or fields.get("kind") not in {
            "request_started", "request_finished", "action", "visit_started",
            "milestone", "data_load", "http_attempt", "poll_summary",
        }):
            return
        fields.pop("source", None)
        fields.pop("appRun", None)
        kind = fields.pop("kind")
        if kind in {"request_started", "request_finished"}:
            fields["phase"] = "request" if kind == "request_started" else "response"
        with self._lock:
            if pid is not None:
                if pid != self._app_pid:
                    self._app_run += 1
                    self._app_pid = pid
                fields["appRun"] = self._app_run
            self.emit("app", kind, **fields)

    def _device_logcat_since(self):
        # adb shell joins arguments remotely. Quote the fixed date format so
        # the spaces remain one date argument on the device.
        stamp = self._command("shell", "date", shlex.quote("+%m-%d %H:%M:%S.%N"), timeout=4)
        if not re.fullmatch(r"[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{9}", stamp):
            raise ValueError("Device clock is not available")
        return stamp[:-6]

    def _logcat_loop(self):
        try:
            since = self._device_logcat_since()
            self._logcat = subprocess.Popen(
                [*self._adb, "logcat", "-v", "brief", "-T", since, "EolssuLive:I", "*:S"],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            )
            while not self._stop.is_set():
                line = self._logcat.stdout.readline(16385)
                if not line:
                    break
                self.ingest_app_line(line.decode("utf-8", "replace").strip())
            if not self._stop.is_set():
                self.emit("observer", "logcat_unavailable", target="app")
        except (OSError, ValueError, subprocess.SubprocessError):
            self.emit("observer", "logcat_unavailable", target="app")

    def ingest_server_line(self, line, source):
        if len(line) > 16384:
            return
        marker = re.search(r"QA_EVENT_JSON\s*[=:]?\s*(\{.*\})", line)
        if marker:
            try:
                event = json.loads(marker[1])
            except ValueError:
                return
            if isinstance(event, dict):
                event.pop("source", None)
                kind = event.pop("kind", event.pop("event", "state_observed"))
                self.emit(source, kind, **event)
            return
        if source == "ai":
            marker = line.find('{"event":"ai_phase"')
            if marker >= 0:
                try:
                    event = json.loads(line[marker:])
                except ValueError:
                    return
                event.pop("event", None)
                self.emit("ai", "phase_finished", **event)
                return
            match = re.search(r"analysis session=(\d+) attempt=(\d+) result=(\w+) ms=(\d+)", line)
            if match:
                self.emit("ai", "analysis_finished", sessionId=match[1], attempt=int(match[2]),
                          outcome=match[3], durationMs=int(match[4]), phase="finished", target="grading")
        elif source == "backend":
            match = re.search(r"Analysis finished session=(\d+) attempt=(\d+) outcome=(\w+) elapsedMs=(\d+)", line)
            if match:
                self.emit("backend", "analysis_finished", sessionId=match[1], attempt=int(match[2]),
                          outcome=match[3], durationMs=int(match[4]), phase="finished", target="grading")
            match = re.search(r"event=evaluation_analysis_timing id=([a-f0-9-]{36}) attempt=(\d+) "
                              r"queue_ms=(\d+) processing_ms=(\d+) outcome=(\w+)", line)
            if match:
                self.emit("backend", "analysis_timing", evaluationTimingId=match[1], attempt=int(match[2]),
                          queueMs=int(match[3]), processingMs=int(match[4]), outcome=match[5], target="grading")
            match = re.search(r"Analysis timed out session=(\d+)", line)
            if match:
                self.emit("backend", "analysis_timeout", sessionId=match[1], phase="failed", target="grading")

    def _tail_loop(self):
        while not self._stop.is_set():
            paths = [(self._runtime / "backend.log", "backend")]
            paths.extend((p, "ai") for p in self._runtime.glob("ai-18*.log"))
            for path, source in paths:
                try:
                    stat = path.stat()
                    state = self._tails.get(path)
                    if state is None:
                        self._tails[path] = (stat.st_ino, stat.st_size, b"")
                        continue
                    inode, offset, pending = state
                    if inode != stat.st_ino or stat.st_size < offset:
                        offset, pending = 0, b""
                    with path.open("rb") as stream:
                        stream.seek(offset)
                        data = stream.read(256 * 1024)
                        offset = stream.tell()
                    lines = (pending + data).split(b"\n")
                    pending = lines.pop()
                    self._tails[path] = (stat.st_ino, offset, pending if len(pending) <= 16384 else b"")
                    for line in lines:
                        self.ingest_server_line(line.decode("utf-8", "replace"), source)
                except OSError:
                    continue
            self._stop.wait(0.2)

    def _query(self, sql):
        sock = self._runtime / "scratchpad/mysql-qa/mysql.sock"
        result = subprocess.run(
            [MYSQL, "--no-defaults", "--protocol=SOCKET", f"--socket={sock}", "-uroot", "-NB",
             "--connect-timeout=2", "-e", sql, "eolssu_qa"],
            capture_output=True, text=True, timeout=4, check=True,
        )
        return result.stdout.strip()

    def _database_loop(self):
        verified = unavailable = False
        while not self._stop.is_set():
            with self._lock:
                ids = list(self._sessions)
            try:
                if ids:
                    if not verified:
                        self.verify_database()
                        verified = True
                    selected = ",".join(ids)
                    sql = (
                        "SELECT s.id,s.status,s.analysis_attempt,COALESCE(s.analysis_timing_id,'-'),"
                        "COUNT(DISTINCT e.id),COUNT(n.note_idx),COALESCE(MAX(e.id),0),COALESCE(s.fail_reason,'-') "
                        "FROM practice_sessions s LEFT JOIN evaluations e ON e.session_id=s.id "
                        f"LEFT JOIN note_results n ON n.evaluation_id=e.id WHERE s.id IN ({selected}) "
                        "GROUP BY s.id,s.status,s.analysis_attempt,s.analysis_timing_id,s.fail_reason"
                    )
                    for row in self._query(sql).splitlines():
                        sid, status, attempt, tid, evaluations, notes, eid, fail = row.split("\t")
                        fingerprint = (status, attempt, tid, evaluations, notes, eid, fail)
                        if self._db_seen.get(sid) == fingerprint:
                            continue
                        self._db_seen[sid] = fingerprint
                        phase = {"PENDING": "queued", "DONE": "saved", "FAILED": "failed"}.get(status, "created")
                        self.emit("db", "state_observed", sessionId=sid, status=status, phase=phase, attempt=int(attempt),
                                  evaluationTimingId=tid, evaluations=int(evaluations), notes=int(notes),
                                  evaluationId=eid, failReason=fail, target="grading")
                unavailable = False
            except (OSError, ValueError, subprocess.SubprocessError):
                if not unavailable:
                    self.emit("db", "unavailable", target="grading")
                unavailable = True
            self._stop.wait(0.75 if not unavailable else 2)

    def verify_database(self):
        expected = str(self._runtime / "scratchpad/mysql-qa/data") + "/"
        if self._query("SELECT @@datadir") != expected:
            raise ValueError("Owned database directory differs")

    def _metadata(self, body):
        try:
            result = json.loads(body).get("result", {})
            if not isinstance(result, dict):
                return {}
            fields = {key: result[key] for key in (
                "sessionId", "evaluationId", "attempt", "evaluationTimingId",
            ) if key in result}
            fields["state"] = result.get("status")
            return clean(fields)
        except (ValueError, TypeError, AttributeError):
            return {}

    def _handler(self):
        observer = self

        class Handler(BaseHTTPRequestHandler):
            protocol_version = "HTTP/1.1"

            def setup(self):
                super().setup()
                self.connection.settimeout(75)

            def log_message(self, *_args):
                pass

            def do_GET(self):
                self._forward()

            do_POST = do_GET
            do_PUT = do_GET
            do_PATCH = do_GET
            do_DELETE = do_GET
            do_HEAD = do_GET
            do_OPTIONS = do_GET

            def _forward(self):
                began = time.monotonic()
                parsed = urlsplit(self.path)
                if parsed.scheme or parsed.netloc or not parsed.path.startswith("/api/"):
                    self.send_error(404)
                    return
                raw_length, transfer = self.headers.get("Content-Length"), self.headers.get("Transfer-Encoding", "").lower()
                if transfer and (transfer != "chunked" or raw_length is not None):
                    self.send_error(400)
                    return
                try:
                    length = int(raw_length or 0)
                except ValueError:
                    length = -1
                if length < 0 or length > MAX_BODY:
                    self.send_error(413)
                    return
                if raw_length is not None and len(self.headers.get_all("Content-Length")) != 1:
                    self.send_error(400)
                    return
                rid, tid = uuid_value(self.headers.get("X-QA-Request-Id")), uuid_value(self.headers.get("X-Evaluation-Timing-Id"))
                target = target_for(self.command, parsed.path)
                match = re.match(r"/api/practice-sessions/([1-9][0-9]{0,18})(?:/|$)", parsed.path)
                fields = {"requestId": rid, "evaluationTimingId": tid,
                          "sessionId": match[1] if match else None, "target": target, "method": self.command}
                observer.emit("proxy", "request_received", phase="request", **fields)
                connection = HTTPConnection("127.0.0.1", observer._backend_port, timeout=70)
                sent = False
                try:
                    dropped = HOP | {s.strip().lower() for s in self.headers.get("Connection", "").split(",")}
                    connection.putrequest(self.command, self.path, skip_host=True, skip_accept_encoding=True)
                    for key, value in self.headers.items():
                        if key.lower() not in dropped:
                            connection.putheader(key, value)
                    if transfer:
                        connection.putheader("Transfer-Encoding", "chunked")
                    connection.putheader("Connection", "close")
                    connection.endheaders()
                    body = _Body(self.rfile, length, bool(transfer))
                    while True:
                        data = body.read()
                        if not data:
                            break
                        connection.send(f"{len(data):X}\r\n".encode() + data + b"\r\n" if transfer else data)
                    if transfer:
                        connection.send(b"0\r\n\r\n")
                    response = connection.getresponse()
                    fields["evaluationTimingId"] = uuid_value(response.getheader("X-Evaluation-Timing-Id")) or tid
                    self.send_response_only(response.status)
                    headers, dropped = response.getheaders(), set(HOP)
                    for key, value in headers:
                        if key.lower() == "connection":
                            dropped.update(s.strip().lower() for s in value.split(","))
                    for key, value in headers:
                        if key.lower() not in dropped:
                            self.send_header(key, value)
                    self.send_header("Connection", "close")
                    self.end_headers()
                    sent = True
                    metadata = bytearray()
                    collect = target in {"session_create", "practice_status", "recording_upload", "analysis_retry", "evaluation"}
                    while True:
                        data = response.read(CHUNK)
                        if not data:
                            break
                        self.wfile.write(data)
                        if collect and len(metadata) + len(data) <= MAX_METADATA:
                            metadata.extend(data)
                        else:
                            collect = False
                            metadata.clear()
                    if collect:
                        fields.update(observer._metadata(metadata))
                    observer.emit("proxy", "response_finished", **fields, phase="response", status=response.status,
                                  durationMs=round((time.monotonic() - began) * 1000, 3))
                except (OSError, ValueError, HTTPException) as error:
                    observer.emit("proxy", "request_failed", **fields, phase="failed", outcome=type(error).__name__,
                                  durationMs=round((time.monotonic() - began) * 1000, 3))
                    if not sent:
                        try:
                            self.send_error(502)
                        except OSError:
                            pass
                finally:
                    connection.close()
                    self.close_connection = True

        return Handler
