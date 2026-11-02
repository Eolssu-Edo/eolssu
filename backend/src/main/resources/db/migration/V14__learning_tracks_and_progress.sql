-- 기초부터 배우기 학습 단계와 진도 (S15P31E102-344, LRN-01·02·03). 규칙은 V1 머리말을 따른다.
-- 계약: docs/specs/learning-progress-apis.md. 단계는 악기 테이블이 아니라 앱의 학습 트랙(GuideInstrument) 4개에 묶는다.
-- V13(기본음·시김새 카탈로그) 다음 번호다. 통합 시 중복 V13을 V14로 옮겼다(S15P31E102-368).

-- ---------- 적용 전 확인 ----------
-- MySQL DDL은 되돌려지지 않는다. 두 테이블은 쓰는 API가 없어 비어 있어야 하며, 행이 있으면 DDL 전에 멈춘다.
-- 아래에서 지우는 V1 자동 이름(외래 키·CHECK·유일 인덱스)이 다른 DB도 DDL 전에 멈춘다.
-- 여기서 실패하면 원인을 정리하고 flyway_schema_history의 실패 행(version 14, success 0)을 지운 뒤 다시 적용한다(backend README).
CREATE TEMPORARY TABLE v14_preconditions (
  item VARCHAR(40) NOT NULL,
  ok   BOOLEAN     NOT NULL,
  CONSTRAINT chk_v14_preconditions CHECK (ok = TRUE)
);
INSERT INTO v14_preconditions (item, ok) SELECT 'lessons.empty', NOT EXISTS (SELECT 1 FROM lessons);
INSERT INTO v14_preconditions (item, ok) SELECT 'lesson_progress.empty', NOT EXISTS (SELECT 1 FROM lesson_progress);
INSERT INTO v14_preconditions (item, ok) SELECT 'lessons_ibfk_1', EXISTS (
  SELECT 1 FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE() AND table_name = 'lessons' AND constraint_name = 'lessons_ibfk_1');
INSERT INTO v14_preconditions (item, ok) SELECT 'lessons_chk_1', EXISTS (
  SELECT 1 FROM information_schema.check_constraints
  WHERE constraint_schema = DATABASE() AND constraint_name = 'lessons_chk_1' AND check_clause LIKE '%kind%');
INSERT INTO v14_preconditions (item, ok) SELECT 'lesson_progress_chk_1', EXISTS (
  SELECT 1 FROM information_schema.check_constraints
  WHERE constraint_schema = DATABASE() AND constraint_name = 'lesson_progress_chk_1' AND check_clause LIKE '%score%');
INSERT INTO v14_preconditions (item, ok) SELECT 'lesson_progress.user_id', EXISTS (
  SELECT 1 FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'lesson_progress' AND index_name = 'user_id' AND non_unique = 0);
DROP TEMPORARY TABLE v14_preconditions;

-- ---------- 학습 단계 ----------
-- V1의 kind(문항 종류)·instrument_id를 학습 트랙 코드로 바꾼다. 자동 이름 외래 키·CHECK와 함수형 유일 키를 먼저 지운다.
-- content_json은 LRN-02 단계 상세(goal·why·tasks·passCriteria·pitchNames·pitchNote·parts·ranges·songs·source)의 트랙별 완성본이다.
ALTER TABLE lessons DROP FOREIGN KEY lessons_ibfk_1;
ALTER TABLE lessons DROP INDEX uq_lessons_kind_instrument_seq;
ALTER TABLE lessons DROP CHECK lessons_chk_1;
ALTER TABLE lessons
  DROP COLUMN kind,
  DROP COLUMN instrument_id,
  ADD COLUMN track       VARCHAR(30) COLLATE ascii_bin NOT NULL AFTER id,       -- DANSO·SOGEUM·DAEGEUM_JEONGAK·DAEGEUM_SANJO
  ADD COLUMN judge_type  VARCHAR(10) COLLATE ascii_bin NOT NULL AFTER title,    -- 통과 확인 방식 안내. 서버는 판정하지 않는다
  ADD COLUMN is_optional BOOLEAN     NOT NULL DEFAULT FALSE AFTER judge_type,   -- 진행률(필수 단계 수)에 넣지 않는 선택 단계
  ADD CONSTRAINT chk_lessons_track CHECK (track IN ('DANSO', 'SOGEUM', 'DAEGEUM_JEONGAK', 'DAEGEUM_SANJO')),
  ADD CONSTRAINT chk_lessons_judge_type CHECK (judge_type IN ('READ', 'PITCH', 'BEAT', 'LISTEN')),
  ADD CONSTRAINT chk_lessons_seq CHECK (seq >= 1),
  ADD CONSTRAINT uq_lessons_track_seq UNIQUE (track, seq);                      -- LRN-01: 트랙의 단계를 순서대로 읽는다

-- ---------- 학습 진도 ----------
-- 행이 있으면 완료이고 해제하면 행을 지운다. 점수는 받지 않는다(사용자 완료 표시만 저장).
ALTER TABLE lesson_progress DROP CHECK lesson_progress_chk_1;
ALTER TABLE lesson_progress
  DROP COLUMN score,
  MODIFY COLUMN completed_at DATETIME(6) NOT NULL,                              -- 처음 완료 표시 시각. 다시 표시해도 바꾸지 않는다
  RENAME INDEX user_id TO uq_lesson_progress_user_lesson;                       -- 회원+단계 한 행. LRN-01에서 본인 표시를 붙인다

-- ---------- 단계 적재 ----------
-- 앱 frontend/shared/.../feature/guide/learn/LearnCurriculum.kt(develop cfad44c3)의 LearnStages·learnContent·LearnSongs를 옮겼다.
-- id는 V5·V6처럼 고정한다. 트랙 순서(단소·소금·정악대금·산조대금)대로 단계 1~9가 이어진다.
-- 2단계 '악기 잡기' 설명은 트랙별 잡는 법, pitchNames는 3단계 첫 소리·4단계 익힐 순서·5·8단계 기본 5음(낮은 음부터)이다.
-- 문구를 고칠 때는 이 파일이 아니라 새 마이그레이션에서 바꾼다. 같은 값이 여러 행에 있으니 함께 바꾼다(스펙 참고).
INSERT INTO lessons (id, track, seq, title, judge_type, is_optional, content_json, status) VALUES
  (1, 'DANSO', 1, '악기 알기·감상', 'READ', FALSE,
   JSON_OBJECT('goal', '소리와 악기 구조를 안다', 'why', '지도서는 악기를 보고 모범 연주를 먼저 듣는 범주 학습을 가장 앞에 둬요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '악기 구조와 이름 보기', 'description', '취구·지공 같은 부분 이름을 알아 둬요.'), JSON_OBJECT('title', '모범 연주 듣기', 'description', '이 악기로 연주한 소리를 먼저 들어요.'), JSON_OBJECT('title', '이 악기 소리의 특징 느끼기', 'description', '높낮이·음색을 귀로 익혀요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY('취구(U자 홈)', '뒷구멍 1개 · 왼손 엄지', '앞 지공 4개'), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 기본원칙 ①·④, 과정안 기초단계 1·2항. 모든 강좌의 1차시'),
   'PUBLISHED'),
  (2, 'DANSO', 2, '자세·호흡', 'READ', FALSE,
   JSON_OBJECT('goal', '바르게 잡고 숨을 고르게 쓴다', 'why', '지도서 연주법 장의 순서(자세 → 안공법 → 발음법 → 호흡법)를 따라요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앉은 자세', 'description', '허리를 펴고 어깨 힘을 빼요.'), JSON_OBJECT('title', '악기 잡기', 'description', '세로로 세워 잡아요. 뒷구멍은 왼손 엄지로, 앞 지공은 왼손 검지·중지와 오른손 검지·중지로 막아요.'), JSON_OBJECT('title', '복식호흡', 'description', '배로 숨을 들이쉬고 길고 고르게 내쉬어요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 연주법 장. 소금 교실 2·3차시, 대금 기초편 8차시, 단소 교실 2차시'),
   'PUBLISHED'),
  (3, 'DANSO', 3, '첫 소리', 'PITCH', FALSE,
   JSON_OBJECT('goal', '안정된 소리 하나를 낸다', 'why', '첫 소리는 음높이보다 소리가 끊기지 않고 고르게 나는지가 중요해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '취구에 입술 대기', 'description', '아랫입술로 취구를 반쯤 덮어요.'), JSON_OBJECT('title', '입김 방향·세기 찾기', 'description', '음료수병을 불듯 가늘고 곧게 불어요(대금 기초편 7차시).'), JSON_OBJECT('title', '한 소리 길게 내기', 'description', '같은 소리를 끊지 않고 이어 봐요.')), 'passCriteria', JSON_OBJECT('title', '한 소리를 2초 이상 끊김 없이 3번', 'description', '음높이는 묻지 않고 소리가 났는지·흔들림만 봐요.'), 'pitchNames', JSON_ARRAY('汰'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 1·2항, 대금 기초편 7차시'),
   'PUBLISHED'),
  (4, 'DANSO', 4, '기본음', 'PITCH', FALSE,
   JSON_OBJECT('goal', '쉬운 음부터 율명과 운지를 익힌다', 'why', '소리 내기 쉬운 음부터 배워요. 율명 구음을 소리 내어 읽으며 함께 익혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '음마다 2초 이상 유지', 'description', '목표 음과 ±25센트 이내면 통과(예시).'), 'pitchNames', JSON_ARRAY('汰', '潢', '無', '林', '仲'), 'pitchNote', JSON_OBJECT('text', '구멍을 다 연 汰(태)부터 하나씩 막으며 내려가요. 仲·林 같은 낮은 음은 어려워서 뒤에 해요. 반음 夾·南은 다섯 음이 익은 뒤에 배워요.', 'caution', '정일영(2014)은 汰와 같은 입김으로 나는 가운데 소리를 먼저 익히자고 하고, 단소 교실은 낮은 소리 → 높은 소리 순이라 자료마다 첫 음이 달라요.'), 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항, 소금 교실 5~7차시, 대금 교실 2~4차시'),
   'PUBLISHED'),
  (5, 'DANSO', 5, '음 잇기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '이웃 음과 건너뛴 음을 이어 분다', 'why', '기본음이 되면 순차 진행 → 건너뛰기 → 세 음 동요 순서로 넓혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '패턴의 8음 중 7음 이상 맞음', 'description', '음 사이 끊김은 박으로 세지 않아요(예시).'), 'pitchNames', JSON_ARRAY('仲', '林', '無', '潢', '汰'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항·과정안 발전단계, 소금 교실 5차시(3음 동요)'),
   'PUBLISHED'),
  (6, 'DANSO', 6, '음역 넓히기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '높은·낮은 음역을 낸다', 'why', '같은 운지에서 입김 세기로 옥타브를 바꾸는 연습이에요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '새 음역의 음을 각각 2초 이상 유지', 'description', '평취 음과 옥타브 차이가 나는지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(JSON_OBJECT('blow', 'HIGH', 'label', '역취 · 한 옥타브 위', 'description', '평취와 같은 운지에 입김을 세게 불어요.', 'firstPitch', '㳞')), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 4·5항, 대금 교실 5~7차시, 대금 기초편 10~13차시, 소금 교실 13차시'),
   'PUBLISHED'),
  (7, 'DANSO', 7, '혀치기·박 나누기', 'BEAT', FALSE,
   JSON_OBJECT('goal', '같은 음 반복과 정간 안 박 나누기를 한다', 'why', '같은 음을 여러 번 낼 때 혀로 끊는 혀치기와, 한 정간을 둘·셋으로 나누는 연습이에요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '혀치기', 'description', '같은 음을 "투·투·투" 혀로 끊어 4번 반복해요.'), JSON_OBJECT('title', '1정간을 2로 나누기', 'description', '한 칸 안에 두 음을 고르게 넣어요.'), JSON_OBJECT('title', '1정간을 3으로 나누기', 'description', '한 칸 안에 세 음을 고르게 넣어요.')), 'passCriteria', JSON_OBJECT('title', '혀치기 4번 반복, 박 어긋남 ±15% 이내', 'description', '2분·3분은 각 음 길이가 고른지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 6항, 소금 교실 8차시, 대금 기초편 14~18차시'),
   'PUBLISHED'),
  (8, 'DANSO', 8, '악보와 곡', 'BEAT', FALSE,
   JSON_OBJECT('goal', '정간보를 보고 장단에 맞춰 곡을 분다', 'why', '지도서는 평취로 된 쉬운 곡부터, 정간보로 먼저 가르친 뒤 오선보를 보게 해요. 장단 반주를 함께 틀어요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '정간보 읽는 순서 확인', 'description', '위에서 아래로, 오른쪽에서 왼쪽으로.'), JSON_OBJECT('title', '율명 보며 천천히 불기', 'description', '한 각씩 끊어 연습해요.'), JSON_OBJECT('title', '장단 반주에 맞춰 곡 전체 불기', 'description', '연습곡 화면에서 속도를 낮춰 시작해요.')), 'passCriteria', JSON_OBJECT('title', '곡 전체 음정·박자 각 70점 이상', 'description', 'AI 연주 평가 결과 화면의 점수를 그대로 써요(예시).'), 'pitchNames', JSON_ARRAY('仲', '林', '無', '潢', '汰'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(JSON_OBJECT('title', '비행기', 'meta', '3음 · 평취'), JSON_OBJECT('title', '새야새야', 'meta', '평취'), JSON_OBJECT('title', '아리랑', 'meta', '세마치장단'), JSON_OBJECT('title', '도라지타령', 'meta', '세마치장단'), JSON_OBJECT('title', '천안삼거리', 'meta', '굿거리장단'), JSON_OBJECT('title', '풍년가', 'meta', '굿거리장단')), 'source', '단소 지도서 과정안 통합단계·기본원칙 ⑧, 대금 교실 8~10차시, 소금 교실 9·10차시'),
   'PUBLISHED'),
  (9, 'DANSO', 9, '장식음·시김새', 'LISTEN', TRUE,
   JSON_OBJECT('goal', '꾸밈음과 요성·퇴성을 맛본다', 'why', '모든 자료가 마지막 단계로 둬요. 지도서는 잘 되지 않으면 무리하게 지도하지 않고 감상으로 소개하는 선에서 끝내도 된다고 해요. 그래서 판정 없이 듣고 따라 해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앞·뒤 꾸밈음 듣고 따라 하기', 'description', NULL), JSON_OBJECT('title', '굴리기', 'description', NULL), JSON_OBJECT('title', '요성(떠는 소리)·퇴성(흘러내리는 소리)', 'description', '모범 연주를 듣고 흉내 내 봐요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 과정안 심화단계·발음법 8항, 소금 교실 11·14차시, 대금 교실 12·13차시'),
   'PUBLISHED'),
  (10, 'SOGEUM', 1, '악기 알기·감상', 'READ', FALSE,
   JSON_OBJECT('goal', '소리와 악기 구조를 안다', 'why', '지도서는 악기를 보고 모범 연주를 먼저 듣는 범주 학습을 가장 앞에 둬요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '악기 구조와 이름 보기', 'description', '취구·지공 같은 부분 이름을 알아 둬요.'), JSON_OBJECT('title', '모범 연주 듣기', 'description', '이 악기로 연주한 소리를 먼저 들어요.'), JSON_OBJECT('title', '이 악기 소리의 특징 느끼기', 'description', '높낮이·음색을 귀로 익혀요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY('취구', '지공 6개'), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 기본원칙 ①·④, 과정안 기초단계 1·2항. 모든 강좌의 1차시'),
   'PUBLISHED'),
  (11, 'SOGEUM', 2, '자세·호흡', 'READ', FALSE,
   JSON_OBJECT('goal', '바르게 잡고 숨을 고르게 쓴다', 'why', '지도서 연주법 장의 순서(자세 → 안공법 → 발음법 → 호흡법)를 따라요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앉은 자세', 'description', '허리를 펴고 어깨 힘을 빼요.'), JSON_OBJECT('title', '악기 잡기', 'description', '가로로 오른쪽을 향해 잡아요. 왼손이 취구 쪽 세 구멍, 오른손이 아래 세 구멍을 막아요.'), JSON_OBJECT('title', '복식호흡', 'description', '배로 숨을 들이쉬고 길고 고르게 내쉬어요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 연주법 장. 소금 교실 2·3차시, 대금 기초편 8차시, 단소 교실 2차시'),
   'PUBLISHED'),
  (12, 'SOGEUM', 3, '첫 소리', 'PITCH', FALSE,
   JSON_OBJECT('goal', '안정된 소리 하나를 낸다', 'why', '첫 소리는 음높이보다 소리가 끊기지 않고 고르게 나는지가 중요해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '취구에 입술 대기', 'description', '아랫입술로 취구를 반쯤 덮어요.'), JSON_OBJECT('title', '입김 방향·세기 찾기', 'description', '음료수병을 불듯 가늘고 곧게 불어요(대금 기초편 7차시).'), JSON_OBJECT('title', '한 소리 길게 내기', 'description', '같은 소리를 끊지 않고 이어 봐요.')), 'passCriteria', JSON_OBJECT('title', '한 소리를 2초 이상 끊김 없이 3번', 'description', '음높이는 묻지 않고 소리가 났는지·흔들림만 봐요.'), 'pitchNames', JSON_ARRAY('㴌'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 1·2항, 대금 기초편 7차시'),
   'PUBLISHED'),
  (13, 'SOGEUM', 4, '기본음', 'PITCH', FALSE,
   JSON_OBJECT('goal', '쉬운 음부터 율명과 운지를 익힌다', 'why', '소리 내기 쉬운 음부터 배워요. 율명 구음을 소리 내어 읽으며 함께 익혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '음마다 2초 이상 유지', 'description', '목표 음과 ±25센트 이내면 통과(예시).'), 'pitchNames', JSON_ARRAY('㴌', '汰', '潢', '無', '南', '林', '㳞'), 'pitchNote', JSON_OBJECT('text', '고·태·황 → 무·남·임 → 중 순서예요(소금 교실 5~7차시). 역취 황은 6단계에서 해요.', 'caution', NULL), 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항, 소금 교실 5~7차시, 대금 교실 2~4차시'),
   'PUBLISHED'),
  (14, 'SOGEUM', 5, '음 잇기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '이웃 음과 건너뛴 음을 이어 분다', 'why', '기본음이 되면 순차 진행 → 건너뛰기 → 세 음 동요 순서로 넓혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '패턴의 8음 중 7음 이상 맞음', 'description', '음 사이 끊김은 박으로 세지 않아요(예시).'), 'pitchNames', JSON_ARRAY('林', '南', '潢', '汰', '㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항·과정안 발전단계, 소금 교실 5차시(3음 동요)'),
   'PUBLISHED'),
  (15, 'SOGEUM', 6, '음역 넓히기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '높은·낮은 음역을 낸다', 'why', '같은 운지에서 입김 세기로 옥타브를 바꾸는 연습이에요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '새 음역의 음을 각각 2초 이상 유지', 'description', '평취 음과 옥타브 차이가 나는지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(JSON_OBJECT('blow', 'HIGH', 'label', '역취 · 한 옥타브 위', 'description', '같은 운지에 입김을 세게 불어요. 소금은 대금보다 한 옥타브 높아요.', 'firstPitch', '淋')), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 4·5항, 대금 교실 5~7차시, 대금 기초편 10~13차시, 소금 교실 13차시'),
   'PUBLISHED'),
  (16, 'SOGEUM', 7, '혀치기·박 나누기', 'BEAT', FALSE,
   JSON_OBJECT('goal', '같은 음 반복과 정간 안 박 나누기를 한다', 'why', '같은 음을 여러 번 낼 때 혀로 끊는 혀치기와, 한 정간을 둘·셋으로 나누는 연습이에요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '혀치기', 'description', '같은 음을 "투·투·투" 혀로 끊어 4번 반복해요.'), JSON_OBJECT('title', '1정간을 2로 나누기', 'description', '한 칸 안에 두 음을 고르게 넣어요.'), JSON_OBJECT('title', '1정간을 3으로 나누기', 'description', '한 칸 안에 세 음을 고르게 넣어요.')), 'passCriteria', JSON_OBJECT('title', '혀치기 4번 반복, 박 어긋남 ±15% 이내', 'description', '2분·3분은 각 음 길이가 고른지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 6항, 소금 교실 8차시, 대금 기초편 14~18차시'),
   'PUBLISHED'),
  (17, 'SOGEUM', 8, '악보와 곡', 'BEAT', FALSE,
   JSON_OBJECT('goal', '정간보를 보고 장단에 맞춰 곡을 분다', 'why', '지도서는 평취로 된 쉬운 곡부터, 정간보로 먼저 가르친 뒤 오선보를 보게 해요. 장단 반주를 함께 틀어요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '정간보 읽는 순서 확인', 'description', '위에서 아래로, 오른쪽에서 왼쪽으로.'), JSON_OBJECT('title', '율명 보며 천천히 불기', 'description', '한 각씩 끊어 연습해요.'), JSON_OBJECT('title', '장단 반주에 맞춰 곡 전체 불기', 'description', '연습곡 화면에서 속도를 낮춰 시작해요.')), 'passCriteria', JSON_OBJECT('title', '곡 전체 음정·박자 각 70점 이상', 'description', 'AI 연주 평가 결과 화면의 점수를 그대로 써요(예시).'), 'pitchNames', JSON_ARRAY('林', '南', '潢', '汰', '㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(JSON_OBJECT('title', '비행기', 'meta', '3음 · 평취'), JSON_OBJECT('title', '새야새야', 'meta', '평취'), JSON_OBJECT('title', '아리랑', 'meta', '세마치장단'), JSON_OBJECT('title', '도라지타령', 'meta', '세마치장단'), JSON_OBJECT('title', '천안삼거리', 'meta', '굿거리장단'), JSON_OBJECT('title', '풍년가', 'meta', '굿거리장단')), 'source', '단소 지도서 과정안 통합단계·기본원칙 ⑧, 대금 교실 8~10차시, 소금 교실 9·10차시'),
   'PUBLISHED'),
  (18, 'SOGEUM', 9, '장식음·시김새', 'LISTEN', TRUE,
   JSON_OBJECT('goal', '꾸밈음과 요성·퇴성을 맛본다', 'why', '모든 자료가 마지막 단계로 둬요. 지도서는 잘 되지 않으면 무리하게 지도하지 않고 감상으로 소개하는 선에서 끝내도 된다고 해요. 그래서 판정 없이 듣고 따라 해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앞·뒤 꾸밈음 듣고 따라 하기', 'description', NULL), JSON_OBJECT('title', '굴리기', 'description', NULL), JSON_OBJECT('title', '요성(떠는 소리)·퇴성(흘러내리는 소리)', 'description', '모범 연주를 듣고 흉내 내 봐요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 과정안 심화단계·발음법 8항, 소금 교실 11·14차시, 대금 교실 12·13차시'),
   'PUBLISHED'),
  (19, 'DAEGEUM_JEONGAK', 1, '악기 알기·감상', 'READ', FALSE,
   JSON_OBJECT('goal', '소리와 악기 구조를 안다', 'why', '지도서는 악기를 보고 모범 연주를 먼저 듣는 범주 학습을 가장 앞에 둬요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '악기 구조와 이름 보기', 'description', '취구·지공 같은 부분 이름을 알아 둬요.'), JSON_OBJECT('title', '모범 연주 듣기', 'description', '이 악기로 연주한 소리를 먼저 들어요.'), JSON_OBJECT('title', '이 악기 소리의 특징 느끼기', 'description', '높낮이·음색을 귀로 익혀요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY('취구', '청공(갈대 속청)', '지공 6개', '칠성공'), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 기본원칙 ①·④, 과정안 기초단계 1·2항. 모든 강좌의 1차시'),
   'PUBLISHED'),
  (20, 'DAEGEUM_JEONGAK', 2, '자세·호흡', 'READ', FALSE,
   JSON_OBJECT('goal', '바르게 잡고 숨을 고르게 쓴다', 'why', '지도서 연주법 장의 순서(자세 → 안공법 → 발음법 → 호흡법)를 따라요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앉은 자세', 'description', '허리를 펴고 어깨 힘을 빼요.'), JSON_OBJECT('title', '악기 잡기', 'description', '가로로 오른쪽을 향해 잡아요. 왼손이 취구 쪽 세 구멍, 오른손이 아래 세 구멍을 막아요.'), JSON_OBJECT('title', '복식호흡', 'description', '배로 숨을 들이쉬고 길고 고르게 내쉬어요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 연주법 장. 소금 교실 2·3차시, 대금 기초편 8차시, 단소 교실 2차시'),
   'PUBLISHED'),
  (21, 'DAEGEUM_JEONGAK', 3, '첫 소리', 'PITCH', FALSE,
   JSON_OBJECT('goal', '안정된 소리 하나를 낸다', 'why', '첫 소리는 음높이보다 소리가 끊기지 않고 고르게 나는지가 중요해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '취구에 입술 대기', 'description', '아랫입술로 취구를 반쯤 덮어요.'), JSON_OBJECT('title', '입김 방향·세기 찾기', 'description', '음료수병을 불듯 가늘고 곧게 불어요(대금 기초편 7차시).'), JSON_OBJECT('title', '한 소리 길게 내기', 'description', '같은 소리를 끊지 않고 이어 봐요.')), 'passCriteria', JSON_OBJECT('title', '한 소리를 2초 이상 끊김 없이 3번', 'description', '음높이는 묻지 않고 소리가 났는지·흔들림만 봐요.'), 'pitchNames', JSON_ARRAY('㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 1·2항, 대금 기초편 7차시'),
   'PUBLISHED'),
  (22, 'DAEGEUM_JEONGAK', 4, '기본음', 'PITCH', FALSE,
   JSON_OBJECT('goal', '쉬운 음부터 율명과 운지를 익힌다', 'why', '소리 내기 쉬운 음부터 배워요. 율명 구음을 소리 내어 읽으며 함께 익혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '음마다 2초 이상 유지', 'description', '목표 음과 ±25센트 이내면 통과(예시).'), 'pitchNames', JSON_ARRAY('㳞', '汰', '潢', '南', '林'), 'pitchNote', JSON_OBJECT('text', '지공을 모두 연 음 → 평취 왼손 음 → 평취 오른손 음 순서예요(대금 교실 2~4차시).', 'caution', '강좌는 차시 제목만 확인해서 "모든 지공을 연 음"이 어느 율명인지는 확인이 필요해요. 대금 기초편은 중간 → 높은 → 낮은 음역 순이에요.'), 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항, 소금 교실 5~7차시, 대금 교실 2~4차시'),
   'PUBLISHED'),
  (23, 'DAEGEUM_JEONGAK', 5, '음 잇기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '이웃 음과 건너뛴 음을 이어 분다', 'why', '기본음이 되면 순차 진행 → 건너뛰기 → 세 음 동요 순서로 넓혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '패턴의 8음 중 7음 이상 맞음', 'description', '음 사이 끊김은 박으로 세지 않아요(예시).'), 'pitchNames', JSON_ARRAY('林', '南', '潢', '汰', '㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항·과정안 발전단계, 소금 교실 5차시(3음 동요)'),
   'PUBLISHED'),
  (24, 'DAEGEUM_JEONGAK', 6, '음역 넓히기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '높은·낮은 음역을 낸다', 'why', '같은 운지에서 입김 세기로 옥타브를 바꾸는 연습이에요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '새 음역의 음을 각각 2초 이상 유지', 'description', '평취 음과 옥타브 차이가 나는지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(JSON_OBJECT('blow', 'HIGH', 'label', '역취 · 높은 단', 'description', '입김을 세게 불어 한 옥타브 올려요.', 'firstPitch', '淋'), JSON_OBJECT('blow', 'LOW', 'label', '저취 · 낮은 단', 'description', '입김을 느슨하게 해 한 옥타브 내려요.', 'firstPitch', '㑣')), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 4·5항, 대금 교실 5~7차시, 대금 기초편 10~13차시, 소금 교실 13차시'),
   'PUBLISHED'),
  (25, 'DAEGEUM_JEONGAK', 7, '혀치기·박 나누기', 'BEAT', FALSE,
   JSON_OBJECT('goal', '같은 음 반복과 정간 안 박 나누기를 한다', 'why', '같은 음을 여러 번 낼 때 혀로 끊는 혀치기와, 한 정간을 둘·셋으로 나누는 연습이에요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '혀치기', 'description', '같은 음을 "투·투·투" 혀로 끊어 4번 반복해요.'), JSON_OBJECT('title', '1정간을 2로 나누기', 'description', '한 칸 안에 두 음을 고르게 넣어요.'), JSON_OBJECT('title', '1정간을 3으로 나누기', 'description', '한 칸 안에 세 음을 고르게 넣어요.')), 'passCriteria', JSON_OBJECT('title', '혀치기 4번 반복, 박 어긋남 ±15% 이내', 'description', '2분·3분은 각 음 길이가 고른지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 6항, 소금 교실 8차시, 대금 기초편 14~18차시'),
   'PUBLISHED'),
  (26, 'DAEGEUM_JEONGAK', 8, '악보와 곡', 'BEAT', FALSE,
   JSON_OBJECT('goal', '정간보를 보고 장단에 맞춰 곡을 분다', 'why', '지도서는 평취로 된 쉬운 곡부터, 정간보로 먼저 가르친 뒤 오선보를 보게 해요. 장단 반주를 함께 틀어요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '정간보 읽는 순서 확인', 'description', '위에서 아래로, 오른쪽에서 왼쪽으로.'), JSON_OBJECT('title', '율명 보며 천천히 불기', 'description', '한 각씩 끊어 연습해요.'), JSON_OBJECT('title', '장단 반주에 맞춰 곡 전체 불기', 'description', '연습곡 화면에서 속도를 낮춰 시작해요.')), 'passCriteria', JSON_OBJECT('title', '곡 전체 음정·박자 각 70점 이상', 'description', 'AI 연주 평가 결과 화면의 점수를 그대로 써요(예시).'), 'pitchNames', JSON_ARRAY('林', '南', '潢', '汰', '㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(JSON_OBJECT('title', '비행기', 'meta', '3음 · 평취'), JSON_OBJECT('title', '새야새야', 'meta', '평취'), JSON_OBJECT('title', '아리랑', 'meta', '세마치장단'), JSON_OBJECT('title', '도라지타령', 'meta', '세마치장단'), JSON_OBJECT('title', '천안삼거리', 'meta', '굿거리장단'), JSON_OBJECT('title', '풍년가', 'meta', '굿거리장단')), 'source', '단소 지도서 과정안 통합단계·기본원칙 ⑧, 대금 교실 8~10차시, 소금 교실 9·10차시'),
   'PUBLISHED'),
  (27, 'DAEGEUM_JEONGAK', 9, '장식음·시김새', 'LISTEN', TRUE,
   JSON_OBJECT('goal', '꾸밈음과 요성·퇴성을 맛본다', 'why', '모든 자료가 마지막 단계로 둬요. 지도서는 잘 되지 않으면 무리하게 지도하지 않고 감상으로 소개하는 선에서 끝내도 된다고 해요. 그래서 판정 없이 듣고 따라 해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앞·뒤 꾸밈음 듣고 따라 하기', 'description', NULL), JSON_OBJECT('title', '굴리기', 'description', NULL), JSON_OBJECT('title', '요성(떠는 소리)·퇴성(흘러내리는 소리)', 'description', '모범 연주를 듣고 흉내 내 봐요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 과정안 심화단계·발음법 8항, 소금 교실 11·14차시, 대금 교실 12·13차시'),
   'PUBLISHED'),
  (28, 'DAEGEUM_SANJO', 1, '악기 알기·감상', 'READ', FALSE,
   JSON_OBJECT('goal', '소리와 악기 구조를 안다', 'why', '지도서는 악기를 보고 모범 연주를 먼저 듣는 범주 학습을 가장 앞에 둬요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '악기 구조와 이름 보기', 'description', '취구·지공 같은 부분 이름을 알아 둬요.'), JSON_OBJECT('title', '모범 연주 듣기', 'description', '이 악기로 연주한 소리를 먼저 들어요.'), JSON_OBJECT('title', '이 악기 소리의 특징 느끼기', 'description', '높낮이·음색을 귀로 익혀요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY('취구', '청공(갈대 속청)', '지공 6개', '칠성공'), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 기본원칙 ①·④, 과정안 기초단계 1·2항. 모든 강좌의 1차시'),
   'PUBLISHED'),
  (29, 'DAEGEUM_SANJO', 2, '자세·호흡', 'READ', FALSE,
   JSON_OBJECT('goal', '바르게 잡고 숨을 고르게 쓴다', 'why', '지도서 연주법 장의 순서(자세 → 안공법 → 발음법 → 호흡법)를 따라요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앉은 자세', 'description', '허리를 펴고 어깨 힘을 빼요.'), JSON_OBJECT('title', '악기 잡기', 'description', '가로로 오른쪽을 향해 잡아요. 기본 운지틀은 정악대금과 같아요.'), JSON_OBJECT('title', '복식호흡', 'description', '배로 숨을 들이쉬고 길고 고르게 내쉬어요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 연주법 장. 소금 교실 2·3차시, 대금 기초편 8차시, 단소 교실 2차시'),
   'PUBLISHED'),
  (30, 'DAEGEUM_SANJO', 3, '첫 소리', 'PITCH', FALSE,
   JSON_OBJECT('goal', '안정된 소리 하나를 낸다', 'why', '첫 소리는 음높이보다 소리가 끊기지 않고 고르게 나는지가 중요해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '취구에 입술 대기', 'description', '아랫입술로 취구를 반쯤 덮어요.'), JSON_OBJECT('title', '입김 방향·세기 찾기', 'description', '음료수병을 불듯 가늘고 곧게 불어요(대금 기초편 7차시).'), JSON_OBJECT('title', '한 소리 길게 내기', 'description', '같은 소리를 끊지 않고 이어 봐요.')), 'passCriteria', JSON_OBJECT('title', '한 소리를 2초 이상 끊김 없이 3번', 'description', '음높이는 묻지 않고 소리가 났는지·흔들림만 봐요.'), 'pitchNames', JSON_ARRAY('㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 1·2항, 대금 기초편 7차시'),
   'PUBLISHED'),
  (31, 'DAEGEUM_SANJO', 4, '기본음', 'PITCH', FALSE,
   JSON_OBJECT('goal', '쉬운 음부터 율명과 운지를 익힌다', 'why', '소리 내기 쉬운 음부터 배워요. 율명 구음을 소리 내어 읽으며 함께 익혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '음마다 2초 이상 유지', 'description', '목표 음과 ±25센트 이내면 통과(예시).'), 'pitchNames', JSON_ARRAY('㳞', '汰', '潢', '南', '林'), 'pitchNote', JSON_OBJECT('text', '정악대금과 같은 순서로 해요. 산조대금은 황종이 F라 같은 율명이라도 소리는 정악대금보다 장2도 높아요.', 'caution', '유파마다 조율·세부 운지가 조금 다를 수 있어요.'), 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항, 소금 교실 5~7차시, 대금 교실 2~4차시'),
   'PUBLISHED'),
  (32, 'DAEGEUM_SANJO', 5, '음 잇기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '이웃 음과 건너뛴 음을 이어 분다', 'why', '기본음이 되면 순차 진행 → 건너뛰기 → 세 음 동요 순서로 넓혀요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '패턴의 8음 중 7음 이상 맞음', 'description', '음 사이 끊김은 박으로 세지 않아요(예시).'), 'pitchNames', JSON_ARRAY('林', '南', '潢', '汰', '㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 3항·과정안 발전단계, 소금 교실 5차시(3음 동요)'),
   'PUBLISHED'),
  (33, 'DAEGEUM_SANJO', 6, '음역 넓히기', 'PITCH', FALSE,
   JSON_OBJECT('goal', '높은·낮은 음역을 낸다', 'why', '같은 운지에서 입김 세기로 옥타브를 바꾸는 연습이에요.', 'tasks', JSON_ARRAY(), 'passCriteria', JSON_OBJECT('title', '새 음역의 음을 각각 2초 이상 유지', 'description', '평취 음과 옥타브 차이가 나는지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(JSON_OBJECT('blow', 'HIGH', 'label', '역취 · 높은 단', 'description', '입김을 세게 불어 한 옥타브 올려요.', 'firstPitch', '淋'), JSON_OBJECT('blow', 'LOW', 'label', '저취 · 낮은 단', 'description', '입김을 느슨하게 해 한 옥타브 내려요.', 'firstPitch', '㑣')), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 4·5항, 대금 교실 5~7차시, 대금 기초편 10~13차시, 소금 교실 13차시'),
   'PUBLISHED'),
  (34, 'DAEGEUM_SANJO', 7, '혀치기·박 나누기', 'BEAT', FALSE,
   JSON_OBJECT('goal', '같은 음 반복과 정간 안 박 나누기를 한다', 'why', '같은 음을 여러 번 낼 때 혀로 끊는 혀치기와, 한 정간을 둘·셋으로 나누는 연습이에요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '혀치기', 'description', '같은 음을 "투·투·투" 혀로 끊어 4번 반복해요.'), JSON_OBJECT('title', '1정간을 2로 나누기', 'description', '한 칸 안에 두 음을 고르게 넣어요.'), JSON_OBJECT('title', '1정간을 3으로 나누기', 'description', '한 칸 안에 세 음을 고르게 넣어요.')), 'passCriteria', JSON_OBJECT('title', '혀치기 4번 반복, 박 어긋남 ±15% 이내', 'description', '2분·3분은 각 음 길이가 고른지 봐요(예시).'), 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 발음법 6항, 소금 교실 8차시, 대금 기초편 14~18차시'),
   'PUBLISHED'),
  (35, 'DAEGEUM_SANJO', 8, '악보와 곡', 'BEAT', FALSE,
   JSON_OBJECT('goal', '정간보를 보고 장단에 맞춰 곡을 분다', 'why', '지도서는 평취로 된 쉬운 곡부터, 정간보로 먼저 가르친 뒤 오선보를 보게 해요. 장단 반주를 함께 틀어요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '정간보 읽는 순서 확인', 'description', '위에서 아래로, 오른쪽에서 왼쪽으로.'), JSON_OBJECT('title', '율명 보며 천천히 불기', 'description', '한 각씩 끊어 연습해요.'), JSON_OBJECT('title', '장단 반주에 맞춰 곡 전체 불기', 'description', '연습곡 화면에서 속도를 낮춰 시작해요.')), 'passCriteria', JSON_OBJECT('title', '곡 전체 음정·박자 각 70점 이상', 'description', 'AI 연주 평가 결과 화면의 점수를 그대로 써요(예시).'), 'pitchNames', JSON_ARRAY('林', '南', '潢', '汰', '㳞'), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(JSON_OBJECT('title', '비행기', 'meta', '3음 · 평취'), JSON_OBJECT('title', '새야새야', 'meta', '평취'), JSON_OBJECT('title', '아리랑', 'meta', '세마치장단'), JSON_OBJECT('title', '도라지타령', 'meta', '세마치장단'), JSON_OBJECT('title', '천안삼거리', 'meta', '굿거리장단'), JSON_OBJECT('title', '풍년가', 'meta', '굿거리장단')), 'source', '단소 지도서 과정안 통합단계·기본원칙 ⑧, 대금 교실 8~10차시, 소금 교실 9·10차시'),
   'PUBLISHED'),
  (36, 'DAEGEUM_SANJO', 9, '장식음·시김새', 'LISTEN', TRUE,
   JSON_OBJECT('goal', '꾸밈음과 요성·퇴성을 맛본다', 'why', '모든 자료가 마지막 단계로 둬요. 지도서는 잘 되지 않으면 무리하게 지도하지 않고 감상으로 소개하는 선에서 끝내도 된다고 해요. 그래서 판정 없이 듣고 따라 해요.', 'tasks', JSON_ARRAY(JSON_OBJECT('title', '앞·뒤 꾸밈음 듣고 따라 하기', 'description', NULL), JSON_OBJECT('title', '굴리기', 'description', NULL), JSON_OBJECT('title', '요성(떠는 소리)·퇴성(흘러내리는 소리)', 'description', '모범 연주를 듣고 흉내 내 봐요.')), 'passCriteria', NULL, 'pitchNames', JSON_ARRAY(), 'pitchNote', NULL, 'parts', JSON_ARRAY(), 'ranges', JSON_ARRAY(), 'songs', JSON_ARRAY(), 'source', '단소 지도서 과정안 심화단계·발음법 8항, 소금 교실 11·14차시, 대금 교실 12·13차시'),
   'PUBLISHED');
