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
sys.path.insert(0, str(ROOT / "data/src"))
from test_standard_score_text import score_pdf  # noqa: E402  data와 같은 합성 정간보를 쓴다
from playback.common_score import convert_common_score  # noqa: E402

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


@pytest.mark.parametrize("jangdan, per_gak, beats", [
    ("세마치", 3, Fraction(1)), ("굿거리", 12, Fraction(1, 3)), ("중모리", 12, Fraction(1)),
    ("자진모리", 4, Fraction(1)), ("중중모리", 4, Fraction(1)), ("자진타령", 4, Fraction(1)),
    ("엇모리", 10, Fraction(1, 2)), ("단모리", 4, Fraction(1)), ("타령", 12, Fraction(1, 2)),
    ("계면가락도드리", 12, Fraction(1, 2)), ("세령산", 10, Fraction(3, 2)),
    ("4박 기본 반주", 4, Fraction(1)),
])
def test_beats_per_jeonggan_follows_jangdan(jangdan, per_gak, beats):
    # catalog 설명의 소박 음가: 8분음표, 중모리는 4분음표, 세령산은 점4분음표.
    assert jeongganbo_text.beats_per_jeonggan(jangdan, per_gak) == beats


@needs_generator
@pytest.mark.parametrize("jangdan, per_gak, mismatch", [("세마치", 2, True), ("세령산", 10, False)])
def test_dotted_quarter_cell_exception_is_limited_to_seryeongsan(jangdan, per_gak, mismatch, monkeypatch):
    # 두 장단 모두 정간당 3/2박이지만, 점4분 칸인 세령산만 정상 배치다.
    if per_gak == 2:
        monkeypatch.setattr("test_standard_score_text.JG_H", 60)  # 두 칸 열도 격자 검출 최소 높이를 채운다.
    out = post(pdf_bytes([[["林"] * per_gak, ["林"] * per_gak]]), jangdan=jangdan).json()
    assert out["status"] == "DONE"
    assert any(n["kind"] == "JANGDAN_MISMATCH" for n in out["notices"]) is mismatch


@needs_generator
def test_danmori_pdf_keeps_four_quarter_beats_per_measure_in_synthesized_timeline():
    # 격자 검출의 최소 두 열: 4박 두 마디가 각각 2초여야 한다.
    out = post(pdf_bytes([[["林", "南", "潢", "汰"], ["林", "南", "潢", "汰"]]]), jangdan="단모리").json()
    assert out["status"] == "DONE"
    rendered, timing = convert_common_score(out["score"], bpm=120, instrument="sogeum", input_sha256="0" * 64)
    assert timing["events"][4]["start_seconds"] == 2
    assert timing["score_seconds"] == 4
    assert [e["start_seconds"] for e in timing["events"]] == [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5]
    assert [e["duration_seconds"] for e in timing["events"]] == [0.5] * 8
    assert rendered["quarter_bpm"] == 120
    assert out["notices"] == []
    assert out["engineVersion"] == "4"
    assert out["score"]["provenance"]["converter"] == "jeongganbo-text/standard_score_text@4"


@needs_generator
def test_text_layer_pdf_becomes_common_score():
    out = post(pdf_bytes(SEMACHI)).json()
    assert out["status"] == "DONE" and out["engine"] == "jeongganbo-text"
    score = out["score"]
    assert score["provenance"] | {"jgbSha256": None} == {"sourceId": "c-7", "jgbSha256": None, "review": "inferred",
                                                          "converter": "jeongganbo-text/standard_score_text@4"}
    assert score["music"]["jangdan"] == "세마치" and score["music"]["bpmUnit"] == "DOTTED_QUARTER"
    tpb = score["music"]["ticksPerBeat"]
    assert score["music"]["ticksPerJeonggan"] == tpb  # 세마치 3정간 각이라 정간 하나 = 1박
    notes = [(e["kind"], e["yul"], e["octave"], e["startTick"] / tpb, e["durationTick"] / tpb) for e in score["events"]]
    # 임 1박, 남 2박(이음), 청황 2박(빈 정간이 이어짐), 청태 1박
    assert notes == [("NOTE", "임", 0, 0, 1), ("NOTE", "남", 0, 1, 2), ("NOTE", "황", 1, 3, 2), ("NOTE", "태", 1, 5, 1)]
    assert out["issues"] == [] == out["notices"] and out["jgb"]["controls"]["beats"] == "3"


@needs_generator
def test_pdf_explicit_ties_cross_gak_and_rest_only_gak_keeps_its_place():
    out = post(pdf_bytes([[["林", "-", "-", "-"], ["-", "-", "-", "-"],
                          ["△", "△", "△", "△"], ["仲", "林", "南", "潢"]]]), jangdan="단모리").json()
    assert out["status"] == "DONE"
    score = out["score"]
    assert len(score["columns"]) == 4
    tpb = score["music"]["ticksPerBeat"]
    first = score["events"][0]
    assert (first["kind"], first["yul"], first["durationTick"]) == ("NOTE", "임", 8 * tpb)
    assert all(u["eventId"] == first["id"] for c in score["columns"][1]["cells"] for u in c["units"])
    assert [(e["kind"], e["startTick"] // tpb) for e in score["events"][1:5]] == [("REST", x) for x in range(8, 12)]
    assert score["events"][5]["startTick"] == 12 * tpb


@needs_generator
def test_glyph_that_is_not_a_note_becomes_issue_at_its_place():
    tiers = [[["林", "南", "-"], ["潢", "ㄱ", "汰"]]]  # 시김새 글자 ㄱ은 읽지 않는다
    out = post(pdf_bytes(tiers)).json()
    assert out["status"] == "DONE"
    (issue,) = out["issues"]
    # 글자 층 읽기가 NFKC로 맞춰 한글 자모 ㄱ(U+3131)은 첫소리 ᄀ(U+1100)이 된다
    assert issue["kind"] == "UNREADABLE" and issue["detail"] == {"text": "ᄀ"} and issue["page"] == 0
    assert issue["cellId"] == "c1-j1"  # 오른쪽 둘째 열(각 1)의 가운데 정간
    assert issue["cellId"] in {c["id"] for col in out["score"]["columns"] for c in col["cells"]}
    x, y, w, h = issue["bbox"]
    # 글자 크기 10.5pt를 300dpi 쪽 그림 픽셀로 옮긴 정사각형
    assert 0 < x < out["pages"][0]["width"] and 0 < y < out["pages"][0]["height"] and w == h == round(10.5 * 300 / 72)


def test_pdf_without_text_layer_is_left_to_omr(monkeypatch, tmp_path):
    """율명 글자가 없는 PDF는 실제 모델을 단위 검사에서 실행하지 않고 스캔 엔진에 위임한다."""
    monkeypatch.setenv("JGB_GENERATOR_DIR", "unused")
    path = tmp_path / "scan.pdf"
    path.write_bytes(pdf_bytes(SEMACHI, no_text=True))
    request = api.RecognitionIn(conversionId="c-7", notation="JEONGGANBO", jangdan="세마치", instrument="SOGEUM",
                                files=[{"url": "https://r2.example/s.pdf", "contentType": "application/pdf"}])
    job = api.Job(request, "PDF", [path], [api.Page(path, 1, 1)], 30)
    with pytest.raises(jeongganbo_text.NoTextLayer):
        jeongganbo_text.recognize(job)
    seen = []
    def no_grid(job):
        seen.append(job)
        raise api.RecognitionFailed("NO_GRID_FOUND")
    monkeypatch.setattr(api.jeongganbo_scan, "recognize", no_grid)
    assert post(path.read_bytes()).json()["failReason"] == "NO_GRID_FOUND"
    assert len(seen) == 1 and seen[0].kind == "PDF" and len(seen[0].pages) == 1


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
    assert out["issues"] == []
    (issue,) = out["notices"]
    assert (issue["kind"], issue["page"], issue["detail"]) == ("UNREADABLE", 0, {"reason": "NO_TEXT"})


@needs_generator
def test_page_with_unreadable_grid_is_marked_and_others_are_kept():
    out = post(pages_pdf((SEMACHI, {}), (SEMACHI, {"no_lines": True}))).json()
    assert out["status"] == "DONE" and len(out["score"]["events"]) == 4
    (issue,) = out["notices"]
    assert issue["kind"] == "UNREADABLE" and issue["page"] == 1 and issue["detail"] == {"reason": "NO_GRID"}
    assert issue["bbox"] == [0, 0, out["pages"][1]["width"], out["pages"][1]["height"]]


@needs_generator
def test_duet_keeps_first_voice_and_says_so():
    tiers = [[["林", "南", "-"], ["仲", "林", "-"], ["潢", "汰", "-"], ["南", "仲", "-"]]]
    out = post(pdf_bytes(tiers, labels=["단소1", "단소2", "단소1", "단소2"])).json()
    assert out["status"] == "DONE"
    assert [e["yul"] for e in out["score"]["events"]] == ["임", "남", "황", "태"]
    assert [i["kind"] for i in out["notices"]] == ["VOICE_GUESSED"] and out["notices"][0]["detail"] == {"voices": 2, "kept": 1}


@needs_generator
def test_jangdan_that_does_not_fit_the_gak_is_flagged():
    # 3정간 각을 굿거리(4박)로 보내면 정간 하나가 4/3박이 된다
    out = post(pdf_bytes(SEMACHI), jangdan="굿거리").json()
    assert out["status"] == "DONE"
    assert out["notices"] == [{"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
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
    (issue,) = out["notices"]
    assert issue["kind"] == "UNREADABLE" and issue["detail"] == {"reason": "NOT_SCORE_COLUMN"}
    x, y, w, h = issue["bbox"]
    assert x > out["pages"][0]["width"] / 2 and w == round(36 * 300 / 72) and h == round(4 * 45 * 300 / 72)


@needs_generator
def test_short_last_gak_keeps_its_own_length():
    # 곡 끝 각이 짧은 악보(소금 취타)는 각별 정간 수(gakBeats)로 그 각만 짧게 읽는다(S15P31E102-404 score_data)
    cols = [["林", "南", "-", "-"], ["潢", "汰", "-", "-"], ["仲", "-", "△"]]
    out = post(pdf_bytes([cols])).json()
    assert out["status"] == "DONE" and out["jgb"]["controls"]["gakBeats"] == "3:3"
    starts = {e["cellId"]: e["startTick"] for e in out["score"]["events"]}
    assert starts["c1-j0"] == 12 and starts["c2-j0"] == 24 and starts["c2-j2"] == 30  # 정간당 3틱, 4·4·3정간


def test_logged_errors_have_no_paths():
    stderr = "Traceback\nFileNotFoundError: no such file: 'C:\\tmp\\x\\s.pdf' (/tmp/y)\n".encode()
    line = jeongganbo_text._last_line(stderr)
    assert line == "FileNotFoundError: no such file: '<path>' (<path>)"


@needs_generator
def test_unreadable_glyph_on_a_later_page_points_to_the_cell_after_earlier_pages():
    second = [[["林", "南", "-"], ["潢", "ㄱ", "汰"]]]
    out = post(pages_pdf((SEMACHI, {}), (second, {}))).json()
    assert [i["cellId"] for i in out["issues"]] == ["c3-j1"]  # 첫 쪽 2각 뒤의 둘째 각
