-- 얼쑤 운영 DB 스키마 (MySQL 8.4, DB: eolssu). 정본. ERDCloud 그림과 어긋나면 이 파일을 믿는다.
-- ERD: https://www.erdcloud.com/d/aB9vM5edsqHqmedcE (설명: docs/contracts/db-schema.md)
-- 집계 테이블은 data/mart/schema.sql (DB: eolssu_mart)
--
-- 규칙
-- * 시각은 UTC로 저장한다(서버 default-time-zone=+00:00). 화면에서 한국 시각으로 바꿔 보여 준다.
-- * 외래키는 반드시 테이블 끝의 FOREIGN KEY 절로 쓴다. 열 옆에 REFERENCES만 적으면 MySQL은 조용히 무시한다.
-- * 기호·한자 비교가 필요한 열은 utf8mb4_bin. 기본 정렬 규칙은 전각 '－'와 반각 '-'를 같은 글자로 본다.
-- * 상태 열은 ENUM 대신 VARCHAR + CHECK. 값이 바뀌어도 제약만 고치면 된다.

-- ---------- 기준 데이터 ----------
CREATE TABLE pitches (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  yul       VARCHAR(10) NOT NULL,                                   -- 황 대 태 협 고 중 유 임 이 남 무 응
  octave    SMALLINT    NOT NULL,                                   -- -1 배, 0 중, 1 청, 2 중청
  hanja     VARCHAR(5)  COLLATE utf8mb4_bin NOT NULL,               -- 淋 등
  hangul    VARCHAR(10) NOT NULL,                                   -- 청임 등
  semitone  SMALLINT    NOT NULL,                                   -- 황(0)부터 반음 번호. 주파수 계산·음역 비교용
  UNIQUE (semitone),
  UNIQUE (yul, octave),
  CHECK (octave BETWEEN -2 AND 2)
);

CREATE TABLE instruments (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  code                 VARCHAR(20) NOT NULL,                        -- DAEGEUM / DANSO
  name                 VARCHAR(50) NOT NULL,
  range_low_pitch_id   BIGINT      NOT NULL,                        -- 음역 하한
  range_high_pitch_id  BIGINT      NOT NULL,                        -- 음역 상한
  pitch_tolerance_cent SMALLINT    NOT NULL,
  is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
  created_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE (code),
  CHECK (pitch_tolerance_cent BETWEEN 0 AND 100),
  FOREIGN KEY (range_low_pitch_id)  REFERENCES pitches(id),
  FOREIGN KEY (range_high_pitch_id) REFERENCES pitches(id)
);

CREATE TABLE fingering_charts (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  instrument_id BIGINT       NOT NULL,
  pitch_id      BIGINT       NOT NULL,
  image_url     VARCHAR(512) NOT NULL,
  description   VARCHAR(255),                                       -- 취법 등
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE (instrument_id, pitch_id),
  FOREIGN KEY (instrument_id) REFERENCES instruments(id),
  FOREIGN KEY (pitch_id)      REFERENCES pitches(id)
);

CREATE TABLE jangdans (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(30)  NOT NULL,                                -- 세마치, 굿거리 ...
  pattern     JSON         NOT NULL,                                -- {beats:[소박별 구음], group:[대박 묶음]}. 소박 수는 beats 길이
  default_bpm SMALLINT     NOT NULL,
  source      VARCHAR(255),
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE (name),
  CHECK (default_bpm BETWEEN 30 AND 400)
);

CREATE TABLE songs (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  instrument_id BIGINT       NOT NULL,
  jangdan_id    BIGINT       NOT NULL,
  title         VARCHAR(100) NOT NULL,
  difficulty    SMALLINT     NOT NULL,
  default_bpm   SMALLINT     NOT NULL,
  score_json    JSON         NOT NULL,                              -- 각·정간·음표 순번·율명·옥타브·길이
  umulsai_text  TEXT,                                               -- 우물사이 평문 원문
  note_count    INT          NOT NULL,
  source        VARCHAR(255),
  is_scorable   BOOLEAN      NOT NULL DEFAULT FALSE,                -- 채점 가능(검증 통과)
  status        VARCHAR(20)  NOT NULL,
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at    DATETIME(6),
  CHECK (difficulty BETWEEN 1 AND 5),
  CHECK (default_bpm BETWEEN 30 AND 400),
  CHECK (note_count >= 0),
  CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN')),
  FOREIGN KEY (instrument_id) REFERENCES instruments(id),
  FOREIGN KEY (jangdan_id)    REFERENCES jangdans(id)
);

CREATE TABLE notation_symbols (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol      VARCHAR(10)  COLLATE utf8mb4_bin NOT NULL,            -- △ － ㄱ ...
  name        VARCHAR(50)  NOT NULL,
  description VARCHAR(500) NOT NULL,
  image_url   VARCHAR(512),
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE (symbol)
);

-- ---------- 사용자 ----------
CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  email         VARCHAR(320),                                       -- 소문자 정규화. 탈퇴 시 NULL
  password_hash VARCHAR(255),                                       -- 탈퇴 시 NULL
  nickname      VARCHAR(60),                                        -- 탈퇴 시 NULL
  instrument_id BIGINT       NOT NULL,                              -- 선호 악기
  notation_mode VARCHAR(20)  NOT NULL DEFAULT 'HANGUL',
  last_login_at DATETIME(6),
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at    DATETIME(6),                                        -- 탈퇴 시각. 탈퇴 시 email·password_hash·nickname을 비움(익명화). 재가입은 새 행
  UNIQUE (email),                                                   -- NULL은 여러 개 허용되므로 탈퇴자 이메일이 재가입을 막지 않음
  CHECK (notation_mode IN ('HANJA', 'HANGUL', 'SOLFEGE')),
  CHECK (deleted_at IS NOT NULL OR (email IS NOT NULL AND password_hash IS NOT NULL AND nickname IS NOT NULL)),
  FOREIGN KEY (instrument_id) REFERENCES instruments(id)
);

-- ---------- 연습·평가 (데이터 파이프라인이 읽는 테이블. 추가만 하고 수정하지 않는다. status만 바뀐다) ----------
CREATE TABLE practice_sessions (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id        BIGINT       NOT NULL,
  song_id        BIGINT       NOT NULL,                             -- 악기는 곡에서 따라온다
  bpm            SMALLINT     NOT NULL,
  start_note_idx INT          NOT NULL DEFAULT 0,
  end_note_idx   INT          NOT NULL,
  recording_url  VARCHAR(512),                                      -- 보관 30일은 created_at 기준으로 삭제 배치가 계산
  status         VARCHAR(20)  NOT NULL DEFAULT 'RECORDING',
  fail_reason    VARCHAR(100),                                      -- FAILED일 때 원인(NO_SOUND, TOO_SHORT, SERVER_ERROR 등)
  started_at     DATETIME(6)  NOT NULL,
  ended_at       DATETIME(6),
  created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CHECK (bpm BETWEEN 30 AND 400),
  CHECK (start_note_idx >= 0),
  CHECK (end_note_idx >= start_note_idx),
  CHECK (status IN ('RECORDING', 'PENDING', 'DONE', 'FAILED')),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (song_id) REFERENCES songs(id),
  INDEX idx_sessions_created (created_at),                          -- Grafana 시간 범위 조회·일 배치
  INDEX idx_sessions_user_started (user_id, started_at)             -- 내 연습 기록 목록
);

CREATE TABLE evaluations (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id     BIGINT      NOT NULL,                              -- 세션당 평가 1개
  total_score    SMALLINT    NOT NULL,
  pitch_score    SMALLINT    NOT NULL,
  timing_score   SMALLINT    NOT NULL,
  duration_score SMALLINT    NOT NULL,
  model_version  VARCHAR(30) NOT NULL,
  evaluated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 채점 완료 시각(행 생성 시각과 같아 created_at 없음)
  UNIQUE (session_id),
  CHECK (total_score    BETWEEN 0 AND 100),
  CHECK (pitch_score    BETWEEN 0 AND 100),
  CHECK (timing_score   BETWEEN 0 AND 100),
  CHECK (duration_score BETWEEN 0 AND 100),
  FOREIGN KEY (session_id) REFERENCES practice_sessions(id),
  INDEX idx_evaluations_evaluated (evaluated_at)                    -- 일 배치
);

CREATE TABLE note_results (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  evaluation_id     BIGINT      NOT NULL,
  note_idx          INT         NOT NULL,                           -- songs.score_json 기준 순번
  expected_pitch_id BIGINT      NOT NULL,                           -- 정답 음. songs JSON 안 열고 집계하려고 복사
  detected_pitch_id BIGINT,                                         -- 인식 음. 못 잡으면 NULL
  pitch_error_cent  SMALLINT,
  onset_error_ms    INT,
  duration_error_ms INT,
  verdict           VARCHAR(20) NOT NULL,
  UNIQUE (evaluation_id, note_idx),                                 -- 채점 재실행 시 중복 방지
  CHECK (note_idx >= 0),
  CHECK (verdict IN ('CORRECT', 'PITCH', 'TIMING', 'MISSED')),
  FOREIGN KEY (evaluation_id)     REFERENCES evaluations(id),
  FOREIGN KEY (expected_pitch_id) REFERENCES pitches(id),
  FOREIGN KEY (detected_pitch_id) REFERENCES pitches(id)
);

-- ---------- 학습 ----------
CREATE TABLE lessons (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  kind          VARCHAR(20)  NOT NULL,
  instrument_id BIGINT,                                             -- 공통이면 NULL
  seq           SMALLINT     NOT NULL,
  title         VARCHAR(100) NOT NULL,
  content_json  JSON         NOT NULL,
  status        VARCHAR(20)  NOT NULL,
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_lessons_kind_instrument_seq (kind, (COALESCE(instrument_id, 0)), seq),               -- NULL끼리는 중복으로 보지 않으므로 공통 레슨은 0으로 바꿔 비교
  CHECK (kind IN ('YUL', 'STEP', 'SIGIMSAE')),
  CHECK (status IN ('DRAFT', 'PUBLISHED')),
  FOREIGN KEY (instrument_id) REFERENCES instruments(id)
);

CREATE TABLE lesson_progress (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT      NOT NULL,
  lesson_id    BIGINT      NOT NULL,
  score        SMALLINT,
  completed_at DATETIME(6),
  created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  UNIQUE (user_id, lesson_id),
  CHECK (score BETWEEN 0 AND 100),
  FOREIGN KEY (user_id)   REFERENCES users(id),
  FOREIGN KEY (lesson_id) REFERENCES lessons(id)
);
