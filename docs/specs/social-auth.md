# 간편 로그인과 계정 연결

- 작업 S15P31E102-211, [계획·확정 사항](../plans/S15P31E102-211.md). 상태: 전체 기능 미완료. 서버 코드의 리뷰 지적 수정·로컬 검증을 마쳤으며 정식 재검토·제공자 키 발급·앱 등록/콜백 합의·실제 통합 확인이 남았다. 설정이 없으면 해당 제공자 인증은503이다.
- 제공자: kakao·google·naver. 가입은 이메일/비밀번호만 허용하고 소셜 신규 회원은 만들지 않는다.
- POST /api/auth/oauth/{provider}: authorizationCode·redirectUri·state. 연결된 제공자 사용자 ID로 로그인(기존 쿠키 정책), 미연결이며 제공자 이메일과 같은 회원이 있으면409 ACCOUNT_MERGE_AVAILABLE와 mergeTicket·maskedEmail·providerEmailVerified·expiresAt. 나머지는401 SOCIAL_ACCOUNT_NOT_REGISTERED. mergeTicket 유효기간10분.
- POST /api/auth/oauth/merge: mergeTicket. 제공자 확인 이메일이 아니면 password 또는 ACCOUNT_MERGE verificationToken 필요. 증명 없으면422 IDENTITY_CONFIRMATION_REQUIRED, 본인 확인 실패401 INVALID_CREDENTIALS, 만료/사용 티켓410 MERGE_TICKET_EXPIRED. 성공은 기존 회원으로 로그인하고 연결 제공자 목록을 반환한다.
- POST /api/users/me/social-accounts/{provider}: 로그인 회원의 제공자 소유 확인 후201 provider·linkedAt. 같은 제공자 연결409 PROVIDER_ALREADY_LINKED, 다른 회원 소유409 SOCIAL_ACCOUNT_IN_USE. DELETE 같은 경로는204, 연결 없으면404 RESOURCE_NOT_FOUND. 마지막 로그인 수단 제한 없음.
- GET /api/users/me에 linkedProviders(제공자·연결 시각)를 추가한다. 제공자 ID·이메일은 반환하지 않는다.
- 공통5필드 envelope와 result.errorCode·필요한 details를 유지한다. 소셜/회원 ID는 JSON 문자열이다.
- POST /api/auth/oauth/{provider}/start(공개 로그인), POST /api/users/me/social-accounts/{provider}/start(인증 연결)은 redirectUri를 받아 authorizationUrl·expiresAt을 반환한다. state는10분·1회 사용이며 HttpOnly 브라우저 쿠키·제공자·목적·연결 회원·정확한 redirectUri와 결속한다. MySQL에 state/mergeTicket의 SHA-256과 소비 여부를 저장하고 원문은 저장하지 않는다. mergeTicket도 브라우저에 결속한다. 임의 redirectUri·제공자 URL을 서버 호출 대상으로 사용하지 않는다.
- 계정 연결 비밀번호 확인은 회원별 독립15분 내5회 실패·15분 잠금(429)이며 기존 로그인/현재 비밀번호 카운터는 유지한다. 잠겨도 ACCOUNT_MERGE 이메일 인증 경로는 허용한다.
- 추가 증명을 생략할 수 있는 Google 이메일은 email_verified=true이면서 Gmail 또는 Workspace hd 권위가 있는 경우다. 외부 이메일의 email_verified만으로는 현재 소유를 신뢰하지 않는다. 회원별 병렬 요청도 비밀번호5회 제한을 지키며, 잠금 대기 후 만료 시각을 다시 확인해야 한다.
- 보안 검증: 잘못된/만료/재사용 state·다른 브라우저·다른 목적/회원·다른 provider, 코드 교환 실패·제공자 장애/키 누락, 미확인 이메일의 자동 연결 금지, 티켓 변조/만료/재사용, 연결/해제 경쟁·사용자 선행 잠금·실패 롤백을 검사한다. 실제 SMTP/제공자 통합은 별도다.
