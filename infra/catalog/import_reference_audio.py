"""Verify a converted M4A manifest and emit a non-overwriting MySQL DRAFT import."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys


DATASETS = {"danso", "sogeum", "jeongak-daegeum", "sanjo-daegeum"}
TEXT_FIELDS = {
    "assetId": 64, "datasetId": 80, "labelId": 160, "category": 32,
    "technique": 100, "register": 32, "yulmyeong": 32, "displayLabel": 255,
    "objectKey": 512, "contentType": 64, "sha256": 64, "review": 32,
}
NUMERIC_FIELDS = {"bytes": 2**63 - 1, "durationMs": 2**31 - 1, "sampleRateHz": 192000, "channels": 2}
COLUMNS = (
    ("asset_id", "assetId"), ("dataset_id", "datasetId"), ("label_id", "labelId"),
    ("category", "category"), ("technique", "technique"), ("register_name", "register"),
    ("yulmyeong", "yulmyeong"), ("display_label", "displayLabel"), ("object_key", "objectKey"),
    ("content_type", "contentType"), ("sha256", "sha256"), ("byte_size", "bytes"),
    ("duration_ms", "durationMs"), ("sample_rate_hz", "sampleRateHz"), ("channels", "channels"),
    ("review_status", "review"), ("eligible_for_note_scoring", "eligibleForNoteScoring"),
    ("metadata_json", "metadata"),
)


def _invalid_constant(value):
    raise ValueError(f"Non-finite JSON constant: {value}")


def _validate_provenance(asset):
    metadata = asset.get("metadata")
    if not isinstance(metadata, dict) or any(not isinstance(metadata.get(key), dict) for key in ("label", "source", "wav", "encoding")):
        raise ValueError("Missing original label/source/WAV/encoding provenance")
    label, source, wav, encoding = (metadata[key] for key in ("label", "source", "wav", "encoding"))
    if label.get("id") != asset["labelId"] or any(label.get(key) != asset[key] for key in (
        "category", "technique", "register", "yulmyeong", "displayLabel", "review", "eligibleForNoteScoring",
    )) or type(label.get("eligibleForNoteScoring")) is not bool:
        raise ValueError("Asset differs from original label identity/classification/review")
    if not source.get("id") or source["id"] != label.get("sourceId") or any(
        not isinstance(source.get(key), str) or not source[key].strip() for key in ("attribution", "license", "sourceUrl")
    ):
        raise ValueError("Missing source attribution/license/identity provenance")
    if not re.fullmatch(r"[0-9a-f]{64}", str(metadata.get("catalogSha256", ""))):
        raise ValueError("Invalid catalog provenance hash")
    if not re.fullmatch(r"[0-9a-f]{64}", str(wav.get("sha256", ""))) or wav["sha256"] != str(label.get("sha256", "")).lower():
        raise ValueError("Invalid WAV provenance hash")
    for key in ("bytes", "frames", "sampleRateHz", "channels"):
        if type(wav.get(key)) is not int or wav[key] <= 0 or wav[key] != label.get(key):
            raise ValueError("Invalid WAV provenance dimensions")
    if wav["sampleRateHz"] != asset["sampleRateHz"] or wav["channels"] != asset["channels"] or round(wav["frames"] * 1000 / wav["sampleRateHz"]) != asset["durationMs"]:
        raise ValueError("M4A dimensions differ from original WAV")
    decoded, padding = encoding.get("decodedFrames"), encoding.get("endPaddingFrames")
    if type(decoded) is not int or type(padding) is not int or decoded - wav["frames"] != padding or not 0 <= padding < 1024:
        raise ValueError("Invalid full decode provenance")


def load_assets(manifest):
    manifest = Path(manifest).resolve(strict=True)
    document = json.loads(manifest.read_text(encoding="utf-8"), parse_constant=_invalid_constant)
    if not isinstance(document, dict) or type(document.get("schemaVersion")) is not int or document["schemaVersion"] != 1:
        raise ValueError("Expected manifest schemaVersion 1")
    assets = document.get("assets")
    if not isinstance(assets, list) or not assets:
        raise ValueError("Manifest assets must be a non-empty array")
    identities = set()
    for asset in assets:
        if not isinstance(asset, dict):
            raise ValueError("Asset must be an object")
        for field, maximum in TEXT_FIELDS.items():
            value = asset.get(field)
            if not isinstance(value, str) or not value.strip() or len(value) > maximum or "\x00" in value:
                raise ValueError(f"Invalid {field}")
        for field, maximum in NUMERIC_FIELDS.items():
            value = asset.get(field)
            if type(value) is not int or not 0 < value <= maximum:
                raise ValueError(f"Invalid {field}")
        if asset["datasetId"] not in DATASETS or asset["category"] not in {"기본음계", "시김새"}:
            raise ValueError("Unsupported dataset/category")
        identity = (asset["datasetId"], asset["labelId"])
        if identity in identities:
            raise ValueError("duplicate dataset/label")
        identities.add(identity)
        expected_id = hashlib.sha256("\n".join(identity).encode()).hexdigest()
        if asset["assetId"] != expected_id:
            raise ValueError("assetId does not match dataset/label")
        if not re.fullmatch(r"[0-9a-f]{64}", asset["sha256"]):
            raise ValueError("Invalid sha256")
        expected_key = f"reference-audio/v1/{asset['datasetId']}/{expected_id}/{asset['sha256']}.m4a"
        if asset["objectKey"] != expected_key or asset["contentType"] != "audio/mp4":
            raise ValueError("Invalid objectKey/contentType")
        if asset["review"] not in {"inferred", "reviewed", "rejected"} or type(asset.get("eligibleForNoteScoring")) is not bool:
            raise ValueError("Invalid review/scoring status")
        if asset["eligibleForNoteScoring"] and asset["review"] != "reviewed":
            raise ValueError("Only reviewed labels can be eligible for scoring")
        _validate_provenance(asset)
        filename = asset.get("file")
        if not isinstance(filename, str) or not filename or "\\" in filename or ":" in filename:
            raise ValueError("Expected relative file path")
        relative = PurePosixPath(filename)
        if relative.is_absolute() or ".." in relative.parts or relative.suffix != ".m4a":
            raise ValueError("Expected relative file path ending in .m4a")
        path = (manifest.parent / filename).resolve(strict=True)
        if not path.is_relative_to(manifest.parent) or not path.is_file():
            raise ValueError("Expected relative file inside manifest directory")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if path.stat().st_size != asset["bytes"] or digest != asset["sha256"]:
            raise ValueError(f"Output file integrity mismatch: {asset['assetId']}")
    return sorted(assets, key=lambda asset: (asset["datasetId"], asset["labelId"]))


def _sql_value(value):
    if isinstance(value, dict):
        value = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)
    if isinstance(value, str):
        return f"CONVERT(X'{value.encode('utf-8').hex()}' USING utf8mb4)"
    if type(value) is bool:
        return "1" if value else "0"
    if type(value) is int:
        return str(value)
    raise ValueError("Unsupported SQL value")


def render_sql(assets):
    if not assets:
        raise ValueError("No assets to import")
    columns = ", ".join(column for column, _ in COLUMNS)
    values = ",\n".join("(" + ", ".join(_sql_value(asset[field]) for _, field in COLUMNS) + ")" for asset in assets)
    comparisons = []
    for column, field in COLUMNS:
        if field in TEXT_FIELDS:
            comparisons.append(f"(BINARY a.{column} <=> BINARY s.{column})")
        elif field == "metadata":
            comparisons.append(f"(BINARY CAST(a.{column} AS CHAR CHARACTER SET utf8mb4) <=> BINARY CAST(s.{column} AS CHAR CHARACTER SET utf8mb4))")
        else:
            comparisons.append(f"(a.{column} <=> s.{column})")
    equal = " AND\n    ".join(comparisons)
    selected = ", ".join(f"s.{column}" for column, _ in COLUMNS)
    return f"""-- Generated from verified M4A files. Use a dedicated mysql connection without --force.
-- Exact reruns preserve existing publication state; conflicting identities abort before inserts.
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE reference_audio_import_stage LIKE reference_audio_assets;
INSERT INTO reference_audio_import_stage ({columns}) VALUES
{values};
CREATE TEMPORARY TABLE reference_audio_import_guard (
    conflict_count BIGINT NOT NULL CHECK (conflict_count = 0)
);
INSERT INTO reference_audio_import_guard
SELECT COUNT(*) FROM reference_audio_import_stage s
JOIN reference_audio_assets a ON a.asset_id = s.asset_id
    OR (a.dataset_id = s.dataset_id AND a.label_id = s.label_id)
WHERE NOT ({equal});
INSERT INTO reference_audio_assets ({columns})
SELECT {selected} FROM reference_audio_import_stage s
WHERE NOT EXISTS (
    SELECT 1 FROM reference_audio_assets a WHERE a.asset_id = s.asset_id
        OR (a.dataset_id = s.dataset_id AND a.label_id = s.label_id)
)
AND EXISTS (SELECT 1 FROM reference_audio_import_guard WHERE conflict_count = 0);
DROP TEMPORARY TABLE reference_audio_import_guard;
DROP TEMPORARY TABLE reference_audio_import_stage;
COMMIT;
"""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="New SQL file; existing files are never replaced")
    args = parser.parse_args(argv)
    try:
        assets = load_assets(args.manifest)
        sql = render_sql(assets)
        with args.output.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(sql)
        print(json.dumps({"assets": len(assets), "status": "DRAFT", "output": str(args.output)}, ensure_ascii=False))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"Import preparation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
