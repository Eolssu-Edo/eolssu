"""녹음 원본을 채점 입력으로 디코딩한다(PRA-02 '분석용 변환').

원본(m4a·AAC)을 ffmpeg로 16kHz·모노·PCM16으로 바꿔 메모리 배열로만 돌려준다. 파일로 남기지 않는다.
"""

import subprocess
from pathlib import Path

import numpy as np

SAMPLE_RATE = 16000  # 채점 입력 샘플레이트(Hz)


class DecodeError(Exception):
    """원본을 읽지 못했다. 분석 결과는 FAILED, fail_reason DECODE_ERROR다."""

    fail_reason = "DECODE_ERROR"


def decode_recording(path: str | Path, timeout_s: float = 60.0) -> np.ndarray:
    """원본 파일을 16kHz·모노·PCM16(int16) 배열로 디코딩한다. 스테레오는 두 채널 평균이다.

    손상됐거나 오디오가 없는 파일은 DecodeError. ffmpeg가 없거나 시간 안에 끝나지 않으면
    파일 문제가 아니므로 FileNotFoundError·subprocess.TimeoutExpired를 그대로 올린다.
    """
    cmd = [
        "ffmpeg", "-nostdin", "-v", "error", "-i", str(path),
        "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "s16le", "-acodec", "pcm_s16le", "-",
    ]
    result = subprocess.run(cmd, capture_output=True, timeout=timeout_s, check=False)
    if result.returncode != 0 or not result.stdout:
        raise DecodeError(result.stderr.decode(errors="replace").strip()[-500:] or "디코딩 결과가 비었다")
    return np.frombuffer(result.stdout, dtype="<i2").astype(np.int16)
