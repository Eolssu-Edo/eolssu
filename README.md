# 얼쑤 · Team Edo 프로젝트 사이트

Hugo로 만든 프로젝트 소개·기술 블로그·팀 소개 사이트예요. 주소는 **https://team-edo.github.io** 예요.

이 `blog` 브랜치는 **블로그 전용 브랜치**예요. 서비스 코드(develop·main)와 기록을 공유하지 않아서, develop에는 블로그 파일이 없고 블로그 글을 머지해도 앱 빌드·배포가 일어나지 않아요.

```
글 브랜치 → MR (대상: blog) → blog 머지 → Jenkins가 1분마다 blog 브랜치 polling
  → Hugo 빌드 → 결과물(HTML)만 GitHub team-edo/team-edo.github.io에 push → https://team-edo.github.io
```

- 원본(글·설정)은 이 GitLab `blog` 브랜치에만 있고, GitHub에는 완성된 HTML만 올라가요.
- 글 이미지는 GitHub `team-edo/blog-images`에 따로 둬요 (아래 [이미지](#이미지)).
- Jenkins는 서버 안(loopback)에서만 접속을 받아서 GitLab webhook 대신 SCM polling으로 변경을 감지해요.

## 팀원이 블로그를 사용하는 방식

**coco-blog 실행 → 로컬 TIL 목록에서 하나 또는 여러 글감 선택 → 대화로 정리 → 공개용 draft 작성 → 요청한 Git 반영 → 작성자의 draft 해제 → blog 병합 후 자동 게시** 흐름입니다. 원본 TIL과 대화는 Git에 올리지 않습니다. 요청은 저장소를 연결한 Codex 채팅에 직접 전달하며, GitLab 댓글·메신저를 감지하는 자동 작성 봇은 없습니다.

누가 어디에서 무엇을 하는지, TIL 머리말과 요청 문구, 검토·commit·push·MR·게시 확인 절차는 [블로그 사용 방식](docs/blog-workflow.md)을 따라 주세요. 개발 브랜치에서 실행하면 스킬이 `origin/blog` 기반의 별도 worktree·글 작업 브랜치를 준비합니다. Git 반영 시 선택한 공개 Markdown만 올리고, 로컬 TIL과 앱 변경은 포함하지 않습니다. 공개 글 MR의 대상은 `blog`입니다. GitLab MR의 Draft 표시와 Hugo의 `draft`는 별개입니다.

공개 글에서 작성자가 수정하거나 삭제할 때는 [글 편집과 일괄 반영 안내](editor/README.md)를 따릅니다. [동작·검증 계획](docs/blog-batch-editor.md)에는 임시 저장과 배포 조건을 정리했습니다.

웹에서 글을 수정할 때는 원문과 미리보기를 함께 보면서 Markdown 서식과 글자 색을 적용할 수 있습니다. [편집 기능과 검증 기준](docs/blog-editor-preview.md)을 참고하세요.

## 글 쓰기

1. `blog` 브랜치에서 글 브랜치를 만들어요. **develop에서 만들지 않아요.**
   ```bash
   git fetch origin
   git switch -c "docs(blog)/S15P31E102-<번호>-<글 제목>" origin/blog
   ```
   브랜치 이름은 [팀 규칙](https://lab.ssafy.com/s15-final/S15P31E102/-/blob/develop/AGENTS.md)의 `타입(파트)/전체JiraKey-Task제목`을 따르고 파트 자리에 `blog`를 써요.
2. `content/posts/`에 `YYYY-MM-DD-주제.md` 파일을 추가해요 (아래 머리말 복사)
3. 이미지는 GitHub `team-edo/blog-images`에 올리고 주소로 넣어요
4. MR을 **대상 브랜치 `blog`**로 올리고, 리뷰 후 머지하면 1분 안에 자동 배포돼요

```markdown
---
title: "글 제목"
date: 2026-10-26
slug: "english-slug"      # 주소에 쓸 영어 이름 → /posts/2026/10/english-slug/
author: "이름"
categories: ["ai"]        # backend, frontend, ai, ai-native, data, infra, security, 회고
tags: ["단소", "음높이"]
description: "문제를 확인한 과정과 해결 방법을 소개합니다."
draft: false              # true면 배포되지 않아요
---

본문은 일반 마크다운으로 써요. 코드 블록·표·링크 모두 돼요.

![그림 설명](https://team-edo.github.io/blog-images/2026-10-pitch-result.webp)
```

- `date`는 **오늘이나 지난 날짜**로 써요. 미래 날짜 글은 Hugo가 건너뛰어서, 머지해도 그 날짜가 지난 뒤 다음 배포 때까지 안 보여요.

## 블로그 글감과 게시 기준

후보는 넓게 모으고, 게시 전에는 직접 한 일·선택한 이유·결과의 근거를 확인합니다. 성과·설계·문제 예방·자동화도 글감이며, 숫자는 여러 근거 중 하나입니다. 구체적인 기준은 [Team Edo 블로그 작성 기준](docs/blog-editorial-guidelines.md)을 따릅니다.

## 기존 develop TIL 가져오기 (호환 기능)

`blog.candidate: true`로 표시한 TIL만 목록에서 선택해 `draft: true` 초안으로 가져올 수 있습니다. 원본은 develop에 유지하며 중복 생성과 편집한 글 덮어쓰기를 막습니다. 도구는 커밋·push·MR 생성·게시를 하지 않습니다.

설치·TIL 표시 예시·가져오기·갱신·검토 절차는 [TIL 가져오기 안내](docs/til-import.md)를 따릅니다.

## 이미지

한 번 커밋한 이미지는 지워도 Git 기록에 남아 저장소 clone이 계속 무거워져요. 그래서 이미지는 **GitHub 이미지 전용 저장소**에 두고, 글에는 주소만 적어요.

| 무엇 | 어디 |
| --- | --- |
| 글(.md) | 이 `blog` 브랜치 `content/posts/` (MR로) |
| 이미지 | GitHub [`team-edo/blog-images`](https://github.com/team-edo/blog-images) (웹에서 업로드) |
| 글에 쓰는 주소 | `https://team-edo.github.io/blog-images/<파일명>` |

**올리는 법**
1. GitHub `team-edo/blog-images` → **Add file** → **Upload files**
2. 파일을 끌어다 놓고, 커밋 메시지에 어떤 글의 이미지인지 적고 **Commit changes** (main에 바로)
3. 1~2분 뒤 `https://team-edo.github.io/blog-images/<파일명>`이 열리면 글에 그 주소를 써요

**규칙**
- 파일 이름은 `YYYY-MM-설명.webp`처럼 **날짜를 앞에** 붙이고 영어 소문자·숫자·하이픈만 써요 (예: `2026-10-pitch-result.webp`). 한글·공백은 주소가 깨져요
- 형식은 WebP 또는 압축한 PNG·JPG, **한 장 300KB 이하**. 동영상·큰 GIF는 올리지 않고 외부 링크로 걸어요
- 공개 저장소예요. 서버 화면·설정·토큰·내부 시스템이 보이는 캡처는 가리고 올려요
- 간단한 구조도는 이미지 대신 글 안의 표·코드 블록으로 대신할 수 있는지 먼저 생각해요
- 이 브랜치의 `static/`에는 글 이미지를 넣지 않아요 (디자인에 꼭 필요한 작은 파일만 예외)

### 글 썸네일

캐릭터 선택과 시안 대화는 [썸네일 캐릭터 가이드](docs/blog-thumbnail-characters.md)를 따릅니다. coco-blog로 글을 작성할 때 이 가이드를 먼저 확인합니다.

머리말 `thumbnail`에 이미지 주소를 넣으면 목록·추천 영역에 그 이미지가 보여요. 비우면 첫 분야에 맞춘 기본 그림이 나와요. coco-blog로 글을 쓰면 원고 방향이 정해진 뒤 썸네일 후보를 그려 고르게 해 줘요.

- **크기:** 1200×630, WebP, 300KB 이하. 파일 이름은 위 규칙대로 `YYYY-MM-slug.webp`
- **잘림:** 추천 영역(약 2:1), 목록(약 4:3), 모바일 목록(세로형 80×94)에서 가운데 기준으로 잘려요. 핵심 그림은 **가운데 520px 안**에 두고, 모바일 목록 크기에서도 읽히게 크게 그려요
- **색:** 배경 `#f3f6e9`, 포인트 연두 `#ADBE31`, 글자 먹색 `#27221E`. 얼쑤 모티프(정간보 칸, 율명, 백호 캐릭터)를 활용해요
- **확인:** 올린 뒤 공개 주소가 열리는지 보고, 미리보기에서 추천 영역·목록·모바일을 확인한 다음 머리말에 넣어요

이미지 업로드는 `team-edo` Organization 멤버만 할 수 있어요. 처음 쓰는 팀원은 Organization 관리자에게 초대를 요청해요.

## 공개 블로그 규칙

- 서버 주소, IP, 도메인, 포트, 키, 토큰, 비밀번호를 쓰지 않기
- `application-*.yml`, `.env` 같은 설정 파일 내용을 붙여넣지 않기
- GitLab, Jira, 노션 등 내부 링크를 쓰지 않기
- 팀원 실명·연락처 등 개인정보는 본인 동의 후에만
- 다른 사람의 글·이미지·악보·음원은 출처와 이용 조건 확인 후 사용 (AI-Hub 데이터는 재배포 금지)

## 내 컴퓨터에서 미리 보기

[Hugo](https://gohugo.io/installation/) 설치 후 이 브랜치 폴더에서:

```bash
hugo server -D
```

`http://localhost:1313`에서 확인해요. `-D`는 `draft: true` 글도 보여줘요.

## 배포 설정 (최초 1회, Infra 담당)

### GitHub
1. Organization `team-edo`, Public 저장소 `team-edo.github.io` (Pages: `main` / `(root)`)
2. Fine-grained 토큰: 대상 저장소는 `team-edo.github.io`만, 권한은 **Contents: Read and write**만
3. 이미지 저장소 `blog-images`를 **Public**으로 생성 (README만 추가), Settings → Pages → Branch `main` / `(root)`. 배포 토큰에는 이 저장소 권한을 주지 않아요

### Jenkins
접속은 서비스 저장소 운영 문서(develop의 `infra/runtime/README.md` Jenkins 절, SSH 터널)를 따라요.

1. 플러그인: Git, Credentials Binding, Pipeline (GitLab·Docker Pipeline 플러그인은 필요 없어요)
2. Credentials: **Secret text** `github-blog-token`. GitLab 읽기는 기존 `eolssu-gitlab`
3. Pipeline 잡 `eolssu-blog`: **Pipeline script from SCM**, 저장소 `https://lab.ssafy.com/s15-final/S15P31E102.git`, Credentials `eolssu-gitlab`, 브랜치 `*/blog`, Script Path `Jenkinsfile`
4. 잡을 **한 번 직접 실행**하면 polling 트리거가 등록돼요. 다음부터는 `blog` 브랜치 변경을 1분 안에 감지해요

빌드는 [블로그 전용 에이전트 `edo1-blog`](docs/blog-jenkins-executor.md)에서 `docker run`으로 Hugo 이미지를 실행하고, 배포는 에이전트의 `git`으로 해요. 실패하면 Stages에서 정리·checkout·Hugo 출력 준비·Hugo 빌드·산출물 검사·Pages 원격 준비·파일 복사·커밋·Push 중 실패한 단계를 확인하세요. S15P31E102-262 변경은 `blog` 병합 후 적용됩니다. `scripts/deploy.sh`는 인자 없이 기존 일괄 게시를 유지하며 Jenkins는 `prepare`, `sync`, `commit`, `push`를 분리 호출합니다. `PAGES_WORK_DIR`은 workspace의 전용 임시 디렉터리이고 성공·실패 모두 Pipeline의 `post/always`에서 정리합니다.

### Team Edo 주소로 전환하기

목표 주소는 `https://team-edo.github.io/`, 배포 저장소는 `team-edo/team-edo.github.io`, 이미지 저장소는 `team-edo/blog-images`다. 2026-10-27 GitHub 조직을 `team-edo`로, Pages 저장소를 `team-edo.github.io`로 실제 변경했다. 저장소 ID와 기존 이력은 유지됐다. Pages 빌드 `40473037319` 성공 및 홈·기존 글·CSS·이미지 사이트의 HTTP 200을 확인했다. 아래 소스 설정은 새 주소로 준비했지만 아직 `blog`에 병합하지 않았으며, Jenkins의 기존 배포 토큰 검증도 다음 운영 배포에서 확인해야 한다.

1. **GitHub 조직 확인**: 기존 `team-edo` 조직이 없다면 조직 소유자가 `team-ido`의 이름을 `team-edo`로 바꾼다. 이미 팀이 사용하는 `team-edo` 조직이 있다면 블로그·이미지 저장소를 해당 조직으로 이전한다. 다른 저장소·팀·패키지에 미치는 영향도 먼저 확인한다.
2. **Pages 저장소 이름**: 대상 조직의 블로그 저장소 이름은 반드시 `team-edo.github.io`로 맞춘다. 이미지 저장소 이름은 `blog-images`를 유지한다. 두 저장소의 Pages는 `main` / `(root)`로 확인한다.
3. **Jenkins 인증**: `github-blog-token`이 새 조직의 블로그 저장소에 쓸 수 있는지 확인한다. 같은 조직의 이름만 바꾼 경우에는 기존 토큰으로 새 주소에 접근할 수 있는지 먼저 검증한다. 다른 조직으로 이전했거나 기존 권한으로 접근할 수 없을 때만 관리자가 대상 조직과 블로그 저장소를 지정한 토큰을 발급하고 Jenkins 자격증명을 갱신한다. 토큰 값은 저장소·문서에 기록하지 않는다.
4. **소스 반영과 배포**: 이 작업의 변경을 `blog`에 병합한 뒤 Jenkins `eolssu-blog`의 빌드·배포 성공과 새 Pages 주소·글 링크·RSS·이미지 주소를 확인한다. 외부 설정이 준비되기 전에 대상 주소 변경만 먼저 병합하면 배포가 실패할 수 있다.
5. **기존 링크**: GitHub 저장소 URL 리디렉션과 Pages 사이트 URL은 별개다. 이전 Pages 주소의 자동 이동을 전제로 하지 말고, 공유한 링크와 외부 문서를 새 주소로 갱신한다.

조직명 변경·저장소 이전·토큰 갱신·실제 배포는 별도 운영 단계다. 로컬 빌드 성공만으로 주소 전환이 완료된 것으로 보지 않는다.

## 구조

```
(blog 브랜치)
├── hugo.toml          # 사이트 설정
├── content/posts/     # 글
├── layouts/           # 블로그·프로젝트·팀 소개·글 상세 템플릿
├── data/              # 로고·캐릭터·프로젝트·팀 소개 데이터
├── static/            # CSS·사이트 JS·로고·앱 홈 화면 (글 이미지는 GitHub blog-images 저장소)
├── archetypes/        # 새 글 머리말 템플릿
├── scripts/deploy.sh  # GitHub Pages로 결과물 push
└── Jenkinsfile        # 빌드·배포 파이프라인 (blog 브랜치 SCM polling)
```

`hugo server`·`hugo`가 만드는 `public/`, `resources/_gen/`, `.hugo_build.lock`은 Git에서 제외돼요.

## 기록

- 2026-10-20 develop의 `blog/`로 시작 → 2026-10-26 GitHub Pages 자동 배포 완성 (S15P31E102-128)
- 2026-10-26 "develop에는 관리 대상 코드만" 팀 의견에 따라 이 `blog` 브랜치로 분리, 이미지는 `blog-images`로 (S15P31E102-137). 자세한 기록은 develop의 `docs/plans/S15P31E102-137.md`

## 화면 디자인과 유지보수

2026-11-02 앱 디자인(연두 `#ADBE31`·먹색 `#27221E`)에 맞춰 개편했다(S15P31E102-386). 글 목록은 토스 기술 블로그의 추천 글·전체 글 구성을 참고했다. 승인된 로컬 시안의 백업은 저장소 밖 `E102-planning/blog-design-preview-2026-11-02`에 있다.

| 주소 | 화면 |
| --- | --- |
| `/` | 블로그 첫 화면. 추천 글 → 왼쪽 직무별·작성자별 읽기·함께 읽을 이야기·프로젝트 안내, 오른쪽 전체 글 |
| `/posts/` | 직무·작성자 필터와 전체 글 목록 |
| `/project/` | 프로젝트 소개, 세로 정간보 문구, 캐릭터, 갤럭시 프레임 속 앱 홈 화면, 학습 4단계 |
| `/about/` | Team Edo 소개, 팀원 6명의 담당, HOW WE WORK |
| `/posts/연/월/slug/` | 본문(720px)과 넓은 화면의 오른쪽 목차, 태그, 이전·다음 글 |

상단 메뉴는 **블로그 → 프로젝트 → 팀 소개** 순서다(`hugo.toml`의 `menus.main`).

### 팀과 프로젝트 표기

- 팀은 **Team Edo**, 프로젝트는 **얼쑤**로 표기한다. Edo는 세종대왕의 이름인 이도에서 가져왔다.
- 페이지·게시글·검색 설명·RSS 등 공개 콘텐츠에는 교육기관·기수·반 표기를 사용하지 않는다.
- 팀 표시는 `data/brand.yaml`의 `name`, 글 작성자 기본값은 `hugo.toml`의 `params.team`으로 관리한다.
- GitHub 공개 주소와 Jenkins 배포 대상은 `team-edo`로 통일한다. 원본 GitLab 저장소 주소는 기존 연결을 유지한다.

### 내용 바꾸기

- `data/brand.yaml`: 상단 로고·캐릭터 이미지(`static/` 기준 경로).
- `data/blog.yaml`: 분야 필터의 순서와 표기. 키는 글 머리말 `categories` 값과 같다. 글이 없는 분야도 버튼으로 표시한다.
- 직무 버튼을 고르면 해당 분야의 글만 표시하고, `전체`를 누르면 전체 목록으로 돌아간다. 글 목록은 최신 글이 위에 오고, 같은 날짜의 글은 제목 순으로 고정한다. 글 머리말의 `author`는 카드의 작성자 표기에 쓰인다.
- `data/project.yaml`: 프로젝트 소개 문구와 학습 4단계. 제목과 정간보 칸은 `layouts/_default/project.html`에 있다.
- `data/team.yaml`: 팀 소개 문구와 팀원 카드(`name`, `role`, `headline`, `description`, 카드 색 `accent`·`tint`). 공개에 동의한 팀원 정보만 넣는다.
- `static/images/app-home.webp`: 프로젝트 화면 휴대폰 안의 앱 홈 화면. 실제 Android 앱 홈을 1080×2400으로 캡처해 390:844 비율로 위에서부터 자르고 720px 폭 WebP로 줄였다. 앱 홈 화면이 바뀌면 같은 방법으로 다시 캡처해 교체한다.
- 프로젝트 설명은 아직 개발 중인 기능을 이용 가능한 기능처럼 표현하지 않는다.

### 글의 썸네일과 추천 글

- 머리말의 선택 필드 `thumbnail: "https://team-edo.github.io/blog-images/2026-10-example.webp"`로 썸네일을 지정한다. 없으면 첫 분야에 맞춘 기본 그림을 표시한다.
- `featured: true`인 글 중 최신 3편을 추천 영역에 표시한다. 지정된 글이 없으면 최신 3편을 사용한다.
- 추천 글이 두 편 이상이면 이전·다음 버튼과 좌우 방향키로 넘긴다. 자동 재생은 하지 않는다.
- 추천 글은 같은 칸에 겹쳐 두고 보이기만 바꾼다. 영역 높이는 가장 긴 추천 글에 맞춰 고정되므로 넘겨도 버튼·이미지 위치가 그대로이며, 제목은 2줄·요약은 3줄에서 자른다.

### 화면과 동작

- 서체는 **Noto Sans KR**(Google Fonts 400·500·600·700)이며 불러오지 못하면 시스템 고딕을 사용한다. 한글은 단어 단위로 줄바꿈한다(`word-break: keep-all`).
- 직무 버튼은 전체 글 제목 오른쪽에 나란히 놓고, 공간이 좁으면 제목 아래에서 줄을 바꾼다. 750px 이하에서는 보조 영역을 숨긴다. 600px 이하에서는 상단 메뉴가 햄버거 버튼으로 바뀌며 Esc·바깥 클릭으로 닫힌다.
- `static/js/site.js`는 추천 글 넘기기, 분야 필터(빈 결과·전체 보기 포함), 모바일 메뉴를 담당한다. JavaScript가 없어도 모든 글 링크를 이용할 수 있다. 이전 화면의 로컬 검색은 이번 디자인에서 제외했다.
- 글 본문의 소제목(`##`)에는 순서 번호가 붙고, 인용(`>`)은 강조 상자, 번호 목록은 카드, 표는 둥근 테두리로 표시된다.
- `static/css/style.css`에서 색상·간격·반응형·동작 줄이기 대응을 관리한다.
- `assets/images/`의 확정 로고·캐릭터 시트 원본은 보존하며, 화면에는 `static/images/`의 WebP를 사용한다.

### 댓글과 좋아요 (giscus)

- 글 상세 아래에 [giscus](https://giscus.app/ko) 댓글을 표시한다. 댓글은 `team-edo/team-edo.github.io`의 GitHub Discussions에 글 주소(`pathname`)별로 저장되고, 글에 누르는 반응(👍 등)이 좋아요 역할을 한다. 댓글·반응에는 GitHub 로그인이 필요하다.
- `hugo.toml`의 `[params.giscus]`에 `repoId`·`categoryId`가 비어 있으면 댓글 영역을 표시하지 않는다.
- 2026-11-03 Discussions 활성화, `Comments`(Announcement) 카테고리 생성, giscus 앱 설치(`team-edo.github.io`만, Discussions 읽기·쓰기)를 마치고 두 값을 반영했다.
- 처음 한 번 조직 관리자가 준비한다. 저장소를 바꾸거나 다시 설정할 때 같은 순서를 따른다.
  1. `team-edo.github.io` 저장소 Settings → General → Features에서 **Discussions**를 켠다.
  2. Discussions에서 형식이 **Announcement**인 `Comments` 카테고리를 만든다. 방문자는 새 글타래를 만들 수 없고 giscus가 만든 글타래에만 댓글을 단다.
  3. [giscus 앱](https://github.com/apps/giscus)을 `team-edo` 조직의 `team-edo.github.io` 저장소에만 설치한다.
  4. [giscus.app](https://giscus.app/ko)에 저장소 `team-edo/team-edo.github.io`, 카테고리 `Comments`를 넣어 나온 `data-repo-id`·`data-category-id`를 `hugo.toml`에 적고 `blog`에 MR한다. 두 값은 공개 식별자라 저장소에 적어도 된다.
- 댓글 모양은 `static/css/giscus-theme.css`(giscus 기본 light 테마 위에 블로그 색·글꼴·배치를 덮어쓴 전용 테마)가 정한다. giscus가 배포된 주소의 이 파일을 불러오므로, 바꾼 모양은 `blog` 병합·배포 후에 보인다. 로컬 미리 보기(`localhost`)에서는 브라우저의 사설망 접근 차단으로 이 테마가 적용되지 않는다. 글 반응은 "댓글 N개" 줄 오른쪽에 둔다.
- Jenkins 배포는 저장소 파일만 덮어쓰므로 Discussions의 댓글은 그대로 남는다. 글 주소(slug·날짜)를 바꾸면 기존 댓글과 연결이 끊긴다.

### 검증 기록 (2026-11-02, S15P31E102-386)

- Jenkins와 같은 Hugo `ghcr.io/gohugoio/hugo:v0.145.0`, 네트워크 차단·읽기 전용 소스로 17페이지 빌드 성공(경고 없음).
- 실제 Chrome에서 1280px·390px의 블로그·프로젝트·팀 소개·글 상세 화면을 확인했다. 가로 넘침 없음, 휴대폰 프레임 안 앱 홈 화면 표시 확인.
- 빌드 결과에 시안·초안·미리보기·예시 등 개발용 문구가 없음을 확인했다. `node --check static/js/site.js`, `git diff --check` 통과.

재확인할 때는 `hugo server -D`로 미리보기를 열어 위 주소와 화면 너비를 확인한다. 배포와 같은 빌드는 `Jenkinsfile`의 Build 단계 옵션을 기준으로 실행한다.

### Jenkins 단계 검증 (S15P31E102-262)

`python -m pip install -r requirements-blog.txt` 후 `python -B -m unittest discover -s tests -v`로 확인합니다. 게시 단계 검사는 외부 GitHub 대신 로컬 bare Git 원격을 사용합니다. 원격 준비·파일 복사·커밋까지 게시가 일어나지 않고 마지막 Push에서만 반영되며, 동일 내용은 추가 커밋을 만들지 않습니다. Git 토큰은 단계 사이 `.git/config`에 남기지 않습니다. Jenkins 선언형 문법은 실제 컨트롤러 linter를 통과했고 실제 Pages 게시는 수행하지 않았습니다. 이 변경은 `blog`를 대상으로 전달하며 서비스 develop 이력과 합치지 않습니다.
