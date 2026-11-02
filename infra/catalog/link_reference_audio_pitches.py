"""Verify explicit nominal-pitch mappings and emit an atomic, non-overwriting MySQL update."""
import argparse
import json
from pathlib import Path
import sys

from import_reference_audio import _sql_value
from reference_audio_mapping import IDENTITY, exact_fields, pitch_target, load_mapping_input


def load_mappings(path, manifest):
    basis, entries = load_mapping_input(path, manifest, '기본음계')
    mappings = []
    for asset, row in entries:
        exact_fields(row, ('labelId', 'yul', 'octave'))
        yul, octave = pitch_target({key: row[key] for key in ('yul', 'octave')})
        mappings.append({**{key: asset[key] for key in IDENTITY}, 'yul': yul, 'octave': octave, 'basis': basis})
    return sorted(mappings, key=lambda row: row['assetId'])


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
        mappings = load_mappings(args.mapping, args.manifest)
        with args.output.open("x", encoding="utf-8", newline="\n") as stream:
            stream.write(render_sql(mappings))
        print(json.dumps({"mappings": len(mappings), "output": str(args.output)}, ensure_ascii=False))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"Pitch mapping preparation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
