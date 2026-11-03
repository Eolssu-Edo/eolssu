"""Shared schema-2 boundary for explicit basic and sigimsae mappings."""
import json
import re
from pathlib import Path

from import_reference_audio import DATASETS, _invalid_constant, _unique_json_object, load_assets

IDENTITY = {"assetId": "asset_id", "datasetId": "dataset_id", "labelId": "label_id",
            "sha256": "sha256", "category": "category", "register": "register_name", "yulmyeong": "yulmyeong"}
YUL = set("황대태협고중유임이남무응")


def exact_fields(value, fields):
    if not isinstance(value, dict) or set(value) != set(fields):
        raise ValueError("Unexpected or missing mapping fields")


def pitch_target(value):
    exact_fields(value, ("yul", "octave"))
    if not isinstance(value['yul'], str) or value['yul'] not in YUL or type(value['octave']) is not int or not -2 <= value['octave'] <= 2:
        raise ValueError("Invalid yul/octave target")
    return value['yul'], value['octave']


def load_mapping_input(path, manifest, category):
    document = json.loads(Path(path).read_text(encoding='utf-8'), parse_constant=_invalid_constant,
                          object_pairs_hook=_unique_json_object)
    exact_fields(document, ('schemaVersion', 'datasetId', 'manifestSha256', 'basis', 'mappings'))
    if type(document['schemaVersion']) is not int or document['schemaVersion'] != 2:
        raise ValueError('Expected mapping schemaVersion 2')
    dataset = document['datasetId']
    if not isinstance(dataset, str) or dataset not in DATASETS:
        raise ValueError('Unsupported mapping dataset')
    basis, rows = document['basis'], document['mappings']
    if not isinstance(basis, str) or not basis.strip() or '\x00' in basis or len(basis.encode('utf-8')) > 60000:
        raise ValueError('Mapping basis/source is required')
    if not isinstance(rows, list) or not rows:
        raise ValueError('Expected non-empty explicit mappings')
    if not isinstance(document['manifestSha256'], str) or not re.fullmatch(r'[0-9a-f]{64}', document['manifestSha256']):
        raise ValueError('Expected pinned manifest SHA-256')
    # Hash and parse the same bytes, then retain the existing manifest/file/provenance verification.
    assets = load_assets(manifest, expected_sha256=document['manifestSha256'])
    indexed = {a['labelId']: a for a in assets if a['datasetId'] == dataset}
    seen, entries = set(), []
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get('labelId'), str):
            raise ValueError('Invalid mapping labelId')
        label_id = row['labelId']
        if label_id in seen:
            raise ValueError('Duplicate asset mapping')
        asset = indexed.get(label_id)
        if asset is None or asset['category'] != category:
            raise ValueError('Mapping asset is missing or category differs from verified manifest')
        seen.add(label_id)
        entries.append((asset, row))
    return basis, entries
