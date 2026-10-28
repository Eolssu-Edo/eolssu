"""배포 진입점에서 외부 백업 실패 시 교체를 시작하지 않는지 확인한다."""
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

class DeployBackupHooks(unittest.TestCase):
    def load(self, name):
        path = Path(__file__).parents[2] / 'jenkins' / ('deploy-' + name + '.py')
        spec = importlib.util.spec_from_file_location('hook_' + name, path)
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        return module

    def drive(self, name, fail_backup=False):
        module = self.load(name); events = []
        class Candidate:
            def deploy(self, sha): events.append('deploy')
        def run(*args, **kwargs):
            events.append(args[-1])
            if fail_backup: raise module.DeployError('backup failed')
        cls = 'Deployer' if name == 'backend' else 'FrontendDeployer'
        previous_mask = os.umask(0o077)
        try:
            with tempfile.TemporaryFile('w') as lock, patch.object(module, cls, Candidate), \
                 patch.object(module, 'run', run), patch.object(sys, 'argv', ['deploy', 'a' * 40]), \
                 patch.object(os, 'geteuid', lambda: 0), patch.dict(os.environ), \
                 patch('builtins.open', lambda *a, **k: lock):
                module.main()
        except module.DeployError:
            if not fail_backup: raise
        finally:
            os.umask(previous_mask)
        return events

    def test_each_entrypoint_backs_up_before_and_after_deployment(self):
        for name in ['backend', 'frontend']:
            with self.subTest(name=name):
                self.assertEqual(self.drive(name), ['prechange', 'deploy', 'postchange'])

    def test_external_backup_failure_prevents_both_deployment_entrypoints(self):
        for name in ['backend', 'frontend']:
            with self.subTest(name=name):
                self.assertEqual(self.drive(name, True), ['prechange'])
