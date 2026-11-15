"""배포 직후 채점 경로를 한 번 실행해 numba 컴파일 결과를 만들고, 실행 환경을 확인한다(S15P31E102-244).

`python -m ai_service.warmup`은 합성 사인파 WAV를 임시 폴더에 쓰고 실제 서비스와 같은 순서로
ffmpeg 디코딩 → pYIN → 채점을 한다. librosa(numba)는 첫 호출 때 컴파일 결과를 NUMBA_CACHE_DIR에
저장하므로, 운영 컨테이너 안에서 실행하면 첫 사용자 요청이 컴파일 시간을 떠안지 않는다.
실행 사용자·읽기 전용 파일 시스템·ffmpeg 누락 같은 문제도 여기서 드러난다.
녹음이나 네트워크를 쓰지 않고, 결과는 JSON 한 줄로 출력한다. 실패하면 종료 코드 1이다.
"""

import json
import sys
import tempfile
import time
import wave
from pathlib import Path

import numpy as np

from ai_service.pipelines.audio import SAMPLE_RATE, decode_recording
from ai_service.pipelines.scoring import HWANG_HZ, Note, frame_rms, pyin_f0, score

SECONDS = 2.0
BPM = 60
NOTES = [Note(0, SECONDS * BPM / 60)]  # 황(반음 0) 한 음을 녹음 전체 길이만큼


def _write_tone(path: Path) -> None:
    t = np.arange(round(SAMPLE_RATE * SECONDS)) / SAMPLE_RATE
    samples = (0.3 * 32767 * np.sin(2 * np.pi * HWANG_HZ * t)).astype("<i2")
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(SAMPLE_RATE)
        out.writeframes(samples.tobytes())


def run() -> dict:
    began = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="warmup-") as work:
        recording = Path(work) / "tone.wav"
        _write_tone(recording)
        audio = decode_recording(recording).astype(np.float32) / 32768.0
    result = score(pyin_f0(audio, SAMPLE_RATE), NOTES, BPM, HWANG_HZ, rms=frame_rms(audio, SAMPLE_RATE))
    verdicts = [n.verdict for n in result.notes]
    return {
        "status": "ok" if result.status == "DONE" and verdicts == ["CORRECT"] else "unexpected",
        "result": result.status,
        "verdicts": verdicts,
        "ms": round((time.monotonic() - began) * 1000),
    }


def main() -> int:
    try:
        report = run()
    except Exception as e:  # 실행 환경 문제를 종류만 남긴다(경로가 메시지에 들 수 있다)
        report = {"status": "error", "error": type(e).__name__}
    print(json.dumps(report), flush=True)
    return 0 if report["status"] == "ok" else 1


if __name__ == "__main__":
    sys.exit(main())
