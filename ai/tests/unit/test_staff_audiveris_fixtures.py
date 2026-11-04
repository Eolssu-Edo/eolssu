"""실제 Audiveris 결과로 MusicXML → 정간보 변환을 검사한다(score-omr-feasibility.md 실측 4곡).

고정 자료(tests/fixtures/omr/)
  <곡>.audiveris.mxl     국립국악원 2022 표준악보집 오선보 쪽을 Audiveris 5.11로 읽은 결과(2026-11-03 실측)
  <곡>.answer.jgb.json   같은 곡의 검수된 정간보(팀 묶음 정간보_모음, 소금은 2026-10-26 검토본). 2중주는 단소1

변환 결과 줄(JG-like)을 여기서 직접 읽어 정답과 음 간격·율명·길이를 비교한다. 생성기 없이 돈다.
기준은 313 U4 변환 결과(score-omr-feasibility.md 표)다. 그보다 나빠지면 실패한다.
"""

import difflib
import os
import json
import statistics
import sys
from fractions import Fraction
from pathlib import Path

import pytest

from omr_service.staff_musicxml import OCTAVE, YUL, convert

ROOT = Path(__file__).resolve().parents[3]
FIXTURES = Path(__file__).resolve().parents[1] / "fixtures/omr"
sys.path.insert(0, str(ROOT / "data/src/scores"))
import score_data  # noqa: E402

# 곡: (악기, 정답과의 실제 조 차이 반음, 음 간격 일치 기준, 맞은 구간 길이 일치 기준, 결과 음 수 상한)
# 음 수 상한은 313 U4 결과 그대로다. 어이얼어자리는 Audiveris가 두 성부를 한 성부로 섞어 정답(28음)보다 많다
CASES = {"danso_onara": ("DANSO", 6, 61, 58, 66), "danso_bukcheon": ("DANSO", 8, 67, 70, 73),
         "danso_eoi": ("DANSO", 15, 27, 28, 57), "sogeum_onara": ("SOGEUM", 8, 53, 50, 56)}
PREFIX = {v: k for k, v in OCTAVE.items()}


def onset(pos):
    """JG-like 위치 번호 → 정간 안 시작(박). 3행 1~9, 2행 10·11 통째와 12~15 좌우."""
    if pos == 0:
        return Fraction(0)
    if pos <= 9:
        row, side = divmod(pos - 1, 3)
        return Fraction(row, 3) + (Fraction(1, 6) if side == 2 else 0)
    return {10: 0, 11: Fraction(1, 2), 12: 0, 13: Fraction(1, 4), 14: Fraction(1, 2), 15: Fraction(3, 4)}[pos]


def semitone(name):
    for prefix in sorted(PREFIX, key=len, reverse=True):
        if name.startswith(prefix) and name[len(prefix):] in YUL and len(name) == len(prefix) + 1:
            return 12 * PREFIX[prefix] + YUL.index(name[len(prefix):])
    raise ValueError(name)


def read_lines(lines):
    """각마다 시작을 모아 (반음 또는 None, 길이). 소리는 다음 시작이나 각 끝까지(score_data와 같음)."""
    out = []
    for line in lines:
        cells = line.split("|")
        starts = []
        for j, cell in enumerate(cells):
            toks = cell.split()
            for pos, name in zip(toks[::2], toks[1::2]):
                if name != "-":
                    starts.append((j + onset(int(pos[1:])), None if name == "쉼표" else semitone(name)))
        starts.sort(key=lambda s: s[0])
        for (at, s), nxt in zip(starts, [s[0] for s in starts[1:]] + [len(cells)]):
            out.append((s, nxt - at))
    return [n for n in out if n[0] is not None]


def answer(name):
    doc = json.loads((FIXTURES / f"{name}.answer.jgb.json").read_text(encoding="utf-8"))
    r = score_data.convert(doc, beats_per_jeonggan=Fraction(1), title="t", instrument="X", source_id="t",
                           jangdan="굿거리", bpm_unit="DOTTED_QUARTER", review="inferred", jgb_sha256="0" * 64)
    tpb = r["music"]["ticksPerBeat"]
    return [(12 * e["octave"] + YUL.index(e["yul"]), Fraction(e["durationTick"], tpb))
            for e in r["events"] if e["kind"] == "NOTE"]


@pytest.mark.parametrize("name", CASES)
def test_converted_melody_matches_answer_at_least_as_before(name):
    instrument, shift, interval_floor, duration_floor, note_ceiling = CASES[name]
    out = convert((FIXTURES / f"{name}.audiveris.mxl").read_bytes(), instrument=instrument, hwang_pitch=None)
    assert out.shift == shift
    got, want = read_lines(out.lines), answer(name)
    assert len(got) <= note_ceiling  # 반복을 두 번 펼치는 것처럼 음이 늘면 실패한다
    gi, wi = [b[0] - a[0] for a, b in zip(got, got[1:])], [b[0] - a[0] for a, b in zip(want, want[1:])]
    blocks = [b for b in difflib.SequenceMatcher(a=wi, b=gi, autojunk=False).get_matching_blocks() if b.size]
    assert sum(b.size for b in blocks) >= interval_floor
    pairs = [(want[b.a + k], got[b.b + k]) for b in blocks for k in range(b.size + 1)]
    assert all(w[0] == g[0] for w, g in pairs)  # 맞은 구간은 율명·옥타브까지 같다(조 옮김이 맞다)
    ratio = statistics.median(g[1] / w[1] for w, g in pairs)  # 정간 하나의 박이 정답과 다를 수 있다
    assert sum(g[1] == w[1] * ratio for w, g in pairs) >= duration_floor


@pytest.mark.skipif(not os.environ.get("JGB_GENERATOR_DIR"), reason="JGB_GENERATOR_DIR(jeongganbo-generator 클론)가 없다")
@pytest.mark.parametrize("name", CASES)
def test_cell_issues_point_to_cells_of_the_converted_score(name):
    # 실제 Audiveris 결과에서도 칸에 건 issue가 생성기를 거친 결과 악보의 칸을 가리킨다
    from time import monotonic
    from types import SimpleNamespace

    from omr_service.jeongganbo_text import lines_to_score

    out = convert((FIXTURES / f"{name}.audiveris.mxl").read_bytes(), instrument=CASES[name][0], hwang_pitch=None)
    request = SimpleNamespace(conversion_id="c-1", instrument=CASES[name][0])
    _, score = lines_to_score(out.lines, request, "굿거리", lambda doc: Fraction(1), monotonic() + 30, "test")
    ids = {c["id"] for col in score["columns"] for c in col["cells"]}
    placed = [i["cellId"] for i in out.issues if i["cellId"]]
    assert set(placed) <= ids
