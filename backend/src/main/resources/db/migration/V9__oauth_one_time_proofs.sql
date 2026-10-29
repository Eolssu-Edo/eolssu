-- OAuth state와 계정 연결 티켓. 원문 대신 임의 256비트 토큰의 SHA-256만 저장한다.
CREATE TABLE oauth_proofs (
  token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  kind VARCHAR(8) NOT NULL,
  provider VARCHAR(8) NOT NULL,
  purpose VARCHAR(8) NOT NULL,
  binding_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  user_id BIGINT NULL,
  redirect_uri VARCHAR(2048) NULL,
  provider_subject VARCHAR(255) COLLATE utf8mb4_bin NULL,
  email VARCHAR(320) NULL,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  expires_at DATETIME(6) NOT NULL,
  consumed_at DATETIME(6) NULL,
  CONSTRAINT chk_oauth_proofs_kind CHECK (kind IN ('STATE','MERGE')),
  CONSTRAINT chk_oauth_proofs_provider CHECK (provider IN ('KAKAO','GOOGLE','NAVER')),
  CONSTRAINT chk_oauth_proofs_purpose CHECK (purpose IN ('LOGIN','LINK')),
  CONSTRAINT chk_oauth_proofs_context CHECK (
    (kind='STATE' AND redirect_uri IS NOT NULL AND provider_subject IS NULL AND email IS NULL
      AND ((purpose='LOGIN' AND user_id IS NULL) OR (purpose='LINK' AND user_id IS NOT NULL)))
    OR (kind='MERGE' AND purpose='LOGIN' AND user_id IS NOT NULL AND provider_subject IS NOT NULL AND email IS NOT NULL)
  ),
  INDEX idx_oauth_proofs_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
