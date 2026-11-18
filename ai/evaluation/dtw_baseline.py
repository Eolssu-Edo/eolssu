"""작은 고정 f0에서 현행 score의 DTW 경로·비용·판정을 관찰한다.

실행: uv run python -m evaluation.dtw_baseline --out ../data/processed/dtw-286/baseline.json
서비스의 판정 코드를 복제하지 않는다. 단일 프로세스에서만 DTW 호출을 관찰하며,
후보 정렬 구현이나 동시 실행에는 쓰지 않는다.
"""
import argparse
from dataclasses import asdict
import hashlib
from importlib.metadata import version
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import time
import tracemalloc
from unittest.mock import patch

import librosa
import numpy as np

from ai_service.pipelines.scoring import HOP_S, HWANG_HZ, Note, score

REPO = Path(__file__).resolve().parents[2]
CASES = ("single", "rest", "repeated", "unequal", "silence", "normal-2s", "normal-4s", "normal-8s")


def inputs(name):
    if name.startswith("normal-"):
        seconds = {"normal-2s": 2, "normal-4s": 4, "normal-8s": 8}[name]
        return np.full(round(seconds / HOP_S), HWANG_HZ), [Note(0, seconds * 2)], 120
    if name == "rest":
        return (np.array([HWANG_HZ, np.nan, np.nan, HWANG_HZ * 2 ** (2 / 12)]),
                [Note(0, 1, idx=7, start_beat=0), Note(2, 1, idx=9, start_beat=3)], 6000)
    if name == "silence":
        return np.array([np.nan]), [Note(0, 1)], 6000
    if name == "single":
        return np.array([HWANG_HZ]), [Note(0, 1)], 6000
    if name == "repeated":
        return np.full(3, HWANG_HZ), [Note(0, 1), Note(0, 1), Note(0, 1)], 6000
    if name == "unequal":
        return np.full(2, HWANG_HZ), [Note(0, 3)], 6000
    raise ValueError(f"unknown case: {name}")


def run_case(name):
    """실제 score 호출을 한 번 관찰한다. 전체 비용 표는 작은 사례만 저장한다."""
    f0, notes, bpm = inputs(name)
    alignment = None
    original = librosa.sequence.dtw

    def observe(*args, **kwargs):
        nonlocal alignment
        accumulated, path = original(*args, **kwargs)
        cost = kwargs["C"]
        alignment = {
            "shape": list(cost.shape), "dtype": str(cost.dtype),
            "totalCost": float(accumulated[-1, -1]), "path": path.tolist(),
            "smallCost": cost.tolist() if max(cost.shape) <= 8 else None,
        }
        return accumulated, path

    with patch.object(librosa.sequence, "dtw", observe):
        result = score(f0, notes, bpm, HWANG_HZ)
    return {"case": name, "input": {
        "f0Sha256": hashlib.sha256(f0.astype("<f8").tobytes()).hexdigest(),
        "notes": [asdict(n) for n in notes], "bpm": bpm,
        "hopSeconds": HOP_S, "referenceHz": HWANG_HZ,
    }, "score": asdict(result), "alignment": alignment}


def tie_probes():
    """이동 우선순위를 손으로 계산한 표다. 녹음 품질의 근거는 아니다."""
    cost = np.array([[0, 0, 0], [0, 200, 0], [0, 0, 100]], dtype=float)
    accumulated, path = librosa.sequence.dtw(
        C=cost, step_sizes_sigma=np.array([[1, 1], [0, 1], [1, 0]]),
        weights_add=np.array([0., 50., 50.]),
    )
    return {"horizontalBeforeVertical": {"cost": cost.tolist(),
            "totalCost": float(accumulated[-1, -1]), "path": path.tolist()}}


def process_peak_rss():
    """import·JIT 준비를 포함한 프로세스 전체 최대 상주 메모리다."""
    if sys.platform == "win32":
        import ctypes
        from ctypes import wintypes

        class Counters(ctypes.Structure):
            _fields_ = [("cb", wintypes.DWORD), ("faults", wintypes.DWORD)] + [
                (name, ctypes.c_size_t) for name in (
                    "peakWorkingSet", "workingSet", "quotaPeakPaged", "quotaPaged",
                    "quotaPeakNonPaged", "quotaNonPaged", "pagefile", "peakPagefile")]
        counters = Counters()
        counters.cb = ctypes.sizeof(counters)
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.GetCurrentProcess.restype = wintypes.HANDLE
        query = ctypes.WinDLL("psapi", use_last_error=True).GetProcessMemoryInfo
        query.argtypes = [wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD]
        if not query(kernel.GetCurrentProcess(), ctypes.byref(counters), counters.cb):
            raise ctypes.WinError(ctypes.get_last_error())
        return int(counters.peakWorkingSet)
    import resource
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return int(peak if sys.platform == "darwin" else peak * 1024)


def measure_case(name, max_bytes=512 * 2**20):
    f0, notes, bpm = inputs(name)
    span_beats = (sum(n.beats for n in notes) if notes[0].start_beat is None else
                  notes[-1].start_beat + notes[-1].beats - notes[0].start_beat)
    span = span_beats * 60 / bpm
    estimated = 24 * round(span / HOP_S) * len(f0)
    if estimated > max_bytes:
        return {"case": name, "status": "skipped_budget", "estimatedDtwBytes": estimated}
    began = time.perf_counter()
    run_case("single")  # 같은 dtype으로 JIT를 준비한다. 큰 표는 만들지 않는다.
    warmup = time.perf_counter() - began
    tracemalloc.start()
    try:
        began = time.perf_counter()
        snapshot = run_case(name)
        elapsed = time.perf_counter() - began
        _, peak = tracemalloc.get_traced_memory()
    finally:
        tracemalloc.stop()
    return {
        "case": name, "status": "measured", "estimatedDtwBytes": estimated,
        "warmupSeconds": warmup, "scoreSeconds": elapsed,
        "tracemallocPeakBytes": peak,
        "processPeakRssBytesIncludingWarmup": process_peak_rss(), "snapshot": snapshot,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cases", nargs="+", choices=CASES, default=list(CASES))
    parser.add_argument("--repeat", type=int, choices=range(1, 6), default=3)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--child", choices=CASES, help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.child:
        print(json.dumps(measure_case(args.child), ensure_ascii=False, allow_nan=False))
        return
    runs = []
    for name in args.cases:
        for _ in range(args.repeat):
            child = subprocess.run(
                [sys.executable, "-m", "evaluation.dtw_baseline", "--child", name],
                cwd=REPO / "ai", check=True, capture_output=True, text=True,
                encoding="utf-8", timeout=60,
            )
            runs.append(json.loads(child.stdout))
    report = {
        "environment": {"python": platform.python_version(), "platform": platform.platform(),
                        "numpy": version("numpy"), "librosa": version("librosa"),
                        "machine": platform.machine(), "logicalCpuCount": os.cpu_count(),
                        "threadEnvironment": {key: os.environ.get(key) for key in
                                              ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "NUMBA_NUM_THREADS")},
                        "evaluationSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                        "scoringSha256": hashlib.sha256(
                            (REPO / "ai/src/ai_service/pipelines/scoring.py").read_bytes()).hexdigest(),
                        "uvLockSha256": hashlib.sha256((REPO / "ai/uv.lock").read_bytes()).hexdigest()},
        "tieProbes": tie_probes(), "runs": runs,
    }
    output = json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(output, encoding="utf-8", newline="\n")
        print(f"{args.out}: {len(runs)} runs")
    else:
        print(output, end="")


if __name__ == "__main__":
    main()
