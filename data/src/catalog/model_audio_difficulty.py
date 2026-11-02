"""Provisional written-score difficulty; not an expert or acoustic assessment.

Five equally weighted axes (0..2 each): attacks/sounding second (>1.5, >3),
shortest NOTE (<.5s, <.25s), pitch span (>12, >24 semitones) OR >=7-semitone
leap ratio (>=.10, >=.25), under-half-jeonggan NOTE ratio (>=.10, >=.30),
and annotated NOTE ratio (>=.10, >=.30). The pitch axis takes the larger score.
Totals 0..2 / 3..5 / 6..10 map to existing FE 1=입문 / 2=초급 / 3=중급.
These are initial, reviewable thresholds, not a confirmed teaching standard.
Rest padding cannot dilute attack rate. Written ticks, not rendered ornaments,
set durations; all sigimsae strings count as notation-reading load, once/note.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path

from data.src.playback.common_score import YUL_NAMES, validate_common

POLICY_VERSION = "score-difficulty/provisional-fe3/v1"
LABELS = {1: "입문", 2: "초급", 3: "중급"}


def classify_difficulty(source, bpm):
    """Return new evidence without modifying the canonical source object."""
    if type(bpm) not in (int, float) or not math.isfinite(bpm) or bpm <= 0:
        raise ValueError("bpm must be a finite positive number")
    validate_common(source, allow_sigimsae=True)
    notes = [e for e in source["events"] if e["kind"] == "NOTE"]
    if not notes:
        raise ValueError("At least one NOTE is required")
    music = source["music"]
    seconds = [e["durationTick"] / music["ticksPerBeat"] * 60 / bpm for e in notes]
    if not all(math.isfinite(s) and s > 0 for s in seconds):
        raise ValueError("NOTE duration must be finite and positive")
    pitches = [YUL_NAMES.index(e["yul"]) + 12 * e["octave"] for e in notes]
    leaps = [abs(b - a) for a, b in zip(pitches, pitches[1:])]
    sounding = sum(seconds)
    rate, shortest = len(notes) / sounding, min(seconds)
    span = max(pitches) - min(pitches)
    leap_ratio = sum(v >= 7 for v in leaps) / len(leaps) if leaps else 0.0
    split_ratio = sum(e["durationTick"] * 2 < music["ticksPerJeonggan"] for e in notes) / len(notes)
    marked_ratio = sum(bool(e["sigimsae"]) for e in notes) / len(notes)
    points = {
        "attackRate": int(rate > 1.5) + int(rate > 3),
        "shortNotes": int(shortest < .5) + int(shortest < .25),
        "pitchMovement": max(int(span > 12) + int(span > 24), int(leap_ratio >= .10) + int(leap_ratio >= .25)),
        "subdivision": int(split_ratio >= .10) + int(split_ratio >= .30),
        "ornamentDensity": int(marked_ratio >= .10) + int(marked_ratio >= .30),
    }
    total = sum(points.values())
    level = 1 if total <= 2 else 2 if total <= 5 else 3
    measurements = {"noteCount": len(notes), "soundingSeconds": sounding, "attacksPerSoundingSecond": rate,
                    "shortestNoteSeconds": shortest, "pitchRangeSemitones": span, "largeLeapRatio": leap_ratio,
                    "fineSubdivisionRatio": split_ratio, "annotatedNoteRatio": marked_ratio, "totalPoints": total}
    return {"level": level, "label": LABELS[level], "policyVersion": POLICY_VERSION,
            "measurements": measurements, "axisPoints": points,
            "reason": f"임시 {LABELS[level]}({total}/10점): 초당 {rate:.2f}음, 최단 {shortest:.2f}초, 음역 {span}반음, 분박 {split_ratio:.0%}, 기호 {marked_ratio:.0%}"}


def rate_bundle(bundle):
    """Read canonical bytes from a previously validated 339 bundle, checking identity/hash."""
    bundle = Path(bundle).resolve()
    manifest_bytes = (bundle / "manifest.json").read_bytes()
    manifest = json.loads(manifest_bytes)
    if manifest.get("schema") != "eolssu-draft-model-audio-batch/v1":
        raise ValueError("Expected validated model-audio batch v1")
    cases = manifest["cases"]
    if not cases or len(cases) != manifest["counts"]["cases"]:
        raise ValueError("Batch case count mismatch")
    rows, seen = [], set()
    for case in cases:
        identity = (case["caseId"], case["sourceId"])
        if any(identity[i] in {old[i] for old in seen} for i in (0, 1)):
            raise ValueError("Duplicate caseId/sourceId")
        seen.add(identity)
        files = [f for f in case["files"] if f["role"] == "canonical_common_score"]
        if len(files) != 1:
            raise ValueError(f"{identity[0]}: expected one canonical score")
        record = files[0]
        path = (bundle / record["path"]).resolve()
        if not path.is_relative_to(bundle) or Path(record["path"]).is_absolute():
            raise ValueError("Canonical score path must stay within bundle")
        raw = path.read_bytes()
        digest = hashlib.sha256(raw).hexdigest()
        if digest != record["sha256"] or len(raw) != record["bytes"]:
            raise ValueError(f"{identity[0]}: canonical SHA-256/size mismatch")
        source = json.loads(raw)
        if source["score"]["sourceId"] != identity[1] or source["music"]["bpmUnit"] != case["bpmUnit"]:
            raise ValueError(f"{identity[0]}: canonical identity mismatch")
        evidence = classify_difficulty(source, case["defaultBpm"])
        rows.append({"caseId": identity[0], "sourceId": identity[1], "inputSha256": digest,
                     "bpm": case["defaultBpm"], "difficulty": evidence["level"], "evidence": evidence})
    return {"schema": "model-audio-difficulty-batch/v1", "policyVersion": POLICY_VERSION,
            "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(), "count": len(rows),
            "histogram": dict(sorted(Counter(str(r["difficulty"]) for r in rows).items())), "cases": rows}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)
    if args.out.resolve().is_relative_to(args.bundle.resolve()):
        raise ValueError("Output must be outside the read-only bundle")
    result = rate_bundle(args.bundle)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")
    print(json.dumps({"count": result["count"], "histogram": result["histogram"], "policyVersion": POLICY_VERSION}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
