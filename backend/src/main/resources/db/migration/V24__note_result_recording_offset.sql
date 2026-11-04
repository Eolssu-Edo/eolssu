-- 음표별 녹음 위치(S15P31E102-480). 업로드 원본 녹음의 재생 0ms부터 잰 음표 시작 시각이며 AI가 주지 않았거나 MISSED면 NULL이다.
-- 기존 평가 행은 NULL로 남는다. detected_pitch_id는 V1부터 있던 칸을 그대로 채운다.
ALTER TABLE note_results
  ADD COLUMN recording_offset_ms INT NULL,
  ADD CONSTRAINT chk_note_results_recording_offset CHECK (recording_offset_ms IS NULL OR recording_offset_ms >= 0);
