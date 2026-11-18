"""릴리스 후보 검사·노트 생성이 읽기 전용 계약을 지키는지 임시 Git 저장소로 검사한다."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('release_candidate', Path(__file__).parents[1] / 'release-candidate.py')
rc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rc)

FILES = {
    'backend/build.gradle.kts': 'group = "com.ssafy"\nversion = "0.0.1-SNAPSHOT"\n',
    'ai/pyproject.toml': '[project]\nname = "ai"\nversion = "0.1.0"\n',
    'frontend/androidApp/build.gradle.kts': (
        'versionCode = providers.gradleProperty("eolssu.versionCode").orElse("2").get().toInt()\n'
        'versionName = providers.gradleProperty("eolssu.versionName").orElse("1.0.0").get()\n'),
    'frontend/iosApp/Configuration/Config.xcconfig': 'CURRENT_PROJECT_VERSION=5\nMARKETING_VERSION=1.0.1\n',
}


class Repo:
    def __init__(self, root):
        self.root = root
        env = dict(os.environ, GIT_AUTHOR_NAME='t', GIT_AUTHOR_EMAIL='t@example.invalid',
                   GIT_COMMITTER_NAME='t', GIT_COMMITTER_EMAIL='t@example.invalid',
                   GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM='1')
        self.env = env
        self.git('init', '-q', '-b', 'develop')

    def git(self, *args):
        return subprocess.run(('git', '-C', str(self.root), *args), check=True, capture_output=True,
                              text=True, env=self.env).stdout.strip()

    def commit(self, message, files=None):
        for path, text in (files or {'note.txt': message}).items():
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)
            self.git('add', path)
        self.git('commit', '-q', '-m', message)
        return self.git('rev-parse', 'HEAD')

    def merge(self, branch, files=None):
        base = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-q', '-b', branch)
        self.commit('work on ' + branch, files)
        self.git('checkout', '-q', 'develop')
        self.git('merge', '-q', '--no-ff', branch, '-m', "Merge branch '" + branch + "' into 'develop'")
        self.git('branch', '-q', '-D', branch)
        assert base != self.git('rev-parse', 'HEAD')
        return self.git('rev-parse', 'HEAD')

    def publish(self, main, develop):
        self.git('update-ref', 'refs/remotes/origin/main', main)
        self.git('update-ref', 'refs/remotes/origin/develop', develop)


class ReleaseCandidateTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.repo = Repo(Path(tmp.name))
        self.main = self.repo.commit('chore: 프로젝트 초기 설정', FILES)
        self.be = self.repo.merge('feat(be)/S15P31E102-10-연습-기록-저장')
        self.fe = self.repo.merge('fix(fe)/S15P31E102-11-화면-수정')
        self.rc = self.repo.merge('docs(infra)/S15P31E102-12-운영-문서')
        self.after = self.repo.merge('feat(ai)/S15P31E102-13-발표-뒤-작업')
        self.repo.publish(self.main, self.after)
        self.git = rc.Git(self.repo.root)

    def test_check_accepts_develop_commit_that_main_can_fast_forward_to(self):
        facts = rc.check(self.git, self.rc)
        self.assertEqual(facts['revision'], self.rc)
        self.assertEqual(facts['main'], self.main)
        self.assertEqual(facts['behind_develop'], 2)

    def test_check_rejects_abbreviated_unknown_and_non_develop_commits(self):
        with self.assertRaisesRegex(rc.ReleaseError, '40-character'):
            rc.check(self.git, self.rc[:12])
        with self.assertRaisesRegex(rc.ReleaseError, 'not present'):
            rc.check(self.git, 'f' * 40)
        self.repo.git('checkout', '-q', '-b', 'side', self.main)
        side = self.repo.commit('side work')
        with self.assertRaisesRegex(rc.ReleaseError, 'not in origin/develop'):
            rc.check(self.git, side)

    def test_check_rejects_candidate_when_main_has_diverged(self):
        self.repo.git('checkout', '-q', '-b', 'hotfix', self.main)
        diverged = self.repo.commit('main only fix')
        self.repo.publish(diverged, self.after)
        with self.assertRaisesRegex(rc.ReleaseError, 'cannot fast-forward'):
            rc.check(self.git, self.rc)

    def test_check_requires_fetched_remote_refs(self):
        self.repo.git('update-ref', '-d', 'refs/remotes/origin/main')
        with self.assertRaisesRegex(rc.ReleaseError, 'must be fetched'):
            rc.check(self.git, self.rc)

    def test_notes_list_versions_mismatches_and_merged_work_since_main(self):
        notes = rc.build_notes(self.git, self.rc, tag='v1.0.0')
        self.assertEqual(notes['versions']['backend'], '0.0.1-SNAPSHOT')
        self.assertEqual(notes['versions']['android.versionCode'], '2')
        self.assertEqual(notes['versions']['ios.CURRENT_PROJECT_VERSION'], '5')
        self.assertEqual(notes['missing_versions'], [])
        self.assertTrue(any('marketing versions differ' in w for w in notes['warnings']))
        self.assertTrue(any('SNAPSHOT' in w for w in notes['warnings']))
        self.assertEqual([item['key'] for item in notes['merged']],
                         ['S15P31E102-12', 'S15P31E102-11', 'S15P31E102-10'])
        self.assertEqual(notes['merged'][0]['part'], 'infra')
        self.assertEqual(notes['merged'][0]['title'], '운영 문서')
        self.assertNotIn('S15P31E102-13', json.dumps(notes, ensure_ascii=False))

    def test_expected_version_is_checked_against_both_apps(self):
        notes = rc.build_notes(self.git, self.rc, expected='1.0.1')
        self.assertIn('android.versionName is 1.0.0, expected 1.0.1', notes['warnings'])
        self.assertNotIn('ios.MARKETING_VERSION is 1.0.1, expected 1.0.1', notes['warnings'])

    def test_missing_version_file_is_reported_not_guessed(self):
        self.repo.git('checkout', '-q', 'develop')
        self.repo.git('rm', '-q', 'ai/pyproject.toml')
        removed = self.repo.commit('chore: ai 설정 제거', {})
        self.repo.publish(self.main, removed)
        notes = rc.build_notes(self.git, removed)
        self.assertNotIn('ai', notes['versions'])
        self.assertEqual(notes['missing_versions'], ['ai (ai/pyproject.toml)'])
        self.assertEqual(notes['other_commits'][0]['subject'], 'chore: ai 설정 제거')

    def test_latest_release_tag_becomes_the_comparison_base(self):
        self.repo.git('tag', '-a', 'v0.9.0', self.be, '-m', 'previous')
        self.repo.git('tag', 'not-a-release', self.fe)
        notes = rc.build_notes(self.git, self.rc)
        self.assertEqual((notes['since'], notes['since_ref']), (self.be, 'v0.9.0'))
        self.assertEqual([item['key'] for item in notes['merged']], ['S15P31E102-12', 'S15P31E102-11'])

    def test_since_must_be_an_ancestor(self):
        with self.assertRaisesRegex(rc.ReleaseError, 'ancestor'):
            rc.build_notes(self.git, self.rc, since=self.after)

    def test_tag_format_is_validated(self):
        with self.assertRaisesRegex(rc.ReleaseError, 'tag must look like'):
            rc.build_notes(self.git, self.rc, tag='final')
        self.assertEqual(rc.build_notes(self.git, self.rc, tag='v1.0.0-rc.2')['tag'], 'v1.0.0-rc.2')

    def test_deployed_revisions_are_compared_with_the_candidate(self):
        self.repo.git('checkout', '-q', '-b', 'side', self.main)
        side = self.repo.commit('diverged deploy')
        notes = rc.build_notes(self.git, self.rc, deployed={
            'backend': self.rc, 'web': self.be, 'ai': self.after, 'omr': side, 'playback': 'abc'})
        states = {row['component']: row['state'] for row in notes['deployed']}
        self.assertEqual(states, {'backend': 'same', 'web': 'older', 'ai': 'newer',
                                  'omr': 'diverged', 'playback': 'not-found'})
        self.assertIn('deployed web is older', notes['warnings'])
        self.assertNotIn('deployed backend is same', notes['warnings'])

    def test_unknown_deployed_revisions_stay_unknown(self):
        states = {row['state'] for row in rc.build_notes(self.git, self.rc)['deployed']}
        self.assertEqual(states, {'unknown'})

    def test_parse_deployed_rejects_bad_components(self):
        self.assertEqual(rc.parse_deployed(['backend=' + self.rc]), {'backend': self.rc})
        for values in (['db=' + self.rc], ['backend'], ['web=a', 'web=b']):
            with self.assertRaises(rc.ReleaseError):
                rc.parse_deployed(values)

    def run_main(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = rc.main(['--repo', str(self.repo.root), *args])
        return code, out.getvalue(), err.getvalue()

    def test_cli_prints_markdown_without_tagging_or_moving_refs(self):
        refs = self.repo.git('for-each-ref', '--format=%(refname) %(objectname)')
        code, out, _ = self.run_main('notes', self.rc, '--tag', 'v1.0.0', '--deployed', 'backend=' + self.rc)
        self.assertEqual(code, 0)
        self.assertIn('# 릴리스 후보 v1.0.0', out)
        self.assertIn("git tag -a v1.0.0 " + self.rc, out)
        self.assertIn('### be', out)
        self.assertIn('| backend | `' + self.rc + '` | same |', out)
        self.assertEqual(self.repo.git('for-each-ref', '--format=%(refname) %(objectname)'), refs)

    def test_cli_strict_mode_fails_on_warnings_and_errors_exit_one(self):
        code, out, _ = self.run_main('notes', self.rc, '--format', 'json', '--strict')
        self.assertEqual(code, 2)
        self.assertEqual(json.loads(out)['check']['revision'], self.rc)
        code, _, err = self.run_main('check', 'nope')
        self.assertEqual(code, 1)
        self.assertIn('40-character', err)
        code, out, _ = self.run_main('check', self.rc)
        self.assertEqual((code, json.loads(out)['main']), (0, self.main))


if __name__ == '__main__':
    unittest.main()
