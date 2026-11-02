-- Nominal source-label relationships, independent of review/scoring and observed F0.
CREATE TABLE reference_audio_pitch_relations (
    asset_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    anchor_index INT NOT NULL,
    pitch_id BIGINT NOT NULL,
    evidence_path VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    mapping_basis TEXT NOT NULL,
    PRIMARY KEY (asset_id, role, anchor_index),
    CONSTRAINT fk_reference_audio_relation_asset FOREIGN KEY (asset_id) REFERENCES reference_audio_assets(asset_id) ON DELETE CASCADE,
    CONSTRAINT fk_reference_audio_relation_pitch FOREIGN KEY (pitch_id) REFERENCES pitches(id),
    CONSTRAINT chk_reference_audio_relation_role CHECK (role IN ('LABEL','SEQUENCE','LOWER_ANCHOR','DESTINATION')),
    CONSTRAINT chk_reference_audio_relation_index CHECK (anchor_index >= 0 AND (role='SEQUENCE' OR anchor_index=0)),
    CONSTRAINT chk_reference_audio_relation_evidence CHECK (
        (role='LABEL' AND evidence_path='/label') OR
        (role='SEQUENCE' AND evidence_path=CONCAT('/label/evidence/sequenceDecision/orderedAnchors/',anchor_index)) OR
        (role='LOWER_ANCHOR' AND evidence_path='/label/gesture/lowerAnchor') OR
        (role='DESTINATION' AND evidence_path='/label/gesture/destination')),
    CONSTRAINT chk_reference_audio_relation_basis CHECK (CHAR_LENGTH(TRIM(mapping_basis)) > 0),
    INDEX idx_reference_audio_relation_pitch (pitch_id, asset_id)
);
