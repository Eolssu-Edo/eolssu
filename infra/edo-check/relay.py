#!/usr/bin/python3
"""Bounded Mattermost command receiver. Secrets and callback URLs are never logged."""
import concurrent.futures
import hashlib
import hmac
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import logging
from pathlib import Path
import re
import socket
import ssl
import subprocess
import sys
import threading
import time
from urllib.parse import parse_qs, urlsplit
import uuid

MODES = {'/edo1-server': 'server', '/edo1-containers': 'containers', '/edo1-health': 'health'}
ENDPOINT = '/integrations/mattermost/commands'


def authorize(form, config):
    command = form.get('command', '')
    expected = config['tokens'].get(command, '')
    if command not in MODES or not expected or not hmac.compare_digest(form.get('token', '').encode(), expected.encode()):
        raise ValueError('인증 실패')
    if config.get('team_id') and form.get('team_id') != config['team_id']:
        raise ValueError('허용되지 않은 팀')
    if form.get('channel_id') not in config['channel_ids']:
        raise ValueError('Team 이도 (CI/CD) 채널에서만 실행할 수 있습니다.')
    if form.get('text', '').strip() or not form.get('user_id'):
        raise ValueError('명령 인자를 지원하지 않습니다.')
    callback = form.get('response_url', '')
    url = urlsplit(callback)
    origin = urlsplit(config['mattermost_origin'])
    if (len(callback) > 512 or url.scheme != 'https' or url.hostname != origin.hostname
            or url.port not in (None, 443) or url.username or url.password
            or url.query or url.fragment
            or not re.fullmatch(r'/hooks/commands/[a-z0-9]{26}', url.path)):
        raise ValueError('잘못된 응답 주소')
    return dict(form, mode=MODES[command])


def cell(value):
    text = re.sub(r'[\x00-\x1f\x7f]', ' ', str(value))
    return text.replace('@', '@\u200b').replace('|', '\\|').replace('<', '&lt;').replace('>', '&gt;')[:1600]


def build_report(result, request_id):
    columns, rows = result['columns'], result['rows']
    if not 1 <= len(columns) <= 20 or any(len(row) != len(columns) for row in rows):
        raise ValueError('잘못된 점검 결과')
    issues = list(result.get('issues', []))
    if result['mode'] == 'health':
        issues.extend(f'{row[0]}: {row[2]} ({row[3]})' for row in rows if len(row) >= 4 and row[2] != '정상')
    issues = list(dict.fromkeys(issues))
    tables = []
    bold = result['mode'] in ('server', 'containers', 'health')
    if result['mode'] == 'server' and len(columns) == 10 and len(rows) == 1:
        rows = [[label, value] for label, value in zip(columns, rows[0])]
        columns = ['항목', '측정값']
    elif result['mode'] == 'containers' and len(columns) == 11:
        names = [re.sub(r'^eolssu-(runtime|monitoring)-', '', str(row[0])) for row in rows]
        tables.append(('자원·상태',
                       ['컨테이너', '실행 / Health', 'CPU 사용 / 제한', 'RAM 사용 / 제한', '쓰기 / 제한 · 로그', '가동 / 재시작'],
                       [[name] + [row[i] for i in (1, 2, 3, 4, 6)] for name, row in zip(names, rows)]))
        rows = [[name] + [row[i] for i in (5, 7, 8, 9, 10)] for name, row in zip(names, rows)]
        columns = [columns[i] for i in (0, 5, 7, 8, 9, 10)]
    tables.append(('저장소·실행 상세' if tables else '점검 결과', columns, rows))
    if issues:
        tables.append(('확인 사항', ['확인 사항'], [[item] for item in issues[:8]]))

    # Mattermost interprets colon-delimited time fragments as emoji outside code.
    captured_at = cell(result.get('captured_at', '확인 불가')).replace('`', '')
    summary = f'**판정: 확인 사항 {len(issues)}건**' if issues else '**판정: 수집한 항목에 이상 없음**'
    parts = [f"### edo1 {cell(result['mode'])} 점검",
             f"수집 시각: `{captured_at}` · 요청 `{request_id}`", '', summary]
    render = lambda value: '**' + cell(value) + '**' if bold else cell(value)
    for title, table_columns, table_rows in tables:
        header = ['', '**' + title + '**', '',
                  '| ' + ' | '.join(map(render, table_columns)) + ' |',
                  '| ' + ' | '.join('---' for _ in table_columns) + ' |']
        if len('\n'.join(parts + header)) > 14000:
            parts.append('\n출력 제한: 나머지 표 생략. 운영자 확인이 필요합니다.')
            break
        parts.extend(header)
        for row in table_rows[:100]:
            rendered = '| ' + ' | '.join(render(value) if bold and column < 4 else cell(value)
                                        for column, value in enumerate(row)) + ' |'
            if len('\n'.join(parts)) + len(rendered) + 1 > 14000:
                parts.append('\n출력 제한: 나머지 행·표 생략. 운영자 확인이 필요합니다.')
                return '\n'.join(parts)
            parts.append(rendered)
        if len(table_rows) > 100:
            parts.append(f'\n출력 제한: {len(table_rows) - 100}개 행 생략.')
    if len(issues) > 8:
        parts.append(f'\n확인 사항 {len(issues) - 8}건 생략. 운영자 확인이 필요합니다.')
    return '\n'.join(parts)


class PublicHTTPS(http.client.HTTPSConnection):
    """Pin the verified public address while retaining hostname/TLS verification."""
    def connect(self):
        addresses = socket.getaddrinfo(self.host, self.port, type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
            raise ValueError('non-public callback address')
        address = next((x for x in addresses if x[0] == socket.AF_INET), addresses[0])
        raw = socket.create_connection(address[4][:2], self.timeout)
        self.sock = self._context.wrap_socket(raw, server_hostname=self.host)


def post_callback(url, text):
    parsed = urlsplit(url)
    connection = PublicHTTPS(parsed.hostname, 443, timeout=15, context=ssl.create_default_context())
    try:
        body = json.dumps({'response_type': 'in_channel', 'text': text}, ensure_ascii=False).encode()
        connection.request('POST', parsed.path, body, {'Content-Type': 'application/json'})
        response = connection.getresponse()
        response.read(4096)
        if not 200 <= response.status < 300:
            raise RuntimeError('callback rejected')
    finally:
        connection.close()


class App:
    def __init__(self, config):
        self.config = config
        self.lock = threading.Lock()
        self.seen = {}
        self.last_run = {}
        self.slots = threading.BoundedSemaphore(3)
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=3)

    def submit(self, form):
        request = authorize(form, self.config)
        key = hashlib.sha256(request['response_url'].encode()).hexdigest()
        throttle = (request['channel_id'], request['mode'])
        now = time.monotonic()
        with self.lock:
            self.seen = {k: v for k, v in self.seen.items() if now - v < 1800}
            if key in self.seen:
                return '이미 접수한 점검 요청입니다.'
            if now - self.last_run.get(throttle, -1000) < 10:
                return '같은 점검은 10초 후 다시 요청하세요.'
            if len(self.seen) >= 512 or not self.slots.acquire(blocking=False):
                return '점검 실행 중입니다. 잠시 후 다시 요청하세요.'
            self.seen[key] = now
            self.last_run[throttle] = now
        request_id = uuid.uuid4().hex[:10]
        self.pool.submit(self.run, request, request_id)
        return f"edo1 {request['mode']} 점검을 시작했습니다. 완료 후 이 채널에 공개합니다. 요청 {request_id}"

    def run(self, request, request_id):
        try:
            ssh = ['ssh', '-4', '-T', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
                   '-o', 'ConnectTimeout=8', '-o', 'IdentitiesOnly=yes',
                   '-o', 'UserKnownHostsFile=' + self.config['known_hosts'],
                   '-i', self.config['identity_file'], self.config['ssh_target'], request['mode']]
            completed = subprocess.run(ssh, capture_output=True, timeout=90, check=True)
            if len(completed.stdout) > 250000:
                raise ValueError('oversize result')
            result = json.loads(completed.stdout)
            if result.get('mode') != request['mode']:
                raise ValueError('unexpected mode')
            if request['mode'] == 'health':
                result['rows'].extend(external_health(self.config['service_host']))
            report = build_report(result, request_id)
        except subprocess.TimeoutExpired:
            report = f'edo1 점검 시간 초과 · 요청 {request_id}. 수집 결과를 확인할 수 없습니다.'
        except Exception:
            logging.error('collection_failed request=%s mode=%s', request_id, request['mode'])
            report = f'edo1 점검 실패 · 요청 {request_id}. SSH 연결 또는 수집 상태를 운영자가 확인해야 합니다.'
        try:
            post_callback(request['response_url'], report)
            logging.info('delivered request=%s mode=%s', request_id, request['mode'])
        except Exception:
            logging.error('delivery_failed request=%s mode=%s', request_id, request['mode'])
        finally:
            self.slots.release()


def external_health(host):
    rows = []
    for label, path in [('웹 외부 접속', '/'), ('API health', '/api/actuator/health')]:
        started = time.monotonic()
        connection = http.client.HTTPSConnection(host, timeout=8, context=ssl.create_default_context())
        try:
            connection.request('GET', path)
            response = connection.getresponse()
            response.read(4096)
            status = '정상' if response.status == 200 else ('미설정' if response.status == 404 and label == 'API health' else '실패')
            rows.append([label, 'edo2 외부 HTTPS', status, f'HTTP {response.status}',
                         f'{(time.monotonic()-started)*1000:.0f}ms', path])
        except Exception:
            rows.append([label, 'edo2 외부 HTTPS', '실패', '연결·TLS·응답 확인 불가', '—', path])
        finally:
            connection.close()
    try:
        with socket.create_connection((host, 443), timeout=8) as raw:
            with ssl.create_default_context().wrap_socket(raw, server_hostname=host) as secured:
                expires = ssl.cert_time_to_seconds(secured.getpeercert()['notAfter'])
        days = (expires - time.time()) / 86400
        rows.append(['서비스 인증서', 'edo2 외부 TLS', '정상' if days >= 7 else '주의',
                     f'만료까지 {days:.1f}일', '—', host])
    except Exception:
        rows.append(['서비스 인증서', 'edo2 외부 TLS', '실패', '인증서 검증 실패', '—', host])
    return rows


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, *args):
        pass

    def respond(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.respond(200 if self.path == '/healthz' else 404,
                     {'status': 'ok' if self.path == '/healthz' else 'not found'})

    def do_POST(self):
        if self.path != ENDPOINT:
            self.respond(404, {'error': 'not found'})
            return
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 1 <= length <= 8192 or self.headers.get_content_type() != 'application/x-www-form-urlencoded':
                raise ValueError('잘못된 요청')
            fields = parse_qs(self.rfile.read(length).decode('utf-8'), keep_blank_values=True, max_num_fields=30)
            if any(len(values) != 1 for values in fields.values()):
                raise ValueError('중복된 요청 항목')
            message = self.server.app.submit({key: value[0] for key, value in fields.items()})
            self.respond(200, {'response_type': 'ephemeral', 'text': message})
        except (ValueError, UnicodeError):
            self.respond(200, {'response_type': 'ephemeral', 'text': '요청이 허용되지 않았습니다. CI/CD 채널·명령·인증 설정을 확인하세요.'})


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
    config = json.loads(Path(sys.argv[1]).read_text())
    if set(config['tokens']) != set(MODES) or any(len(v) < 20 for v in config['tokens'].values()):
        raise SystemExit('invalid token configuration')
    server = ThreadingHTTPServer(('127.0.0.1', 8765), Handler)
    server.daemon_threads = True
    server.app = App(config)
    server.serve_forever()
