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

## 웹 우선 실연동 준비 (2026-10-29)

사용자는 웹부터 연결하고 Android 복귀 처리는 후속으로 진행하기로 선택했다. 다음 주소는 FE와 콘솔 등록에 제안하는 계약안이며, 해당 화면 구현·FE 합의·콘솔 등록 완료를 뜻하지 않는다.

| 제공자 | 웹 콜백 계약안 | 서버 설정 |
| --- | --- | --- |
| kakao | https://k15e102.p.ssafy.io/auth/oauth/kakao/callback | AUTH_OAUTH_KAKAO_REDIRECT_URIS |
| google | https://k15e102.p.ssafy.io/auth/oauth/google/callback | AUTH_OAUTH_GOOGLE_REDIRECT_URIS |
| naver | https://k15e102.p.ssafy.io/auth/oauth/naver/callback | AUTH_OAUTH_NAVER_REDIRECT_URIS |

주소는 제공자 콘솔·start 요청·완료 요청·서버 허용 목록에서 동일해야 한다. /api 콜백이 아니며, 제공자의 GET code/state를 FE가 받아 기존 JSON POST 완료 API를 호출하는 구조다. Nginx의 / 경로 SPA fallback은 있지만 FE의 콜백 처리는 아직 없다.

### FE 연결 계약안

1. 로그인 버튼은 공개 start, 마이페이지 연결 버튼은 인증된 연결 start를 호출한다. 시작 전에 제공자·LOGIN/LINK 목적·정확한 redirectUri를 탭의 sessionStorage에 보관해 리다이렉트 뒤 복구한다. 비밀키·제공자 토큰·mergeTicket은 URL이나 영구 저장소에 넣지 않는다.
2. authorizationUrl로 같은 탭을 이동한다. 돌아온 콜백은 code/state 또는 제공자 오류를 읽고 코드가 브라우저 이력·외부 리소스로 퍼지지 않게 URL 쿼리를 즉시 지운다. 브라우저가 HttpOnly oauth_flow 쿠키를 보내도록 같은 출처의 기존 HTTP 클라이언트를 사용한다.
3. 보관한 목적에 따라 로그인 완료/회원 연결 완료를 호출한다. 목적이 없거나 제공자·redirectUri가 다르면 임의로 LOGIN으로 간주하지 말고 인증을 다시 시작한다. 완료 POST를 자동 재시도하지 않는다(state는1회용).
4. 로그인200이면 기존 로그인 상태를 갱신한다. 409 ACCOUNT_MERGE_AVAILABLE이면 명시적 연결 동의를 받으며 ticket은 현재 흐름의 메모리에만 둔다. 제공자 이메일 미확인 시 비밀번호 또는 ACCOUNT_MERGE 이메일 증명으로 merge한다. 401 SOCIAL_ACCOUNT_NOT_REGISTERED는 이메일 가입을 안내하고 자동 가입하지 않는다. 나머지401/410은 다시 시작,503은 재시도를 안내한다.
5. provider 오류/취소와 새로고침·중복 콜백·다른 탭·만료도 확인한다. Android 예시 화면이나 딥링크를 구현 완료로 표시하지 않는다.

### 앱 등록·키 발급 준비

- 서비스 기본 주소: https://k15e102.p.ssafy.io . 실제 앱 이름·소유 계정·관리자·문의/정책 URL은 운영자가 확인한다. 가상 회사명·정책 URL로 등록하지 않는다.
- Kakao: 개발자 앱에서 REST API 키와 Client secret, 카카오 로그인 활성화·이메일 동의 항목과 위 Redirect URI를 설정한다. [공식 REST 안내](https://developers.kakao.com/docs/ko/kakaologin/rest-api).
- Google: OAuth 클라이언트 유형은 이 서버 코드 교환 방식에 맞는 Web application으로 준비하고 승인된 redirect URI·동의 화면/테스트 사용자 접근 범위를 확인한다. [공식 서버 앱 안내](https://developers.google.com/identity/protocols/oauth2/web-server).
- Naver: 네이버 로그인 애플리케이션을 등록하고 서비스 URL·Callback URL·이메일 제공 항목과 Client ID/Secret을 확인한다. [공식 API 안내](https://developers.naver.com/docs/login/api/api.md).
- 키는 현재 백엔드/런타임 예시의 AUTH_OAUTH_*_CLIENT_ID·CLIENT_SECRET에 서버 비밀값으로 주입한다. 이 문서·Git·채팅·FE 번들에 secret을 복사하지 않는다. 콘솔 등록·환경 주입·Push/배포·실제 제공자 호출은 아직 수행하지 않았다.
