"""오선보 엔진(omr_service.staff_engine)을 가짜 Audiveris로 검사한다.

가짜는 Audiveris처럼 `-output <폴더> -- <쪽 그림>`을 받아, 쪽 그림 이름에 따라 고정 MusicXML을 쓰거나(악보),
아무것도 쓰지 않거나(오선 없음), 실패하거나, 오래 걸린다. 실제 Audiveris 실행은 ai/README.md의 컨테이너 확인을 따른다.
변환까지 가는 검사는 jeongganbo-generator가 필요해 JGB_GENERATOR_DIR이 없으면 건너뛴다.
"""

import io
import os
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from omr_service import api, staff_engine
from omr_service.api import app, get_fetcher

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures/omr"
needs_generator = pytest.mark.skipif(not os.environ.get("JGB_GENERATOR_DIR"),
                                     reason="JGB_GENERATOR_DIR(jeongganbo-generator 클론)가 없다")
FAKE = '''
import shutil, sys, time
from pathlib import Path
args = sys.argv[1:]
out, page = Path(args[args.index("-output") + 1]), Path(args[-1])
mode = page.read_bytes()[-12:].decode("ascii", "replace")
if "crash" in mode:
    sys.exit(3)
if "slow" in mode:
    time.sleep(30)
if "score" in mode:
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy(r"{fixture}", out / (page.stem + ".mxl"))
'''


def page_png(mode):
    """가짜 Audiveris가 읽을 표시(mode)를 PNG 끝에 붙인 쪽 그림."""
    buf = io.BytesIO()
    Image.new("RGB", (40, 30), "white").save(buf, "PNG")
    return buf.getvalue() + mode.ljust(12).encode("ascii")


@pytest.fixture
def fake_audiveris(tmp_path, monkeypatch):
    script = tmp_path / "audiveris.py"
    script.write_text(FAKE.format(fixture=FIXTURES / "danso_onara.audiveris.mxl"), encoding="utf-8")
    monkeypatch.setattr(staff_engine, "AUDIVERIS", [sys.executable, str(script)])


def post(*modes, **override):
    files = [{"url": f"https://r2.example/p{i}.png?sig=x", "contentType": "image/png"} for i in range(len(modes))]
    data = {f["url"]: page_png(m) for f, m in zip(files, modes)}

    def fetch(url, dest, max_bytes, deadline):
        dest.write_bytes(data[url])

    app.dependency_overrides[get_fetcher] = lambda: fetch
    try:
        body = {"conversionId": "c-9", "notation": "STAFF", "instrument": "DANSO", "files": files} | override
        return TestClient(app, raise_server_exceptions=False).post("/v1/recognitions", json=body)
    finally:
        app.dependency_overrides.clear()


@needs_generator
def test_staff_page_becomes_common_score(fake_audiveris):
    out = post("score").json()
    assert out["status"] == "DONE" and out["engine"] == "audiveris"
    assert out["score"]["provenance"]["converter"] == f"staff-omr/audiveris-{staff_engine.AUDIVERIS_VERSION}"
    assert out["score"]["provenance"]["review"] == "inferred"
    # 오나라는 박자표가 없어 굿거리(12/8)로 추정했고, 단소 5율에 맞춰 6반음 옮겼다
    assert out["score"]["music"]["jangdan"] == "굿거리"
    kinds = {i["kind"] for i in out["issues"]}
    assert {"TRANSPOSED", "JANGDAN_GUESSED"} <= kinds
    assert sum(e["kind"] == "NOTE" for e in out["score"]["events"]) == 66


@needs_generator
def test_requested_jangdan_wins_and_mismatch_is_flagged(fake_audiveris):
    # 각이 굿거리 4박인 악보를 세마치(3박)로 보내면 세마치로 저장하고 맞지 않는다고 알린다
    out = post("score", jangdan="세마치").json()
    assert out["status"] == "DONE" and out["score"]["music"]["jangdan"] == "세마치"
    assert {"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
            "detail": {"jangdan": "세마치", "jeongganPerGak": 4}} in out["issues"]


@needs_generator
def test_page_without_staff_is_marked_and_others_are_kept(fake_audiveris):
    out = post("empty", "score").json()
    assert out["status"] == "DONE"
    assert {"kind": "UNREADABLE", "eventId": None, "cellId": None, "page": 0, "bbox": [0, 0, 40, 30],
            "detail": {"reason": "NO_STAFF"}} in out["issues"]


def test_no_staff_on_any_page_is_no_staff_found(fake_audiveris):
    assert post("empty", "empty").json()["failReason"] == "NO_STAFF_FOUND"


def test_audiveris_crash_is_engine_error(fake_audiveris, caplog):
    with caplog.at_level("INFO"):
        assert post("crash").json()["failReason"] == "ENGINE_ERROR"
    assert "audiveris page=0 exit=3" in caplog.text


def test_slow_page_is_killed_as_timeout(fake_audiveris, monkeypatch):
    monkeypatch.setattr(api, "PAGE_TIME_S", 1)  # 쪽당 1초. 가짜는 30초 잔다
    out = post("slow").json()
    assert out["failReason"] == "TIMEOUT"


def test_pages_are_merged_in_order():
    import xml.etree.ElementTree as ET

    from omr_service.staff_musicxml import _xml_bytes

    a = (FIXTURES / "danso_onara.audiveris.mxl").read_bytes()
    b = (FIXTURES / "danso_eoi.audiveris.mxl").read_bytes()
    merged = ET.fromstring(staff_engine.merge_pages([a, b]))
    first = [ET.tostring(m) for m in ET.fromstring(_xml_bytes(a)).find("part").findall("measure")]
    second = [ET.tostring(m) for m in ET.fromstring(_xml_bytes(b)).find("part").findall("measure")]
    assert [ET.tostring(m) for m in merged.find("part").findall("measure")] == first + second


def test_each_page_has_its_own_time_limit(fake_audiveris, monkeypatch):
    # 두 쪽이면 요청 예산은 2초지만 한 쪽은 1초에서 끊는다(남은 쪽 시간을 한 쪽이 다 쓰지 않게)
    import time

    monkeypatch.setattr(api, "PAGE_TIME_S", 1)
    began = time.monotonic()
    assert post("slow", "score").json()["failReason"] == "TIMEOUT"
    assert time.monotonic() - began < 1.8
