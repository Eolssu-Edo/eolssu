"""CI images must contain the tested artifact without a second compilation."""
import hashlib
import importlib.util
from pathlib import Path
import subprocess
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

    def __call__(self, args, **kwargs):
        self.calls.append(args)
        if args[:2] == ['docker', 'build']:
            self.context = Path(args[-1])
            assert [p.name for p in self.context.iterdir()] == ['app.jar']
            assert (self.context / 'app.jar').read_bytes() == self.artifact
        elif args[:2] == ['docker', 'create']:
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
        self.assertIn(['docker', 'rm', 'c' * 64], self.docker.calls)

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
        self.assertIn(['docker', 'rm', 'c' * 64], self.docker.calls)
        self.assertFalse(self.docker.context.exists())

    def test_copy_failure_still_removes_verification_container(self):
        self.docker = DockerBoundary(fail_copy=True)
        with self.assertRaises(subprocess.CalledProcessError):
            self.build()
        self.assertIn(['docker', 'rm', 'c' * 64], self.docker.calls)
        self.assertFalse(self.docker.context.exists())


if __name__ == '__main__':
    unittest.main()
