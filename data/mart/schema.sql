-- 얼쑤 집계 테이블 (MySQL 8.4, DB: eolssu_mart). 일 배치가 만들고 Metabase가 읽는다.
-- 운영 스키마는 backend/src/main/resources/db/migration/V1__initial_schema.sql (DB: eolssu)
--
-- * 외래키 없음: 곡·사용자가 지워져도 지난 통계는 남긴다.
-- * 배치는 날짜 하나를 받아 DELETE ... WHERE stat_date = :d 후 INSERT ... SELECT 로 다시 쓴다. 몇 번 돌려도 같다.
-- * stat_date는 한국 날짜(KST) 기준. 운영 DB의 UTC 시각을 +9시간 해서 날짜를 자른다.
-- 적용: batch 계정으로 eolssu_mart DB에 실행

CREATE TABLE IF NOT EXISTS mart_daily_practice (
  stat_date       DATE         NOT NULL,
  instrument_id   BIGINT       NOT NULL,
  session_count   INT          NOT NULL,
  user_count      INT          NOT NULL,
  avg_total_score DECIMAL(5,2),
  failed_count    INT          NOT NULL,
  PRIMARY KEY (stat_date, instrument_id)
);

CREATE TABLE IF NOT EXISTS mart_song_accuracy (
  stat_date          DATE         NOT NULL,
  song_id            BIGINT       NOT NULL,
  session_count      INT          NOT NULL,
  avg_total_score    DECIMAL(5,2),
  avg_pitch_score    DECIMAL(5,2),
  avg_timing_score   DECIMAL(5,2),
  avg_duration_score DECIMAL(5,2),
  PRIMARY KEY (stat_date, song_id)
);

CREATE TABLE IF NOT EXISTS mart_pitch_error (
  stat_date     DATE   NOT NULL,
  instrument_id BIGINT NOT NULL,
  pitch_id      BIGINT NOT NULL,
  attempts      INT    NOT NULL,
  wrong_count   INT    NOT NULL,
  PRIMARY KEY (stat_date, instrument_id, pitch_id)
);
