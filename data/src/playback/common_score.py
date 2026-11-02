"""Service score_data v1 adapter; original ticks/identity remain authoritative."""
from fractions import Fraction
import json
import math
from pathlib import Path

from jsonschema import Draft202012Validator

from .expressions import BREATH_POLICY, adaptive_breath
from .score import PROFILES, finite, midi_tempo, require, ticks

ADAPTER = "common-score-playback/v1"
YUL_NAMES = "황대태협고중유임이남무응"
PREFIXES = {-2: "하배", -1: "배", 0: "", 1: "청", 2: "중청"}
SCHEMA_PATH = Path(__file__).resolve().parents[1] / 'scores/score_data.schema.json'
# Old import companions may carry these equivalent anchor spellings. Canonical
# common events/units still use PREFIXES and are validated before interpretation.
ANCHOR_OFFSETS = {prefix + yul: octave * 12 + degree
                  for prefix, octave in {'': 0, '청': 1, '중청': 2, '겹청': 2, '배': -1, '탁': -1,
                                         '하배': -2, '배탁': -2, '하하배': -3}.items()
                  for degree, yul in enumerate(YUL_NAMES)}


def require_integer(value, location):
    require(type(value) is int, f"{location}: 정수 필드는 소수·불리언 없이 입력해야 합니다")


def validate_common(source, *, allow_sigimsae=False):
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    error = next(Draft202012Validator(schema).iter_errors(source), None)
    if error is not None:
        location = ".".join(map(str, error.absolute_path)) or "scoreData"
        raise ValueError(f"{location}: {error.message}")
    require_integer(source["schemaVersion"], "schemaVersion")
    for section, keys in (("score", ("part", "scoreVersion")), ("music", ("ticksPerBeat", "ticksPerJeonggan"))):
        for key in keys:
            require_integer(source[section][key], f"{section}.{key}")
    require(source["provenance"]["review"] != "rejected", "provenance.review: rejected 악보는 재생하지 않습니다")
    require(source["score"]["sourceId"] == source["provenance"]["sourceId"], "provenance.sourceId 불일치")
    events = source["events"]
    require(events and source["columns"], "columns/events: 빈 악보")
    require(len(events) <= 10000, "events: 최대 10000개까지 지원합니다")
    require(len({e["id"] for e in events}) == len(events), "events.id: 중복 이벤트")
    by_id, cursor = {}, 0
    for event in events:
        eid = event["id"]
        for key in ("startTick", "durationTick", *(("octave",) if event["kind"] == "NOTE" else ())):
            require_integer(event[key], f"{eid}.{key}")
        require(event["startTick"] == cursor, f"{eid}: 빈틈·겹침 없는 시간축이 필요합니다")
        require(allow_sigimsae or not event["sigimsae"], f"{eid}: 공통 v1 sigimsae 미지원: 배치 시각·조 정보가 필요합니다")
        require(not (event["kind"] == "REST" and event["breathAfter"]), f"{eid}: REST의 숨표는 지원하지 않습니다")
        cursor += event["durationTick"]
        by_id[eid] = event
    positions, origins, offset = {}, set(), 0
    cell_ticks = source["music"]["ticksPerJeonggan"]
    for c, column in enumerate(source["columns"]):
        require_integer(column["index"], f"columns[{c}].index")
        require(column["index"] == c and column["cells"], f"columns[{c}]: 각 순서·정간을 확인하세요")
        for j, cell in enumerate(column["cells"]):
            cid = f"c{c}-j{j}"
            require_integer(cell["index"], f"{cid}.index")
            require(cell["id"] == cid and cell["index"] == j, f"{cid}: 정간 ID·위치 불일치")
            rows = {}
            for unit in cell["units"]:
                for key in ("row", "col"):
                    require_integer(unit[key], f"{cid}.units.{key}")
                rows.setdefault(unit["row"], []).append(unit)
            require(list(rows) == list(range(len(rows))), f"{cid}: row 순서 불일치")
            require(cell["units"] == sorted(cell["units"], key=lambda u: (u["row"], u["col"])),
                    f"{cid}: 표시 자리 순서 불일치")
            for row, units in rows.items():
                require([u["col"] for u in units] == list(range(len(units))), f"{cid}: col 순서 불일치")
                duration = Fraction(cell_ticks, len(rows) * len(units))
                for unit in units:
                    eid = unit["eventId"]
                    require(eid in by_id, f"{cid}: 존재하지 않는 eventId {eid}")
                    event = by_id[eid]
                    start = offset + Fraction(cell_ticks * row, len(rows)) + duration * unit["col"]
                    end = event["startTick"] + event["durationTick"]
                    require(event["startTick"] <= start and start + duration <= end, f"{cid}: 표시 자리와 이벤트 시간 불일치")
                    is_origin = eid == f"{cid}-r{row}-u{unit['col']}"
                    if is_origin:
                        require(event["cellId"] == cid and start == event["startTick"], f"{eid}: 시작 정간·시간 불일치")
                        origins.add(eid)
                    symbol = unit["symbol"]
                    if symbol in ("", "-"):
                        require(not (is_origin and event["kind"] == "NOTE"), f"{eid}: 시작 음의 표시 율명이 없습니다")
                        require(event["kind"] == "REST" or event["cellId"].split("-")[0] == f"c{c}",
                                f"{cid}: NOTE 이음이 각 경계를 넘었습니다")
                    else:
                        expected = ("쉼" if event["kind"] == "REST" else
                                    PREFIXES[event["octave"]] + event["yul"])
                        require(symbol == expected and is_origin, f"{cid}: 표시 율명·쉼과 이벤트 불일치")
                    positions.setdefault(eid, []).append({"cellId": cid, **unit})
            offset += cell_ticks
    require(origins == set(by_id), "events: 시작 표시 자리 누락")
    require(offset == cursor, "events: 전체 길이와 정간 길이 불일치")
    return positions, cursor


def convert_common_score(source, *, bpm, instrument, input_sha256, notation_policy=None, expressions=None):
    """BPM counts music.bpmUnit beats, never guessed from the title/jangdan."""
    require(notation_policy in (None, "educational-preview/v1"), "알 수 없는 notation policy")
    require(expressions is None or notation_policy is not None, "표현 파일은 교육용 미리듣기 정책을 선택해야 합니다")
    positions, total = validate_common(source, allow_sigimsae=notation_policy is not None)
    require(bpm is not None, "공통 악보는 music.bpmUnit 기준 --common-bpm이 필요합니다")
    bpm = finite(bpm, "common_bpm", 0, math.inf)
    require(bpm > 0, "common_bpm must be positive")
    expected_instrument = source["score"]["instrument"].lower().replace("_", "-")
    require(instrument in PROFILES and instrument == expected_instrument,
            "--instrument는 공통 악보의 score.instrument와 같아야 합니다")
    music = source["music"]
    ratio = Fraction(3, 2) if music["bpmUnit"] == "DOTTED_QUARTER" else Fraction(1)
    quarter_bpm = bpm * float(ratio)
    tempo_us = midi_tempo(quarter_bpm)
    hwang = finite(music["hwangHz"], "music.hwangHz", 0, math.inf)
    require(hwang > 0, "music.hwangHz must be positive")
    hwang_midi = round(69 + 12 * math.log2(hwang / 440))
    require(0 <= hwang_midi <= 127, "music.hwangHz: MIDI 음역 밖입니다")
    tuning_a = hwang * 2 ** ((69 - hwang_midi) / 12)
    notes, mapping, note_index = [], [], 0
    for event in source["events"]:
        eid = event["id"]
        duration = Fraction(event["durationTick"], music["ticksPerBeat"]) * ratio
        start = Fraction(event["startTick"], music["ticksPerBeat"]) * ratio
        # Reject unrepresentable subdivisions instead of silently rounding them.
        try:
            start_midi, duration_midi = ticks(start), ticks(duration)
        except ValueError as exc:
            raise ValueError(f"{eid}: {exc}") from exc
        note = {"id": eid, "duration": str(duration)}
        target_hz = None
        ordinal = None
        if event["kind"] == "REST":
            note["rest"] = True
        else:
            semitones = YUL_NAMES.index(event["yul"]) + 12 * event["octave"]
            note.update(pitch=hwang_midi + semitones, gate_fraction=1)
            target_hz = hwang * 2 ** (semitones / 12)
            ordinal = note_index
            note_index += 1
            if event["breathAfter"]:
                note.update(adaptive_breath(duration, quarter_bpm))
        notes.append(note)
        mapping.append({**event, "noteIdx": ordinal, "displayUnits": positions[eid],
                        "target_hz": target_hz, "start_seconds": event["startTick"] / music["ticksPerBeat"] * 60 / bpm,
                        "duration_seconds": event["durationTick"] / music["ticksPerBeat"] * 60 / bpm,
                        "midi_start_tick": start_midi, "midi_duration_tick": duration_midi})
    score = {"schema": "gugak-reviewed-score/v1", "title": source["score"]["title"],
             "source": {"edition": source["score"]["sourceId"], "review_status": "source-review-unverified",
                        "common_score": source["score"], "common_provenance": source["provenance"],
                        "common_schema_version": source["schemaVersion"], "common_sha256": input_sha256},
             "context": {"pitch_mode": "sounding", "notation_octave_shift": 0, "tuning_a_hz": tuning_a,
                         "style": "공통 악보", "tuning_description": f"공통 악보 hwangHz={hwang}; 자동 이조 없음"},
             "quarter_bpm": quarter_bpm, "notes": notes,
             "assumptions": ["공통 악보의 검수 상태와 합성 연주 전문가 검수는 별개입니다.",
                             "release를 원본 음 길이 안에 포함합니다. 숨표는 기존 재생 정책을 사용합니다."]}
    source_map = {"adapter": ADAPTER, "input_sha256": input_sha256,
                  "schemaVersion": source["schemaVersion"], "score": source["score"],
                  "provenance": source["provenance"], "music": music,
                  "bpm": bpm, "quarter_ratio": str(ratio), "quarter_bpm": quarter_bpm,
                  "midi_tempo_us": tempo_us, "hwang_midi": hwang_midi, "tuning_a_hz": tuning_a,
                  "breath_policy": dict(BREATH_POLICY), "release_within_note": True,
                  "total_ticks": total, "score_seconds": total / music["ticksPerBeat"] * 60 / bpm,
                  "events": mapping}
    if notation_policy is not None:
        from .common_preview import convert_preview
        return convert_preview(source, score, source_map, expressions)
    return score, source_map
