import base64
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from urllib.parse import unquote

from editor.app import BRANCH, GITHUB, GITHUB_API, GITLAB, UpstreamError, create_app, parse_post

PATH = "posts/2026-11-02-test-fails-first.md"
SOURCE = '---\ntitle: "기존 제목"\nslug: test-fails-first\nauthor: 오은서\n# Keep this comment.\ncategories: [backend]\ndraft: false\n---\n\n기존 본문\n'
VERSION = "1" * 40
NEW_VERSION = "2" * 40
ORIGIN = "https://team-edo.github.io"


class EditorTests(unittest.TestCase):
    def setUp(self):
        author_directory = TemporaryDirectory()
        self.addCleanup(author_directory.cleanup)
        author_file = Path(author_directory.name) / "authors.json"
        author_file.write_text('{"오은서":"owner@example.org","사지한":"other@example.org"}')
        self.app = create_app({
            "TESTING": True, "BLOG_ORIGIN": ORIGIN,
            "CLIENT_ID": "test-client", "CLIENT_SECRET": "test-oauth-secret",
            "WRITE_TOKEN": "test-project-token", "SESSION_SECRET": "test-secret-" * 4,
            "AUTHOR_EMAILS_FILE": str(author_file),
        })
        self.client = self.app.test_client()
        self.source = SOURCE
        self.emails = [{"email": "OWNER@example.org", "verified": True, "primary": False}]
        self.version = VERSION
        self.updates = []
        self.simulate_race = False
        self.oauth_response = {"access_token": "test-read-token", "scope": "user:email"}
        upstream = patch("editor.app.upstream_json", side_effect=self.upstream)
        self.calls = upstream.start()
        self.addCleanup(upstream.stop)

    def upstream(self, path, *, headers=None, form=None, update=None, expected_type=dict):
        if path == GITHUB + "/login/oauth/access_token":
            self.assertEqual(form["redirect_uri"], ORIGIN + "/edit/")
            self.assertEqual(form["code_verifier"], "x" * 43)
            return self.oauth_response
        if path == GITHUB_API + "/user/emails?per_page=100&page=1":
            self.assertEqual(headers, {"Authorization": "Bearer test-read-token"})
            self.assertIs(expected_type, list)
            return self.emails
        self.assertTrue(path.startswith(GITLAB + "/api/v4/projects/"))
        self.assertEqual(headers, {"PRIVATE-TOKEN": "test-project-token"})
        self.assertIn("/repository/files/content/" + PATH, unquote(path))
        if update:
            self.updates.append(update)
            self.assertEqual(update["branch"], BRANCH)
            self.assertEqual(update["last_commit_id"], self.version)
            if self.simulate_race:
                self.version = NEW_VERSION
                raise UpstreamError(400)
            self.source = update["content"]
            self.version = NEW_VERSION
            return {"branch": BRANCH}
        self.assertTrue(path.endswith("?ref=blog"))
        return {"content": base64.b64encode(self.source.encode()).decode(), "last_commit_id": self.version}

    def start(self, path=PATH):
        return self.client.post("/session", headers={"Origin": ORIGIN},
                                json={"post": path, "code": "code", "verifier": "x" * 43})

    def save(self, session, *, title="새 제목", markdown="\n새 본문\n", version=VERSION):
        return self.client.put("/post", headers={"Origin": ORIGIN, "Authorization": "Bearer " + session},
                               json={"title": title, "markdown": markdown, "version": version})

    def test_owner_saves_title_and_body_to_blog_and_retry_does_not_write_twice(self):
        login = self.start()
        self.assertEqual(login.status_code, 200)
        self.assertEqual(login.headers["Cache-Control"], "no-store")
        self.assertEqual(login.json["markdown"], "\n기존 본문\n")
        session = login.json["session"]
        self.assertEqual(self.save(session).json, {"status": "saved"})
        self.assertEqual(self.source, SOURCE.replace("기존 제목", "새 제목").replace("기존 본문", "새 본문"))
        self.assertEqual(self.save(session).json, {"status": "unchanged"})
        self.assertEqual(len(self.updates), 1)

    def test_without_write_token_only_github_identity_is_verified(self):
        config = dict(self.app.config, WRITE_TOKEN="", SESSION_SECRET="")
        self.client = create_app(config).test_client()
        self.assertEqual(self.start(path=None).json, {"status": "verified"})
        self.assertEqual(self.save("forged").status_code, 403)
        self.assertEqual(self.calls.call_count, 2)
        self.assertEqual(self.updates, [])
        self.emails = [{"email": "outsider@example.org", "verified": True}]
        self.assertEqual(self.start(path=None).status_code, 403)
        self.assertEqual(self.calls.call_count, 4)

    def test_writable_server_can_still_verify_an_author_without_a_post(self):
        self.assertEqual(self.start(path=None).json, {"status": "verified"})
        self.assertEqual(self.calls.call_count, 2)

    def test_writable_server_uses_mounted_credentials(self):
        config = dict(self.app.config)
        token = config.pop("WRITE_TOKEN")
        secret = config.pop("SESSION_SECRET")
        with TemporaryDirectory() as directory:
            token_file = Path(directory) / "token"
            token_file.write_text(token + "\n")
            secret_file = Path(directory) / "secret"
            secret_file.write_text(secret + "\n")
            with patch.dict("os.environ", {
                "EDITOR_GITLAB_WRITE_TOKEN_FILE": str(token_file),
                "EDITOR_SESSION_SECRET_FILE": str(secret_file),
            }, clear=True):
                self.client = create_app(config).test_client()
        session = self.start().json["session"]
        self.assertEqual(self.save(session).json, {"status": "saved"})

    def test_another_author_cannot_open_editor(self):
        self.emails = [{"email": "other@example.org", "verified": True}]
        self.assertEqual(self.start().status_code, 403)
        self.assertEqual(self.updates, [])

    def test_unverified_or_ambiguous_email_is_denied(self):
        for emails in (
            [{"email": "owner@example.org", "verified": False}],
            [{"email": "owner@example.org", "verified": True},
             {"email": "other@example.org", "verified": True}],
        ):
            with self.subTest(emails=emails):
                self.emails = emails
                self.assertEqual(self.start().status_code, 403)
        self.assertEqual(self.updates, [])

    def test_invalid_code_and_token_with_extra_scopes_cannot_open_editor(self):
        self.oauth_response = {"error": "bad_verification_code"}
        self.assertEqual(self.start().status_code, 401)
        self.oauth_response = {"access_token": "test-read-token", "scope": "repo,user:email"}
        self.assertEqual(self.start().status_code, 403)
        self.assertEqual(self.updates, [])

    def test_author_changed_after_login_cannot_save(self):
        session = self.start().json["session"]
        self.source = self.source.replace("author: 오은서", "author: 사지한")
        self.assertEqual(self.save(session).status_code, 403)
        self.assertEqual(self.updates, [])

    def test_missing_forged_and_expired_sessions_cannot_write(self):
        for session in ("", "forged"):
            with self.subTest(session=session):
                self.assertEqual(self.save(session).status_code, 401)
        session = self.start().json["session"]
        with patch("editor.app.SESSION_SECONDS", -1):
            self.assertEqual(self.save(session).status_code, 401)
        self.assertEqual(self.updates, [])

    def test_conflicting_edit_is_rejected_before_write(self):
        session = self.start().json["session"]
        self.version = NEW_VERSION
        self.assertEqual(self.save(session).status_code, 409)
        self.assertEqual(self.updates, [])

    def test_concurrent_upstream_change_returns_conflict(self):
        session = self.start().json["session"]
        self.simulate_race = True
        self.assertEqual(self.save(session).status_code, 409)
        self.assertEqual(self.source, SOURCE)

    def test_traversal_and_foreign_origin_are_rejected_without_upstream_calls(self):
        self.assertEqual(self.start("posts/../hugo.toml").status_code, 400)
        foreign = self.client.post("/session", headers={"Origin": "https://other.example"}, json={})
        self.assertEqual(foreign.status_code, 403)
        self.assertNotIn("Access-Control-Allow-Origin", foreign.headers)
        self.calls.assert_not_called()

    def test_team_draft_and_yaml_alias_posts_are_not_editable(self):
        for source in (
            SOURCE.replace("author: 오은서", "author: Team Edo"),
            SOURCE.replace("draft: false", "draft: true"),
            SOURCE.replace('title: "기존 제목"', 'title: &name "기존 제목"').replace("author: 오은서", "author: *name"),
            SOURCE.replace("draft: false", "author: 오은서\ndraft: false"),
        ):
            with self.subTest(source=source):
                self.source = source
                self.assertEqual(self.start().status_code, 403)
        self.assertEqual(self.updates, [])

    def test_shortcode_cannot_be_written(self):
        session = self.start().json["session"]
        self.assertEqual(self.save(session, markdown="{{< invalid >}}").status_code, 422)
        self.assertEqual(self.updates, [])

    def test_existing_articles_can_be_parsed(self):
        with self.app.app_context():
            for path in Path("content/posts").glob("????-??-??-*.md"):
                with self.subTest(path=path):
                    file = {"content": base64.b64encode(path.read_bytes()).decode(), "last_commit_id": VERSION}
                    post = parse_post(file)
                    self.assertTrue(post.markdown.strip())
                    self.assertTrue(post.author)
                    self.assertEqual(post.with_edit(post.title.value, post.markdown), post.source)


if __name__ == "__main__":
    unittest.main()
