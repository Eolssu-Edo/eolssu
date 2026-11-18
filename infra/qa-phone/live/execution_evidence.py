"""Bounded, sanitized evidence from one isolated server test execution."""
from collections import deque
from pathlib import Path
import re
import threading
from uuid import uuid4

from live_observer import Observer, clean, now, target_for


class ExecutionEvidence:
    def __init__(self, runtime):
        self.runtime = runtime
        self.lock = threading.RLock()
        self.records = deque(maxlen=500)
        self.requests, self.sessions = set(), set()
        self.offsets = {}
        self.parser = Observer(max_events=500)
        self.cursor = 0
        self.sequence = 0
        for path in self.paths():
            self.offsets[path] = (path.stat().st_size, b'')

    def paths(self):
        root = self.runtime.work / 'runtime'
        return [path for path in [root / 'backend.log', *root.glob('ai-18*.log')] if path.is_file()]

    def append(self, source, kind, **fields):
        with self.lock:
            self.sequence += 1
            self.records.append({'seq': self.sequence, 'at': now(), 'source': source, **clean({'kind': kind, **fields})})

    async def request(self, request):
        rid = str(uuid4())
        request.headers['X-QA-Request-Id'] = rid
        match = re.fullmatch(r'/api/practice-sessions/([1-9][0-9]{0,18})(?:/[a-z-]+)?', request.url.path)
        with self.lock:
            self.requests.add(rid)
            if match:
                self.sessions.add(match[1])
        self.append('test', 'request_started', requestId=rid, method=request.method,
                    target=target_for(request.method, request.url.path), sessionId=match[1] if match else None,
                    evaluationTimingId=request.headers.get('X-Evaluation-Timing-Id'))

    async def response(self, response):
        request = response.request
        self.append('test', 'response_finished', requestId=request.headers.get('X-QA-Request-Id'),
                    target=target_for(request.method, request.url.path), status=response.status_code,
                    evaluationTimingId=request.headers.get('X-Evaluation-Timing-Id'))

    def database(self, evidence):
        for sid, row in evidence.get('sessions', {}).items():
            if sid in self.sessions:
                self.append('db', 'state_observed', sessionId=sid, status=row.get('status'),
                            attempt=row.get('attempts'), evaluations=row.get('evaluations'), notes=row.get('notes'),
                            modelVersion=row.get('modelVersion'), failReason=row.get('failReason'))
        return evidence

    def snapshot(self):
        with self.lock:
            for path in self.paths():
                offset, pending = self.offsets.get(path, (0, b''))
                try:
                    if path.stat().st_size < offset:
                        offset, pending = 0, b''
                    with path.open('rb') as stream:
                        stream.seek(offset)
                        data = stream.read(4 * 1024 * 1024)
                        offset = stream.tell()
                    lines = (pending + data).split(b'\n')
                    pending = lines.pop()
                    self.offsets[path] = (offset, pending if len(pending) <= 16384 else b'')
                    for line in lines:
                        self.parser.ingest_server_line(line.decode('utf8', 'replace'), 'backend' if path.name == 'backend.log' else 'ai')
                except OSError:
                    continue
            parsed = self.parser.status(self.cursor)
            self.cursor = parsed['eventCursor']
            for event in parsed['events']:
                if event.get('requestId') in self.requests or event.get('sessionId') in self.sessions:
                    fields = {key: value for key, value in event.items() if key not in {'source', 'kind', 'at', 'seq'}}
                    self.append(event['source'], event['kind'], **fields)
            return list(self.records)
