#!/usr/bin/env python3
"""Build, test and smoke-check the ai runtime image of one develop commit (S15P31E102-244).

Run from the root of a checkout of that commit as the Jenkins user (Docker access, no root).

  plan SHA   print whether the runtime inputs differ from the image the running ai container uses
  build SHA  build ai/ into eolssu-ai:ci-SHA with revision and source labels
  test SHA   run the scoring unit tests with the image's Python, dependencies and ffmpeg against the
             read-only checkout (dev dependencies only in a throwaway container)
  smoke SHA  start the image like production (non-root, read-only, tmpfs /tmp, no network),
             then check /health and run one synthetic scoring pass (ai_service.warmup)

Every subcommand prints one JSON object. Nothing here touches the running service.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import signal
import subprocess
import tempfile
import time
import uuid

SHA = re.compile('[0-9a-f]{40}')
REPOSITORY = 'eolssu-ai'
CONTAINER = 'eolssu-runtime-ai-1'
REVISION_LABEL = 'org.opencontainers.image.revision'
SOURCE_LABEL = 'com.eolssu.ai.source'
VERIFY_LABEL = 'com.eolssu.ci.verification'
# The image is built only from these paths (ai/.dockerignore keeps the rest out of the context).
RUNTIME_INPUTS = ('ai/.dockerignore', 'ai/.python-version', 'ai/Dockerfile', 'ai/pyproject.toml',
                  'ai/src', 'ai/uv.lock')
# Tests find repository data (data/reference, data/tests/fixtures, ...) relative to the source tree, so the
# whole checkout is mounted read-only and the project is installed editable into the image's own environment.
REPOSITORY_MOUNT = '/repo'
TEST_DIRECTORY = Path('ai/tests/unit')
# The scoring image does not serve omr_service; those tests belong to the OMR image (244 stage 2).
OMR_MARKER = 'omr_service'
# Same constraints as the ai service in infra/runtime/compose.yml.
RUNTIME_FLAGS = ('--read-only', '--tmpfs', '/tmp:rw,size=512m', '--memory', '6g')
HEALTH_SCRIPT = ("import json, urllib.request; "
                 "print(json.dumps(json.load(urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=2))))")


class BuildError(Exception):
    pass


def image_name(sha):
    if not SHA.fullmatch(sha):
        raise BuildError('a full lowercase commit SHA is required')
    return f'{REPOSITORY}:ci-{sha}'


def call(run, args, *, timeout, check=True):
    try:
        result = run(args, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise BuildError('command unavailable or timed out: ' + ' '.join(args[:3])) from None
    if check and result.returncode:
        raise BuildError('command failed: ' + ' '.join(args[:3]))
    return result


def source_key(sha, run=subprocess.run):
    """Stable digest of the git objects the runtime image is built from."""
    image_name(sha)
    listing = call(run, ['git', 'ls-tree', '-r', '--full-tree', sha, '--', *RUNTIME_INPUTS], timeout=30).stdout
    if not re.search(r'\tai/src/', listing) or '\tai/Dockerfile\n' not in listing + '\n':
        raise BuildError('commit lacks the ai runtime sources')
    return hashlib.sha256(listing.encode()).hexdigest()


def require_checkout(sha, run=subprocess.run):
    """The build context is the working tree, so it must be exactly the commit being labelled."""
    if call(run, ['git', 'rev-parse', 'HEAD'], timeout=30).stdout.strip() != sha:
        raise BuildError('working tree is not at the requested commit')
    if call(run, ['git', 'status', '--porcelain', '--', 'ai', 'data'], timeout=30).stdout.strip():
        raise BuildError('working tree has local changes under the build inputs')


def deployed_source(run=subprocess.run):
    """Source label of the image the running ai container uses; None when unknown (e.g. a manual build)."""
    found = call(run, ['docker', 'inspect', '--format', '{{.Image}}', CONTAINER], timeout=30, check=False)
    if found.returncode:
        return None
    labels = call(run, ['docker', 'image', 'inspect', '--format', '{{json .Config.Labels}}', found.stdout.strip()],
                  timeout=30, check=False)
    if labels.returncode:
        return None
    return (json.loads(labels.stdout or 'null') or {}).get(SOURCE_LABEL)


def plan(sha, run=subprocess.run):
    key = source_key(sha, run)
    current = deployed_source(run)
    return {'action': 'skip' if current == key else 'deploy', 'source': key, 'deployed_source': current,
            'image': image_name(sha)}


def build(sha, run=subprocess.run):
    image = image_name(sha)
    require_checkout(sha, run)
    key = source_key(sha, run)
    call(run, ['docker', 'build', '--pull=false', '--label', f'{REVISION_LABEL}={sha}',
               '--label', f'{SOURCE_LABEL}={key}', '--tag', image, 'ai'], timeout=1800)
    labels = json.loads(call(run, ['docker', 'image', 'inspect', '--format', '{{json .Config.Labels}}', image],
                             timeout=30).stdout)
    if labels.get(REVISION_LABEL) != sha or labels.get(SOURCE_LABEL) != key:
        raise BuildError('built image labels do not match the commit')
    image_id = call(run, ['docker', 'image', 'inspect', '--format', '{{.Id}}', image], timeout=30).stdout.strip()
    return {'image': image, 'image_id': image_id, 'source': key}


def remove(run, name):
    # Defer a first SIGTERM while removing so an aborted build does not leave the container behind.
    previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM})
    try:
        call(run, ['docker', 'rm', '-f', name], timeout=60, check=False)
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous)


def scoring_tests(directory=TEST_DIRECTORY):
    """Unit test files of the scoring service and its evaluation tools (everything that does not use omr_service)."""
    files = sorted(path for path in directory.glob('test_*.py') if OMR_MARKER not in path.read_text(encoding='utf-8'))
    if not any(path.name == 'test_api.py' for path in files):
        raise BuildError('scoring API tests are missing')
    return [path.as_posix().removeprefix('ai/') for path in files]


def test(sha, junit, run=subprocess.run):
    image = image_name(sha)
    require_checkout(sha, run)
    selected = scoring_tests()
    name = 'eolssu-ci-ai-test-' + uuid.uuid4().hex
    # Root only to add the dev group to the image's environment in this throwaway container; the runtime user,
    # read-only file system and installed (non-editable) package are checked by smoke.
    command = ('uv sync --locked --no-default-groups --group dev && uv run --locked --no-default-groups --group dev '
               'pytest -p no:cacheprovider -q --junitxml=/tmp/junit.xml ' + ' '.join(selected))
    try:
        call(run, ['docker', 'create', '--name', name, '--label', f'{VERIFY_LABEL}=ai-tests', '--user', '0',
                   '--tmpfs', '/tmp:rw,exec,size=1g', '--env', 'UV_PROJECT_ENVIRONMENT=/app/.venv',
                   '--env', 'PYTHONDONTWRITEBYTECODE=1', '--workdir', REPOSITORY_MOUNT + '/ai',
                   '--mount', f'type=bind,src={Path.cwd().resolve()},dst={REPOSITORY_MOUNT},readonly',
                   image, 'sh', '-c', command], timeout=60)
        result = call(run, ['docker', 'start', '--attach', name], timeout=1800, check=False)
        print(result.stdout[-20000:], end='', flush=True)
        print(result.stderr[-5000:], end='', flush=True)
        # The JUnit file sits on the container's tmpfs, so copy it while the container still exists.
        copied = call(run, ['docker', 'cp', name + ':/tmp/junit.xml', str(junit)], timeout=60, check=False)
        if result.returncode:
            raise BuildError('ai tests failed inside the image')
        if copied.returncode:
            raise BuildError('ai test report missing')
    finally:
        remove(run, name)
    return {'image': image, 'junit': str(junit), 'test_files': len(selected)}


def smoke(sha, run=subprocess.run, sleep=time.sleep, seconds=60):
    image = image_name(sha)
    name = 'eolssu-ci-ai-smoke-' + uuid.uuid4().hex
    try:
        call(run, ['docker', 'run', '-d', '--name', name, '--label', f'{VERIFY_LABEL}=ai-smoke',
                   '--network', 'none', *RUNTIME_FLAGS, image], timeout=60)
        user = call(run, ['docker', 'exec', name, 'id', '-u'], timeout=30).stdout.strip()
        if user == '0':
            raise BuildError('ai image runs as root')
        deadline = time.monotonic() + seconds
        while True:
            probe = call(run, ['docker', 'exec', name, 'python', '-c', HEALTH_SCRIPT], timeout=10, check=False)
            if probe.returncode == 0:
                health = json.loads(probe.stdout)
                if health.get('status') == 'ok' and health.get('modelVersion'):
                    break
            if time.monotonic() >= deadline:
                raise BuildError('ai /health did not answer in time')
            sleep(1)
        warm = call(run, ['docker', 'exec', name, 'python', '-m', 'ai_service.warmup'], timeout=300, check=False)
        report = json.loads(warm.stdout.strip().splitlines()[-1]) if warm.stdout.strip() else {}
        if warm.returncode or report.get('status') != 'ok':
            raise BuildError('synthetic scoring failed in the runtime image: ' + str(report.get('error', 'unexpected')))
    finally:
        remove(run, name)
    return {'image': image, 'model_version': health['modelVersion'], 'warmup_ms': report.get('ms')}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('command', choices=('plan', 'build', 'test', 'smoke'))
    parser.add_argument('revision')
    parser.add_argument('--junit', type=Path, default=Path('ai-test-results/junit.xml'))
    args = parser.parse_args()

    def terminate(signum, _frame):
        signal.signal(signum, signal.SIG_IGN)
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, terminate)
    try:
        if args.command == 'plan':
            result = plan(args.revision)
        elif args.command == 'build':
            result = build(args.revision)
        elif args.command == 'test':
            args.junit.parent.mkdir(parents=True, exist_ok=True)
            result = test(args.revision, args.junit)
        else:
            result = smoke(args.revision)
    except (BuildError, OSError, ValueError) as error:
        parser.exit(1, json.dumps({'status': 'failed', 'reason': str(error)}) + '\n')
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
