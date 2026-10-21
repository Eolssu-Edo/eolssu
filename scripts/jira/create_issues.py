#!/usr/bin/env python3
"""Create Tasks and relate them to existing Stories from a reviewed YAML plan.

Adapted from S15P21E106/scripts/jira/create_issues.py at 7d86dab.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import tempfile
import uuid
from contextlib import contextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

import yaml


ROOT = Path(__file__).resolve().parents[2]
TEMP_PLAN_ROOT = ROOT / ".jira" / "tmp"
DEFAULT_PROJECT = "S15P31E102"
KEY_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]*-[1-9][0-9]*$")
AUTOMATION_PATTERN = re.compile(r"^jira-auto-[0-9a-f]{32}$")
TYPE_ALIASES = {"Task": {"task", "작업"}, "Story": {"story", "스토리"}}
PLAN_FIELDS = {
    "id", "summary", "story", "description", "done_when", "priority",
    "issue_type", "jira_key", "automation_id", "creation_pending",
}


class PlanError(ValueError):
    """An invalid plan or an operation that needs human attention."""


@dataclass(frozen=True)
class JiraConfig:
    base_url: str
    project_key: str
    email: str
    api_token: str = field(repr=False)


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise PlanError(f".env:{number}: KEY=VALUE 형식이 필요합니다.")
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


def load_config() -> JiraConfig:
    required = ("JIRA_BASE_URL", "JIRA_EMAIL", "JIRA_API_TOKEN")
    missing = [name for name in required if not os.environ.get(name, "").strip()]
    if missing:
        raise PlanError(f"필수 환경 변수가 없습니다: {', '.join(missing)}")
    base_url = os.environ["JIRA_BASE_URL"].rstrip("/")
    parsed = urlsplit(base_url)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username
            or parsed.password or parsed.path or parsed.query or parsed.fragment):
        raise PlanError("JIRA_BASE_URL에는 경로·인증정보 없는 HTTPS 사이트 주소를 입력하세요.")
    project = os.environ.get("JIRA_PROJECT_KEY", DEFAULT_PROJECT)
    if not re.fullmatch(r"[A-Z][A-Z0-9_]*", project):
        raise PlanError("JIRA_PROJECT_KEY 형식이 올바르지 않습니다.")
    return JiraConfig(base_url, project, os.environ["JIRA_EMAIL"], os.environ["JIRA_API_TOKEN"])


def validate_tasks(tasks: list[Any]) -> None:
    if not tasks:
        raise PlanError("tasks에 Task가 하나 이상 필요합니다.")
    ids: set[str] = set()
    automation_ids: set[str] = set()
    jira_keys: set[str] = set()
    summaries: set[str] = set()
    for index, task in enumerate(tasks, 1):
        label = f"tasks[{index}]"
        if not isinstance(task, dict):
            raise PlanError(f"{label}는 객체여야 합니다.")
        unknown = set(task) - PLAN_FIELDS
        if unknown:
            raise PlanError(f"{label}: 지원하지 않는 필드 {sorted(map(str, unknown))}. 포인트·Epic·parent·sprint는 입력하지 않습니다.")
        for name in ("id", "summary", "story"):
            if not isinstance(task.get(name), str) or not task[name].strip():
                raise PlanError(f"{label}.{name}은 비어 있지 않은 문자열이어야 합니다.")
        if task["id"] in ids:
            raise PlanError(f"중복 id: {task['id']}")
        ids.add(task["id"])
        if len(task["summary"]) > 255:
            raise PlanError(f"{label}.summary는 255자 이하여야 합니다.")
        if task["summary"] in summaries:
            raise PlanError("계획에 같은 제목의 Task가 있습니다. 업무 목적을 구분해 주세요.")
        summaries.add(task["summary"])
        if task.get("issue_type", "Task") != "Task":
            raise PlanError("Task만 생성합니다. Story가 없으면 사지한에게 생성을 요청하세요.")
        for name in ("story", "jira_key"):
            value = task.get(name)
            if value is not None and (not isinstance(value, str) or not KEY_PATTERN.fullmatch(value)):
                raise PlanError(f"{label}.{name}에는 실제 Jira Key를 입력하세요.")
        key = task.get("jira_key")
        if key:
            if key in jira_keys or key == task["story"]:
                raise PlanError(f"중복 Task Key 또는 자기 자신 연결: {key}")
            jira_keys.add(key)
        for name in ("description", "priority"):
            if name in task and (not isinstance(task[name], str) or not task[name].strip()):
                raise PlanError(f"{label}.{name}은 비어 있지 않은 문자열이어야 합니다.")
        if "done_when" in task and (
            not isinstance(task["done_when"], list) or not task["done_when"]
            or not all(isinstance(item, str) and item.strip() for item in task["done_when"])
        ):
            raise PlanError(f"{label}.done_when은 비어 있지 않은 문자열 목록이어야 합니다.")
        automation_id = task.get("automation_id")
        if automation_id is not None:
            if not isinstance(automation_id, str) or not AUTOMATION_PATTERN.fullmatch(automation_id):
                raise PlanError(f"{label}.automation_id 형식이 올바르지 않습니다.")
            if automation_id in automation_ids:
                raise PlanError("automation_id는 계획 안에서 고유해야 합니다.")
            automation_ids.add(automation_id)
        if "creation_pending" in task and not isinstance(task["creation_pending"], bool):
            raise PlanError(f"{label}.creation_pending은 boolean이어야 합니다.")
        if task.get("creation_pending") and not automation_id:
            raise PlanError("응답 미확정 Task의 automation_id를 보존해야 합니다.")


def load_plan(path: Path) -> dict[str, Any]:
    try:
        plan = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        raise PlanError("계획 파일의 YAML 문법이 올바르지 않습니다.") from error
    if not isinstance(plan, dict) or set(plan) != {"tasks"} or not isinstance(plan["tasks"], list):
        raise PlanError("최상위에는 tasks 목록만 입력합니다.")
    validate_tasks(plan["tasks"])
    return plan


def validate_project_keys(plan: dict[str, Any], project: str) -> None:
    for task in plan["tasks"]:
        for name in ("story", "jira_key"):
            if task.get(name) and not task[name].startswith(project + "-"):
                raise PlanError(f"{task['id']}.{name}은 {project} 프로젝트 Key여야 합니다.")


def save_plan(path: Path, plan: dict[str, Any]) -> None:
    """Replace atomically so a partial write cannot discard issued Jira keys."""
    temporary: str | None = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                         prefix=path.name + ".", delete=False) as stream:
            temporary = stream.name
            yaml.safe_dump(plan, stream, allow_unicode=True, sort_keys=False)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if temporary and os.path.exists(temporary):
            os.unlink(temporary)


@contextmanager
def plan_lock(path: Path):
    lock = path.with_name(path.name + ".lock")
    try:
        descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    except FileExistsError as error:
        raise PlanError(f"다른 실행 또는 남은 잠금이 있습니다: {lock}. 실행 중인 프로세스를 먼저 확인하세요.") from error
    try:
        os.close(descriptor)
        yield
    finally:
        lock.unlink()


def ensure_automation_ids(path: Path, plan: dict[str, Any]) -> None:
    changed = False
    for task in plan["tasks"]:
        if not task.get("automation_id"):
            task["automation_id"] = "jira-auto-" + uuid.uuid4().hex
            changed = True
    if changed:
        save_plan(path, plan)


def adf_description(task: dict[str, Any]) -> dict[str, Any] | None:
    content: list[dict[str, Any]] = []
    for line in task.get("description", "").splitlines():
        content.append({"type": "paragraph", "content": [{"type": "text", "text": line}] if line else []})
    if task.get("done_when"):
        content.append({"type": "bulletList", "content": [
            {"type": "listItem", "content": [{"type": "paragraph", "content": [
                {"type": "text", "text": item}]}]} for item in task["done_when"]
        ]})
    return {"type": "doc", "version": 1, "content": content} if content else None


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise PlanError("Jira API 리다이렉트가 발생했습니다. JIRA_BASE_URL을 확인하세요.")


class JiraClient:
    def __init__(self, config: JiraConfig):
        self.config = config
        self.opener = build_opener(NoRedirect())
        self._task_type: str | None = None

    def request(self, path: str, method: str = "GET", payload: dict[str, Any] | None = None) -> Any:
        credentials = base64.b64encode(f"{self.config.email}:{self.config.api_token}".encode()).decode()
        request = Request(self.config.base_url + path, method=method,
                          data=json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None,
                          headers={"Accept": "application/json", "Content-Type": "application/json",
                                   "Authorization": "Basic " + credentials})
        try:
            with self.opener.open(request, timeout=30) as response:
                body = response.read()
                return json.loads(body) if body else None
        except HTTPError as error:
            # Do not log response bodies, which can echo credentials or private data.
            raise PlanError(f"Jira API {method} {path}: HTTP {error.code}. 권한과 입력을 확인하세요.") from error
        except (URLError, TimeoutError, OSError) as error:
            raise PlanError("Jira 연결 실패. 생성 요청 중이었다면 같은 계획 파일로 재확인하세요.") from error
        except (ValueError, UnicodeError) as error:
            raise PlanError("Jira API 응답이 JSON이 아닙니다. 생성 결과를 확인하세요.") from error

    def current_account(self) -> str:
        account = self.request("/rest/api/3/myself").get("accountId")
        if not account:
            raise PlanError("인증 계정의 accountId를 확인하지 못했습니다.")
        return str(account)

    def task_type(self) -> str:
        if self._task_type is not None:
            return self._task_type
        types = []
        start = 0
        while True:
            page = self.request(f"/rest/api/3/issue/createmeta/{quote(self.config.project_key, safe='')}/issuetypes?startAt={start}&maxResults=100")
            values = page.get("issueTypes", [])
            types.extend(values)
            start += len(values)
            if start >= page.get("total", start):
                break
            if not values:
                raise PlanError("Jira Task 유형 목록의 페이지가 비어 있습니다.")
        matches = [item for item in types if str(item.get("name", "")).casefold() in TYPE_ALIASES["Task"]]
        if len(matches) != 1:
            raise PlanError("생성 가능한 Task(작업) 유형을 하나로 결정하지 못했습니다.")
        self._task_type = str(matches[0]["id"])
        return self._task_type

    def relates_type(self) -> str:
        aliases = {"relates to", "다음과 관련됨"}
        matches = [item for item in self.request("/rest/api/3/issueLinkType").get("issueLinkTypes", [])
                   if str(item.get("inward", "")).casefold() in aliases
                   and str(item.get("outward", "")).casefold() in aliases]
        if len(matches) != 1:
            raise PlanError("'다음과 관련됨' 연결 유형을 하나로 결정하지 못했습니다. 관리자에게 확인하세요.")
        return str(matches[0]["id"])

    def get_issue(self, key: str) -> dict[str, Any]:
        issue = self.request(f"/rest/api/3/issue/{quote(key, safe='')}?fields=project,summary,issuetype,status,assignee,issuelinks,labels")
        if issue.get("fields", {}).get("project", {}).get("key") != self.config.project_key:
            raise PlanError(f"다른 프로젝트의 업무입니다: {key}")
        return issue

    def search(self, clause: str) -> list[dict[str, Any]]:
        issues: list[dict[str, Any]] = []
        token: str | None = None
        seen_tokens: set[str] = set()
        while True:
            body: dict[str, Any] = {"jql": f'project = "{self.config.project_key}" AND ({clause})',
                                    "fields": ["summary"], "maxResults": 100}
            if token:
                body["nextPageToken"] = token
            page = self.request("/rest/api/3/search/jql", "POST", body)
            issues.extend(page.get("issues", []))
            token = page.get("nextPageToken")
            if page.get("isLast", not token) or not token:
                return issues
            if token in seen_tokens:
                raise PlanError("Jira 검색 페이지 토큰이 반복됩니다.")
            seen_tokens.add(token)

    def search_automation(self, automation_id: str) -> list[dict[str, Any]]:
        return self.search(f'labels = "{automation_id}"')

    def exact_summary_matches(self, summary: str) -> list[dict[str, Any]]:
        # Exact comparison avoids Lucene escaping/phrase-search false negatives.
        return [issue for issue in self.search(f'issuetype = "{self.task_type()}"')
                if issue.get("fields", {}).get("summary") == summary]

    def create_task(self, task: dict[str, Any], type_id: str, account: str) -> str:
        fields: dict[str, Any] = {
            "project": {"key": self.config.project_key}, "issuetype": {"id": type_id},
            "summary": task["summary"], "assignee": {"accountId": account},
            "labels": [task["automation_id"]],
        }
        description = adf_description(task)
        if description:
            fields["description"] = description
        if task.get("priority"):
            fields["priority"] = {"name": task["priority"]}
        result = self.request("/rest/api/3/issue", "POST", {"fields": fields})
        key = result.get("key") if isinstance(result, dict) else None
        if not isinstance(key, str) or not KEY_PATTERN.fullmatch(key) or not key.startswith(self.config.project_key + "-"):
            raise PlanError("생성 응답의 Jira Key를 확인하지 못했습니다. 같은 계획 파일을 보존하세요.")
        return key

    def link_story(self, key: str, story: str, link_type: str) -> None:
        self.request("/rest/api/3/issueLink", "POST", {
            "type": {"id": link_type}, "inwardIssue": {"key": key}, "outwardIssue": {"key": story},
        })


def require_type(issue: dict[str, Any], logical_type: str) -> None:
    if str(issue.get("fields", {}).get("issuetype", {}).get("name", "")).casefold() not in TYPE_ALIASES[logical_type]:
        raise PlanError(f"{issue.get('key')}: {logical_type} 유형이어야 합니다.")


def has_story_link(issue: dict[str, Any], story: str, link_type: str) -> bool:
    return any(str(link.get("type", {}).get("id")) == link_type
               and any(link.get(side, {}).get("key") == story for side in ("inwardIssue", "outwardIssue"))
               for link in issue.get("fields", {}).get("issuelinks", []))


def prepare_plan(path: Path, plan: dict[str, Any], client: JiraClient,
                 account: str, allow_existing_task: bool = False) -> None:
    """Check every Story and recovery candidate before the first Jira write."""
    validate_project_keys(plan, client.config.project_key)
    resolved_keys: set[str] = set()
    for task in plan["tasks"]:
        story = client.get_issue(task["story"])
        require_type(story, "Story")
        matches = client.search_automation(task["automation_id"])
        if len(matches) > 1:
            raise PlanError(f"중복된 automation_id: {task['id']}. Jira에서 직접 확인하세요.")
        recovered_key = matches[0]["key"] if matches else None
        if recovered_key and task.get("jira_key") and task["jira_key"] != recovered_key:
            raise PlanError("계획의 jira_key와 automation_id 검색 결과가 다릅니다.")
        if recovered_key:
            task["jira_key"] = recovered_key
            task.pop("creation_pending", None)
            save_plan(path, plan)
        key = task.get("jira_key")
        if key:
            if key in resolved_keys:
                raise PlanError(f"여러 계획 항목이 같은 Task를 가리킵니다: {key}")
            resolved_keys.add(key)
            issue = client.get_issue(key)
            require_type(issue, "Task")
            fields = issue["fields"]
            owner = (fields.get("assignee") or {}).get("accountId")
            done = fields.get("status", {}).get("statusCategory", {}).get("key") == "done"
            if (owner not in (None, account) or done) and not allow_existing_task:
                raise PlanError(f"{key}: 타 담당자 또는 완료 Task입니다. 먼저 확인하고 --allow-existing-task로 명시하세요.")
            print(f"재사용 {key}: {fields.get('summary')} / 담당자={owner or '미할당'} / 상태={fields.get('status', {}).get('name')}")
        elif task.get("creation_pending"):
            raise PlanError(f"{task['id']}: 이전 생성 응답이 미확정입니다. 잠시 후 같은 파일로 재조회하고, Jira 확인 없이 creation_pending을 지우지 마세요.")
        else:
            candidates = client.exact_summary_matches(task["summary"])
            if candidates:
                keys = ", ".join(item["key"] for item in candidates)
                raise PlanError(f"같은 제목의 기존 Task: {keys}. 범위·담당자·상태를 확인한 뒤 jira_key를 지정하세요.")
        print(f"{task['id']}: {key or '새 Task'} → {task['story']} {story['fields'].get('summary')} (다음과 관련됨)")


def apply_plan(path: Path, plan: dict[str, Any], client: JiraClient,
               type_id: str, link_type: str, account: str) -> int:
    created = 0
    for task in plan["tasks"]:
        key = task.get("jira_key")
        if not key:
            task["creation_pending"] = True
            save_plan(path, plan)
            key = client.create_task(task, type_id, account)
            task["jira_key"] = key
            task.pop("creation_pending", None)
            save_plan(path, plan)
            created += 1
            print(f"생성 완료: {key}")
        issue = client.get_issue(key)
        if not has_story_link(issue, task["story"], link_type):
            client.link_story(key, task["story"], link_type)
        if not has_story_link(client.get_issue(key), task["story"], link_type):
            raise PlanError(f"{key}: Story 연결 결과를 확인하지 못했습니다. 같은 파일로 다시 실행하세요.")
        print(f"연결 확인: {key} ↔ {task['story']}")
    return created


def delete_temporary_plan(path: Path) -> None:
    if not path.resolve().is_relative_to(TEMP_PLAN_ROOT.resolve()):
        raise PlanError("--delete-on-success는 .jira/tmp/ 아래의 계획 파일에만 사용할 수 있습니다.")
    path.unlink()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--file", type=Path)
    source.add_argument("--check", action="store_true", help="인증·Task 유형·관련됨 유형을 읽기 전용으로 확인")
    parser.add_argument("--validate-only", action="store_true", help="환경 변수·외부 호출 없이 YAML만 검사")
    parser.add_argument("--apply", action="store_true", help="Task 생성·Story 연결 실행")
    parser.add_argument("--allow-existing-task", action="store_true", help="확인한 타 담당자/완료 Task의 연결 허용; 상태·담당자는 유지")
    parser.add_argument("--delete-on-success", action="store_true")
    args = parser.parse_args(argv)
    if args.check and (args.apply or args.validate_only or args.delete_on_success or args.allow_existing_task):
        parser.error("--check는 단독으로 사용합니다.")
    if args.validate_only and (args.apply or args.delete_on_success or args.allow_existing_task):
        parser.error("--validate-only는 실행 옵션과 함께 사용할 수 없습니다.")
    if args.delete_on_success and not args.apply:
        parser.error("--delete-on-success에는 --apply가 필요합니다.")
    return args


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        if args.validate_only:
            load_plan(args.file)
            print("YAML 검사 통과. 실제 Story 존재·권한은 확인하지 않았습니다.")
            return 0
        load_dotenv(ROOT / ".env")
        config = load_config()
        client = JiraClient(config)
        account = client.current_account()
        type_id = client.task_type()
        link_type = client.relates_type()
        if args.check:
            print(f"연결 확인: {config.project_key}, Task 유형={type_id}, 관련됨 유형={link_type}")
            return 0
        path = args.file.resolve()
        if args.delete_on_success and not path.is_relative_to(TEMP_PLAN_ROOT.resolve()):
            raise PlanError("--delete-on-success는 .jira/tmp/ 아래의 계획 파일에만 사용할 수 있습니다.")
        with plan_lock(path):
            plan = load_plan(path)
            ensure_automation_ids(path, plan)
            prepare_plan(path, plan, client, account, args.allow_existing_task)
            if not args.apply:
                print("미리보기 완료. Jira 변경 없음. 검토 후 같은 파일에 --apply를 사용하세요.")
                return 0
            created = apply_plan(path, plan, client, type_id, link_type, account)
            if args.delete_on_success:
                delete_temporary_plan(path)
            print(f"완료: 신규 Task {created}개, 전체 Story 연결 확인.")
        return 0
    except (PlanError, OSError) as error:
        print(f"오류: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    # Windows consoles can otherwise fail after an API write on Korean/arrows.
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())
