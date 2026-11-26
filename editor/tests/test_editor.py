import base64
import json
import unittest
from datetime import datetime
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from urllib.parse import unquote

import yaml
from werkzeug.exceptions import Forbidden

from editor.app import (BRANCH, FRONT_MATTER, GITHUB, GITHUB_API, REPOSITORY_API, UpstreamError, create_app,
                        parse_post, upstream_json)

PATH = "posts/2026-11-02-test-fails-first.md"
SOURCE = '---\ntitle: "기존 제목"\nslug: test-fails-first\nauthor: 오은서\n# Keep this comment.\ncategories: [backend]\ndraft: false\n---\n\n기존 본문\n'
VERSION = "1" * 40
NEW_VERSION = "2" * 40
ORIGIN = "https://team-edo.github.io"
WRITE_HEADERS = {"Authorization": "Bearer test-project-token", "X-GitHub-Api-Version": "2022-11-28"}


def github_file(source, version):
    """Shape of GitHub's contents API response: base64 wrapped at 60 characters, blob sha."""
    encoded = base64.b64encode(source if isinstance(source, bytes) else source.encode()).decode()
    return {"type": "file", "sha": version,
            "content": "\n".join(encoded[i:i + 60] for i in range(0, len(encoded), 60)) + "\n"}


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
        self.deletions = []
        self.simulate_race = False
        self.oauth_response = {"access_token": "test-read-token", "scope": "user:email"}
        upstream = patch("editor.app.upstream_json", side_effect=self.upstream)
        self.calls = upstream.start()
        self.addCleanup(upstream.stop)

    def test_views_sum_seven_korean_dates_and_persist_across_app_restarts(self):
        origin = {"Origin": ORIGIN}
        first, second = "/posts/2026/10/first/", "/posts/2026/10/second/"
        with TemporaryDirectory() as directory, patch("editor.app.datetime") as clock:
            config = dict(self.app.config, VIEWS_DB=str(Path(directory) / "views.sqlite3"))
            client = create_app(config).test_client()

            def view(path):
                return client.post("/views", headers=origin, data=path, content_type="text/plain")

            clock.now.return_value = datetime(2026, 11, 1, 23, 59)
            self.assertEqual(client.get("/views", headers=origin).json, {})
            self.assertEqual(view(first).status_code, 204)
            clock.now.return_value = datetime(2026, 11, 2, 0, 0)
            self.assertEqual(view(second).status_code, 204)
            clock.now.return_value = datetime(2026, 11, 8, 23, 59)
            self.assertEqual(view(second).status_code, 204)
            self.assertEqual(view(second).status_code, 204)
            client = create_app(config).test_client()
            weekly = client.get("/views", headers=origin)
            self.assertEqual(weekly.json, {second: 3})
            self.assertEqual(weekly.headers["Access-Control-Allow-Origin"], ORIGIN)
            self.assertEqual(weekly.headers["Cache-Control"], "no-store")

    def test_views_validate_the_origin_and_body_and_are_disabled_without_storage(self):
        origin = {"Origin": ORIGIN}
        self.assertEqual(self.client.get("/views", headers=origin).status_code, 404)
        with TemporaryDirectory() as directory:
            config = dict(self.app.config, VIEWS_DB=str(Path(directory) / "views.sqlite3"))
            client = create_app(config).test_client()
            self.assertEqual(client.get("/views").status_code, 403)
            for path, expected in [("/edit/", 400), ("a" * 257, 413)]:
                response = client.post("/views", headers=origin, data=path, content_type="text/plain")
                self.assertEqual(response.status_code, expected)
            self.assertEqual(client.get("/views", headers=origin).json, {})

    def upstream(self, path, *, headers=None, form=None, update=None, delete=None, expected_type=dict):
        if path == GITHUB + "/login/oauth/access_token":
            self.assertEqual(form["redirect_uri"], ORIGIN + "/edit/")
            self.assertEqual(form["code_verifier"], "x" * 43)
            return self.oauth_response
        if path == GITHUB_API + "/user/emails?per_page=100&page=1":
            self.assertEqual(headers, {"Authorization": "Bearer test-read-token"})
            self.assertIs(expected_type, list)
            return self.emails
        self.assertTrue(unquote(path).startswith(REPOSITORY_API + "/contents/content/" + PATH))
        self.assertEqual(headers, WRITE_HEADERS)
        if delete is not None:
            self.assertEqual(delete["branch"], BRANCH)
            self.assertEqual(delete["sha"], self.version)
            self.assertNotIn("content", delete)
            if self.simulate_race:
                self.version = NEW_VERSION
                raise UpstreamError(409)
            self.deletions.append(delete)
            self.source = None
            return None
        if update:
            self.updates.append(update)
            self.assertEqual(update["branch"], BRANCH)
            self.assertEqual(update["sha"], self.version)
            if self.simulate_race:
                self.version = NEW_VERSION
                raise UpstreamError(409)
            self.source = base64.b64decode(update["content"], validate=True).decode()
            self.version = NEW_VERSION
            return {"content": {"sha": NEW_VERSION}, "commit": {"sha": "3" * 40}}
        self.assertTrue(path.endswith("?ref=blog"))
        if self.source is None:
            raise UpstreamError(404)
        return github_file(self.source, self.version)

    def start(self, path=PATH):
        return self.client.post("/session", headers={"Origin": ORIGIN},
                                json={"post": path, "code": "code", "verifier": "x" * 43})

    def save(self, session, *, title="새 제목", markdown="\n새 본문\n", version=VERSION):
        return self.client.put("/post", headers={"Origin": ORIGIN, "Authorization": "Bearer " + session},
                               json={"title": title, "markdown": markdown, "version": version})

    def delete(self, session, *, version=VERSION, origin=ORIGIN, **extra):
        return self.client.delete("/post", headers={"Origin": origin, "Authorization": "Bearer " + session},
                                  json={"version": version, **extra})

    def test_owner_deletes_only_the_session_post_once(self):
        login = self.start().json
        self.assertIs(login["canDelete"], True)
        session = login["session"]
        response = self.delete(session)
        self.assertEqual(response.json, {"status": "deleted"})
        self.assertIn("DELETE", response.headers["Access-Control-Allow-Methods"])
        self.assertIsNone(self.source)
        self.assertEqual(self.delete(session).status_code, 404)
        self.assertEqual(len(self.deletions), 1)

    def test_delete_rejects_wrong_origin_session_owner_version_or_target(self):
        session = self.start().json["session"]
        self.assertEqual(self.delete(session, origin="https://invalid.example").status_code, 403)
        self.assertEqual(self.delete("forged").status_code, 401)
        self.assertEqual(self.delete(session, version="invalid").status_code, 422)
        self.assertEqual(self.delete(session, post="posts/2026-11-02-other.md").status_code, 422)
        self.assertEqual(self.delete(session, version=NEW_VERSION).status_code, 409)
        self.source = SOURCE.replace("draft: false", "draft: true")
        self.assertEqual(self.delete(session).status_code, 403)
        self.source = SOURCE.replace("author: 오은서", "author: 사지한")
        self.assertEqual(self.delete(session).status_code, 403)
        self.assertEqual(self.deletions, [])

    def test_delete_race_leaves_newer_version_in_place(self):
        session = self.start().json["session"]
        self.simulate_race = True
        self.assertEqual(self.delete(session).status_code, 409)
        self.assertEqual(self.source, SOURCE)
        self.assertEqual(self.version, NEW_VERSION)
        self.assertEqual(self.deletions, [])

    def test_upstream_delete_uses_json_and_accepts_empty_response(self):
        with patch("editor.app.upstream_http.open") as open_upstream:
            open_upstream.return_value.__enter__.return_value.read.return_value = b""
            payload = {"branch": BRANCH, "sha": VERSION, "message": "delete fixture"}
            self.assertIsNone(upstream_json(REPOSITORY_API + "/fixture", delete=payload))
            outgoing = open_upstream.call_args.args[0]
            self.assertEqual(outgoing.get_method(), "DELETE")
            self.assertEqual(outgoing.get_header("Content-type"), "application/json")
            self.assertEqual(json.loads(outgoing.data), payload)

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
        delete = self.delete("forged")
        self.assertEqual(delete.status_code, 403)
        self.assertNotIn("DELETE", delete.headers["Access-Control-Allow-Methods"])
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
                "EDITOR_GITHUB_WRITE_TOKEN_FILE": str(token_file),
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

    def test_existing_articles_follow_public_editing_policy(self):
        with self.app.app_context():
            for path in Path("content/posts").glob("????-??-??-*.md"):
                with self.subTest(path=path):
                    source = path.read_bytes()
                    file = github_file(source, VERSION)
                    metadata = yaml.safe_load(FRONT_MATTER.match(source.decode())[1])
                    if metadata.get("draft") is True:
                        with self.assertRaises(Forbidden):
                            parse_post(file)
                        continue
                    post = parse_post(file)
                    self.assertTrue(post.markdown.strip())
                    self.assertTrue(post.author)
                    self.assertEqual(post.with_edit(post.title.value, post.markdown), post.source)


if __name__ == "__main__":
    unittest.main()
