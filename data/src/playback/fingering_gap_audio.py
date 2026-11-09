"""Fill selected fingering audio gaps with verified KOGL-1 derivatives."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import re

import soundfile as sf

from .__main__ import dump
from .audio import resolve_encoder, write_audio
from .fingering_derivatives import (
    LICENSE_PAGE, ORIGINAL_PAGE, PROCESSING, YOSEONG_PROCESSING,
    load_source, measure, shift_clip,
)
from .fingering_reference import hz
from .score import require
from .verify_common import decode_audio


DATASETS = {"danso", "sogeum", "sanjo-daegeum", "jeongak-daegeum"}
VARIANTS = {"basic": "기본음", "yoseong": "요성"}


def read_plan(path):
    plan = json.loads(path.read_text(encoding="utf-8"))
    require(plan["schemaVersion"] == 1, "Invalid gap plan version")
    keys, names = set(), set()
    for row in plan["targets"]:
        dataset, register, yul = (row[k] for k in ("dataset", "register", "yul"))
        suffix, source = row["suffix"], row["source"]
        require(dataset in DATASETS and register in {"저취", "평취", "역취"}
                and isinstance(yul, str) and bool(yul) and re.fullmatch(r"[a-z0-9-]+", suffix),
                "Invalid target identity")
        require(all(type(row[k]) is int and 0 <= row[k] <= 127 for k in ("midi", "hwangMidi")),
                "Invalid MIDI pitch")
        require(re.fullmatch(r"[a-zA-Z0-9._-]+", source["labelId"]) and
                all(re.fullmatch(r"[0-9a-f]{64}", source[k]) for k in ("assetId", "sha256")),
                "Invalid source identity")
        require(bool(row["variants"]) and len(set(row["variants"])) == len(row["variants"]) and
                set(row["variants"]) <= VARIANTS.keys(), "Invalid variants")
        require((dataset, register, yul) not in keys and (dataset, suffix) not in names,
                "Duplicate target")
        keys.add((dataset, register, yul))
        names.add((dataset, suffix))
    require(bool(keys), "No target notes")
    return plan["targets"]


def render_variant(row, kind, detail, detail_hash, pcm, rate, ratio, encoder, out):
    dataset, yul, register = (row[k] for k in ("dataset", "yul", "register"))
    original_asset, original_meta = detail["asset"], detail["metadata"]
    source = original_meta["source"]
    yoseong = kind == "yoseong"
    label_id = f"eolssu-622-{dataset}-{row['suffix']}-{kind}"
    processing = f"{PROCESSING}. {YOSEONG_PROCESSING}" if yoseong else PROCESSING
    credit = f"{source['attribution']}. 가공: {processing}. 원본: {ORIGINAL_PAGE}"
    wav = out / "wav" / f"{label_id}.wav"
    wav_meta = write_audio(shift_clip(pcm, rate, ratio, yoseong), wav)
    m4a = out / "m4a" / f"{label_id}.m4a"
    audio = write_audio(sf.read(wav)[0], m4a, encoder, credit=credit)
    decoded, _, _, _ = decode_audio(m4a, encoder["ffmpeg"], encoder["ffprobe"])
    target = hz(row["midi"])
    metrics = measure(decoded, rate, target)
    require(abs(metrics["medianCents"]) <= 12, f"{label_id}: center misses target pitch")
    amplitude = metrics["fourHzCents"]
    require(38 <= amplitude <= 62 if yoseong else amplitude <= 8, f"{label_id}: invalid 4 Hz amplitude")
    classification = {"category": "기본음계", "technique": f"{VARIANTS[kind]} · 음높이 가공",
                      "register": register, "yulmyeong": yul, "displayLabel": f"{register} {yul} (음높이 가공)",
                      "review": "inferred", "eligibleForNoteScoring": False}
    pcm_info = {key: wav_meta[key] for key in ("sha256", "bytes", "frames")} | {"sampleRateHz": rate, "channels": 1}
    asset_id = hashlib.sha256(f"{dataset}\n{label_id}".encode()).hexdigest()
    checksum = audio["sha256"]
    metadata = {
        "label": {"id": label_id, "sourceId": source["id"], **classification, **pcm_info, "processing": processing},
        "source": source, "wav": pcm_info, "catalogSha256": original_meta["catalogSha256"],
        "encoding": {"codec": audio["codec"], "profile": audio["profile"], "decodedFrames": audio["decoded_frames"],
                     "endPaddingFrames": audio["end_padding_frames"]},
        "origin": {"kind": "GENERATED", "method": "PITCH_SHIFTED_RECORDING",
                   "sourceAssetId": original_asset["assetId"], "syntheticYoseong": yoseong},
        "derivative": {"originalAssetId": original_asset["assetId"], "originalLabelId": original_asset["labelId"],
                       "originalM4aSha256": original_asset["sha256"], "originalDetailSha256": detail_hash,
                       "originalYulmyeong": original_asset["yulmyeong"], "originalRegister": original_asset["register"],
                       "originalF0Hz": original_meta["label"]["evidence"]["labelF0Hz"], "sourceLicenseUrl": LICENSE_PAGE,
                       "targetHwangMidi": row["hwangMidi"], "targetMidi": row["midi"], "targetHz": target,
                       "shiftSemitones": 12 * math.log2(ratio), "processing": processing,
                       "syntheticYoseong": yoseong, "verification": metrics},
    }
    return {"assetId": asset_id, "datasetId": dataset, "labelId": label_id, **classification,
            "file": m4a.relative_to(out).as_posix(), "objectKey": f"reference-audio/v1/{dataset}/{asset_id}/{checksum}.m4a",
            "contentType": "audio/mp4", "sha256": checksum, "bytes": audio["bytes"],
            "durationMs": round(audio["container_seconds"] * 1000), "sampleRateHz": rate, "channels": 1, "metadata": metadata}


def build(plan, source_dir, out):
    targets = read_plan(plan)
    encoder = resolve_encoder()
    require(not out.exists() and not out.is_symlink(), "Use a new output directory")
    (out / "m4a").mkdir(parents=True)
    (out / "wav").mkdir()
    assets = []
    for row in targets:
        source = row["source"]
        detail, pcm, rate, nominal, detail_hash = load_source(
            source_dir, source["labelId"], encoder, identity=(source["assetId"], source["sha256"]),
        )
        require(detail["asset"]["datasetId"] == row["dataset"], "Source instrument differs from target")
        target = hz(row["midi"])
        ratio = target / nominal
        center = measure(shift_clip(pcm, rate, ratio, False), rate, target)["medianCents"]
        ratio /= 2 ** (center / 1200)
        for kind in row["variants"]:
            assets.append(render_variant(row, kind, detail, detail_hash, pcm, rate, ratio, encoder, out))
    dump(out / "manifest.json", {"schemaVersion": 1, "assets": assets})
    dump(out / "verification.json", {a["labelId"]: a["metadata"]["derivative"]["verification"] for a in assets})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--sources", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    build(args.plan, args.sources, args.out)


if __name__ == "__main__":
    main()
