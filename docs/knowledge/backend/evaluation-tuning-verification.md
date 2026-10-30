# 평가 조율 정보 저장·실제 AI 연동 검증

- Task: [S15P31E102-304](https://ssafy.atlassian.net/browse/S15P31E102-304), Story 36 평가 결과 저장.
- 실행: 2026-10-30, Windows JDK 21.0.11·Docker Desktop의 격리 MySQL 8.4.13·실제 AI 서비스. 코드 기준은 develop `f0f1191`에 이 Task 변경을 더한 상태다.
- 계약: [평가 조회 API](../../specs/practice-evaluation-api.md), [분석 요청·응답](../../specs/practice-analysis-failure.md), [V11 마이그레이션](../../../backend/src/main/resources/db/migration/V11__evaluation_tuning_metadata.sql).

최초 검증 시 조율 마이그레이션은 V10이었다. 이후 최신 develop `5c38033`의 카카오 웹훅 V10과 번호가 겹쳐, 조율 SQL 내용은 유지하고 **V11**로 옮겼다. 아래 최초 실행의 버전·JAR·시험 수는 당시 증거이며, 통합 후 재검증은 별도 기록한다.

## 결과

**실제 `POST /v1/analyses` 응답의 두 값이 수정한 백엔드를 거쳐 MySQL에 저장되고 평가 GET 응답에 그대로 나오는 것을 확인했다.** 알고리즘이나 점수는 바꾸지 않았다.

| 입력·세션 | AI 응답 | DB | 평가 조회 | 저장 점수(전체/음정/박자/길이) |
| --- | --- | --- | --- | --- |
| 두 음을 +25cent로 합성, 991305 | `25.0 / false` | `25.0 / 0` | `25.0 / false` | 100/100/100/100 |
| 두 음을 +250cent로 합성, 991306 | `255.0 / true` | `255.0 / 1` | `255.0 / true` | 67/0/100/100 |
| V9에서 저장한 기존 평가 991304 | 해당 없음 | `NULL / NULL` | `null / null` | 84/88/80/84 유지 |

표의 두 값은 `tuningOffsetCent / tuningOutOfRange` 순서다. 합성 설정 +250과 AI 추정 +255는 서로 다른 값이며, 백엔드는 **AI가 실제 반환한 255를 보존**했다. 각 신규 세션은 retry HTTP 202 → DONE → evaluation HTTP 200, 분석 시도 2, 평가 1행·음표 결과 2행이다. 음표 판정은 첫 입력 두 음 모두 CORRECT, 두 번째 입력 모두 PITCH였다.

## 실행 경로와 입력

1. 폐기 가능한 MySQL에 `db` 프로필·`spring.flyway.target=9`로 V1~V9를 적용했다. 조율 컬럼 0개인 상태에서 기존 평가 1행을 넣었다.
2. 같은 변경 JAR을 target 제한 없이 시작해 V10을 적용했다. 기존 점수·모델 버전은 유지되고 두 새 컬럼만 NULL임을 SQL과 실제 GET으로 확인했다.
3. 격리 DB의 곡 1을 검증용 두 음(중0/임0, 반음5/7, `ticksPerBeat=1`, `startTick=0/1`, 각 `durationTick=1`)으로 준비했다. BPM96, DOTTED_QUARTER, idx0~1이다.
4. 고정 테스트 회원·합성 서명 쿠키와 `FAILED/SERVER_ERROR` 녹음 세션 두 개를 준비했다. `Content-Type: application/json`, `Idempotency-Key`를 넣어 `POST /api/practice-sessions/{id}/retry`를 호출했다. 인증 비밀값과 쿠키는 결과에 남기지 않았다.
5. 실제 `AnalysisOnUpload → HttpAnalysisClient → AI HTTP → MyBatisPracticeAnalysisRepository`를 사용했다. 저장소만 임시 HTTP 파일 서버로 대체했다. S3Presigner는 테스트 전용 설정으로 URL을 생성하고 AI가 해당 AAC를 실제 다운로드·디코딩했다. 투명 HTTP 중계가 실제 AI 응답을 변경 없이 전달하면서 두 필드·점수·음표만 증거로 남겼다.
6. DONE 뒤 SQL과 `GET /api/practice-sessions/{id}/evaluation`을 읽어 실제 AI 응답과 비교했다. 네 점수, 두 조율 값, 평가 1행과 음표 2행이 일치했다.

입력은 44.1kHz·모노·약1.25초 AAC/m4a이며 기본음과 배음 1/0.5/0.25, 10ms 페이드로 생성했다. 첫 파일 12,123바이트의 SHA-256은 `33d6ebba5ea9c070c73936e6d070b09e95bfa81286c0c7d220ff235c247baf85`, 두 번째 12,126바이트는 `f483e1a637c4a38e0aebf182d856dfef4902083e704866229973d9282d6532f6`이다.

백엔드 JAR SHA-256은 `9601bddf5e70297ee22d56299cb2555908ffea61b59b8c8a9786ad11448df665`다. AI는 저장소 Dockerfile과 잠금 파일로 `docker build --tag eolssu-ai:qa304 ai`를 실행해 만들었다. 이미지 ID는 `sha256:14b03b86fd2ebcbb3d9666f3b305bad5833e13f9f1d85beb0c4911442d463583`, 응답 modelVersion은 `pyin-2`다. 운영 이미지 교체·운영 DB 변경은 하지 않았다.

## 자동 검사와 발견한 문제

`backend/`에서 JDK21, 폐기 MySQL의 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 지정했다.

```powershell
./gradlew.bat test --tests '*HttpAnalysisClientTests'
./gradlew.bat clean build databaseTest
./gradlew.bat build databaseTest
```

- 구현 전 21개 중 새 조율 값 보존 검사 1개가 실패해 기존 코드의 누락을 재현했다.
- 최종 기본 검사 **369개**, DB 검사 **125개**, 실패·오류·건너뜀 없음. 값·범위 초과·기존 null·부분 누락·유한하지 않은 수·동일 시도/마감/중복 저장 조건·소유권을 확인했다.
- 첫 DB 실행에서 `EvaluationRow`의 기본 인자가 Kotlin 생성자를 추가해 MyBatis의 이름 기반 생성자 선택이 실패했다. 조회 행 타입의 기본값을 제거하고 실제 MySQL에서 다시 통과했다.
- 전용 MySQL의 기본 151 연결 한도에 여러 Spring 테스트 컨텍스트의 풀을 동시에 유지하면서 도달했다. **이 폐기 DB만** `max_connections=500`으로 설정해 전체 검사를 통과했다. 앱이나 운영 DB 설정을 바꾸지 않았다.
- 실제 OpenAPI에서도 `tuningOffsetCent`는 number/double/nullable, `tuningOutOfRange`는 boolean/nullable로 확인했다.
- FE의 `ApiJson.ignoreUnknownKeys=true`를 확인했다. UI 경고·프론트 모델 변경은 이 Task 범위에 포함하지 않는다.

## 최신 develop 통합 후 재검증

2026-10-30 `origin/develop`의 `5c38033`을 반영했다. 소스 충돌은 없었지만 카카오 웹훅 마이그레이션이 V10을 사용하므로 조율 마이그레이션을 V11로 옮기고 최신 버전 기대값·문서 링크를 고쳤다. 조율 SQL 내용은 이전과 동일하며 Kakao V10을 변경하지 않았다.

- 같은 격리 환경에서 `./gradlew.bat clean build databaseTest --console=plain` 통과: 기본 **377개**, DB **137개**, 실패·오류·건너뜀 0. 새 DB의 V1~V11 적용과 재적용 없음, 두 기능의 테이블·매핑을 함께 확인했다.
- 별도 실행 DB에 `spring.flyway.target=10`으로 Kakao V10까지 적용하고 조율 컬럼이 0개인 상태에서 기존 평가를 넣었다. 제한 없이 다시 기동해 V11로 올린 뒤 조율 컬럼 2개·Kakao 테이블 2개, 기존 점수 84/88/80/84와 null/null을 확인했다.
- 통합 JAR에서 같은 실제 AI 입력으로 retry→DONE→DB→평가 GET을 다시 실행했다. `25.0/false`, `255.0/true`, 점수와 음표 수가 다시 일치했고 OpenAPI nullable 계약도 유지됐다. 운영 DB는 사용하지 않았다.
- 통합 JAR SHA-256: `adafa91b4010333961c8c2efb3829ae0dcaabe3b083e3fa0bf9e18bc1cb32c63`. AI 이미지와 입력 파일은 최초 검증과 같다.
- FE는 `5c38033`과 파일 차이가 없고 `ignoreUnknownKeys=true`가 유지됨을 별도로 검토했다. FE 전체 빌드와 실기기 검사는 반복하지 않았다.

## 운영 V11 적용 결과

2026-10-30 사용자가 백업 후 운영 V11 적용과 정상 확인 후 MR Draft 해제를 승인했다. 배포 잠금 아래 검증한 통합 JAR을 별도 일회성 컨테이너에서 `db`·non-web·스케줄링 비활성·Flyway target 11로 실행했다. 운영 JAR의 V1~V10 SQL이 후보와 동일함을 확인했고 백업 직후에도 이력·컬럼 부재·서비스 ID를 다시 확인했다.

- 변경 직전 DB 백업 `20261030T024426Z-bc475652c699`, 설정 백업 `20261030T024429Z-149db97ac8e4`를 edo2에 수신한 뒤 적용했다. 수신 시각은 각각 11:44:29, 11:44:55 KST다. 수신 영수증뿐 아니라 저장된 암호문 SHA-256도 대조했다.
- 실행 종료 코드 0, Flyway 1~11 전부 성공. `tuning_offset_cent DOUBLE NULL`, `tuning_out_of_range BOOLEAN NULL`과 활성 CHECK를 확인했다.
- 기존 평가 3건의 원래 모든 컬럼을 ID별 SHA-256으로 비교해 모두 동일했고, 새 조율 값은 모두 NULL이었다. 운영 검증용 평가를 추가하지 않았다.
- backend·frontend·MySQL 컨테이너 ID와 시작 시각, MySQL 물리 볼륨이 유지됐다. 모두 healthy, readiness UP, `/api/terms`·`/` HTTP 200을 확인했다. 일회성 마이그레이션 컨테이너는 제거했다.
- 최종 확인 시각은 **11:45:27 KST**다. 후보 JAR·실행 스크립트·마이그레이션 로그·백업 수신 기록·원래 값 해시·`receipt.json`은 edo1의 root 전용 `/opt/eolssu/ops304-v11-20261030/`에 보존했다.

DB 선행 조건은 완료했다. 이 실행에서 운영 앱은 기존 `5c38033` JAR을 유지했으며 MR 병합·새 백엔드 배포는 하지 않았다. 조율 값을 포함한 새 API의 운영 검증은 새 코드 배포 뒤 수행한다.

운영 적용 중 develop에 `884c74f`(녹음 지원 안내·탈퇴 경합 수정·평가 상세 조회)가 병합돼 MR178의 충돌을 확인했다. 공통 평가 조회의 시작·끝 음표 번호와 두 조율 필드, 음표의 예상·검출 pitch ID를 모두 보존해 5파일 충돌을 해결했다. 통합 후 `clean build databaseTest`는 기본 **396개**·DB **146개**, 실패·오류·건너뜀 0으로 통과했다. 조율 조회와 새 음표 상세·취약 구간 조회 검사를 함께 포함한다. V11 SQL은 운영 적용본과 동일하며 운영 DDL을 다시 실행하지 않았다. 이 통합 JAR의 SHA-256은 `352f10a56685befb67b0e058031cf670764b19629320c82e6219ad14bda14c87`이다.

## 적용 조건과 검증 한계

운영 `ops`는 Flyway가 꺼져 있으므로 **V11 적용 후 새 백엔드를 배포**해야 한다. 이전 코드는 nullable 새 컬럼을 무시할 수 있어 코드 롤백 시 컬럼을 삭제하지 않는다.

이번 실행은 실제 AI·수정 백엔드·DB·평가 조회 연결을 증명한다. 녹음 업로드/ffprobe, 실제 R2 인증·저장, FE 녹음 화면과 실악기 채점 품질은 다시 검증하지 않았다. 실제 AI 요청 처리 시간은 첫 건 7.740초, 둘째 0.165초였지만 짧은 합성음·로컬 환경의 관찰이며 운영 성능 보장은 아니다. 원격 연결을 묶어 실행하는 명령이 자동 승인 검토에서 차단되어 검증 환경을 로컬로 바꿨고, 변경 코드의 실제 분석·저장 검증 범위는 유지했다.
