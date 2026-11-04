-- 로그인 세션 ID(S15P31E102-466). 로그인·가입마다 새로 정하고 refresh 회전은 이전 값을 이어받는다.
-- access_token의 sid 클레임과 비교해 로그아웃·비밀번호 변경·재사용 탐지 뒤의 access_token을 바로 거절한다.
-- 기존 행은 행마다 다른 값으로 채운다. 이 행들로 발급된 access_token에는 sid가 없어 만료까지 지금처럼 통과한다.
-- 배포 도구는 교체 직전에 적용하고 교체가 실패하면 JAR만 되돌리므로, 이전 JAR이 session_id 없이 넣는 행을 받도록 NULL을 허용한다.
-- 그런 행을 재발급하면 새 세션을 만든다. NOT NULL은 이 릴리스가 자리 잡은 뒤 별도 마이그레이션으로 건다.
ALTER TABLE refresh_tokens
  ADD COLUMN session_id VARCHAR(64) COLLATE ascii_bin NULL AFTER token_hash,
  ADD INDEX idx_refresh_tokens_session (session_id, revoked_at);
UPDATE refresh_tokens SET session_id = CONCAT('legacy-', id) WHERE session_id IS NULL;
