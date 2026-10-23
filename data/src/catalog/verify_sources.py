"""data/catalog/<datasetId>/sources.json의 원본 목록과 data/raw의 실제 파일을 대조한다.

실행: python data/src/catalog/verify_sources.py [--dataset ID ...] [--root 저장소_루트]
--dataset을 생략하면 모든 데이터셋을 대조한다. 내 PC에 받은 데이터셋만 고르려면 ID를 적는다.
파일이 없거나 크기·SHA-256이 다르면 목록에 적고 종료 코드 1로 끝난다.
"""

import argparse
import hashlib
import pathlib
import sys

from source_catalog import load_source_catalog
REPO_ROOT = pathlib.Path(__file__).resolve().parents[3]
CATALOG = REPO_ROOT / "data/catalog"


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", nargs="+", help="대조할 datasetId. 생략하면 전체")
    parser.add_argument("--root", type=pathlib.Path, default=REPO_ROOT, help="rawPath의 기준 폴더. 기본은 이 저장소 루트")
    args = parser.parse_args()

    available = sorted(p.parent.name for p in CATALOG.glob("*/sources.json"))
    chosen = args.dataset or available
    unknown = sorted(set(chosen) - set(available))
    if unknown:
        parser.error(f"없는 datasetId: {', '.join(unknown)}. 있는 것: {', '.join(available)}")

    total, problems = 0, []
    for dataset in chosen:
        try:
            sources = load_source_catalog(CATALOG / dataset / "sources.json")["sources"]
        except (OSError, ValueError) as error:
            print(f"목록 오류 {dataset}: {error}", file=sys.stderr)
            return 1
        for s in sources:
            total += 1
            path = args.root / s["rawPath"]
            if not path.is_file():
                problems.append(f"없음      {dataset}/{s['id']}  {s['rawPath']}")
            elif path.stat().st_size != s["bytes"]:
                problems.append(f"크기 다름 {dataset}/{s['id']}  {path.stat().st_size} != {s['bytes']}")
            elif sha256(path) != s["sha256"].lower():
                problems.append(f"해시 다름 {dataset}/{s['id']}")

    print(f"일치 {total - len(problems)} / {total}  ({', '.join(chosen)})")
    print("\n".join(problems))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
