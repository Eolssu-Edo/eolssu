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

- 사용자 확정: 실제 사용 대상은 앱이며 Android 휴대폰·태블릿부터 진행한다. 웹과 iOS 실연동은 후속이다. 기존 웹 우선 선택을 대체한다.
- 저장소 확인: applicationId는 com.ssafy.eolssu. 현재 MainActivity는 복귀 Intent를 처리하지 않고 manifest에 OAuth intent-filter가 없다. CI frontend 파이프라인은 웹 산출물만 만들며, 현재 작업 폴더에서 배포 APK를 찾지 못했다. release signingConfig도 명시되지 않았다. 실제 설치 APK의 서명을 알기 전 로컬 디버그 인증서를 배포 인증서로 간주하지 않는다.

### 비교·권고안 (아직 구현/콘솔 합의 전)

| 안 | 기존 서버 영향 | 앱/인프라 준비 | 판단 |
| --- | --- | --- | --- |
| 외부 브라우저 + 검증된 HTTPS App Links | 현재 start/code 교환·state·쿠키·merge 흐름 재사용 가능. 제공자별 클라이언트/콜백 허용은 실제 콘솔에서 확인 필요 | 앱 Intent 처리·인증 흐름 저장/복구, 도메인 assetlinks.json·설치 APK 인증서 등록 | 서버 변경을 줄이는 우선 검토안 |
| 제공자별 Android SDK | 제공자별 ID token/server auth code 등 실제 반환 자격증명에 맞춘 서버 검증 계약이 추가로 필요할 수 있음 | SDK3종과 플랫폼 설정·패키지/서명·테스트 기기, Google 공식 Android 경로 검토 | 앱 설치 연동 UX가 중요하면 선택. 기존 code API에 임의로 SDK 토큰을 보내지 않음 |

권고안에서 브라우저는 제공자 인증 화면만 맡고, 앱이 서버 start·완료 요청의 쿠키를 보유한다. 브라우저와 앱 쿠키를 공유한다고 가정하지 않는다. client secret은 서버에만 둔다. RFC8252의 외부 사용자 에이전트/HTTPS 복귀와 [Android App Links 검증](https://developer.android.com/training/app-links/about)을 근거로 검토하며, 제공자 콘솔 호환·실기기 복귀가 확인되기 전 작동 완료로 선언하지 않는다.

### Android 계약안

- 후보 App Links: https://k15e102.p.ssafy.io/auth/android/oauth/kakao/callback, https://k15e102.p.ssafy.io/auth/android/oauth/google/callback, https://k15e102.p.ssafy.io/auth/android/oauth/naver/callback. 웹 경로와 구분한다. 후보일 뿐 콘솔 등록·서버 환경 주입은 아직 하지 않았다.
- 앱은 제공자·LOGIN/LINK 목적·정확한 redirectUri·요청 state·만료를 인증 시도 상태로 보관한다. 외부 브라우저로 갔다가 프로세스가 종료돼도 잘못된 목적/사용자의 흐름으로 복구하지 않아야 한다. 코드·state·쿠키를 로그에 쓰거나 인가 코드를 영구 저장하지 않는다.
- 새 시작과 onNewIntent 복귀 모두 다루고, URI의 https·정확한 호스트·허용된 경로·provider·state를 확인한다. 재생·중복 복귀와 취소를 처리하며 완료 요청은1회만 보낸다. 로그인과 마이페이지 연결의 start·완료를 섞지 않는다.
- 현재 Android HttpClient는 SecureCookiesStorage를 공유해 서버 쿠키를 암호화 저장하며 OAuth 결속 쿠키도 이름 제한 없이 저장할 수 있다. 브라우저 대신 이 HTTP 클라이언트로 완료 API를 호출한다.
- 현재 HttpClients.kt의401 자동 refresh/재전송 예외는 refresh·sign-in·sign-up뿐이다. 새 OAuth start/완료/merge·소셜 연결 완료는1회용 state 또는 비밀번호 시도 카운터를 소비하므로, 담당 FE가 자동 재전송 예외/명시적 요청 정책을 먼저 추가해야 한다.
- /.well-known/assetlinks.json에는 실제 APK의 package_name과 SHA-256 공개 인증서 지문이 필요하다. 현재 Nginx에는 전용 JSON 제공 설정이 없고 SPA fallback뿐이므로 검증 파일200·application/json·리다이렉트 없음도 확인해야 한다. 서명값을 추측하거나 임의 키를 만들지 않는다.
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
