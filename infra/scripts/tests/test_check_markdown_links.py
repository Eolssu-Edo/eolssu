"""Markdown 상대 링크 검사기가 끊긴 링크만 찾고 외부 URL·코드·앵커는 건너뛰는지 검사한다."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location(
    'check_markdown_links', Path(__file__).parents[1] / 'check-markdown-links.py')
links = importlib.util.module_from_spec(spec)
spec.loader.exec_module(links)


class LinkCheckTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        (self.root / 'docs/sub').mkdir(parents=True)
        (self.root / 'docs/sub/target.md').write_text('# 대상\n')
        (self.root / 'docs/한글 파일.md').write_text('# 한글\n')
        (self.root / 'infra').mkdir()

    def write(self, name, text):
        path = self.root / name
        path.write_text(text, encoding='utf-8')
        return path

    def check(self, path, exclude=()):
        return links.broken_links(self.root, files=[path], exclude=exclude)

    def test_existing_relative_links_pass(self):
        page = self.write('docs/index.md', '\n'.join([
            '[대상](sub/target.md)',
            '[앵커 포함](sub/target.md#대상)',
            '[폴더](../infra/)',
            '[루트 기준](/docs/sub/target.md)',
            '[인코딩](%ED%95%9C%EA%B8%80%20%ED%8C%8C%EC%9D%BC.md)',
            '[꺾쇠](<한글 파일.md>)',
            '[제목 포함](sub/target.md "설명")',
            '![그림](sub/target.md)',
            '[참조]: sub/target.md',
        ]))
        self.assertEqual(self.check(page), [])

    def test_reports_missing_targets_with_line(self):
        page = self.write('docs/index.md', '첫 줄\n[없음](missing.md) 그리고 [있음](sub/target.md)\n[참조]: gone/\n')
        self.assertEqual(self.check(page), [('docs/index.md', 2, 'missing.md'), ('docs/index.md', 3, 'gone/')])

    def test_skips_external_anchor_and_code(self):
        page = self.write('docs/index.md', '\n'.join([
            '[웹](https://example.com/x.md)',
            '[메일](mailto:a@b.c)',
            '[같은 문서](#제목)',
            '`[코드](missing.md)`',
            '```md',
            '[펜스 안](missing.md)',
            '```',
            '~~~',
            '[물결 펜스](missing.md)',
            '~~~',
        ]))
        self.assertEqual(self.check(page), [])

    def test_link_text_with_brackets(self):
        page = self.write('docs/index.md', '[[INF] 제목](missing.md)\n')
        self.assertEqual(self.check(page), [('docs/index.md', 1, 'missing.md')])

    def test_exclude_prefix(self):
        page = self.write('docs/index.md', '[없음](missing.md)\n')
        self.assertEqual(self.check(page, exclude=['docs']), [])

    def test_cli_reads_git_tracked_files_and_returns_status(self):
        script = Path(__file__).parents[1] / 'check-markdown-links.py'
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True)
        self.write('docs/index.md', '[없음](missing.md)\n')
        self.write('untracked.md', '[무시](nothing.md)\n')
        subprocess.run(['git', '-C', str(self.root), 'add', 'docs'], check=True)
        result = subprocess.run(['python3', '-B', str(script), '--root', str(self.root)],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertEqual(result.stdout, 'docs/index.md:1: missing.md\n')
        self.assertIn('1건', result.stderr)


if __name__ == '__main__':
    unittest.main()
