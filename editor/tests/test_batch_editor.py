import base64
import json
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Barrier
from unittest.mock import patch
from urllib.parse import parse_qs, unquote, urlsplit

from editor.app import BRANCH, GITHUB, GITHUB_API, REPOSITORY_API, UpstreamError, create_app
from test_editor import NEW_VERSION, ORIGIN, PATH, SOURCE, VERSION, WRITE_HEADERS, github_file

SECOND = "posts/2026-11-03-second.md"
OTHER = "posts/2026-11-04-other.md"
BATCH = "/post?mode=batch"
HEAD = "a" * 40
TREE = "b" * 40
NEW_TREE = "c" * 40


class BatchEditorTests(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        author_file = Path(directory.name) / "authors.json"
        author_file.write_text(json.dumps({"오은서": "owner@example.org", "사지한": "other@example.org"}))
        self.config = {
            "TESTING": True, "BLOG_ORIGIN": ORIGIN,
            "CLIENT_ID": "test-client", "CLIENT_SECRET": "test-oauth-secret",
            "WRITE_TOKEN": "test-project-token", "SESSION_SECRET": "test-secret-" * 4,
            "AUTHOR_EMAILS_FILE": str(author_file), "QUEUE_DB": str(Path(directory.name) / "queue.sqlite3"),
        }
        self.client = create_app(self.config).test_client()
        self.files = {
            PATH: (SOURCE, VERSION),
            SECOND: (SOURCE.replace("기존 제목", "둘째 글"), VERSION),
            OTHER: (SOURCE.replace("오은서", "사지한"), VERSION),
        }
        self.writes = []
        self.commits = []
        self.head = HEAD
        self.trees = {}
        self.pending = {}
        self.read_refs = []
        self.fail = None
        self.validation_barrier = None
        mock = patch("editor.app.upstream_json", side_effect=self.upstream)
        mock.start()
        self.addCleanup(mock.stop)

    def upstream(self, url, *, headers=None, form=None, submit=None, patch=None, expected_type=dict):
        if url == GITHUB + "/login/oauth/access_token":
            return {"access_token": form["code"], "scope": "user:email"}
        if url.startswith(GITHUB_API + "/user/emails?"):
            return [{"email": headers["Authorization"].split()[1] + "@example.org", "verified": True}]
        self.assertEqual(headers, WRITE_HEADERS)
        parts = urlsplit(url)
        query = parse_qs(parts.query)
        route = unquote(parts.path).removeprefix(urlsplit(REPOSITORY_API).path)
        if route == "/git/ref/heads/" + BRANCH:
            return {"object": {"sha": self.head}}
        if route == "/git/commits/" + self.head and submit is None:
            return {"sha": self.head, "tree": {"sha": TREE}}
        if route == "/git/trees":
            self.assertEqual(submit["base_tree"], TREE)
            for entry in submit["tree"]:
                self.assertEqual((entry["mode"], entry["type"]), ("100644", "blob"))
                self.assertTrue(entry["path"].startswith("content/posts/"))
                self.assertTrue(("content" in entry) != (entry.get("sha", "missing") is None))
            self.trees[NEW_TREE] = submit["tree"]
            return {"sha": NEW_TREE}
        if route == "/git/commits":
            self.writes.append(submit)
            if self.fail == "before":
                raise UpstreamError(502)
            self.assertEqual(submit["parents"], [self.head])
            self.pending[NEW_VERSION] = submit
            return {"sha": NEW_VERSION}
        if route == "/git/refs/heads/" + BRANCH:
            self.assertEqual(patch, {"sha": NEW_VERSION, "force": False})
            commit = self.pending.pop(NEW_VERSION)
            if commit["parents"] != [self.head]:
                raise UpstreamError(422)
            for entry in self.trees[commit["tree"]]:
                file = entry["path"].removeprefix("content/")
                if "content" in entry:
                    self.files[file] = (entry["content"], NEW_VERSION)
                else:
                    self.files.pop(file)
            self.head = NEW_VERSION
            self.commits.insert(0, {"sha": NEW_VERSION, "commit": {"message": commit["message"]}})
            if self.fail == "after":
                raise UpstreamError(502)
            return {"object": {"sha": NEW_VERSION}}
        if route == "/commits":
            self.assertEqual(query["sha"], [BRANCH])
            return self.commits
        self.assertTrue(route.startswith("/contents/content/"), route)
        self.assertIn(query["ref"], ([BRANCH], [self.head]))
        self.read_refs.append(query["ref"][0])
        file = route.removeprefix("/contents/content/")
        if self.validation_barrier:
            self.validation_barrier.wait(timeout=2)
        if file not in self.files:
            raise UpstreamError(404)
        source, version = self.files[file]
        return github_file(source, version)

    def login(self, code="owner"):
        response = self.client.post("/session", headers={"Origin": ORIGIN},
                                    json={"mode": "batch", "code": code, "verifier": "x" * 43})
        self.assertEqual(response.status_code, 200)
        return response.json["session"]

    def call(self, method, session, body=None, post=None):
        headers = {"Origin": ORIGIN}
        if session:
            headers["Authorization"] = "Bearer " + session
        path = BATCH + ("&post=" + post if post else "")
        return self.client.open(path, method=method, headers=headers, json=body)

    def stage(self, session, path=PATH, item=None, version=VERSION):
        return self.call("PUT", session, {"post": path, "item": item, "version": version,
                                         "title": "임시 제목", "markdown": "새 본문\n"})

    def remove(self, session, path=SECOND, item=None):
        return self.call("DELETE", session, {"post": path, "item": item, "version": VERSION})

    def apply(self, session, revision):
        return self.call("POST", session, {"action": "apply", "revision": revision})

    def test_persistent_mixed_queue_writes_one_commit_only_on_apply(self):
        session = self.login()
        self.assertEqual(self.stage(session).status_code, 200)
        pending = self.remove(session).json
        self.assertEqual([item["kind"] for item in pending["items"]], ["edit", "delete"])
        self.assertEqual(self.files[PATH][0], SOURCE)
        self.assertEqual(self.writes, [])
        self.client = create_app(self.config).test_client()
        self.assertEqual(self.call("GET", session).json, pending)
        draft = self.call("GET", session, post=PATH).json
        self.assertEqual(draft["draft"]["title"], "임시 제목")
        applied = self.apply(session, pending["revision"])
        self.assertEqual(applied.status_code, 200)
        self.assertEqual(applied.json["items"], [])
        self.assertEqual(self.apply(session, pending["revision"]).json["commit"], NEW_VERSION)
        self.assertEqual(len(self.writes), 1)
        self.assertEqual(self.writes[0]["parents"], [HEAD])
        self.assertIn("Blog-Batch: " + pending["revision"], self.writes[0]["message"])
        self.assertEqual([("content" in entry) for entry in self.trees[NEW_TREE]], [True, False])
        self.assertEqual(self.head, NEW_VERSION)
        self.assertIn(HEAD, self.read_refs)
        self.assertIn("# Keep this comment.", self.files[PATH][0])
        self.assertNotIn(SECOND, self.files)

    def test_only_author_can_read_stage_cancel_and_publish_own_queue(self):
        owner = self.login()
        other = self.login("other")
        self.assertEqual(self.call("GET", None).status_code, 401)
        self.assertEqual(self.call("GET", "forged").status_code, 401)
        self.assertEqual(self.call("GET", other, post=PATH).status_code, 403)
        self.assertEqual(self.stage(other).status_code, 403)
        self.assertEqual(self.stage(owner, OTHER).status_code, 403)
        self.assertEqual(self.stage(owner, "../README.md").status_code, 400)
        saved = self.stage(owner).json
        self.assertEqual(self.call("GET", other).json["items"], [])
        self.assertEqual(self.apply(other, saved["revision"]).status_code, 409)
        self.assertEqual(self.call("POST", other, {"action": "undo", "post": PATH,
                                                  "item": saved["items"][0]["item"]}).status_code, 409)
        self.assertEqual(self.call("GET", owner).json, saved)
        self.assertEqual(self.writes, [])

    def test_cancel_delete_restores_draft_and_stale_views_cannot_apply(self):
        session = self.login()
        saved = self.stage(session).json
        saved_item = saved["items"][0]["item"]
        self.assertEqual(self.stage(session).status_code, 409)
        deleted = self.remove(session, PATH, saved_item).json
        self.assertEqual(len(deleted["items"]), 1)
        self.assertEqual(self.apply(session, saved["revision"]).status_code, 409)
        restored = self.call("POST", session, {"action": "undo", "post": PATH,
                                              "item": deleted["items"][0]["item"]}).json
        self.assertEqual(restored["items"][0]["kind"], "edit")
        self.assertNotEqual(restored["items"][0]["item"], saved_item)
        self.assertEqual(self.call("GET", session, post=PATH).json["draft"]["title"], "임시 제목")
        self.assertEqual(self.writes, [])

    def test_source_conflict_keeps_every_item_without_writing(self):
        session = self.login()
        self.stage(session)
        queued = self.remove(session).json
        self.files[SECOND] = (self.files[SECOND][0], NEW_VERSION)
        result = self.apply(session, queued["revision"])
        self.assertEqual(result.status_code, 409)
        self.assertEqual(result.json["post"], SECOND)
        self.assertEqual(self.call("GET", session).json, queued)
        self.assertEqual(self.writes, [])

    def test_upstream_failure_keeps_queue_and_lost_success_is_recovered(self):
        session = self.login()
        pending = self.remove(session).json
        self.fail = "before"
        self.assertEqual(self.apply(session, pending["revision"]).status_code, 502)
        self.assertEqual(self.call("GET", session).json, pending)
        self.fail = "after"
        self.assertEqual(self.apply(session, pending["revision"]).status_code, 502)
        self.assertEqual(self.call("GET", session).json, pending)
        self.assertEqual(self.apply(session, pending["revision"]).json["commit"], NEW_VERSION)
        self.assertEqual(self.call("GET", session).json["items"], [])
        self.assertEqual(len(self.writes), 2)
        self.assertEqual(len(self.commits), 1)

    def test_concurrent_apply_returns_the_same_commit_without_submitting_twice(self):
        session = self.login()
        pending = self.stage(session).json
        self.validation_barrier = Barrier(2)
        body = {"action": "apply", "revision": pending["revision"]}
        headers = {"Origin": ORIGIN, "Authorization": "Bearer " + session}
        app = self.client.application

        def submit():
            return app.test_client().post(BATCH, json=body, headers=headers)

        with ThreadPoolExecutor(max_workers=2) as executor:
            requests = [executor.submit(submit) for _ in range(2)]
            results = [request.result(timeout=4) for request in requests]
        self.assertEqual([result.status_code for result in results], [200, 200])
        self.assertEqual([result.json["commit"] for result in results], [NEW_VERSION, NEW_VERSION])
        self.assertEqual(len(self.writes), 1)

    def test_branch_moved_before_ref_update_keeps_queue(self):
        session = self.login()
        pending = self.stage(session).json
        original = self.upstream

        def moved_branch(url, **kwargs):
            if kwargs.get("patch") is not None:
                self.head = "d" * 40
            return original(url, **kwargs)

        with patch("editor.app.upstream_json", side_effect=moved_branch):
            result = self.apply(session, pending["revision"])
        self.assertEqual(result.status_code, 409)
        self.assertEqual(self.call("GET", session).json, pending)
        self.assertEqual(self.files[PATH][0], SOURCE)

    def test_enabling_batch_rejects_legacy_direct_writes(self):
        session = self.login()
        headers = {"Origin": ORIGIN, "Authorization": "Bearer " + session}
        for method in ("PUT", "DELETE"):
            self.assertEqual(self.client.open("/post", method=method, headers=headers, json={}).status_code, 409)
        self.assertEqual(self.writes, [])
