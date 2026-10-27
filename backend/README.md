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
| 실행 JAR 생성 | `.\gradlew.bat bootJar` | `./gradlew bootJar` |

기본 주소는 `http://localhost:8080`입니다. 아직 업무 API가 없으므로 `/` 요청의 `404`는 정상입니다. 시작 로그에서 애플리케이션과 내장 Tomcat의 기동을 확인하며, 개발 서버는 `Ctrl+C`로 종료합니다.

실행 JAR은 `build/libs/eolssu-0.0.1-SNAPSHOT.jar`입니다. `java -jar build/libs/eolssu-0.0.1-SNAPSHOT.jar`로 실행합니다. `*-plain.jar`는 실행용 Spring Boot JAR이 아닙니다.

기본 필수 검사는 `clean build`입니다. 시작 테스트는 외부 DB·AI 서버 없이 임의 포트에 실제 HTTP 서버를 띄우고, DB 관련 Bean 미생성과 미등록 경로의 `404` 응답을 확인합니다. 결과는 `build/reports/tests/test/index.html`에 생성됩니다. 저장 모델·쿼리·마이그레이션 변경에서는 아래의 `databaseTest`도 병합 전에 수행합니다. AI 통합과 모델 품질은 두 검사 범위에 포함하지 않습니다.

## 환경 설정

공통 설정은 `src/main/resources/application.yml`, 마이그레이션을 포함하는 DB 설정은 `application-db.yml`에 둡니다. 기본 실행은 DB 없이 시작하며, `SERVER_PORT` 기본값은 `8080`입니다. 운영은 기존 DB의 연결 검사만 수행하는 `ops` 프로필을 사용합니다. `db` 프로필의 Flyway 적용은 별도로 명시적으로 활성화합니다.

### 준비 상태 검사

`GET /api/actuator/health/readiness`는 준비되면 HTTP 200과 `{"status":"UP"}`, 준비되지 않으면 HTTP 503을 반환합니다. 기본 개발 모드는 애플리케이션 준비 상태만 확인합니다. 운영 `ops` 프로필은 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`로 기존 MySQL에 연결해 DB health를 함께 확인합니다. DB 접속 실패 시 프로세스가 살아 있어도 readiness는 DOWN이고 `/api/actuator/health/liveness`는 UP일 수 있습니다.

운영 `ops`에서는 Flyway와 SQL 초기화를 끕니다. health 요청은 연결·검증 쿼리만 수행하며 스키마나 업무 데이터를 바꾸지 않습니다. 기존 DB에 마이그레이션을 실행하려고 `db` 프로필을 함께 활성화하지 않습니다. 연결 대기는 2초, 풀은 최대 3개입니다. HTTP에는 health만 공개하고 상세·컴포넌트·JMX를 숨깁니다. `env`·`beans`·`configprops`는 404입니다.

필수 `clean build`에서 실제 HTTP 준비 응답·관리 정보 비공개와 연결할 수 없는 격리 DB 대상의 readiness 503/liveness 200·Flyway Bean 부재를 검사합니다. 운영 후보에서는 기존 DB 연결의 UP 응답도 별도로 확인합니다.

```powershell
$env:SERVER_PORT = '8081'
.\gradlew.bat bootRun
```

macOS / Linux에서는 `SERVER_PORT=8081 ./gradlew bootRun`을 사용합니다. `.env.example`은 변수 설명용이며 `.env` 파일을 자동으로 읽지 않습니다. 실제 비밀값은 터미널·IDE·배포 환경에서 주입하고 커밋하지 않습니다.

### 보안과 공통 오류 응답

Spring Security로 API 경로를 나눕니다. 로그인 토큰 발급·확인은 아직 없어 회원 전용 API는 모두 401입니다(S15P31E102-143 작업 순서 4·5에서 추가).

- 공개: 로그인 전 API(`/api/auth/sign-in`·`sign-up`·`refresh`·`password-reset`·`oauth/**`·`email-verifications/**`, POST), 카탈로그·약관 조회(`/api/terms`·`songs`·`instruments`·`pitches`·`jangdans`·`notation-symbols`, GET), `/api/actuator/**`(health만 노출).
- 그 밖의 `/api/**`는 로그인이 필요합니다. 오류는 API 명세 공통 형식 `{httpStatus, isSuccess:false, message, code, result{errorCode}}`로 응답합니다(401 `UNAUTHENTICATED`, 403 `FORBIDDEN`, 404 `RESOURCE_NOT_FOUND`, 415 `UNSUPPORTED_MEDIA_TYPE`).
- CSRF: CSRF 토큰 대신 SameSite=Lax 쿠키와 `StateChangingRequestFilter`로 막습니다. `/api/**`의 POST·PUT·PATCH·DELETE는 `Origin` 헤더가 있으면 같은 사이트(요청 `Host`와 같은 호스트)이거나 `SECURITY_ALLOWED_ORIGINS`의 주소여야 합니다(403). 본문은 `application/json`만 받고, `Content-Type`이 없으면 본문도 없어야 합니다(415). 녹음 업로드(`POST /api/practice-sessions/{id}/recording`)만 `multipart/form-data`를 받습니다. 앱처럼 `Origin`을 보내지 않는 요청은 Origin 확인을 건너뜁니다.
- CORS: 운영은 웹과 API가 같은 주소라 열지 않습니다. FE 개발 서버처럼 다른 주소에서 쿠키를 포함해 부를 때만 `SECURITY_ALLOWED_ORIGINS`에 그 주소를 쉼표로 구분해 넣습니다.

```powershell
$env:SECURITY_ALLOWED_ORIGINS = 'http://localhost:5173'
.\gradlew.bat bootRun
```

### MySQL 연결과 Flyway

비어 있는 MySQL 8.4 DB와 접속 계정을 먼저 준비한 뒤 아래 환경변수를 설정합니다. URL에는 실제 DB 이름을 지정합니다. 기존 서버의 `eolsu`를 `eolssu`로 변경하는 작업은 포함하지 않습니다.

| 환경변수 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=db` | DataSource·MyBatis·Flyway 활성화 |
| `DB_URL` | JDBC URL. 예: `jdbc:mysql://localhost:3306/eolssu` |
| `DB_USERNAME`, `DB_PASSWORD` | 해당 DB의 접속 정보. 기본값 없음 |

```powershell
$env:SPRING_PROFILES_ACTIVE = 'db'
$env:DB_URL = 'jdbc:mysql://localhost:3306/eolssu'
$env:DB_USERNAME = 'eolssu'
$env:DB_PASSWORD = '<로컬 DB 비밀번호>'
.\gradlew.bat bootRun
```

macOS / Linux에서는 같은 값을 터미널에서 `export`한 뒤 `./gradlew bootRun`을 실행합니다. JAR도 같은 환경변수로 실행합니다. `db` 프로필에서 연결·마이그레이션 검증이 실패하면 기동에 실패합니다.

- Flyway가 `src/main/resources/db/migration/`의 `V1__initial_schema.sql`, `V2__auth_user_practice_extensions.sql`을 순서대로 적용하고 `flyway_schema_history`에 기록합니다. 다시 시작하면 이력을 검증하고 이미 적용한 버전은 재실행하지 않습니다.
- 적용한 버전 파일은 수정하지 않고 `V3__설명.sql`처럼 새 버전을 추가합니다. `schema.sql` 자동 초기화는 사용하지 않습니다.
- MySQL DDL은 실패해도 되돌려지지 않습니다. V2는 DDL 전에 기존 데이터를 확인해, 세마치·굿거리·중모리·타령 밖의 장단, 개인정보가 남은 탈퇴 회원, 20~240 밖의 세션 BPM이 있으면 `chk_v2_preconditions` 위반으로 스키마를 바꾸지 않고 기동에 실패합니다. 기존 장단·세션의 `bpm_unit`은 장단 이름으로 채웁니다.
- 이때 `flyway_schema_history`에는 실패 행(`version = '2'`, `success = 0`)이 남아, 데이터만 고쳐 다시 시작하면 `Detected failed migration to version 2`로 다시 실패합니다. 이 프로젝트에는 Flyway CLI·Gradle 작업이 없으므로 같은 DB에 MySQL 클라이언트로 접속해 해당 행을 고친 뒤 실패 행을 지우고 `db` 프로필로 다시 시작합니다.

```sql
SELECT version, success FROM flyway_schema_history;               -- 2, 0 확인
DELETE FROM flyway_schema_history WHERE version = '2' AND success = 0;
```
- 자동 baseline과 Flyway clean은 비활성화했습니다. 이미 테이블이 있는 DB에 이력 없이 연결하면 자동 편입하지 않고 실패합니다. 기존 DB 도입은 스키마 일치 확인과 별도 적용 계획이 필요합니다.
- 연결마다 MySQL 세션 시간대를 `+00:00`으로 맞춥니다. `DATETIME(6)` 저장 모델의 `LocalDateTime`은 UTC 값으로 취급하고 API 시간대 변환은 기능 구현에서 정합니다.

### 실제 MySQL 통합 검사

담당 개발자가 저장 계층 변경 후, MR 병합 전에 실행합니다. **운영·공유 개발 DB가 아닌 비어 있는 폐기 가능한 MySQL 8.4 DB**를 준비합니다. V1·V2 DDL과 Flyway 이력은 남고, 검사 데이터는 테스트 트랜잭션 종료 시 롤백됩니다. 검사는 DB를 자동 생성하거나 삭제하지 않습니다.

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/eolssu_test'
$env:TEST_DB_USERNAME = 'eolssu_test'
$env:TEST_DB_PASSWORD = '<테스트 DB 비밀번호>'
.\gradlew.bat databaseTest
```

macOS / Linux에서는 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 `export`한 뒤 `./gradlew databaseTest`를 실행합니다. 이 검사는 앱용 `DB_*` 대신 `TEST_DB_*`로 접속하며 세 변수가 없으면 실패합니다. 보고서는 `build/reports/tests/databaseTest/index.html`입니다.

검사 범위는 MySQL 8.4 V1·V2 적용·재실행·검증, 19개 테이블의 Mapper Bean·XML·생성자 매핑, nullable 값·JSON·한글/한자·전각 기호·마이크로초·UTC 세션, FK·CHECK·공통 레슨 유일성과 V2의 회원 상태·간편 로그인 연결·약관·인증·토큰·곡별 BPM·연습 기록 중복 키·체류 기록 제약(위반 제약 이름까지 확인), 탈퇴 일괄 삭제에서 회원 세션을 한 문장으로 지우는 동작입니다. 기본 `test`와 CI의 `clean build`에는 이 DB 검사를 포함하지 않습니다.

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
│   │   ├── config/SecurityConfig.kt, SecurityProperties.kt
│   │   ├── common/error/       # 공통 오류 응답·예외 처리
│   │   ├── common/security/    # CSRF 필터
│   │   └── feature/<기능>/infrastructure/persistence/
│   │       ├── <테이블명>Row.kt
│   │       └── <테이블명>Mapper.kt
│   └── resources/
│       ├── application.yml / application-db.yml
│       ├── db/migration/V1__initial_schema.sql, V2__auth_user_practice_extensions.sql
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

현재는 저장소 초기 구조로 PK 조회 `findById`만 제공합니다. 존재하지 않는 ID는 `null`을 반환하며, 삭제·공개 상태 필터는 적용하지 않습니다. Row는 저장 계층 전용이므로 API 응답으로 그대로 노출하지 않습니다. 기능 구현 시 domain 저장소 계약과 infrastructure 구현을 연결합니다.

| 기능 패키지 | 테이블 |
| --- | --- |
| `pitch`, `instrument`, `fingering` | `pitches`, `instruments`, `fingering_charts` |
| `jangdan`, `song`, `notation` | `jangdans`, `songs`, `notation_symbols` |
| `user`, `practice` | `users`, `user_song_settings`, `practice_sessions` |
| `auth`, `term`, `growth` | `user_social_accounts`, `email_verifications`, `refresh_tokens`, `terms`, `user_term_agreements`, `score_views` |
| `evaluation` | `evaluations`, `note_results` |
| `lesson` | `lessons`, `lesson_progress` |

Mapper는 `@Mapper`로 검색하고 XML namespace는 인터페이스 전체 이름, statement ID는 메서드 이름과 맞춥니다. Kotlin 생성자 매핑을 위해 `javaParameters`, `arg-name-based-constructor-auto-mapping`, `map-underscore-to-camel-case`를 사용합니다. XML은 조회 열을 명시합니다. JSON 열은 원문 `String`, 상태 열은 `String`, nullable 열은 Kotlin nullable로 보존하며 도메인 정책은 추가하지 않습니다.

Docker 이미지·개발/운영 Compose·Nginx·백엔드 Jenkins 구성과 실행 방법은 [인프라 실행 문서](../infra/README.md)를 따릅니다. Docker 빌드는 `docker build --tag eolsu-backend:check backend`를 저장소 루트에서 실행합니다. 실제 Jenkins 잡 등록·서버 배포는 별도 작업입니다.

인증 토큰 발급·업무 API·AI 호출·운영 DB 적용은 아직 포함하지 않습니다(인증·회원은 S15P31E102-143에서 구현 중). 실행·검증 방법은 이 README, 조사 결과는 루트 `docs/knowledge/backend/`, 공통 계약은 `docs/contracts/`, 기술 결정은 `docs/decisions/`에 기록합니다.

## 작업 기준

- Jira: [S15P31E102-60](https://ssafy.atlassian.net/browse/S15P31E102-60)
- 저장 계층: [S15P31E102-74](https://ssafy.atlassian.net/browse/S15P31E102-74) · [MyBatis·Flyway 구성 근거](../docs/knowledge/backend/mybatis-flyway-bootstrap.md)
- V2 ERD 보완: [S15P31E102-142](https://ssafy.atlassian.net/browse/S15P31E102-142) · [보완안·검증 기록](../docs/plans/S15P31E102-142.md)
- 구조안: [Notion 폴더 구조](https://app.notion.com/p/401a0252aa458076afe3e2ff12e6e5b1)
- 초기화 방법: [Spring Boot + Kotlin 공식 가이드](https://spring.io/guides/tutorials/spring-boot-kotlin/)
