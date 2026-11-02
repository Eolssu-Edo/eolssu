"""작은 고정 음높이 배열의 DTW 시간과 프로세스 최대 RSS를 비교한다.

Windows에서 각 측정을 별도 프로세스로 실행한다. 커밋 메모리 제한은 RSS 제한이 아니다.
곡 전체, m4a, 큰 입력은 이 도구의 측정 대상이 아니다.
"""
import argparse
import collections
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
METHODS = ('reference', 'A', 'B0.5', 'B1.0', 'B2.0')
LIMIT = 1024 ** 3
BUDGET = 512 * 1024 ** 2


def estimate_bytes(n, m):
    return 24 * n * m


def summarize(rows):
    done = [r for r in rows if r['status'] == 'DONE']
    return {'attempts': len(rows), 'statusCounts': dict(collections.Counter(r['status'] for r in rows)),
            'medianSeconds': statistics.median(r['seconds'] for r in done) if done else None,
            'minSeconds': min((r['seconds'] for r in done), default=None),
            'maxSeconds': max((r['seconds'] for r in done), default=None),
            'maxPeakRssBytes': max((r['peakRssBytes'] for r in done), default=None)}


class BasicLimit(ctypes.Structure):
    _fields_ = [('process_time', ctypes.c_longlong), ('job_time', ctypes.c_longlong),
                ('flags', wintypes.DWORD), ('min_ws', ctypes.c_size_t), ('max_ws', ctypes.c_size_t),
                ('active', wintypes.DWORD), ('affinity', ctypes.c_size_t),
                ('priority', wintypes.DWORD), ('scheduling', wintypes.DWORD)]


class IoCounters(ctypes.Structure):
    _fields_ = [(name, ctypes.c_ulonglong) for name in (
        'read_ops', 'write_ops', 'other_ops', 'read_bytes', 'write_bytes', 'other_bytes')]


class ExtendedLimit(ctypes.Structure):
    _fields_ = [('basic', BasicLimit), ('io', IoCounters), ('process_memory', ctypes.c_size_t),
                ('job_memory', ctypes.c_size_t), ('peak_process', ctypes.c_size_t), ('peak_job', ctypes.c_size_t)]


def limit_commit(amount):
    if sys.platform != 'win32':
        raise RuntimeError('이 작은 입력 측정의 커밋 메모리 제한은 Windows에서만 구현됐다')
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
    kernel.CreateJobObjectW.restype = wintypes.HANDLE
    kernel.SetInformationJobObject.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p, wintypes.DWORD]
    kernel.SetInformationJobObject.restype = wintypes.BOOL
    kernel.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
    kernel.AssignProcessToJobObject.restype = wintypes.BOOL
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    job = kernel.CreateJobObjectW(None, None)
    if not job:
        raise ctypes.WinError(ctypes.get_last_error())
    limits = ExtendedLimit()
    limits.basic.flags = 0x100 | 0x200 | 0x2000  # process and job commit limits; close terminates children
    limits.process_memory = amount
    limits.job_memory = amount
    if not kernel.SetInformationJobObject(job, 9, ctypes.byref(limits), ctypes.sizeof(limits)):
        raise ctypes.WinError(ctypes.get_last_error())
    if not kernel.AssignProcessToJobObject(job, kernel.GetCurrentProcess()):
        raise ctypes.WinError(ctypes.get_last_error())
    return job


def child(method, seconds):
    job = limit_commit(LIMIT)  # 핸들은 작업 종료까지 유지한다.
    from functools import partial
    import numpy as np
    from ai_service.pipelines import scoring
    from evaluation.dtw_baseline import process_peak_rss
    from evaluation.dtw_candidates import band_path, checkpoint_path

    target = {'reference': scoring._align, 'A': checkpoint_path,
              **{f'B{w}': partial(band_path, half_width_frames=round(w / scoring.HOP_S))
                 for w in (0.5, 1.0, 2.0)}}[method]
    began = time.perf_counter()
    target(np.array([0., 200.]), np.array([0., 200.]))
    warmup = time.perf_counter() - began
    frames = round(seconds / scoring.HOP_S)
    expected = np.array([0., 200., 500., 700., np.nan])[np.arange(frames) // 100 % 5]
    detected = expected.copy()
    began = time.perf_counter()
    path = target(expected, detected)
    elapsed = time.perf_counter() - began
    return {'status': 'DONE', 'seconds': elapsed, 'warmupSeconds': warmup,
            'peakRssBytes': process_peak_rss(), 'estimatedBytes': estimate_bytes(frames, frames),
            'expectedFrames': frames, 'detectedFrames': frames,
            'inputHash': hashlib.sha256(expected.tobytes()).hexdigest(),
            'pathHash': hashlib.sha256(path.tobytes()).hexdigest()}


def run_process(args, timeout):
    try:
        return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', timeout=timeout)
    except subprocess.TimeoutExpired:
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument('--out', type=Path)
    parser.add_argument('--repeat', type=int, choices=range(1, 6), default=5)
    parser.add_argument('--durations', nargs='+', type=int, choices=(2, 4, 8), default=[2, 4, 8])
    parser.add_argument('--child', nargs=2, metavar=('METHOD', 'SECONDS'))
    parser.add_argument('--guard-check', action='store_true')
    args = parser.parse_args()
    if args.guard_check:
        job = limit_commit(32 * 1024 ** 2)
        try:
            block = bytearray(128 * 1024 ** 2)
        except MemoryError:
            print('LIMIT_OK')
            return
        raise RuntimeError('메모리 제한 시험에서 할당이 거절되지 않았다')
    if args.child:
        print(json.dumps(child(args.child[0], int(args.child[1])), allow_nan=False))
        return
    if args.out is None:
        parser.error('--out is required')
    check = run_process([sys.executable, '-m', 'evaluation.dtw_performance', '--guard-check'], 10)
    if check is None or check.returncode != 0 or 'LIMIT_OK' not in check.stdout:
        raise RuntimeError('커밋 메모리 제한을 확인할 수 없어 측정을 시작하지 않는다')
    timeout_test = run_process([sys.executable, '-c', 'while True: pass'], 0.25)
    if timeout_test is not None:
        raise RuntimeError('시간 제한 시험이 실패해 측정을 시작하지 않는다')
    report = {'scope': 'small fixed f0, isolated DTW only', 'commitLimitBytes': LIMIT,
              'rssHardLimit': False, 'timeoutIncludesWarmupSeconds': 60, 'budgetBytes': BUDGET,
              'environment': {'python': platform.python_version(), 'platform': platform.platform(),
                'cpuCount': os.cpu_count(), 'threadEnvironment': {k: os.environ.get(k) for k in
                ('OPENBLAS_NUM_THREADS', 'OMP_NUM_THREADS', 'NUMBA_NUM_THREADS')},
                'codeHash': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'lockHash': hashlib.sha256((ROOT / 'ai/uv.lock').read_bytes()).hexdigest()}, 'runs': []}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    for seconds in sorted(set(args.durations)):
        for method in METHODS:
            for repeat in range(args.repeat):
                frames = seconds * 100
                if estimate_bytes(frames, frames) > BUDGET:
                    row = {'status': 'SKIPPED_BUDGET', 'seconds': None, 'peakRssBytes': None}
                else:
                    process = run_process([sys.executable, '-m', 'evaluation.dtw_performance', '--child', method, str(seconds)], 60)
                    if process is None:
                        row = {'status': 'TIMEOUT', 'seconds': None, 'peakRssBytes': None}
                    elif process.returncode:
                        row = {'status': 'ERROR', 'seconds': None, 'peakRssBytes': None, 'error': process.stderr[-1000:]}
                    else:
                        row = json.loads(process.stdout)
                row.update({'durationSeconds': seconds, 'method': method, 'repeat': repeat + 1})
                report['runs'].append(row)
                args.out.write_text(json.dumps(report, ensure_ascii=False, indent=1, allow_nan=False), encoding='utf-8')
    report['summary'] = {f'{s}:{m}': summarize([r for r in report['runs'] if r['durationSeconds'] == s and r['method'] == m])
                         for s in sorted(set(args.durations)) for m in METHODS}
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=1, allow_nan=False), encoding='utf-8')
    print(f"{args.out}: {len(report['runs'])} runs")


if __name__ == '__main__':
    main()
