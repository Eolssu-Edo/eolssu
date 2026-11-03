-- 내 악보 자동 저장 초안 (S15P31E102-413, SHT-11). 규칙은 V1 머리말을 따른다. 계약: docs/specs/user-sheets.md.
-- 기존 행은 초안 없음(NULL, 순번 0)으로 시작한다. 데이터는 바꾸지 않는다. V17(4악기 카탈로그, S15P31E102-407) 다음 번호다.

ALTER TABLE user_sheets
  ADD COLUMN draft_json         JSON        NULL AFTER pending_cell_count,            -- title·jangdanId·bpm·score. 확정 저장(SHT-04)에 성공하면 NULL
  ADD COLUMN draft_revision     INT         NOT NULL DEFAULT 0 AFTER draft_json,      -- 자동 저장마다 1씩 오른다. 다른 기기의 늦은 저장을 409로 막는다
  ADD COLUMN draft_base_version INT         NULL AFTER draft_revision,                -- 초안을 시작한 score_version
  ADD COLUMN draft_saved_at     DATETIME(6) NULL AFTER draft_base_version,
  ADD CONSTRAINT chk_user_sheets_draft CHECK (
    draft_revision >= 0
    AND ((draft_json IS NULL AND draft_base_version IS NULL AND draft_saved_at IS NULL)
      -- MySQL CHECK는 NULL 비교(알 수 없음)를 통과시키므로 IS NOT NULL을 따로 적는다.
      OR (draft_json IS NOT NULL AND draft_base_version IS NOT NULL AND draft_base_version >= 1 AND draft_saved_at IS NOT NULL))
  );
