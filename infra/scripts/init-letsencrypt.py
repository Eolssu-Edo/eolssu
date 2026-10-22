"""기존 eolssu HTTP webroot로 인증서를 발급하고 프론트에 HTTPS를 추가한다."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def run(*args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=300)
    if result.returncode:
        # Compose 오류는 확장한 비밀 환경변수를 포함할 수 있다.
        raise RuntimeError('command failed: ' + args[0])
    return result.stdout.strip()


def validate_domain(domain):
    label = r'[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?'
    if len(domain) > 253 or not re.fullmatch(label + r'(?:\.' + label + r')+', domain):
        raise ValueError('invalid DNS domain')
    return domain


def initialize(domain, email=None, root=Path('/opt/eolssu'),
               certificates=Path('/etc/letsencrypt')):
    validate_domain(domain)
    config = root / 'config'
    base = config / 'compose.yml'
    if not base.is_file() or not (root / '.env').is_file():
        raise RuntimeError('runtime missing: /opt/eolssu/config/compose.yml and .env are required')
    compose = ('docker', 'compose', '--env-file', str(root / '.env'), '-f', str(base))
    details = json.loads(run('docker', 'inspect', 'eolssu-runtime-frontend-1'))[0]
    webroot = Path(next(m['Source'] for m in details['Mounts']
                        if m['Destination'] == '/usr/share/nginx/html'))
    if not webroot.is_dir():
        raise RuntimeError('running frontend webroot is unavailable')
    # 웹 서버를 중지하지 않고 기존 80 포트에서 HTTP-01을 처리한다.
    args = ('certbot', 'certonly', '--webroot', '--webroot-path', str(webroot),
            '--cert-name', domain, '--domain', domain, '--non-interactive',
            '--agree-tos', '--keep-until-expiring')
    run(*(args + (('--email', email) if email else ('--register-unsafely-without-email',))))
    live = certificates / 'live' / domain
    if not all((live / name).is_file() for name in ('fullchain.pem', 'privkey.pem')):
        raise RuntimeError('certificate files were not created')

    source = Path(__file__).resolve().parents[1] / 'runtime'
    tls = config / 'nginx.tls.conf'
    overlay = config / 'compose.tls.yml'
    previous = {p: p.read_bytes() if p.exists() else None for p in (tls, overlay)}
    backup = Path(tempfile.mkdtemp(prefix='tls-', dir=root))
    backup.chmod(0o700)
    for path, content in previous.items():
        if content is not None:
            (backup / path.name).write_bytes(content)
    candidate = backup / 'candidate.conf'
    candidate.write_text((source / 'nginx.tls.conf.template').read_text().replace('__TLS_DOMAIN__', domain))
    # 적용 전에 실제 이미지·인증서로 새 Nginx 설정을 검사한다.
    run('docker', 'run', '--rm', '--network', 'none',
        '--mount', 'type=bind,src=' + str(candidate) + ',dst=/etc/nginx/conf.d/tls.conf,readonly',
        '--mount', 'type=bind,src=' + str(certificates) + ',dst=/etc/letsencrypt,readonly',
        details['Config']['Image'], 'nginx', '-t')
    applied = False
    try:
        shutil.copyfile(candidate, tls)
        shutil.copyfile(source / 'compose.tls.yml', overlay)
        tls.chmod(0o644)
        overlay.chmod(0o644)
        run(*(compose + ('-f', str(overlay), 'config', '--quiet')))
        applied = True
        run(*(compose + ('-f', str(overlay), 'up', '-d', '--no-deps', '--pull', 'never', 'frontend')))
        run('docker', 'exec', 'eolssu-runtime-frontend-1', 'nginx', '-t')
        # 로컬 바인딩과 도메인 인증서를 함께 확인하며 TLS 검증을 끄지 않는다.
        run('curl', '--fail', '--silent', '--show-error', '--retry', '10',
            '--retry-connrefused', '--retry-delay', '1', '--max-time', '10',
            '--resolve', domain + ':443:127.0.0.1', 'https://' + domain + '/')
    except Exception:
        for path, content in previous.items():
            if content is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(content)
        if applied:
            old = compose + (('-f', str(overlay)) if previous[overlay] is not None else ())
            run(*(old + ('up', '-d', '--no-deps', '--pull', 'never', 'frontend')))
        raise

    hook = certificates / 'renewal-hooks/deploy/eolssu-nginx'
    hook.parent.mkdir(parents=True, exist_ok=True)
    hook.write_text('#!/bin/sh\nset -eu\n'
                    '/usr/bin/docker exec eolssu-runtime-frontend-1 nginx -t\n'
                    '/usr/bin/docker exec eolssu-runtime-frontend-1 nginx -s reload\n')
    hook.chmod(0o755)
    run('systemctl', 'enable', '--now', 'certbot.timer')
    return backup


def main():
    parser = argparse.ArgumentParser(description='eolssu HTTPS 초기화 (기존 HTTP·백엔드·DB 유지)')
    parser.add_argument('--domain', default='k15e102.p.ssafy.io', type=validate_domain,
                        help='인증서 DNS 이름 (기본: k15e102.p.ssafy.io)')
    parser.add_argument('--email', help='선택: Let\'s Encrypt 계정 연락처')
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('run through init-letsencrypt.sh or as root')
    for command in ('docker', 'certbot', 'curl', 'systemctl'):
        if not shutil.which(command):
            parser.error('required command missing: ' + command + ' (Ubuntu: sudo apt-get install certbot)')
    # Jenkins의 백엔드 배포와 프론트 교체가 겹치지 않게 같은 lock을 사용한다.
    with open('/run/lock/eolssu-backend-deploy.lock', 'a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        backup = initialize(args.domain, args.email)
    print('HTTPS ready: https://' + args.domain + '/; config backup: ' + str(backup))


if __name__ == '__main__':
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        raise SystemExit(str(error))
