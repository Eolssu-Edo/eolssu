import unittest
from pathlib import Path

JENKINS = Path(__file__).parents[1]
WEB_TESTS = JENKINS / 'web-tests.Jenkinsfile'
BACKEND = JENKINS / 'backend.Jenkinsfile'


class WebTestsJenkinsfileTests(unittest.TestCase):
    """S15P31E102-1270: develop 웹 브라우저 테스트 자동 실행 경로."""

    def test_web_test_job_runs_both_browser_test_tasks_without_deploying(self):
        text = WEB_TESTS.read_text(encoding='utf-8')
        self.assertIn("agent { label 'edo1-web' }", text)
        self.assertIn('disableConcurrentBuilds(abortPrevious: true)', text)
        self.assertIn('git merge-base --is-ancestor "$GIT_COMMIT" origin/develop', text)
        self.assertIn(':shared:wasmJsTest', text)
        self.assertIn(':shared:jsTest', text)
        self.assertIn('wasmJsBrowserTest/TEST-*.xml', text)
        self.assertIn('jsBrowserTest/TEST-*.xml', text)
        self.assertNotIn('allowEmptyResults: true', text)
        for deploy in ('deploy-frontend', 'docker build', 'archiveArtifacts', 'sudo'):
            self.assertNotIn(deploy, text)

    def test_backend_job_queues_web_tests_before_backend_stages_when_web_inputs_change(self):
        text = BACKEND.read_text(encoding='utf-8')
        stage = text[text.index("stage('Queue web tests')"):]
        stage = stage[:stage.index("stage('Backend image tool tests')")]
        self.assertLess(text.index("stage('Checkout develop')"), text.index("stage('Queue web tests')"))
        for path in ('frontend/shared/src/commonMain/', 'frontend/shared/src/webMain/',
                     'frontend/shared/src/webTest/', 'frontend/shared/src/commonTest/',
                     'frontend/shared/build.gradle.kts', 'frontend/gradle/', 'frontend/kotlin-js-store/'):
            self.assertIn(path, stage)
        self.assertIn("build job: 'eolssu-web-tests'", stage)
        self.assertIn('wait: false', stage)
        self.assertIn('unstable(', stage)


if __name__ == '__main__':
    unittest.main()
