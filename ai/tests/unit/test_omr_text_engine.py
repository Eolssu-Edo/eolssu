"""글자 층 정간보 PDF 엔진(omr_service.jeongganbo_text)을 합성 PDF로 검사한다.

변환까지 가는 테스트는 jeongganbo-generator(9d4a24e) 클론과 Node가 필요하다. JGB_GENERATOR_DIR이 없으면
그 테스트만 건너뛴다(ai/README.md '악보 인식 API'). 실제 표준악보집 8곡과 정답 대조는
docs/knowledge/data/danso-scores-umulsai.md에 있다.
"""

import os
import sys
import tempfile
from fractions import Fraction
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from omr_service import api, jeongganbo_text
from omr_service.api import app, get_fetcher

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "data/tests"))
from test_standard_score_text import score_pdf  # noqa: E402  data와 같은 합성 정간보를 쓴다

needs_generator = pytest.mark.skipif(not os.environ.get("JGB_GENERATOR_DIR"),
                                     reason="JGB_GENERATOR_DIR(jeongganbo-generator 클론)가 없다")
SEMACHI = [[["林", "南", "-"], ["潢", "", "汰"]]]  # 세마치 2각(각마다 3정간), 오른쪽 열부터


def pdf_bytes(tiers, **kw):
    with tempfile.TemporaryDirectory() as d:
        path = Path(d) / "s.pdf"
        score_pdf(path, tiers, **kw)
        return path.read_bytes()


def pages_pdf(*pages):
    """쪽마다 (tiers, 옵션)인 여러 쪽 PDF."""
    import pymupdf

    doc = pymupdf.open()
    for tiers, kw in pages:
        doc.insert_pdf(pymupdf.open(stream=pdf_bytes(tiers, **kw)))
    return doc.tobytes()


def post(data, **override):
    def fetch(url, dest, max_bytes, deadline):
        dest.write_bytes(data)

    app.dependency_overrides[get_fetcher] = lambda: fetch
    try:
        body = {"conversionId": "c-7", "notation": "JEONGGANBO", "jangdan": "세마치", "instrument": "SOGEUM",
                "files": [{"url": "https://r2.example/s.pdf?sig=x", "contentType": "application/pdf"}]} | override
        return TestClient(app, raise_server_exceptions=False).post("/v1/recognitions", json=body)
    finally:
        app.dependency_overrides.clear()


@pytest.mark.parametrize("jangdan, per_gak, beats", [("세마치", 3, Fraction(1)), ("굿거리", 12, Fraction(1, 3)),
                                                      ("중모리", 12, Fraction(1))])
def test_beats_per_jeonggan_follows_jangdan(jangdan, per_gak, beats):
    # 정답 검토 기록의 값: 소금 아리랑(세마치) 1박, 늴리리야(굿거리) 1/3박
    assert jeongganbo_text.beats_per_jeonggan(jangdan, per_gak) == beats


@needs_generator
def test_text_layer_pdf_becomes_common_score():
    out = post(pdf_bytes(SEMACHI)).json()
    assert out["status"] == "DONE" and out["engine"] == "jeongganbo-text"
    score = out["score"]
    assert score["provenance"] | {"jgbSha256": None} == {"sourceId": "c-7", "jgbSha256": None, "review": "inferred",
                                                          "converter": "jeongganbo-text/standard_score_text"}
    assert score["music"]["jangdan"] == "세마치" and score["music"]["bpmUnit"] == "DOTTED_QUARTER"
    tpb = score["music"]["ticksPerBeat"]
    assert score["music"]["ticksPerJeonggan"] == tpb  # 세마치 3정간 각이라 정간 하나 = 1박
    notes = [(e["kind"], e["yul"], e["octave"], e["startTick"] / tpb, e["durationTick"] / tpb) for e in score["events"]]
    # 임 1박, 남 2박(이음), 청황 2박(빈 정간이 이어짐), 청태 1박
    assert notes == [("NOTE", "임", 0, 0, 1), ("NOTE", "남", 0, 1, 2), ("NOTE", "황", 1, 3, 2), ("NOTE", "태", 1, 5, 1)]
    assert out["issues"] == [] and out["jgb"]["controls"]["beats"] == "3"


@needs_generator
def test_glyph_that_is_not_a_note_becomes_issue_at_its_place():
    tiers = [[["林", "南", "-"], ["潢", "ㄱ", "汰"]]]  # 시김새 글자 ㄱ은 읽지 않는다
    out = post(pdf_bytes(tiers)).json()
    assert out["status"] == "DONE"
    (issue,) = out["issues"]
    # 글자 층 읽기가 NFKC로 맞춰 한글 자모 ㄱ(U+3131)은 첫소리 ᄀ(U+1100)이 된다
    assert issue["kind"] == "UNREADABLE" and issue["detail"] == {"text": "ᄀ"} and issue["page"] == 0
    x, y, w, h = issue["bbox"]
    # 글자 크기 10.5pt를 300dpi 쪽 그림 픽셀로 옮긴 정사각형
    assert 0 < x < out["pages"][0]["width"] and 0 < y < out["pages"][0]["height"] and w == h == round(10.5 * 300 / 72)


def test_pdf_without_text_layer_is_left_to_omr(monkeypatch, tmp_path):
    """율명 글자가 없는 PDF(스캔)는 글자로 읽지 않고 정간보 OMR 경로로 넘긴다. OMR(U3) 전이라 응답은 ENGINE_ERROR다."""
    monkeypatch.setenv("JGB_GENERATOR_DIR", "unused")
    path = tmp_path / "scan.pdf"
    path.write_bytes(pdf_bytes(SEMACHI, no_text=True))
    request = api.RecognitionIn(conversionId="c-7", notation="JEONGGANBO", jangdan="세마치", instrument="SOGEUM",
                                files=[{"url": "https://r2.example/s.pdf", "contentType": "application/pdf"}])
    job = api.Job(request, "PDF", [path], [api.Page(path, 1, 1)], 30)
    with pytest.raises(jeongganbo_text.NoTextLayer):
        jeongganbo_text.recognize(job)
    assert post(path.read_bytes()).json()["failReason"] == "ENGINE_ERROR"


def test_text_without_grid_is_no_grid_found(monkeypatch):
    monkeypatch.setenv("JGB_GENERATOR_DIR", "unused")
    out = post(pdf_bytes(SEMACHI, no_lines=True)).json()
    assert out["failReason"] == "NO_GRID_FOUND"


def test_child_process_past_deadline_is_killed_as_timeout():
    began = jeongganbo_text.monotonic()
    with pytest.raises(TimeoutError):
        jeongganbo_text._run([sys.executable, "-c", "import time; time.sleep(30)"], began + 0.5)
    assert jeongganbo_text.monotonic() - began < 10


def test_unknown_jangdan_is_rejected():
    assert post(pdf_bytes(SEMACHI), jangdan="없는장단").status_code == 422


@needs_generator
def test_page_without_yul_is_skipped_and_marked():
    # 표지일 수도, 글자 층 없이 스캔한 악보 쪽일 수도 있어 건너뛰되 알린다
    out = post(pages_pdf((SEMACHI, {"no_text": True}), (SEMACHI, {}))).json()
    assert out["status"] == "DONE" and len(out["pages"]) == 2
    assert len(out["score"]["events"]) == 4  # 둘째 쪽 악보만
    (issue,) = out["issues"]
    assert (issue["kind"], issue["page"], issue["detail"]) == ("UNREADABLE", 0, {"reason": "NO_TEXT"})


@needs_generator
def test_page_with_unreadable_grid_is_marked_and_others_are_kept():
    out = post(pages_pdf((SEMACHI, {}), (SEMACHI, {"no_lines": True}))).json()
    assert out["status"] == "DONE" and len(out["score"]["events"]) == 4
    (issue,) = out["issues"]
    assert issue["kind"] == "UNREADABLE" and issue["page"] == 1 and issue["detail"] == {"reason": "NO_GRID"}
    assert issue["bbox"] == [0, 0, out["pages"][1]["width"], out["pages"][1]["height"]]


@needs_generator
def test_duet_keeps_first_voice_and_says_so():
    tiers = [[["林", "南", "-"], ["仲", "林", "-"], ["潢", "汰", "-"], ["南", "仲", "-"]]]
    out = post(pdf_bytes(tiers, labels=["단소1", "단소2", "단소1", "단소2"])).json()
    assert out["status"] == "DONE"
    assert [e["yul"] for e in out["score"]["events"]] == ["임", "남", "황", "태"]
    assert [i["kind"] for i in out["issues"]] == ["VOICE_GUESSED"] and out["issues"][0]["detail"] == {"voices": 2, "kept": 1}


@needs_generator
def test_jangdan_that_does_not_fit_the_gak_is_flagged():
    # 3정간 각을 굿거리(4박)로 보내면 정간 하나가 4/3박이 된다
    out = post(pdf_bytes(SEMACHI), jangdan="굿거리").json()
    assert out["status"] == "DONE"
    assert out["issues"] == [{"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
                              "detail": {"jangdan": "굿거리", "jeongganPerGak": 3}}]


def test_reader_crash_is_engine_error_not_no_grid(monkeypatch, tmp_path, caplog):
    monkeypatch.setenv("JGB_GENERATOR_DIR", "unused")
    (tmp_path / "standard_score_text.py").write_text("raise RuntimeError('boom')", encoding="utf-8")
    monkeypatch.setattr(jeongganbo_text, "SCORES", tmp_path)
    with caplog.at_level("INFO"):
        out = post(pdf_bytes(SEMACHI)).json()
    assert out["failReason"] == "ENGINE_ERROR"
    assert "result=ENGINE_ERROR:RuntimeError" in caplog.text


@needs_generator
def test_column_that_is_not_score_is_dropped_and_marked():
    # 오른쪽 쪽 장식음 표처럼 정간 수가 다른 열(천년만세 고급 PDF184)
    out = post(pdf_bytes(SEMACHI, width=1100, side_col=["淋", "仲", "南", "潢"])).json()
    assert out["status"] == "DONE" and len(out["score"]["events"]) == 4
    (issue,) = out["issues"]
    assert issue["kind"] == "UNREADABLE" and issue["detail"] == {"reason": "NOT_SCORE_COLUMN"}
    x, y, w, h = issue["bbox"]
    assert x > out["pages"][0]["width"] / 2 and w == round(36 * 300 / 72) and h == round(4 * 45 * 300 / 72)


@needs_generator
def test_mixed_gak_lengths_fail_with_reason_logged(caplog):
    # 곡 끝 각이 짧은 악보(소금 취타)는 score_data가 각별 정간 수를 아직 받지 않아 ENGINE_ERROR다
    cols = [["林", "南", "-", "-"], ["潢", "汰", "-", "-"], ["仲", "-", "△"]]
    with caplog.at_level("INFO"):
        out = post(pdf_bytes([cols])).json()
    assert out["failReason"] == "ENGINE_ERROR"
    assert "convert failed" in caplog.text and "gakBeats" in caplog.text


def test_logged_errors_have_no_paths():
    stderr = "Traceback\nFileNotFoundError: no such file: 'C:\\tmp\\x\\s.pdf' (/tmp/y)\n".encode()
    line = jeongganbo_text._last_line(stderr)
    assert line == "FileNotFoundError: no such file: '<path>' (<path>)"
