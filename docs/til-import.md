# TIL을 골라 블로그 초안으로 가져오기

## 기본 흐름: 로컬 TIL → 대화 → 공개 원고

신규 작성은 로컬 TIL에서 시작합니다. 원본을 커밋하거나 candidate 표시하지 않고, coco-blog와 공개 내용을 검토한 별도 원고를 `prepare-local <공개원고.md> --reviewed`로 준비합니다. `--dry-run`으로 경로와 검사를 먼저 확인할 수 있습니다. `--repo`는 명령 앞에 지정하며, 목적지는 coco-blog가 최신 origin/blog에서 직접 준비한 별도 worktree·작업 브랜치입니다. 개발자는 개발 브랜치에 그대로 머무릅니다. Git 반영을 요청하면 선택한 공개 글 Markdown만 올립니다. 원고에는 title, slug, author, description, categories가 필요하고 tags, thumbnail, date를 선택할 수 있습니다. draft는 항상 true로 생성하며 같은 slug는 덮어쓰지 않습니다. 로컬 경로나 원본 추적 기록을 게시글에 넣지 않습니다.

자세한 대화·검토·공개 절차는 [블로그 사용 방식](blog-workflow.md)을 따릅니다. 아래 내용은 이미 Git에 커밋한 TIL을 위한 기존 호환 모드이며 로컬 흐름의 필수 단계가 아닙니다.

## 기존 Git 스냅샷 가져오기

`develop`의 TIL 원본은 유지하고, 작성자가 공개 후보로 표시한 글만 `blog` 작업 브랜치의 Hugo 초안으로 복사합니다. 두 브랜치를 합치지 않으며, 앱 코드나 develop의 다른 문서를 가져오지 않습니다.

이 도구는 목록 조회·초안 생성·변경 확인만 합니다. Git fetch·브랜치 생성·커밋·push·MR 생성·병합·게시를 실행하지 않습니다. Jenkins의 기존 `blog` 배포 파이프라인도 바꾸지 않습니다.

전체 역할 분담과 Codex 요청 예시는 [블로그 사용 방식](blog-workflow.md)을 먼저 확인하세요. 이 문서는 CLI 설치·실행·상태·갱신 방법을 설명합니다.

## 준비

- Python 3.10 이상과 Git이 필요합니다.
- 블로그 작업 환경에서 `python -m pip install -r requirements-blog.txt`로 PyYAML을 설치합니다. Jenkins의 Hugo 빌드에는 설치할 필요가 없습니다.
- TIL 원본을 `develop`의 `TIL/` 아래에 커밋합니다. 작업 중이거나 커밋하지 않은 파일은 가져오지 않습니다.
- 실행 전 `git fetch origin develop blog`로 원격 추적 브랜치를 갱신하고, 최신 `origin/blog`를 포함하는 별도 작업 브랜치를 준비합니다. `blog`, `develop`, `main`에서 직접 가져오기는 거부합니다.

## 1. 작성자가 공개 후보 표시

글감 선정과 사람의 게시 검토에는 [Team Edo 블로그 작성 기준](blog-editorial-guidelines.md)을 적용합니다. 수치를 필수 조건으로 삼지 않고, 잘한 설계·완성한 기능·문제 예방·자동화도 후보로 모읍니다.

`develop`의 `TIL/YYYY-MM-DD-주제.md`에 YAML 머리말을 작성합니다. `blog.candidate`가 불리언 `true`인 글만 선택 가능합니다. `"true"` 문자열이나 표시가 없는 글은 제외합니다.

```markdown
---
title: "개인 학습 메모 제목"
date: 2026-10-28
blog:
  candidate: true
  title: "녹음 형식을 통일하며 배운 점"
  slug: "audio-format"
  author: "Team Edo"
  description: "녹음 규격을 맞추면서 확인한 문제와 해결 과정을 정리했습니다."
  categories: ["ai"]
  tags: ["녹음", "오디오"]
---

개인 메모와 내부 자료는 공개 구간 밖에 둘 수 있습니다.

<!-- blog:start -->
## 어떤 문제가 있었나요?

독자가 이해할 수 있도록 공개할 내용만 정리합니다.

## 어떻게 해결했나요?

검증 과정과 배운 점을 설명합니다.
<!-- blog:end -->

이 아래의 개인 메모도 복사되지 않습니다.
```

공개 구간 표시가 없으면 **머리말을 제외한 본문 전체**를 가져옵니다. 표시를 쓴다면 순서가 올바른 한 쌍만 허용합니다. 후보 표시는 자동 게시 승인이 아닙니다.

- `blog` 안에 쓴 값이 원래 머리말보다 우선합니다. `title`, `slug`, `author`, `description`, `categories`는 필수이고 `tags`는 선택입니다.
- `categories`는 `backend`, `frontend`, `ai`, `ai-native`, `data`, `infra`, `security`, `회고` 중에서 선택합니다.
- TIL 작성일은 게시일로 복사하지 않습니다. 게시일은 가져온 한국 날짜가 기본이며 `blog.date` 또는 `import --date YYYY-MM-DD`로 지정할 수 있습니다. 갱신할 때는 처음 게시일을 유지합니다.
- 생성한 게시일은 `YYYY-MM-DDT00:00:00+09:00`으로 기록해 Jenkins가 UTC로 실행돼도 한국 날짜 기준 당일 글을 미래 글로 건너뛰지 않도록 합니다. 미래 게시일은 거부합니다.
- `thumbnail`과 본문 이미지는 `https://team-edo.github.io/blog-images/`의 영문 소문자 파일명 URL만 허용합니다. 이미지 업로드는 기존 README 절차를 따릅니다.

## 2. 후보를 보고 한 편 선택

블로그 작업 트리의 루트에서 실행합니다.

```sh
git fetch origin develop blog
python scripts/til_to_blog.py list
python scripts/til_to_blog.py import TIL/2026-10-28-audio.md --dry-run
python scripts/til_to_blog.py import TIL/2026-10-28-audio.md
```

기본 원본은 `origin/develop`입니다. 특정 시점의 파일을 가져오려면 공통 옵션을 명령 앞에 붙입니다.

```sh
python scripts/til_to_blog.py --ref <커밋-SHA> list
python scripts/til_to_blog.py --ref <커밋-SHA> import TIL/2026-10-28-audio.md
```

출력은 `content/posts/YYYY-MM-DD-audio-format.md`이며 **항상 `draft: true`**입니다. 원본 머리말의 공개 필드만 복사하므로 내부 원본 경로나 커밋 SHA가 공개 글에 들어가지 않습니다. 자동 요약이나 문장 재작성은 하지 않으며 글 다듬기는 작성자가 합니다.

`--dry-run`은 결과 경로와 상태를 보여주고 게시글·가져오기 기록을 생성하지 않습니다. 동시에 두 가져오기를 실행하지 않도록 임시 잠금을 사용합니다.

## 3. 수정·중복 처리

저장소 루트 `.til-imports.json`에 원본 경로·읽은 커밋·blob·출력 경로·생성 내용 해시를 기록합니다. **이 파일을 글과 함께 커밋**해야 다음 작업자도 중복을 방지할 수 있습니다. 이 파일은 Hugo 콘텐츠·static·assets 밖에 있으므로 공개 사이트에 포함되지 않습니다.

| 목록의 상태 | 의미 |
| --- | --- |
| `new` | 아직 가져오지 않은 후보 |
| `unchanged` | 원본과 생성한 초안 모두 그대로이며 재실행해도 중복 생성하지 않음 |
| `updated` | 원본이 바뀌었고 초안은 그대로임 |
| `local-edits` | 초안이 편집·게시됐거나 삭제됨. 자동으로 덮어쓰지 않음 |
| `invalid` | 원본 머리말·공개 구간 등을 고쳐야 함 |

원본만 수정된 경우 변경을 검토하고 명시적으로 갱신합니다.

```sh
python scripts/til_to_blog.py import TIL/2026-10-28-audio.md --update --dry-run
python scripts/til_to_blog.py diff TIL/2026-10-28-audio.md
python scripts/til_to_blog.py import TIL/2026-10-28-audio.md --update
```

처음 만든 글이 그대로일 때만 `--update`가 작동합니다. 이미 다듬었거나 `draft: false`로 바꾼 글에는 작동하지 않으므로 원본과 비교해 직접 반영합니다. 원본 날짜나 slug 변경으로 기존 글 URL을 자동 변경하지 않습니다. 같은 slug의 기존 수동 작성 글도 덮어쓰지 않습니다.

`diff`는 편집된 글에도 사용할 수 있으며 현재 글과 원본에서 새로 만든 draft 초안의 차이만 출력합니다. 파일이나 가져오기 기록은 변경하지 않습니다.

## 4. 공개 검토 후 기존 방식으로 게시

```sh
python scripts/til_to_blog.py check content/posts/YYYY-MM-DD-audio-format.md
hugo server -D
```

- 검사는 내부 링크·IP·자격증명 형태·로컬 링크·이미지 경로·필수 머리말 등을 확인하고 문제 위치만 표시합니다. 감지한 비밀값을 출력하지 않습니다.
- 이는 패턴 검사이며 모든 개인정보·내부 정보·저작권 문제를 판별하지는 않습니다. 이미지 URL의 실제 응답이나 이미지 속 정보도 자동 확인하지 않습니다. 작성자가 본문·이미지·출처를 확인합니다.
- 확인한 뒤 직접 `draft: false`로 바꾸고 `check`와 Hugo 빌드를 다시 실행합니다. 준비된 글과 `.til-imports.json`을 작업 브랜치에서 커밋합니다.
- 정해진 GitLab 양식에 맞춰 MR 본문을 준비하고 **사용자가 MR을 생성·검토·병합**합니다. 도구는 MR을 만들지 않습니다.
- `blog` 병합 후에는 기존 Jenkins가 빌드하고 GitHub Pages에 게시합니다.

## 검증

```sh
python -m unittest discover -s tests -v
```

테스트는 서로 이력이 없는 develop/blog 임시 저장소에서 실행합니다. 테스트 글과 내부 정보 예시는 실제 원격이나 공개 사이트로 보내지 않습니다.
