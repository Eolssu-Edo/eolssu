"""JGB v1/v2 single-part adapter; source positions use Unicode character offsets.

Notation semantics: jeongganbo-generator 9d4a24e, app.js and symbols-registry.js.
This implements a strict playback subset, not the shared FE/AI/BE score contract.

The notation tables and rules are adapted from jeongganbo-generator (MIT):
Copyright (c) 2026 Danbinaerin Han

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""
from fractions import Fraction
import re

from .score import PROFILES, finite, midi_tempo, require, ticks
from . import expressions
from .tempo_repeat import TEMPO_CUES, apply_tempo_repeat

YUL = "황대태협고중유임이남무응"
PREFIX = {"": 0, "청": 1, "중청": 2, "겹청": 2,
          "배": -1, "탁": -1, "하배": -2, "배탁": -2, "하하배": -3}
NOTE = re.compile(r"(하하배|중청|겹청|하배|배탁|청|배|탁)?([황대태협고중유임이남무응])")
RATIOS = {"dotted": Fraction(3, 2), "plain": Fraction(1), "eighth": Fraction(1, 2)}


def declared_instrument(document, voice, instrument, assume_danso):
    require(instrument in PROFILES, "지원하지 않는 악기 프로필입니다")
    require(type(assume_danso) is bool, "assume_danso는 true/false여야 합니다")
    require(not assume_danso or instrument == "danso", "--assume-danso는 단소 선택에만 사용할 수 있습니다")
    metadata = document.get("playback", {})
    require(isinstance(metadata, dict), "playback은 객체여야 합니다")
    declared, override = voice.get("instrument"), metadata.get("instrument")
    require(override is None or override == instrument, "playback.instrument와 선택 악기가 다릅니다")
    if instrument == "danso" and override is None:
        require(declared in (None, "danso") or declared == "all" and assume_danso,
                "선택한 성부의 악기가 단소(danso)가 아닙니다. all은 단소 자료임을 확인한 뒤 --assume-danso로 선택하세요")
        return "explicit-all-as-danso" if declared == "all" else "jgb", None
    if declared == instrument:
        basis = metadata.get("instrument_basis")
        return "jgb", basis.strip() if isinstance(basis, str) and basis.strip() else f"JGB part instrument: {declared}"
    generic_daegeum = declared == "daegeum" and instrument in ("jeongak-daegeum", "sanjo-daegeum")
    require(declared in (None, "all") or generic_daegeum, "선택한 성부의 악기와 요청한 악기가 다릅니다")
    basis = metadata.get("instrument_basis")
    require(override == instrument and isinstance(basis, str) and basis.strip(),
            "악기 미지정입니다. 판본 근거와 playback.instrument·instrument_basis가 필요합니다")
    return "prepared-input", basis.strip()


def number(value, label, low, high):
    require(not isinstance(value, bool), f"{label}: 숫자가 필요합니다")
    try:
        value = float(value)
    except (TypeError, ValueError):
        raise ValueError(f"{label}: 숫자가 필요합니다") from None
    return finite(value, label, low, high)


def integer(value, label, low, high):
    result = number(value, label, low, high)
    require(result.is_integer(), f"{label}: 정수가 필요합니다")
    return int(result)


def row_groups(text, label, tempo_cues=None, instrument="danso"):
    """Group attached ornaments without allocating another time slot."""
    groups, breath, i = [], False, 0
    while i < len(text):
        if text[i] == "<":
            require(not breath, f"{label}: 중복 숨표")
            breath = True
            i += 1
            continue
        if text[i] in "{([":
            close = {"{": "}", "(": ")", "[": "]"}[text[i]]
            end = text.find(close, i + 1)
            require(end != -1, f"{label}: 닫히지 않은 기호")
            raw = text[i + 1:end]
            name, _, adjustment = raw.partition("@")
            expressions.validate_symbol(name, instrument, label)
            if adjustment:
                require(all(re.fullmatch(r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)", v)
                            for v in adjustment.split(",")), f"{label}: 잘못된 기호 배치값")
            if name in TEMPO_CUES:
                require(tempo_cues is not None, f"{label}: {{{name}}}는 --performance-plan에 BPM·적용 구간을 지정하세요")
                tempo_cues.append(name)
            elif name in expressions.PROFILE_MARKS:
                kind = expressions.PROFILE_MARKS[name]
                if kind in expressions.INDEPENDENT_PROFILE_EFFECTS or not groups:
                    require(kind in expressions.HOLD_PROFILE_EFFECTS,
                            f"{label}: {{{name}}} 앞에 본음/지속 자리가 필요합니다")
                    groups.append({"kind": "hold", "symbols": []})
                group = groups[-1]
                require(group["kind"] in ("note", "relative", "hold"), f"{label}: {{{name}}}의 본음이 필요합니다")
                effects = group.setdefault("profile_effects", [])
                require(kind not in effects, f"{label}: 겹친 {name}")
                effects.append(kind)
                group["symbols"].append(name)
            elif name in ("쉼표", "쉼", "pause_007"):
                groups.append({"kind": "rest", "symbols": [name]})
            elif name in ("이음표", "-", "빈지속"):
                groups.append({"kind": "hold", "symbols": [name]})
            elif name in expressions.LENGTH_MARKS or name in expressions.EFFECT_MARKS or name in expressions.EXPRESSION_POLICY["unresolved_display_annotations"]:
                require(groups, f"{label}: {{{name}}} 앞에 본음/지속 자리가 필요합니다")
                group = groups[-1]
                if name in expressions.LENGTH_MARKS:
                    require("weight" not in group, f"{label}: 겹친 길이 기호")
                    group["weight"] = expressions.LENGTH_MARKS[name]
                elif name in expressions.EFFECT_MARKS:
                    effect = expressions.EFFECT_MARKS[name]
                    require(effect == "fermata" or group["kind"] in ("note", "relative", "hold"),
                            f"{label}: {{{name}}}의 본음이 필요합니다")
                    require(effect not in group, f"{label}: 겹친 {name}")
                    group[effect] = True
                group["symbols"].append(name)
            else:
                symbols = expressions.symbols_for(instrument)
                require(name in symbols, f"{label}: 미지원 기호 {{{name}}}; 생략하지 않고 중단합니다")
                kind, steps = symbols[name]
                if kind == "independent":
                    groups.append({"kind": "relative", "sequence": steps, "symbols": [name]})
                elif kind == "articulation":
                    require(groups and groups[-1]["kind"] == "note",
                            f"{label}: {{{name}}}는 명시한 본음 뒤에 붙여야 합니다")
                    require(not groups[-1].get("reattack"), f"{label}: 겹친 혀치기")
                    groups[-1]["reattack"] = True
                    groups[-1]["symbols"].append(name)
                elif kind == "alteration":
                    require(groups and groups[-1]["kind"] == "note",
                            f"{label}: {{{name}}}는 명시한 본음 뒤에 붙여야 합니다")
                    require("semitone_offset" not in groups[-1], f"{label}: 겹친 반음올림표")
                    groups[-1]["semitone_offset"] = steps[0]
                    groups[-1]["symbols"].append(name)
                else:
                    require(groups and groups[-1]["kind"] == "note",
                            f"{label}: {{{name}}}는 본음 뒤에 붙여야 합니다")
                    group = groups[-1]
                    field = "grace" if kind in ("grace", "surround") else "sequence"
                    require(field not in group, f"{label}: 겹친 {kind} 기호는 미지원입니다")
                    group[field] = steps
                    if kind == "surround":
                        group["post_grace"] = steps
                    group["symbols"].append(name)
            i = end + 1
            continue
        rest = re.match(r"쉼표|쉼|-", text[i:])
        if rest:
            groups.append({"kind": "hold" if rest[0] == "-" else "rest", "symbols": []})
            i += len(rest[0])
            continue
        note = NOTE.match(text, i)
        require(note is not None, f"{label}: 읽을 수 없는 표기 {text[i:]!r}")
        groups.append({"kind": "note", "offset": YUL.index(note[2]) + 12 * PREFIX[note[1] or ""],
                       "symbols": []})
        i = note.end()
    return groups, breath


def convert_jgb(document, *, source_name, part=None, bpm=None, speed=1, hwang_midi=None, jo=None,
                performance_plan=None, input_sha256=None, expression_profile=None, assume_danso=False,
                instrument="danso"):
    """Return the existing score IR and a map from IR note IDs to JGB locations."""
    require(isinstance(document, dict) and type(document.get("v")) is int
            and document["v"] in (1, 2), "지원하는 JGB 버전은 v1·v2입니다")
    controls = document.get("controls")
    require(isinstance(controls, dict), "JGB controls가 필요합니다")
    if document["v"] == 1:
        require("parts" not in document, "JGB v1에 parts가 함께 있습니다")
        parts = [{"id": None, "name": PROFILES.get(instrument, {}).get("label"),
                  "instrument": document.get("ornInstrument", "danso" if instrument == "danso" else None),
                  "melody": document.get("melody")}]
    else:
        parts = document.get("parts")
    require(isinstance(parts, list) and parts and all(isinstance(p, dict) for p in parts),
            "JGB parts가 필요합니다")
    require(part is not None or len(parts) == 1, "여러 성부가 있습니다. --part로 1부터 성부 번호를 선택하세요")
    selected = integer(1 if part is None else part, "part", 1, len(parts))
    voice = parts[selected - 1]
    selection, instrument_basis = declared_instrument(document, voice, instrument, assume_danso)
    profile = expressions.resolve_expression_profile(expression_profile, instrument)
    policy = expressions.expression_policy(document, instrument)
    custom_scale = expressions.relative_scale(document)
    require(voice.get("muted", False) is False, "선택한 성부가 음소거되어 있습니다")
    for name, default in (("wantJangdan", False), ("playJanggu", True), ("playSigimsae", True)):
        require(type(controls.get(name, default)) is bool, f"controls.{name}: true/false가 필요합니다")
    require(not (controls.get("wantJangdan", False) and controls.get("playJanggu", True) and
                 str(document.get("jangdan") or controls.get("jangdan") or "").strip()),
            "장구 반주 생성은 미지원입니다. 선율만 재생할 입력인지 확인하세요")
    melody = voice.get("melody")
    require(isinstance(melody, str) and melody.strip(), "선택한 성부의 melody가 비어 있습니다")
    title = controls.get("title")
    require(isinstance(title, str) and title.strip(), "JGB controls.title이 필요합니다")
    if "tempoBpmUserSet" in document:
        require(type(document["tempoBpmUserSet"]) is bool, "JGB 최상위 tempoBpmUserSet은 true/false여야 합니다")
    require(bpm is not None or document.get("tempoBpmUserSet") is True,
            "재생 빠르기 선택이 확인되지 않았습니다. 최상위 tempoBpmUserSet:true 또는 명시적인 --bpm이 필요합니다; 장단명·기본60·--speed만으로 확정하지 않습니다")
    base_bpm = number(controls.get("tempoBpm") if bpm is None else bpm, "정간 BPM (--bpm)", 1, 800)
    speed = number(speed, "speed", .25, 4)
    hwang = integer(controls.get("hwangPitch") if hwang_midi is None else hwang_midi,
                    "황종 MIDI (--hwang-midi)", 0, 127)
    beats = integer(controls.get("beats"), "각 정간 수 (beats)", 1, 64)
    requested_unit = controls.get("staffUnit", "auto")
    require(isinstance(requested_unit, str), "staffUnit은 문자열이어야 합니다")
    unit = ("eighth" if beats == 12 else "dotted") if requested_unit in ("auto", "") else requested_unit
    require(unit in RATIOS, f"미지원 staffUnit: {unit}")
    ratio = RATIOS[unit]
    quarter_bpm = base_bpm * speed * float(ratio)
    tempo_us = midi_tempo(quarter_bpm)
    scale_name = controls.get("joPreset", "") if jo is None else jo
    require(isinstance(scale_name, str), "joPreset은 문자열이어야 합니다")
    scale = expressions.SCALES.get(scale_name)
    if custom_scale is not None:
        require(jo is None and scale_name in ("", "all"), "상대음 음계와 joPreset/--jo가 중복 지정되었습니다")
        scale = tuple(custom_scale["pitch_classes"])
    overrides = {}
    gak_beats = controls.get("gakBeats", "")
    require(isinstance(gak_beats, str), "gakBeats는 '각:정간 수' 문자열이어야 합니다")
    for entry in re.split(r"[,;\n]", gak_beats):
        if not entry.strip():
            continue
        m = re.fullmatch(r"\s*(\d+)\s*:\s*(\d+)\s*", entry)
        require(m is not None, f"잘못된 gakBeats: {entry!r}")
        g, n = integer(m[1], "각 번호", 1, 10000), integer(m[2], "각 정간 수", 1, 64)
        require(g not in overrides, f"중복 gakBeats: {g}")
        overrides[g] = n

    notes, locations, tempo_cues = [], {}, []
    previous_pitch, previous_gak, cursor, written_cursor = None, None, Fraction(), Fraction()
    sustain_index, sustain_start, breath_applied = None, Fraction(), False
    part_id = voice.get("id")
    require(part_id is None or type(part_id) in (str, int), "성부 ID는 문자열 또는 정수여야 합니다")

    def append(nid, duration, location, pitch=None, grace=False, slur=None):
        require(duration > 0, f"{nid}: 꾸밈이 본음 길이를 소진합니다")
        ticks(duration)
        n = {"id": nid, "duration": str(duration)}
        if pitch is None:
            n["rest"] = True
        else:
            n["pitch"] = pitch
            if grace:
                n["velocity"] = expressions.GRACE_VELOCITY
            if slur:
                n["slur"] = slur
        notes.append(n)
        locations[nid] = [dict(location)]

    # Keep offsets against the original melody, including CRLF; ignore terminal line breaks only.
    end = len(melody.rstrip("\r\n"))
    separators = list(re.finditer(r"\|\|(?:\r\n|\r|\n)?|\r\n|\n|\r", melody[:end]))
    starts = [0] + [m.end() for m in separators]
    ends = [m.start() for m in separators] + [end]
    require(all(g <= len(starts) for g in overrides), "gakBeats에 존재하지 않는 각 번호가 있습니다")
    continuation_entries = document.get("playback", {}).get("gak_continuations", [])
    require(isinstance(continuation_entries, list), "playback.gak_continuations는 배열이어야 합니다")
    continuations, continuation_keys = {}, set()
    for entry in continuation_entries:
        require(isinstance(entry, dict) and set(entry) == {"part", "gak", "basis"},
                "각 경계 지속에는 part·gak·basis가 필요합니다")
        p = integer(entry["part"], "각 경계 지속 성부", 1, len(parts))
        g = integer(entry["gak"], "각 경계 지속 각", 2, 10000)
        require((p, g) not in continuation_keys, "중복 각 경계 지속 위치입니다")
        continuation_keys.add((p, g))
        require(isinstance(entry["basis"], str) and entry["basis"].strip(), "각 경계 지속 근거가 필요합니다")
        if p == selected:
            require(g <= len(starts), "각 경계 지속에 존재하지 않는 각 번호가 있습니다")
            continuations[g] = {"part": p, "gak": g, "basis": entry["basis"].strip()}
    total_cells = 0
    for g, (start, stop) in enumerate(zip(starts, ends), 1):
        line = melody[start:stop]
        cells = line.split("|")
        require(len(cells) == overrides.get(g, beats),
                f"성부 {selected} · {g}각: 실제 {len(cells)}정간, 설정 {overrides.get(g, beats)}정간")
        total_cells += len(cells)
        require(total_cells <= 10000, "10000정간을 초과합니다")
        cell_start = start
        for c, cell in enumerate(cells, 1):
            label = f"성부 {selected} · {g}각 · {c}정간"
            rows = list(re.finditer(r"\S+", cell))
            parsed, row_numbers = [], []
            for r, row in enumerate(rows, 1):
                cues = [] if performance_plan is not None else None
                groups, breath = row_groups(row[0], f"{label} · {r}분박", cues, instrument)
                tempo_cues.extend({"gak": g, "cell": c, "symbol": symbol} for symbol in cues or [])
                if groups or breath or not cues:
                    parsed.append((groups, breath))
                    row_numbers.append(r)
            parsed, row_numbers = parsed or [([], False)], row_numbers or [1]
            rows = parsed
            require(len(rows) <= 3, f"{label}: 4개 이상의 분박은 미지원입니다")
            weight = sum(sum((g.get("weight", 1) for g in groups), Fraction()) / max(1, len(groups))
                         if groups else Fraction(1) for groups, _ in parsed)
            require(weight == len(rows), f"{label}: 길이 기호의 짝이 정간 전체 시가를 보존하지 않습니다")
            cell_breath = False
            for r, (groups, breath) in zip(row_numbers, parsed):
                cell_breath |= breath
                require(len(groups) <= 3 and not (len(rows) == 1 and len(groups) > 1),
                        f"{label}: 좌우 분할에는 상하 분박이 필요하며 한 분박에 최대 3음입니다")
                groups = groups or [{"kind": "hold", "symbols": []}]
                for u, group in enumerate(groups, 1):
                    written_duration = ratio / len(rows) / len(groups) * group.get("weight", 1)
                    duration = expressions.performed_duration(written_duration, group)
                    nid = f"p{selected}.g{g}.c{c}.r{r}.u{u}"
                    loc = {"part_index": selected, "part_id": part_id, "gak": g, "cell": c,
                           "row": r, "unit": u, "cell_start": cell_start,
                           "cell_end": cell_start + len(cell), "text": cell,
                           "kind": group["kind"], "symbols": group["symbols"],
                           "start_quarters": str(cursor), "duration_quarters": str(duration),
                           "written_start_quarters": str(written_cursor),
                           "written_duration_quarters": str(written_duration)}
                    if group.get("semitone_offset"):
                        loc["written_pitch_shift_semitones"] = group["semitone_offset"]
                    written_cursor += written_duration
                    cursor += duration
                    effects = expressions.has_effects(group)
                    require(not effects or controls.get("playSigimsae", True) is True,
                            f"{label}: 시김새 재생이 꺼져 있습니다")
                    kind = group["kind"]
                    crosses_gak = g in continuations and c == 1 and r == row_numbers[0] and u == 1
                    if crosses_gak:
                        require(kind == "hold" and previous_gak == g - 1 and notes and not notes[-1].get("rest"),
                                f"{label}: 각 경계 지속은 바로 앞 각의 본음과 첫 지속칸이 필요합니다")
                        loc["gak_continuation"] = dict(continuations[g])
                    if kind == "hold" and (previous_gak == g or crosses_gak) and notes and not notes[-1].get("rest"):
                        require(not breath_applied, f"{label}: 숨표 뒤의 연장은 명시한 새 음이 필요합니다")
                        target = notes[sustain_index]
                        expressions.validate_sustain_effects(target, group, label)
                        target["duration"] = str(Fraction(target["duration"]) + duration)
                        delay = float(Fraction(loc["start_quarters"]) - sustain_start) * 60 / quarter_bpm
                        expressions.apply_effects(target, group, label, delay, profile, policy,
                                                  locations[target["id"]][0].get("written_pitch_shift_semitones", 0))
                        locations[target["id"]].append(loc)
                        previous_gak = g
                    elif kind in ("rest", "hold"):
                        require(not expressions.has_effects(group, include_fermata=False),
                                f"{label}: 표현을 적용할 앞 본음이 없습니다")
                        append(nid, duration, loc)
                    else:
                        require(not group["symbols"] or controls.get("playSigimsae", True) is True,
                                f"{label}: 시김새 재생이 꺼져 있습니다. 기호 적용 여부를 확인하세요")
                        if kind == "relative":
                            require(previous_pitch is not None, f"{label}: 상대음의 앞 기준음이 없습니다")
                            reference = previous_pitch
                        else:
                            reference = hwang + group["offset"] + group.get("semitone_offset", 0)
                        sequence, grace, post = expressions.resolve_ornament_pitches(
                            group, reference, hwang, scale, label)
                        grace_count = len(grace) + len(post)
                        grace_quarters = expressions.adaptive_grace(duration, grace_count, len(sequence), label)
                        body = duration - grace_count * grace_quarters
                        require(body > 0, f"{label}: 꾸밈이 본음 길이를 소진합니다")
                        if grace_count:
                            loc["grace_timing"] = {"each_quarters": str(grace_quarters),
                                                   "count": grace_count, "main_quarters": str(body),
                                                   "reduced": grace_quarters < expressions.GRACE_QUARTERS}
                        slur = nid if len(grace) + len(sequence) + len(post) > 1 else None
                        for k, pitch in enumerate(grace, 1):
                            append(f"{nid}.grace{k}", grace_quarters, loc, pitch, True, slur)
                        for k, pitch in enumerate(sequence, 1):
                            append(nid if not slur else f"{nid}.main{k}", body / len(sequence), loc, pitch, slur=slur)
                            body_effects = expressions.main_note_effects(group, k, len(sequence))
                            expressions.apply_effects(notes[-1], body_effects, label, profile=profile, policy=policy,
                                                      written_pitch_shift=group.get("semitone_offset", 0))
                        sustain_index = len(notes) - 1
                        sustain_start = cursor - duration + len(grace) * grace_quarters + body * (len(sequence) - 1) / len(sequence)
                        for k, pitch in enumerate(post, 1):
                            append(f"{nid}.postgrace{k}", grace_quarters, loc, pitch, True, slur)
                        previous_pitch, previous_gak = sequence[-1], g
                        breath_applied = False
            if cell_breath:
                require(notes and not notes[-1].get("rest") and previous_gak == g,
                        f"{label}: 숨표를 적용할 본음이 없습니다")
                notes[-1].update(expressions.adaptive_breath(notes[-1]["duration"], quarter_bpm))
                breath_applied = True
                locations[notes[-1]["id"]][-1]["breath"] = True
                locations[notes[-1]["id"]][-1]["breath_timing"] = {
                    k: notes[-1][k] for k in ("breath_seconds", "release_seconds")}
            cell_start += len(cell) + 1
    require(sum(Fraction(n["duration"]) for n in notes) == cursor and written_cursor == total_cells * ratio,
            "JGB 변환 중 전체 길이가 바뀌었습니다")
    assumptions = ["JGB 재생 템포는 정간/분. tempoBpmGak은 표시용이며 사용하지 않음.",
                   "빈칸·-는 같은 각 안에서만 앞 음을 연장. 각 첫 연장은 쉼.",
                   "꾸밈은 각각 최대1/16 quarter·velocity80. 짧은 분박은 같은 tick 길이로 줄여 본음에 자리 절반 이상을 남김. 음열은 본음 자리를 균등 분할.",
                   "추가 떨림 ±25cent 고정. 미지원 표현은 생략하지 않고 실패.",
                   "농음·흘림·미는·풀어내림 수치는 명시된 청취용 근사. 풀어내림은 음높이 농음+하행. 원문점은 효과 미확정 표시로 보존.",
                   "끊는표는 release 포함 반길이 발음. 늘임표는 표시된 자리의 연주시간을 2배로 늘리는 선택값.",
                   "니느라니·느나르나니는 우물사이 음열 대신 공식 단소 교육자료의 음열을 적용.",
                   "JGB를 읽었다는 것은 원보·연주 품질 검수 완료를 뜻하지 않음."]
    if requested_unit in ("auto", ""):
        assumptions.append(f"staffUnit 없는 구형 JGB 또는 auto: 기본각 {beats}정간에 따라 {unit}(정간={ratio} quarter) 적용.")
    score = {"schema": "gugak-reviewed-score/v1", "title": title,
             "source": {"edition": source_name, "review_status": "source-review-unverified",
                        "method": "JGB playback adapter; source review status not inferred"},
             "context": {"pitch_mode": "sounding", "notation_octave_shift": 0, "tuning_a_hz": 440,
                         "style": f"JGB {PROFILES[instrument]['label']}", "tuning_description": f"황종 MIDI {hwang}, A440; no automatic octave correction"},
             "quarter_bpm": quarter_bpm, "notes": notes, "assumptions": assumptions}
    source_map = {"schema": "jgb-playback-map/v1", "jgb_version": document["v"],
                  "part_index": selected, "part_id": part_id, "part_name": voice.get("name"),
                  "offset_unit": "Unicode code points, zero-based, end-exclusive",
                  "jeonggan_bpm": base_bpm, "speed": speed, "staff_unit": unit, "quarter_ratio": str(ratio),
                  "tempo_selection": {"kind": "cli-override" if bpm is not None else "jgb-user-set",
                                      "unit": "jeonggan/minute", "musicological_basis": "not-verified"},
                  "quarter_bpm": quarter_bpm, "midi_tempo_us": tempo_us,
                  "huangjong_midi": hwang, "jo_preset": scale_name,
                  "gaks": len(starts), "cells": total_cells, "total_quarters": str(cursor),
                  "written_total_quarters": str(written_cursor), "symbol_policy": expressions.symbol_policy(instrument),
                  "grace_policy": dict(expressions.GRACE_POLICY), "breath_policy": dict(expressions.BREATH_POLICY),
                  "expression_policy": policy,
                  "expression_profile": profile,
                  "instrument_selection": {"original": voice.get("instrument", "danso" if instrument == "danso" else None),
                                           "selected": instrument, "kind": selection},
                  "tempo_cues": tempo_cues,
                  "notes": locations}
    if continuations:
        source_map["gak_continuations"] = list(continuations.values())
        assumptions[1] = "빈칸·-는 같은 각에서 앞 음을 연장. 근거와 성부·각을 명시한 playback.gak_continuations 위치만 각 경계를 이어 불며 나머지 각 첫 연장은 쉼."
    if instrument != "danso":
        source_map.update(instrument=instrument, instrument_basis=instrument_basis, relative_scale=custom_scale)
        assumptions[3:5] = ["악보에 없는 추가 떨림을 자동 적용하지 않음. 미지원 표현은 생략하지 않고 실패.",
                            "농음·흘림·미는·풀어내림은 playback.expression_policy에 수치·근거를 지정한 경우만 적용."]
        assumptions[6] = ("소금 교재의 네로나·니르나니 음열을 별도로 적용. 느니르나니 끝음은 교재 예시의 두 음 위 선택값이며 한 음 위 대안도 있음."
                          if instrument == "sogeum" else
                          f"{instrument}: source-map.symbol_policy의 악기별 기호 근거를 적용. 미확정 기호는 거부.")
    elif custom_scale is not None:
        source_map["relative_scale"] = custom_scale
    if performance_plan is not None:
        score, source_map = apply_tempo_repeat(score, source_map, performance_plan, input_sha256, instrument=instrument)
    return score, source_map
