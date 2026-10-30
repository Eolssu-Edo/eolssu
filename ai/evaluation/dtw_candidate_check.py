"""U2의 작은 고정 입력으로 연구용 후보와 실제 score를 연결한다.

실행: uv run python -m evaluation.dtw_candidate_check --method A --out ../data/processed/dtw-286/u2-A.json
단독 평가 프로세스용이다. 정렬 교체를 서비스나 여러 스레드에서 사용하지 않는다.
"""
import argparse
from dataclasses import asdict
from functools import partial
import hashlib
import json
import math
from pathlib import Path
from unittest.mock import patch

from ai_service.pipelines import scoring
from evaluation.dtw_baseline import CASES, inputs
from evaluation.dtw_candidates import NoPath, _distance, band_bounds, band_path, checkpoint_path


def check_case(name, method, half_width_seconds=0.5):
    f0, notes, bpm = inputs(name)
    frames = round(half_width_seconds / scoring.HOP_S)
    target = {"reference": scoring._align, "A": checkpoint_path,
              "B": partial(band_path, half_width_frames=frames)}[method]
    path, total, cells = None, None, None

    def align(expected, detected):
        nonlocal path, total, cells
        n, m = len(expected), len(detected)
        cells = n * m  # 누적 비용을 계산한 칸 수. A의 재계산은 경로를 얻은 뒤 더한다.
        if method == "B":  # B는 band 안의 칸만 계산하고 그 칸마다 uint8 방향 하나를 저장한다
            starts, stops = band_bounds(n, m, frames)
            cells = 0 if n == 1 or m == 1 or (stops <= starts).any() else int((stops - starts).sum())
        path = target(expected, detected)
        if method == "A":
            k = min(n, math.ceil(math.sqrt(8 * n)))
            blocks = {}
            for i, _ in path[:-1]:
                first = (int(i) // k) * k
                blocks.setdefault(first, int(i) + 1 - first)
            cells += sum(blocks.values()) * m
        total, previous = 0., None
        for i, j in path[::-1]:
            cost = float(_distance(expected[i], detected[j]))
            stretch = scoring._STRETCH_COST if previous is not None and (i == previous[0] or j == previous[1]) else 0.
            total += cost + stretch
            previous = i, j
        return path

    try:
        with patch.object(scoring, "_align", align):
            result = scoring.score(f0, notes, bpm, scoring.HWANG_HZ)
    except NoPath as error:
        return {"case": name, "method": method, "halfWidthFrames": frames if method == "B" else None,
                "computedCells": cells, "status": "NO_PATH", "reason": str(error), "score": None, "path": None,
                "totalCost": None}
    return {"case": name, "method": method, "halfWidthFrames": frames if method == "B" else None,
            "computedCells": cells, "status": result.status, "score": asdict(result),
            "path": None if path is None else path.tolist(), "totalCost": total}


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--method", choices=("reference", "A", "B"), required=True)
    parser.add_argument("--cases", choices=CASES, nargs="+", default=list(CASES))
    parser.add_argument("--half-width-seconds", type=float, choices=(0.5, 1., 2.), default=0.5)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    paths = ("ai/evaluation/dtw_candidates.py", "ai/evaluation/dtw_candidate_check.py",
             "ai/evaluation/dtw_baseline.py", "ai/src/ai_service/pipelines/scoring.py", "ai/uv.lock")
    report = {"hashes": {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in paths},
              "results": [check_case(c, args.method, args.half_width_seconds) for c in args.cases]}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n",
                        encoding="utf-8", newline="\n")
    print(f"{args.out}: {len(report['results'])} cases")


if __name__ == "__main__":
    main()
