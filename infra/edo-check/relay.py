#!/usr/bin/python3
"""Bounded Mattermost command receiver. Secrets and callback URLs are never logged."""
import concurrent.futures
from contextlib import closing
import hashlib
import hmac
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import logging
from pathlib import Path
from datetime import datetime, timedelta, timezone
import re
import socket
import sqlite3
import ssl
import subprocess
import sys
import threading
import time
from urllib.parse import parse_qs, urlsplit
import uuid

TARGETS = ('edo1', 'edo2')
COLLECTION_MODES = ('health', 'server', 'containers')
MODES = {f'/{target}-{mode}': mode for target in TARGETS for mode in COLLECTION_MODES}
MODES.update({f'/{target}': 'overview' for target in TARGETS})
COMMAND_TARGETS = {command: command[1:].split('-', 1)[0] for command in MODES}
ENDPOINT = '/integrations/mattermost/commands'
APPLOG = '/applog'
APPLOG_DATABASE = Path('/var/log/eolssu-qa-perf/events.sqlite3')
APPLOG_RESPONSE_LIMIT = 128 * 1024
APPLOG_TIMEOUT = 600
APPLOG_RETENTION = 7 * 24 * 60 * 60


def authorize(form, config):
    command = form.get('command', '')
    settings = config.get('applog') if command == APPLOG else config
    if settings is None:
        raise ValueError('인증 실패')
    expected = settings.get('token', '') if command == APPLOG else config['tokens'].get(command, '')
    if ((command != APPLOG and command not in MODES) or not expected
            or not hmac.compare_digest(form.get('token', '').encode(), expected.encode())):
        raise ValueError('인증 실패')
    if settings.get('team_id') and form.get('team_id') != settings['team_id']:
        raise ValueError('허용되지 않은 팀')
    if form.get('channel_id') not in settings['channel_ids']:
        raise ValueError('Team 이도 (CI/CD) 채널에서만 실행할 수 있습니다.')
    if command == APPLOG and form.get('user_id') not in settings['user_ids']:
        raise ValueError('허용되지 않은 실행자')
    if form.get('text', '').strip() or not form.get('user_id') or 'target' in form or 'mode' in form:
        raise ValueError('명령 인자를 지원하지 않습니다.')
    callback = form.get('response_url', '')
    url = urlsplit(callback)
    origin = urlsplit(config['mattermost_origin'])
    if (len(callback) > 512 or url.scheme != 'https' or url.hostname != origin.hostname
            or url.port not in (None, 443) or url.username or url.password
            or url.query or url.fragment
            or not re.fullmatch(r'/hooks/commands/[a-z0-9]{26}', url.path)):
        raise ValueError('잘못된 응답 주소')
    if command == APPLOG:
        return dict(form, target='edo2', mode='applog')
    return dict(form, target=COMMAND_TARGETS[command], mode=MODES[command])


def target_settings(config, target):
    if config.get('targets') is not None:
        if target not in config['targets']:
            raise ValueError('missing target configuration')
        return config['targets'][target]
    if target == 'edo1':
        return config  # Existing three-command deployment remains compatible during rollout.
    raise ValueError('missing target configuration')


def validate_config(config):
    commands = set(config['tokens'])
    if not commands or not commands.issubset(MODES):
        raise ValueError('invalid command configuration')
    tokens = list(config['tokens'].values())
    if any(not isinstance(value, str) or len(value) < 20 for value in tokens) or len(set(tokens)) != len(tokens):
        raise ValueError('invalid token configuration')
    if 'applog' in config:
        settings = config['applog']
        keys = {'token', 'tunnel_token', 'team_id', 'channel_ids', 'user_ids'}
        if not isinstance(settings, dict) or set(settings) != keys:
            raise ValueError('invalid applog configuration')
        for key in ('token', 'tunnel_token'):
            value = settings[key]
            pattern = r'[A-Za-z0-9_-]{32,128}' if key == 'tunnel_token' else r'[!-~]{20,256}'
            if (not isinstance(value, str) or not re.fullmatch(pattern, value)
                    or value in tokens):
                raise ValueError('invalid applog token configuration')
            tokens.append(value)
        if not isinstance(settings['team_id'], str) or not settings['team_id']:
            raise ValueError('invalid applog team')
        for key in ('channel_ids', 'user_ids'):
            values = settings[key]
            if not isinstance(values, list) or not values or not all(isinstance(value, str) and value for value in values):
                raise ValueError('invalid applog access list')
    for target in TARGETS:
        overview = f'/{target}'
        legacy = {f'/{target}-{mode}' for mode in COLLECTION_MODES}
        if overview not in commands and commands & legacy and not legacy.issubset(commands):
            raise ValueError('incomplete target command group')
        if overview in commands or commands & legacy:
            settings = target_settings(config, target)
            if any(not settings.get(key) for key in ('ssh_target', 'known_hosts', 'identity_file', 'service_host')):
                raise ValueError('incomplete target configuration')


def cell(value):
    text = re.sub(r'[\x00-\x1f\x7f]', ' ', str(value))
    return text.replace('@', '@\u200b').replace('|', '\\|').replace('<', '&lt;').replace('>', '&gt;')[:1600]


STATUS_ICONS = {
    '정상': '✅', '검증 완료': '✅', '장애': '❌', '주의': '⚠️', '미검증': '❓', '미측정': '🚫', '설정 참고': 'ℹ️',
    '통신 정상': '✅', '로컬 수신 정상': '✅', '복제 통신 정상': '✅',
    '실행 · HTTPS 검사 통과': '✅', '실행 · HTTPS 미측정': '🚫',
    '실행 · 검사 통과': '✅', '실행 · 준비 미검증': '❓',
    '실행 · 검증 완료': '✅', '실행 · 준비 확인 불가': '🚫', '실행 · 준비 상태 주의': '⚠️',
    '실행 · 통신 정상': '✅', '실행 · 로컬 수신 확인': '✅',
    '해당 없음': '➖', '컨테이너 없음': '➖',
}

CONTAINER_READINESS_LABELS = {
    '정상': '실행 · 검증 완료', '장애': '장애', '주의': '실행 · 준비 상태 주의',
    '미측정': '실행 · 준비 확인 불가', '미검증': '실행 · 준비 미검증',
}


def display_status(value):
    """Decorate final labels only; health decisions and counts keep raw values."""
    if isinstance(value, tuple):
        label, evidence = value
        return f'{display_status(label)} · {cell(evidence)}'
    icon = STATUS_ICONS.get(str(value))
    if not icon:
        return cell(value)
    # Keep Korean status words intact in narrow Mattermost table columns.
    label = re.sub(r'[가-힣]{2,}', lambda match: '\u2060'.join(match.group()), cell(value))
    return f'{icon}\u00a0**{label}**'


NAMES = {
    'eolssu-runtime-frontend-1': '웹', 'eolssu-runtime-backend-1': 'API 서버(블루)',
    'eolssu-runtime-backend-green-1': 'API 서버(그린)',
    'eolssu-runtime-mysql-1': '서비스 DB(MySQL)',
    'eolssu-runtime-ai-1': '연주 분석 AI', 'eolssu-runtime-omr-1': '악보 인식(OMR)',
    'eolssu-runtime-playback-1': '악보 음원 재생',
    'eolssu-monitoring-grafana-1': '모니터링(Grafana)',
    'eolssu-monitoring-prometheus-1': '수치 수집(Prometheus)',
    'eolssu-monitoring-node-exporter-1': '서버 지표(node-exporter)',
    'eolssu-jenkins-controller-1': '배포(Jenkins)',
    'eolssu-replication-proxy-579': 'MySQL 복제 프록시',
    'eolssu-replica-579-mysql-1': 'MySQL 복제본',
    'eolssu-standby-579-backend-1': '대기 API',
    'eolssu-standby-579-frontend-1': '대기 웹',
    'eolssu-standby-579-mysql-1': '대기 MySQL',
    'eolssu-standby-579-playback-1': '대기 악보 음원 재생',
    'eolssu-standby-579-ai-1': '대기 연주 분석 AI',
    'eolssu-standby-579-omr-1': '대기 악보 인식(OMR)',
    'hey-edo1-controller-1': '리뷰 봇 제어', 'hey-edo1-runner-1': '리뷰 실행기',
    'hey-edo1-n8n-1': '리뷰 워크플로(n8n)', 'hey-edo1-tunnel-1': '리뷰 봇 터널',
    'hey-edo1-postgres-1': '리뷰 봇 DB(Postgres)',
}


def friendly(value):
    text = str(value)
    for original, name in NAMES.items():
        text = text.replace(original, name)
    return text


def amount(value):
    if value is None:
        return '미측정'
    for unit in ('B', 'KiB', 'MiB', 'GiB', 'TiB'):
        if value < 1024 or unit == 'TiB':
            return f'{value:.1f}{unit}'
        value /= 1024


def host_instructions(value, target):
    return (value.replace('/edo1-', f'/{target}-').replace('edo1에서', target + '에서')
            .replace('edo1의', target + '의').replace('edo2→edo1', 'edo2→' + target))


def compact_image_reference(value):
    """Keep deployment pins in collected data, not in the narrow image column."""
    text = str(value)
    pinned = re.fullmatch(r'(.+)@sha256:([0-9a-fA-F]{64})', text)
    if pinned:
        name, digest = pinned.groups()
        return name if ':' in name.rsplit('/', 1)[-1] else name + '@sha256:' + digest[:12] + '…'
    return re.sub(r'^sha256:([0-9a-fA-F]{12})[0-9a-fA-F]{52}$', r'sha256:\1…', text)


MODE_LABELS = {'health': '상태', 'server': '서버', 'containers': '컨테이너'}
HEALTH_LABELS = {'실패': '장애', '미설정': '미검증', '확인 불가': '미측정'}
HEALTH_SUCCESS_LABELS = {
    'edo1': {
        'edo1 서비스 준비 검사': '검증 완료',
        'edo1 서비스 HTTP 통신 검사': '통신 정상',
        'edo1 MySQL 프록시 로컬 수신 검사': '로컬 수신 정상',
    },
    'edo2': {'edo2 복제 감시 systemd': '복제 통신 정상'},
}
BACKEND_SLOTS = {'backend': '블루', 'backend-green': '그린'}
FINDING_ORDER = ('장애', '주의', '미측정', '미검증')
COUNT_KINDS = ('장애', '주의', '미검증', '미측정')
REPORT_LIMIT = 14000
OVERVIEW_POST_LIMIT = 5
OMISSION_NOTICE = '출력 제한으로 나머지 행과 표를 생략했습니다.'


def ram_usage(values, facts):
    total, available = facts.get('ram_total'), facts.get('ram_available')
    if total and available is not None:
        return f'{amount(total-available)} / {amount(total)} ({(1-available/total)*100:.0f}%) · 가용 {amount(available)}'
    return values[2]


def report_summary(mode, target, columns, rows, facts):
    if mode == 'health' and target == 'edo1' and len(columns) >= 4:
        slot = (facts or {}).get('backend_slot')
        if slot not in BACKEND_SLOTS:
            return 'API 라우팅 미측정'
        status = next((row[2] for row in rows if row[0] == f'eolssu-runtime-{slot}-1'), '미측정')
        return f'API 라우팅 {BACKEND_SLOTS[slot]} · 컨테이너 {HEALTH_LABELS.get(status, status)}'
    if mode == 'containers' and len(columns) == 11 and rows:
        running = sum(str(row[1]).startswith('running/') for row in rows)
        summary = [f'수집 {len(rows)}개 · 실행 {running}개']
        for key, label, format_value in (('cpu_percent', 'CPU', lambda value: f'{value:.1f}%'),
                                         ('ram_bytes', 'RAM', amount)):
            highest = max((item for item in facts or [] if item.get(key) is not None),
                          key=lambda item: item[key], default=None)
            if highest is not None:
                summary.append(f'{label} 최대 {friendly(highest["name"])} {format_value(highest[key])}')
        return ' · '.join(summary)
    return ''


def health_group(name, location):
    if any(word in name for word in ('백업', '복구', '복제', 'replica', 'replication')):
        return '백업·복제'
    if location.endswith(('systemd', '구성 검사', '모니터링 HTTP')) or name in ('Docker 조회', '저장공간 자동 측정'):
        return '호스트·모니터링'
    return '서비스·통신'


def report_content(result, *, overview=False):
    target = result.get('target', 'edo1')
    if target not in TARGETS:
        raise ValueError('unexpected report target')
    columns, rows = result['columns'], result['rows']
    mode = result['mode']
    if not 1 <= len(columns) <= 20 or any(len(row) != len(columns) for row in rows):
        raise ValueError('잘못된 점검 결과')
    findings, tables = [], []
    # Settings without limits are capacity policy, not evidence of an outage.
    for issue in dict.fromkeys(result.get('issues', [])):
        if result['mode'] == 'health' and any(str(row[0]) in issue for row in rows):
            continue  # The per-service probe below owns this finding.
        if result['mode'] == 'containers' and any(str(row[0]) in issue for row in rows):
            if any(word in issue for word in ('상세 수집 실패', '통계 미측정', '로그 미측정', '데이터 용량 미측정')) or ': ' in issue:
                continue  # The container row owns missing measurements and state.
        if '메모리 제한 미설정' in issue:
            kind, action = '설정 참고', '/edo1-containers의 RAM과 호스트 가용 RAM을 확인한 후 최대 사용량을 근거로 한도 설정'
        elif any(word in issue for word in ('미측정', '확인 불가', '수집 실패', '수집 불가', '시간 초과')):
            kind, action = '미측정', '같은 명령을 1회 재실행; 반복되면 edo2의 edo-check journal에서 해당 요청 ID 확인'
        elif '(실패)' in issue:
            kind, action = '주의', '/edo1-containers에서 해당 파일시스템의 로그·쓰기·데이터 용량 확인; 공간 확보·증설 우선'
        elif '주의' in issue or '80%' in issue:
            kind, action = '주의', '/edo1-containers에서 RAM 큰 컨테이너와 한도를 확인; 30초 간격 3회 재점검해 증가 여부 비교'
        else:
            kind, action = '장애', '대상 실행 상태·최근 로그 확인'
        if '공간 ' in issue and 'inode ' in issue:
            space = re.search(r'공간\s+(\d+)%', issue)
            inode = re.search(r'inode\s*(\d+)%', issue)
            used = int(space[1]) if space else 0
            files = int(inode[1]) if inode else 0
            checks = []
            if used >= 80:
                checks.append('공간: 해당 mount의 큰 로그·데이터 확인 후 공간 확보·증설')
            if files >= 80:
                checks.append('inode: 파일 개수 많은 디렉터리·작은 회전 파일 확인 후 보존 정책에 따라 정리')
            action = ('90% 이상으로 긴급 확인. ' if max(used, files) >= 90 else '') + '; '.join(checks)
        elif '실패 서비스' in issue:
            action = 'edo1에서 systemctl --failed로 서비스명 확인 후 해당 서비스 journal의 실패 원인 확인'
        elif 'OOM' in issue:
            action = 'edo1의 최근 24시간 커널 OOM 로그에서 시각·대상 확인 후 /edo1-containers의 RAM·한도와 비교'
        findings.append([kind, friendly(issue), action])

    inherited_findings = {tuple(item) for item in findings}
    if result['mode'] == 'health':
        titles = ('서비스·통신', '호스트·모니터링', '백업·복제') if overview else ('서비스 검사 결과',)
        groups = {title: [] for title in titles}
        success_labels = HEALTH_SUCCESS_LABELS[target]
        # Prefer a successful direct readiness probe over an absent Docker check.
        ready = {row[0] for row in rows if row[1] == target + ' loopback HTTP' and row[2] == '정상'}
        for row in rows:
            name, location, status, evidence = row[:4]
            if name == 'eolssu-monitoring-grafana-1' and 'Grafana' in ready and status in ('미검증', '미설정'):
                continue
            if name == 'hey-edo1-n8n-1' and 'n8n' in ready and status in ('미검증', '미설정'):
                continue
            if name == 'eolssu-monitoring-node-exporter-1' and status in ('미검증', '미설정') and any(r[0] == 'node 지표 수집' and r[2] == '정상' for r in rows):
                continue
            status = HEALTH_LABELS.get(status, status)
            if status == '정상':
                action = '현재 조치 없음'
            elif status == '미검증':
                action = ('실제 백엔드 health URL·Nginx 프록시 경로를 대조; HTTP 200과 status=UP을 반환하는 경로 확정 후 재점검' if name == 'API health'
                          else '프로세스 실행만 확인; /edo1-health의 별도 HTTP 준비 검사를 확인하고 없으면 대상 healthcheck 구성 필요')
            elif status == '미측정':
                action = '/edo1-health를 1회 재실행; 반복되면 edo2→edo1 SSH 연결·Docker 조회 권한 확인'
            else:
                action = (('만료 7일 미만이면 서비스 인증서 갱신 작업·실패 로그 확인 후 /edo1-health 재실행' if status == '주의' else
                           'edo2에서 TLS 재연결; 서비스 호스트명·인증서 체인·현재 인증서 유효기간 확인 후 /edo1-health 재실행') if 'TLS' in location else
                          'edo2에서 공개 HTTPS 재요청; /edo1-containers의 웹·API 실행 상태와 Nginx upstream 확인' if '외부 HTTPS' in location else
                          'edo1에서 해당 loopback 준비 경로 재요청; 서비스 로그의 DB 연결 오류 확인' if 'loopback' in location else
                          'edo1에서 대상 docker inspect의 State·Health와 docker logs --tail 50 확인')
            if (location in success_labels or name in ('eolssu-runtime-ai-1', 'eolssu-replication-proxy-579')
                    or location in ('edo1 systemd', 'edo2 systemd', 'edo2 구성 검사', 'edo2 백업 메타데이터',
                                    'edo1 모니터링 HTTP', 'edo1 운영 검사', 'edo2 대기 웹 loopback HTTPS')):
                action = str(row[5]) if len(row) > 5 else 'edo2의 해당 unit·설정 확인'
            elif target == 'edo2' and 'TLS' in location:
                action = ('만료 24시간 미만: snap.certbot.renew.timer와 최근 갱신 로그 확인; 인증서 갱신 뒤 /edo2-health 재실행'
                          if status == '주의' else 'edo2에서 자체 HTTPS TLS 연결·IP SAN·인증서 체인·유효기간 확인')
            elif target == 'edo2' and location == 'edo2 자체 HTTPS':
                action = 'nginx -t와 /healthz proxy_pass, edo-check.service의 내부 HTTP 응답 확인'
            elif target == 'edo2' and 'loopback' in location:
                action = 'edo-check.service와 127.0.0.1:8765/healthz의 JSON status=ok 응답 확인'
            if location != 'edo2 백업 메타데이터':
                action = host_instructions(action, target)
            elapsed = row[4] if len(row) > 4 else '—'
            checked = ('Docker healthcheck · ' if location == 'Docker healthcheck' else 'Docker · ' if 'Docker' in location else '') + str(evidence)
            if location in ('edo1 systemd', 'edo2 systemd'):
                # Mattermost can turn :00: inside systemd timestamps into emoji.
                checked = re.sub(r'\b\d{2}:\d{2}:\d{2}\b', r'`\g<0>`', checked)
            if elapsed != '—':
                checked += ' · ' + str(elapsed)
            verdict = success_labels.get(location, status) if status == '정상' else status
            title = health_group(name, location) if overview else '서비스 검사 결과'
            groups[title].append([friendly(name), verdict, checked, '—' if status == '정상' else action])
            if status != '정상':
                findings.append([status, friendly(name) + ': ' + str(evidence), action])
        tables.extend((title, ['대상', '판정', '검사 근거 · 소요', '필요한 조치'], group)
                      for title, group in groups.items() if group or not overview)

    elif result['mode'] == 'server' and len(columns) == 10 and len(rows) == 1:
        values, facts = rows[0], result.get('facts') or {}
        criteria = ['예상 밖 재부팅 시 last reboot 확인',
                    '0.2초 표본 · 100%=서버 전체 CPU · Load와 비교',
                    '80% 이상이면 /edo1-containers RAM 상위 확인 · 30초 간격 3회 비교',
                    'RAM 80% 이상 또는 OOM 발생 시 RAM·한도 확인',
                    '공간 80% 이상이면 큰 로그·데이터 확인 · inode 80% 이상이면 파일 많은 경로 확인 · 90% 이상 긴급',
                    '엔진 실패·필수 컨테이너 누락 시 /edo1-health 확인',
                    '사용량 합계 / 호스트 RAM · 호스트 80% 이상이면 서비스별 확인',
                    '분류별 점유량 · 공유 계층 중복 주의',
                    '공유 계층 중복 주의 · 디스크 80% 이상이면 미사용 이미지·캐시 확인',
                    '실패 서비스는 systemctl --failed · OOM은 최근 24시간 로그 줄 수']
        if target == 'edo2':
            criteria[5] = '엔진 조회 실패 시 docker.service·소켓 권한 확인'
        entries, storage_entries = [], []
        for index, (label, value) in enumerate(zip(columns, values)):
            if index == 1 and facts.get('load'):
                value = str(value).split(' · Load ')[0]
                entries.append(['부하(Load 1 / 5 / 15분)', ' / '.join(f'{x:.2f}' for x in facts['load']),
                                f"실행·대기 작업 평균 · 5분·15분 중 {facts['cpu_cores']} 초과 시 /edo1-containers CPU 상위 확인 · CPU 사용률이 낮으면 I/O 대기 확인"])
            if index == 2:
                value = ram_usage(values, facts)
            group = storage_entries if overview and index in (4, 7, 8) else entries
            if index == 4:
                for part in str(value).split('; '):
                    group.append(['파일시스템', part, criteria[index]])
            elif index == 7 and len(str(value).split(' / ')) == 4:
                for title, number in zip(('Docker 이미지', '컨테이너 쓰기 계층', 'Docker 볼륨', '빌드 캐시'), str(value).split(' / ')):
                    action = {'Docker 이미지': '미사용 이미지 확인',
                              '컨테이너 쓰기 계층': '/edo1-containers 쓰기 상위 확인',
                              'Docker 볼륨': '/edo1-containers 저장공간 확인',
                              '빌드 캐시': 'docker builder du 확인'}[title]
                    group.append([title, number, '디스크 80% 이상이면 ' + action])
            else:
                group.append([label, value, criteria[index]])
        groups = [('서버 자원', entries), ('서버 저장공간', storage_entries)] if overview else [('서버 현재 자원', entries)]
        tables.extend((title, ['항목', '현재값', '확인 기준 · 다음 조치'],
                       [[label, value, host_instructions(criterion, target)] for label, value, criterion in group])
                      for title, group in groups)

    elif result['mode'] == 'containers' and len(columns) == 11 and not rows:
        tables.append(('컨테이너 현황', ['대상', '판정', '확인한 내용'],
                       [[target + ' Docker', '해당 없음', '엔진 조회 성공 · 등록된 컨테이너 0개. 이미지·캐시는 /' + target + '-server에서 확인']]))
    elif result['mode'] == 'containers' and len(columns) == 11:
        facts = {item['name']: item for item in result.get('facts') or []}
        resource_rows, storage_rows, runtime_rows = [], [], []
        for row in rows:
            name, state = str(row[0]), str(row[1])
            fact = facts.get(name, {})
            readiness_evidence = None
            if '확인 불가' in state:
                status = '미측정'
            elif target == 'edo1' and state.startswith('exited/') and fact.get('rollback_retained') is True:
                status = '롤백 보관 · 중지'
            elif not state.startswith('running/') or 'unhealthy' in state:
                status = '장애'
            else:
                status = '실행 · 검사 통과' if state.endswith('/healthy') else '실행 · 준비 미검증'
            if status == '미측정':
                findings.append(['미측정', friendly(name) + ' 상세 수집 실패', 'Docker inspect 연결·권한 확인'])
            elif status == '장애':
                findings.append(['장애', friendly(name) + ': ' + state, '컨테이너 실행 상태·최근 로그 확인'])
            elif str(row[7]).endswith('/예'):
                findings.append(['주의', friendly(name) + ' OOM 강제 종료 기록 있음', '현재 실행 상태와 별도로 메모리 한도·커널 OOM 기록 확인'])
            readiness = fact.get('readiness')
            if (target == 'edo1' and state in ('running/미설정', 'running/healthy')
                    and isinstance(readiness, (list, tuple)) and len(readiness) == 3
                    and all(isinstance(value, str) for value in readiness)):
                verdict, evidence, elapsed = readiness
                kind = HEALTH_LABELS.get(verdict, verdict)
                if evidence.strip() and kind in CONTAINER_READINESS_LABELS:
                    status = CONTAINER_READINESS_LABELS[kind]
                    readiness_evidence = evidence if elapsed in ('', '—') else f'{evidence} · {elapsed}'
                    if kind == '정상':
                        scope = fact.get('readiness_kind')
                        if scope == 'http_communication':
                            status = '실행 · 통신 정상'
                        elif scope == 'tcp_listener':
                            status = '실행 · 로컬 수신 확인'
                            replication_hint = '복제 통신은 /edo2-health에서 확인'
                            if replication_hint not in evidence:
                                readiness_evidence += ' · ' + replication_hint
                    else:
                        findings.append([kind, friendly(name) + ': ' + evidence,
                                         '/edo1-health에서 준비 검사 근거·서비스 상태 확인'])
            elif status == '실행 · 준비 미검증' and target == 'edo2' and readiness:
                verdict, evidence, elapsed = readiness
                if overview:
                    readiness_evidence = evidence if elapsed in ('', '—') else f'{evidence} · {elapsed}'
                if verdict == '정상':
                    status = '실행 · HTTPS 검사 통과'
                else:
                    kind = HEALTH_LABELS.get(verdict, verdict)
                    status = '장애' if kind == '장애' else '실행 · HTTPS 미측정'
                    findings.append([kind, friendly(name) + ': ' + evidence, '/edo2-health에서 대기 웹 HTTPS 검사 확인'])
            if status == '실행 · 준비 미검증' and readiness_evidence is None:
                findings.append(['미검증', friendly(name) + ' 준비 상태', '/edo1-health에서 실제 준비 상태 검사 확인; 장애 확정 아님'])
            net, separator, block = str(row[9]).partition(' · IO ')
            measurements = [(row[4], '쓰기/로그'), (row[5], '데이터 용량')]
            measurements.extend([(net, '누적 네트워크'), (block, '누적 블록 I/O')] if separator
                                else [(net, '누적 NET/IO')])
            missing = [label for value, label in measurements if '미측정' in str(value) or '확인 불가' in str(value)]
            if status != '미측정' and missing:
                action = ('Docker 네트워크 통계·cgroup 블록 I/O 확인' if all(label.startswith('누적') for label in missing)
                          else '재점검 후 측정 경로·드라이버 지원 확인')
                findings.append(['미측정', friendly(name) + ' ' + ', '.join(missing), action])
            ram = str(row[3]).replace('미설정', '제한 없음').replace(' · —', '')
            if fact.get('ram_bytes') is not None:
                used, limit, host = fact['ram_bytes'], fact.get('ram_limit'), fact.get('host_ram')
                if limit:
                    ram = f'{amount(used)} / {amount(limit)} ({used/limit*100:.0f}%)'
                elif host:
                    ram = f'{amount(used)} · 제한 없음 (호스트의 {used/host*100:.1f}%)'
            cpu = str(row[2]).replace('/미설정', ' · 제한 없음').replace('/', ' / ')
            write, _, old_log = str(row[4]).partition(' · 로그 ')
            log = fact.get('log', old_log or '미측정')
            resource_rows.append([friendly(name), (status, readiness_evidence) if readiness_evidence else status,
                                  cpu, ram, '쓰기 ' + write.replace('/미설정(호스트 공유)', ' · 호스트 공유') + ' / 로그 ' + str(log)])
            for mount in str(row[5]).split('; '):
                mount_target, separator, usage = mount.rpartition(': ')
                storage_rows.append([friendly(name), mount_target if separator else '데이터 저장소', usage if separator else mount,
                                     '다른 컨테이너와 중복 합산 금지' if '(공유)' in mount else
                                     '같은 명령을 1회 재실행' if '확인 불가' in mount else
                                     '별도 데이터 mount 없음' if mount == '없음' else '볼륨/bind 값은 쓰기 계층·로그와 별개'])
            display_name = friendly(name)
            runtime_name = name if display_name == name else f'{display_name} ({name})'
            runtime_rows.append([runtime_name, row[6], row[7], row[8], row[9], row[10]])
            if status not in ('미측정', '롤백 보관 · 중지') and (fact.get('ram_bytes') is None or fact.get('cpu_percent') is None):
                findings.append(['미측정', friendly(name) + ' CPU 또는 RAM', '다시 점검; 실행 상태와 통계 수집은 별개'])
        if overview:
            tables.extend([
                ('컨테이너 상태', ['서비스', '판정', '검사 근거'],
                 [[name, *status] if isinstance(status, tuple) else [name, status, '—']
                  for name, status, *_ in resource_rows]),
                ('컨테이너 자원', ['서비스', 'CPU / 상한', 'RAM / 상한', '쓰기 · 로그'],
                 [[name, cpu, ram, disk] for name, _, cpu, ram, disk in resource_rows]),
            ])
        else:
            tables.append(('컨테이너 현재 자원', ['서비스', '상태', 'CPU / 상한', 'RAM / 상한', '쓰기 · 로그'], resource_rows))
        tables.append(
            ('데이터 저장공간', ['서비스', '볼륨 · 마운트', '사용량 · 참고'],
             [[service, mount, usage + (' · ' + note if '확인 불가' in usage else '')]
              for service, mount, usage, note in storage_rows]))
        if overview:
            tables.extend([
                ('실행·포트', ['서비스 / 실제 이름', '가동·재시작 / 종료·OOM', 'PID · 공개 포트'],
                 [[name, f'{uptime} / {exit_oom}', ports] for name, uptime, exit_oom, ports, _, _ in runtime_rows]),
                ('통신량·이미지', ['서비스', 'NET·IO 누적', '이미지'],
                 [[resource[0], io, compact_image_reference(image)]
                  for resource, (_, _, _, _, io, image) in zip(resource_rows, runtime_rows)]),
            ])
        else:
            tables.append(
                ('실행 세부 정보', ['서비스 / 실제 이름', '가동·재시작 / 종료·OOM', 'PID · 공개 포트', 'NET·IO 누적', '이미지'],
                 [[service, f'{uptime} / {exit_oom}', ports, io, compact_image_reference(image)]
                  for service, uptime, exit_oom, ports, io, image in runtime_rows]))
    else:
        tables.append(('점검 결과', columns, rows))

    findings = [[kind, evidence, host_instructions(action, target)] for kind, evidence, action in findings]
    inherited_findings = {(kind, evidence, host_instructions(action, target))
                          for kind, evidence, action in inherited_findings}
    findings = list({tuple(item): item for item in findings}.values())
    counts = {kind: sum(item[0] == kind for item in findings) for kind in (*COUNT_KINDS, '설정 참고')}
    counts['미측정'] += sum(1 for _, _, data in tables for row in data if any('미측정' in str(x) or '확인 불가' in str(x) for x in row)) if mode == 'server' else 0
    actionable = sorted((item for item in findings if item[0] in FINDING_ORDER),
                        key=lambda item: FINDING_ORDER.index(item[0]))
    finding_columns = ['구분', '대상 / 근거', '다음 조치']
    if actionable:
        tables.insert(0, ('먼저 확인할 항목', finding_columns, actionable[:5]))
        remaining = [item for item in actionable[5:] if mode != 'health' or tuple(item) in inherited_findings]
        if remaining:
            tables.append(('추가 확인 항목', finding_columns, remaining))
    notes = []
    if counts['설정 참고']:
        notes.extend(['', f"ℹ️ RAM 한도 미설정 {counts['설정 참고']}개 · 호스트 RAM 공유"])
    if mode == 'containers' and rows:
        notes.extend(['', 'CPU 100%=1코어 · RAM 캐시 제외 · 공유 볼륨 중복 집계 주의'
                      + ('' if overview else f' · 상태 /{target}-health')])
    summary = report_summary(mode, target, columns, rows, result.get('facts'))
    return counts, summary, tables, notes


def report_metadata(captured, elapsed, request_id):
    captured = cell(captured).replace('`', '')
    duration = f'{elapsed}초' if elapsed is not None else '미측정'
    return f"`{captured}` · {duration} · 요청 `{request_id}`"


def table_header(title, columns):
    return ['', f'**{title}**', '', '| ' + ' | '.join(f'**{cell(column)}**' for column in columns) + ' |',
            '| ' + ' | '.join('---' for _ in columns) + ' |']


def build_report(result, request_id):
    counts, summary, tables, notes = report_content(result)
    mode, target = result['mode'], result.get('target', 'edo1')
    totals = ' · '.join(f'{STATUS_ICONS[kind]} **{kind} {counts[kind]}**' if counts[kind] else f'{kind} 0'
                        for kind in COUNT_KINDS)
    if not any(counts[kind] for kind in COUNT_KINDS):
        empty = tables[0][0] == '컨테이너 현황' and not counts['설정 참고']
        status = '컨테이너 없음' if empty else '정상'
        totals = f'{STATUS_ICONS[status]} {status} · {totals}'
    parts = [f"### {target} {MODE_LABELS.get(mode, mode)}", totals,
             report_metadata(result.get('captured_at', '확인 불가'), result.get('elapsed_seconds'), request_id)]
    if summary:
        parts.append(cell(summary))
    reserved = len('\n'.join(notes)) + len(OMISSION_NOTICE) + 3
    body_limit = REPORT_LIMIT - reserved
    for title, table_columns, table_rows in tables:
        header = table_header(title, table_columns)
        if len('\n'.join(parts + header)) > body_limit:
            parts.extend(['', OMISSION_NOTICE])
            break
        parts.extend(header)
        for row in table_rows[:100]:
            rendered = '| ' + ' | '.join(display_status(x) for x in row) + ' |'
            if len('\n'.join(parts)) + len(rendered) + 1 > body_limit:
                return '\n'.join(parts + ['', OMISSION_NOTICE] + notes)
            parts.append(rendered)
        if len(table_rows) > 100:
            omitted = f'출력 제한: {len(table_rows)-100}개 행 생략.'
            if len('\n'.join(parts)) + len(omitted) + 2 > body_limit:
                return '\n'.join(parts + ['', OMISSION_NOTICE] + notes)
            parts.extend(['', omitted])
    return '\n'.join(parts + notes)


def overview_cell(value):
    if isinstance(value, str):
        value = value.replace('복제 통신은 /edo2-health에서 확인', '복제 통신은 /edo2의 백업·복제 표에서 확인')
        value = re.sub(r'(?<![\w/])/(edo[12])-(health|server|containers)(?![a-zA-Z0-9-])', r'/\1', value)
    return display_status(value)


def table_blocks(title, columns, rows, budget):
    block = '\n' + '\n'.join(table_header(title, columns))
    continuation = '\n' + '\n'.join(table_header(title + ' (계속)', columns))
    omitted = False
    for row in rows:
        line = '\n| ' + ' | '.join(overview_cell(value) for value in row) + ' |'
        if len(continuation) + len(line) > budget:
            omitted = True
            continue
        if len(block) + len(line) > budget:
            yield block
            block = continuation
        block += line
    yield block
    if omitted:
        yield '\n\n' + OMISSION_NOTICE


def pack_blocks(blocks, budget):
    pages = []
    page = ''
    for block in blocks:
        if page and len(page) + len(block) > budget:
            pages.append(page)
            page = ''
        page += block
    if page:
        pages.append(page)
    return pages


def build_overview(target, results, request_id, captured_at, elapsed_seconds):
    counts_rows, summaries, core, details, notes = [], [], [], [], []
    totals = dict.fromkeys(COUNT_KINDS, 0)
    detail_titles = {'데이터 저장공간', '실행·포트', '통신량·이미지'}
    for mode in ('health', 'server', 'containers'):
        label = MODE_LABELS[mode]
        try:
            result = dict(results[mode], target=target)
            counts, summary, tables, mode_notes = report_content(result, overview=True)
        except (KeyError, IndexError, TypeError, ValueError, AttributeError):
            counts = dict.fromkeys(COUNT_KINDS, 0)
            counts['미측정'] = 1
            summary, mode_notes = '', []
            tables = [(f'{label} 수집 실패', ['판정', '다음 조치'],
                       [['미측정', f'/{target} 재실행 · 반복되면 edo-check journal의 요청 ID 확인']])]
        counts_rows.append([label, *(f'{STATUS_ICONS[kind]} **{counts[kind]}**' if counts[kind] else '0'
                                     for kind in COUNT_KINDS)])
        for kind in COUNT_KINDS:
            totals[kind] += counts[kind]
        if summary:
            summaries.append(cell(summary))
        for title, columns, rows in tables:
            if title in ('먼저 확인할 항목', '추가 확인 항목'):
                title = label + ' · ' + title
            (details if title in detail_titles else core).append((title, columns, rows))
        notes.extend(mode_notes)

    status = next((kind for kind in FINDING_ORDER if totals[kind]), '정상')
    intro = '\n'.join([f'### {target}', f'{STATUS_ICONS[status]} {status}',
                       report_metadata(captured_at, elapsed_seconds, request_id)])
    summary_lines = ''.join('\n' + summary for summary in summaries)
    continuation = f'### {target} 상세\n요청 `{request_id}`'
    budget = REPORT_LIMIT - max(len(intro + summary_lines), len(continuation)) - len(OMISSION_NOTICE) - 16
    core.insert(0, ('점검 요약', ['구분', *COUNT_KINDS], counts_rows))
    core_blocks = [block for title, columns, rows in core for block in table_blocks(title, columns, rows, budget)]
    detail_blocks = [block for title, columns, rows in details for block in table_blocks(title, columns, rows, budget)]
    if notes:
        detail_blocks.append('\n' + '\n'.join(notes))
    if sum(map(len, core_blocks + detail_blocks)) <= budget:
        bodies = [''.join(core_blocks + detail_blocks)]
    else:
        bodies = pack_blocks(core_blocks, budget) + pack_blocks(detail_blocks, budget)
    if len(bodies) > OVERVIEW_POST_LIMIT:
        bodies = bodies[:OVERVIEW_POST_LIMIT]
        bodies[-1] += '\n\n' + OMISSION_NOTICE
    return [(intro if index == 1 else continuation)
            + (f' · {index}/{len(bodies)}' if len(bodies) > 1 else '')
            + (summary_lines if index == 1 else '') + body
            for index, body in enumerate(bodies, 1)]


class PublicHTTPS(http.client.HTTPSConnection):
    """Pin the verified public address while retaining hostname/TLS verification."""
    def connect(self):
        addresses = socket.getaddrinfo(self.host, self.port, type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(item[4][0]).is_global for item in addresses):
            raise ValueError('non-public callback address')
        address = next((x for x in addresses if x[0] == socket.AF_INET), addresses[0])
        raw = socket.create_connection(address[4][:2], self.timeout)
        self.sock = self._context.wrap_socket(raw, server_hostname=self.host)


def post_callback(url, text, *, response_type='in_channel'):
    parsed = urlsplit(url)
    connection = PublicHTTPS(parsed.hostname, 443, timeout=15, context=ssl.create_default_context())
    try:
        body = json.dumps({'response_type': response_type, 'text': text}, ensure_ascii=False).encode()
        connection.request('POST', parsed.path, body, {'Content-Type': 'application/json'})
        response = connection.getresponse()
        response.read(4096)
        if not 200 <= response.status < 300:
            raise RuntimeError('callback rejected')
    finally:
        connection.close()


APPLOG_ENUMS = {
    'kind': set('run_started action visit_started data_load http_attempt poll_summary milestone flow_end log_gap'.split()),
    'target': set(('song_detail practice result weak_errors weak_select weak_loop section_result analysis '
                   'records record_detail song_bundle score evaluation record_page model_audio_metadata '
                   'model_audio_timeline model_audio practice_status weak_sections comparison auth_refresh '
                   'instruments song_list').split()),
    'source': {'network', 'navigation', 'retained'},
    'method': {'get', 'post'},
    'outcome': {'ready', 'missing', 'failed', 'cancelled', 'http_timeout', 'already_pending'},
    'reason': set(('initial retry version_mismatch refresh replay poll overflow storage uncertain cancelled failed missing '
                   'ready left unavailable http_timeout').split()),
    'phase': set(('score_ready score_drawn practice_ready practice_drawn record_drawn record_empty '
                  'model_audio_play timeline_ready player_load player_started session_create recording_upload '
                  'analysis_retry first_status_request analysis_ready').split()),
}
APPLOG_REQUIRED = {'kind', 'ms', 'run_id', 'version_name', 'version_code'}
APPLOG_NUMBERS = set('ms version_code flow visit load resource revision status duration_ms count failed missing evaluation_submission'.split())
APPLOG_FIELDS = APPLOG_REQUIRED | APPLOG_NUMBERS | APPLOG_ENUMS.keys() | {'evaluation_timing_id'}
APPLOG_VERSION = re.compile(r'qa-[0-9]{8}\.[0-9]{6}-[a-f0-9]{8}(?:-qa309)?')
APPLOG_ID = re.compile(r'[a-f0-9]{32}')
APPLOG_EVALUATION_TIMING_ID = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}')


def applog_id(value):
    return isinstance(value, str) and APPLOG_ID.fullmatch(value) is not None


def applog_number(value):
    return type(value) is int and 0 <= value <= 2**53 - 1


def encode_applog_event(event):
    if not isinstance(event, dict) or not APPLOG_REQUIRED <= event.keys() <= APPLOG_FIELDS:
        raise ValueError('invalid applog event fields')
    if (not applog_id(event['run_id']) or not isinstance(event['version_name'], str)
            or not APPLOG_VERSION.fullmatch(event['version_name'])):
        raise ValueError('invalid applog build metadata')
    if (not all(applog_number(event[key]) for key in event.keys() & APPLOG_NUMBERS)
            or not 1 <= event['version_code'] <= 2100000000):
        raise ValueError('invalid applog numeric values')
    if 'evaluation_timing_id' in event:
        timing_id = event['evaluation_timing_id']
        if (event['kind'] != 'milestone' or event.get('phase') not in ('recording_upload', 'analysis_retry', 'analysis_ready')
                or not isinstance(timing_id, str) or not APPLOG_EVALUATION_TIMING_ID.fullmatch(timing_id)):
            raise ValueError('invalid evaluation timing ID')
    for key in event.keys() & APPLOG_ENUMS.keys():
        value = event[key]
        if not isinstance(value, str) or value not in APPLOG_ENUMS[key]:
            raise ValueError('invalid applog enum')
    encoded = json.dumps(event, ensure_ascii=True, sort_keys=True, separators=(',', ':'))
    if len(encoded) > 1024:
        raise ValueError('oversize applog event')
    return encoded


class AppLogUnavailable(ValueError):
    def __init__(self, status):
        self.status = status
        super().__init__('QA bridge rejected applog request')


def request_applog(token, request_id, op, *, timeout, **fields):
    connection = http.client.HTTPConnection('127.0.0.1', 27890, timeout=timeout)
    try:
        body = json.dumps({'op': op, 'request_id': request_id, **fields})
        connection.request('POST', '/_internal/qa-perf/snapshot', body, {
            'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token,
        })
        response = connection.getresponse()
        if response.status != 200:
            raise AppLogUnavailable(response.status)
        raw = response.read(APPLOG_RESPONSE_LIMIT + 1)
        if len(raw) > APPLOG_RESPONSE_LIMIT:
            raise ValueError('oversize applog response')
        return json.loads(raw)
    finally:
        connection.close()


def open_applog_store(database):
    connection = sqlite3.connect(database, timeout=2)
    try:
        connection.execute('PRAGMA synchronous=FULL')
        connection.execute('PRAGMA secure_delete=ON')
        connection.executescript("""
            CREATE TABLE IF NOT EXISTS applog_events (
                stream_id TEXT NOT NULL, seq INTEGER NOT NULL, body TEXT NOT NULL,
                received_at INTEGER NOT NULL, PRIMARY KEY (stream_id, seq)
            );
            CREATE INDEX IF NOT EXISTS applog_events_retention ON applog_events(received_at);
            CREATE TABLE IF NOT EXISTS applog_requests (
                request_id TEXT PRIMARY KEY, stream_id TEXT NOT NULL,
                after INTEGER NOT NULL, end INTEGER NOT NULL, saved INTEGER NOT NULL,
                acknowledged INTEGER NOT NULL, started_at INTEGER NOT NULL
            );
        """)
        return connection
    except Exception:
        connection.close()
        raise


def save_applog_batch(connection, request_id, stream, after, end, rows):
    if not isinstance(rows, list) or not rows or len(rows) > end - after:
        raise ValueError('invalid applog batch size')
    received_at = int(time.time())
    with connection:
        for seq, row in enumerate(rows, start=after + 1):
            if (not isinstance(row, dict) or set(row) != {'seq', 'event'}
                    or not applog_number(row['seq']) or row['seq'] != seq):
                raise ValueError('applog sequence gap')
            body = encode_applog_event(row['event'])
            previous = connection.execute(
                'SELECT body FROM applog_events WHERE stream_id=? AND seq=?', (stream, seq),
            ).fetchone()
            if previous is None:
                connection.execute('INSERT INTO applog_events VALUES (?, ?, ?, ?)', (stream, seq, body, received_at))
            elif previous[0] != body:
                raise ValueError('conflicting applog retransmission')
            else:
                connection.execute(
                    'UPDATE applog_events SET received_at=? WHERE stream_id=? AND seq=?',
                    (received_at, stream, seq),
                )
        saved = after + len(rows)
        connection.execute('UPDATE applog_requests SET saved=? WHERE request_id=?', (saved, request_id))
    return saved


def prune_applog(database):
    cutoff = int(time.time()) - APPLOG_RETENTION
    with closing(open_applog_store(database)) as connection, connection:
        connection.execute('DELETE FROM applog_events WHERE received_at<=?', (cutoff,))
        connection.execute('DELETE FROM applog_requests WHERE started_at<=?', (cutoff,))


def collect_applog(token, request_id, database=APPLOG_DATABASE, *, tunnel=request_applog):
    deadline = time.monotonic() + APPLOG_TIMEOUT
    after = saved = acknowledged = end = None
    attempts = auth_attempts = retry_attempts = polls = reused = dropped = 0
    unknown_gap = False
    builds = set()
    slowest = {}

    def request(op, **fields):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError()
        result = tunnel(token, request_id, op, timeout=min(remaining, 9), **fields)
        if not isinstance(result, dict) or result.get('request_id') != request_id or result.get('op') != op:
            raise ValueError('applog reply does not match request')
        return result

    try:
        begin = request('begin')
        stream, after, end = begin['stream_id'], begin['ack'], begin['end']
        if not applog_id(stream) or not applog_number(after) or not applog_number(end) or after > end:
            raise ValueError('invalid applog range')
        saved = acknowledged = after
        with closing(open_applog_store(database)) as connection:
            with connection:
                connection.execute(
                    'INSERT INTO applog_requests VALUES (?, ?, ?, ?, ?, ?, ?)',
                    (request_id, stream, after, end, saved, acknowledged, int(time.time())),
                )
            while acknowledged < end:
                reply = request('read', stream_id=stream, after=acknowledged)
                if reply.get('stream_id') != stream:
                    raise ValueError('applog stream changed')
                rows = reply['events']
                saved = save_applog_batch(connection, request_id, stream, acknowledged, end, rows)
                for row in rows:
                    event = row['event']
                    if len(builds) < 4:
                        builds.add((event['version_name'], event['version_code']))
                    if event['kind'] == 'http_attempt':
                        reason = event.get('reason')
                        if reason in ('refresh', 'replay'):
                            auth_attempts += 1
                        elif reason == 'retry':
                            retry_attempts += 1
                        elif reason != 'poll':
                            attempts += 1
                    if event['kind'] == 'poll_summary':
                        polls += event.get('count', 0)
                    reused += event['kind'] == 'data_load' and event.get('source') in ('navigation', 'retained')
                    dropped += event.get('missing', 0) if event['kind'] == 'log_gap' else 0
                    unknown_gap |= event['kind'] == 'log_gap' and event.get('reason') == 'uncertain'
                    if event['kind'] == 'milestone' and 'phase' in event and 'duration_ms' in event:
                        phase = event['phase']
                        slowest[phase] = max(slowest.get(phase, 0), event['duration_ms'])
                ack = request('ack', stream_id=stream, through=saved)
                if not applog_number(ack.get('ack')) or ack['ack'] != saved:
                    raise ValueError('applog deletion unconfirmed')
                acknowledged = saved
                with connection:
                    connection.execute(
                        'UPDATE applog_requests SET acknowledged=? WHERE request_id=?', (acknowledged, request_id),
                    )
        result = '수집할 새 로그가 없습니다.' if after == end else '로그 수집·기기 삭제 완료'
    except (ValueError, KeyError, OSError, sqlite3.Error, http.client.HTTPException) as error:
        logging.warning('applog_interrupted request=%s error=%s', request_id, type(error).__name__)
        result = '로그 수집 중단 · 기기 연결과 QA 앱 상태를 확인한 뒤 다시 실행하세요.'
        if isinstance(error, AppLogUnavailable):
            result += f' (중계 HTTP {error.status})'
    parts = [f'**{result}**', f'요청 ID `{request_id}`']
    if acknowledged is not None:
        parts.append(f'범위 {after + 1}–{end} · 저장 {saved} · 삭제 확인 {acknowledged}' if after < end
                     else f'저장·삭제 확인 순번 {acknowledged}')
    if builds:
        versions = ', '.join(f'`{name}` ({code})' for name, code in sorted(builds))
        parts.extend([f'APK {versions}',
                      f'HTTP 최초 시도 {attempts} · 인증 갱신·재전송 {auth_attempts} · 재시도 {retry_attempts} · 상태 확인 {polls}',
                      f'데이터 재사용 {reused} · 기록 누락 {dropped}'])
        if unknown_gap:
            parts.append('이전 실행의 기록 누락 가능성이 있습니다.')
        phases = {
            'score_drawn': '첫 점수', 'practice_drawn': '연습 악보',
            'record_drawn': '첫 기록', 'player_started': '플레이어 시작',
        }
        timings = [f'{label} {slowest[phase]}ms' for phase, label in phases.items() if phase in slowest]
        if timings:
            parts.append('관측 최대 ' + ' · '.join(timings))
    return '\n'.join(parts)


class App:
    def __init__(self, config):
        self.config = config
        self.lock = threading.Lock()
        self.seen = {}
        self.last_run = {}
        self.active_targets = set()
        self.slots = threading.BoundedSemaphore(3)
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=3)
        if self.config.get('applog'):
            self.applog_seen = {}
            self.applog_last_run = -1000
            self.applog_slot = threading.BoundedSemaphore(1)
            self.applog_pool = concurrent.futures.ThreadPoolExecutor(max_workers=1)

    def submit(self, form):
        request = authorize(form, self.config)
        key = hashlib.sha256(request['response_url'].encode()).hexdigest()
        if request['mode'] == 'applog':
            return self.submit_applog(request, key)
        target, mode = request['target'], request['mode']
        throttle = (request['channel_id'], target, mode)
        now = time.monotonic()
        with self.lock:
            self.seen = {k: v for k, v in self.seen.items() if now - v < 1800}
            if key in self.seen:
                return '이미 접수한 점검 요청입니다.'
            if now - self.last_run.get(throttle, -1000) < 10:
                return '같은 점검은 10초 후 다시 요청하세요.'
            if target in self.active_targets or len(self.seen) >= 512 or not self.slots.acquire(blocking=False):
                return '점검 실행 중입니다. 잠시 후 다시 요청하세요.'
            self.active_targets.add(target)
            self.seen[key] = now
            self.last_run[throttle] = now
        request_id = uuid.uuid4().hex[:10]
        self.pool.submit(self.run, request, request_id)
        label = target if mode == 'overview' else f'{target} {MODE_LABELS[mode]}'
        return f'⏳ {label} 점검을 시작했습니다. 완료 후 이 채널에 공개합니다. 요청 {request_id}'

    def submit_applog(self, request, key):
        now = time.monotonic()
        with self.lock:
            self.applog_seen = {key: seen for key, seen in self.applog_seen.items() if now - seen < 1800}
            if key in self.applog_seen:
                return '이미 접수한 앱 로그 요청입니다.'
            if now - self.applog_last_run < 10:
                return '앱 로그 수집 요청은 10초 간격으로 실행할 수 있습니다.'
            if len(self.applog_seen) >= 512 or not self.applog_slot.acquire(blocking=False):
                return '앱 로그를 수집 중입니다. 잠시 후 다시 요청하세요.'
            self.applog_seen[key] = now
            self.applog_last_run = now
        request_id = uuid.uuid4().hex
        self.applog_pool.submit(self.run_applog, request['response_url'], request_id)
        return f'QA 휴대폰의 앱 로그 수집을 시작했습니다. 요청 ID `{request_id}`'

    def run_applog(self, callback, request_id):
        try:
            try:
                report = collect_applog(self.config['applog']['tunnel_token'], request_id)
            except Exception as error:
                logging.error('applog_failed request=%s error=%s', request_id, type(error).__name__)
                report = f'앱 로그 처리 결과를 확인하지 못했습니다. 요청 ID `{request_id}`'
            post_callback(callback, report, response_type='ephemeral')
            logging.info('applog_delivered request=%s', request_id)
        except Exception as error:
            logging.error('applog_delivery_failed request=%s error=%s', request_id, type(error).__name__)
        finally:
            self.applog_slot.release()

    def maintain_applog(self):
        while True:
            try:
                prune_applog(APPLOG_DATABASE)
            except (OSError, sqlite3.Error) as error:
                logging.error('applog_retention_failed error=%s', type(error).__name__)
            time.sleep(3600)

    def collect(self, settings, target, mode):
        ssh = ['ssh', '-4', '-T', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
               '-o', 'ConnectTimeout=8', '-o', 'IdentitiesOnly=yes',
               '-o', 'UserKnownHostsFile=' + settings['known_hosts'],
               '-i', settings['identity_file'], settings['ssh_target'], mode]
        completed = subprocess.run(ssh, capture_output=True, timeout=90, check=True)
        if len(completed.stdout) > 250000:
            raise ValueError('oversize result')
        result = json.loads(completed.stdout)
        if result.get('mode') != mode or result.get('target', 'edo1') != target:
            raise ValueError('unexpected collection target or mode')
        columns, rows = result['columns'], result['rows']
        minimum_columns = 4 if mode == 'health' else 1
        if (not isinstance(columns, list) or not minimum_columns <= len(columns) <= 20
                or not isinstance(rows, list)
                or any(not isinstance(row, list) or len(row) != len(columns) for row in rows)):
            raise ValueError('unexpected collection table')
        result['target'] = target
        return result

    def collect_overview(self, settings, target, request_id):
        results = {}
        # Keep Docker disk-usage scans sequential on each host.
        for mode in COLLECTION_MODES:
            try:
                results[mode] = self.collect(settings, target, mode)
            except Exception as error:
                logging.error('collection_failed request=%s target=%s mode=%s error=%s',
                              request_id, target, mode, type(error).__name__)
        if 'health' not in results:
            results['health'] = {
                'target': target, 'mode': 'health',
                'columns': ['대상', '위치/방식', '판정', '응답/측정값', '소요', '상세'],
                'rows': [[f'{target} 내부 상태', 'SSH 수집', '미측정', '수집 실패', '—', 'SSH 연결·점검 로그 확인']],
            }
        return results

    def run(self, request, request_id):
        target = request.get('target', 'edo1')
        mode = request['mode']
        started = time.monotonic()
        try:
            settings = target_settings(self.config, target)
            results = (self.collect_overview(settings, target, request_id) if mode == 'overview'
                       else {mode: self.collect(settings, target, mode)})
            if 'health' in results:
                results['health']['rows'].extend(external_health(settings['service_host'], target=target))
            captured_at = datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d %H:%M:%S KST')
            elapsed_seconds = round(time.monotonic() - started, 2)
            if mode == 'overview':
                reports = build_overview(target, results, request_id, captured_at, elapsed_seconds)
            else:
                result = dict(results[mode], captured_at=captured_at, elapsed_seconds=elapsed_seconds)
                reports = [build_report(result, request_id)]
        except subprocess.TimeoutExpired:
            reports = [f'🚫 **{target} 점검 시간 초과** · 요청 {request_id}. 수집 결과 미측정입니다. 같은 명령을 1회 재실행하고 반복되면 edo2의 edo-check journal에서 해당 요청 ID를 확인하세요.']
        except Exception:
            logging.error('collection_failed request=%s target=%s mode=%s', request_id, target, mode)
            reports = [f'🚫 **{target} 점검 실패** · 요청 {request_id}. 수집 결과 미측정입니다. 운영자는 edo2→{target} SSH 연결과 edo-check journal의 해당 요청 ID를 확인하세요.']
        try:
            for part, report in enumerate(reports, 1):
                try:
                    post_callback(request['response_url'], report)
                    logging.info('delivered request=%s target=%s mode=%s part=%s/%s',
                                 request_id, target, mode, part, len(reports))
                except Exception:
                    logging.error('delivery_failed request=%s target=%s mode=%s part=%s/%s',
                                  request_id, target, mode, part, len(reports))
        finally:
            with self.lock:
                self.active_targets.discard(target)
                self.slots.release()


def external_health(host, target='edo1'):
    rows = []
    probes = [('웹 외부 접속', '/'), ('API health', '/api/actuator/health/readiness')] if target == 'edo1' else [('중계 HTTPS', '/healthz')]
    location = 'edo2 외부 HTTPS' if target == 'edo1' else 'edo2 자체 HTTPS'
    for label, path in probes:
        started = time.monotonic()
        connection = http.client.HTTPSConnection(host, timeout=8, context=ssl.create_default_context())
        try:
            connection.request('GET', path)
            response = connection.getresponse()
            body = response.read(4096)
            evidence = f'HTTP {response.status}'
            status = '정상' if response.status == 200 else '실패'
            if label in ('API health', '중계 HTTPS'):
                if response.status == 404:
                    status, evidence = '미검증', 'HTTP 404 · 이 URL에서 준비 상태 확인 못 함'
                elif response.status == 200:
                    try:
                        health = json.loads(body)
                        state = health.get('status')
                        expected = 'UP' if target == 'edo1' else 'ok'
                        status = '정상' if state == expected else '실패' if state in ('DOWN', 'OUT_OF_SERVICE') else '미검증'
                        evidence += ' · JSON status=' + str(health.get('status', '없음'))
                    except (ValueError, AttributeError):
                        status, evidence = '미검증', 'HTTP 200 · 기대한 health JSON 아님'
            rows.append([label, location, status, evidence,
                         f'{(time.monotonic()-started)*1000:.0f}ms', path])
        except Exception:
            rows.append([label, location, '실패', '연결·TLS·응답 확인 불가', '—', path])
        finally:
            connection.close()
    try:
        with socket.create_connection((host, 443), timeout=8) as raw:
            with ssl.create_default_context().wrap_socket(raw, server_hostname=host) as secured:
                expires = ssl.cert_time_to_seconds(secured.getpeercert()['notAfter'])
        days = (expires - time.time()) / 86400
        threshold = 7 if target == 'edo1' else 1
        rows.append(['서비스 인증서' if target == 'edo1' else '중계 IP 인증서', 'edo2 외부 TLS' if target == 'edo1' else 'edo2 자체 TLS', '정상' if days >= threshold else '주의',
                     f'만료까지 {days:.1f}일', '—', host])
    except Exception:
        rows.append(['서비스 인증서' if target == 'edo1' else '중계 IP 인증서',
                     'edo2 외부 TLS' if target == 'edo1' else 'edo2 자체 TLS', '실패', '인증서 검증 실패', '—', host])
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
    validate_config(config)
    server = ThreadingHTTPServer(('127.0.0.1', 8765), Handler)
    server.daemon_threads = True
    server.app = App(config)
    if config.get('applog'):
        threading.Thread(target=server.app.maintain_applog, daemon=True, name='applog-retention').start()
    server.serve_forever()
