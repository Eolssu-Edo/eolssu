"""Strict, reviewed score IR. Fractions are quarter-note units, not seconds."""
from fractions import Fraction
import json
import math
from pathlib import Path

PPQN = 4800
PROFILES = {path.stem: json.loads(path.read_text(encoding="utf-8"))
            for path in sorted(Path(__file__).with_name("profiles").glob("*.json"))}
YUL = dict(zip("黃大太夾姑仲蕤林夷南無應", range(12)))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def finite(value, label, low, high):
    require(isinstance(value, (int, float)) and not isinstance(value, bool)
            and math.isfinite(value) and low <= value <= high, f"{label}: expected {low}..{high}")
    return float(value)


def ticks(value):
    q = Fraction(str(value)) * PPQN
    require(q.denominator == 1 and q >= 0, f"Unrepresentable quarter duration: {value}")
    return int(q)


def reject_unknown(obj, allowed, label):
    require(isinstance(obj, dict), f"{label}: expected object")
    require(not (obj.keys() - set(allowed)), f"{label}: unsupported fields {obj.keys() - set(allowed)}")


def resolve_pitch(value, context):
    mode = context["pitch_mode"]
    if mode == "yul":
        reject_unknown(value, ["yul", "register"], "pitch")
        require(value.get("yul") in YUL and type(value.get("register")) is int, "Explicit yul and register required")
        pitch = context["huangjong_midi"] + YUL[value["yul"]] + value["register"] * 12
    else:
        require(type(value) is int, "MIDI pitch must be an integer")
        pitch = value + context["notation_octave_shift"] * 12
    require(0 <= pitch <= 127, "Resolved pitch outside MIDI range")
    return pitch


def compile_score(score, instrument, transpose=0):
    """Resolve pitch once, expand authored decorations, preserve source IDs.

    Transposition is an explicit audition choice; no automatic octave folding.
    """
    reject_unknown(score, ["schema", "title", "source", "context", "quarter_bpm", "notes", "assumptions"], "score")
    require(score.get("schema") == "gugak-reviewed-score/v1", "Unsupported score schema")
    require(isinstance(score.get("title"), str) and score["title"], "Title required")
    require(isinstance(score.get("source"), dict) and score["source"].get("edition"), "Source edition required")
    require(score["source"].get("review_status") == "manually-transcribed-unverified", "Declare transcription review status")
    require(instrument in PROFILES, "Unknown instrument")
    require(type(transpose) is int and -24 <= transpose <= 24, "Explicit transpose must be -24..24")
    profile = PROFILES[instrument]
    c = score["context"]
    reject_unknown(c, ["pitch_mode", "notation_octave_shift", "huangjong_midi", "tuning_a_hz", "style", "tuning_description"], "context")
    require(c.get("pitch_mode") in ("sounding", "written", "yul"), "Explicit pitch mode required")
    require(c.get("style") and c.get("tuning_description"), "Style and tuning description required")
    if c["pitch_mode"] == "yul":
        require(type(c.get("huangjong_midi")) is int and 0 <= c["huangjong_midi"] <= 127, "Explicit huangjong MIDI required")
        require(c.get("notation_octave_shift", 0) == 0, "Yul register already defines octave")
    else:
        require(type(c.get("notation_octave_shift")) is int and -2 <= c["notation_octave_shift"] <= 2, "Explicit notation octave shift required")
        require(c["pitch_mode"] != "sounding" or c["notation_octave_shift"] == 0, "Sounding input cannot be transposed again by notation")
        require("huangjong_midi" not in c, "Huangjong mapping only applies to yul input")
    bpm = finite(score["quarter_bpm"], "quarter_bpm", 10, 400)
    tempo = round(60_000_000 / bpm)
    tuning = 1200 * math.log2(finite(c.get("tuning_a_hz", 440), "tuning_a_hz", 420, 460) / 440)
    require(isinstance(score.get("notes"), list) and 0 < len(score["notes"]) <= 10000, "1..10000 notes required")
    events, warnings, seen = [], [], set()
    cursor = 0
    tie_open = False
    for n in score["notes"]:
        reject_unknown(n, ["id", "duration", "pitch", "rest", "ornament", "slur", "tie", "velocity", "breath_seconds", "expression", "yoseong"], "note")
        nid = n.get("id")
        require(isinstance(nid, str) and nid and nid not in seen, "Unique nonempty note IDs required")
        seen.add(nid)
        duration = ticks(n["duration"])
        require(duration > 0, f"{nid}: duration must be positive")
        start, end = cursor, cursor + duration
        cursor = end
        require(cursor*tempo/1e6/PPQN <= 600, "Local audition is limited to 10 minutes")
        require(type(n.get("rest", False)) is bool, f"{nid}: rest must be boolean")
        require(n.get("slur") is None or isinstance(n["slur"], str) and n["slur"], f"{nid}: slur must be an ID")
        if n.get("rest", False):
            require(set(n) <= {"id", "duration", "rest"} and not tie_open, f"{nid}: rest has pitch/articulation or interrupts tie")
            continue
        pitch = resolve_pitch(n["pitch"], c) + transpose
        require(0 <= pitch <= 127, f"{nid}: transposed pitch outside MIDI")
        velocity = n.get("velocity", 90)
        require(type(velocity) is int and 1 <= velocity <= 127, f"{nid}: velocity 1..127 required")
        tie = n.get("tie")
        require(tie in (None, "start", "continue", "stop"), f"{nid}: invalid tie")
        if tie_open:
            require(tie in ("continue", "stop") and events[-1]["pitch"] == pitch and events[-1]["end_tick"] == start,
                    f"{nid}: tie must continue the same adjacent pitch")
            require(not (set(n) & {"ornament", "breath_seconds", "yoseong", "expression"}), f"{nid}: split tie has independent expression")
            require("slur" not in n or n["slur"] == events[-1]["slur"], f"{nid}: slur changes inside a tie are unsupported")
            require("velocity" not in n or n["velocity"] == events[-1]["velocity"], f"{nid}: velocity changes inside a tie are unsupported")
            events[-1]["end_tick"] = end
            events[-1]["parent_ids"].append(nid)
            tie_open = tie == "continue"
            continue
        require(tie not in ("continue", "stop"), f"{nid}: unmatched tie")
        tie_open = tie == "start"
        require(not tie_open or not (set(n) & {"ornament", "breath_seconds"}), f"{nid}: unsupported ornament/breath on tie")
        breath = finite(n.get("breath_seconds", 0), "breath_seconds", 0, 1)
        if breath:
            # Leave a real gap after the fixed 50ms release, without extending score time.
            end -= round((breath + .05) * 1_000_000 / tempo * PPQN)
        require(end > start, f"{nid}: breath consumes note")
        expression = n.get("expression", [127, 127, 127])
        require(isinstance(expression, list) and len(expression) == 3, "expression needs start/middle/end")
        for v in expression:
            finite(v, "expression", 0, 127)
        yoseong = n.get("yoseong")
        if yoseong is not None:
            reject_unknown(yoseong, ["mode", "hz", "cents", "depth", "delay_seconds"], "yoseong")
            require("정악" in c["style"] or "mode" in yoseong, "Yoseong outside jeongak requires explicit mode")
            mode = yoseong.get("mode", profile["yoseong_mode"])
            require(mode in ("amplitude", "pitch", "mixed"), f"{nid}: choose yoseong mode from source context")
            yoseong = {"mode": mode, "hz": finite(yoseong.get("hz", 4), "yoseong hz", .1, 10),
                       "cents": finite(yoseong.get("cents", 25), "yoseong cents", 0, 100),
                       "depth": finite(yoseong.get("depth", .08), "yoseong depth", 0, .5),
                       "delay_seconds": finite(yoseong.get("delay_seconds", .25), "yoseong delay", 0, 20)}
            warnings.append(f"{nid}: 요성 수치는 청취용 설정값이며 전문가 검증 전입니다.")
        steps = [(pitch, start, end, "main")]
        ornament = n.get("ornament")
        if ornament is not None:
            reject_unknown(ornament, ["kind", "pitch", "duration"], "ornament")
            kind = ornament.get("kind")
            require(kind in ("upper-return", "grace-before"), f"{nid}: unresolved ornament {kind}")
            other = resolve_pitch(ornament["pitch"], c) + transpose
            step = ticks(ornament.get("duration", "1/16"))
            count = 2 if kind == "upper-return" else 1
            require(step > 0 and step * count < end-start, f"{nid}: ornament consumes main note")
            if kind == "upper-return":
                require(other > pitch, f"{nid}: explicit upper neighbour must be higher")
                steps = [(pitch, start, start+step, "base"), (other, start+step, start+2*step, "upper"), (pitch, start+2*step, end, "return")]
            else:
                steps = [(other, start, start+step, "grace"), (pitch, start+step, end, "main")]
        for p, a, b, role in steps:
            require(0 <= p <= 127, f"{nid}: ornament outside MIDI")
            limits = profile["technical_range"]
            require(limits is None or limits[0] <= p <= limits[1], f"{nid}: {p} outside {instrument} guide range; choose explicit arrangement")
            typical = profile["style_range"]
            if typical and not typical[0] <= p <= typical[1]:
                warnings.append(f"{nid}: MIDI {p}는 {profile['label']} 전통 관행 음역 밖입니다. 실제 편곡 검토가 필요합니다.")
            events.append({"id": f"{nid}.{role}", "parent_ids": [nid], "pitch": p, "start_tick": a, "end_tick": b,
                           "velocity": 80 if role == "grace" else velocity, "slur": n.get("slur"),
                           "expression": expression, "yoseong": yoseong, "tuning_cents": tuning})
    require(not tie_open, "Unclosed tie")
    require(events, "Score contains no sounding notes")
    for i, event in enumerate(events):
        prev = events[i-1] if i else None
        event["legato"] = bool(prev and prev["end_tick"] == event["start_tick"] and
                               (prev["parent_ids"] == event["parent_ids"] or
                                event["slur"] and prev["slur"] == event["slur"]))
    warnings.append("음색 비교용 렌더입니다. 다른 악기의 실제 운지·호흡·편곡 가능성을 검증한 결과가 아닙니다.")
    if profile["technical_range"] is None:
        warnings.append("산조대금 관종별 실연 음역이 미확정입니다. CAT 프리셋 범위만 검사합니다.")
    return {"instrument": instrument, "title": score["title"], "source": score["source"], "context": c,
            "transpose_semitones": transpose, "tempo_us": tempo, "ppqn": PPQN, "end_tick": cursor,
            "events": events, "source_notes": score["notes"], "warnings": sorted(set(warnings)), "assumptions": score.get("assumptions", [])}
