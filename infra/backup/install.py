#!/usr/bin/env python3
"""관리 SSH 별칭으로 112 전용 파일만 설치한다. 활성화는 별도 명령으로 수행한다."""
import base64
import json
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).parent

def remote(host, code):
    r = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ClearAllForwardings=yes', '-o', 'ConnectTimeout=10',
                        host, 'sudo -n python3 -'], input=code.encode(), capture_output=True, timeout=120)
    if r.returncode:
        print(r.stderr.decode(), file=sys.stderr)
        raise RuntimeError('installation failed on ' + host)
    return r.stdout.decode()

def write_code(path, data, mode=0o600):
    return f"p=Path({str(path)!r});p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(base64.b64decode({base64.b64encode(data).decode()!r}));p.chmod({mode})\n"

def main():
    common = "import os,base64,subprocess,json,pwd\nfrom pathlib import Path\nos.umask(0o077)\n"
    prepare = """
p=Path('/opt/eolssu-backup');p.mkdir(mode=0o755,exist_ok=True);p.chmod(0o755)
marker=p/'.owner'
if marker.exists() and marker.read_text()!='S15P31E102-112':raise RuntimeError('owner mismatch')
if not marker.exists() and list(p.iterdir()):raise RuntimeError('unowned installation')
marker.write_text('S15P31E102-112')
for name in ['/etc/eolssu-backup','/var/lib/eolssu-backup']:
 Path(name).mkdir(mode=0o700,exist_ok=True)
"""
    payload = write_code('/opt/eolssu-backup/backup.py', (HERE / 'backup.py').read_bytes(), 0o755)
    store_code = common + prepare + payload + """
keys=Path('/var/lib/eolssu-backup-keys');keys.mkdir(mode=0o700,exist_ok=True)
Path('/var/lib/eolssu-backups').mkdir(mode=0o700,exist_ok=True)
if not (keys/'restore-key.pem').exists():
 subprocess.run(['openssl','req','-x509','-newkey','rsa:3072','-nodes','-keyout',str(keys/'restore-key.pem'),'-out','/etc/eolssu-backup/recipient.pem','-days','3650','-subj','/CN=eolssu-backup-112'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
try:
 account=pwd.getpwnam('eolssu-backup')
 assert account.pw_dir=='/var/lib/eolssu-backup-receiver'
except KeyError:
 subprocess.run(['useradd','--system','--create-home','--home-dir','/var/lib/eolssu-backup-receiver','--shell','/bin/sh','eolssu-backup'],check=True)
home=Path('/var/lib/eolssu-backup-receiver');os.chown(home,0,0);home.chmod(0o755)
(home/'.ssh').mkdir(exist_ok=True);os.chown(home/'.ssh',0,0);(home/'.ssh').chmod(0o755)
print(json.dumps({'certificate':Path('/etc/eolssu-backup/recipient.pem').read_text(),'host_key':Path('/etc/ssh/ssh_host_ed25519_key.pub').read_text().strip()}))
"""
    store = json.loads(remote('edo2', store_code))
    source = json.loads(remote('edo1', common + prepare + payload + """
key=Path('/etc/eolssu-backup/transport')
if not key.exists():subprocess.run(['ssh-keygen','-t','ed25519','-N','','-f',str(key),'-C','S15P31E102-112'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
print(json.dumps({'public_key':Path(str(key)+'.pub').read_text().strip()}))
"""))
    dispatch = '#!/bin/sh\nexec timeout 600 sudo -n /opt/eolssu-backup/backup.py receive "$SSH_ORIGINAL_COMMAND"\n'
    auth = 'from="43.203.215.107",restrict,command="/opt/eolssu-backup/receive-ssh" ' + source['public_key'] + '\n'
    sudo = 'eolssu-backup ALL=(root) NOPASSWD: /opt/eolssu-backup/backup.py receive *\n'
    remote('edo2', common + write_code('/opt/eolssu-backup/receive-ssh', dispatch.encode(), 0o755)
           + write_code('/var/lib/eolssu-backup-receiver/.ssh/authorized_keys', auth.encode(), 0o644)
           + write_code('/etc/sudoers.d/eolssu-backup-112', sudo.encode(), 0o440)
           + "subprocess.run(['visudo','-cf','/etc/sudoers.d/eolssu-backup-112'],check=True,stdout=subprocess.DEVNULL)\n")
    ssh = '''Host backup-store
 HostName 15.165.144.7
 User eolssu-backup
 IdentityFile /etc/eolssu-backup/transport
 IdentitiesOnly yes
 BatchMode yes
 StrictHostKeyChecking yes
 UserKnownHostsFile /etc/eolssu-backup/known_hosts
 ConnectTimeout 10
 ServerAliveInterval 15
 ServerAliveCountMax 3
 ClearAllForwardings yes
'''
    remote('edo1', common + write_code('/etc/eolssu-backup/ssh_config', ssh.encode())
           + write_code('/etc/eolssu-backup/known_hosts', ('15.165.144.7 ' + store['host_key'] + '\n').encode())
           + write_code('/etc/eolssu-backup/recipient.pem', store['certificate'].encode()))
    service = '''[Unit]
Description=Eolssu encrypted backup %i
After=network-online.target docker.service
Wants=network-online.target
[Service]
Type=oneshot
ExecStart=/opt/eolssu-backup/backup.py send %i
TimeoutStartSec=15min
UMask=0077
Nice=10
IOSchedulingClass=idle
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/var/lib/eolssu-backup /run/lock
PrivateTmp=true
'''
    jobs = {'db': '*-*-* *:10:00 Asia/Seoul', 'config': '*-*-* 03:20:00 Asia/Seoul',
            'grafana': '*-*-* 03:30:00 Asia/Seoul', 'jenkins': '*-*-* 03:40:00 Asia/Seoul',
            'controller-key': '*-*-* 03:45:00 Asia/Seoul'}
    text = common + write_code('/etc/systemd/system/eolssu-backup@.service', service.encode(), 0o644)
    for kind, schedule in jobs.items():
        timer = f'[Unit]\nDescription=Eolssu {kind} backup schedule\n[Timer]\nOnCalendar={schedule}\nPersistent=true\nUnit=eolssu-backup@{kind}.service\n[Install]\nWantedBy=timers.target\n'
        text += write_code('/etc/systemd/system/eolssu-backup-' + kind + '.timer', timer.encode(), 0o644)
    text += "subprocess.run(['systemctl','daemon-reload'],check=True)\n"
    remote('edo1', text)
    text = common
    for action, schedule in [('prune', '*-*-* 04:20:00 Asia/Seoul'), ('status', '*-*-* *:00/15:00 Asia/Seoul'), ('restore-test', 'Sun *-*-* 04:40:00 Asia/Seoul')]:
        name = 'eolssu-backup-' + action
        unit = f'[Unit]\nDescription=Eolssu backup {action}\nAfter=docker.service\n[Service]\nType=oneshot\nExecStart=/opt/eolssu-backup/backup.py {action}\nTimeoutStartSec=20min\nUMask=0077\nNice=10\n'
        persistent = 'false' if action == 'restore-test' else 'true'
        timer = f'[Unit]\nDescription=Eolssu backup {action} schedule\n[Timer]\nOnCalendar={schedule}\nPersistent={persistent}\n[Install]\nWantedBy=timers.target\n'
        text += write_code('/etc/systemd/system/' + name + '.service', unit.encode(), 0o644)
        text += write_code('/etc/systemd/system/' + name + '.timer', timer.encode(), 0o644)
    text += "subprocess.run(['systemctl','daemon-reload'],check=True)\n"
    remote('edo2', text)
    print('Prepared root-owned code, encrypted transport and disabled timers; activation is separate.')

if __name__ == '__main__':
    main()
