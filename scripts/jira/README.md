# Jira Task 생성 자동화

검토한 YAML 계획으로 **Task만 생성**하고, 지정한 기존 Story와 **다음과 관련됨**으로 연결합니다.
기본 실행은 미리보기이며, `--apply`를 명시한 경우에만 Jira를 변경합니다.
EC2·GitLab Runner 없이 로컬 Python에서 실행합니다.

## 프로젝트 규칙

- 먼저 Jira에서 업무 목적·범위·담당자·상태·연결된 Story를 확인하고 대응되는 Task를 재사용합니다. 도구의 동일 제목 검사는 의미가 비슷한 업무까지 찾아주지 않습니다.
- `story`에는 준비된 실제 Story Key를 지정합니다. Story가 없으면 사지한에게 생성을 요청합니다. Story·Epic은 생성하거나 추정하지 않습니다.
- Task의 Story 연결에는 이슈 링크를 사용합니다. Story를 `parent`로 지정하지 않습니다.
- 포인트를 입력하거나 추정하지 않습니다. `story_points`, `points`, `epic`, `parent`, `sprint`, `assignee` 등 지원하지 않는 필드는 오류로 처리합니다.
- 새 Task는 인증한 본인에게 할당합니다. 스프린트는 자동 배정하지 않습니다.
- 기존 Task는 본문·담당자·우선순위·레이블·상태를 유지하고, 필요한 Story 연결만 추가합니다. 완료 Task나 다른 담당자의 Task는 먼저 확인한 뒤 `--allow-existing-task`로 명시해야 합니다.
- 작업 시작 시 `진행 중` 전환·결과 확인은 별도로 수행합니다. 이 도구는 상태 전환, 완료 처리, 댓글 보고를 실행하지 않습니다.

## 환경 설정

저장소 루트에서 Python 3.11 이상을 사용합니다. 아래는 PowerShell 기준입니다.

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r scripts/jira/requirements.txt
Copy-Item .env.example .env
```

`.env`에 본인 Atlassian 이메일과 개인 API Token을 입력합니다. 실제 값은 Git·Jira·MR·로그에 올리지 않습니다. 환경 변수가 이미 있으면 `.env`보다 우선합니다.

| 변수 | 용도 |
| --- | --- |
| `JIRA_BASE_URL` | 경로 없는 Jira HTTPS 사이트 주소. 현재 `https://ssafy.atlassian.net` |
| `JIRA_PROJECT_KEY` | 현재 `S15P31E102`. 생략해도 이 값을 사용 |
| `JIRA_EMAIL` | 인증할 본인 Atlassian 이메일 |
| `JIRA_API_TOKEN` | 해당 계정의 개인 API Token |

이슈 조회·생성, 담당자 지정, 이슈 연결에 필요한 프로젝트 권한이 있어야 합니다. GitLab for Jira Cloud 앱은 이 도구의 전제 조건이 아닙니다.

```powershell
.venv/Scripts/python.exe scripts/jira/create_issues.py --check
```

`--check`는 인증 계정, 생성 가능한 Task 유형, 관련됨 연결 유형을 읽기 전용으로 확인합니다. 실제 Task 생성·할당·연결 권한을 모두 검증하는 명령은 아닙니다. Story별 조회·유형 확인은 계획 미리보기에서 수행합니다.

## YAML 계획

```powershell
New-Item -ItemType Directory -Force .jira/tmp
Copy-Item scripts/jira/task.example.yml .jira/tmp/my-task.yml
```

복사한 파일의 `S15P31E102-<Story번호>`를 확인한 실제 Story Key로 바꾸고 Task 목적을 작성합니다. 예시 파일은 자리표시자를 포함하므로 수정 없이 검사·실행하면 실패합니다.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `id` | O | 계획 안에서 고유한 식별자 |
| `summary` | O | 대상과 할 일이 드러나는 Task 제목. 최대 255자 |
| `story` | O | 같은 프로젝트에 이미 존재하는 Story Key |
| `description` | X | 필요한 목적·범위 설명. 정해진 본문 양식을 강제하지 않음 |
| `done_when` | X | 필요한 경우 확인 가능한 완료 조건 목록 |
| `priority` | X | 신규 Task에 적용할 실제 Jira 우선순위 이름 |
| `issue_type` | X | 생략하거나 `Task`만 입력 |
| `jira_key` | X | 재사용하기로 확인한 기존 Task 또는 생성 결과 Key |
| `automation_id` | 자동 | 미리보기 때 발급·저장하는 중복 생성 방지 식별자 |
| `creation_pending` | 자동 | 생성 요청 결과가 미확정인 경우 재생성을 막는 표시 |

본문이나 완료 조건을 생략할 수 있지만, 착수 전에 결과물·완료 조건과 검증 방법은 파악해야 합니다. 도구는 업무 범위에 대한 사람의 검토를 대신하지 않습니다.

## 검사·미리보기·적용

외부 호출이나 인증정보 없이 YAML 형식만 확인합니다. 파일을 변경하지 않으며 Story 존재·프로젝트·권한은 검증하지 않습니다.

```powershell
.venv/Scripts/python.exe scripts/jira/create_issues.py --file .jira/tmp/my-task.yml --validate-only
```

실제 Jira를 읽어 모든 Story의 프로젝트·유형, 중복 식별자, 기존 Task 후보를 확인합니다. Jira는 변경하지 않지만 재시도용 식별자를 **같은 YAML 파일에 저장**합니다. 이름이 같은 Task가 있으면 중단하므로 목적·범위·담당자·상태를 확인한 뒤 재사용할 `jira_key`를 입력합니다. 별개 업무라면 목적이 구분되는 제목으로 작성합니다.

```powershell
.venv/Scripts/python.exe scripts/jira/create_issues.py --file .jira/tmp/my-task.yml
```

내용과 연결 대상이 맞으면 같은 파일에 적용합니다.

```powershell
.venv/Scripts/python.exe scripts/jira/create_issues.py --file .jira/tmp/my-task.yml --apply
```

생성된 Key는 즉시 파일에 저장합니다. 각 Task의 Story 연결을 다시 조회해 확인한 뒤 완료로 보고합니다. Task가 이미 연결되어 있으면 중복 링크를 추가하지 않습니다. 실제 업무 시작 시에는 Jira에서 `진행 중`으로 전환하고 결과를 확인합니다.

확인한 다른 담당자 또는 완료 Task에 연결할 때만 `--allow-existing-task`를 함께 사용합니다. 이 옵션도 기존 상태나 담당자를 변경하지 않습니다.

## 실패·재시도

- **Task 생성 후 연결 실패:** Key가 저장된 같은 파일로 미리보기·적용을 다시 수행합니다. 새 Task를 만들지 않고 Story 연결부터 이어갑니다.
- **생성 응답 유실:** `automation_id` 레이블로 기존 Task를 검색해 Key를 복구합니다. Jira 검색 반영이 늦어 찾지 못하면 `creation_pending`을 유지하고 중단합니다. POST 요청을 자동 재시도하지 않습니다.
- **생성되지 않았음이 확실한 경우:** Jira에서 식별자 레이블·동일 제목 후보를 확인하고, 동시 실행이 없는지 확인한 뒤에만 `creation_pending`을 제거해 재시도합니다. HTTP 입력 오류도 원인을 해결하기 전에 파일을 지우거나 새 계획으로 바꾸지 않습니다.
- **잠금 파일:** 같은 파일의 동시 실행을 차단합니다. 프로세스 강제 종료로 `<계획파일>.lock`이 남으면 실행 중인 프로세스가 없음을 확인한 후 그 잠금 파일만 제거합니다.
- 서로 다른 계획 파일·사용자가 동시에 동일한 업무를 생성하는 것까지 원자적으로 차단하지는 못합니다. 같은 작업은 하나의 계획과 주 담당자로 관리합니다.

전체 Task의 연결을 확인한 뒤 임시 계획도 삭제하려면 `--apply --delete-on-success`를 사용합니다. 삭제는 `.jira/tmp/` 안에서만 허용합니다. 실패한 경우에는 파일을 보존합니다. `automation_id`를 바꾸거나 기존 Task의 식별자 레이블을 임의로 지우지 않습니다.

## 검증

```powershell
python -m unittest discover -s tests/jira -p 'test_*.py' -v
python scripts/jira/create_issues.py --help
```

테스트는 작은 가짜 Jira 응답을 사용하며 실제 Jira에 업무를 생성하지 않습니다. Task만 생성하는 요청, 포인트·상위 항목 미전송, Story 유형 사전 검사, 부분 실패 재개, 응답 유실 복구, 중복 방지, 기존 업무 보존, 페이지네이션을 검증합니다.

설계와 실제 검증 결과는 [검증 기록](../../docs/knowledge/tooling/jira-task-automation.md)에 정리했습니다.
