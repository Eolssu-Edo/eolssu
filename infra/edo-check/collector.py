#!/usr/bin/python3
"""Root-owned, read-only edo1 collector; only three fixed modes are accepted."""
import concurrent.futures
from datetime import datetime, timedelta, timezone
import http.client
import json
import os
import re
from pathlib import Path
import socket
import subprocess
import sys
import time

REQUIRED_CONTAINERS = {'eolssu-runtime-frontend-1', 'eolssu-runtime-backend-1', 'eolssu-runtime-mysql-1'}


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
    return data.get('LayersSize', 0)


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


def snapshot(with_storage=False, collect_stats=True):
    info = docker('/info')
    containers = docker('/containers/json?all=1&size=1')
    issues = []
    if len(containers) > 100:
        issues.append(f'컨테이너 {len(containers)}개 중 100개만 수집')
    names = {name.lstrip('/') for item in containers for name in item.get('Names', [])}
    issues.extend(name + ' 필수 컨테이너 누락' for name in sorted(REQUIRED_CONTAINERS - names))
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
            storage = docker('/system/df', timeout=20)
        except Exception:
            issues.append('Docker 저장공간 수집 실패·시간 초과')
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
                issues.append(f'{mount} 공간 {percent:.0f}%·inode {inode:.0f}% ({"실패" if max(percent,inode)>=90 else "주의"})')
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


def server_report():
    issues = []
    memory = {line.split(':')[0]: int(line.split()[1]) * 1024 for line in Path('/proc/meminfo').read_text().splitlines() if len(line.split()) >= 2}
    total, available = memory['MemTotal'], memory['MemAvailable']
    if (total - available) / total >= .8:
        issues.append('서버 RAM 사용률 80% 이상 (주의)')
    disks = host_disks(issues)
    failed = run(['systemctl', 'list-units', '--state=failed', '--no-legend', '--plain'])
    failures = len(failed.stdout.strip().splitlines()) if failed.returncode == 0 and failed.stdout.strip() else (0 if failed.returncode == 0 else '확인 불가')
    oom = run(['journalctl', '-k', '--since=-24h', '--grep=Out of memory: Killed process|oom-kill:', '--no-pager', '--output=cat', '--quiet'])
    oom_count = len(oom.stdout.strip().splitlines()) if oom.stdout.strip() else ('확인 불가' if oom.stderr else 0)
    if failures or oom_count:
        issues.append(f'실패 서비스 {failures}개 / 최근 24h OOM {oom_count}건')
    try:
        info, containers, storage, docker_issues = snapshot(True)
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
        if storage:
            volumes = storage.get('Volumes') or []
            known_volumes = all(v.get('UsageData', {}).get('Size', -1) >= 0 for v in volumes)
            volume_size = sum(v['UsageData']['Size'] for v in volumes) if known_volumes else None
            cache = storage.get('BuildCache') or []
            # Shared cache records are not added to image-layer bytes; both categories stay separate.
            cache_size = sum(c.get('Size', 0) for c in cache)
            write_size = sum(c.get('SizeRw', 0) for c in storage.get('Containers') or [])
            disk = f"{size(image_disk_bytes(storage))} / {size(write_size)} / {size(volume_size)} / {size(cache_size)}"
            reclaim = run(['docker', 'system', 'df', '--format', '{{.Type}}={{.Reclaimable}}'], timeout=15)
            reclaimable = '; '.join(reclaim.stdout.strip().splitlines()) if reclaim.returncode == 0 else '확인 불가'
        else:
            disk = reclaimable = '확인 불가'
    except Exception:
        docker_state = docker_ram = disk = reclaimable = '확인 불가'
        issues.append('Docker 데몬·자원 수집 실패')
    operating_system = next((line.split('=', 1)[1].strip('"') for line in Path('/etc/os-release').read_text().splitlines() if line.startswith('PRETTY_NAME=')), 'Linux')
    row = [socket.gethostname() + ' / ' + operating_system + ' / ' + os.uname().release + ' / ' + duration(float(Path('/proc/uptime').read_text().split()[0])),
           f'{cpu_sample():.1f}% / {os.cpu_count()}코어 · Load ' + '/'.join(f'{x:.2f}' for x in os.getloadavg()),
           f'{size(total-available)}/{size(total)} · 가용 {size(available)}',
           f"{size(memory['SwapTotal']-memory['SwapFree'])}/{size(memory['SwapTotal'])}",
           disks, docker_state, docker_ram, disk, reclaimable, f'{failures}개 / {oom_count}건']
    facts = {'ram_total': total, 'ram_available': available, 'cpu_cores': os.cpu_count(),
             'load': list(os.getloadavg()), 'failed_units': failures, 'oom_events': oom_count}
    return ['서버·OS·커널·가동', 'CPU 사용/코어·Load 1·5·15분', 'RAM 사용/전체·가용', 'Swap 사용/전체',
            '디스크 사용/전체·여유·inode', 'Docker/컨테이너', 'Docker RAM 합/인식',
            '이미지/쓰기/볼륨/캐시', '회수 가능량(Docker)', '시스템 실패/OOM 24h'], [row], issues, facts


def mounted_storage(mounts, volumes, measurements=None):
    result = []
    for mount in mounts[:12]:
        if mount['Type'] == 'volume':
            volume = volumes.get(mount.get('Name'), {})
            usage = volume.get('UsageData', {})
            result.append(f"볼륨 {mount.get('Name', '?')}: {size(usage.get('Size'))}" + (' (공유)' if usage.get('RefCount', 0) > 1 else ''))
        elif mount['Type'] == 'bind':
            path = mount['Source']
            try:
                if measurements is not None and path in measurements:
                    result.append('bind ' + mount['Destination'] + ': ' + measurements[path])
                    continue
                remaining = min(12, measurements.get('deadline', time.monotonic()+12) - time.monotonic()) if measurements is not None else 12
                if remaining <= 0:
                    raise TimeoutError('measurement budget exhausted')
                measured = run(['nice', '-n', '10', 'du', '-sx', '--block-size=1', '--', path], timeout=remaining)
                amount = size(int(measured.stdout.split()[0])) if measured.returncode == 0 else '확인 불가'
            except Exception:
                amount = '시간 초과·확인 불가'
            if measurements is not None:
                measurements[path] = amount
            result.append('bind ' + mount['Destination'] + ': ' + amount)
        else:
            result.append(mount['Type'] + ': ' + mount['Destination'])
    if len(mounts) > 12:
        result.append('일부 mount 생략')
    return '; '.join(result) or '없음'


def log_usage(detail, docker_root):
    driver = detail['HostConfig'].get('LogConfig', {}).get('Type', '?')
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


def container_report():
    info, containers, storage, issues = snapshot(True)
    volumes = {v['Name']: v for v in storage.get('Volumes') or []}
    rows, facts, measurements = [], [], {'deadline': time.monotonic() + 25}
    for value in containers:
        item, detail, stats = value['item'], value['detail'], value['stats']
        name = detail.get('Name', (item.get('Names') or ['?'])[0]).lstrip('/')
        if value.get('unavailable'):
            rows.append([name, item['State'] + '/확인 불가'] + ['확인 불가'] * 9)
            issues.append(name + ' 상세 수집 실패')
            continue
        state, host, config = detail['State'], detail['HostConfig'], detail['Config']
        health = state.get('Health', {}).get('Status', '미설정')
        if health == 'unhealthy' or not state.get('Running') or state.get('OOMKilled'):
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
        log = log_usage(detail, info.get('DockerRootDir', '/var/lib/docker'))
        if '미측정' in log:
            issues.append(name + ' 로그 ' + log)
        networks = stats.get('networks', {})
        net = size(sum(v.get('rx_bytes', 0) for v in networks.values())) + '/' + size(sum(v.get('tx_bytes', 0) for v in networks.values()))
        io = stats.get('blkio_stats', {}).get('io_service_bytes_recursive') or []
        block = size(sum(v['value'] for v in io if v['op'].lower() == 'read')) + '/' + size(sum(v['value'] for v in io if v['op'].lower() == 'write'))
        ports = ','.join(f"{p.get('IP', '')}:{p['PublicPort']}→{p['PrivatePort']}" for p in item.get('Ports', []) if 'PublicPort' in p) or '없음'
        try:
            started = datetime.fromisoformat(state['StartedAt'].replace('Z', '+00:00'))
            uptime = duration((datetime.now(timezone.utc) - started).total_seconds()) if state['Running'] else '중지'
        except ValueError:
            uptime = '확인 불가'
        cpu = f'{metrics["cpu"]:.1f}%' if metrics['cpu_available'] else '확인 불가'
        ram = size(metrics['memory']) if metrics['memory_available'] else '확인 불가'
        disk_limit = (host.get('StorageOpt') or {}).get('size', '미설정(호스트 공유)')
        if 'networks' not in stats:
            net = '미측정 (네트워크 통계 없음)'
        if 'io_service_bytes_recursive' not in stats.get('blkio_stats', {}) or stats.get('blkio_stats', {}).get('io_service_bytes_recursive') is None:
            block = '미측정 (IO 통계 없음)'
        mounted = mounted_storage(detail.get('Mounts', []), volumes, measurements)
        if '확인 불가' in mounted:
            issues.append(name + ' 데이터 용량 미측정: 12초 한도 또는 저장소 접근 실패')
        facts.append({'name': name, 'running': state.get('Running'), 'health': health,
                      'ram_bytes': metrics['memory'] if metrics['memory_available'] else None,
                      'ram_limit': limit, 'host_ram': info.get('MemTotal'), 'cpu_limit': cpus,
                      'cpu_percent': metrics['cpu'] if metrics['cpu_available'] else None,
                      'log': log, 'mounts': mounted})
        rows.append([name, state['Status'] + '/' + health, f'{cpu}/{cpu_limit}',
                     f'{ram}/{size(limit) if limit else "미설정"} · {percent}',
                     size(item.get('SizeRw')) + '/' + disk_limit + ' · 로그 ' + log, mounted,
                     uptime + f'/{detail.get("RestartCount", 0)}회', f'{state.get("ExitCode", "?")}/{"예" if state.get("OOMKilled") else "아니오"}',
                     str(stats.get('pids_stats', {}).get('current', '확인 불가')) + '/' + ports,
                     net + ' · IO ' + block, config.get('Image', '?')])
    return ['컨테이너', '실행/Health', 'CPU 사용/제한', 'RAM 사용/제한·비율', '쓰기 사용/제한·로그(회전 포함)',
            '데이터 저장소/사용량', '가동/재시작', '종료 코드/OOM', 'PID/공개 포트', 'NET 수신/송신·IO 읽기/쓰기(누적)', '이미지'], rows, issues, facts


def health_report():
    _, containers, _, issues = snapshot(collect_stats=False)
    rows = []
    names = {name.lstrip('/') for value in containers for name in value['item'].get('Names', [])}
    rows.extend([name, 'edo1 Docker', '실패', '필수 컨테이너 누락', '—', '실행 구성 확인 필요'] for name in sorted(REQUIRED_CONTAINERS - names))
    for value in containers:
        item, state = value['item'], value['detail'].get('State', {})
        name = (item.get('Names') or ['?'])[0].lstrip('/')
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
        rows.append([name, 'Docker 준비 검사', status, evidence, '—', action])
    for name, port, path in [('Grafana', 3000, '/api/health'), ('Metabase', 3001, '/api/health'), ('n8n', 5688, '/healthz/readiness')]:
        if name == 'n8n' and 'hey-edo1-n8n-1' not in names:
            continue
        started = time.monotonic()
        connection = http.client.HTTPConnection('127.0.0.1', port, timeout=4)
        try:
            connection.request('GET', path)
            response = connection.getresponse()
            body = json.loads(response.read(4096))
            good = response.status == 200 and (body.get('database') == 'ok' if name == 'Grafana' else body.get('status') == 'ok')
            rows.append([name, 'edo1 loopback HTTP', '정상' if good else '실패', f'HTTP {response.status} · 준비 상태·DB 응답 확인',
                         f'{(time.monotonic()-started)*1000:.0f}ms', '준비 상태·DB 응답 확인'])
        except Exception:
            rows.append([name, 'edo1 loopback HTTP', '실패', '응답 확인 불가', '—', path])
        finally:
            connection.close()
    return ['대상', '위치/방식', '판정', '응답/측정값', '소요', '상세'], rows, issues


def main(mode):
    started_at = datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d %H:%M:%S KST')
    started = time.monotonic()
    function = {'server': server_report, 'containers': container_report, 'health': health_report}.get(mode)
    if function is None:
        raise SystemExit('unsupported mode')
    try:
        report = function()
        columns, rows, issues = report[:3]
        facts = report[3] if len(report) > 3 else None
    except Exception:
        if mode == 'health':
            columns, rows = ['대상', '위치/방식', '판정', '응답/측정값', '소요', '상세'], [['edo1', 'SSH 수집', '확인 불가', '수집 실패·시간 초과', '—', '운영자 확인 필요']]
        else:
            columns, rows = ['대상', '결과'], [['edo1', '수집 실패·확인 불가']]
        issues = ['수집 실패·시간 초과; 운영자 확인 필요']
        facts = None
    return {'mode': mode, 'columns': columns, 'rows': rows, 'issues': issues, 'facts': facts,
            'started_at': started_at, 'elapsed_seconds': round(time.monotonic()-started, 2),
            'captured_at': datetime.now(timezone(timedelta(hours=9))).strftime('%Y-%m-%d %H:%M:%S KST')}


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('one fixed mode required')
    print(json.dumps(main(sys.argv[1]), ensure_ascii=False))
