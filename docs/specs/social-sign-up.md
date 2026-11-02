# OAuth 신규 회원가입

기준: [Task356](../plans/S15P31E102-356.md). 사용자 확정으로 기존211의 이메일 전용 가입 정책을 대체한다. 연결된 소셜 로그인과 같은 이메일 기존 회원 병합은 유지한다. 미연결 신규는409 SOCIAL_SIGN_UP_REQUIRED로10분·1회·HttpOnly 쿠키 결속 가입 티켓만 발급한다.

POST /api/auth/oauth/sign-up은 signupTicket·nickname·현재 agreements와 선택 instrumentId/notationMode를 받는다. 이메일·비밀번호·제공자ID는 받지 않는다.201과 기존 가입 응답 및 로그인 쿠키를 반환한다. 기본 DANSO/HANJA. 현재 필수 약관 검증 실패422, 티켓 만료/재사용/쿠키 불일치410 SIGNUP_TICKET_EXPIRED. 새 연결 경합은409 SOCIAL_ACCOUNT_IN_USE, 이메일 경합은409 EMAIL_ALREADY_EXISTS다.

소유 확인 가능한 제공자 이메일만 계정 주소로 저장한다. 네이버 및 Google 비권위 외부 이메일은 기존 회원 검색·병합 안내에는 쓰되 신규 계정 주소로 저장하지 않는다. email/passwordHash nullable 회원을 지원하며 이메일 로그인·비밀번호 재설정의 기존 소유 확인을 우회하지 않는다. 마지막 소셜 연결만 남고 비밀번호가 없으면409 LAST_LOGIN_METHOD로 해제를 거절한다.

FE: 신규 오류에서 약관·닉네임 화면으로 이동하고 같은 앱 HTTP 쿠키와 티켓으로 가입 완료를1회 요청한다. 성공 전에는 회원 토큰이 없으며 가입 취소/만료는 새 소셜 인증부터 시작한다. MyInfo email=null 표시와 비밀번호 없는 회원의 비밀번호 기능 안내가 필요하다. [Task385](../plans/S15P31E102-385.md): GET /api/users/me의 result.hasPassword는 필수 boolean이며 password_hash가 null이 아니면 true, null이면 false다. FE는 이 값으로 비밀번호 기능·탈퇴 방식을 안내하고 email 또는 linkedProviders로 추측하지 않는다. 서버 검증은 별도로 유지한다. FE 구현은 이 Task에 포함하지 않는다.

## 소셜 재인증 탈퇴

POST /api/users/me/withdrawal/oauth/{provider}/start에 redirectUri를 보내 authorizationUrl·expiresAt과 결속 쿠키를 받는다. 앱의 별도 탈퇴 복귀 흐름에서 POST /api/users/me/withdrawal/oauth/{provider}에 authorizationCode·redirectUri·state를 보내 withdrawalTicket·expiresAt을 받는다. 두 API는 ACTIVE access_token이 필요하다. 제공자 인증은 기존 콜백 URI를 재사용하되 앱에서 WITHDRAW 시도로 구분해 처리한다.

DELETE /api/users/me는 currentPassword 또는 withdrawalTicket 중 정확히 하나를 받는다. 후자는 같은 회원의 access_token과 재인증 결속 쿠키가 필수다.410 WITHDRAWAL_TICKET_EXPIRED는 누락·목적/회원/쿠키 불일치·만료·재사용·연결 변경을 포함한다.200 기존 Withdrawn 응답·모든 refresh 폐기·쿠키 삭제·30일 유예를 유지한다. 소셜 가입의 마지막 수단 해제 보호는 앱 내 해제 API에 적용하며 제공자 콘솔에서 발생한 외부 해제를 막는다는 뜻이 아니다.

탈퇴 시작 state와 withdrawalTicket은 서버 내부에서 소셜 연결 행의 고유 ID에도 결속한다. 해제 후 같은 소셜 계정을 다시 연결해도 이전 티켓은410, 이전 시작 흐름의 인증 완료는401로 거절한다. 재연결 후 새 탈퇴 재인증을 시작해야 한다. 외부 API에는 연결 행 ID를 노출하지 않는다.
