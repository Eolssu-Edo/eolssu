-- 1차 MVP API 명세 보완 (S15P31E102-142). 규칙은 V1 머리말을 따른다.
-- 인증·회원(AUTH·USR), 곡·장단(CAT), 연습 기록(PRA·REC), 악보 화면 체류(GRO-04)에 필요한 저장 구조
-- CHECK·UNIQUE 제약에는 이름을 붙인다. 앱과 검사가 위반한 제약을 이름으로 구분한다(3819·1062 메시지).

-- ---------- 적용 전 확인 ----------
-- MySQL DDL은 되돌려지지 않는다. 아래 변경 도중 실패하면 앞부분만 남아 다시 실행할 수 없으므로,
-- 기존 데이터가 V2 제약을 만족하는지 DDL 전에 확인한다. 실패하면 아무것도 바뀌지 않는다.
-- 실패 시: 해당 행을 고치고 flyway_schema_history의 실패 행(version 2, success 0)을 지운 뒤 다시 적용한다(backend README).
CREATE TEMPORARY TABLE v2_preconditions (
  item VARCHAR(40) NOT NULL,
  ok   BOOLEAN     NOT NULL,
  CONSTRAINT chk_v2_preconditions CHECK (ok = TRUE)
);
INSERT INTO v2_preconditions (item, ok)
SELECT 'jangdans.name', name IN ('세마치', '굿거리', '중모리', '타령') FROM jangdans        -- bpm_unit을 정할 수 있는 장단(회의 1-2)
UNION ALL
SELECT 'users.deleted_personal', email IS NULL AND password_hash IS NULL AND nickname IS NULL
FROM users WHERE deleted_at IS NOT NULL                                             -- 탈퇴 완료 회원은 개인정보가 비어 있어야 함
UNION ALL
SELECT 'practice_sessions.bpm', bpm BETWEEN 20 AND 240 FROM practice_sessions;     -- PRA-01 BPM 범위(초안)
DROP TEMPORARY TABLE v2_preconditions;

-- ---------- 사용자 ----------
-- 간편 로그인 회원은 비밀번호가 없고, 계정 통합을 거절한 회원은 이메일도 없다. 비밀번호가 있으면 이메일은 있어야 한다.
ALTER TABLE users DROP CHECK users_chk_2;

ALTER TABLE users
  ALTER COLUMN notation_mode SET DEFAULT 'HANJA',                                 -- AUTH-02: 기본 표기 HANJA
  ADD COLUMN status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER notation_mode,
  ADD COLUMN withdrawal_requested_at DATETIME(6) AFTER last_login_at,              -- USR-05 탈퇴 요청 시각
  ADD COLUMN purge_scheduled_at      DATETIME(6) AFTER withdrawal_requested_at;    -- 일괄 삭제 예정 시각(요청 + 30일). 그 전에 로그인하면 취소

UPDATE users SET status = 'DELETED' WHERE deleted_at IS NOT NULL;

-- deleted_at은 30일 뒤 일괄 삭제(개인정보 비움)를 마친 시각이다. 탈퇴 요청 시각은 withdrawal_requested_at.
-- 탈퇴를 취소하거나 일괄 삭제를 마치면 withdrawal_requested_at·purge_scheduled_at을 함께 비운다.
ALTER TABLE users
  ADD CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'DELETED')),
  ADD CONSTRAINT chk_users_password_email CHECK (password_hash IS NULL OR email IS NOT NULL),
  ADD CONSTRAINT chk_users_withdrawal CHECK ((status = 'WITHDRAWAL_PENDING') = (withdrawal_requested_at IS NOT NULL)),
  ADD CONSTRAINT chk_users_withdrawal_times CHECK ((withdrawal_requested_at IS NULL) = (purge_scheduled_at IS NULL)),
  ADD CONSTRAINT chk_users_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL)),
  ADD CONSTRAINT chk_users_deleted_personal CHECK (
    status <> 'DELETED' OR (email IS NULL AND password_hash IS NULL AND nickname IS NULL)
  ),
  ADD INDEX idx_users_purge (status, purge_scheduled_at);                          -- 매일 일괄 삭제 대상 조회

CREATE TABLE user_social_accounts (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT       NOT NULL,
  provider         VARCHAR(20)  NOT NULL,
  provider_user_id VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,                    -- 제공자 사용자 ID. 대소문자 구분
  created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_user_social_accounts_provider_user UNIQUE (provider, provider_user_id), -- 회원은 이 쌍으로 찾는다. 이메일로 찾지 않음. 일괄 삭제 때 행을 지워 재가입 가능
  CONSTRAINT uq_user_social_accounts_user_provider UNIQUE (user_id, provider),          -- 한 회원에 제공자별 1개(AUTH-09 PROVIDER_ALREADY_LINKED)
  CONSTRAINT chk_user_social_accounts_provider CHECK (provider IN ('KAKAO', 'GOOGLE', 'NAVER')),
  FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 약관은 버전마다 행 하나. 내용을 바꾸면 새 버전 행을 추가하고 예전 행은 동의 기록 때문에 남긴다.
CREATE TABLE terms (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  code         VARCHAR(30)  NOT NULL,                                              -- SERVICE_TERMS / PRIVACY_COLLECTION / MICROPHONE_NOTICE 등
  version      VARCHAR(20)  NOT NULL,                                              -- 2026-10 등
  title        VARCHAR(100) NOT NULL,
  content_url  VARCHAR(512) NOT NULL,
  is_required  BOOLEAN      NOT NULL,
  seq          SMALLINT     NOT NULL,                                              -- 화면 표시 순서
  effective_at DATETIME(6)  NOT NULL,                                              -- 시행 시각. 같은 code에서 시행된 가장 최신 버전이 현재 약관
  created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_terms_code_version UNIQUE (code, version)
);

CREATE TABLE user_term_agreements (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id   BIGINT      NOT NULL,
  term_id   BIGINT      NOT NULL,                                                  -- 동의한 버전의 terms 행
  agreed    BOOLEAN     NOT NULL,                                                  -- 선택 약관은 FALSE 허용
  agreed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_user_term_agreements_user_term UNIQUE (user_id, term_id),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (term_id) REFERENCES terms(id)
);

-- 인증번호 원문은 저장하지 않는다. 같은 이메일·목적으로 다시 보내면 이전 행은 invalidated_at을 채워 무효화한다.
-- ACCOUNT_MERGE는 계정 통합(AUTH-09) 때 기존 이메일 본인 확인용. SIGN_UP은 가입된 이메일이면 409라 따로 둔다.
CREATE TABLE email_verifications (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  email            VARCHAR(320) NOT NULL,                                          -- 소문자 정규화
  purpose          VARCHAR(20)  NOT NULL,
  code_hash        VARCHAR(255) NOT NULL,                                          -- 서버 비밀키 HMAC-SHA256. 6자리는 경우의 수가 적어 솔트 없는 해시면 원문이 드러난다
  attempt_count    SMALLINT     NOT NULL DEFAULT 0,                                -- 틀린 확인 시도 수
  expires_at       DATETIME(6)  NOT NULL,                                          -- 발송 + 3분
  verified_at      DATETIME(6),                                                    -- 인증번호 확인 성공 시각
  token_hash       CHAR(64)     COLLATE ascii_bin,                                 -- verificationToken(AUTH-07 응답, 임의 256비트)의 SHA-256
  token_expires_at DATETIME(6),
  consumed_at      DATETIME(6),                                                    -- 가입·통합에 토큰을 쓴 시각. 한 번만 사용
  invalidated_at   DATETIME(6),                                                    -- 재발송으로 무효가 된 시각
  created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_email_verifications_token UNIQUE (token_hash),
  CONSTRAINT chk_email_verifications_purpose CHECK (purpose IN ('SIGN_UP', 'ACCOUNT_MERGE', 'PASSWORD_RESET')),
  CONSTRAINT chk_email_verifications_attempts CHECK (attempt_count >= 0),
  CONSTRAINT chk_email_verifications_token CHECK (
    (token_hash IS NULL) = (verified_at IS NULL) AND (token_hash IS NULL) = (token_expires_at IS NULL)
  ),
  CONSTRAINT chk_email_verifications_consumed CHECK (consumed_at IS NULL OR token_hash IS NOT NULL),
  INDEX idx_email_verifications_email (email, purpose, created_at)                 -- 최근 발송·재발송 간격·발송 횟수 확인
);

-- 리프레시 토큰은 해시만 저장한다. 재발급마다 새 행을 만들고 이전 행은 ROTATED로 폐기한다.
-- 폐기된 토큰이 다시 오면 그 회원(user_id)의 리프레시 토큰을 모두 REUSE_DETECTED로 폐기한다(AUTH-04).
CREATE TABLE refresh_tokens (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT      NOT NULL,
  token_hash    CHAR(64)    COLLATE ascii_bin NOT NULL,                            -- 임의 256비트 토큰의 SHA-256 hex
  expires_at    DATETIME(6) NOT NULL,                                              -- 발급 + 14일
  revoked_at    DATETIME(6),
  revoke_reason VARCHAR(20),
  created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_refresh_tokens_token UNIQUE (token_hash),
  CONSTRAINT chk_refresh_tokens_revoke_reason CHECK (
    revoke_reason IN ('ROTATED', 'SIGN_OUT', 'PASSWORD_CHANGED', 'WITHDRAWN', 'REUSE_DETECTED')
  ),
  CONSTRAINT chk_refresh_tokens_revoked CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
  FOREIGN KEY (user_id) REFERENCES users(id),
  INDEX idx_refresh_tokens_user (user_id, revoked_at)                              -- 재사용 탐지·로그아웃·비밀번호 변경·탈퇴 시 회원 단위 일괄 폐기
);

-- 곡별로 마지막에 쓴 BPM(USR-02 bpmBySong). 설정이 없으면 곡 기본 BPM을 쓴다.
CREATE TABLE user_song_settings (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT      NOT NULL,
  song_id    BIGINT      NOT NULL,
  bpm        SMALLINT    NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_user_song_settings_user_song UNIQUE (user_id, song_id),
  CONSTRAINT chk_user_song_settings_bpm CHECK (bpm BETWEEN 20 AND 240),            -- 세션 BPM과 같은 범위(PRA-01 초안)
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (song_id) REFERENCES songs(id)
);

-- ---------- 기준 데이터 ----------
-- 기존 장단은 이름으로 단위를 채운 뒤 NOT NULL로 바꾼다. 새 장단은 단위를 반드시 넣는다(기본값 없음).
ALTER TABLE jangdans
  ADD COLUMN bpm_unit VARCHAR(20) AFTER default_bpm;                               -- BPM 한 박의 단위

UPDATE jangdans
SET bpm_unit = CASE
  WHEN name IN ('세마치', '굿거리') THEN 'DOTTED_QUARTER'
  WHEN name = '중모리' THEN 'QUARTER'
  WHEN name = '타령' THEN 'JEONGGAN'
END;

ALTER TABLE jangdans
  MODIFY COLUMN bpm_unit VARCHAR(20) NOT NULL,
  ADD CONSTRAINT chk_jangdans_bpm_unit CHECK (bpm_unit IN ('QUARTER', 'DOTTED_QUARTER', 'JEONGGAN', 'JANGDAN_BEAT'));

ALTER TABLE songs
  ADD COLUMN description   TEXT AFTER title,                                       -- 곡 소개(CAT-02)
  ADD COLUMN score_version INT  NOT NULL DEFAULT 1 AFTER score_json,               -- score_json을 바꿀 때마다 1씩 올린다
  ADD CONSTRAINT chk_songs_score_version CHECK (score_version >= 1);

-- ---------- 연습 기록 ----------
-- saved_at·memo·viewed_at·record_deleted_at은 사용자가 나중에 바꾼다. 데이터 파이프라인은 이 칸을 읽지 않는다.
-- 부모 세션이 지워지면(탈퇴 일괄 삭제) 자식의 parent_session_id를 비운다. 한 문장으로 회원의 세션을 지울 수 있어야 한다.
-- BPM 범위는 V1의 30~400(practice_sessions_chk_1)에서 PRA-01의 20~240(bpmUnit 단위로 센 값, 초안)으로 바꾼다.
ALTER TABLE practice_sessions DROP CHECK practice_sessions_chk_1;

ALTER TABLE practice_sessions
  ADD COLUMN mission_id        BIGINT AFTER song_id,                               -- 미션 연습이면 미션 ID. missions 테이블 추가 시 외래키 연결
  ADD COLUMN parent_session_id BIGINT AFTER mission_id,                            -- 비교·재도전의 기준이 된 이전 세션(본인·DONE·같은 곡은 앱이 확인)
  ADD COLUMN score_version     INT    NOT NULL DEFAULT 1 AFTER parent_session_id,  -- 세션을 만들 때의 songs.score_version
  ADD COLUMN bpm_unit          VARCHAR(20) AFTER bpm,                              -- bpm의 단위. 생략하면 곡 장단의 bpm_unit
  ADD COLUMN idempotency_key   VARCHAR(100) COLLATE ascii_bin AFTER bpm_unit,      -- PRA-01 Idempotency-Key. 같은 키 재전송은 기존 세션 반환
  ADD COLUMN request_hash      CHAR(64)     COLLATE ascii_bin AFTER idempotency_key, -- 같은 키에 다른 본문이면 409를 내려고 본문 SHA-256을 남긴다
  ADD COLUMN saved_at          DATETIME(6) AFTER ended_at,                         -- REC-01로 연습 기록에 저장한 시각. NULL이면 저장 안 함
  ADD COLUMN memo              VARCHAR(500) AFTER saved_at,
  ADD COLUMN viewed_at         DATETIME(6) AFTER memo,                             -- 기록 상세를 처음 연 시각. NULL이면 REC-02 isNew
  ADD COLUMN record_deleted_at DATETIME(6) AFTER viewed_at,                        -- REC-04로 기록을 지운 시각. 점수 집계는 남기고 목록·재생에서 뺀다
  ADD CONSTRAINT chk_practice_sessions_bpm CHECK (bpm BETWEEN 20 AND 240),
  ADD CONSTRAINT chk_practice_sessions_score_version CHECK (score_version >= 1),
  ADD CONSTRAINT chk_practice_sessions_idempotency CHECK ((idempotency_key IS NULL) = (request_hash IS NULL)),
  ADD CONSTRAINT fk_practice_sessions_parent FOREIGN KEY (parent_session_id) REFERENCES practice_sessions(id) ON DELETE SET NULL,
  ADD CONSTRAINT uq_sessions_user_idempotency UNIQUE (user_id, idempotency_key);        -- REC-02 목록은 기존 idx_sessions_user_started를 쓴다

UPDATE practice_sessions ps
JOIN songs s ON s.id = ps.song_id
JOIN jangdans j ON j.id = s.jangdan_id
SET ps.bpm_unit = j.bpm_unit;

ALTER TABLE practice_sessions
  MODIFY COLUMN bpm_unit VARCHAR(20) NOT NULL,
  ADD CONSTRAINT chk_practice_sessions_bpm_unit CHECK (bpm_unit IN ('QUARTER', 'DOTTED_QUARTER', 'JEONGGAN', 'JANGDAN_BEAT'));

-- 악보 화면 체류 기록(GRO-04). 연습 시간 집계 기준 후보(회의 E1 결정안)
CREATE TABLE score_views (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id          BIGINT      NOT NULL,
  song_id          BIGINT      NOT NULL,
  idempotency_key  VARCHAR(100) COLLATE ascii_bin NOT NULL,                        -- Idempotency-Key 헤더. 같은 키에 다른 본문은 저장한 칸과 비교해 409
  started_at       DATETIME(6) NOT NULL,
  ended_at         DATETIME(6) NOT NULL,
  visible_seconds  INT         NOT NULL,                                           -- FE가 보낸 실제 보인 시간
  counted_seconds  INT         NOT NULL,                                           -- 최대 인정 시간을 적용해 연습 시간으로 센 값
  created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT uq_score_views_user_idempotency UNIQUE (user_id, idempotency_key),
  CONSTRAINT chk_score_views_period CHECK (ended_at >= started_at),
  CONSTRAINT chk_score_views_counted CHECK (counted_seconds BETWEEN 0 AND visible_seconds),          -- 보인 초가 0 이상인 것도 함께 강제
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (song_id) REFERENCES songs(id),
  INDEX idx_score_views_user_started (user_id, started_at)                         -- 날짜별 연습 시간 집계
);
