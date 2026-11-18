"""omr·playback 이미지 빌드·이미지 안 테스트·실행 확인 도구를 Git/Docker 대역으로 검사한다(S15P31E102-951)."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

HERE = Path(__file__).parents[1]
REPOSITORY = Path(__file__).parents[3]
spec = importlib.util.spec_from_file_location('build_service', HERE / 'build-service-image.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)

SHA = 'a' * 40
OMR = build.SERVICES['omr']
PLAYBACK = build.SERVICES['playback']
LISTINGS = {
    'omr': ('100644 blob 1111\tai/omr.Dockerfile\n100644 blob 2222\tai/src/omr_service/api.py\n'
            '100644 blob 3333\tai/uv.lock\n100644 blob 4444\tdata/reference/songs.json\n'),
    'playback': ('100644 blob 5555\tdata/playback.Dockerfile\n100644 blob 6666\tdata/src/playback/service.py\n'
                 '100644 blob 7777\tdata/reference/songs.json\n'),
}


def done(args, stdout='', code=0, stderr=''):
    return subprocess.CompletedProcess(args, code, stdout, stderr)


class Boundary:
    """git·docker 명령만 대신한다. 명령 순서와 인자를 기록한다."""

    def __init__(self, service, **overrides):
        self.service = service
        self.calls = []
        self.head = SHA
        self.dirty = ''
        self.listing = LISTINGS[service.name]
        self.running = 'sha256:run'
        self.running_labels = {service.source_label: 'old'}
        self.built_labels = None
        self.build_code = 0
        self.test_code = 0
        self.health_after = 0
        self.health_calls = 0
        self.health = {'status': service.healthy_status}
        self.contract = service.contract_code
        self.exited = False
        self.tool_code = 0
        self.user = '10001'
        self.mounts = [{'Destination': '/assets', 'Source': '/opt/eolssu/playback-assets/v1'},
                       {'Destination': '/janggu', 'Source': '/opt/eolssu/playback-assets/v1/janggu'}]
        self.__dict__.update(overrides)

    def __call__(self, args, **kwargs):
        self.calls.append(args)
        if args[:2] == ['git', 'ls-tree']:
            return done(args, self.listing)
        if args[:2] == ['git', 'rev-parse']:
            return done(args, self.head + '\n')
        if args[:2] == ['git', 'status']:
            return done(args, self.dirty)
        if args[:2] == ['docker', 'inspect']:
            if '{{json .Mounts}}' in args:
                return done(args, json.dumps(self.mounts)) if self.mounts is not None else done(args, '', 1)
            if '{{.State.Running}}' in args:
                return done(args, 'false\n' if self.exited else 'true\n')
            return done(args, self.running + '\n') if self.running else done(args, '', 1)
        if args[:3] == ['docker', 'image', 'inspect']:
            if '{{.Id}}' in args:
                return done(args, 'sha256:built\n')
            if args[-1] == self.running:
                return done(args, json.dumps(self.running_labels))
            return done(args, json.dumps(self.built_labels))
        if args[:2] == ['docker', 'build']:
            labels = [args[i + 1] for i, a in enumerate(args) if a == '--label']
            self.built_labels = self.built_labels or dict(label.split('=', 1) for label in labels)
            return done(args, '', self.build_code, 'build log\n')
        if args[:2] == ['docker', 'start']:
            return done(args, '5 passed\n', self.test_code)
        if args[:2] == ['docker', 'cp']:
            Path(args[-1]).write_text('<testsuite/>')
            return done(args)
        if args[:2] == ['docker', 'exec']:
            if args[3:5] == ['id', '-u']:
                return done(args, self.user + '\n')
            if args[3:5] == ['python', '-c'] and args[5] == build.PROBE:
                self.health_calls += 1
                if self.health_calls <= self.health_after:
                    return done(args, '', 1)
                return done(args, json.dumps({'health': self.health, 'contract': self.contract}))
            return done(args, '', self.tool_code)
        return done(args)

    def commands(self, *prefix):
        return [c for c in self.calls if c[:len(prefix)] == list(prefix)]


class DefinitionTests(unittest.TestCase):
    def test_source_inputs_cover_everything_each_dockerignore_lets_into_the_context(self):
        for service in build.SERVICES.values():
            ignore = (REPOSITORY / (service.dockerfile + '.dockerignore')).read_text(encoding='utf-8')
            allowed = [line[1:].rstrip('/').removesuffix('/**') for line in ignore.splitlines() if line.startswith('!')]
            for path in allowed:
                # Parent folders are re-included only so their children can be; the children are listed too.
                if any(other != path and other.startswith(path + '/') for other in allowed):
                    continue
                self.assertTrue(any(path == entry or path.startswith(entry + '/') for entry in service.inputs),
                                f'{service.name}: {path} reaches the image but not the source key')
            self.assertIn(service.dockerfile, service.inputs)
            self.assertIn(service.dockerfile + '.dockerignore', service.inputs)

    def test_runtime_flags_match_the_compose_limits(self):
        compose = (REPOSITORY / 'infra/runtime/compose.yml').read_text(encoding='utf-8')
        self.assertIn('mem_limit: 2g', compose.split('  omr:')[1].split('\n\n')[0])
        self.assertIn('/tmp:rw,exec,size=1g', compose.split('  omr:')[1].split('\n\n')[0])
        playback = compose.split('  playback:')[1].split('\n\n')[0]
        for value in ('mem_limit: 1g', 'memswap_limit: 1g', 'cpus: 1', '/tmp:rw,size=512m',
                      'PLAYBACK_ASSETS_JSON: /assets/assets.json', 'PLAYBACK_JANGGU_SAMPLES: /janggu'):
            self.assertIn(value, playback)
        self.assertIn('/tmp:rw,exec,size=1g', OMR.runtime_flags)
        self.assertIn('/tmp:rw,size=512m', PLAYBACK.runtime_flags)

    def test_omr_and_scoring_test_selections_split_the_ai_unit_tests(self):
        cwd = os.getcwd()
        os.chdir(REPOSITORY)
        self.addCleanup(os.chdir, cwd)
        omr = set(build.omr_tests())
        scoring = set(build.ai.scoring_tests())
        everything = {path.as_posix().removeprefix('ai/') for path in Path('ai/tests/unit').glob('test_*.py')}
        self.assertFalse(omr & scoring)
        self.assertEqual(omr | scoring, everything)


class PlanAndBuildTests(unittest.TestCase):
    def test_source_key_depends_only_on_the_service_inputs(self):
        for service in (OMR, PLAYBACK):
            key = build.source_key(service, SHA, Boundary(service))
            self.assertRegex(key, '^[0-9a-f]{64}$')
            changed = Boundary(service, listing=LISTINGS[service.name].replace('blob 1', 'blob 9').replace('blob 5', 'blob 9'))
            self.assertNotEqual(build.source_key(service, SHA, changed), key)
            listed = Boundary(service)
            build.source_key(service, SHA, listed)
            self.assertNotIn('ai/tests', listed.commands('git', 'ls-tree')[0])

    def test_commit_without_the_service_sources_is_refused(self):
        with self.assertRaisesRegex(build.BuildError, 'lacks the omr'):
            build.source_key(OMR, SHA, Boundary(OMR, listing=LISTINGS['playback']))

    def test_plan_skips_only_when_running_image_has_same_source(self):
        key = build.source_key(PLAYBACK, SHA, Boundary(PLAYBACK))
        same = Boundary(PLAYBACK, running_labels={PLAYBACK.source_label: key})
        self.assertEqual(build.plan(PLAYBACK, SHA, same)['action'], 'skip')
        self.assertEqual(same.commands('docker', 'inspect')[0][-1], 'eolssu-runtime-playback-1')
        self.assertEqual(build.plan(PLAYBACK, SHA, Boundary(PLAYBACK))['action'], 'deploy')
        # 수동 빌드 이미지(라벨 없음)나 컨테이너가 없으면 배포한다.
        self.assertEqual(build.plan(PLAYBACK, SHA, Boundary(PLAYBACK, running_labels=None))['action'], 'deploy')
        self.assertEqual(build.plan(OMR, SHA, Boundary(OMR, running=''))['action'], 'deploy')

    def test_omr_builds_scanner_target_from_repository_root(self):
        boundary = Boundary(OMR)
        result = build.build(OMR, SHA, boundary)
        command = boundary.commands('docker', 'build')[0]
        self.assertEqual(command[-1], '.')
        self.assertEqual(command[command.index('--file') + 1], 'ai/omr.Dockerfile')
        self.assertEqual(command[command.index('--target') + 1], 'scanner')
        self.assertIn('eolssu-omr:ci-' + SHA, command)
        self.assertIn(build.REVISION_LABEL + '=' + SHA, command)
        self.assertEqual(result['source'], build.source_key(OMR, SHA, Boundary(OMR)))

    def test_playback_builds_default_stage(self):
        boundary = Boundary(PLAYBACK)
        build.build(PLAYBACK, SHA, boundary)
        command = boundary.commands('docker', 'build')[0]
        self.assertNotIn('--target', command)
        self.assertEqual(command[command.index('--file') + 1], 'data/playback.Dockerfile')

    def test_build_refuses_other_commit_dirty_tree_bad_sha_or_failed_build(self):
        for boundary in (Boundary(OMR, head='b' * 40), Boundary(OMR, dirty=' M data/reference/songs.json\n')):
            with self.assertRaises(build.BuildError):
                build.build(OMR, SHA, boundary)
            self.assertEqual(boundary.commands('docker', 'build'), [])
        with self.assertRaises(build.BuildError):
            build.build(OMR, 'develop', Boundary(OMR))
        with self.assertRaisesRegex(build.BuildError, 'build failed'):
            build.build(OMR, SHA, Boundary(OMR, build_code=1))
        with self.assertRaisesRegex(build.BuildError, 'labels'):
            build.build(OMR, SHA, Boundary(OMR, built_labels={build.REVISION_LABEL: 'b' * 40}))

    def test_unknown_service_is_rejected(self):
        with self.assertRaises(build.BuildError):
            build.service_named('ai')


class GateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.cwd = os.getcwd()
        os.chdir(self.tmp.name)
        self.addCleanup(os.chdir, self.cwd)
        tests = Path('ai/tests/unit')
        tests.mkdir(parents=True)
        (tests / 'test_api.py').write_text('from ai_service import api\n')
        (tests / 'test_omr_api.py').write_text('from omr_service import api\n')
        (tests / 'test_staff_tempo.py').write_text('from omr_service.staff import tempo\n')
        self.junit = Path('out/junit.xml')
        self.junit.parent.mkdir()

    def test_omr_tests_run_in_image_with_its_tools_against_read_only_checkout(self):
        boundary = Boundary(OMR)
        result = build.test(OMR, SHA, self.junit, boundary)
        create = boundary.commands('docker', 'create')[0]
        self.assertIn('eolssu-omr:ci-' + SHA, create)
        mounts = [create[i + 1] for i, a in enumerate(create) if a == '--mount']
        self.assertEqual(mounts, [f'type=bind,src={Path.cwd().resolve()},dst=/repo,readonly'])
        self.assertEqual(create[create.index('--workdir') + 1], '/repo/ai')
        self.assertIn('PYTHONPATH=/repo/ai/src', create)
        command = create[-1]
        self.assertIn('tests/unit/test_omr_api.py tests/unit/test_staff_tempo.py', command)
        self.assertNotIn('test_api.py ', command)
        # 스캔 확장이 올린 패키지 버전을 바꾸지 않고 dev 그룹만 더한다.
        self.assertIn('--no-deps --constraint /tmp/installed.txt', command)
        self.assertNotIn('uv sync', command)
        self.assertEqual(result['test_files'], 2)
        self.assertTrue(self.junit.exists())
        self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_playback_tests_run_with_unittest_from_repository_root(self):
        boundary = Boundary(PLAYBACK)
        result = build.test(PLAYBACK, SHA, self.junit, boundary)
        create = boundary.commands('docker', 'create')[0]
        self.assertEqual(create[create.index('--workdir') + 1], '/repo')
        self.assertIn('unittest discover -s data/src/playback/tests', create[-1])
        self.assertEqual(boundary.commands('docker', 'cp'), [])
        self.assertIsNone(result['junit'])

    def test_failing_tests_fail_the_gate_and_still_clean_up(self):
        for service in (OMR, PLAYBACK):
            boundary = Boundary(service, test_code=1)
            with self.assertRaisesRegex(build.BuildError, 'tests failed'):
                build.test(service, SHA, self.junit, boundary)
            self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_missing_omr_api_tests_are_reported_before_docker(self):
        Path('ai/tests/unit/test_omr_api.py').unlink()
        boundary = Boundary(OMR)
        with self.assertRaisesRegex(build.BuildError, 'OMR API tests'):
            build.test(OMR, SHA, self.junit, boundary)
        self.assertEqual(boundary.commands('docker', 'create'), [])


class SmokeTests(unittest.TestCase):
    def smoke(self, service, boundary, **kwargs):
        return build.smoke(service, SHA, boundary, sleep=lambda _: None, seconds=0.5, **kwargs)

    def test_omr_runs_like_production_and_checks_contract_and_tools(self):
        boundary = Boundary(OMR, health_after=2)
        result = self.smoke(OMR, boundary)
        started = boundary.commands('docker', 'run')[0]
        for flag in ('--read-only', '--network', 'none', '/tmp:rw,exec,size=1g', '2g'):
            self.assertIn(flag, started)
        self.assertNotIn('--user', started)
        self.assertEqual(result['contract'], 422)
        tools = [c for c in boundary.commands('docker', 'exec') if c[3:] == list(OMR.tool_check)]
        self.assertEqual(len(tools), 1)
        self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_playback_mounts_the_running_service_assets_read_only(self):
        boundary = Boundary(PLAYBACK)
        self.smoke(PLAYBACK, boundary)
        started = boundary.commands('docker', 'run')[0]
        mounts = [started[i + 1] for i, a in enumerate(started) if a == '--mount']
        self.assertEqual(mounts, ['type=bind,src=/opt/eolssu/playback-assets/v1,dst=/assets,readonly',
                                  'type=bind,src=/opt/eolssu/playback-assets/v1/janggu,dst=/janggu,readonly'])
        for flag in ('--memory-swap', 'PLAYBACK_ASSETS_JSON=/assets/assets.json'):
            self.assertIn(flag, started)
        explicit = Boundary(PLAYBACK, mounts=None)
        self.smoke(PLAYBACK, explicit, assets='/a', janggu='/j')
        self.assertIn('type=bind,src=/a,dst=/assets,readonly', explicit.commands('docker', 'run')[0])
        with self.assertRaisesRegex(build.BuildError, 'asset folders unknown'):
            self.smoke(PLAYBACK, Boundary(PLAYBACK, mounts=None))

    def test_wrong_contract_root_user_or_missing_tool_is_rejected(self):
        cases = ((Boundary(OMR, contract=500), 'expected 422'), (Boundary(OMR, user='0'), 'root'),
                 (Boundary(PLAYBACK, tool_code=1), 'command failed'))
        for boundary, message in cases:
            with self.assertRaisesRegex(build.BuildError, message):
                self.smoke(boundary.service, boundary)
            self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_health_timeout_or_exit_is_reported(self):
        with self.assertRaisesRegex(build.BuildError, 'health'):
            build.smoke(OMR, SHA, Boundary(OMR, health_after=10**6), sleep=lambda _: None, seconds=0)
        exited = Boundary(PLAYBACK, health_after=10**6, exited=True)
        with self.assertRaisesRegex(build.BuildError, 'health'):
            build.smoke(PLAYBACK, SHA, exited, sleep=lambda _: None, seconds=60)
        self.assertEqual(exited.health_calls, 1)


if __name__ == '__main__':
    unittest.main()
