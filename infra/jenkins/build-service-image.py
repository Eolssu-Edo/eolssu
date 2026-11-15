#!/usr/bin/env python3
"""Build, test and smoke-check the omr or playback runtime image of one develop commit (S15P31E102-951).

Run from the root of a checkout of that commit as the Jenkins user (Docker access, no root).

  SERVICE plan SHA   print whether the image inputs differ from the image the running container uses
  SERVICE build SHA  build SERVICE into eolssu-SERVICE:ci-SHA (repository root context, BuildKit) with labels
  SERVICE test SHA   run the service's unit tests with the image's Python, dependencies and tools against the
                     read-only checkout in a throwaway container
  SERVICE smoke SHA  start the image like production (non-root, read-only, tmpfs /tmp, no network, same memory
                     and CPU limits), then check /health, the empty-request contract and the tools it needs

SERVICE is omr or playback. Every subcommand prints one JSON object. Nothing here touches the running service.
The ai image keeps its own tool (build-ai-image.py); the shared helpers come from it.
"""
import argparse
from dataclasses import dataclass
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import signal
import subprocess
import time
import uuid

_spec = importlib.util.spec_from_file_location('build_ai_image', Path(__file__).with_name('build-ai-image.py'))
ai = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ai)
BuildError, call, remove, SHA = ai.BuildError, ai.call, ai.remove, ai.SHA

REVISION_LABEL = ai.REVISION_LABEL
VERIFY_LABEL = ai.VERIFY_LABEL
REPOSITORY_MOUNT = '/repo'
PROBE = ("import json, sys, urllib.error, urllib.request\n"
         "def call(path, data=None):\n"
         "    request = urllib.request.Request('http://127.0.0.1:8000' + path, data=data,\n"
         "                                     headers={'Content-Type': 'application/json'})\n"
         "    try:\n"
         "        with urllib.request.urlopen(request, timeout=5) as response:\n"
         "            return response.status, json.load(response)\n"
         "    except urllib.error.HTTPError as error:\n"
         "        return error.code, None\n"
         "status, health = call('/health')\n"
         "contract, _ = call(sys.argv[1], b'{}')\n"
         "print(json.dumps({'health': health, 'contract': contract}))\n")


@dataclass(frozen=True)
class Service:
    name: str
    dockerfile: str
    # Every path the build context can carry (the Dockerfile's own .dockerignore decides), so the source key
    # changes exactly when the image can.
    inputs: tuple
    required: tuple
    target: str
    # Same constraints as the service in infra/runtime/compose.yml.
    runtime_flags: tuple
    healthy_status: str
    contract_path: str
    contract_code: int
    # Run as the runtime user in the smoke container; must exit 0.
    tool_check: tuple
    test_command: str
    test_env: tuple = ()

    @property
    def repository(self):
        return 'eolssu-' + self.name

    @property
    def container(self):
        return f'eolssu-runtime-{self.name}-1'

    @property
    def source_label(self):
        return f'com.eolssu.{self.name}.source'


# The OMR tests are the ai/tests/unit files that use omr_service (build-ai-image.py runs the rest). The image carries
# Audiveris, the JGB generator and the scanner model, so the tests that need JGB_GENERATOR_DIR run here.
# Only the dev group is added, and every package the image already has is pinned as a constraint: the scanner
# extension installs newer versions than uv.lock (e.g. pypdfium2), and `uv sync` would quietly test older ones.
OMR_VENV = '/app/ai/.venv/bin/python'
OMR_TESTS = ('uv export --locked --only-group dev --no-emit-project --no-hashes --quiet -o /tmp/dev.txt '
             f'&& uv pip freeze --python {OMR_VENV} > /tmp/installed.txt '
             f'&& uv pip install --python {OMR_VENV} --no-deps --constraint /tmp/installed.txt -r /tmp/dev.txt '
             f'&& {OMR_VENV} -m pytest -p no:cacheprovider -q -rs --junitxml={{junit}} {{files}}')
# The playback venv has no pytest; its tests are unittest and need only the image's Python, libraries and FFmpeg.
PLAYBACK_TESTS = ('python -m unittest discover -s data/src/playback/tests -p "test_*.py" '
                  '&& python -m unittest data.tests.test_playback_import')

SERVICES = {
    'omr': Service(
        name='omr', dockerfile='ai/omr.Dockerfile',
        inputs=('ai/.python-version', 'ai/omr-scan-requirements.txt', 'ai/omr.Dockerfile',
                'ai/omr.Dockerfile.dockerignore', 'ai/pyproject.toml', 'ai/src', 'ai/uv.lock', 'data/reference',
                'data/src/scores'),
        required=('ai/omr.Dockerfile', 'ai/src/omr_service/api.py'),
        # Production runs the last stage (independent scanner extension included), as a plain build would.
        target='scanner',
        runtime_flags=('--read-only', '--tmpfs', '/tmp:rw,exec,size=1g', '--memory', '2g', '--cpus', '2'),
        healthy_status='ok', contract_path='/v1/recognitions', contract_code=422,
        tool_check=('sh', '-c', 'test -x "$AUDIVERIS_CMD" && test -r "$JGB_GENERATOR_DIR/tools/import-ngc-omr.mjs" '
                    '&& test -r "$JEONGGANBO_OMR_DIR/jngbomr/__init__.py" && node --version >/dev/null '
                    '&& python -c "import omr_service.api"'),
        test_command=OMR_TESTS,
        # The checkout's sources, not the image copy, so data/ resolves next to them as in the repository.
        test_env=(f'PYTHONPATH={REPOSITORY_MOUNT}/ai/src', 'PYTHONDONTWRITEBYTECODE=1')),
    'playback': Service(
        name='playback', dockerfile='data/playback.Dockerfile',
        inputs=('data/playback.Dockerfile', 'data/playback.Dockerfile.dockerignore', 'data/reference',
                'data/src/audio/jangdan_audio.py', 'data/src/playback', 'data/src/scores'),
        required=('data/playback.Dockerfile', 'data/src/playback/service.py'),
        target='',
        runtime_flags=('--read-only', '--tmpfs', '/tmp:rw,size=512m', '--memory', '1g', '--memory-swap', '1g',
                       '--cpus', '1', '--env', 'PLAYBACK_TIMEOUT_SECONDS=90',
                       '--env', 'PLAYBACK_ASSETS_JSON=/assets/assets.json', '--env', 'PLAYBACK_JANGGU_SAMPLES=/janggu'),
        healthy_status='UP', contract_path='/v1/render', contract_code=400,
        tool_check=('python', '-c', 'from data.src.playback.audio import resolve_encoder; resolve_encoder()'),
        test_command=PLAYBACK_TESTS, test_env=('PYTHONDONTWRITEBYTECODE=1',)),
}
JUNIT_IN_CONTAINER = '/var/tmp/eolssu-service-junit.xml'
OMR_MARKER = 'omr_service'


def service_named(name):
    if name not in SERVICES:
        raise BuildError('unknown service')
    return SERVICES[name]


def image_name(service, sha):
    if not SHA.fullmatch(sha):
        raise BuildError('a full lowercase commit SHA is required')
    return f'{service.repository}:ci-{sha}'


def source_key(service, sha, run=subprocess.run):
    """Stable digest of the git objects the image is built from."""
    image_name(service, sha)
    listing = call(run, ['git', 'ls-tree', '-r', '--full-tree', sha, '--', *service.inputs], timeout=30).stdout
    if any(f'\t{path}\n' not in listing + '\n' for path in service.required):
        raise BuildError(f'commit lacks the {service.name} runtime sources')
    return hashlib.sha256(listing.encode()).hexdigest()


def require_checkout(service, sha, run=subprocess.run):
    """The build context is the working tree, so it must be exactly the commit being labelled."""
    if call(run, ['git', 'rev-parse', 'HEAD'], timeout=30).stdout.strip() != sha:
        raise BuildError('working tree is not at the requested commit')
    if call(run, ['git', 'status', '--porcelain', '--', 'ai', 'data'], timeout=30).stdout.strip():
        raise BuildError('working tree has local changes under the build inputs')


def inspect_labels(run, reference):
    found = call(run, ['docker', 'image', 'inspect', '--format', '{{json .Config.Labels}}', reference],
                 timeout=30, check=False)
    return (json.loads(found.stdout or 'null') or {}) if found.returncode == 0 else None


def deployed_source(service, run=subprocess.run):
    """Source label of the image the running container uses; None when unknown (e.g. a manual build)."""
    found = call(run, ['docker', 'inspect', '--format', '{{.Image}}', service.container], timeout=30, check=False)
    if found.returncode:
        return None
    return (inspect_labels(run, found.stdout.strip()) or {}).get(service.source_label)


def plan(service, sha, run=subprocess.run):
    key = source_key(service, sha, run)
    current = deployed_source(service, run)
    return {'service': service.name, 'action': 'skip' if current == key else 'deploy', 'source': key,
            'deployed_source': current, 'image': image_name(service, sha)}


def build(service, sha, run=subprocess.run):
    image = image_name(service, sha)
    require_checkout(service, sha, run)
    key = source_key(service, sha, run)
    target = ('--target', service.target) if service.target else ()
    # The Dockerfiles use BuildKit syntax (ADD --checksum, <Dockerfile>.dockerignore) and the repository root context.
    # Docker 23+ builds with BuildKit by default (edo1: Docker 29, buildx 0.38).
    built = call(run, ['docker', 'build', '--pull=false', '--progress', 'plain', '--file', service.dockerfile, *target,
                       '--label', f'{REVISION_LABEL}={sha}', '--label', f'{service.source_label}={key}',
                       '--tag', image, '.'], timeout=3600, check=False)
    print(built.stderr[-20000:], end='', flush=True)
    if built.returncode:
        raise BuildError(f'{service.name} image build failed')
    labels = inspect_labels(run, image) or {}
    if labels.get(REVISION_LABEL) != sha or labels.get(service.source_label) != key:
        raise BuildError('built image labels do not match the commit')
    image_id = call(run, ['docker', 'image', 'inspect', '--format', '{{.Id}}', image], timeout=30).stdout.strip()
    return {'service': service.name, 'image': image, 'image_id': image_id, 'source': key}


def omr_tests(directory=Path('ai/tests/unit')):
    """The ai unit test files that use omr_service: the complement of build-ai-image.py's scoring tests."""
    files = sorted(path for path in directory.glob('test_*.py') if OMR_MARKER in path.read_text(encoding='utf-8'))
    if not any(path.name == 'test_omr_api.py' for path in files):
        raise BuildError('OMR API tests are missing')
    return [path.as_posix().removeprefix('ai/') for path in files]


def test_container(service, junit):
    """Working directory and shell command for the service's tests inside the image."""
    if service.name == 'omr':
        files = omr_tests()
        return f'{REPOSITORY_MOUNT}/ai', service.test_command.format(junit=JUNIT_IN_CONTAINER, files=' '.join(files)), \
            len(files)
    return REPOSITORY_MOUNT, service.test_command, None


def test(service, sha, junit, run=subprocess.run):
    image = image_name(service, sha)
    require_checkout(service, sha, run)
    workdir, command, count = test_container(service, junit)
    name = f'eolssu-ci-{service.name}-test-' + uuid.uuid4().hex
    environment = [part for value in service.test_env for part in ('--env', value)]
    # Root only so uv can add the dev group to the image's environment in this throwaway container; the runtime user
    # and read-only file system are checked by smoke.
    try:
        call(run, ['docker', 'create', '--name', name, '--label', f'{VERIFY_LABEL}={service.name}-tests', '--user', '0',
                   '--tmpfs', '/tmp:rw,exec,size=1g', '--env', 'HOME=/tmp/home', *environment, '--workdir', workdir,
                   '--mount', f'type=bind,src={Path.cwd().resolve()},dst={REPOSITORY_MOUNT},readonly',
                   image, 'sh', '-c', command], timeout=60)
        result = call(run, ['docker', 'start', '--attach', name], timeout=3600, check=False)
        print(result.stdout[-20000:], end='', flush=True)
        print(result.stderr[-5000:], end='', flush=True)
        if service.name == 'omr':
            copied = call(run, ['docker', 'cp', name + ':' + JUNIT_IN_CONTAINER, str(junit)], timeout=60, check=False)
        if result.returncode:
            raise BuildError(f'{service.name} tests failed inside the image')
        if service.name == 'omr' and copied.returncode:
            raise BuildError(f'{service.name} test report missing')
    finally:
        remove(run, name)
    return {'service': service.name, 'image': image, 'junit': str(junit) if service.name == 'omr' else None,
            'test_files': count}


def playback_assets(run=subprocess.run):
    """Asset and janggu folders the running playback container mounts (the paths live in the root-only .env)."""
    found = call(run, ['docker', 'inspect', '--format', '{{json .Mounts}}', SERVICES['playback'].container],
                 timeout=30, check=False)
    mounts = {mount.get('Destination'): mount.get('Source') for mount in json.loads(found.stdout or '[]')} \
        if found.returncode == 0 else {}
    if not mounts.get('/assets') or not mounts.get('/janggu'):
        raise BuildError('playback asset folders unknown; pass --assets and --janggu')
    return mounts['/assets'], mounts['/janggu']


def smoke(service, sha, run=subprocess.run, sleep=time.sleep, seconds=90, assets=None, janggu=None):
    image = image_name(service, sha)
    name = f'eolssu-ci-{service.name}-smoke-' + uuid.uuid4().hex
    mounts = ()
    if service.name == 'playback':
        if not (assets and janggu):
            assets, janggu = playback_assets(run)
        mounts = ('--mount', f'type=bind,src={assets},dst=/assets,readonly',
                  '--mount', f'type=bind,src={janggu},dst=/janggu,readonly')
    try:
        call(run, ['docker', 'run', '-d', '--name', name, '--label', f'{VERIFY_LABEL}={service.name}-smoke',
                   '--network', 'none', *service.runtime_flags, *mounts, image], timeout=60)
        user = call(run, ['docker', 'exec', name, 'id', '-u'], timeout=30).stdout.strip()
        if user == '0':
            raise BuildError(f'{service.name} image runs as root')
        deadline = time.monotonic() + seconds
        while True:
            probe = call(run, ['docker', 'exec', name, 'python', '-c', PROBE, service.contract_path], timeout=15,
                         check=False)
            if probe.returncode == 0:
                answer = json.loads(probe.stdout)
                if (answer.get('health') or {}).get('status') == service.healthy_status:
                    break
            running = call(run, ['docker', 'inspect', '--format', '{{.State.Running}}', name], timeout=30,
                           check=False).stdout.strip()
            if running == 'false' or time.monotonic() >= deadline:
                raise BuildError(f'{service.name} /health did not answer in time')
            sleep(1)
        if answer.get('contract') != service.contract_code:
            raise BuildError(f'{service.name} empty request answered {answer.get("contract")}, '
                             f'expected {service.contract_code}')
        call(run, ['docker', 'exec', name, *service.tool_check], timeout=120)
    finally:
        remove(run, name)
    return {'service': service.name, 'image': image, 'health': answer['health'], 'contract': answer['contract']}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('service', choices=sorted(SERVICES))
    parser.add_argument('command', choices=('plan', 'build', 'test', 'smoke'))
    parser.add_argument('revision')
    parser.add_argument('--junit', type=Path, default=Path('service-test-results/junit.xml'))
    parser.add_argument('--assets', help='playback smoke: CAT asset folder (default: the running container mount)')
    parser.add_argument('--janggu', help='playback smoke: janggu sample folder (default: the running container mount)')
    args = parser.parse_args()

    def terminate(signum, _frame):
        signal.signal(signum, signal.SIG_IGN)
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, terminate)
    try:
        service = service_named(args.service)
        if args.command == 'plan':
            result = plan(service, args.revision)
        elif args.command == 'build':
            result = build(service, args.revision)
        elif args.command == 'test':
            args.junit.parent.mkdir(parents=True, exist_ok=True)
            result = test(service, args.revision, args.junit)
        else:
            result = smoke(service, args.revision, assets=args.assets, janggu=args.janggu)
    except (BuildError, OSError, ValueError) as error:
        parser.exit(1, json.dumps({'status': 'failed', 'reason': str(error)}) + '\n')
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
