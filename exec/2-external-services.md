# 2. 외부 서비스 정보

- 작업: S15P31E102-1106
- 기준: `develop` `9d8c7dfc4`(2026-11-17)의 코드·설정·운영 문서
- 비밀값은 이 문서에 적지 않는다. 운영 값은 edo1의 `/opt/eolssu/.env`(root 0600)에만 있고, 각 변수 옆에 **[비밀]** 여부만 표시한다.
- 콘솔 상태는 저장소 문서에 마지막으로 기록된 내용이며, 제출 시점에 콘솔에서 다시 확인하지 않았다.

## 요약

| 구분 | 서비스 | 용도 | 필수 여부 |
| --- | --- | --- | --- |
| 서버 | AWS EC2 서울 리전(SSAFY 제공) | edo1 운영 서버, edo2 백업·대기 서버 | 필수 |
| 도메인 | `k15e102.p.ssafy.io`(SSAFY Route 53) | 웹·API·OAuth 복귀 주소·App Links | 필수 |
| 인증서 | Let's Encrypt(Certbot) | HTTPS | 필수 |
| 저장소 | Cloudflare R2 | 녹음 원본, 학습·모범 음원, 내 악보 음원 | 필수(없으면 해당 기능만 503) |
| 메일 | 네이버 메일 SMTP | 회원가입·비밀번호 재설정 인증번호 | 이메일 가입 시 필수 |
| 소셜 로그인 | 카카오, Google, 네이버, Apple | 간편 로그인 | 선택(제공자별) |
| 앱 배포 | Google Play Console, App Store Connect·TestFlight | 앱 테스트 배포 | 선택 |
| 알림 | Mattermost(SSAFY) | Jenkins 실패·서버 상태 알림 | 운영 편의 |
| 블로그 | GitHub Pages | 기술 블로그 | 서비스와 무관 |

푸시 알림(FCM·APNs), 외부 AI·LLM API, 외부 모니터링 SaaS(Sentry·Crashlytics 등), 분석 도구(GA·Firebase), Firebase App Distribution은 **사용하지 않는다**. 연주 분석(pYIN·DTW)과 악보 인식(OMR)은 edo1 내부 컨테이너에서 자체 실행한다.

## 1. 서버 인프라: AWS EC2 (SSAFY 제공)

- 서울 리전(ap-northeast-2) 인스턴스 2대를 사용한다.
  - edo1: Nginx·backend(블루그린)·ai·omr·playback·MySQL, Jenkins, Prometheus·Grafana
  - edo2: 암호화 백업 보관·격리 복원, MySQL 읽기 전용 복제본·대기 환경, Mattermost 점검 중계
- 팀에는 AWS 콘솔 권한이 없다. SSH 별칭 `edo1`, `edo2`로만 접속한다.
- 근거: `infra/README.md`, `infra/runtime/README.md`, 개인정보 처리방침 4항(`backend/src/main/resources/legal/2026-11-15/privacy.html`)

## 2. 도메인·DNS: `k15e102.p.ssafy.io`

- SSAFY가 관리하는 Route 53 `ssafy.io` 존의 A 레코드(TTL 300초)다. 팀에는 편집 권한이 없어 변경은 SSAFY 담당자에게 요청한다.
- DNS TXT 권한이 없어 Let's Encrypt DNS-01 인증은 사용하지 않는다.
- 근거: `infra/standby/failover-runbook.md`

## 3. TLS 인증서: Let's Encrypt (Certbot)

- edo1 운영 도메인: webroot 방식(`/opt/eolssu/acme`). `certbot.timer`가 갱신하고, deploy hook `/etc/letsencrypt/renewal-hooks/deploy/eolssu-nginx`가 Nginx 설정을 다시 읽힌다.
- 전제 조건: DNS가 서버를 가리키고 외부에서 TCP 80·443에 접속할 수 있어야 한다.
- 최초 발급: `infra/scripts/init-letsencrypt.sh`
- edo2: snap Certbot의 `shortlived` 프로필로 받은 IP 인증서(수명 160시간)를 Mattermost 중계에 사용한다.
- 비밀값: 없음
- 근거: `infra/runtime/README.md`의 "Let's Encrypt와 HTTPS", `infra/edo-check/README.md`

## 4. 객체 저장소: Cloudflare R2 (S3 호환)

### 용도

| 데이터 | 버킷·접두사 |
| --- | --- |
| 연주 녹음 원본 | `eolssu-recordings` / `recordings/` |
| 학습(기본음·시김새) 음원, 모범 연주 음원 | `eolssu-recordings` / `reference-audio/`, `model-audio/` |
| 내 악보 생성 음원(M4A) | `eolssu-sheet-audio` |

backend가 AWS SDK v2로 업로드하고, 앱과 ai 서비스에는 기한이 있는 서명 URL(녹음 900초, 내 악보 음원 다운로드 10분)을 발급한다.

- 연동 코드: `backend/src/main/kotlin/com/ssafy/eolssu/backend/common/storage/S3ObjectStore.kt`, `.../feature/catalog/infrastructure/storage/S3ReferenceAudioReadUrls.kt`
- 운영 도구: `infra/r2/upload.py`, `infra/edo-check/r2_probe.py`

### 환경 변수

세 그룹이 같은 형식의 키 다섯 개씩을 가진다.

| 접두사 | 용도 |
| --- | --- |
| `RECORDING_STORAGE_` | 녹음 원본(읽기·쓰기). `RECORDING_STORAGE_TYPE=s3|local`로 로컬 저장 전환 가능 |
| `SHEET_STORAGE_` | 내 악보 음원(읽기·쓰기) |
| `REFERENCE_AUDIO_STORAGE_` | 학습·모범 음원(읽기 전용) |

| 접미사 | 비밀 여부 | 설명 |
| --- | --- | --- |
| `ENDPOINT` | 비밀 아님 | `https://<account_id>.r2.cloudflarestorage.com` |
| `BUCKET` | 비밀 아님 | 버킷 이름(토큰 이름과 혼동 금지) |
| `REGION` | 비밀 아님 | 기본 `auto` |
| `ACCESS_KEY_ID` | **[비밀]** | R2 API 토큰의 Access Key |
| `SECRET_ACCESS_KEY` | **[비밀]** | R2 API 토큰의 Secret |

한 그룹이라도 값이 비어 있으면 그 기능만 503으로 응답하고 나머지 기능은 동작한다.

### 가입·콘솔 설정

1. Cloudflare 계정에서 R2를 활성화하고 버킷 `eolssu-recordings`, `eolssu-sheet-audio`를 만든다(위치 APAC, Standard 등급).
2. 버킷마다 API 토큰을 따로 만들고 **해당 버킷만 Object Read & Write**를 허용한다(`eolssu-backend-recordings`, `eolssu-backend-sheet-audio`).
3. 공개 접근을 끈다: Public Development URL(r2.dev) 비활성, 사용자 도메인 연결 없음.
4. CORS(웹 앱의 음원 재생용):
   - 허용 출처: `https://k15e102.p.ssafy.io`, `http://localhost:8080` (`*` 사용 금지)
   - 메서드: GET, HEAD
   - 노출 헤더: Content-Length, Content-Type, Content-Range, Accept-Ranges
   - MaxAge: 3600
5. 미완료 multipart 업로드는 기본값(7일)으로 중단한다. 저장하지 않은 녹음의 30일 정리는 R2 수명 주기 규칙이 아니라 backend 일괄 작업(매일 04:30 KST)이 담당한다.

- 주의: 버킷 이름 자리에 토큰 이름을 넣으면 PUT·DELETE가 403으로 실패한다(2026-10-29 실제 발생).
- 개인정보 국외 이전 고지 대상(Cloudflare, Inc.)이며 처리방침에 반영되어 있다.
- 근거: `docs/knowledge/infra/r2-storage-verification.md`, `infra/r2/README.md`, `backend/README.md`

## 5. 이메일 발송: 네이버 메일 SMTP

- 용도: 회원가입·비밀번호 재설정·계정 연결 인증번호 메일
- 연동 코드: `backend/src/main/kotlin/com/ssafy/eolssu/backend/feature/auth/infrastructure/mail/SmtpVerificationMailSender.kt` (STARTTLS 고정, 연결 3초·읽기 5초 제한)

| 변수 | 비밀 여부 | 값·설명 |
| --- | --- | --- |
| `AUTH_MAIL_MODE` | 비밀 아님 | `smtp`(운영) / `memory`(테스트) / `unavailable` |
| `AUTH_MAIL_HOST` | 비밀 아님 | `smtp.naver.com` |
| `AUTH_MAIL_PORT` | 비밀 아님 | `587` (465 사용 안 함) |
| `AUTH_MAIL_USERNAME` | **[비밀]** | 발송 계정 |
| `AUTH_MAIL_PASSWORD` | **[비밀]** | 네이버 애플리케이션 비밀번호 |
| `AUTH_MAIL_FROM`, `AUTH_MAIL_FROM_NAME` | 비밀 아님 | 발신 주소, 표시 이름 `얼쑤` |
| `AUTH_CODE_HMAC_SECRET` | **[비밀]** | 인증번호 HMAC 키 |

가입·설정 절차:

1. 발송용 네이버 계정에 2단계 인증을 켠다.
2. 네이버ID 보안설정에서 애플리케이션 비밀번호를 발급한다.
3. PC 네이버 메일 환경설정에서 IMAP/SMTP를 "사용함"으로 바꾼다.

- 서비스 제한: 같은 이메일 기준 60초 간격, KST 하루 10회
- 근거: `infra/runtime/README.md`, `backend/README.md`

## 6. 소셜 로그인 (OAuth 2.0 / OIDC)

앱이 인가 코드를 받고 **서버가 토큰 교환**을 수행한다. 스펙은 `docs/specs/social-auth.md`, 코드는 `backend/.../feature/auth/infrastructure/oauth/RestOAuthGateway.kt`, `AppleOAuthGateway.kt`에 있다.

### 공통 복귀 주소(Redirect URI)

| 플랫폼 | 주소 |
| --- | --- |
| Android (App Links) | `https://k15e102.p.ssafy.io/auth/android/oauth/{provider}/callback` |
| iOS (서버 중계) | `https://k15e102.p.ssafy.io/api/auth/ios/oauth/{provider}/callback` → 302 `com.ssafy.eolssu://oauth/{provider}/callback` |

서버는 `AUTH_OAUTH_<PROVIDER>_REDIRECT_URIS`(쉼표 구분, 비밀 아님)에 등록된 주소만 허용한다. `{provider}`는 `kakao`, `google`, `naver`, `apple`이다.

### 6-1. 카카오

| 변수 | 비밀 여부 |
| --- | --- |
| `AUTH_OAUTH_KAKAO_CLIENT_ID` | 비밀 아님(REST API 키) |
| `AUTH_OAUTH_KAKAO_CLIENT_SECRET` | **[비밀]** |
| `AUTH_OAUTH_KAKAO_REDIRECT_URIS` | 비밀 아님 |
| `AUTH_KAKAO_WEBHOOK_APP_ID` | 비밀 아님(앱 ID) |

카카오 디벨로퍼스 설정:

1. 애플리케이션을 만들고 개인 개발자 비즈 앱으로 전환한다(이메일 필수 동의에 필요).
2. 카카오 로그인 ON, OpenID Connect OFF
3. 동의 항목: `account_email` 필수(닉네임·프로필 사진은 사용하지 않음)
4. 위 Android·iOS 복귀 주소를 Redirect URI로 등록한다.
5. 연결 해제 웹훅: `https://k15e102.p.ssafy.io/api/webhooks/kakao/events`에서 "User Unlinked"만 구독한다. 서버가 카카오 JWKS로 서명을 검증하고 2.5초 안에 응답한다(카카오 요구 3초). 어드민 키는 사용하지 않는다.

### 6-2. Google

| 변수 | 비밀 여부 |
| --- | --- |
| `AUTH_OAUTH_GOOGLE_CLIENT_ID` | 비밀 아님 |
| `AUTH_OAUTH_GOOGLE_CLIENT_SECRET` | **[비밀]** |
| `AUTH_OAUTH_GOOGLE_REDIRECT_URIS` | 비밀 아님 |

Google Cloud Console 설정:

1. 프로젝트 `eolssu-s15p31e102`를 만든다(결제 계정 불필요).
2. OAuth 동의 화면: 외부(External). 기록된 상태는 **테스트 중(Testing)**이므로 등록된 테스트 사용자만 로그인할 수 있다.
3. OAuth 클라이언트 유형 **Web application**, JavaScript 원본은 비우고 복귀 주소만 등록한다.
4. scope `openid email`, PKCE S256을 사용한다.

### 6-3. 네이버

| 변수 | 비밀 여부 |
| --- | --- |
| `AUTH_OAUTH_NAVER_CLIENT_ID` | 비밀 아님 |
| `AUTH_OAUTH_NAVER_CLIENT_SECRET` | **[비밀]** |
| `AUTH_OAUTH_NAVER_REDIRECT_URIS` | 비밀 아님 |

네이버 개발자센터 설정:

1. 애플리케이션 등록: 사용 API "네이버 로그인", 제공 항목은 기본 식별자와 연락처 이메일(필수)
2. 플랫폼 "Mobile 웹", 서비스 URL `https://k15e102.p.ssafy.io`, Callback URL에 복귀 주소 등록
3. 기록된 상태는 **개발 중**이므로 멤버관리에 등록한 계정만 로그인할 수 있다.

### 6-4. Sign in with Apple (iOS 전용)

| 변수 | 비밀 여부 |
| --- | --- |
| `AUTH_OAUTH_APPLE_CLIENT_ID` | 비밀 아님(번들 ID `com.ssafy.eolssu`) |
| `AUTH_OAUTH_APPLE_TEAM_ID`, `AUTH_OAUTH_APPLE_KEY_ID` | 비밀 아님(식별자) |
| `AUTH_OAUTH_APPLE_PRIVATE_KEY` | **[비밀]** (.p8, PKCS#8) |
| `AUTH_OAUTH_APPLE_REDIRECT_URIS` | 비밀 아님 |
| `AUTH_OAUTH_APPLE_DEV_CLIENT_ID`, `AUTH_OAUTH_APPLE_DEV_REDIRECT_URIS` | 비밀 아님(개발 앱 `com.ssafy.eolssu.dev`) |

Apple Developer 설정:

1. **유료 Apple Developer Program** 팀이 필요하다(무료 Personal Team 불가).
2. App ID `com.ssafy.eolssu`에서 Sign in with Apple을 켜고, 개발 번들 ID를 같은 그룹으로 묶는다. entitlement는 `frontend/iosApp/iosApp/iosApp.entitlements`에 있다.
3. Services용 Key를 만들어 Key ID와 .p8 파일을 서버 환경 변수로 넣는다.

## 7. Android App Links (Digital Asset Links)

- OAuth 복귀 주소로 앱을 바로 열기 위해 사용한다.
- backend가 `/.well-known/assetlinks.json`을 제공하고 Nginx는 이 경로만 프록시한다.
- `AUTH_ANDROID_APP_SHA256_FINGERPRINTS`(`com.ssafy.eolssu`), `AUTH_ANDROID_QA_APP_SHA256_FINGERPRINTS`(`com.ssafy.eolssu.qa`): 서명 인증서 SHA-256 지문(공개값)
- 지문은 실제 설치할 APK에서 `apksigner verify --print-certs <apk>`로 구한다. 서명 키가 바뀌면 지문도 다시 등록해야 한다.
- 앱 설정: `frontend/androidApp/src/main/AndroidManifest.xml`의 `autoVerify` intent-filter(호스트 `k15e102.p.ssafy.io`)
- iOS는 Associated Domains를 사용하지 않고 6장의 서버 중계 방식을 쓴다.

## 8. 앱 배포

### Google Play Console

- 패키지 `com.ssafy.eolssu`(QA 변형 `com.ssafy.eolssu.qa`), 비공개 테스트 트랙으로 배포한다.
- 릴리스 서명 키는 빌드 환경 변수로 주입한다: `EOLSSU_STORE_FILE`, `EOLSSU_STORE_PASSWORD`, `EOLSSU_KEY_ALIAS`, `EOLSSU_KEY_PASSWORD` **[모두 비밀]**
- Console에 입력하는 URL
  - 개인정보 처리방침: `https://k15e102.p.ssafy.io/api/terms/documents/current/privacy`
  - 계정 삭제 안내: `https://k15e102.p.ssafy.io/api/terms/documents/account-deletion`

### App Store Connect·TestFlight

- 번들 ID `com.ssafy.eolssu`, iPhone 전용, 무료, 판매 국가 대한민국
- 버전은 `frontend/iosApp/Configuration/Config.xcconfig`의 `MARKETING_VERSION`·`CURRENT_PROJECT_VERSION`으로 관리한다.
- 수출 규정: `ITSAppUsesNonExemptEncryption=false`

### iOS Ad Hoc 배포(내부 QA)

- Jenkins `eolssu-ios-qa`가 원격 Mac 에이전트에서 빌드해 `https://k15e102.p.ssafy.io/ipas/`에 게시한다. Ad Hoc 프로파일에 등록된 기기만 설치할 수 있다.
- App Store Connect API 키(`AuthKey_<KEY_ID>.p8`, issuer ID) **[비밀]**는 Mac의 `~/jenkins-agent/asc/`에 둔다.
- 근거: `infra/ios-qa/README.md`

## 9. 알림: Mattermost (SSAFY `meeting.ssafy.com`)

- Jenkins 실패 알림, edo2 서버 상태 변화 알림을 incoming webhook으로 보낸다.
- 슬래시 명령 `/edo1`, `/edo2`, `/applog`로 서버 상태와 앱 로그를 조회한다.
- webhook URL과 명령 토큰 **[비밀]**은 서버의 root 전용 파일에 둔다(`/etc/eolssu-jenkins-notify/webhook-url`, `/etc/edo-check-alert/webhook-url`, `/etc/edo-check/relay.json`). 예시는 `infra/edo-check/relay.example.json`이다.
- 근거: `infra/jenkins/notifications/README.md`, `infra/edo-check/README.md`

## 10. 소스 저장소·CI·블로그

- SSAFY GitLab `https://lab.ssafy.com/s15-final/S15P31E102.git`: Jenkins credential `eolssu-gitlab` **[비밀]**로 접근한다.
- Jenkins, Prometheus, Grafana, phpMyAdmin은 edo1에서 자체 호스팅한다(외부 SaaS 아님). Grafana 관리자 비밀번호 `GRAFANA_ADMIN_PASSWORD` **[비밀]**
- 기술 블로그: Jenkins `eolssu-blog`가 `blog` 브랜치를 Hugo로 빌드해 GitHub Pages(`https://team-edo.github.io`)에 게시한다. Jenkins credential `github-blog-token` **[비밀]**

## 11. 빌드 중 내려받는 외부 자원

- Docker Hub 공개 이미지: `nginx:1.30.6-alpine`, `mysql:8.4.13`, `eclipse-temurin:21-jdk`/`21-jre`, `ubuntu:24.04`, `grafana/grafana:11.3.0`, `prom/prometheus:v3.5.0`, `prom/node-exporter:v1.9.1`, `phpmyadmin:5.2.3`, `jenkins/jenkins:2.580.1-jdk21`
- GHCR: `ghcr.io/astral-sh/uv:0.13.6`
- OMR 이미지(`ai/omr.Dockerfile`)가 GitHub에서 받는 자원: Audiveris 5.11.0 deb(AGPL-3.0), tesseract `eng.traineddata`, `danbinaerinHan/jeongganbo-generator`(MIT), `MALerLab/jeongganbo-omr` 소스·체크포인트 v1.0.0(MIT)
- 패키지 저장소: Maven Central·Gradle Plugin Portal, PyPI(uv)
- 이미지를 push하는 레지스트리는 없다. 모든 이미지는 edo1에서 빌드해 로컬 태그로 사용한다.

## 12. 데이터 출처 (사전 수집, 런타임 호출 없음)

| 출처 | 사용처 | 이용 조건 |
| --- | --- | --- |
| 국립국악원 국악기 디지털 음원 | 기본음·요성 예시, 장단 소리(학습 음원 430개, R2 게시) | 공공누리 제1유형(출처 표시) |
| 국립국악원 e국악아카데미 악보 자료실, 『2021 대금정악보』 등 | 운지표·악보 원문 | 공공누리 2·4유형 혼재 |
| 서울예술과학센터 CAT 국악 가상악기 라이브러리 | 모범 연주·내 악보 음원 합성(playback) | 2차 저작물 출처 문구 표시. 원본 ZIP은 Git 제외, `PLAYBACK_ASSETS_DIRECTORY`에 배치 |
| 우물사이 jeongganbo-generator | 시김새 기호 SVG, 정간 분절 | MIT |

앱의 데이터 출처 화면 문구는 `frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/profile/DataSources.kt`에 있다. 운영 중 실시간으로 호출하는 공공데이터 API는 없다.

## 비용·쿼터

저장소 문서에서 확인한 제약은 메일 발송 제한(60초 간격·하루 10회), Google 프로젝트 결제 계정 미활성, App Store 무료 판매, 카카오 웹훅 3초 응답뿐이다. R2 요금·무료 한도와 네이버 SMTP 일일 한도는 기록이 없다.
