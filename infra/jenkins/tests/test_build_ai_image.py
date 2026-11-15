"""ai 이미지 빌드·테스트 게이트·실행 확인 도구를 Git/Docker 대역으로 검사한다(S15P31E102-244)."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('build_ai', Path(__file__).parents[1] / 'build-ai-image.py')
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)

SHA = 'a' * 40
LISTING = ('100644 blob 1111\tai/.dockerignore\n100644 blob 2222\tai/Dockerfile\n'
           '100644 blob 3333\tai/src/ai_service/api.py\n100644 blob 4444\tai/uv.lock\n')


def done(args, stdout='', code=0, stderr=''):
    return subprocess.CompletedProcess(args, code, stdout, stderr)


class Boundary:
    """git·docker 명령만 대신한다. 명령 순서와 인자를 기록한다."""

    def __init__(self, **overrides):
        self.calls = []
        self.head = SHA
        self.dirty = ''
        self.listing = LISTING
        self.running = 'sha256:run'
        self.running_labels = {build.SOURCE_LABEL: 'old'}
        self.built_labels = None
        self.test_code = 0
        self.health_after = 0
        self.health_calls = 0
        self.warm = '{"status": "ok", "ms": 900}'
        self.user = '10001'
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
            return done(args)
        if args[:2] == ['docker', 'start']:
            return done(args, '5 passed\n', self.test_code)
        if args[:2] == ['docker', 'cp']:
            Path(args[-1]).write_text('<testsuite/>')
            return done(args)
        if args[:2] == ['docker', 'exec']:
            if args[3:5] == ['id', '-u']:
                return done(args, self.user + '\n')
            if '-c' in args:
                self.health_calls += 1
                if self.health_calls <= self.health_after:
                    return done(args, '', 1)
                return done(args, json.dumps({'status': 'ok', 'modelVersion': 'pyin-9'}))
            return done(args, self.warm + '\n', 0 if '"ok"' in self.warm else 1)
        return done(args)

    def commands(self, *prefix):
        return [c for c in self.calls if c[:len(prefix)] == list(prefix)]


class PlanAndBuildTests(unittest.TestCase):
    def test_source_key_depends_only_on_runtime_inputs(self):
        key = build.source_key(SHA, Boundary())
        self.assertRegex(key, '^[0-9a-f]{64}$')
        changed = Boundary(listing=LISTING.replace('3333', '9999'))
        self.assertNotEqual(build.source_key(SHA, changed), key)
        ls_tree = Boundary()
        build.source_key(SHA, ls_tree)
        self.assertNotIn('ai/tests', ls_tree.commands('git', 'ls-tree')[0])

    def test_plan_skips_when_running_image_has_same_source(self):
        key = build.source_key(SHA, Boundary())
        self.assertEqual(build.plan(SHA, Boundary(running_labels={build.SOURCE_LABEL: key}))['action'], 'skip')
        self.assertEqual(build.plan(SHA, Boundary())['action'], 'deploy')
        # 수동 빌드 이미지(라벨 없음)나 컨테이너가 없으면 배포한다.
        self.assertEqual(build.plan(SHA, Boundary(running_labels=None))['action'], 'deploy')
        self.assertEqual(build.plan(SHA, Boundary(running=''))['action'], 'deploy')

    def test_build_labels_image_with_commit_and_source(self):
        boundary = Boundary()
        result = build.build(SHA, boundary)
        command = boundary.commands('docker', 'build')[0]
        self.assertEqual(command[-1], 'ai')
        self.assertIn('eolssu-ai:ci-' + SHA, command)
        self.assertIn(build.REVISION_LABEL + '=' + SHA, command)
        self.assertEqual(result['source'], build.source_key(SHA, Boundary()))

    def test_build_refuses_other_commit_dirty_tree_or_bad_sha(self):
        for boundary in (Boundary(head='b' * 40), Boundary(dirty=' M ai/src/x.py\n')):
            with self.assertRaises(build.BuildError):
                build.build(SHA, boundary)
            self.assertEqual(boundary.commands('docker', 'build'), [])
        with self.assertRaises(build.BuildError):
            build.build('develop', Boundary())

    def test_build_rejects_image_whose_labels_do_not_match(self):
        boundary = Boundary(built_labels={build.REVISION_LABEL: 'b' * 40})
        with self.assertRaisesRegex(build.BuildError, 'labels'):
            build.build(SHA, boundary)


class GateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.cwd = os.getcwd()
        os.chdir(self.tmp.name)
        self.addCleanup(os.chdir, self.cwd)
        for source, _ in build.TEST_MOUNTS:
            Path(source).mkdir(parents=True)
        self.junit = Path('out/junit.xml')
        self.junit.parent.mkdir()

    def test_tests_run_in_image_with_read_only_inputs_and_report_is_copied(self):
        boundary = Boundary()
        build.test(SHA, self.junit, boundary)
        create = boundary.commands('docker', 'create')[0]
        self.assertIn('eolssu-ai:ci-' + SHA, create)
        mounts = [create[i + 1] for i, a in enumerate(create) if a == '--mount']
        self.assertEqual(len(mounts), len(build.TEST_MOUNTS))
        self.assertTrue(all(m.endswith(',readonly') for m in mounts))
        self.assertTrue(self.junit.exists())
        self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_failing_tests_fail_the_gate_and_still_clean_up(self):
        boundary = Boundary(test_code=1)
        with self.assertRaisesRegex(build.BuildError, 'tests failed'):
            build.test(SHA, self.junit, boundary)
        self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_missing_test_input_is_reported_before_docker(self):
        Path('data/src/scores').rmdir()
        boundary = Boundary()
        with self.assertRaisesRegex(build.BuildError, 'data/src/scores'):
            build.test(SHA, self.junit, boundary)
        self.assertEqual(boundary.commands('docker', 'create'), [])


class SmokeTests(unittest.TestCase):
    def smoke(self, boundary):
        return build.smoke(SHA, boundary, sleep=lambda _: None, seconds=0.5)

    def test_runtime_like_container_answers_health_and_scores(self):
        boundary = Boundary(health_after=2)
        result = self.smoke(boundary)
        started = boundary.commands('docker', 'run')[0]
        for flag in ('--read-only', '--network', 'none', '/tmp:rw,size=512m'):
            self.assertIn(flag, started)
        self.assertNotIn('--user', started)
        self.assertEqual(result['model_version'], 'pyin-9')
        self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_root_user_or_failed_scoring_is_rejected(self):
        for boundary, message in ((Boundary(user='0'), 'root'),
                                  (Boundary(warm='{"status": "error", "error": "RuntimeError"}'), 'RuntimeError')):
            with self.assertRaisesRegex(build.BuildError, message):
                self.smoke(boundary)
            self.assertEqual(len(boundary.commands('docker', 'rm', '-f')), 1)

    def test_health_timeout_is_reported(self):
        with self.assertRaisesRegex(build.BuildError, 'health'):
            build.smoke(SHA, Boundary(health_after=10**6), sleep=lambda _: None, seconds=0)


if __name__ == '__main__':
    unittest.main()
