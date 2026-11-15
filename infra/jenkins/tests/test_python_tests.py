"""Changed paths choose the right Python test groups, and each group runs the intended commands."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).parents[1] / 'python-tests.py'


def load():
    spec = importlib.util.spec_from_file_location('python_tests', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


tool = load()


class SelectionTests(unittest.TestCase):
    def groups(self, *paths):
        return tool.select(list(paths))[0]

    def test_each_area_selects_its_readers(self):
        self.assertEqual(self.groups('ai/src/ai_service/api.py'), ['ai'])
        self.assertEqual(self.groups('data/reference/pitches.json'), ['catalog', 'data', 'ai'])
        self.assertEqual(self.groups('infra/catalog/enable_scoring.py'), ['catalog', 'data'])
        self.assertEqual(self.groups('infra/edo-check/collector.py'), ['infra'])
        self.assertEqual(self.groups('backend/src/main/resources/db/migration/V30__x.sql'), ['infra', 'catalog'])
        self.assertEqual(self.groups('docs/knowledge/data/danso.md'), ['catalog'])
        self.assertEqual(self.groups('backend/tools/prepare_staff_four_beat_repair.py'), ['infra'])
        self.assertEqual(self.groups(
            'frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/guide/Sigimsae.kt'), ['data'])

    def test_areas_without_python_tests_select_nothing(self):
        self.assertEqual(self.groups('frontend/shared/src/A.kt', 'backend/src/main/kotlin/B.kt',
                                     'docs/plans/S15P31E102-1.md', 'README.md', '.gitignore'), [])

    def test_job_files_and_unknown_paths_select_every_group(self):
        for path in ['infra/jenkins/python-tests.py', 'infra/jenkins/python-tests-requirements.txt',
                     'ai/.python-version', 'new-area/tool.py']:
            with self.subTest(path=path):
                self.assertEqual(self.groups(path), list(tool.GROUPS))
        self.assertIn('unknown path', tool.select(['new-area/tool.py'])[1][0])

    def test_union_keeps_fixed_order(self):
        self.assertEqual(self.groups('ai/x.py', 'infra/r2/upload.py'), ['infra', 'ai'])


class GitRangeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmp.name).resolve()
        self.addCleanup(self.tmp.cleanup)
        self.root = tool.ROOT
        tool.ROOT = self.repo
        self.addCleanup(setattr, tool, 'ROOT', self.root)
        self.git('init', '-q')
        self.git('config', 'user.email', 'ci@example.invalid')
        self.git('config', 'user.name', 'ci')

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.repo), *args], check=True,
                              capture_output=True, text=True).stdout.strip()

    def commit(self, path, text='x'):
        target = self.repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding='utf-8')
        self.git('add', '-A')
        self.git('commit', '-q', '-m', path)
        return self.git('rev-parse', 'HEAD')

    def test_diff_from_last_success_selects_only_changed_groups(self):
        base = self.commit('README.md')
        head = self.commit('ai/src/x.py')
        groups, reasons = tool.plan(base, head, False)
        self.assertEqual(groups, ['ai'])
        self.assertIn('1 changed paths', reasons[0])

    def test_non_ascii_paths_are_not_quoted(self):
        base = self.commit('README.md')
        head = self.commit('data/raw/정간보 악보.txt')
        self.assertEqual(tool.changed_paths(base, head), ['data/raw/정간보 악보.txt'])

    def test_missing_or_rewritten_base_runs_everything(self):
        first = self.commit('README.md')
        head = self.commit('frontend/a.kt')
        self.assertEqual(tool.plan('', head, False)[0], list(tool.GROUPS))
        self.assertEqual(tool.plan('0' * 40, head, False)[0], list(tool.GROUPS))
        self.git('checkout', '-q', '--orphan', 'other')
        other = self.commit('frontend/b.kt')
        self.assertEqual(tool.plan(other, head, False)[0], list(tool.GROUPS))
        self.assertEqual(tool.plan(first, head, False)[0], [])

    def test_full_run_ignores_the_diff(self):
        head = self.commit('frontend/a.kt')
        self.assertEqual(tool.plan(head, head, True)[0], list(tool.GROUPS))


class RunTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.work = Path(self.tmp.name).resolve()
        self.addCleanup(self.tmp.cleanup)
        self.calls = []
        quiet = contextlib.redirect_stdout(io.StringIO())
        quiet.__enter__()
        self.addCleanup(quiet.__exit__, None, None, None)

    def runner(self, fail=()):
        def fake(cmd, cwd, env=None):
            self.calls.append((cmd, Path(cwd), dict(env or {})))
            return 1 if any(token in ' '.join(map(str, cmd)) for token in fail) else 0
        return fake

    def test_infra_discovers_every_suite_except_catalog_and_fixtures(self):
        dirs = tool.test_dirs('infra', exclude=('infra/catalog',)) + tool.test_dirs('backend/tools')
        self.assertIn('backend/tools/tests', dirs)
        self.assertIn('infra/jenkins/tests', dirs)
        self.assertIn('infra/edo-check/tests', dirs)
        self.assertFalse(any(d.startswith('infra/catalog') or 'fixtures' in d for d in dirs))
        self.assertEqual(tool.run('infra', self.work, runner=self.runner()), 0)
        self.assertEqual([c[0][6] for c in self.calls], dirs)
        self.assertTrue(all(c[2]['TMPDIR'] == str(self.work / 'tmp') for c in self.calls))

    def test_data_gate_deselects_quarantine_and_quarantine_runs_only_those(self):
        self.assertEqual(tool.run('data', self.work, runner=self.runner()), 0)
        venv, install, gate = self.calls
        self.assertEqual(venv[0][:2], ['uv', 'venv'])
        self.assertIn('python-tests-requirements.txt', install[0][-1])
        self.assertIn('--deselect=data/tests/test_scan_register_cli.py', gate[0])
        self.assertIn('data/tests', gate[0])
        self.calls.clear()
        self.assertEqual(tool.run('data', self.work, quarantine=True, runner=self.runner()), 0)
        quarantine = self.calls[-1][0]
        self.assertEqual(quarantine[-1], 'data/tests/test_scan_register_cli.py')
        self.assertFalse(any(arg.startswith('--deselect') for arg in quarantine))

    def test_ai_uses_locked_environment_inside_work(self):
        self.assertEqual(tool.run('ai', self.work, runner=self.runner()), 0)
        sync, gate = self.calls
        self.assertEqual(sync[0], ['uv', 'sync', '--locked', '--quiet'])
        self.assertEqual(sync[1], tool.ROOT / 'ai')
        self.assertEqual(sync[2]['UV_PROJECT_ENVIRONMENT'], str(self.work / 'venv-ai'))
        self.assertIn(f'--junitxml={self.work / "results/ai.xml"}', gate[0])
        self.assertEqual(sum(arg.startswith('--deselect=') for arg in gate[0]), len(tool.QUARANTINE['ai']))

    def test_setup_failure_and_suite_failure_fail_the_group_but_later_suites_still_run(self):
        self.assertEqual(tool.run('ai', self.work, runner=self.runner(fail=('uv sync',))), 1)
        self.assertEqual(len(self.calls), 1)
        self.calls.clear()
        dirs = tool.test_dirs('infra', exclude=('infra/catalog',)) + tool.test_dirs('backend/tools')
        self.assertEqual(tool.run('infra', self.work, runner=self.runner(fail=(dirs[0],))), 1)
        self.assertEqual(len(self.calls), len(dirs))

    def test_groups_without_quarantine_run_nothing_in_quarantine_mode(self):
        for group in ('infra', 'catalog'):
            with self.subTest(group=group):
                self.assertEqual(tool.run(group, self.work, quarantine=True, runner=self.runner()), 0)
        self.assertEqual(self.calls, [])

    def test_quarantined_paths_exist(self):
        for group, entries in tool.QUARANTINE.items():
            base = tool.ROOT / 'ai' if group == 'ai' else tool.ROOT
            for node, reason in entries:
                with self.subTest(node=node):
                    self.assertTrue((base / node.split('::')[0]).is_file())
                    self.assertTrue(reason)


class CommandLineTests(unittest.TestCase):
    def test_plan_prints_groups_on_stdout_and_reasons_on_stderr(self):
        result = subprocess.run(['python3', '-B', str(SCRIPT), 'plan', '--head', 'HEAD', '--all'],
                                capture_output=True, text=True, check=True, cwd=tool.ROOT,
                                env={**os.environ, 'PYTHONDONTWRITEBYTECODE': '1'})
        self.assertEqual(result.stdout.strip(), ','.join(tool.GROUPS))
        self.assertIn('full run requested', result.stderr)


if __name__ == '__main__':
    unittest.main()
