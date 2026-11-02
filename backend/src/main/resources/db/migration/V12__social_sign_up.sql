-- S15P31E102-356: 비밀번호·확인 이메일 없는 소셜 전용 회원과 가입 증명.
ALTER TABLE users DROP CHECK users_chk_2,
 ADD CONSTRAINT chk_users_auth_fields CHECK (deleted_at IS NOT NULL OR
   (nickname IS NOT NULL AND (password_hash IS NULL OR email IS NOT NULL)));
ALTER TABLE oauth_proofs ADD COLUMN social_account_id BIGINT NULL,
 DROP CHECK chk_oauth_proofs_kind, DROP CHECK chk_oauth_proofs_context, DROP CHECK chk_oauth_proofs_purpose,
 ADD CONSTRAINT chk_oauth_proofs_kind CHECK (kind IN ('STATE','MERGE','SIGNUP','WITHDRAW')),
 ADD CONSTRAINT chk_oauth_proofs_social_account CHECK (
   (purpose='WITHDRAW' AND social_account_id IS NOT NULL AND social_account_id>0)
   OR (purpose<>'WITHDRAW' AND social_account_id IS NULL)),
 ADD CONSTRAINT chk_oauth_proofs_purpose CHECK (purpose IN ('LOGIN','LINK','WITHDRAW')),
 ADD CONSTRAINT chk_oauth_proofs_context CHECK (
    (kind='STATE' AND redirect_uri IS NOT NULL AND provider_subject IS NULL AND email IS NULL
      AND ((purpose='LOGIN' AND user_id IS NULL) OR (purpose IN ('LINK','WITHDRAW') AND user_id IS NOT NULL)))
    OR (kind='MERGE' AND purpose='LOGIN' AND user_id IS NOT NULL AND provider_subject IS NOT NULL AND email IS NOT NULL)
    OR (kind='SIGNUP' AND purpose='LOGIN' AND user_id IS NULL AND redirect_uri IS NULL AND provider_subject IS NOT NULL)
    OR (kind='WITHDRAW' AND purpose='WITHDRAW' AND user_id IS NOT NULL AND redirect_uri IS NULL AND provider_subject IS NOT NULL AND email IS NULL)
 );
