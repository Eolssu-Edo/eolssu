"""녹음 원본을 채점 입력으로 디코딩한다(PRA-02 '분석용 변환').

원본(m4a·AAC)을 ffmpeg로 16kHz·모노·PCM16으로 바꿔 메모리 배열로만 돌려준다. 파일로 남기지 않는다.
"""

import json
import subprocess
from pathlib import Path

import numpy as np

SAMPLE_RATE = 16000  # 채점 입력 샘플레이트(Hz)
LENGTH_TOLERANCE_S = 0.2  # 디코딩 길이가 컨테이너 길이보다 이만큼 넘게 짧으면 잘린 파일. AAC 앞부분 지연은 0.1초 미만


class DecodeError(Exception):
    """원본을 읽지 못했다. 분석 결과는 FAILED, fail_reason DECODE_ERROR다."""

    fail_reason = "DECODE_ERROR"


def decode_recording(path: str | Path, timeout_s: float = 60.0) -> np.ndarray:
    """로컬 원본 파일을 16kHz·모노·PCM16(int16) 배열로 디코딩한다. 스테레오는 두 채널 평균이다.

    중간이 손상됐거나 끝이 잘린 파일, 오디오가 없는 파일은 DecodeError다. 손상 구간을 건너뛰면 뒤의
    소리가 앞당겨져 박자가 틀어지므로 디코딩 오류가 하나라도 나면 실패로 본다(-xerror). 패킷 경계에서
    잘린 파일은 오류 없이 일찍 끝나므로 디코딩 길이가 컨테이너에 적힌 길이보다 짧은지도 본다.
    파일이 없거나 ffmpeg가 없거나 시간 안에 끝나지 않는 것은 녹음 문제가 아니므로 FileNotFoundError·
    TimeoutError다. 서명 URL 같은 입력 위치는 오류 메시지에 남기지 않는다.
    """
    given = Path(path)
    if not given.is_file():  # URL 문자열도 여기서 걸러 ffmpeg가 네트워크로 받지 않게 한다
        raise FileNotFoundError("녹음 원본 파일이 없다")
    source = given.resolve()  # 상대 경로의 ':'를 ffmpeg가 프로토콜 이름으로 읽지 않게 한다
    hidden = (str(source), str(given))

    probe = _run(
        ["ffprobe", "-v", "error", "-protocol_whitelist", "file", "-select_streams", "a:0",
         "-show_entries", "stream=duration:format=duration", "-of", "json", str(source)],
        timeout_s,
    )
    decoded = _run(
        ["ffmpeg", "-nostdin", "-v", "error", "-xerror", "-protocol_whitelist", "file", "-i", str(source),
         "-vn", "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "s16le", "-"],
        timeout_s,
    )
    if decoded.returncode != 0 or not decoded.stdout:
        raise DecodeError(_masked(decoded.stderr, hidden) or "디코딩 결과가 비었다")
    pcm = np.frombuffer(decoded.stdout, dtype="<i2").astype(np.int16)

    expected_s = _container_seconds(probe.stdout) if probe.returncode == 0 else None
    if expected_s is not None and len(pcm) / SAMPLE_RATE < expected_s - LENGTH_TOLERANCE_S:
        raise DecodeError(f"디코딩 길이 {len(pcm) / SAMPLE_RATE:.2f}초가 컨테이너 길이 {expected_s:.2f}초보다 짧다")
    return pcm


def _run(cmd: list[str], timeout_s: float) -> subprocess.CompletedProcess:
    """명령을 실행한다. 시간 초과는 명령줄(입력 경로 포함)을 담지 않은 TimeoutError로 바꾼다."""
    try:
        return subprocess.run(cmd, capture_output=True, timeout=timeout_s, check=False)
    except subprocess.TimeoutExpired:
        pass
    # except 밖에서 올려 TimeoutExpired가 예외 연결(__context__)에 남지 않게 한다
    raise TimeoutError(f"녹음 디코딩이 {timeout_s}초 안에 끝나지 않았다")


def _container_seconds(probe_json: bytes) -> float | None:
    """ffprobe가 읽은 오디오 스트림(없으면 파일) 길이. 길이를 적지 않은 형식이면 None."""
    info = json.loads(probe_json or b"{}")
    for value in [s.get("duration") for s in info.get("streams", [])] + [info.get("format", {}).get("duration")]:
        try:
            return float(value)
        except (TypeError, ValueError):
            continue
    return None


def _masked(stderr: bytes, hidden: tuple[str, ...]) -> str:
    text = stderr.decode(errors="replace")
    for location in hidden:
        text = text.replace(location, "<input>")
    return text.strip()[-500:]
