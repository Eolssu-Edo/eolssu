-- S15P31E102-707. Publishers archive a public edition before replacing it, in the same transaction.
-- This table contains public catalog data only; user sheets, recordings and results are not copied.
CREATE TABLE song_score_versions (
    song_id BIGINT NOT NULL,
    score_version INT NOT NULL,
    instrument_id BIGINT NOT NULL,
    jangdan_id BIGINT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT NULL,
    difficulty SMALLINT NOT NULL,
    default_bpm SMALLINT NOT NULL,
    score_json JSON NOT NULL,
    umulsai_text TEXT NULL,
    note_count INT NOT NULL,
    source VARCHAR(255) NULL,
    is_scorable BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (song_id, score_version),
    CONSTRAINT fk_song_score_version_song FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
    CONSTRAINT chk_song_score_version_number CHECK (score_version > 0),
    CONSTRAINT chk_song_score_version_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT chk_song_score_version_bpm CHECK (default_bpm BETWEEN 30 AND 400),
    CONSTRAINT chk_song_score_version_note_count CHECK (note_count >= 0),
    CONSTRAINT chk_song_score_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
