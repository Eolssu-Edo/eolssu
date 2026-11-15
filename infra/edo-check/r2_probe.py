#!/usr/bin/env python3
"""edo1 R2 probes with fixed targets and sanitized results."""
from contextlib import closing
from datetime import datetime, timezone
import http.client
import json
import logging
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import time
from urllib.parse import parse_qs, urlsplit
from uuid import uuid4


API_HOST = 'k15e102.p.ssafy.io'
ASSET_ID = 'c64c09876e71be58f9ac1e457e42f5666e9b81e9cad0aa26806708725c3c0e86'
ASSET_SHA256 = 'e6a9a9eb2fe9e0ee2506ec91aa0efe5323f022b9fa036a5c87e96285d986a81e'
ASSET_PREFIX = bytes.fromhex('0000001c667479704d344120000002004d34412069736f6d69736f3200000662')
BUCKET = 'eolssu-recordings'
SHEET_BUCKET = 'eolssu-sheet-audio'
STORAGE_OK = 'put_signed_get_delete_ok'
R2_HOST = re.compile(r'[a-f0-9]{32}\.r2\.cloudflarestorage\.com')
READ_TIMEOUT = 3
RECORDING_ENV = Path('/opt/eolssu/.env')
WRITE_RESULT = Path('/var/lib/edo-check-r2/write.json')
WRITE_OK = 'put_get_delete_ok'
SETTINGS_PREFIX = 'RECORDING_STORAGE_'
SETTINGS_FIELDS = ('ENDPOINT', 'BUCKET', 'ACCESS_KEY_ID', 'SECRET_ACCESS_KEY', 'REGION')


def validated_location(location, expected_path, expected_host=None):
    if not location or len(location) > 2048 or any(ord(char) <= 32 or ord(char) >= 127 for char in location):
        raise ValueError('invalid R2 redirect')
    url = urlsplit(location)
    if (url.scheme != 'https' or not R2_HOST.fullmatch(url.netloc)
            or (expected_host is not None and url.netloc != expected_host)
            or url.path != expected_path or url.fragment):
        raise ValueError('invalid R2 redirect')
    query = parse_qs(url.query, strict_parsing=True, max_num_fields=12)
    signatures = query.get('X-Amz-Signature', [])
    if (query.get('X-Amz-Algorithm') != ['AWS4-HMAC-SHA256']
            or len(signatures) != 1 or not re.fullmatch(r'[a-f0-9]{64}', signatures[0])):
        raise ValueError('invalid R2 signature')
    return url


def check_read_range():
    try:
        with closing(http.client.HTTPSConnection(API_HOST, timeout=READ_TIMEOUT)) as conn:
            conn.request('GET', f'/api/reference-audios/{ASSET_ID}/play')
            response = conn.getresponse()
            if response.status != 302:
                return f'backend_http_{response.status}'
            location = response.getheader('Location')
    except (OSError, http.client.HTTPException):
        return 'backend_connection'

    try:
        path = f'/{BUCKET}/reference-audio/v1/danso/{ASSET_ID}/{ASSET_SHA256}.m4a'
        url = validated_location(location, path)
    except ValueError:
        return 'invalid_redirect'

    try:
        with closing(http.client.HTTPSConnection(url.hostname, timeout=READ_TIMEOUT)) as conn:
            conn.request('GET', f'{url.path}?{url.query}', headers={'Range': 'bytes=0-31'})
            response = conn.getresponse()
            if response.status != 206:
                return f'r2_http_{response.status}'
            content_type = (response.getheader('Content-Type') or '').partition(';')[0].strip().lower()
            if (response.getheader('Content-Range') != 'bytes 0-31/68500'
                    or content_type != 'audio/mp4'
                    or response.read(len(ASSET_PREFIX) + 1) != ASSET_PREFIX):
                return 'invalid_range'
    except (OSError, http.client.HTTPException):
        return 'r2_connection'
    return 'range_ok'


def read_probe():
    started = time.monotonic_ns()
    detail = check_read_range()
    return {'status': 'ok' if detail == 'range_ok' else 'failed', 'detail': detail,
            'elapsed_ms': (time.monotonic_ns() - started) // 1_000_000}


def recording_settings():
    fields = {SETTINGS_PREFIX + name for name in SETTINGS_FIELDS}
    settings = {}
    for line in RECORDING_ENV.read_text(encoding='utf-8-sig').splitlines():
        key, separator, raw = line.strip().removeprefix('export ').partition('=')
        key = key.strip()
        if separator and key in fields:
            words = shlex.split(raw, comments=True, posix=True)
            if len(words) > 1:
                raise ValueError('invalid R2 configuration')
            settings[key.removeprefix(SETTINGS_PREFIX)] = words[0] if words else ''
    return validated_settings(settings, BUCKET)


def active_storage_settings(prefix, bucket):
    slot = subprocess.run(['/opt/eolssu/bin/deploy-backend', '--active-container'],
                          capture_output=True, check=True, text=True, timeout=4).stdout.strip()
    if slot not in ('eolssu-runtime-backend-1', 'eolssu-runtime-backend-green-1'):
        raise ValueError('invalid active backend')
    output = subprocess.run(['docker', 'inspect', '--format', '{{json .Config.Env}}', slot],
                            capture_output=True, check=True, text=True, timeout=4).stdout
    entries = json.loads(output)
    if not isinstance(entries, list):
        raise ValueError('invalid active backend environment')
    settings = {}
    for entry in entries:
        key, _, value = entry.partition('=')
        name = key.removeprefix(prefix)
        if key != name and name in SETTINGS_FIELDS:
            if name in settings:
                raise ValueError('duplicate active storage setting')
            settings[name] = value
    return validated_settings(settings, bucket)


def validated_settings(settings, bucket):
    if not all(settings.get(name) for name in SETTINGS_FIELDS[:-1]):
        raise ValueError('missing R2 configuration')
    endpoint = urlsplit(settings['ENDPOINT'])
    if (endpoint.scheme != 'https' or not R2_HOST.fullmatch(endpoint.netloc)
            or endpoint.path not in ('', '/') or endpoint.query or endpoint.fragment
            or settings['BUCKET'] != bucket):
        raise ValueError('unexpected R2 write target')
    settings['REGION'] = settings.get('REGION') or 'auto'
    return settings


def write_client(settings):
    import boto3
    from botocore.config import Config

    logging.getLogger('botocore').setLevel(logging.CRITICAL)
    options = dict(signature_version='s3v4', s3={'addressing_style': 'path'}, proxies={},
                   connect_timeout=3, read_timeout=4, retries={'total_max_attempts': 1})
    try:
        config = Config(**options, request_checksum_calculation='when_required',
                        response_checksum_validation='when_required')
    except TypeError:
        config = Config(**options)  # The installed 1.34 SDK predates these checksum settings.
    client = boto3.client('s3', endpoint_url=settings['ENDPOINT'],
                          aws_access_key_id=settings['ACCESS_KEY_ID'],
                          aws_secret_access_key=settings['SECRET_ACCESS_KEY'],
                          region_name=settings['REGION'], config=config)

    # Cloudflare's S3 compatibility header also works with botocore 1.34.
    def if_absent(params, **kwargs):
        params['headers']['If-None-Match'] = '*'

    client.meta.events.register('before-call.s3.PutObject', if_absent, unique_id='edo-check-r2-if-absent')
    return client


def status_code(response):
    return response.get('ResponseMetadata', {}).get('HTTPStatusCode')


def write_cycle(client, bucket):
    run_id = uuid4().hex
    key = f'recordings/verification/edo-check/{run_id}.txt'
    payload = f'edo1 R2 write probe {run_id}\n'.encode('ascii')
    try:
        response = client.put_object(Bucket=bucket, Key=key, Body=payload,
                                     ContentType='text/plain', ContentLength=len(payload))
    except Exception as error:
        # A timeout can happen after R2 commits. The recording prefix expires the object.
        return 'write_put_conflict' if status_code(getattr(error, 'response', {})) == 412 else 'write_put'
    if status_code(response) != 200:
        return 'write_put_conflict' if status_code(response) == 412 else 'write_put'

    try:
        response = client.get_object(Bucket=bucket, Key=key)
        with closing(response['Body']) as body:
            verified = (status_code(response) == 200 and response.get('ContentLength') == len(payload)
                        and body.read(len(payload) + 1) == payload)
    except Exception:
        verified = False
    try:
        deleted = status_code(client.delete_object(Bucket=bucket, Key=key)) == 204
    except Exception:
        deleted = False
    if not deleted:
        return 'write_delete'
    return WRITE_OK if verified else 'write_read'


def signed_download_matches(client, target, host, payload):
    try:
        signed = client.generate_presigned_url('get_object', Params=target, ExpiresIn=90)
        path = f"/{target['Bucket']}/{target['Key']}"
        url = validated_location(signed, path, expected_host=host)
        with closing(http.client.HTTPSConnection(host, timeout=READ_TIMEOUT)) as connection:
            connection.request('GET', f'{url.path}?{url.query}')
            response = connection.getresponse()
            return response.status == 200 and response.read(len(payload) + 1) == payload
    except Exception:
        return False


def object_removed(client, target):
    try:
        if status_code(client.delete_object(**target)) != 204:
            return False
    except Exception:
        return False
    try:
        client.head_object(**target)
    except Exception as error:
        return status_code(getattr(error, 'response', None) or {}) == 404
    return False


def storage_cycle(client, bucket, host, kind):
    run_id = uuid4().hex
    prefix = {'sheet': 'sheet-audio', 'recording': 'recordings'}[kind]
    target = {'Bucket': bucket, 'Key': f'{prefix}/verification/edo-check/{run_id}.txt'}
    payload = f'edo1 {kind} storage probe {run_id}\n'.encode('ascii')
    try:
        response = client.put_object(**target, Body=payload, ContentType='text/plain', ContentLength=len(payload))
        code = status_code(response)
    except Exception as error:
        code = status_code(getattr(error, 'response', None) or {})
    if code in (409, 412):
        return kind + '_conflict'

    # A failed PUT response can still leave an object. Check cleanup for this run's unique key.
    detail = kind + '_put'
    if code == 200:
        detail = STORAGE_OK if signed_download_matches(client, target, host, payload) else kind + '_download'
    if not object_removed(client, target):
        return kind + '_delete' if detail == STORAGE_OK else detail + '_delete'
    return detail


def check_storage(kind):
    started = time.monotonic_ns()
    try:
        prefix, bucket = {'sheet': ('SHEET_STORAGE_', SHEET_BUCKET),
                          'recording': (SETTINGS_PREFIX, BUCKET)}[kind]
        settings = active_storage_settings(prefix, bucket)
        host = urlsplit(settings['ENDPOINT']).netloc
        with closing(write_client(settings)) as client:
            detail = storage_cycle(client, settings['BUCKET'], host, kind)
    except ImportError:
        detail = kind + '_dependency'
    except (OSError, ValueError, TypeError, AttributeError, subprocess.SubprocessError):
        detail = kind + '_configuration'
    except Exception:
        detail = kind + '_unavailable'
    return {'status': 'ok' if detail == STORAGE_OK else 'failed', 'detail': detail,
            'elapsed_ms': (time.monotonic_ns() - started) // 1_000_000}


def last_success_at(path):
    try:
        with path.open(encoding='utf-8') as source:
            value = json.loads(source.read(4096)).get('last_success_at')
        if isinstance(value, str) and datetime.fromisoformat(value).utcoffset() is not None:
            return value
    except (OSError, ValueError, AttributeError):
        pass
    return None


def save_write_result(path, attempted_at, detail):
    succeeded = detail == WRITE_OK
    result = {
        'last_attempt_at': attempted_at.isoformat(),
        'status': 'ok' if succeeded else 'failed',
        'detail': detail,
        'last_success_at': datetime.now(timezone.utc).isoformat() if succeeded else last_success_at(path),
    }
    with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', dir=path.parent, delete=False) as output:
        pending = Path(output.name)
        try:
            json.dump(result, output)
            output.flush()
            os.fsync(output.fileno())
            pending.replace(path)
        finally:
            pending.unlink(missing_ok=True)
    return result


def check_write():
    try:
        settings = recording_settings()
    except (OSError, ValueError):
        return 'write_configuration'
    try:
        with closing(write_client(settings)) as client:
            return write_cycle(client, settings['BUCKET'])
    except ImportError:
        return 'write_dependency'
    except Exception:
        return 'write_configuration'


def main():
    if sys.argv[1:] == ['read']:
        print(json.dumps(read_probe()))
        return 0
    if sys.argv[1:] in (['sheet'], ['recording']):
        print(json.dumps(check_storage(sys.argv[1])))
        return 0
    if sys.argv[1:] == ['write']:
        attempted_at = datetime.now(timezone.utc)
        detail = check_write()
        try:
            result = save_write_result(WRITE_RESULT, attempted_at, detail)
        except OSError:
            print('R2 write probe result could not be saved', file=sys.stderr)
            return 1
        print(json.dumps(result))
        return 0
    print('usage: r2_probe.py {read,write,sheet,recording}', file=sys.stderr)
    return 2


if __name__ == '__main__':
    sys.exit(main())
