"""Exercise staged publication against a local bare Git remote; no external calls."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class DeployPhasesTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.remote = self.root / 'pages.git'
        self.public = self.root / 'public'
        self.public.mkdir()
        (self.public / 'index.html').write_text('first site')
        self.work = self.root / 'work'
        self.script = Path(os.environ.get('DEPLOY_SCRIPT', str(Path(__file__).parents[1] / 'scripts/deploy.sh')))
        self.git('init', '--bare', str(self.remote))
        self.env = dict(os.environ, GH_TOKEN='fixture-token', GH_REPO='pages', GH_BRANCH='main',
                        PUBLIC_DIR=str(self.public), PAGES_WORK_DIR=str(self.work),
                        GIT_CONFIG_COUNT='1', GIT_CONFIG_KEY_0='url.' + self.root.as_uri() + '/.insteadOf',
                        GIT_CONFIG_VALUE_0='https://x-access-token:fixture-token@github.com/')

    def git(self, *args):
        return subprocess.run(['git', *args], text=True, capture_output=True, check=True).stdout.strip()

    def phase(self, *args, check=True):
        return subprocess.run(['bash', str(self.script), *args], env=self.env,
                              text=True, capture_output=True, check=check)

    def head(self):
        return self.git('--git-dir', str(self.remote), 'rev-parse', 'refs/heads/main')

    def test_phases_do_not_publish_before_push_and_reuse_existing_branch(self):
        self.phase('prepare')
        self.assertEqual(self.git('--git-dir', str(self.remote), 'for-each-ref', 'refs/heads'), '')
        self.phase('sync')
        self.phase('commit')
        self.assertNotIn('fixture-token', (self.work / '.git/config').read_text())
        self.phase('push')
        first = self.head()
        self.assertEqual(self.git('--git-dir', str(self.remote), 'show', 'main:index.html'), 'first site')
        # Next staged run starts from the published branch and removes old assets.
        import shutil
        shutil.rmtree(self.work)
        (self.public / 'index.html').write_text('second site')
        self.phase('prepare')
        self.assertEqual(self.git('-C', str(self.work), 'remote'), '')
        self.phase('sync'); self.phase('commit')
        self.assertEqual(self.head(), first)
        self.phase('push')
        self.assertEqual(self.git('--git-dir', str(self.remote), 'rev-parse', 'main^'), first)
        self.assertEqual(self.git('--git-dir', str(self.remote), 'show', 'main:index.html'), 'second site')

    def test_same_content_creates_no_extra_commit(self):
        self.phase()
        first = self.head()
        self.phase()
        self.assertEqual(self.head(), first)

    def test_missing_output_and_unknown_phase_fail_without_publication(self):
        self.phase('prepare')
        (self.public / 'index.html').unlink()
        self.assertNotEqual(self.phase('sync', check=False).returncode, 0)
        self.assertEqual(self.phase('unknown', check=False).returncode, 2)
        self.assertEqual(self.git('--git-dir', str(self.remote), 'for-each-ref', 'refs/heads'), '')


if __name__ == '__main__':
    unittest.main()
