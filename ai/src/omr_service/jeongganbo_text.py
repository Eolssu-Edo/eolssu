"""글자 층이 있는 정간보 PDF → 공통 악보(score_data@1) 엔진(docs/specs/score-recognition.md '엔진 선택').

쪽마다 data/src/scores/standard_score_text.py로 JG-like 텍스트를 쓰고, jeongganbo-generator의
tools/import-ngc-omr.mjs로 우물사이 문서(.jgb.json)를 만든 뒤 score_data.convert로 바꾼다.
standard_score_text.py는 pymupdf(AGPL)를 쓰므로 이 프로세스에 들이지 않고 별도 프로세스로 돌린다.
"""

import hashlib
import json
import logging
import os
import re
import subprocess
import sys
import tempfile
from fractions import Fraction
from pathlib import Path
from time import monotonic

ROOT = Path(__file__).resolve().parents[3]  # ai/src/omr_service → 저장소
SCORES = ROOT / "data/src/scores"
sys.path.insert(0, str(SCORES))
import score_data  # noqa: E402  data/와 같은 변환을 쓴다(중복 구현하지 않음)

ENGINE, ENGINE_VERSION = "jeongganbo-text", "2"  # 읽기·변환 규칙을 바꾸면 올린다
CONVERTER = "jeongganbo-text/standard_score_text@2"
NO_YUL_EXIT, NO_GRID_EXIT = 3, 4  # standard_score_text.py: 율명 글자가 없는 쪽, 정간 격자를 못 읽은 쪽
# 장단과 각의 정간 수가 맞을 때 나오는 정간당 박. 그 밖이면 고른 장단이 악보와 다를 수 있다(JANGDAN_MISMATCH)
USUAL_BEATS = {Fraction(1), Fraction(1, 2), Fraction(1, 3)}
log = logging.getLogger("omr_service.api")  # api의 로그 설정을 함께 쓴다
JANGDANS = {r["name"]: r for r in json.loads((ROOT / "data/reference/jangdans.json").read_text(encoding="utf-8"))["rows"]}
# pattern.beats는 BPM 박이 아닌 구음 칸이다. jangdans.json 설명의 각 칸 음가를 4분음표로 환산한다.
PATTERN_CELL_QUARTERS = {
    "세마치": Fraction(1, 2), "굿거리": Fraction(1, 2), "중모리": Fraction(1),
    "자진모리": Fraction(1, 2), "중중모리": Fraction(1, 2), "엇모리": Fraction(1, 2),
    "단모리": Fraction(1, 2), "타령": Fraction(1, 2), "계면가락도드리": Fraction(1, 2),
    "세령산": Fraction(3, 2), "자진타령": Fraction(1, 2),
}
BPM_UNIT_QUARTERS = {"QUARTER": Fraction(1), "DOTTED_QUARTER": Fraction(3, 2)}


class NoTextLayer(Exception):
    """율명 글자 층이 없는 PDF다. 정간보 OMR(그림) 경로로 읽어야 한다."""


def beats_per_jeonggan(jangdan: str, jeonggans_per_gak: int) -> Fraction:
    """한 각을 장단 한 번으로 보고 정간 하나의 박(bpm_unit 기준)을 정한다. 세마치 3정간 → 1박, 굿거리 12정간 → 1/3박."""
    row = JANGDANS[jangdan]
    cells = len(row["pattern"]["beats"])
    beats = cells * PATTERN_CELL_QUARTERS[jangdan] / BPM_UNIT_QUARTERS[row["bpm_unit"]]
    return beats / jeonggans_per_gak


def recognize(job) -> dict:
    """api.Job을 받아 엔진 결과를 돌려준다. 시간을 넘기면 TimeoutError, 글자 층이 없으면 NoTextLayer다.

    쪽 단위로 읽는다. 율명 글자가 없는 쪽(표지·설명)은 건너뛰고, 정간 격자를 못 읽은 쪽은 UNREADABLE로 남긴다.
    모든 쪽에 율명이 없으면 NoTextLayer, 읽은 쪽이 하나도 없으면 NO_GRID_FOUND다.
    """
    from omr_service.api import DPI, RecognitionFailed  # api가 이 모듈을 불러 순환을 피한다

    generator = os.environ.get("JGB_GENERATOR_DIR")
    if not generator:
        raise RuntimeError("JGB_GENERATOR_DIR is not set")
    deadline = monotonic() + job.time_budget_s
    request = job.request
    with tempfile.TemporaryDirectory(prefix="jeongganbo-text-") as work:
        work = Path(work)
        lines, issues, no_yul = [], [], []
        scale = DPI / 72  # PDF 좌표(pt) → 쪽 그림 픽셀
        for i, page in enumerate(job.pages):
            out = work / f"page-{i}"
            run = _run([sys.executable, "-X", "utf8", str(SCORES / "standard_score_text.py"), str(job.files[0]),
                        str(out), "--title", "page", "--page", str(i)], deadline)
            whole_page = {"eventId": None, "cellId": None, "page": i, "bbox": [0, 0, page.width, page.height]}
            if run.returncode == NO_YUL_EXIT:
                no_yul.append(whole_page)
                continue
            if run.returncode == NO_GRID_EXIT:
                log.info("jeongganbo-text page=%d no grid: %s", i, _last_line(run.stderr))
                issues.append({"kind": "UNREADABLE", **whole_page, "detail": {"reason": "NO_GRID"}})
                continue
            if run.returncode != 0:
                log.info("jeongganbo-text page=%d reader failed: %s", i, _last_line(run.stderr))
                raise RuntimeError("standard_score_text failed")
            first_gak = len(lines)  # 쪽을 이어 붙이므로 결과 악보의 각 번호는 앞 쪽 각 수만큼 밀린다
            lines += (out / "page.txt").read_text(encoding="utf-8").splitlines()
            report = json.loads((out / "report.json").read_text(encoding="utf-8"))
            issues += [{"kind": "UNREADABLE", "eventId": None, "page": i,
                        "cellId": f"c{first_gak + g['gak']}-j{g['jeonggan']}",
                        "bbox": [round((g["x"] - g["size"] / 2) * scale), round((g["y"] - g["size"] / 2) * scale),
                                 round(g["size"] * scale), round(g["size"] * scale)], "detail": {"text": g["c"]}}
                       for g in report["ignored_at"]]
            if report["voices"] > 1:  # 2중주 이상 쪽은 첫 성부만 읽었다
                issues.append({"kind": "VOICE_GUESSED", **whole_page, "detail": {"voices": report["voices"], "kept": 1}})
            issues += [{"kind": "UNREADABLE", "eventId": None, "cellId": None, "page": i,  # 정간 수가 달라 뺀 열
                        "bbox": [round(c[k] * scale) for k in ("x", "y", "w", "h")],
                        "detail": {"reason": "NOT_SCORE_COLUMN"}} for c in report["dropped"]]
        if not lines:
            if len(no_yul) == len(job.pages):
                raise NoTextLayer()
            raise RecognitionFailed("NO_GRID_FOUND")
        # 율명 글자가 없는 쪽(표지·설명이거나 글자 층 없이 스캔한 악보 쪽)은 건너뛰었다고 알린다
        issues += [{"kind": "UNREADABLE", **p, "detail": {"reason": "NO_TEXT"}} for p in no_yul]

    def bpj_of(doc):
        per_gak = int(doc["controls"]["beats"])
        bpj = beats_per_jeonggan(request.jangdan, per_gak)
        seryeongsan_cell = request.jangdan == "세령산" and bpj == Fraction(3, 2)
        if bpj not in USUAL_BEATS and not seryeongsan_cell:
            issues.append({"kind": "JANGDAN_MISMATCH", "eventId": None, "cellId": None, "page": None, "bbox": None,
                           "detail": {"jangdan": request.jangdan, "jeongganPerGak": per_gak}})
        return bpj

    doc, score = lines_to_score(lines, request, request.jangdan, bpj_of, deadline, CONVERTER)
    return {"engine": ENGINE, "engineVersion": ENGINE_VERSION, "jgb": doc, "score": score, "issues": issues}


def lines_to_score(lines, request, jangdan_name, bpj_of, deadline, converter):
    """JG-like 줄 → (.jgb.json 문서, 공통 악보). 글자 층·오선보 경로가 같이 쓴다.

    jeongganbo-generator import-ngc-omr.mjs로 문서를 만들고 score_data.convert로 바꾼다. bpj_of(문서)가 정간 하나의 박이다.
    """
    generator = os.environ.get("JGB_GENERATOR_DIR")
    if not generator:
        raise RuntimeError("JGB_GENERATOR_DIR is not set")
    with tempfile.TemporaryDirectory(prefix="jgb-") as work:
        work = Path(work)
        (work / "txt").mkdir()
        (work / "txt/score.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
        run = _run(["node", str(Path(generator) / "tools/import-ngc-omr.mjs"), str(work / "txt"), str(work / "jgb")],
                   deadline)
        if run.returncode != 0:
            log.info("import-ngc-omr failed: %s", _last_line(run.stderr))
            raise RuntimeError("import-ngc-omr failed")
        jgb_bytes = (work / "jgb/score.jgb.json").read_bytes()
    doc = json.loads(jgb_bytes)
    jangdan = JANGDANS[jangdan_name]
    try:
        score = score_data.convert(
            doc, beats_per_jeonggan=bpj_of(doc), title=request.conversion_id, instrument=request.instrument,
            source_id=request.conversion_id, jangdan=jangdan["name"], bpm_unit=jangdan["bpm_unit"],
            review="inferred", jgb_sha256=hashlib.sha256(jgb_bytes).hexdigest())
    except ValueError as e:  # 예: 율명 없는 독립 시김새는 score_data가 아직 받지 않는다
        log.info("convert failed: %s", _clean(str(e)))
        raise
    score["provenance"]["converter"] = converter
    return doc, score


def _clean(text: str) -> str:
    """로그용. 파일 경로를 지운다(임시 폴더 이름 등)."""
    # 앞에 글자·숫자가 없는 '/'로 시작하는 경로만 지운다. '12/8'·'1/3' 같은 박자·비율은 남긴다
    return re.sub(r"[A-Za-z]:[\\/][^\s'\")]*|(?<![\w/])/[^\s'\")]+", "<path>", text)[:200]


def _last_line(stderr: bytes) -> str:
    """하위 프로세스 오류의 마지막 줄(예외 종류와 이유). 경로는 지운다."""
    text = stderr.decode("utf-8", "replace").strip()
    return _clean(text.splitlines()[-1]) if text else ""


def _run(cmd: list[str], deadline: float) -> subprocess.CompletedProcess:
    remaining = deadline - monotonic()
    if remaining <= 0:
        raise TimeoutError()
    try:
        return subprocess.run(cmd, capture_output=True, timeout=remaining)  # 넘으면 자식 프로세스를 끝낸다
    except subprocess.TimeoutExpired:
        raise TimeoutError() from None
