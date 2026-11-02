# Android 간편 로그인: 외부 브라우저와 App Links 복귀

- 작업: [S15P31E102-303](https://ssafy.atlassian.net/browse/S15P31E102-303), 소셜 신규 가입·재인증 탈퇴는 [S15P31E102-383](https://ssafy.atlassian.net/browse/S15P31E102-383). 서버 계약은 [간편 로그인과 계정 연결](../../specs/social-auth.md)의 Android 계약안(S15P31E102-211)과 [OAuth 신규 회원가입](../../specs/social-sign-up.md)(S15P31E102-356)이다.
- 상태: 앱 구현과 단위 테스트까지. 운영 서버의 211 배포·`assetlinks.json`(현재 404)·APK 지문 등록 전이라 실제 브라우저 왕복과 실기기 검증은 하지 않았다. iOS·웹은 '사용할 수 없음' 안내를 유지한다.

## 흐름과 코드 위치

1. AUTH-05(`feature/auth/OAuth*`)·MY-05(`feature/profile/SocialLink*`) 화면에 들어오면 서버 start(`POST /api/auth/oauth/{provider}/start`, 연결은 `/api/users/me/social-accounts/{provider}/start`)에 복귀 주소 `https://k15e102.p.ssafy.io/auth/android/oauth/{provider}/callback`을 보낸다. 서버 경로의 제공자는 소문자만 받는다.
2. 응답의 `authorizationUrl`에서 `state`를 꺼내 시도 상태(제공자·LOGIN/LINK·redirectUri·state·만료·연결 회원)를 `data/user/OAuthAttempts`에 남기고, `core/browser/ExternalBrowser`(Android 구현 `platform/browser/AndroidExternalBrowser`)로 연다.
3. 제공자가 복귀 주소로 돌아오면 Manifest의 App Links `intent-filter`가 `MainActivity`를 열고, `onCreate`(처음 만들 때만)·`onNewIntent`가 주소를 `core/browser/IncomingLinks`에 넣는다.
4. 시도를 시작한 화면이 주소를 꺼내 `OAuthAttempts.accept`로 확인한 뒤 완료 요청을 한 번 보낸다. 그 화면이 떠 있지 않으면(앱이 꺼졌다 켜진 경우) `app/navigation/OAuthReturnEffect`가 시도 상태를 보고 그 화면으로 이동한다.
5. 결과: 로그인 200 → 홈, 409 → AUTH-09(`mergeTicket`은 메모리에만), 401 미가입 → '가입된 계정이 없어요', 연결 409 `SOCIAL_ACCOUNT_IN_USE` → MY-05 안내, 503·연결 실패 → '다시 시도'(start부터 새로).

## 결정과 근거

- 복귀 확인: https·정확한 호스트·기본 포트·`/auth/android/oauth/{provider}/callback` 경로·state 일치를 모두 봐야 받아들인다. 우리 주소가 아니거나 다른 state면 진행 중 시도를 건드리지 않고 무시하고, 같은 state가 다시 오면(중복·재생) 무시한다. 제공자 `error`·만료·목적/제공자/회원 불일치는 시도를 지우고 처음부터 다시 하게 한다.
- 완료 요청 1회: 받아들이는 순간 시도를 지운 뒤 요청한다. 실패해도 같은 코드로 다시 보내지 않는다(state 1회용).
- 시도 상태 저장: Android `SecureStore`(Keystore 암호화)에 남겨 브라우저에 다녀오는 사이 프로세스가 끝나도 이어 간다. LINK는 시작한 회원 ID와 지금 회원 ID가 같을 때만 이어 간다. 인가 코드는 저장하지 않는다.
- `MainActivity`는 `launchMode="singleTask"`, 브라우저는 `FLAG_ACTIVITY_NEW_TASK`로 띄운다. 그래야 App Links 복귀가 브라우저 작업 위에 두 번째 `MainActivity`를 만들지 않고 기존 화면의 `onNewIntent`로 온다.
- 401 자동 갱신·재전송 제외(`core/network/HttpClients.kt`의 `skipsRefresh`): 로그인 start·완료·merge·마이페이지 연결 완료(POST). 연결 start와 해제(DELETE)는 1회용 값을 쓰지 않아 평소처럼 갱신한다. 연결 완료는 재전송이 없으므로 보내기 전에 `GET /api/users/me`로 액세스 토큰을 갱신해 둔다.
- 로그: 코드·state·쿠키·mergeTicket을 로그에 쓰지 않고, 이 값을 담는 클래스는 `toString`에서 가린다.

## 소셜 신규 가입과 비밀번호 없는 회원 (383)

- 가입: 로그인 완료가 409 `SOCIAL_SIGN_UP_REQUIRED`면 `signupTicket`을 `OAuthAttempts`의 메모리에만 두고, 동의한 약관이 없으면 가입용 약관 동의(`auth/signup/consent?next=Social`, 마이크 안내 없음)를 거쳐 프로필 설정(AUTH-07, 단계 표시 없음)으로 간다. `POST /api/auth/oauth/sign-up`은 티켓·닉네임·약관·악기만 보내고 1회만 요청한다(401 재전송 제외). 410·409 `SOCIAL_ACCOUNT_IN_USE`면 티켓을 버리고 간편 로그인부터 다시 하게 한다. 티켓은 start 때 받은 `oauth_flow_<제공자>` 쿠키와 묶여 있어 같은 HTTP 클라이언트로 보내야 한다.
- 탈퇴 재인증: MY-04에서 연결된 제공자로 start(`/api/users/me/withdrawal/oauth/{provider}/start`) → 브라우저 → 복귀(목적 WITHDRAW, 시작한 회원만) → 완료로 `withdrawalTicket`을 받고 바로 `DELETE /api/users/me {withdrawalTicket}`을 보낸다. 완료 요청은 연결 완료처럼 `GET /api/users/me`로 갱신한 뒤 재전송 없이 보낸다.
- 비밀번호 유무: 서버의 비밀번호 변경·비밀번호 탈퇴는 비밀번호 없는 회원을 401 `UNAUTHENTICATED`로 거절해 앱이 로그아웃으로 처리하게 된다. 그래서 `UserProfile.signsInWithPassword`로 미리 막는다. 내 정보 응답에 `hasPassword`가 없어(2026-11-02 BE에 추가 요청) 지금은 이메일 유무로 짐작하고, 오면 그 값을 쓴다. 짐작은 제공자가 확인한 이메일로 가입한 회원(카카오 대부분)을 비밀번호 회원으로 잘못 본다.
- 비밀번호 없는 회원의 마지막 간편 로그인 해제는 앱이 먼저 막고, 서버의 409 `LAST_LOGIN_METHOD`도 같은 안내로 받는다.
- 대기 화면: 와이어프레임의 점선 상자는 제공자 화면 자리 표시라 앱에서는 그리지 않고, AUTH-05·MY-05·MY-04 재인증이 `core/ui/component/ProviderWaiting`(큰 제공자 표시·진행 표시·안내)을 함께 쓴다.

## 남은 확인 (실기기 전에)

- 설치 APK의 SHA-256 지문을 BE에 전달하고, 운영 `assetlinks.json` 200·`application/json`·리다이렉트 없음을 확인한다. 각 PC의 디버그 서명은 다르다.
- Android 12 이상 기기에서 `adb shell pm get-app-links com.ssafy.eolssu`로 verified를 확인한 뒤, 앱이 살아 있을 때/종료됐을 때 복귀, 제공자 취소, 만료·중복 복귀, 로그인/계정 연결/마이페이지 연결·해제를 실제 카카오 계정으로 확인한다.
- Google·Naver 복귀 주소는 콘솔 등록 전 후보다. Manifest에는 세 경로가 모두 있다.
- 계정 연결의 이메일 인증 증명(`ACCOUNT_MERGE` verificationToken)은 화면이 없다. 지금은 비밀번호로만 본인을 확인한다.
- 소셜 가입·재인증 탈퇴는 운영 서버에 356이 배포된 뒤 실제 제공자 계정으로 가입 → 마이페이지 → 재인증 탈퇴 왕복을 확인한다. 2026-11-02 에뮬레이터에서는 카카오 로그인 start와 브라우저 열기만 운영 서버로 확인했다.
