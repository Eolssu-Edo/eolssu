# 박찬형 담당 카탈로그 API 구현·검증

- 작업: [S15P31E102-288](https://ssafy.atlassian.net/browse/S15P31E102-288), Story 24 / Epic 15, 박찬형, 스프린트 59139, 진행 중. 사용자 후속 요청으로 API와 앱 연결 작업의 commit·Push·MR을 준비한다. 운영 배포와 Play 제출은 범위 밖이다.
- 기준: 2026-10-29 `origin/develop=95a0ce7795b6a61e08d05c05433bcd9fd9408ae7` 및 [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad). Person=찬형 박인 CAT-03~10 8개를 브라우저에서 읽었다. 이전 Android 261 변경은 다른 worktree에 보존했다.
- 계약과 초안 차이: [카탈로그 API 스펙](../../specs/catalog-read-apis.md). 현재 공통 정수 버전·ID·tick, 기존 nullable 저장 필드와 등록 데이터만 사용한다.

## 구현·통합·검증 구분

| 기능 | 확정/입력 근거 | 구현 상태 | 통합 상태 | 검증 결과·증거 | 남은 의존성·사용자 영향 |
| --- | --- | --- | --- | --- | --- |
| CAT-03 활성 악기 | Notion 401a0252aa458193a28ed814b71227de, instruments | 구현 완료 | 로컬 288, 미병합 | 통과: 활성/비활성·문자열 bigint·빈 목록 | 운영에는 기존 단소만 등록됨 |
| CAT-04 악보 | Notion 401a0252aa458163b48aeecac251ed79, 공통 score schema1, V6 | 구현 완료 | 로컬 288, 미병합 | 통과: 실제 4곡 원본 JSON·305 NOTE·17 숨표, REST/ID/tick·위치·버전/길이 불일치 | 원본 정수 계약을 보존. 앱 기존 CAT-02는 변경 없음 |
| CAT-05 모범 음원 | Notion 401a0252aa4581dea149f6f878bf52e5, 기술 후보 M4A 검증 기록 | 구현 완료 | 로컬 V8/API, 미병합 | 통과: 실제 MySQL 게시/버전/BPM/단위·HTTPS 메타데이터·404 | 운영 음원 공개 URL·사용권·PUBLISHED 적재 미완료. 파일 자체 재생 검사는 이번 API 검증 범위 밖 |
| CAT-06 율명 | Notion 401a0252aa458118b35dd90a941b893a, pitches/instruments | 구현 완료 | 로컬 288, 미병합 | 통과: PK 순서와 다른 semitone 음역 필터·등록 표기 | 설명/음 소리 미확보는 null |
| CAT-07 운지 | Notion 401a0252aa4581c5ba89c9dd2c39a53c, fingering_charts | 구현 완료 | 로컬 288, 미병합 | 통과: 악기/음 필터·비활성404·빈 목록 | 운영 운지/구조 이미지 검수·등록 미완료 |
| CAT-08 장단 상세 | Notion 401a0252aa45819da290f2c9209e23d2, jangdans | 구현 완료 | 로컬 288, 미병합 | 통과: 저장 구음·묶음·BPM 단위·잘못된 패턴500 | 장단 음원 미확보 null |
| CAT-09 기호 | Notion 401a0252aa45812b8461f9db7ae5bc78, notation_symbols | 구현 완료 | 로컬 288, 미병합 | 통과: 문자열 ID·nullable 이미지·빈 목록 | 운영 기호 설명/이미지 등록 미완료 |
| CAT-10 장단 목록 | Notion 401a0252aa45805b8357fbc7d3dc4a9d, jangdans | 구현 완료 | 로컬 288, 미병합 | 통과: 소박/묶음 수·ID 순서·빈 목록 | 제품 추천 순서를 확정한 것이 아님 |

구현 완료는 운영 콘텐츠 확보·배포 또는 Android 전체 흐름 완료를 뜻하지 않는다. 245/250은 이번 조회 시점에 develop에 병합된 작업으로 확인했으며 중복 수정하지 않았다.

## 실행 환경과 검사

- Windows, JDK Temurin 21.0.12, backend Gradle Wrapper 9.8.2, Kotlin 2.3.21, Spring Boot 4.1.1.
- MySQL 8.4, 전용 Docker 컨테이너 `eolssu-261-catalog-db`, `127.0.0.1:13316`. 운영/공유 개발 DB를 사용하지 않았다. 테스트는 기존 V1~V7 DB에 V8 적용·재실행 무변경을 검증한다.
- 단위와 MockMvc: `gradlew.bat clean build` 통과, **341개 / 59 suites, 실패·오류·skip 0**. 이 시점 뒤 독립 검토 수정은 영향 검사로 별도 확인했다.
- 실제 DB/HTTP: `gradlew.bat databaseTest` 통과, **111개 / 24 suites, 실패·오류·skip 0**. 신규 12개(학습7·악보3·음원2), 기존 인증/탈퇴/성장/업로드/분석/Swagger 회귀 포함.
- 실제 HTTP의 잘못된 로그인 쿠키에서도 공개 카탈로그 접근, 비공개/삭제 리소스 미노출, Swagger의 공개 보안·응답 스키마를 확인했다. 테스트용 미디어 URL은 HTTP 응답 필드 검사이며 실제 파일 다운로드 성공으로 보고하지 않는다.
- 독립 검토 수정 뒤 영향 단위 검사 **13개**, 실제 DB/HTTP·OpenAPI 검사 **8개** 통과(실패·오류·skip 0), 실행 JAR 재생성. 전체 검사를 불필요하게 반복하지 않고 변경된 악보/음원 및 API 스키마를 다시 확인했다.
- 최종 bootJar를 별도 로컬 포트 18088에서 실행해 8개 경로를 직접 호출했다. 악기1·율명11·장단3과 실제 악보 조회 200, 운지/기호 빈 목록200, 미등록 모범 음원404가 기대와 일치했다. 테스트 fixture가 운영 콘텐츠처럼 남아 있지 않음을 확인했다. 응답 JSON과 HTTP 검사표를 증거 묶음에 보존했다.

재현은 `backend/`에서 JDK 21과 별도 MySQL 8.4의 `TEST_DB_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD`를 환경에 주입하고 위 Wrapper 명령을 실행한다. 비밀값을 파일/보고서에 적지 않는다. 테스트 fixture는 해당 ID 범위만 정리하며 시드·Flyway 이력은 전용 DB에 남는다.

### 최신 develop 통합 후 MR 검증

2026-10-29 사용자 MR 요청을 받아 `origin/develop=bf07d4b`로 동기화했다. 기존 변경을 백업한 뒤 fast-forward·복원했고 충돌은 없었다. 새 Swagger 공개 보안 설정과 내 정보 이메일 응답을 보존했다. 이 통합 소스에서 `clean build` **344개 / 60 suites**, 실제 MySQL `databaseTest` **111개 / 24 suites**를 다시 실행해 모두 통과했다(실패·오류·skip 0). `git diff --check`도 통과했다. 테스트가 끝난 뒤 전용 DB 컨테이너를 정지했으며 운영 서버·DB는 변경하지 않았다.

MR은 이 Task의 서버 코드·V8·계약·검증 기록을 대상으로 한다. 앱 연결은 별도 261에서 검증하며, 실제 음원/학습 콘텐츠 등록과 운영 배포는 이 MR의 완료 증거에 포함하지 않는다. Jira는 MR 병합 전까지 진행 중을 유지한다.

## 독립 검토

작성 맥락을 전달하지 않은 별도 검토자가 요구사항·원문 증거·diff·테스트를 읽었다. 두 P2 지적을 수정했고 재검토에서 추가 지적이 없음을 확인했다.

1. 타임라인 최종 tick과 `전체 정간 수 × ticksPerJeonggan`의 일치 검사 추가. 마지막 길이 ±1·정간 tick 변경에 409 회귀 검사.
2. 모범 음원의 scalar 버전뿐 아니라 `score_json.score.scoreVersion` 정수·값 및 내부 bpmUnit까지 조회 조건으로 대조. 각각 불일치할 때 실제 DB 404 검사 추가.

추가로 악보 조회에 녹음 세션의 20~240 한도를 적용하지 않고, 기존 songs 저장 계약인 30~400을 보존하도록 경계 검사를 추가했다. 검토자는 직접 테스트를 실행하지 않았으며 실행 증거는 위 root 검사 결과다.

## 후속 인계

- API/DB 배포: 박찬형·배포 담당(실행 담당 미정). V8 적용 후 애플리케이션 배포, 공개 8경로 및 기존 API 회귀 확인. 실제 배포 권한 요청 전 로컬까지 완료.
- 콘텐츠: 운지·기호·구조 이미지·음 소리·모범 음원 공개 URL과 권리/악보 버전 검수. 음원 Task159 및 기존 데이터 업무의 담당을 임의 변경하지 않았다.
- Android 261 통합: 기존 임시 `feature/instrument/api/InstrumentController`와 288의 `/instruments`를 동시에 두지 않는다. 288 Controller로 대체한다. 2026-10-29 현재 245/250/191 및 FE285는 develop에 병합됐으며, 261에서 이를 재사용해 API 소비자 통합/실행을 검증한다. 이 서버 문서의 검증 결과를 앱 검증으로 확대하지 않는다.
- 실기기·Play: 이번 서버 API 검사는 Android APK/AAB 재생성·마이크/오디오 포커스·Google 심사 또는 스토어 제출 검증이 아니다. 기존 release3 산출물과 정책·서명·실기기 보류 사유는 유지한다.
