"""data/catalog/sources.json의 원본 목록과 data/raw의 실제 파일을 대조한다.

실행: python data/src/catalog/verify_sources.py [--root 저장소_루트]
파일이 없거나 크기·SHA-256이 다르면 목록에 적고 종료 코드 1로 끝난다.
"""

import argparse
import hashlib
import json
import pathlib
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8 << 20), b""):
            h.update(chunk)
    return h.hexdigest().upper()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, default=REPO_ROOT, help="rawPath의 기준 폴더. 기본은 이 저장소 루트")
    args = parser.parse_args()

    catalog = json.loads((REPO_ROOT / "data/catalog/sources.json").read_text(encoding="utf-8"))
    problems = []
    for s in catalog["sources"]:
        path = args.root / s["rawPath"]
        if not path.is_file():
            problems.append(f"없음      {s['id']}  {s['rawPath']}")
        elif path.stat().st_size != s["bytes"]:
            problems.append(f"크기 다름 {s['id']}  {path.stat().st_size} != {s['bytes']}")
        elif sha256(path) != s["sha256"]:
            problems.append(f"해시 다름 {s['id']}")

    total = len(catalog["sources"])
    print(f"일치 {total - len(problems)} / {total}")
    print("\n".join(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
