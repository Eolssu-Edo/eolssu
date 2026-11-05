-- 음표별 부분 점수와 합산 규칙(S15P31E102-502). EVA-04가 임의 구간 점수를 계산하는 재료다.
-- 값은 AI가 준 그대로 DOUBLE로 둔다. 곡 전체를 합친 값이 AI 점수와 같아야 해서 자리를 줄이지 않는다.
-- 기존 평가 행과 값을 주지 않는 이전 AI 응답은 NULL이며 EVA-04가 NO_NOTE_SCORES로 답한다. 시김새 음은 pitch_score만 NULL이다.
ALTER TABLE note_results
  ADD COLUMN pitch_score DOUBLE NULL,
  ADD COLUMN timing_score DOUBLE NULL,
  ADD COLUMN duration_score DOUBLE NULL,
  ADD COLUMN weight DOUBLE NULL,
  ADD CONSTRAINT chk_note_results_pitch_score CHECK (pitch_score IS NULL OR pitch_score BETWEEN 0 AND 100),
  ADD CONSTRAINT chk_note_results_timing_score CHECK (timing_score IS NULL OR timing_score BETWEEN 0 AND 100),
  ADD CONSTRAINT chk_note_results_duration_score CHECK (duration_score IS NULL OR duration_score BETWEEN 0 AND 100),
  ADD CONSTRAINT chk_note_results_weight CHECK (weight IS NULL OR weight > 0);

ALTER TABLE evaluations
  ADD COLUMN scoring_version VARCHAR(30) NULL,
  ADD COLUMN score_weights JSON NULL,
  ADD CONSTRAINT chk_evaluations_scoring_rule CHECK ((scoring_version IS NULL) = (score_weights IS NULL));
