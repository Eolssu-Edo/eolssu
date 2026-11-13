#!/usr/bin/python3
"""Observe public HTTPS endpoints from edo1 and record a local snapshot."""
from datetime import datetime, timezone
import http.client
import json
import os
from pathlib import Path
import ssl
from tempfile import NamedTemporaryFile


STATE_FILE = Path('/var/lib/edo-check-outside/latest.json')
MAX_BODY = 65536
TARGETS = {
    'relay': ('15.165.144.7', '/healthz', '중계 JSON 정상'),
    'terms': ('k15e102.p.ssafy.io', '/api/terms', '약관 목록 JSON 정상'),
}


def valid_terms(body):
    if (body.get('httpStatus') != 'OK' or body.get('isSuccess') is not True
            or type(body.get('code')) is not int or body['code'] != 200
            or not isinstance(body.get('message'), str)):
        return False
    result = body.get('result')
    items = result.get('items') if isinstance(result, dict) else None
    return isinstance(items, list) and all(
        isinstance(item, dict) and isinstance(item.get('required'), bool)
        and all(isinstance(item.get(field), str)
                for field in ('termId', 'code', 'title', 'version', 'contentUrl'))
        for item in items
    )


def probe(target):
    host, path, success = TARGETS[target]
    connection = http.client.HTTPSConnection(host, timeout=5)
    try:
        connection.request('GET', path, headers={'Accept': 'application/json'})
        response = connection.getresponse()
        if response.status != 200:
            return {'ok': False, 'detail': f'HTTP {response.status}'}
        content_type = response.getheader('Content-Type', '').split(';', 1)[0].strip().lower()
        if content_type != 'application/json':
            return {'ok': False, 'detail': 'HTTP 200 · JSON 응답 아님'}
        payload = response.read(MAX_BODY + 1)
        if len(payload) > MAX_BODY:
            return {'ok': False, 'detail': 'HTTP 200 · 응답 크기 초과'}
        body = json.loads(payload)
        valid = isinstance(body, dict) and (
            body == {'status': 'ok'} if target == 'relay' else valid_terms(body)
        )
        return {'ok': valid, 'detail': f'HTTP 200 · {success if valid else "JSON 형식 불일치"}'}
    except ssl.SSLCertVerificationError:
        return {'ok': False, 'detail': 'TLS 인증 실패'}
    except TimeoutError:
        return {'ok': False, 'detail': 'HTTPS 응답 시간 초과'}
    except (OSError, http.client.HTTPException):
        return {'ok': False, 'detail': 'HTTPS 요청 실패'}
    except (ValueError, RecursionError):
        return {'ok': False, 'detail': 'HTTP 200 · JSON 해석 실패'}
    finally:
        connection.close()


def collect():
    results = {target: probe(target) for target in TARGETS}
    captured_at = datetime.now(timezone.utc).isoformat(timespec='seconds').replace('+00:00', 'Z')
    return {'captured_at': captured_at, **results}


def publish(path, state):
    with NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent,
                            prefix='.latest.', delete=False) as temporary:
        temporary_path = Path(temporary.name)
        try:
            json.dump(state, temporary, ensure_ascii=False)
            temporary.write('\n')
            temporary.flush()
            os.fsync(temporary.fileno())
            temporary.close()
            temporary_path.replace(path)
        finally:
            temporary_path.unlink(missing_ok=True)


def read_previous(path):
    try:
        previous = json.loads(path.read_text(encoding='utf-8'))
    except (FileNotFoundError, ValueError, RecursionError):
        return {}
    return previous if isinstance(previous, dict) else {}


def main():
    previous = read_previous(STATE_FILE)
    result = collect()
    publish(STATE_FILE, result)
    for target in TARGETS:
        old = previous.get(target)
        was_ok = old.get('ok') if isinstance(old, dict) else None
        current = result[target]
        if current['ok'] and was_ok is False:
            event, priority = 'recovered', '<5>'
        elif not current['ok'] and was_ok is not False:
            event, priority = 'outage_started', '<4>'
        else:
            continue
        record = {'captured_at': result['captured_at'], 'target': target,
                  'event': event, 'detail': current['detail']}
        print(priority + json.dumps(record, ensure_ascii=False))


if __name__ == '__main__':
    main()
