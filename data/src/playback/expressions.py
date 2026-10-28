"""Danso notation semantics and expression policies used while building score.

Numeric choices are not notation standards. Parsing and source positions live
in jgb.py; this module owns ornament pitches, timing, effects and profiles.

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
from copy import deepcopy
from fractions import Fraction
import math

from .score import PPQN, finite, reject_unknown, require, ticks, validate_pitch_curve, validate_yoseong

SCALES = {"hwang-pyeong": (0, 2, 5, 7, 9), "hwang-gyemyeon": (0, 3, 5, 7, 10),
          "jung-pyeong": (0, 2, 5, 7, 10)}
# (Korean name, registry ID, scale steps). Grace timing is bounded by its slot.
GRACES = [("니레", "nire", (1,)), ("니라", "nina", (2,)), ("니로", "niro", (3,)),
          ("노네", "none", (-1,)), ("너녜", "neonye", (-2,)),
          ("노니로", "noniro", (0, 1)), ("노리노", "norino", (0, 2)),
          ("네로네", "nerone", (0, -1)), ("떠이어표", "roll", (0, 1)),
          ("느네느", "neuneneu", (0, 3)), ("나니로", "naniro", (-1, 1)),
          ("느로니르", "neuronireu", (0, -1, 1))]
ATTACHED = [("나니나", "nanina", (0, 1, 0)), ("나느나", "naneuna", (0, -1, 0))]
INDEPENDENT = [("노", "no", (-1,)), ("니", "ni", (1,)), ("로", "ro", (-2,)),
               ("리", "ri", (2,)), ("니나", "nina-dur", (1, 0)),
               ("느나", "neuna", (-1, 0)), ("노라", "nora", (-1, -2)),
               ("느니", "neuni", (1, 2)), ("니로나", "nirona", (1, 0, -1)),
               ("니레나", "nirena", (2, 1, 0)), ("네로나", "nerona", (-1, -2, 0)),
               ("니느라니", "nineurani", (1, 0, 1, 0)),
               ("느나니나", "neunanina", (-1, 0, 1, 0)),
               ("느나르나니", "neunareunani", (1, 2, 1, 0, 2)),
               ("같은음표", "repeat", (0,))]
SYMBOLS = {alias: (kind, steps) for kind, table in
           (("grace", GRACES), ("attached", ATTACHED), ("independent", INDEPENDENT))
           for korean, registry, steps in table for alias in (korean, registry)}
SYMBOLS.update({name: ("surround", (-1,)) for name in ("느니르", "neunireu")})
SYMBOLS.update({name: ("surround", (1,)) for name in ("니루니", "니루-니", "niruni")})
SYMBOLS.update({"너네": SYMBOLS["너녜"], "나니르": SYMBOLS["나니로"],
                "로니로": ("grace", (-2, 1)), "느니-르": SYMBOLS["느니르"],
                "느니르나니": SYMBOLS["느나르나니"]})
# These two previously unsupported figures follow official Danso teaching,
# not the conflicting sequences in the pinned editor registry.
SYMBOL_POLICY = {"name": "danso-official-figures-2024-v1",
                 "upstream_differences": {"nineurani": [1, 0, 1, 0],
                                          "neunareunani": [1, 2, 1, 0, 2]},
                 "surround_anchor": "2020 Korean music braille commentary: long middle is the written note"}
GRACE_QUARTERS = Fraction(1, 16)
GRACE_VELOCITY = 80
GRACE_POLICY = {"name": "bounded-half-slot-v1", "maximum_quarters": "1/16",
                "maximum_slot_fraction": "1/2", "equal_grace_lengths": True,
                "quantization": "whole MIDI ticks; main sequence remains divisible",
                "status": "playback-choice-not-performance-standard"}
BREATH_SECONDS = .12
BREATH_POLICY = {"name": "bounded-half-note-v1", "target_silence_seconds": .12,
                 "maximum_release_seconds": .05, "minimum_gate_fraction": .5}
EXPRESSION_POLICY = {
    "name": "danso-notated-expression-v2", "status": "audition-policy-not-performance-standard",
    "vibrato": {"mode": "amplitude", "hz": 4, "depth": .08, "delay_seconds": 0},
    "fall": {"end_cents": -50, "tail_fraction": .3, "maximum_seconds": .2},
    "push": {"end_cents": 50, "tail_fraction": .3, "maximum_seconds": .2},
    "unwind": {"yoseong": {"mode": "pitch", "hz": 4, "cents": 25, "delay_seconds": 0},
               "pitch_curve": {"end_cents": -50, "tail_fraction": .6, "maximum_seconds": .6}},
    "staccato": {"gate_fraction": .5, "release_in_gate": True},
    "fermata": {"duration_multiplier": 2, "scope": "marked slot; following onsets shift"},
    "unresolved_display_annotations": ["원문점"],
}
LENGTH_MARKS = {"덧길이표": Fraction(3, 2), "len-double": Fraction(3, 2),
                "반길이표": Fraction(1, 2), "len-half": Fraction(1, 2)}
EFFECT_MARKS = {"농음표": "vibrato", "떠는표": "vibrato", "vib": "vibrato",
                "흘림표": "fall", "flow": "fall", "미는표": "push", "push": "push",
                "풀어내림표": "unwind", "풀어내리는표": "unwind", "vib-long": "unwind",
                "끊는표": "staccato", "staccato": "staccato", "늘임표": "fermata", "fermata": "fermata"}
PROFILE_MARKS = {alias: kind for kind, aliases in {
    "accent": ("특강표", "accent"), "shake": ("요성표", "shake"),
    "shake-rep": ("겹요성표", "shake-rep"), "flow-rep": ("겹흘림표", "flow-rep"),
    "push-rep": ("겹미는표", "push-rep"), "bend-down": ("퇴성", "퇴성표", "bend-down"),
    "bend-up": ("추성", "추성표", "bend-up"),
}.items() for alias in aliases}
UNRESOLVED = {"느니라", "neunira", "나니르노니르", "nanireunonireu", "노라느니", "noraneuni",
              "반복표", "도돌이표", "격음표", "격음", "내림표", "하성", "꺾는음", "깊은요성", "얕은요성"}
UNRESOLVED_REASONS = {
    "격음표": "지공 조작에 따른 음색의 합성이 미확정입니다. 격음 전용 샘플·운지 정책이 필요하며 특강표로 대체하지 않습니다",
    "격음": "지공 조작에 따른 음색의 합성이 미확정입니다. 격음 전용 샘플·운지 정책이 필요합니다",
    "내림표": "하성의 JGB ID·합성 대응이 미확정입니다. 퇴성으로 치환하지 않습니다",
    "하성": "하성의 JGB ID·합성 대응이 미확정입니다. 퇴성으로 치환하지 않습니다",
    "꺾는음": "으뜸음·음계 문맥과 기호 대응이 미확정입니다. 도착음·복귀 구간을 확인해야 합니다",
    "깊은요성": "글리프 연결이 미확정입니다. 깊이 수치는 표현 프로필에 명시할 수 있으나 요성표의 별칭으로 추정하지 않습니다",
    "얕은요성": "글리프 연결이 미확정입니다. 깊이 수치는 표현 프로필에 명시할 수 있으나 요성표의 별칭으로 추정하지 않습니다",
    "반복표": "원문 반복 범위가 미확정입니다. --performance-plan에 각 범위와 횟수를 명시하세요",
    "도돌이표": "원문 반복 범위가 미확정입니다. --performance-plan에 각 범위와 횟수를 명시하세요",
}


def apply_effects(note, group, label, delay=0, profile=None):
    """Keep ambiguous superimposed curves explicit instead of overwriting them."""
    if group.get("vibrato") or group.get("unwind"):
        require(not (group.get("vibrato") and group.get("unwind")), f"{label}: 농음과 풀어내림을 겹쳐 지정할 수 없습니다")
        require("yoseong" not in note, f"{label}: 겹친 농음/풀어내림의 범위를 분리하세요")
        policy = EXPRESSION_POLICY["unwind"]["yoseong"] if group.get("unwind") else EXPRESSION_POLICY["vibrato"]
        note["yoseong"] = dict(policy, delay_seconds=delay)
    curves = [effect for effect in ("fall", "push", "unwind") if group.get(effect)]
    require(len(curves) <= 1, f"{label}: 하행·상행·풀어내림을 겹쳐 지정할 수 없습니다")
    if curves:
        require("pitch_curve" not in note, f"{label}: 지속에 겹친 음높이 곡선의 범위를 분리하세요")
        effect = curves[0]
        note["pitch_curve"] = dict(EXPRESSION_POLICY[effect]["pitch_curve"] if effect == "unwind" else EXPRESSION_POLICY[effect])
        if delay:
            note["pitch_curve"]["delay_seconds"] = delay
    if group.get("staccato"):
        note["gate_fraction"] = EXPRESSION_POLICY["staccato"]["gate_fraction"]
    for kind in group.get("profile_effects", []):
        try:
            overrides = expression_for(profile, kind)
        except ValueError as exc:
            raise ValueError(f"{label}: {exc}") from None
        for key, value in overrides.items():
            require(key not in note or key == "velocity", f"{label}: 겹친 {key} 표현의 범위를 분리하세요")
            if delay:
                require(key in ("yoseong", "pitch_curve"),
                        f"{label}: 지속 중 {kind}의 강세 적용에는 명시한 새 음이 필요합니다")
                value["delay_seconds"] = value.get("delay_seconds", 0) + delay
            note[key] = value


def adaptive_grace(duration, count, main_count, label):
    """Retain the existing cap while reserving at least half the slot for main notes."""
    if not count:
        return Fraction()
    slot_ticks = ticks(duration)
    grace_ticks = min(ticks(GRACE_QUARTERS), slot_ticks // (2 * count))
    # Equal front/back graces and an evenly split main figure must both fit the
    # exact MIDI grid. At most main_count candidates cover every residue class.
    for _ in range(main_count):
        if (slot_ticks - count * grace_ticks) % main_count == 0:
            break
        grace_ticks -= 1
    require(grace_ticks > 0 and (slot_ticks - count * grace_ticks) % main_count == 0,
            f"{label}: 꾸밈과 본음을 보존할 MIDI 시간 해상도가 부족합니다")
    return Fraction(grace_ticks, PPQN)


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


# Fixed audition motion, separate from notation and the CAT sample's own vibrato.
PLAYBACK_VIBRATO = {
    "cents": 25, "hz": 4.5, "minimum_note_seconds": .65,
    "delay_seconds": .22, "fade_in_seconds": .18, "fade_out_seconds": .16,
}
SCHEMA = "danso-expression-profile/v1"
KINDS = {"accent", "shake", "shake-rep", "bend-down", "bend-up", "flow-rep", "push-rep"}
STATUS = "playback-choice-not-performance-standard"
DEFAULT_ACCENT = {
    "basis": "특강표의 세게 연주하는 뜻을 velocity 110과 CC11 127→90→90으로 근사한 재생 선택값",
    "status": STATUS,
    "note": {"velocity": 110, "expression": [127, 90, 90]},
}


def resolve_expression_profile(profile=None):
    """Return validated policies. Missing pitch gestures have no invented defaults.

    Input policies contain ``basis`` and score note fields at the same level.
    Resolved policies contain ``basis``, ``status`` and normalized ``note`` fields.
    This file is a CLI rendering option, not a change to the JGB data contract.
    """
    result = {"schema": SCHEMA, "policies": {"accent": deepcopy(DEFAULT_ACCENT)}}
    if profile is None:
        return result
    reject_unknown(profile, ["schema", "policies"], "expression profile")
    require(profile.get("schema") == SCHEMA, "Unsupported expression profile schema")
    policies = profile.get("policies")
    reject_unknown(policies, KINDS, "expression policies")
    for kind, policy in policies.items():
        reject_unknown(policy, ["basis", "velocity", "expression", "yoseong", "pitch_curve"], f"{kind} policy")
        basis = policy.get("basis")
        require(isinstance(basis, str) and basis.strip(), f"{kind}: explicit basis required")
        note = {}
        if "velocity" in policy:
            value = policy["velocity"]
            require(type(value) is int and 1 <= value <= 127, f"{kind}: velocity 1..127 required")
            note["velocity"] = value
        if "expression" in policy:
            values = policy["expression"]
            require(isinstance(values, list) and len(values) == 3, f"{kind}: expression needs start/middle/end")
            note["expression"] = [finite(v, "expression", 0, 127) for v in values]
        if "yoseong" in policy:
            y = policy["yoseong"]
            require(isinstance(y, dict) and {"mode", "hz", "delay_seconds"} <= y.keys(),
                    f"{kind}: yoseong requires explicit mode, hz and delay_seconds")
            require(y.get("mode") not in ("pitch", "mixed") or "cents" in y,
                    f"{kind}: pitch yoseong requires explicit cents")
            require(y.get("mode") not in ("amplitude", "mixed") or "depth" in y,
                    f"{kind}: amplitude yoseong requires explicit depth")
            note["yoseong"] = validate_yoseong(y)
        if "pitch_curve" in policy:
            note["pitch_curve"] = validate_pitch_curve(policy["pitch_curve"])
        if kind == "accent":
            require(note and not (note.keys() & {"yoseong", "pitch_curve"}),
                    "accent policy must use velocity/expression only")
        elif kind in ("shake", "shake-rep"):
            require("yoseong" in note, f"{kind}: explicit yoseong required")
            y = note["yoseong"]
            require((y["mode"] in ("pitch", "mixed") and y["cents"] > 0) or
                    (y["mode"] in ("amplitude", "mixed") and y["depth"] > 0),
                    f"{kind}: yoseong must contain nonzero modulation")
        else:
            require("pitch_curve" in note, f"{kind}: explicit pitch_curve required")
            curve = note["pitch_curve"]
            points = curve.get("points", [[0, 0], [1, curve.get("end_cents")]])
            values = [p[1] for p in points]
            if kind in ("bend-down", "bend-up"):
                sign = -1 if kind == "bend-down" else 1
                require(all(sign*v >= 0 for v in values) and any(sign*v > 0 for v in values),
                        f"{kind}: pitch curve direction contradicts the selected symbol")
            else:
                require("points" in curve, f"{kind}: repeated gesture requires explicit points")
                sign = -1 if kind == "flow-rep" else 1
                directions = [sign*(b-a) for a, b in zip(values, values[1:]) if b != a]
                # Count separate descents/ascents, not adjacent pieces of one slope.
                gestures = sum(d > 0 and (i == 0 or directions[i-1] < 0)
                               for i, d in enumerate(directions))
                require(gestures >= 2, f"{kind}: curve needs at least two distinct gestures")
        result["policies"][kind] = {"basis": basis.strip(), "status": STATUS, "note": note}
    return result


def expression_for(profile, kind):
    require(kind in KINDS, f"Unknown expression kind: {kind}")
    require(kind in profile["policies"],
            f"{kind}: numerical playback choice is required in --expression-profile; no default inferred")
    return deepcopy(profile["policies"][kind]["note"])


# Placement is a notation rule; jgb.py consumes these sets while parsing.
INDEPENDENT_PROFILE_EFFECTS = {"shake", "shake-rep"}
HOLD_PROFILE_EFFECTS = INDEPENDENT_PROFILE_EFFECTS | {"bend-down", "bend-up"}


def has_effects(group, *, include_fermata=True):
    effects = set(EFFECT_MARKS.values()) - (set() if include_fermata else {"fermata"})
    return bool(group.get("profile_effects") or any(group.get(effect) for effect in effects))


def validate_sustain_effects(note, group, label):
    require("gate_fraction" not in note and not group.get("staccato"),
            f"{label}: 끊는표와 지속을 함께 지정하려면 새 음으로 분리하세요")


def performed_duration(written_duration, group):
    multiplier = EXPRESSION_POLICY["fermata"]["duration_multiplier"] if group.get("fermata") else 1
    return written_duration * multiplier


def main_note_effects(group, index, count):
    """Continuous vibrato spans the figure; ending effects apply to its last main."""
    return group if index == count else {"vibrato": group.get("vibrato")}


def resolve_ornament_pitches(group, reference, hwang, scale, label):
    """Return main, front-grace and post-grace pitches without changing the source."""
    def step(pitch, degree):
        if degree == 0:
            return pitch
        require(scale is not None, f"{label}: 상대음 시김새에는 명시한 조가 필요합니다 (--jo)")
        direction = 1 if degree > 0 else -1
        for _ in range(abs(degree)):
            pitch += direction
            while (pitch - hwang) % 12 not in scale:
                pitch += direction
        return pitch

    is_roll = any(s in ("떠이어표", "roll") for s in group["symbols"])
    if is_roll:
        require((reference - hwang) % 12 == 7, f"{label}: 떠이어표는 임→무→임 교육 예시만 확인됐습니다")
    sequence = [step(reference, d) for d in group.get("sequence", (0,))]
    # The official Im/Mu example is three semitones, not the next scale degree.
    grace = ([reference, reference + 3] if is_roll else
             [step(reference, d) for d in group.get("grace", ())])
    post = [step(reference, d) for d in group.get("post_grace", ())]
    require(not group.get("staccato") or not (grace or post or len(sequence) > 1),
            f"{label}: 장식음열과 끊는표의 적용 범위를 새 음으로 분리하세요")
    return sequence, grace, post


def fit_breath_to_duration(note, seconds):
    """Retain the authored breath unless retiming requires the half-note budget."""
    if note.get("breath_seconds"):
        breath, release = note["breath_seconds"], note.get("release_seconds", .05)
        budget = min(breath + release, seconds / 2)
        if budget < breath + release:
            new_release = math.floor(release * budget / (breath + release) / .05 * 127) / 127 * .05
            note["release_seconds"], note["breath_seconds"] = new_release, budget - new_release
