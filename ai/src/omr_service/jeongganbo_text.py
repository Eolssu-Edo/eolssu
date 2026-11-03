"""글자 층이 있는 정간보 PDF → 공통 악보(score_data@1) 엔진(docs/specs/score-recognition.md '엔진 선택').

쪽마다 data/src/scores/standard_score_text.py로 JG-like 텍스트를 쓰고, jeongganbo-generator의
tools/import-ngc-omr.mjs로 우물사이 문서(.jgb.json)를 만든 뒤 score_data.convert로 바꾼다.
standard_score_text.py는 pymupdf(AGPL)를 쓰므로 이 프로세스에 들이지 않고 별도 프로세스로 돌린다.
"""

import hashlib
import json
import os
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

ENGINE, ENGINE_VERSION = "jeongganbo-text", "1"  # 읽기·변환 규칙을 바꾸면 올린다
CONVERTER = "jeongganbo-text/standard_score_text"
NO_YUL_EXIT = 3  # standard_score_text.py: 쪽에 율명 글자가 없음(글자 층 없음)
JANGDANS = {r["name"]: r for r in json.loads((ROOT / "data/reference/jangdans.json").read_text(encoding="utf-8"))["rows"]}


class NoTextLayer(Exception):
    """율명 글자 층이 없는 PDF다. 정간보 OMR(그림) 경로로 읽어야 한다."""


def beats_per_jeonggan(jangdan: str, jeonggans_per_gak: int) -> Fraction:
    """한 각을 장단 한 번으로 보고 정간 하나의 박(bpm_unit 기준)을 정한다. 세마치 3정간 → 1박, 굿거리 12정간 → 1/3박."""
    row = JANGDANS[jangdan]
    cells = len(row["pattern"]["beats"])  # 세마치·굿거리는 8분음표, 중모리는 4분음표 칸(jangdans.json 설명)
    beats = Fraction(cells, 3) if row["bpm_unit"] == "DOTTED_QUARTER" else Fraction(cells)
    return beats / jeonggans_per_gak


def recognize(job) -> dict:
    """api.Job을 받아 엔진 결과를 돌려준다. 시간을 넘기면 TimeoutError, 글자 층이 없으면 NoTextLayer다."""
    from omr_service.api import DPI, RecognitionFailed  # api가 이 모듈을 불러 순환을 피한다

    generator = os.environ.get("JGB_GENERATOR_DIR")
    if not generator:
        raise RuntimeError("JGB_GENERATOR_DIR is not set")
    deadline = monotonic() + job.time_budget_s
    request = job.request
    with tempfile.TemporaryDirectory(prefix="jeongganbo-text-") as work:
        work = Path(work)
        lines, issues = [], []
        for i, page in enumerate(job.pages):
            out = work / f"page-{i}"
            run = _run([sys.executable, "-X", "utf8", str(SCORES / "standard_score_text.py"), str(job.files[0]),
                        str(out), "--title", "page", "--page", str(i)], deadline)
            if run.returncode == NO_YUL_EXIT:
                raise NoTextLayer()
            if run.returncode != 0:
                raise RecognitionFailed("NO_GRID_FOUND")  # 율명은 있지만 정간 격자를 읽지 못했다
            lines += (out / "page.txt").read_text(encoding="utf-8").splitlines()
            report = json.loads((out / "report.json").read_text(encoding="utf-8"))
            scale, size = DPI / 72, 12  # PDF 좌표(pt) → 쪽 그림 픽셀. 글자 상자는 약 12pt
            issues += [{"kind": "UNREADABLE", "eventId": None, "cellId": None, "page": i,
                        "bbox": [round((g["x"] - size / 2) * scale), round((g["y"] - size / 2) * scale),
                                 round(size * scale), round(size * scale)], "detail": {"text": g["c"]}}
                       for g in report["ignored_at"]]
        (work / "txt").mkdir()
        (work / "txt/score.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
        run = _run(["node", str(Path(generator) / "tools/import-ngc-omr.mjs"), str(work / "txt"), str(work / "jgb")],
                   deadline)
        if run.returncode != 0:
            raise RuntimeError("import-ngc-omr failed")
        jgb_bytes = (work / "jgb/score.jgb.json").read_bytes()
    doc = json.loads(jgb_bytes)
    jangdan = JANGDANS[request.jangdan]
    score = score_data.convert(
        doc, beats_per_jeonggan=beats_per_jeonggan(request.jangdan, int(doc["controls"]["beats"])),
        title=request.conversion_id, instrument=request.instrument, source_id=request.conversion_id,
        jangdan=jangdan["name"], bpm_unit=jangdan["bpm_unit"], review="inferred",
        jgb_sha256=hashlib.sha256(jgb_bytes).hexdigest())
    score["provenance"]["converter"] = CONVERTER
    return {"engine": ENGINE, "engineVersion": ENGINE_VERSION, "jgb": doc, "score": score, "issues": issues}


def _run(cmd: list[str], deadline: float) -> subprocess.CompletedProcess:
    remaining = deadline - monotonic()
    if remaining <= 0:
        raise TimeoutError()
    try:
        return subprocess.run(cmd, capture_output=True, timeout=remaining)  # 넘으면 자식 프로세스를 끝낸다
    except subprocess.TimeoutExpired:
        raise TimeoutError() from None
