-- 녹음 원본 메타데이터 (S15P31E102-179, PRA-02 녹음 파일 규격). 규칙은 V1 머리말을 따른다.
-- 업로드 검사(ffprobe)를 통과해 원본을 저장할 때 네 칸을 함께 채운다. 업로드 전 세션과 기존 행은 모두 NULL이다.
-- 크기·길이 한도 실측과 분석 실패 원인 추적에 쓴다. 코덱은 검사를 통과하면 항상 AAC라 두지 않는다.
-- 기존 행은 모두 NULL이 되어 CHECK를 만족하므로 적용 전 확인이 필요 없다.

ALTER TABLE practice_sessions
  ADD COLUMN recording_bytes       INT      AFTER recording_url,                  -- 원본 파일 바이트 수
  ADD COLUMN recording_duration_ms INT      AFTER recording_bytes,                -- ffprobe 길이(ms, 반올림)
  ADD COLUMN recording_sample_rate INT      AFTER recording_duration_ms,          -- 원본 샘플레이트(Hz)
  ADD COLUMN recording_channels    SMALLINT AFTER recording_sample_rate,          -- 원본 채널 수
  ADD CONSTRAINT chk_practice_sessions_recording_meta CHECK (
    (recording_bytes IS NULL AND recording_duration_ms IS NULL AND recording_sample_rate IS NULL AND recording_channels IS NULL)
    OR (recording_bytes IS NOT NULL AND recording_duration_ms IS NOT NULL AND recording_sample_rate IS NOT NULL AND recording_channels IS NOT NULL
        AND recording_bytes > 0 AND recording_duration_ms > 0 AND recording_sample_rate > 0 AND recording_channels > 0)
  );                                                                              -- CHECK는 NULL(알 수 없음)을 통과시키므로 IS NOT NULL을 따로 적는다
