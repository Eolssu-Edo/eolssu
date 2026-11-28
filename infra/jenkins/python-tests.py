#!/usr/bin/env python3
"""Run the repository's Python tests for the paths changed on develop (S15P31E102-945).

plan: choose test groups from the changed paths between the last successful run and HEAD.
run:  prepare the group's environment and run its suites; quarantined tests run separately.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[2]
GROUPS = ('infra', 'catalog', 'data', 'ai')

# A change to the job itself must re-run every group.
SELF = ('infra/jenkins/python-tests.py', 'infra/jenkins/python-tests.Jenkinsfile',
        'infra/jenkins/python-tests-requirements.txt', 'ai/.python-version')

# First matching prefix wins. Tests read across areas, so a source area also triggers its readers:
# ai and catalog tests read data/src/scores·data/reference·fixtures; data tests read infra/catalog plans;
# infra deploy and catalog tests read backend migrations; catalog tests check docs/knowledge/data names;
# data playback tests count the sigimsae guide symbols in the frontend source.
RULES = (
    ('ai/', ('ai',)),
    ('data/', ('data', 'ai', 'catalog')),
    ('infra/catalog/', ('catalog', 'data')),
    ('infra/', ('infra',)),
    ('backend/src/main/resources/db/migration/', ('infra', 'catalog')),
    ('backend/tools/', ('infra',)),
    ('frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/', ('data',)),
    ('docs/knowledge/data/', ('catalog',)),
)
# Standard-library unittest suites outside infra/ that run with the infra group.
INFRA_EXTRA = ('backend/tools',)
# Areas with no Python tests. Anything not listed here or above selects every group.
NO_PYTHON = ('frontend/', 'backend/', 'blog/', 'docs/', 'TIL/', '.github/')
NO_PYTHON_ROOT = ('.env.dev.example', '.env.prod.example', '.gitattributes', '.gitignore',
                  'docker-compose.dev.yml', 'docker-compose.prod.yml')

# Known failures caused by the CI environment, not by the change under test. They run in a separate,
# non-gating stage so their state stays visible. Paths are relative to the group's working directory.
QUARANTINE = {
    'ai': (
        ('tests/unit/test_staff_rhythm_preservation.py',
         'needs JGB_GENERATOR_DIR (jeongganbo-generator and Node) but has no skip marker'),
        ('tests/unit/test_onset_spread_experiment.py::test_experiment_rejects_wrong_recording_hash',
         'evaluation/dtw_performance.py applies its Windows-only memory limit before the hash check'),
    ),
    'data': (
        ('data/tests/test_scan_register_cli.py', 'needs the OMR model runtime (torch) of the OMR image'),
    ),
}


def classify(path):
    """Groups one changed path selects, or None when the path is outside every known area."""
    if path in SELF:
        return set(GROUPS)
    for prefix, groups in RULES:
        if path.startswith(prefix):
            return set(groups)
    if path.startswith(NO_PYTHON) or path in NO_PYTHON_ROOT or ('/' not in path and path.endswith('.md')):
        return set()
    return None


def select(paths):
    """Return (groups in fixed order, reasons) for a list of changed paths."""
    chosen, reasons = set(), []
    for path in paths:
        groups = classify(path)
        if groups is None:
            reasons.append(f'unknown path selects all groups: {path}')
            groups = set(GROUPS)
        chosen |= groups
    return [g for g in GROUPS if g in chosen], reasons


def git(*args):
    return subprocess.run(['git', '-C', str(ROOT), *args], check=True, capture_output=True).stdout


def changed_paths(base, head):
    """Paths changed from base to head, or None when the range cannot be trusted."""
    if not base:
        return None
    try:
        git('cat-file', '-e', base + '^{commit}')
        git('merge-base', '--is-ancestor', base, head)
    except subprocess.CalledProcessError:
        return None
    out = git('-c', 'core.quotepath=off', 'diff', '--name-only', '-z', '--no-renames', base, head)
    return [p for p in out.decode('utf-8').split('\0') if p]


def plan(base, head, run_all):
    if run_all:
        return list(GROUPS), ['full run requested']
    paths = changed_paths(base, head)
    if paths is None:
        return list(GROUPS), [f'no trusted base for {head[:12]} (base={base[:12] or "none"}); running all groups']
    groups, reasons = select(paths)
    return groups, [f'{len(paths)} changed paths from {base[:12]} to {head[:12]}', *reasons]


def test_dirs(top, exclude=()):
    """Directories under top that hold test_*.py files, so new suites are picked up automatically."""
    found = set()
    for path in (ROOT / top).rglob('test_*.py'):
        rel = path.parent.relative_to(ROOT)
        if 'fixtures' in rel.parts or rel.as_posix().startswith(exclude):
            continue
        found.add(rel.as_posix())
    return sorted(found)


def python_version():
    return (ROOT / 'ai/.python-version').read_text(encoding='utf-8').strip()


def data_python(work, runner):
    """Shared venv for data and catalog tests with the pinned requirements."""
    venv = work / 'venv-data'
    python = venv / 'bin/python'
    if runner(['uv', 'venv', '--quiet', '--allow-existing', '--python', python_version(), str(venv)], ROOT) != 0:
        return None
    req = ROOT / 'infra/jenkins/python-tests-requirements.txt'
    if runner(['uv', 'pip', 'install', '--quiet', '--python', str(python), '-r', str(req)], ROOT) != 0:
        return None
    return str(python)


def suites(group, work, runner, quarantine):
    """Yield (name, command, cwd, env) for a group, or a single failing setup entry."""
    junit = work / 'results'
    marked = QUARANTINE.get(group, ())
    if group == 'infra':
        if quarantine:
            return
        dirs = test_dirs('infra', exclude=('infra/catalog',))
        for extra in INFRA_EXTRA:
            dirs += test_dirs(extra)
        for d in dirs:
            yield d, ['python3', '-B', '-m', 'unittest', 'discover', '-s', d, '-p', 'test_*.py', '-v'], ROOT, {}
    elif group == 'catalog':
        if quarantine:
            return
        python = data_python(work, runner)
        if python is None:
            yield 'catalog setup', None, ROOT, {}
            return
        yield 'infra/catalog', [python, '-B', '-m', 'unittest', 'discover', '-s', 'infra/catalog', '-p', 'test_*.py', '-v'], ROOT, {}
    elif group == 'data':
        if quarantine and not marked:
            return
        python = data_python(work, runner)
        if python is None:
            yield 'data setup', None, ROOT, {}
            return
        base = [python, '-B', '-m', 'pytest', '-p', 'no:cacheprovider', '--import-mode=importlib', '-rs']
        if quarantine:
            yield 'data quarantine', [*base, *[n for n, _ in marked]], ROOT, {}
        else:
            yield 'data', [*base, f'--junitxml={junit / "data.xml"}',
                           *[f'--deselect={n}' for n, _ in marked], *test_dirs('data')], ROOT, {}
    elif group == 'ai':
        if quarantine and not marked:
            return
        env = {'UV_PROJECT_ENVIRONMENT': str(work / 'venv-ai')}
        if runner(['uv', 'sync', '--locked', '--quiet'], ROOT / 'ai', env) != 0:
            yield 'ai setup', None, ROOT / 'ai', env
            return
        base = ['uv', 'run', '--locked', '--no-sync', 'pytest', '-p', 'no:cacheprovider', '-rs']
        if quarantine:
            yield 'ai quarantine', [*base, *[n for n, _ in marked]], ROOT / 'ai', env
        else:
            yield 'ai', [*base, f'--junitxml={junit / "ai.xml"}', *[f'--deselect={n}' for n, _ in marked]], ROOT / 'ai', env
    else:
        raise ValueError(f'unknown group: {group}')


def call(cmd, cwd, env=None):
    merged = {**os.environ, **(env or {})}
    sys.stdout.flush()
    return subprocess.run(cmd, cwd=cwd, env=merged).returncode


def run(group, work, quarantine=False, runner=call):
    work = Path(work).resolve()
    (work / 'results').mkdir(parents=True, exist_ok=True)
    tmp = work / 'tmp'
    tmp.mkdir(parents=True, exist_ok=True)
    rows = []
    if quarantine:
        for name, reason in QUARANTINE.get(group, ()):
            print(f'[quarantine] {group}: {name} — {reason}', flush=True)
    entries = suites(group, work, runner, quarantine)
    while True:
        started = time.monotonic()  # includes the environment setup done before the first suite
        try:
            name, cmd, cwd, env = next(entries)
        except StopIteration:
            break
        if cmd is None:
            code = 1
        else:
            print(f'=== {name}', flush=True)
            # A resolved, non-symlink TMPDIR: some tests reject symlinked destinations.
            code = runner(cmd, cwd, {**env, 'TMPDIR': str(tmp)})
        rows.append((name, code, time.monotonic() - started))
    print(f'\n{"suite":<32} {"result":<7} seconds')
    for name, code, secs in rows:
        print(f'{name:<32} {"ok" if code == 0 else "FAILED":<7} {secs:.1f}')
    return 0 if all(code == 0 for _, code, _ in rows) else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('plan', help='print the comma-separated groups to run')
    p.add_argument('--base', default='')
    p.add_argument('--head', required=True)
    p.add_argument('--all', action='store_true')
    r = sub.add_parser('run', help='run one group')
    r.add_argument('group', choices=GROUPS)
    r.add_argument('--work', required=True, help='directory for virtualenvs, results and TMPDIR')
    r.add_argument('--quarantine', action='store_true', help='run only the quarantined tests of the group')
    args = parser.parse_args(argv)
    if args.command == 'plan':
        groups, reasons = plan(args.base, args.head, args.all)
        for reason in reasons:
            print(reason, file=sys.stderr)
        print(','.join(groups))
        return 0
    return run(args.group, args.work, args.quarantine)


if __name__ == '__main__':
    sys.exit(main())
