"""Opt-in educational approximations, never source or performance standards.

Approved in Task338: fill unresolved notation with audible teaching examples.
The instrument's established figures take precedence. New numbers/figures below
are authored playback choices, not newly discovered musicological evidence.
"""
from copy import deepcopy

from .score import require

MODE = "educational-preview/v1"
STATUS = "educational-approximation-not-performance-standard"
DEFAULT_SCALE = "hwang-gyemyeon"
BASIS = "S15P31E102-338 user-authorized educational preview; authored audible approximation"
ALIASES = {"요성·농음": "농음표", "농음": "농음표", "요성": "요성표"}
EXTRA_MARKS = {"격음": "geok", "격음표": "geok", "깊은요성": "deep",
               "얕은요성": "shallow", "내림표": "lower", "하성": "lower"}
TIMELINE_MARKS = {"반복표", "도돌이표"}


def mode(document):
    value = document.get("playback", {}).get("notation_policy")
    require(value in (None, "source", MODE), "Unknown playback.notation_policy")
    return value if value == MODE else None


def canonical(name):
    return ALIASES.get(name, name)


def symbol_table(base, additions, confirmed):
    fallback = dict(base)
    fallback.update(additions)
    for names, kind, steps in (
        (("느니라", "neunira"), "grace", (1, 0, -1)),
        (("노라느니", "noraneuni"), "independent", (-1, -2, 1, 2)),
        (("느네느", "neuneneu"), "grace", (0, 2)),
        (("나니르노니르", "nanireunonireu"), "grace", (-1, 1, -2, 1)),
        (("느나르나니", "느니르나니", "neunareunani"), "independent", (1, 2, 1, 0, -1)),
        (("혀치기",), "articulation", ()),
        (("반음올림표",), "alteration", (1,)),
        # 전성(轉聲): only "rolled quickly" is sourced (encykorea E0032321). The
        # wind-instrument sequence is unconfirmed; this short main→upper grace
        # before the sustained main note is an authored audition choice.
        (("전성",), "grace", (0, 1)),
    ):
        fallback.update({name: (kind, steps) for name in names})
    fallback.update(confirmed)
    return fallback


def effect_policy(base, instrument):
    result = deepcopy(base)
    # Preserve Danso's existing choices; the other instruments receive explicit
    # preview values instead of being described as having the same tradition.
    if instrument != "danso":
        result["vibrato"] = {"mode": "pitch", "hz": 4, "cents": 40, "delay_seconds": 0}
    result.update(name=MODE, basis=BASIS, status="audition-policy-not-performance-standard")
    result["kkeok"] = {"points": [[0, 0], [.12, -100], [1, -100]], "delay_seconds": 0}
    return result


def profile_defaults(instrument):
    def policy(**values):
        return {"basis": BASIS, **values}
    def wave(cents, hz=4):
        return {"mode": "pitch", "hz": hz, "cents": cents, "delay_seconds": 0}
    return {"schema": f"{instrument}-expression-profile/v1", "policies": {
        "shake": policy(yoseong=wave(50)),
        "shake-rep": policy(yoseong=wave(100, 6)),
        "bend-down": policy(pitch_curve={"points": [[0, 0], [.65, 0], [.8, -200], [1, -200]]}),
        "bend-up": policy(pitch_curve={"points": [[0, 0], [.65, 0], [1, 200]]}),
        "flow-rep": policy(pitch_curve={"points": [[0, 0], [.3, -100], [.5, 0], [.8, -100], [1, -100]]}),
        "push-rep": policy(pitch_curve={"points": [[0, 0], [.3, 100], [.5, 0], [.8, 100], [1, 100]]}),
    }}


def apply_extra(note, marks, label, delay=0):
    for name in marks:
        kind = EXTRA_MARKS[name]
        if kind in ("deep", "shallow"):
            fields = {"yoseong": {"mode": "pitch", "hz": 4,
                                   "cents": 100 if kind == "deep" else 20, "delay_seconds": delay}}
        elif kind == "lower":
            fields = {"pitch_curve": {"points": [[0, 0], [.25, -200], [1, -200]], "delay_seconds": delay}}
        else:
            require(delay == 0, f"{label}: 격음 attack 근사에는 명시한 새 음이 필요합니다")
            fields = {"velocity": 116, "expression": [127, 75, 90]}
        for key, value in fields.items():
            require(key not in note, f"{label}: 겹친 {key} 표현의 범위를 분리하세요")
            note[key] = value


def evidence(instrument, locations, confirmed, resolved, default_scale, policy, profile):
    approximations, seen = [], set()
    for refs in locations.values():
        for ref in refs:
            for raw in ref["symbols"]:
                key = (ref["gak"], ref["cell"], ref["row"], ref["unit"], raw)
                if key in seen:
                    continue
                seen.add(key)
                name = canonical(raw)
                figure = resolved.get(name)
                established = name in confirmed and name not in ("떠이어표", "roll")
                approximations.append({"symbol": raw, "canonical": name,
                    "gak": ref["gak"], "cell": ref["cell"], "row": ref["row"], "unit": ref["unit"],
                    "basis": "existing instrument figure" if established else BASIS,
                    "status": "existing-instrument-policy" if established else STATUS,
                    "figure": {"kind": figure[0], "steps": list(figure[1])} if figure else None,
                    "roll_rule": "Im to Mu to Im retained; other written pitches use a scale-neighbour roll"
                                 if name in ("떠이어표", "roll") else None,
                    "timbre_limit": "CAT attack/strength approximation; no dedicated geokeum sample"
                                     if name in ("격음", "격음표") else None})
    return {"mode": MODE, "status": STATUS, "basis": BASIS, "instrument": instrument,
            "default_scale_applied": default_scale,
            "default_scale": DEFAULT_SCALE if default_scale else None,
            "approximations": approximations, "expression_policy": deepcopy(policy),
            "expression_profile": deepcopy(profile),
            "extra_effects": {"deep": "4Hz/100cent", "shallow": "4Hz/20cent",
                              "lower": [[0, 0], [.25, -200], [1, -200]],
                              "geok": {"velocity": 116, "expression": [127, 75, 90]}},
            "limitations": ["Authored preview values are not an expert-approved performance.",
                            "Established instrument figures take precedence over educational fallbacks."]}
