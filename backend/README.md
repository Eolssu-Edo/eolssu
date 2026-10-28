# 얼쑤 Backend

Spring Boot + Kotlin으로 구성한 독립 Gradle 애플리케이션입니다. MyBatis와 Flyway로 [ERD·정본 SQL](../docs/contracts/db-schema.md)의 MySQL 8.4 저장 계층을 구성합니다. 프로젝트명과 `spring.application.name`은 `eolssu`, 기본 패키지는 `com.ssafy.eolssu.backend`입니다. IDE에서는 저장소 루트가 아닌 `backend/`를 Gradle 프로젝트로 엽니다.

## 개발 환경

| 항목 | 버전 및 위치 |
| --- | --- |
| JDK | 21 |
| Spring Boot | 4.1.1 |
| Kotlin | 2.3.21 |
| Gradle | Wrapper 9.8.2 |
| MyBatis Spring Boot Starter | 4.0.0, Spring Boot 4 호환 |
| DB | MySQL 8.4, Flyway·Connector/J 버전은 Spring Boot 관리 |
| 플러그인·라이브러리 관리 | `gradle/libs.versions.toml` |

[Spring Initializr](https://start.spring.io/)에서 Gradle Kotlin DSL, Kotlin, Java 21, Spring Web, Validation을 선택한 생성본을 기반으로 구성했습니다. Kotlin JVM·Spring 플러그인과 JSON 변환 의존성을 사용하며, Spring Boot가 관리하는 라이브러리 버전은 중복 지정하지 않습니다. 프론트엔드와 Gradle 모듈·버전을 공유하지 않습니다.

JDK 21을 설치하고 `JAVA_HOME`이 설치 경로를 가리키도록 설정합니다. Kotlin이나 Gradle을 별도로 전역 설치할 필요는 없습니다. 첫 실행에는 Wrapper와 의존성을 내려받을 네트워크 연결이 필요합니다.

## 실행과 검증

아래 명령은 `backend/`에서 실행합니다. PowerShell에서는 `JAVA_HOME`을 설정한 터미널을 사용합니다.

| 목적 | PowerShell | macOS / Linux |
| --- | --- | --- |
| 개발 서버 | `.\gradlew.bat bootRun` | `./gradlew bootRun` |
| 테스트 | `.\gradlew.bat test` | `./gradlew test` |
| 전체 빌드·테스트 | `.\gradlew.bat clean build` | `./gradlew clean build` |
| 실제 MySQL 통합 검사 | `.\gradlew.bat databaseTest` | `./gradlew databaseTest` |
| 실제 녹음 파일 검사(ffmpeg) | `.\gradlew.bat mediaTest` | `./gradlew mediaTest` |
| 실행 JAR 생성 | `.\gradlew.bat bootJar` | `./gradlew bootJar` |

기본 주소는 `http://localhost:8080`입니다. `/` 요청의 `404`는 정상입니다. 약관 조회는 아래의 `db` 프로필에서 제공합니다. 시작 로그에서 애플리케이션과 내장 Tomcat의 기동을 확인하며, 개발 서버는 `Ctrl+C`로 종료합니다.

실행 JAR은 `build/libs/eolssu-0.0.1-SNAPSHOT.jar`입니다. `java -jar build/libs/eolssu-0.0.1-SNAPSHOT.jar`로 실행합니다. `*-plain.jar`는 실행용 Spring Boot JAR이 아닙니다.

기본 필수 검사는 `clean build`입니다. 시작 테스트는 외부 DB·AI 서버 없이 임의 포트에 실제 HTTP 서버를 띄우고, DB 관련 Bean 미생성과 미등록 경로의 `404` 응답을 확인합니다. 결과는 `build/reports/tests/test/index.html`에 생성됩니다. 저장 모델·쿼리·마이그레이션 변경에서는 아래의 `databaseTest`, 녹음 파일 검사 변경에서는 `mediaTest`도 병합 전에 수행합니다. AI 통합과 모델 품질은 두 검사 범위에 포함하지 않습니다.

### 약관 목록(AUTH-08)

`db` 프로필과 V2 이상 스키마에서 `GET /api/terms`를 로그인 없이 호출합니다(`Accept: application/json`). 응답은 `{httpStatus,isSuccess,message,code,result:{items:[...]}}`이며 항목은 문자열 `termId`, `code`, `title`, `required`, `version`, `contentUrl`입니다. UTC 조회 시각까지 시행된 버전 중 code별 가장 늦은 `effective_at`을 고르고, 같은 시각이면 큰 ID 한 개를 반환합니다. `seq`·ID 오름차순으로 표시하며 현재 약관이 없으면 200과 빈 목록입니다. 문구·URL·초기 데이터는 자동 생성하지 않습니다.

무DB 기본 프로필에는 약관 컨트롤러·조회 Bean을 만들지 않습니다. 업무 Bean은 `@Profile("db | ops")`라 운영 `ops`에서도 만듭니다. `databaseTest`의 `TermsApiDatabaseTests`는 폐기 가능한 MySQL 8.4에 고정 약관 데이터를 넣고 실제 HTTP 응답까지 확인한 뒤 약관 데이터를 지웁니다.

400(JSON·검증·파라미터 오류), 405(메서드 오류, `Allow` 보존), 500(예상하지 못한 예외)은 기존 공통 오류 본문을 사용하며 요청값·예외 메시지는 응답에 넣지 않습니다. ProblemDetail은 쓰지 않습니다(2026-10-28 확정, S15P31E102-168 녹음 업로드 오류도 같은 형식). 위 목록에 없는 프레임워크 상태(예: 409·410)도 같은 형식이며 `result.errorCode`는 HTTP 상태 이름입니다.

## 환경 설정

공통 설정은 `src/main/resources/application.yml`, 마이그레이션을 포함하는 DB 설정은 `application-db.yml`에 둡니다. 기본 실행은 DB 없이 시작하며, `SERVER_PORT` 기본값은 `8080`입니다. 운영은 `ops` 프로필을 사용합니다. `ops`는 `db`와 같은 업무 API·Mapper를 켜되 Flyway를 끄고 기존 DB를 그대로 씁니다. 운영 DB의 마이그레이션은 `db` 프로필로 따로 적용합니다.

### 이메일 인증(AUTH-06·07)

`db` 프로필은 `AUTH_CODE_HMAC_SECRET`이 필수입니다. 임의 32바이트 이상을 Base64로 인코딩한 값을 한 번 발급해 비밀값 저장소에 보관하고 실행 환경에 주입합니다. 비어 있거나 형식·길이가 잘못되면 시작에 실패합니다. 키를 바꾸면 기존 인증번호 확인이 실패하므로 매 실행 때 새로 만들지 않습니다. 테스트는 `src/test/resources/application.properties`의 공개 고정 키를 쓰며 실행 JAR에는 포함하지 않습니다.

`AUTH_MAIL_MODE` 기본값은 `smtp`입니다. `AUTH_MAIL_HOST`·`AUTH_MAIL_USERNAME`·`AUTH_MAIL_PASSWORD`가 모두 있으면 STARTTLS(설정 고정)로 실제 발송하고, 하나라도 없으면 서버는 기동하되 발송만 거절합니다. 발송 거절·실패 때 가입 인증 발송은 503이며, 비밀번호 재설정·계정 연결은 가입 여부를 숨기기 위해 202를 반환합니다. SMTP 시간 제한은 연결 3초·읽기/쓰기 5초입니다. `unavailable`은 항상 발송을 거절합니다. 로컬에서는 `AUTH_MAIL_MODE=memory`로 메모리 대역을 명시할 수 있습니다. 최근 100건을 프로세스 안에서만 보관하고 로그·HTTP 조회·파일 저장을 제공하지 않습니다. 번호 확인은 IDE에서 `InMemoryVerificationMailSender.messages()`로 할 수 있습니다.

- `POST /api/auth/email-verifications`: JSON `email`(최대 320자·소문자 정규화)과 `purpose`(`SIGN_UP`, `PASSWORD_RESET`, `ACCOUNT_MERGE`). 202에 문자열 `verificationId`, UTC `expiresAt`, `resendAvailableAt`을 반환합니다. 같은 이메일의 모든 목적을 합산해 60초 간격·KST 날짜별 10회이며 재발송하면 같은 목적의 이전 번호·미사용 완료 토큰을 무효화합니다.
- `POST /api/auth/email-verifications/{verificationId}/confirm`: JSON `code`는 숫자 6자리 **문자열**입니다. 3분 경과·재발송·확인 성공 뒤에는 410, 없는 ID는 404입니다. 처음 네 오답은 400과 `result.details.remainingAttempts`, 다섯 번째 오답부터는 429입니다. 네 번 틀린 뒤 다섯 번째에 맞히면 성공합니다.
- 확인 성공 시 임의 256비트 `verificationToken`, 정규화 이메일, 성공 시각부터 30분 뒤의 `expiresAt`을 반환합니다. DB에는 번호 HMAC-SHA256과 완료 토큰 SHA-256만 저장합니다. 가입(AUTH-02)이 완료 토큰을 1회 소비합니다. 계정 연결·비밀번호 재설정의 소비는 후속 Task(S15P31E102-211·210)입니다.
- 활성·탈퇴 대기 회원의 SIGN_UP은 409 `EMAIL_ALREADY_EXISTS`입니다. PASSWORD_RESET은 미가입자에게도 동일한 202와 제한을 적용하고 실제 메일은 가입된 이메일에만 보냅니다. 미가입·메일 장애 요청에는 6자리 코드로 확인할 수 없는 해시를 저장합니다. 재설정 메일 장애는 개인정보 없는 경고 로그만 남기며 응답에 드러내지 않습니다.
- 발송 간격·일일 제한의 429는 `result.details.resendAvailableAt`을 제공합니다. 가입 메일 장애 503은 신규 행·이전 번호 무효화를 롤백합니다. 재설정·계정 연결의 숨긴 발송 실패는 요청 행·횟수·이전 번호 무효화를 유지합니다. 서버 안에서 DB 연결을 잡기 전에 먼저 줄을 세운 뒤 단일 MySQL의 DB별 잠금으로 최초 동시 발송과 번호 재확인을 직렬화하며, 5초 내 차례를 얻지 못하면 공통 503입니다([선택과 제약](../docs/knowledge/backend/auth-token-storage.md#이메일-인증-구현의-동시성2026-10-28)).

### 가입·로그인·재발급·로그아웃(AUTH-01~04)

`db` 프로필에서 제공합니다. 로그인 토큰은 응답 본문에 넣지 않고 쿠키로만 내려갑니다.

- 쿠키: 모두 HttpOnly·SameSite=Lax이고 Secure는 `AUTH_COOKIE_SECURE`(기본 `true`, 로컬 HTTP 개발에서만 `false`)를 따릅니다. `access_token`은 HS256 JWT(`sub`=users.id, 30분, Path `/api`), `refresh_token`은 임의 256비트(14일, Path `/api/auth`, DB에는 SHA-256만)입니다.
- `POST /api/auth/sign-up`: 이메일·비밀번호(영문·숫자·특수문자 포함 8~128자, Argon2id 저장)·닉네임·SIGN_UP `verificationToken`·`agreements`(시행 중인 필수 약관 전부 현재 버전 동의)·선택 `instrumentId`(활성 악기, 생략하면 코드 `DANSO` 악기)·`notationMode`(기본 HANJA). 201과 로그인 쿠키. 토큰 문제는 401 `EMAIL_NOT_VERIFIED`, 중복은 409, 약관은 422 `TERMS_AGREEMENT_REQUIRED`이며 어느 단계가 실패해도 회원이 남지 않습니다.
- `POST /api/auth/sign-in`: 이메일 없음·비밀번호 불일치는 같은 401 `INVALID_CREDENTIALS`입니다. 같은 이메일 15분 안 5번째 실패부터 15분 동안 429이고 `result.details.retryAvailableAt`을 줍니다. 잠금은 `users.email` 콜레이션(`utf8mb4_0900_ai_ci`)의 정렬 키 기준이라 악센트·대소문자 변형으로 피할 수 없고, 가입하지 않은 이메일에도 똑같이 걸려 가입 여부를 드러내지 않습니다. 실패 기록은 서버 메모리라 재시작하면 초기화됩니다. 비밀번호 확인(Argon2)은 DB 트랜잭션 밖에서 합니다. 탈퇴 대기 회원은 로그인하면 ACTIVE로 돌아가고 `withdrawalCancelled=true`입니다.
- `POST /api/auth/refresh`: `refresh_token` 쿠키만 봅니다. 성공하면 이전 토큰을 폐기(ROTATED)하고 두 쿠키를 새로 줍니다. 재발급으로 폐기된(ROTATED) 토큰이 다시 오면 그 회원의 토큰을 모두 폐기(REUSE_DETECTED)합니다. 비밀번호 변경·로그아웃·탈퇴로 폐기된 토큰은 401만 줍니다. 401이면 두 쿠키를 지웁니다. 두 탭이 동시에 재발급하면 한쪽이 재사용으로 판정돼 로그아웃될 수 있습니다.
- `POST /api/auth/sign-out`: 로그인 필요. 쿠키의 refresh_token이 그 회원의 것일 때만 폐기(SIGN_OUT)하고 두 쿠키를 지웁니다.
- `AUTH_JWT_SECRET`이 없으면 서버는 기동하고 가입·로그인·재발급만 503입니다. 가입 기본 악기(`DANSO`)가 없어도 503입니다.

### 내 정보와 비밀번호 변경(USR-01~04)

`db`·`ops` 프로필에서 제공하며 모두 로그인(access_token 쿠키)이 필요합니다. 토큰은 유효하지만 회원이 없거나 ACTIVE가 아니면 401 `UNAUTHENTICATED`입니다(회원 행이 없을 때 401은 GRO-04와 같음).

- `GET /api/users/me`: 닉네임·악기·표기와 연결된 간편 로그인(`linkedProviders`, 연결 순). 이메일·제공자 사용자 ID는 내보내지 않습니다.
- `PATCH /api/users/me`: 닉네임(가입과 같은 규칙, 60자 이하).
- `PATCH /api/users/me/preferences`: `instrumentId`·`notationMode` 중 한 개 이상. 없거나 비활성인 악기는 422 `UNPROCESSABLE_INPUT`. 곡별 BPM(`user_song_settings`)은 형식이 정해지지 않아 아직 받지 않습니다.
- `PATCH /api/users/me/password`: 현재 비밀번호가 틀리면 403 `CURRENT_PASSWORD_MISMATCH`, 같은 회원 15분 안 5번째 실패부터 15분 동안 429(`retryAvailableAt`, 로그인 잠금과 따로 셈). 새 비밀번호는 가입 규칙을 따르고 현재 값과 같으면 400입니다. 성공하면 그 회원의 refresh를 모두 폐기(PASSWORD_CHANGED)하고 현재 기기에만 두 쿠키를 새로 줍니다. 다른 기기의 access_token은 만료(최대 30분)까지 쓸 수 있습니다.

### 회원 탈퇴와 일괄 삭제(USR-05)

- `DELETE /api/users/me`: JSON `currentPassword`로 다시 확인합니다. 틀리면 403 `CURRENT_PASSWORD_MISMATCH`이고, 15분 5회 실패 잠금(429)은 비밀번호 변경과 함께 셉니다. 성공하면 `WITHDRAWAL_PENDING`으로 바꾸고 요청+30일 뒤 첫 04:00(Asia/Seoul)을 `purgeAt`으로 돌려주며, refresh를 모두 폐기(WITHDRAWN)하고 두 쿠키를 지웁니다. 대기 중 로그인하면 탈퇴가 취소됩니다.
- 인증 필터: `db`·`ops` 프로필에서 로그인이 필요한 요청마다 토큰의 회원 상태를 조회해 ACTIVE가 아니면 401 `UNAUTHENTICATED`와 두 쿠키 삭제로 응답합니다. 공개 API는 그대로입니다.
- 일괄 삭제: 매일 04:00(Asia/Seoul)에 삭제 예정 시각이 지난 탈퇴 대기 회원을 100명씩 읽어 회원 단위로 처리합니다. 녹음 원본을 먼저 지우고, [DB 스키마 계약](../docs/contracts/db-schema.md#탈퇴-일괄-삭제-대상) 순서로 한 트랜잭션에서 지운 뒤 개인정보를 비우고 `DELETED`로 바꿉니다. 녹음 삭제가 실패한 회원은 다음 날로 미룹니다(녹음 저장소가 설정되지 않은 서버도 같음). 끝나면 처리·미룸·건너뜀 수와 걸린 시간을 로그로 남깁니다. `eolssu.scheduling.enabled=false`면 돌지 않습니다.

### 준비 상태 검사

`GET /api/actuator/health/readiness`는 준비되면 HTTP 200과 `{"status":"UP"}`, 준비되지 않으면 HTTP 503을 반환합니다. 기본 개발 모드는 애플리케이션 준비 상태만 확인합니다. 운영 `ops` 프로필은 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`로 기존 MySQL에 연결해 DB health를 함께 확인합니다. DB 접속 실패 시 프로세스가 살아 있어도 readiness는 DOWN이고 `/api/actuator/health/liveness`는 UP일 수 있습니다.

운영 `ops`에서는 Flyway와 SQL 초기화를 끕니다. 업무 API는 기존 스키마를 읽고 쓰지만 스키마를 바꾸지 않습니다. 기존 DB에 마이그레이션을 실행하려고 `db` 프로필을 함께 활성화하지 않습니다(`db,ops`처럼 함께 켜면 나중 프로필의 Flyway 설정이 이겨 순서에 따라 Flyway가 켜집니다). 세션 시간대는 UTC(`SET time_zone = '+00:00'`), 연결 대기는 2초, 풀은 최대 10개입니다. `AUTH_CODE_HMAC_SECRET`이 비어 있어도 기동하고 이메일 인증번호 발송·확인만 503입니다. HTTP에는 health만 공개하고 상세·컴포넌트·JMX를 숨깁니다. `env`·`beans`·`configprops`는 404입니다.

필수 `clean build`에서 실제 HTTP 준비 응답·관리 정보 비공개와 연결할 수 없는 격리 DB 대상의 readiness 503/liveness 200·Flyway Bean 부재를 검사합니다. 운영 후보에서는 기존 DB 연결의 UP 응답도 별도로 확인합니다.

```powershell
$env:SERVER_PORT = '8081'
.\gradlew.bat bootRun
```

macOS / Linux에서는 `SERVER_PORT=8081 ./gradlew bootRun`을 사용합니다. `.env.example`은 변수 설명용이며 `.env` 파일을 자동으로 읽지 않습니다. 실제 비밀값은 터미널·IDE·배포 환경에서 주입하고 커밋하지 않습니다.

### 보안과 공통 오류 응답

Spring Security로 API 경로를 나누며 `access_token` 쿠키의 HS256 JWT를 검증합니다. Authorization 헤더·쿼리·폼·다른 쿠키는 인증 수단으로 사용하지 않습니다. 공개 GET·POST·health 경로는 `access_token` 쿠키를 검증하지 않아 만료·잘못된 쿠키가 있어도 접근할 수 있습니다. 재발급은 후속 단계에서 refresh 쿠키로 확인합니다. 로그인 토큰 발급·재발급은 S15P31E102-143 후속 단계에서 추가합니다.

`AUTH_JWT_SECRET`에는 별도 발급해 보관한 **UTF-8 원문 32바이트 이상**의 비밀키를 넣습니다(Base64 자동 디코딩 없음). 기본 키는 없습니다. 누락·공백이면 서버는 기동하지만 보호 API의 모든 토큰을 공통 401 `UNAUTHENTICATED`로 거절합니다. 명시한 키가 32바이트 미만이면 기동에 실패합니다. 서버 환경 연결은 S15P31E102-200에서 진행합니다. 서명·만료·nbf·필수 exp 및 양의 Long 범위 sub를 검증하며 시간 유예는 없습니다. 토큰·키는 로그나 오류 본문에 싣지 않습니다.

JWT `sub`는 `users.id`입니다. 컨트롤러에서 `@AuthenticationPrincipal jwt: Jwt`로 받고 `requireNotNull(jwt.subject).toLong()`으로 읽습니다. 의미상 `jwt.subject.toLong()`이며 Spring Security 7의 nullable 선언 때문에 `requireNotNull`을 씁니다. `Jwt`는 `org.springframework.security.oauth2.jwt.Jwt`, 어노테이션은 `org.springframework.security.core.annotation.AuthenticationPrincipal`입니다. 이 단계에서는 사용자 DB 상태를 재조회하지 않습니다.

컨트롤러 테스트는 `spring-security-test`의 `post("/api/...").with(jwt().jwt { it.subject("1") })`로 인증할 수 있습니다(`SecurityMockMvcRequestPostProcessors.jwt`). 이는 서명 검증을 대체하는 테스트 대역이며, `AccessTokenSecurityTests`는 실제 HS256 서명 쿠키·위조·만료도 별도로 검사합니다. 테스트 고정 키는 테스트 코드에만 둡니다.

- 공개(명세 Request Header '공개' 경로만, 와일드카드 없음): POST `/api/auth/sign-in`·`sign-up`·`refresh`·`password-reset`·`oauth/{provider}`·`email-verifications`·`email-verifications/{verificationId}/confirm`, GET `/api/terms`·`songs`·`songs/{songId}`·`songs/{songId}/score`·`songs/{songId}/model-audio`·`instruments`·`instruments/{instrumentId}/fingerings`·`pitches`·`jangdans`·`jangdans/{jangdanId}`·`notation-symbols`, `/api/actuator/**`(health만 노출). 전체 표는 `ApiAccessRulesTests`에 있습니다.
- 그 밖의 `/api/**`는 로그인이 필요합니다. 오류는 API 명세 공통 형식 `{httpStatus, isSuccess:false, message, code, result{errorCode}}`로 응답합니다(400 `INVALID_REQUEST`, 401 `UNAUTHENTICATED`, 403 `FORBIDDEN`, 404 `RESOURCE_NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`, 406 `NOT_ACCEPTABLE`, 413 `RECORDING_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE`, 500 `INTERNAL_SERVER_ERROR`, 녹음 업로드 코드는 아래 '녹음 업로드와 원본 저장소'). Spring multipart 한도 초과도 같은 413 형식으로 처리합니다. 지원하지 않는 Accept의 406도 공통 JSON 본문으로 반환합니다. 오류 추가 정보는 필요할 때만 `result.details`에 담고 민감값은 제외합니다. ProblemDetail 형식은 쓰지 않기로 확정했습니다. `//api`·`/./`·`;x`처럼 비정상 경로는 Spring Security 방화벽이 400 `INVALID_REQUEST`로 거절합니다. 인코딩된 `/` 등 Tomcat이 먼저 거절하는 경로는 Tomcat 기본 응답입니다.
- CSRF: CSRF 토큰 대신 SameSite=Lax 쿠키와 `StateChangingRequestFilter`로 막습니다. 경로와 관계없이 모든 POST·PUT·PATCH·DELETE에 적용합니다.
  - `Origin` 헤더가 있으면 요청 `Host`와 호스트 이름이 같거나 `SECURITY_ALLOWED_ORIGINS`의 주소여야 합니다(403). Nginx는 HTTPS를 끝내고 `Host`를 포트 없이 넘기므로, `Host`에 포트가 없으면 호스트 이름만 비교하고 포트가 있으면 포트도 비교합니다. 그래서 Nginx 뒤에서는 같은 호스트의 다른 포트에서 온 요청도 통과합니다. 지금 서버는 Nginx의 80·443만 공개하므로 문제가 없지만, 같은 도메인의 다른 포트에 서비스를 올리면 이 판단을 다시 확인합니다. 앱처럼 `Origin`을 보내지 않는 요청은 Origin 확인을 건너뜁니다.
  - 본문은 `application/json`만 받고, `Content-Type`이 없으면 본문도 없어야 합니다(415). 녹음 업로드(`POST /api/practice-sessions/{id}/recording`, 디코딩된 경로 기준)만 `multipart/form-data`를 받습니다.
- CORS: `SECURITY_ALLOWED_ORIGINS`가 비어 있으면 켜지 않습니다. 운영·compose는 웹과 API가 같은 주소이고, Nginx 뒤 백엔드는 http로 받아 Spring CORS가 https `Origin`을 다른 출처로 잘못 판단하기 때문입니다. FE 개발 서버처럼 다른 주소에서 쿠키를 포함해 **백엔드를 직접** 부를 때만 그 주소를 쉼표로 구분해 넣습니다. `*`는 쿠키 허용 설정과 함께 쓸 수 없어 쓰지 않습니다. Nginx 뒤 서버에 이 값을 넣으면 같은 사이트 요청도 CORS 검사를 받아 막힐 수 있습니다.

FE 웹 개발 서버(`:webApp:wasmJsBrowserDevelopmentRun`)는 `http://localhost:8080`을 쓰므로, 함께 개발할 때는 백엔드를 다른 포트로 띄우고 FE 주소를 허용합니다.

```powershell
$env:SERVER_PORT = '8081'
$env:SECURITY_ALLOWED_ORIGINS = 'http://localhost:8080'
.\gradlew.bat bootRun
```

### MySQL 연결과 Flyway

비어 있는 MySQL 8.4 DB와 접속 계정을 먼저 준비한 뒤 아래 환경변수를 설정합니다. URL에는 실제 DB 이름을 지정합니다. 인프라 합의의 서버 DB 이름은 `eolssu`입니다.

| 환경변수 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=db` | DataSource·MyBatis·Flyway 활성화 |
| `DB_URL` | JDBC URL. 예: `jdbc:mysql://localhost:3306/eolssu` |
| `DB_USERNAME`, `DB_PASSWORD` | 해당 DB의 접속 정보. 기본값 없음 |
| `AUTH_CODE_HMAC_SECRET` | 인증번호 HMAC 비밀키(Base64, 디코딩 후 32바이트 이상). `db`에서 필수 |
| `AUTH_MAIL_MODE` | 기본 `unavailable`, 로컬 메일 대역은 `memory` |
| `ANALYSIS_AI_BASE_URL` | 연주 분석 AI 서비스 주소. 비우면 업로드 뒤 분석을 시작하지 않음 |

```powershell
$env:SPRING_PROFILES_ACTIVE = 'db'
$env:DB_URL = 'jdbc:mysql://localhost:3306/eolssu'
$env:DB_USERNAME = 'eolssu'
$env:DB_PASSWORD = '<로컬 DB 비밀번호>'
$env:AUTH_CODE_HMAC_SECRET = '<별도로 발급해 보관한 Base64 비밀키>'
.\gradlew.bat bootRun
```

macOS / Linux에서는 같은 값을 터미널에서 `export`한 뒤 `./gradlew bootRun`을 실행합니다. JAR도 같은 환경변수로 실행합니다. `db` 프로필에서 연결·마이그레이션 검증이 실패하면 기동에 실패합니다.

- Flyway가 `src/main/resources/db/migration/`의 `V1__initial_schema.sql`, `V2__auth_user_practice_extensions.sql`, `V3__practice_session_recording_metadata.sql`, `V4__practice_session_analysis_attempt.sql`을 버전 순서대로 적용하고 `flyway_schema_history`에 기록합니다. 다시 시작하면 이력을 검증하고 이미 적용한 버전은 재실행하지 않습니다.
- 적용한 버전 파일은 수정하지 않고 `V5__설명.sql`처럼 다음 번호로 새 버전을 추가합니다. `schema.sql` 자동 초기화는 사용하지 않습니다.
- MySQL DDL은 실패해도 되돌려지지 않습니다. V2는 DDL 전에 기존 데이터를 확인해, 세마치·굿거리·중모리·타령 밖의 장단, 개인정보가 남은 탈퇴 회원, 20~240 밖의 세션 BPM이 있으면 `chk_v2_preconditions` 위반으로 스키마를 바꾸지 않고 기동에 실패합니다. 기존 장단·세션의 `bpm_unit`은 장단 이름으로 채웁니다. V4도 같은 방식으로, 실패 코드(`fail_reason`)가 6종 밖이거나 `FAILED` 여부와 실패 코드가 맞지 않는 세션이 있으면 `chk_v4_preconditions` 위반으로 멈춥니다(실패 행은 `version = '4'`).
- 이때 `flyway_schema_history`에는 실패 행(`version = '2'`, `success = 0`)이 남아, 데이터만 고쳐 다시 시작하면 `Detected failed migration to version 2`로 다시 실패합니다. 이 프로젝트에는 Flyway CLI·Gradle 작업이 없으므로 같은 DB에 MySQL 클라이언트로 접속해 해당 행을 고친 뒤 실패 행을 지우고 `db` 프로필로 다시 시작합니다.

```sql
SELECT version, success FROM flyway_schema_history;               -- 2, 0 확인
DELETE FROM flyway_schema_history WHERE version = '2' AND success = 0;
```
- 자동 baseline과 Flyway clean은 비활성화했습니다. 이미 테이블이 있는 DB에 이력 없이 연결하면 자동 편입하지 않고 실패합니다. 기존 DB 도입은 스키마 일치 확인과 별도 적용 계획이 필요합니다.
- 연결마다 MySQL 세션 시간대를 `+00:00`으로 맞춥니다. `DATETIME(6)` 저장 모델의 `LocalDateTime`은 UTC 값으로 취급하고 API 시간대 변환은 기능 구현에서 정합니다.

### 연습 세션 생성(PRA-01)

`db` 프로필에서 로그인한 회원이 `POST /api/practice-sessions`를 호출합니다. `Idempotency-Key` 헤더(출력 가능 ASCII 1~100자)가 필수이고, 본문은 `songId`(양의 정수 문자열)·`bpm`(20~240 정수)이 필수, `bpmUnit`·`startNoteIdx`·`endNoteIdx`·`parentSessionId`·`missionId`·`jangdanSoundOn`이 선택입니다. JSON 타입이 명세와 다르면(`96.7`, `"96"`, 숫자 ID, `1`인 불리언 등) 자동 변환하지 않고 400입니다. 성공하면 201과 `result{sessionId,songId,status,bpm,bpmUnit,startNoteIdx,endNoteIdx,startedAt}`이며 세션은 `RECORDING`, `started_at`은 서버 UTC 시각, `score_version`은 곡의 현재 값입니다. 생략한 구간은 곡 전체, 생략한 `bpmUnit`은 곡 장단의 값입니다.

- 곡은 `PUBLISHED`·채점 가능·삭제 안 됨이어야 합니다(아니면 404). `missionId`는 미션 정의가 아직 없어 404입니다. `jangdanSoundOn`은 저장하지 않습니다.
- 부모 세션은 본인 소유가 아니면 404, 다른 곡이면 400, `DONE`이 아니면 409 `STATE_CONFLICT`입니다. 곡 밖의 구간은 400입니다.
- 같은 회원·키로 같은 본문을 다시 보내면 처음 세션을 201로 돌려주고, 다른 본문이면 409 `IDEMPOTENCY_CONFLICT`입니다. 비교할 때 `startNoteIdx` 생략은 0, `jangdanSoundOn` 생략은 false와 같게 봅니다. `endNoteIdx`·`bpmUnit`은 생략과 명시를 다른 본문으로 봅니다(곡 값에 따라 달라지기 때문). 동시에 보내도 `(user_id, idempotency_key)` 유일 제약으로 한 행만 저장됩니다.
- 결정 근거는 [작업 계획](../docs/plans/S15P31E102-217.md)에 있습니다. `databaseTest`의 `PracticeSessionApiDatabaseTests`가 실제 HTTP·서명 쿠키로 확인합니다.

### 실제 MySQL 통합 검사

담당 개발자가 저장 계층 변경 후, MR 병합 전에 실행합니다. **운영·공유 개발 DB가 아닌 비어 있는 폐기 가능한 MySQL 8.4 DB**를 준비합니다. V1~V5 DDL과 Flyway 이력은 남고, 검사 데이터는 테스트 트랜잭션 종료 시 롤백됩니다. 검사는 DB를 자동 생성하거나 삭제하지 않습니다.

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/eolssu_test'
$env:TEST_DB_USERNAME = 'eolssu_test'
$env:TEST_DB_PASSWORD = '<테스트 DB 비밀번호>'
.\gradlew.bat databaseTest
```

macOS / Linux에서는 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 `export`한 뒤 `./gradlew databaseTest`를 실행합니다. 이 검사는 앱용 `DB_*` 대신 `TEST_DB_*`로 접속하며 세 변수가 없으면 실패합니다. 보고서는 `build/reports/tests/databaseTest/index.html`입니다.

검사 범위는 MySQL 8.4 V1~V5 적용·재실행·검증, 19개 테이블의 Mapper Bean·XML·생성자 매핑, nullable 값·JSON·한글/한자·전각 기호·마이크로초·UTC 세션, FK·CHECK·공통 레슨 유일성과 V2의 회원 상태·간편 로그인 연결·약관·인증·토큰·곡별 BPM·연습 기록 중복 키·체류 기록 제약과 V3 녹음 메타데이터의 전부 NULL 또는 전부 양수 제약, V4 분석 시도·실패 코드 제약(위반 제약 이름까지 확인), V5 단소 율명 시드와 곡 악보를 연습 구간 음표로 푸는 동작, 탈퇴 일괄 삭제에서 회원 세션을 한 문장으로 지우는 동작, 체류 구간의 회원·키별 1회 저장과 마이크로초 왕복, 분석 결과를 같은 시도의 마감 전 `PENDING` 세션에만 저장하고 마감을 넘긴 `PENDING`만 `TIMEOUT`으로 바꾸며 음표별 결과 저장이 실패하면 상태 변경까지 되돌리는 동작, 녹음 업로드의 조건부 갱신(회원 본인의 `RECORDING` 세션만 원본·메타데이터·`ended_at` 기록, `PENDING` 전환, 분석 시도 시작, 같은 트랜잭션에서 `RecordingUploaded` 발행), KST 날짜별 연습 시간·`DONE` 세션 합산과 연습한 날 조회, 성장 요약·날짜별 API의 실제 HTTP·JWT 쿠키 조회, 분석 다시 시도를 본인의 녹음이 있는 `TIMEOUT`·`SERVER_ERROR` 세션에만 시작하고 동시 요청 두 건 중 하나만 새 시도로 바꾸는 동작입니다. 되돌림·동시 요청 검사는 테스트 트랜잭션 밖에서 커밋하고 넣은 행을 지웁니다. 기본 `test`와 CI의 `clean build`에는 이 DB 검사를 포함하지 않습니다.

### 녹음 파일 검사와 ffmpeg

업로드된 녹음이 [녹음 파일 규격(PRA-02)](../docs/specs/recording-file.md)에 맞는지 `RecordingFileValidator`가 판정합니다. 크기(20MiB 초과면 `TooLarge`)를 먼저 보고, 실제 내용을 `ffprobe`로 읽어 MP4 계열 컨테이너의 AAC 오디오인지(`UnsupportedFormat`), 길이가 1ms 이상·601초 미만인지(`InvalidDuration`) 봅니다. 1ms 미만은 저장할 길이가 0ms로 반올림돼 V3 메타데이터 CHECK에 걸리므로 먼저 거부합니다. 확장자·MIME는 보지 않습니다. 오디오 스트림이 없는 m4a는 길이 검사 전에 `UnsupportedFormat`이 됩니다. `ffprobe` 실행 실패·10초 초과·대기 중 중단·출력 읽기 실패는 규격 위반이 아니라 `AudioProbeException`이며, 남은 ffprobe 프로세스는 종료합니다. multipart 한도는 스펙대로 파일·요청 25MiB이고, 20MiB 경계는 검사기가 정합니다(Nginx `client_max_body_size 25m`은 업로드 API Task에서 설정).

`mediaTest`는 테스트 중 `ffmpeg`로 사인파·무음 AAC(2초, 600.5초, 601초), 이름만 바꾼 MP3, ALAC, 오디오 없는 m4a, 잘린 파일을 만들어 `ffprobe`로 검사합니다. 큰 파일은 커밋하지 않습니다. `ffmpeg`·`ffprobe`가 `PATH`에 없으면 건너뛰지 않고 실패합니다. 기본 `test`·`clean build`는 테스트 JVM의 `java`로 띄우는 가짜 ffprobe(`FakeFfprobe`)로 출력 해석·종료 코드·시간 초과·중단 시 프로세스 정리를 검사하므로 OS와 ffmpeg 설치에 관계없이 돕니다.

| 환경 | 설치 |
| --- | --- |
| macOS | `brew install ffmpeg` |
| Ubuntu | `sudo apt-get install ffmpeg` |
| Windows | [FFmpeg 공식 빌드](https://ffmpeg.org/download.html)를 받아 `bin`을 `PATH`에 추가 |
| 실행 이미지 | `Dockerfile` 실행 단계에서 `ffmpeg=7:8.0.1-3ubuntu2`(베이스 `eclipse-temurin:21-jre`의 Ubuntu 26.04 패키지)를 고정 설치 |

고정 버전은 베이스 이미지의 Ubuntu 버전이 바뀌면 설치에 실패하므로 함께 갱신합니다. ffmpeg 설치로 실행 이미지가 약 410MB 커집니다. CI는 아직 `mediaTest`를 실행하지 않습니다. edo1 Jenkins 에이전트에 ffmpeg를 설치한 뒤 Jenkinsfile에 추가하고, 운영 이미지를 만드는 서버의 `/opt/eolssu/config/backend.Dockerfile`도 이 파일과 맞춥니다.

### 녹음 업로드와 원본 저장소(PRA-02)

`POST /api/practice-sessions/{sessionId}/recording`(`db` 프로필, 로그인 필요)은 multipart의 `audio` 파트 하나를 받아 [녹음 파일 규격](../docs/specs/recording-file.md)의 순서(404→410→409→413→415→422→503→500)로 판정합니다. 통과하면 원본을 `recordings/{sessionId}/{UUID}.m4a`에 저장하고, 세션이 아직 `RECORDING`일 때만 한 번의 조건부 갱신으로 원본 키·메타데이터 4칸·`ended_at`(서버 UTC)을 기록하고 `PENDING`으로 바꾸며 분석 시도를 시작합니다(`analysis_attempt` + 1, `analysis_started_at`). 응답은 202이고 본문은 `{sessionId, status: "PENDING", evaluationId: null, pollAfterMs: 2000}`입니다. 앱이 보내는 `endedAt`과 `Idempotency-Key`는 읽지 않습니다. 저장에 성공한 세션에 다시 올리면 409이며, 응답을 받지 못한 앱은 상태 조회로 결과를 확인합니다.

거부 코드(`result.errorCode`)는 404 `SESSION_NOT_FOUND`, 410 `SESSION_EXPIRED`, 409 `SESSION_NOT_RECORDING`, 413 `RECORDING_TOO_LARGE`, 415 `RECORDING_UNSUPPORTED_FORMAT`, 422 `RECORDING_INVALID_DURATION`, 503 `RECORDING_STORAGE_UNAVAILABLE`, 500 `INTERNAL_SERVER_ERROR`입니다. DB 갱신이 실패하거나 동시 업로드에서 지면 자기 요청이 만든 객체만 지웁니다. 조건부 갱신과 같은 트랜잭션에서 `RecordingUploaded(sessionId)`를 발행하고, 분석은 아래 `AnalysisOnUpload`가 커밋 뒤 백그라운드에서 시작합니다. AI가 원본을 읽는 15분짜리 서명 URL은 `RecordingReadUrls` 빈이 만듭니다. 서명 URL은 녹음 접근 토큰이라 로그에 남기지 않습니다.

원본 저장소는 S3 호환 API로 연결하고 처음 쓰는 업체는 Cloudflare R2입니다. 아래 다섯 값이 모두 있어야 연결하며, 하나라도 비면 기동은 하되 업로드를 503으로 답합니다. 키는 서버 `.env`에만 두고 커밋하지 않습니다.

| 변수 | 값 |
| --- | --- |
| `RECORDING_STORAGE_ENDPOINT` | R2 S3 API 주소 `https://<account_id>.r2.cloudflarestorage.com` |
| `RECORDING_STORAGE_BUCKET` | 녹음 버킷 이름 |
| `RECORDING_STORAGE_ACCESS_KEY_ID`·`RECORDING_STORAGE_SECRET_ACCESS_KEY` | 이 버킷 하나에 객체 읽기·쓰기만 허용한 R2 API 토큰의 S3 키 |
| `RECORDING_STORAGE_REGION` | `auto`(기본값) |

R2 버킷 준비(계정 소유자, Cloudflare 대시보드):

1. R2 > 버킷 만들기: 위치 힌트 `Asia-Pacific(apac)`, 기본 저장 등급 `Standard`.
2. 버킷 설정 > 공개 접근: `r2.dev` 주소와 사용자 도메인을 모두 끈 상태로 둡니다.
3. 버킷 설정 > 객체 수명 주기 규칙: 접두사 `recordings/`, 객체 생성 30일 뒤 삭제를 추가합니다. R2는 만료 뒤 보통 24시간 안에 지웁니다.
4. R2 > API 토큰 관리 > API 토큰 만들기: 권한 `Object Read & Write`, 적용 버킷은 이 버킷 하나로 지정합니다. 발급된 Access Key ID·Secret Access Key와 S3 API 주소를 서버 `.env`에 넣습니다.

샘플레이트·채널 수를 양수로 읽지 못한 AAC는 415로 거부합니다(메타데이터 4칸은 전부 양수여야 저장됨). 저장소 호출은 재시도를 포함해 40초에서 끊어 503으로 답합니다. ffprobe 상한 10초를 더해도 Nginx `proxy_read_timeout` 기본값 60초보다 짧게 두어, 느린 저장소가 Nginx 504가 되지 않게 합니다.

S3 클라이언트는 AWS SDK for Java v2를 경로 방식(`엔드포인트/버킷/키`)으로 쓰고, SDK 기본값인 요청마다의 CRC 체크섬은 업체마다 지원이 달라 필요한 요청에만 붙입니다(`WHEN_REQUIRED`). `http://` 엔드포인트에서는 SDK가 본문을 서명 청크(`aws-chunked`)로 보내므로, 로컬 대역 서버를 만들 때 이를 풀어야 합니다. 자동 검사(`S3RecordingStorageTests`)는 로컬 HTTP 서버를 S3 엔드포인트로 두고 요청 경로·서명·서명 URL 형식을 확인하며 실제 R2에 연결하지 않습니다. 실제 R2 확인(쓰기, 서명 URL로 자격 증명 없이 읽기, 15분 뒤 거부, 삭제)은 테스트 전용 버킷을 만든 뒤 수동으로 합니다.

Nginx(`infra/runtime/nginx.conf`·`nginx.tls.conf.template`)는 `/api/`의 본문 한도를 25MiB(`client_max_body_size 25m`)로 둡니다. 25MiB를 넘으면 Nginx가 자체 HTML 413을, 20MiB 초과~25MiB는 백엔드가 공통 오류 형식 413을 돌려줍니다.

## 폴더와 코드 배치

```text
backend/
├── AGENTS.md / CLAUDE.md
├── settings.gradle.kts        # 독립 프로젝트 eolssu
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── .env.example
├── src/main/
│   ├── kotlin/com/ssafy/eolssu/backend/
│   │   ├── BackendApplication.kt
│   │   ├── config/                # 구현 연결·스케줄링·보안 설정 (SecurityConfig, SecurityProperties)
│   │   ├── common/error/          # 공통 오류 응답·예외 처리
│   │   ├── common/security/       # CSRF 필터
│   │   └── feature/<기능>/
│   │       ├── application/       # 구현이 있는 기능만 (예: practice)
│   │       ├── domain/
│   │       └── infrastructure/
│   │           ├── persistence/   # <테이블명>Row.kt, <테이블명>Mapper.kt
│   │           └── media/·storage/ 등  # 외부 도구·저장소 구현 (예: practice의 ffprobe, S3 호환 원본 저장소)
│   └── resources/
│       ├── application.yml / application-db.yml
│       ├── db/migration/V1__initial_schema.sql, V2__auth_user_practice_extensions.sql, V3__practice_session_recording_metadata.sql, V4__practice_session_analysis_attempt.sql
│       └── mybatis/<기능>/<테이블명>Mapper.xml
├── src/test/kotlin/com/ssafy/eolssu/backend/
│   ├── BackendApplicationTests.kt
│   └── DatabaseIntegrationTests.kt
├── src/test/resources/db/mapper-fixtures.sql
└── README.md
```

실제 기능 코드는 다음 기준으로 추가합니다. 아직 구현이 없는 패키지와 빈 클래스는 생성하지 않습니다.

```text
com.ssafy.eolssu.backend/
├── config/                    # Spring 설정·최종 의존성 조립
├── common/error/              # 실제로 공유하는 오류 응답·예외 처리
├── common/security/           # 여러 기능이 함께 쓰는 보안 필터
└── feature/<기능명>/
    ├── api/                   # Controller·공개 요청/응답 DTO
    ├── application/           # 서비스·처리 흐름
    ├── domain/                # 업무 모델·규칙·인터페이스
    └── infrastructure/        # 저장소·외부 호출 구현
```

의존 방향은 `api → application → domain`, `infrastructure → domain`으로 두며 `config`에서 구현을 연결합니다. `domain`은 Spring/MyBatis/HTTP 구현에, `application`은 구체적인 `infrastructure` 구현에 의존하지 않습니다. 기능 간 협력은 application의 공개 계약으로 한정하고 순환 참조를 피합니다. 같은 폴더 안의 패키지 경계는 Gradle이 자동 강제하지 않으므로 실제 기능 추가 시 필요한 검증을 함께 구성합니다.

Mapper는 기본적으로 PK 조회 `findById`를 제공하고, 기능 구현에 필요한 조회·갱신을 더합니다(예: `PracticeSessionMapper.failRecordingStartedBefore`). `findById`는 존재하지 않는 ID에 `null`을 반환하며, 삭제·공개 상태 필터는 적용하지 않습니다. Row는 저장 계층 전용이므로 API 응답으로 그대로 노출하지 않습니다. 기능 구현 시 domain 저장소 계약과 infrastructure 구현을 연결합니다.

`db`·`ops` 프로필에서는 녹음 업로드 만료 작업이 5분마다 실행됩니다. `RECORDING`인 채 `started_at` + 40분이 지난 연습 세션을 `FAILED`·`UPLOAD_TIMEOUT`으로 바꿉니다([녹음 파일 규격](../docs/specs/recording-file.md), S15P31E102-168에서 develop 병합 전). 무DB 기본 프로필에서는 만들지 않습니다. 주기 실행은 `eolssu.scheduling.enabled=false`로 끌 수 있으며, `databaseTest`는 공유 테스트 DB와 겹치지 않도록 이 값을 끕니다.

`growth`에는 악보 화면 체류 구간 기록 API `POST /api/users/me/score-views`(GRO-04)와 그 검증·중복 처리·저장소, 연습 기록지 요약(GRO-01, `GET /api/users/me/growth/summary`)·날짜별 값(GRO-02, `GET /api/users/me/growth/calendar`) API가 있습니다. `db` 프로필에서 조회 API는 `GrowthStatsConfig`, 체류 기록 API는 `ScoreViewConfig`로 연결하고 access_token의 회원으로 처리합니다. 체류 기록 요청·응답 계약은 [연습 시간 집계 기준](../docs/specs/practice-time.md#체류-기록-api-gro-04)을 따릅니다. 요약의 곡별 최고점·항목별 성장은 기간과 관계없이 회원의 전체 곡 연주(첫 음~끝 음)로 냅니다.

`analysis`에는 업로드된 연습 녹음을 AI로 분석하는 흐름(PRA-03 [분석 요청과 상태 조회 계약](../docs/specs/practice-analysis-failure.md))이 있습니다. `HttpAnalysisClient`가 AI의 `POST /v1/analyses`를 부르고, `MyBatisPracticeAnalysisRepository`가 같은 시도의 마감 전 `PENDING` 세션에만 결과를 저장합니다. `AnalyzePracticeSession`은 실패를 `SERVER_ERROR`, 제한 시간(기본 60초)을 넘긴 시도를 `TIMEOUT`으로 기록합니다. 한 흐름이 `practice_sessions`·`evaluations`·`note_results`를 함께 바꾸므로 `PracticeAnalysisMapper` 하나에 모았습니다. `db` 프로필의 분석 실행·AI 클라이언트 연결은 아래 `AnalysisOnUpload` 설명을 따릅니다.

`practice`의 `GET /api/practice-sessions/{sessionId}`(`PracticeSessionController`)는 API 명세의 연습 세션·분석 상태 조회입니다. 회원 본인의 세션만 돌려주고(없거나 남의 세션은 404 `RESOURCE_NOT_FOUND`), 제한 시간을 넘긴 `PENDING`은 응답 전에 `TIMEOUT`으로 바꿉니다(`GetPracticeSession`).

업로드 뒤 분석은 `AnalysisOnUpload`가 시작합니다. 업로드는 `PENDING`으로 바꾸는 트랜잭션 안에서 `RecordingUploaded(sessionId)`를 발행하고, 커밋 뒤에 `analysisExecutor`(기본 2개 동시·대기 50개)에서 `AnalyzePracticeSession`이 돕니다. 롤백된 업로드나 트랜잭션 밖 발행은 분석하지 않습니다. 음표는 `MyBatisPracticeScoreNotes`가 곡 악보(공통 악보 데이터)의 `NOTE`만 0부터 세어 연습 구간을 고르고, `pitches`로 반음·음 ID를 채웁니다. 이 연결은 `PracticeScoreNotes`·`RecordingReadUrls` 구현 Bean과 `ANALYSIS_AI_BASE_URL`이 모두 있을 때만 켜지며, 없으면 경고 로그만 남기고 세션은 제한 시간 뒤 `TIMEOUT`이 됩니다. 설정은 `eolssu.analysis.*`(`time-limit` 기본 60초, `ai-response-timeout` 기본은 제한 시간 + 5초이며 제한 시간보다 길어야 함, `workers`, `queue-capacity`)입니다. 두 시간은 숫자만 쓰면 초로 읽습니다.

| 기능 패키지 | 테이블 |
| --- | --- |
| `pitch`, `instrument`, `fingering` | `pitches`, `instruments`, `fingering_charts` |
| `jangdan`, `song`, `notation` | `jangdans`, `songs`, `notation_symbols` |
| `user`, `practice` | `users`, `user_song_settings`, `practice_sessions` |
| `auth`, `term`, `growth` | `user_social_accounts`, `email_verifications`, `refresh_tokens`, `terms`, `user_term_agreements`, `score_views` |
| `evaluation` | `evaluations`, `note_results` |
| `analysis` | 분석 결과 저장(`practice_sessions` 상태, `evaluations`, `note_results`) |
| `lesson` | `lessons`, `lesson_progress` |

Mapper는 `@Mapper`로 검색하고 XML namespace는 인터페이스 전체 이름, statement ID는 메서드 이름과 맞춥니다. Kotlin 생성자 매핑을 위해 `javaParameters`, `arg-name-based-constructor-auto-mapping`, `map-underscore-to-camel-case`를 사용합니다. XML은 조회 열을 명시합니다. JSON 열은 원문 `String`, 상태 열은 `String`, nullable 열은 Kotlin nullable로 보존하며 도메인 정책은 추가하지 않습니다.

Docker 이미지·개발/운영 Compose·Nginx·백엔드 Jenkins 구성과 실행 방법은 [인프라 실행 문서](../infra/README.md)를 따릅니다. Docker 빌드는 `docker build --tag eolsu-backend:check backend`를 저장소 루트에서 실행합니다. 실제 Jenkins 잡 등록·서버 배포는 별도 작업입니다.

인증은 토큰 검증·약관 목록·이메일 인증까지 포함하고, 가입·로그인 등 나머지 회원 API(S15P31E102-207~211)와 업무 API·운영 DB 적용은 아직 포함하지 않습니다. AI 호출은 클라이언트만 있고 앱에 연결하지 않았습니다. 실행·검증 방법은 이 README, 조사 결과는 루트 `docs/knowledge/backend/`, 공통 계약은 `docs/contracts/`, 기술 결정은 `docs/decisions/`에 기록합니다.

## 작업 기준

- Jira: [S15P31E102-60](https://ssafy.atlassian.net/browse/S15P31E102-60)
- 저장 계층: [S15P31E102-74](https://ssafy.atlassian.net/browse/S15P31E102-74) · [MyBatis·Flyway 구성 근거](../docs/knowledge/backend/mybatis-flyway-bootstrap.md)
- V2 ERD 보완: [S15P31E102-142](https://ssafy.atlassian.net/browse/S15P31E102-142) · [보완안·검증 기록](../docs/plans/S15P31E102-142.md)
- V3 녹음 원본 메타데이터: [S15P31E102-179](https://ssafy.atlassian.net/browse/S15P31E102-179) (PRA-02 녹음 파일 규격, S15P31E102-168)
- V4 분석 시도·실패 코드 제약: [S15P31E102-173](https://ssafy.atlassian.net/browse/S15P31E102-173) · [작업 기록](../docs/plans/S15P31E102-173.md)
- V5 단소 율명(`pitches`) 시드: [S15P31E102-186](https://ssafy.atlassian.net/browse/S15P31E102-186) · [PRA-03 스펙](../docs/specs/practice-analysis-failure.md)
- 체류 구간 기록: [S15P31E102-189](https://ssafy.atlassian.net/browse/S15P31E102-189) · [작업 기록](../docs/plans/S15P31E102-189.md)
- 구조안: [Notion 폴더 구조](https://app.notion.com/p/401a0252aa458076afe3e2ff12e6e5b1)
- 초기화 방법: [Spring Boot + Kotlin 공식 가이드](https://spring.io/guides/tutorials/spring-boot-kotlin/)
