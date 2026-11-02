-- Nominal basic-tone identity only; existing assets stay unlinked until an explicit mapping is applied.
ALTER TABLE reference_audio_assets
    ADD COLUMN pitch_id BIGINT NULL,
    ADD COLUMN pitch_mapping_basis TEXT NULL,
    ADD CONSTRAINT fk_reference_audio_pitch FOREIGN KEY (pitch_id) REFERENCES pitches(id),
    ADD INDEX ix_reference_audio_pitch (dataset_id, status, pitch_id),
    ADD CONSTRAINT ck_reference_audio_pitch_basis CHECK (
        (pitch_id IS NULL AND pitch_mapping_basis IS NULL) OR
        (pitch_id IS NOT NULL AND pitch_mapping_basis IS NOT NULL AND CHAR_LENGTH(TRIM(pitch_mapping_basis)) > 0
            AND category = '기본음계')
    );
