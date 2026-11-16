-- S15P31E102-938. 비행기 단소 132·소금 133·산조대금 134의 곡 설명에서 CAT 음원 고지를 뺀다.
-- 고지는 앱의 '자료 및 데이터 출처' 화면과 음원 파일 메타데이터에 남는다.
-- 56 판본 게시기(publish_airplane_revision.py)는 세 곡의 설명까지 게시 전과 같은지 비교하므로,
-- 56이 v2로 게시된 뒤에만 바꾼다. 조건이 하나라도 다르면 CHECK 위반으로 멈추고 아무 행도 바꾸지 않는다.
-- 이미 적용된 경우(세 곡 모두 새 설명) 재실행은 변경 없이 끝난다.
SET @old_description = '세 음으로 연주하는 입문곡이에요.\n이 2차 저작물은 서울예술과학센터에서 개발한 국악 가상악기 음원 라이브러리를 사용하였습니다. (서울예술과학센터: catsnu.com, 문의: catantsnu@gmail.com)';
SET @new_description = '세 음으로 연주하는 입문곡이에요.';

CREATE TEMPORARY TABLE airplane_description_preconditions (
    ok INT NOT NULL, CONSTRAINT chk_airplane_description_preconditions CHECK (ok = 1)
);

START TRANSACTION;
SELECT id FROM songs WHERE id IN (56, 132, 133, 134) ORDER BY id FOR UPDATE;
-- 56 판본 게시가 끝났는지 확인한다.
INSERT INTO airplane_description_preconditions
SELECT IF(COUNT(*) = 1, 1, 0) FROM songs WHERE id = 56 AND score_version = 2 AND deleted_at IS NULL;
-- 세 곡이 모두 비행기이고, 설명이 옛 문구 또는 이미 적용된 새 문구다.
INSERT INTO airplane_description_preconditions
SELECT IF(COUNT(*) = 3, 1, 0) FROM songs
WHERE id IN (132, 133, 134) AND title = '비행기' AND deleted_at IS NULL
  AND BINARY description IN (BINARY @old_description, BINARY @new_description);
UPDATE songs SET description = @new_description
WHERE id IN (132, 133, 134) AND BINARY description = BINARY @old_description;
COMMIT;

DROP TEMPORARY TABLE airplane_description_preconditions;

-- 확인: 세 행 모두 new_ok = 1이어야 한다.
SELECT id, instrument_id, score_version, BINARY description = BINARY @new_description AS new_ok
FROM songs WHERE id IN (132, 133, 134) ORDER BY id;
