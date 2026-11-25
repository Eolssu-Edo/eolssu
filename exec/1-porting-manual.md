# 1. 포팅 매뉴얼 (GitLab 클론 이후 빌드·배포)

- 작업: S15P31E102-1106
- 기준 소스: `develop` `9d8c7dfc4`(2026-11-17). 운영 서버 값은 2026-11-17 edo1에서 읽기 전용으로 확인했다.
- 비밀값은 적지 않는다. 변수 이름과 용도만 적고 **[비밀]**로 표시한다. 운영 실제 값은 edo1의 `/opt/eolssu/.env`(root 0600)에만 있다.
- 외부 서비스 가입·설정은 [2. 외부 서비스 정보](2-external-services.md), DB 덤프는 [3-eolssu-db-dump.sql](3-eolssu-db-dump.sql), 시연 절차는 [4. 시연 시나리오](4-demo-scenario.md)를 따른다.

## 목차

1. [시스템 구성](#1-시스템-구성)
2. [사용 제품과 버전](#2-사용-제품과-버전)
3. [소스 받기와 저장소 구조](#3-소스-받기와-저장소-구조)
4. [영역별 빌드](#4-영역별-빌드)
5. [환경 변수](#5-환경-변수)
6. [서버 배포 절차](#6-서버-배포-절차)
7. [배포 특이사항](#7-배포-특이사항)
8. [DB 접속 정보와 주요 프로퍼티 파일](#8-db-접속-정보와-주요-프로퍼티-파일)
9. [로컬 개발 환경](#9-로컬-개발-환경)

## 1. 시스템 구성

```text
[Android·iOS 앱 / 웹 브라우저]
          │ HTTPS (k15e102.p.ssafy.io)
          ▼
edo1 ─ Nginx(frontend 컨테이너: 웹 정적 파일 + /api 프록시 + TLS)
          ├─ backend / backend-green (Spring Boot, 블루그린 슬롯, :8080)
          │     ├─ MySQL 8.4 (eolssu DB)
          │     ├─ ai (연주 분석 FastAPI, :8000)
          │     ├─ omr (악보 인식 FastAPI, :8000)
          │     ├─ playback (내 악보 음원 생성, :8000)
          │     └─ Cloudflare R2 (녹음·음원 객체 저장소, 외부)
          └─ Jenkins, Prometheus·Grafana (loopback 전용)
edo2 ─ 암호화 백업 보관·격리 복원, MySQL 읽기 전용 복제본, 대기 환경, Mattermost 점검 중계
```

| 서버 | OS | 사양 | 역할 |
| --- | --- | --- | --- |
| edo1 | Ubuntu 24.04.4 LTS | 4 vCPU, 약 16GB RAM | 운영 서비스 전체, Jenkins, 모니터링 |
| edo2 | Ubuntu 24.04 | - | 백업·복원, 복제본, 장애 대비 대기 환경 |

- 도메인: `k15e102.p.ssafy.io` (Let's Encrypt TLS)
- 방화벽: 외부에는 22·80·443만 연다. Jenkins·Grafana·Prometheus·phpMyAdmin은 `127.0.0.1`에만 바인딩하고 SSH 터널로 접속한다.

## 2. 사용 제품과 버전

### 2-1. Backend (JVM·WAS)

| 항목 | 버전·설정 | 근거 파일 |
| --- | --- | --- |
| JDK | Java 21 (Gradle toolchain) | `backend/build.gradle.kts` |
| 빌드 이미지 | `eclipse-temurin:21-jdk` | `backend/Dockerfile` |
| 실행 이미지 | `eclipse-temurin:21-jre` + ffmpeg 8.0.1 → `eolssu-backend-runtime:21-jre-ffmpeg8.0.1` | `infra/runtime/backend-runtime.Dockerfile` |
| Kotlin | 2.3.21 | `backend/gradle/libs.versions.toml` |
| Spring Boot | 4.1.1 (dependency-management 1.1.7) | 같은 파일 |
| WAS | Spring Boot 내장 Tomcat(`spring-boot-starter-webmvc`), 포트 8080 | `application.yml` `server.port` |
| MyBatis Spring Boot Starter | 4.0.0 | `libs.versions.toml` |
| Flyway, MySQL Connector/J | Spring Boot 관리 버전 | 같은 파일 |
| 기타 | springdoc-openapi 3.2.0, AWS SDK v2 2.56.0, PDFBox 3.0.5 | 같은 파일 |
| Gradle Wrapper | 9.8.2 (`org.gradle.jvmargs=-Xmx1g`) | `backend/gradle/wrapper/gradle-wrapper.properties`, `backend/gradle.properties` |
| 산출물 | `backend/build/libs/eolssu-0.0.1-SNAPSHOT.jar` (`*-plain.jar`는 실행용 아님) | `backend/build.gradle.kts` |

### 2-2. Frontend (Kotlin Multiplatform)

| 항목 | 버전·설정 | 근거 파일 |
| --- | --- | --- |
| Kotlin | 2.4.20 | `frontend/gradle/libs.versions.toml` |
| Compose Multiplatform | 1.12.1 (material3 1.12.0-alpha03) | 같은 파일 |
| Android Gradle Plugin | 9.1.1 | 같은 파일 |
| compileSdk / minSdk / targetSdk | 37 / 24 / 37 | 같은 파일 |
| Android JVM target | 11 | `frontend/androidApp/build.gradle.kts` |
| Gradle Wrapper | 9.5.1 (Daemon JVM: Azul JDK 21, 없으면 자동 다운로드) | `frontend/gradle/wrapper/gradle-wrapper.properties`, `gradle-daemon-jvm.properties` |
| Gradle 메모리 | `-Xmx4096M`, Kotlin daemon `-Xmx3072M` | `frontend/gradle.properties` |
| 주요 라이브러리 | Ktor 3.6.0, Koin 4.2.2, kotlinx-coroutines 1.11.0, media3 1.11.1 | `libs.versions.toml` |
| 웹 | Kotlin/Wasm (운영 배포 대상) | `frontend/README.md` |
| Android 패키지 | `com.ssafy.eolssu`, versionCode 2 / versionName 1.0.0 | `androidApp/build.gradle.kts` |
| iOS | 최소 iOS 18.2, Swift 5.0, 번들 ID `com.ssafy.eolssu`, 1.0.1 (7) | `iosApp.xcodeproj/project.pbxproj`, `iosApp/Configuration/Config.xcconfig` |

### 2-3. AI·악보 인식·음원 생성 (Python)

| 항목 | 버전·설정 | 근거 파일 |
| --- | --- | --- |
| Python | 3.12 (uv 관리형) | `ai/.python-version` |
| uv | 0.13.6 (`ghcr.io/astral-sh/uv`) | `ai/Dockerfile` |
| ai 베이스 이미지 | `ubuntu:26.04` + ffmpeg 8.0.1 | `ai/Dockerfile` |
| ai 주요 패키지 | FastAPI 0.162.0, uvicorn 0.54.0, librosa 1.0.0, numpy 2.5.4, scipy 1.18.1, numba 0.68.0, httpx 0.28.1 | `ai/uv.lock` |
| omr 베이스 이미지 | `ubuntu:24.04`, Audiveris 5.11.0, Node.js 18.19.1, torch 2.15.0+cpu, jeongganbo-omr v1.0.0 | `ai/omr.Dockerfile`, `ai/omr-scan-requirements.in` |
| playback | `ubuntu:26.04`, ffmpeg 8.0.1, Python 3.12, mido 1.3.3, jsonschema 4.26.0 | `data/playback.Dockerfile` |

### 2-4. 인프라

| 항목 | 버전 | 근거 |
| --- | --- | --- |
| Docker Engine / Compose | 29.9.2 / 5.6.1 | edo1 실측(2026-11-17) |
| 웹서버(리버스 프록시) | `nginx:1.30.6-alpine` | `infra/runtime/compose.yml` |
| DB | `mysql:8.4.13` | 같은 파일 |
| CI/CD | `jenkins/jenkins:2.580.1-jdk21` | `infra/jenkins/controller/compose.yml` |
| 모니터링 | Prometheus v3.5.0, Grafana 11.3.0, node-exporter v1.9.1 | `infra/monitoring/compose.yml` |
| DB 웹 조회 | `phpmyadmin:5.2.3-apache` | `infra/mysql-admin/compose.yml` |
| 인증서 | Certbot 2.9 (edo1) | `infra/runtime/README.md` |

### 2-5. IDE

| IDE | 버전 | 용도 |
| --- | --- | --- |
| Android Studio | 2026.2.1 (최신 안정판에서 확인) + Kotlin Multiplatform 플러그인 | frontend(Android·Web·shared) |
| Xcode | 16.2 이상 (프로젝트 `CreatedOnToolsVersion = 16.2`) | iOS 빌드·서명 (macOS 전용) |
| IntelliJ IDEA | 버전 기록 없음. `backend/`를 Gradle 프로젝트로 연다 | backend |
| VS Code 등 | 버전 기록 없음. `ai/`는 uv 가상 환경 사용 | ai·data |

## 3. 소스 받기와 저장소 구조

```sh
git clone https://lab.ssafy.com/s15-final/S15P31E102.git
cd S15P31E102
git switch develop          # 통합·CI/CD 기준 브랜치
```

```text
S15P31E102/
├── frontend/   # KMP 앱 (androidApp, iosApp, webApp, shared)
├── backend/    # Spring Boot API
├── ai/         # 연주 분석 API(ai_service), 악보 인식 API(omr_service)
├── data/       # 악보·음원 데이터, playback(음원 생성기)
├── infra/      # 운영 Compose·Nginx·Jenkins·백업·릴리스 스크립트
├── docs/       # 스펙·계약·지식 문서
└── exec/       # 제출 산출물 (이 문서)
```

- Android Studio에서 열 때 **저장소 경로에 한글이 없어야** 한다.
- `data/raw/`, `data/processed/`, `ai/data/`, `ai/models/`는 Git에서 제외한다. 서비스 실행에 필요한 음원 합성 라이브러리 원본(서울예술과학센터 CAT 국악 가상악기)은 저장소에 없으며, 서버의 `PLAYBACK_ASSETS_DIRECTORY`(`/opt/eolssu/playback-assets`)에 별도로 둔다.

## 4. 영역별 빌드

운영에서는 Jenkins가 아래 명령을 실행한다. 수동으로 재현할 때도 같은 명령을 사용한다.

### 4-1. Backend

```sh
cd backend
./gradlew clean build            # 컴파일 + 단위 테스트 + bootJar
./gradlew bootJar                # 실행 JAR만 생성
java -jar build/libs/eolssu-0.0.1-SNAPSHOT.jar
```

Jenkins `eolssu-backend`(develop 1분 polling, 노드 `edo1-host`)의 단계는 다음과 같다.

```sh
./gradlew clean compileKotlin compileTestKotlin
./gradlew test                    # 단위 테스트(database·media 태그 제외)
./gradlew mediaTest               # 실제 ffmpeg/ffprobe 사용
python3 -B ../infra/jenkins/run-database-tests.py -- ./gradlew databaseTest   # 일회용 MySQL 컨테이너
./gradlew bootJar build
docker compose --env-file /opt/eolssu/config/ci.env.example -f /opt/eolssu/config/compose.yml --profile backend-slots config --quiet
python3 -B infra/jenkins/build-backend-image.py "$GIT_COMMIT" --dockerfile /opt/eolssu/config/backend-artifact.Dockerfile
sudo -n /opt/eolssu/bin/deploy-backend "$GIT_COMMIT"
```

소스로 Docker 이미지를 만들 때는 **저장소 루트**를 context로 쓴다(`data/reference/`를 함께 복사하기 때문이다).

```sh
docker build -f backend/Dockerfile -t eolssu-backend:check .
```

### 4-2. Frontend 웹 (Kotlin/Wasm)

```sh
cd frontend
./gradlew :webApp:wasmJsBrowserDistribution
# 산출물: frontend/webApp/build/dist/wasmJs/productionExecutable/ (약 13MB)
```

Jenkins `eolssu-frontend-web-수동배포용`(노드 `edo1-web`, 파라미터 `REVISION`=40자리 SHA)은 Wasm·JS 테스트(`:shared:wasmJsTest`, `:shared:jsTest`, `CHROME_BIN=/usr/bin/google-chrome` 필요) 후 아래를 실행한다.

```sh
./gradlew :webApp:wasmJsBrowserProductionWebpack :webApp:wasmJsBrowserDistribution
docker build --file /opt/eolssu/config/frontend.Dockerfile --tag "eolssu-frontend:ci-${GIT_COMMIT}" frontend/webApp/build/dist/wasmJs/productionExecutable
sudo -n /opt/eolssu/bin/deploy-frontend "$GIT_COMMIT"
```

### 4-3. Frontend Android

```sh
cd frontend
# local.properties에 sdk.dir 지정 (커밋 금지), JAVA_HOME은 JDK 17 이상
./gradlew :androidApp:assembleDebug
# 산출물: androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

릴리스 APK/AAB는 서명 환경 변수(`EOLSSU_STORE_FILE`, `EOLSSU_STORE_PASSWORD`, `EOLSSU_KEY_ALIAS`, `EOLSSU_KEY_PASSWORD`)를 주입한 뒤 빌드한다. release 키가 없으면 스크립트가 중단되며 debug 키로 대체하지 않는다.

```powershell
./tools/build-android-release.ps1 -VersionCode 2 -VersionName '1.0.0'
python tools/inspect_android_package.py <apk> <aab>
```

- debug 빌드의 API 주소는 기본 `https://k15e102.p.ssafy.io`이다. 로컬 서버를 쓰려면 `-Peolssu.debugApiBaseUrl=http://127.0.0.1:<port>`와 `adb reverse tcp:<port> tcp:<port>`를 사용한다(loopback만 허용).
- release 빌드의 API 주소는 `https://k15e102.p.ssafy.io`로 고정된다.

### 4-4. Frontend iOS (macOS 전용)

```sh
cd frontend
./gradlew :shared:compileKotlinIosArm64 :shared:iosSimulatorArm64Test
```

1. `frontend/iosApp/Configuration/Config.local.xcconfig`(Git 제외)에 `TEAM_ID`, `PRODUCT_BUNDLE_IDENTIFIER`, `CODE_SIGN_ENTITLEMENTS`를 개인 값으로 적는다.
2. Xcode에서 `frontend/iosApp/iosApp.xcodeproj`를 열고 scheme `iosApp`으로 실행한다.
3. Ad Hoc IPA는 Jenkins `eolssu-ios-qa`(Mac 에이전트)가 `xcodebuild archive`·`-exportArchive`(method `release-testing`)로 만들고 `https://k15e102.p.ssafy.io/ipas/`에 게시한다.

### 4-5. AI·OMR·Playback 이미지

```sh
# 연주 분석 (약 1.65GB, 디스크 여유 2GB 이상)
docker build -t eolssu-ai:runtime ai

# 악보 인식 (BuildKit 필요, 저장소 루트 context, 약 4.0GB)
DOCKER_BUILDKIT=1 docker build -f ai/omr.Dockerfile --target scanner -t eolssu-omr:runtime .

# 내 악보 음원 생성 (저장소 루트 context)
docker build -f data/playback.Dockerfile -t eolssu-playback:runtime .
```

Jenkins `eolssu-ai`, `eolssu-omr`, `eolssu-playback`(노드 `edo1-ai`)은 `infra/jenkins/build-ai-image.py`·`build-service-image.py`의 `plan → build → test → smoke` 순서로 실행한 뒤 `deploy-ai`·`deploy-service-image`로 배포한다.

## 5. 환경 변수

운영 backend는 edo1 `/opt/eolssu/.env` → `infra/runtime/compose.yml`의 `environment` → `application.yml`의 `${...}` 순서로 값을 받는다. 템플릿은 `infra/runtime/.env.example`이다.

### 5-1. 런타임·DB

| 변수 | 용도 | 기본값·예시 |
| --- | --- | --- |
| `WEB_DIST_DIR` | 웹 정적 파일 릴리스 경로 | `/opt/eolssu/releases/<SHA>/web` |
| `BACKEND_JAR` | 실행 JAR 경로(컨테이너 `/app/app.jar`로 읽기 전용 마운트) | `/opt/eolssu/releases/<SHA>/app.jar` |
| `BACKEND_SLOT` | 활성 블루그린 슬롯 | `backend` / `backend-green` (배포 도구가 기록) |
| `BACKEND_SHARED_DIR` | 슬롯 공유 디렉터리 | `/opt/eolssu/backend-shared` |
| `HTTP_BIND_ADDRESS`, `HTTP_PORT` | Nginx 바인딩 | `0.0.0.0`, `80` (TLS 443은 `compose.tls.yml`) |
| `MYSQL_DATABASE`, `MYSQL_USER` | DB 이름·앱 계정 | `eolssu`, `eolssu` |
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | DB 비밀번호 | **[비밀]** |
| `MYSQL_DATA_VOLUME` | 외부 MySQL 볼륨 | `eolsu-runtime_mysql_data` |
| `SPRING_PROFILES_ACTIVE` | Spring 프로필 | `ops` (Compose 고정) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Compose가 생성 | `jdbc:mysql://mysql:3306/eolssu?connectTimeout=2000&socketTimeout=2000` |
| `SERVER_PORT` | 앱 포트 | `8080` |

### 5-2. 인증

| 변수 | 용도 | 비고 |
| --- | --- | --- |
| `AUTH_JWT_SECRET` | 로그인 토큰 HS256 서명 키(32바이트 이상) | **[비밀]** |
| `AUTH_CODE_HMAC_SECRET` | 이메일 인증번호 HMAC 키 | **[비밀]** |
| `AUTH_MAIL_HOST`, `AUTH_MAIL_PORT` | SMTP | `smtp.naver.com`, `587` |
| `AUTH_MAIL_USERNAME`, `AUTH_MAIL_PASSWORD` | 발송 계정, 네이버 애플리케이션 비밀번호 | **[비밀]** |
| `AUTH_MAIL_FROM`, `AUTH_MAIL_FROM_NAME` | 발신 주소·이름 | 이름 `얼쑤` |
| `AUTH_MAIL_MODE` | `smtp` / `memory` / `unavailable` | 기본 `smtp`, 로컬은 `memory` |
| `AUTH_COOKIE_SECURE` | 쿠키 Secure 속성 | 기본 `true`, 로컬 HTTP는 `false` |
| `AUTH_OAUTH_{KAKAO,GOOGLE,NAVER}_CLIENT_ID` | 소셜 로그인 클라이언트 ID | |
| `AUTH_OAUTH_{KAKAO,GOOGLE,NAVER}_CLIENT_SECRET` | 소셜 로그인 시크릿 | **[비밀]** |
| `AUTH_OAUTH_{KAKAO,GOOGLE,NAVER,APPLE}_REDIRECT_URIS` | 허용 복귀 주소(쉼표 구분) | [외부 서비스 6장](2-external-services.md#6-소셜-로그인-oauth-20--oidc) |
| `AUTH_KAKAO_WEBHOOK_APP_ID` | 카카오 연결 해제 웹훅 앱 ID | |
| `AUTH_OAUTH_APPLE_CLIENT_ID`, `_TEAM_ID`, `_KEY_ID` | Sign in with Apple 식별자 | |
| `AUTH_OAUTH_APPLE_PRIVATE_KEY` | Apple .p8 키 | **[비밀]** |
| `AUTH_OAUTH_APPLE_DEV_CLIENT_ID`, `_DEV_REDIRECT_URIS` | 개발 앱(`com.ssafy.eolssu.dev`) | |
| `AUTH_ANDROID_APP_SHA256_FINGERPRINTS`, `AUTH_ANDROID_QA_APP_SHA256_FINGERPRINTS` | App Links 인증서 지문(공개값) | |
| `AUTH_NON_EXPIRING_USER_IDS` | 만료 없는 테스트 회원 ID 목록 | 기본 빈 값 |
| `LEGAL_PUBLICATION_ENABLED` | 약관 HTML 공개 여부 | 기본 `false` |
| `SECURITY_ALLOWED_ORIGINS` | CORS 허용 출처(웹 로컬 개발용) | 운영은 빈 값(같은 출처) |

### 5-3. 저장소·내부 서비스

| 변수 | 용도 | 비고 |
| --- | --- | --- |
| `RECORDING_STORAGE_{ENDPOINT,BUCKET,REGION}` | 녹음 R2 버킷 | 버킷 `eolssu-recordings`, REGION `auto` |
| `RECORDING_STORAGE_{ACCESS_KEY_ID,SECRET_ACCESS_KEY}` | 녹음 R2 토큰 | **[비밀]** |
| `RECORDING_STORAGE_TYPE` | `s3` / `local` | 기본 `s3` |
| `RECORDING_STORAGE_LOCAL_*` | 로컬 디스크 전환용(`HOST_DIRECTORY`, `READ_BASE_URL`, `SIGNING_SECRET`) | `SIGNING_SECRET` **[비밀]** |
| `SHEET_STORAGE_*` (5종) | 내 악보 음원 R2 버킷(`eolssu-sheet-audio`) | 키 **[비밀]** |
| `REFERENCE_AUDIO_STORAGE_*` (5종) | 학습·모범 음원 읽기 서명 | 키 **[비밀]** |
| `ANALYSIS_AI_BASE_URL` | 연주 분석 API | Compose 고정 `http://ai:8000` |
| `SHEET_CONVERSION_OMR_BASE_URL` | 악보 인식 API | `http://omr:8000` |
| `SHEET_IMPORT_READ_BASE_URL` | OMR이 원본을 읽는 내부 주소 | `http://backend:8080/api/internal/sheet-imports` |
| `SHEET_AUDIO_BASE_URL`, `SHEET_AUDIO_TIMEOUT_SECONDS` | 음원 생성 API, 제한 시간 | 운영 `http://playback:8000`, `100` |
| `PLAYBACK_ASSETS_DIRECTORY`, `PLAYBACK_JANGGU_DIRECTORY`, `PLAYBACK_TIMEOUT_SECONDS` | playback 자원 경로 | `/opt/eolssu/playback-assets`, `/opt/eolssu/janggu`, `90` |

### 5-4. 컨테이너 내부 고정 값

| 서비스 | 변수 |
| --- | --- |
| ai | `NUMBA_CACHE_DIR=/tmp/numba-cache` |
| omr | `JGB_GENERATOR_DIR=/opt/jeongganbo-generator`, `AUDIVERIS_CMD=/opt/audiveris/bin/Audiveris`, `AUDIVERIS_VERSION=5.11.0`, `TESSDATA_PREFIX=/opt/tessdata`, `OMP_NUM_THREADS=2` |
| playback | `PLAYBACK_ASSETS_JSON=/assets/assets.json`, `PLAYBACK_JANGGU_SAMPLES=/janggu` |

### 5-5. Frontend 빌드 속성

| 이름 | 종류 | 용도 |
| --- | --- | --- |
| `sdk.dir` | `frontend/local.properties` (Git 제외) | Android SDK 경로 |
| `eolssu.debugApiBaseUrl` | Gradle 속성 | debug API 주소(기본 운영 도메인, 변경은 loopback만) |
| `eolssu.versionCode`, `eolssu.versionName` | Gradle 속성 | 기본 2 / 1.0.0 |
| `eolssu.qaLive`, `eolssu.qaSkipConsent` | Gradle 속성 | QA 전용, release에서는 강제로 false |
| `EOLSSU_STORE_FILE`, `EOLSSU_STORE_PASSWORD`, `EOLSSU_KEY_ALIAS`, `EOLSSU_KEY_PASSWORD` | 환경 변수 | release 서명 **[비밀]** |
| BuildConfig `API_BASE_URL` | 빌드 결과 | release는 `https://k15e102.p.ssafy.io` |
| 웹 API 주소 | 실행 시 결정 | `window.location.origin` (같은 출처 `/api`) |
| `TEAM_ID`, `PRODUCT_BUNDLE_IDENTIFIER` | `Config.local.xcconfig` (Git 제외) | iOS 개인 서명 |

### 5-6. 기타 env 예시 파일

| 파일 | 용도 |
| --- | --- |
| `infra/runtime/.env.example` | 운영 `/opt/eolssu/.env` 템플릿 |
| `infra/runtime/ci.env.example` | CI의 Compose 문법 검사용 더미값(서비스 기동에 사용하지 않음) |
| `backend/.env.example` | 로컬 backend 실행 예시(Spring이 자동으로 읽지 않으므로 셸에 export) |
| `.env.dev.example`, `.env.prod.example` | 루트 예시 Compose 치환 값 |
| `infra/r2/.env.example` | R2 업로드 도구 |
| `infra/monitoring/.env.example` | `GRAFANA_ADMIN_PASSWORD` **[비밀]** |

## 6. 서버 배포 절차

### 6-1. 새 서버 최초 구성

1. Ubuntu 24.04에 Docker Engine과 Compose 플러그인을 설치한다.
2. 저장소를 서버 소스 체크아웃 `/opt/app`에 클론하고, 운영 설정 디렉터리와 블루그린 라우팅 파일을 준비한다.

   ```sh
   sudo git clone https://lab.ssafy.com/s15-final/S15P31E102.git /opt/app
   sudo install -d -o root -g root -m 0755 /opt/eolssu/config /opt/eolssu/config/backend-route /opt/eolssu/bin /opt/eolssu/releases
   sudo install -d -o 10001 -g 10001 -m 0700 /opt/eolssu/backend-shared
   printf '%s\n' 'set $backend http://backend:8080;' | sudo tee /opt/eolssu/config/backend-route/active.conf >/dev/null
   ```

   `infra/scripts/install-runtime-compose.py`를 `/opt/eolssu/bin/install-runtime-compose`(root 0755)로 설치한 뒤, 검토한 `infra/runtime/compose.yml`을 넘겨 `/opt/eolssu/config/compose.yml`로 교체한다. 이 도구는 관리 중인 `nginx.conf`와 운영 도메인으로 치환한 `nginx.tls.conf`도 함께 설치한다. 배포 도구(`infra/jenkins/deploy-*.py`)와 `backend-runtime.Dockerfile`·`backend-artifact.Dockerfile`·`frontend.Dockerfile`도 같은 위치(`/opt/eolssu/bin`, `/opt/eolssu/config`)에 둔다.

   ```sh
   sudo /opt/eolssu/bin/install-runtime-compose /opt/app/infra/runtime/compose.yml
   ```

   서버 설정(`/opt/eolssu/config/*`, `/opt/eolssu/bin/*`)은 MR 병합으로 자동 반영되지 않는다. 변경할 때마다 위 설치를 다시 실행한다. 상세 절차는 `infra/runtime/README.md`를 따른다.
3. `infra/runtime/.env.example`을 `/opt/eolssu/.env`로 복사해 root 0600으로 두고 비밀값을 채운다.
4. 외부 MySQL 볼륨을 만든다: `docker volume create eolsu-runtime_mysql_data`
5. backend 실행 이미지를 서버에서 빌드한다.

   ```sh
   D=$(mktemp -d); cp /opt/eolssu/config/backend-runtime.Dockerfile "$D/Dockerfile"
   sudo docker build --tag eolssu-backend-runtime:21-jre-ffmpeg8.0.1 "$D"
   ```

6. ai·omr·playback 이미지를 [4-5](#4-5-aiomrplayback-이미지)대로 빌드하고 `:runtime` 태그를 붙인다. playback 음원 자원을 `/opt/eolssu/playback-assets`, `/opt/eolssu/janggu`에 둔다.
7. TLS 인증서를 발급한다(DNS가 서버를 가리키고 80·443이 열려 있어야 한다).

   ```sh
   sudo apt-get install -y certbot
   cd /opt/app/infra && ./scripts/init-letsencrypt.sh --domain k15e102.p.ssafy.io
   ```

   스크립트가 필요할 때 sudo로 재실행하며, 발급·Nginx 검사를 통과하면 `compose.tls.yml`로 443을 추가한다.

8. DB를 준비한다. 빈 DB에서는 첫 backend 배포가 Flyway V1~V46을 적용한다. 기존 데이터를 옮길 때는 [3-eolssu-db-dump.sql](3-eolssu-db-dump.sql)을 복원한다.

   ```sh
   sudo docker compose --env-file /opt/eolssu/.env -f /opt/eolssu/config/compose.yml -f /opt/eolssu/config/compose.tls.yml up -d mysql
   sudo docker exec -i eolssu-runtime-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' < ./3-eolssu-db-dump.sql   # 이 exec 폴더의 덤프를 서버로 복사한 경로
   ```

   MySQL 컨테이너는 첫 기동 때 `MYSQL_DATABASE`·`MYSQL_USER`로 `eolssu` DB와 앱 계정을 만든다. 덤프에는 `flyway_schema_history`(V46까지)가 들어 있으므로, 복원 후 배포 도구는 V47 이후만 적용한다.

9. Jenkins 컨트롤러를 `infra/jenkins/controller/compose.yml`로 띄우고(`127.0.0.1:8080`), 노드 `edo1-host`·`edo1-web`·`edo1-ai`와 Credentials `eolssu-gitlab`을 등록한 뒤 `infra/jenkins/*.Jenkinsfile`로 잡을 만든다. 접속은 `ssh -N -L 18080:127.0.0.1:8080 edo1`로 한다.
10. 첫 backend·웹 배포를 실행하고 확인한다.

    ```sh
    curl -fsS https://k15e102.p.ssafy.io/api/actuator/health/readiness   # {"status":"UP"}
    curl -fsS https://k15e102.p.ssafy.io/__eolssu_release.json           # 웹 릴리스 SHA
    ```

### 6-2. 일상 배포

| 대상 | 트리거 | 방식 |
| --- | --- | --- |
| backend | `develop` 병합 → Jenkins 1분 polling | 블루그린 자동 배포 |
| 웹 | Jenkins `eolssu-frontend-web-수동배포용`을 SHA로 수동 실행 | 정적 파일 교체 |
| ai·omr·playback | `develop` 병합 시 이미지 입력 파일이 바뀐 경우만 | 이미지 재빌드 후 해당 서비스만 재생성 |
| Android | Jenkins `eolssu-android-qa`(backend 배포 후 예약) / Play Console 수동 업로드 | APK·AAB |
| iOS | Jenkins `eolssu-ios-qa`(iOS 입력 변경 시) / TestFlight 수동 업로드 | Ad Hoc IPA |

## 7. 배포 특이사항

### 7-1. 운영 Compose 서비스 (`eolssu-runtime`)

| 서비스 | 이미지 | 포트·자원 |
| --- | --- | --- |
| frontend | `nginx:1.30.6-alpine` | 호스트 80·443, 내부 8081(메트릭·녹음 읽기) |
| backend / backend-green | `eolssu-backend-runtime:21-jre-ffmpeg8.0.1` | 내부 8080, UID 10001, profile `backend-slots`, readiness healthcheck |
| ai | `eolssu-ai:runtime` | 내부 8000, 메모리 6GiB, 읽기 전용 파일시스템 |
| omr | `eolssu-omr:runtime` | 내부 8000, 메모리 2GiB, CPU 2 |
| playback | `eolssu-playback:runtime` | 내부 8000, profile `playback`, 메모리 1GiB, CPU 1 |
| mysql | `mysql:8.4.13` | 호스트 포트 없음, 외부 볼륨 `eolsu-runtime_mysql_data` |

- backend는 profile `backend-slots`라서 profile 없이 `up`하면 기동하지 않는다. 슬롯은 배포 도구가 관리한다.
- 컨테이너 로그는 syslog 드라이버로 보내므로 `docker logs`로 보이지 않는다. `/var/log/eolssu/ordinary/eolssu-<서비스>.<날짜>.log`에서 확인한다.

### 7-2. Nginx 라우팅 (`infra/runtime/nginx.conf`, `nginx.tls.conf.template`)

| 경로 | 처리 |
| --- | --- |
| `/.well-known/acme-challenge/` | HTTP로 제공(인증서 갱신). 나머지 HTTP는 HTTPS로 308 전환 |
| `= /.well-known/assetlinks.json` | backend (Android App Links) |
| `= /api/actuator/health/readiness` | backend. 그 외 `/api/actuator`는 404 |
| `^~ /api/internal/` | 404 (내부 전용) |
| `/api/` | backend, `client_max_body_size 25m` |
| `/ipas/` | iOS QA IPA 게시 |
| `/` | 웹 SPA(`try_files ... /index.html`). 해시 이름 Wasm은 1년 캐시, 나머지 no-cache |

### 7-3. backend 블루그린 배포 (`/opt/eolssu/bin/deploy-backend`)

1. 공유 lock `/run/lock/eolssu-backend-deploy.lock`을 잡고 배포 전 백업(`backup.py send prechange`)을 edo2로 보낸다. 백업에 실패하면 배포하지 않는다.
2. 후보 이미지 라벨·배포 순서를 검사한다(이미 배포된 후속 SHA를 덮어쓰지 않는다).
3. 후보 컨테이너를 내부 네트워크에서 기동해 확인하고, JAR을 `/opt/eolssu/releases/<SHA>/app.jar`로 추출한다.
4. Flyway 마이그레이션을 적용한다(7-4).
5. 비활성 슬롯(`backend` ↔ `backend-green`)을 기동하고 readiness를 확인한 뒤 Nginx upstream(`active.conf`)을 바꾸고 reload한다. 실패하면 이전 라우트로 자동 복귀한다.
6. 이전 슬롯을 최대 250초 drain한다. 진행 중인 분석은 최대 65초까지 마무리한다.
7. 배포 후 백업(`postmigration` 또는 `postchange`)을 보낸다.

배포 기록은 `/opt/eolssu/deployment.json`, 중단된 배포 복구는 `sudo /opt/eolssu/bin/deploy-backend --recover`이다.

### 7-4. Flyway 마이그레이션

- 운영 `ops` 프로필은 Flyway를 끈다(`application-ops.yml`). 배포 도구가 슬롯 교체 직전에 후보 JAR을 `db` 프로필 일회성 컨테이너(`eolssu-migrate-<SHA12>`, 300초 제한)로 실행해 적용한다. 로그는 `/opt/eolssu/releases/<SHA>/migration.log`이다.
- DROP·RENAME·DELETE 등 파괴적 변경은 `/opt/eolssu/migration-approvals/<파일명>`에 SHA-256 승인 파일이 있어야 적용된다.
- 실패 시 `flyway repair`를 쓰지 않는다. 스키마를 확인하고 `prechange` 백업과 대조한다.

### 7-5. 웹 배포

`deploy-frontend`가 정적 파일 SHA-256과 Wasm MIME 타입을 검사한 뒤 `/opt/eolssu/releases/<SHA>/web`에 보관하고, `.env`의 `WEB_DIST_DIR`만 원자적으로 바꿔 frontend 컨테이너만 재생성한다. 기록은 `/opt/eolssu/frontend-deployment.json`이다.

### 7-6. ai·omr·playback 배포

- 이미지 입력 파일의 Git 해시를 실행 중 이미지 라벨(`com.eolssu.<서비스>.source`)과 비교해 같으면 건너뛴다. `ai/src`가 바뀌면 ai와 omr이 함께 다시 빌드된다.
- omr·playback은 대기열(`sheet_conversions`·`sheet_audios`의 QUEUED·RUNNING)이 비기를 최대 20분 기다린 뒤 교체한다.
- 검사에 실패하면 `:rollback` 태그로 자동 복구한다.

### 7-7. 롤백

| 대상 | 방법 |
| --- | --- |
| backend·웹 | 오래된 SHA 배포는 도구가 거부한다. 되돌리는 커밋을 `develop`에 병합하는 roll-forward로 처리한다 |
| 웹(긴급) | journal의 `previous_web` 값으로 `WEB_DIST_DIR`를 되돌리고 frontend만 재생성 |
| ai·omr·playback | 잡을 비활성화하고 `sudo docker tag eolssu-<서비스>:rollback eolssu-<서비스>:runtime` 뒤 해당 서비스만 `up -d --no-deps --pull never` |
| DB | JAR 롤백으로 스키마는 돌아가지 않는다. edo2 백업에서 격리 복원 후 대조 |
| edo1 장애 | `infra/standby/failover-runbook.md`에 따라 edo2 대기 환경으로 전환(DNS 변경은 SSAFY 요청) |

### 7-8. 백업

edo1 → edo2 암호화 전송(`/opt/eolssu-backup/backup.py`, systemd 타이머). DB는 매시 10분, 설정은 매일 03:20과 배포 전후, Grafana 03:30, Jenkins 03:40에 백업하고 일요일 04:40에 격리 복원 검증을 한다. 세부 사항은 `infra/backup/README.md`를 따른다.

### 7-9. 자원 제약

- edo1 4코어를 서비스·MySQL·Jenkins가 함께 쓴다. ai는 120초 녹음 채점에 약 3GiB를 쓰며, backend가 분석을 동시에 최대 2건 보낸다.
- 업로드 한도: Nginx 25MiB, backend 판정 20MiB
- 이미지 크기: ai 약 1.65GB, omr 약 4.0GB. 빌드 전 디스크 여유를 확인한다.

## 8. DB 접속 정보와 주요 프로퍼티 파일

### 8-1. 접속 정보

| 항목 | 운영 | 로컬 개발 | 테스트 |
| --- | --- | --- | --- |
| DBMS | MySQL 8.4.13 | MySQL 8.4 | MySQL 8.4 (일회용 컨테이너) |
| 호스트 | Compose 내부 `mysql:3306` (외부 포트 없음) | `localhost:3306` | `localhost:3306` |
| DB 이름 | `eolssu` | `eolssu` | `eolssu_test` |
| 앱 계정 | `eolssu` | `eolssu` | `eolssu_test` |
| 비밀번호 | `MYSQL_PASSWORD` **[비밀]** | `DB_PASSWORD` | `TEST_DB_PASSWORD` |
| 문자셋·시간대 | utf8mb4, 연결 시 `SET time_zone='+00:00'` (UTC 저장) | 같음 | 같음 |

- 그 밖의 계정: `root`(컨테이너 `MYSQL_ROOT_PASSWORD` **[비밀]**), Grafana 읽기 전용 `grafana`@`%`(일부 테이블 SELECT)
- 운영 DB 조회: `ssh -N -L 8081:127.0.0.1:8081 edo1` 후 phpMyAdmin, 또는 `docker exec eolssu-runtime-mysql-1 mysql ...`
- 집계 DB `eolssu_mart`의 스키마는 `data/mart/schema.sql`에 있다.

### 8-2. 프로퍼티·설정 파일 목록

| 파일 | 정의 내용 |
| --- | --- |
| `backend/src/main/resources/application.yml` | 공통 설정. `server.port`, `eolssu.*`(인증·저장소·분석·OMR·playback)의 `${...}` 참조, actuator `/api/actuator`(health·prometheus), springdoc `/api/swagger-ui.html`(기본 비활성) |
| `backend/src/main/resources/application-db.yml` | `db` 프로필. `spring.datasource.url/username/password = ${DB_URL}/${DB_USERNAME}/${DB_PASSWORD}`, Flyway 활성(`classpath:db/migration`, `clean-disabled: true`), MyBatis Mapper 경로 |
| `backend/src/main/resources/application-ops.yml` | 운영 `ops` 프로필. 같은 DB 키, Hikari 최대 풀 10·연결 제한 2초, Flyway 비활성, readiness에 DB 포함 |
| `backend/src/test/resources/application.properties` | 테스트용 공개 고정 키 |
| `backend/src/main/resources/db/migration/V1__initial_schema.sql` ~ `V46__song_sigimsae_placements.sql` | Flyway 스키마·기준 데이터(46개, 번호 연속) |
| `backend/src/main/resources/mybatis/**/*.xml` | MyBatis Mapper |
| `infra/runtime/compose.yml`, `compose.tls.yml` | 운영 서비스·`DB_URL` 구성·`MYSQL_*` 전달, TLS 포트 |
| `infra/runtime/.env.example` | 운영 env 템플릿(DB 이름·계정·볼륨, 비밀값 자리) |
| `infra/runtime/nginx.conf`, `nginx.tls.conf.template` | 라우팅·TLS |
| `backend/.env.example` | 로컬 `DB_URL=jdbc:mysql://localhost:3306/eolssu`, 테스트 `TEST_DB_*` |
| `infra/monitoring/compose.yml`, `.env.example` | Grafana·Prometheus, Grafana 관리자 비밀번호 |
| `infra/mysql-admin/compose.yml` | phpMyAdmin(TLS로 MySQL 연결) |
| `infra/standby/replica.compose.yml` | edo2 읽기 전용 복제본(`REPLICA_ROOT_PASSWORD` **[비밀]**) |
| `docs/contracts/db-schema.md` | ERD·테이블 구성 설명 |
| `frontend/androidApp/build.gradle.kts` | 앱 ID·버전·API 주소·서명 변수 |
| `frontend/iosApp/Configuration/Config.xcconfig` | iOS 번들 ID·버전 |

## 9. 로컬 개발 환경

### backend + 웹 프론트 동시 개발

```sh
# 1) MySQL 8.4에 빈 DB eolssu와 계정을 만든다.
# 2) backend (bash 예시)
cd backend
export SPRING_PROFILES_ACTIVE=db
export DB_URL=jdbc:mysql://localhost:3306/eolssu DB_USERNAME=eolssu DB_PASSWORD=<로컬 비밀번호>
export AUTH_CODE_HMAC_SECRET=<로컬 임의값> AUTH_JWT_SECRET=<32바이트 이상 임의값>
export AUTH_MAIL_MODE=memory AUTH_COOKIE_SECURE=false
export SERVER_PORT=8081 SECURITY_ALLOWED_ORIGINS=http://localhost:8080
./gradlew bootRun           # 첫 실행 시 Flyway V1~V46 적용

# 3) 웹 프론트
cd frontend
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # http://localhost:8080
```

### 분석 서비스

```sh
cd ai
uv sync
uv run uvicorn ai_service.api:app --host 0.0.0.0 --port 8000
# backend에 ANALYSIS_AI_BASE_URL=http://localhost:8000 지정. ffmpeg·ffprobe가 PATH에 있어야 한다.
```

### Android 앱

Android Studio에서 `frontend/`를 열고 API 37 에뮬레이터로 `androidApp`을 실행한다. 로컬 API를 쓰려면 `-Peolssu.debugApiBaseUrl=http://127.0.0.1:8081`과 `adb reverse tcp:8081 tcp:8081`을 사용한다.

### 테스트

```sh
cd backend && ./gradlew test                                                   # 단위 테스트
cd backend && python3 -B ../infra/jenkins/run-database-tests.py -- ./gradlew databaseTest   # DB 통합 테스트(Docker 필요)
cd frontend && ./gradlew :shared:testAndroidHostTest
cd ai && uv run pytest
```
