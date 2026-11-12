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
# 실제 Audiveris 5.11은 오선이 없는 쪽에서 .mxl 없이 종료 코드 1로 끝나고, 원인을 표준 출력에만 쓴다(U5 리뷰 실측)
FAKE = '''
import shutil, sys, time
from pathlib import Path
args = sys.argv[1:]
out, page = Path(args[args.index("-output") + 1]), Path(args[-1])
mode = page.read_bytes()[-12:].decode("ascii", "replace").strip()
fixtures = {fixtures!r}
if mode == "slow":
    time.sleep(30)
if mode == "empty":  # 실제 출력(eolssu-omr, 빈 쪽)에서 줄인 것
    print("WARN  [blank]  Book 2044 | Error processing stub org.audiveris.omr.step.StepException: No regularly spaced lines found")
    print("Caused by: org.audiveris.omr.step.StepException: No regularly spaced lines found")
    print("WARN  [blank]  CLI 956  | Exception occurred java.lang.Exception: Error in reaching step PAGE")
    print("java.lang.Exception: Error in reaching step PAGE")
    sys.exit(1)
if mode == "jvmfail":  # 오선 문제가 아닌 실패(메모리 부족 등)
    print("Exception in thread main java.lang.OutOfMemoryError: Java heap space")
    sys.exit(1)
out.mkdir(parents=True, exist_ok=True)
shutil.copy(fixtures[mode.rstrip("!")], out / (page.stem + ".mxl"))
if mode.endswith("!"):  # 악보는 냈지만 종료 코드가 0이 아니다
    sys.exit(1)
'''
GUTGEORI = (b'<?xml version="1.0"?><score-partwise><part id="P1"><measure><attributes><divisions>2</divisions>'
            b'<time><beats>12</beats><beat-type>8</beat-type></time></attributes>'
            + b"".join(b"<note><pitch><step>%s</step><octave>4</octave></pitch><duration>3</duration><voice>1</voice></note>"
                       % s for s in [b"E", b"G", b"A", b"G"]) + b"</measure></part></score-partwise>")
FOUR_QUARTERS = (b'<?xml version="1.0"?><score-partwise><part id="P1"><measure><attributes><divisions>1</divisions>'
                 b'<time><beats>4</beats><beat-type>4</beat-type></time></attributes>'
                 + b"".join(b"<note><pitch><step>%s</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>"
                            % s for s in [b"E", b"G", b"A", b"G"]) + b"</measure></part></score-partwise>")


def page_png(mode):
    """가짜 Audiveris가 읽을 표시(mode)를 PNG 끝에 붙인 쪽 그림."""
    buf = io.BytesIO()
    Image.new("RGB", (40, 30), "white").save(buf, "PNG")
    return buf.getvalue() + mode.ljust(12).encode("ascii")


@pytest.fixture
def fake_audiveris(tmp_path, monkeypatch):
    script = tmp_path / "audiveris.py"
    (tmp_path / "gutgeori.musicxml").write_bytes(GUTGEORI)
    (tmp_path / "four-quarters.musicxml").write_bytes(FOUR_QUARTERS)
    fixtures = {"score": str(FIXTURES / "danso_onara.audiveris.mxl"), "s44": str(FIXTURES / "danso_bukcheon.audiveris.mxl"),
                "s128": str(tmp_path / "gutgeori.musicxml"), "quarters": str(tmp_path / "four-quarters.musicxml")}
    script.write_text(FAKE.format(fixtures=fixtures), encoding="utf-8")
    monkeypatch.setattr(staff_engine, "AUDIVERIS_LOGS", tmp_path / "audiveris-logs")  # 개발 PC의 로그를 지우지 않게
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
    # 박자 추정은 유지하지만 사용자가 검토하기 전 음고는 바꾸지 않는다.
    assert out["score"]["music"]["jangdan"] == "굿거리"
    kinds = {i["kind"] for i in out["notices"]}
    assert "TRANSPOSED" not in kinds
    originals = {p['eventId']: p['midi'] for p in out['sourcePitches']}
    for event in out['score']['events']:
        if event['kind'] == 'NOTE':
            assert 63 + staff_engine.staff_musicxml.YUL.index(event['yul']) + 12 * event['octave'] == originals[event['id']]
    assert guessed(out) == [{"source": "rhythm", "jangdan": "굿거리"}]
    assert sum(e["kind"] == "NOTE" for e in out["score"]["events"]) == 66


@needs_generator
def test_requested_jangdan_wins_and_mismatch_is_flagged(fake_audiveris):
    # 각이 굿거리 4박인 악보를 세마치(3박)로 보내면 세마치로 저장하고 맞지 않는다고 알린다
    out = post("score", jangdan="세마치").json()
    assert out["status"] == "DONE" and out["score"]["music"]["jangdan"] == "세마치"
    assert {"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
            "detail": {"jangdan": "세마치", "jeongganPerGak": 4}} in out["notices"]


@needs_generator
def test_four_quarter_staff_measure_matches_danmori_without_stretching(fake_audiveris):
    out = post("quarters", jangdan="단모리").json()
    assert out["status"] == "DONE"
    score = out["score"]
    ticks = score["music"]["ticksPerBeat"]
    assert score["music"]["ticksPerJeonggan"] == ticks
    assert [(e["startTick"] / ticks, e["durationTick"] / ticks) for e in score["events"]] == [
        (0, 1), (1, 1), (2, 1), (3, 1),
    ]
    assert not any(n["kind"] == "JANGDAN_MISMATCH" for n in out["notices"])


@needs_generator
def test_page_without_staff_is_marked_and_others_are_kept(fake_audiveris):
    out = post("empty", "score").json()
    assert out["status"] == "DONE"
    assert {"kind": "UNREADABLE", "eventId": None, "cellId": None, "page": 0, "bbox": [0, 0, 40, 30],
            "detail": {"reason": "NO_STAFF"}} in out["notices"]


def test_no_staff_on_any_page_is_no_staff_found(fake_audiveris):
    assert post("empty", "empty").json()["failReason"] == "NO_STAFF_FOUND"


def test_audiveris_killed_is_engine_error(fake_audiveris, monkeypatch, caplog):
    # 메모리 부족 등으로 신호를 받아 죽으면(음수 종료 코드) 악보 문제가 아니라 엔진 오류다
    import subprocess

    monkeypatch.setattr(staff_engine, "_run", lambda cmd, deadline: subprocess.CompletedProcess(cmd, -9, b"", b""))
    with caplog.at_level("INFO"):
        assert post("score").json()["failReason"] == "ENGINE_ERROR"
    assert "audiveris page=0 killed" in caplog.text


def test_page_reason_is_logged_from_stdout(fake_audiveris, caplog):
    # 겉포장(Error in reaching step PAGE)이 아니라 그 원인(Caused by) 줄을 남긴다
    with caplog.at_level("INFO"):
        post("empty")
    assert ("audiveris page=0 no staff: Caused by: org.audiveris.omr.step.StepException: "
            "No regularly spaced lines found") in caplog.text


def test_failure_that_is_not_about_staff_is_engine_error(fake_audiveris, caplog):
    # 메모리 부족·설정 오류도 종료 코드 1이다. 오선을 못 찾았다는 StepException이 없으면 엔진 오류로 알린다
    with caplog.at_level("INFO"):
        assert post("jvmfail").json()["failReason"] == "ENGINE_ERROR"
    assert "audiveris page=0 exit=1 without score: Exception in thread main java.lang.OutOfMemoryError" in caplog.text


@needs_generator
def test_score_with_nonzero_exit_is_still_used(fake_audiveris):
    assert post("score!").json()["status"] == "DONE"


def test_audiveris_runs_one_at_a_time(fake_audiveris, monkeypatch):
    class Busy:
        def acquire(self, timeout):
            return False

    monkeypatch.setattr(staff_engine, "AUDIVERIS_LOCK", Busy())
    assert post("score").json()["failReason"] == "TIMEOUT"


def guessed(out):
    return [i["detail"] for i in out["notices"] if i["kind"] == "JANGDAN_GUESSED"]


@needs_generator
@pytest.mark.parametrize("mode, detail, mismatch", [
    ("s128", {"source": "timeSignature", "jangdan": "굿거리"}, False),
    ("s44", {"source": "default", "jangdan": "굿거리"}, True),  # 4/4는 장단이 없어 굿거리로 두고 맞지 않음을 알린다
])
def test_jangdan_guess_source(fake_audiveris, mode, detail, mismatch):
    out = post(mode).json()
    assert out["status"] == "DONE" and guessed(out) == [detail]
    assert any(i["kind"] == "JANGDAN_MISMATCH" for i in out["notices"]) == mismatch


@needs_generator
def test_requested_jangdan_is_not_a_guess(fake_audiveris):
    assert guessed(post("score", jangdan="굿거리").json()) == []


def test_unknown_jangdan_on_staff_is_rejected(fake_audiveris):
    assert post("score", jangdan="없는장단").status_code == 422


def test_movements_are_read_in_natural_order():
    names = [Path(f"p.mvt{n}.mxl") for n in (10, 2, 1)]
    assert [p.name for p in sorted(names, key=staff_engine._natural)] == ["p.mvt1.mxl", "p.mvt2.mxl", "p.mvt10.mxl"]


def test_merge_starts_from_first_sheet_with_a_part():
    a = (FIXTURES / "danso_onara.audiveris.mxl").read_bytes()
    empty = b'<?xml version="1.0"?><score-partwise/>'
    assert staff_engine.merge_pages([empty, a]) == staff_engine.merge_pages([a])


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


def test_log_cleaning_keeps_meter_and_ratios():
    from omr_service.jeongganbo_text import _clean

    text = r"12/8 time, 1/3 beat, file /tmp/home/x.log and C:\Users\a\b.pdf"
    assert _clean(text) == "12/8 time, 1/3 beat, file <path> and <path>"


def test_audiveris_logs_are_removed_after_each_page(fake_audiveris, tmp_path):
    logs = tmp_path / "audiveris-logs"
    logs.mkdir()
    (logs / "20261104T011307.log").write_text("x")
    post("empty")
    assert list(logs.glob("*.log")) == []
