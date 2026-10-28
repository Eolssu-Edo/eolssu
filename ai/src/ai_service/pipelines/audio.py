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
    """로컬 원본 파일을 16kHz·모노·PCM16(int16) 배열로 디코딩한다. 스테레오는 두 채널 평균이다.

    중간이 손상됐거나 끝이 잘린 파일, 오디오가 없는 파일은 DecodeError다. 손상 구간을 건너뛰면 뒤의
    소리가 앞당겨져 박자가 틀어지므로 디코딩 오류가 하나라도 나면 실패로 본다(-xerror).
    파일이 없거나 ffmpeg가 없거나 시간 안에 끝나지 않는 것은 녹음 문제가 아니므로 FileNotFoundError·
    TimeoutError다. 서명 URL 같은 입력 위치는 오류 메시지에 남기지 않는다.
    """
    source = Path(path)
    if not source.is_file():  # URL 문자열도 여기서 걸러 ffmpeg가 네트워크로 받지 않게 한다
        raise FileNotFoundError("녹음 원본 파일이 없다")
    cmd = [
        "ffmpeg", "-nostdin", "-v", "error", "-xerror", "-protocol_whitelist", "file", "-i", str(source),
        "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "s16le", "-",
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, timeout=timeout_s, check=False)
    except subprocess.TimeoutExpired:
        raise TimeoutError(f"녹음 디코딩이 {timeout_s}초 안에 끝나지 않았다") from None
    if result.returncode != 0 or not result.stdout:
        detail = result.stderr.decode(errors="replace").replace(str(source), "<input>").strip()[-500:]
        raise DecodeError(detail or "디코딩 결과가 비었다")
    return np.frombuffer(result.stdout, dtype="<i2").astype(np.int16)
