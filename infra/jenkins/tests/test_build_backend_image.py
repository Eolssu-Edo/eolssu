"""CI images must contain the tested artifact without a second compilation."""
import hashlib
import importlib.util
import os
from pathlib import Path
import selectors
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).parents[1] / 'build-backend-image.py'


def load_builder():
    spec = importlib.util.spec_from_file_location('backend_image', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class DockerBoundary:
    """Only Docker is substituted; artifact selection, copying and hashing are real."""
    def __init__(self, artifact=b'tested executable jar', extracted=None, fail_copy=False):
        self.artifact = artifact
        self.extracted = artifact if extracted is None else extracted
        self.fail_copy = fail_copy
        self.calls = []
        self.context = None
        self.container = None

    def __call__(self, args, **kwargs):
        self.calls.append(args)
        if args[:2] == ['docker', 'build']:
            self.context = Path(args[-1])
            assert [p.name for p in self.context.iterdir()] == ['app.jar']
            assert (self.context / 'app.jar').read_bytes() == self.artifact
        elif args[:2] == ['docker', 'create']:
            self.container = args[args.index('--name') + 1]
            return subprocess.CompletedProcess(args, 0, 'c' * 64 + '\n')
        elif args[:2] == ['docker', 'cp']:
            if self.fail_copy:
                raise subprocess.CalledProcessError(1, args)
            Path(args[-1]).write_bytes(self.extracted)
        return subprocess.CompletedProcess(args, 0, '')


class BuildBackendImageTests(unittest.TestCase):
    def setUp(self):
        self.builder = load_builder()
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.libs = self.root / 'libs'
        self.libs.mkdir()
        self.jar = self.libs / 'application-1.0.jar'
        self.jar.write_bytes(b'tested executable jar')
        (self.libs / 'application-1.0-plain.jar').write_bytes(b'plain jar')
        self.dockerfile = self.root / 'Dockerfile'
        self.dockerfile.write_text('FROM scratch\nCOPY app.jar /app/app.jar\n')
        self.docker = DockerBoundary()

    def build(self, revision='a' * 40):
        return self.builder.build_image(revision, self.libs, self.dockerfile, run=self.docker)

    def test_copies_only_the_executable_artifact_and_checks_image_bytes(self):
        result = self.build()
        self.assertEqual(result['jar_sha256'], hashlib.sha256(self.jar.read_bytes()).hexdigest())
        self.assertEqual(result['image'], 'eolssu-backend:ci-' + 'a' * 40)
        self.assertFalse(self.docker.context.exists())
        self.assertIn(['docker', 'rm', self.docker.container], self.docker.calls)

    def test_plain_only_missing_and_ambiguous_artifacts_fail_before_docker(self):
        self.jar.unlink()
        with self.assertRaises(ValueError):
            self.build()
        self.jar.write_bytes(b'first')
        (self.libs / 'another.jar').write_bytes(b'second')
        with self.assertRaises(ValueError):
            self.build()
        self.assertEqual(self.docker.calls, [])

    def test_symlink_artifact_is_not_sent_to_docker(self):
        self.jar.unlink()
        secret = self.root / 'outside.jar'
        secret.write_bytes(b'must not be copied')
        self.jar.symlink_to(secret)
        with self.assertRaises(ValueError):
            self.build()
        self.assertEqual(self.docker.calls, [])

    def test_invalid_revision_is_rejected_before_image_creation(self):
        for revision in ['', 'main', 'A' * 40, 'a' * 39, 'a' * 41]:
            with self.subTest(revision=revision), self.assertRaises(ValueError):
                self.build(revision)
        self.assertEqual(self.docker.calls, [])

    def test_different_image_artifact_fails_and_removes_verification_container(self):
        self.docker = DockerBoundary(extracted=b'recompiled different jar')
        with self.assertRaisesRegex(ValueError, 'does not match'):
            self.build()
        self.assertIn(['docker', 'rm', self.docker.container], self.docker.calls)
        self.assertFalse(self.docker.context.exists())

    def test_copy_failure_still_removes_verification_container(self):
        self.docker = DockerBoundary(fail_copy=True)
        with self.assertRaises(subprocess.CalledProcessError):
            self.build()
        self.assertIn(['docker', 'rm', self.docker.container], self.docker.calls)
        self.assertFalse(self.docker.context.exists())

    def check_termination(self, pause_action):
        executable = self.root / 'docker'
        executable.write_text("""#!/usr/bin/env python3
import os
from pathlib import Path
import sys

action = sys.argv[1]
state = Path(os.environ['VERIFY_STATE'])
if action == os.environ['VERIFY_PAUSE_ACTION']:
    print('VERIFY_ACTION_READY', flush=True)
    sys.stdin.read()
if action == 'create':
    name = sys.argv[sys.argv.index('--name') + 1] if '--name' in sys.argv else 'c' * 64
    state.write_text(name)
    print('c' * 64)
elif action == 'cp':
    Path(sys.argv[-1]).write_bytes(b'tested executable jar')
elif action == 'rm':
    assert sys.argv[2] == state.read_text()
    state.unlink()
""")
        executable.chmod(0o700)
        state = self.root / 'verification-container'
        env = dict(os.environ, PATH=str(self.root) + os.pathsep + os.environ['PATH'],
                   VERIFY_STATE=str(state), VERIFY_PAUSE_ACTION=pause_action, TMPDIR=str(self.root))
        process = subprocess.Popen([sys.executable, '-B', str(SCRIPT), 'a' * 40,
                                    '--libs', str(self.libs), '--dockerfile', str(self.dockerfile)],
                                   env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE, text=True)
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(process.stdout, selectors.EVENT_READ)
                self.assertTrue(selector.select(timeout=5), 'Docker verification did not start')
                self.assertEqual(process.stdout.readline().strip(), 'VERIFY_ACTION_READY')
            self.assertTrue(state.exists())
            process.terminate()
            if pause_action == 'rm':
                with self.assertRaises(subprocess.TimeoutExpired):
                    process.wait(timeout=0.5)
            _, stderr = process.communicate(timeout=5)
            self.assertFalse(state.exists(), 'SIGTERM left the verification container behind')
            self.assertFalse(list(self.root.glob('eolssu-backend-image-*')))
            self.assertEqual(process.returncode, 143, stderr)
        finally:
            if process.poll() is None:
                process.kill()
            if not process.stdout.closed:
                process.communicate(timeout=5)

    def test_termination_during_verification_cleans_up(self):
        self.check_termination('cp')

    def test_termination_during_removal_waits_for_cleanup(self):
        self.check_termination('rm')


if __name__ == '__main__':
    unittest.main()
