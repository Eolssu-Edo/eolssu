-- schema.sql에 total_practice_seconds를 추가하기 전에 만든 eolssu_mart에 한 번만 실행한다(S15P31E102-190).
-- 지난 날짜의 행은 0으로 채워진다. 실제 값은 일 배치가 그 날짜를 다시 쓸 때 들어간다.
-- 이미 칸이 있으면 MySQL 1060(Duplicate column name)으로 멈추며 아무것도 바뀌지 않는다.
ALTER TABLE mart_daily_practice
  ADD COLUMN total_practice_seconds BIGINT NOT NULL DEFAULT 0 AFTER failed_count;
