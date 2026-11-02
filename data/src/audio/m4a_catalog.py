"""Convert exported label WAVs into verified AAC-LC assets and a DB import manifest.

This command never edits source catalogs, uploads objects, or publishes DB rows.
Python's standard library and explicitly installed FFmpeg/ffprobe are sufficient.
"""
import argparse
from array import array
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import tempfile
import wave

DATASETS = {"danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum"}
AAC_BIT_RATE = 192000
AAC_FRAME_SAMPLES = 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    with Path(path).open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def command(args, timeout=180):
    try:
        result = subprocess.run([str(arg) for arg in args], capture_output=True,
                                timeout=timeout, check=False,
                                creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ValueError(f"Audio tool execution failed: {Path(args[0]).name}") from exc
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()[-2000:]
        raise ValueError(f"Audio tool failed ({Path(args[0]).name}): {detail}")
    return result.stdout


def resolve_tools(ffmpeg=None, ffprobe=None):
    tools = {}
    versions = {}
    for name, supplied in (("ffmpeg", ffmpeg), ("ffprobe", ffprobe)):
        requested = supplied or os.environ.get(f"{name.upper()}_BINARY") or name
        executable = shutil.which(str(requested))
        require(executable is not None, f"{name} not found; supply --{name} or {name.upper()}_BINARY")
        version = command([executable, "-version"], timeout=15).decode("utf-8", errors="replace")
        require(version.startswith(f"{name} version "), f"Unexpected {name} executable")
        tools[name] = executable
        versions[name] = version.splitlines()[0]
    return tools, versions


def input_path(root, relative):
    require(isinstance(relative, str) and relative and "\\" not in relative and ":" not in relative,
            "WAV file must be a relative POSIX path")
    parts = PurePosixPath(relative)
    require(not parts.is_absolute() and all(p not in (".", "..") for p in relative.split("/")),
            "WAV path traversal is not allowed")
    path = (root / relative).resolve()
    require(path.is_relative_to(root) and path.is_file(), "WAV file is missing or escapes catalog directory")
    require(path.suffix.lower() == ".wav", "Only exported WAV input is supported")
    return path


def read_catalogs(catalogs):
    require(bool(catalogs), "At least one catalog is required")
    rows, catalog_receipts, ids = [], [], set()
    for given in catalogs:
        path = Path(given).resolve(strict=True)
        payload = path.read_bytes()
        catalog = json.loads(payload.decode("utf-8"))
        require(catalog.get("schemaVersion") == 2, "Expected exported catalog schemaVersion=2")
        dataset = catalog.get("datasetId")
        require(dataset in DATASETS, "Unsupported datasetId")
        labels, source_rows = catalog.get("labels"), catalog.get("sources")
        require(isinstance(labels, list) and labels, "Catalog labels must be a nonempty array")
        require(isinstance(source_rows, list), "Catalog sources must be an array")
        sources = {}
        for source in source_rows:
            require(isinstance(source, dict) and isinstance(source.get("id"), str), "Invalid source entry")
            require(source["id"] not in sources, "Duplicate source ID")
            for field in ("attribution", "license", "sourceUrl"):
                require(isinstance(source.get(field), str) and source[field].strip(), f"Missing source {field}")
            sources[source["id"]] = source
        catalog_hash = hashlib.sha256(payload).hexdigest()
        catalog_receipts.append({"datasetId": dataset, "sha256": catalog_hash, "labelCount": len(labels)})
        for label in labels:
            require(isinstance(label, dict), "Invalid label entry")
            for field in ("id", "sourceId", "category", "technique", "register", "yulmyeong", "displayLabel", "review"):
                require(isinstance(label.get(field), str) and label[field].strip(), f"Missing label {field}")
            require(type(label.get("eligibleForNoteScoring")) is bool, "Missing scoring eligibility")
            require(label["sourceId"] in sources, "Label source is missing")
            identity = (dataset, label["id"])
            require(identity not in ids, "Duplicate datasetId + labelId")
            ids.add(identity)
            source_file = input_path(path.parent, label.get("file"))
            expected_hash = label.get("sha256", "")
            require(isinstance(expected_hash, str) and re.fullmatch("[0-9a-fA-F]{64}", expected_hash),
                    "Invalid source WAV hash")
            require(digest(source_file) == expected_hash.lower(), f"Source WAV hash mismatch: {label['id']}")
            require(source_file.stat().st_size == label.get("bytes"), "Source WAV byte count mismatch")
            with wave.open(str(source_file), "rb") as wav:
                rate, channels, frames = wav.getframerate(), wav.getnchannels(), wav.getnframes()
                require(wav.getsampwidth() == 2 and wav.getcomptype() == "NONE", "Expected PCM16 WAV")
                require(channels in (1, 2) and frames > 0 and rate > 0, "Invalid WAV dimensions")
                # Reading the entire declared payload detects truncated RIFF data before encoding.
                require(len(wav.readframes(frames)) == frames * channels * 2, "Truncated WAV payload")
            require((rate, channels, frames) == (label.get("sampleRateHz"), label.get("channels"), label.get("frames")),
                    "Source WAV header disagrees with catalog")
            require(round(frames * 1000 / rate) > 0, "WAV is shorter than the catalog millisecond precision")
            rows.append({"dataset": dataset, "label": label, "source": sources[label["sourceId"]],
                         "path": source_file, "catalogPath": path, "catalogHash": catalog_hash,
                         "instrument": catalog.get("instrument"), "rate": rate, "channels": channels,
                         "frames": frames, "hash": expected_hash.lower()})
    return sorted(rows, key=lambda row: (row["dataset"], row["label"]["id"])), sorted(
        catalog_receipts, key=lambda item: (item["datasetId"], item["sha256"]))


def validate_m4a(path, row, tools):
    info = json.loads(command([tools["ffprobe"], "-v", "error", "-show_streams", "-of", "json", path]))
    streams = info.get("streams", [])
    require(len(streams) == 1, "M4A must contain one audio stream")
    stream = streams[0]
    require(stream.get("codec_type") == "audio" and stream.get("codec_name") == "aac"
            and stream.get("profile") == "LC", "Expected AAC-LC audio")
    require((int(stream.get("sample_rate", 0)), stream.get("channels")) == (row["rate"], row["channels"]),
            "M4A changed sample rate or channel count")
    seconds = float(stream.get("duration", 0))
    require(math.isfinite(seconds) and abs(seconds - row["frames"] / row["rate"]) <= 2 / row["rate"],
            "M4A display duration differs from source WAV")
    require(abs(float(stream.get("start_time", 0))) <= 1 / row["rate"], "M4A start time is not zero")
    decoded = command([tools["ffmpeg"], "-hide_banner", "-loglevel", "error", "-nostdin", "-xerror",
                       "-i", path, "-map", "0:a:0", "-f", "f32le", "-c:a", "pcm_f32le", "pipe:1"])
    require(len(decoded) % (4 * row["channels"]) == 0, "M4A decode produced a partial PCM frame")
    frames = len(decoded) // (4 * row["channels"])
    require(row["frames"] <= frames < row["frames"] + AAC_FRAME_SAMPLES,
            "Decoded length is outside one AAC frame of trailing padding")
    samples = array("f")
    samples.frombytes(decoded)
    if sys.byteorder != "little":
        samples.byteswap()
    require(all(math.isfinite(value) for value in samples), "Non-finite decoded PCM")
    return {"decodedFrames": frames, "endPaddingFrames": frames - row["frames"],
            "containerSeconds": seconds, "peak": max(abs(value) for value in samples),
            "encodedBitRate": int(stream.get("bit_rate", 0))}


def convert_catalogs(catalogs, output, *, ffmpeg=None, ffprobe=None):
    output = Path(output).absolute()
    require(not output.exists() and not output.is_symlink(), "Output already exists; refusing to replace it")
    rows, receipts = read_catalogs(catalogs)
    for row in rows:
        require(not output.resolve().is_relative_to(row["catalogPath"].parent),
                "Output must be outside source catalog directories")
    tools, versions = resolve_tools(ffmpeg, ffprobe)
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = {"schemaVersion": 1, "catalogs": receipts,
                "encoder": {**versions, "codec": "aac", "profile": "LC", "targetBitRate": AAC_BIT_RATE},
                "assets": []}
    with tempfile.TemporaryDirectory(prefix=".reference-audio-", dir=output.parent) as temporary:
        stage = Path(temporary) / "result"
        stage.mkdir()
        for row in rows:
            label, dataset = row["label"], row["dataset"]
            asset_id = hashlib.sha256(f"{dataset}\n{label['id']}".encode("utf-8")).hexdigest()
            relative = f"audio/{dataset}/{asset_id}.m4a"
            destination = stage / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            command([tools["ffmpeg"], "-hide_banner", "-loglevel", "error", "-nostdin", "-xerror", "-y",
                     "-i", row["path"], "-map", "0:a:0", "-map_metadata", "-1", "-c:a", "aac",
                     "-profile:a", "aac_low", "-b:a", str(AAC_BIT_RATE), "-threads", "1",
                     "-movflags", "+faststart", "-movie_timescale", str(row["rate"]), "-f", "ipod", destination])
            verification = validate_m4a(destination, row, tools)
            require(digest(row["path"]) == row["hash"], "Source WAV changed during conversion")
            output_hash = digest(destination)
            manifest["assets"].append({
                "assetId": asset_id, "datasetId": dataset, "labelId": label["id"],
                **{key: label[key] for key in ("category", "technique", "register", "yulmyeong", "displayLabel",
                                             "review", "eligibleForNoteScoring")},
                "file": relative, "objectKey": f"reference-audio/v1/{dataset}/{asset_id}/{output_hash}.m4a",
                "contentType": "audio/mp4", "sha256": output_hash, "bytes": destination.stat().st_size,
                "durationMs": round(row["frames"] * 1000 / row["rate"]), "sampleRateHz": row["rate"],
                "channels": row["channels"], "metadata": {
                    "label": label, "source": row["source"], "instrument": row["instrument"],
                    "catalogSha256": row["catalogHash"],
                    "wav": {"sha256": row["hash"], "bytes": label["bytes"], "frames": row["frames"],
                            "sampleRateHz": row["rate"], "channels": row["channels"]},
                    "encoding": {**manifest["encoder"], **verification,
                                 "processing": "AAC-LC compression only; no resampling, pitch correction, normalization or trimming"},
                },
            })
        for row in rows:
            require(digest(row["catalogPath"]) == row["catalogHash"], "Source catalog changed during conversion")
        (stage / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
                                              encoding="utf-8", newline="\n")
        require(not output.exists(), "Output appeared during conversion; refusing to replace it")
        stage.rename(output)
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", action="append", type=Path, required=True,
                        help="Exported schemaVersion=2 catalog.json; repeat for several instruments")
    parser.add_argument("--output-dir", type=Path, required=True, help="New output directory; never overwritten")
    parser.add_argument("--ffmpeg", help="Executable path, otherwise FFMPEG_BINARY or PATH")
    parser.add_argument("--ffprobe", help="Executable path, otherwise FFPROBE_BINARY or PATH")
    args = parser.parse_args()
    try:
        result = convert_catalogs(args.catalog, args.output_dir, ffmpeg=args.ffmpeg, ffprobe=args.ffprobe)
    except (ValueError, OSError, KeyError, TypeError, wave.Error) as error:
        parser.exit(1, f"M4A conversion failed: {error}\n")
    print(json.dumps({"assets": len(result["assets"]), "bytes": sum(a["bytes"] for a in result["assets"]),
                      "manifest": str(args.output_dir / "manifest.json")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
