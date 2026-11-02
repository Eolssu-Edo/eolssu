-- 내 악보 (S15P31E102-390, SHT-01·03·08). 규칙은 V1 머리말을 따른다. 계약: docs/specs/user-sheets.md.
-- 새 테이블만 추가한다. 기존 테이블과 데이터는 바꾸지 않는다. V15(모범 음원 객체 키) 다음 번호다.

CREATE TABLE user_sheets (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id            BIGINT       NOT NULL,
  title              VARCHAR(50)  NOT NULL,
  kind               VARCHAR(20)  COLLATE ascii_bin NOT NULL,                   -- 악보 종류. MIDI·PDF는 해당 기능에서 CHECK를 넓힌다
  source             VARCHAR(20)  COLLATE ascii_bin NOT NULL,                   -- 만든 방식. 직접 작성·복사는 MANUAL
  status             VARCHAR(20)  COLLATE ascii_bin NOT NULL,                   -- NEEDS_REVIEW(평가 불가) / EVALUABLE
  jangdan_id         BIGINT,                                                    -- 장단이 없는 곡(V15)을 복사하면 NULL
  bpm                SMALLINT     NOT NULL,
  bpm_unit           VARCHAR(20)  COLLATE ascii_bin NOT NULL,                   -- 만들 때의 장단 또는 원본 악보 music.bpmUnit
  column_count       INT          NOT NULL,                                     -- 각 수. 복사한 곡은 40을 넘을 수 있다
  score_version      INT          NOT NULL DEFAULT 1,                           -- score_json의 score.scoreVersion과 같다
  score_json         JSON,                                                      -- 공통 악보 v1. 한 번도 저장하지 않은 빈 악보는 NULL
  note_count         INT          NOT NULL DEFAULT 0,
  pending_cell_count INT          NOT NULL DEFAULT 0,                           -- 확인이 필요한 정간 수
  idempotency_key    VARCHAR(100) COLLATE ascii_bin NOT NULL,                   -- SHT-03 Idempotency-Key. 같은 키 재전송은 기존 악보 반환
  request_hash       CHAR(64)     COLLATE ascii_bin NOT NULL,                   -- 같은 키에 다른 본문이면 409를 내려고 본문 SHA-256을 남긴다
  created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT chk_user_sheets_kind CHECK (kind IN ('JEONGGANBO')),
  CONSTRAINT chk_user_sheets_source CHECK (source IN ('MANUAL')),
  CONSTRAINT chk_user_sheets_status CHECK (status IN ('NEEDS_REVIEW', 'EVALUABLE')),
  CONSTRAINT chk_user_sheets_bpm CHECK (bpm BETWEEN 30 AND 400),
  CONSTRAINT chk_user_sheets_bpm_unit CHECK (bpm_unit IN ('QUARTER', 'DOTTED_QUARTER', 'JEONGGAN', 'JANGDAN_BEAT')),
  CONSTRAINT chk_user_sheets_column_count CHECK (column_count >= 1),
  CONSTRAINT chk_user_sheets_counts CHECK (score_version >= 1 AND note_count >= 0 AND pending_cell_count >= 0),
  CONSTRAINT fk_user_sheets_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_sheets_jangdan FOREIGN KEY (jangdan_id) REFERENCES jangdans(id),
  CONSTRAINT uq_user_sheets_user_idempotency UNIQUE (user_id, idempotency_key),
  INDEX idx_user_sheets_user_updated (user_id, updated_at, id)                  -- SHT-01 목록 정렬
);
