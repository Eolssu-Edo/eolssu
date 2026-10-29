"""녹음 정답 기록으로 채점의 오류 검출률·오탐률을 허용값 조합별로 잰다.

실행: uv run python -m evaluation.error_detection <정답 기록.json ...> [--root 저장소_루트] [--out 결과.json]
전후 비교: 같은 명령에 --compare 이전결과.json --vary scoring(바꾼 변인 묶음)을 더한다.

녹음마다 f0를 한 번 구하고 허용값 조합마다 다시 채점한다. 결과 JSON에는 지표와 함께 결과를 바꿀 수 있는
변인 다섯 묶음(데이터·채점 설정·실행 환경·합성 입력·실제 녹음 조건)을 남겨 전후 비교에 쓴다.
"""

import argparse
import hashlib
import inspect
import itertools
import json
import math
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
# 음별 판정 글자. O는 옥타브 의심이 붙은 PITCH. 정답 문자열의 C는 오류를 넣지 않은 음이다.
LETTER = {"CORRECT": "C", "PITCH": "P", "TIMING": "T", "MISSED": "M"}
INTENDED = {"PITCH": "P", "OCTAVE": "O", "TIMING": "T", "MISSED": "M"}
DECLARABLE = {"scoring", "environment"}  # 나머지 묶음은 정답 기록에 들어 있어 바뀌면 같은 녹음 비교가 아니다


class CompareError(ValueError):
    """두 결과를 같은 조건의 전후로 비교할 수 없다."""


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


def _letter(r: NoteResult) -> str:
    return "O" if r.verdict == "PITCH" and r.octave_suspect else LETTER[r.verdict]


def expected_letters(trials: list[Trial]) -> dict:
    """녹음마다 첫 음 번호와 음별 정답 글자. 전후 비교에서 판정이 맞았는지 가린다."""
    out = {}
    for t in trials:
        errors = {e["idx"]: INTENDED[e["kind"]] for e in t.label["errors"]}
        out[t.label["recordingId"]] = {"start": t.notes[0].idx, "letters": "".join(errors.get(n.idx, "C") for n in t.notes)}
    return out


def _ok(expected: str, got: str) -> bool:
    return expected == got or (expected == "P" and got == "O")


def mcnemar(b: int, c: int) -> float:
    """같은 음의 판정이 맞음→틀림(b), 틀림→맞음(c)으로 바뀐 수로 두 채점이 다른지 본다(양측 p).

    b + c가 25 미만이면 이항 정확 검정 2 × P(X ≤ min(b, c)), X ~ B(b + c, 1/2), 아니면 χ² = (b − c)² / (b + c).
    """
    n = b + c
    if n == 0:
        return 1.0
    if n < 25:
        return min(1.0, 2 * sum(math.comb(n, k) for k in range(min(b, c) + 1)) / 2**n)
    return math.erfc(math.sqrt((b - c) ** 2 / n / 2))


def _flatten(value, prefix: str = "") -> dict:
    if isinstance(value, dict):
        return {k: v for key, item in value.items() for k, v in _flatten(item, f"{prefix}{key}.").items()}
    return {prefix[:-1]: value}


def compare(before: dict, after: dict, vary: set[str]) -> dict:
    """선언한 묶음(vary) 말고는 변인이 같아야 비교한다. 다르면 달라진 항목을 모두 적어 멈춘다."""
    if bad := sorted(vary - DECLARABLE):
        raise CompareError(f"--vary에 {bad}를 쓸 수 없다. 이 묶음은 정답 기록에 들어 있어 바뀌면 같은 녹음 비교가 아니다")
    old, new = _flatten(before["variables"]), _flatten(after["variables"])
    changed = [f"{k}: {old.get(k)} → {new.get(k)}" for k in sorted(old.keys() | new.keys()) if old.get(k) != new.get(k)]
    if undeclared := [c for c in changed if c.split(".")[0] not in vary]:
        raise CompareError("선언하지 않은 변인이 다르다: " + "; ".join(undeclared))

    expected = before["expected"]
    default = asdict(Tolerance())
    rows, changed_notes = [], []
    for b_row in before["results"]:
        a_row = next((r for r in after["results"] if r["tolerance"] == b_row["tolerance"]), None)
        if a_row is None:
            continue
        b = c = 0
        for rec, exp in expected.items():
            for pos, (e, x, y) in enumerate(zip(exp["letters"], b_row["verdicts"][rec], a_row["verdicts"][rec])):
                b += _ok(e, x) and not _ok(e, y)
                c += not _ok(e, x) and _ok(e, y)
                if b_row["tolerance"] == default and x != y:
                    changed_notes.append({"recordingId": rec, "idx": exp["start"] + pos, "expected": e, "before": x, "after": y})
        rows.append(
            {
                "tolerance": b_row["tolerance"],
                "meanTotal": (b_row["normal"]["meanTotal"], a_row["normal"]["meanTotal"]),
                "recall": (b_row["detection"]["recall"], a_row["detection"]["recall"]),
                "falsePositiveRate": (b_row["falsePositive"]["rate"], a_row["falsePositive"]["rate"]),
                "b": b,
                "c": c,
                "p": mcnemar(b, c),
            }
        )
    return {"changedVariables": changed, "rows": rows, "changedNotes": changed_notes}


def _arrow(pair, fmt="{:.0%}") -> str:
    return " → ".join("-" if v is None else fmt.format(v) for v in pair)


def compare_table(report: dict) -> str:
    lines = ["달라진 변인: " + ("; ".join(report["changedVariables"]) or "없음"), ""]
    lines += [
        "| 음정 | 시작(박) | 길이 | 정상 점수 | 검출률 | 오탐률 | 맞음→틀림 b | 틀림→맞음 c | p |",
        "| ---: | ---: | ---: | --- | --- | --- | ---: | ---: | ---: |",
    ]
    for r in report["rows"]:
        tol = r["tolerance"]
        lines.append(
            f'| ±{tol["pitch_cent"]:.0f} | {tol["onset_beat"]} | ±{tol["duration_ratio"]:.0%} | {_arrow(r["meanTotal"], "{:.0f}")} '
            f'| {_arrow(r["recall"])} | {_arrow(r["falsePositiveRate"])} | {r["b"]} | {r["c"]} | {r["p"]:.3g} |'
        )
    notes = [f'- {n["recordingId"]} {n["idx"]}번: 정답 {n["expected"]}, {n["before"]} → {n["after"]}' for n in report["changedNotes"]]
    lines += ["", "기본 허용값에서 판정이 바뀐 음:", *(notes or ["- 없음"])]
    return "\n".join(lines) + "\n"


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
            verdicts[t.label["recordingId"]] = "".join(_letter(n) for n in notes)
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
            # 기준 결과(baselines)는 채점 코드가 아니라서 뺀다. 넣으면 결과를 저장하는 것만으로 변인이 바뀐다.
            "uncommittedChanges": bool(
                _command("git", "status", "--porcelain", "--", "ai/src", "ai/evaluation", ":!ai/evaluation/baselines")
            ),
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
    parser.add_argument("--compare", type=Path, help="비교할 이전 결과 JSON")
    parser.add_argument("--vary", nargs="*", default=[], choices=sorted(DECLARABLE), help="이번에 바꾼 변인 묶음")
    args = parser.parse_args()
    labels, trials = load_trials(args.labels, args.root)
    report = evaluate(trials, GRID)
    print(table(report))
    result = {
        "schemaVersion": 1,
        "createdAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "command": " ".join(sys.argv),
        "variables": variables(args.labels, labels),
        "expected": expected_letters(trials),
        "results": report,
    }
    if args.out:
        args.out.write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    if args.compare:
        print(compare_table(compare(json.loads(args.compare.read_text(encoding="utf-8")), result, set(args.vary))))


if __name__ == "__main__":
    main()
