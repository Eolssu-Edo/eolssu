"""backend 변환 작업자가 부르는 악보 인식 API(docs/specs/score-recognition.md).

`POST /v1/recognitions`는 서명 URL의 PDF·이미지를 내려받아 쪽 그림으로 만들고, 기보법에 맞는 엔진의 초안을
같은 응답으로 돌려준다. 서명 URL은 원본 접근 토큰이라 로그·오류 응답에 남기지 않는다(ai_service.api와 같은 방식).
요청 형식 오류(422) 말고는 어떤 실패도 200과 계약의 실패 코드로 돌려준다.
"""

import logging
import tempfile
import threading
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path
from time import monotonic
from typing import Annotated, Literal

import httpx
import pypdfium2 as pdfium
from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from PIL import Image, ImageOps
from pydantic import BaseModel, ConfigDict, Field, model_validator
from pydantic.alias_generators import to_camel

MAX_TOTAL_BYTES = 20 * 1024 * 1024  # 파일·합계 20MiB(382)
MAX_PDF_PAGES = 10
MAX_IMAGES = 10
# 요청 전체(내려받기·쪽 그림·엔진) 시간 = 쪽당 30초 × 쪽 수. 쪽 수를 알기 전인 내려받기는 최대 10쪽의 300초까지 쓴다.
PAGE_TIME_S = 30
MAX_TIME_S = PAGE_TIME_S * MAX_PDF_PAGES
# 내려받기의 읽기 한 번 제한. 마감은 조각 사이에서 보므로, 조금씩 흘려 보내는 서버에서는 이만큼 넘길 수 있다.
READ_TIMEOUT_S = 10.0
DPI = 300  # 실측(score-omr-feasibility.md)과 Audiveris 권장 해상도
# 쪽 그림 하나의 최대 화소 수. Pillow가 압축 폭탄으로 경고하는 값과 같다(약 8,950만, A4 300dpi의 10배).
MAX_PIXELS = Image.MAX_IMAGE_PIXELS
# 선언한 형식별로 받는 실제 형식. 여러 그림이 든 폰 사진 JPEG(삼성·iPhone HDR)는 Pillow가 MPO로 연다.
FORMATS = {"image/png": {"PNG"}, "image/jpeg": {"JPEG", "MPO"}}
ENGINE_FAIL_REASONS = {"NO_GRID_FOUND", "NO_STAFF_FOUND"}  # 엔진이 낼 수 있는 계약의 실패 코드
# PDFium은 스레드 안전하지 않다. 동기 엔드포인트는 요청마다 다른 스레드에서 돌아 겹치면 프로세스가 죽을 수 있다.
PDFIUM_LOCK = threading.Lock()

log = logging.getLogger(__name__)
log.setLevel(logging.INFO)
log.propagate = False
if not log.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(levelname)s:     %(message)s"))
    log.addHandler(_handler)
for _name in ("httpx", "httpcore"):  # INFO에서 서명이 든 URL 전체를 남긴다
    logging.getLogger(_name).setLevel(logging.WARNING)

app = FastAPI(title="얼쑤 악보 인식", docs_url=None, redoc_url=None, openapi_url=None)


class DownloadError(Exception):
    """원본을 받지 못했거나 크기 한도를 넘었다. 메시지에 URL을 넣지 않는다."""


class UnsupportedFile(Exception):
    """형식·쪽 수·화소 수가 계약 밖이거나 열 수 없는 파일이다."""


class RecognitionFailed(Exception):
    """엔진이 악보를 찾지 못했다. fail_reason은 계약의 실패 코드(ENGINE_FAIL_REASONS)다."""

    def __init__(self, fail_reason: str):
        super().__init__(fail_reason)
        self.fail_reason = fail_reason


class _Camel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")


class FileIn(_Camel):
    url: str = Field(pattern=r"^https?://")
    content_type: Literal["application/pdf", "image/png", "image/jpeg"]


class RecognitionIn(_Camel):
    conversion_id: str = Field(min_length=1, max_length=64)
    notation: Literal["JEONGGANBO", "STAFF"]
    files: list[FileIn] = Field(min_length=1, max_length=MAX_IMAGES)
    instrument: str
    jangdan: str | None = None
    hwang_pitch: int | None = Field(default=None, ge=0, le=127)  # MIDI 번호

    @model_validator(mode="after")
    def _check(self) -> "RecognitionIn":
        if any(f.content_type == "application/pdf" for f in self.files) and len(self.files) != 1:
            raise ValueError("a PDF must be the only file")
        if self.notation == "JEONGGANBO" and not self.jangdan:
            raise ValueError("jangdan is required for JEONGGANBO")
        return self


@dataclass(frozen=True)
class Page:
    path: Path  # 쪽 그림(PNG·JPEG). 응답의 bbox는 이 그림의 픽셀 좌표다.
    width: int
    height: int


@dataclass(frozen=True)
class Job:
    """엔진이 받는 입력. 엔진은 time_budget_s 안에 끝내고 넘으면 TimeoutError를 낸다.

    엔진이 files의 PDF를 PDFium(pypdfium2)으로 읽을 때도 PDFIUM_LOCK을 잡는다. 재진입이 안 되는 잠금이라
    한 스레드에서 두 번 잡으면 멈춘다.
    """

    request: RecognitionIn
    kind: Literal["PDF", "IMAGE"]
    files: list[Path]  # 받은 원본(글자 층 PDF 읽기용)
    pages: list[Page]
    time_budget_s: float


# 엔진은 {"engine", "engineVersion", "jgb", "score", "issues"}를 돌려준다.
Engine = Callable[[Job], dict]
Fetcher = Callable[[str, Path, int, float], None]


def download(url: str, dest: Path, max_bytes: int, deadline: float,
             transport: httpx.BaseTransport | None = None) -> None:
    """URL의 파일을 dest에 받는다. 실패·크기 초과는 URL 없는 DownloadError, 마감(monotonic) 초과는 TimeoutError다."""
    try:
        with httpx.Client(timeout=READ_TIMEOUT_S, transport=transport) as client, \
                client.stream("GET", url) as response, dest.open("wb") as out:
            if response.status_code != 200:
                raise DownloadError(f"status {response.status_code}")
            size = 0
            for chunk in response.iter_bytes():
                size += len(chunk)
                if size > max_bytes:
                    raise DownloadError("file exceeds size limit")
                if monotonic() > deadline:
                    raise TimeoutError()
                out.write(chunk)
    except httpx.TimeoutException:
        # 저장소가 늦은 것은 내려받기 실패다. 요청 시간 상한을 넘긴 때만 계약의 TIMEOUT이다.
        if monotonic() > deadline:
            raise TimeoutError() from None
        raise DownloadError("storage timed out") from None
    except (httpx.HTTPError, httpx.InvalidURL) as e:  # InvalidURL은 HTTPError 하위가 아니다
        raise DownloadError(type(e).__name__) from None  # httpx 메시지에는 URL이 들 수 있다


def get_fetcher() -> Fetcher:
    return download


def get_engines() -> dict[str, Engine]:
    return {}  # 정간보(U2·U3)·오선보(U5) 엔진을 연결하기 전이라 비어 있다


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.post("/v1/recognitions", response_model=None)
def recognize(
    request: RecognitionIn,
    fetch: Annotated[Fetcher, Depends(get_fetcher)],
    engines: Annotated[dict[str, Engine], Depends(get_engines)],
) -> dict:
    # 동기 함수라 FastAPI가 스레드 풀에서 돌린다. 동시 1건은 U6에서 정한다(PDFium만 잠금으로 막아 둠).
    began = monotonic()
    outcome, pages = "ENGINE_ERROR", []
    try:
        with tempfile.TemporaryDirectory(prefix="recognition-", ignore_cleanup_errors=True) as work:
            try:
                kind = "PDF" if request.files[0].content_type == "application/pdf" else "IMAGE"
                # 이미지는 장 수가 곧 쪽 수라 처음부터 예산을 안다. PDF는 열어 볼 때까지 최대 10쪽으로 본다.
                max_pages = MAX_PDF_PAGES if kind == "PDF" else len(request.files)
                files = _fetch_all(request, Path(work), fetch, began + PAGE_TIME_S * max_pages)
                # 이미지 열기는 짧아 따로 마감을 보지 않는다. 넘었으면 아래 엔진 예산에서 TIMEOUT이 된다.
                pages = _pdf_pages(files[0], Path(work), began) if kind == "PDF" else [
                    _image_page(f, t.content_type) for f, t in zip(files, request.files)]
            except (DownloadError, UnsupportedFile):
                outcome = "UNSUPPORTED_FILE"
                return _failed(request, outcome)
            engine = engines.get(request.notation)
            if engine is None:
                return _failed(request, outcome)
            budget = began + PAGE_TIME_S * len(pages) - monotonic()
            if budget <= 0:
                raise TimeoutError()
            started = monotonic()
            try:
                result = _done(request, engine(Job(request, kind, files, pages, budget)), pages)
            except RecognitionFailed as e:
                outcome = e.fail_reason if e.fail_reason in ENGINE_FAIL_REASONS else f"ENGINE_ERROR:{e.fail_reason}"
                return _failed(request, outcome.split(":")[0])
            if monotonic() - started > budget:
                raise TimeoutError()
        outcome = "DONE"
        return result
    except TimeoutError:
        outcome = "TIMEOUT"
        return _failed(request, outcome)
    except Exception as e:
        outcome = f"ENGINE_ERROR:{type(e).__name__}"  # 예외 메시지에는 경로가 들 수 있어 종류만 남긴다
        return _failed(request, "ENGINE_ERROR")
    finally:
        log.info("recognition conversion=%s notation=%s pages=%d result=%s ms=%d", request.conversion_id,
                 request.notation, len(pages), outcome, round((monotonic() - began) * 1000))


@app.exception_handler(RequestValidationError)
def _invalid_request(_: Request, error: RequestValidationError) -> JSONResponse:
    """FastAPI 기본 422는 입력값을 되돌려 서명 URL이 실린다. 위치와 종류만 돌려준다."""
    return JSONResponse({"errors": [{"loc": e["loc"], "type": e["type"]} for e in error.errors()]}, status_code=422)


def _failed(request: RecognitionIn, reason: str) -> dict:
    return {"conversionId": request.conversion_id, "status": "FAILED", "failReason": reason}


def _done(request: RecognitionIn, result: dict, pages: list[Page]) -> dict:
    """엔진 결과를 응답으로 만든다. 필드가 빠진 결과는 KeyError로 ENGINE_ERROR가 된다."""
    return {
        "conversionId": request.conversion_id,
        "status": "DONE",
        "failReason": None,
        "engine": result["engine"],
        "engineVersion": result["engineVersion"],
        "pages": [{"index": i, "width": p.width, "height": p.height} for i, p in enumerate(pages)],
        "jgb": result["jgb"],
        "score": result["score"],
        "issues": result["issues"],
    }


def _fetch_all(request: RecognitionIn, work: Path, fetch: Fetcher, deadline: float) -> list[Path]:
    remaining, paths = MAX_TOTAL_BYTES, []
    for i, f in enumerate(request.files):
        path = work / f"source-{i}"
        fetch(f.url, path, remaining, deadline)
        remaining -= path.stat().st_size
        paths.append(path)
    return paths


def _pdf_pages(path: Path, work: Path, began: float) -> list[Page]:
    """PDF를 쪽 그림으로 만든다. 잠금 대기와 쪽마다 그리기도 요청 시간(began부터)에 넣는다."""
    scale = DPI / 72
    remaining = began + MAX_TIME_S - monotonic()
    if remaining <= 0 or not PDFIUM_LOCK.acquire(timeout=remaining):
        raise TimeoutError()
    try:
        try:
            doc = pdfium.PdfDocument(path)  # 머리말 앞에 다른 바이트가 있어도 PDFium이 판단한다
        except pdfium.PdfiumError:
            raise UnsupportedFile("unreadable PDF") from None
        with doc:
            if not 1 <= len(doc) <= MAX_PDF_PAGES:
                raise UnsupportedFile("page count")
            deadline, pages = began + PAGE_TIME_S * len(doc), []
            for i, page in enumerate(doc):
                if monotonic() > deadline:
                    raise TimeoutError()
                w, h = page.get_size()
                if w * h * scale * scale > MAX_PIXELS:  # 그리기 전에 막는다
                    raise UnsupportedFile("page too large")
                try:
                    image = page.render(scale=scale).to_pil()
                except pdfium.PdfiumError:
                    raise UnsupportedFile("unrenderable page") from None
                out = work / f"page-{i}.png"
                image.save(out)
                pages.append(Page(out, *image.size))
            return pages
    finally:
        PDFIUM_LOCK.release()


def _image_page(path: Path, content_type: str) -> Page:
    try:
        with Image.open(path) as image:
            if image.format not in FORMATS[content_type]:
                raise UnsupportedFile("content type mismatch")
            if image.width * image.height > MAX_PIXELS:  # 머리말만 읽은 상태라 아직 풀지 않았다
                raise UnsupportedFile("image too large")
            image.load()
            if image.getexif().get(0x0112, 1) != 1:  # 폰 사진의 회전 정보. bbox가 보이는 방향과 맞게 돌려 둔다
                turned = ImageOps.exif_transpose(image)
                if turned.mode not in ("1", "L", "LA", "RGB", "RGBA"):  # CMYK 등은 PNG로 저장할 수 없다
                    turned = turned.convert("RGB")
                path = path.with_name(path.name + ".png")
                turned.save(path)
                return Page(path, turned.width, turned.height)
            return Page(path, image.width, image.height)
    except (OSError, Image.DecompressionBombError):  # UnidentifiedImageError·잘린 파일은 OSError다
        raise UnsupportedFile("unreadable image") from None
