"""databaseTest must run against its own disposable MySQL and always remove it."""
import importlib.util
from pathlib import Path
import subprocess
import unittest


SCRIPT = Path(__file__).parents[1] / 'run-database-tests.py'
JENKINSFILE = Path(__file__).parents[1] / 'backend.Jenkinsfile'


def load_runner():
    spec = importlib.util.spec_from_file_location('run_database_tests', SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class DockerBoundary:
    """Only Docker and the test command are substituted; the run sequence is real."""
    def __init__(self, stale=(), ready_after=0, never_ready=False, test_code=0, test_error=None):
        self.stale = list(stale)
        self.ready_after = ready_after
        self.never_ready = never_ready
        self.test_code = test_code
        self.test_error = test_error
        self.calls = []
        self.envs = []
        self.container = None
        self.test_env = None
        self.probes = 0

    def __call__(self, args, **kwargs):
        self.calls.append(list(args))
        self.envs.append(kwargs.get('env'))
        if args[:2] == ['docker', 'ps']:
            return subprocess.CompletedProcess(args, 0, ''.join(s + '\n' for s in self.stale), '')
        if args[:2] == ['docker', 'run']:
            self.container = args[args.index('--name') + 1]
            return subprocess.CompletedProcess(args, 0, 'c' * 64 + '\n', '')
        if args[:2] == ['docker', 'port']:
            return subprocess.CompletedProcess(args, 0, '127.0.0.1:41234\n', '')
        if args[:2] == ['docker', 'exec']:
            if args[-1] == '--execute=SELECT 1':
                self.probes += 1
                ready = not self.never_ready and self.probes > self.ready_after
                return subprocess.CompletedProcess(args, 0 if ready else 1, '1\n' if ready else '', '')
            return subprocess.CompletedProcess(args, 0, '', '')
        if args[:2] in (['docker', 'rm'], ['docker', 'logs']):
            return subprocess.CompletedProcess(args, 0, '', '')
        # The test command itself.
        self.test_env = kwargs.get('env')
        if self.test_error:
            raise self.test_error
        return subprocess.CompletedProcess(args, self.test_code)

    def commands(self, *prefix):
        return [call for call in self.calls if call[:len(prefix)] == list(prefix)]


class RunDatabaseTestsTests(unittest.TestCase):
    def setUp(self):
        self.runner = load_runner()
        self.clock = Clock()
        self.logs = []

    def execute(self, docker, command=('./gradlew', 'databaseTest'), **kwargs):
        return self.runner.run_database_tests(list(command), run=docker, clock=self.clock,
                                              sleep=self.clock.sleep, log=self.logs.append, **kwargs)

    def assert_removed(self, docker):
        removals = docker.commands('docker', 'rm', '--force', '--volumes')
        self.assertEqual([['docker', 'rm', '--force', '--volumes', docker.container]], removals)

    def test_runs_tests_against_loopback_disposable_mysql_and_removes_it(self):
        docker = DockerBoundary(ready_after=3)

        self.assertEqual(0, self.execute(docker))

        start = docker.commands('docker', 'run')[0]
        self.assertIn('--label', start)
        self.assertIn(self.runner.LABEL, start)
        self.assertEqual('127.0.0.1::3306', start[start.index('--publish') + 1])
        self.assertEqual('/var/lib/mysql', start[start.index('--tmpfs') + 1])
        self.assertIn('mysql:8.4.13', start)
        self.assertIn('--max-connections=1000', start)
        env = docker.test_env
        self.assertEqual('jdbc:mysql://127.0.0.1:41234/eolssu_test', env['TEST_DB_URL'])
        self.assertEqual('eolssu_test', env['TEST_DB_USERNAME'])
        self.assertGreaterEqual(len(env['TEST_DB_PASSWORD']), 24)
        # Passwords reach Docker and the tests only through the environment.
        start_env = docker.envs[docker.calls.index(start)]
        self.assertEqual(env['TEST_DB_PASSWORD'], start_env['MYSQL_PASSWORD'])
        secrets = {env['TEST_DB_PASSWORD'], start_env['MYSQL_ROOT_PASSWORD']}
        for call in docker.calls:
            self.assertFalse(any(secret in arg for secret in secrets for arg in call), call)
        grants = [c for c in docker.commands('docker', 'exec') if 'GRANT' in c[-1]]
        self.assertIn('performance_schema.data_locks', grants[0][-1])
        self.assertIn('performance_schema.data_lock_waits', grants[0][-1])
        self.assertIn('performance_schema.threads', grants[0][-1])
        self.assertEqual(4, docker.probes)
        self.assert_removed(docker)
        # Cleanup comes after the test command.
        test_index = docker.calls.index(['./gradlew', 'databaseTest'])
        self.assertGreater(docker.calls.index(docker.commands('docker', 'rm', '--force', '--volumes')[0]), test_index)

    def test_removes_containers_left_by_an_interrupted_run_first(self):
        docker = DockerBoundary(stale=['old1', 'old2'])

        self.execute(docker)

        stale_removals = [c for c in docker.commands('docker', 'rm', '--force') if c[-1] in ('old1', 'old2')]
        self.assertEqual([['docker', 'rm', '--force', 'old1'], ['docker', 'rm', '--force', 'old2']], stale_removals)
        stale_filter = docker.commands('docker', 'ps')[0]
        self.assertIn('label=' + self.runner.LABEL, stale_filter)
        self.assertLess(docker.calls.index(['docker', 'rm', '--force', 'old2']),
                        docker.calls.index(docker.commands('docker', 'run')[0]))

    def test_returns_the_test_exit_code_and_still_removes_the_database(self):
        docker = DockerBoundary(test_code=1)

        self.assertEqual(1, self.execute(docker))

        self.assert_removed(docker)

    def test_removes_the_database_when_the_build_is_aborted(self):
        docker = DockerBoundary(test_error=SystemExit(143))

        with self.assertRaises(SystemExit):
            self.execute(docker)

        self.assert_removed(docker)

    def test_stops_with_container_logs_when_mysql_never_becomes_ready(self):
        docker = DockerBoundary(never_ready=True)

        with self.assertRaisesRegex(RuntimeError, 'did not become ready'):
            self.execute(docker, ready_timeout=5)

        self.assertIsNone(docker.test_env)
        self.assertTrue(docker.commands('docker', 'logs'))
        self.assert_removed(docker)

    def test_requires_a_test_command_before_touching_docker(self):
        docker = DockerBoundary()

        with self.assertRaises(ValueError):
            self.execute(docker, command=())

        self.assertEqual([], docker.calls)


class BackendJenkinsfileTests(unittest.TestCase):
    def test_backend_job_runs_database_tests_through_the_disposable_mysql_wrapper(self):
        text = JENKINSFILE.read_text()
        stage = text[text.index("stage('Backend database tests')"):]
        stage = stage[:stage.index("stage('Package executable JAR')")]
        self.assertIn('../infra/jenkins/run-database-tests.py', stage)
        self.assertIn('databaseTest', stage)
        self.assertIn("backend/build/test-results/databaseTest/*.xml", stage)
        self.assertNotIn('TEST_DB_', stage)
        # Warning-only during the release freeze: failures and timeouts must not stop deploy.
        self.assertIn("catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE')", stage)
        self.assertLess(stage.index('catchError('), stage.index("timeout(time: 10, unit: 'MINUTES')"))
        self.assertIn('allowEmptyResults: true', stage)
        self.assertIn("timeout(time: 25, unit: 'MINUTES')", text)
        self.assertIn("stage('Database wrapper tests')", text)


if __name__ == '__main__':
    unittest.main()
