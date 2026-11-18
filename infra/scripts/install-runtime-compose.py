#!/usr/bin/python3
"""Validate and atomically replace the managed edo1 runtime Compose file."""
import argparse
import fcntl
import grp
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile


def validate(candidate, env_file, *, as_jenkins=False):
    command = ['docker', 'compose', '--env-file', str(env_file), '-f', str(candidate), 'config', '--quiet']
    if as_jenkins:
        command[:0] = ['sudo', '-n', '-u', 'jenkins']
    result = subprocess.run(command, capture_output=True, timeout=30)
    if result.returncode:
        account = 'jenkins' if as_jenkins else 'root'
        raise RuntimeError(f'Compose validation failed ({account})')


def install(candidate, root=Path('/opt/eolssu'), lock_path=Path('/run/lock/eolssu-backend-deploy.lock')):
    config = root / 'config'
    live = config / 'compose.yml'
    jenkins_gid = grp.getgrnam('jenkins').gr_gid
    with lock_path.open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        if live.is_symlink() or not live.is_file():
            raise ValueError('managed Compose must be a regular file')
        fd, name = tempfile.mkstemp(prefix='.compose-candidate-', suffix='.yml', dir=config)
        staged = Path(name)
        try:
            with os.fdopen(fd, 'wb') as stream:
                source_fd = os.open(candidate, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
                with os.fdopen(source_fd, 'rb') as source:
                    if not stat.S_ISREG(os.fstat(source.fileno()).st_mode):
                        raise ValueError('a regular Compose candidate file is required')
                    shutil.copyfileobj(source, stream)
            validate(staged, root / '.env')
            os.chown(staged, 0, jenkins_gid)
            os.chmod(staged, 0o640)
            validate(staged, config / 'ci.env.example', as_jenkins=True)

            with tempfile.NamedTemporaryFile(dir=config, prefix='compose.yml.backup-', delete=False) as backup:
                with live.open('rb') as previous:
                    shutil.copyfileobj(previous, backup)
            os.replace(staged, live)
            return Path(backup.name)
        finally:
            staged.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('candidate', type=Path)
    args = parser.parse_args()
    if os.geteuid() != 0:
        parser.error('run as root')
    try:
        backup = install(args.candidate)
    except BlockingIOError:
        parser.exit(1, 'Runtime deployment lock is busy; retry after deployment.\n')
    except subprocess.TimeoutExpired:
        parser.exit(1, 'Compose validation timed out.\n')
    except (OSError, RuntimeError, ValueError) as error:
        parser.exit(1, f'{error}\n')
    print(f'Installed runtime Compose (root:jenkins 0640); previous file: {backup}')


if __name__ == '__main__':
    main()
