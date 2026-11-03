# 간편 로그인과 계정 연결

- 기존 간편 로그인·연결은 S15P31E102-211·311·314·337에서 구현했으며 사용자가 Android QA에서 카카오·구글·네이버 로그인 성공을 확인했다. 신규 소셜 가입·재인증 탈퇴는 [356 계약](social-sign-up.md)의 미배포 변경이다.
- 제공자: kakao·google·naver. 이메일/비밀번호 가입과 함께 소셜 신규 가입을 지원한다. 소셜 가입은 닉네임·현재 약관 동의를 받고, 미확인 이메일·비밀번호는 저장하지 않는다.
- POST /api/auth/oauth/{provider}: authorizationCode·redirectUri·state. 연결된 제공자 사용자 ID로 로그인(기존 쿠키 정책), 미연결이며 제공자 이메일과 같은 회원이 있으면409 ACCOUNT_MERGE_AVAILABLE와 mergeTicket·maskedEmail·providerEmailVerified·expiresAt. 신규는409 SOCIAL_SIGN_UP_REQUIRED와 signupTicket·expiresAt을 반환하고 POST /api/auth/oauth/sign-up에서 가입한다. 두 티켓 유효기간은10분이다.
- POST /api/auth/oauth/merge: mergeTicket. 제공자 확인 이메일이 아니면 password 또는 ACCOUNT_MERGE verificationToken 필요. 증명 없으면422 IDENTITY_CONFIRMATION_REQUIRED, 본인 확인 실패401 INVALID_CREDENTIALS, 만료/사용 티켓410 MERGE_TICKET_EXPIRED. 성공은 기존 회원으로 로그인하고 연결 제공자 목록을 반환한다.
- POST /api/users/me/social-accounts/{provider}: 로그인 회원의 제공자 소유 확인 후201 provider·linkedAt. 같은 제공자 연결409 PROVIDER_ALREADY_LINKED, 다른 회원 소유409 SOCIAL_ACCOUNT_IN_USE. DELETE 같은 경로는204, 연결 없으면404 RESOURCE_NOT_FOUND. 비밀번호 없는 회원의 마지막 소셜 연결 해제는409 LAST_LOGIN_METHOD로 차단한다.
- GET /api/users/me에 linkedProviders(제공자·연결 시각)를 추가한다. 제공자 ID·이메일은 반환하지 않는다. 회원 email은 null일 수 있다.
- 공통5필드 envelope와 result.errorCode·필요한 details를 유지한다. 소셜/회원 ID는 JSON 문자열이다.
- POST /api/auth/oauth/{provider}/start(공개 로그인), POST /api/users/me/social-accounts/{provider}/start(인증 연결)은 redirectUri를 받아 authorizationUrl·expiresAt을 반환한다. state는10분·1회 사용이며 HttpOnly 브라우저 쿠키·제공자·목적·연결 회원·정확한 redirectUri와 결속한다. MySQL에 state/mergeTicket의 SHA-256과 소비 여부를 저장하고 원문은 저장하지 않는다. mergeTicket도 브라우저에 결속한다. 임의 redirectUri·제공자 URL을 서버 호출 대상으로 사용하지 않는다.
- 계정 연결 비밀번호 확인은 회원별 독립15분 내5회 실패·15분 잠금(429)이며 기존 로그인/현재 비밀번호 카운터는 유지한다. 잠겨도 ACCOUNT_MERGE 이메일 인증 경로는 허용한다.
- 추가 증명을 생략할 수 있는 Google 이메일은 email_verified=true이면서 Gmail 또는 Workspace hd 권위가 있는 경우다. 외부 이메일의 email_verified만으로는 현재 소유를 신뢰하지 않는다. 회원별 병렬 요청도 비밀번호5회 제한을 지키며, 잠금 대기 후 만료 시각을 다시 확인해야 한다.
- 보안 검증: 잘못된/만료/재사용 state·다른 브라우저·다른 목적/회원·다른 provider, 코드 교환 실패·제공자 장애/키 누락, 미확인 이메일의 자동 연결 금지, 티켓 변조/만료/재사용, 연결/해제 경쟁·사용자 선행 잠금·실패 롤백을 검사한다. 실제 SMTP/제공자 통합은 별도다.

## 운영 반영 순서

1. 211 후보를 최신 develop과 통합·검증한다. 기존 develop V8은 song_model_audios이며 OAuth는 미배포 버전을 V9로 분리했다. 운영 사전 조회(2026-10-29)는 V1~V7 성공·두 테이블 미존재였다. 적용 직전에 이력과 배포 상태를 다시 확인한다.
2. 운영 변경 승인을 받은 뒤 배포와 동일한 lock·백업 절차로 관리된 Compose·Nginx 원본과 환경 파일을 보존한다. Compose에 AUTH_OAUTH_KAKAO_REDIRECT_URIS와 AUTH_ANDROID_APP_SHA256_FINGERPRINTS 전달을 추가한다. Nginx HTTP 원본과 nginx.tls.conf.template 및 운영에 생성된 nginx.tls.conf의443 서버에도 정확한 /.well-known/assetlinks.json 프록시를 반영한다. TLS 오버레이가 별도 설정을 마운트하므로 HTTP 원본만 바꾸면 안 된다. 실제 APK 지문이 없으면 빈 값과404를 유지한다.
3. 검증한 후보 JAR을 db 프로필·웹 서버/스케줄러 없이 별도로 실행해 V8→V9를 적용하고 Flyway 이력을 확인한다. 실행 인자는 --spring.profiles.active=db --spring.main.web-application-type=none --eolssu.scheduling.enabled=false다. DB 비밀값은 서버 보호 환경으로 전달하며 명령·로그에 쓰지 않는다. ops와 db를 함께 켜지 않는다. 운영은 자동 마이그레이션을 하지 않는다.
4. 사람이 MR을 develop에 병합한 뒤 정상 CI/CD로 릴리스를 배포한다. 배포 파이프라인은 병합을 감지하므로 DB·관리 설정 준비 완료 전에 병합하지 않는다. backend readiness UP, 기존 회원 API 인증, 카카오 start200·허용하지 않은 콜백401, OAuth state 저장을 확인한다. 키·쿠키·인가 코드는 출력하지 않는다. 실제 제공자 로그인 성공과 Android 복귀는 별도 실기기 완료 조건이다.
5. 실패 시 새 릴리스 적용을 중단하고 관리된 배포 절차로 이전 JAR·Compose·Nginx를 복구한다. V8·V9는 추가 테이블이므로 앱 복구를 위해 DROP하지 않는다. 사용자 데이터 복구는 별도 승인·복구 절차로 진행한다. 카카오 키가 있는 환경 파일 저장만으로 배포·기능 완료로 처리하지 않는다.

## 웹 우선 실연동 준비 (2026-10-29)

**아래 웹 우선안은 같은 날 사용자의 Android 앱 우선 지시로 대체됐다.** 아직 등록·구현하지 않은 과거 계약안이며, 현재 콘솔 등록이나 FE 구현 기준으로 사용하지 않는다. 현재 기준은 아래 Android 우선 준비다.

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

## Android 앱 우선 준비 (2026-10-29)

준비 상태: 카카오 얼쑤 앱1628175의 개인 개발자 비즈 앱 전환·로그인 ON·이메일 필수 동의·Android 콜백 등록을 확인했다. REST API 키·로그인 Client secret·콜백은 edo1의 /opt/eolssu/.env(root:root 0600)에 저장했다. 운영 DB V8→V9 적용·Compose 전달 설정·HTTP/HTTPS App Links 프록시 준비를 마쳤다. OIDC는 OFF이며211 배포·컨테이너 환경 반영·실제 인증 검증은 미완료다. Google/Naver 콘솔 상태는 아직 확인하지 않았다. 실제 설치 APK의 공개 서명 지문·환경 주입·도메인 검증도 대기다. 기존 릴리스의 HTTPS App Links404 application/json과 readiness200을 확인했으며 이 결과를211 로그인 검증으로 보지 않는다.

카카오 이메일 설정(2026-10-29 콘솔 확인): 사용자가 본인 계정 절차를 수행한 뒤 개인 개발자 비즈 앱 전환을 확인했고, account_email은 필수 동의로 저장됐다. 동의 목적 안내는 '기존 회원 계정 확인 및 카카오 로그인 계정 연결'이다. 닉네임·프로필 사진은 사용 안 함을 유지한다. 이메일이 없어도 이미 연결된 제공자 ID는 로그인할 수 있고, 미연결 계정은401 SOCIAL_ACCOUNT_NOT_REGISTERED로 이메일 가입을 안내한다.

### 서버 App Links 제공 계약

BE 준비 범위를 먼저 마친 뒤 FE에 전달한다는 사용자 지시에 따라, GET /.well-known/assetlinks.json의 서버/Nginx 제공 경로를 준비한다. AUTH_ANDROID_APP_SHA256_FINGERPRINTS에 실제 설치 APK의 공개 SHA-256 지문(쉼표 구분)을 설정했을 때만200 application/json 배열을 반환하며, 대상 패키지는 저장소의 com.ssafy.eolssu로 한정한다. 지문은64자리16진수 또는32바이트 콜론 표기를 받아 표준 콜론 대문자 형태로 정규화한다. 잘못된 지문은 기동 시 거절하고, 미설정은 기동을 허용하되404로 제공한다. 임의의 지문·기본 인증서는 두지 않는다. 인증 쿠키 없이 조회하며 API의5필드 성공 envelope는 Android의 Digital Asset Links 형식에 덧씌우지 않는다. 회원 API 인증은 유지한다. Nginx는 이 정확한 경로만 백엔드로 보내고 SPA fallback/리다이렉트를 피한다. 실제 지문 등록·도메인 검증·배포 완료는 별도다.

- 사용자 확정: 실제 사용 대상은 앱이며 Android 휴대폰·태블릿부터 진행한다. 웹과 iOS 실연동은 후속이다. 기존 웹 우선 선택을 대체한다.
- 저장소 확인: applicationId는 com.ssafy.eolssu. 현재 MainActivity는 복귀 Intent를 처리하지 않고 manifest에 OAuth intent-filter가 없다. CI frontend 파이프라인은 웹 산출물만 만들며, 현재 작업 폴더에서 배포 APK를 찾지 못했다. release signingConfig도 명시되지 않았다. 실제 설치 APK의 서명을 알기 전 로컬 디버그 인증서를 배포 인증서로 간주하지 않는다.

### 비교·권고안 (아직 구현/콘솔 합의 전)

| 안 | 기존 서버 영향 | 앱/인프라 준비 | 판단 |
| --- | --- | --- | --- |
| 외부 브라우저 + 검증된 HTTPS App Links | 현재 start/code 교환·state·쿠키·merge 흐름 재사용 가능. 제공자별 클라이언트/콜백 허용은 실제 콘솔에서 확인 필요 | 앱 Intent 처리·인증 흐름 저장/복구, 도메인 assetlinks.json·설치 APK 인증서 등록 | 서버 변경을 줄이는 우선 검토안 |
| 제공자별 Android SDK | 제공자별 ID token/server auth code 등 실제 반환 자격증명에 맞춘 서버 검증 계약이 추가로 필요할 수 있음 | SDK3종과 플랫폼 설정·패키지/서명·테스트 기기, Google 공식 Android 경로 검토 | 앱 설치 연동 UX가 중요하면 선택. 기존 code API에 임의로 SDK 토큰을 보내지 않음 |

권고안에서 브라우저는 제공자 인증 화면만 맡고, 앱이 서버 start·완료 요청의 쿠키를 보유한다. 브라우저와 앱 쿠키를 공유한다고 가정하지 않는다. client secret은 서버에만 둔다. RFC8252의 외부 사용자 에이전트/HTTPS 복귀와 [Android App Links 검증](https://developer.android.com/training/app-links/about)을 근거로 검토하며, 제공자 콘솔 호환·실기기 복귀가 확인되기 전 작동 완료로 선언하지 않는다.

### Android 계약안

- 카카오 App Links: https://k15e102.p.ssafy.io/auth/android/oauth/kakao/callback . 사용자가 BE 기준 주소로 확정했으며 카카오 앱1628175의 REST API 키 설정에 등록 후 재조회했다. 로그인·연결 모두 같은 URI를 사용한다. AUTH_OAUTH_KAKAO_REDIRECT_URIS에 같은 값을 서버 환경 파일로 저장했고 운영 Compose에도 콜백·APK 지문 전달을 추가했다. 새 코드 배포 후 컨테이너에 반영해야 한다. FE 담당 합의·앱 복귀 구현·실기기 검증 완료를 뜻하지 않는다.
- Google/Naver 후보 App Links: https://k15e102.p.ssafy.io/auth/android/oauth/google/callback, https://k15e102.p.ssafy.io/auth/android/oauth/naver/callback. 두 주소는 아직 미확정이며 콘솔 등록·서버 환경 주입 전이다.
- 앱은 제공자·LOGIN/LINK 목적·정확한 redirectUri·요청 state·만료를 인증 시도 상태로 보관한다. 외부 브라우저로 갔다가 프로세스가 종료돼도 잘못된 목적/사용자의 흐름으로 복구하지 않아야 한다. 코드·state·쿠키를 로그에 쓰거나 인가 코드를 영구 저장하지 않는다.
- 새 시작과 onNewIntent 복귀 모두 다루고, URI의 https·정확한 호스트·허용된 경로·provider·state를 확인한다. 재생·중복 복귀와 취소를 처리하며 완료 요청은1회만 보낸다. 로그인과 마이페이지 연결의 start·완료를 섞지 않는다.
- 현재 Android HttpClient는 SecureCookiesStorage를 공유해 서버 쿠키를 암호화 저장하며 OAuth 결속 쿠키도 이름 제한 없이 저장할 수 있다. 브라우저 대신 이 HTTP 클라이언트로 완료 API를 호출한다.
- 현재 HttpClients.kt의401 자동 refresh/재전송 예외는 refresh·sign-in·sign-up뿐이다. 새 OAuth start/완료/merge·소셜 연결 완료는1회용 state 또는 비밀번호 시도 카운터를 소비하므로, 담당 FE가 자동 재전송 예외/명시적 요청 정책을 먼저 추가해야 한다.
- /.well-known/assetlinks.json에는 실제 APK의 package_name과 SHA-256 공개 인증서 지문이 필요하다. 서버와 Nginx 전용 경로는 준비됐으며 실제 지문 주입·배포 후 검증 파일200·application/json·리다이렉트 없음을 운영 HTTPS에서 확인해야 한다. 서명값을 추측하거나 임의 키를 만들지 않는다.
- 실기기 완료 조건: 설치 APK 링크 검증, 앱이 살아 있을 때/종료됐을 때 복귀, 쿠키 유지, 제공자 취소, 잘못된/만료/중복 state 거절, 신규 소셜 가입 거절, 동의/비밀번호/이메일 증명 연결, 연결 해제, 로그 비밀값 미노출이다. 현재는 모두 실연동 대기다.

### 권영민 인계 체크리스트

사용자 결정: FE 코드는 이211 브랜치에서 수정하지 않고 권영민에게 인계할 자료만 준비한다. 테스트는 Android Studio에서 만든 APK로 진행한다. 다음은 전달용 계약안이며, 실제 전달·FE 착수·콜백 합의 완료를 의미하지 않는다.

| 구분 | 인계할 작업·회신 |
| --- | --- |
| FE | 외부 브라우저/App Links 안과 후보3개 URI의 사용 여부 확인. 다른 SDK 방식을 선택하면 서버 코드 교환 계약부터 재협의 |
| FE | 로그인/마이페이지 연결 start → 브라우저 → 앱 복귀 → 목적별 완료 API. onCreate/onNewIntent, 프로세스 복구, 중복 완료 방지 구현 |
| FE | 기존 SecureCookiesStorage·HttpClient 유지. OAuth 관련1회용 요청은401 refresh/재전송 대상에서 제외하고 원래 오류 응답을 표시 |
| FE | 409 연결 동의·미확인 이메일의 비밀번호/이메일 증명·401 미가입 안내·410 재시작·503 장애 처리와 기존 로그인 상태 갱신 |
| APK 제작자 | 실제 설치할 APK의 빌드 variant·파일명·패키지명·공개 인증서 SHA-256 지문·테스트 Android 버전을 회신 |
| BE/인프라 | FE와 합의한 정확한 URI만 제공자 콘솔/서버 허용 목록에 등록. 실제 APK 서명으로 assetlinks.json 제공·검증. 키는 서버 비밀값으로 주입 |
| 공동 검증 | 앱 실행/종료 상태 복귀, 링크 검증·쿠키 유지, 취소·만료·중복·새 시작, 로그인/연결/해제·탈퇴 대기 복원을 실제 제공자 계정으로 확인 |

#### APK 공개 서명 확인

Android Studio에서 테스트 APK를 만든 뒤 그 APK 자체의 인증서를 확인한다. 이 저장소의 디버그 빌드 작업은 frontend에서 `./gradlew :androidApp:assembleDebug`이며 산출물은 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`다. 실제로 다른 variant를 설치하면 그 파일을 기준으로 한다. 각 개발자의 debug 서명은 다를 수 있으므로 다른 PC의 지문을 그대로 쓰지 않는다.

Android SDK Build Tools의 apksigner를 사용할 수 있는 터미널에서 다음 형식으로 공개 인증서를 출력한다. `<실제 설치할 APK 경로>`는 제작자가 선택한 파일로 바꾼다. 키스토어 파일이나 비밀번호를 전달할 필요는 없다.

```text
apksigner verify --print-certs <실제 설치할 APK 경로>
```

App Links 검증에는 출력 중 SHA-256 지문을 사용한다. [apksigner 공식 명령](https://developer.android.com/tools/apksigner)을 근거로 제시한 절차이며, 이 작업에서는 APK 생성·서명 추출을 실행하지 않았다.

#### 실기기 링크 검증

Manifest·assetlinks.json 배포와 APK 설치를 마친 뒤 Android12 이상 연결 기기에서, Android SDK Platform Tools의 adb를 사용할 수 있는 터미널에서 확인한다. 먼저 Android 공식 절차에 따라 기기의 기존 도메인 검증 상태를 초기화한 뒤 재검증한다.

```text
adb shell pm set-app-links --package com.ssafy.eolssu 0 all
adb shell pm verify-app-links --re-verify com.ssafy.eolssu
adb shell pm get-app-links com.ssafy.eolssu
```

재검증은 비동기다. verify-app-links 뒤 검증 에이전트가 완료할 시간을 둔 다음 get-app-links를 조회한다. none이면 대기 중일 수 있으므로 완료 시간을 두고 다시 조회하며, 계속 검증되지 않으면 설정/네트워크를 점검한다. 대상 도메인의 verified 상태를 확인하고 실제 제공자 브라우저 왕복을 시험한다. 단순히 인텐트를 해당 패키지로 강제 실행한 결과만으로 App Links 검증을 통과 처리하지 않는다. [Android 공식 검증 절차](https://developer.android.com/training/app-links/verify-applinks)를 근거로 하며 아직 기기에서 실행하지 않았다.

## 탈퇴 유예 만료

2026-10-30 사용자 수정 요청에 따라 소셜 로그인·병합도 [탈퇴 유예 만료와 삭제 경계](withdrawal-expiry.md)를 따른다. 회원 잠금을 얻은 뒤의 시각이 탈퇴 요청 후 30일 정각 이상이면 `401 INVALID_CREDENTIALS`로 거절하며, 탈퇴를 취소하거나 로그인 토큰을 발급하지 않는다.

## QA Android App Links

사용자 요청(2026-10-30)에 따라 일반 com.ssafy.eolssu와 QA com.ssafy.eolssu.qa의 실제 APK 지문을 독립 등록한다. 일반 환경변수는 유지하고 QA는 AUTH_ANDROID_QA_APP_SHA256_FINGERPRINTS를 사용한다. 운영 적용·기기 verified·카카오 왕복 결과는 [Task337](../plans/S15P31E102-337.md)에 기록하며 구현 테스트와 구분한다.

## iOS 인증 복귀 (S15P31E102-417)

사용자 결정(2026-11-03): iOS도 Android와 같은 간편 로그인·신규 가입·계정 연결 제안·마이페이지 연결·탈퇴 재인증을 제공한다. 무료 Apple 개발 계정(Personal Team)은 Associated Domains를 쓸 수 없어 Android의 HTTPS App Links 복귀를 iOS에서 그대로 받을 수 없으므로, 서버의 HTTPS 중계 주소를 거쳐 앱 전용 주소로 돌아온다. 공통 화면·시도 상태·완료 API 계약은 바꾸지 않고 redirectUri와 앱이 받는 복귀 주소만 플랫폼별로 둔다.

| 항목 | Android | iOS |
| --- | --- | --- |
| redirectUri(start·완료 요청, 제공자 콘솔·서버 허용 목록) | `https://k15e102.p.ssafy.io/auth/android/oauth/{provider}/callback` | `https://k15e102.p.ssafy.io/api/auth/ios/oauth/{provider}/callback` |
| 앱이 받는 복귀 주소 | redirectUri 그대로(App Links) | `com.ssafy.eolssu://oauth/{provider}/callback?…` |
| 제공자 화면 | 외부 브라우저 | `ASWebAuthenticationSession`(callbackURLScheme `com.ssafy.eolssu`) |

- 중계: `GET /api/auth/ios/oauth/{provider}/callback`은 로그인 없이 연다. provider는 kakao·google·naver만 받고 그 밖은 404다. 받은 쿼리 중 `code`·`state`·`error`·`error_code`·`error_description`만 그대로 옮겨 `302 Location: com.ssafy.eolssu://oauth/{provider}/callback?…`로 보낸다. 본문은 없고 `Cache-Control: no-store`·`Referrer-Policy: no-referrer`다. 코드를 서버에서 교환·저장·기록하지 않는다. 대상 스킴은 고정이라 임의 주소로 보내지 않는다.
- 앱 확인: 복귀 주소는 스킴 `com.ssafy.eolssu`·호스트 `oauth`·경로 `/{provider}/callback`이어야 한다. state·만료·목적·회원·`error`·`code` 확인과 완료 요청의 redirectUri(위 HTTPS 주소)는 Android와 같다.
- 취소: 사용자가 인증 창을 닫으면 복귀 주소가 오지 않는다. Android에서 브라우저를 닫은 것과 같이 화면은 '다시 열기'를 보여 준다.
- 운영 준비(배포 전 필요): 세 제공자 콘솔에 iOS redirectUri를 등록하고 `AUTH_OAUTH_{KAKAO|GOOGLE|NAVER}_REDIRECT_URIS`에 쉼표로 추가한다. 등록 전 start는 `401 OAUTH_VERIFICATION_FAILED`다.
- 제외: Sign in with Apple(App Store 심사 4.8 대응)과 유료 계정 Universal Links는 이번 범위가 아니다.
