# 이도 기술 블로그

Hugo로 만든 팀 기술 블로그예요. 주소는 **https://team-ido.github.io** 예요.

이 `blog` 브랜치는 **블로그 전용 브랜치**예요. 서비스 코드(develop·main)와 기록을 공유하지 않아서, develop에는 블로그 파일이 없고 블로그 글을 머지해도 앱 빌드·배포가 일어나지 않아요.

```
글 브랜치 → MR (대상: blog) → blog 머지 → Jenkins가 1분마다 blog 브랜치 polling
  → Hugo 빌드 → 결과물(HTML)만 GitHub team-ido/team-ido.github.io에 push → https://team-ido.github.io
```

- 원본(글·설정)은 이 GitLab `blog` 브랜치에만 있고, GitHub에는 완성된 HTML만 올라가요.
- 글 이미지는 GitHub `team-ido/blog-images`에 따로 둬요 (아래 [이미지](#이미지)).
- Jenkins는 서버 안(loopback)에서만 접속을 받아서 GitLab webhook 대신 SCM polling으로 변경을 감지해요.

## 글 쓰기

1. `blog` 브랜치에서 글 브랜치를 만들어요. **develop에서 만들지 않아요.**
   ```bash
   git fetch origin
   git switch -c "docs(blog)/S15P31E102-<번호>-<글 제목>" origin/blog
   ```
   브랜치 이름은 [팀 규칙](https://lab.ssafy.com/s15-final/S15P31E102/-/blob/develop/AGENTS.md)의 `타입(파트)/전체JiraKey-Task제목`을 따르고 파트 자리에 `blog`를 써요.
2. `content/posts/`에 `YYYY-MM-DD-주제.md` 파일을 추가해요 (아래 머리말 복사)
3. 이미지는 GitHub `team-ido/blog-images`에 올리고 주소로 넣어요
4. MR을 **대상 브랜치 `blog`**로 올리고, 리뷰 후 머지하면 1분 안에 자동 배포돼요

```markdown
---
title: "글 제목"
date: 2026-10-26
slug: "english-slug"      # 주소에 쓸 영어 이름 → /posts/2026/10/english-slug/
author: "이름"
categories: ["ai"]        # backend, frontend, ai, data, infra, 회고
tags: ["단소", "음높이"]
description: "목록에 보일 한 줄 요약"
draft: false              # true면 배포되지 않아요
---

본문은 일반 마크다운으로 써요. 코드 블록·표·링크 모두 돼요.

![그림 설명](https://team-ido.github.io/blog-images/2026-10-pitch-result.webp)
```

- `date`는 **오늘이나 지난 날짜**로 써요. 미래 날짜 글은 Hugo가 건너뛰어서, 머지해도 그 날짜가 지난 뒤 다음 배포 때까지 안 보여요.

## 이미지

한 번 커밋한 이미지는 지워도 Git 기록에 남아 저장소 clone이 계속 무거워져요. 그래서 이미지는 **GitHub 이미지 전용 저장소**에 두고, 글에는 주소만 적어요.

| 무엇 | 어디 |
| --- | --- |
| 글(.md) | 이 `blog` 브랜치 `content/posts/` (MR로) |
| 이미지 | GitHub [`team-ido/blog-images`](https://github.com/team-ido/blog-images) (웹에서 업로드) |
| 글에 쓰는 주소 | `https://team-ido.github.io/blog-images/<파일명>` |

**올리는 법**
1. GitHub `team-ido/blog-images` → **Add file** → **Upload files**
2. 파일을 끌어다 놓고, 커밋 메시지에 어떤 글의 이미지인지 적고 **Commit changes** (main에 바로)
3. 1~2분 뒤 `https://team-ido.github.io/blog-images/<파일명>`이 열리면 글에 그 주소를 써요

**규칙**
- 파일 이름은 `YYYY-MM-설명.webp`처럼 **날짜를 앞에** 붙이고 영어 소문자·숫자·하이픈만 써요 (예: `2026-10-pitch-result.webp`). 한글·공백은 주소가 깨져요
- 형식은 WebP 또는 압축한 PNG·JPG, **한 장 300KB 이하**. 동영상·큰 GIF는 올리지 않고 외부 링크로 걸어요
- 공개 저장소예요. 서버 화면·설정·토큰·내부 시스템이 보이는 캡처는 가리고 올려요
- 간단한 구조도는 이미지 대신 글 안의 표·코드 블록으로 대신할 수 있는지 먼저 생각해요
- 이 브랜치의 `static/`에는 글 이미지를 넣지 않아요 (디자인에 꼭 필요한 작은 파일만 예외)

이미지 업로드는 `team-ido` Organization 멤버만 할 수 있어요. 처음 쓰는 팀원은 Organization 관리자에게 초대를 요청해요.

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
1. Organization `team-ido`, Public 저장소 `team-ido.github.io` (Pages: `main` / `(root)`)
2. Fine-grained 토큰: 대상 저장소는 `team-ido.github.io`만, 권한은 **Contents: Read and write**만
3. 이미지 저장소 `blog-images`를 **Public**으로 생성 (README만 추가), Settings → Pages → Branch `main` / `(root)`. 배포 토큰에는 이 저장소 권한을 주지 않아요

### Jenkins
접속은 서비스 저장소 운영 문서(develop의 `infra/runtime/README.md` Jenkins 절, SSH 터널)를 따라요.

1. 플러그인: Git, Credentials Binding, Pipeline (GitLab·Docker Pipeline 플러그인은 필요 없어요)
2. Credentials: **Secret text** `github-blog-token`. GitLab 읽기는 기존 `eolssu-gitlab`
3. Pipeline 잡 `eolssu-blog`: **Pipeline script from SCM**, 저장소 `https://lab.ssafy.com/s15-final/S15P31E102.git`, Credentials `eolssu-gitlab`, 브랜치 `*/blog`, Script Path `Jenkinsfile`
4. 잡을 **한 번 직접 실행**하면 polling 트리거가 등록돼요. 다음부터는 `blog` 브랜치 변경을 1분 안에 감지해요

빌드는 호스트 에이전트에서 `docker run`으로 Hugo 이미지를 실행하고, 배포는 에이전트의 `git`으로 해요. 실패하면 잡 로그의 `Build`·`Deploy` 단계를 보세요.

### 이름을 바꿀 때
Organization 이름이 `team-ido`가 아니면 `Jenkinsfile`의 `GH_REPO`·`SITE_URL`, `hugo.toml`의 `baseURL`을 바꿔요.

## 구조

```
(blog 브랜치)
├── hugo.toml          # 사이트 설정
├── content/posts/     # 글
├── layouts/           # 화면 틀 (직접 만든 간단한 테마)
├── static/            # CSS (글 이미지는 GitHub blog-images 저장소)
├── archetypes/        # 새 글 머리말 템플릿
├── scripts/deploy.sh  # GitHub Pages로 결과물 push
└── Jenkinsfile        # 빌드·배포 파이프라인 (blog 브랜치 SCM polling)
```

`hugo server`·`hugo`가 만드는 `public/`, `resources/_gen/`, `.hugo_build.lock`은 Git에서 제외돼요.

## 기록

- 2026-10-20 develop의 `blog/`로 시작 → 2026-10-26 GitHub Pages 자동 배포 완성 (S15P31E102-128)
- 2026-10-26 "develop에는 관리 대상 코드만" 팀 의견에 따라 이 `blog` 브랜치로 분리, 이미지는 `blog-images`로 (S15P31E102-137). 자세한 기록은 develop의 `docs/plans/S15P31E102-137.md`
