"""Explicit local playback plans; never infer numeric tempo from a printed cue.

Tempo is linear in written score position. Its reciprocal is integrated to
retime the score on the existing fixed-tempo MIDI grid, including held notes.
The original JGB and caller-owned dictionaries are never changed.
"""
from copy import deepcopy
from bisect import bisect_right
from fractions import Fraction
import math
import re

from .score import PPQN, finite, reject_unknown, require


TEMPO_CUES = {"본래속도로", "점점느리게", "점점속하게", "조금느리게", "조금속하게"}


def _positive_int(value, label, high):
    require(type(value) is int and 1 <= value <= high, f"{label}: 1..{high} 정수가 필요합니다")
    return value


def apply_performance(score, mapping, plan, input_sha256):
    """Return copied score/map after a hash-bound tempo and repeat plan.

    A tempo entry's ``bpm`` is jeonggan/minute, before the existing global speed
    multiplier. An entry with end_gak/end_cell ramps from the preceding tempo
    to that target. Repeat times includes the first pass. Repeated sections
    reuse their written-position tempo plan on every pass.
    """
    reject_unknown(plan, ["schema", "input_sha256", "tempos", "repeats"], "performance plan")
    require(plan.get("schema") == "danso-performance/v1", "performance plan schema는 danso-performance/v1이어야 합니다")
    require(isinstance(input_sha256, str) and re.fullmatch(r"[0-9a-f]{64}", input_sha256),
            "원본 input_sha256이 필요합니다")
    require(plan.get("input_sha256") == input_sha256, "performance plan의 input_sha256이 원본 JGB와 다릅니다")
    require("performance" not in mapping, "같은 악보에 performance plan을 두 번 적용할 수 없습니다")
    result, result_map = deepcopy(score), deepcopy(mapping)
    base = finite(mapping["jeonggan_bpm"], "base jeonggan BPM", 0, math.inf)
    require(base > 0, "기준 빠르기는 양수여야 합니다")
    quarter_seconds = 60 / score["quarter_bpm"]

    # Locations are shared by all grace/main notes of a written unit. Deduping
    # by its original start retains a single continuous written/performed map.
    units = {}
    for locations in mapping["notes"].values():
        for loc in locations:
            start = Fraction(loc["start_quarters"])
            unit = (Fraction(loc["duration_quarters"]), Fraction(loc["written_start_quarters"]),
                    Fraction(loc["written_duration_quarters"]), loc["gak"], loc["cell"])
            require(start not in units or units[start] == unit, "source-map의 같은 위치에 서로 다른 길이가 있습니다")
            units[start] = unit
    spans, cells, cursor, written_cursor = [], {}, Fraction(), Fraction()
    for start, (duration, written, width, gak, cell) in sorted(units.items()):
        require(start == cursor and written == written_cursor and duration > 0 and width > 0,
                "source-map 시간 구간이 연속적이지 않습니다")
        spans.append((float(start), float(start + duration), float(written), float(written + width)))
        cells.setdefault((gak, cell), (written, start))
        cursor, written_cursor = start + duration, written + width
    require(cursor == sum(Fraction(n["duration"]) for n in score["notes"]), "source-map과 악보 길이가 다릅니다")
    require(cursor > 0, "빈 악보는 performance plan을 적용할 수 없습니다")
    terminal = (mapping["gaks"] + 1, 1)
    cells[terminal] = (written_cursor, cursor)

    def position(gak, cell, *, end=False):
        _positive_int(gak, "각 번호", mapping["gaks"] + int(end))
        _positive_int(cell, "정간 번호", 64)
        require((gak, cell) in cells and (end or (gak, cell) != terminal),
                f"존재하지 않는 악보 위치: {gak}각 {cell}정간")
        return cells[(gak, cell)]

    authored = plan.get("tempos", [])
    require(isinstance(authored, list) and len(authored) <= 10000, "tempos는 최대 10000개의 배열이어야 합니다")
    changes = {}
    for entry in authored:
        reject_unknown(entry, ["gak", "cell", "bpm", "end_gak", "end_cell"], "tempo entry")
        where = (entry.get("gak"), entry.get("cell"))
        start = position(*where)[0]
        target = finite(entry.get("bpm"), "tempo BPM", 0, math.inf)
        require(target > 0, "tempo BPM은 양수여야 합니다")
        has_end = "end_gak" in entry or "end_cell" in entry
        require(not has_end or "end_gak" in entry and "end_cell" in entry, "점진 빠르기는 end_gak과 end_cell이 모두 필요합니다")
        finish = position(entry["end_gak"], entry["end_cell"], end=True)[0] if has_end else start
        require(not has_end or finish > start, "점진 빠르기의 끝은 시작 뒤여야 합니다")
        require(start not in changes, "같은 위치에 빠르기 계획이 중복되었습니다")
        changes[start] = {"start": start, "end": finish, "bpm": target, "cue": None, "entry": deepcopy(entry)}

    for cue in mapping.get("tempo_cues", []):
        name = cue["symbol"].strip("{}")
        require(name in TEMPO_CUES, f"알 수 없는 빠르기 기호: {name}")
        start = position(cue["gak"], cue["cell"])[0]
        if name == "본래속도로" and start not in changes:
            changes[start] = {"start": start, "end": start, "bpm": base, "cue": name,
                              "entry": {"gak": cue["gak"], "cell": cue["cell"], "bpm": base}}
        require(start in changes, f"{cue['gak']}각 {cue['cell']}정간 {name}: 명시한 BPM 계획이 필요합니다")
        change = changes[start]
        require(change["cue"] in (None, name), "같은 위치에 충돌하는 빠르기 기호가 있습니다")
        change["cue"] = name
        if name.startswith("점점"):
            require(change["end"] > start, f"{name}: 끝 위치와 목표 BPM이 필요합니다")
        else:
            require(change["end"] == start, f"{name}: 즉시 적용할 BPM을 지정하세요")
        if name == "본래속도로":
            require(change["bpm"] == base, "본래속도로의 BPM은 원래 기준 빠르기와 같아야 합니다")

    tempo_segments, written, current = [], Fraction(), base
    for start, change in sorted(changes.items()):
        require(start >= written, "점진 빠르기 구간 안에 다른 빠르기 변경이 겹칩니다")
        if start > written:
            tempo_segments.append((float(written), float(start), current, current))
        name, target, finish = change["cue"], change["bpm"], change["end"]
        if name in ("점점느리게", "조금느리게"):
            require(target < current, f"{name}: 앞 빠르기보다 낮은 목표 BPM이 필요합니다")
        if name in ("점점속하게", "조금속하게"):
            require(target > current, f"{name}: 앞 빠르기보다 높은 목표 BPM이 필요합니다")
        if finish > start:
            tempo_segments.append((float(start), float(finish), current, target))
        written, current = finish, target
    if written < written_cursor:
        tempo_segments.append((float(written), float(written_cursor), current, current))

    def segment_integral(segment, stop):
        a, b, left, right = segment
        width = min(stop, b) - a
        if width <= 0:
            return 0.
        if abs(right - left) < 1e-12 * max(left, right):
            return base * width / left
        slope = (right - left) / (b - a)
        proportion = width / (b - a)
        target = left * (1 - proportion) + right * proportion
        return base * (math.log(target) - math.log(left)) / slope

    tempo_starts = [segment[0] for segment in tempo_segments]
    tempo_prefix = [0.]
    for segment in tempo_segments:
        tempo_prefix.append(tempo_prefix[-1] + segment_integral(segment, segment[1]))

    def written_integral(q):
        index = max(0, bisect_right(tempo_starts, q) - 1)
        return tempo_prefix[index] + segment_integral(tempo_segments[index], q)

    span_starts = [span[0] for span in spans]
    span_prefix = [0.]
    for a, b, wa, wb in spans:
        span_prefix.append(span_prefix[-1] + (written_integral(wb) - written_integral(wa)) * (b - a) / (wb - wa))

    def warp(q):
        q = min(max(float(q), 0.), float(cursor))
        index = max(0, bisect_right(span_starts, q) - 1)
        a, b, wa, wb = spans[index]
        wstop = wa + (q - a) * (wb - wa) / (b - a)
        value = span_prefix[index] + (written_integral(wstop) - written_integral(wa)) * (b - a) / (wb - wa)
        require(math.isfinite(value), "빠르기 계획의 재생시간을 표현할 수 없습니다")
        return value

    source_notes, note_cursor = [], Fraction()
    for note in score["notes"]:
        end = note_cursor + Fraction(note["duration"])
        source_notes.append((note_cursor, end, note))
        note_cursor = end
    boundaries = {a: i for i, (a, _, _) in enumerate(source_notes)}
    boundaries[cursor] = len(source_notes)

    def safe_boundary(value):
        require(value in boundaries, "반복 경계가 지속음/꾸밈음 내부를 자릅니다. 경계에 새 음을 명시하세요")
        index = boundaries[value]
        if 0 < index < len(source_notes):
            before, after = source_notes[index - 1][2], source_notes[index][2]
            require(not before.get("tie") and not after.get("tie") and
                    not (before.get("slur") and before.get("slur") == after.get("slur")),
                    "반복 경계가 이어진 꾸밈음/붙임줄을 자릅니다")
        return index

    repeats = plan.get("repeats", [])
    require(isinstance(repeats, list), "repeats는 배열이어야 합니다")
    sections = []
    for repeat in repeats:
        reject_unknown(repeat, ["start_gak", "end_gak", "times"], "repeat entry")
        first = _positive_int(repeat.get("start_gak"), "반복 시작 각", mapping["gaks"])
        last = _positive_int(repeat.get("end_gak"), "반복 끝 각", mapping["gaks"])
        times = _positive_int(repeat.get("times"), "반복 횟수", 8)
        require(first <= last and times >= 2, "반복은 시작≤끝, 총 2..8회여야 합니다")
        a, b = position(first, 1)[1], position(last + 1, 1, end=True)[1]
        sections.append((a, b, times, deepcopy(repeat)))
    sections.sort(key=lambda item: item[0])
    require(all(a[1] <= b[0] for a, b in zip(sections, sections[1:])), "반복 구간은 중첩할 수 없습니다")
    passes, previous = [], Fraction()
    for start, end, times, repeat in sections:
        safe_boundary(start)
        safe_boundary(end)
        if start > previous:
            passes.append((previous, start, 1, None))
        passes.extend((start, end, number, repeat) for number in range(1, times + 1))
        previous = end
    if previous < cursor:
        passes.append((previous, cursor, 1, None))
    count = sum(boundaries[b] - boundaries[a] for a, b, _, _ in passes)
    require(count <= 10000, "반복 후 음표가 10000개를 초과합니다")

    notes, locations, note_times, pass_trace = [], {}, [], []
    exact_cursor, tick_cursor = 0., 0
    for start, end, number, repeat in passes:
        offset = exact_cursor - warp(start)
        pass_trace.append({"source_start_quarters": str(start), "source_end_quarters": str(end),
                           "pass": number, "repeat": repeat, "start_tick": tick_cursor})
        for a, b, source_note in source_notes[boundaries[start]:boundaries[end]]:
            note = deepcopy(source_note)
            original_id = note["id"]
            if number > 1:
                note["id"] += f".repeat{number}"
                if note.get("slur"):
                    note["slur"] += f".repeat{number}"
            finish_tick = round((offset + warp(b)) * PPQN)
            require(finish_tick > tick_cursor, f"{original_id}: 빠르기 변경 후 음을 보존할 MIDI 시간 해상도가 부족합니다")
            note["duration"] = str(Fraction(finish_tick - tick_cursor, PPQN))
            old_seconds = float(b - a) * quarter_seconds
            new_seconds = (finish_tick - tick_cursor) / PPQN * quarter_seconds
            # Delays name source positions. Oscillation Hz and real-time curve
            # caps remain chosen performance settings; point fractions follow
            # the warped sounding span rather than claiming physical fidelity.
            for field in ("yoseong", "pitch_curve"):
                effect = note.get(field)
                if effect and "delay_seconds" in effect:
                    delay = effect["delay_seconds"]
                    require(delay <= old_seconds, f"{original_id}: 표현 시작이 음 끝을 넘습니다")
                    effect["delay_seconds"] = (warp(float(a) + delay / quarter_seconds) - warp(a)) * quarter_seconds
            if note.get("breath_seconds"):
                breath, release = note["breath_seconds"], note.get("release_seconds", .05)
                budget = min(breath + release, new_seconds / 2)
                if budget < breath + release:
                    new_release = math.floor(release * budget / (breath + release) / .05 * 127) / 127 * .05
                    note["release_seconds"], note["breath_seconds"] = new_release, budget - new_release
            notes.append(note)
            copied = deepcopy(mapping["notes"][original_id])
            for loc in copied:
                old_start, old_length = Fraction(loc["start_quarters"]), Fraction(loc["duration_quarters"])
                loc["source_start_quarters"], loc["source_duration_quarters"] = str(old_start), str(old_length)
                loc["start_quarters"] = str(Fraction(round((offset + warp(old_start)) * PPQN), PPQN))
                loc["duration_quarters"] = str(Fraction(round((offset + warp(old_start + old_length)) * PPQN) -
                                                        round((offset + warp(old_start)) * PPQN), PPQN))
                loc["repeat_pass"] = number
                if "breath_timing" in loc and "breath_seconds" in note:
                    loc["source_breath_timing"] = loc["breath_timing"]
                    loc["breath_timing"] = {key: note[key] for key in ("breath_seconds", "release_seconds")}
            locations[note["id"]] = copied
            note_times.append({"id": note["id"], "source_id": original_id, "repeat_pass": number,
                               "source_start_quarters": str(a), "source_end_quarters": str(b),
                               "start_tick": tick_cursor, "end_tick": finish_tick})
            tick_cursor = finish_tick
        exact_cursor += warp(end) - warp(start)
        pass_trace[-1]["end_tick"] = tick_cursor
    require(len({note["id"] for note in notes}) == len(notes), "반복 후 음표 ID가 충돌했습니다")
    result["notes"] = notes
    result.setdefault("assumptions", []).append(
        "명시한 performance plan의 정간/분과 각 반복을 적용. 점진 BPM은 기보 위치에서 선형이며 시간은 역수를 적분. "
        "반복마다 원래 위치의 빠르기를 재사용하고 전역 speed를 유지. 표현 Hz·곡선 최대초·점 비율은 재생 선택값.")
    result_map["notes"] = locations
    result_map["source_total_quarters"] = mapping["total_quarters"]
    result_map["total_quarters"] = str(Fraction(tick_cursor, PPQN))
    result_map["performance"] = {
        "schema": plan["schema"], "input_sha256": input_sha256, "plan": deepcopy(plan),
        "status": "explicit-playback-choice-not-performance-standard",
        "resolved_tempos": [dict(change["entry"], source_cue=change["cue"]) for _, change in sorted(changes.items())],
        "tempo_interpolation": "linear BPM in written quarters; reciprocal integrated across held notes",
        "timing_encoding": "fixed base MIDI tempo with globally rounded event endpoints",
        "repeat_tempo": "reuse original written-position tempo on each pass",
        "expression_timing": "delay positions warped; Hz, real-time curve caps and relative point positions retained",
        "passes": pass_trace, "note_times": note_times,
    }
    return result, result_map
