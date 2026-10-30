-- 이전 OAuth 흐름과 해제 후 새 흐름을 구분한다. 기존 행은 created_at을 사용한다.
ALTER TABLE oauth_proofs ADD COLUMN flow_started_at DATETIME(6) NULL;
ALTER TABLE user_social_accounts ADD COLUMN oauth_started_at DATETIME(6) NULL;

CREATE TABLE kakao_unlink_subjects (
  subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  unlinked_at DATETIME(6) NULL,
  touched_at DATETIME(6) NOT NULL,
  INDEX idx_kakao_subjects_touched (touched_at)
) ENGINE=InnoDB;

CREATE TABLE kakao_event_receipts (
  event_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  transaction_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  received_at DATETIME(6) NOT NULL,
  INDEX idx_kakao_receipts_received (received_at)
) ENGINE=InnoDB;
