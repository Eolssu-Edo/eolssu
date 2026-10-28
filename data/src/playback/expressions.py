"""Explicit local audition profiles; numeric choices are not notation standards."""
from copy import deepcopy

from .score import finite, reject_unknown, require, validate_pitch_curve, validate_yoseong

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
