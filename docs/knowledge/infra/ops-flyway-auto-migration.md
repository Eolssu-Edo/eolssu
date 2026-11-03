# 운영 Flyway 마이그레이션 자동화

- Jira: [S15P31E102-426](https://ssafy.atlassian.net/browse/S15P31E102-426). 실행·복구 명령: [운영 구성의 운영 DB 마이그레이션](../../../infra/runtime/README.md).
- 상태: 저장소 구현·검증 완료. 서버의 `deploy-backend`·`backup.py`는 MR 병합 뒤 별도 승인으로 설치한다. 설치 전까지는 아래 임시 규칙을 따른다.

## 배경

develop 병합은 Jenkins가 1분 안에 감지해 새 JAR을 배포한다. 운영 `ops` 프로필은 Flyway를 끄므로 스키마가 필요한 코드가 먼저 나가면 해당 API가 500이 된다(395의 모범 음원). 그래서 V12·V15·V16·V17·V18을 사람이 lock → `prechange` 백업 → 실행 중인 JAR의 일회성 `db` 프로필 Flyway → 사후 확인·백업 순서로 따로 적용했다.

2026-11-03에는 두 문제가 함께 드러났다.

- 409의 `V18__builtin_fingering_patterns.sql`이 15:00 KST에 병합됐다. 413의 `V18__user_sheet_drafts.sql`(MR !257)도 develop V17 다음으로 번호를 맞춘 상태였다. 파일 이름이 달라 Git 충돌이 없다. 또 Jenkins는 `test`·`build`만 돌리고 `databaseTest`를 돌리지 않으므로, 병합하면 같은 번호가 그대로 배포될 수 있었다.
- Jenkins #211(`6212d717`, 409 포함)이 06:24 UTC에 배포됐고, 422 작업자가 06:25 UTC에 운영 V18을 수동 적용했다. 그 사이 운지 API가 스키마보다 앞선 코드로 동작했을 수 있다.

## 선택

| 대안 | 판단 |
| --- | --- |
| `ops`에서 Flyway 켜기 | 기각. 앱 기동과 마이그레이션 실패가 묶이고, 교체 중 재기동마다 적용 시점이 생기며, 백업 시점을 통제하기 어렵다 |
| 사람이 병합 전에 적용(현행) | 자동화 전 임시 규칙으로만 유지. 병합·배포와 적용 담당이 달라 같은 500이 반복된다 |
| 배포 도구가 교체 직전에 적용 | 채택. 기존 공유 lock·`prechange` 백업·후보 검증·롤백 흐름 안에 수동 절차를 그대로 옮긴다 |

배포 도구는 후보 JAR의 마이그레이션과 운영 이력을 비교한다. 대기 중인 마이그레이션이 있을 때만 수동 적용 때 검증된 인자 그대로 일회성 컨테이너를 실행하고, 이력을 다시 확인한 뒤 교체한다. 교체가 실패해 JAR이 롤백돼도 스키마는 새 버전으로 남는다. 그래서 자동 적용은 이전 릴리스도 읽을 수 있는 추가형 변경으로 제한한다. 테이블·컬럼 DROP, RENAME(인덱스 제외), CHANGE, TRUNCATE, DELETE는 사람이 검토한 파일의 SHA-256 승인이 있어야 적용한다. 기존 V1~V18 중에는 V14만 이 조건에 걸린다.

번호 중복은 배포 도구에서도 거부한다. 다만 더 이른 단계에서 막기 위해 백엔드 `MigrationFileTests`를 일반 `test`에 추가했다. 이 테스트는 Jenkins의 `Backend unit tests`와 개발자의 로컬 `clean build`에서 돈다.

적용된 파일이 수정됐는지는 Flyway와 같은 줄 단위 CRC32로 계산해 운영 이력과 비교한다. 대기 중인 마이그레이션이 없어 Flyway를 실행하지 않는 배포에서도 이 검사는 한다. 2026-11-03 운영 이력 V1~V18 checksum 18개가 모두 저장소 파일 계산값과 일치했다(`infra/jenkins/tests/fixtures/`).

## 검증

- 백엔드 `MigrationFileTests`: develop은 통과한다. 413의 `V18__user_sheet_drafts.sql`을 함께 두면 `{18=[V18__builtin_fingering_patterns.sql, V18__user_sheet_drafts.sql]}`로 실패한다.
- `infra/jenkins/tests` 50개(기존 34개 + 16개), `infra/backup/tests` 15개: Python 3.9·3.12에서 통과. 계획(이력 불일치·실패 행·수정·순서·잔여 컨테이너·승인), 적용(대상 버전, 비밀값이 명령줄에 없음, 실패·시간 초과 때 컨테이너 보존), 배포 순서(후보 검증 뒤·교체 전), 사후 `postmigration` 백업을 포함한다.
- 리허설: 이 브랜치의 `bootJar`, 폐기용 MySQL 8.4.9(`temp-mysql`), 실제 `migration_plan`·`migrate` 코드. docker 호출만 로컬 java·mysql로 바꿨다.

| 경우 | 결과 |
| --- | --- |
| V17 DB + develop JAR | 계획 V18 → 적용 성공, 운지 92행. 재계획은 대기 없음(Flyway 기록 checksum과 일치) |
| 413 파일을 V19로 옮긴 JAR | V19 적용 성공, `draft_*` 4개 컬럼 |
| 적용된 V19 내용 수정 | `applied migration was edited`로 거부 |
| V20에 컬럼 DROP | 승인 없으면 거부 |
| 실패하는 V20 | exit 1, 컨테이너·로그 보존. 다음 배포는 잔여 컨테이너 → 실패 행 순서로 거부 |

마이그레이션 로그에서 DB 비밀번호는 발견되지 않았다. 실패한 V20의 첫 문장(컬럼 추가)은 MySQL DDL 특성상 남았다. 이런 경우 실패 뒤 실제 스키마 확인과 `prechange` 백업 대조가 필요한 이유다.

확인하지 못한 것: 운영 실행 이미지(`eolssu-backend-runtime`)·runtime network·실제 백업 수신을 포함한 서버 경로. 수동 적용 스크립트(`/opt/eolssu/ops395-v15-v16-20261102/apply-v15-v16.py`)와 같은 인자를 쓰지만, 설치 뒤 첫 스키마 변경 배포에서 단계 로그·이력·`postmigration` 수신을 확인해야 한다.

## 자동화 설치 전 임시 규칙

- 운영 적용 담당은 인프라(우동균)다. 스키마를 바꾸는 MR 작성자는 MR에 버전을 적고 병합 전에 알린다.
- 병합 직전 develop을 받아 번호를 최신 다음으로 맞추고 `test`·`databaseTest`를 통과시킨다.
- 추가형 변경은 인프라가 MR 최종 커밋의 JAR로 수동 절차를 먼저 적용한 뒤 바로 병합한다. 적용 뒤에는 파일을 바꾸지 않는다. 사후 DB 백업은 lock을 놓은 뒤 보낸다(V17 때 lock 안 호출이 timeout).
- 스키마 MR은 한 번에 하나씩 병합한다. 파괴적 변경은 앱 배포 순서를 따로 정한다.
