"""Author-only write API for the static Team Edo blog."""

import base64
import binascii
import hashlib
import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

import yaml
from flask import Flask, abort, jsonify, request
from itsdangerous import BadData, URLSafeTimedSerializer
from werkzeug.exceptions import HTTPException
from yaml.nodes import MappingNode, ScalarNode
from yaml.tokens import AliasToken, AnchorToken

GITLAB = "https://lab.ssafy.com"
GITHUB = "https://github.com"
GITHUB_API = "https://api.github.com"
PROJECT = "s15-final/S15P31E102"
BRANCH = "blog"
POST_PATH = re.compile(r"posts/\d{4}-\d{2}-\d{2}-[a-z0-9][a-z0-9-]{0,175}\.md")
COMMIT = re.compile(r"[a-f0-9]{40}")
VERIFIER = re.compile(r"[A-Za-z0-9._~-]{43,128}")
EMAIL = re.compile(r"[^\s@]+@[^\s@]+\.[^\s@]+")
FRONT_MATTER = re.compile(r"\A---[ \t]*\r?\n(.*?)\r?\n---[ \t]*\r?\n", re.DOTALL)
YAML_STRING = "tag:yaml.org,2002:str"
MAX_SOURCE = 250_000
SESSION_SECONDS = 15 * 60


class UpstreamError(Exception):
    def __init__(self, status):
        super().__init__(status)
        self.status = status


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


upstream_http = build_opener(NoRedirect)


def upstream_json(url, *, headers=None, form=None, update=None, expected_type=dict):
    headers = dict(headers or {}, Accept="application/json", **{"User-Agent": "Team-Edo-Blog-Editor"})
    data = None
    method = "GET"
    if form is not None:
        method = "POST"
        data = urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    if update is not None:
        method = "PUT"
        data = json.dumps(update, ensure_ascii=False).encode()
        headers["Content-Type"] = "application/json"
    upstream = Request(url, data, headers, method=method)
    try:
        with upstream_http.open(upstream, timeout=5) as response:
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
        source = base64.b64decode(file["content"], validate=True).decode()
        version = file["last_commit_id"]
    except (KeyError, TypeError, UnicodeError, binascii.Error):
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


def create_app(config=None):
    app = Flask(__name__)
    app.config.from_mapping(
        BLOG_ORIGIN=os.environ.get("BLOG_ORIGIN", ""),
        CLIENT_ID=os.environ.get("EDITOR_GITHUB_CLIENT_ID", ""),
        CLIENT_SECRET=read_secret("EDITOR_GITHUB_CLIENT_SECRET"),
        WRITE_TOKEN=read_secret("EDITOR_GITLAB_WRITE_TOKEN"),
        SESSION_SECRET=read_secret("EDITOR_SESSION_SECRET"),
        AUTHOR_EMAILS_FILE=os.environ.get("EDITOR_GITHUB_AUTHOR_EMAILS_FILE", ""),
        MAX_CONTENT_LENGTH=MAX_SOURCE + 20_000,
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
    project_files = GITLAB + "/api/v4/projects/" + quote(PROJECT, safe="") + "/repository/files/"
    write_headers = {"PRIVATE-TOKEN": app.config["WRITE_TOKEN"]}

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
            response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, OPTIONS" if writes_enabled else "GET, POST, OPTIONS"
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.errorhandler(HTTPException)
    def http_error(error):
        return jsonify(error=error.description), error.code

    @app.errorhandler(UpstreamError)
    def upstream_error(error):
        return jsonify(error="글 서버에 연결할 수 없습니다. 다시 시도해 주세요."), 502

    def json_body():
        data = request.get_json(silent=True)
        if not isinstance(data, dict):
            abort(400, description="요청 형식을 확인해 주세요.")
        return data

    def file_endpoint(path):
        if not isinstance(path, str) or not POST_PATH.fullmatch(path):
            abort(400, description="글 주소를 확인해 주세요.")
        return project_files + quote("content/" + path, safe="")

    def read_owned_post(path, author):
        endpoint = file_endpoint(path)
        try:
            file = upstream_json(endpoint + "?" + urlencode({"ref": BRANCH}), headers=write_headers)
        except UpstreamError as error:
            if error.status == 404:
                abort(404, description="글을 찾을 수 없습니다.")
            raise
        post = parse_post(file)
        if post.author != author:
            abort(403, description="이 글은 작성자만 수정할 수 있습니다.")
        return post

    def editor_session():
        auth = request.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or len(auth) > 2048:
            abort(401, description="GitHub로 다시 로그인해 주세요.")
        try:
            return serializer.loads(auth.removeprefix("Bearer "), max_age=SESSION_SECONDS)
        except BadData:
            abort(401, description="GitHub로 다시 로그인해 주세요.")

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

    @app.get("/config")
    def login_config():
        return jsonify(authorize=GITHUB + "/login/oauth/authorize", clientId=app.config["CLIENT_ID"],
                       redirectUri=redirect_uri)

    @app.post("/session")
    def start_session():
        data = json_body()
        path = data.get("post")
        edit_post = writes_enabled and path is not None
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
        if not edit_post:
            return jsonify(status="verified")
        post = read_owned_post(path, author)
        return jsonify(session=serializer.dumps({"author": author, "post": path}),
                       title=post.title.value, markdown=post.markdown, version=post.version)

    @app.put("/post")
    def save_post():
        if not writes_enabled:
            abort(403, description="현재는 작성자 확인만 할 수 있습니다.")
        session = editor_session()
        data = json_body()
        title = data.get("title")
        markdown = data.get("markdown")
        version = data.get("version")
        if (
            not isinstance(title, str) or not 1 <= len(title.strip()) <= 160
            or any(ord(char) < 32 for char in title)
            or not isinstance(markdown, str) or not markdown.strip() or "\x00" in markdown
            or not isinstance(version, str) or not COMMIT.fullmatch(version)
        ):
            abort(422, description="제목과 본문을 확인해 주세요.")
        if re.search(r"\{\{[<%]", markdown):
            abort(422, description="본문에는 일반 Markdown만 사용할 수 있습니다.")
        path = session["post"]
        post = read_owned_post(path, session["author"])
        content = post.with_edit(title.strip(), markdown)
        try:
            size = len(content.encode())
        except UnicodeEncodeError:
            abort(422, description="제목과 본문에 저장할 수 없는 문자가 있습니다.")
        if size > MAX_SOURCE:
            abort(413, description="글이 너무 깁니다.")
        if content == post.source:
            return jsonify(status="unchanged")
        if version != post.version:
            abort(409, description="다른 수정이 먼저 저장됐습니다. 내용을 복사한 뒤 글을 다시 열어 주세요.")
        try:
            upstream_json(file_endpoint(path), headers=write_headers, update={
                "branch": BRANCH,
                "content": content,
                "last_commit_id": version,
                "commit_message": f"docs(blog): {post.author} 글 수정",
            })
        except UpstreamError as error:
            if error.status in (400, 409) and read_owned_post(path, session["author"]).version != version:
                abort(409, description="다른 수정이 먼저 저장됐습니다. 내용을 복사한 뒤 글을 다시 열어 주세요.")
            raise
        return jsonify(status="saved")

    return app
