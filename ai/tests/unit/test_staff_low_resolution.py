"""오선 간격을 잘못 재거나 작게 잰 사진·그림을 흑백으로, 그래도 작으면 확대해 다시 읽는다(S15P31E102-1002).

Audiveris 5.11은 오선 간격이 너무 작으면 쪽을 버리고(`Sheet ignored`) 표준 출력에 간격 값을 남긴다.
문구는 축소한 폰 사진(750×1000, 간격 8픽셀)을 컨테이너의 실제 Audiveris로 돌린 출력에서 줄였다.
운영에서 실패한 폰 사진 원본은 컬러로는 간격 3, 흑백으로는 24로 재졌다.
여기서는 Audiveris 실행(_run)만 고정하고 쪽 처리(_read_pages)는 실제 코드로 돈다.
"""

import io
import subprocess
import zipfile
from pathlib import Path
from time import monotonic
from types import SimpleNamespace

from PIL import Image

from omr_service import staff_engine
from omr_service.api import Page

FIXTURE = Path(__file__).resolve().parents[1] / "fixtures/omr/danso_onara.audiveris.mxl"
LOW_INTERLINE = (b"WARN  [photo]  SheetStub 411  | photo   With a too low interline value of 8 pixels,  either this "
                 b"sheet contains no multi-line staves,  or the picture resolution is too low (try 300 DPI).   "
                 b"This interline value is NOT RELIABLE!\n"
                 b"WARN  [photo]  Book 2044 | Error processing stub org.audiveris.omr.step.StepException: Sheet ignored\n"
                 b"Caused by: org.audiveris.omr.step.StepException: Sheet ignored\n")
NO_LINES = b"Caused by: org.audiveris.omr.step.StepException: No regularly spaced lines found\n"


def time_signature_archive(box):
    """신뢰도가 낮은 박자표 하나가 든 .omr. 좌표는 Audiveris가 읽은 그림의 픽셀이다."""
    x, y, w, h = box
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as z:
        z.writestr("sheet#1/sheet#1.xml", f'<sheet><time-pair time-rational="4/4" grade="0.3" staff="1">'
                                          f'<bounds x="{x}" y="{y}" w="{w}" h="{h}"/></time-pair></sheet>')
    return stream.getvalue()


def read(tmp_path, monkeypatch, size, outputs):
    """outputs((크기, 색 형식)) → (표준 출력, .omr 또는 None, 악보 여부). 실행된 (크기, 색 형식) 목록을 함께 돌려준다."""
    page_path = tmp_path / "photo.png"
    Image.new("RGB", size, "white").save(page_path)
    seen = []

    def run(command, deadline):
        out, image = Path(command[command.index("-output") + 1]), Path(command[-1])
        with Image.open(image) as opened:
            seen.append((opened.size, opened.mode))
        stdout, archive, score = outputs(seen[-1])
        if score:
            out.mkdir(parents=True)
            (out / "page.mxl").write_bytes(FIXTURE.read_bytes())
            if archive:
                (out / "page.omr").write_bytes(archive)
            return subprocess.CompletedProcess(command, 0, stdout, b"")
        return subprocess.CompletedProcess(command, 1, stdout, b"")

    monkeypatch.setattr(staff_engine, "_run", run)
    monkeypatch.setattr(staff_engine, "AUDIVERIS_LOGS", tmp_path / "logs")
    job = SimpleNamespace(kind="IMAGE", files=[page_path], pages=[Page(page_path, *size)])
    pages, issues = staff_engine._read_pages(job, monotonic() + 60, 60)
    return pages, issues, seen


RGB, GRAY = ((400, 300), "RGB"), ((400, 300), "L")


def test_wrongly_measured_color_page_is_read_again_in_grayscale(tmp_path, monkeypatch):
    # 운영 실패 사진처럼 컬러로는 간격을 작게 재지만 흑백으로는 제대로 읽히는 쪽. 확대하지 않는다.
    def outputs(image):
        return (LOW_INTERLINE, None, False) if image == RGB else (b"", time_signature_archive((100, 50, 25, 75)), True)

    pages, issues, seen = read(tmp_path, monkeypatch, (400, 300), outputs)
    assert seen[:2] == [RGB, GRAY]
    assert pages == [FIXTURE.read_bytes()]
    assert [i for i in issues if i["detail"].get("reason") == "LOW_INTERLINE_RETRY"] == [
        {"kind": "LOW_CONFIDENCE", "eventId": None, "cellId": None, "page": 0, "bbox": None,
         "detail": {"reason": "LOW_INTERLINE_RETRY", "interline": 8, "scale": 1.0}}]
    review = [i for i in issues if i["detail"].get("reason") == "TIME_SIGNATURE_REVIEW_REQUIRED"]
    assert [i["bbox"] for i in review] == [[100, 50, 25, 75]]


def test_low_interline_page_is_enlarged_and_read_again(tmp_path, monkeypatch):
    # 흑백으로도 간격 8 → 목표 20픽셀이라 2.5배. 확대한 그림의 박자표 좌표는 원래 쪽 좌표로 되돌린다.
    def outputs(image):
        if image[0] == (400, 300):
            return LOW_INTERLINE, None, False
        return b"", time_signature_archive((100, 50, 25, 75)), True

    pages, issues, seen = read(tmp_path, monkeypatch, (400, 300), outputs)
    assert seen[:3] == [RGB, GRAY, ((1000, 750), "L")]
    assert pages == [FIXTURE.read_bytes()]
    assert [i["detail"] for i in issues if i["detail"].get("reason") == "LOW_INTERLINE_RETRY"] == [
        {"reason": "LOW_INTERLINE_RETRY", "interline": 8, "scale": 2.5}]
    review = [i for i in issues if i["detail"].get("reason") == "TIME_SIGNATURE_REVIEW_REQUIRED"]
    assert [i["bbox"] for i in review] == [[40, 20, 10, 30]]


def test_enlargement_is_limited_by_the_pixel_limit(tmp_path, monkeypatch):
    monkeypatch.setattr(Image, "MAX_IMAGE_PIXELS", 400 * 300 * 4)  # 최대 2배
    def outputs(image):
        return (LOW_INTERLINE, None, False) if image[0] == (400, 300) else (b"", None, True)

    pages, issues, seen = read(tmp_path, monkeypatch, (400, 300), outputs)
    assert seen[:3] == [RGB, GRAY, ((800, 600), "L")]
    assert pages and any(i["detail"].get("scale") == 2.0 for i in issues)


def test_page_that_still_has_no_staff_keeps_original_coordinates(tmp_path, monkeypatch):
    pages, issues, seen = read(tmp_path, monkeypatch, (400, 300), lambda image: (LOW_INTERLINE, None, False))
    assert seen == [RGB, GRAY, ((1000, 750), "L")]
    assert pages == []
    assert [i for i in issues if i["kind"] == "UNREADABLE"] == [
        {"kind": "UNREADABLE", "eventId": None, "cellId": None, "page": 0, "bbox": [0, 0, 400, 300],
         "detail": {"reason": "NO_STAFF"}}]
    assert not any(i["detail"].get("reason") == "LOW_INTERLINE_RETRY" for i in issues)


def test_grayscale_page_without_staff_lines_is_not_enlarged(tmp_path, monkeypatch):
    # 흑백으로 다시 읽어 오선이 없다고 하면(간격 경고 없음) 확대하지 않는다.
    def outputs(image):
        return (LOW_INTERLINE, None, False) if image == RGB else (NO_LINES, None, False)

    pages, issues, seen = read(tmp_path, monkeypatch, (400, 300), outputs)
    assert seen == [RGB, GRAY]
    assert pages == [] and [i["kind"] for i in issues] == ["UNREADABLE"]


def test_page_without_staff_lines_is_not_retried(tmp_path, monkeypatch):
    pages, issues, seen = read(tmp_path, monkeypatch, (400, 300), lambda image: (NO_LINES, None, False))
    assert seen == [RGB]
    assert pages == [] and [i["kind"] for i in issues] == ["UNREADABLE"]


def test_large_color_photo_is_read_again_in_grayscale_within_the_long_side_limit(tmp_path, monkeypatch):
    # 간격을 잘못 잰 큰 사진은 긴 변 1500픽셀로 줄인 흑백으로 읽는다. 거기서도 작으면 그 크기 기준으로 확대한다.
    def outputs(image):
        if image == ((3000, 2000), "RGB"):
            return LOW_INTERLINE.replace(b"of 8 pixels", b"of 3 pixels"), None, False
        if image == ((1500, 1000), "L"):
            return LOW_INTERLINE, None, False  # 줄인 그림에서 간격 8
        return b"", time_signature_archive((100, 50, 25, 75)), True

    pages, issues, seen = read(tmp_path, monkeypatch, (3000, 2000), outputs)
    assert seen[:3] == [((3000, 2000), "RGB"), ((1500, 1000), "L"), ((3750, 2500), "L")]
    assert [i["detail"] for i in issues if i["detail"].get("reason") == "LOW_INTERLINE_RETRY"] == [
        {"reason": "LOW_INTERLINE_RETRY", "interline": 3, "scale": 1.25}]
    review = [i for i in issues if i["detail"].get("reason") == "TIME_SIGNATURE_REVIEW_REQUIRED"]
    assert [i["bbox"] for i in review] == [[80, 40, 20, 60]]
