"""Verify explicit nominal-pitch mappings and emit an atomic, non-overwriting MySQL update."""
import argparse
import json
from pathlib import Path
import sys

from import_reference_audio import _invalid_constant, _sql_value, load_assets

IDENTITY = {"assetId": "asset_id", "datasetId": "dataset_id", "labelId": "label_id",
            "sha256": "sha256", "category": "category", "register": "register_name", "yulmyeong": "yulmyeong"}
YUL = set("황대태협고중유임이남무응")


def load_mappings(path, assets):
    document = json.loads(Path(path).read_text(encoding="utf-8"), parse_constant=_invalid_constant)
    if not isinstance(document, dict) or type(document.get("schemaVersion")) is not int or document["schemaVersion"] != 1:
        raise ValueError("Expected mapping schemaVersion 1")
    basis = document.get("basis")
    if not isinstance(basis, str) or not basis.strip() or len(basis.encode("utf-8")) > 60000 or "\x00" in basis:
        raise ValueError("Mapping basis/source is required")
    rows = document.get("mappings")
    if not isinstance(rows, list) or not rows:
        raise ValueError("Expected non-empty explicit mappings")
    indexed = {asset["assetId"]: asset for asset in assets}
    seen, mappings = set(), []
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("assetId"), str):
            raise ValueError("Invalid mapping row")
        asset = indexed.get(row["assetId"])
        if row["assetId"] in seen:
            raise ValueError("Duplicate asset mapping")
        if asset is None or any(row.get(key) != asset[key] for key in IDENTITY):
            raise ValueError("Mapping identity/hash/classification differs from verified manifest")
        if row["category"] != "기본음계":
            raise ValueError("Only basic tones may link to a nominal pitch")
        if not isinstance(row.get("yul"), str) or row["yul"] not in YUL or type(row.get("octave")) is not int or not -2 <= row["octave"] <= 2:
            raise ValueError("Invalid yul/octave target")
        seen.add(row["assetId"])
        mappings.append({**{key: row[key] for key in IDENTITY}, "yul": row["yul"], "octave": row["octave"], "basis": basis})
    return sorted(mappings, key=lambda row: row["assetId"])


def render_sql(mappings):
    if not mappings:
        raise ValueError("No mappings to apply")
    fields = [*IDENTITY, "yul", "octave", "basis"]
    columns = [*IDENTITY.values(), "yul", "octave", "basis"]
    values = ",\n".join("(" + ", ".join(_sql_value(row[field]) for field in fields) + ")" for row in mappings)
    equal = " AND ".join(f"BINARY a.{column} = BINARY s.{column}" for column in IDENTITY.values())
    return f"""-- Nominal labels only: never promotes review/scoring/publication status.
-- Run after catalog import and V13, in a dedicated mysql connection without --force.
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE reference_audio_pitch_stage (
    asset_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    dataset_id VARCHAR(80), label_id VARCHAR(160), sha256 CHAR(64), category VARCHAR(32),
    register_name VARCHAR(32), yulmyeong VARCHAR(32), yul VARCHAR(10), octave SMALLINT, basis TEXT
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO reference_audio_pitch_stage ({', '.join(columns)}) VALUES
{values};
-- Lock before conflict inspection so concurrent mapping/import changes cannot pass a stale check.
SELECT a.asset_id FROM reference_audio_assets a JOIN reference_audio_pitch_stage s ON a.asset_id=s.asset_id
ORDER BY a.asset_id FOR UPDATE;
SELECT p.id FROM pitches p JOIN reference_audio_pitch_stage s ON BINARY p.yul=BINARY s.yul AND p.octave=s.octave
ORDER BY p.id FOR SHARE;
CREATE TEMPORARY TABLE reference_audio_pitch_guard (conflict_count BIGINT NOT NULL CHECK (conflict_count=0));
INSERT INTO reference_audio_pitch_guard
SELECT COUNT(*) FROM reference_audio_pitch_stage s
LEFT JOIN reference_audio_assets a ON a.asset_id=s.asset_id
LEFT JOIN pitches p ON BINARY p.yul=BINARY s.yul AND p.octave=s.octave
WHERE a.asset_id IS NULL OR p.id IS NULL OR NOT ({equal}) OR BINARY a.category <> BINARY {_sql_value('기본음계')}
    OR (a.pitch_id IS NOT NULL AND (a.pitch_id<>p.id OR NOT (BINARY a.pitch_mapping_basis <=> BINARY s.basis)));
UPDATE reference_audio_assets a
JOIN reference_audio_pitch_stage s ON a.asset_id=s.asset_id
JOIN pitches p ON BINARY p.yul=BINARY s.yul AND p.octave=s.octave
SET a.pitch_id=p.id, a.pitch_mapping_basis=s.basis
WHERE a.pitch_id IS NULL AND EXISTS (SELECT 1 FROM reference_audio_pitch_guard WHERE conflict_count=0);
DROP TEMPORARY TABLE reference_audio_pitch_guard;
DROP TEMPORARY TABLE reference_audio_pitch_stage;
COMMIT;
"""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="New SQL file; never replaces an existing file")
    args = parser.parse_args(argv)
    try:
        mappings = load_mappings(args.mapping, load_assets(args.manifest))
        with args.output.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(render_sql(mappings))
        print(json.dumps({"mappings": len(mappings), "output": str(args.output)}, ensure_ascii=False))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"Pitch mapping preparation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
