#!/usr/bin/env python3
"""Plan (default) or apply retention for edo1 deployment releases, CI image tags and build cache."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import fcntl
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys

ROOT = Path('/opt/eolssu')
LOCK = Path('/run/lock/eolssu-backend-deploy.lock')
REPOSITORIES = ('eolssu-backend', 'eolssu-frontend')
KEEP_COUNT = 20
KEEP_AGE = timedelta(days=7)
CACHE_UNTIL = '72h'
SHA = re.compile('[0-9a-f]{40}')
# Only these keys are read from the secret-bearing .env file; nothing else is parsed or printed.
RELEASE_KEYS = ('BACKEND_JAR', 'WEB_DIST_DIR')
STATES = (('deployment.json', ('jar', 'previous_jar')), ('frontend-deployment.json', ('web', 'previous_web')))


class RetentionError(Exception):
    pass


def run(*args, timeout=120):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise RetentionError('command unavailable or timed out: ' + args[0]) from None
    if result.returncode:
        raise RetentionError('command failed: ' + ' '.join(args[:3]))
    return result.stdout


@contextmanager
def deployment_lock(path=LOCK, seconds=120):
    """Share the deploy tools' lock so a release is never removed while a deployment selects one."""
    with open(path, 'a') as lock:
        def expired(signum, frame):
            raise RetentionError('deployment lock wait timed out')
        previous = signal.signal(signal.SIGALRM, expired)
        signal.alarm(seconds)
        try:
            fcntl.flock(lock, fcntl.LOCK_EX)
        finally:
            signal.alarm(0)
            signal.signal(signal.SIGALRM, previous)
        yield


def release_sha(path, releases):
    """Return the SHA of a managed release path, or raise for anything outside the release root."""
    path = Path(path)
    try:
        relative = path.resolve().relative_to(releases.resolve())
    except ValueError:
        raise RetentionError('referenced release outside managed root') from None
    if not relative.parts or not SHA.fullmatch(relative.parts[0]):
        raise RetentionError('referenced release is not a SHA directory')
    return relative.parts[0]


def protected_shas(root, mounts=()):
    """Releases the running system or a rollback can still reach. Fails closed on any unexpected state."""
    releases = root / 'releases'
    protected = set()
    lines = (root / '.env').read_text().splitlines()
    for key in RELEASE_KEYS:
        values = [line.split('=', 1)[1] for line in lines if line.startswith(key + '=')]
        if len(values) != 1:
            raise RetentionError(key + ' must occur exactly once')
        protected.add(release_sha(values[0], releases))
    for name, keys in STATES:
        state = json.loads((root / name).read_text())
        # A deployment or rollback in progress may still need any release; retry once it settles.
        if state.get('status') != 'deployed':
            raise RetentionError(name + ' is not in the deployed state')
        if SHA.fullmatch(state.get('sha') or ''):
            protected.add(state['sha'])
        for key in keys:
            if state.get(key):
                protected.add(release_sha(state[key], releases))
    for source in mounts:
        if Path(source).resolve().is_relative_to(releases.resolve()):
            protected.add(release_sha(source, releases))
    return protected


def keep_set(items, protected, now, keep_count=KEEP_COUNT, keep_age=KEEP_AGE):
    """items: iterable of (sha, timestamp). Keep protected, the newest keep_count and anything newer than keep_age."""
    ordered = sorted(items, key=lambda item: item[1], reverse=True)
    keep = set(protected)
    keep.update(sha for sha, _ in ordered[:keep_count])
    keep.update(sha for sha, stamp in ordered if now - stamp < keep_age)
    return keep


def plan(releases, images, protected, now, in_use=(), keep_count=KEEP_COUNT, keep_age=KEEP_AGE):
    """releases: [(sha, mtime)]; images: [{'repository','sha','id','created'}]. Returns what may be removed."""
    kept_releases = keep_set(releases, protected, now, keep_count, keep_age)
    remove_images = []
    for repository in REPOSITORIES:
        tagged = [image for image in images if image['repository'] == repository]
        kept = keep_set([(image['sha'], image['created']) for image in tagged], protected, now, keep_count, keep_age)
        # An image whose release is kept stays deployable by SHA; an image a container uses is never removed.
        kept |= kept_releases
        remove_images += [image for image in tagged if image['sha'] not in kept and image['id'] not in in_use]
    return {'releases': sorted(sha for sha, _ in releases if sha not in kept_releases),
            'images': sorted(remove_images, key=lambda image: (image['repository'], image['created']))}


def list_releases(root):
    releases = root / 'releases'
    result = []
    for entry in releases.iterdir():
        # Bootstrap directories and anything that is not a plain SHA directory are left alone.
        if SHA.fullmatch(entry.name) and entry.is_dir() and not entry.is_symlink():
            result.append((entry.name, datetime.fromtimestamp(entry.stat().st_mtime, timezone.utc)))
    return result


def tree_bytes(path):
    total = 0
    for directory, _, files in os.walk(path):
        for name in files:
            file = Path(directory) / name
            if not file.is_symlink():
                total += file.stat().st_size
    return total


def parse_created(text):
    # docker image ls prints e.g. "2026-11-14 22:10:01 +0900 KST"
    return datetime.strptime(' '.join(text.split()[:3]), '%Y-%m-%d %H:%M:%S %z')


def list_images():
    images = []
    output = run('docker', 'image', 'ls', '--no-trunc', '--format', '{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedAt}}')
    for line in output.splitlines():
        repository, tag, image_id, created = line.split('\t')
        if repository in REPOSITORIES and tag.startswith('ci-') and SHA.fullmatch(tag[3:]):
            images.append({'repository': repository, 'sha': tag[3:], 'id': image_id, 'created': parse_created(created)})
    return images


def container_facts():
    """Image IDs used by any container (running or stopped) and bind-mount sources."""
    ids = run('docker', 'ps', '-aq').split()
    if not ids:
        return set(), []
    details = json.loads(run('docker', 'inspect', *ids))
    return ({item['Image'] for item in details},
            [mount['Source'] for item in details for mount in item.get('Mounts') or [] if mount.get('Source')])


def disk_free(path='/'):
    usage = shutil.disk_usage(path)
    return usage.free


def remove_release(root, sha):
    releases = root / 'releases'
    target = releases / sha
    if not SHA.fullmatch(sha) or target.is_symlink() or not target.is_dir() or target.resolve().parent != releases.resolve():
        raise RetentionError('unexpected release target')
    shutil.rmtree(target)


def execute(root=ROOT, apply=False, now=None, keep_count=KEEP_COUNT, keep_age=KEEP_AGE, lock=deployment_lock,
            images_source=list_images, containers=container_facts, docker=run, free=disk_free, below_free=None):
    now = now or datetime.now(timezone.utc)
    report = {'mode': 'apply' if apply else 'dry-run', 'keep_count': keep_count,
              'keep_days': keep_age.days, 'cache_until': CACHE_UNTIL, 'free_bytes_before': free(),
              'image_failures': []}
    if below_free is not None:
        report['below_free_bytes'] = below_free
        # Enough space: do not touch the deployment lock or Docker at all.
        if report['free_bytes_before'] >= below_free:
            report['skipped'] = 'enough free space'
            return report
    with lock():
        in_use, mounts = containers()
        protected = protected_shas(root, mounts)
        releases = list_releases(root)
        images = images_source()
        decided = plan(releases, images, protected, now, in_use, keep_count, keep_age)
        report.update({'protected': sorted(protected), 'releases_total': len(releases), 'images_total': len(images),
                       'releases_to_remove': decided['releases'],
                       'release_bytes_to_remove': sum(tree_bytes(root / 'releases' / sha) for sha in decided['releases']),
                       'images_to_remove': [f"{image['repository']}:ci-{image['sha']}" for image in decided['images']]})
        if apply:
            # Release directories are what a deployment selects, so they are removed while the lock is held.
            for sha in decided['releases']:
                remove_release(root, sha)
    failures = []
    if apply:
        # Old tags and cache are not read by a deployment; a deploy may proceed meanwhile.
        for name in report['images_to_remove']:
            try:
                docker('docker', 'image', 'rm', name)
            except RetentionError:
                failures.append(name)
        docker('docker', 'builder', 'prune', '--force', '--filter', 'until=' + CACHE_UNTIL, timeout=900)
        report['free_bytes_after'] = free()
    report['image_failures'] = failures
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true', help='remove the planned items; default prints the plan only')
    parser.add_argument('--below-free-gib', type=float, help='do nothing unless free space on / is below this many GiB')
    args = parser.parse_args()
    if args.below_free_gib is not None and args.below_free_gib <= 0:
        raise RetentionError('--below-free-gib must be positive')
    os.environ.update({'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LANG': 'C.UTF-8'})
    if os.geteuid() != 0:
        raise RetentionError('run as root')
    below = None if args.below_free_gib is None else int(args.below_free_gib * 2 ** 30)
    report = execute(apply=args.apply, below_free=below)
    print(json.dumps(report, sort_keys=True))
    if report['image_failures']:
        sys.exit(1)


if __name__ == '__main__':
    try:
        main()
    except (RetentionError, OSError, ValueError) as error:
        print(json.dumps({'status': 'failed', 'reason': str(error) if isinstance(error, RetentionError) else type(error).__name__}), file=sys.stderr)
        sys.exit(1)
