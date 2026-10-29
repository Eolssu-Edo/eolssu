"""Strict score IR with explicit unverified provenance. Durations are quarter notes."""
from fractions import Fraction
import json
import math
from pathlib import Path

# Exact ninth/eighteenth-quarter subdivisions, while retaining the previous grid.
PPQN = 14400
MIDI_TEMPO_MAX = 0xFFFFFF
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


def midi_tempo(bpm):
    """Validate the encoded Set Tempo value, not an arbitrary musical BPM band."""
    bpm = finite(bpm, "quarter_bpm", 0, 60_000_000)
    require(bpm > 0, "quarter_bpm must be positive")
    require(bpm >= 60_000_000 / MIDI_TEMPO_MAX,
            "quarter_bpm exceeds MIDI Set Tempo range (1..16777215 microseconds per quarter); "
            "choose a different staffUnit or an explicit faster playback tempo")
    return round(60_000_000 / bpm)


def reject_unknown(obj, allowed, label):
    require(isinstance(obj, dict), f"{label}: expected object")
    require(not (obj.keys() - set(allowed)), f"{label}: unsupported fields {obj.keys() - set(allowed)}")


def validate_pitch_curve(curve):
    """Normalize either a legacy tail bend or explicit piecewise linear points."""
    reject_unknown(curve, ["end_cents", "tail_fraction", "maximum_seconds", "delay_seconds", "points", "step_at_delay"], "pitch_curve")
    delay = finite(curve.get("delay_seconds", 0), "curve delay_seconds", 0, math.inf)
    step = curve.get("step_at_delay", False)
    require(type(step) is bool and ("step_at_delay" not in curve or "points" in curve)
            and (not step or delay > 0),
            "step_at_delay requires explicit points and a positive delay")
    if "points" in curve:
        require(not (curve.keys() & {"end_cents", "tail_fraction", "maximum_seconds"}),
                "pitch_curve points cannot be combined with legacy tail fields")
        points = curve["points"]
        require(isinstance(points, list) and 2 <= len(points) <= 128,
                "pitch_curve points needs 2..128 [fraction, cents] pairs")
        normalized = []
        for point in points:
            require(isinstance(point, list) and len(point) == 2,
                    "pitch_curve point must be [fraction, cents]")
            x = finite(point[0], "curve point fraction", 0, 1)
            y = finite(point[1], "curve point cents", -1200, 1200)
            require(not normalized or x > normalized[-1][0], "pitch_curve fractions must increase strictly")
            normalized.append([x, y])
        require(normalized[0][0] == 0 and normalized[-1][0] == 1,
                "pitch_curve points must include fractions 0 and 1")
        # Before a delayed effect the note remains at its current pitch.
        require(not delay or normalized[0][1] == 0 or step,
                "delayed pitch_curve must start at zero cents")
        return {"points": normalized, "delay_seconds": delay, **({"step_at_delay": True} if step else {})}
    return {"end_cents": finite(curve.get("end_cents"), "curve end_cents", -1200, 1200),
            "tail_fraction": finite(curve.get("tail_fraction"), "curve tail_fraction", .01, 1),
            "maximum_seconds": finite(curve.get("maximum_seconds"), "curve maximum_seconds", .005, 1),
            "delay_seconds": delay}


def validate_yoseong(yoseong, default_mode=None):
    reject_unknown(yoseong, ["mode", "hz", "cents", "depth", "delay_seconds"], "yoseong")
    mode = yoseong.get("mode", default_mode)
    require(mode in ("amplitude", "pitch", "mixed"), "choose yoseong mode from source context")
    return {"mode": mode, "hz": finite(yoseong.get("hz", 4), "yoseong hz", .1, 10),
            "cents": finite(yoseong.get("cents", 25), "yoseong cents", 0, 1200),
            "depth": finite(yoseong.get("depth", .08), "yoseong depth", 0, .5),
            "delay_seconds": finite(yoseong.get("delay_seconds", .25), "yoseong delay", 0, math.inf)}


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


def compile_score(score, instrument, transpose=0, *, max_duration_seconds=600):
    """Resolve pitch once, expand authored decorations, preserve source IDs.

    Transposition is an explicit audition choice; no automatic octave folding.
    """
    duration_limit = finite(max_duration_seconds, "max_duration_seconds", 0, math.inf)
    require(duration_limit > 0, "max_duration_seconds must be positive")
    reject_unknown(score, ["schema", "title", "source", "context", "quarter_bpm", "notes", "assumptions"], "score")
    require(score.get("schema") == "gugak-reviewed-score/v1", "Unsupported score schema")
    require(isinstance(score.get("title"), str) and score["title"], "Title required")
    require(isinstance(score.get("source"), dict) and score["source"].get("edition"), "Source edition required")
    require(score["source"].get("review_status") in (
        "manually-transcribed-unverified", "machine-transcribed-unverified", "source-review-unverified"),
        "Declare transcription review status")
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
    tempo = midi_tempo(score["quarter_bpm"])
    tuning = 1200 * math.log2(finite(c.get("tuning_a_hz", 440), "tuning_a_hz", 420, 460) / 440)
    require(isinstance(score.get("notes"), list) and 0 < len(score["notes"]) <= 10000, "1..10000 notes required")
    events, warnings, seen = [], [], set()
    if profile.get("sample_bank_note"):
        warnings.append(profile["sample_bank_note"])
    if score["source"]["review_status"] == "machine-transcribed-unverified":
        warnings.append("자동 인식 악보의 합성 미리듣기입니다. 원보와 음높이·길이·기호 대조가 필요합니다.")
    elif score["source"]["review_status"] == "source-review-unverified":
        warnings.append("입력 악보의 검수 상태를 확인하지 않은 합성 미리듣기입니다. 원보 대조가 필요합니다.")
    cursor = 0
    tie_open = False
    for n in score["notes"]:
        reject_unknown(n, ["id", "duration", "pitch", "rest", "ornament", "slur", "tie", "velocity", "breath_seconds", "release_seconds", "gate_fraction", "expression", "yoseong", "pitch_curve"], "note")
        nid = n.get("id")
        require(isinstance(nid, str) and nid and nid not in seen, "Unique nonempty note IDs required")
        seen.add(nid)
        duration = ticks(n["duration"])
        require(duration > 0, f"{nid}: duration must be positive")
        start, end = cursor, cursor + duration
        cursor = end
        require(cursor*tempo/1e6/PPQN <= duration_limit,
                f"Score exceeds the configured resource limit of {duration_limit:g} seconds; "
                "increase max_duration_seconds explicitly after checking render memory")
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
        require(not (tie and "gate_fraction" in n), f"{nid}: gate and tie cannot be combined")
        if tie_open:
            require(tie in ("continue", "stop") and events[-1]["pitch"] == pitch and events[-1]["end_tick"] == start,
                    f"{nid}: tie must continue the same adjacent pitch")
            require(not (set(n) & {"ornament", "breath_seconds", "release_seconds", "yoseong", "expression", "pitch_curve"}), f"{nid}: split tie has independent expression")
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
        release = finite(n.get("release_seconds", .05), "release_seconds", 0, .05)
        release = round(release / .05 * 127) / 127 * .05
        if "gate_fraction" in n:
            gate = finite(n["gate_fraction"], "gate_fraction", 0, 1)
            require(gate > 0, f"{nid}: gate_fraction must be positive")
            require("ornament" not in n, f"{nid}: split ornament and gate into explicit notes")
            sec_tick = tempo / 1_000_000 / PPQN
            # Gate includes release. Breath and gate share an end budget, so a
            # staccato + breath is never shortened twice.
            audible_end = min(end - round(breath / sec_tick), start + round(duration * gate))
            release = int(min(release, max(0, audible_end - start) * sec_tick / 2) / .05 * 127) / 127 * .05
            end = audible_end - round(release / sec_tick)
        elif breath:
            # Leave the requested gap after the actual, MIDI-encoded release.
            end -= round((breath + release) * 1_000_000 / tempo * PPQN)
        require(end > start, f"{nid}: breath consumes note")
        expression = n.get("expression", [127, 127, 127])
        require(isinstance(expression, list) and len(expression) == 3, "expression needs start/middle/end")
        for v in expression:
            finite(v, "expression", 0, 127)
        yoseong = n.get("yoseong")
        if yoseong is not None:
            require(isinstance(yoseong, dict), "yoseong: expected object")
            require("정악" in c["style"] or "mode" in yoseong, "Yoseong outside jeongak requires explicit mode")
            yoseong = validate_yoseong(yoseong, profile["yoseong_mode"])
            warnings.append(f"{nid}: 요성 수치는 청취용 설정값이며 전문가 검증 전입니다.")
        curve = n.get("pitch_curve")
        if curve is not None:
            curve = validate_pitch_curve(curve)
            require(curve["delay_seconds"] < (end - start) * tempo / 1_000_000 / PPQN,
                    f"{nid}: pitch curve starts after sounding note")
            require(not (curve["delay_seconds"] and "ornament" in n),
                    f"{nid}: split ornament and delayed pitch curve into explicit notes")
            warnings.append(f"{nid}: 음높이 곡선의 폭·시점은 청취용 근사값입니다.")
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
                           "expression": expression, "yoseong": yoseong, "tuning_cents": tuning,
                           "release_seconds": release, "pitch_curve": curve})
    require(not tie_open, "Unclosed tie")
    require(events, "Score contains no sounding notes")
    # Check after ties and ornaments have their final sounding lengths. A short
    # grace may end before the delay; at least one event of its source note must
    # actually reach an explicitly requested, nonzero modulation.
    audible_yoseong = {}
    for event in events:
        y = event["yoseong"]
        if y and ((y["mode"] in ("pitch", "mixed") and y["cents"] > 0) or
                  (y["mode"] in ("amplitude", "mixed") and y["depth"] > 0)):
            nid = event["parent_ids"][0]
            seconds = (event["end_tick"] - event["start_tick"]) * tempo / 1_000_000 / PPQN
            audible_yoseong[nid] = audible_yoseong.get(nid, False) or y["delay_seconds"] < seconds
    for nid, audible in audible_yoseong.items():
        require(audible, f"{nid}: yoseong starts after sounding note")
    for i, event in enumerate(events):
        prev = events[i-1] if i else None
        event["legato"] = bool(prev and prev["end_tick"] == event["start_tick"] and
                               (prev["parent_ids"] == event["parent_ids"] or
                                event["slur"] and prev["slur"] == event["slur"]))
    warnings.append("악보 재생용 합성음입니다. 실제 운지·호흡·연주 품질을 검증한 결과가 아닙니다.")
    if profile["technical_range"] is None:
        warnings.append("이 악기의 실연 음역이 미확정입니다. CAT 프리셋 범위만 검사합니다.")
    return {"instrument": instrument, "title": score["title"], "source": score["source"], "context": c,
            "transpose_semitones": transpose, "tempo_us": tempo, "ppqn": PPQN, "end_tick": cursor,
            "events": events, "source_notes": score["notes"], "warnings": sorted(set(warnings)), "assumptions": score.get("assumptions", [])}
