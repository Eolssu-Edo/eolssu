#!/usr/bin/env python3
"""Read selected TIL blobs from Git and prepare reviewed Hugo drafts. No network or Git writes."""
from __future__ import annotations

import argparse
import difflib
from datetime import date, datetime, time, timedelta, timezone
import hashlib
import ipaddress
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tempfile

try:
    import yaml
except ImportError:
    sys.exit("PyYAML이 필요합니다: python -m pip install -r requirements-blog.txt")

KST = timezone(timedelta(hours=9))
STATE = ".til-imports.json"
CATEGORIES = {"backend", "frontend", "ai", "data", "infra", "회고"}
SLUG = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\Z")


class ImportError_(ValueError):
    pass


class UniqueLoader(yaml.SafeLoader):
    pass


def unique_mapping(loader, node):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node)
        if not isinstance(key, str) or key in result:
            raise ImportError_("YAML 키는 중복 없는 문자열이어야 합니다")
        result[key] = loader.construct_object(value_node)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def git(repo, *args):
    run = subprocess.run(["git", "-C", str(repo), *args], capture_output=True)
    if run.returncode:
        raise ImportError_("Git 조회 실패: ref·저장소·fetch 여부를 확인하세요")
    return run.stdout


def split_document(raw):
    text = raw.decode("utf-8-sig").replace("\r\n", "\n")
    if len(raw) > 1_000_000:
        raise ImportError_("문서는 1MB 이하여야 합니다")
    if not text.startswith("---\n"):
        return {}, text
    match = re.match(r"\A---\n(.*?)\n---(?:\n|\Z)(.*)\Z", text, re.S)
    if not match:
        raise ImportError_("YAML 머리말의 닫는 ---가 없습니다")
    try:
        meta = yaml.load(match[1], Loader=UniqueLoader)
    except yaml.YAMLError:
        raise ImportError_("YAML 머리말을 해석할 수 없습니다") from None
    if not isinstance(meta, dict):
        raise ImportError_("YAML 머리말은 객체여야 합니다")
    return meta, match[2]


def source_path(value):
    p = PurePosixPath(value)
    if ("\\" in value or not value.startswith("TIL/") or ".." in p.parts
            or p.is_absolute() or p.suffix.lower() != ".md"):
        raise ImportError_("TIL/ 아래 Markdown 경로를 지정하세요")
    return value


def local_path(repo, name, post=False):
    relative = PurePosixPath(name)
    if relative.is_absolute() or ".." in relative.parts or "\\" in name:
        raise ImportError_("저장소 밖 경로는 사용할 수 없습니다")
    if post and (not name.startswith("content/posts/") or relative.suffix != ".md"):
        raise ImportError_("출력은 content/posts/ 아래 Markdown이어야 합니다")
    path = repo.joinpath(*relative.parts)
    if not path.resolve().is_relative_to(repo.resolve()) or path.is_symlink():
        raise ImportError_("심볼릭 링크나 저장소 밖 경로에는 기록하지 않습니다")
    return path


def read_state(repo):
    path = local_path(repo, STATE)
    if not path.exists():
        return {"version": 1, "entries": {}}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if data["version"] != 1 or not isinstance(data["entries"], dict):
            raise ValueError()
        for key, entry in data["entries"].items():
            source_path(key)
            local_path(repo, entry["output"], post=True)
            if not all(isinstance(entry.get(k), str) for k in
                       ("source_commit", "source_blob", "generated_sha256", "date")):
                raise ValueError()
        return data
    except (ValueError, KeyError, TypeError):
        raise ImportError_("가져오기 기록이 손상됐습니다. 덮어쓰지 않고 중단합니다") from None


def snapshot(repo, ref):
    if ref.startswith("-") or not re.fullmatch(r"[A-Za-z0-9_./-]+", ref):
        raise ImportError_("브랜치 이름 또는 커밋 SHA를 지정하세요")
    commit = git(repo, "rev-parse", "--verify", ref + "^{commit}").decode().strip()
    files = {}
    for record in git(repo, "ls-tree", "-r", "-z", commit, "--", "TIL/").split(b"\0"):
        if not record:
            continue
        info, name = record.split(b"\t", 1)
        mode, kind, blob = info.decode().split()
        path = name.decode("utf-8")
        if path.lower().endswith(".md") and kind == "blob" and mode in {"100644", "100755"}:
            files[path] = blob
    return commit, files


def load_candidate(repo, path, blob):
    size = int(git(repo, "cat-file", "-s", blob))
    if size > 1_000_000:
        raise ImportError_("TIL은 1MB 이하여야 합니다")
    meta, body = split_document(git(repo, "cat-file", "blob", blob))
    blog = meta.get("blog", {})
    if not isinstance(blog, dict):
        raise ImportError_("blog 설정은 객체여야 합니다")
    if blog.get("candidate") is not True:
        return None
    start, end = "<!-- blog:start -->", "<!-- blog:end -->"
    if start in body or end in body:
        if body.count(start) != 1 or body.count(end) != 1 or body.index(start) >= body.index(end):
            raise ImportError_("공개 본문 구간 표시를 한 쌍으로 작성하세요")
        body = body.split(start, 1)[1].split(end, 1)[0]
    if not body.strip():
        raise ImportError_("공개 본문이 비어 있습니다")
    export = {**meta, **blog}
    export.pop("blog", None)
    if "date" not in blog:
        export.pop("date", None)  # TIL 작성일과 공개 게시일은 별개입니다.
    return {"meta": export, "body": body.strip() + "\n"}


def publication_date(value=None):
    if value is None:
        day = datetime.now(KST).date()
    elif isinstance(value, datetime):
        day = value.astimezone(KST).date() if value.tzinfo else value.date()
    elif isinstance(value, date):
        day = value
    elif isinstance(value, str):
        try:
            parsed = datetime.fromisoformat(value)
            day = parsed.astimezone(KST).date() if parsed.tzinfo else parsed.date()
        except ValueError:
            raise ImportError_("날짜는 YYYY-MM-DD 형식이어야 합니다") from None
    else:
        raise ImportError_("날짜 형식이 올바르지 않습니다")
    if day > datetime.now(KST).date():
        raise ImportError_("미래 날짜는 게시일로 사용할 수 없습니다")
    return datetime.combine(day, time(), KST).isoformat()


def public_issues(text):
    """Heuristics only. Human review remains necessary before setting draft: false."""
    findings = []
    patterns = [
        ("내부 링크", r"(?i)(?:lab\.ssafy\.com|project\.ssafy\.com|[\w.-]+\.atlassian\.net|(?:www\.)?notion\.(?:so|site)|localhost|127\.0\.0\.1)"),
        ("자격증명 형태", r"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|glpat-[A-Za-z0-9_-]{15,}|AKIA[A-Z0-9]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)"),
        ("비밀값 대입", r"(?im)\b(?:password|passwd|api[_-]?key|secret|access[_-]?token)\s*[:=]\s*[\"']?[^\s\"'<>${}]{8,}"),
        ("인증 포함 URL", r"https?://[^\s/<>]+:[^\s/<>]+@"),
        ("로컬·내부 링크", r"(?:!?\[[^\]\n]*\]\((?!https://|#)[^\s)][^)]*\)|^\s*\[[^\]]+\]:\s*(?!https://|#)\S+)"),
        ("HTML 이미지", r"(?i)<img\b"),
    ]
    for line_number, line in enumerate(text.splitlines(), 1):
        for label, pattern in patterns:
            if re.search(pattern, line):
                findings.append(f"{line_number}행: {label}")
        for match in re.finditer(r"(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])", line):
            try:
                ipaddress.ip_address(match[0])
            except ValueError:
                continue
            findings.append(f"{line_number}행: IP 주소")
        for match in re.finditer(r'!\[[^\]]*\]\(\s*<?([^\s)>]+)', line):
            if not re.fullmatch(r"https://team-edo\.github\.io/blog-images/[a-z0-9][a-z0-9.-]*\.(?:webp|png|jpe?g)", match[1]):
                findings.append(f"{line_number}행: 이미지 저장소·파일명 확인 필요")
        if re.search(r"!\[[^\]]*\](?!\()", line):
            findings.append(f"{line_number}행: 이미지는 직접 URL 문법을 사용하세요")
    return sorted(set(findings))


def validate_metadata(meta):
    for key in ("title", "slug", "author", "description"):
        if not isinstance(meta.get(key), str) or not meta[key].strip():
            raise ImportError_(f"공개 머리말의 {key}가 필요합니다")
    if not SLUG.fullmatch(meta["slug"]):
        raise ImportError_("slug는 영어 소문자·숫자·하이픈만 사용할 수 있습니다")
    for key in ("categories", "tags"):
        values = meta.get(key, [])
        if not isinstance(values, list) or not all(isinstance(x, str) and x.strip() for x in values):
            raise ImportError_(f"{key}는 문자열 목록이어야 합니다")
    if not meta.get("categories") or not set(meta["categories"]) <= CATEGORIES:
        raise ImportError_("categories는 backend, frontend, ai, data, infra, 회고 중에서 선택하세요")


def render(candidate, day):
    meta = candidate["meta"]
    validate_metadata(meta)
    public = {key: meta[key] for key in ("title", "slug", "author", "description", "categories")}
    public.update(date=day, tags=meta.get("tags", []), draft=True)
    if meta.get("thumbnail"):
        public["thumbnail"] = meta["thumbnail"]
    result = "---\n" + yaml.safe_dump(public, allow_unicode=True, sort_keys=False) + "---\n\n" + candidate["body"]
    issues = public_issues(result)
    if meta.get("thumbnail") and not re.fullmatch(r"https://team-edo\.github\.io/blog-images/[a-z0-9][a-z0-9.-]*\.(?:webp|png|jpe?g)", str(meta["thumbnail"])):
        issues.append("thumbnail: 이미지 저장소·파일명 확인 필요")
    if issues:
        raise ImportError_("공개 전 수정 필요: " + "; ".join(issues))
    return result.encode("utf-8")


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def atomic_write(path, raw):
    descriptor, temporary = tempfile.mkstemp(prefix=".til-import-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(raw)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def status(repo, entry, blob):
    if not entry:
        return "new"
    output = local_path(repo, entry["output"], post=True)
    if not output.exists() or digest(output.read_bytes()) != entry["generated_sha256"]:
        return "local-edits"
    return "unchanged" if entry["source_blob"] == blob else "updated"


def import_one(repo, commit, files, path, args):
    source_path(path)
    if path not in files:
        raise ImportError_("지정한 ref에 TIL 일반 파일이 없습니다")
    candidate = load_candidate(repo, path, files[path])
    if candidate is None:
        raise ImportError_("blog.candidate: true인 TIL만 가져올 수 있습니다")
    branch = git(repo, "branch", "--show-current").decode().strip()
    if not branch or branch in {"blog", "develop", "main", "master"}:
        raise ImportError_("blog에서 분기한 작업 브랜치에서 실행하세요")
    try:
        git(repo, "merge-base", "--is-ancestor", "refs/remotes/origin/blog", "HEAD")
    except ImportError_:
        raise ImportError_("최신 origin/blog를 포함한 작업 브랜치에서 실행하세요") from None
    state = read_state(repo)
    entry = state["entries"].get(path)
    current = status(repo, entry, files[path])
    if current == "local-edits":
        raise ImportError_("가져온 글이 편집·게시되었거나 없어졌습니다. 자동으로 덮어쓰지 않습니다")
    if current == "unchanged":
        return {"status": "unchanged", "output": entry["output"]}
    if entry and not args.update:
        raise ImportError_("원본이 바뀌었습니다. 비교한 뒤 --update로 명시적으로 갱신하세요")
    day = publication_date(args.date or candidate["meta"].get("date") or (entry and entry["date"]))
    raw = render(candidate, day)
    filename = f"content/posts/{day[:10]}-{candidate['meta']['slug']}.md"
    if entry and filename != entry["output"]:
        raise ImportError_("갱신으로 게시글 URL을 변경할 수 없습니다. 기존 날짜·slug를 유지하세요")
    output = local_path(repo, filename, post=True)
    if not entry:
        for existing in (repo / "content/posts").rglob("*.md"):
            local_path(repo, existing.relative_to(repo).as_posix(), post=True)
            meta, _ = split_document(existing.read_bytes())
            if meta.get("slug") == candidate["meta"]["slug"]:
                raise ImportError_("같은 slug의 게시글이 이미 있습니다")
        if output.exists():
            raise ImportError_("출력 파일이 이미 있습니다")
    if not args.dry_run:
        state["entries"][path] = {"output": filename, "source_commit": commit,
            "source_blob": files[path], "generated_sha256": digest(raw), "date": day}
        state_path = local_path(repo, STATE)
        temporary = local_path(repo, STATE + ".tmp")
        # Prepare the record before touching a post, and roll back our post
        # if committing the record fails. Existing temp files are never erased.
        with temporary.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(state, stream, ensure_ascii=False, indent=2, sort_keys=True)
            stream.write("\n")
        previous = None
        wrote = False
        try:
            output.parent.mkdir(parents=True, exist_ok=True)
            if entry:
                previous = output.read_bytes()
                if digest(previous) != entry["generated_sha256"]:
                    raise ImportError_("실행 중 글이 편집됐습니다")
                atomic_write(output, raw)
            else:
                with output.open("xb") as stream:
                    stream.write(raw)
            wrote = True
            os.replace(temporary, state_path)
        except (OSError, ImportError_):
            if wrote and output.exists() and output.read_bytes() == raw:
                if previous is None:
                    output.unlink()
                else:
                    atomic_write(output, previous)
            raise
        finally:
            temporary.unlink(missing_ok=True)
    return {"status": "dry-run" if args.dry_run else ("updated" if entry else "imported"),
        "output": filename, "draft": True}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--ref", default="origin/develop", help="사전에 fetch한 브랜치 또는 SHA")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("list", help="공개 후보와 원본 변경·로컬 편집 상태 조회")
    command = commands.add_parser("import", help="선택한 TIL 한 편을 draft로 가져오기")
    command.add_argument("path")
    command.add_argument("--date", help="게시일 YYYY-MM-DD (기본: 한국 날짜)")
    command.add_argument("--dry-run", action="store_true")
    command.add_argument("--update", action="store_true")
    command = commands.add_parser("diff", help="현재 글과 원본으로 만든 초안의 차이만 보기")
    command.add_argument("path")
    command.add_argument("--date")
    command = commands.add_parser("check", help="가져온 뒤 편집한 글의 공개 전 검사")
    command.add_argument("path")
    args = parser.parse_args(argv)
    repo = args.repo.resolve()
    lock = None
    try:
        top = Path(git(repo, "rev-parse", "--show-toplevel").decode("utf-8").strip())
        if top.resolve() != repo:
            raise ImportError_("--repo에는 Git 저장소 루트를 지정하세요")
        if args.command == "check":
            path = local_path(repo, args.path, post=True)
            original = path.read_bytes()
            meta, body = split_document(original)
            validate_metadata(meta)
            allowed = {"title", "slug", "author", "description", "categories", "tags", "date", "draft", "thumbnail", "featured"}
            if set(meta) - allowed:
                raise ImportError_("공개 글에 허용되지 않은 머리말 필드가 있습니다")
            if "date" not in meta:
                raise ImportError_("공개 머리말의 date가 필요합니다")
            issues = public_issues(original.decode("utf-8"))
            if issues:
                raise ImportError_("공개 전 수정 필요: " + "; ".join(issues))
            publication_date(meta["date"])
            if not isinstance(meta.get("draft"), bool):
                raise ImportError_("draft는 true 또는 false여야 합니다")
            # Reuse the export checks without changing the user's file.
            render({"meta": meta, "body": body}, publication_date(meta["date"]))
            result = {"status": "checked", "draft": meta["draft"], "human_review_required": True}
        else:
            commit, files = snapshot(repo, args.ref)
            if args.command == "list":
                state = read_state(repo)
                result = []
                for path, blob in sorted(files.items()):
                    try:
                        candidate = load_candidate(repo, path, blob)
                        if candidate:
                            entry = state["entries"].get(path)
                            result.append({"path": path, "title": candidate["meta"].get("title", ""),
                                "status": status(repo, entry, blob)})
                    except ImportError_ as error:
                        result.append({"path": path, "status": "invalid", "error": str(error)})
            elif args.command == "diff":
                source_path(args.path)
                if args.path not in files:
                    raise ImportError_("지정한 ref에 TIL 일반 파일이 없습니다")
                candidate = load_candidate(repo, args.path, files[args.path])
                if candidate is None:
                    raise ImportError_("blog.candidate: true인 TIL만 비교할 수 있습니다")
                entry = read_state(repo)["entries"].get(args.path)
                day = publication_date(args.date or candidate["meta"].get("date") or (entry and entry["date"]))
                after = render(candidate, day).decode("utf-8")
                output = local_path(repo, entry["output"], post=True) if entry else None
                before = output.read_text(encoding="utf-8") if output and output.exists() else ""
                issues = public_issues(before)
                if issues:
                    raise ImportError_("현재 글의 공개 전 수정 필요: " + "; ".join(issues))
                result = "".join(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                    fromfile="현재 블로그 글", tofile="원본 기준 draft 초안")) or "변경 사항이 없습니다.\n"
            else:
                lock_path = local_path(repo, ".til-import.lock")
                lock = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
                result = import_one(repo, commit, files, args.path, args)
        print(result if isinstance(result, str) else json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (ImportError_, OSError, UnicodeError) as error:
        if isinstance(error, FileExistsError):
            message = "동시 실행 잠금 또는 임시 파일이 있습니다. 다른 실행 여부를 확인하세요"
        elif isinstance(error, (OSError, UnicodeError)):
            message = "파일을 읽거나 기록할 수 없습니다. UTF-8·경로·권한을 확인하세요"
        else:
            message = str(error)
        print("오류: " + message, file=sys.stderr)
        return 1
    finally:
        if lock is not None:
            os.close(lock)
            lock_path.unlink(missing_ok=True)


if __name__ == "__main__":
    sys.exit(main())
