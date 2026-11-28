import runpy
from pathlib import Path
import unittest


SELECT = runpy.run_path(str(Path(__file__).resolve().parents[1] / 'ci-plan.py'))['select']


class PlanTests(unittest.TestCase):
    def test_common_code_runs_both_frontend_targets(self):
        result = SELECT(['frontend/shared/src/commonMain/kotlin/Main.kt'])
        self.assertEqual(result, dict(backend=False, web=True, android=True, python=[]))

    def test_database_migrations_run_backend_and_python_readers(self):
        result = SELECT(['backend/src/main/resources/db/migration/V1__sample.sql'])
        self.assertEqual(result, dict(backend=True, web=False, android=False, python=['infra', 'catalog']))

    def test_missing_base_and_ci_changes_select_every_suite(self):
        expected = dict(backend=True, web=True, android=True, python=['infra', 'catalog', 'data', 'ai'])
        self.assertEqual(SELECT(None), expected)
        self.assertEqual(SELECT(['.github/workflows/ci.yml']), expected)

    def test_docs_only_change_can_skip_builds(self):
        self.assertEqual(SELECT(['docs/README.md']), dict(backend=False, web=False, android=False, python=[]))
