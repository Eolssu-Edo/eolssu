#!/usr/bin/python3
"""Root-owned, read-only collector; only fixed host profiles and modes are accepted."""
import concurrent.futures
from datetime import datetime, timedelta, timezone
import http.client
from ipaddress import IPv4Address, IPv4Network
import json
import os
import re
from pathlib import Path
import socket
import stat
import subprocess
import sys
import time

REQUIRED_CONTAINERS = {'eolssu-runtime-frontend-1', 'eolssu-runtime-mysql-1', 'eolssu-runtime-ai-1',
                       'eolssu-monitoring-grafana-1', 'eolssu-monitoring-prometheus-1',
                       'eolssu-monitoring-node-exporter-1'}
REQUIRED_BY_TARGET = {'edo1': REQUIRED_CONTAINERS, 'edo2': set()}
STANDBY_WEB = 'eolssu-standby-579-frontend-1'
STANDBY_WEB_LOCATION = 'edo2 대기 웹 loopback HTTPS'
STANDBY_WEB_ACTION = '대기 웹 127.0.0.1:18443 포트·localhost 인증서·/index.html 응답 확인'
EDO1_READINESS_LOCATION = 'edo1 서비스 준비 검사'
BLOG_ORIGIN = 'https://team-edo.github.io'
BLOG_LOGIN = 'eolssu-blog-editor'
BLOG_WRITABLE = 'eolssu-blog-editor-writable'
MYSQL_PROXY = 'eolssu-replication-proxy-579'
PHPMYADMIN = 'eolssu-mysql-admin-phpmyadmin-1'
ANALYSIS_AI = 'eolssu-runtime-ai-1'
REVIEW_TUNNEL = 'hey-edo1-tunnel-1'
DOCKER_BRIDGE_RANGE = IPv4Network('172.16.0.0/12')
EDO1_PUBLISHED_PROBES = {
    BLOG_LOGIN: ('8000/tcp', '172.18.0.1', 18120, '/config'),
    BLOG_WRITABLE: ('8000/tcp', '172.18.0.1', 18121, '/config'),
    PHPMYADMIN: ('80/tcp', '127.0.0.1', 8081, '/'),
}
EDO1_BRIDGE_PROBES = {
    ANALYSIS_AI: ('eolssu-runtime_default', 8000, '/v1/analyses'),
    REVIEW_TUNNEL: ('hey-edo1_control', 20241, '/ready'),
}
EDO1_READINESS_ACTIONS = {
    BLOG_LOGIN: '블로그 로그인 편집기의 내부 /config, OAuth 설정과 CORS 확인',
    BLOG_WRITABLE: '블로그 쓰기 편집기의 내부 /config, OAuth 설정·CORS와 canBatch 확인',
    MYSQL_PROXY: 'edo1 포트 매핑·수신 상태와 edo2 eolssu-replication-watch.service 결과 확인',
    PHPMYADMIN: 'phpMyAdmin 127.0.0.1:8081 로그인 화면과 컨테이너 로그 확인',
    ANALYSIS_AI: '분석 API 입력 검증과 별도로 격리된 음원으로 추론·채점 확인',
    REVIEW_TUNNEL: 'Cloudflared 내부 /ready와 엣지 연결 상태 확인',
}
EOLSSU_SYSLOG_DIR = Path('/var/log/eolssu/ordinary')
EOLSSU_SYSLOG_TAGS = {'eolssu-frontend', 'eolssu-backend', 'eolssu-ai', 'eolssu-mysql', 'eolssu-playback', 'eolssu-omr'}
PROC_ROOT = Path('/proc')
CGROUP_ROOT = Path('/sys/fs/cgroup')
BLOG_EDITOR_ROLLBACK_NAME = re.compile(r'eolssu-blog-editor-writable-(?:before|rollback|pre)-[0-9a-f]{8}')


def resource_stats(stats):
    memory = stats.get('memory_stats', {})
    cached = memory.get('stats', {})
    cache = cached.get('inactive_file', cached.get('total_inactive_file', cached.get('cache', 0)))
    current, previous = stats.get('cpu_stats', {}), stats.get('precpu_stats', {})
    used = current.get('cpu_usage', {}).get('total_usage', 0) - previous.get('cpu_usage', {}).get('total_usage', 0)
    total = current.get('system_cpu_usage', 0) - previous.get('system_cpu_usage', 0)
    cores = current.get('online_cpus', len(current.get('cpu_usage', {}).get('percpu_usage', [])) or 1)
    return {'memory': max(0, memory.get('usage', 0) - cache),
            'cpu': max(0, used / total * cores * 100) if total > 0 else 0,
            'memory_available': 'usage' in memory, 'cpu_available': total > 0}


def image_disk_bytes(data):
    return data.get('LayersSize')


def disk_usage_summary(issues):
    try:
        storage = docker('/system/df', timeout=20)
        if not isinstance(storage, dict):
            raise ValueError('unexpected Docker storage response')
    except (OSError, http.client.HTTPException, RuntimeError, ValueError):
        issues.append('Docker 저장공간 수집 실패·시간 초과')
        return '확인 불가', '확인 불가'

    categories = ('ImageUsage', 'ContainerUsage', 'VolumeUsage', 'BuildCacheUsage')
    if 'LayersSize' in storage and not any(category in storage for category in categories):
        # Before Engine API 1.52 the response did not include reclaimable summaries.
        def total(values):
            return sum(values) if all(isinstance(value, int) and value >= 0 for value in values) else None

        used = [image_disk_bytes(storage),
                total([item.get('SizeRw') for item in storage.get('Containers') or []]),
                total([(item.get('UsageData') or {}).get('Size') for item in storage.get('Volumes') or []]),
                total([item.get('Size') for item in storage.get('BuildCache') or []])]
        reclaimable = [None] * len(categories)
    else:
        summaries = [storage.get(category) for category in categories]
        # Engine omits zero totals from a present category, including an empty object.
        used = [summary.get('TotalSize', 0) if isinstance(summary, dict) else None for summary in summaries]
        reclaimable = [summary.get('Reclaimable', 0) if isinstance(summary, dict) else None for summary in summaries]

    def valid_bytes(value):
        return type(value) is int and value >= 0

    if not all(map(valid_bytes, used + reclaimable)):
        issues.append('Docker 저장공간 일부 수집 실패')
    used_text = ' / '.join(size(value) if valid_bytes(value) else '확인 불가' for value in used)
    reclaimable_text = ' · '.join(f'{label} {size(value) if valid_bytes(value) else "확인 불가"}'
                                  for label, value in zip(('이미지', '쓰기', '볼륨', '캐시'), reclaimable))
    return used_text, reclaimable_text


def size(value):
    if value is None or value < 0:
        return '확인 불가'
    for unit in ('B', 'KiB', 'MiB', 'GiB', 'TiB'):
        if value < 1024 or unit == 'TiB':
            return f'{value:.1f}{unit}'
        value /= 1024


def duration(seconds):
    seconds = max(0, int(seconds))
    return f'{seconds // 86400}일 {(seconds % 86400) // 3600}시간 {(seconds % 3600) // 60}분'


def run(arguments, timeout=4):
    return subprocess.run(arguments, capture_output=True, text=True, timeout=timeout)


class DockerConnection(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(self.timeout)
        self.sock.connect('/var/run/docker.sock')


def docker(path, timeout=15):
    connection = DockerConnection('localhost', timeout=timeout)
    try:
        connection.request('GET', path)
        response = connection.getresponse()
        body = response.read(4 * 1024 * 1024 + 1)
        if response.status != 200 or len(body) > 4 * 1024 * 1024:
            raise RuntimeError('Docker read unavailable')
        return json.loads(body)
    finally:
        connection.close()


def configured_backend_slot(target, issues):
    if target != 'edo1':
        return None
    try:
        route = Path('/opt/eolssu/config/backend-route/active.conf').read_text().strip()
        return {'set $backend http://backend:8080;': 'backend',
                'set $backend http://backend-green:8080;': 'backend-green'}[route]
    except FileNotFoundError:
        return 'backend'  # Legacy single-slot runtime.
    except (OSError, KeyError, UnicodeError):
        issues.append('활성 백엔드 슬롯 확인 불가')
        return None


def required_containers(target, slot):
    required = REQUIRED_BY_TARGET[target]
    return required | {f'eolssu-runtime-{slot}-1'} if slot else required


def snapshot(with_storage=False, collect_stats=True, target='edo1', *, required=None):
    info = docker('/info')
    containers = docker('/containers/json?all=1' + ('&size=1' if with_storage else ''))
    issues = []
    if len(containers) > 100:
        issues.append(f'컨테이너 {len(containers)}개 중 100개만 수집')
    names = {name.lstrip('/') for item in containers for name in item.get('Names', [])}
    if required is None:
        required = required_containers(target, configured_backend_slot(target, issues))
    issues.extend(name + ' 필수 컨테이너 누락' for name in sorted(required - names))
    def one(item):
        try:
            detail = docker('/containers/' + item['Id'] + '/json', timeout=5)
        except Exception:
            return {'item': item, 'detail': {}, 'stats': {}, 'unavailable': True}
        result = {'item': item, 'detail': detail, 'stats': {}}
        if collect_stats and detail.get('State', {}).get('Running'):
            try:
                result['stats'] = docker('/containers/' + item['Id'] + '/stats?stream=false', timeout=5)
            except Exception:
                result['stats_unavailable'] = True
        return result
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        collected = list(pool.map(one, containers[:100]))
    issues.extend((value['item'].get('Names') or ['?'])[0] + ' 상세 수집 실패' for value in collected if value.get('unavailable'))
    issues.extend((value['item'].get('Names') or ['?'])[0] + ' 통계 미측정' for value in collected if value.get('stats_unavailable'))
    storage = {}
    if with_storage:
        try:
            storage = docker('/system/df?type=volume&verbose=1', timeout=20)
        except Exception:
            issues.append('Docker 볼륨 저장공간 수집 실패·시간 초과')
    return info, collected, storage, issues


def host_disks(issues):
    result, seen = [], set()
    for line in Path('/proc/mounts').read_text().splitlines():
        device, mount, filesystem = line.split()[:3]
        if not device.startswith('/dev/') or filesystem in ('squashfs', 'iso9660') or device in seen:
            continue
        seen.add(device)
        mount = mount.replace('\\040', ' ')
        try:
            stat = os.statvfs(mount)
            used = (stat.f_blocks - stat.f_bfree) * stat.f_frsize
            total, available = stat.f_blocks * stat.f_frsize, stat.f_bavail * stat.f_frsize
            percent = used / (used + available) * 100 if used + available else 0
            inode = (1 - stat.f_ffree / stat.f_files) * 100 if stat.f_files else 0
            result.append(f'{mount} {size(used)}/{size(total)} · 여유 {size(available)} · {percent:.0f}% · inode {inode:.0f}%')
            if percent >= 80 or inode >= 80:
                issues.append(f'{mount} 공간 {percent:.0f}%·inode {inode:.0f}% ({"주의·긴급 용량 점검" if max(percent,inode)>=90 else "주의"})')
        except OSError:
            result.append(mount + ' 확인 불가')
            issues.append(mount + ' 파일시스템 미측정')
    return '; '.join(result) or '확인 불가'


def cpu_sample():
    def read():
        values = list(map(int, Path('/proc/stat').read_text().splitlines()[0].split()[1:9]))
        return sum(values), values[3] + values[4]
    before, idle_before = read()
    time.sleep(0.2)  # Resource measurement interval, not a service-readiness delay.
    after, idle_after = read()
    return 100 * (1 - (idle_after - idle_before) / max(1, after - before))


def system_events(issues):
    failed = run(['systemctl', 'list-units', '--state=failed', '--no-legend', '--plain'])
    failures = len(failed.stdout.strip().splitlines()) if failed.returncode == 0 and failed.stdout.strip() else (0 if failed.returncode == 0 else '확인 불가')
    oom = run(['journalctl', '-k', '--since=-24h', '--grep=Out of memory: Killed process|oom-kill:', '--no-pager', '--output=cat', '--quiet'])
    oom_lines = '확인 불가' if oom.returncode not in (0, 1) or oom.stderr else len(oom.stdout.strip().splitlines()) if oom.stdout.strip() else 0
    if failures:
        issues.append(f'실패 서비스 {failures}개')
    if oom_lines:
        issues.append(f'최근 24h OOM 매칭 로그 {oom_lines}줄 (과거 기록·주의)')
    return failures, oom_lines


def server_report(target='edo1'):
    issues = []
    memory = {line.split(':')[0]: int(line.split()[1]) * 1024 for line in Path('/proc/meminfo').read_text().splitlines() if len(line.split()) >= 2}
    total, available = memory['MemTotal'], memory['MemAvailable']
    if (total - available) / total >= .8:
        issues.append('서버 RAM 사용률 80% 이상 (주의)')
    disks = host_disks(issues)
    failures, oom_lines = system_events(issues)
    try:
        info, containers, _, docker_issues = snapshot(target=target)
        issues.extend(docker_issues)
        running = sum(c['item']['State'] == 'running' for c in containers)
        docker_root = info.get('DockerRootDir')
        docker_space = '파일시스템 용량 확인 불가'
        if docker_root:
            try:
                stat = os.statvfs(docker_root)
                docker_space = f'공유 FS 전체 {size(stat.f_blocks * stat.f_frsize)} · 여유 {size(stat.f_bavail * stat.f_frsize)}'
            except OSError:
                issues.append('Docker 파일시스템 용량 확인 불가')
        docker_state = f"정상 · {info.get('ServerVersion', '?')} · 실행 {running}/전체 {info.get('Containers', '?')} · {docker_root or '?'} · {info.get('Driver', '?')} · {docker_space}"
        metrics = [resource_stats(c['stats']) for c in containers if c['item']['State'] == 'running']
        missing = sum(not metric['memory_available'] for metric in metrics)
        docker_ram = f"{size(sum(metric['memory'] for metric in metrics))} / 인식 {size(info.get('MemTotal'))}"
        if missing:
            docker_ram += f' · {missing}개 확인 불가'
            issues.append(f'컨테이너 RAM {missing}개 수집 불가: 합계는 일부 사용량')
    except Exception:
        docker_state = docker_ram = disk = reclaimable = '확인 불가'
        issues.append('Docker 데몬·자원 수집 실패')
    else:
        disk, reclaimable = disk_usage_summary(issues)
    operating_system = next((line.split('=', 1)[1].strip('"') for line in Path('/etc/os-release').read_text().splitlines() if line.startswith('PRETTY_NAME=')), 'Linux')
    row = [socket.gethostname() + ' / ' + operating_system + ' / ' + os.uname().release + ' / ' + duration(float(Path('/proc/uptime').read_text().split()[0])),
           f'{cpu_sample():.1f}% / {os.cpu_count()}코어 · Load ' + '/'.join(f'{x:.2f}' for x in os.getloadavg()),
           f'{size(total-available)}/{size(total)} · 가용 {size(available)}',
           f"{size(memory['SwapTotal']-memory['SwapFree'])}/{size(memory['SwapTotal'])}",
           disks, docker_state, docker_ram, disk, reclaimable, f'{failures}개 / OOM 매칭 {oom_lines}줄']
    facts = {'ram_total': total, 'ram_available': available, 'cpu_cores': os.cpu_count(),
             'load': list(os.getloadavg()), 'failed_units': failures, 'oom_log_lines': oom_lines}
    return ['서버·OS·커널·가동', 'CPU 사용/코어·Load 1·5·15분', 'RAM 사용/전체·가용', 'Swap 사용/전체',
            '디스크 사용/전체·여유·inode', 'Docker/컨테이너', 'Docker RAM 합/인식',
            '이미지/쓰기/볼륨/캐시', '회수 가능량(Docker)', '시스템 실패/OOM 24h'], [row], issues, facts


def mounted_storage(mounts, volumes, measurements=None, *, name=None):
    result = []
    for mount in mounts[:12]:
        if mount['Type'] == 'volume':
            volume = volumes.get(mount.get('Name'), {})
            usage = volume.get('UsageData') or {}
            result.append(f"볼륨 {mount.get('Name', '?')}: {size(usage.get('Size'))}" + (' (공유)' if usage.get('RefCount', 0) > 1 else ''))
        elif mount['Type'] == 'bind':
            path, destination = mount['Source'], mount['Destination']
            if (name == 'eolssu-monitoring-node-exporter-1' and path == '/'
                    and destination == '/host' and mount.get('RW') is False):
                result.append('bind /host: 호스트 관측용 (서버 파일시스템 지표 참고)')
                continue
            if measurements is not None and path in measurements:
                amount = measurements[path]
            else:
                remaining = measurements['deadline'] - time.monotonic() if measurements is not None else 12
                amount = bind_usage(path, remaining)
                if measurements is not None:
                    measurements[path] = amount
            result.append('bind ' + destination + ': ' + amount)
        else:
            result.append(mount['Type'] + ': ' + mount['Destination'])
    if len(mounts) > 12:
        result.append('일부 mount 생략')
    return '; '.join(result) or '없음'


def bind_usage(path, remaining):
    if remaining <= 0:
        return '확인 불가 (전체 25초 측정 예산 소진)'
    try:
        measured = run(['nice', '-n', '10', 'du', '-sx', '--block-size=1', '--', path], timeout=min(12, remaining))
        if measured.returncode != 0:
            return '확인 불가 (저장소 읽기 실패)'
        return size(int(measured.stdout.split()[0]))
    except subprocess.TimeoutExpired:
        reason = '경로당 12초 한도' if remaining >= 12 else '전체 25초 측정 예산 소진'
        return f'확인 불가 ({reason})'
    except OSError:
        return '확인 불가 (저장소 읽기 실패)'
    except (IndexError, ValueError):
        return '확인 불가 (측정값 해석 실패)'


def syslog_usage(tag):
    directory = EOLSSU_SYSLOG_DIR
    pattern = re.compile(re.escape(tag) + r'\.[0-9]{4}-[0-9]{2}-[0-9]{2}\.log')
    count = total = 0
    try:
        if directory.resolve(strict=True) != directory:
            return '미측정 (syslog 로그 경로 불일치)'
        with os.scandir(directory) as files:
            for index, file in enumerate(files, 1):
                if index > 1024:
                    return '미측정 (syslog 탐색 한도 초과)'
                if not pattern.fullmatch(file.name):
                    continue
                metadata = file.stat(follow_symlinks=False)
                if stat.S_ISREG(metadata.st_mode):
                    count += 1
                    total += metadata.st_size
                    if count > 100:
                        return '미측정 (syslog 파일 수 한도 초과)'
    except (OSError, RuntimeError):
        return '미측정 (syslog 파일 접근 실패)'
    if not count:
        return '미측정 (syslog 파일 없음)'
    label = 'API 공용 로그' if tag == 'eolssu-backend' else '일별 보관분 포함'
    return f'{size(total)} (syslog, {label})'


def log_usage(detail, docker_root, target='edo1'):
    log_config = detail['HostConfig'].get('LogConfig', {})
    driver = log_config.get('Type', '?')
    if driver == 'syslog' and target == 'edo1':
        options = log_config.get('Config') or {}
        tag = options.get('tag')
        if options.get('syslog-address') == 'tcp://127.0.0.1:1514' and tag in EOLSSU_SYSLOG_TAGS:
            return syslog_usage(tag)
    if driver == 'none':
        return '저장 안 함 (none)'
    path = detail.get('LogPath')
    try:
        if path:
            log_path = Path(path)
            candidates = list(log_path.parent.glob(log_path.name + '*'))
        elif driver == 'local' and re.fullmatch(r'[a-f0-9]{64}', detail.get('Id', '')):
            folder = Path(docker_root)/'containers'/detail['Id']/'local-logs'
            if not folder.is_dir() or folder.is_symlink():
                return '미측정 (local 로그 경로 없음)'
            candidates = list(folder.glob('container.log*'))
        else:
            return f'미측정 ({driver} 저장소)'
        if len(candidates) > 100 or not candidates:
            return f'미측정 ({driver} 파일 없음·한도 초과)'
        total = sum(file.lstat().st_size for file in candidates if not file.is_symlink() and file.is_file())
        return f'{size(total)} ({driver}, 회전 포함)'
    except OSError:
        return f'미측정 ({driver} 파일 접근 실패)'


def has_port_binding(detail, container_port, host, port):
    ports = detail.get('NetworkSettings', {}).get('Ports') or {}
    return {'HostIp': host, 'HostPort': str(port)} in (ports.get(container_port) or [])


def mysql_proxy_readiness(detail):
    if not has_port_binding(detail, '3306/tcp', '127.0.0.1', 13306):
        return '확인 불가', 'MySQL 프록시 loopback 포트 매핑 확인 불가', '—'
    started = time.monotonic()
    # Opening and dropping a pre-auth connection can count against the shared MySQL proxy host.
    try:
        listener = run(['/usr/bin/ss', '-H', '-lnt4', '( src 127.0.0.1 and sport = :13306 )'], timeout=2)
    except (OSError, subprocess.TimeoutExpired):
        return '확인 불가', 'MySQL 프록시 로컬 수신 상태 수집 불가', '—'
    elapsed = f'{(time.monotonic()-started)*1000:.0f}ms'
    if listener.returncode != 0 or not listener.stdout.strip():
        return '확인 불가', 'MySQL 프록시 로컬 수신 소켓 확인 불가', elapsed
    return '미검증', 'ss로 127.0.0.1:13306 수신 확인 · MySQL 전달·복제 미검증', elapsed


def readiness_response(name, status, content_type, cors_origin, body):
    mismatch = '실패', f'HTTP {status} · 기대한 서비스 응답 불일치'
    if name == PHPMYADMIN:
        markers = (b'id="login_form"', b'name="pma_username"', b'name="pma_password"')
        if status == 200 and content_type == 'text/html' and all(marker in body for marker in markers):
            return '정상', 'GET / HTTP 200 · phpMyAdmin 로그인 양식 · MySQL 인증 미검증'
        return mismatch

    try:
        payload = json.loads(body)
    except ValueError:
        return mismatch
    if not isinstance(payload, dict):
        return mismatch

    if name == REVIEW_TUNNEL:
        connections = payload.get('readyConnections')
        if type(connections) is int and payload.get('status') == status:
            if status == 200 and connections > 0:
                return '정상', f'GET /ready HTTP 200 · Cloudflare 엣지 연결 {connections}개 · 웹훅 미검증'
            if status == 503 and connections == 0:
                return '실패', 'GET /ready HTTP 503 · Cloudflare 엣지 연결 0개'
    elif name == ANALYSIS_AI and status == 422 and content_type == 'application/json':
        errors = payload.get('errors')
        if isinstance(errors, list) and all(
            {'loc': ['body', field], 'type': 'missing'} in errors for field in ('audioUrl', 'notes')
        ):
            return '미검증', 'POST /v1/analyses HTTP 422 · 필수 입력 검증 · 실제 음원 처리·추론 미검증'
    elif (name in (BLOG_LOGIN, BLOG_WRITABLE) and status == 200
          and content_type == 'application/json' and cors_origin == BLOG_ORIGIN
          and payload.get('authorize') == 'https://github.com/login/oauth/authorize'
          and isinstance(payload.get('clientId'), str) and payload['clientId'].strip()
          and payload.get('redirectUri') == BLOG_ORIGIN + '/edit/'
          and (name == BLOG_LOGIN or payload.get('canBatch') is True)):
        checked = 'OAuth·일괄 반영 설정' if name == BLOG_WRITABLE else '내부 OAuth 설정'
        return '정상', f'GET /config HTTP 200 · {checked}·CORS 확인 · 실제 로그인 미검증'
    return mismatch

def edo1_service_readiness(name, detail):
    if name not in EDO1_READINESS_ACTIONS:
        return None
    state = detail.get('State', {})
    if (state.get('Running') is not True or state.get('Status') != 'running'
            or state.get('OOMKilled') or state.get('Health', {}).get('Status') not in (None, 'healthy')):
        return None
    if detail.get('Name') != '/' + name:
        return '확인 불가', 'Docker inspect 대상 이름 확인 불가', '—'
    if name == MYSQL_PROXY:
        return mysql_proxy_readiness(detail)
    if name in EDO1_PUBLISHED_PROBES:
        container_port, host, port, path = EDO1_PUBLISHED_PROBES[name]
        if not has_port_binding(detail, container_port, host, port):
            return '확인 불가', '기대한 Docker 포트 매핑 확인 불가', '—'
    else:
        network, port, path = EDO1_BRIDGE_PROBES[name]
        networks = detail.get('NetworkSettings', {}).get('Networks') or {}
        host = (networks.get(network) or {}).get('IPAddress', '')
        try:
            if IPv4Address(host) not in DOCKER_BRIDGE_RANGE:
                return '확인 불가', '기대한 Docker 내부 주소 확인 불가', '—'
        except ValueError:
            return '확인 불가', '기대한 Docker 내부 주소 확인 불가', '—'

    method, body, headers = 'GET', None, {}
    if name == ANALYSIS_AI:
        method, body, headers = 'POST', b'{}', {'Content-Type': 'application/json'}
    elif name in (BLOG_LOGIN, BLOG_WRITABLE):
        headers = {'Origin': BLOG_ORIGIN}

    started = time.monotonic()
    connection = http.client.HTTPConnection(host, port, timeout=2)
    response = None
    try:
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        expected_status = 422 if name == ANALYSIS_AI else 200
        tunnel_unready = name == REVIEW_TUNNEL and response.status == 503
        if response.status != expected_status and not tunnel_unready:
            elapsed = f'{(time.monotonic()-started)*1000:.0f}ms'
            return '실패', f'{method} {path} HTTP {response.status} · 기대 HTTP {expected_status} 불일치', elapsed
        limit = 32768 if name == PHPMYADMIN else 4096
        payload = response.read(limit + 1)
        content_type = response.getheader('Content-Type', '').split(';', 1)[0].strip().lower()
        elapsed = f'{(time.monotonic()-started)*1000:.0f}ms'
        if len(payload) > limit:
            verdict = '실패' if tunnel_unready else '확인 불가'
            return verdict, f'HTTP {response.status} · 응답 검사 크기 한도 초과', elapsed
        return (*readiness_response(name, response.status, content_type,
                                    response.getheader('Access-Control-Allow-Origin'), payload), elapsed)
    except PermissionError:
        return '확인 불가', '내부 HTTP 검사 권한 확인 필요', '—'
    except (OSError, http.client.HTTPException):
        elapsed = f'{(time.monotonic()-started)*1000:.0f}ms'
        if name == REVIEW_TUNNEL and response is None:
            # Cloudflared can choose a different metrics port when its default is occupied.
            return '확인 불가', 'Cloudflared 기본 포트 /ready 응답 확인 불가', elapsed
        return '실패', f'{method} {path} HTTP 응답 확인 실패', elapsed
    finally:
        connection.close()


def container_readiness(target, name, detail):
    if target == 'edo1':
        return edo1_service_readiness(name, detail)
    return standby_web_readiness(target, name, detail)


def standby_web_readiness(target, name, detail):
    state = detail.get('State', {})
    if (target != 'edo2' or name != STANDBY_WEB or detail.get('Name') != '/' + name
            or state.get('Running') is not True or state.get('Status') != 'running'
            or state.get('OOMKilled') or state.get('Health', {}).get('Status')):
        return None
    if not has_port_binding(detail, '8443/tcp', '127.0.0.1', 18443):
        return '확인 불가', '대기 웹 loopback 포트 매핑 확인 불가', '—'

    started = time.monotonic()
    try:
        response = run(['curl', '--disable', '--silent', '--show-error', '--noproxy', '*',
                        '--resolve', 'localhost:18443:127.0.0.1', '--max-time', '3',
                        '--cacert', '/opt/eolssu-standby-579/tls/localhost.crt',
                        '--output', '/dev/null', '--write-out', '%{http_code} %{content_type}',
                        'https://localhost:18443/index.html'], timeout=4)
    except OSError:
        return '확인 불가', 'HTTPS 검사 실행 불가', '—'
    except subprocess.TimeoutExpired:
        return '실패', '대기 웹 HTTPS 응답 시간 초과', '—'
    elapsed = f'{(time.monotonic()-started)*1000:.0f}ms'
    if response.returncode == 77:
        return '확인 불가', '대기 웹 신뢰 인증서 읽기 실패', elapsed
    if response.returncode != 0:
        return '실패', f'HTTPS 연결·인증서 검사 실패 (curl {response.returncode})', elapsed
    status, _, content_type = response.stdout.strip().partition(' ')
    if status == '200' and content_type.split(';', 1)[0].strip().lower() == 'text/html':
        return '정상', '인증서 검증 · /index.html HTTP 200', elapsed
    code = status if re.fullmatch(r'[1-5][0-9]{2}', status) else '확인 불가'
    return '실패', f'/index.html HTTP {code} · 기대한 HTML 200 응답 불일치', elapsed


def cgroup_block_io(detail):
    state = detail.get('State', {})
    container_id, pid = detail.get('Id', ''), state.get('Pid')
    if (not state.get('Running') or type(pid) is not int or pid <= 0
            or not isinstance(container_id, str) or not re.fullmatch(r'[a-f0-9]{64}', container_id)):
        return None
    try:
        membership = (PROC_ROOT / str(pid) / 'cgroup').read_text(encoding='ascii').splitlines()
        supported = {f'0::/system.slice/docker-{container_id}.scope', f'0::/docker/{container_id}'}
        match = next((line for line in membership if line in supported), None)
        if match is None:
            return None
        path = CGROUP_ROOT / match.removeprefix('0::/') / 'io.stat'
        with path.open(encoding='ascii') as source:
            contents = source.read(16385)
        if len(contents) > 16384:
            return None
        if not contents.strip():
            return '기록 없음'
        read = written = 0
        for line in contents.splitlines():
            device, *fields = line.split()
            values = dict(field.split('=', 1) for field in fields)
            if not re.fullmatch(r'[0-9]+:[0-9]+', device) or any(
                    not re.fullmatch(r'[0-9]{1,20}', values.get(key, '')) for key in ('rbytes', 'wbytes')):
                return None
            read += int(values['rbytes'])
            written += int(values['wbytes'])
        return f'{size(read)}/{size(written)}'
    except (OSError, UnicodeError, ValueError):
        return None


def block_io_usage(stats, detail):
    counters = (stats.get('blkio_stats') or {}).get('io_service_bytes_recursive')
    if counters is None:
        return cgroup_block_io(detail) or '미측정 (블록 I/O 통계 없음)'
    read = sum(entry['value'] for entry in counters if entry['op'].lower() == 'read')
    written = sum(entry['value'] for entry in counters if entry['op'].lower() == 'write')
    return f'{size(read)}/{size(written)}'


def is_retained_blog_rollback(target, name, detail):
    state = detail.get('State', {})
    return (target == 'edo1' and BLOG_EDITOR_ROLLBACK_NAME.fullmatch(name) is not None
            and detail.get('Name') == '/' + name
            and state.get('Status') == 'exited' and state.get('Running') is False
            and state.get('ExitCode') == 0 and state.get('OOMKilled') is False
            and state.get('Error') == '')


def container_report(target='edo1'):
    info, containers, storage, issues = snapshot(True, target=target)
    volume_items = (storage.get('VolumeUsage') or {}).get('Items', storage.get('Volumes'))
    volumes = {v['Name']: v for v in volume_items or []}
    rows, facts, readiness_inputs = [], [], []
    measurements = {'deadline': time.monotonic() + 25}
    for value in containers:
        item, detail, stats = value['item'], value['detail'], value['stats']
        name = detail.get('Name', (item.get('Names') or ['?'])[0]).lstrip('/')
        if value.get('unavailable'):
            rows.append([name, item['State'] + '/확인 불가'] + ['확인 불가'] * 9)
            issues.append(name + ' 상세 수집 실패')
            continue
        state, host, config = detail['State'], detail['HostConfig'], detail['Config']
        health = state.get('Health', {}).get('Status', '미설정')
        retained = is_retained_blog_rollback(target, (item.get('Names') or ['?'])[0].lstrip('/'), detail)
        if not retained and (health == 'unhealthy' or not state.get('Running') or state.get('OOMKilled')):
            issues.append(name + ': ' + state['Status'] + '/' + health)
        metrics = resource_stats(stats)
        limit = host.get('Memory', 0)
        percent = f'{metrics["memory"] / limit * 100:.0f}%' if limit and metrics['memory_available'] else '—'
        if limit and metrics['memory_available'] and metrics['memory'] / limit >= .8:
            issues.append(name + ' 메모리 제한 대비 80% 이상 (주의)')
        if not limit:
            issues.append(name + ' 메모리 제한 미설정')
        cpus = host.get('NanoCpus', 0) / 1e9 or (host.get('CpuQuota', 0) / (host.get('CpuPeriod') or 100000) if host.get('CpuQuota', 0) > 0 else 0)
        cpu_limit = f'{cpus:g}코어' if cpus else '미설정'
        if host.get('CpusetCpus'):
            cpu_limit += ' · CPU집합 ' + host['CpusetCpus']
        log = log_usage(detail, info.get('DockerRootDir', '/var/lib/docker'), target)
        if '미측정' in log:
            issues.append(name + ' 로그 ' + log)
        networks = stats.get('networks')
        if networks is None:
            net = '미측정 (네트워크 통계 없음)'
        else:
            net = f"{size(sum(v.get('rx_bytes', 0) for v in networks.values()))}/{size(sum(v.get('tx_bytes', 0) for v in networks.values()))}"
        block = block_io_usage(stats, detail)
        ports = ','.join(f"{p.get('IP', '')}:{p['PublicPort']}→{p['PrivatePort']}" for p in item.get('Ports', []) if 'PublicPort' in p) or '없음'
        try:
            started = datetime.fromisoformat(state['StartedAt'].replace('Z', '+00:00'))
            uptime = duration((datetime.now(timezone.utc) - started).total_seconds()) if state['Running'] else '중지'
        except ValueError:
            uptime = '확인 불가'
        cpu = f'{metrics["cpu"]:.1f}%' if metrics['cpu_available'] else '확인 불가'
        ram = size(metrics['memory']) if metrics['memory_available'] else '확인 불가'
        disk_limit = (host.get('StorageOpt') or {}).get('size', '미설정(호스트 공유)')
        if retained:
            cpu = ram = net = block = '해당 없음'
        pids = '해당 없음' if retained else str(stats.get('pids_stats', {}).get('current', '확인 불가'))
        mounted = mounted_storage(detail.get('Mounts', []), volumes, measurements, name=name)
        if '확인 불가' in mounted:
            issues.append(name + ' 데이터 용량 미측정')
        fact = {'name': name, 'running': state.get('Running'), 'health': health,
                'ram_bytes': metrics['memory'] if metrics['memory_available'] else None,
                'ram_limit': limit, 'host_ram': info.get('MemTotal'), 'cpu_limit': cpus,
                'cpu_percent': metrics['cpu'] if metrics['cpu_available'] else None,
                'log': log, 'mounts': mounted, 'rollback_retained': retained}
        facts.append(fact)
        readiness_inputs.append((fact, detail))
        rows.append([name, state['Status'] + '/' + health, f'{cpu}/{cpu_limit}',
                     f'{ram}/{size(limit) if limit else "미설정"} · {percent}',
                     size(item.get('SizeRw')) + '/' + disk_limit + ' · 로그 ' + log, mounted,
                     uptime + f'/{detail.get("RestartCount", 0)}회', f'{state.get("ExitCode", "?")}/{"예" if state.get("OOMKilled") else "아니오"}',
                     pids + '/' + ports,
                     net + ' · IO ' + block, config.get('Image', '?')])
    # Readiness timeouts must not consume the shared bind-mount measurement budget.
    for fact, detail in readiness_inputs:
        fact['readiness'] = container_readiness(target, fact['name'], detail)
    return ['컨테이너', '실행/Health', 'CPU 사용/제한', 'RAM 사용/제한·비율', '쓰기 사용/제한·로그(회전 포함)',
            '데이터 저장소/사용량', '가동/재시작', '종료 코드/OOM', 'PID/공개 포트', 'NET 수신/송신·IO 읽기/쓰기(누적)', '이미지'], rows, issues, facts


def systemd_health(unit, kind='service', target='edo2'):
    properties = 'LoadState,ActiveState,SubState,Result,ExecMainStatus,ExecMainStartTimestamp,LastTriggerUSec,NextElapseUSecRealtime'
    try:
        result = run(['systemctl', 'show', unit, '--property=' + properties])
        state = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
        if result.returncode:
            return '미측정', 'systemd 상태 조회 실패', f'{target}에서 systemctl show {unit} 재확인'
        if state.get('LoadState') != 'loaded':
            return '실패', '필수 unit 미등록', f'{target}에서 systemctl cat {unit}와 설치 상태 확인'
        active = state.get('ActiveState', '?')
        if kind == 'oneshot':
            started = state.get('ExecMainStartTimestamp')
            if active in ('activating', 'active'):
                return '미검증', '이번 작업 실행 중', f'완료 후 /{target}-health 재실행; journalctl -u {unit}에서 이번 결과 확인'
            if not started:
                return '미검증', 'systemd 실행 이력 없음', f'{unit}의 수동/예약 실행 완료 기록을 확인; 이 점검은 작업을 실행하지 않음'
            good = state.get('Result') == 'success' and state.get('ExecMainStatus') == '0'
            return ('정상' if good else '실패', f"최근 시작 {started} · 결과 {state.get('Result', '?')} · 종료 {state.get('ExecMainStatus', '?')}",
                    f'journalctl -u {unit}에서 최근 실행의 실패 원인 확인')
        good = active == 'active' and (kind == 'timer' or state.get('SubState') == 'running')
        evidence = active + '/' + state.get('SubState', '?')
        if kind == 'timer':
            evidence += ' · 다음 ' + (state.get('NextElapseUSecRealtime') or '확인 불가')
            good = good and bool(state.get('NextElapseUSecRealtime'))
        return '정상' if good else '실패', evidence, f'systemctl status {unit}와 journalctl -u {unit}에서 비활성/실패 원인 확인'
    except (OSError, subprocess.TimeoutExpired, ValueError):
        return '미측정', 'systemd 수집 실패·시간 초과', f'{target}에서 systemctl show {unit} 재확인'


def edo2_host_health():
    rows = []
    units = [('Nginx', 'nginx.service', 'service'), ('점검 중계', 'edo-check.service', 'service'),
             ('Docker 엔진 서비스', 'docker.service', 'service'), ('SSH', 'ssh.service', 'service'),
             ('인증서 갱신 예약', 'snap.certbot.renew.timer', 'timer'),
             ('백업 상태 확인 예약', 'eolssu-backup-status.timer', 'timer'),
             ('백업 보관 정리 예약', 'eolssu-backup-prune.timer', 'timer'),
             ('백업 복구 검증 예약', 'eolssu-backup-restore-test.timer', 'timer'),
             ('백업 상태 최근 실행', 'eolssu-backup-status.service', 'oneshot'),
             ('백업 복구 검증 최근 실행', 'eolssu-backup-restore-test.service', 'oneshot')]
    for name, unit, kind in units:
        status, evidence, action = systemd_health(unit, kind)
        rows.append([name, 'edo2 systemd', status, evidence, '—', action])
    for name, command, good_text, action in (
            ('Nginx 구성', ['nginx', '-t'], None, 'edo2에서 nginx -t의 실패 위치·인증서 경로 확인'),
            ('UFW', ['ufw', 'status'], 'Status: active', 'edo2에서 ufw status verbose로 방화벽 활성 상태 확인')):
        try:
            result = run(command)
            good = result.returncode == 0 and (good_text is None or good_text in result.stdout)
            rows.append([name, 'edo2 구성 검사', '정상' if good else '실패',
                         '구성 검사 통과' if name == 'Nginx 구성' and good else '방화벽 active' if good else '검사 실패 또는 비활성', '—', action])
        except (OSError, subprocess.TimeoutExpired):
            rows.append([name, 'edo2 구성 검사', '미측정', '검사 실행 실패·시간 초과', '—', action])
    return rows


BACKUP_KINDS = ('db', 'config', 'jenkins', 'grafana', 'controller-key')


def age_seconds(value):
    timestamp = datetime.fromisoformat(value.replace('Z', '+00:00'))
    if timestamp.tzinfo is None:
        raise ValueError('timezone required')
    age = (datetime.now(timezone.utc) - timestamp).total_seconds()
    if age < -60:
        raise ValueError('future timestamp')
    return max(0, age)


def scrape_state(targets, job):
    matched = [item for item in targets if item.get('labels', {}).get('job') == job]
    if not matched:
        return '실패', '필수 수집 대상 누락'
    if any(item.get('health') != 'up' for item in matched):
        return '실패', '수집 대상 down · Prometheus 수집 오류 확인'
    try:
        age = max(age_seconds(item['lastScrape']) for item in matched)
    except (KeyError, TypeError, ValueError, AttributeError):
        return '미측정', '최근 수집 시각 확인 불가'
    return ('주의' if age > 90 else '정상'), f'up · 최근 수집 {age:.0f}초 전 · 기준 90초 이내'


def prometheus_health():
    rows = []
    for name, path in [('Prometheus 준비', '/-/ready'), ('수집 대상', '/api/v1/targets')]:
        connection = http.client.HTTPConnection('127.0.0.1', 3001, timeout=4)
        action = 'edo1의 127.0.0.1:3001' + path + ' 응답과 Prometheus 수집 설정·대상 서비스 상태 확인'
        try:
            connection.request('GET', path)
            response = connection.getresponse()
            body = response.read(65537)
            if response.status != 200:
                raise OSError('HTTP failed')
            if len(body) > 65536:
                raise ValueError('response too large')
            if name == 'Prometheus 준비':
                good = body.strip() == b'Prometheus Server is Ready.'
                rows.append([name, 'edo1 모니터링 HTTP', '정상' if good else '미검증', 'HTTP 200 · 준비 응답 확인' if good else 'HTTP 200 · 기대한 준비 응답 아님', '—', action])
            else:
                data = json.loads(body)
                if data.get('status') != 'success' or not isinstance(data.get('data', {}).get('activeTargets'), list):
                    raise ValueError('unexpected targets')
                for job in ('node', 'eolssu-backend'):
                    status, evidence = scrape_state(data['data']['activeTargets'], job)
                    rows.append([job + ' 지표 수집', 'edo1 모니터링 HTTP', status, evidence, '—', action])
        except (ValueError, TypeError, KeyError, AttributeError):
            rows.append([name, 'edo1 모니터링 HTTP', '미측정', '기대한 응답 형식·수집 시각 확인 불가', '—', action])
        except (OSError, http.client.HTTPException):
            rows.append([name, 'edo1 모니터링 HTTP', '실패', 'HTTP 준비·수집 응답 실패', '—', action])
        finally:
            connection.close()
    return rows


def read_record(path):
    if path.is_symlink() or path.stat().st_size > 65536:
        raise ValueError('invalid metadata file')
    data = json.loads(path.read_text())
    if not isinstance(data, dict):
        raise ValueError('invalid metadata object')
    return data


def backup_health(store=Path('/var/lib/eolssu-backups'), keys=Path('/var/lib/eolssu-backup-keys'), state=Path('/var/lib/eolssu-backup')):
    rows = []
    for kind in BACKUP_KINDS:
        directory = keys/'controller'/kind if kind == 'controller-key' else store/kind
        action = 'edo1 해당 백업 timer·최근 전송 결과 확인; edo2 백업 메타데이터와 암호문 존재 확인 (무결성·복원 검사는 별도)'
        try:
            records = sorted(path for path in directory.glob('*.json') if re.fullmatch(r'\d{8}T\d{6}Z-[a-f0-9]{12}\.json', path.name))
            if not records:
                raise FileNotFoundError()
            path = records[-1]
            age = age_seconds(read_record(path)['snapshot_at'])
            encrypted = path.with_suffix('.cms')
            if encrypted.is_symlink() or not encrypted.is_file() or encrypted.stat().st_size == 0:
                status, evidence = '실패', '완료 메타데이터의 암호문 없음·빈 파일'
            else:
                limit = 5400 if kind == 'db' else 27*3600
                status = '주의' if age > limit else '정상'
                evidence = f'스냅샷 {age/3600:.1f}시간 전 / 기준 {limit/3600:g}시간 · 암호문 존재 (무결성 미검증)'
        except (OSError, ValueError, KeyError, TypeError, AttributeError):
            status, evidence = '미측정', '완료 백업 메타데이터·시각 확인 불가'
        rows.append([kind + ' 백업 최신성', 'edo2 백업 메타데이터', status, evidence, '—', action])
    action = 'edo2 journalctl -u eolssu-backup-restore-test.service와 restore-last.json의 실패 단계 확인; 이 점검은 복구 작업을 실행하지 않음'
    try:
        record = read_record(state/'restore-last.json')
        age = age_seconds(record['finished_at'])
        status = ('실패' if record.get('status') == 'failed' or record.get('cleanup_errors') else
                  '주의' if record.get('status') == 'passed' and age > 8*86400 else
                  '정상' if record.get('status') == 'passed' else '미검증')
        evidence = f'최근 완료 {age/3600:.1f}시간 전 · ' + ('복구 검증 통과' if record.get('status') == 'passed' else '복구 검증 실패' if record.get('status') == 'failed' else '결과 미확정')
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        status, evidence = '미측정', '복구 검증 결과·완료 시각 확인 불가'
    rows.append(['실제 복구 검증 결과', 'edo2 백업 메타데이터', status, evidence, '—', action])
    return rows


def edo1_host_health():
    rows = []
    units = [('Docker 엔진 서비스', 'docker.service', 'service'), ('SSH', 'ssh.service', 'service'), ('인증서 갱신 예약', 'certbot.timer', 'timer')]
    for kind in BACKUP_KINDS:
        units.extend([(kind + ' 백업 예약', 'eolssu-backup-' + kind + '.timer', 'timer'),
                      (kind + ' 백업 최근 실행', 'eolssu-backup@' + kind + '.service', 'oneshot')])
    for name, unit, kind in units:
        status, evidence, action = systemd_health(unit, kind, target='edo1')
        rows.append([name, 'edo1 systemd', status, evidence, '—', action])
    return rows


def health_report(target='edo1'):
    rows = edo2_host_health() + backup_health() if target == 'edo2' else edo1_host_health() + prometheus_health()
    issues = []
    slot = configured_backend_slot(target, issues)
    required = required_containers(target, slot)
    try:
        info, containers, _, docker_issues = snapshot(collect_stats=False, target=target, required=required)
        issues.extend(docker_issues)
        if target == 'edo2':
            rows.append(['Docker 조회', 'edo2 Docker', '정상', f"엔진 응답 · 등록 {len(containers)}개 · {info.get('ServerVersion', '?')}", '—', '현재 조치 없음'])
    except Exception:
        if target == 'edo1':
            raise
        containers = []
        rows.append(['Docker 조회', 'edo2 Docker', '확인 불가', '기대한 Docker 엔진에 연결하지 못함', '—', 'docker.service와 Docker socket 조회 권한 확인'])
    names = {name.lstrip('/') for value in containers for name in value['item'].get('Names', [])}
    rows.extend([name, target + ' Docker', '실패', '필수 컨테이너 누락', '—', '실행 구성 확인 필요'] for name in sorted(required - names))
    for value in containers:
        item, detail = value['item'], value['detail']
        state = detail.get('State', {})
        name = (item.get('Names') or ['?'])[0].lstrip('/')
        if not value.get('unavailable') and is_retained_blog_rollback(target, name, detail):
            continue
        health = state.get('Health', {}).get('Status')
        observed_state = state.get('Status', item['State'])
        running = state.get('Running', item['State'] == 'running') and observed_state == 'running'
        status = ('실패' if not running or health == 'unhealthy' else
                  '정상' if health == 'healthy' else '미검증')
        if value.get('unavailable'):
            status = '확인 불가'
        evidence = ('중지·일시정지: ' + observed_state) if not running else (health or '실행 중 · 건강 검사 없음')
        action = ('실행 상태·서비스 로그 확인' if status == '실패' else
                  '현재 조치 없음' if status == '정상' else '실행만 확인됨; 서비스 준비 상태 검사 필요')
        if name == ANALYSIS_AI and status == '정상':
            status, evidence = '미검증', 'TCP 연결 검사 통과 · 분석 HTTP 준비·추론 기능 미검증'
            action = 'AI 서비스의 분석 준비 검증 경로·검증 절차 확인; TCP 통과만으로 추론 성공을 확정하지 않음'
        readiness = container_readiness(target, name, detail) if status in ('정상', '미검증') else None
        if readiness:
            location = EDO1_READINESS_LOCATION if target == 'edo1' else STANDBY_WEB_LOCATION
            next_action = EDO1_READINESS_ACTIONS[name] if target == 'edo1' else STANDBY_WEB_ACTION
            rows.append([name, location, *readiness, next_action])
        else:
            rows.append([name, 'Docker healthcheck', status, evidence, '—', action])
    probes = [('Grafana', 3000, '/api/health'), ('n8n', 5688, '/healthz/readiness')] if target == 'edo1' else [('중계 내부 HTTP', 8765, '/healthz')]
    for name, port, path in probes:
        if name == 'n8n' and 'hey-edo1-n8n-1' not in names:
            continue
        started = time.monotonic()
        connection = http.client.HTTPConnection('127.0.0.1', port, timeout=4)
        try:
            connection.request('GET', path)
            response = connection.getresponse()
            body = json.loads(response.read(4096))
            good = response.status == 200 and (body.get('database') == 'ok' if name == 'Grafana' else body.get('status') == 'ok')
            checked = '준비 상태·DB 응답 확인' if target == 'edo1' else 'JSON status=ok · 중계 프로세스 응답'
            rows.append([name, target + ' loopback HTTP', '정상' if good else '실패', f'HTTP {response.status} · {checked if good else "기대한 상태 불일치"}',
                         f'{(time.monotonic()-started)*1000:.0f}ms', '준비 상태·DB 응답 확인' if target == 'edo1' else 'edo-check.service와 127.0.0.1:8765/healthz 응답 확인'])
        except Exception:
            rows.append([name, target + ' loopback HTTP', '실패', '응답 확인 불가', '—', path])
        finally:
            connection.close()
    facts = {'backend_slot': slot} if target == 'edo1' else None
    return ['대상', '위치/방식', '판정', '응답/측정값', '소요', '상세'], rows, issues, facts


def main(mode, target='edo1'):
    if target not in REQUIRED_BY_TARGET:
        raise SystemExit('unsupported target')
    started_at = datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d %H:%M:%S KST')
    started = time.monotonic()
    function = {'server': server_report, 'containers': container_report, 'health': health_report}.get(mode)
    if function is None:
        raise SystemExit('unsupported mode')
    try:
        report = function(target=target)
        columns, rows, issues = report[:3]
        facts = report[3] if len(report) > 3 else None
    except Exception:
        if mode == 'health':
            columns, rows = ['대상', '위치/방식', '판정', '응답/측정값', '소요', '상세'], [[target, 'SSH 수집', '확인 불가', '수집 실패·시간 초과', '—', '운영자 확인 필요']]
        else:
            columns, rows = ['대상', '결과'], [[target, '수집 실패·확인 불가']]
        issues = ['수집 실패·시간 초과; 운영자 확인 필요']
        facts = None
    return {'target': target, 'mode': mode, 'columns': columns, 'rows': rows, 'issues': issues, 'facts': facts,
            'started_at': started_at, 'elapsed_seconds': round(time.monotonic()-started, 2),
            'captured_at': datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d %H:%M:%S KST')}


if __name__ == '__main__':
    if len(sys.argv) not in (2, 3):
        raise SystemExit('one fixed mode required')
    print(json.dumps(main(sys.argv[1], sys.argv[2] if len(sys.argv) == 3 else 'edo1'), ensure_ascii=False))
