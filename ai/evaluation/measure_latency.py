"""녹음 길이별 분석 처리 시간과 메모리를 잰다.

실행: uv run --group eval python -m evaluation.measure_latency [--seconds 15 30 60] [--methods pYIN ...] [--out 결과.md]

길이마다 합성 음계를 이어 붙인 입력을 따로 띄운 프로세스에서 분석해, 음높이 추정·채점(DTW 포함)
시간과 프로세스 최대 메모리를 기록한다. 서버처럼 모델은 미리 읽어 두고, 그 준비 시간은 따로 적는다.
채점의 DTW는 프레임 수의 제곱만큼 메모리를 쓰므로 예상치가 한도를 넘으면 채점하지 않는다.
"""

import argparse
import json
import subprocess
import sys
import time

from ai_service.pipelines.scoring import HOP_S, frame_rms, score
from evaluation.run_evaluation import METHODS
from evaluation.synthetic import BPM, REF_HZ, SCALE, SR, synth

DTW_BYTES_PER_CELL = 24  # tracemalloc 실측 20(비용 8 + 누적 비용 8 + 이동 기록 4)에 여유를 둔 값


def long_notes(seconds):
    """합성 음계를 이어 붙여 seconds 길이가 되는 음표 목록. 음표 하나는 한 박이다."""
    count = round(seconds * BPM / 60)
    return [SCALE[i % len(SCALE)] for i in range(count)]


def dtw_bytes(seconds):
    """채점 DTW의 최대 메모리 예상치. 정답 프레임 수 × 녹음 프레임 수 × 칸당 바이트."""
    frames = round(seconds / HOP_S)
    return DTW_BYTES_PER_CELL * frames * frames


def decide(seconds, memory_gib, last, max_seconds, failed):
    """(채점할지, 건너뛸 이유)를 정한다. last는 직전 길이의 (길이, 음높이 추정 시간)."""
    with_score = dtw_bytes(seconds) <= memory_gib * 2**30
    if failed:
        return with_score, "건너뜀(앞 길이 실패)"
    if last and last[1] * seconds / last[0] > max_seconds:
        return with_score, f"건너뜀(예상 {last[1] * seconds / last[0]:.0f}초)"
    return with_score, None


def peak_mb():
    try:
        import resource
    except ImportError:  # Windows에는 resource가 없다
        return None
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return peak / 2**20 if sys.platform == "darwin" else peak / 2**10  # macOS는 바이트, Linux는 KB


def run_child(method, seconds, with_score):
    """한 프로세스에서 한 번 분석하고 결과를 JSON 한 줄로 출력한다."""
    f0_fn = METHODS[method]
    started = time.perf_counter()
    f0_fn(synth(SCALE[:2]), SR)  # 모델 로딩·JIT 준비
    warmup = time.perf_counter() - started

    notes = long_notes(seconds)
    audio = synth(notes)
    started = time.perf_counter()
    f0 = f0_fn(audio, SR)
    f0_s = time.perf_counter() - started
    row = {"warmup_s": warmup, "f0_s": f0_s, "score_s": None, "status": "f0만"}
    if with_score:
        started = time.perf_counter()
        result = score(f0, notes, BPM, REF_HZ, rms=frame_rms(audio, SR))
        row.update(score_s=time.perf_counter() - started, status=result.status)
    row["peak_mb"] = peak_mb()
    print(json.dumps(row))


def measure(method, seconds, with_score, timeout):
    cmd = [sys.executable, "-m", "evaluation.measure_latency", "--child", method, str(seconds)]
    if not with_score:
        cmd.append("--no-score")
    try:
        done = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, check=True)
    except subprocess.TimeoutExpired:
        return {"status": f"{timeout}초 넘어 중단"}
    except subprocess.CalledProcessError as e:
        return {"status": "실패: " + (e.stderr.strip().splitlines() or ["?"])[-1]}
    return json.loads(done.stdout.strip().splitlines()[-1])


def fmt(value, digits=2):
    return "-" if value is None else f"{value:.{digits}f}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seconds", nargs="+", type=int, default=[15, 30, 60, 120, 300, 600])
    parser.add_argument("--methods", nargs="+", choices=list(METHODS), default=list(METHODS))
    parser.add_argument("--memory-gib", type=float, default=6.0, help="채점 DTW 예상 메모리 한도")
    parser.add_argument("--max-seconds", type=float, default=600.0, help="음높이 추정 예상 시간 한도")
    parser.add_argument("--out", help="결과 표를 저장할 Markdown 파일")
    parser.add_argument("--child", nargs=2, metavar=("METHOD", "SECONDS"), help=argparse.SUPPRESS)
    parser.add_argument("--no-score", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()

    if args.child:
        run_child(args.child[0], int(args.child[1]), not args.no_score)
        return

    lines = [
        "| 녹음 길이(초) | 방법 | 준비(초) | 음높이 추정(초) | 채점(초) | 합계(초) | 최대 메모리(MB) | DTW 예상(MB) | 비고 |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for method in args.methods:
        last = None  # (길이, 음높이 추정 시간): 다음 길이의 예상 시간을 비례로 잡는다
        failed = False  # 시간 초과·실패가 나면 더 긴 길이는 더 오래 걸리므로 돌리지 않는다
        for seconds in sorted(args.seconds):
            dtw_mb = dtw_bytes(seconds) / 2**20
            with_score, skip = decide(seconds, args.memory_gib, last, args.max_seconds, failed)
            note = "" if with_score else f"채점 안 함(DTW 예상 {dtw_mb / 1024:.0f}GiB)"
            if skip:
                row = {"status": skip}
            else:
                row = measure(method, seconds, with_score, timeout=args.max_seconds * 2)
                if row.get("f0_s") is not None:
                    last = (seconds, row["f0_s"])
                else:
                    failed = True
            total = None if row.get("score_s") is None else row["f0_s"] + row["score_s"]
            status = row["status"] if row["status"] not in ("DONE", "f0만") else ""
            lines.append(
                f"| {seconds} | {method} | {fmt(row.get('warmup_s'))} | {fmt(row.get('f0_s'))} "
                f"| {fmt(row.get('score_s'))} | {fmt(total)} | {fmt(row.get('peak_mb'), 0)} "
                f"| {dtw_mb:.0f} | {', '.join(x for x in (note, status) if x)} |"
            )
            print(lines[-1], flush=True)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
