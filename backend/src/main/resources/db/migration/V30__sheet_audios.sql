-- S15P31E102-481: requested snapshot and last successful pair have separate lifetimes.
CREATE TABLE sheet_audios (
  sheet_id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  generation CHAR(36) COLLATE ascii_bin NOT NULL,
  score_version INT NOT NULL,
  instrument_id BIGINT NOT NULL,
  with_jangdan BOOLEAN NOT NULL,
  bpm INT NOT NULL,
  status VARCHAR(12) COLLATE ascii_bin NOT NULL,
  failure_code VARCHAR(20) COLLATE ascii_bin,
  request_json JSON NOT NULL,
  result_json JSON,
  accepted_at DATETIME(6) NOT NULL,
  started_at DATETIME(6),
  finished_at DATETIME(6),
  deleting BOOLEAN NOT NULL DEFAULT FALSE,
  active_user BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('QUEUED','RUNNING') THEN user_id END) STORED,
  running_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status='RUNNING' THEN 1 END) STORED,
  CONSTRAINT fk_sheet_audio_sheet FOREIGN KEY(sheet_id) REFERENCES user_sheets(id) ON DELETE CASCADE,
  CONSTRAINT fk_sheet_audio_user FOREIGN KEY(user_id) REFERENCES users(id),
  CONSTRAINT fk_sheet_audio_instrument FOREIGN KEY(instrument_id) REFERENCES instruments(id),
  CONSTRAINT uq_sheet_audio_active_user UNIQUE(active_user),
  CONSTRAINT uq_sheet_audio_running UNIQUE(running_slot),
  CONSTRAINT chk_sheet_audio_status CHECK(status IN ('QUEUED','RUNNING','DONE','FAILED')),
  CONSTRAINT chk_sheet_audio_settings CHECK(score_version > 0 AND bpm BETWEEN 30 AND 160),
  INDEX ix_sheet_audio_queue(status,accepted_at,sheet_id)
);

-- Deliberately independent of sheet/user FKs: failed object deletion remains retryable
-- after the owning sheet is deleted. Signed URLs and score bodies are never stored here.
CREATE TABLE sheet_audio_objects (
  object_key VARCHAR(200) COLLATE ascii_bin PRIMARY KEY,
  sheet_id BIGINT NOT NULL,
  generation CHAR(36) COLLATE ascii_bin NOT NULL,
  state VARCHAR(8) COLLATE ascii_bin NOT NULL,
  CONSTRAINT chk_sheet_audio_object_state CHECK(state IN ('PENDING','CURRENT','RETIRED')),
  INDEX ix_sheet_audio_object_cleanup(state),
  INDEX ix_sheet_audio_object_sheet(sheet_id)
);

CREATE TABLE sheet_audio_requests (
  user_id BIGINT PRIMARY KEY,
  window_at DATETIME(6) NOT NULL,
  request_count INT NOT NULL,
  CONSTRAINT fk_sheet_audio_rate_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
