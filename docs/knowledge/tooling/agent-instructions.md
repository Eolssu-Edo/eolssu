# Codex·Claude 공통 지침과 COCO 사용

공식 문서 확인일: 2026-10-20. 이 문서는 지침을 구성한 근거와 사용 방법을 설명합니다. 실제 작업 규칙은 루트와 각 폴더의 `AGENTS.md`에 있습니다.

## 확인한 도구 동작

Codex는 시작할 때 저장소 루트부터 작업 디렉터리까지의 지침을 읽습니다. 루트에서 시작한 세션이 모든 하위 지침을 처음부터 읽는다고 가정할 수는 없습니다. 그래서 루트 지침에 작업 영역의 파일을 읽도록 연결했습니다. 파일명은 자동 탐색에 맞춰 `AGENTS.md`로 통일했습니다. [OpenAI 공식 문서](https://learn.chatgpt.com/docs/agent-configuration/agents-md)

현재 Claude Code는 조건에 따라 `AGENTS.md`를 직접 읽지만, 버전·개인 지침·설정에 따라 달라집니다. 공식 문서는 `CLAUDE.md`에서 같은 폴더의 `@AGENTS.md`를 불러오는 방식도 지원하며, Windows에서는 심볼릭 링크보다 이 방식을 권장합니다. 이 저장소는 루트와 각 작업 폴더에 한 줄짜리 `CLAUDE.md`를 두었습니다. [Claude Code 공식 문서](https://code.claude.com/docs/en/memory#share-one-file-with-other-coding-tools)

Claude의 하위 지침은 해당 폴더 파일을 다룰 때 읽힙니다. 상하위 지침은 함께 적용되므로 내용이 충돌하지 않아야 합니다. 코드 없는 조사에서도 해당 분야의 지침을 읽도록 루트에서 안내합니다. [Claude Code 지침 로딩](https://code.claude.com/docs/en/memory#how-claudemd-files-load)

## 이 저장소에 적용한 구성

| 파일 | 역할 |
| --- | --- |
| 루트 `AGENTS.md` | 공통 범위·권한·COCO·Jira/Git/커밋/MR·검증·기록 규칙 |
| 각 파트의 `AGENTS.md` | 해당 파트의 경계·계약·검증·조사 지침 |
| `docs/AGENTS.md` | 지식·실험·결정 기록의 정확성과 관리 |
| 각 `CLAUDE.md` | 같은 폴더의 `AGENTS.md`를 불러오는 연결 |
| README·일반 문서 | 제품 설명·실행 방법·설계 이유·도메인 지식 |

공통 규칙을 파트마다 복사하지 않고, 기술이나 업무 범위에 따라 행동이 달라지는 지침만 분리했습니다. 하위 폴더를 만들 때마다 지침 파일을 추가할 필요는 없습니다. 기획상 미정인 프레임워크나 아직 없는 실행 명령도 넣지 않았습니다.

작업마다 모든 문서를 읽거나 정해진 개발 단계를 반복하게 하는 지침은 피했습니다. 필요한 상황에 해당 문서를 읽게 하고, 도구가 기본적으로 하는 일을 장황하게 재설명하지 않는 방향입니다. [OpenAI 작성 가이드](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra), [Claude Code 작성 가이드](https://code.claude.com/docs/en/best-practices#write-an-effective-claudemd)

## COCO 명령

팀 기준 버전은 npm의 `@thisis-joe/coco-skills@0.2.0`입니다. 기본은 필요한 스킬을 사용자가 선택하는 방식이며, 특정 순서로 전부 실행하지 않습니다.

| 도구 | 명령 형태 | 예시 |
| --- | --- | --- |
| Codex | `$coco-스킬명` | `$coco-build` |
| Claude Code | `/coco-스킬명` | `/coco-build` |

명령은 각 도구에 해당 스킬이 설치된 경우에 사용합니다. 위 차이는 확인한 npm 0.2.0 배포본 README를 기준으로 합니다. [COCO 0.2.0 배포본](https://www.npmjs.com/package/@thisis-joe/coco-skills/v/0.2.0)

자연어에 따른 스킬 자동 선택은 개인이 명시적으로 허용할 수 있습니다. 일반적인 개발 요청은 스킬 명령 없이도 수행합니다. 어떤 스킬을 선택해도 E102의 Jira 제목 중심 규칙과 실행 권한을 따릅니다.

## 적용 확인과 유지

- 저장소 루트에서 도구를 시작하고, 지침을 수정한 뒤에는 새 세션에서 확인합니다.
- Codex에는 읽은 지침 파일을 나열하도록 요청합니다. 루트에서 특정 파트를 작업할 때는 해당 파트의 지침까지 읽었는지 확인합니다.
- Claude Code에서는 `/context`의 Memory files에서 지침이 로드됐는지 확인합니다.
- 규칙 변경은 해당 `AGENTS.md`에 한 번만 반영합니다. `CLAUDE.md`에는 설명이나 별도 규칙을 늘리지 않습니다.
- 구현이 생기면 해당 파트 README에 실제 명령을 기록하고, 기존 지침과 어긋나는 부분만 조정합니다.

파일의 참조 구조와 문서 연결은 검사했습니다. 팀원별 Codex·Claude 버전과 개인 설정을 포함한 실제 세션의 자동 로딩은 각 환경에서 확인해야 합니다.
