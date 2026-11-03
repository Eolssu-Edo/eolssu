"""POST /v1/recognitions를 가짜 내려받기·가짜 엔진으로 검사한다(docs/specs/score-recognition.md).

인식 품질 검증이 아니다. 요청 검사, 쪽 이미지화, 시간 예산, 실패 코드, 응답 모양만 본다.
"""

import io
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


def image(fmt="PNG", size=(40, 30), orientation=None, mode="RGB"):
    out = io.BytesIO()
    exif = Image.Exif()
    if orientation:
        exif[0x0112] = orientation
    # MPO는 빈 exif를 함께 주면 Pillow가 MP 머리말 없이 보통 JPEG로 쓴다
    extra = {"save_all": True, "append_images": [Image.new(mode, size)]} if fmt == "MPO" else {"exif": exif}
    Image.new(mode, size, "white").save(out, fmt, **extra)
    return out.getvalue()


def body(files=None, **override):
    files = [{"url": SIGNED_URL, "contentType": "application/pdf"}] if files is None else files
    return {"conversionId": "c-1", "notation": "STAFF", "files": files, "instrument": "DANSO", "jangdan": None,
            "hwangPitch": None} | override


def images(n, content_type="image/png"):
    return [{"url": f"{SIGNED_URL}&n={i}", "contentType": content_type} for i in range(n)]


class Engine:
    """받은 작업을 기록하고 정해진 결과를 돌려주거나 예외를 낸다."""

    def __init__(self, outcome=None, clock=None, takes_s=0.0):
        self.jobs = []
        self.outcome = outcome
        self.clock, self.takes_s = clock, takes_s

    def __call__(self, job):
        self.jobs.append(job)
        # 임시 폴더는 응답 뒤 지워지므로 엔진이 받은 그 순간의 파일을 기록한다
        self.heads = [f.read_bytes()[:5] for f in job.files]
        self.sizes = [Image.open(p.path).size for p in job.pages]
        if self.clock:
            self.clock.now += self.takes_s
        if isinstance(self.outcome, Exception):
            raise self.outcome
        return self.outcome or {"engine": "fake", "engineVersion": "1", "jgb": {"v": 1}, "score": {"events": []},
                                "issues": [{"kind": "LOW_CONFIDENCE", "eventId": "e0", "cellId": None, "page": 0,
                                            "bbox": [1, 2, 3, 4], "detail": None}]}


@pytest.fixture
def clock(monkeypatch):
    """서비스가 보는 시계. 테스트가 직접 시간을 넘긴다."""

    class Clock:
        now = 1000.0

    c = Clock()
    monkeypatch.setattr(api, "monotonic", lambda: c.now)
    return c


@pytest.fixture
def serve():
    """URL마다 정한 바이트를 돌려주는 가짜 내려받기와 가짜 엔진을 끼운 클라이언트."""

    def use(files, engine=None, takes_s=0.0, clock=None):
        def fetch(url, dest, max_bytes, deadline):
            use.deadlines.append(deadline)
            if clock:
                clock.now += takes_s
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
        use.deadlines = []
        return TestClient(app, raise_server_exceptions=False)

    yield use
    app.dependency_overrides.clear()


def failed(response, reason):
    assert response.status_code == 200
    assert response.json() == {"conversionId": "c-1", "status": "FAILED", "failReason": reason}


def test_pdf_is_rendered_per_page_and_engine_result_is_returned(serve, clock):
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
    assert job.time_budget_s == 60  # 쪽당 30초(시계를 멈춰 둠)
    assert serve.deadlines == [clock.now + 300]  # 쪽 수를 모르는 내려받기는 최대 300초
    assert job.request.notation == "STAFF"


def test_images_become_pages_in_order(serve, clock):
    files = images(2)
    client = serve({files[0]["url"]: image(size=(40, 30)), files[1]["url"]: image(size=(50, 20))})
    out = client.post("/v1/recognitions", json=body(files)).json()
    assert out["status"] == "DONE"
    assert out["pages"] == [{"index": 0, "width": 40, "height": 30}, {"index": 1, "width": 50, "height": 20}]
    assert serve.engine.jobs[0].kind == "IMAGE" and serve.engine.jobs[0].time_budget_s == 60
    assert serve.deadlines == [clock.now + 60] * 2  # 이미지는 장 수를 미리 알아 30초 × 장 수까지만 받는다


@pytest.mark.parametrize("mode", ["RGB", "CMYK"])
def test_phone_photo_orientation_is_applied(serve, mode):
    files = images(1, "image/jpeg")
    data = image("JPEG", size=(40, 30), orientation=6, mode=mode)
    out = serve(data).post("/v1/recognitions", json=body(files)).json()
    assert out["pages"] == [{"index": 0, "width": 30, "height": 40}]
    assert serve.engine.sizes == [(30, 40)]


def test_multi_picture_phone_jpeg_is_accepted(serve):
    """삼성·iPhone HDR 사진은 JPEG 안에 그림이 여럿이라 Pillow가 MPO로 연다."""
    assert Image.open(io.BytesIO(image("MPO"))).format == "MPO"
    out = serve(image("MPO")).post("/v1/recognitions", json=body(images(1, "image/jpeg"))).json()
    assert out["status"] == "DONE" and out["pages"] == [{"index": 0, "width": 40, "height": 30}]


def test_pdf_with_leading_bytes_is_accepted(serve):
    assert serve(b"\n" + pdf()).post("/v1/recognitions", json=body()).json()["status"] == "DONE"


def test_pdfium_is_called_under_lock(serve, monkeypatch):
    """PDFium은 스레드 안전하지 않아 동시 요청이 겹쳐도 한 번에 하나만 그린다."""
    render, held = pdfium.PdfPage.render, []

    def spy(page, **kw):
        held.append(api.PDFIUM_LOCK.locked())
        return render(page, **kw)

    monkeypatch.setattr(pdfium.PdfPage, "render", spy)
    assert serve(pdf()).post("/v1/recognitions", json=body()).json()["status"] == "DONE"
    assert held == [True]


def test_waiting_for_pdfium_counts_against_deadline(serve, clock, monkeypatch):
    """다른 요청이 PDF를 그리는 동안 남은 시간만큼만 기다린다."""

    class Busy:
        waits = []

        def acquire(self, timeout):
            self.waits.append(timeout)
            return False

    monkeypatch.setattr(api, "PDFIUM_LOCK", Busy())
    failed(serve(pdf(), takes_s=100, clock=clock).post("/v1/recognitions", json=body()), "TIMEOUT")
    assert Busy.waits == [200]  # 최대 300초 중 내려받기에 100초를 썼다


def test_rendering_stops_at_deadline(serve, clock, monkeypatch):
    """2쪽이면 60초다. 첫 쪽에 61초가 걸리면 둘째 쪽은 그리지 않는다."""
    render, calls = pdfium.PdfPage.render, []

    def slow(page, **kw):
        calls.append(1)
        clock.now += 61
        return render(page, **kw)

    monkeypatch.setattr(pdfium.PdfPage, "render", slow)
    failed(serve(pdf(pages=2)).post("/v1/recognitions", json=body()), "TIMEOUT")
    assert len(calls) == 1 and not serve.engine.jobs
    assert not api.PDFIUM_LOCK.locked()  # 시간 초과로 나가도 잠금을 푼다


def test_render_failure_is_unsupported(serve, monkeypatch):
    def broken(page, **kw):
        raise pdfium.PdfiumError("render failed")

    monkeypatch.setattr(pdfium.PdfPage, "render", broken)
    failed(serve(pdf()).post("/v1/recognitions", json=body()), "UNSUPPORTED_FILE")


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
    (RecognitionFailed("SOMETHING_ELSE"), "ENGINE_ERROR"),  # 계약 목록 밖 코드는 내보내지 않는다
    ({"engine": "fake"}, "ENGINE_ERROR"),  # 응답 필드가 빠진 엔진 결과
])
def test_engine_failures_map_to_fail_reason(serve, outcome, reason):
    failed(serve(pdf(), Engine(outcome)).post("/v1/recognitions", json=body()), reason)


@pytest.mark.parametrize("engine_s, fail_reason", [(29, None), (31, "TIMEOUT")])
def test_engine_past_page_budget_is_timeout(serve, clock, engine_s, fail_reason):
    out = serve(pdf(), Engine(clock=clock, takes_s=engine_s)).post("/v1/recognitions", json=body()).json()
    assert out["failReason"] == fail_reason


def test_time_before_engine_counts_against_budget(serve, clock):
    """요청 전체가 쪽당 30초다. 내려받기에 20초를 쓰면 엔진에는 10초가 남는다."""
    engine = Engine(clock=clock, takes_s=15)
    failed(serve(pdf(), engine, takes_s=20, clock=clock).post("/v1/recognitions", json=body()), "TIMEOUT")
    assert engine.jobs[0].time_budget_s == 10


def test_no_time_left_after_download_skips_engine(serve, clock):
    failed(serve(pdf(), takes_s=30, clock=clock).post("/v1/recognitions", json=body()), "TIMEOUT")
    assert not serve.engine.jobs


def test_download_timeout_is_timeout(serve):
    failed(serve(TimeoutError()).post("/v1/recognitions", json=body()), "TIMEOUT")


def test_unexpected_error_is_still_a_contract_response(serve, caplog):
    with caplog.at_level(logging.INFO):
        failed(serve(RuntimeError("disk full")).post("/v1/recognitions", json=body()), "ENGINE_ERROR")
    assert "result=ENGINE_ERROR:RuntimeError" in caplog.text


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


def chunks(n, clock=None, step_s=0.0):
    """1바이트씩 n번 보내는 응답. 보낼 때마다 시계를 step_s만큼 넘긴다."""

    def stream():
        for _ in range(n):
            if clock:
                clock.now += step_s
            yield b"x"

    return httpx.MockTransport(lambda _: httpx.Response(200, content=stream()))


def test_download_stops_at_size_limit(tmp_path, clock):
    with pytest.raises(DownloadError) as error:
        api.download(SIGNED_URL, tmp_path / "f", max_bytes=10, deadline=clock.now + 30, transport=chunks(11))
    assert "secret-token" not in str(error.value)
    api.download(SIGNED_URL, tmp_path / "f", max_bytes=11, deadline=clock.now + 30, transport=chunks(11))
    assert (tmp_path / "f").read_bytes() == b"x" * 11


def test_download_stops_at_deadline_between_chunks(tmp_path, clock):
    with pytest.raises(TimeoutError):
        api.download(SIGNED_URL, tmp_path / "f", max_bytes=100, deadline=clock.now + 5,
                     transport=chunks(10, clock, step_s=1))


def timing_out(clock=None, step_s=0.0):
    """연결 중 시간 초과가 나는 저장소. 그동안 시계를 step_s만큼 넘긴다."""

    def handler(request):
        if clock:
            clock.now += step_s
        raise httpx.ConnectTimeout("timed out", request=request)

    return httpx.MockTransport(handler)


def test_storage_timeout_before_deadline_is_download_error(tmp_path, clock):
    """마감 전 저장소 시간 초과는 내려받기 실패(UNSUPPORTED_FILE)다. 계약의 TIMEOUT은 요청 시간 상한 초과다."""
    with pytest.raises(DownloadError):
        api.download(SIGNED_URL, tmp_path / "f", max_bytes=10, deadline=clock.now + 30,
                     transport=timing_out(clock, step_s=10))


def test_storage_timeout_after_deadline_is_timeout(tmp_path, clock):
    with pytest.raises(TimeoutError):
        api.download(SIGNED_URL, tmp_path / "f", max_bytes=10, deadline=clock.now + 30,
                     transport=timing_out(clock, step_s=31))


@pytest.mark.parametrize("url, transport", [
    (SIGNED_URL, httpx.MockTransport(lambda _: httpx.Response(403))),
    ("https://[::1", None),  # 형식 검사는 통과하지만 httpx가 InvalidURL을 낸다
])
def test_download_errors_are_download_error(tmp_path, clock, url, transport):
    with pytest.raises(DownloadError):
        api.download(url, tmp_path / "f", max_bytes=10, deadline=clock.now + 30, transport=transport)


def test_health():
    assert TestClient(app).get("/health").json() == {"status": "ok"}
