"""사람이 확정한 우물사이 문서(.jgb.json)를 공통 악보 데이터로 바꾼다.

실행: python data/src/scores/score_data.py <원본 ID> [...] [--out data/processed/scores]
채점 정답(data/annotations/scores/<원본 ID>/)을 변환해 <out>/<원본 ID>/score.json에 쓴다.
스키마나 불변식 검사가 실패하면 쓰지 않고 멈춘다.

정간 읽기: 줄바꿈과 `||`가 각 경계, `|`가 정간 경계다(우물사이 parseMelodyOffsets와 같다).
정간 안에서 띄어 쓴 낱말이 위→아래 줄이고, 한 줄에 이어 쓴 음 단위는 그 줄을 왼→오른쪽으로
똑같이 나눈다. 숨표(<)는 자리를 차지하지 않고 그 정간의 마지막 글자에 붙는다.
시간은 곡 장단의 bpm_unit 박으로 세며, 계산 중에는 반올림이 없도록 Fraction으로 둔다.

이벤트 만들기: 율명·쉼표가 새 이벤트를 열고, 이음(-)과 빈 정간은 앞 이벤트의 길이에 더한다.
단 지속은 제 각 안에서만이라, 마지막 음과 다른 각의 이음·빈 정간은 쉼이 된다(우물사이 realizeMelody,
2026-08-14 확정). 숨표·시김새는 그 글자가 속한 이벤트에 붙는다.

결과: 화면 배치용 columns → cells와 재생·채점용 events를 함께 담고 cellId·eventId로 잇는다(노션 B2).
시간은 박을 ticksPerBeat 칸으로 나눈 정수 틱이다. ticksPerBeat는 모든 시작·길이·정간 길이 분모의
최소공배수라서 어떤 길이도 정수 틱이 된다. 형식은 score_data.schema.json이 정한다.
"""
from dataclasses import dataclass, replace
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import re

DATA = Path(__file__).resolve().parents[2]
SCHEMA_PATH = Path(__file__).with_name("score_data.schema.json")
SCHEMA_VERSION = 1
CONVERTER = "score_data@1"  # 변환 규칙을 바꾸면 올린다
HWANG_HZ = 311.13  # 황 = E♭4(노션 1차 MVP 논의사항 1-1)

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
        parts = [{"melody": doc["melody"]}]
    if part is None:
        if len(parts) > 1:
            raise ValueError(f"파트가 {len(parts)}개라 변환할 파트 번호가 필요하다")
        part = 0
    if not 0 <= part < len(parts):
        raise ValueError(f"파트 {part}가 없다(파트 {len(parts)}개)")
    return parts[part]["melody"]


def gaks_of(melody):
    """선율을 각별 정간 글자 목록으로 나눈다. '||'(예전 형식)도 각 경계이고 빈 줄은 빈 정간 하나짜리 각이다."""
    return [[c.strip() for c in line.split("|")] for line in melody.replace("||\n", "\n").replace("||", "\n").split("\n")]


def read_units(melody, beats_per_jeonggan):
    """선율을 연주 순서의 글자 목록으로 펼치고 시작·길이(박)를 붙인다."""
    units, start = [], Fraction(0)
    for column, cells in enumerate(gaks_of(melody)):
        for index, cell in enumerate(cells):
            cell_id = f"c{column}-j{index}"
            breath = "<" in cell  # 숨표는 자리를 차지하지 않고 정간의 마지막 글자에 붙는다
            # 줄을 먼저 나누고 줄마다 숨표를 뺀다. 숨표만 있던 줄은 빈 글자(앞 소리를 잇는 자리)로 남는다.
            words = [w.replace("<", "") for w in cell.split()] or [""]  # 빈 정간은 줄 하나짜리 빈 글자
            first = len(units)
            for row, word in enumerate(words):
                parsed = _parse_word(word, cell_id)
                for col, (symbol, sigimsae) in enumerate(parsed):
                    duration = beats_per_jeonggan / len(words) / len(parsed)
                    units.append(Unit(cell_id, column, index, row, col, symbol, False, sigimsae, start, duration))
                    start += duration
            if breath and len(units) > first:
                units[-1] = replace(units[-1], breath=True)
    return units


def build_events(units):
    """글자 목록을 이벤트 목록으로 묶는다. 글자마다 속한 이벤트 id 목록도 같은 순서로 돌려준다."""
    events, unit_events = [], []
    note_column = None  # 마지막 음이 난 각. 지속은 제 각 안에서만 잇는다
    for u in units:
        if u.symbol in ("-", "") and (not events or events[-1].kind == "NOTE" and note_column != u.column):
            u = replace(u, symbol="쉼")  # 앞 음이 없거나 다른 각이면 쉼으로 끊는다
        if u.symbol in ("-", ""):
            last = events[-1]
            events[-1] = replace(last, duration=last.duration + u.duration,
                                 breath_after=last.breath_after or u.breath, sigimsae=last.sigimsae + u.sigimsae)
        else:
            prefix, yul = (u.symbol[:-1], u.symbol[-1]) if u.symbol != "쉼" else (None, None)
            if yul is not None:
                note_column = u.column
            events.append(Event(f"{u.cell_id}-r{u.row}-u{u.col}", "REST" if yul is None else "NOTE",
                                yul, None if yul is None else OCTAVE[prefix],
                                u.start, u.duration, u.cell_id, u.breath, u.sigimsae))
        unit_events.append(events[-1].id)
    return events, unit_events


def convert(doc, *, beats_per_jeonggan, title, instrument, source_id, jangdan, bpm_unit, review, jgb_sha256,
            part=None, score_version=1):
    """우물사이 문서 하나를 공통 악보 데이터(dict)로 바꾼다. beats_per_jeonggan은 사람이 원본에서 정한 값이다."""
    melody, controls = melody_of(doc, part), doc.get("controls", {})
    # 우물사이는 beats를 넘는 정간을 잘라 쓰고 gakBeats로 각마다 길이를 바꾼다. 옮기기 전까지는 멈춘다.
    if controls.get("gakBeats"):
        raise ValueError(f"각별 정간 수(gakBeats={controls['gakBeats']!r})는 아직 지원하지 않는다")
    beats = int(controls.get("beats") or 0)
    long = [g for g, cells in enumerate(gaks_of(melody)) if beats and len(cells) > beats]
    if long:
        raise ValueError(f"각 {long}의 정간 수가 beats={beats}보다 많다")
    units = read_units(melody, Fraction(beats_per_jeonggan))
    events, unit_events = build_events(units)
    tpb = math.lcm(Fraction(beats_per_jeonggan).denominator,
                   *(t.denominator for e in events for t in (e.start, e.duration)))
    tick = lambda beats: int(beats * tpb)  # tpb가 모든 분모의 배수라 나머지가 없다

    columns = []
    for u, event_id in zip(units, unit_events):
        if u.column == len(columns):
            columns.append({"index": u.column, "cells": []})
        cells = columns[u.column]["cells"]
        if not cells or cells[-1]["id"] != u.cell_id:
            cells.append({"id": u.cell_id, "index": u.index, "units": []})
        cells[-1]["units"].append({"row": u.row, "col": u.col, "symbol": u.symbol, "eventId": event_id})
    return {
        "schemaVersion": SCHEMA_VERSION,
        "score": {"title": title, "instrument": instrument, "part": part or 0, "sourceId": source_id,
                  "scoreVersion": score_version},
        "music": {"hwangHz": HWANG_HZ, "jangdan": jangdan, "bpmUnit": bpm_unit, "ticksPerBeat": tpb,
                  "ticksPerJeonggan": tick(Fraction(beats_per_jeonggan))},
        "columns": columns,
        "events": [{"id": e.id, "kind": e.kind, "yul": e.yul, "octave": e.octave, "startTick": tick(e.start),
                    "durationTick": tick(e.duration), "cellId": e.cell_id, "breathAfter": e.breath_after,
                    "sigimsae": list(e.sigimsae)} for e in events],
        "provenance": {"sourceId": source_id, "jgbSha256": jgb_sha256, "review": review, "converter": CONVERTER},
    }


def convert_answer(source_id, data=DATA):
    """저장소의 채점 정답 하나를 변환한다. 정간당 박·검수 상태는 정답 검토 기록에서,
    곡 제목·악기·장단은 data/reference의 기준 데이터에서 읽는다. 서비스 곡(songs.json)이 아닌
    평가용 정답(소금 등)은 검토 기록의 song에서 읽는다."""
    folder = data / "annotations/scores" / source_id
    (jgb,) = folder.glob("*.jgb.json")
    (review_path,) = folder.glob("*.review.json")
    review = json.loads(review_path.read_text(encoding="utf-8"))
    songs = [s for s in _rows(data, "songs.json") if s["source"] == source_id]
    (song,) = songs or [review["song"]]
    if song["jangdan"]:
        (jangdan,) = [j for j in _rows(data, "jangdans.json") if j["name"] == song["jangdan"]]
        bpm_unit = jangdan["bpm_unit"]
    else:
        bpm_unit = song["bpmUnit"]
    return convert(json.loads(jgb.read_text(encoding="utf-8")),
                   beats_per_jeonggan=Fraction(review["sourceValues"]["beatsPerJeonggan"]),
                   title=song["title"], instrument=song["instrument"], source_id=source_id,
                   jangdan=song["jangdan"], bpm_unit=bpm_unit, review=review["review"],
                   jgb_sha256=hashlib.sha256(jgb.read_bytes()).hexdigest())


def check(result, data=DATA):
    """스키마가 못 보는 불변식을 검사해 어긋난 것을 문장 목록으로 돌려준다. 빈 목록이면 통과다."""
    problems = []
    cells = [c for column in result["columns"] for c in column["cells"]]
    events = result["events"]
    expected = len(cells) * result["music"]["ticksPerJeonggan"]
    total = sum(e["durationTick"] for e in events)
    if total != expected:
        problems.append(f"길이 합 {total}틱 ≠ 정간 {len(cells)} × {result['music']['ticksPerJeonggan']}틱")
    ends = [0] + [e["startTick"] + e["durationTick"] for e in events]
    for e, end in zip(events, ends):
        if e["startTick"] != end:
            problems.append(f"이어짐: {e['id']} 시작 {e['startTick']} ≠ 앞 이벤트 끝 {end}")
    event_ids, cell_ids = {e["id"] for e in events}, {c["id"] for c in cells}
    problems += [f"eventId {u['eventId']}가 events에 없다 ({c['id']})"
                 for c in cells for u in c["units"] if u["eventId"] not in event_ids]
    problems += [f"cellId {e['cellId']}가 cells에 없다 ({e['id']})" for e in events if e["cellId"] not in cell_ids]
    # 율명 표는 단소 음역이다. 다른 악기는 읽기 규칙(12율)만 따른다.
    if result["score"]["instrument"] == "DANSO":
        pitches = {(p["yul"], p["octave"]) for p in _rows(data, "pitches.json")}
        problems += [f"율명 {e['yul']}·{e['octave']}가 율명 표에 없다 ({e['id']})"
                     for e in events if e["kind"] == "NOTE" and (e["yul"], e["octave"]) not in pitches]
    return problems


def dumps(result):
    """결과를 쓰는 유일한 방법. 같은 결과는 항상 같은 글자열이 된다."""
    return json.dumps(result, ensure_ascii=False, indent=1) + "\n"


def _rows(data, name):
    return json.loads((data / "reference" / name).read_text(encoding="utf-8"))["rows"]


def _parse_word(word, cell_id):
    if not word:
        return [("", ())]
    out, i = [], 0
    while i < len(word):
        m = UNIT.match(word, i)
        if not m or not m.group(0):
            raise ValueError(f"{cell_id}: '{word[i:]}'를 읽을 수 없다")
        if not m.group(1):
            # 우물사이는 율명 없는 시김새를 앞 음 기준의 소리로 푼다. 그 규칙을 옮기기 전까지는 멈춘다.
            raise ValueError(f"{cell_id}: 율명 없는 시김새 '{m.group(0)}'는 아직 지원하지 않는다")
        symbol = "쉼" if m.group(1) == "쉼표" else m.group(1)
        out.append((symbol, tuple(SIGIMSAE.findall(m.group(3)))))
        i = m.end()
    return out


if __name__ == "__main__":
    import argparse
    import sys

    import jsonschema

    parser = argparse.ArgumentParser()
    parser.add_argument("source_ids", nargs="+", help="채점 정답의 원본 ID(data/annotations/scores/<원본 ID>)")
    parser.add_argument("--out", type=Path, default=DATA / "processed/scores")
    args = parser.parse_args()
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    for source_id in args.source_ids:
        result = convert_answer(source_id)
        jsonschema.validate(result, schema)
        problems = check(result)
        if problems:
            sys.exit(f"{source_id}: " + "; ".join(problems))
        path = args.out / source_id / "score.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(dumps(result), encoding="utf-8", newline="\n")
        notes = sum(e["kind"] == "NOTE" for e in result["events"])
        print(f"{path}  이벤트 {len(result['events'])}개(음 {notes}), 박당 {result['music']['ticksPerBeat']}틱")
