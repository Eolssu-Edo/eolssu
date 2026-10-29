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
from datetime import datetime, timedelta, timezone
import re
import socket
import ssl
import subprocess
import sys
import threading
import time
from urllib.parse import parse_qs, urlsplit
import uuid

TARGETS = ('edo1', 'edo2')
MODES = {f'/{target}-{mode}': mode for target in TARGETS for mode in ('server', 'containers', 'health')}
COMMAND_TARGETS = {command: command.split('-')[0][1:] for command in MODES}
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
    for target in TARGETS:
        group = {command for command in MODES if COMMAND_TARGETS[command] == target}
        if commands & group:
            if not group.issubset(commands):
                raise ValueError('incomplete target command group')
            settings = target_settings(config, target)
            if any(not settings.get(key) for key in ('ssh_target', 'known_hosts', 'identity_file', 'service_host')):
                raise ValueError('incomplete target configuration')


def cell(value):
    text = re.sub(r'[\x00-\x1f\x7f]', ' ', str(value))
    return text.replace('@', '@\u200b').replace('|', '\\|').replace('<', '&lt;').replace('>', '&gt;')[:1600]


STATUS_ICONS = {
    '정상': '✅', '장애': '❌', '주의': '⚠️', '미검증': '❓', '미측정': '🚫', '설정 참고': 'ℹ️',
    '실행 · 검사 통과': '✅', '실행 · 준비 미검증': '❓',
    '검사한 범위 정상': '✅', '장애 확인': '❌', '주의 필요': '⚠️', '일부 확인 못 함': '❓',
    '일부 측정 못 함': '🚫',
    '해당 없음': '➖', '컨테이너 없음': '➖', '실험 중지': 'ℹ️',
}


def display_status(value):
    """Decorate final labels only; health decisions and counts keep raw values."""
    icon = STATUS_ICONS.get(str(value))
    if not icon:
        return cell(value)
    # Keep Korean status words intact in narrow Mattermost table columns.
    label = re.sub(r'[가-힣]{2,}', lambda match: '\u2060'.join(match.group()), cell(value))
    return f'{icon}\u00a0**{label}**'


NAMES = {
    'eolssu-runtime-frontend-1': '웹', 'eolssu-runtime-backend-1': 'API 서버',
    'eolssu-runtime-mysql-1': '서비스 DB(MySQL)',
    'eolssu-monitoring-grafana-1': '모니터링(Grafana)',
    'eolssu-monitoring-prometheus-1': '수치 수집(Prometheus)',
    'eolssu-jenkins-controller-1': '배포(Jenkins)',
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


def build_report(result, request_id):
    target = result.get('target', 'edo1')
    if target not in TARGETS:
        raise ValueError('unexpected report target')
    columns, rows = result['columns'], result['rows']
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

    inherited_findings = list(findings)
    if result['mode'] == 'health':
        rendered = []
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
            status = {'실패': '장애', '미설정': '미검증', '확인 불가': '미측정'}.get(status, status)
            if status == '정상':
                action = '현재 조치 없음'
            elif status == '미검증':
                action = ('실제 백엔드 health URL·Nginx 프록시 경로를 대조; HTTP 200과 status=UP을 반환하는 경로 확정 후 재점검' if name == 'API health'
                          else '프로세스 실행만 확인; /edo1-health의 별도 HTTP 준비 검사를 확인하고 없으면 대상 healthcheck 구성 필요')
            elif status == '미측정':
                action = '/edo1-health를 1회 재실행; 반복되면 edo2→edo1 SSH 연결·Docker 조회 권한 확인'
            elif status == '실험 중지':
                action = str(row[5])
            else:
                action = (('만료 7일 미만이면 서비스 인증서 갱신 작업·실패 로그 확인 후 /edo1-health 재실행' if status == '주의' else
                           'edo2에서 TLS 재연결; 서비스 호스트명·인증서 체인·현재 인증서 유효기간 확인 후 /edo1-health 재실행') if 'TLS' in location else
                          'edo2에서 공개 HTTPS 재요청; /edo1-containers의 웹·API 실행 상태와 Nginx upstream 확인' if '외부 HTTPS' in location else
                          'edo1에서 해당 loopback 준비 경로 재요청; 서비스 로그의 DB 연결 오류 확인' if 'loopback' in location else
                          'edo1에서 대상 docker inspect의 State·Health와 docker logs --tail 50 확인')
            if location in ('edo1 systemd', 'edo2 systemd', 'edo2 구성 검사', 'edo2 백업 메타데이터', 'edo1 모니터링 HTTP') or name == 'eolssu-runtime-ai-1':
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
            rendered.append([friendly(name), status, checked, '—' if status == '정상' else action])
            if status != '정상':
                findings.append([status, friendly(name) + ': ' + str(evidence), action])
        tables.append(('서비스 검사 결과', ['대상', '판정', '검사 근거 · 소요', '필요한 조치'], rendered))

    elif result['mode'] == 'server' and len(columns) == 10 and len(rows) == 1:
        values, facts = rows[0], result.get('facts') or {}
        meanings = ['호스트·OS·커널과 서버 가동 시간', '0.2초 표본. CPU 100%는 서버 전체 사용',
                    '현재 사용량과 즉시 사용 가능한 메모리', '디스크로 옮겨진 메모리. 사용 중이라는 이유만으로 장애는 아님',
                    '사용 / 전체, 가용 공간, 사용률과 inode(파일 개수) 사용률',
                    'Docker 데몬 응답과 실행 수; 컨테이너 준비 상태는 /edo1-health에서 확인',
                    '컨테이너 사용량 합계 / Docker가 인식하는 호스트 RAM. 예약량 아님',
                    '저장공간 분류별 점유량. 공유 계층 때문에 단순 합산 금지',
                    'Docker가 회수 가능하다고 계산한 양. 자동 정리하지 않음',
                    '현재 실패한 systemd 서비스 / 최근 24시간 OOM 매칭 로그 줄 수(사건 수 아님)']
        criteria = ['예상하지 않은 재부팅이면 edo1에서 last reboot로 발생 시각 확인',
                    '0.2초 표본만으로 지속 과부하 판정하지 않음; 아래 Load 기준과 함께 확인',
                    'RAM 80% 이상이면 /edo1-containers에서 RAM 큰 서비스 확인; 30초 간격 3회 값 비교',
                    'Swap 사용만으로 장애 아님; RAM 80% 이상 또는 OOM 동반 시 큰 서비스의 RAM·한도 확인',
                    '',
                    'Docker 응답 실패 또는 웹·API·MySQL 누락이면 /edo1-health로 대상 판정 확인',
                    '호스트 RAM 80% 이상일 때 서비스별 사용량 확인; 합계는 메모리 예약량이 아님',
                    '',
                    '호스트 디스크 80% 이상일 때 미사용 이미지·캐시 목록 확인 후 정리 대상을 결정',
                    '실패 서비스 1개 이상: systemctl --failed; OOM 로그 1줄 이상: 시각·대상 확인 후 RAM·한도 비교']
        if target == 'edo2':
            criteria[5] = 'Docker 응답 실패 시 docker.service·socket 권한 확인; 등록 0개는 컨테이너를 실행하지 않는 현재 구성'
        entries = []
        for index, (label, value) in enumerate(zip(columns, values)):
            if index == 1 and facts.get('load'):
                value = str(value).split(' · Load ')[0]
                entries.append(['부하(Load 1 / 5 / 15분)', ' / '.join(f'{x:.2f}' for x in facts['load']),
                                '실행 가능 작업·중단 불가 대기의 1·5·15분 평균. CPU 사용률과 다른 값',
                                f"5분 또는 15분 값이 {facts['cpu_cores']}코어 수를 넘으면 /edo1-containers CPU 상위 확인; 낮으면 edo1의 I/O 대기 확인(수동 확인 기준)"])
            if index == 2 and facts.get('ram_total'):
                total, available = facts['ram_total'], facts['ram_available']
                value = f'{amount(total-available)} / {amount(total)} ({(1-available/total)*100:.0f}%) · 가용 {amount(available)}'
            if index == 4:
                for part in str(value).split('; '):
                    entries.append(['파일시스템', part, meanings[index], '공간 80% 이상: 큰 로그·데이터 / inode 80% 이상: 파일 개수 많은 디렉터리·회전 파일 확인. 둘 중 90% 이상이면 긴급 처리'])
            elif index == 7 and len(str(value).split(' / ')) == 4:
                for title, number in zip(('Docker 이미지', '컨테이너 쓰기 계층', 'Docker 볼륨', '빌드 캐시'), str(value).split(' / ')):
                    action = {'Docker 이미지': 'FS 80% 이상이면 사용 중 컨테이너와 연결되지 않은 이미지 목록 확인',
                              '컨테이너 쓰기 계층': 'FS 80% 이상이면 /edo1-containers의 쓰기 계층 큰 서비스 확인',
                              'Docker 볼륨': 'FS 80% 이상이면 데이터 저장공간 표에서 큰 mount 확인; DB 데이터 자동 삭제 없음',
                              '빌드 캐시': 'FS 80% 이상이면 docker builder du로 캐시별 회수 가능량 확인'}[title]
                    entries.append([title, number, meanings[index], action])
            else:
                entries.append([label, value, meanings[index], criteria[index]])
        tables.append(('서버 현재 자원', ['항목', '현재값', '확인 기준 · 다음 조치'],
                       [[label, value, host_instructions(meaning + '. ' + criterion, target)] for label, value, meaning, criterion in entries]))

    elif result['mode'] == 'containers' and len(columns) == 11 and not rows:
        tables.append(('컨테이너 현황', ['대상', '판정', '확인한 내용'],
                       [[target + ' Docker', '해당 없음', '엔진 조회 성공 · 등록된 컨테이너 0개. 이미지·캐시는 /' + target + '-server에서 확인']]))
    elif result['mode'] == 'containers' and len(columns) == 11:
        facts = {item['name']: item for item in result.get('facts') or []}
        resource_rows, storage_rows, runtime_rows = [], [], []
        for row in rows:
            name, state = str(row[0]), str(row[1])
            fact = facts.get(name, {})
            if '확인 불가' in state:
                status = '미측정'
            elif target == 'edo2' and fact.get('expected_stop') and state.startswith('exited/') and str(row[7]) == '143/아니오':
                status = '실험 중지'
            elif not state.startswith('running/') or 'unhealthy' in state:
                status = '장애'
            else:
                status = '실행 · 검사 통과' if state.endswith('/healthy') else '실행 · 준비 미검증'
            if status == '미측정':
                findings.append(['미측정', friendly(name) + ' 상세 수집 실패', 'Docker inspect 연결·권한 확인'])
            elif status == '장애':
                findings.append(['장애', friendly(name) + ': ' + state, '컨테이너 실행 상태·최근 로그 확인'])
            elif status == '실험 중지':
                findings.append(['실험 중지', friendly(name) + ': ' + str(fact['expected_stop']), '의도한 중지 유지; 이미지·볼륨·로그 보존'])
            elif str(row[7]).endswith('/예'):
                findings.append(['주의', friendly(name) + ' OOM 강제 종료 기록 있음', '현재 실행 상태와 별도로 메모리 한도·커널 OOM 기록 확인'])
            if status == '실행 · 준비 미검증':
                findings.append(['미검증', friendly(name) + ' 준비 상태', '/edo1-health에서 실제 준비 상태 검사 확인; 장애 확정 아님'])
            missing = [label for index, label in ((4, '쓰기/로그'), (5, '데이터 용량'), (9, '누적 NET/IO'))
                       if '미측정' in str(row[index]) or '확인 불가' in str(row[index])]
            if status != '미측정' and missing:
                findings.append(['미측정', friendly(name) + ' ' + ', '.join(missing),
                                 'Docker가 통계를 제공하는지 확인; 기록 없음은 0으로 단정하지 않음' if missing == ['누적 NET/IO'] else '재점검 후 측정 경로·드라이버 지원 확인'])
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
            resource_rows.append([friendly(name), status, cpu, ram,
                                  '쓰기 ' + write.replace('/미설정(호스트 공유)', ' · 호스트 공유') + ' / 로그 ' + str(log)])
            for mount in str(row[5]).split('; '):
                mount_target, separator, usage = mount.rpartition(': ')
                storage_rows.append([friendly(name), mount_target if separator else '데이터 저장소', usage if separator else mount,
                                     '다른 컨테이너와 중복 합산 금지' if '(공유)' in mount else
                                     '12초 또는 전체 25초 측정 예산; 재점검 필요' if '확인 불가' in mount else
                                     '별도 데이터 mount 없음' if mount == '없음' else '볼륨/bind 값은 쓰기 계층·로그와 별개'])
            uptime = str(row[6]) + (' · ' + str(fact['expected_stop']) if status == '실험 중지' else '')
            runtime_rows.append([friendly(name) + ' (' + name + ')', uptime, row[7], row[8], row[9], row[10]])
            if status != '미측정' and (fact.get('ram_bytes') is None or fact.get('cpu_percent') is None):
                findings.append(['미측정', friendly(name) + ' CPU 또는 RAM', '다시 점검; 실행 상태와 통계 수집은 별개'])
        tables.extend([
            ('컨테이너 현재 자원', ['서비스', '상태', 'CPU / 상한', 'RAM / 상한', '쓰기 · 로그'], resource_rows),
            ('데이터 저장공간', ['서비스', '볼륨 · 마운트', '사용량 · 참고'],
             [[service, target, usage + (' · ' + note if '확인 불가' in usage else '')] for service, target, usage, note in storage_rows]),
            ('실행 세부 정보', ['서비스 / 실제 이름', '가동·재시작 / 종료·OOM', 'PID · 공개 포트', 'NET·IO 누적', '이미지'],
             [[service, '가동·재시작 ' + str(uptime) + ' / 종료·OOM ' + str(exit_oom), ports, io, compact_image_reference(image)]
              for service, uptime, exit_oom, ports, io, image in runtime_rows]),
        ])
    else:
        tables.append(('점검 결과', columns, rows))

    findings = [[kind, evidence, host_instructions(action, target)] for kind, evidence, action in findings]
    inherited_findings = [[kind, evidence, host_instructions(action, target)] for kind, evidence, action in inherited_findings]
    findings = list({tuple(item): item for item in findings}.values())
    counts = {kind: sum(item[0] == kind for item in findings) for kind in ('장애', '주의', '미검증', '미측정', '설정 참고', '실험 중지')}
    counts['미측정'] += sum(1 for _, _, data in tables for row in data if any('미측정' in str(x) or '확인 불가' in str(x) for x in row)) if result['mode'] == 'server' else 0
    status = '장애 확인' if counts['장애'] else '주의 필요' if counts['주의'] else '일부 확인 못 함' if counts['미검증'] else '일부 측정 못 함' if counts['미측정'] else '검사한 범위 정상'
    if result['mode'] == 'containers' and len(columns) == 11 and not rows and not findings:
        status = '컨테이너 없음'
    captured = cell(result.get('captured_at', '확인 불가')).replace('`', '')
    started = cell(result.get('started_at', captured)).replace('`', '')
    parts = [f"### {target} {result['mode']} 점검 — {display_status(status)}",
             f"수집 시각: `{captured}`", f"시작 `{started}` · 소요 {result.get('elapsed_seconds', '미측정')}초 · 요청 `{request_id}`", '',
             ' · '.join(f'{STATUS_ICONS[kind]} **{kind} {counts[kind]}**' if counts[kind] else f'{kind} 0'
                        for kind in ('장애', '주의', '미검증', '미측정')),
             '이번 명령에서 새로 수집한 스냅샷입니다. 이 메시지는 자동 갱신되지 않습니다. 최신 상태는 같은 명령을 다시 실행하세요.']
    actionable = [item for item in (inherited_findings if result['mode'] == 'health' else findings) if item[0] not in ('설정 참고', '실험 중지')]
    if actionable:
        tables.insert(0, ('먼저 확인할 항목', ['구분', '대상 / 근거', '다음 조치'], actionable[:12]))
    if counts['설정 참고']:
        parts.extend(['', f"ℹ️ **설정 참고 {counts['설정 참고']}건**: 메모리 제한 없음은 현재 장애라는 뜻이 아닙니다. 해당 컨테이너는 호스트 메모리를 공유합니다."])
    if counts['실험 중지']:
        parts.extend(['', f"ℹ️ **실험 중지 {counts['실험 중지']}건**: 기록된 종료 사건과 일치합니다. 실행·준비 상태가 정상이라는 뜻은 아니며 자원 미측정은 그대로 표시합니다."])
    if result['mode'] == 'containers':
        parts.extend(['', f'CPU 100%=1코어 사용(2코어 한도는 200%). RAM은 cache 제외. 쓰기·로그·데이터는 별도 공간이며 공유 볼륨은 중복 합산하지 않습니다. 별도 mount 없음은 데이터가 없다는 뜻이 아닙니다. NET·IO는 누적 전송량입니다. 준비 상태는 /{target}-health에서 확인하세요.'])
    if result['mode'] == 'health':
        parts.extend(['', 'Docker healthy는 설정된 healthcheck 통과입니다. HTTP 준비 응답·업무 기능·장기 오류율은 서로 다른 검사 범위입니다.'])
        if target == 'edo2':
            parts.extend(['자체 HTTPS는 edo2에서 자기 공인 IP를 검사한 결과입니다. IP 인증서는 만료 24시간 미만에 주의로 표시합니다. 백업 timer와 oneshot 이력은 실제 백업 무결성 검증을 대신하지 않습니다.'])
    for title, table_columns, table_rows in tables:
        header = ['', '**' + title + '**', '', '| ' + ' | '.join('**'+cell(x)+'**' for x in table_columns) + ' |',
                  '| ' + ' | '.join('---' for _ in table_columns) + ' |']
        if len('\n'.join(parts + header)) > 14000:
            parts.append('\n출력 제한: 남은 표 생략. 운영자 확인이 필요합니다.')
            break
        parts.extend(header)
        for row in table_rows[:100]:
            rendered = '| ' + ' | '.join(display_status(x) for x in row) + ' |'
            if len('\n'.join(parts)) + len(rendered) + 1 > 14000:
                parts.append('\n출력 제한: 나머지 행·표 생략. 운영자 확인이 필요합니다.')
                return '\n'.join(parts)
            parts.append(rendered)
        if len(table_rows) > 100:
            parts.append(f'\n출력 제한: {len(table_rows)-100}개 행 생략.')
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
        throttle = (request['channel_id'], request['target'], request['mode'])
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
        return f"⏳ {request['target']} {request['mode']} 점검을 시작했습니다. 완료 후 이 채널에 공개합니다. 요청 {request_id}"

    def run(self, request, request_id):
        target = request.get('target', 'edo1')
        started = time.monotonic()
        started_at = datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d %H:%M:%S KST')
        try:
            settings = target_settings(self.config, target)
            ssh = ['ssh', '-4', '-T', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
                   '-o', 'ConnectTimeout=8', '-o', 'IdentitiesOnly=yes',
                   '-o', 'UserKnownHostsFile=' + settings['known_hosts'],
                   '-i', settings['identity_file'], settings['ssh_target'], request['mode']]
            completed = subprocess.run(ssh, capture_output=True, timeout=90, check=True)
            if len(completed.stdout) > 250000:
                raise ValueError('oversize result')
            result = json.loads(completed.stdout)
            if result.get('mode') != request['mode'] or result.get('target', 'edo1') != target:
                raise ValueError('unexpected collection target or mode')
            result['target'] = target
            if request['mode'] == 'health':
                result['rows'].extend(external_health(settings['service_host'], target=target))
            result['started_at'] = started_at
            result['captured_at'] = datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d %H:%M:%S KST')
            result['elapsed_seconds'] = round(time.monotonic()-started, 2)
            report = build_report(result, request_id)
        except subprocess.TimeoutExpired:
            report = f'🚫 **{target} 점검 시간 초과** · 요청 {request_id}. 수집 결과 미측정입니다. 같은 명령을 1회 재실행하고 반복되면 edo2의 edo-check journal에서 해당 요청 ID를 확인하세요.'
        except Exception:
            logging.error('collection_failed request=%s target=%s mode=%s', request_id, target, request['mode'])
            report = f'🚫 **{target} 점검 실패** · 요청 {request_id}. 수집 결과 미측정입니다. 운영자는 edo2→{target} SSH 연결과 edo-check journal의 해당 요청 ID를 확인하세요.'
        try:
            post_callback(request['response_url'], report)
            logging.info('delivered request=%s target=%s mode=%s', request_id, target, request['mode'])
        except Exception:
            logging.error('delivery_failed request=%s target=%s mode=%s', request_id, target, request['mode'])
        finally:
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
    server.serve_forever()
