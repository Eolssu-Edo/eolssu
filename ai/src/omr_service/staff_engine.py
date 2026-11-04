"""오선보 쪽 그림 → 공통 악보 엔진(docs/specs/score-recognition.md '엔진 선택'·'오선보 → 정간보 기본값').

쪽마다 Audiveris(AGPL-3.0, 수정 없이 별도 프로세스)로 MusicXML을 만들고, 쪽들을 이어 붙여 staff_musicxml로
JG-like 줄을 만든 뒤 글자 층 경로와 같은 lines_to_score로 .jgb.json·공통 악보가 된다.
"""

import logging
import os
import tempfile
import xml.etree.ElementTree as ET
from fractions import Fraction
from pathlib import Path
from time import monotonic

from omr_service import staff_musicxml
from omr_service.jeongganbo_text import JANGDANS, _last_line, _run, beats_per_jeonggan, lines_to_score

AUDIVERIS = os.environ.get("AUDIVERIS_CMD", "audiveris").split()  # 컨테이너에는 /usr/bin/audiveris
AUDIVERIS_VERSION = os.environ.get("AUDIVERIS_VERSION", "5.11.0")  # 컨테이너 빌드가 넣는다(omr.Dockerfile)
ENGINE = "audiveris"
DEFAULT_JANGDAN = "굿거리"  # 요청에도 박자표에도 장단이 없을 때(계약: 굿거리 격자로 두고 검수에서 고른다)
UNIT_QUARTERS = {"DOTTED_QUARTER": Fraction(3, 2), "QUARTER": Fraction(1)}  # bpm 단위 한 박의 4분음표 수
log = logging.getLogger("omr_service.api")


def recognize(job) -> dict:
    """api.Job(STAFF) → 엔진 결과. 쪽마다 쪽당 시간(api.PAGE_TIME_S)과 남은 요청 시간 중 짧은 쪽 안에 끝낸다."""
    from omr_service import api  # api가 이 모듈을 불러 순환을 피한다

    deadline = monotonic() + job.time_budget_s
    request = job.request
    pages, issues = [], []
    with tempfile.TemporaryDirectory(prefix="audiveris-") as work:
        for i, page in enumerate(job.pages):
            out = Path(work) / f"page-{i}"
            run = _run(AUDIVERIS + ["-batch", "-transcribe", "-export", "-output", str(out), "--", str(page.path)],
                       min(deadline, monotonic() + api.PAGE_TIME_S))
            if run.returncode != 0:
                log.info("audiveris page=%d exit=%d: %s", i, run.returncode, _last_line(run.stderr))
                raise RuntimeError("audiveris failed")
            sheets = sorted(out.rglob("*.mxl")) + sorted(out.rglob("*.musicxml"))  # 한 쪽에 악곡이 여럿이면 여러 개
            if not sheets:  # 오선을 찾지 못한 쪽
                issues.append({"kind": "UNREADABLE", "eventId": None, "cellId": None, "page": i,
                               "bbox": [0, 0, page.width, page.height], "detail": {"reason": "NO_STAFF"}})
                continue
            pages += [p.read_bytes() for p in sheets]
    if not pages:
        raise api.RecognitionFailed("NO_STAFF_FOUND")
    try:
        conv = staff_musicxml.convert(merge_pages(pages), instrument=request.instrument, hwang_pitch=request.hwang_pitch)
    except ValueError as e:  # 파트·음표가 없음
        log.info("staff convert: %s", e)
        raise api.RecognitionFailed("NO_STAFF_FOUND") from None
    issues += conv.issues

    jangdan = request.jangdan or conv.jangdan or DEFAULT_JANGDAN
    if not request.jangdan and not conv.jangdan and not any(i["kind"] == "JANGDAN_GUESSED" for i in issues):
        issues.append({"kind": "JANGDAN_GUESSED", "eventId": None, "cellId": None, "page": None, "bbox": None,
                       "detail": {"timeSignature": "other", "jangdan": jangdan}})
    bpj = conv.beat / UNIT_QUARTERS[JANGDANS[jangdan]["bpm_unit"]]  # 정간 하나를 장단 bpm 단위 박으로
    if bpj * conv.beats_per_gak != beats_per_jeonggan(jangdan, 1):  # 한 각 길이가 장단 한 번과 다르다
        issues.append({"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
                       "detail": {"jangdan": jangdan, "jeongganPerGak": conv.beats_per_gak}})
    doc, score = lines_to_score(conv.lines, request, jangdan, lambda _: bpj, deadline,
                                f"staff-omr/audiveris-{AUDIVERIS_VERSION}")
    return {"engine": ENGINE, "engineVersion": AUDIVERIS_VERSION, "jgb": doc, "score": score, "issues": issues}


def merge_pages(sheets: list[bytes]) -> bytes:
    """쪽마다 나온 MusicXML(.mxl 포함)을 첫 쪽의 첫 파트 뒤에 마디로 이어 붙인다."""
    first = ET.fromstring(staff_musicxml._xml_bytes(sheets[0]))
    part = first.find("part")
    for data in sheets[1:]:
        other = ET.fromstring(staff_musicxml._xml_bytes(data)).find("part")
        if part is not None and other is not None:
            part.extend(other.findall("measure"))
    return ET.tostring(first)
