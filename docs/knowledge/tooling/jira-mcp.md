# Jira MCP 사용 안내

- 작업: `S15P31E102-47`
- Jira 작업 규칙의 기준: [AGENTS.md](../../../AGENTS.md)의 `작업 시작과 Jira 연결`, `Jira·Git·MR`.

## 처음 연결하기

1. 사용하는 AI 도구에서 Jira를 지원하는 Atlassian MCP를 연결하고 본인 Atlassian 계정으로 인증한다. 연결은 팀원별로 필요하다.
2. `https://ssafy.atlassian.net` 사이트와 `S15P31E102` 프로젝트를 사용할 수 있는지 AI에게 조회를 요청한다. 계정에 해당 Jira 작업 권한이 있어야 한다.
3. 연결이 확인되면 저장소를 열어둔 AI에게 업무를 요청한다. 이 저장소의 Jira 작업을 위해 Python·PyYAML 설치, YAML 계획 작성, `.env`의 이메일·API 토큰 설정은 필요하지 않다. 인증 방식은 사용하는 MCP의 연결 절차를 따른다.

## 업무 요청하기

예를 들어 다음과 같이 요청한다.

> Jira MCP로 이 업무에 해당하는 기존 Task가 있는지 확인하고, 없으면 Task를 생성해줘. 연결할 Story는 내가 지정한 실제 Story Key를 사용하고 저장소의 Jira 규칙을 따라줘.

연결할 Story를 알고 있으면 실제 Key와 업무 목적·범위를 함께 전달한다. AI는 기존 Task와 Story를 확인하고, 생성이 필요하면 Task를 만든 뒤 `다음과 관련됨` 연결을 추가한다. 포인트는 입력하지 않는다. 결과로 실제 Key와 연결 확인 결과를 받는다. Task 생성·연결과 작업 시작 시 상태 전환의 규칙은 AGENTS.md를 따른다.

MCP가 연결되지 않았거나 인증·권한 오류가 있으면 연결 상태부터 해결한다. 생성 응답이 유실됐거나 연결만 실패한 경우에는 기존 이슈와 링크를 재조회하고 확인된 Key로 이어서 처리한다. 생성과 연결은 별도 요청이므로 한 번에 성공한다고 가정하지 않는다.

## 실제 검증 결과

2026-10-21, Atlassian MCP를 `ssafy.atlassian.net` 계정에 연결한 환경에서 MCP 호출만으로 검증했다.

- 사이트·프로젝트의 생성 가능한 유형, 관련됨 연결 유형과 기존 Story 조회에 성공했다. MCP 테스트 식별자와 제목 검색 결과가 비어 있음을 확인하고 Task 하나를 생성했다.
- `createJiraIssue`로 [S15P31E102-53](https://ssafy.atlassian.net/browse/S15P31E102-53)을 `작업` 유형으로 생성했다. 담당자는 인증한 박찬형이며 포인트·상위 항목·스프린트는 요청에 넣지 않았다.
- `createJiraIssueLink`로 기존 Story [S15P31E102-48 하네스 엔지니어링](https://ssafy.atlassian.net/browse/S15P31E102-48)에 `Relates` 연결을 추가했다. 양쪽 이슈 재조회에서 같은 링크 ID `1470854`가 각각 하나임을 확인했다.
- `key = S15P31E102-53 AND cf[10031] IS EMPTY AND cf[10016] IS EMPTY` 검색에서 해당 Task가 반환됐다. `Story Points`와 `Story point estimate`가 모두 비어 있음을 확인했다.

이 테스트는 Python 생성 도구나 브라우저를 사용하지 않았다. MCP 생성 요청 자체의 중복 방지·응답 유실 자동 복구와 상태 전환은 검증하지 않았다. 테스트 Task는 `해야 할 일` 상태로 남겨뒀다. 다른 팀원의 계정·권한은 각자의 MCP 연결 후 확인해야 한다.
