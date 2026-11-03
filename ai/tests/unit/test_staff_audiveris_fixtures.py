"""실제 Audiveris 결과로 MusicXML → 정간보 변환을 검사한다(score-omr-feasibility.md 실측 4곡).

고정 자료(tests/fixtures/omr/)
  <곡>.audiveris.mxl     국립국악원 2022 표준악보집 오선보 쪽을 Audiveris 5.11로 읽은 결과(2026-11-03 실측)
  <곡>.answer.jgb.json   같은 곡의 검수된 정간보(팀 묶음 정간보_모음, 소금은 2026-10-26 검토본). 2중주는 단소1

기준은 같은 Audiveris 결과에서 잰 음 간격 일치(실측 표)다. 변환이 그보다 나빠지면 실패한다.
변환까지 가는 검사는 jeongganbo-generator가 필요해 JGB_GENERATOR_DIR이 없으면 건너뛴다.
"""

import difflib
import json
import os
import subprocess
import sys
import tempfile
from fractions import Fraction
from pathlib import Path

import pytest

from omr_service.staff_musicxml import convert

ROOT = Path(__file__).resolve().parents[3]
FIXTURES = Path(__file__).resolve().parents[1] / "fixtures/omr"
sys.path.insert(0, str(ROOT / "data/src/scores"))
import score_data  # noqa: E402

# 곡: (악기, 실측 기준 음 간격 일치, 정답과의 실제 조 차이 반음)
CASES = {"danso_onara": ("DANSO", 59, 6), "danso_bukcheon": ("DANSO", 67, 8), "danso_eoi": ("DANSO", 27, 15),
         "sogeum_onara": ("SOGEUM", 53, 8)}


def notes(doc):
    r = score_data.convert(doc, beats_per_jeonggan=Fraction(1), title="t", instrument="X", source_id="t",
                           jangdan="굿거리", bpm_unit="DOTTED_QUARTER", review="inferred", jgb_sha256="0" * 64)
    return [12 * e["octave"] + "황대태협고중유임이남무응".index(e["yul"]) for e in r["events"] if e["kind"] == "NOTE"]


@pytest.mark.parametrize("name", CASES)
def test_key_is_moved_by_the_measured_amount(name):
    instrument, _, shift = CASES[name]
    out = convert((FIXTURES / f"{name}.audiveris.mxl").read_bytes(), instrument=instrument, hwang_pitch=None)
    assert out.shift == shift


@pytest.mark.skipif(not os.environ.get("JGB_GENERATOR_DIR"), reason="JGB_GENERATOR_DIR(jeongganbo-generator 클론)가 없다")
@pytest.mark.parametrize("name", CASES)
def test_converted_melody_matches_answer_at_least_as_measured(name):
    instrument, baseline, _ = CASES[name]
    out = convert((FIXTURES / f"{name}.audiveris.mxl").read_bytes(), instrument=instrument, hwang_pitch=None)
    with tempfile.TemporaryDirectory() as d:
        (Path(d) / "txt").mkdir()
        (Path(d) / "txt/s.txt").write_text("\n".join(out.lines) + "\n", encoding="utf-8")
        subprocess.run(["node", str(Path(os.environ["JGB_GENERATOR_DIR"]) / "tools/import-ngc-omr.mjs"), f"{d}/txt",
                        f"{d}/jgb"], check=True, capture_output=True)
        got = notes(json.loads((Path(d) / "jgb/s.jgb.json").read_text(encoding="utf-8")))
    want = notes(json.loads((FIXTURES / f"{name}.answer.jgb.json").read_text(encoding="utf-8")))
    gi, wi = [b - a for a, b in zip(got, got[1:])], [b - a for a, b in zip(want, want[1:])]
    blocks = [b for b in difflib.SequenceMatcher(a=wi, b=gi, autojunk=False).get_matching_blocks() if b.size]
    assert sum(b.size for b in blocks) >= baseline
    # 맞은 구간의 율명(옥타브 포함)까지 정답과 같다: 자동 조 옮김이 맞았다
    assert all(want[b.a + k] == got[b.b + k] for b in blocks for k in range(b.size + 1))
