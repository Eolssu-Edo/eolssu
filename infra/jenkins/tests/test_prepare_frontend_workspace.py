"""Real Git workspace cleanup preserves incremental frontend outputs."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(os.environ.get('PREPARE_FRONTEND_SCRIPT', str(Path(__file__).parents[1] / 'prepare-frontend-workspace.sh')))


class PrepareFrontendWorkspaceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.git('init')
        (self.root / '.gitignore').write_text('.gradle/\n.kotlin/\n**/build/\nnode_modules/\n!src/**/build/\n')
        self.source = self.root / 'frontend/src/Tracked.kt'
        self.source.parent.mkdir(parents=True)
        self.source.write_text('original source')
        self.git('add', '.')
        self.commit('initial')
        self.first = self.git('rev-parse', 'HEAD')
        self.caches = []
        for name in ['frontend/.gradle/configuration-cache/state', 'frontend/.kotlin/session',
                     'frontend/shared/build/compiler.bin', 'frontend/webApp/build/dist/index.html',
                     'frontend/build/js/node_modules/package/file.js']:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b'cached-output')
            self.caches.append(path)

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.root), *args], capture_output=True,
                              text=True, check=True).stdout.strip()

    def commit(self, message):
        self.git('-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.test',
                 '-c', 'commit.gpgsign=false', 'commit', '-m', message)

    def prepare(self, cwd=None):
        return subprocess.run(['bash', str(SCRIPT)], cwd=cwd or self.root,
                              capture_output=True, text=True)

    def test_restores_source_removes_untracked_source_and_keeps_cached_bytes(self):
        self.source.write_text('dirty source')
        stray = self.source.with_name('Unexpected.kt')
        stray.write_text('must not compile')
        result = self.prepare()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.source.read_text(), 'original source')
        self.assertFalse(stray.exists())
        self.assertEqual(self.git('rev-parse', 'HEAD'), self.first)
        for path in self.caches:
            self.assertTrue(path.is_file(), str(path))
            self.assertEqual(path.read_bytes(), b'cached-output')

    def test_checkout_can_remove_old_source_without_removing_outputs(self):
        self.source.unlink()
        new_source = self.source.with_name('New.kt')
        new_source.write_text('new revision')
        self.git('add', '-A'); self.commit('next revision')
        second = self.git('rev-parse', 'HEAD')
        self.git('checkout', '--detach', self.first)
        self.assertEqual(self.prepare().returncode, 0)
        self.git('checkout', '--detach', second)
        self.assertFalse(self.source.exists())
        self.assertEqual(new_source.read_text(), 'new revision')
        for path in self.caches:
            self.assertTrue(path.is_file(), str(path))

    def test_refuses_subdirectory_before_mutating_repository(self):
        self.source.write_text('keep existing change')
        result = self.prepare(self.source.parent)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.source.read_text(), 'keep existing change')

    def test_unborn_repository_requests_bootstrap_without_mutating_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            sentinel = root / 'partial-fetch.txt'
            sentinel.write_text('incomplete checkout')
            result = self.prepare(root)
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertEqual(sentinel.read_text(), 'incomplete checkout')

    def test_refuses_non_repository_without_deleting_files(self):
        with tempfile.TemporaryDirectory() as directory:
            sentinel = Path(directory) / 'keep.txt'
            sentinel.write_text('keep')
            self.assertNotEqual(self.prepare(Path(directory)).returncode, 0)
            self.assertEqual(sentinel.read_text(), 'keep')


if __name__ == '__main__':
    unittest.main()
