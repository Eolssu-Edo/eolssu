"""Verify explicit sigimsae pitch roles and emit an atomic, non-overwriting MySQL import."""
import argparse
import json
import math
from pathlib import Path
import sys

from import_reference_audio import _sql_value
from reference_audio_mapping import IDENTITY, exact_fields, pitch_target, load_mapping_input


def evidence_nodes(label):
    """Enumerate only recorded roles; analysis windows are not note-event timing."""
    nodes = [('LABEL', 0, '/label', label)]
    evidence = label.get('evidence', {})
    if not isinstance(evidence, dict):
        raise ValueError('Invalid source label evidence')
    sequence = evidence.get('sequenceDecision', {})
    if not isinstance(sequence, dict):
        raise ValueError('Invalid source sequence evidence')
    if 'orderedAnchors' in sequence:
        anchors, selected = sequence['orderedAnchors'], sequence.get('labelAnchorIndex')
        if not isinstance(anchors, list) or not anchors or type(selected) is not int or not 0 <= selected < len(anchors):
            raise ValueError('Invalid ordered anchors/labelAnchorIndex')
        chosen = anchors[selected]
        if not isinstance(chosen, dict) or any(chosen.get(k) != label.get(k) for k in ('register', 'yulmyeong')) or chosen.get('f0Hz') != evidence.get('labelF0Hz'):
            raise ValueError('Selected anchor differs from row label')
        nodes.extend(('SEQUENCE', i, f'/label/evidence/sequenceDecision/orderedAnchors/{i}', anchor)
                     for i, anchor in enumerate(anchors))
    if 'gesture' in label:
        gesture = label['gesture']
        if not isinstance(gesture, dict) or not all(isinstance(gesture.get(k), dict) for k in ('lowerAnchor', 'destination')):
            raise ValueError('Expected explicit lowerAnchor/destination roles')
        if any(gesture['destination'].get(k) != label.get(k) for k in ('register', 'yulmyeong')):
            raise ValueError('Gesture destination differs from row label')
        nodes.extend((role, 0, '/label/gesture/' + key, gesture[key])
                     for role, key in [('LOWER_ANCHOR', 'lowerAnchor'), ('DESTINATION', 'destination')])
    for role, _, _, node in nodes:
        if not isinstance(node, dict) or any(not isinstance(node.get(k), str) or not node[k] for k in ('register', 'yulmyeong')):
            raise ValueError('Missing role pitch evidence')
        if role == 'LABEL':
            continue
        f0, windows = node.get('f0Hz'), node.get('windowsSeconds')
        if type(f0) not in (int, float) or not math.isfinite(f0) or f0 <= 0 or not isinstance(windows, list) or not windows:
            raise ValueError('Invalid observed anchor evidence')
        for window in windows:
            if not isinstance(window, list) or len(window) != 2 or any(type(t) not in (int, float) or not math.isfinite(t) for t in window):
                raise ValueError('Invalid source analysis window')
            if not label['startSeconds'] <= window[0] < window[1] <= label['endSeconds']:
                raise ValueError('Analysis window lies outside source clip')
    return nodes


def load_mappings(path, manifest):
    basis, entries = load_mapping_input(path, manifest, '시김새')
    mappings = []
    for asset, row in entries:
        nodes = evidence_nodes(asset['metadata']['label'])
        fields = {'labelId', 'label'}
        sequence_count = sum(role == 'SEQUENCE' for role, _, _, _ in nodes)
        if sequence_count:
            fields.add('sequence')
        if any(role == 'LOWER_ANCHOR' for role, _, _, _ in nodes):
            fields.update(('lowerAnchor', 'destination'))
        exact_fields(row, fields)
        if sequence_count and (not isinstance(row['sequence'], list) or len(row['sequence']) != sequence_count):
            raise ValueError('Complete source sequence is required')
        targets_by_label, relations = {}, []
        for role, index, evidence_path, node in nodes:
            target_input = row['sequence'][index] if role == 'SEQUENCE' else row[
                {'LABEL': 'label', 'LOWER_ANCHOR': 'lowerAnchor', 'DESTINATION': 'destination'}[role]]
            yul, octave = pitch_target(target_input)
            source_pitch = (node['register'], node['yulmyeong'])
            if targets_by_label.setdefault(source_pitch, (yul, octave)) != (yul, octave):
                raise ValueError('The same source pitch has conflicting role targets')
            relations.append(dict(role=role, anchorIndex=index, evidencePath=evidence_path,
                register=node['register'], yulmyeong=node['yulmyeong'], yul=yul, octave=octave))
        mappings.append({**{k: asset[k] for k in IDENTITY}, 'metadata': asset['metadata'], 'relations': relations, 'basis': basis})
    return sorted(mappings, key=lambda row: row['assetId'])


def render_sql(mappings):
    if not mappings:
        raise ValueError('No role mappings to apply')
    asset_fields = [*IDENTITY, 'metadata']
    asset_columns = [*IDENTITY.values(), 'metadata_json']
    asset_values = ',\n'.join('(' + ', '.join(_sql_value(m[k]) for k in asset_fields) + ')' for m in mappings)
    relation_values = ',\n'.join('(' + ', '.join(_sql_value(v) for v in
        [m['assetId'], r['role'], r['anchorIndex'], r['yul'], r['octave'], r['evidencePath'], m['basis']]) + ')'
        for m in mappings for r in m['relations'])
    identity_equal = ' AND '.join(f'BINARY a.{c}=BINARY s.{c}' for c in IDENTITY.values())
    return f"""-- V13 required. Dedicated mysql connection; never use --force.
-- Exact complete sets are idempotent; any missing target/evidence/conflict rejects the whole batch.
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE reference_audio_role_assets (
    asset_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    dataset_id VARCHAR(80),label_id VARCHAR(160),sha256 CHAR(64),category VARCHAR(32),
    register_name VARCHAR(32),yulmyeong VARCHAR(32),metadata_json JSON NOT NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO reference_audio_role_assets ({','.join(asset_columns)}) VALUES
{asset_values};
CREATE TEMPORARY TABLE reference_audio_role_stage (
    asset_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin,
    role VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin,anchor_index INT,
    yul VARCHAR(10),octave SMALLINT,evidence_path VARCHAR(255),basis TEXT,
    PRIMARY KEY(asset_id,role,anchor_index)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO reference_audio_role_stage VALUES
{relation_values};
SELECT a.asset_id FROM reference_audio_assets a JOIN reference_audio_role_assets s ON a.asset_id=s.asset_id
ORDER BY a.asset_id FOR UPDATE;
SELECT p.id FROM pitches p JOIN reference_audio_role_stage s ON BINARY p.yul=BINARY s.yul AND p.octave=s.octave
ORDER BY p.id FOR SHARE;
SELECT r.asset_id FROM reference_audio_pitch_relations r JOIN reference_audio_role_assets s ON r.asset_id=s.asset_id
ORDER BY r.asset_id,r.role,r.anchor_index FOR UPDATE;
CREATE TEMPORARY TABLE reference_audio_role_guard (conflict_count BIGINT NOT NULL CHECK(conflict_count=0));
INSERT INTO reference_audio_role_guard
SELECT COUNT(*) FROM reference_audio_role_assets s LEFT JOIN reference_audio_assets a ON a.asset_id=s.asset_id
    WHERE a.asset_id IS NULL OR NOT ({identity_equal}) OR BINARY a.category<>BINARY {_sql_value('시김새')}
        OR NOT (BINARY CAST(a.metadata_json AS CHAR CHARACTER SET utf8mb4) <=> BINARY CAST(s.metadata_json AS CHAR CHARACTER SET utf8mb4))
;
INSERT INTO reference_audio_role_guard
SELECT COUNT(*) FROM reference_audio_role_stage s LEFT JOIN pitches p ON BINARY p.yul=BINARY s.yul AND p.octave=s.octave
    WHERE p.id IS NULL
;
INSERT INTO reference_audio_role_guard
SELECT COUNT(*) FROM reference_audio_pitch_relations r JOIN reference_audio_role_assets a ON r.asset_id=a.asset_id
    LEFT JOIN reference_audio_role_stage s ON r.asset_id=s.asset_id AND r.role=s.role AND r.anchor_index=s.anchor_index
    LEFT JOIN pitches p ON BINARY p.yul=BINARY s.yul AND p.octave=s.octave
    WHERE s.asset_id IS NULL OR NOT (r.pitch_id <=> p.id) OR NOT (BINARY r.evidence_path <=> BINARY s.evidence_path)
        OR NOT (BINARY r.mapping_basis <=> BINARY s.basis)
;
INSERT INTO reference_audio_role_guard
SELECT COUNT(*) FROM reference_audio_role_assets a
    WHERE EXISTS(SELECT 1 FROM reference_audio_pitch_relations r WHERE r.asset_id=a.asset_id)
      AND (SELECT COUNT(*) FROM reference_audio_pitch_relations r WHERE r.asset_id=a.asset_id)
          <> (SELECT COUNT(*) FROM reference_audio_role_stage s WHERE s.asset_id=a.asset_id);

INSERT INTO reference_audio_pitch_relations(asset_id,role,anchor_index,pitch_id,evidence_path,mapping_basis)
SELECT s.asset_id,s.role,s.anchor_index,p.id,s.evidence_path,s.basis FROM reference_audio_role_stage s
JOIN pitches p ON BINARY p.yul=BINARY s.yul AND p.octave=s.octave
WHERE NOT EXISTS(SELECT 1 FROM reference_audio_pitch_relations r WHERE r.asset_id=s.asset_id AND r.role=s.role AND r.anchor_index=s.anchor_index)
AND (SELECT COUNT(*) FROM reference_audio_role_guard)=4;
DROP TEMPORARY TABLE reference_audio_role_guard;
DROP TEMPORARY TABLE reference_audio_role_stage;
DROP TEMPORARY TABLE reference_audio_role_assets;
COMMIT;
"""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--mapping', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True, help='New SQL file; never overwrites')
    args = parser.parse_args(argv)
    try:
        mappings = load_mappings(args.mapping, args.manifest)
        with args.output.open('x', encoding='utf-8', newline='\n') as stream:
            stream.write(render_sql(mappings))
        print(json.dumps({'assets':len(mappings),'relations':sum(len(m['relations']) for m in mappings),'output':str(args.output)}))
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f'Role mapping preparation failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
