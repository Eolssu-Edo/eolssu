#!/usr/bin/env python3
"""Run backend databaseTest against a disposable MySQL container that is always removed.

The container listens only on a random loopback port, keeps its data in tmpfs and is
labelled so a run interrupted before cleanup is removed by the next run. Passwords are
generated per run and passed to Docker through the environment, never as arguments.
"""
import argparse
import functools
import os
import re
import secrets
import signal
import subprocess
import sys
import time
import uuid


IMAGE = 'mysql:8.4.13'  # Same tag as the production MySQL in infra/runtime/compose.yml.
LABEL = 'com.eolssu.ci.database-test=backend'
DATABASE = 'eolssu_test'
USER = 'eolssu_test'
LOCK_TABLES = ('data_locks', 'data_lock_waits', 'threads')
# Every cached Spring test context keeps its own Hikari pool; the default 151 is too few.
SERVER_ARGS = ['--max-connections=1000', '--disable-log-bin']
LIMITS = ['--memory', '1536m', '--cpus', '2', '--log-opt', 'max-size=5m', '--log-opt', 'max-file=1']


def remove_stale(run):
    """Remove containers left by an earlier run that could not reach its own cleanup."""
    listed = run(['docker', 'ps', '--all', '--quiet', '--filter', 'label=' + LABEL],
                 check=True, capture_output=True, text=True).stdout.split()
    for container in listed:
        run(['docker', 'rm', '--force', container], check=True, capture_output=True, timeout=60)
    return listed


def published_port(run, container):
    output = run(['docker', 'port', container, '3306/tcp'],
                 check=True, capture_output=True, text=True).stdout
    for line in output.splitlines():
        match = re.fullmatch(r'127\.0\.0\.1:(\d+)', line.strip())
        if match:
            return int(match.group(1))
    raise RuntimeError('Disposable MySQL is not published on a loopback port')


def query(run, container, password, sql, *, check=True):
    # TCP succeeds only on the final server: the image's init server runs without networking.
    return run(['docker', 'exec', '-e', 'MYSQL_PWD', container, 'mysql', '--protocol=TCP',
                '--host=127.0.0.1', '--user=root', '--batch', '--skip-column-names', '--execute=' + sql],
               check=check, capture_output=True, text=True, timeout=30,
               env=dict(os.environ, MYSQL_PWD=password))


def wait_ready(run, container, password, *, timeout, clock, sleep):
    deadline = clock() + timeout
    while True:
        try:
            result = query(run, container, password, 'SELECT 1', check=False)
        except subprocess.TimeoutExpired:
            result = None
        if result is not None and result.returncode == 0 and result.stdout.strip() == '1':
            return
        if clock() >= deadline:
            raise RuntimeError('Disposable MySQL did not become ready in %d seconds' % timeout)
        sleep(1)


def run_database_tests(command, *, run=subprocess.run, clock=time.monotonic, sleep=time.sleep,
                       ready_timeout=180, log=functools.partial(print, flush=True)):
    if not command:
        raise ValueError('A test command is required after --')
    stale = remove_stale(run)
    if stale:
        log('Removed %d stale disposable MySQL container(s)' % len(stale))

    container = 'eolssu-ci-dbtest-' + uuid.uuid4().hex[:12]
    root_password = secrets.token_urlsafe(24)
    user_password = secrets.token_urlsafe(24)
    try:
        run(['docker', 'run', '--detach', '--name', container, '--label', LABEL,
             '--publish', '127.0.0.1::3306', '--tmpfs', '/var/lib/mysql', *LIMITS,
             '-e', 'MYSQL_DATABASE=' + DATABASE, '-e', 'MYSQL_USER=' + USER,
             '-e', 'MYSQL_PASSWORD', '-e', 'MYSQL_ROOT_PASSWORD', IMAGE, *SERVER_ARGS],
            check=True, capture_output=True, text=True,
            env=dict(os.environ, MYSQL_PASSWORD=user_password, MYSQL_ROOT_PASSWORD=root_password))
        port = published_port(run, container)
        started = clock()
        try:
            wait_ready(run, container, root_password, timeout=ready_timeout, clock=clock, sleep=sleep)
        except RuntimeError:
            logs = run(['docker', 'logs', '--tail', '40', container],
                       check=False, capture_output=True, text=True)
            log((logs.stdout or '') + (logs.stderr or ''))
            raise
        # Lock-wait tests observe real InnoDB locks; these grants stay on the test account only.
        query(run, container, root_password, ' '.join(
            "GRANT SELECT ON performance_schema.%s TO '%s'@'%%';" % (table, USER) for table in LOCK_TABLES))
        log('Disposable MySQL %s ready on 127.0.0.1:%d after %.1fs' % (IMAGE, port, clock() - started))
        env = dict(os.environ, TEST_DB_URL='jdbc:mysql://127.0.0.1:%d/%s' % (port, DATABASE),
                   TEST_DB_USERNAME=USER, TEST_DB_PASSWORD=user_password)
        return run(command, env=env).returncode
    finally:
        # Remove by name even if `docker run` failed after creating it. SIGTERM is deferred
        # during removal so an aborted build still deletes the container.
        previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM})
        try:
            run(['docker', 'rm', '--force', '--volumes', container],
                check=False, capture_output=True, timeout=60)
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ready-timeout', type=int, default=180)
    parser.add_argument('command', nargs=argparse.REMAINDER,
                        help='test command after --, run in the current directory')
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command

    def terminate(signum, _frame):
        signal.signal(signum, signal.SIG_IGN)
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, terminate)
    try:
        code = run_database_tests(command, ready_timeout=args.ready_timeout)
    except (ValueError, RuntimeError, OSError, subprocess.SubprocessError) as error:
        # Docker's own reason (port in use, image missing) is in the captured stderr.
        detail = getattr(error, 'stderr', None) or ''
        parser.exit(1, str(error) + '\n' + (detail if isinstance(detail, str) else detail.decode(errors='replace')))
    sys.exit(code)


if __name__ == '__main__':
    main()
