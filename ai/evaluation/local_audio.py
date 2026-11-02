"""로컬 비교용으로 녹음을 제한된 크기의 임시 PCM으로 디코딩한다."""

from pathlib import Path
import subprocess
import tempfile
import threading

import numpy as np

from ai_service.api import RecordingTooLong
from ai_service.pipelines.audio import (
    DecodeError, LENGTH_TOLERANCE_S, SAMPLE_RATE, _container_seconds, _masked, _run,
)


def decode_window(path, start_sample, max_samples, timeout_s=60.0):
    """시작 전 PCM은 버리고, 선택 구간은 크기를 제한해 임시 파일에 둔다."""
    given = Path(path)
    if not given.is_file():
        raise FileNotFoundError('녹음 원본 파일이 없다')
    if start_sample < 0 or max_samples < 1 or start_sample + max_samples > (2**63 - 3) // 2:
        raise ValueError('시작 위치 또는 녹음 길이 제한이 올바르지 않습니다')
    source = given.resolve()
    probe = _run(
        ['ffprobe', '-v', 'error', '-protocol_whitelist', 'file', '-select_streams', 'a:0',
         '-show_entries', 'stream=duration:format=duration', '-of', 'json', str(source)], timeout_s,
    )
    start_bytes, limit_bytes = 2 * start_sample, 2 * max_samples
    with tempfile.TemporaryFile() as raw, tempfile.TemporaryFile() as errors:
        cmd = ['ffmpeg', '-nostdin', '-v', 'error', '-xerror', '-protocol_whitelist', 'file', '-i', str(source),
               '-vn', '-ac', '1', '-ar', str(SAMPLE_RATE), '-f', 's16le', '-']
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=errors)
        state = {'total': 0, 'kept': 0, 'overflow': False, 'error': None}

        def consume():
            try:
                while chunk := process.stdout.read(65536):
                    before = state['total']
                    state['total'] += len(chunk)
                    chosen = chunk[max(0, start_bytes - before):]
                    if state['kept'] + len(chosen) > limit_bytes:
                        state['overflow'] = True
                        try:
                            process.kill()
                        except OSError:
                            pass
                        return
                    raw.write(chosen)
                    state['kept'] += len(chosen)
            except OSError as exc:
                state['error'] = exc
                try:
                    process.kill()
                except OSError:
                    pass
            finally:
                process.stdout.close()

        worker = threading.Thread(target=consume, daemon=True)
        worker.start()
        try:
            process.wait(timeout=timeout_s)
        except subprocess.TimeoutExpired:
            try:
                process.kill()
            except OSError:
                pass
            process.wait()
            worker.join()
            raise TimeoutError(f'녹음 디코딩이 {timeout_s}초 안에 끝나지 않았다') from None
        worker.join()
        if state['error'] is not None:
            raise state['error']
        if state['overflow']:
            raise RecordingTooLong('악보와 녹음이 분석 메모리 한도를 넘습니다')
        errors.seek(0)
        if process.returncode != 0 or not state['total']:
            raise DecodeError(_masked(errors.read(), (str(source), str(given))) or '디코딩 결과가 비었다')
        total_samples = state['total'] // 2
        expected_s = _container_seconds(probe.stdout) if probe.returncode == 0 else None
        if expected_s is not None and total_samples / SAMPLE_RATE < expected_s - LENGTH_TOLERANCE_S:
            raise DecodeError('디코딩 길이가 녹음 파일의 길이보다 짧습니다')
        if start_sample >= total_samples:
            raise ValueError('시작 위치가 녹음 길이 밖입니다')
        raw.seek(0)
        return np.frombuffer(raw.read(), dtype='<i2').astype(np.int16)
