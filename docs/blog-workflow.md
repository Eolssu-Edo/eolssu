# 블로그 사용 방식: 로컬 TIL에서 공개까지

관련 작업: [S15P31E102-227 — 로컬 TIL 기반 블로그 초안 작성과 coco-blog 연동](https://ssafy.atlassian.net/browse/S15P31E102-227). Story: S15P31E102-40 배포 환경·CI.

TIL 원본은 개발자 로컬에만 보관합니다. 개발자가 coco-blog를 실행하면 로컬 TIL 목록을 보여주고, 하나 또는 여러 글감을 선택한 뒤 대화로 공개용 원고를 만듭니다. TIL 생성 직후에는 블로그 작성 여부를 묻지 않습니다. 원본을 develop에 커밋하거나 공개 후보 머리말을 추가할 필요가 없습니다.

## 누가 무엇을 하나요?

| 단계 | 담당 | 작업 |
| --- | --- | --- |
| 1 | 개발자 | 원하는 시점에 coco-blog 실행 |
| 2 | coco-blog와 개발자 | 최근 로컬 TIL 목록에서 하나 또는 여러 글감 선택 |
| 3 | coco-blog와 개발자 | 분야·독자·핵심·선택 이유·근거·공개 범위를 필요한 만큼 질문하며 결정 |
| 4 | coco-blog | 최신 origin/blog에서 별도 worktree·작업 브랜치를 직접 준비하고, 대화로 정리한 공개 Markdown을 draft: true로 저장 |
| 5 | 개발자 | 초안의 사실관계와 공개 범위 검토, Git 반영 여부 결정 |
| 6 | coco-blog | 개발자가 Git 반영을 요청하면 블로그 작업 브랜치에 선택한 공개 글 .md 파일만 commit·push |
| 7 | 작성자·리뷰어 | 저장소 양식으로 MR 생성·검토·blog 병합. AI는 요청받지 않으면 MR 본문만 전달 |
| 8 | 작성자 | 공개할 때 draft: false 변경을 blog에 반영 |
| 9 | Jenkins → Hugo → GitHub Pages | blog 변경 감지 후 초안을 제외해 빌드하고 공개 결과물 게시 |

## 개발 중인 브랜치에서 실행해도 되나요?

개발자는 develop에서 만든 개발 브랜치에서 그대로 coco-blog를 실행합니다. 개발자가 blog로 이동하거나 블로그용 브랜치를 직접 만들 필요가 없습니다. 스킬이 원래 작업 공간·브랜치·HEAD·스테이징 상태·수정 파일·선택한 TIL 경로를 확인한 뒤, 최신 origin/blog를 기준으로 별도 worktree와 저장소 규칙에 맞는 글 작업 브랜치를 준비합니다.

개발 브랜치의 코드와 커밋을 blog로 합치지 않습니다. 원래 브랜치를 전환하거나 작업 파일을 stash·reset하지 않고, 로컬 TIL은 원래 위치에서 읽습니다. 대화는 같은 채팅에서 이어가며, 이관하는 것은 대화로 합의한 공개용 원고입니다. 채팅 원문이나 TIL 원본을 복사해 Git에 올리는 것이 아닙니다.

초안은 블로그 worktree의 content/posts/ 아래 .md로 저장합니다. Git 반영을 요청하면 해당 글 파일만 경로를 지정해 스테이징하고, 공개 브랜치 기준 차이와 게시할 커밋을 검사한 후 글 작업 브랜치에 commit·push합니다. 앱 코드·설정·대화 로그·원본 TIL·추적 JSON·캐시는 포함하지 않습니다. 이미지는 기존 승인된 공개 URL을 사용하며 별도 이미지 업로드는 별도 요청 범위입니다. MR은 작성자가 생성하고 대상은 blog입니다.

## 글감은 어떻게 선택하나요?

coco-blog를 실행하면 현재 프로젝트의 로컬 TIL을 최근 수정 순으로 보여줍니다. Git에서 무시되는 파일도 포함하고 제목·날짜·상대 경로·짧은 요약 또는 가능한 글 방향으로 선택을 돕습니다. 목록은 인덱스와 머리말·도입부로 준비하고, 전문은 개발자가 선택한 뒤 읽습니다. 목록이 길면 더 보기나 주제·분야·날짜 필터를 제공합니다. 수정일과 작성일은 구분합니다.

“1번”, “2번과 4번을 묶어서”, “배포 관련 글만 보여줘”처럼 답할 수 있습니다. 연관된 여러 TIL은 하나의 글로 묶을 수 있고, 분야는 담당 직무와 달라도 됩니다. 이미 TIL 경로를 지정했다면 선택을 반복하지 않습니다. TIL이 없으면 해당 사실을 안내하고 다른 로컬 자료 경로를 확인합니다.

## 대화는 어떻게 진행하나요?

AI는 TIL에서 파악한 내용을 먼저 제안하고, 부족한 사실만 한 번에 한 질문 또는 관련된 소수 질문으로 확인합니다.

> “이번 작업을 INFRA 분야의 배포 실수 예방 글로 정리할까요?”
> “기존에는 어떤 실수가 있었고, 변경 후에는 무엇으로 방지를 확인했나요?”

분야는 DATA(data), AI(ai), AI-NATIVE(ai-native), INFRA(infra), FE(frontend), BE(backend), 회고 중에서 선택하고 태그는 여러 개 사용할 수 있습니다. 분야와 글의 방향은 별개입니다. 성과·설계·자동화·예방도 글감이며 수치가 없으면 테스트·화면·구조·피드백으로 설명합니다. 수치나 선택 이유를 지어내지 않습니다.

원본 TIL·비공개 대화·로컬 절대 경로는 Git에 올리지 않습니다. 공개 원고는 원본과 별도 파일로 만들고 개발자가 확인한 본문과 필요한 이미지만 반영합니다. 새 TIL이 생겼다고 기존 게시글을 자동 갱신하지 않습니다.

## 로컬 공개 원고를 준비하는 명령

AI가 대화로 정리한 공개용 Markdown에는 title, slug, author, description, categories와 선택적인 tags, thumbnail을 작성합니다. 날짜는 기본 한국 날짜이며 draft는 항상 true로 생성됩니다. 원본 TIL을 이 명령에 바로 넣지 않습니다.

```powershell
python -m pip install -r requirements-blog.txt
python scripts/til_to_blog.py prepare-local C:/path/to/reviewed-public-post.md --reviewed --dry-run
python scripts/til_to_blog.py prepare-local C:/path/to/reviewed-public-post.md --reviewed
python scripts/til_to_blog.py check content/posts/YYYY-MM-DD-post-slug.md
```

명령은 coco-blog가 별도로 준비한 블로그 worktree에서 실행합니다. 스킬은 목적지를 해당 worktree로 지정하며, 개발자가 브랜치를 준비할 필요는 없습니다. 목적지 브랜치는 origin/blog를 포함해야 합니다. develop 조회·fetch는 필요하지 않습니다. 같은 slug가 있으면 중단하며 기존 글을 덮어쓰지 않습니다. 원고 내용 검사는 보조 수단이고 --reviewed는 사람 검토를 대신하지 않습니다. 이 명령은 Git 쓰기·원격 업로드·MR 생성·게시를 수행하지 않습니다.

## Draft와 실제 공개

공개 제어는 Hugo 머리말의 draft: true / false입니다. 초안을 blog에 병합해도 기본 빌드에서는 글이 제외됩니다. 단, Git 저장소 접근자는 초안 소스를 볼 수 있고 공개 소스 저장소에 올리면 초안도 보이므로 업로드 전에 저장소 공개 범위를 확인합니다.

작성자가 공개를 결정하면 draft: false로 수정하고 검사·리뷰 후 blog에 반영합니다. 로컬에서만 수정하거나 GitLab Draft MR 표시만 해제하는 것으로는 공개되지 않습니다. Jenkins는 초안 변경에도 실행될 수 있으며 draft:false가 blog에 반영된 다음 빌드에서 글을 포함합니다. 기존 배포 명령에는 --buildDrafts를 넣지 않습니다.

## 자동화 범위

TIL을 생성하는 도구와 블로그 작성 흐름은 분리합니다. TIL 저장 직후의 자동 제안은 없으며 개발자가 coco-blog를 실행할 때 글감을 고릅니다. 로컬 TIL 경로를 직접 전달해도 됩니다. 파일 감시나 생성 도구의 완료 훅은 필요하지 않습니다. 실제 사용하려면 수정된 COCO 패키지를 사용하는 AI 환경에 설치·반영해야 하며, 소스 수정만으로 모든 팀원 환경이 갱신되지는 않습니다.

기존 develop 커밋 기반 list/import/diff는 호환 기능으로 유지합니다. 신규 로컬 흐름에는 필요하지 않습니다. CLI 세부 사항은 [가져오기 안내](til-import.md), 편집 기준은 [작성 기준](blog-editorial-guidelines.md)을 참고하세요.
