"""Optional audition expression, separate from symbols authored in the score."""
from copy import deepcopy

from .score import require

PRESETS = ("none", "quarter", "third")


def apply_expression(performance, preset="quarter"):
    """Return a copy with an audition preset; preserve all score expression.

    Depth is the added bend in each direction, in cents (100 cents = semitone).
    These are the 2026-10-23 listening experiment's settings, not a notation rule
    or a measurement of CAT's recorded sample vibrato.
    """
    require(isinstance(preset, str) and preset in PRESETS, "Unknown playback expression preset")
    result = deepcopy(performance)
    result.pop("playback_vibrato", None)
    if preset != "none":
        result["playback_vibrato"] = {
            "preset": preset,
            "cents": 25 if preset == "quarter" else 100 / 3,
            "hz": 4.5,
            "minimum_note_seconds": .65,
            "delay_seconds": .22,
            "fade_in_seconds": .18,
            "fade_out_seconds": .16,
        }
    return result
