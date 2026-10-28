"""Offline end-to-end tests with unrelated develop/blog histories."""
from contextlib import redirect_stdout, redirect_stderr
from datetime import datetime, timedelta, timezone
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

import yaml

MODULE = Path(__file__).resolve().parents[1] / "scripts/til_to_blog.py"
spec = importlib.util.spec_from_file_location("til_to_blog", MODULE)
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)


class ImportTests(unittest.TestCase):
    def git(self, *args, env=None):
        return subprocess.run(["git", "-C", str(self.repo), *args], check=True,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout.decode("utf-8").strip()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="til-blog-test-")
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git("init", "-b", "develop")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.source = "TIL/2026-10-28-audio.md"
        self.metadata = {"title": "내부 TIL 제목", "date": "2020-01-01", "blog": {
            "candidate": True, "title": "녹음 형식을 통일하며 배운 점", "slug": "audio-format",
            "author": "Team Edo", "description": "녹음 형식과 분석 과정을 정리했습니다.",
            "categories": ["ai"], "tags": ["녹음"]}}
        self.body = "## 배운 점\n\n녹음 형식을 먼저 확인합니다.\n"
        self.write_til()
        (self.repo / "app.txt").write_text("internal application", encoding="utf-8")
        (self.repo / "TIL/unselected.md").write_text("# 개인 메모\n", encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-m", "source")
        self.source_commit = self.git("rev-parse", "HEAD")
        self.git("update-ref", "refs/remotes/origin/develop", self.source_commit)
        self.git("switch", "--orphan", "blog")
        (self.repo / "hugo.toml").write_text('baseURL = "https://example.invalid/"\n', encoding="utf-8")
        self.git("add", ".")
        self.git("commit", "-m", "blog")
        self.git("update-ref", "refs/remotes/origin/blog", self.git("rev-parse", "HEAD"))
        self.git("switch", "-c", "test/import")

    def write_til(self):
        p = self.repo / self.source
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("---\n" + yaml.safe_dump(self.metadata, allow_unicode=True) + "---\n\n" + self.body, encoding="utf-8")

    def change_source(self):
        # Update only the object database via an isolated source checkout.
        checkout = self.repo / "source-worktree"
        self.git("worktree", "add", "--detach", str(checkout), "origin/develop")
        p = checkout / self.source
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("---\n" + yaml.safe_dump(self.metadata, allow_unicode=True) + "---\n\n" + self.body, encoding="utf-8")
        subprocess.run(["git", "-C", str(checkout), "add", "."], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(checkout), "commit", "-m", "updated source"], check=True, capture_output=True)
        sha = subprocess.check_output(["git", "-C", str(checkout), "rev-parse", "HEAD"]).decode().strip()
        self.git("update-ref", "refs/remotes/origin/develop", sha)
        self.git("worktree", "remove", str(checkout))

    def call(self, *args, code=0):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            result = tool.main(["--repo", str(self.repo), *args])
        self.assertEqual(result, code, err.getvalue())
        if result != 0:
            return err.getvalue()
        return out.getvalue() if args[0] == "diff" else json.loads(out.getvalue())

    def imported(self):
        result = self.call("import", self.source, "--date", "2026-01-01")
        return self.repo / result["output"]

    def test_only_explicit_candidates_are_listed_and_imported(self):
        head = self.git("rev-parse", "HEAD")
        listed = self.call("list")
        self.assertEqual([x["path"] for x in listed], [self.source])
        path = self.imported()
        self.assertIn("draft: true", path.read_text(encoding="utf-8"))
        self.assertNotIn("TIL/", path.read_text(encoding="utf-8"))
        self.assertFalse((self.repo / "app.txt").exists())
        self.assertEqual(self.git("rev-parse", "HEAD"), head)
        self.assertFalse((self.repo / "TIL").exists())
        self.assertIn("candidate", self.call("import", "TIL/unselected.md", code=1))

    def test_reimport_is_idempotent(self):
        path = self.imported()
        original = path.read_bytes()
        record = (self.repo / tool.STATE).read_bytes()
        self.assertEqual(self.call("import", self.source)["status"], "unchanged")
        self.assertEqual(path.read_bytes(), original)
        self.assertEqual((self.repo / tool.STATE).read_bytes(), record)

    def test_dry_run_leaves_no_posts_or_state(self):
        result = self.call("import", self.source, "--dry-run")
        self.assertEqual(result["status"], "dry-run")
        self.assertFalse((self.repo / tool.STATE).exists())
        self.assertFalse((self.repo / "content").exists())
        self.assertFalse((self.repo / ".til-import.lock").exists())

    def test_source_updates_require_explicit_update_and_keep_url(self):
        path = self.imported()
        self.body += "\n두 번째 실험 결과를 추가했습니다.\n"
        self.change_source()
        self.assertEqual(self.call("list")[0]["status"], "updated")
        self.call("import", self.source, code=1)
        result = self.call("import", self.source, "--update")
        self.assertEqual(self.repo / result["output"], path)
        self.assertIn("두 번째", path.read_text(encoding="utf-8"))

    def test_local_edits_and_published_posts_are_never_overwritten(self):
        path = self.imported()
        edited = path.read_text(encoding="utf-8").replace("draft: true", "draft: false") + "\n편집 내용\n"
        path.write_text(edited, encoding="utf-8")
        self.assertEqual(self.call("list")[0]["status"], "local-edits")
        self.call("import", self.source, "--update", code=1)
        self.assertEqual(path.read_text(encoding="utf-8"), edited)

    def test_public_excerpt_omits_private_notes(self):
        self.body = "비공개 링크 https://lab.ssafy.com/private\n<!-- blog:start -->\n## 공개 본문\n내용\n<!-- blog:end -->\n내부 메모\n"
        self.change_source()
        raw = self.imported().read_text(encoding="utf-8")
        self.assertIn("공개 본문", raw)
        self.assertNotIn("lab.ssafy", raw)
        self.assertNotIn("내부 메모", raw)

    def test_private_content_blocks_export_without_leaking_value(self):
        token = "ghp_" + "X" * 30
        self.body += "\n" + token + "\n"
        self.change_source()
        error = self.call("import", self.source, code=1)
        self.assertIn("자격증명", error)
        self.assertNotIn(token, error)
        self.assertFalse((self.repo / tool.STATE).exists())
        self.assertFalse((self.repo / "content").exists())

    def test_local_images_and_links_are_not_silently_copied(self):
        self.body += "\n![캡처](./image.png)\n[설정](../config.md)\n"
        self.change_source()
        self.call("import", self.source, code=1)

    def test_slug_cannot_escape_output(self):
        self.metadata["blog"]["slug"] = "../../escape"
        self.change_source()
        self.call("import", self.source, code=1)
        self.assertFalse((self.repo / tool.STATE).exists())

    def test_collision_with_existing_manual_article_is_blocked(self):
        path = self.repo / "content/posts/manual.md"
        path.parent.mkdir(parents=True)
        path.write_text('---\nslug: audio-format\n---\nexisting\n', encoding="utf-8")
        self.call("import", self.source, code=1)
        self.assertIn("existing", path.read_text(encoding="utf-8"))

    def test_candidate_must_be_boolean(self):
        self.metadata["blog"]["candidate"] = "true"
        self.change_source()
        self.assertEqual(self.call("list"), [])
        self.call("import", self.source, code=1)

    def test_check_detects_post_import_issues(self):
        path = self.imported()
        self.assertTrue(self.call("check", path.relative_to(self.repo).as_posix())["draft"])
        path.write_text(path.read_text(encoding="utf-8") + "\nhttps://example.atlassian.net/wiki\n", encoding="utf-8")
        self.call("check", path.relative_to(self.repo).as_posix(), code=1)

    def test_protected_branch_cannot_be_written(self):
        self.git("switch", "blog")
        self.call("import", self.source, code=1)
        self.assertFalse((self.repo / tool.STATE).exists())

    def test_invalid_ref_and_path_are_read_only_failures(self):
        self.call("--ref", "not-existing", "list", code=1)
        self.call("import", "TIL/../secret.md", code=1)
        self.call("import", "README.md", code=1)

    def test_changed_slug_cannot_change_an_imported_url(self):
        self.imported()
        self.metadata["blog"]["slug"] = "new-slug"
        self.change_source()
        self.call("import", self.source, "--update", code=1)
        self.assertEqual(len(list((self.repo / "content/posts").glob("*.md"))), 1)

    def test_til_date_is_not_used_as_publication_date(self):
        result = self.call("import", self.source)
        self.assertIn(datetime.now(tool.KST).date().isoformat(), result["output"])

    def test_future_publication_date_is_blocked(self):
        tomorrow = (datetime.now(tool.KST) + timedelta(days=1)).date().isoformat()
        self.call("import", self.source, "--date", tomorrow, code=1)

    def test_corrupt_state_does_not_reset_import_history(self):
        (self.repo / tool.STATE).write_text('{"version":99,"entries":{}}', encoding="utf-8")
        self.call("import", self.source, code=1)

    def test_duplicate_yaml_keys_and_python_tags_are_rejected(self):
        for raw in (b'---\nblog: {}\nblog: {}\n---\nbody',
                    b'---\nblog: !!python/object/apply:os.system [echo]\n---\nbody'):
            with self.assertRaises(tool.ImportError_):
                tool.split_document(raw)

    def test_concurrent_import_is_blocked(self):
        (self.repo / ".til-import.lock").write_text("active", encoding="utf-8")
        self.call("import", self.source, code=1)
        self.assertTrue((self.repo / ".til-import.lock").exists())

    def test_diff_helps_reconcile_without_overwriting_an_edited_post(self):
        path = self.imported()
        original = path.read_text(encoding="utf-8") + "\n블로그 편집 내용\n"
        path.write_text(original, encoding="utf-8")
        self.body += "\n원본의 새 실험 결과\n"
        self.change_source()
        difference = self.call("diff", self.source)
        self.assertIn("+원본의 새 실험 결과", difference)
        self.assertIn("-블로그 편집 내용", difference)
        self.assertEqual(path.read_text(encoding="utf-8"), original)

    def test_stale_record_temp_file_cannot_create_a_partial_import(self):
        (self.repo / (tool.STATE + ".tmp")).write_text("preserve", encoding="utf-8")
        self.call("import", self.source, code=1)
        self.assertFalse((self.repo / "content").exists())
        self.assertEqual((self.repo / (tool.STATE + ".tmp")).read_text(), "preserve")

    def test_failed_record_write_rolls_back_the_new_post(self):
        with mock.patch.object(tool.os, "replace", side_effect=OSError("simulated record failure")):
            self.call("import", self.source, code=1)
        self.assertFalse(list((self.repo / "content/posts").glob("*.md")))
        self.assertFalse((self.repo / tool.STATE).exists())
        self.assertFalse((self.repo / (tool.STATE + ".tmp")).exists())

    def test_shortcut_reference_image_cannot_bypass_image_checks(self):
        self.body += "\n![private]\n[private]: https://example.invalid/private.png\n"
        self.change_source()
        self.call("import", self.source, code=1)

    def test_diff_does_not_print_a_secret_added_to_the_local_draft(self):
        path = self.imported()
        token = "glpat-" + "Q" * 22
        path.write_text(path.read_text(encoding="utf-8") + "\n" + token + "\n", encoding="utf-8")
        error = self.call("diff", self.source, code=1)
        self.assertNotIn(token, error)
        self.assertIn("자격증명", error)

    def test_record_failure_restores_an_existing_unedited_draft(self):
        path = self.imported()
        original = path.read_bytes()
        state = (self.repo / tool.STATE).read_bytes()
        self.body += "\n갱신 내용\n"
        self.change_source()
        original_replace = tool.os.replace
        def fail_record(source, target):
            if Path(target).name == tool.STATE:
                raise OSError("simulated record failure")
            return original_replace(source, target)
        with mock.patch.object(tool.os, "replace", side_effect=fail_record):
            self.call("import", self.source, "--update", code=1)
        self.assertEqual(path.read_bytes(), original)
        self.assertEqual((self.repo / tool.STATE).read_bytes(), state)


if __name__ == "__main__":
    unittest.main()
