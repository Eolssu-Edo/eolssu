-- Sign in with Apple(S15P31E102-446). APPLE 제공자를 허용하고, 연결 해제·탈퇴 때 해지할 Apple refresh token을 봉인해 둔다.
-- provider_token은 서버가 AES-GCM으로 봉인한 값이며 원문이 아니다. Apple이 아닌 제공자는 NULL이다.
ALTER TABLE user_social_accounts
  DROP CHECK chk_user_social_accounts_provider,
  ADD CONSTRAINT chk_user_social_accounts_provider CHECK (provider IN ('KAKAO','GOOGLE','NAVER','APPLE')),
  ADD COLUMN provider_token VARCHAR(2048) NULL;

-- 소셜 가입·계정 연결 티켓(10분)이 연결을 만들 때 같은 토큰을 넘긴다.
ALTER TABLE oauth_proofs
  DROP CHECK chk_oauth_proofs_provider,
  ADD CONSTRAINT chk_oauth_proofs_provider CHECK (provider IN ('KAKAO','GOOGLE','NAVER','APPLE')),
  ADD COLUMN provider_token VARCHAR(2048) NULL;
