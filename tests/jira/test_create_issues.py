"""Isolated regression tests; no Jira requests or real credentials."""

import copy
import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError

import yaml

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/jira/create_issues.py"
SPEC = importlib.util.spec_from_file_location("create_issues", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def task(**overrides):
    return {"id": "one", "summary": "Jira 업무 생성", "story": "TEST-90", **overrides}


def issue(key, kind="Task", **fields):
    return {"key": key, "fields": {
        "project": {"key": "TEST"}, "summary": key, "issuetype": {"name": kind},
        "assignee": {"accountId": "me"},
        "status": {"name": "진행 중", "statusCategory": {"key": "indeterminate"}},
        "issuelinks": [], **fields,
    }}


class FakeClient:
    def __init__(self):
        self.config = MODULE.JiraConfig("https://example.atlassian.net", "TEST", "user", "secret")
        self.issues = {"TEST-90": issue("TEST-90", "Story")}
        self.created = []
        self.links = []
        self.matches = {}
        self.summaries = {}

    def get_issue(self, key):
        return copy.deepcopy(self.issues[key])

    def search_automation(self, automation_id):
        return self.matches.get(automation_id, [])

    def exact_summary_matches(self, summary):
        return self.summaries.get(summary, [])

    def create_task(self, entry, type_id, account):
        self.created.append(copy.deepcopy(entry))
        key = f"TEST-{len(self.created)}"
        self.issues[key] = issue(key)
        self.matches[entry["automation_id"]] = [{"key": key}]
        return key

    def link_story(self, key, story, link_type):
        self.links.append((key, story, link_type))
        self.issues[key]["fields"]["issuelinks"].append({
            "type": {"id": link_type}, "outwardIssue": {"key": story},
        })


class Response:
    def __init__(self, payload):
        self.body = json.dumps(payload).encode() if payload is not None else b""

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def read(self):
        return self.body


class PlanTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "plan.yml"
        self.client = FakeClient()
        self.plan = {"tasks": [task()]}
        self.output = redirect_stdout(io.StringIO())
        self.output.__enter__()
        self.addCleanup(self.output.__exit__, None, None, None)

    def prepare(self, plan=None, allow=False):
        plan = plan or self.plan
        MODULE.validate_tasks(plan["tasks"])
        MODULE.save_plan(self.path, plan)
        MODULE.ensure_automation_ids(self.path, plan)
        MODULE.prepare_plan(self.path, plan, self.client, "me", allow)

    def apply(self):
        return MODULE.apply_plan(self.path, self.plan, self.client, "task-type", "relates", "me")

    def test_minimal_plan_needs_no_body_or_points(self):
        MODULE.validate_tasks([task()])

    def test_reject_old_epic_points_parent_and_unrequested_automatic_fields(self):
        for name, value in (("story_points", 3), ("points", 1), ("epic", "TEST-1"),
                            ("parent", "TEST-90"), ("sprint", "active"), ("assignee", "other"),
                            ("fields", {"customfield_1": 3})):
            with self.subTest(name=name), self.assertRaises(MODULE.PlanError):
                MODULE.validate_tasks([task(**{name: value})])
        for kind in ("Epic", "Story", "Sub-task"):
            with self.subTest(kind=kind), self.assertRaises(MODULE.PlanError):
                MODULE.validate_tasks([task(issue_type=kind)])

    def test_story_required_and_must_be_jira_key(self):
        for value in (None, "", "local-story", 90, "TEST-0"):
            with self.subTest(value=value), self.assertRaises(MODULE.PlanError):
                MODULE.validate_tasks([task(story=value)])

    def test_invalid_plan_fields_and_duplicates_are_rejected(self):
        for entry in (task(summary=""), task(summary="a" * 256), task(done_when="Done"),
                      task(automation_id="wrong"), task(creation_pending=True), task(jira_key=42)):
            with self.subTest(entry=entry), self.assertRaises(MODULE.PlanError):
                MODULE.validate_tasks([entry])
        for second in (task(), task(id="two")):
            with self.assertRaises(MODULE.PlanError):
                MODULE.validate_tasks([task(), second])

    def test_all_keys_must_belong_to_configured_project(self):
        self.plan["tasks"][0]["story"] = "OTHER-90"
        with self.assertRaisesRegex(MODULE.PlanError, "프로젝트"):
            self.prepare()
        self.assertEqual(self.client.created, [])

    def test_atomic_save_failure_preserves_original_plan(self):
        MODULE.save_plan(self.path, self.plan)
        before = self.path.read_bytes()
        with patch.object(MODULE.os, "replace", side_effect=OSError("disk")):
            with self.assertRaises(OSError):
                MODULE.save_plan(self.path, {"tasks": [task(summary="Changed")]})
        self.assertEqual(self.path.read_bytes(), before)
        self.assertEqual(list(self.path.parent.iterdir()), [self.path])

    def test_automation_id_survives_preview_and_preparation_does_not_write_jira(self):
        self.prepare()
        before = self.plan["tasks"][0]["automation_id"]
        MODULE.ensure_automation_ids(self.path, self.plan)
        self.assertEqual(MODULE.load_plan(self.path)["tasks"][0]["automation_id"], before)
        self.assertEqual(self.client.created, [])
        self.assertEqual(self.client.links, [])

    def test_preflight_checks_every_story_before_creation(self):
        self.client.issues["TEST-91"] = issue("TEST-91", "Epic")
        self.plan["tasks"].append(task(id="two", summary="Second", story="TEST-91"))
        with self.assertRaisesRegex(MODULE.PlanError, "Story"):
            self.prepare()
        self.assertEqual(self.client.created, [])

    def test_creates_task_links_story_and_repeated_apply_is_idempotent(self):
        self.prepare()
        self.assertEqual(self.apply(), 1)
        self.assertEqual(MODULE.load_plan(self.path)["tasks"][0]["jira_key"], "TEST-1")
        self.prepare()
        self.assertEqual(self.apply(), 0)
        self.assertEqual(self.client.links, [("TEST-1", "TEST-90", "relates")])

    def test_link_failure_keeps_created_key_and_retry_only_links(self):
        self.prepare()
        with patch.object(self.client, "link_story", side_effect=MODULE.PlanError("link failed")):
            with self.assertRaisesRegex(MODULE.PlanError, "link failed"):
                self.apply()
        self.plan = MODULE.load_plan(self.path)
        self.assertEqual(self.plan["tasks"][0]["jira_key"], "TEST-1")
        self.prepare()
        self.assertEqual(self.apply(), 0)
        self.assertEqual(len(self.client.created), 1)

    def test_lost_create_response_recovers_key_by_automation_label(self):
        self.prepare()
        real_create = self.client.create_task

        def lost_response(*args):
            real_create(*args)
            raise MODULE.PlanError("response lost")

        with patch.object(self.client, "create_task", side_effect=lost_response):
            with self.assertRaises(MODULE.PlanError):
                self.apply()
        self.plan = MODULE.load_plan(self.path)
        self.assertTrue(self.plan["tasks"][0]["creation_pending"])
        self.prepare()
        self.assertNotIn("creation_pending", self.plan["tasks"][0])
        self.assertEqual(self.apply(), 0)
        self.assertEqual(len(self.client.created), 1)

    def test_unindexed_create_stops_instead_of_creating_duplicate(self):
        self.prepare()
        self.plan["tasks"][0]["creation_pending"] = True
        with self.assertRaisesRegex(MODULE.PlanError, "미확정"):
            self.prepare()
        self.assertEqual(self.client.created, [])

    def test_duplicate_label_or_mismatching_key_stops(self):
        self.prepare()
        entry = self.plan["tasks"][0]
        self.client.matches[entry["automation_id"]] = [{"key": "TEST-1"}, {"key": "TEST-2"}]
        with self.assertRaisesRegex(MODULE.PlanError, "중복"):
            self.prepare()
        self.client.matches[entry["automation_id"]] = [{"key": "TEST-1"}]
        entry["jira_key"] = "TEST-2"
        with self.assertRaisesRegex(MODULE.PlanError, "다릅니다"):
            self.prepare()

    def test_same_summary_requires_reviewing_existing_task(self):
        self.client.summaries[task()["summary"]] = [{"key": "TEST-7"}]
        with self.assertRaisesRegex(MODULE.PlanError, "기존 Task"):
            self.prepare()
        self.assertEqual(self.client.created, [])

    def test_existing_task_body_assignee_status_and_labels_are_preserved(self):
        self.plan["tasks"][0]["jira_key"] = "TEST-7"
        self.client.issues["TEST-7"] = issue("TEST-7", description="original", labels=["existing"])
        before = copy.deepcopy(self.client.issues["TEST-7"]["fields"])
        self.prepare()
        self.apply()
        after = self.client.issues["TEST-7"]["fields"]
        for name in ("description", "assignee", "status", "labels"):
            self.assertEqual(after[name], before[name])
        self.assertEqual(self.client.created, [])

    def test_other_owner_and_completed_task_need_explicit_acknowledgement(self):
        self.plan["tasks"][0]["jira_key"] = "TEST-7"
        for fields in ({"assignee": {"accountId": "other"}},
                       {"status": {"name": "Done", "statusCategory": {"key": "done"}}}):
            self.client.issues["TEST-7"] = issue("TEST-7", **fields)
            with self.subTest(fields=fields), self.assertRaisesRegex(MODULE.PlanError, "먼저 확인"):
                self.prepare()
            self.prepare(allow=True)

    def test_reverse_link_is_recognized_and_other_link_type_is_not(self):
        data = issue("TEST-7", issuelinks=[{"type": {"id": "relates"}, "inwardIssue": {"key": "TEST-90"}}])
        self.assertTrue(MODULE.has_story_link(data, "TEST-90", "relates"))
        self.assertFalse(MODULE.has_story_link(data, "TEST-90", "blocks"))

    def test_link_post_success_without_visible_link_is_not_reported_complete(self):
        self.prepare()
        with patch.object(self.client, "link_story"):
            with self.assertRaisesRegex(MODULE.PlanError, "결과를 확인"):
                self.apply()

    def test_same_plan_cannot_run_concurrently_and_lock_is_released(self):
        with MODULE.plan_lock(self.path):
            with self.assertRaisesRegex(MODULE.PlanError, "잠금"):
                with MODULE.plan_lock(self.path):
                    self.fail("Lock must block")
        self.assertFalse(self.path.with_name("plan.yml.lock").exists())

    def test_delete_only_allows_temporary_plan_directory(self):
        MODULE.save_plan(self.path, self.plan)
        with self.assertRaises(MODULE.PlanError):
            MODULE.delete_temporary_plan(self.path)
        with patch.object(MODULE, "TEMP_PLAN_ROOT", self.path.parent):
            MODULE.delete_temporary_plan(self.path)
        self.assertFalse(self.path.exists())

    def test_validate_only_needs_no_credentials_or_network_and_does_not_modify_file(self):
        MODULE.save_plan(self.path, self.plan)
        before = self.path.read_bytes()
        with patch.dict(os.environ, {}, clear=True), patch.object(MODULE, "JiraClient") as client:
            self.assertEqual(MODULE.main(["--file", str(self.path), "--validate-only"]), 0)
            client.assert_not_called()
        self.assertEqual(self.path.read_bytes(), before)

    def test_cli_default_is_preview_and_invalid_option_combinations_fail(self):
        self.assertFalse(MODULE.parse_args(["--file", "plan.yml"]).apply)
        for args in (["--check", "--apply"], ["--file", "a.yml", "--delete-on-success"],
                     ["--file", "a.yml", "--validate-only", "--apply"]):
            with self.subTest(args=args), redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                MODULE.parse_args(args)


class ApiContractTests(unittest.TestCase):
    def setUp(self):
        self.client = MODULE.JiraClient(MODULE.JiraConfig("https://example.atlassian.net", "TEST", "user", "secret"))

    def test_task_payload_has_no_parent_points_or_sprint(self):
        entry = task(automation_id="jira-auto-" + "a" * 32)
        with patch.object(self.client, "request", return_value={"key": "TEST-7"}) as request:
            self.assertEqual(self.client.create_task(entry, "task-id", "me"), "TEST-7")
        body = request.call_args.args[2]["fields"]
        self.assertEqual(set(body), {"project", "issuetype", "summary", "assignee", "labels"})
        self.assertEqual(body["issuetype"], {"id": "task-id"})

    def test_task_optional_description_is_adf_without_forced_headings(self):
        entry = task(description="Purpose\n\nScope", done_when=["Verified"])
        content = MODULE.adf_description(entry)["content"]
        self.assertEqual([item["type"] for item in content], ["paragraph"] * 3 + ["bulletList"])

    def test_link_payload_uses_related_issue_link_without_comment(self):
        with patch.object(self.client, "request") as request:
            self.client.link_story("TEST-7", "TEST-90", "10003")
        self.assertEqual(request.call_args.args, ("/rest/api/3/issueLink", "POST", {
            "type": {"id": "10003"}, "inwardIssue": {"key": "TEST-7"}, "outwardIssue": {"key": "TEST-90"}}))

    def test_localized_symmetric_relates_type_and_ambiguous_type(self):
        for label in ("relates to", "다음과 관련됨"):
            data = {"issueLinkTypes": [{"id": "1", "inward": label, "outward": label}]}
            with patch.object(self.client, "request", return_value=data):
                self.assertEqual(self.client.relates_type(), "1")
        with patch.object(self.client, "request", return_value={"issueLinkTypes": []}):
            with self.assertRaises(MODULE.PlanError):
                self.client.relates_type()

    def test_task_metadata_is_paginated_and_cached(self):
        # PageOfCreateMetaIssueTypes uses total/startAt, not isLast.
        pages = [{"issueTypes": [{"id": "1", "name": "스토리"}], "total": 2, "startAt": 0, "maxResults": 1},
                 {"issueTypes": [{"id": "2", "name": "작업"}], "total": 2, "startAt": 1, "maxResults": 1}]
        with patch.object(self.client, "request", side_effect=pages) as request:
            self.assertEqual(self.client.task_type(), "2")
            self.assertEqual(self.client.task_type(), "2")
        self.assertEqual(request.call_count, 2)
        self.assertIn("startAt=1", request.call_args.args[0])

    def test_summary_search_checks_later_pages_and_uses_type_id(self):
        self.client._task_type = "10002"
        pages = [{"issues": [{"key": "TEST-1", "fields": {"summary": "different"}}], "nextPageToken": "next", "isLast": False},
                 {"issues": [{"key": "TEST-7", "fields": {"summary": "exact"}}], "isLast": True}]
        with patch.object(self.client, "request", side_effect=pages) as request:
            matches = self.client.exact_summary_matches("exact")
        self.assertEqual(matches[0]["key"], "TEST-7")
        self.assertEqual(request.call_args.args[2]["nextPageToken"], "next")
        self.assertIn('issuetype = "10002"', request.call_args.args[2]["jql"])

    def test_empty_201_response_is_supported(self):
        with patch.object(self.client.opener, "open", return_value=Response(None)):
            self.assertIsNone(self.client.request("/rest/api/3/issueLink", "POST", {}))

    def test_cli_apply_and_retry_with_real_client_api_contract(self):
        state = {"created": False, "linked": False}
        writes = []

        def transport(path, method="GET", payload=None):
            if path == "/rest/api/3/myself":
                return {"accountId": "me"}
            if "/createmeta/" in path:
                return {"issueTypes": [{"id": "10002", "name": "Task"}], "total": 1}
            if path == "/rest/api/3/issueLinkType":
                return {"issueLinkTypes": [{"id": "10003", "inward": "relates to", "outward": "relates to"}]}
            if path.startswith("/rest/api/3/issue/TEST-90?"):
                return issue("TEST-90", "Story")
            if path == "/rest/api/3/search/jql":
                matches = [{"key": "TEST-7", "fields": {"summary": task()["summary"]}}] if state["created"] else []
                return {"issues": matches, "isLast": True}
            if path == "/rest/api/3/issue" and method == "POST":
                writes.append((path, copy.deepcopy(payload)))
                self.assertEqual(set(payload["fields"]), {"project", "issuetype", "summary", "assignee", "labels"})
                state["created"] = True
                return {"key": "TEST-7"}
            if path.startswith("/rest/api/3/issue/TEST-7?"):
                links = [{"type": {"id": "10003"}, "inwardIssue": {"key": "TEST-90"}}] if state["linked"] else []
                return issue("TEST-7", issuelinks=links)
            if path == "/rest/api/3/issueLink" and method == "POST":
                writes.append((path, copy.deepcopy(payload)))
                state["linked"] = True
                return None
            self.fail(f"Unexpected request: {method} {path}")

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "plan.yml"
            MODULE.save_plan(path, {"tasks": [task()]})
            with patch.object(MODULE, "load_dotenv"), \
                 patch.object(MODULE, "load_config", return_value=self.client.config), \
                 patch.object(MODULE.JiraClient, "request", side_effect=transport), redirect_stdout(io.StringIO()):
                self.assertEqual(MODULE.main(["--file", str(path), "--apply"]), 0)
                self.assertEqual(MODULE.main(["--file", str(path), "--apply"]), 0)
            self.assertEqual(MODULE.load_plan(path)["tasks"][0]["jira_key"], "TEST-7")
        self.assertEqual([path for path, _ in writes], ["/rest/api/3/issue", "/rest/api/3/issueLink"])

    def test_http_errors_never_echo_response_body_or_token(self):
        error = HTTPError("https://example.atlassian.net", 403, "denied", {}, io.BytesIO(b"secret"))
        with patch.object(self.client.opener, "open", side_effect=error):
            with self.assertRaises(MODULE.PlanError) as caught:
                self.client.request("/rest/api/3/issue", "POST", {})
        self.assertNotIn("secret", str(caught.exception))
        self.assertIn("403", str(caught.exception))
        self.assertNotIn("secret", repr(self.client.config))

    def test_connection_check_and_preview_do_not_transition_issues(self):
        self.assertTrue(MODULE.parse_args(["--check"]).check)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "plan.yml"
            MODULE.save_plan(path, {"tasks": [task()]})
            with patch.object(MODULE, "load_config", return_value=self.client.config), \
                 patch.object(MODULE.JiraClient, "current_account", return_value="me"), \
                 patch.object(MODULE.JiraClient, "task_type", return_value="task"), \
                 patch.object(MODULE.JiraClient, "relates_type", return_value="relates"), \
                 patch.object(MODULE, "prepare_plan") as prepare, \
                 patch.object(MODULE, "apply_plan") as apply, redirect_stdout(io.StringIO()):
                self.assertEqual(MODULE.main(["--file", str(path)]), 0)
                prepare.assert_called_once()
                apply.assert_not_called()

    def test_credential_configuration_does_not_require_board_id(self):
        env = {"JIRA_BASE_URL": "https://example.atlassian.net", "JIRA_EMAIL": "user", "JIRA_API_TOKEN": "secret"}
        with patch.dict(os.environ, env, clear=True):
            self.assertEqual(MODULE.load_config().project_key, "S15P31E102")
        for url in ("http://example.com", "https://user:secret@example.com", "https://example.com/path"):
            with patch.dict(os.environ, {**env, "JIRA_BASE_URL": url}, clear=True):
                with self.assertRaises(MODULE.PlanError):
                    MODULE.load_config()


if __name__ == "__main__":
    unittest.main()
