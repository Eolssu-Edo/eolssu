"""녹음 정답 기록으로 채점의 오류 검출률·오탐률을 허용값 조합별로 잰다.

실행: uv run python -m evaluation.error_detection <정답 기록.json ...> [--root 저장소_루트] [--out 결과.json]

녹음마다 f0를 한 번 구하고 허용값 조합마다 다시 채점한다. 결과 JSON에는 지표와 함께 결과를 바꿀 수 있는
변인 다섯 묶음(데이터·채점 설정·실행 환경·합성 입력·실제 녹음 조건)을 남겨 전후 비교에 쓴다.
"""

import argparse
import hashlib
import inspect
import itertools
import json
import platform
import subprocess
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from importlib.metadata import version
from pathlib import Path

import numpy as np

from ai_service.api import MODEL_VERSION
from ai_service.pipelines.audio import SAMPLE_RATE, decode_recording
from ai_service.pipelines.scoring import HOP_S, HWANG_HZ, Note, NoteResult, Tolerance, frame_rms, pyin_f0, score
from evaluation.danso_tuning import PITCHES
from evaluation.labels import REPO, load_label

GRID = [
    Tolerance(pitch_cent=p, onset_beat=o, duration_ratio=d)
    for p, o, d in itertools.product((30.0, 40.0, 50.0, 60.0), (0.125, 0.2), (0.2, 0.25))
]
# 의도한 오류가 맞게 잡혔다고 보는 판정. OCTAVE는 PITCH이면서 옥타브 의심 표시가 붙어야 한다.
EXPECTED = {"PITCH": "PITCH", "OCTAVE": "PITCH", "TIMING": "TIMING", "MISSED": "MISSED"}
LETTER = {"CORRECT": "C", "PITCH": "P", "TIMING": "T", "MISSED": "M"}


@dataclass
class Trial:
    label: dict
    notes: list[Note]
    f0: np.ndarray
    rms: np.ndarray | None


def wilson(x: int, n: int, z: float = 1.96) -> tuple[float, float] | None:
    """이항 비율 x/n의 윌슨 신뢰구간. 표본이 적어도 0~1 밖으로 나가지 않는다."""
    if n == 0:
        return None
    p = x / n
    center = (p + z * z / (2 * n)) / (1 + z * z / n)
    half = z / (1 + z * z / n) * np.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return max(0.0, float(center - half)), min(1.0, float(center + half))


def score_notes(score_data: dict, start: int, end: int) -> list[Note]:
    """공통 악보 데이터의 NOTE를 0부터 센 번호로 [start, end] 구간만 채점 음표로 바꾼다(백엔드 ScoreJsonNotes와 같은 규칙)."""
    tpb = score_data["music"]["ticksPerBeat"]
    events = [e for e in score_data["events"] if e["kind"] == "NOTE"]
    return [
        Note(PITCHES[e["yul"], e["octave"]], e["durationTick"] / tpb, bool(e["sigimsae"]), i, e["startTick"] / tpb)
        for i, e in enumerate(events)
        if start <= i <= end
    ]


def classify(results: list[NoteResult], errors: dict[int, str]) -> list[str]:
    """음마다 TP(의도한 종류로 잡음)·WRONG_KIND(오류로는 잡았지만 종류가 다름)·FN·FP·TN."""
    out = []
    for r in results:
        kind = errors.get(r.idx)
        if kind is None:
            out.append("TN" if r.verdict == "CORRECT" else "FP")
        elif r.verdict == "CORRECT":
            out.append("FN")
        elif r.verdict == EXPECTED[kind] and (kind != "OCTAVE" or r.octave_suspect):
            out.append("TP")
        else:
            out.append("WRONG_KIND")
    return out


def evaluate(trials: list[Trial], grid: list[Tolerance]) -> list[dict]:
    report = []
    for tol in grid:
        counts = {k: 0 for k in ("TP", "WRONG_KIND", "FN", "FP", "TN")}
        by_kind = {k: {"tp": 0, "wrongKind": 0, "fn": 0} for k in EXPECTED}
        totals, verdicts = [], {}
        for t in trials:
            errors = {e["idx"]: e["kind"] for e in t.label["errors"]}
            result = score(t.f0, t.notes, t.label["bpm"], HWANG_HZ, tol, t.rms)
            # 무음 실패면 음표 결과가 없다. 모든 음을 놓친 것으로 센다.
            notes = result.notes or [NoteResult(n.idx, "MISSED") for n in t.notes]
            verdicts[t.label["recordingId"]] = "".join(LETTER[n.verdict] for n in notes)
            for n, c in zip(notes, classify(notes, errors)):
                counts[c] += 1
                if n.idx in errors:
                    by_kind[errors[n.idx]][{"TP": "tp", "WRONG_KIND": "wrongKind", "FN": "fn"}[c]] += 1
            if not errors:
                totals.append(result.total)
        flagged = counts["TP"] + counts["WRONG_KIND"] + counts["FN"]
        clean = counts["FP"] + counts["TN"]
        report.append(
            {
                "tolerance": asdict(tol),
                "onsetMs": sorted({round(tol.onset_beat * 60000 / t.label["bpm"]) for t in trials}),
                "normal": {"recordings": len(totals), "meanTotal": float(np.mean(totals)) if totals else None},
                "detection": {
                    "tp": counts["TP"],
                    "wrongKind": counts["WRONG_KIND"],
                    "fn": counts["FN"],
                    "recall": counts["TP"] / flagged if flagged else None,
                    "ci": wilson(counts["TP"], flagged),
                },
                "falsePositive": {
                    "fp": counts["FP"],
                    "tn": counts["TN"],
                    "rate": counts["FP"] / clean if clean else None,
                    "ci": wilson(counts["FP"], clean),
                },
                "byKind": by_kind,
                "verdicts": verdicts,
            }
        )
    return report


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _command(*args: str) -> str | None:
    try:
        return subprocess.run(args, capture_output=True, text=True, check=True, cwd=REPO).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def variables(label_paths: list[Path], labels: list[dict]) -> dict:
    """결과를 바꿀 수 있는 변인 다섯 묶음. 전후 비교는 바꾸겠다고 선언한 묶음 말고는 같아야 한다."""
    hashes = {Path(p).name: _sha256(Path(p)) for p in label_paths}
    pyin_defaults = {k: v.default for k, v in inspect.signature(pyin_f0).parameters.items() if v.default is not v.empty}
    ffmpeg = _command("ffmpeg", "-version")
    return {
        "data": {
            "labels": dict(sorted(hashes.items())),
            "datasetHash": hashlib.sha256("\n".join(sorted(hashes.values())).encode()).hexdigest(),
        },
        "scoring": {
            "modelVersion": MODEL_VERSION,
            "commit": _command("git", "rev-parse", "HEAD"),
            "uncommittedChanges": bool(_command("git", "status", "--porcelain", "--", "ai/src", "ai/evaluation")),
            "toleranceDefaults": asdict(Tolerance()),
            "pitchEstimator": {"name": "pyin", **pyin_defaults},
            "hopSeconds": HOP_S,
            "referenceHz": HWANG_HZ,
        },
        "environment": {
            "python": platform.python_version(),
            "packages": {p: version(p) for p in ("numpy", "librosa", "jsonschema")},
            "uvLock": _sha256(REPO / "ai/uv.lock"),
            "ffmpeg": ffmpeg.splitlines()[0] if ffmpeg else None,
            "platform": platform.platform(),
            "machine": platform.machine(),
        },
        "synthetic": [lb["conditions"] for lb in labels if lb["conditions"]["kind"] == "SYNTHETIC"],
        "performed": [lb["conditions"] for lb in labels if lb["conditions"]["kind"] == "PERFORMED"],
    }


def load_trials(label_paths: list[Path], root: Path) -> tuple[list[dict], list[Trial]]:
    labels, trials = [], []
    for path in label_paths:
        label = load_label(path, root)
        score_data = json.loads((root / label["score"]["path"]).read_text(encoding="utf-8"))
        notes = score_notes(score_data, label["range"]["startNoteIdx"], label["range"]["endNoteIdx"])
        # 서비스와 같은 디코딩(ffmpeg, 16kHz 모노)을 거친다.
        audio = decode_recording(root / label["audio"]["path"]).astype(np.float32) / 32768.0
        labels.append(label)
        trials.append(Trial(label, notes, pyin_f0(audio, SAMPLE_RATE), frame_rms(audio, SAMPLE_RATE)))
    return labels, trials


def _pct(value, ci) -> str:
    return "-" if value is None else f"{value:.0%} ({ci[0]:.0%}~{ci[1]:.0%})"


def table(report: list[dict]) -> str:
    """조합별 지표. 오탐률 5% 이하 중 검출률 최대를 ★, 유덴 지수(검출률 − 오탐률) 최대를 ◆로 표시한다."""
    usable = [r for r in report if r["detection"]["recall"] is not None and r["falsePositive"]["rate"] is not None]
    safe = [r for r in usable if r["falsePositive"]["rate"] <= 0.05]
    star = max(safe, key=lambda r: r["detection"]["recall"], default=None)
    diamond = max(usable, key=lambda r: r["detection"]["recall"] - r["falsePositive"]["rate"], default=None)
    lines = [
        "| 음정 | 시작(박, ms) | 길이 | 정상 점수 | 검출률(95%) | 종류 틀림 | 오탐률(95%) | 표시 |",
        "| ---: | --- | ---: | ---: | --- | ---: | --- | --- |",
    ]
    for r in report:
        tol, d, f, n = r["tolerance"], r["detection"], r["falsePositive"], r["normal"]
        mark = ("★" if r is star else "") + ("◆" if r is diamond else "")
        lines.append(
            f'| ±{tol["pitch_cent"]:.0f} | {tol["onset_beat"]} ({", ".join(map(str, r["onsetMs"]))}) | ±{tol["duration_ratio"]:.0%} '
            f'| {"-" if n["meanTotal"] is None else round(n["meanTotal"])} | {_pct(d["recall"], d["ci"])} | {d["wrongKind"]} '
            f'| {_pct(f["rate"], f["ci"])} | {mark} |'
        )
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("labels", nargs="+", type=Path, help="녹음 정답 기록 JSON")
    parser.add_argument("--root", type=Path, default=REPO, help="정답 기록 안 경로의 기준 폴더")
    parser.add_argument("--out", type=Path, help="결과 JSON을 저장할 파일")
    args = parser.parse_args()
    labels, trials = load_trials(args.labels, args.root)
    report = evaluate(trials, GRID)
    print(table(report))
    if args.out:
        result = {
            "schemaVersion": 1,
            "createdAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "command": " ".join(sys.argv),
            "variables": variables(args.labels, labels),
            "results": report,
        }
        args.out.write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
