"""오선보 엔진(omr_service.staff_engine)을 가짜 Audiveris로 검사한다.

가짜는 Audiveris처럼 `-output <폴더> -- <쪽 그림>`을 받아, 쪽 그림 이름에 따라 고정 MusicXML을 쓰거나(악보),
아무것도 쓰지 않거나(오선 없음), 실패하거나, 오래 걸린다. 실제 Audiveris 실행은 ai/README.md의 컨테이너 확인을 따른다.
변환까지 가는 검사는 jeongganbo-generator가 필요해 JGB_GENERATOR_DIR이 없으면 건너뛴다.
"""

import io
import os
import sys
from concurrent.futures import ThreadPoolExecutor, wait
from fractions import Fraction
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from omr_service import api, staff_engine
from omr_service.api import app, get_fetcher

FIXTURES = Path(__file__).resolve().parents[1] / "fixtures/omr"


def test_tempo_note_inside_numeric_direction_is_masked_but_ledger_note_is_not():
    import zipfile
    from omr_service.staff_engine import tempo_regions
    doc = b'''<sheet><system><sig><inters>
      <sentence role="Direction"><bounds x="100" y="50" w="180" h="20"/></sentence>
      <word value="Moderato"><bounds x="100" y="50" w="70" h="20"/></word>
      <word value="="><bounds x="220" y="50" w="10" h="20"/></word>
      <word value="100"><bounds x="240" y="50" w="40" h="20"/></word>
      <head><bounds x="190" y="55" w="20" h="16"/></head>
      <head><bounds x="310" y="55" w="20" h="16"/></head>
    </inters></sig></system></sheet>'''
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as z:
        z.writestr("sheet#1/sheet#1.xml", doc)
    assert tempo_regions(stream.getvalue()) == [(100, 20, 280, 75)]


def test_numeric_fingering_does_not_mask_notes():
    import zipfile
    from omr_service.staff_engine import tempo_regions
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as z:
        z.writestr("sheet#1/sheet#1.xml", '<sheet><sentence role="Direction"><bounds x="1" y="1" w="20" h="20"/></sentence></sheet>')
    assert tempo_regions(stream.getvalue()) == []


def test_rhythm_check_detects_extra_eighth_but_allows_pickup_and_meter_change():
    from omr_service.staff_engine import rhythm_quality
    def m(beats, duration):
        return (f'<measure><attributes><divisions>4</divisions><time><beats>{beats}</beats>'
                f'<beat-type>8</beat-type></time></attributes><note><pitch><step>C</step><octave>4</octave>'
                f'</pitch><duration>{duration}</duration></note></measure>')
    def doc(*ms):
        return ('<score-partwise><part>' + ''.join(ms) + '</part></score-partwise>').encode()
    assert rhythm_quality([doc(m(9, 6), m(9, 18), m(6, 12))]) == (0, 0, 3)
    count, excess, notes = rhythm_quality([doc(m(9, 19))])
    assert (count, excess, notes) == (1, Fraction(1, 18), 1)


def test_rhythm_refinement_preserves_graces_and_repairs_only_an_overfull_bar():
    from omr_service.staff_engine import refine_rhythm
    def note(step, duration, grace=False):
        return f'<note>{"<grace/>" if grace else ""}<pitch><step>{step}</step><octave>4</octave></pitch><duration>{duration}</duration></note>'
    def doc(notes):
        return ('<score-partwise><part><measure><attributes><divisions>4</divisions><time><beats>9</beats>'
                '<beat-type>8</beat-type></time></attributes>'+notes+'</measure></part></score-partwise>').encode()
    original=doc(note('A',0,True)+note('E',12)+note('G',3)+note('F',2)+note('E',2))
    candidate=doc(note('A',4)+note('E',12)+note('G',3)+note('F',1)+note('E',2))
    fixed=refine_rhythm(original,candidate)
    assert staff_engine.rhythm_quality([fixed]) == (0,0,4)
    import xml.etree.ElementTree as ET
    ns=ET.fromstring(fixed).findall('.//note')
    assert ns[0].find('grace') is not None
    assert [n.findtext('duration') for n in ns[1:]] == ['12','3','1','2']
    # Different pitches and already valid bars are never rewritten.
    assert refine_rhythm(original,candidate.replace(b'<step>F</step>',b'<step>D</step>')) == original
    assert refine_rhythm(fixed,original) == fixed
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


def _quarters(count):
    return b"".join(b"<note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><voice>1</voice></note>"
                    for _ in range(count))


# 4/4인데 가운데 두 마디가 3박으로 읽힌 악보(쉼표를 놓친 그림 PDF처럼). 박자 오독이라 장단을 정하지 않는다.
SHORT_FOUR_FOUR = (b'<?xml version="1.0"?><score-partwise><part id="P1"><measure><attributes><divisions>1</divisions>'
                   b'<time><beats>4</beats><beat-type>4</beat-type></time></attributes>' + _quarters(4) + b"</measure>"
                   + b"".join(b"<measure>" + _quarters(n) + b"</measure>" for n in (3, 3, 4))
                   + b"</part></score-partwise>")


def page_png(mode):
    """가짜 Audiveris가 읽을 표시(mode)를 PNG 끝에 붙인 쪽 그림."""
    buf = io.BytesIO()
    Image.new("RGB", (40, 30), "white").save(buf, "PNG")
    return buf.getvalue() + mode.ljust(12).encode("ascii")


@pytest.fixture(autouse=True)
def capture_omr_logs(caplog):
    # The service intentionally does not propagate to the root logger.
    # Capture its real handler path without changing production logging behavior.
    staff_engine.log.addHandler(caplog.handler)
    try:
        yield
    finally:
        staff_engine.log.removeHandler(caplog.handler)


@pytest.fixture
def fake_audiveris(tmp_path, monkeypatch):
    script = tmp_path / "audiveris.py"
    (tmp_path / "gutgeori.musicxml").write_bytes(GUTGEORI)
    (tmp_path / "four-quarters.musicxml").write_bytes(FOUR_QUARTERS)
    (tmp_path / "short-four-four.musicxml").write_bytes(SHORT_FOUR_FOUR)
    fixtures = {"score": str(FIXTURES / "danso_onara.audiveris.mxl"), "s44": str(FIXTURES / "danso_bukcheon.audiveris.mxl"),
                "s128": str(tmp_path / "gutgeori.musicxml"), "quarters": str(tmp_path / "four-quarters.musicxml"),
                "short44": str(tmp_path / "short-four-four.musicxml")}
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
    assert sum(e["kind"] == "NOTE" for e in out["score"]["events"]) == 63  # 각 경계 지속 3개는 재발음하지 않는다.


@needs_generator
def test_requested_jangdan_wins_and_mismatch_is_flagged(fake_audiveris):
    # 각이 굿거리 4박인 악보를 세마치(3박)로 보내면 세마치로 저장하고 맞지 않는다고 알린다
    out = post("score", jangdan="세마치").json()
    assert out["status"] == "DONE" and out["score"]["music"]["jangdan"] == "세마치"
    assert {"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
            "detail": {"jangdan": "세마치", "jeongganPerGak": 4}} in out["notices"]


@needs_generator
def test_four_quarter_staff_measure_uses_four_beat_accompaniment(fake_audiveris):
    out = post("quarters").json()
    assert out["status"] == "DONE"
    music = out["score"]["music"]
    assert music["jangdan"] == "4박 기본 반주" and music["bpmUnit"] == "QUARTER"
    assert music["ticksPerJeonggan"] == music["ticksPerBeat"]
    assert len(out["score"]["columns"]) == 1
    assert len(out["score"]["columns"][0]["cells"]) == 4
    assert max(e["startTick"] + e["durationTick"] for e in out["score"]["events"]) == 4 * music["ticksPerBeat"]
    assert guessed(out) == [{"source": "timeSignature", "jangdan": "4박 기본 반주"}]
    assert not any(i["kind"] == "JANGDAN_MISMATCH" for i in out["notices"])
    assert "bpm" not in out  # 장단이 있으면 backend가 그 장단의 기본 빠르기를 쓴다


def default_tempo(out):
    return [i["detail"] for i in out["notices"] if i["detail"].get("reason") == "TEMPO_DEFAULT_FROM_TIME_SIGNATURE"]


@needs_generator
def test_unsettled_jangdan_gets_the_time_signature_default_tempo(fake_audiveris):
    # 박자표는 4/4로 읽었지만 마디 길이가 맞지 않아 장단을 비운 악보. 빠르기 표시도 없다(S15P31E102-1035).
    out = post("short44").json()
    assert out["status"] == "DONE" and out["score"]["music"]["jangdan"] == ""
    assert out["score"]["music"]["bpmUnit"] == "QUARTER"
    assert out["bpm"] == 160 and "expressions" not in out
    assert default_tempo(out) == [{"reason": "TEMPO_DEFAULT_FROM_TIME_SIGNATURE", "timeSignature": "4/4",
                                   "jangdan": "4박 기본 반주", "bpm": 160}]


@needs_generator
def test_requested_jangdan_keeps_its_own_default_tempo(fake_audiveris):
    out = post("short44", jangdan="굿거리").json()
    assert out["status"] == "DONE" and "bpm" not in out and default_tempo(out) == []


@needs_generator
def test_recognized_tempo_wins_over_the_time_signature_default(fake_audiveris, tmp_path):
    path = tmp_path / "short-four-four.musicxml"
    path.write_bytes(SHORT_FOUR_FOUR.replace(b'</attributes>', b'</attributes><direction><direction-type>'
        b'<metronome><beat-unit>quarter</beat-unit><per-minute>156</per-minute></metronome>'
        b'</direction-type><sound tempo="156"/></direction>', 1))
    out = post("short44").json()
    assert out["bpm"] == 156 and default_tempo(out) == []


@needs_generator
def test_recognized_numeric_tempo_and_existing_companion_reach_http(fake_audiveris, tmp_path):
    import json, hashlib
    path = tmp_path / 'four-quarters.musicxml'
    path.write_bytes(FOUR_QUARTERS.replace(b'</attributes>', b'</attributes><direction><direction-type>'
        b'<metronome><beat-unit>quarter</beat-unit><per-minute>80</per-minute></metronome>'
        b'</direction-type><sound tempo="80"/></direction>'))
    out = post('quarters').json()
    # A tempo at the start is the score's base BPM, not a section change (S15P31E102-1083).
    assert out['status'] == 'DONE' and out['bpm'] == 80 and 'expressions' not in out

    path.write_bytes(FOUR_QUARTERS.replace(b'</attributes>', b'</attributes><direction><direction-type>'
        b'<metronome><beat-unit>quarter</beat-unit><per-minute>80</per-minute></metronome>'
        b'</direction-type><sound tempo="80"/></direction>').replace(b'</note>', b'</note><direction>'
        b'<direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>120</per-minute></metronome>'
        b'</direction-type><sound tempo="120"/></direction>', 1))
    out = post('quarters').json()
    assert out['status'] == 'DONE' and out['bpm'] == 80
    digest = hashlib.sha256(json.dumps(out['score'], ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
    assert out['expressions'] == {'schema':'common-score-expressions/v1', 'sourceSha256':digest,
        'placements':[], 'performancePlan':{'schema':'danso-performance/v1','input_sha256':digest,
        'tempos':[{'gak':1,'cell':2,'bpm':120}], 'repeats':[]}}


@needs_generator
@pytest.mark.parametrize('source', ['smufl', 'original-opt-in'])
def test_pdf_numeric_tempo_reaches_real_conversion_http(tmp_path, monkeypatch, source):
    """Real PDFium/render/conversion/JGB/HTTP; only download and Audiveris are fixed."""
    import subprocess
    from test_staff_pdf_tempo import text_pdf, archive
    if source == 'original-opt-in':
        supplied = os.environ.get('AUCLAIR_TEMPO_PDF')
        if not supplied:
            pytest.skip('Set AUCLAIR_TEMPO_PDF to exercise the local PDF through HTTP.')
        pdf = Path(supplied).read_bytes()
        top = 500
    else:
        pdf = text_pdf('\ueca5 = 100')
        top = 400
    # One self-authored measure at render scale; this is not an OCR quality test.
    geometry = archive(f'''<sheet><system>
      <stack left="80" right="800"><slot x-offset="620" time-offset="0"/></stack>
      <part><staff><lines><line><point x="80" y="{top}"/></line>
      <line><point x="80" y="{top+20}"/></line></lines></staff></part>
      </system></sheet>''')

    def run(command, deadline):
        out = Path(command[command.index('-output') + 1])
        out.mkdir(parents=True)
        (out / 'fixed.musicxml').write_bytes(FOUR_QUARTERS)
        (out / 'fixed.omr').write_bytes(geometry)
        return subprocess.CompletedProcess(command, 0, b'', b'')

    monkeypatch.setattr(staff_engine, '_run', run)
    monkeypatch.setattr(staff_engine, 'AUDIVERIS_LOGS', tmp_path / 'logs')

    def fetch(url, dest, max_bytes, deadline):
        dest.write_bytes(pdf)

    monkeypatch.setitem(app.dependency_overrides, get_fetcher, lambda: fetch)
    response = TestClient(app).post('/v1/recognitions', json={
        'conversionId':'pdf-tempo-935', 'notation':'STAFF', 'instrument':'DANSO',
        'files':[{'url':'https://example.test/score.pdf', 'contentType':'application/pdf'}]})
    assert response.status_code == 200
    out = response.json()
    # The opening 100 becomes the base BPM and leaves no section tempo (S15P31E102-1083).
    assert out['status'] == 'DONE' and out['bpm'] == 100 and 'expressions' not in out
    assert any(n['detail'].get('reason') == 'TEMPO_PDF_TEXT' for n in out['notices'])
    music = out['score']['music']
    assert music['bpmUnit'] == 'QUARTER'
    assert [(e['startTick']/music['ticksPerBeat'], e['durationTick']/music['ticksPerBeat'])
            for e in out['score']['events']] == [(0,1), (1,1), (2,1), (3,1)]


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
    ("s44", {"source": "timeSignature", "jangdan": "4박 기본 반주"}, False),
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


def test_pdf_tempo_lock_wait_expires_and_releases_the_engine_slot(tmp_path, fake_audiveris):
    pdf = tmp_path / "blank.pdf"
    with api.pdfium.PdfDocument.new() as document:
        page = document.new_page(72, 72)
        page.close()
        document.save(pdf)
    job = api.Job(SimpleNamespace(), "PDF", [pdf], [api.Page(pdf, 72, 72)], .05)
    api.PDFIUM_LOCK.acquire()
    with ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(staff_engine.recognize, job)
        try:
            completed, _ = wait([future], timeout=1)
            assert future in completed, "PDFium lock waiting must consume the request budget"
            with pytest.raises(TimeoutError):
                future.result()
            assert not staff_engine.AUDIVERIS_LOCK.locked()
            assert api.PDFIUM_LOCK.locked(), "The timed-out request must not release another request's lock"
        finally:
            api.PDFIUM_LOCK.release()


def test_timeout_removes_audiveris_logs_before_the_next_request(fake_audiveris, tmp_path, monkeypatch):
    logs = tmp_path / "audiveris-logs"
    logs.mkdir()
    (logs / "synthetic.log").write_text("temporary diagnostic output")
    monkeypatch.setattr(api, "PAGE_TIME_S", 1)
    assert post("slow").json()["failReason"] == "TIMEOUT"
    assert list(logs.glob("*.log")) == []
    assert not staff_engine.AUDIVERIS_LOCK.locked()
    assert post("empty").json()["failReason"] == "NO_STAFF_FOUND"


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
