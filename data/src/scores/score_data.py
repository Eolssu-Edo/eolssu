"""사람이 확정한 우물사이 문서(.jgb.json)를 공통 악보 데이터로 바꾼다.

정간 읽기: 선율의 한 줄이 각, `|`가 정간 경계다. 정간 안에서 띄어 쓴 낱말이 위→아래 줄이고,
한 줄에 이어 쓴 음 단위는 그 줄을 왼→오른쪽으로 똑같이 나눈다(우물사이·검토 화면과 같은 문법).
시간은 곡 장단의 bpm_unit 박으로 세며, 계산 중에는 반올림이 없도록 Fraction으로 둔다.

이벤트 만들기: 율명·쉼표가 새 이벤트를 열고, 이음(-)과 빈 정간은 앞 이벤트의 길이에 더한다.
숨표·시김새는 그 글자가 속한 이벤트에 붙는다.
"""
from dataclasses import dataclass, replace
from fractions import Fraction
import re

YUL = r"(?:하하배|하배|중청|배|청)?[황대태협고중유임이남무응]"
# 율명 앞말 → 옥타브 번호(backend V1 pitches.octave: -1 배, 0 중, 1 청, 2 중청)
OCTAVE = {"하하배": -3, "하배": -2, "배": -1, "": 0, "청": 1, "중청": 2}
# 음 단위 = 율명·이음·쉼표 + 숨표 + 시김새({…}·(…)·[…]). review_page.html의 UNIT과 같다.
UNIT = re.compile(rf"({YUL}|-|쉼표|쉼)?(<)?((?:[{{(\[][^{{}}()\[\]]+[}})\]])*)")
SIGIMSAE = re.compile(r"[{(\[]([^{}()\[\]]+)[})\]]")


@dataclass(frozen=True)
class Unit:
    """정간 안 글자 하나. symbol은 율명·'-'(이음)·'쉼'·''(빈 정간)이다."""
    cell_id: str
    column: int
    index: int
    row: int
    col: int
    symbol: str
    breath: bool
    sigimsae: tuple
    start: Fraction
    duration: Fraction


@dataclass(frozen=True)
class Event:
    """소리 하나 또는 쉼 하나. id는 시작 글자의 위치이고 cell_id는 시작 정간이다."""
    id: str
    kind: str  # NOTE, REST
    yul: str | None
    octave: int | None
    start: Fraction
    duration: Fraction
    cell_id: str
    breath_after: bool
    sigimsae: tuple


def melody_of(doc, part=None):
    """v2는 parts[]의 선율, v1은 루트 선율이다(우물사이 applyState와 같은 규칙).
    파트가 여럿이면 어느 파트인지 받아야 한다."""
    parts = doc.get("parts")
    if not (isinstance(parts, list) and parts):
        return doc["melody"]
    if part is None:
        if len(parts) > 1:
            raise ValueError(f"파트가 {len(parts)}개라 변환할 파트 번호가 필요하다")
        part = 0
    return parts[part]["melody"]


def read_units(melody, beats_per_jeonggan):
    """선율을 연주 순서의 글자 목록으로 펼치고 시작·길이(박)를 붙인다."""
    units, start = [], Fraction(0)
    for column, line in enumerate(l for l in melody.split("\n") if l.strip()):
        for index, cell in enumerate(c.strip() for c in line.split("|")):
            cell_id = f"c{column}-j{index}"
            words = cell.split() or [""]  # 빈 정간은 줄 하나짜리 빈 글자
            for row, word in enumerate(words):
                parsed = _parse_word(word, cell_id)
                for col, (symbol, breath, sigimsae) in enumerate(parsed):
                    duration = beats_per_jeonggan / len(words) / len(parsed)
                    units.append(Unit(cell_id, column, index, row, col, symbol, breath, sigimsae, start, duration))
                    start += duration
    return units


def build_events(units):
    """글자 목록을 이벤트 목록으로 묶는다. 글자마다 속한 이벤트 id 목록도 같은 순서로 돌려준다."""
    events, unit_events = [], []
    for u in units:
        if u.symbol in ("-", ""):
            if not events:
                raise ValueError(f"{u.cell_id}: 이을 앞 소리가 없다")
            last = events[-1]
            events[-1] = replace(last, duration=last.duration + u.duration,
                                 breath_after=last.breath_after or u.breath, sigimsae=last.sigimsae + u.sigimsae)
        else:
            prefix, yul = (u.symbol[:-1], u.symbol[-1]) if u.symbol != "쉼" else (None, None)
            events.append(Event(f"{u.cell_id}-r{u.row}-u{u.col}", "REST" if yul is None else "NOTE",
                                yul, None if yul is None else OCTAVE[prefix],
                                u.start, u.duration, u.cell_id, u.breath, u.sigimsae))
        unit_events.append(events[-1].id)
    return events, unit_events


def _parse_word(word, cell_id):
    if not word:
        return [("", False, ())]
    out, i = [], 0
    while i < len(word):
        m = UNIT.match(word, i)
        if not m or not m.group(0):
            raise ValueError(f"{cell_id}: '{word[i:]}'를 읽을 수 없다")
        symbol = "쉼" if m.group(1) == "쉼표" else (m.group(1) or "")
        out.append((symbol, bool(m.group(2)), tuple(SIGIMSAE.findall(m.group(3)))))
        i = m.end()
    return out
