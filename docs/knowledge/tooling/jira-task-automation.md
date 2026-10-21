# Jira Task 생성 자동화 이식

- 작업: `S15P31E102-47 Jira 업무 생성 자동화 이식`
- 기준: MR !5의 `5b25d90` 상태에서 이식. 원격 `develop`에는 이 문서 작업 기준이 아직 포함되지 않은 상태를 확인했다.
- 사용자 요청: Task를 기존 Story에 `다음과 관련됨`으로 연결하고 포인트는 입력하지 않는다.
- 이번 이식 업무의 Jira 배치: 사용자가 기존 노란색 `기획·검증`을 지정하여 Task `S15P31E102-47`의 상위 항목을 Epic `S15P31E102-1`로 연결했다. 이 배치는 도구가 생성하는 Task의 Story 연결 규칙을 바꾸지 않는다.

## 출처와 관찰

이전 프로젝트 `S15P21E106`의 `develop` 커밋 `7d86dab557e630b81371868b9ea48a5227894931`에서 다음 자료를 확인했다.

- [기존 생성 스크립트](https://lab.ssafy.com/s15-bigdata-dist-sub1/S15P21E106/-/blob/7d86dab557e630b81371868b9ea48a5227894931/scripts/jira/create_issues.py)
- [기존 사용 문서](https://lab.ssafy.com/s15-bigdata-dist-sub1/S15P21E106/-/blob/7d86dab557e630b81371868b9ea48a5227894931/docs/jira/README.md)

기존 도구는 YAML 계획으로 Epic·Task를 생성하고, Task에 Epic 상위 항목·스토리 포인트·활성 스프린트를 입력했다. 이번 도구는 YAML 읽기, 개인 인증, 생성 식별자, 생성 Key 저장, 미리보기 방식을 가져오되 프로젝트 규칙에 맞게 API 요청과 재개 처리를 수정했다. 이전 프로젝트의 실행 지침은 현재 프로젝트 지침으로 가져오지 않았다.

이 자동화는 Jira 업무 생성 도구다. GitLab의 번호 링크 설정이나 Jira 개발 패널의 브랜치·커밋·MR 동기화는 수행하지 않는다.

## 적용 내용

| 항목 | 이식 결과와 근거 |
| --- | --- |
| 생성 유형 | Task만 허용. Story·Epic 생성 제거 |
| 연결 | 실제 Story를 사전 조회하고 `/rest/api/3/issueLink`로 관련됨 링크 생성. `parent` 미사용 |
| 포인트 | 입력·추정·필드 검색 제거. YAML의 포인트 필드도 거부 |
| 스프린트 | 자동 배정·보드 ID 의존 제거. 현재 프로젝트의 확정 요구가 아님 |
| 본문 | 선택 입력. 고정 제목·본문 양식을 강제하지 않음 |
| 기존 업무 | 내용·담당자·상태·레이블 보존. 별도 확인 없이 완료·타 담당자 Task를 편입하지 않음 |
| 실행 환경 | 로컬 Python 3.11 이상과 PyYAML. EC2·CI·앱 설치 불필요 |
| 재시도 | 생성 Key 저장과 식별자 검색 유지. 응답 미확정 표식을 추가해 검색 반영 지연 중 재생성 차단 |

Jira Cloud에서 이슈 링크를 만드는 API와 관련됨 유형을 읽는 API는 별개다. 구현은 링크 유형 ID를 추측하지 않고 사이트의 대칭적인 `relates to` / `다음과 관련됨` 유형을 조회한다. 유형이 없거나 여러 개면 중단한다.

- [Atlassian: Create issue link](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-links/)
- [Atlassian: Get issue link types](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-link-types/)
- [Atlassian: Enhanced JQL search](https://developer.atlassian.com/cloud/jira/platform/rest/v3/api-group-issue-search/)

## 검증 범위와 한계

2026-10-21, Windows PowerShell / Python 3.12.12 / PyYAML 6.0.3에서 격리된 unittest **33개 통과**로 생성 요청·관련됨 연결·중복 방지·부분 실패 재개·응답 유실·기존 Task 보존을 검증했다. CLI 도움말, 오프라인 YAML 검사 성공, 미수정 자리표시자 거부, Markdown 로컬 링크와 변경 파일 공백 검사도 통과했다. 실제 명령과 사용 방법은 [도구 README](../../../scripts/jira/README.md)를 따른다.

공식 OpenAPI의 `PageOfCreateMetaIssueTypes`가 `total`·`startAt` 기반인 것을 확인해 유형 목록 페이지네이션에 반영했다. JQL 검색은 별도의 `nextPageToken`·`isLast` 응답을 처리한다.

실제 Jira에서 이번 구현 Task 생성, 본인 할당, 진행 중 전환, 지정한 기획·검증 Epic 연결은 브라우저로 확인했다.

최초 `--check`는 환경 변수 미설정으로 중단됐다. 이후 사용자가 로컬 `.env`에 인증정보를 입력한 뒤 2026-10-21 재실행하여 API 인증·프로젝트 생성 메타데이터 조회·연결 유형 조회에 성공했다(종료 코드 0). `S15P31E102`의 Task 유형 ID는 `10002`, 관련됨 유형 ID는 `10003`으로 확인했다. 이 단계는 읽기 전용 검사이며, 생성·연결 권한은 아래 실제 실행으로 검증했다. `.env`가 Git 제외 대상인 것도 확인했다.

같은 날 사용자의 테스트 Task 생성 요청에 따라 `python scripts/jira/create_issues.py --file .jira/tmp/task-generation-smoke.yml --apply`를 실행하고, 생성 후 API 재조회로 다음 결과를 확인했다.

- [S15P31E102-51](https://ssafy.atlassian.net/browse/S15P31E102-51): `[테스트] Jira API Task 생성·Story 관련됨 연결 검증` 제목의 Task 하나를 생성했다. 담당자는 API 실행 계정인 박찬형, 상태는 `해야 할 일`이다.
- 기존 Story [S15P31E102-48 하네스 엔지니어링](https://ssafy.atlassian.net/browse/S15P31E102-48)에 `다음과 관련됨`(`Relates`, 유형 ID `10003`)으로 연결했다. 양쪽 이슈 조회에서 링크 ID `1470605`가 각각 하나이며 `parent`는 비어 있음을 확인했다.
- 생성 요청에는 포인트 필드를 넣지 않았다. 생성 후 `Story Points`(`customfield_10031`)와 `Story point estimate`(`customfield_10016`)가 응답에 포함되고 두 값 모두 `null`임을 확인했다.
- 동일 계획으로 `--apply`를 다시 실행했을 때 신규 Task는 0개였다. 식별자 검색 결과는 기존 Task 하나이고 Story 연결도 같은 링크 하나로 유지됐다.

검증 계획과 API 확인 결과는 Git 제외 경로인 `.jira/tmp/task-generation-smoke.yml`, `.jira/tmp/task-generation-smoke-verification.json`에 보관했다. 실제 생성·연결·재실행 검증은 통과했으며, 테스트 Task를 삭제하거나 완료 상태로 전환하지 않았다.

이슈 생성과 Story 연결은 서로 다른 API 요청이므로 하나의 트랜잭션이 아니다. 생성 성공 후 연결 실패는 생성된 Key로 재개하며 기존 Task를 삭제하거나 롤백하지 않는다. 동일 계획의 동시 실행은 로컬 잠금으로 막지만, 서로 다른 계획의 동시 생성은 업무 소유자 간 조율이 필요하다.
