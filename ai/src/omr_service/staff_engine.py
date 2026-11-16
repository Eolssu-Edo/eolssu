"""오선보 쪽 그림 → 공통 악보 엔진(docs/specs/score-recognition.md '엔진 선택'·'오선보 경로').

쪽마다 Audiveris(AGPL-3.0, 수정 없이 별도 프로세스)로 MusicXML을 만들고, 쪽들을 이어 붙여 staff_musicxml로
JG-like 줄을 만든 뒤 글자 층 경로와 같은 lines_to_score로 .jgb.json·공통 악보가 된다.
"""

import logging
import io
import hashlib
import json
from copy import deepcopy
from dataclasses import replace
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
UNIT_QUARTERS = {"DOTTED_QUARTER": Fraction(3, 2), "QUARTER": Fraction(1)}  # bpm 단위 한 박의 4분음표 수
# 검증 표본의 5/8·7/8 오독을 검수로 보내는 보수적 가드다. 정확도 보증 임계값이 아니다.
TIME_SIGNATURE_REVIEW_GRADE = 0.65
# Audiveris 5.11은 오선 간격을 너무 작게 재면 이 경고 뒤 쪽을 버린다(StepException: Sheet ignored).
LOW_INTERLINE = re.compile(rb"too low interline value of (\d+) pixels")
# 확대 목표 간격(픽셀). Audiveris 권장 300dpi에서 보통 오선 간격 1.75mm가 약 20.7픽셀인 데서 잡은 값이다. 정확도 보증값이 아니다.
TARGET_INTERLINE = 20
# 간격을 잘못 잰 사진을 흑백으로 다시 읽을 때의 긴 변 상한. 운영 실패 사진 원본 1장 실측(이 PC)에서 1500·2448은
# 채팅 사진 결과와 음높이 68/68, 2000은 55/68, 원본 크기는 70초였다. 가장 빠른 1500을 쓴다. 표본 1장의 값이다.
GRAY_LONG_SIDE = 1500
MIN_ENLARGEMENT = 1.1  # 최대 화소 때문에 이보다 덜 키울 수밖에 없으면 다시 읽어도 같다고 보고 건너뛴다
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
        try:
            _forget_logs()
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
    else:
        uncertain_meter = any(i['detail'].get('reason') == 'TIME_SIGNATURE_REVIEW_REQUIRED' for i in issues)
        jangdan = (conv.jangdan or "") if not uncertain_meter else ""
        if jangdan:
            source = "rhythm" if conv.meter_guessed else "timeSignature"
            issues.append({"kind": "JANGDAN_GUESSED", "eventId": None, "cellId": None, "page": None, "bbox": None,
                           "detail": {"source": source, "jangdan": jangdan}})
    bpm_unit = JANGDANS[jangdan]['bpm_unit'] if jangdan else conv.bpm_unit
    bpj = conv.beat / UNIT_QUARTERS[bpm_unit]
    if jangdan and any(bpj * width != beats_per_jeonggan(jangdan, 1) for width in conv.gak_beats):
        issues.append({"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
                       "detail": {"jangdan": jangdan, "jeongganPerGak": conv.beats_per_gak}})
    doc, score = lines_to_score(conv.lines, request, jangdan, lambda _: bpj, deadline,
                                f"staff-omr/audiveris-{AUDIVERIS_VERSION}", continue_explicit_ties=True,
                                gak_beats=conv.gak_beats, bpm_unit=bpm_unit)
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
    pdf_marks = staff_pdf_tempo.read_marks(job, with_bounds=True, deadline=deadline)
    with tempfile.TemporaryDirectory(prefix="audiveris-") as work:
        for i, page in enumerate(job.pages):
            out = Path(work) / f"page-{i}"
            page_began = monotonic()
            page_deadline = min(deadline, monotonic() + page_time_s)
            command = AUDIVERIS + ["-batch", *AUDIVERIS_OPTIONS, "-transcribe", "-export", "-output"]
            run = _run(command + [str(out), "--", str(page.path)], page_deadline)
            first_issue, scale = len(issues), 1.0
            retried = _retry_low_interline(command, out, run, page, Path(work) / f"interline-{i}", page_deadline, page_index=i)
            if retried:
                out, run, page, scale, issue = retried
                issues.append(issue)
                if i < len(pdf_marks):  # PDF 글자 좌표를 확대한 쪽 그림에 맞춰 다시 읽는다
                    pages_now = [*job.pages[:i], page, *job.pages[i + 1:]]
                    pdf_marks[i] = staff_pdf_tempo.read_marks(replace(job, pages=pages_now), with_bounds=True,
                                                              deadline=deadline)[i]
            first_run_s = monotonic() - page_began
            input_path = page.path
            archives = [p.read_bytes() for p in out.rglob('*.omr')]
            issues += [issue for archive in archives for issue in time_signature_reviews(archive, page=i)]
            region_groups = [[box] for archive in archives for box in tempo_regions(archive)]
            sheets = sorted([*out.rglob("*.mxl"), *out.rglob("*.musicxml")], key=_natural)
            pdf_regions = []
            if i < len(pdf_marks) and len(sheets) == len(archives) == 1:
                pdf_regions = staff_pdf_tempo.tuplet_regions(sheets[0].read_bytes(), archives[0], pdf_marks[i])
                region_groups += pdf_regions
            if region_groups:
                # OCR 빠르기의 잘못 붙은 음표 또는 PDF 빠르기 숫자로 확인된 잇단음표를
                # 작업용 그림에서 지우고 한 번 다시 읽는다. 원본과 글자 사이 음은 보존한다.
                cleaned = Path(work) / f"cleaned-{i}.png"
                with Image.open(page.path) as original:
                    image = original.convert("RGB")
                    draw = ImageDraw.Draw(image)
                    for box in (box for group in region_groups for box in group):
                        draw.rectangle(box, fill="white")
                    image.save(cleaned)
                original_out, original_run = out, run
                out = Path(work) / f"retry-{i}"
                run = _run(command + [str(out), "--", str(cleaned)], page_deadline)
                input_path = cleaned
                if pdf_regions and run.returncode >= 0:
                    candidates = sorted([*out.rglob("*.mxl"), *out.rglob("*.musicxml")], key=_natural)
                    if (len(candidates) != 1 or staff_pdf_tempo.tuplet_regions(
                            candidates[0].read_bytes(), archives[0], pdf_marks[i]) != pdf_regions):
                        # A changed measure count or new conflicting tempo would
                        # prevent restoration. Keep the original recognition.
                        out, run, input_path = original_out, original_run, page.path
                        region_groups = []
                        issues.append(staff_musicxml._issue('LOW_CONFIDENCE', {
                            'reason': 'TEMPO_TUPLET_RETRY_REJECTED'}))
                regions = [(min(b[0] for b in group), min(b[1] for b in group),
                            max(b[2] for b in group), max(b[3] for b in group)) for group in region_groups]
                issues += [{"kind": "LOW_CONFIDENCE", "eventId": None, "cellId": None, "page": i,
                            "bbox": [x, y, right - x, bottom - y],
                            "detail": {"reason": "TEMPO_MARK_REMOVED"}} for x, y, right, bottom in regions]
            if run.returncode >= 0 and page_deadline - monotonic() > first_run_s * 1.2:
                out, run, input_path, lyric_issues = _retry_without_lyrics(
                    command, out, run, input_path, Path(work) / f"lyrics-{i}", page_deadline, page=i)
                issues += lyric_issues
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
                _restore_scale(issues[first_issue:], i, scale, job.pages[i])
                continue
            if run.returncode != 0:
                log.info("audiveris page=%d exit=%d with score: %s", i, run.returncode, _reason(run))
            data = [p.read_bytes() for p in sheets]
            if i < len(pdf_marks) and pdf_marks[i] and len(data) == len(archives) == 1:
                data[0], count = staff_pdf_tempo.supplement(data[0], archives[0], pdf_marks[i])
                if count:
                    issues.append(staff_musicxml._issue('LOW_CONFIDENCE', {'reason': 'TEMPO_PDF_TEXT', 'count': count}))
            _restore_scale(issues[first_issue:], i, scale, job.pages[i])
            pages += data
    return pages, issues


def time_signature_reviews(archive: bytes, *, page: int) -> list[dict]:
    """Keep Audiveris's low-confidence time signature and original pixel bounds for review."""
    issues = []
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if not re.fullmatch(r'sheet#\d+/sheet#\d+\.xml', name):
                continue
            root = ET.fromstring(z.read(name))
            for el in root.iter():
                if el.tag not in ('time-whole', 'time-pair') or not el.get('time-rational'):
                    continue
                try:
                    grade = float(el.get('grade', 'nan'))
                    bounds = el.find('bounds')
                    box = [int(bounds.get(k)) for k in ('x', 'y', 'w', 'h')]
                except (AttributeError, TypeError, ValueError):
                    continue
                if 0 <= grade < TIME_SIGNATURE_REVIEW_GRADE:
                    issues.append({**staff_musicxml._issue('LOW_CONFIDENCE', {
                        'reason': 'TIME_SIGNATURE_REVIEW_REQUIRED', 'timeSignature': el.get('time-rational'),
                        'grade': grade, 'staff': el.get('staff')}), 'page': page, 'bbox': box})
    return issues


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


def _retry_without_lyrics(command, out, run, input_path, retry_out, deadline, *, page):
    """가사 줄을 가린 그림으로 한 번 다시 읽는다. 마디 수가 같고 박자와 맞지 않는 마디가 늘지 않을 때만 쓴다.

    선택 단계이므로 시간 초과·실패·악화는 앞 결과를 그대로 돌려준다.
    """
    archives = [p.read_bytes() for p in out.rglob("*.omr")]
    sheets = sorted([*out.rglob("*.mxl"), *out.rglob("*.musicxml")], key=_natural)
    if len(archives) != 1 or not sheets:
        return out, run, input_path, []
    boxes = lyric_regions(archives[0])
    if not boxes:
        return out, run, input_path, []
    masked = retry_out.with_suffix(".png")
    with Image.open(input_path) as original:
        image = original.convert("RGB")
        draw = ImageDraw.Draw(image)
        for box in boxes:
            draw.rectangle(box, fill="white")
        image.save(masked)
    try:
        retry = _run(command + [str(retry_out), "--", str(masked)], deadline)
    except TimeoutError:
        log.info("audiveris page=%d lyric retry skipped: timeout", page)
        return out, run, input_path, []
    candidates = sorted([*retry_out.rglob("*.mxl"), *retry_out.rglob("*.musicxml")], key=_natural)
    before = rhythm_mismatches([p.read_bytes() for p in sheets])
    after = rhythm_mismatches([p.read_bytes() for p in candidates]) if candidates else None
    if retry.returncode < 0 or after is None or after[0] != before[0] or after[1] > before[1]:
        log.info("audiveris page=%d lyric retry kept original: %s -> %s", page, before, after)
        return out, run, input_path, []
    issues = [{"kind": "LOW_CONFIDENCE", "eventId": None, "cellId": None, "page": page,
               "bbox": [x0, y0, x1 - x0, y1 - y0], "detail": {"reason": "LYRICS_REMOVED"}}
              for x0, y0, x1, y1 in boxes]
    return retry_out, retry, masked, issues


def _retry_low_interline(command, out, run, page, work: Path, deadline, *, page_index):
    """Audiveris가 오선 간격을 너무 작게 재 쪽을 버렸으면 흑백으로, 그래도 작다고 하면 확대해 다시 읽는다.

    운영에서 실패한 폰 사진 원본(2448×3264)은 컬러로는 간격 3픽셀로 재져 버려졌고 흑백으로는 24픽셀로 읽혔다.
    흑백 원본 크기는 처리가 오래 걸려(이 PC 70초) 긴 변 GRAY_LONG_SIDE 안으로 줄여 읽는다. 악보가 작게 찍혀
    그래도 간격이 작다고 하면 그 크기에서 잰 간격이 TARGET_INTERLINE이 되게(최대 화소 안) 확대한다(S15P31E102-1002).
    악보가 나왔을 때만 (출력 폴더, 실행 결과, 다시 읽은 쪽, 원래 쪽 대비 배율, notice)를, 아니면 None을 돌려준다.
    """
    match = LOW_INTERLINE.search(run.stdout + run.stderr)
    if run.returncode < 0 or not match or _sheets(out):
        return None
    first_interline = interline = int(match.group(1))
    with Image.open(page.path) as original:
        already_gray, gray = original.mode in ("1", "L"), original.convert("L")
    limit = (Image.MAX_IMAGE_PIXELS / (page.width * page.height)) ** 0.5 if Image.MAX_IMAGE_PIXELS else float("inf")
    measured_at = 1.0  # interline을 잰 그림의 원래 쪽 대비 배율
    scale = round(min(1.0, GRAY_LONG_SIDE / max(page.width, page.height)), 2)
    if already_gray and scale == 1:
        scale = None  # 같은 그림을 다시 읽지 않고 바로 확대한다
    for _ in range(2):
        if scale is None:
            scale = round(min(measured_at * TARGET_INTERLINE / max(interline, 1), limit), 2)
            if scale < measured_at * MIN_ENLARGEMENT:
                log.info("audiveris page=%d low interline %d: no room to enlarge", page_index, interline)
                return None
        size = (round(page.width * scale), round(page.height * scale))
        path, retry_out = work.with_name(f"{work.name}-x{scale}.png"), work.with_name(f"{work.name}-x{scale}")
        (gray if size == gray.size else gray.resize(size, Image.Resampling.LANCZOS)).save(path)
        try:
            retry = _run(command + [str(retry_out), "--", str(path)], deadline)
        except TimeoutError:
            log.info("audiveris page=%d low interline retry x%s skipped: timeout", page_index, scale)
            return None
        if retry.returncode >= 0 and _sheets(retry_out):
            log.info("audiveris page=%d low interline %d: read again in grayscale x%s", page_index, first_interline, scale)
            issue = {"kind": "LOW_CONFIDENCE", "eventId": None, "cellId": None, "page": page_index, "bbox": None,
                     "detail": {"reason": "LOW_INTERLINE_RETRY", "interline": first_interline, "scale": scale}}
            return retry_out, retry, type(page)(path, *size), scale, issue
        log.info("audiveris page=%d grayscale x%s still without score: %s", page_index, scale, _reason(retry))
        match = LOW_INTERLINE.search(retry.stdout + retry.stderr)
        if retry.returncode < 0 or not match:
            return None  # 오선이 없다거나 엔진이 실패했다. 확대해도 같다
        measured_at, interline, scale = scale, int(match.group(1)), None
    return None


def _sheets(out: Path) -> list[Path]:
    return sorted([*out.rglob("*.mxl"), *out.rglob("*.musicxml")], key=_natural)


def _restore_scale(issues: list[dict], page_index: int, scale: float, page) -> None:
    """확대한 그림 기준의 bbox를 원래 쪽 픽셀 좌표로 되돌린다. backend는 쪽 크기 밖 bbox를 거부하므로 쪽 안으로 자른다."""
    if scale == 1:
        return
    for issue in issues:
        if issue.get("page") != page_index or not issue.get("bbox"):
            continue
        x, y, w, h = (round(v / scale) for v in issue["bbox"])
        x, y = min(max(x, 0), page.width), min(max(y, 0), page.height)
        issue["bbox"] = [x, y, min(max(w, 0), page.width - x), min(max(h, 0), page.height - y)]


def rhythm_mismatches(sheets: list[bytes]):
    """(악곡별 마디 수, 처음·끝 마디를 뺀 박자표와 길이가 다른 마디 수). 못갖춘마디·끝 불완전마디는 세지 않는다."""
    shape, count = [], 0
    for data in sheets:
        part = ET.fromstring(staff_musicxml._xml_bytes(data)).find("part")
        measures = [] if part is None else part.findall("measure")
        shape.append(len(measures))
        div, meter = Fraction(1), None
        for k, measure in enumerate(measures):
            initial_div = div
            for attrs in measure.findall("attributes"):
                div, _ = staff_musicxml._attributes(attrs, div, 0)
                if attrs.find("time") is not None:
                    meter = staff_musicxml._meter(attrs.find("time"))
            if (0 < k < len(measures) - 1 and meter
                    and staff_musicxml._measure_length(measure, initial_div) != meter[1] * meter[2]):
                count += 1
    return tuple(shape), count


# 가사 줄 판정(줄 간격 배수). legacy 영어 OCR은 가타카나·한글 가사를 글자로 읽지 못해 잇단음표 숫자·악센트·
# 스타카토·음표 머리 등으로 분류한다. 오선 맨 아랫줄에서 LYRIC_GAP 안쪽(첫 덧줄 음)은 건드리지 않는다.
LYRIC_GAP = 1.5
LYRIC_CENTER = 2.5  # 후보 기호의 중심이 맨 아랫줄에서 이만큼 아래에 있어야 한다
LYRIC_LINE = 3.0  # 한 글줄의 최대 높이. 겹침으로 여러 줄이 이어 붙지 않게 한다
LYRIC_PAD = 0.5  # 인식되지 않은 글자 조각까지 가리도록 글줄 위아래에 더하는 여유
LYRIC_MIN_GLYPHS = 3  # 띄엄띄엄 한두 개인 아래쪽 기호는 가사로 보지 않는다
LYRIC_ANCHORS = {"ledger", "stem", "beam", "flag", "rest", "barline", "clef", "key", "time-number", "time-whole",
                 "time-pair", "repeat-dot"}  # 오선에 딸린 구조. 가리지 않는다
LYRIC_IGNORED = {"head-chord", "rest-chord", "small-chord", "beam-group", "staff-barline", "sentence", "word",
                 "slur", "ending", "wedge"}  # 묶음·글자·곡선은 후보로 세지 않는다


def lyric_regions(archive: bytes) -> list[tuple[int, int, int, int]]:
    """각 단의 마지막 오선 아래 가사 줄에서 Audiveris가 악보 기호로 읽은 글자 영역(x0, y0, x1, y1).

    오선·기둥·덧줄과 떨어진 기호가 LYRIC_MIN_GLYPHS개 이상 같은 높이에 늘어선 줄만 가사로 본다.
    그 줄을 오선 너비로 가리되 덧줄 음·기둥·쉼표 등 구조 기호의 가로 구간은 남긴다. 단 안의 오선 사이는 보지 않는다.
    """
    boxes = []
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if re.fullmatch(r"sheet#\d+/sheet#\d+\.xml", name):
                boxes += _sheet_lyric_regions(ET.fromstring(z.read(name)))
    return boxes


def _sheet_lyric_regions(root) -> list[tuple[int, int, int, int]]:
    scale = root.find("scale/interline")
    try:
        il = float(scale.get("main"))
    except (AttributeError, TypeError, ValueError):
        return []
    picture = root.find("picture")
    page_bottom = float(picture.get("height")) if picture is not None and picture.get("height") else float("inf")

    systems = []  # (위, 아래, 왼쪽, 오른쪽): 단의 첫 오선 윗줄과 마지막 오선 아랫줄
    for system in root.iter("system"):
        staves = []
        for staff in system.iter("staff"):
            ys = [sum(float(p.get("y")) for p in line.findall("point")) / max(1, len(line.findall("point")))
                  for line in staff.findall("lines/line")]
            if ys and staff.get("left") and staff.get("right"):
                staves.append((min(ys), max(ys), float(staff.get("left")), float(staff.get("right"))))
        if staves:
            systems.append((min(s[0] for s in staves), max(s[1] for s in staves),
                            min(s[2] for s in staves), max(s[3] for s in staves)))
    systems.sort()

    inters = []
    for el in root.iter():
        b = el.find("bounds")
        if b is None or el.tag in LYRIC_IGNORED or el.tag in ("staff", "glyph"):
            continue
        try:
            x, y, w, h = (float(b.get(k)) for k in ("x", "y", "w", "h"))
        except (TypeError, ValueError):
            continue
        inters.append((el.tag, (x, y, x + w, y + h)))

    def near(a, b, pad):
        return a[0] - pad < b[2] and b[0] - pad < a[2] and a[1] - pad < b[3] and b[1] - pad < a[3]

    anchors = [box for tag, box in inters if tag in LYRIC_ANCHORS]
    supports = [box for tag, box in inters if tag in ("stem", "ledger")]
    anchors += [box for tag, box in inters if tag == "head" and any(near(box, s, il / 2) for s in supports)]
    loose = [box for tag, box in inters if tag not in LYRIC_ANCHORS and box not in anchors]

    regions = []
    for k, (_, bottom, left, right) in enumerate(systems):
        lo = bottom + LYRIC_GAP * il
        hi = (systems[k + 1][0] if k + 1 < len(systems) else page_bottom) - LYRIC_GAP * il
        found = sorted((b for b in loose if lo <= b[1] and b[3] <= hi and (b[1] + b[3]) / 2 >= bottom + LYRIC_CENTER * il
                        and not any(near(b, a, il) for a in anchors)), key=lambda b: b[1])
        rows = []  # 세로 범위가 겹치는 기호를 한 글줄로 묶는다(점·탁점처럼 작은 조각도 같은 줄에 들어간다)
        for b in found:
            if rows and b[1] < rows[-1][2] and max(rows[-1][2], b[3]) - rows[-1][1] <= LYRIC_LINE * il:
                rows[-1] = (rows[-1][0] + [b], rows[-1][1], max(rows[-1][2], b[3]))
            else:
                rows.append(([b], b[1], b[3]))
        for row, row_top, row_bottom in rows:
            if len(row) < LYRIC_MIN_GLYPHS:
                continue
            top = max(lo, row_top - LYRIC_PAD * il)
            low = min(hi, row_bottom + LYRIC_PAD * il)
            keep = sorted((a[0] - il / 2, a[2] + il / 2) for a in anchors if a[1] < low and top < a[3])
            start = left
            for x0, x1 in keep + [(right, right)]:
                if x0 > start:
                    regions.append((int(start), int(top), int(min(x0, right)), int(low) + 1))
                start = max(start, x1)
                if start >= right:
                    break
    return regions


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
