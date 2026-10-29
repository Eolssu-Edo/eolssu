"""국립국악원 단소 기본음계 녹음으로 김 세기·취법별 조율 흔들림을 잰다.

실행: uv run python -m evaluation.danso_tuning --raw-root <data/raw가 있는 저장소 루트> [--out 결과.md]

원본은 data/catalog/danso/sources.json의 rawPath에 받아 두고 data/src/catalog/verify_sources.py로 확인한다.
음 구간은 data/annotations/audio/danso/의 라벨을 쓰고, 채점(scoring.py)과 같이 pYIN f0에서
소리 있는 가운데 60% 프레임을 잰다. 연주자 한 명·악기 하나의 녹음이라 결론을 일반화하지 않는다.
"""

import argparse
import json
from pathlib import Path

import librosa
import numpy as np

from ai_service.pipelines.scoring import HOP_S, HWANG_HZ, pyin_f0

REPO = Path(__file__).resolve().parents[2]
SR = 16000
SOURCES = ["ngc-danso-2338", "ngc-danso-2339", "ngc-danso-2340", "ngc-danso-2341", "ngc-danso-2342"]
PITCHES = {
    (r["yul"], r["octave"]): r["semitone"]
    for r in json.loads((REPO / "data/reference/pitches.json").read_text(encoding="utf-8"))["rows"]
}


def semitone_of(register: str, yul: str) -> int:
    """평취 중·임·무는 옥타브 0, 황·태는 청(1). 역취는 한 옥타브 위(data/tests/test_reference_data.py와 같은 규칙)."""
    return PITCHES[yul, (yul in "황태") + (register == "역취")]


def summarize(notes: list[tuple[int, np.ndarray]]) -> dict:
    """(반음, 음 구간 f0) 목록에서 녹음 보정량(가운데 프레임 전체의 중앙값)과 음별 값을 구한다."""
    measured = []
    for semitone, f0 in notes:
        voiced = f0[~np.isnan(f0)]
        cents = 1200 * np.log2(voiced / HWANG_HZ) - 100 * semitone
        middle = cents[int(len(cents) * 0.2) : max(int(len(cents) * 0.8), 1)]
        measured.append((semitone, cents, middle))
    offset = float(np.median(np.concatenate([m for _, _, m in measured])))
    return {
        "offset": offset,
        "notes": [
            {
                "semitone": s,
                "voiced": len(c),
                "cents": float(np.median(m)),
                "residual": float(np.median(m)) - offset,
                "octave_ratio": float(np.mean(np.abs(c) > 600)) if len(c) else 0.0,
            }
            for s, c, m in measured
        ],
    }


def measure(raw_root: Path, source_id: str) -> tuple[dict, dict]:
    catalog = json.loads((REPO / "data/catalog/danso/sources.json").read_text(encoding="utf-8"))
    source = next(s for s in catalog["sources"] if s["id"] == source_id)
    labels = json.loads((REPO / Path(source["annotationPath"])).read_text(encoding="utf-8"))
    column = {name: i for i, name in enumerate(labels["columns"])}
    audio, _ = librosa.load(raw_root / source["rawPath"], sr=SR, mono=True)
    f0 = pyin_f0(audio, SR)
    notes, names = [], []
    for row in (r for g in labels["groups"] for r in g["rows"]):
        start, end = (round(row[column[k]] / HOP_S) for k in ("startSeconds", "endSeconds"))
        notes.append((semitone_of(row[column["register"]], row[column["yulmyeong"]]), f0[start:end]))
        names.append(f'{row[column["register"]]} {row[column["yulmyeong"]]}')
    return source, summarize(notes) | {"names": names}


def table(results: list[tuple[dict, dict]]) -> str:
    lines = [
        "| 녹음 | 보정량 | 음별 잔차(센트, 보정 뒤) | 가장 큰 잔차 | 옥타브 오인식 프레임 |",
        "| --- | ---: | --- | ---: | ---: |",
    ]
    for source, r in results:
        residuals = ", ".join(f'{name} {n["residual"]:+.1f}' for name, n in zip(r["names"], r["notes"]))
        worst = max(abs(n["residual"]) for n in r["notes"])
        octave = sum(n["octave_ratio"] * n["voiced"] for n in r["notes"]) / sum(n["voiced"] for n in r["notes"])
        lines.append(f'| {source["title"]} | {r["offset"]:+.1f} | {residuals} | {worst:.1f} | {octave:.1%} |')
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--raw-root", type=Path, default=REPO, help="rawPath의 기준 폴더. 기본은 이 저장소 루트")
    parser.add_argument("--out", type=Path, help="결과 표를 저장할 Markdown 파일")
    args = parser.parse_args()
    text = table([measure(args.raw_root, s) for s in SOURCES])
    print(text)
    if args.out:
        args.out.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    main()
