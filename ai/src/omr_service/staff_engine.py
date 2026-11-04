"""오선보 쪽 그림 → 공통 악보 엔진(docs/specs/score-recognition.md '엔진 선택'·'오선보 경로').

쪽마다 Audiveris(AGPL-3.0, 수정 없이 별도 프로세스)로 MusicXML을 만들고, 쪽들을 이어 붙여 staff_musicxml로
JG-like 줄을 만든 뒤 글자 층 경로와 같은 lines_to_score로 .jgb.json·공통 악보가 된다.
"""

import logging
import os
import re
import tempfile
import threading
import xml.etree.ElementTree as ET
from fractions import Fraction
from pathlib import Path
from time import monotonic

from omr_service import staff_musicxml
from omr_service.jeongganbo_text import JANGDANS, _clean, _run, beats_per_jeonggan, lines_to_score

AUDIVERIS = os.environ.get("AUDIVERIS_CMD", "audiveris").split()  # 컨테이너에는 /opt/audiveris/bin/Audiveris
AUDIVERIS_VERSION = os.environ.get("AUDIVERIS_VERSION", "5.11.0")  # 컨테이너 빌드가 넣는다(omr.Dockerfile)
# Audiveris(JVM)는 한 번에 0.7~1GB를 쓰고 홈 폴더 캐시를 함께 쓴다. 겹친 요청은 남은 시간 안에서 기다린다(동시 1건)
AUDIVERIS_LOCK = threading.Lock()
AUDIVERIS_LOGS = Path.home() / ".cache/AudiverisLtd/audiveris/log"  # 실행마다 쌓이는 Audiveris 로그
ENGINE = "audiveris"
DEFAULT_JANGDAN = "굿거리"  # 요청에도 박자표에도 장단이 없을 때(계약: 굿거리 격자로 두고 검수에서 고른다)
UNIT_QUARTERS = {"DOTTED_QUARTER": Fraction(3, 2), "QUARTER": Fraction(1)}  # bpm 단위 한 박의 4분음표 수
log = logging.getLogger("omr_service.api")


def recognize(job) -> dict:
    """api.Job(STAFF) → 엔진 결과. 쪽마다 쪽당 시간(api.PAGE_TIME_S)과 남은 요청 시간 중 짧은 쪽 안에 끝낸다."""
    from omr_service import api  # api가 이 모듈을 불러 순환을 피한다

    deadline = monotonic() + job.time_budget_s
    request = job.request
    remaining = deadline - monotonic()
    if remaining <= 0 or not AUDIVERIS_LOCK.acquire(timeout=remaining):
        raise TimeoutError()
    try:
        pages, issues = _read_pages(job, deadline, api.PAGE_TIME_S)
    finally:
        AUDIVERIS_LOCK.release()
    if not pages:
        raise api.RecognitionFailed("NO_STAFF_FOUND")
    try:
        conv = staff_musicxml.convert(merge_pages(pages), instrument=request.instrument, hwang_pitch=request.hwang_pitch)
    except ValueError as e:  # 파트·음표가 없음
        log.info("staff convert: %s", e)
        raise api.RecognitionFailed("NO_STAFF_FOUND") from None
    issues += conv.issues

    if request.jangdan:
        jangdan = request.jangdan
    else:  # 요청에 장단이 없으면 어떻게 정했든 확인하라고 알린다
        jangdan = conv.jangdan or DEFAULT_JANGDAN
        source = "default" if not conv.jangdan else "rhythm" if conv.meter_guessed else "timeSignature"
        issues.append({"kind": "JANGDAN_GUESSED", "eventId": None, "cellId": None, "page": None, "bbox": None,
                       "detail": {"source": source, "jangdan": jangdan}})
    bpj = conv.beat / UNIT_QUARTERS[JANGDANS[jangdan]["bpm_unit"]]  # 정간 하나를 장단 bpm 단위 박으로
    if bpj * conv.beats_per_gak != beats_per_jeonggan(jangdan, 1):  # 한 각 길이가 장단 한 번과 다르다
        issues.append({"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
                       "detail": {"jangdan": jangdan, "jeongganPerGak": conv.beats_per_gak}})
    doc, score = lines_to_score(conv.lines, request, jangdan, lambda _: bpj, deadline,
                                f"staff-omr/audiveris-{AUDIVERIS_VERSION}")
    return {"engine": ENGINE, "engineVersion": AUDIVERIS_VERSION, "jgb": doc, "score": score, "issues": issues}


def _read_pages(job, deadline, page_time_s):
    """쪽마다 Audiveris를 돌려 (MusicXML 목록, issues).

    Audiveris 5.11은 오선이 없는 쪽에서 .mxl 없이 종료 코드 1로 끝나고 표준 출력에 StepException(No regularly spaced
    lines found·No system found)을 남긴다. 그 쪽만 오선 없는 쪽이다. 악보 없이 끝난 다른 실패(메모리 부족·설정 오류,
    신호로 죽음)는 엔진 오류다. 악보를 냈으면 종료 코드가 0이 아니어도 쓴다.
    """
    pages, issues = [], []
    with tempfile.TemporaryDirectory(prefix="audiveris-") as work:
        for i, page in enumerate(job.pages):
            out = Path(work) / f"page-{i}"
            run = _run(AUDIVERIS + ["-batch", "-transcribe", "-export", "-output", str(out), "--", str(page.path)],
                       min(deadline, monotonic() + page_time_s))
            _forget_logs()
            if run.returncode < 0:
                log.info("audiveris page=%d killed by signal %d", i, -run.returncode)
                raise RuntimeError("audiveris killed")
            sheets = sorted([*out.rglob("*.mxl"), *out.rglob("*.musicxml")], key=_natural)  # 한 쪽에 악곡이 여럿일 수 있다
            if not sheets and b"StepException" not in run.stdout + run.stderr:
                log.info("audiveris page=%d exit=%d without score: %s", i, run.returncode, _reason(run))
                raise RuntimeError("audiveris failed")
            if not sheets:
                log.info("audiveris page=%d no staff: %s", i, _reason(run))
                issues.append({"kind": "UNREADABLE", "eventId": None, "cellId": None, "page": i,
                               "bbox": [0, 0, page.width, page.height], "detail": {"reason": "NO_STAFF"}})
                continue
            if run.returncode != 0:
                log.info("audiveris page=%d exit=%d with score: %s", i, run.returncode, _reason(run))
            pages += [p.read_bytes() for p in sheets]
    return pages, issues


def _reason(run) -> str:
    """Audiveris 출력에서 원인 한 줄. 오류는 표준 출력에 쓴다. 겉포장(Error in reaching step) 대신 Caused by 줄을,
    없으면 오류·예외가 든 마지막 줄을 고른다."""
    lines = [l.strip() for out in (run.stdout, run.stderr) for l in out.decode("utf-8", "replace").splitlines() if l.strip()]
    caused = [l for l in lines if l.startswith("Caused by:")]
    errors = [l for l in lines if "rror" in l or "xception" in l]
    return _clean((caused or errors or lines or [""])[-1])


def _forget_logs():
    """Audiveris가 실행마다 홈 폴더에 남기는 로그를 지운다(컨테이너 쓰기 층에 쌓이지 않게)."""
    for f in AUDIVERIS_LOGS.glob("*.log"):
        f.unlink(missing_ok=True)


def _natural(path: Path):
    """mvt2가 mvt10보다 앞에 오게 숫자를 수로 비교한다."""
    return [int(t) if t.isdigit() else t for t in re.split(r"(\d+)", path.name)]


def merge_pages(sheets: list[bytes]) -> bytes:
    """쪽마다 나온 MusicXML(.mxl 포함)을 파트가 있는 첫 악보의 첫 파트 뒤에 마디로 이어 붙인다."""
    roots = [ET.fromstring(staff_musicxml._xml_bytes(data)) for data in sheets]
    with_part = [r for r in roots if r.find("part") is not None]
    if not with_part:
        return ET.tostring(roots[0])
    first, part = with_part[0], with_part[0].find("part")
    for other in with_part[1:]:
        part.extend(other.find("part").findall("measure"))
    return ET.tostring(first)
