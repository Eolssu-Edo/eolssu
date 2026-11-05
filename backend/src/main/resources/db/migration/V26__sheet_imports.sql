-- 내 악보 불러오기 접수·원본 보관·변환 상태 (S15P31E102-471, SHT-07·10). 규칙은 V1 머리말을 따른다. 계약: docs/specs/user-sheets.md.
-- 기존 행·데이터는 바꾸지 않는다. V25(drop_practice_session_mission, S15P31E102-465) 다음 번호다.

-- 불러오기로 만든 악보의 만든 방식(SHT-01 source).
ALTER TABLE user_sheets
  DROP CHECK chk_user_sheets_source,
  ADD CONSTRAINT chk_user_sheets_source CHECK (source IN ('MANUAL', 'JEONGGANBO_OMR', 'STAFF_OMR'));

-- 변환 한 건이 악보 하나다. 이 표가 곧 큐이며 작업자(S15P31E102-472)가 QUEUED를 접수 순으로 꺼낸다.
CREATE TABLE sheet_conversions (
  sheet_id             BIGINT       PRIMARY KEY,
  user_id              BIGINT       NOT NULL,
  notation             VARCHAR(20)  COLLATE ascii_bin NOT NULL,                 -- JEONGGANBO / STAFF
  instrument_id        BIGINT       NOT NULL,
  jangdan_id           BIGINT,                                                  -- 요청 장단. STAFF에서 생략하면 NULL(인식 서비스가 정한다)
  bpm                  SMALLINT,                                                -- 요청 속도. 생략하면 NULL(장단 기본 BPM, bpmAuto)
  hwang_pitch          VARCHAR(4)   COLLATE ascii_bin,                          -- STAFF 요청 황종 음(예 Eb4). 생략하면 NULL(자동 조 맞춤)
  status               VARCHAR(20)  COLLATE ascii_bin NOT NULL,                 -- QUEUED / RUNNING / DONE / FAILED
  stage                VARCHAR(20)  COLLATE ascii_bin,                          -- RUNNING일 때만 OMR / BUILDING_SCORE
  page_count           INT          NOT NULL,                                   -- PDF 쪽 수 또는 이미지 장 수
  processed_page_count INT          NOT NULL DEFAULT 0,
  failure_code         VARCHAR(30)  COLLATE ascii_bin,                          -- FAILED일 때만(SHT-10 failureCode)
  attempt              INT          NOT NULL DEFAULT 1,                         -- 처음 1, 재시도(SHT-14)마다 1씩
  accepted_at          DATETIME(6)  NOT NULL,
  started_at           DATETIME(6),
  finished_at          DATETIME(6),
  -- 회원당 진행 중 변환 1건(SHT-07 규칙 6). 끝난 변환은 NULL이라 UNIQUE에 걸리지 않는다.
  active_user_id       BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('QUEUED', 'RUNNING') THEN user_id END) STORED,
  CONSTRAINT chk_sheet_conversions_notation CHECK (notation IN ('JEONGGANBO', 'STAFF')),
  CONSTRAINT chk_sheet_conversions_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
  CONSTRAINT chk_sheet_conversions_stage CHECK (stage IS NULL OR (status = 'RUNNING' AND stage IN ('OMR', 'BUILDING_SCORE'))),
  CONSTRAINT chk_sheet_conversions_bpm CHECK (bpm IS NULL OR bpm BETWEEN 30 AND 160),
  CONSTRAINT chk_sheet_conversions_pages CHECK (page_count BETWEEN 1 AND 10 AND processed_page_count BETWEEN 0 AND page_count),
  CONSTRAINT chk_sheet_conversions_failure CHECK ((status = 'FAILED') = (failure_code IS NOT NULL)),
  CONSTRAINT chk_sheet_conversions_attempt CHECK (attempt >= 1),
  CONSTRAINT chk_sheet_conversions_staff_only CHECK (notation = 'STAFF' OR (hwang_pitch IS NULL AND jangdan_id IS NOT NULL)),
  CONSTRAINT fk_sheet_conversions_sheet FOREIGN KEY (sheet_id) REFERENCES user_sheets(id) ON DELETE CASCADE,
  CONSTRAINT fk_sheet_conversions_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_sheet_conversions_instrument FOREIGN KEY (instrument_id) REFERENCES instruments(id),
  CONSTRAINT fk_sheet_conversions_jangdan FOREIGN KEY (jangdan_id) REFERENCES jangdans(id),
  CONSTRAINT uq_sheet_conversions_active_user UNIQUE (active_user_id),
  INDEX idx_sheet_conversions_queue (status, accepted_at, sheet_id)              -- 작업자가 오래된 QUEUED부터 꺼낸다
);

-- 올린 원본 파일. 비공개 저장소(eolssu.sheet-source-storage)의 객체 키만 둔다. 악보를 지우거나 탈퇴할 때까지 보관한다.
CREATE TABLE sheet_source_files (
  sheet_id     BIGINT       NOT NULL,
  file_idx     INT          NOT NULL,                                           -- 올린 순서(이미지는 쪽 순서). PDF는 0 하나
  object_key   VARCHAR(200) COLLATE ascii_bin NOT NULL,
  content_type VARCHAR(20)  COLLATE ascii_bin NOT NULL,                         -- application/pdf / image/png / image/jpeg
  byte_size    INT          NOT NULL,
  file_name    VARCHAR(255) NOT NULL,                                           -- 올린 파일 이름(SHT-08 source.fileName)
  PRIMARY KEY (sheet_id, file_idx),
  CONSTRAINT chk_sheet_source_files_idx CHECK (file_idx BETWEEN 0 AND 9),
  CONSTRAINT chk_sheet_source_files_type CHECK (content_type IN ('application/pdf', 'image/png', 'image/jpeg')),
  CONSTRAINT chk_sheet_source_files_size CHECK (byte_size BETWEEN 1 AND 20971520),
  CONSTRAINT uq_sheet_source_files_key UNIQUE (object_key),
  CONSTRAINT fk_sheet_source_files_sheet FOREIGN KEY (sheet_id) REFERENCES user_sheets(id) ON DELETE CASCADE
);
