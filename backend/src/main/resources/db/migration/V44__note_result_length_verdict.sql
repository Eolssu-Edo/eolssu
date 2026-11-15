-- 긴 음의 길이 오류 판정 LENGTH(S15P31E102-912). AI pyin-8부터 note_results.verdict에 LENGTH가 온다.
-- V1의 판정 CHECK는 이름 없이 만들어져 MySQL 자동 이름 note_results_chk_2를 쓴다. 기존 행은 바뀌지 않는다.

-- ---------- 적용 전 확인 ----------
-- MySQL DDL은 되돌려지지 않는다. 자동 이름이 다른 DB면 DDL 전에 멈춘다.
-- 여기서 실패하면 원인을 정리하고 flyway_schema_history의 실패 행(version 44, success 0)을 지운 뒤 다시 적용한다(backend README).
CREATE TEMPORARY TABLE v44_preconditions (
  item VARCHAR(40) NOT NULL,
  ok   BOOLEAN     NOT NULL,
  CONSTRAINT chk_v44_preconditions CHECK (ok = TRUE)
);
INSERT INTO v44_preconditions (item, ok) SELECT 'note_results_chk_2', EXISTS (
  SELECT 1 FROM information_schema.check_constraints
  WHERE constraint_schema = DATABASE() AND constraint_name = 'note_results_chk_2' AND check_clause LIKE '%verdict%');
DROP TEMPORARY TABLE v44_preconditions;

ALTER TABLE note_results
  DROP CHECK note_results_chk_2,
  ADD CONSTRAINT chk_note_results_verdict CHECK (verdict IN ('CORRECT', 'PITCH', 'TIMING', 'LENGTH', 'MISSED'));
