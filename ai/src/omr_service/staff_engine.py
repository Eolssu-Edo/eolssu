"""오선보 쪽 그림 → 공통 악보 엔진(docs/specs/score-recognition.md '엔진 선택'·'오선보 경로').

쪽마다 Audiveris(AGPL-3.0, 수정 없이 별도 프로세스)로 MusicXML을 만들고, 쪽들을 이어 붙여 staff_musicxml로
JG-like 줄을 만든 뒤 글자 층 경로와 같은 lines_to_score로 .jgb.json·공통 악보가 된다.
"""

import logging
import io
import hashlib
import json
from copy import deepcopy
from functools import lru_cache
import os
import re
import tempfile
import threading
import xml.etree.ElementTree as ET
import zipfile
from fractions import Fraction
from pathlib import Path
from time import monotonic

from PIL import Image, ImageDraw

from omr_service import staff_musicxml, staff_pdf_tempo
from omr_service.jeongganbo_text import JANGDANS, _clean, _run, beats_per_jeonggan, lines_to_score

AUDIVERIS = os.environ.get("AUDIVERIS_CMD", "audiveris").split()  # 컨테이너에는 /opt/audiveris/bin/Audiveris
AUDIVERIS_VERSION = os.environ.get("AUDIVERIS_VERSION", "5.11.0")  # 컨테이너 빌드가 넣는다(omr.Dockerfile)
AUDIVERIS_OPTIONS = ["-constant", "org.audiveris.omr.sheet.ProcessingSwitches.smallHeads=true",
                     "-constant", "org.audiveris.omr.sheet.ProcessingSwitches.smallBeams=true",
                     # 30 간격보다 짧은 마지막 악단도 인식한다(AuClair 15~16마디).
                     "-constant", "org.audiveris.omr.sheet.grid.LinesRetriever.minStaffLength=20"]
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
    restore_source_pitches(score, conv.source_pitches, request.hwang_pitch)
    tempo = {}
    if conv.tempos:
        digest = hashlib.sha256(json.dumps(score, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        tempo['expressions'] = {'schema': 'common-score-expressions/v1', 'sourceSha256': digest, 'placements': [],
            'performancePlan': {'schema': f"{request.instrument.lower().replace('_', '-')}-performance/v1",
                                'input_sha256': digest, 'tempos': conv.tempos, 'repeats': []}}
        if conv.quarter_bpm is not None:
            bpm = conv.quarter_bpm / UNIT_QUARTERS[score['music']['bpmUnit']]
            if bpm.denominator == 1 and 0 < bpm <= 2147483647:
                tempo['bpm'] = int(bpm)
            else:
                issues.append(staff_musicxml._issue('LOW_CONFIDENCE', {'reason': 'TEMPO_BASE_NOT_INTEGER'}))
    return {"engine": ENGINE, "engineVersion": AUDIVERIS_VERSION, "jgb": doc, "score": score, "issues": issues,
            "sourcePitches": conv.source_pitches, **tempo}


def restore_source_pitches(score, originals, hwang_pitch):
    """Common-score drafts retain original pitches, even when their glyph is unavailable."""
    hwang = staff_musicxml.DEFAULT_HWANG if hwang_pitch is None else hwang_pitch
    score['music']['hwangHz'] = 440 * 2 ** ((hwang - 69) / 12)
    by_id = {item['eventId']: item for item in originals}
    unresolved = set()
    for event in score['events']:
        if event['kind'] != 'NOTE' or event['id'] not in by_id:
            continue
        semitone = by_id[event['id']]['midi'] - hwang
        event['yul'], event['octave'] = staff_musicxml.YUL[semitone % 12], semitone // 12
        if not -2 <= event['octave'] <= 2:
            unresolved.add(event['id'])
    for column in score['columns']:
        for cell in column['cells']:
            for unit in cell['units']:
                if unit['eventId'] in unresolved and unit['symbol'] != '-':
                    unit['symbol'] = ''  # editable unresolved slot, not an octave-clamped note


def _read_pages(job, deadline, page_time_s):
    """쪽마다 Audiveris를 돌려 (MusicXML 목록, issues).

    Audiveris 5.11은 오선이 없는 쪽에서 .mxl 없이 종료 코드 1로 끝나고 표준 출력에 StepException(No regularly spaced
    lines found·No system found)을 남긴다. 그 쪽만 오선 없는 쪽이다. 악보 없이 끝난 다른 실패(메모리 부족·설정 오류,
    신호로 죽음)는 엔진 오류다. 악보를 냈으면 종료 코드가 0이 아니어도 쓴다.
    """
    pages, issues = [], []
    pdf_marks = staff_pdf_tempo.read_marks(job)
    with tempfile.TemporaryDirectory(prefix="audiveris-") as work:
        for i, page in enumerate(job.pages):
            out = Path(work) / f"page-{i}"
            page_began = monotonic()
            page_deadline = min(deadline, monotonic() + page_time_s)
            command = AUDIVERIS + ["-batch", *AUDIVERIS_OPTIONS, "-transcribe", "-export", "-output"]
            run = _run(command + [str(out), "--", str(page.path)], page_deadline)
            input_path = page.path
            archives = [p.read_bytes() for p in out.rglob('*.omr')]
            regions = [box for archive in archives for box in tempo_regions(archive)]
            if regions:
                # 빠르기 표시의 음표가 성부 음으로 붙은 경우만, OCR로 확인한 영역을
                # 별도 쪽 그림에서 지우고 다시 읽는다. 원본과 진짜 덧줄 음은 보존한다.
                cleaned = Path(work) / f"cleaned-{i}.png"
                with Image.open(page.path) as original:
                    image = original.convert("RGB")
                    draw = ImageDraw.Draw(image)
                    for box in regions:
                        draw.rectangle(box, fill="white")
                    image.save(cleaned)
                out = Path(work) / f"retry-{i}"
                run = _run(command + [str(out), "--", str(cleaned)], page_deadline)
                input_path = cleaned
                issues += [{"kind": "LOW_CONFIDENCE", "eventId": None, "cellId": None, "page": i,
                            "bbox": [x, y, right - x, bottom - y],
                            "detail": {"reason": "TEMPO_MARK_REMOVED"}} for x, y, right, bottom in regions]
            _forget_logs()
            if run.returncode < 0:
                log.info("audiveris page=%d killed by signal %d", i, -run.returncode)
                raise RuntimeError("audiveris killed")
            sheets = sorted([*out.rglob("*.mxl"), *out.rglob("*.musicxml")], key=_natural)  # 한 쪽에 악곡이 여럿일 수 있다
            quality = rhythm_quality([p.read_bytes() for p in sheets])
            if quality[0] and page_deadline - monotonic() > (monotonic() - page_began) * 1.5:
                with Image.open(input_path) as original:
                    size = (round(original.width * 1.25), round(original.height * 1.25))
                    if size[0] * size[1] <= Image.MAX_IMAGE_PIXELS:
                        scaled = Path(work) / f"scaled-{i}.png"
                        original.convert("RGB").resize(size, Image.Resampling.LANCZOS).save(scaled)
                    else:
                        scaled = None
                if scaled is not None:
                    retry_out = Path(work) / f"rhythm-{i}"
                    retry = _run(command + [str(retry_out), "--", str(scaled)], page_deadline)
                    candidates = sorted([*retry_out.rglob("*.mxl"), *retry_out.rglob("*.musicxml")], key=_natural)
                    refined_ok = False
                    if len(candidates) == len(sheets):
                        refined = [refine_rhythm(a.read_bytes(), b.read_bytes()) for a, b in zip(sheets, candidates)]
                        if rhythm_quality(refined)[:2] < quality[:2]:
                            refined_ok = True
                            for j, data in enumerate(refined):
                                p = Path(work) / f"refined-{i}-{j}.musicxml"
                                p.write_bytes(data)
                                candidates[j] = p
                    candidate_quality = rhythm_quality([p.read_bytes() for p in candidates])
                    if (retry.returncode >= 0 and refined_ok
                            and candidate_quality[:2] < quality[:2]):
                        sheets, run = candidates, retry
                        issues.append({"kind": "LOW_CONFIDENCE", "eventId": None, "cellId": None, "page": i,
                                       "bbox": None, "detail": {"reason": "RHYTHM_RECOGNITION_RETRY"}})
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
            data = [p.read_bytes() for p in sheets]
            if i < len(pdf_marks) and pdf_marks[i] and len(data) == len(archives) == 1:
                data[0], count = staff_pdf_tempo.supplement(data[0], archives[0], pdf_marks[i])
                if count:
                    issues.append(staff_musicxml._issue('LOW_CONFIDENCE', {'reason': 'TEMPO_PDF_TEXT', 'count': count}))
            pages += data
    return pages, issues


def rhythm_quality(sheets: list[bytes]):
    """명시한 박자보다 긴 마디의 수·초과 비율·본음 수. 못갖춘마디는 초과가 아니다."""
    count, excess, notes = 0, Fraction(0), 0
    for data in sheets:
        root = ET.fromstring(staff_musicxml._xml_bytes(data))
        part = root.find("part")
        if part is None:
            continue
        div, meter = Fraction(1), None
        for measure in part.findall("measure"):
            initial_div = div
            for attrs in measure.findall("attributes"):
                div, _ = staff_musicxml._attributes(attrs, div, 0)
                if attrs.find("time") is not None:
                    meter = staff_musicxml._meter(attrs.find("time"))
            length = staff_musicxml._measure_length(measure, initial_div)
            notes += sum(n.find("pitch") is not None and n.find("grace") is None for n in measure.findall("note"))
            if meter and length > meter[1] * meter[2]:
                count += 1
                excess += length / (meter[1] * meter[2]) - 1
    return count, excess, notes


def refine_rhythm(original: bytes, candidate: bytes) -> bytes:
    """한 선율의 초과 마디만 교정한다. 원본의 본음·꾸밈음 구분과 모든 음고를 보존한다.

    재인식에서 꾸밈음이 빠지거나 본음으로 바뀌어도 원본의 꾸밈음 순서와 본음을
    유일하게 대응할 수 있을 때만 음가를 가져온다. 대응이 모호하면 원본을 쓴다.
    """
    root, other = (ET.fromstring(staff_musicxml._xml_bytes(b)) for b in (original, candidate))
    part, other_part = root.find("part"), other.find("part")
    if part is None or other_part is None or len(part.findall("measure")) != len(other_part.findall("measure")):
        return original
    div, other_div, meter, changed = Fraction(1), Fraction(1), None, False
    for measure, retry in zip(part.findall("measure"), other_part.findall("measure")):
        for attrs in measure.findall("attributes"):
            div, _ = staff_musicxml._attributes(attrs, div, 0)
            if attrs.find("time") is not None:
                meter = staff_musicxml._meter(attrs.find("time"))
        for attrs in retry.findall("attributes"):
            other_div, _ = staff_musicxml._attributes(attrs, other_div, 0)
        if not meter or staff_musicxml._measure_length(measure, div) <= meter[1] * meter[2]:
            continue
        if any(m.find(tag) is not None for m in (measure, retry) for tag in ("backup", "forward", "note/chord", "note/rest")):
            continue  # 다성부의 대응을 한 선율로 추정하지 않는다.
        source, target = measure.findall("note"), retry.findall("note")
        if max(len(source), len(target)) > 128 or any(n.find("pitch") is None for n in source + target):
            continue
        if len({n.findtext("voice", "1") for n in source + target}) != 1:
            continue
        groups, graces = [], []
        for n in source:
            if n.find("grace") is not None:
                graces.append(staff_musicxml._midi(n, 0))
            else:
                groups.append((tuple(graces), n))
                graces = []
        pitches = [staff_musicxml._midi(n, 0) for n in target]

        def subsequence(values, allowed):
            it = iter(allowed)
            return all(any(x == value for x in it) for value in values)

        @lru_cache(None)
        def align(g, start):
            if g == len(groups):
                return ((),) if subsequence(pitches[start:], graces) else ()
            allowed, n = groups[g]
            found = []
            for j in range(start, len(target)):
                if not subsequence(pitches[start:j], allowed):
                    break
                if pitches[j] == staff_musicxml._midi(n, 0) and target[j].findtext("duration") and target[j].find("grace") is None:
                    found += [(j, *tail) for tail in align(g + 1, j + 1)]
                    if len(found) > 1:
                        return tuple(found[:2])
            return tuple(found)

        matches = align(0, 0)
        if len(matches) != 1:
            continue
        fixed = deepcopy(measure)
        main = [n for n in fixed.findall("note") if n.find("grace") is None]
        for n, j in zip(main, matches[0]):
            duration = Fraction(int(target[j].findtext("duration"))) * div / other_div
            if duration <= 0 or duration.denominator != 1:
                break
            n.find("duration").text = str(int(duration))
            for tag in ("type", "dot", "time-modification"):
                for old in n.findall(tag):
                    n.remove(old)
                for new in target[j].findall(tag):
                    n.append(deepcopy(new))
        else:
            if staff_musicxml._measure_length(fixed, div) == meter[1] * meter[2]:
                part[list(part).index(measure)] = fixed
                changed = True
    return ET.tostring(root) if changed else original


def tempo_regions(archive: bytes) -> list[tuple[int, int, int, int]]:
    """Audiveris가 숫자 빠르기 지시문 안의 기호를 일반 음표로 읽은 영역.

    5.11 OCR은 '♩ = 100'의 '='를 긴 대시로 읽기도 한다. 숫자만 있는 운지,
    지시문 바깥의 덧줄 음, 이미 인식된 metronome은 대상이 아니다.
    """
    boxes = []
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if not re.fullmatch(r"sheet#\d+/sheet#\d+\.xml", name):
                continue
            root = ET.fromstring(z.read(name))
            words = [(w.get("value", ""), w.find("bounds")) for w in root.iter("word")]
            heads = [h.find("bounds") for h in root.iter("head")]
            for sentence in root.iter("sentence"):
                if sentence.get("role") != "Direction" or sentence.find("bounds") is None:
                    continue
                b = sentence.find("bounds")
                x, y, w, h = (int(b.get(k)) for k in ("x", "y", "w", "h"))
                inside = lambda a: a is not None and x <= int(a.get("x")) < x + w and y <= int(a.get("y")) < y + h
                text = " ".join(t for t, bounds in words if inside(bounds))
                if not re.search(r"[=—−-]\s*(?:[2-9]\d|[1-3]\d\d|400)\b", text):
                    continue
                if any(inside(a) for a in heads):
                    boxes.append((x, max(0, y - round(h * 1.5)), x + w, y + h + round(h / 4)))
    return boxes


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
