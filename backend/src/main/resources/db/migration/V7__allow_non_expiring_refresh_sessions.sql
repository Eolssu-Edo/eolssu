-- S15P31E102-260: 명시적으로 허용한 테스트 회원의 세션만 expires_at=NULL로 발급한다.
-- 기존 토큰의 만료 시각은 보존한다. 서버 허용 목록과 활성 세션 확인 없이는 무기한 인증을 허용하지 않는다.
ALTER TABLE refresh_tokens MODIFY COLUMN expires_at DATETIME(6) NULL;
