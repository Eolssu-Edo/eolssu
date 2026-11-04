-- 로그인 세션 ID(S15P31E102-466). 로그인·가입마다 새로 정하고 refresh 회전은 이전 값을 이어받는다.
-- access_token의 sid 클레임과 비교해 로그아웃·비밀번호 변경·재사용 탐지 뒤의 access_token을 바로 거절한다.
-- 기존 행은 행마다 다른 값으로 채운다. 이 행들로 발급된 access_token에는 sid가 없어 만료까지 지금처럼 통과한다.
ALTER TABLE refresh_tokens ADD COLUMN session_id VARCHAR(64) COLLATE ascii_bin NULL AFTER token_hash;
UPDATE refresh_tokens SET session_id = CONCAT('legacy-', id) WHERE session_id IS NULL;
ALTER TABLE refresh_tokens
  MODIFY COLUMN session_id VARCHAR(64) COLLATE ascii_bin NOT NULL,
  ADD INDEX idx_refresh_tokens_session (session_id, revoked_at);
