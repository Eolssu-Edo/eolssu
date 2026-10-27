-- 분석 시도 정보와 실패 코드 제약 (S15P31E102-173, PRA-03 분석 실패 사유). 규칙은 V1 머리말을 따른다.
-- 시간 초과(TIMEOUT)와 늦게 도착한 결과 무시에 쓴다(docs/specs/practice-analysis-failure.md).
-- V3(녹음 메타데이터) 다음 번호다.

-- ---------- 적용 전 확인 ----------
-- MySQL DDL은 되돌려지지 않는다. 기존 행이 아래 제약을 만족하는지 DDL 전에 확인한다. 실패하면 아무것도 바뀌지 않는다.
-- 실패 시: 해당 행을 고치고 flyway_schema_history의 실패 행(version 4, success 0)을 지운 뒤 다시 적용한다(backend README).
CREATE TEMPORARY TABLE v4_preconditions (
  item VARCHAR(40) NOT NULL,
  ok   BOOLEAN     NOT NULL,
  CONSTRAINT chk_v4_preconditions CHECK (ok = TRUE)
);
INSERT INTO v4_preconditions (item, ok)
SELECT 'practice_sessions.fail_reason',
       (status = 'FAILED') = (fail_reason IS NOT NULL)
       AND (fail_reason IS NULL OR fail_reason COLLATE utf8mb4_bin IN ('NO_SOUND', 'TOO_SHORT', 'DECODE_ERROR', 'UPLOAD_TIMEOUT', 'TIMEOUT', 'SERVER_ERROR'))
FROM practice_sessions;
DROP TEMPORARY TABLE v4_preconditions;

-- ---------- 연습 기록 ----------
-- 분석 시도는 세션마다 현재 시도 하나만 둔다. 0은 아직 분석을 시작하지 않은 세션이다.
-- 시도를 시작(업로드 직후·다시 시도)할 때 번호를 1 올리고 시작 시각을 함께 바꾼다. 결과는 같은 번호일 때만 저장한다.
-- 실패 코드는 대소문자까지 같아야 한다(utf8mb4_bin 비교). 앱은 모르는 코드를 SERVER_ERROR로 보여 준다.
-- 실패 코드: NO_SOUND·TOO_SHORT·DECODE_ERROR는 다시 연주, TIMEOUT·SERVER_ERROR는 분석 다시 시도, UPLOAD_TIMEOUT은 업로드 만료(화면에 보이지 않음).
ALTER TABLE practice_sessions
  ADD COLUMN analysis_attempt    INT         NOT NULL DEFAULT 0 AFTER fail_reason, -- 현재 분석 시도 번호
  ADD COLUMN analysis_started_at DATETIME(6) AFTER analysis_attempt,                -- 현재 시도 시작 시각. 시간 초과 기준
  ADD CONSTRAINT chk_practice_sessions_analysis_attempt CHECK (analysis_attempt >= 0),
  ADD CONSTRAINT chk_practice_sessions_analysis_started CHECK ((analysis_attempt = 0) = (analysis_started_at IS NULL)),
  ADD CONSTRAINT chk_practice_sessions_fail_reason CHECK (
    (status = 'FAILED') = (fail_reason IS NOT NULL)
    AND (fail_reason IS NULL OR fail_reason COLLATE utf8mb4_bin IN ('NO_SOUND', 'TOO_SHORT', 'DECODE_ERROR', 'UPLOAD_TIMEOUT', 'TIMEOUT', 'SERVER_ERROR'))
  );
