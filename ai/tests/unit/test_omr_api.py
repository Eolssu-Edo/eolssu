"""POST /v1/recognitions를 가짜 내려받기·가짜 엔진으로 검사한다(docs/specs/score-recognition.md).

인식 품질 검증이 아니다. 요청 검사, 쪽 이미지화, 시간 예산, 실패 코드, 응답 모양만 본다.
"""

import io
import itertools
import logging

import httpx
import pypdfium2 as pdfium
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from omr_service import api
from omr_service.api import DownloadError, RecognitionFailed, app, get_engines, get_fetcher

SIGNED_URL = "https://r2.example/sheets/1.pdf?X-Amz-Signature=secret-token"
A4 = (595, 842)  # pt
DONE_KEYS = {"conversionId", "status", "failReason", "engine", "engineVersion", "pages", "jgb", "score", "issues"}


def pdf(pages=1, size=A4):
    doc = pdfium.PdfDocument.new()
    for _ in range(pages):
        doc.new_page(*size)
    out = io.BytesIO()
    doc.save(out)
    return out.getvalue()


def image(fmt="PNG", size=(40, 30), orientation=None):
    out = io.BytesIO()
    exif = Image.Exif()
    if orientation:
        exif[0x0112] = orientation
    Image.new("RGB", size, "white").save(out, fmt, exif=exif)
    return out.getvalue()


def body(files=None, **override):
    files = [{"url": SIGNED_URL, "contentType": "application/pdf"}] if files is None else files
    return {"conversionId": "c-1", "notation": "STAFF", "files": files, "instrument": "DANSO", "jangdan": None,
            "hwangPitch": None} | override


def images(n, content_type="image/png"):
    return [{"url": f"{SIGNED_URL}&n={i}", "contentType": content_type} for i in range(n)]


class Engine:
    """받은 작업을 기록하고 정해진 결과를 돌려주거나 예외를 낸다."""

    def __init__(self, outcome=None):
        self.jobs = []
        self.outcome = outcome

    def __call__(self, job):
        self.jobs.append(job)
        # 임시 폴더는 응답 뒤 지워지므로 엔진이 받은 그 순간의 파일을 기록한다
        self.heads = [f.read_bytes()[:5] for f in job.files]
        self.sizes = [Image.open(p.path).size for p in job.pages]
        if isinstance(self.outcome, Exception):
            raise self.outcome
        return self.outcome or {"engine": "fake", "engineVersion": "1", "jgb": {"v": 1}, "score": {"events": []},
                                "issues": [{"kind": "LOW_CONFIDENCE", "eventId": "e0", "cellId": None, "page": 0,
                                            "bbox": [1, 2, 3, 4], "detail": None}]}


@pytest.fixture
def serve():
    """URL마다 정한 바이트를 돌려주는 가짜 내려받기와 가짜 엔진을 끼운 클라이언트."""

    def use(files, engine=None):
        def fetch(url, dest, max_bytes):
            data = files if not isinstance(files, dict) else files[url]
            if isinstance(data, Exception):
                raise data
            if len(data) > max_bytes:
                raise DownloadError("too large")
            dest.write_bytes(data)

        engine = engine or Engine()
        app.dependency_overrides[get_fetcher] = lambda: fetch
        app.dependency_overrides[get_engines] = lambda: {"STAFF": engine, "JEONGGANBO": engine}
        use.engine = engine
        return TestClient(app, raise_server_exceptions=False)

    yield use
    app.dependency_overrides.clear()


def failed(response, reason):
    assert response.status_code == 200
    assert response.json() == {"conversionId": "c-1", "status": "FAILED", "failReason": reason}


def test_pdf_is_rendered_per_page_and_engine_result_is_returned(serve):
    client = serve(pdf(pages=2))
    response = client.post("/v1/recognitions", json=body())
    assert response.status_code == 200
    out = response.json()
    assert set(out) == DONE_KEYS
    assert out["conversionId"] == "c-1" and out["status"] == "DONE" and out["failReason"] is None
    assert out["engine"] == "fake" and out["issues"][0]["kind"] == "LOW_CONFIDENCE"
    # 300dpi: A4 595×842pt → 2479.2×3508.3px, pdfium은 올림한다
    assert out["pages"] == [{"index": i, "width": 2480, "height": 3509} for i in range(2)]
    (job,) = serve.engine.jobs
    assert job.kind == "PDF" and len(job.files) == 1 and serve.engine.heads == [b"%PDF-"]
    assert serve.engine.sizes == [(2480, 3509)] * 2
    assert job.time_budget_s == 60  # 쪽당 30초
    assert job.request.notation == "STAFF"


def test_images_become_pages_in_order(serve):
    files = images(2)
    client = serve({files[0]["url"]: image(size=(40, 30)), files[1]["url"]: image(size=(50, 20))})
    out = client.post("/v1/recognitions", json=body(files)).json()
    assert out["status"] == "DONE"
    assert out["pages"] == [{"index": 0, "width": 40, "height": 30}, {"index": 1, "width": 50, "height": 20}]
    assert serve.engine.jobs[0].kind == "IMAGE" and serve.engine.jobs[0].time_budget_s == 60


def test_phone_photo_orientation_is_applied(serve):
    files = images(1, "image/jpeg")
    out = serve(image("JPEG", size=(40, 30), orientation=6)).post("/v1/recognitions", json=body(files)).json()
    assert out["pages"] == [{"index": 0, "width": 30, "height": 40}]
    assert serve.engine.sizes == [(30, 40)]


def test_jeongganbo_goes_to_its_engine(serve):
    staff, jg = Engine(), Engine()
    client = serve(pdf())
    app.dependency_overrides[get_engines] = lambda: {"STAFF": staff, "JEONGGANBO": jg}
    assert client.post("/v1/recognitions", json=body(notation="JEONGGANBO", jangdan="굿거리")).json()["status"] == "DONE"
    assert len(jg.jobs) == 1 and not staff.jobs


@pytest.mark.parametrize("case", ["too_many_pages", "wrong_type", "broken_pdf", "download_failed", "too_large",
                                  "too_many_pixels"])
def test_bad_files_fail_as_unsupported(serve, monkeypatch, case):
    request = body()
    data = {"too_many_pages": pdf(pages=11), "wrong_type": image(), "broken_pdf": b"%PDF-1.7 broken",
            "download_failed": DownloadError("status 403"), "too_large": pdf(), "too_many_pixels": pdf()}[case]
    if case == "too_large":
        monkeypatch.setattr(api, "MAX_TOTAL_BYTES", len(data) - 1)
    if case == "too_many_pixels":
        monkeypatch.setattr(api, "MAX_PIXELS", 2479 * 3508 - 1)  # 그리기 전 pt 기준 2479.2×3508.3
    client = serve(data)
    failed(client.post("/v1/recognitions", json=request), "UNSUPPORTED_FILE")
    assert not serve.engine.jobs


@pytest.mark.parametrize("data", [image("JPEG"), b"not an image", image()[:30]])
def test_image_must_match_declared_type(serve, data):
    failed(serve(data).post("/v1/recognitions", json=body(images(1))), "UNSUPPORTED_FILE")


def test_total_size_counts_all_images(serve, monkeypatch):
    files = images(2)
    one = image()
    monkeypatch.setattr(api, "MAX_TOTAL_BYTES", len(one) * 2 - 1)
    failed(serve({f["url"]: one for f in files}).post("/v1/recognitions", json=body(files)), "UNSUPPORTED_FILE")


@pytest.mark.parametrize("outcome, reason", [
    (TimeoutError(), "TIMEOUT"),
    (RecognitionFailed("NO_STAFF_FOUND"), "NO_STAFF_FOUND"),
    (RecognitionFailed("NO_GRID_FOUND"), "NO_GRID_FOUND"),
    (RuntimeError("boom"), "ENGINE_ERROR"),
])
def test_engine_failures_map_to_fail_reason(serve, outcome, reason):
    failed(serve(pdf(), Engine(outcome)).post("/v1/recognitions", json=body()), reason)


def test_engine_past_time_budget_is_timeout(serve, monkeypatch):
    clock = itertools.count(0.0, 31.0)  # 부를 때마다 31초가 지난다
    monkeypatch.setattr(api.time, "monotonic", lambda: next(clock))
    failed(serve(pdf()).post("/v1/recognitions", json=body()), "TIMEOUT")


def test_missing_engine_is_engine_error(serve):
    client = serve(pdf())
    app.dependency_overrides[get_engines] = lambda: {}
    failed(client.post("/v1/recognitions", json=body()), "ENGINE_ERROR")


@pytest.mark.parametrize("override", [
    {"files": images(11)},
    {"files": []},
    {"files": [{"url": SIGNED_URL, "contentType": "application/pdf"}] * 2},
    {"files": [{"url": SIGNED_URL, "contentType": "application/pdf"}, {"url": SIGNED_URL, "contentType": "image/png"}]},
    {"files": [{"url": SIGNED_URL, "contentType": "image/gif"}]},
    {"files": [{"url": "file:///etc/passwd", "contentType": "image/png"}]},
    {"notation": "JEONGGANBO", "jangdan": None},
    {"notation": "TAB"},
])
def test_malformed_request_is_422_without_signed_url(serve, override):
    response = serve(pdf()).post("/v1/recognitions", json=body(**override))
    assert response.status_code == 422
    assert "secret-token" not in response.text
    assert not serve.engine.jobs


def test_signed_url_is_not_logged(serve, caplog):
    with caplog.at_level(logging.INFO):
        serve(DownloadError("x")).post("/v1/recognitions", json=body())
        serve(pdf()).post("/v1/recognitions", json=body())
    assert "secret-token" not in caplog.text
    assert "recognition conversion=c-1" in caplog.text


def test_download_stops_at_size_limit(tmp_path):
    transport = httpx.MockTransport(lambda _: httpx.Response(200, content=b"x" * 11))
    with pytest.raises(DownloadError) as error:
        api.download(SIGNED_URL, tmp_path / "f", max_bytes=10, transport=transport)
    assert "secret-token" not in str(error.value)
    api.download(SIGNED_URL, tmp_path / "f", max_bytes=11, transport=transport)
    assert (tmp_path / "f").read_bytes() == b"x" * 11


def test_download_rejects_non_200(tmp_path):
    transport = httpx.MockTransport(lambda _: httpx.Response(403))
    with pytest.raises(DownloadError):
        api.download(SIGNED_URL, tmp_path / "f", max_bytes=10, transport=transport)


def test_health():
    assert TestClient(app).get("/health").json() == {"status": "ok"}
