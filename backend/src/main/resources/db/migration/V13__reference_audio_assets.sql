-- S15P31E102-364. Permanent reference clips are separate from private, expiring user recordings.
-- Files/rights must be checked before changing DRAFT to PUBLISHED. This migration seeds no media.
-- Final unapplied feature migration: assets, nominal pitch identities and role relationships.
-- MySQL DDL is not transactionally rolled back; inspect backup/schema state if deployment fails.
CREATE TABLE reference_audio_assets (
    asset_id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dataset_id VARCHAR(80) NOT NULL,
    label_id VARCHAR(160) NOT NULL,
    category VARCHAR(32) NOT NULL,
    technique VARCHAR(100) NOT NULL,
    register_name VARCHAR(32) NOT NULL,
    yulmyeong VARCHAR(32) NOT NULL,
    display_label VARCHAR(255) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    byte_size BIGINT NOT NULL,
    duration_ms INT NOT NULL,
    sample_rate_hz INT NOT NULL,
    channels SMALLINT NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    eligible_for_note_scoring BOOLEAN NOT NULL,
    metadata_json JSON NOT NULL,
    pitch_id BIGINT NULL,
    pitch_mapping_basis TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (asset_id),
    UNIQUE KEY uq_reference_audio_label (dataset_id, label_id),
    KEY idx_reference_audio_browse (dataset_id, status, category),
    KEY ix_reference_audio_pitch (dataset_id, status, pitch_id),
    CONSTRAINT fk_reference_audio_pitch FOREIGN KEY (pitch_id) REFERENCES pitches(id),
    CONSTRAINT ck_reference_audio_pitch_basis CHECK (
        (pitch_id IS NULL AND pitch_mapping_basis IS NULL) OR
        (pitch_id IS NOT NULL AND pitch_mapping_basis IS NOT NULL AND CHAR_LENGTH(TRIM(pitch_mapping_basis)) > 0
            AND category = '기본음계')
    ),
    CONSTRAINT chk_reference_audio_dataset CHECK (dataset_id IN ('danso','sogeum','jeongak-daegeum','sanjo-daegeum')),
    CONSTRAINT chk_reference_audio_category CHECK (category IN ('기본음계','시김새')),
    CONSTRAINT chk_reference_audio_hashes CHECK (REGEXP_LIKE(asset_id, '^[0-9a-f]{64}$', 'c') AND REGEXP_LIKE(sha256, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_reference_audio_key CHECK (BINARY object_key = BINARY CONCAT('reference-audio/v1/',dataset_id,'/',asset_id,'/',sha256,'.m4a')),
    CONSTRAINT chk_reference_audio_type CHECK (content_type = 'audio/mp4'),
    CONSTRAINT chk_reference_audio_dimensions CHECK (byte_size > 0 AND duration_ms > 0 AND sample_rate_hz > 0 AND channels BETWEEN 1 AND 8),
    CONSTRAINT chk_reference_audio_review CHECK (review_status IN ('inferred','reviewed','rejected') AND eligible_for_note_scoring IN (0,1) AND (eligible_for_note_scoring = 0 OR review_status = 'reviewed')),
    CONSTRAINT chk_reference_audio_metadata CHECK (JSON_TYPE(metadata_json) = 'OBJECT'),
    CONSTRAINT chk_reference_audio_status CHECK (status IN ('DRAFT','PUBLISHED') AND (status <> 'PUBLISHED' OR review_status <> 'rejected'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;

-- Nominal identities required by existing jeongak/sogeum/sanjo basic-tone labels.
-- Existing V5 IDs/semitones and instrument range endpoints remain unchanged; IDs are allocated by MySQL.
-- Hanja follows data/src/scores/review_page.html HANJA. This seed does not verify recording tuning.
INSERT INTO pitches (yul, octave, hanja, hangul, semitone) VALUES
    ('임', -1, '㑣', '배임', -5),
    ('남', -1, '㑲', '배남', -3),
    ('무', -1, '㒇', '배무', -2),
    ('황', 0, '黃', '황', 0),
    ('태', 0, '太', '태', 2),
    ('고', 0, '姑', '고', 4),
    ('남', 0, '南', '남', 9),
    ('고', 1, '㴌', '청고', 16),
    ('남', 1, '湳', '청남', 21),
    ('고', 2, '㵈', '중청고', 28),
    ('임', 2, '㵉', '중청임', 31);

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
