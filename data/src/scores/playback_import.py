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
from .import_source import prepare_source, educational_plan
from data.src.playback.jgb import convert_jgb, YUL
from data.src.playback.common_score import convert_common_score, validate_common, PREFIXES
from data.src.playback.preview import mode
from data.src.playback.score import require, resolve_pitch
from data.src.playback.tempo_repeat import apply_tempo_repeat
from data.src.playback.common_preview import _stretch_lengths

CONVERTER = "jgb-playback-import/v1"


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


def import_jgb(document, *, source_name, instrument, part=None, input_sha256, source_id=None,
               performance_plan=None, expression_profile=None, notation_policy=None):
    """Return common/expressions/source_map/bpm; part uses the JGB CLI's 1-based numbering.

    Input metadata must already be explicit enough for the original JGB reader.
    Validation includes declarations on unselected parts; no source is modified.
    """
    require(isinstance(input_sha256, str) and re.fullmatch(r"[0-9a-f]{64}", input_sha256),
            "input_sha256: 원본 바이트 SHA-256이 필요합니다")
    require(isinstance(source_name, str) and source_name.strip(), "source_name이 필요합니다")
    require(notation_policy in (None, "educational-preview/v1"), "알 수 없는 가져오기 notation policy")
    require(isinstance(document, dict), "JGB는 객체여야 합니다")
    require(isinstance(document.get("playback", {}), dict), "playback은 객체여야 합니다")
    if notation_policy is not None:
        document = deepcopy(document)
        document.setdefault("playback", {})["notation_policy"] = notation_policy
    policy = mode(document)
    prepared, source_cells, length_factors, tempo_cues, repeats, normalization = prepare_source(
        document, instrument, part, policy, performance_plan is not None)
    # Resolve glyphs before tempo/repetition, and retain original source coordinates separately.
    legacy, mapping = convert_jgb(prepared, source_name=source_name, instrument=instrument,
                                  part=part, input_sha256=input_sha256, expression_profile=expression_profile,
                                  source_boundaries=any(';' in row['clean'] for cell in source_cells for row in cell['rows']))
    final_score, final_mapping = deepcopy(legacy), deepcopy(mapping)
    _stretch_lengths(final_score, final_mapping, length_factors)
    final_mapping['tempo_cues'] = tempo_cues
    selected_plan = performance_plan
    if selected_plan is None and (tempo_cues or repeats):
        selected_plan = educational_plan(source_cells, tempo_cues, repeats, mapping['jeonggan_bpm'], input_sha256, instrument)
    if selected_plan is not None:
        # The plan is checked against the original raw SHA before intentional rebinding below.
        final_score, final_mapping = apply_tempo_repeat(final_score, final_mapping, selected_plan, input_sha256, instrument=instrument)
    selected = mapping["part_index"]
    source_id = source_id or f"jgb-playback-{input_sha256[:16]}-p{selected}"
    require(isinstance(source_id, str) and source_id.strip(), "source_id가 비어 있습니다")
    voice = document["parts"][selected - 1] if document["v"] == 2 else document
    melody = voice["melody"]
    ratio, hwang = Fraction(mapping["quarter_ratio"]), mapping["huangjong_midi"]
    # The original interpreter supplies resolved pitches, including the previous
    # sounding pitch after attached figures. Do not maintain a second scale engine.
    interpreted = {}
    for note in legacy["notes"]:
        loc = mapping["notes"][note["id"]][0]
        key = (loc["gak"], loc["cell"], loc["row"], loc["unit"])
        interpreted.setdefault(key, []).append(note)
    units, slots, cursor = [], [], Fraction()
    needs_source_slots, previous_main = False, None
    source_cells_iter = iter(source_cells)
    lines = re.split(r"\|\|(?:\r\n|\r|\n)?|\r\n|\n|\r", melody.rstrip("\r\n"))
    for column, line in enumerate(lines):
        for cell_index, cell in enumerate(line.split("|")):
            cid = f"c{column}-j{cell_index}"
            cell_info = next(source_cells_iter)
            parsed = []
            for row, row_info in enumerate(cell_info['rows']):
                text = row_info['text']
                label = f"성부 {selected} · {column+1}각 · {cell_index+1}정간 · {row_info['row']}분박"
                groups, breath = row_info['groups'], row_info['breath']
                raw_marks = row_info['marks']
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
                        needs_source_slots |= len(original_marks) > 1 or bool(group.get("weight"))
                        notes = interpreted.get(key, [])
                        require(notes and all("pitch" in n for n in notes),
                                f"{label}: 독립 음열의 해석 결과를 확인할 수 없습니다")
                        symbols = [_pitch_symbol(n["pitch"], hwang, label) for n in notes]
                        marks = original_marks[1:]
                        anchor = _pitch_symbol(previous_main, hwang, label)
                    else:
                        symbols = [(_pitch_symbol(hwang + group["offset"], hwang, label) if kind == "note"
                                    else "쉼" if kind == "rest" else "-" if text else "")]
                        marks = [m for m in original_marks if m.partition("@")[0] not in
                                 ("이음표", "-", "빈지속", "쉼표", "쉼", "pause_007")]
                        anchor = symbols[0] or "-"
                    if group.get("weight"):
                        needs_source_slots = True
                    main = [n for n in interpreted.get(key, []) if "pitch" in n and
                            ".grace" not in n["id"] and ".postgrace" not in n["id"]]
                    if kind in ("note", "relative") and main:
                        previous_main = main[-1]["pitch"]
                    row_start = row_info['start']
                    origin = {"part": selected, "gak": key[0], "cell": key[1], "row": row_info['row'], "unit": key[3],
                              "cellText": cell, "kind": kind, "symbols": original_marks,
                              "cellStart": cell_info['start'], "cellEnd": cell_info['start'] + len(cell),
                              "rowStart": row_start, "rowEnd": row_start + len(text),
                              "symbolSpans": [{"text": m[0], "start": row_start + m.start(),
                                               "end": row_start + m.end()} for m in matched_marks]}
                    if kind == "relative" and len(notes) > 1 and notes[0].get("slur") and all(
                            n.get("slur") == notes[0]["slur"] for n in notes):
                        origin["interpretedSlur"] = {"id": notes[0]["slur"], "noteIds": [n["id"] for n in notes]}
                    origin["anchorSymbol"] = anchor
                    origin["lengthMode"] = cell_info['lengthMode']
                    if row == group_index == 0:
                        marks.extend(a['symbol'] for a in cell_info['annotations'])
                    group_data.append((group, symbols, marks, origin))
                require(mark_index == len(raw_marks), f"{label}: 원문 기호 누락")
                factor = math.lcm(*(len(v[1]) for v in group_data))
                if factor > 1:
                    needs_source_slots |= any(g.get("grace") or g.get("post_grace") or g.get("weight") or g.get("fermata") or
                                              g.get("sequence") and g["kind"] != "relative" for g, *_ in group_data)
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
    if selected_plan is not None:
        expressions["performancePlan"] = deepcopy(selected_plan)
        expressions["performancePlan"]["input_sha256"] = common_hash
    if expression_profile is not None:
        expressions["expressionProfile"] = deepcopy(expression_profile)
    controls, playback = document["controls"], document.get("playback", {})
    if "joPreset" in controls:
        expressions["jo"] = controls["joPreset"]
    for original, output in (("relative_scale", "relativeScale"), ("expression_policy", "expressionPolicy")):
        if original in playback:
            expressions[output] = deepcopy(playback[original])
    if mapping.get("gak_continuations"):
        expressions["gakContinuations"] = [{"gak": e["gak"], "basis": e["basis"]} for e in mapping["gak_continuations"]]
    for slot in slots:
        slot["generated"] = [{"eventId": ids[i], "cellId": units[i].cell_id,
                              "row": units[i].row, "col": units[i].col} for i in slot.pop("unitIndices")]
        origin = slot["source"]
        event_ids = list(dict.fromkeys(g["eventId"] for g in slot["generated"]))
        if needs_source_slots:
            rendered_marks = [m for g in slot["generated"] for m in next(
                (p["symbols"] for p in placements if all(p[k] == g[k] for k in ("cellId", "row", "col"))), [])]
            raw = origin["symbols"][:1] + rendered_marks if slot["expandedIndependent"] else rendered_marks
            expressions.setdefault("sourceSlots", []).append({
                "cellId": slot["generated"][0]["cellId"], "row": slot["generated"][0]["row"],
                "cols": [g["col"] for g in slot["generated"]], "kind": origin["kind"],
                "anchorSymbol": origin["anchorSymbol"], "symbols": raw, "lengthMode": origin['lengthMode'],
                "basis": f"JGB {source_name}: 성부 {selected}, {origin['gak']}각 {origin['cell']}정간 {origin['row']}분박 {origin['unit']}자리"})
        elif "interpretedSlur" in origin and len(event_ids) >= 2:
            basis = (f"JGB {source_name}: 성부 {selected}, {origin['gak']}각 {origin['cell']}정간 "
                     f"{origin['row']}분박 {origin['unit']}자리의 독립 음열 {origin['symbols']} — "
                     f"원본 재생 슬러 {origin['interpretedSlur']['id']}")
            expressions.setdefault("slurGroups", []).append({"eventIds": event_ids, "basis": basis})
    _verify_projection(final_score, final_mapping, common, expressions, mapping["quarter_bpm"], instrument)
    source_map = {"schema": CONVERTER, "sourceName": source_name, "sourceSha256": input_sha256,
                  "commonSha256": common_hash, "part": selected, "slots": slots, "offsetUnit": "unicode-codepoint",
                  "staffUnit": mapping["staff_unit"], "quarterRatio": str(ratio),
                  "bpm": mapping["quarter_bpm"], "referenceWarnings": check(common),
                  "sourcePolicy": {"notation": policy, "expression": mapping["expression_policy"],
                                   "symbol": mapping["symbol_policy"]},
                  "slurGroups": deepcopy(expressions.get("slurGroups", [])),
                  "validation": {"nominalPitchTiming": "passed", "toleranceSeconds": 0.000001,
                                 "reference": "educational-policy-normalized-source" if length_factors or repeats or
                                              tempo_cues and performance_plan is None else "original-source-semantics",
                                 "restComparison": "adjacent rests coalesced"},
                  "scope": "Inferred playback projection preserving nominal pitch/onset/duration against the stated reference. Consumed independent glyphs and source slot semantics are recorded here and in the expression companion, not applied twice. Gate, velocity, breath/release and waveform identity are not guaranteed by the nominal check."}
    if performance_plan is not None:
        source_map["originalPerformancePlan"] = deepcopy(performance_plan)
    if expression_profile is not None:
        source_map["originalExpressionProfile"] = deepcopy(expression_profile)
    if normalization:
        source_map["normalization"] = normalization
    return {"common": common, "expressions": expressions, "source_map": source_map, "bpm": mapping["quarter_bpm"]}


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--instrument", required=True, choices=("danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum"))
    parser.add_argument("--part", type=int, help="1-based JGB part; required for multipart input")
    parser.add_argument("--source-id")
    parser.add_argument("--performance-plan", type=Path, help="JGB-SHA-bound tempo/repeat plan; BPM is jeonggan/minute")
    parser.add_argument("--expression-profile", type=Path, help="Explicit instrument expression parameters and basis")
    parser.add_argument("--notation-policy", choices=("educational-preview/v1",), help="Explicit educational import policy")
    parser.add_argument("--out", type=Path, required=True, help="New bundle directory (must not exist)")
    args = parser.parse_args()
    raw = args.input.read_bytes()
    result = import_jgb(json.loads(raw.decode("utf-8-sig")), source_name=args.input.name,
                        instrument=args.instrument, part=args.part, source_id=args.source_id,
                        performance_plan=json.loads(args.performance_plan.read_text(encoding="utf-8-sig")) if args.performance_plan else None,
                        expression_profile=json.loads(args.expression_profile.read_text(encoding="utf-8-sig")) if args.expression_profile else None,
                        notation_policy=args.notation_policy,
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
