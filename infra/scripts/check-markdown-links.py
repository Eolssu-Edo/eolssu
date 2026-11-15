#!/usr/bin/env python3
"""Git이 추적하는 Markdown의 상대 링크가 실제 파일·폴더를 가리키는지 검사한다.

외부 URL·메일·같은 문서 안의 앵커(#...)는 검사하지 않는다. 코드 블록과 인라인 코드 안의 텍스트는
링크로 보지 않는다. 끊긴 링크가 있으면 `파일:행: 대상`을 출력하고 종료 코드 1을 반환한다.
"""
import argparse
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote

INLINE_LINK = re.compile(r'!?\[(?:[^\[\]]|\[[^\[\]]*\])*\]\(\s*(<[^>]*>|[^)\s]+)(?:\s+"[^"]*"|\s+\'[^\']*\')?\s*\)')
REFERENCE_DEF = re.compile(r'^\s{0,3}\[[^\]]+\]:\s*(<[^>]*>|\S+)')
FENCE = re.compile(r'^\s{0,3}(`{3,}|~{3,})')
INLINE_CODE = re.compile(r'(`+)(?:(?!\1).)+?\1')
SKIP_SCHEMES = re.compile(r'^[a-zA-Z][a-zA-Z0-9+.-]*:')


def tracked_markdown(root):
    out = subprocess.run(['git', '-C', str(root), 'ls-files', '-z', '--', '*.md'],
                         check=True, capture_output=True).stdout
    return [root / name for name in out.decode().split('\0') if name]


def link_targets(text):
    """(행 번호, 대상) 목록을 돌려준다. 코드 블록·인라인 코드는 제외한다."""
    fence = None
    for number, line in enumerate(text.splitlines(), 1):
        match = FENCE.match(line)
        if match:
            marker = match.group(1)
            if fence is None:
                fence = marker[0] * len(marker)
            elif marker.startswith(fence):
                fence = None
            continue
        if fence is not None:
            continue
        plain = INLINE_CODE.sub('', line)
        for found in INLINE_LINK.finditer(plain):
            yield number, found.group(1)
        ref = REFERENCE_DEF.match(plain)
        if ref:
            yield number, ref.group(1)


def resolve(source, target, root):
    """검사할 경로를 돌려준다. 검사 대상이 아니면 None."""
    target = target.strip()
    if target.startswith('<') and target.endswith('>'):
        target = target[1:-1].strip()
    if not target or target.startswith('#') or SKIP_SCHEMES.match(target) or target.startswith('//'):
        return None
    path = unquote(target.split('#', 1)[0].split('?', 1)[0])
    if not path:
        return None
    base = root if path.startswith('/') else source.parent
    return (base / path.lstrip('/')).resolve()


def broken_links(root, files=None, exclude=()):
    root = Path(root).resolve()
    results = []
    for source in files if files is not None else tracked_markdown(root):
        source = Path(source).resolve()
        relative = source.relative_to(root).as_posix()
        if any(relative == prefix or relative.startswith(prefix.rstrip('/') + '/') for prefix in exclude):
            continue
        text = source.read_text(encoding='utf-8', errors='replace')
        for number, target in link_targets(text):
            path = resolve(source, target, root)
            if path is not None and not path.exists():
                results.append((relative, number, target))
    return results


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--root', default=Path(__file__).resolve().parents[2], type=Path,
                        help='저장소 루트(기본: 이 스크립트 기준 상위 두 단계)')
    parser.add_argument('--exclude', action='append', default=[],
                        help='검사에서 뺄 저장소 기준 경로 접두사. 여러 번 지정 가능')
    args = parser.parse_args(argv)
    results = broken_links(args.root, exclude=args.exclude)
    for relative, number, target in results:
        print(f'{relative}:{number}: {target}')
    print(f'끊긴 상대 링크 {len(results)}건', file=sys.stderr)
    return 1 if results else 0


if __name__ == '__main__':
    sys.exit(main())
