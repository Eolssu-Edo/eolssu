"""백엔드가 부르는 분석 API(PRA-03 '분석 요청과 상태 조회 계약').

`POST /v1/analyses`는 서명 URL의 녹음을 내려받아 디코딩·음높이 추정·채점한 결과를 같은 응답으로 돌려준다.
서명 URL은 녹음 접근 토큰이라 로그·오류 응답에 남기지 않는다. 예외 메시지에도 URL·경로가 들 수 있어
예외는 종류만 남긴다.
"""

import logging
import tempfile
import time
from collections.abc import Callable
from pathlib import Path
from typing import Annotated, Literal

import httpx
import numpy as np
from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel

from ai_service.pipelines.audio import SAMPLE_RATE, DecodeError, decode_recording
from ai_service.pipelines.scoring import Note, ScoreResult, frame_rms, pyin_f0, score

MODEL_VERSION = "pyin-1"  # evaluations.model_version(30자 이하). 음높이 추정·채점 방식을 바꾸면 올린다
# 요청에 기준음이 없어 E♭4로 둔다. 기기 차이는 채점의 조율 보정(±100센트)이 흡수한다.
# ponytail: 기본음 녹음·공통 악보 기준음 반영은 S15P31E102-157.
REFERENCE_HZ = 311.13
MAX_DOWNLOAD_BYTES = 25 * 1024 * 1024  # PRA-02 원본 상한 20MiB, Nginx·multipart 한도 25MiB
DOWNLOAD_TIMEOUT_S = 30.0

log = logging.getLogger(__name__)
# uvicorn은 자기 로거만 설정하므로 시도별 한 줄이 보이게 따로 출력한다.
log.setLevel(logging.INFO)
if not log.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(levelname)s:     %(message)s"))
    log.addHandler(_handler)
# httpx는 INFO에서 요청 URL 전체(서명 포함)를 남긴다. 어떤 로그 설정에서도 나오지 않게 막는다.
for _name in ("httpx", "httpcore"):
    logging.getLogger(_name).setLevel(logging.WARNING)

app =FastAPI(title="얼쑤 연주 분석", docs_url=None, redoc_url=None, openapi_url=None)


class DownloadError(Exception):
    """녹음을 내려받지 못했다. 녹음 탓이 아니므로 실패 코드가 아니라 502다. 메시지에 URL을 넣지 않는다."""


class _Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")


class NoteIn(_Camel):
    idx: int = Field(ge=0)
    semitone: int  # pitches.semitone. 0 = 옥타브 0의 황(E♭4)
    # 곡 처음부터 센 시작 박(startTick ÷ ticksPerBeat). 쉼이 있으면 앞 음의 beats보다 더 벌어진다.
    # 채점은 아직 beats를 이어 붙인 시각을 쓴다. startBeat로 기대 시각을 계산하는 일은 S15P31E102-157.
    start_beat: float = Field(ge=0)
    beats: float = Field(gt=0)
    sigimsae: bool


class AnalysisIn(_Camel):
    session_id: int
    attempt: int = Field(ge=1)
    audio_url: str = Field(pattern=r"^https?://")
    bpm: int = Field(gt=0)
    bpm_unit: Literal["QUARTER", "DOTTED_QUARTER", "JEONGGAN", "JANGDAN_BEAT"]  # 채점은 beats·bpm만 쓴다
    notes: list[NoteIn] = Field(min_length=1)

    @field_validator("notes")
    @classmethod
    def _start_beats_increase(cls, notes: list[NoteIn]) -> list[NoteIn]:
        if any(b.start_beat <= a.start_beat for a, b in zip(notes, notes[1:])):
            raise ValueError("startBeat must increase in note order")
        return notes


def download(url: str, dest: Path) -> None:
    """URL의 녹음을 dest에 받는다. 실패·크기 초과는 URL 없는 DownloadError다."""
    try:
        with httpx.stream("GET", url, timeout=DOWNLOAD_TIMEOUT_S) as response, dest.open("wb") as out:
            if response.status_code != 200:
                raise DownloadError(f"status {response.status_code}")
            size = 0
            for chunk in response.iter_bytes():
                size += len(chunk)
                if size > MAX_DOWNLOAD_BYTES:
                    raise DownloadError("recording exceeds download limit")
                out.write(chunk)
    except httpx.HTTPError as e:
        raise DownloadError(type(e).__name__) from None  # httpx 메시지에는 URL이 들어 있다


def get_fetcher() -> Callable[[str, Path], None]:
    return download


@app.post("/v1/analyses", response_model=None)
def analyze(
    request: AnalysisIn, fetch: Annotated[Callable[[str, Path], None], Depends(get_fetcher)]
) -> dict | JSONResponse:
    # 동기 함수라 FastAPI가 스레드 풀에서 돌린다. 채점은 CPU 작업이다.
    began = time.monotonic()
    outcome = "ERROR"
    try:
        with tempfile.TemporaryDirectory(prefix="analysis-") as work:
            recording = Path(work) / "recording"
            try:
                fetch(request.audio_url, recording)
            except DownloadError:
                outcome = "DOWNLOAD_FAILED"
                return JSONResponse({"error": "DOWNLOAD_FAILED"}, status_code=502)
            try:
                result = _score_recording(recording, request)
            except DecodeError:
                result = ScoreResult("FAILED", DecodeError.fail_reason)
        outcome = result.fail_reason or result.status
        return _response(result, [n.idx for n in request.notes])
    except Exception as e:
        outcome = f"ERROR:{type(e).__name__}"
        return JSONResponse({"error": "ANALYSIS_ERROR"}, status_code=500)
    finally:
        log.info(
            "analysis session=%d attempt=%d result=%s ms=%d",
            request.session_id, request.attempt, outcome, round((time.monotonic() - began) * 1000),
        )


@app.exception_handler(RequestValidationError)
def _invalid_request(_: Request, error: RequestValidationError) -> JSONResponse:
    """FastAPI 기본 422는 입력값을 되돌려 서명 URL이 실린다. 위치와 종류만 돌려준다."""
    return JSONResponse({"errors": [{"loc": e["loc"], "type": e["type"]} for e in error.errors()]}, status_code=422)


def _score_recording(path: Path, request: AnalysisIn) -> ScoreResult:
    audio = decode_recording(path).astype(np.float32) / 32768.0
    notes = [Note(n.semitone, n.beats, n.sigimsae) for n in request.notes]
    f0 = pyin_f0(audio, SAMPLE_RATE)
    return score(f0, notes, request.bpm, REFERENCE_HZ, rms=frame_rms(audio, SAMPLE_RATE))


def _response(result: ScoreResult, idx: list[int]) -> dict:
    if result.status == "FAILED":
        return {"status": "FAILED", "failReason": result.fail_reason}
    return {
        "status": "DONE",
        "modelVersion": MODEL_VERSION,
        "total": result.total,
        "pitch": result.pitch,
        "timing": result.timing,
        "duration": result.duration,
        "tuningOffsetCent": result.tuning_offset_cent,
        "tuningOutOfRange": bool(result.tuning_out_of_range),
        # score()의 idx는 요청 음표의 순번이다. 요청이 보낸 악보 기준 idx로 돌려준다.
        "notes": [
            {
                "idx": idx[n.idx],
                "verdict": n.verdict,
                "pitchErrorCent": n.pitch_error_cent,
                "onsetErrorMs": n.onset_error_ms,
                "durationErrorMs": n.duration_error_ms,
            }
            for n in result.notes
        ],
    }
