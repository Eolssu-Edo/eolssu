"""곡·BPM별 격리 DTW와 합성 m4a 전체 처리 성능을 측정한다. Windows 전용."""
import argparse
import ctypes
from ctypes import wintypes
from functools import partial
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
import time
from unittest.mock import patch

from evaluation.dtw_performance import BUDGET, LIMIT, METHODS, ROOT, estimate_bytes, limit_commit, run_process, summarize

RSS_LIMIT = 768 * 1024 ** 2  # 계획의 1GiB보다 여유를 둔다.


def tempos(default):
    return sorted({40, round((40 + default) / 2), default})


def blocks_later_inputs(status):
    return status in ('ERROR', 'TIMEOUT', 'FAILED', 'PREPARE_ERROR')


def run_status(score_status):
    """채점이 DONE이 아니면(NO_SOUND·TOO_SHORT 등) 측정 성공으로 세지 않는다."""
    return 'DONE' if score_status == 'DONE' else 'FAILED'


def timed_process(args, timeout):
    """부모가 측정한 자식 생성부터 종료까지의 벽시계 시간."""
    started = time.perf_counter()
    process = run_process(args, timeout)
    return process, time.perf_counter() - started


def candidate_estimate(method, n, m):
    """정렬의 보수적 사전 추정. 모듈 준비·음높이 추정 메모리는 포함하지 않는다."""
    if method == 'reference':
        return estimate_bytes(n, m)
    if method == 'A':
        k = min(n, math.ceil(math.sqrt(8 * n)))
        storage = 8 * m * (math.ceil(n / k) + 1) + k * m
    else:
        width = round(float(method[1:]) / 0.01)
        storage = n * min(m, 2 * width + 2)
    return int(2 * (storage + 128 * (n + m) + 32 * (n + m)) + 32 * 1024 ** 2)


def limit_working_set(maximum):
    """Windows의 강제 상주 메모리 상한을 설정하고 설정값을 확인한다."""
    if sys.platform != 'win32':
        raise RuntimeError('상주 메모리 상한은 Windows에서만 구현됐다')
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    kernel.SetProcessWorkingSetSizeEx.argtypes = [wintypes.HANDLE, ctypes.c_size_t, ctypes.c_size_t, wintypes.DWORD]
    kernel.SetProcessWorkingSetSizeEx.restype = wintypes.BOOL
    kernel.GetProcessWorkingSetSizeEx.argtypes = [wintypes.HANDLE, ctypes.POINTER(ctypes.c_size_t),
                                                  ctypes.POINTER(ctypes.c_size_t), ctypes.POINTER(wintypes.DWORD)]
    kernel.GetProcessWorkingSetSizeEx.restype = wintypes.BOOL
    process = kernel.GetCurrentProcess()
    if not kernel.SetProcessWorkingSetSizeEx(process, 1024 ** 2, maximum, 4):
        raise ctypes.WinError(ctypes.get_last_error())
    minimum, actual, flags = ctypes.c_size_t(), ctypes.c_size_t(), wintypes.DWORD()
    if not kernel.GetProcessWorkingSetSizeEx(process, ctypes.byref(minimum), ctypes.byref(actual), ctypes.byref(flags)):
        raise ctypes.WinError(ctypes.get_last_error())
    if actual.value != maximum or not flags.value & 4:
        raise RuntimeError('상주 메모리 강제 상한을 확인할 수 없다')


def load_notes(page):
    sys.path.insert(0, str(ROOT / 'data/src/scores'))
    from score_data import convert_answer
    from evaluation.error_detection import score_notes
    data = convert_answer(f'ngc-standard-2022-p{page}')
    return data, score_notes(data, 0, 10000)


def frames_for(notes, bpm):
    return round((notes[-1].start_beat - notes[0].start_beat + notes[-1].beats) * 60 / bpm / 0.01)


def full_frames(data, notes, bpm):
    """첫 음부터 악보의 마지막 쉼까지 녹음 프레임 수."""
    end_tick = max(e['startTick'] + e['durationTick'] for e in data['events'])
    end_beat = end_tick / data['music']['ticksPerBeat']
    return round((end_beat - notes[0].start_beat) * 60 / bpm / 0.01)


def prepare_audio(page, bpm, output):
    """고정 합성음을 별도 프로세스에서 만든다. 팀원 음원과 실제 연주가 아니다."""
    job = limit_commit(LIMIT)
    limit_working_set(RSS_LIMIT)
    import numpy as np
    import soundfile as sf
    from evaluation.synthetic import SR, synth
    data, notes = load_notes(page)
    wav = output.with_suffix('.wav')
    audio = synth(notes, gap=0.04, bpm=bpm)
    target_samples = full_frames(data, notes, bpm) * round(SR * 0.01)
    audio = np.pad(audio, (0, max(0, target_samples - len(audio))))
    sf.write(wav, audio, SR, subtype='PCM_16')
    try:
        result = subprocess.run(['ffmpeg', '-nostdin', '-v', 'error', '-y', '-i', str(wav),
                                 '-c:a', 'aac', '-b:a', '128k', str(output)], capture_output=True, timeout=45)
        if result.returncode:
            raise RuntimeError('합성 m4a 생성 실패')
    finally:
        wav.unlink(missing_ok=True)
    return {'audioHash': hashlib.sha256(output.read_bytes()).hexdigest()}


def child(page, bpm, method, mode, audio_path):
    job = limit_commit(LIMIT)
    limit_working_set(RSS_LIMIT)
    import numpy as np
    from ai_service.pipelines import scoring
    from ai_service.pipelines.audio import SAMPLE_RATE, decode_recording
    from evaluation.dtw_baseline import process_peak_rss
    from evaluation.dtw_candidates import band_path, checkpoint_path
    from evaluation.dtw_quality import synth

    data, notes = load_notes(page)
    target = {'reference': scoring._align, 'A': checkpoint_path,
              **{f'B{w}': partial(band_path, half_width_frames=round(w / scoring.HOP_S))
                 for w in (0.5, 1.0, 2.0)}}[method]
    warm_start = time.perf_counter()
    target(np.array([0., 200.]), np.array([0., 200.]))
    if mode == 'm4a':
        scoring.pyin_f0(np.sin(np.arange(SAMPLE_RATE) * 2 * np.pi * 440 / SAMPLE_RATE), SAMPLE_RATE)
    warm_seconds = time.perf_counter() - warm_start
    began = time.perf_counter()
    if mode == 'm4a':
        audio = decode_recording(audio_path).astype(np.float32) / 32768.0
        f0, rms = scoring.pyin_f0(audio, SAMPLE_RATE), scoring.frame_rms(audio, SAMPLE_RATE)
    else:
        f0, rms = synth(notes, bpm, 'normal')
        padding = max(0, full_frames(data, notes, bpm) - len(f0))
        f0, rms = np.pad(f0, (0, padding), constant_values=np.nan), np.pad(rms, (0, padding))
    n, m = frames_for(notes, bpm), len(f0)
    estimate = candidate_estimate(method, n, m)
    if estimate > BUDGET:
        return {'status': 'SKIPPED_BUDGET', 'estimatedBytes': estimate, 'expectedFrames': n, 'detectedFrames': m,
                'seconds': None, 'peakRssBytes': process_peak_rss(), 'warmupSeconds': warm_seconds}
    alignment_seconds, path = None, None

    def align(expected, detected):
        nonlocal alignment_seconds, path
        started = time.perf_counter()
        path = target(expected, detected)
        alignment_seconds = time.perf_counter() - started
        return path

    with patch.object(scoring, '_align', align):
        result = scoring.score(f0, notes, bpm, scoring.HWANG_HZ, rms=rms)
    elapsed = time.perf_counter() - began
    peak = process_peak_rss()
    return {'status': run_status(result.status), 'failReason': result.fail_reason,
            'seconds': alignment_seconds if mode == 'dtw' else elapsed, 'alignmentSeconds': alignment_seconds,
            'warmupSeconds': warm_seconds, 'peakRssBytes': peak, 'rssMayBeLimited': peak >= RSS_LIMIT - 16 * 1024 ** 2,
            'estimatedBytes': estimate, 'expectedFrames': n, 'detectedFrames': m,
            'inputHash': hashlib.sha256(f0.tobytes()).hexdigest(), 'rmsHash': hashlib.sha256(rms.tobytes()).hexdigest(),
            'pathHash': None if path is None else hashlib.sha256(path.tobytes()).hexdigest(),
            'scoreTotal': result.total, 'source': data['provenance']['sourceId']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path)
    parser.add_argument('--pages', nargs='+', type=int, choices=(171, 172, 173, 174), default=[171, 172, 173, 174])
    parser.add_argument('--bpm', type=int, help='단일 BPM. 작은 실행 확인용')
    parser.add_argument('--repeat', type=int, choices=range(1, 6), default=5)
    parser.add_argument('--child', nargs=5, metavar=('PAGE', 'BPM', 'METHOD', 'MODE', 'AUDIO'))
    parser.add_argument('--prepare', nargs=3, metavar=('PAGE', 'BPM', 'AUDIO'))
    parser.add_argument('--check-rss', action='store_true')
    args = parser.parse_args()
    if args.check_rss:
        job = limit_commit(128 * 1024 ** 2)
        limit_working_set(32 * 1024 ** 2)
        block = bytearray(64 * 1024 ** 2)
        for i in range(0, len(block), 4096):
            block[i] = 1
        from evaluation.dtw_baseline import process_peak_rss
        peak = process_peak_rss()
        if peak > 33 * 1024 ** 2:
            raise RuntimeError(f'RSS 제한 시험 초과: {peak}')
        print(json.dumps({'rssLimit': 32 * 1024 ** 2, 'peakRss': peak}))
        return
    if args.prepare:
        page, bpm, audio = args.prepare
        print(json.dumps(prepare_audio(int(page), int(bpm), Path(audio))))
        return
    if args.child:
        page, bpm, method, mode, audio = args.child
        print(json.dumps(child(int(page), int(bpm), method, mode, Path(audio)), ensure_ascii=False, allow_nan=False))
        return
    if args.out is None:
        parser.error('--out is required')
    check = run_process([sys.executable, '-m', 'evaluation.dtw_performance', '--guard-check'], 10)
    rss_check = run_process([sys.executable, '-m', 'evaluation.dtw_full_performance', '--check-rss'], 20)
    timeout_check = run_process([sys.executable, '-c', 'while True: pass'], 0.25)
    if check is None or check.returncode or 'LIMIT_OK' not in check.stdout or rss_check is None or rss_check.returncode or timeout_check is not None:
        raise RuntimeError('메모리 또는 시간 제한을 확인하지 못해 측정을 시작하지 않는다')
    output_dir = ROOT / 'data/processed/dtw-286/u4-audio'
    output_dir.mkdir(parents=True, exist_ok=True)
    cases = []
    for page in args.pages:
        data, notes = load_notes(page)
        for bpm in ([args.bpm] if args.bpm else tempos(66 if page == 174 else 96)):
            cases.append((full_frames(data, notes, bpm), frames_for(notes, bpm), page, bpm))
    report = {'environment': {'python': platform.python_version(), 'platform': platform.platform(),
                'cpuCount': os.cpu_count(), 'threadEnvironment': {k: os.environ.get(k) for k in
                ('OPENBLAS_NUM_THREADS', 'OMP_NUM_THREADS', 'NUMBA_NUM_THREADS')},
                'codeHash': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'lockHash': hashlib.sha256((ROOT / 'ai/uv.lock').read_bytes()).hexdigest()},
              'commitLimitBytes': LIMIT, 'rssLimitBytes': RSS_LIMIT, 'rssCheck': json.loads(rss_check.stdout),
              'budgetBytes': BUDGET, 'timeoutIncludesWarmupSeconds': 60,
              'orchestratorMemoryLimited': False, 'combinedPeakMemoryMeasured': False,
              'preparations': [], 'runs': []}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    blocked = {}
    preparation_blocked = None
    for recorded_frames, frames, page, bpm in sorted(cases):
        audio = output_dir / f'p{page}-{bpm}.m4a'
        prior_prepare_failure = preparation_blocked
        if preparation_blocked is None:
            prepared, preparation_seconds = timed_process(
                [sys.executable, '-m', 'evaluation.dtw_full_performance', '--prepare', str(page), str(bpm), str(audio)], 60)
            if prepared is None or prepared.returncode:
                reason = 'TIMEOUT' if prepared is None else f'EXIT_{prepared.returncode}: {prepared.stderr[-500:]}'
                preparation_blocked = f'p{page} BPM {bpm}: {reason}'
                preparation_status, audio_hash = 'PREPARE_ERROR', None
            else:
                preparation_status = 'DONE'
                audio_hash = json.loads(prepared.stdout)['audioHash']
                reason = None
        else:
            preparation_status, audio_hash, preparation_seconds = 'SKIPPED_PRIOR_FAILURE', None, None
            reason = preparation_blocked
        report['preparations'].append({'page': page, 'bpm': bpm, 'status': preparation_status,
                                       'processWallSeconds': preparation_seconds, 'reason': reason, 'audioHash': audio_hash})
        for mode in ('dtw', 'm4a'):
            for method in METHODS:
                for repeat in range(args.repeat):
                    estimate = candidate_estimate(method, frames, recorded_frames + (20 if mode == 'm4a' else 0))
                    key = (mode, method)
                    if prior_prepare_failure is not None:
                        row = {'status': 'SKIPPED_PRIOR_FAILURE', 'seconds': None, 'peakRssBytes': None,
                               'reason': prior_prepare_failure}
                    elif key in blocked:
                        row = {'status': 'SKIPPED_PRIOR_FAILURE', 'seconds': None, 'peakRssBytes': None,
                               'reason': blocked[key]}
                    elif mode == 'm4a' and audio_hash is None:
                        row = {'status': 'PREPARE_ERROR', 'seconds': None, 'peakRssBytes': None, 'reason': reason}
                    elif estimate > BUDGET:
                        row = {'status': 'SKIPPED_BUDGET', 'seconds': None, 'peakRssBytes': None,
                               'estimatedBytes': estimate, 'expectedFrames': frames,
                               'detectedFramesEstimate': recorded_frames + (20 if mode == 'm4a' else 0)}
                    else:
                        process, process_seconds = timed_process(
                            [sys.executable, '-m', 'evaluation.dtw_full_performance', '--child',
                             str(page), str(bpm), method, mode, str(audio)], 60)
                        if process is None:
                            row = {'status': 'TIMEOUT', 'seconds': None, 'peakRssBytes': None}
                        elif process.returncode:
                            row = {'status': 'ERROR', 'seconds': None, 'peakRssBytes': None, 'error': process.stderr[-1000:]}
                        else:
                            row = json.loads(process.stdout)
                        row['processWallSeconds'] = process_seconds
                    if blocks_later_inputs(row['status']):
                        blocked[key] = f"p{page} BPM {bpm}: {row['status']}"
                    row.update({'page': page, 'bpm': bpm, 'method': method, 'mode': mode,
                                'repeat': repeat + 1, 'audioHash': audio_hash if mode == 'm4a' else None})
                    report['runs'].append(row)
                    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=1, allow_nan=False), encoding='utf-8')
    report['summary'] = {f'{page}:{bpm}:{mode}:{method}': summarize([r for r in report['runs'] if
                         (r['page'], r['bpm'], r['mode'], r['method']) == (page, bpm, mode, method)])
                         for _, _, page, bpm in cases for mode in ('dtw', 'm4a') for method in METHODS}
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=1, allow_nan=False), encoding='utf-8')
    print(f"{args.out}: {len(report['runs'])} runs")


if __name__ == '__main__':
    main()
