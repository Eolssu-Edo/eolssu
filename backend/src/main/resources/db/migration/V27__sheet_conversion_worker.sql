-- S15P31E102-472: 인식 작업자 결과와 원인별 재시도. 기존 V26은 변경하지 않는다.
ALTER TABLE sheet_conversions
  ADD COLUMN failure_cause VARCHAR(30) COLLATE ascii_bin,
  ADD COLUMN timeout_count INT NOT NULL DEFAULT 0,
  ADD COLUMN result_json JSON,
  ADD COLUMN running_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'RUNNING' THEN 1 END) STORED,
  ADD CONSTRAINT chk_sheet_conversion_timeouts CHECK (timeout_count >= 0),
  ADD CONSTRAINT uq_sheet_conversion_running UNIQUE (running_slot);

CREATE TABLE sheet_source_pages (
  sheet_id BIGINT NOT NULL,
  page_idx INT NOT NULL,
  object_key VARCHAR(200) COLLATE ascii_bin NOT NULL,
  width INT NOT NULL,
  height INT NOT NULL,
  PRIMARY KEY (sheet_id, page_idx),
  CONSTRAINT fk_sheet_source_pages_sheet FOREIGN KEY (sheet_id) REFERENCES user_sheets(id) ON DELETE CASCADE,
  CONSTRAINT chk_sheet_source_pages_dimensions CHECK (page_idx BETWEEN 0 AND 9 AND width > 0 AND height > 0),
  CONSTRAINT uq_sheet_source_pages_key UNIQUE (object_key)
);
