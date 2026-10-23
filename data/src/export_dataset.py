"""Export explicit note intervals and unchanged performance/score bundles."""

import argparse
from copy import deepcopy
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import tempfile
import wave

import numpy as np


ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


def load_json(path):
    def unique_keys(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key: {key}")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8-sig"), object_pairs_hook=unique_keys)


def relative_path(value):
    """Portable relative paths; reject traversal and Windows aliases on every OS."""
    if not isinstance(value, str) or not value:
        raise ValueError("Expected a nonempty relative path")
    parts = value.split("/")
    reserved = {"CON", "PRN", "AUX", "NUL", *(f"COM{i}" for i in range(1, 10)), *(f"LPT{i}" for i in range(1, 10))}
    for part in parts:
        if (part in ("", ".", "..") or part.endswith((" ", ".")) or
                re.search(r'[<>:"\\|?*\x00-\x1f]', part) or part.split(".")[0].upper() in reserved):
            raise ValueError(f"Unsafe relative path: {value}")
    return Path(*PurePosixPath(value).parts)


def inside(root, relative):
    path = (root / relative_path(relative)).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError(f"Path escapes root: {relative}")
    return path


def index_rows(rows, name):
    if not isinstance(rows, list):
        raise ValueError(f"Expected a {name} list")
    result = {}
    for row in rows:
        key = row.get("id") if isinstance(row, dict) else None
        if not isinstance(key, str) or not key.strip() or key in result:
            raise ValueError(f"Missing or duplicate {name} ID: {key}")
        result[key] = row
    return result


def nonempty_text(value, name):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Expected nonempty {name}")
    return value


def name_component(value):
    nonempty_text(value, "path component")
    if "/" in value:
        raise ValueError(f"Expected a single path component: {value}")
    relative_path(value)
    return value


def expand_labels(document):
    """Expand schema-2 tables before filtering, preserving source-row numbering."""
    allowed = {"schemaVersion", "datasetId", "labelStatus", "eligibleForNoteScoring",
               "sourceHashes", "columns", "groups"}
    if set(document) - allowed:
        raise ValueError("Unknown labels document field")
    columns = document.get("columns")
    required = {"id", "startSeconds", "endSeconds", "register", "yulmyeong", "labelF0Hz"}
    if (not isinstance(columns, list) or any(not isinstance(c, str) for c in columns) or
            len(columns) != len(required) or set(columns) != required):
        raise ValueError("Label columns must name each supported column exactly once")
    nonempty_text(document.get("labelStatus"), "labelStatus")
    if type(document.get("eligibleForNoteScoring")) is not bool:
        raise ValueError("eligibleForNoteScoring must be boolean")
    groups = document.get("groups")
    if not isinstance(groups, list):
        raise ValueError("Expected label groups")
    expanded = []
    for group in groups:
        group_fields = {"sourceId", "category", "technique", "method", "filenamePrefix",
                        "rows", "evidence", "gestureDefaults"}
        if not isinstance(group, dict) or set(group) - group_fields:
            raise ValueError("Invalid label group fields")
        for field in ("sourceId", "category", "technique", "method"):
            nonempty_text(group.get(field), field)
        name_component(group["category"])
        name_component(group["technique"])
        if group.get("filenamePrefix") not in ("none", "rowNumber3"):
            raise ValueError("filenamePrefix must be none or rowNumber3")
        rows, evidence = group.get("rows"), group.get("evidence", {})
        if not isinstance(rows, list) or not isinstance(evidence, dict):
            raise ValueError("Expected rows list and optional evidence object")
        gestures = group.get("gestureDefaults", {})
        if (not isinstance(gestures, dict) or
                set(gestures) - {"repeatedRises", "labelMeaning", "interpretation"}):
            raise ValueError("Invalid gesture defaults")
        group_ids = set()
        for number, row in enumerate(rows, 1):
            if not isinstance(row, list) or len(row) != len(columns):
                raise ValueError("Label row length must match columns")
            label = dict(zip(columns, row))
            key = nonempty_text(label["id"], "label ID")
            group_ids.add(key)
            for field in ("register", "yulmyeong"):
                name_component(label[field])
            start, end, pitch = label["startSeconds"], label["endSeconds"], label.pop("labelF0Hz")
            if (any(type(n) not in (int, float) or not math.isfinite(n) for n in (start, end, pitch)) or
                    not 0 <= start < end or pitch <= 0):
                raise ValueError(f"Invalid interval or pitch: {key}")
            details = deepcopy(evidence.get(key, {}))
            if (not isinstance(details, dict) or set(details) - {
                    "sourceRegionIds", "boundaryReview", "repeatAssessment", "sequenceDecision",
                    "alternatives", "method", "gesture", "labelStatus", "eligibleForNoteScoring"}):
                raise ValueError(f"Invalid evidence fields: {key}")
            status = details.pop("labelStatus", document["labelStatus"])
            scoring = details.pop("eligibleForNoteScoring", document["eligibleForNoteScoring"])
            nonempty_text(status, "labelStatus")
            if type(scoring) is not bool:
                raise ValueError("eligibleForNoteScoring must be boolean")
            method = nonempty_text(details.pop("method", group["method"]), "method")
            display = f"{label['register']} {label['yulmyeong']}"
            gesture = details.pop("gesture", None)
            if gestures and gesture is None:
                raise ValueError(f"Missing gesture anchors: {key}")
            if gesture is not None:
                if not isinstance(gesture, dict) or set(gesture) - {"lowerAnchor", "destination", "review"}:
                    raise ValueError(f"Invalid gesture: {key}")
                lower, destination = gesture.get("lowerAnchor"), gesture.get("destination")
                if (not isinstance(lower, dict) or not isinstance(destination, dict) or
                        set(destination) - {"windowsSeconds", "alternatives"}):
                    raise ValueError(f"Invalid gesture anchors: {key}")
                for field in ("register", "yulmyeong"):
                    name_component(lower.get(field))
                if (type(lower.get("f0Hz")) not in (int, float) or
                        not math.isfinite(lower["f0Hz"]) or lower["f0Hz"] <= 0):
                    raise ValueError(f"Invalid lower-anchor frequency: {key}")
                destination.update(register=label["register"], yulmyeong=label["yulmyeong"], f0Hz=pitch)
                gesture = {**gestures, **gesture}
                label["gesture"] = gesture
                display = f"{lower['register']} {lower['yulmyeong']}→{display}"
            prefix = f"{number:03d}_" if group["filenamePrefix"] == "rowNumber3" else ""
            stem = display.replace(" ", "_").replace("→", "-")
            label.update(sourceId=group["sourceId"], category=group["category"], technique=group["technique"],
                         displayLabel=display, file=f"{group['category']}/{group['technique']}/{prefix}{stem}.wav",
                         labelStatus=status, eligibleForNoteScoring=scoring,
                         evidence={"labelF0Hz": pitch, "method": method, **details})
            expanded.append(label)
        if set(evidence) - group_ids:
            raise ValueError("Evidence refers to an unknown label ID")
    return index_rows(expanded, "label")


def read_wave(path, expected_hash=None):
    """Decode native-rate PCM16 or IEEE float32 WAV without normalization."""
    raw = path.read_bytes()
    if expected_hash is not None and hashlib.sha256(raw).hexdigest() != expected_hash.lower():
        raise ValueError("Source changed before decoding")
    if len(raw) < 12 or raw[:4] != b"RIFF" or raw[8:12] != b"WAVE":
        raise ValueError("Expected a RIFF/WAVE file")
    if struct.unpack_from("<I", raw, 4)[0] + 8 != len(raw):
        raise ValueError("RIFF size mismatch")
    pos, fmt, payload = 12, None, None
    while pos + 8 <= len(raw):
        name, size = struct.unpack_from("<4sI", raw, pos)
        chunk = raw[pos + 8:pos + 8 + size]
        if len(chunk) != size:
            raise ValueError("Truncated WAVE chunk")
        if name == b"fmt ":
            if len(chunk) < 16 or fmt is not None:
                raise ValueError("Invalid or repeated fmt chunk")
            fmt = struct.unpack_from("<HHIIHH", chunk)
        elif name == b"data":
            if payload is not None:
                raise ValueError("Repeated data chunk")
            payload = chunk
        pos += 8 + size + size % 2
    if fmt is None or payload is None:
        raise ValueError("Missing fmt/data chunk")
    code, channels, rate, byte_rate, align, bits = fmt
    if channels < 1 or rate < 1 or align < 1 or align != channels * bits // 8 or byte_rate != rate * align:
        raise ValueError("Invalid WAVE format")
    if not payload or len(payload) % align:
        raise ValueError("Invalid channel alignment")
    if (code, bits) == (3, 32):
        samples = np.frombuffer(payload, dtype="<f4").astype(np.float64)
    elif (code, bits) == (1, 16):
        samples = np.frombuffer(payload, dtype="<i2").astype(np.float64) / 32768
    else:
        raise ValueError(f"Unsupported WAVE encoding: {code}, {bits}")
    samples = samples.reshape(-1, channels)
    if not np.isfinite(samples).all():
        raise ValueError("Nonfinite audio samples")
    return samples, rate


def write_clip(path, samples, rate):
    mono = samples.mean(axis=1)
    peak = float(np.max(np.abs(mono)))
    gain = min(1.0, .999 / peak) if peak else 1.0
    pcm = np.rint(mono * gain * 32767).astype("<i2")
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as stream:
        stream.setnchannels(1)
        stream.setsampwidth(2)
        stream.setframerate(rate)
        stream.writeframes(pcm.tobytes())
    return {"sampleRateHz": rate, "channels": 1, "frames": len(pcm), "format": "WAV_PCM16",
            "gain": gain, "sha256": digest(path), "bytes": path.stat().st_size,
            "processing": "channel mean; attenuation only above 0.999; PCM16; no resampling/pitch correction"}


def validate_manifests(dataset, root):
    documents = {name: load_json(dataset / f"{name}.json") for name in ("sources", "labels", "performances")}
    dataset_id = documents["sources"].get("datasetId")
    if not isinstance(dataset_id, str) or not dataset_id:
        raise ValueError("Missing datasetId")
    for name, document in documents.items():
        version = 2 if name == "labels" else 1
        if type(document.get("schemaVersion")) is not int or document["schemaVersion"] != version or document.get("datasetId") != dataset_id:
            raise ValueError(f"Schema or dataset mismatch: {name}")
    if documents["sources"].get("pathBase") != "repository_root":
        raise ValueError("Source paths must be relative to repository_root")
    sources = index_rows(documents["sources"]["sources"], "source")
    labels = expand_labels(documents["labels"])
    performances = index_rows(documents["performances"]["performances"], "performance")
    for source in sources.values():
        if source.get("kind") not in ("audio", "score") or not re.fullmatch(r"[0-9a-fA-F]{64}", source.get("sha256", "")):
            raise ValueError(f"Invalid source type/hash: {source['id']}")
        inside(root, source["path"])
        for field in ("title", "license", "sourceUrl", "attribution"):
            if not isinstance(source.get(field), str) or not source[field].strip():
                raise ValueError(f"Missing source {field}: {source['id']}")

    paths = {"catalog.json": ("reserved", None)}

    def output_path(value, owner):
        relative_path(value)
        key = value.casefold()
        if key in paths and paths[key] != owner:
            raise ValueError(f"Duplicate output path: {value}")
        if any(key.startswith(p + "/") or p.startswith(key + "/") for p in paths):
            raise ValueError(f"File/directory output collision: {value}")
        paths[key] = owner

    def source_reference(reference, document, kind):
        source_id = reference.get("sourceId")
        if source_id not in sources or sources[source_id]["kind"] != kind:
            raise ValueError(f"Unknown or wrong-kind source: {source_id}")
        expected = document.get("sourceHashes", {}).get(source_id, "")
        if not isinstance(expected, str) or expected.lower() != sources[source_id]["sha256"].lower():
            raise ValueError(f"Labels/performance belongs to another source version: {source_id}")
        return sources[source_id]

    for label in labels.values():
        source_reference(label, documents["labels"], "audio")
        for field in ("category", "technique", "register", "yulmyeong", "labelStatus", "displayLabel"):
            if not isinstance(label.get(field), str) or not label[field].strip():
                raise ValueError(f"Missing label {field}: {label['id']}")
        start, end = label.get("startSeconds"), label.get("endSeconds")
        if any(type(n) not in (int, float) or not math.isfinite(n) for n in (start, end)) or not 0 <= start < end:
            raise ValueError(f"Invalid interval: {label['id']}")
        if Path(label["file"]).suffix.lower() != ".wav":
            raise ValueError("Clips must use a .wav filename")
        output_path(label["file"], ("label", label["id"]))
    for performance in performances.values():
        audio = performance["audio"]
        source = source_reference(audio, documents["performances"], "audio")
        if Path(audio["file"]).suffix.lower() != Path(source["path"]).suffix.lower():
            raise ValueError("Unchanged audio copy must preserve the source file extension")
        output_path(audio["file"], ("source", source["id"]))
        for score in performance["scores"]:
            source = source_reference(score, documents["performances"], "score")
            if Path(score["file"]).suffix.lower() != Path(source["path"]).suffix.lower():
                raise ValueError("Unchanged score copy must preserve the source file extension")
            if score.get("notation") not in ("staff", "jeongganbo"):
                raise ValueError("Expected staff or jeongganbo notation")
            pages, maximum = score.get("pdfPages"), source.get("pdfPageCount")
            if (not isinstance(pages, list) or not pages or type(maximum) is not int or
                    any(type(page) is not int or not 1 <= page <= maximum for page in pages)):
                raise ValueError("Invalid PDF page reference")
            output_path(score["file"], ("source", source["id"]))
    return documents, sources, labels, performances


def export_dataset(dataset, output, *, root=ROOT, label_ids=None, performance_ids=None,
                   category=None, technique=None, register=None, yulmyeong=None):
    dataset, output, root = Path(dataset).resolve(), Path(output).resolve(), Path(root).resolve()
    if output.exists():
        raise ValueError(f"Output already exists; choose a new directory: {output}")
    documents, sources, labels, performances = validate_manifests(dataset, root)
    label_ids, performance_ids = set(label_ids or []), set(performance_ids or [])
    if label_ids - labels.keys() or performance_ids - performances.keys():
        raise ValueError("Unknown selected label/performance ID")
    filters = {"category": category, "technique": technique, "register": register, "yulmyeong": yulmyeong}
    has_filter = any(v is not None for v in filters.values())
    select_all = not label_ids and not performance_ids and not has_filter
    selected_labels = [label for key, label in labels.items()
                       if (select_all or key in label_ids or (has_filter and not label_ids))
                       and all(v is None or label[k] == v for k, v in filters.items())]
    selected_performances = [p for key, p in performances.items() if select_all or key in performance_ids]
    if not selected_labels and not selected_performances:
        raise ValueError("Selection matched no labels or performances")
    used_ids = {label["sourceId"] for label in selected_labels}
    copies = {}
    for performance in selected_performances:
        for reference in [performance["audio"], *performance["scores"]]:
            copies[reference["file"]] = reference["sourceId"]
            used_ids.add(reference["sourceId"])
    source_paths = {key: inside(root, sources[key]["path"]) for key in used_ids}
    for key, path in source_paths.items():
        if digest(path) != sources[key]["sha256"].lower():
            raise ValueError(f"Source SHA-256 mismatch: {key}")
        if path.is_relative_to(output):
            raise ValueError("Output would contain an input source")

    # Decode only selected clips. Keep at most one source recording in memory.
    bounds = {}
    for source_id in {label["sourceId"] for label in selected_labels}:
        samples, rate = read_wave(source_paths[source_id], sources[source_id]["sha256"])
        metadata = sources[source_id]
        if metadata.get("sampleRateHz", rate) != rate or metadata.get("channels", samples.shape[1]) != samples.shape[1]:
            raise ValueError(f"Source format metadata mismatch: {source_id}")
        for label in selected_labels:
            if label["sourceId"] != source_id:
                continue
            first, last = round(label["startSeconds"] * rate), round(label["endSeconds"] * rate)
            if not 0 <= first < last <= len(samples):
                raise ValueError(f"Clip outside source bounds: {label['id']}")
            bounds[label["id"]] = (first, last)
        del samples

    catalog = {"schemaVersion": 1, "datasetId": documents["sources"]["datasetId"],
               "instrument": documents["sources"].get("instrument"),
               "manifestHashes": {name: digest(dataset / f"{name}.json") for name in documents},
               "exporterSha256": digest(Path(__file__)),
               "sources": [source for key, source in sources.items() if key in used_ids],
               "labels": [], "performances": [], "assets": []}
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".dataset-export-", dir=output.parent) as temporary:
        stage = Path(temporary) / "result"
        stage.mkdir()
        measured = {}
        for source_id in {label["sourceId"] for label in selected_labels}:
            samples, rate = read_wave(source_paths[source_id], sources[source_id]["sha256"])
            for label in selected_labels:
                if label["sourceId"] == source_id:
                    first, last = bounds[label["id"]]
                    info = write_clip(stage / relative_path(label["file"]), samples[first:last], rate)
                    measured[label["id"]] = {**label, "sourceStartFrame": first,
                                              "sourceEndFrameExclusive": last, **info}
            del samples
        catalog["labels"] = [measured[label["id"]] for label in selected_labels]
        for filename, source_id in copies.items():
            path = stage / relative_path(filename)
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source_paths[source_id], path)
            actual_hash = digest(path)
            if actual_hash != sources[source_id]["sha256"].lower():
                raise ValueError("Source changed while copying")
            catalog["assets"].append({"sourceId": source_id, "file": filename, "sha256": actual_hash,
                                      "bytes": path.stat().st_size, "processing": "unchanged_copy"})
        catalog["performances"] = selected_performances
        (stage / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
        if output.exists():
            raise ValueError("Output already exists; refusing to replace it")
        stage.rename(output)
    return catalog


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True, help="Directory containing sources/labels/performances.json")
    parser.add_argument("--output-dir", type=Path, required=True, help="New directory; existing directories are never overwritten")
    parser.add_argument("--root", type=Path, default=ROOT, help="Base directory for sources.path (default: repository root)")
    parser.add_argument("--label", dest="label_ids", action="append", help="Export this label ID; repeat to select several")
    parser.add_argument("--performance", dest="performance_ids", action="append", help="Copy this performance bundle; repeat to select several")
    for name in ("category", "technique", "register", "yulmyeong"):
        parser.add_argument(f"--{name}", help="Exact label filter; filters combine with AND")
    args = vars(parser.parse_args())
    args["output"] = args.pop("output_dir")
    try:
        result = export_dataset(**args)
    except (ValueError, OSError, KeyError, TypeError, struct.error) as error:
        parser.exit(1, f"Export failed: {error}\n")
    print(f"Exported {len(result['labels'])} labeled clips and {len(result['performances'])} performance bundles to {args['output']}")


if __name__ == "__main__":
    main()
