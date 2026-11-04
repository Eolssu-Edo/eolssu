-- 미션 기능은 팀 합의로 구현하지 않는다(S15P31E102-465). 값이 늘 NULL이고 외래키·인덱스가 없는 칸을 지운다.
-- 이전 백엔드는 이 칸을 조회하므로, 운영에는 이 칸을 읽지 않는 백엔드를 배포한 뒤 적용한다.
ALTER TABLE practice_sessions DROP COLUMN mission_id;
