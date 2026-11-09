"""Render local CAT listening examples for the S15P31E102-603 fingering rows."""

import argparse
import hashlib
from pathlib import Path
import shutil

from .__main__ import dump, run
from .audio import resolve_encoder
from .midi import read_midi
from .preview import MODE
from .sampler import CREDIT


# Instrument, filename suffix, display yulmyeong, guide Hwang, target MIDI.
NOTES = (
    ("danso", "cheong-hyeop", "청협", 63, 78),
    ("danso", "cheong-go", "청고", 63, 79),
    ("danso", "cheong-nam", "청남", 63, 84),
    ("danso", "jungcheong-hyeop", "중청협", 63, 90),
    ("danso", "jungcheong-jung", "중청중", 63, 92),
    ("sogeum", "jungcheong-tae", "중청태", 63, 89),
    ("sanjo-daegeum", "jungcheong-tae", "중청태", 65, 91),
)
HOLD_SECONDS = 3
# The top Danso CAT zone played 51.8 cents sharp in decoded basic audio. Keep
# the intended MIDI note and compensate within its common-v1 tuning interval.
CAT_TUNING_CENTS = {("danso", 92): -49}


def hz(midi):
    return 440 * 2 ** ((midi - 69) / 12)


def make_score(source_id, instrument, label, hwang_hz, yoseong):
    title = f"{label} {'요성' if yoseong else '기본음'} 운지 예시"
    event_id = "c0-j0-r0-u0"
    cells = [
        {"id": f"c0-j{index}", "index": index,
         "units": [{"row": 0, "col": 0, "symbol": symbol, "eventId": event_id}]}
        for index, symbol in enumerate((label, "-", "-"))
    ]
    return {
        "schemaVersion": 1,
        "score": {"title": title, "instrument": instrument.upper().replace("-", "_"),
                  "part": 0, "sourceId": source_id, "scoreVersion": 1},
        "music": {"hwangHz": hwang_hz, "jangdan": "교육용 고정 박", "bpmUnit": "QUARTER",
                  "ticksPerBeat": 1, "ticksPerJeonggan": 1},
        "columns": [{"index": 0, "cells": cells}],
        "events": [{"id": event_id, "kind": "NOTE", "yul": label[-1],
                    "octave": 2 if label.startswith("중청") else 1, "startTick": 0,
                    "durationTick": HOLD_SECONDS, "cellId": "c0-j0", "breathAfter": False,
                    "sigimsae": ["요성표"] if yoseong else []}],
        # Required legacy slot. The source is authored directly as common v1, without a JGB.
        "provenance": {"sourceId": source_id, "jgbSha256": "0" * 64,
                       "review": "inferred", "converter": "authored-fingering-reference@1"},
    }


def render_examples(assets, out):
    encoder = resolve_encoder()
    inputs = out / "inputs"
    inputs.mkdir(parents=True, exist_ok=True)
    clips = out / "m4a"
    clips.mkdir(exist_ok=True)
    entries = []
    for instrument, suffix, label, hwang_midi, target in NOTES:
        tuning = CAT_TUNING_CENTS.get((instrument, target), 0)
        for variant, yoseong in (("basic", False), ("yoseong", True)):
            label_id = f"cat-fingering-{instrument}-{suffix}-{variant}"
            asset_id = hashlib.sha256(f"{instrument}\n{label_id}".encode()).hexdigest()
            source_path = inputs / f"{label_id}.score.json"
            dump(source_path, make_score(label_id, instrument, label, hz(hwang_midi) * 2 ** (tuning / 1200), yoseong))
            render_dir = out / "renders" / label_id
            result = run(source_path, assets, render_dir, [instrument], encoder=encoder,
                         common_bpm=60, notation_policy=MODE if yoseong else None,
                         add_automatic_vibrato=False, audio_credit=CREDIT)
            midi = read_midi(render_dir / f"{instrument}.mid")
            if [note["pitch"] for note in midi["notes"]] != [target]:
                raise ValueError(f"{label_id}: expected a single MIDI note {target}")
            playing_bends = [cents for time, cents in midi["bends"] if time < midi["notes"][0]["end"]]
            if any(abs(cents - tuning) > 1 for cents in playing_bends) != yoseong:
                raise ValueError(f"{label_id}: unexpected MIDI pitch bend")
            audio = result["results"][0]["audio"]
            m4a = clips / f"{label_id}.m4a"
            shutil.copyfile(render_dir / f"{instrument}.m4a", m4a)
            checksum = hashlib.sha256(m4a.read_bytes()).hexdigest()
            if checksum != audio["m4a_sha256"]:
                raise ValueError(f"{label_id}: copy hash differs from encoded audio")
            entries.append({
                "assetId": asset_id, "datasetId": instrument, "labelId": label_id,
                "category": "기본음계", "technique": "요성 CAT 합성" if yoseong else "기본음 CAT 합성",
                "register": "역취", "yulmyeong": "태" if instrument == "sogeum" else label,
                "displayLabel": label, "file": m4a.relative_to(out).as_posix(),
                "objectKey": f"reference-audio/v1/{instrument}/{asset_id}/{checksum}.m4a",
                "contentType": "audio/mp4", "sha256": checksum, "bytes": m4a.stat().st_size,
                "durationMs": round(audio["container_seconds"] * 1000),
                "sampleRateHz": audio["sample_rate"], "channels": audio["channels"],
                "review": "inferred", "eligibleForNoteScoring": False,
                "metadata": {"sourceType": "synthetic", "sourceName": "CAT Seoul 국악 가상악기 v1.3",
                             "sourceUrl": "https://www.catseoul.com/gugak-library/",
                             "attribution": CREDIT, "sourceLabel": label, "hwangMidi": hwang_midi,
                             "targetMidi": target, "targetHz": hz(target),
                             "catZoneTuningCorrectionCents": tuning,
                             "notationPolicy": MODE if yoseong else None,
                             "processing": "작성한 단음 악보 → MIDI → CAT 샘플 합성 → AAC-LC"},
            })
    dump(out / "catalog-candidate.json", {"schemaVersion": 1,
         "publicationStatus": "LOCAL_ONLY_RIGHTS_UNCONFIRMED", "assets": entries})
    (out / "CAT-attribution.txt").write_text(CREDIT + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True, help="Local output directory outside version control")
    args = parser.parse_args()
    render_examples(args.assets, args.out)


if __name__ == "__main__":
    main()
