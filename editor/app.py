"""Editor and page-view API for the static Team Edo blog."""

import base64
import binascii
import hashlib
import json
import os
import re
import sqlite3
from concurrent.futures import ThreadPoolExecutor
from contextlib import closing
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener
from uuid import uuid4

import yaml
from flask import Flask, abort, jsonify, request
from itsdangerous import BadData, URLSafeTimedSerializer
from werkzeug.exceptions import HTTPException
from yaml.nodes import MappingNode, ScalarNode
from yaml.tokens import AliasToken, AnchorToken

GITHUB = "https://github.com"
GITHUB_API = "https://api.github.com"
REPOSITORY_API = GITHUB_API + "/repos/team-edo/eolssu"
BRANCH = "blog"
POST_PATH = re.compile(r"posts/\d{4}-\d{2}-\d{2}-[a-z0-9][a-z0-9-]{0,175}\.md")
PUBLIC_POST_PATH = re.compile(r"/posts/[0-9]{4}/(?:0[1-9]|1[0-2])/[a-z0-9][a-z0-9-]{0,175}/")
KOREA_TIME = timezone(timedelta(hours=9))
COMMIT = re.compile(r"[a-f0-9]{40}")
VERIFIER = re.compile(r"[A-Za-z0-9._~-]{43,128}")
EMAIL = re.compile(r"[^\s@]+@[^\s@]+\.[^\s@]+")
FRONT_MATTER = re.compile(r"\A---[ \t]*\r?\n(.*?)\r?\n---[ \t]*\r?\n", re.DOTALL)
YAML_STRING = "tag:yaml.org,2002:str"
MAX_SOURCE = 250_000
MAX_QUEUE = 20
SESSION_SECONDS = 15 * 60
QUEUE_REVISION = re.compile(r"[a-f0-9]{64}")
BATCH_CHANGED = "대기 목록이 바뀌었습니다. 목록을 다시 확인해 주세요."


class UpstreamError(Exception):
    def __init__(self, status):
        super().__init__(status)
        self.status = status


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


upstream_http = build_opener(NoRedirect)


def upstream_json(url, *, headers=None, form=None, update=None, delete=None, submit=None, patch=None,
                  expected_type=dict):
    headers = dict(headers or {}, Accept="application/json", **{"User-Agent": "Team-Edo-Blog-Editor"})
    data = None
    method = "GET"
    if form is not None:
        method = "POST"
        data = urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    for verb, payload in (("PUT", update), ("DELETE", delete), ("POST", submit), ("PATCH", patch)):
        if payload is not None:
            method = verb
            data = json.dumps(payload, ensure_ascii=False).encode()
            headers["Content-Type"] = "application/json"
    upstream = Request(url, data, headers, method=method)
    try:
        with upstream_http.open(upstream, timeout=5) as response:
            if delete is not None:
                return None
            result = json.loads(response.read(2 * MAX_SOURCE))
    except HTTPError as error:
        raise UpstreamError(error.code) from None
    except (URLError, TimeoutError, ValueError):
        raise UpstreamError(502) from None
    if not isinstance(result, expected_type):
        raise UpstreamError(502)
    return result


@dataclass(frozen=True)
class Post:
    source: str
    front_matter: re.Match
    title: ScalarNode
    author: str
    slug: str | None
    version: str

    @property
    def markdown(self):
        return self.source[self.front_matter.end():]

    def with_edit(self, title, markdown):
        prefix = self.source[:self.front_matter.end()]
        if title != self.title.value:
            if not self.slug:
                abort(422, description="글 주소가 바뀔 수 있어 이 글의 제목은 수정할 수 없습니다.")
            start = self.front_matter.start(1) + self.title.start_mark.index
            end = self.front_matter.start(1) + self.title.end_mark.index
            old_title = self.source[start:end]
            trailing_break = re.search(r"\r?\n\Z", old_title)
            line_break = trailing_break[0] if trailing_break else ""
            prefix = prefix[:start] + json.dumps(title, ensure_ascii=False) + line_break + prefix[end:]
        return prefix + markdown


def parse_post(file):
    try:
        source = base64.b64decode(file["content"].replace("\n", ""), validate=True).decode()
        version = file["sha"]  # Blob sha: changes whenever the file content changes.
    except (AttributeError, KeyError, TypeError, UnicodeError, binascii.Error):
        abort(502, description="원본 글을 읽을 수 없습니다.")
    if not isinstance(version, str) or not COMMIT.fullmatch(version):
        abort(502, description="원본 글의 버전을 확인할 수 없습니다.")
    front_matter = FRONT_MATTER.match(source)
    if not front_matter or len(source.encode()) > MAX_SOURCE:
        abort(403, description="이 글은 편집할 수 없습니다.")
    try:
        if any(isinstance(token, (AliasToken, AnchorToken)) for token in yaml.scan(front_matter[1])):
            abort(403, description="이 글은 편집할 수 없습니다.")
        metadata = yaml.compose(front_matter[1], Loader=yaml.SafeLoader)
    except yaml.YAMLError:
        abort(403, description="이 글은 편집할 수 없습니다.")
    fields = {}
    if not isinstance(metadata, MappingNode):
        abort(403, description="이 글은 편집할 수 없습니다.")
    for key, value in metadata.value:
        if not isinstance(key, ScalarNode) or key.tag != YAML_STRING or key.value in fields:
            abort(403, description="이 글은 편집할 수 없습니다.")
        fields[key.value] = value

    def string_field(key):
        field = fields.get(key)
        return field.value if isinstance(field, ScalarNode) and field.tag == YAML_STRING else None

    author = string_field("author")
    draft = fields.get("draft")
    if not author or not string_field("title") or (
        draft is not None and not (
            isinstance(draft, ScalarNode) and draft.tag == "tag:yaml.org,2002:bool"
            and draft.value.lower() == "false"
        )
    ):
        abort(403, description="이 글은 편집할 수 없습니다.")
    return Post(source, front_matter, fields["title"], author, string_field("slug"), version)


def read_secret(name):
    filename = os.environ.get(name + "_FILE")
    return Path(filename).read_text(encoding="utf-8").strip() if filename else os.environ.get(name, "")


def queue_connection(path):
    db = sqlite3.connect(path, timeout=2)
    db.row_factory = sqlite3.Row
    return db


def queue_state(db, owner):
    rows = db.execute("""
        SELECT path, item, draft_title, delete_title FROM editor_queue
        WHERE owner = ? ORDER BY path
    """, (owner,)).fetchall()
    revision = hashlib.sha256("".join(row["item"] for row in rows).encode()).hexdigest()
    return {
        "revision": revision,
        "items": [
            {"post": row["path"], "item": row["item"],
             "kind": "delete" if row["delete_title"] is not None else "edit",
             "title": row["delete_title"] or row["draft_title"]}
            for row in rows
        ],
    }


def queue_entry(db, owner, path):
    return db.execute("SELECT * FROM editor_queue WHERE owner = ? AND path = ?", (owner, path)).fetchone()


def validate_edit(data, post):
    title, markdown = data.get("title"), data.get("markdown")
    if (
        not isinstance(title, str) or not 1 <= len(title.strip()) <= 160
        or any(ord(char) < 32 for char in title)
        or not isinstance(markdown, str) or not markdown.strip() or "\x00" in markdown
    ):
        abort(422, description="제목과 본문을 확인해 주세요.")
    if re.search(r"\{\{[<%]", markdown):
        abort(422, description="본문에는 일반 Markdown만 사용할 수 있습니다.")
    content = post.with_edit(title.strip(), markdown)
    try:
        size = len(content.encode())
    except UnicodeEncodeError:
        abort(422, description="제목과 본문에 저장할 수 없는 문자가 있습니다.")
    if size > MAX_SOURCE:
        abort(413, description="글이 너무 깁니다.")
    return title.strip(), markdown, content


def create_app(config=None):
    app = Flask(__name__)
    app.config.from_mapping(
        BLOG_ORIGIN=os.environ.get("BLOG_ORIGIN", ""),
        CLIENT_ID=os.environ.get("EDITOR_GITHUB_CLIENT_ID", ""),
        CLIENT_SECRET=read_secret("EDITOR_GITHUB_CLIENT_SECRET"),
        WRITE_TOKEN=read_secret("EDITOR_GITHUB_WRITE_TOKEN"),
        SESSION_SECRET=read_secret("EDITOR_SESSION_SECRET"),
        AUTHOR_EMAILS_FILE=os.environ.get("EDITOR_GITHUB_AUTHOR_EMAILS_FILE", ""),
        MAX_CONTENT_LENGTH=MAX_SOURCE + 20_000,
        VIEWS_DB=os.environ.get("BLOG_VIEWS_DB", ""),
        QUEUE_DB=os.environ.get("BLOG_QUEUE_DB", ""),
    )
    if config:
        app.config.update(config)
    origin = app.config["BLOG_ORIGIN"]
    parsed = urlsplit(origin)
    local_http = parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost"}
    if (
        not (parsed.scheme == "https" or local_http)
        or not parsed.hostname
        or parsed.username is not None
        or origin != f"{parsed.scheme}://{parsed.netloc}"
    ):
        raise ValueError("BLOG_ORIGIN must be the exact HTTPS blog origin (HTTP is allowed on loopback).")
    for setting in ("CLIENT_ID", "CLIENT_SECRET", "AUTHOR_EMAILS_FILE"):
        if not app.config[setting]:
            raise ValueError(f"Missing editor configuration: {setting}")
    writes_enabled = bool(app.config["WRITE_TOKEN"])
    queue_db = app.config["QUEUE_DB"] if writes_enabled else ""
    if writes_enabled and len(app.config["SESSION_SECRET"]) < 32:
        raise ValueError("EDITOR_SESSION_SECRET must contain at least 32 characters.")
    owners = json.loads(Path(app.config["AUTHOR_EMAILS_FILE"]).read_text(encoding="utf-8"))
    if (
        not isinstance(owners, dict) or not owners
        or any(not name.strip() or name == "Team Edo" or not isinstance(email, str)
               or not EMAIL.fullmatch(email) for name, email in owners.items())
    ):
        raise ValueError("The author map must connect individual authors to GitHub email addresses.")
    authors_by_email = {email.casefold(): author for author, email in owners.items()}
    if len(authors_by_email) != len(owners):
        raise ValueError("Each author must have a distinct GitHub email address.")
    mapping_version = hashlib.sha256(json.dumps(authors_by_email, sort_keys=True).encode()).hexdigest()

    serializer = URLSafeTimedSerializer(
        app.config["SESSION_SECRET"], salt="team-edo-blog-editor-" + mapping_version,
        signer_kwargs={"digest_method": hashlib.sha256},
    ) if writes_enabled else None
    redirect_uri = origin + "/edit/"
    write_headers = {"Authorization": "Bearer " + app.config["WRITE_TOKEN"], "X-GitHub-Api-Version": "2022-11-28"}

    @app.before_request
    def require_blog_origin():
        if request.headers.get("Origin") != origin:
            abort(403, description="블로그에서 편집을 열어 주세요.")

    @app.after_request
    def response_headers(response):
        response.vary.add("Origin")
        if request.headers.get("Origin") == origin:
            response.headers["Access-Control-Allow-Origin"] = origin
            response.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type"
            response.headers["Access-Control-Allow-Methods"] = (
                "GET, POST, PUT, DELETE, OPTIONS" if writes_enabled else "GET, POST, OPTIONS"
            )
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.errorhandler(HTTPException)
    def http_error(error):
        return jsonify(error=error.description), error.code

    @app.errorhandler(UpstreamError)
    def upstream_error(error):
        return jsonify(error="글 서버에 연결할 수 없습니다. 다시 시도해 주세요."), 502

    @app.errorhandler(sqlite3.OperationalError)
    def storage_error(error):
        app.logger.error("Blog SQLite operation failed: %s", error)
        return jsonify(error="저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."), 503

    def json_body():
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            abort(400, description="요청 형식을 확인해 주세요.")
        return data

    def file_endpoint(path):
        if not isinstance(path, str) or not POST_PATH.fullmatch(path):
            abort(400, description="글 주소를 확인해 주세요.")
        return REPOSITORY_API + "/contents/" + quote("content/" + path)

    def read_owned_post(path, author, ref=BRANCH):
        endpoint = file_endpoint(path)
        try:
            file = upstream_json(endpoint + "?" + urlencode({"ref": ref}), headers=write_headers)
        except UpstreamError as error:
            if error.status == 404:
                abort(404, description="글을 찾을 수 없습니다.")
            raise
        post = parse_post(file)
        if post.author != author:
            abort(403, description="이 글은 작성자만 수정하거나 삭제할 수 있습니다.")
        return post

    def editor_session():
        auth = request.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or len(auth) > 2048:
            abort(401, description="GitHub로 다시 로그인해 주세요.")
        try:
            return serializer.loads(auth.removeprefix("Bearer "), max_age=SESSION_SECONDS)
        except BadData:
            abort(401, description="GitHub로 다시 로그인해 주세요.")

    def batch_identity():
        if not queue_db:
            abort(503, description="일괄 반영을 준비하고 있습니다. 잠시 후 다시 시도해 주세요.")
        session = editor_session()
        if session.get("scope") != "batch" or session.get("author") not in owners:
            abort(401, description="GitHub로 다시 로그인해 주세요.")
        author = session["author"]
        owner = hashlib.sha256(owners[author].casefold().encode()).hexdigest()
        return author, owner

    def legacy_session():
        if not writes_enabled:
            abort(403, description="현재는 작성자 확인만 할 수 있습니다.")
        if queue_db:
            abort(409, description="편집 방식이 바뀌었습니다. 화면을 새로고침해 주세요.")
        session = editor_session()
        if "post" not in session:
            abort(401, description="GitHub로 다시 로그인해 주세요.")
        return session

    def verified_author(headers):
        matches = set()
        for page in range(1, 6):
            url = GITHUB_API + "/user/emails?" + urlencode({"per_page": 100, "page": page})
            emails = upstream_json(url, headers=headers, expected_type=list)
            for entry in emails:
                if not isinstance(entry, dict) or entry.get("verified") is not True:
                    continue
                email = entry.get("email")
                if isinstance(email, str) and email.casefold() in authors_by_email:
                    matches.add(authors_by_email[email.casefold()])
            if len(emails) < 100:
                if len(matches) != 1:
                    abort(403, description="등록된 블로그 작성자 계정을 확인할 수 없습니다.")
                return matches.pop()
        raise UpstreamError(502)

    views_db = app.config["VIEWS_DB"]
    if views_db:
        with closing(sqlite3.connect(views_db)) as db, db:
            db.execute("""
                CREATE TABLE IF NOT EXISTS post_views (
                    day TEXT NOT NULL,
                    path TEXT NOT NULL,
                    views INTEGER NOT NULL,
                    PRIMARY KEY (day, path)
                )
            """)

        @app.route("/views", methods=["GET", "POST"])
        def post_views():
            today = datetime.now(KOREA_TIME).date()
            first_day = today - timedelta(days=6)
            if request.method == "POST":
                request.max_content_length = 256
                path = request.get_data(as_text=True)
                if request.mimetype != "text/plain" or not PUBLIC_POST_PATH.fullmatch(path):
                    abort(400, description="글 주소를 확인해 주세요.")
                with closing(sqlite3.connect(views_db)) as db, db:
                    db.execute("DELETE FROM post_views WHERE day < ?", (first_day.isoformat(),))
                    db.execute("""
                        INSERT INTO post_views (day, path, views) VALUES (?, ?, 1)
                        ON CONFLICT (day, path) DO UPDATE SET views = views + 1
                    """, (today.isoformat(), path))
                return "", 204

            with closing(sqlite3.connect(views_db)) as db:
                rows = db.execute("""
                    SELECT path, SUM(views) FROM post_views
                    WHERE day BETWEEN ? AND ?
                    GROUP BY path
                """, (first_day.isoformat(), today.isoformat()))
                return jsonify(dict(rows))

    @app.get("/config")
    def login_config():
        return jsonify(authorize=GITHUB + "/login/oauth/authorize", clientId=app.config["CLIENT_ID"],
                       redirectUri=redirect_uri, canBatch=bool(queue_db))

    @app.post("/session")
    def start_session():
        data = json_body()
        path = data.get("post")
        batch = data.get("mode") == "batch"
        if batch and not queue_db:
            abort(503, description="일괄 반영을 준비하고 있습니다. 잠시 후 다시 시도해 주세요.")
        edit_post = writes_enabled and not batch and path is not None
        if edit_post:
            file_endpoint(path)
        code = data.get("code")
        verifier = data.get("verifier")
        if not isinstance(code, str) or not 1 <= len(code) <= 512 or not isinstance(verifier, str) or not VERIFIER.fullmatch(verifier):
            abort(400, description="GitHub 로그인 응답을 확인할 수 없습니다.")
        try:
            token_response = upstream_json(GITHUB + "/login/oauth/access_token", form={
                "client_id": app.config["CLIENT_ID"],
                "client_secret": app.config["CLIENT_SECRET"],
                "redirect_uri": redirect_uri,
                "code": code,
                "code_verifier": verifier,
            })
            if token_response.get("error") == "bad_verification_code":
                abort(401, description="GitHub로 다시 로그인해 주세요.")
            token = token_response.get("access_token")
            if token_response.get("scope") != "user:email":
                abort(403, description="편집 로그인 권한 설정을 확인해 주세요.")
            if not isinstance(token, str) or not re.fullmatch(r"[\x21-\x7e]{1,1024}", token):
                raise UpstreamError(502)
            author = verified_author({"Authorization": "Bearer " + token})
        except UpstreamError as error:
            if error.status in (400, 401, 403):
                abort(401, description="GitHub로 다시 로그인해 주세요.")
            raise
        if batch:
            return jsonify(session=serializer.dumps({"author": author, "scope": "batch"}),
                           expiresIn=SESSION_SECONDS)
        if not edit_post:
            return jsonify(status="verified")
        post = read_owned_post(path, author)
        return jsonify(session=serializer.dumps({"author": author, "post": path}),
                       title=post.title.value, markdown=post.markdown, version=post.version, canDelete=True)

    @app.put("/post")
    def save_post():
        if request.args.get("mode") == "batch":
            return stage_post(delete=False)
        session = legacy_session()
        data = json_body()
        version = data.get("version")
        if not isinstance(version, str) or not COMMIT.fullmatch(version):
            abort(422, description="제목과 본문을 확인해 주세요.")
        path = session["post"]
        post = read_owned_post(path, session["author"])
        _, _, content = validate_edit(data, post)
        if content == post.source:
            return jsonify(status="unchanged")
        if version != post.version:
            abort(409, description="다른 수정이 먼저 저장됐습니다. 내용을 복사한 뒤 글을 다시 열어 주세요.")
        try:
            upstream_json(file_endpoint(path), headers=write_headers, update={
                "branch": BRANCH,
                "content": base64.b64encode(content.encode()).decode(),
                "sha": version,
                "message": f"docs(blog): {post.author} 글 수정",
            })
        except UpstreamError as error:
            if error.status in (409, 422) and read_owned_post(path, session["author"]).version != version:
                abort(409, description="다른 수정이 먼저 저장됐습니다. 내용을 복사한 뒤 글을 다시 열어 주세요.")
            raise
        return jsonify(status="saved")

    @app.delete("/post")
    def delete_post():
        if request.args.get("mode") == "batch":
            return stage_post(delete=True)
        session = legacy_session()
        data = json_body()
        version = data.get("version")
        if set(data) != {"version"} or not isinstance(version, str) or not COMMIT.fullmatch(version):
            abort(422, description="삭제할 글의 버전을 확인해 주세요.")
        path = session["post"]
        author = session["author"]
        post = read_owned_post(path, author)
        conflict = "글이 수정됐습니다. 다시 열어 최신 내용을 확인한 뒤 삭제해 주세요."
        if post.version != version:
            abort(409, description=conflict)
        try:
            upstream_json(file_endpoint(path), headers=write_headers, delete={
                "branch": BRANCH,
                "sha": version,
                "message": f"docs(blog): {post.author} 글 삭제",
            })
        except UpstreamError as error:
            if error.status in (409, 422) and read_owned_post(path, author).version != version:
                abort(409, description=conflict)
            raise
        return jsonify(status="deleted")

    if queue_db:
        with closing(queue_connection(queue_db)) as db, db:
            db.executescript("""
                CREATE TABLE IF NOT EXISTS editor_queue (
                    owner TEXT NOT NULL, path TEXT NOT NULL, item TEXT NOT NULL, updated_at TEXT NOT NULL,
                    draft_title TEXT, draft_markdown TEXT, draft_version TEXT,
                    delete_title TEXT, delete_version TEXT,
                    PRIMARY KEY (owner, path)
                );
                CREATE TABLE IF NOT EXISTS editor_applied (
                    owner TEXT PRIMARY KEY, revision TEXT NOT NULL, commit_sha TEXT NOT NULL
                );
            """)

    @app.get("/post")
    def read_batch():
        author, owner = batch_identity()
        path = request.args.get("post")
        if path is None:
            with closing(queue_connection(queue_db)) as db:
                return jsonify(queue_state(db, owner))
        post = read_owned_post(path, author)
        with closing(queue_connection(queue_db)) as db, db:
            db.execute("BEGIN")
            entry = queue_entry(db, owner, path)
            state = queue_state(db, owner)
        draft = None
        if entry and entry["draft_title"] is not None:
            draft = {"title": entry["draft_title"], "markdown": entry["draft_markdown"],
                     "version": entry["draft_version"]}
        return jsonify(title=post.title.value, markdown=post.markdown, version=post.version, draft=draft, queue=state,
                       item=entry["item"] if entry else None,
                       deletePending=bool(entry and entry["delete_version"]))

    def require_item(entry, data):
        if "item" not in data or data["item"] != (entry["item"] if entry else None):
            abort(409, description=BATCH_CHANGED)

    def stage_post(*, delete):
        author, owner = batch_identity()
        data = json_body()
        path, version = data.get("post"), data.get("version")
        if not isinstance(version, str) or not COMMIT.fullmatch(version):
            abort(422, description="편집할 글의 버전을 확인해 주세요.")
        post = read_owned_post(path, author)
        if post.version != version:
            abort(409, description="공개 글이 바뀌었습니다. 최신 글을 다시 확인해 주세요.")
        if not delete:
            title, markdown, content = validate_edit(data, post)
        with closing(queue_connection(queue_db)) as db, db:
            db.execute("BEGIN IMMEDIATE")
            entry = queue_entry(db, owner, path)
            require_item(entry, data)
            if entry and entry["delete_version"]:
                abort(409, description="삭제 대기를 먼저 취소해 주세요.")
            if not delete and content == post.source:
                db.execute("DELETE FROM editor_queue WHERE owner = ? AND path = ?", (owner, path))
                return jsonify(queue_state(db, owner))
            if not entry and db.execute("SELECT COUNT(*) FROM editor_queue WHERE owner = ?", (owner,)).fetchone()[0] >= MAX_QUEUE:
                abort(422, description=f"대기 목록에는 최대 {MAX_QUEUE}개 글을 담을 수 있습니다.")
            item, updated_at = uuid4().hex, datetime.now(timezone.utc).isoformat()
            if delete:
                db.execute("""
                    INSERT INTO editor_queue (owner, path, item, updated_at, delete_title, delete_version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (owner, path) DO UPDATE SET item=excluded.item, updated_at=excluded.updated_at,
                        delete_title=excluded.delete_title, delete_version=excluded.delete_version
                """, (owner, path, item, updated_at, post.title.value, version))
            else:
                db.execute("""
                    INSERT INTO editor_queue (owner, path, item, updated_at, draft_title, draft_markdown, draft_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (owner, path) DO UPDATE SET item=excluded.item, updated_at=excluded.updated_at,
                        draft_title=excluded.draft_title, draft_markdown=excluded.draft_markdown, draft_version=excluded.draft_version
                """, (owner, path, item, updated_at, title, markdown, version))
            return jsonify(queue_state(db, owner))

    def undo_item(owner, data):
        path = data.get("post")
        file_endpoint(path)
        with closing(queue_connection(queue_db)) as db, db:
            db.execute("BEGIN IMMEDIATE")
            entry = queue_entry(db, owner, path)
            if entry is None:
                abort(409, description=BATCH_CHANGED)
            require_item(entry, data)
            if entry["delete_version"] and entry["draft_version"]:
                db.execute("""
                    UPDATE editor_queue SET item = ?, delete_title = NULL, delete_version = NULL
                    WHERE owner = ? AND path = ?
                """, (uuid4().hex, owner, path))
            else:
                db.execute("DELETE FROM editor_queue WHERE owner = ? AND path = ?", (owner, path))
            return jsonify(queue_state(db, owner))

    def branch_head():
        sha = upstream_json(REPOSITORY_API + "/git/ref/heads/" + BRANCH, headers=write_headers).get("object") or {}
        sha = sha.get("sha") if isinstance(sha, dict) else None
        if not isinstance(sha, str) or not COMMIT.fullmatch(sha):
            raise UpstreamError(502)
        return sha

    def build_action(author, entry, head):
        path = entry["path"]
        version = entry["delete_version"] or entry["draft_version"]
        try:
            post = read_owned_post(path, author, head)
        except HTTPException as error:
            if error.code in (403, 404):
                return None
            raise
        if post.version != version:
            return None
        action = {"path": "content/" + path, "mode": "100644", "type": "blob"}
        if entry["delete_version"]:
            return dict(action, sha=None)
        return dict(action, content=post.with_edit(entry["draft_title"], entry["draft_markdown"]))

    def commit_actions(head, actions, message):
        """Create one commit on top of head and move the branch without force (fails if it moved)."""
        tree = upstream_json(REPOSITORY_API + "/git/commits/" + head, headers=write_headers).get("tree") or {}
        tree = tree.get("sha") if isinstance(tree, dict) else None
        if not isinstance(tree, str) or not COMMIT.fullmatch(tree):
            raise UpstreamError(502)
        new_tree = upstream_json(REPOSITORY_API + "/git/trees", headers=write_headers,
                                 submit={"base_tree": tree, "tree": actions}).get("sha")
        if not isinstance(new_tree, str) or not COMMIT.fullmatch(new_tree):
            raise UpstreamError(502)
        sha = upstream_json(REPOSITORY_API + "/git/commits", headers=write_headers,
                            submit={"message": message, "tree": new_tree, "parents": [head]}).get("sha")
        if not isinstance(sha, str) or not COMMIT.fullmatch(sha):
            raise UpstreamError(502)
        upstream_json(REPOSITORY_API + "/git/refs/heads/" + BRANCH, headers=write_headers,
                      patch={"sha": sha, "force": False})
        return sha

    def confirmed_commit(trailer, entries):
        # A timed-out POST may already have landed. Identify our exact batch before clearing drafts.
        since = datetime.fromisoformat(max(entry["updated_at"] for entry in entries)) - timedelta(minutes=1)
        for page in range(1, 5):
            params = urlencode({"sha": BRANCH, "since": since.isoformat(), "per_page": 100, "page": page})
            commits = upstream_json(REPOSITORY_API + "/commits?" + params, headers=write_headers, expected_type=list)
            for commit in commits:
                detail = commit.get("commit") if isinstance(commit, dict) else None
                message = str(detail.get("message", "")) if isinstance(detail, dict) else ""
                if message.rstrip().endswith("\n" + trailer):
                    sha = commit.get("sha")
                    if isinstance(sha, str) and COMMIT.fullmatch(sha):
                        return sha
            if len(commits) < 100:
                return None
        raise UpstreamError(502)

    def previous_result(db, owner, revision):
        return db.execute("""
            SELECT commit_sha FROM editor_applied WHERE owner = ? AND revision = ?
        """, (owner, revision)).fetchone()

    def apply_batch(author, owner, revision):
        if not isinstance(revision, str) or not QUEUE_REVISION.fullmatch(revision):
            abort(422, description="반영할 대기 목록을 확인해 주세요.")
        with closing(queue_connection(queue_db)) as db, db:
            db.execute("BEGIN")
            previous = previous_result(db, owner, revision)
            state = queue_state(db, owner)
            if previous:
                return jsonify(**state, commit=previous["commit_sha"])
            if state["revision"] != revision or not state["items"]:
                abort(409, description=BATCH_CHANGED)
            entries = db.execute("SELECT * FROM editor_queue WHERE owner = ? ORDER BY path", (owner,)).fetchall()

        head = branch_head()
        with ThreadPoolExecutor(max_workers=8) as executor:
            actions = list(executor.map(lambda entry: build_action(author, entry, head), entries))
        sha = None
        trailer = "Blog-Batch: " + revision
        if None in actions:
            sha = confirmed_commit(trailer, entries)
            if not sha:
                return jsonify(error="공개 글이 변경됐습니다. 해당 글을 다시 확인해 주세요.",
                               post=entries[actions.index(None)]["path"]), 409

        with closing(queue_connection(queue_db)) as db, db:
            db.execute("BEGIN IMMEDIATE")
            previous = previous_result(db, owner, revision)
            state = queue_state(db, owner)
            if previous:
                return jsonify(**state, commit=previous["commit_sha"])
            if state["revision"] != revision:
                abort(409, description=BATCH_CHANGED)
            if not sha:
                try:
                    sha = commit_actions(head, actions, f"docs(blog): {author} 글 변경 일괄 반영\n\n{trailer}")
                except UpstreamError as error:
                    if error.status in (409, 422):
                        abort(409, description="일괄 반영을 완료하지 못했습니다. 글과 대기 목록을 다시 확인해 주세요.")
                    raise
            db.execute("DELETE FROM editor_queue WHERE owner = ?", (owner,))
            db.execute("""
                INSERT INTO editor_applied (owner, revision, commit_sha) VALUES (?, ?, ?)
                ON CONFLICT (owner) DO UPDATE SET revision=excluded.revision, commit_sha=excluded.commit_sha
            """, (owner, revision, sha))
            return jsonify(**queue_state(db, owner), commit=sha)

    @app.post("/post")
    def handle_batch():
        author, owner = batch_identity()
        data = json_body()
        if data.get("action") == "undo":
            return undo_item(owner, data)
        if data.get("action") == "apply":
            return apply_batch(author, owner, data.get("revision"))
        abort(400, description="요청 형식을 확인해 주세요.")

    return app
