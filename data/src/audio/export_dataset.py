"""Export explicit note intervals and unchanged performance/score bundles."""

import argparse
from copy import deepcopy
import hashlib
import io
import json
import math
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import tempfile
import wave

import numpy as np


ROOT = Path(__file__).resolve().parents[3]


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


def managed_root(root, folder):
    boundary = root.resolve() / "data" / folder
    if boundary.resolve() != boundary:
        raise ValueError(f"Managed directory cannot redirect its boundary: data/{folder}")
    return boundary


def managed_path(root, relative, folder):
    boundary = managed_root(root, folder)
    path = inside(root, relative)
    if path == boundary or not path.is_relative_to(boundary):
        raise ValueError(f"Path escapes data/{folder} boundary: {relative}")
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


def validate_analysis_windows(label, rate=None):
    """Stable-pitch windows use absolute source seconds, like the clip itself."""
    gesture = label.get("gesture")
    if gesture is None:
        return
    windows = []
    for anchor in ("lowerAnchor", "destination"):
        values = gesture[anchor].get("windowsSeconds")
        if not isinstance(values, list) or not values:
            raise ValueError(f"Expected nonempty {anchor} windows: {label['id']}")
        previous_end = None
        for pair in values:
            windows.append(pair)
            if (not isinstance(pair, list) or len(pair) != 2 or
                    any(type(n) not in (int, float) or not math.isfinite(n) for n in pair) or
                    not label["startSeconds"] <= pair[0] < pair[1] <= label["endSeconds"]):
                raise ValueError(f"Invalid {anchor} window: {label['id']}")
            if previous_end is not None and pair[0] < previous_end:
                raise ValueError(f"Unordered or overlapping {anchor} windows: {label['id']}")
            previous_end = pair[1]
    review = gesture.get("analysisReview", {})
    if not isinstance(review, dict):
        raise ValueError(f"Invalid window review: {label['id']}")
    if "rejectedWindow" in review:
        pair = review["rejectedWindow"]
        if (not isinstance(pair, list) or len(pair) != 2 or
                any(type(n) not in (int, float) or not math.isfinite(n) for n in pair) or
                not label["startSeconds"] <= pair[0] < pair[1] <= label["endSeconds"]):
            raise ValueError(f"Invalid rejected window: {label['id']}")
        windows.append(pair)
    # Repetitions can interleave anchors; do not require all lower windows first.
    ordered = sorted(windows)
    if any(right[0] < left[1] for left, right in zip(ordered, ordered[1:])):
        raise ValueError(f"Overlapping analysis windows: {label['id']}")
    if rate is not None:
        first, last = round(label["startSeconds"] * rate), round(label["endSeconds"] * rate)
        if any(not first <= round(start * rate) < round(end * rate) <= last for start, end in windows):
            raise ValueError(f"Empty or out-of-clip window frames: {label['id']}")


def expand_labels(document):
    """Expand schema-3 tables before filtering, preserving source-row numbering."""
    allowed = {"schemaVersion", "datasetId", "review", "eligibleForNoteScoring",
               "sourceHashes", "columns", "groups"}
    if set(document) - allowed:
        raise ValueError("Unknown labels document field")
    columns = document.get("columns")
    required = {"id", "startSeconds", "endSeconds", "register", "yulmyeong", "labelF0Hz"}
    if (not isinstance(columns, list) or any(not isinstance(c, str) for c in columns) or
            len(columns) != len(required) or set(columns) != required):
        raise ValueError("Label columns must name each supported column exactly once")
    if document.get("review") not in ("inferred", "reviewed", "rejected"):
        raise ValueError("Invalid annotation review state")
    if type(document.get("eligibleForNoteScoring")) is not bool:
        raise ValueError("eligibleForNoteScoring must be boolean")
    groups = document.get("groups")
    if not isinstance(groups, list):
        raise ValueError("Expected label groups")
    expanded = []
    for group in groups:
        group_fields = {"sourceId", "category", "technique", "method", "analysisBasis", "filenamePrefix",
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
                    "alternatives", "method", "analysisBasis", "gesture", "review", "eligibleForNoteScoring"}):
                raise ValueError(f"Invalid evidence fields: {key}")
            status = details.pop("review", document["review"])
            scoring = details.pop("eligibleForNoteScoring", document["eligibleForNoteScoring"])
            if status not in ("inferred", "reviewed", "rejected"):
                raise ValueError(f"Invalid annotation review state: {key}")
            if type(scoring) is not bool:
                raise ValueError("eligibleForNoteScoring must be boolean")
            if scoring and status != "reviewed":
                raise ValueError(f"Only reviewed annotations may be scoring references: {key}")
            method = nonempty_text(details.pop("method", group["method"]), "method")
            if method != "manual" and not re.fullmatch(r"tool:[^\s@]+@[^\s@]+", method):
                raise ValueError(f"Expected manual or tool:name@version method: {key}")
            basis = details.get("analysisBasis", group.get("analysisBasis"))
            if basis is not None:
                details["analysisBasis"] = nonempty_text(basis, "analysisBasis")
            display = f"{label['register']} {label['yulmyeong']}"
            gesture = details.pop("gesture", None)
            if gestures and gesture is None:
                raise ValueError(f"Missing gesture anchors: {key}")
            if gesture is not None:
                if not isinstance(gesture, dict) or set(gesture) - {"lowerAnchor", "destination", "analysisReview"}:
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
                         method=method, review=status, eligibleForNoteScoring=scoring,
                         evidence={"labelF0Hz": pitch, **details})
            expanded.append(label)
            validate_analysis_windows(label)
        if set(evidence) - group_ids:
            raise ValueError("Evidence refers to an unknown label ID")
    return namespace_labels(expanded)


def namespace_labels(expanded):
    # Partition a shared technique by source before selection. Single-source
    # datasets keep their existing filenames; no new input fields are needed.
    technique_sources = {}
    for label in expanded:
        directory = (label["category"].casefold(), label["technique"].casefold())
        technique_sources.setdefault(directory, set()).add(label["sourceId"])
    for source_ids in technique_sources.values():
        if len(source_ids) > 1:
            if len({source_id.casefold() for source_id in source_ids}) != len(source_ids):
                raise ValueError("Case-colliding source directories")
            for source_id in source_ids:
                name_component(source_id)
    for label in expanded:
        directory = (label["category"].casefold(), label["technique"].casefold())
        if len(technique_sources[directory]) > 1:
            parent, filename = label["file"].rsplit("/", 1)
            label["file"] = f"{parent}/{label['sourceId']}/{filename}"
    return index_rows(expanded, "label")


def read_wave(path, expected_hash=None):
    """Decode native-rate PCM16 or IEEE float32 WAV without normalization."""
    raw = path.read_bytes()
    if expected_hash is not None and hashlib.sha256(raw).hexdigest() != expected_hash.lower():
        raise ValueError("Source changed before decoding")
    return decode_wave(raw)


def decode_wave(raw):
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


def read_audio(path, expected_hash):
    """Detect content, verify the encoded bytes, then decode at native rate."""
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected_hash.lower():
        raise ValueError("Source changed before decoding")
    if raw[:4] == b"RIFF":
        samples, rate = decode_wave(raw)
        decoder = {"format": "WAV", "decoder": "builtin_pcm16_float32"}
    else:
        try:
            import soundfile as sf
        except (ImportError, OSError) as error:
            raise ValueError("MP3 requires soundfile==0.13.1 with MP3-enabled libsndfile; see data/README.md") from error
        try:
            with sf.SoundFile(io.BytesIO(raw)) as stream:
                if stream.format != "MP3" or stream.subtype != "MPEG_LAYER_III":
                    raise ValueError("Supported audio inputs are PCM16/float32 WAV and MP3 Layer III")
                rate = stream.samplerate
                samples = stream.read(dtype="float64", always_2d=True)
        except sf.SoundFileError as error:
            raise ValueError(f"Cannot decode MP3: {error}") from error
        if not len(samples) or not np.isfinite(samples).all():
            raise ValueError("Empty or nonfinite MP3 audio samples")
        decoder = {"format": "MP3", "decoder": "libsndfile",
                   "soundfileVersion": sf.__version__, "libsndfileVersion": sf.__libsndfile_version__}
    decoder.update(sampleRateHz=rate, channels=samples.shape[1], frames=len(samples),
                   timeline="decoded_pcm_frames; start=0; no additional delay/padding adjustment")
    return samples, rate, decoder


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
    if dataset.parent.name != "catalog" or dataset.parent.parent.name != "data":
        raise ValueError("Dataset must be a directory under data/catalog")
    metadata_root = dataset.parent.parent.parent
    documents = {name: load_json(dataset / f"{name}.json") for name in ("sources", "performances")}
    manifest_hashes = {f"catalog/{dataset.name}/{name}.json": digest(dataset / f"{name}.json") for name in documents}
    dataset_id = documents["sources"].get("datasetId")
    if not isinstance(dataset_id, str) or not dataset_id:
        raise ValueError("Missing datasetId")
    name_component(dataset_id)
    for name, document in documents.items():
        if type(document.get("schemaVersion")) is not int or document["schemaVersion"] != 2 or document.get("datasetId") != dataset_id:
            raise ValueError(f"Schema or dataset mismatch: {name}")
    if documents["sources"].get("pathBase") != "repository_root":
        raise ValueError("Source paths must be relative to repository_root")
    sources = index_rows(documents["sources"]["sources"], "source")
    if len({source_id.casefold() for source_id in sources}) != len(sources):
        raise ValueError("Case-colliding source IDs")
    performances = index_rows(documents["performances"]["performances"], "performance")
    comparisons = index_rows(documents["performances"].get("comparisonScores", []), "comparison score")
    annotations, expanded = {}, []
    for source in sources.values():
        if source.get("kind") not in ("audio", "score") or not re.fullmatch(r"[0-9a-fA-F]{64}", source.get("sha256", "")):
            raise ValueError(f"Invalid source type/hash: {source['id']}")
        raw_path = source.get("rawPath", "")
        if not isinstance(raw_path, str) or not raw_path.startswith("data/raw/") or "path" in source:
            raise ValueError(f"Expected rawPath under data/raw: {source['id']}")
        managed_path(root, raw_path, "raw")
        for field in ("title", "license", "sourceUrl", "attribution"):
            if not isinstance(source.get(field), str) or not source[field].strip():
                raise ValueError(f"Missing source {field}: {source['id']}")
        if "annotationPath" not in source:
            continue
        source_id = name_component(source["id"])
        annotation_path = source["annotationPath"]
        parts = relative_path(annotation_path).parts
        if (source["kind"] != "audio" or len(parts) != 6 or
                parts[:4] != ("data", "annotations", "audio", dataset_id) or parts[-1] != "labels.json"):
            raise ValueError(f"Annotation path must match its dataset and audio source ID: {source_id}")
        folder = parts[4]
        if folder != source_id and not (folder.startswith(source_id + "-") and len(folder) > len(source_id) + 1):
            raise ValueError(f"Annotation path must match its audio source ID: {source['id']}")
        path = managed_path(metadata_root, annotation_path, "annotations")
        document = load_json(path)
        if (type(document.get("schemaVersion")) is not int or document["schemaVersion"] != 3 or
                document.get("datasetId") != dataset_id or
                document.get("sourceHashes") != {source["id"]: source["sha256"]}):
            raise ValueError(f"Annotation source version or dataset mismatch: {source['id']}")
        source_labels = expand_labels(document)
        if any(group["sourceId"] != source["id"] for group in document["groups"]):
            raise ValueError(f"Annotation refers to another audio source: {source['id']}")
        annotations[source["id"]] = document
        expanded.extend(source_labels.values())
        manifest_hashes[annotation_path.removeprefix("data/")] = digest(path)
    # The merged view is needed for IDs and filenames shared across input files.
    labels = namespace_labels(expanded)
    documents["annotations"] = annotations
    documents["manifestHashes"] = manifest_hashes

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

    intervals = {}
    for label in labels.values():
        source = source_reference(label, annotations[label["sourceId"]], "audio")
        for field in ("category", "technique", "register", "yulmyeong", "review", "displayLabel"):
            if not isinstance(label.get(field), str) or not label[field].strip():
                raise ValueError(f"Missing label {field}: {label['id']}")
        start, end = label.get("startSeconds"), label.get("endSeconds")
        if any(type(n) not in (int, float) or not math.isfinite(n) for n in (start, end)) or not 0 <= start < end:
            raise ValueError(f"Invalid interval: {label['id']}")
        interval = (source["sha256"].lower(), start, end)
        if interval in intervals:
            raise ValueError(f"Duplicate source interval: {intervals[interval]} / {label['id']}")
        intervals[interval] = label["id"]
        if Path(label["file"]).suffix.lower() != ".wav":
            raise ValueError("Clips must use a .wav filename")
        output_path(label["file"], ("label", label["id"]))
    def validate_score(score, *, comparison=False):
        source = source_reference(score, documents["performances"], "score")
        if Path(score["file"]).suffix.lower() != Path(source["rawPath"]).suffix.lower():
            raise ValueError("Unchanged score copy must preserve the source file extension")
        if score.get("notation") not in ("staff", "jeongganbo"):
            raise ValueError("Expected staff or jeongganbo notation")
        if not comparison and score.get("samePerformanceEditionVerified") is not True:
            raise ValueError("Unverified score editions belong in comparisonScores")
        pages, maximum = score.get("pdfPages"), source.get("pdfPageCount")
        if (not isinstance(pages, list) or not pages or type(maximum) is not int or
                any(type(page) is not int or not 1 <= page <= maximum for page in pages)):
            raise ValueError("Invalid PDF page reference")
        output_path(score["file"], ("source", source["id"]))

    for performance in performances.values():
        audio = performance["audio"]
        source = source_reference(audio, documents["performances"], "audio")
        if Path(audio["file"]).suffix.lower() != Path(source["rawPath"]).suffix.lower():
            raise ValueError("Unchanged audio copy must preserve the source file extension")
        output_path(audio["file"], ("source", source["id"]))
        for score in performance["scores"]:
            validate_score(score)
    for score in comparisons.values():
        if score.get("forPerformanceId") not in performances:
            raise ValueError("Comparison score refers to an unknown performance")
        validate_score(score, comparison=True)
    return documents, sources, labels, performances


def export_dataset(dataset, output, *, root=ROOT, label_ids=None, performance_ids=None,
                   category=None, technique=None, register=None, yulmyeong=None):
    dataset, output, root = Path(dataset).resolve(), Path(output).resolve(), Path(root).resolve()
    processed_root = managed_root(root, "processed")
    if output == processed_root or not output.is_relative_to(processed_root):
        raise ValueError("Output must be a new directory under data/processed")
    if output.exists():
        raise ValueError(f"Output already exists; choose a new directory: {output}")
    documents, sources, labels, performances = validate_manifests(dataset, root)
    label_ids, performance_ids = set(label_ids or []), set(performance_ids or [])
    if label_ids - labels.keys() or performance_ids - performances.keys():
        raise ValueError("Unknown selected label/performance ID")
    if any(labels[key]["review"] == "rejected" for key in label_ids):
        raise ValueError("Rejected annotations cannot be exported")
    filters = {"category": category, "technique": technique, "register": register, "yulmyeong": yulmyeong}
    has_filter = any(v is not None for v in filters.values())
    select_all = not label_ids and not performance_ids and not has_filter
    selected_labels = [label for key, label in labels.items()
                       if label["review"] != "rejected" and (select_all or key in label_ids or (has_filter and not label_ids))
                       and all(v is None or label[k] == v for k, v in filters.items())]
    selected_performances = [p for key, p in performances.items() if select_all or key in performance_ids]
    selected_comparisons = [s for s in documents["performances"].get("comparisonScores", [])
                            if s["forPerformanceId"] in {p["id"] for p in selected_performances}]
    if not selected_labels and not selected_performances:
        raise ValueError("Selection matched no labels or performances")
    used_ids = {label["sourceId"] for label in selected_labels}
    copies = {}
    for performance in selected_performances:
        for reference in [performance["audio"], *performance["scores"]]:
            copies[reference["file"]] = reference["sourceId"]
            used_ids.add(reference["sourceId"])
    for reference in selected_comparisons:
        copies[reference["file"]] = reference["sourceId"]
        used_ids.add(reference["sourceId"])
    source_paths = {key: managed_path(root, sources[key]["rawPath"], "raw") for key in used_ids}
    for key, path in source_paths.items():
        if digest(path) != sources[key]["sha256"].lower():
            raise ValueError(f"Source SHA-256 mismatch: {key}")
        if path.is_relative_to(output):
            raise ValueError("Output would contain an input source")

    # Decode only selected clips. Keep at most one source recording in memory.
    bounds, decoders = {}, {}
    for source_id in {label["sourceId"] for label in selected_labels}:
        samples, rate, decoder = read_audio(source_paths[source_id], sources[source_id]["sha256"])
        decoders[source_id] = decoder
        metadata = sources[source_id]
        if metadata.get("sampleRateHz", rate) != rate or metadata.get("channels", samples.shape[1]) != samples.shape[1]:
            raise ValueError(f"Source format metadata mismatch: {source_id}")
        frame_intervals = {}
        for label in labels.values():
            if sources[label["sourceId"]]["sha256"].lower() != metadata["sha256"].lower():
                continue
            first, last = round(label["startSeconds"] * rate), round(label["endSeconds"] * rate)
            if not 0 <= first < last <= len(samples):
                raise ValueError(f"Clip outside source bounds: {label['id']}")
            if (first, last) in frame_intervals:
                raise ValueError(f"Duplicate source frame interval: {frame_intervals[first, last]} / {label['id']}")
            frame_intervals[first, last] = label["id"]
            validate_analysis_windows(label, rate)
            bounds[label["id"]] = (first, last)
        del samples

    catalog = {"schemaVersion": 2, "datasetId": documents["sources"]["datasetId"],
               "instrument": documents["sources"].get("instrument"),
               "manifestHashes": documents["manifestHashes"],
               "exporterSha256": digest(Path(__file__)),
               "audioDecoders": decoders,
               "sources": [source for key, source in sources.items() if key in used_ids],
               "labels": [], "performances": [], "comparisonScores": selected_comparisons, "assets": []}
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".dataset-export-", dir=output.parent) as temporary:
        stage = Path(temporary) / "result"
        stage.mkdir()
        measured = {}
        for source_id in {label["sourceId"] for label in selected_labels}:
            samples, rate, decoder = read_audio(source_paths[source_id], sources[source_id]["sha256"])
            if decoder != decoders[source_id]:
                raise ValueError(f"Source decoder changed during export: {source_id}")
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
    parser.add_argument("--dataset", type=Path, required=True, help="data/catalog/<dataset-id> containing sources/performances.json")
    parser.add_argument("--output-dir", type=Path, required=True, help="New directory; existing directories are never overwritten")
    parser.add_argument("--root", type=Path, default=ROOT, help="Base for rawPath and data/processed; use / for server /data/raw. Metadata stays in the checkout.")
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
