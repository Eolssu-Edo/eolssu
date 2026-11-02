"""Opt-in playback projection of authored JGB; reviewed score conversion is unchanged.

Only independent relative slots are expanded into explicit pitches. Original
glyphs and locations live in the import map, never as invented written pitches.
This inferred artifact is not a replacement for a reviewed scoring reference.
"""
from copy import deepcopy
from dataclasses import replace
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import re

from .score_data import Unit, build_events, check, dumps
from data.src.playback.jgb import convert_jgb, row_groups, YUL
from data.src.playback.common_score import convert_common_score, validate_common, PREFIXES
from data.src.playback.preview import mode
from data.src.playback.score import require, resolve_pitch

CONVERTER = "jgb-playback-import/v1"
MARK = re.compile(r"[\{(\[]([^{}()\[\]]+)[\})\]]")


def _nominal(score):
    """Coalesce rests only; adjacent repeated notes retain their attacks."""
    result, cursor = [], Fraction()
    seconds = Fraction(60) / Fraction(str(score["quarter_bpm"]))
    for note in score["notes"]:
        duration = Fraction(note["duration"]) * seconds
        pitch = None if note.get("rest") else resolve_pitch(note["pitch"], score["context"])
        if pitch is None and result and result[-1]["pitch"] is None:
            result[-1]["duration"] += duration
        else:
            result.append({"id": note["id"], "pitch": pitch, "start": cursor, "duration": duration})
        cursor += duration
    return result


def _verify_projection(legacy, mapping, common, expressions, bpm, instrument):
    projected, _ = convert_common_score(common, bpm=bpm, instrument=instrument,
                                       input_sha256=expressions["sourceSha256"],
                                       notation_policy="educational-preview/v1", expressions=expressions)
    expected, actual = _nominal(legacy), _nominal(projected)
    for i in range(max(len(expected), len(actual))):
        before = expected[i] if i < len(expected) else None
        after = actual[i] if i < len(actual) else None
        equal = before is not None and after is not None and before["pitch"] == after["pitch"]
        equal = equal and all(abs(before[k] - after[k]) <= Fraction(1, 1_000_000) for k in ("start", "duration"))
        if not equal:
            loc = mapping["notes"][before["id"]][0] if before else None
            label = (f"성부 {loc['part_index']} · {loc['gak']}각 · {loc['cell']}정간 · {loc['row']}분박"
                     if loc else "악보 끝")
            raise ValueError(f"{label}: 공통 가져오기가 원문의 음높이·명목 시간을 보존하지 못합니다 (음/쉼 {i + 1})")


def _pitch_symbol(midi, hwang, location):
    octave, degree = divmod(midi - hwang, 12)
    require(octave in PREFIXES, f"{location}: 공통 v1 옥타브 범위(-2..2)를 벗어납니다")
    return PREFIXES[octave] + YUL[degree]


def _assemble(units, ratio, doc, source_id, instrument, part, digest, hwang):
    events, ids = build_events(units)
    tpb = math.lcm(ratio.denominator, *(v.denominator for e in events for v in (e.start, e.duration)))
    columns = []
    for unit, eid in zip(units, ids):
        if unit.column == len(columns):
            columns.append({"index": unit.column, "cells": []})
        cells = columns[-1]["cells"]
        if not cells or cells[-1]["id"] != unit.cell_id:
            cells.append({"id": unit.cell_id, "index": unit.index, "units": []})
        cells[-1]["units"].append({"row": unit.row, "col": unit.col, "symbol": unit.symbol, "eventId": eid})
    playback = doc.get("playback", {})
    tempo = playback.get("tempo", playback.get("tempo_basis", {}))
    jangdan = (tempo.get("jangdan") or "") if isinstance(tempo, dict) else ""
    common = {
        "schemaVersion": 1,
        "score": {"title": doc["controls"]["title"], "instrument": instrument.upper().replace("-", "_"),
                  "part": part - 1, "sourceId": source_id, "scoreVersion": 1},
        "music": {"hwangHz": 440 * 2 ** ((hwang - 69) / 12), "jangdan": jangdan,
                  "bpmUnit": "QUARTER", "ticksPerBeat": tpb, "ticksPerJeonggan": int(ratio * tpb)},
        "columns": columns,
        "events": [{"id": e.id, "kind": e.kind, "yul": e.yul, "octave": e.octave,
                    "startTick": int(e.start * tpb), "durationTick": int(e.duration * tpb),
                    "cellId": e.cell_id, "breathAfter": e.breath_after, "sigimsae": list(e.sigimsae)} for e in events],
        "provenance": {"sourceId": source_id, "jgbSha256": digest, "review": "inferred", "converter": CONVERTER},
    }
    return common, ids


def import_jgb(document, *, source_name, instrument, part=None, input_sha256, source_id=None):
    """Return common/expressions/source_map/bpm; part uses the JGB CLI's 1-based numbering.

    Input metadata must already be explicit enough for the original JGB reader.
    Validation includes declarations on unselected parts; no source is modified.
    """
    require(isinstance(input_sha256, str) and re.fullmatch(r"[0-9a-f]{64}", input_sha256),
            "input_sha256: 원본 바이트 SHA-256이 필요합니다")
    require(isinstance(source_name, str) and source_name.strip(), "source_name이 필요합니다")
    legacy, mapping = convert_jgb(document, source_name=source_name, instrument=instrument,
                                  part=part, input_sha256=input_sha256)
    selected = mapping["part_index"]
    source_id = source_id or f"jgb-playback-{input_sha256[:16]}-p{selected}"
    require(isinstance(source_id, str) and source_id.strip(), "source_id가 비어 있습니다")
    voice = document["parts"][selected - 1] if document["v"] == 2 else document
    melody = voice["melody"]
    ratio, hwang = Fraction(mapping["quarter_ratio"]), mapping["huangjong_midi"]
    policy = mode(document)
    # The original interpreter supplies resolved pitches, including the previous
    # sounding pitch after attached figures. Do not maintain a second scale engine.
    interpreted, locations = {}, {}
    for note in legacy["notes"]:
        loc = mapping["notes"][note["id"]][0]
        key = (loc["gak"], loc["cell"], loc["row"], loc["unit"])
        interpreted.setdefault(key, []).append(note)
        for loc in mapping["notes"][note["id"]]:
            locations[(loc["gak"], loc["cell"], loc["row"], loc["unit"])] = loc
    units, slots, cursor = [], [], Fraction()
    lines = re.split(r"\|\|(?:\r\n|\r|\n)?|\r\n|\n|\r", melody.rstrip("\r\n"))
    for column, line in enumerate(lines):
        for cell_index, cell in enumerate(line.split("|")):
            cid = f"c{column}-j{cell_index}"
            words = list(re.finditer(r"\S+", cell))
            parsed = []
            for row, match in enumerate(words or [None]):
                text = match[0] if match else ""
                label = f"성부 {selected} · {column+1}각 · {cell_index+1}정간 · {row+1}분박"
                groups, breath = row_groups(text, label, instrument=instrument, notation_policy=policy)
                raw_marks = list(MARK.finditer(text))
                mark_index = 0
                group_data = []
                for group_index, group in enumerate(groups or [{"kind": "hold", "symbols": []}]):
                    key = (column + 1, cell_index + 1, row + 1, group_index + 1)
                    matched_marks = raw_marks[mark_index:mark_index + len(group["symbols"])]
                    original_marks = [m[1] for m in matched_marks]
                    mark_index += len(group["symbols"])
                    require([x.partition("@")[0] for x in original_marks] == group["symbols"],
                            f"{label}: 기호 원문 대응을 확인할 수 없습니다")
                    kind = group["kind"]
                    if kind == "relative":
                        require(len(original_marks) == 1 and not group.get("weight"),
                                f"{label}: 독립 음열과 추가 효과의 동시 펼침은 지원하지 않습니다")
                        notes = interpreted.get(key, [])
                        require(notes and all("pitch" in n for n in notes),
                                f"{label}: 독립 음열의 해석 결과를 확인할 수 없습니다")
                        symbols = [_pitch_symbol(n["pitch"], hwang, label) for n in notes]
                        marks = []
                    else:
                        symbols = [(_pitch_symbol(hwang + group["offset"], hwang, label) if kind == "note"
                                    else "쉼" if kind == "rest" else "-" if text else "")]
                        marks = [m for m in original_marks if m.partition("@")[0] not in
                                 ("이음표", "-", "빈지속", "쉼표", "쉼", "pause_007")]
                    loc = locations[key]
                    row_start = loc["cell_start"] + (match.start() if match else 0)
                    origin = {"part": selected, "gak": key[0], "cell": key[1], "row": key[2], "unit": key[3],
                              "cellText": cell, "kind": kind, "symbols": original_marks,
                              "cellStart": loc["cell_start"], "cellEnd": loc["cell_end"],
                              "rowStart": row_start, "rowEnd": row_start + len(text),
                              "symbolSpans": [{"text": m[0], "start": row_start + m.start(),
                                               "end": row_start + m.end()} for m in matched_marks]}
                    group_data.append((group, symbols, marks, origin))
                require(mark_index == len(raw_marks), f"{label}: 원문 기호 누락")
                factor = math.lcm(*(len(v[1]) for v in group_data))
                if factor > 1:
                    require(not any(g.get("grace") or g.get("post_grace") or g.get("weight") or g.get("fermata") or
                                    g.get("sequence") and g["kind"] != "relative" for g, *_ in group_data),
                            f"{label}: 독립 음열과 같은 row의 꾸밈/길이 혼합은 지원하지 않습니다")
                require(factor * len(group_data) <= 64, f"{label}: 펼친 row가 64자리를 초과합니다")
                parsed.append((group_data, factor, breath, label))
            for row, (groups, factor, _, label) in enumerate(parsed):
                width = len(groups) * factor
                for group_index, (group, symbols, marks, origin) in enumerate(groups):
                    generated = []
                    for pitch_index, symbol in enumerate(symbols):
                        for fill in range(factor // len(symbols)):
                            col = group_index * factor + pitch_index * (factor // len(symbols)) + fill
                            duration = ratio / len(parsed) / width
                            units.append(Unit(cid, column, cell_index, row, col,
                                              symbol if fill == 0 else "-", False,
                                              tuple(marks) if pitch_index == fill == 0 else (), cursor, duration))
                            generated.append(len(units) - 1)
                            cursor += duration
                    slots.append({"source": origin,
                                  "expandedIndependent": group["kind"] == "relative", "unitIndices": generated})
            if any(b for _, _, b, _ in parsed):
                units[-1] = replace(units[-1], breath=True)
    common, ids = _assemble(units, ratio, document, source_id, instrument, selected, input_sha256, hwang)
    validate_common(common, allow_sigimsae=True)
    common_hash = hashlib.sha256(dumps(common).encode("utf-8")).hexdigest()
    placements = [{"eventId": eid, "cellId": u.cell_id, "row": u.row, "col": u.col,
                   "symbols": list(u.sigimsae)} for u, eid in zip(units, ids) if u.sigimsae]
    expressions = {"schema": "common-score-expressions/v1", "sourceSha256": common_hash, "placements": placements}
    controls, playback = document["controls"], document.get("playback", {})
    if "joPreset" in controls:
        expressions["jo"] = controls["joPreset"]
    for original, output in (("relative_scale", "relativeScale"), ("expression_policy", "expressionPolicy")):
        if original in playback:
            expressions[output] = deepcopy(playback[original])
    if mapping.get("gak_continuations"):
        expressions["gakContinuations"] = [{"gak": e["gak"], "basis": e["basis"]} for e in mapping["gak_continuations"]]
    _verify_projection(legacy, mapping, common, expressions, mapping["quarter_bpm"], instrument)
    for slot in slots:
        slot["generated"] = [{"eventId": ids[i], "cellId": units[i].cell_id,
                              "row": units[i].row, "col": units[i].col} for i in slot.pop("unitIndices")]
    source_map = {"schema": CONVERTER, "sourceName": source_name, "sourceSha256": input_sha256,
                  "commonSha256": common_hash, "part": selected, "slots": slots, "offsetUnit": "unicode-codepoint",
                  "staffUnit": mapping["staff_unit"], "quarterRatio": str(ratio),
                  "bpm": mapping["quarter_bpm"], "referenceWarnings": check(common),
                  "sourcePolicy": {"notation": policy, "expression": mapping["expression_policy"],
                                   "symbol": mapping["symbol_policy"]},
                  "validation": {"nominalPitchTiming": "passed", "toleranceSeconds": 0.000001,
                                 "restComparison": "adjacent rests coalesced"},
                  "scope": "Inferred playback projection preserving nominal pitch/onset/duration. Consumed independent glyphs are recorded here, not applied twice. Slur grouping, gate, velocity, breath/release and waveform identity are not guaranteed by the nominal check."}
    return {"common": common, "expressions": expressions, "source_map": source_map, "bpm": mapping["quarter_bpm"]}


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--instrument", required=True, choices=("danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum"))
    parser.add_argument("--part", type=int, help="1-based JGB part; required for multipart input")
    parser.add_argument("--source-id")
    parser.add_argument("--out", type=Path, required=True, help="New bundle directory (must not exist)")
    args = parser.parse_args()
    raw = args.input.read_bytes()
    result = import_jgb(json.loads(raw.decode("utf-8-sig")), source_name=args.input.name,
                        instrument=args.instrument, part=args.part, source_id=args.source_id,
                        input_sha256=hashlib.sha256(raw).hexdigest())
    args.out.mkdir(parents=True, exist_ok=False)
    (args.out / "source.jgb.json").write_bytes(raw)
    (args.out / "common.json").write_text(dumps(result["common"]), encoding="utf-8", newline="\n")
    for name, value in (("expressions.json", result["expressions"]), ("source-map.json", result["source_map"])):
        (args.out / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"out": str(args.out), "bpm": result["bpm"], "notation_policy": "educational-preview/v1",
                      "playback_args": ["--score", str(args.out / "common.json"), "--common-bpm", str(result["bpm"]),
                                        "--common-expressions", str(args.out / "expressions.json"),
                                        "--notation-policy", "educational-preview/v1", "--instrument", args.instrument]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
