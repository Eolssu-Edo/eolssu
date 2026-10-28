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

from .score import PPQN, PROFILES, finite, require, ticks
from .policy import declared_instrument, expression_policy, relative_scale

YUL = "황대태협고중유임이남무응"
PREFIX = {"": 0, "청": 1, "중청": 2, "겹청": 2,
          "배": -1, "탁": -1, "하배": -2, "배탁": -2, "하하배": -3}
NOTE = re.compile(r"(하하배|중청|겹청|하배|배탁|청|배|탁)?([황대태협고중유임이남무응])")
RATIOS = {"dotted": Fraction(3, 2), "plain": Fraction(1), "eighth": Fraction(1, 2)}
SCALES = {"hwang-pyeong": (0, 2, 5, 7, 9), "hwang-gyemyeon": (0, 3, 5, 7, 10),
          "jung-pyeong": (0, 2, 5, 7, 10)}
# (Korean name, registry ID, scale steps). Each grace takes 1/16 quarter.
GRACES = [("니레", "nire", (1,)), ("니라", "nina", (2,)), ("니로", "niro", (3,)),
          ("노네", "none", (-1,)), ("너녜", "neonye", (-2,)),
          ("노니로", "noniro", (0, 1)), ("노리노", "norino", (0, 2)),
          ("네로네", "nerone", (0, -1))]
ATTACHED = [("나니나", "nanina", (0, 1, 0)), ("나느나", "naneuna", (0, -1, 0))]
INDEPENDENT = [("노", "no", (-1,)), ("니", "ni", (1,)), ("로", "ro", (-2,)),
               ("리", "ri", (2,)), ("니나", "nina-dur", (1, 0)),
               ("느나", "neuna", (-1, 0)), ("노라", "nora", (-1, -2)),
               ("느니", "neuni", (1, 2)), ("니로나", "nirona", (1, 0, -1)),
               ("같은음표", "repeat", (0,))]
SYMBOLS = {alias: (kind, steps) for kind, table in
           (("grace", GRACES), ("attached", ATTACHED), ("independent", INDEPENDENT))
           for korean, registry, steps in table for alias in (korean, registry)}
SYMBOLS.update({name: ("surround", (-1,)) for name in ("느니르", "neunireu")})
GRACE_QUARTERS = Fraction(1, 16)
BREATH_SECONDS = .12
BREATH_POLICY = {"name": "bounded-half-note-v1", "target_silence_seconds": .12,
                 "maximum_release_seconds": .05, "minimum_gate_fraction": .5}
EXPRESSION_POLICY = {
    "name": "danso-notated-expression-v1", "status": "audition-policy-not-performance-standard",
    "vibrato": {"mode": "amplitude", "hz": 4, "depth": .08, "delay_seconds": 0},
    "fall": {"end_cents": -50, "tail_fraction": .3, "maximum_seconds": .2},
    "unresolved_display_annotations": ["원문점"],
}
LENGTH_MARKS = {"덧길이표": Fraction(3, 2), "반길이표": Fraction(1, 2)}
EFFECT_MARKS = {"농음표": "vibrato", "떠는표": "vibrato", "vib": "vibrato",
                "흘림표": "fall", "flow": "fall"}


def adaptive_breath(duration, quarter_bpm):
    """Budget breath and release together; retain at least half the MIDI ticks."""
    sec_tick = round(60_000_000 / quarter_bpm) / 1_000_000 / PPQN
    if round((BREATH_SECONDS + .05) / sec_tick) <= ticks(duration) // 2:
        return {"breath_seconds": BREATH_SECONDS, "release_seconds": .05}
    cut = min(round((BREATH_SECONDS + .05) / sec_tick), ticks(duration) // 2) * sec_tick
    require(cut > 0, "숨표를 표현할 MIDI 시간 해상도가 부족합니다")
    # CC72 uses a project-local linear 0..50ms release scale. Round down so the
    # quantized release cannot consume the reserved silence of a very short note.
    release = int(min(.05, cut / 2) / .05 * 127 + 1e-9) / 127 * .05
    return {"breath_seconds": cut - release, "release_seconds": release}


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


def row_groups(text, label):
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
            if adjustment:
                require(all(re.fullmatch(r"[+-]?(?:\d+(?:\.\d*)?|\.\d+)", v)
                            for v in adjustment.split(",")), f"{label}: 잘못된 기호 배치값")
            if name in ("쉼표", "쉼", "pause_007"):
                groups.append({"kind": "rest", "symbols": [name]})
            elif name in ("이음표", "-", "빈지속"):
                groups.append({"kind": "hold", "symbols": [name]})
            elif name in LENGTH_MARKS or name in EFFECT_MARKS or name == "원문점":
                require(groups, f"{label}: {{{name}}} 앞에 본음/지속 자리가 필요합니다")
                group = groups[-1]
                if name in LENGTH_MARKS:
                    require("weight" not in group, f"{label}: 겹친 길이 기호")
                    group["weight"] = LENGTH_MARKS[name]
                elif name in EFFECT_MARKS:
                    require(group["kind"] in ("note", "relative", "hold"), f"{label}: {{{name}}}의 본음이 필요합니다")
                    effect = EFFECT_MARKS[name]
                    require(effect not in group, f"{label}: 겹친 {name}")
                    group[effect] = True
                group["symbols"].append(name)
            else:
                require(name in SYMBOLS, f"{label}: 미지원 기호 {{{name}}}; 생략하지 않고 중단합니다")
                kind, steps = SYMBOLS[name]
                if kind == "independent":
                    groups.append({"kind": "relative", "sequence": steps, "symbols": [name]})
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
                instrument="danso"):
    """Return the existing score IR and a map from IR note IDs to JGB locations."""
    require(isinstance(document, dict) and type(document.get("v")) is int
            and document["v"] in (1, 2), "지원하는 JGB 버전은 v1·v2입니다")
    controls = document.get("controls")
    require(isinstance(controls, dict), "JGB controls가 필요합니다")
    if document["v"] == 1:
        require("parts" not in document, "JGB v1에 parts가 함께 있습니다")
        parts = [{"id": None, "name": None, "instrument": document.get("ornInstrument"),
                  "melody": document.get("melody")}]
    else:
        parts = document.get("parts")
    require(isinstance(parts, list) and parts and all(isinstance(p, dict) for p in parts),
            "JGB parts가 필요합니다")
    require(part is not None or len(parts) == 1, "여러 성부가 있습니다. --part로 1부터 성부 번호를 선택하세요")
    selected = integer(1 if part is None else part, "part", 1, len(parts))
    voice = parts[selected - 1]
    instrument_basis = declared_instrument(document, voice, instrument)
    policy = expression_policy(document, instrument, EXPRESSION_POLICY)
    custom_scale = relative_scale(document)

    def effect(name, label):
        require(policy is not None and name in policy,
                f"{label}: {instrument}의 {name} 해석에 playback.expression_policy가 필요합니다")
        return dict(policy[name])
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
    quarter_bpm = finite(base_bpm * speed * float(ratio), "quarter_bpm", 10, 400)
    scale_name = controls.get("joPreset", "") if jo is None else jo
    require(isinstance(scale_name, str), "joPreset은 문자열이어야 합니다")
    scale = SCALES.get(scale_name)
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

    notes, locations = [], {}
    previous_pitch, previous_gak, cursor = None, None, Fraction()
    part_id = voice.get("id")
    require(part_id is None or type(part_id) in (str, int), "성부 ID는 문자열 또는 정수여야 합니다")

    def step(pitch, degree, label):
        if degree == 0:
            return pitch
        require(scale is not None, f"{label}: 상대음 시김새에는 명시한 조가 필요합니다 (--jo)")
        direction = 1 if degree > 0 else -1
        for _ in range(abs(degree)):
            pitch += direction
            while (pitch - hwang) % 12 not in scale:
                pitch += direction
        return pitch

    def append(nid, duration, location, pitch=None, grace=False, slur=None):
        require(duration > 0, f"{nid}: 꾸밈이 본음 길이를 소진합니다")
        ticks(duration)
        n = {"id": nid, "duration": str(duration)}
        if pitch is None:
            n["rest"] = True
        else:
            n["pitch"] = pitch
            if grace:
                n["velocity"] = 80
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
            require(len(rows) <= 3, f"{label}: 4개 이상의 분박은 미지원입니다")
            rows = rows or [None]
            parsed = [row_groups(row[0], f"{label} · {r}분박") if row else ([], False)
                      for r, row in enumerate(rows, 1)]
            weight = sum(sum((g.get("weight", 1) for g in groups), Fraction()) / max(1, len(groups))
                         if groups else Fraction(1) for groups, _ in parsed)
            require(weight == len(rows), f"{label}: 길이 기호의 짝이 정간 전체 시가를 보존하지 않습니다")
            cell_breath = False
            for r, (groups, breath) in enumerate(parsed, 1):
                cell_breath |= breath
                require(len(groups) <= 3 and not (len(rows) == 1 and len(groups) > 1),
                        f"{label}: 좌우 분할에는 상하 분박이 필요하며 한 분박에 최대 3음입니다")
                groups = groups or [{"kind": "hold", "symbols": []}]
                for u, group in enumerate(groups, 1):
                    duration = ratio / len(rows) / len(groups) * group.get("weight", 1)
                    nid = f"p{selected}.g{g}.c{c}.r{r}.u{u}"
                    loc = {"part_index": selected, "part_id": part_id, "gak": g, "cell": c,
                           "row": r, "unit": u, "cell_start": cell_start,
                           "cell_end": cell_start + len(cell), "text": cell,
                           "kind": group["kind"], "symbols": group["symbols"],
                           "start_quarters": str(cursor), "duration_quarters": str(duration)}
                    cursor += duration
                    kind = group["kind"]
                    if kind == "hold" and previous_gak == g and notes and not notes[-1].get("rest"):
                        require("breath_seconds" not in notes[-1], f"{label}: 숨표 뒤의 연장은 명시한 새 음이 필요합니다")
                        if group.get("vibrato") or group.get("fall"):
                            require(controls.get("playSigimsae", True) is True, f"{label}: 시김새 재생이 꺼져 있습니다")
                        if group.get("vibrato") and "yoseong" not in notes[-1]:
                            marked = effect("vibrato", label)
                            marked["delay_seconds"] += float(Fraction(notes[-1]["duration"])) * 60 / quarter_bpm
                            notes[-1]["yoseong"] = marked
                        if group.get("fall"):
                            notes[-1]["pitch_curve"] = effect("fall", label)
                        notes[-1]["duration"] = str(Fraction(notes[-1]["duration"]) + duration)
                        locations[notes[-1]["id"]].append(loc)
                    elif kind in ("rest", "hold"):
                        require(not (group.get("vibrato") or group.get("fall")), f"{label}: 표현을 적용할 앞 본음이 없습니다")
                        append(nid, duration, loc)
                    else:
                        require(not group["symbols"] or controls.get("playSigimsae", True) is True,
                                f"{label}: 시김새 재생이 꺼져 있습니다. 기호 적용 여부를 확인하세요")
                        if kind == "relative":
                            require(previous_pitch is not None, f"{label}: 상대음의 앞 기준음이 없습니다")
                            reference = previous_pitch
                        else:
                            reference = hwang + group["offset"]
                        sequence = [step(reference, d, label) for d in group.get("sequence", (0,))]
                        grace = [step(reference, d, label) for d in group.get("grace", ())]
                        post = [step(reference, d, label) for d in group.get("post_grace", ())]
                        body = duration - (len(grace) + len(post)) * GRACE_QUARTERS
                        require(body > 0, f"{label}: 꾸밈이 본음 길이를 소진합니다")
                        slur = nid if len(grace) + len(sequence) + len(post) > 1 else None
                        for k, pitch in enumerate(grace, 1):
                            append(f"{nid}.grace{k}", GRACE_QUARTERS, loc, pitch, True, slur)
                        for k, pitch in enumerate(sequence, 1):
                            append(nid if not slur else f"{nid}.main{k}", body / len(sequence), loc, pitch, slur=slur)
                            if group.get("vibrato"):
                                notes[-1]["yoseong"] = effect("vibrato", label)
                            if group.get("fall") and k == len(sequence):
                                notes[-1]["pitch_curve"] = effect("fall", label)
                        for k, pitch in enumerate(post, 1):
                            append(f"{nid}.postgrace{k}", GRACE_QUARTERS, loc, pitch, True, slur)
                        previous_pitch, previous_gak = sequence[-1], g
            if cell_breath:
                require(notes and not notes[-1].get("rest") and previous_gak == g,
                        f"{label}: 숨표를 적용할 본음이 없습니다")
                notes[-1].update(adaptive_breath(notes[-1]["duration"], quarter_bpm))
                locations[notes[-1]["id"]][-1]["breath"] = True
                locations[notes[-1]["id"]][-1]["breath_timing"] = {
                    k: notes[-1][k] for k in ("breath_seconds", "release_seconds")}
            cell_start += len(cell) + 1
    require(sum(Fraction(n["duration"]) for n in notes) == cursor == total_cells * ratio,
            "JGB 변환 중 전체 길이가 바뀌었습니다")
    assumptions = ["JGB 재생 템포는 정간/분. tempoBpmGak은 표시용이며 사용하지 않음.",
                   "빈칸·-는 같은 각 안에서만 앞 음을 연장. 각 첫 연장은 쉼.",
                   "앞꾸밈 각각 1/16 quarter·velocity80, 음열은 해당 자리를 균등 분할. 숨+감쇠는 본음 절반 이내.",
                   ("추가 떨림 ±25cent 고정." if instrument == "danso" else "악보에 없는 추가 떨림을 자동 적용하지 않음.") + " 미지원 표현은 생략하지 않고 실패.",
                   "농음·흘림은 기록한 청취용 정책으로 근사. 원문점은 효과 미확정 표시로 보존.",
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
                  "instrument": instrument, "instrument_basis": instrument_basis,
                  "offset_unit": "Unicode code points, zero-based, end-exclusive",
                  "jeonggan_bpm": base_bpm, "speed": speed, "staff_unit": unit, "quarter_ratio": str(ratio),
                  "quarter_bpm": quarter_bpm, "huangjong_midi": hwang, "jo_preset": scale_name,
                  "gaks": len(starts), "cells": total_cells, "total_quarters": str(cursor),
                  "breath_policy": dict(BREATH_POLICY), "expression_policy": policy,
                  "relative_scale": custom_scale,
                  "notes": locations}
    return score, source_map
