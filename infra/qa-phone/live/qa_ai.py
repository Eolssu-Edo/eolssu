"""Opt-in tracing and a one-shot retry fault in the isolated QA AI container."""
from datetime import datetime, timezone
import json
from pathlib import Path
from time import monotonic
from fastapi import HTTPException
from ai_service import api

app = api.app
original = api.analyze
gate = Path('/runtime/retry-first-attempt.json')


def observed(request, fetch):
    if request.attempt == 1 and gate.is_file():
        selected = json.loads(gate.read_text())
        if str(request.session_id) == selected.get('sessionId'):
            gate.replace(gate.with_name(gate.name + '.consumed'))
            raise HTTPException(503, 'QA_RETRY_ONCE')
    began = monotonic()
    outcome = 'FAILED'

    def emit(kind):
        event = {
            'kind': kind, 'sessionId': str(request.session_id), 'attempt': request.attempt,
            'at': datetime.now(timezone.utc).isoformat(),
            'durationMs': round((monotonic() - began) * 1000),
        }
        if kind == 'analysis_finished':
            event['outcome'] = outcome
        api.log.info('QA_EVENT_JSON %s', json.dumps(event, separators=(',', ':')))

    emit('analysis_started')
    try:
        result = original(request, fetch)
        outcome = 'DONE'
        return result
    finally:
        emit('analysis_finished')


route = next(route for route in app.routes if getattr(route, 'path', '') == '/v1/analyses')
route.dependant.call = observed
