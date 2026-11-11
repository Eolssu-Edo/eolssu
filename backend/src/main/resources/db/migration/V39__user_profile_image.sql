-- 마이페이지 프로필 그림(S15P31E102-738). 앱에 담긴 절쑤 그림 12종 중 하나의 키만 저장한다. 기존 회원은 '반가워요'(HELLO)로 시작한다.
ALTER TABLE users
  ADD COLUMN profile_image VARCHAR(20) NOT NULL DEFAULT 'HELLO' AFTER notation_mode,
  ADD CONSTRAINT chk_users_profile_image CHECK (profile_image IN (
    'HELLO', 'DANSO', 'STUDY', 'LOOK', 'GREAT', 'JANGDAN', 'AGAIN', 'SURPRISE', 'PROUD', 'OKAY', 'REST', 'DONE'
  ));
