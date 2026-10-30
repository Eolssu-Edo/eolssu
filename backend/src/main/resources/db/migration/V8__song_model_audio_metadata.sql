-- S15P31E102-288. Public model audio is selected only for the song's current score version and tempo.
-- No candidate audio or unreviewed rights metadata is seeded by this migration.
CREATE TABLE song_model_audios (
    song_id BIGINT NOT NULL,
    score_version INT NOT NULL,
    audio_url VARCHAR(2048) NOT NULL,
    bpm SMALLINT NOT NULL,
    bpm_unit VARCHAR(32) NOT NULL,
    duration_ms INT NOT NULL,
    start_offset_ms INT NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (song_id, score_version),
    CONSTRAINT fk_model_audio_song FOREIGN KEY (song_id) REFERENCES songs (id),
    CONSTRAINT chk_model_audio_version CHECK (score_version > 0),
    CONSTRAINT chk_model_audio_bpm CHECK (bpm BETWEEN 30 AND 400),
    CONSTRAINT chk_model_audio_unit CHECK (bpm_unit IN ('QUARTER', 'DOTTED_QUARTER', 'JEONGGAN', 'JANGDAN_BEAT')),
    CONSTRAINT chk_model_audio_duration CHECK (duration_ms > 0 AND start_offset_ms >= 0 AND start_offset_ms < duration_ms),
    CONSTRAINT chk_model_audio_type CHECK (content_type IN ('audio/mp4', 'audio/mpeg', 'audio/ogg', 'audio/wav')),
    CONSTRAINT chk_model_audio_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
