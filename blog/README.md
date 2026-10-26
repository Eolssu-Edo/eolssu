# 이도 기술 블로그

Hugo로 만든 팀 기술 블로그예요. `develop`에 `blog/` 변경이 머지되면 Jenkins가 빌드해서 **결과물(HTML)만** GitHub Pages에 올려요. 소스와 커밋 기록은 GitLab 밖으로 나가지 않아요.

```
blog/ 수정 → MR → develop 머지 → Jenkins가 1분마다 develop polling
  → blog/ 변경이 있으면 Hugo 빌드 (blog-build/public) → GitHub Pages 저장소에 push → https://team-ido.github.io
```

Jenkins는 서버 안(loopback)에서만 접속을 받기 때문에 GitLab webhook을 쓰지 않고, 다른 develop 잡과 같은 SCM polling으로 변경을 감지해요. develop의 다른 변경도 polling에 잡히지만 `blog/` 변경이 없으면 빌드·배포 단계를 건너뛰어요.

## 글 쓰기

1. 브랜치 만들기: [루트 AGENTS.md](../AGENTS.md)의 규칙(`타입(파트)/전체JiraKey-Task제목`)을 따라요. 글만 추가하면 타입은 `docs`예요.
2. `blog/content/posts/`에 `YYYY-MM-DD-주제.md` 파일 추가 (아래 머리말 복사)
3. 이미지는 `blog/static/images/`에 넣고 `![설명](/images/파일명.png)`로 사용
4. MR을 올리고 리뷰 후 develop에 머지 → 자동 배포

```markdown
---
title: "글 제목"
date: 2026-10-21
slug: "english-slug"      # 주소에 쓸 영어 이름
author: "이름"
categories: ["ai"]        # backend, frontend, ai, data, infra, 회고
tags: ["단소", "음높이"]
description: "목록에 보일 한 줄 요약"
draft: false              # true면 배포되지 않아요
---

본문
```

## 공개 블로그 규칙

- 서버 주소, IP, 도메인, 포트, 키, 토큰, 비밀번호를 쓰지 않기
- `application-*.yml`, `.env` 같은 설정 파일 내용을 붙여넣지 않기
- GitLab, Jira, 노션 등 내부 링크를 쓰지 않기
- 팀원 실명·연락처 등 개인정보는 본인 동의 후에만
- 다른 사람의 글·이미지·악보·음원은 출처와 이용 조건 확인 후 사용

## 내 컴퓨터에서 미리 보기

[Hugo](https://gohugo.io/installation/) 설치 후 저장소 루트에서:

```bash
hugo server --source blog -D
```

`http://localhost:1313`에서 확인해요. `-D`는 `draft: true` 글도 보여줘요.

## 배포 설정 (최초 1회, Infra 담당)

### GitHub
1. 팀 Organization 생성 (예: `team-ido`)
2. 저장소 `team-ido.github.io`를 **Public**으로 생성
3. Settings → Pages → Source: **Deploy from a branch**, Branch: `main` / `(root)`
4. Fine-grained 토큰 발급: 대상 저장소는 `team-ido.github.io`만, 권한은 **Contents: Read and write**만

### Jenkins
접속 방법은 [운영 문서](../infra/runtime/README.md)의 Jenkins 절(SSH 터널)을 따라요.

1. 플러그인: Git, Credentials Binding, Pipeline (기존 develop 잡과 같음. GitLab·Docker Pipeline 플러그인은 필요 없어요)
2. Credentials에 토큰 등록: 종류 **Secret text**, ID `github-blog-token`. GitLab 읽기는 기존 `eolssu-gitlab`을 써요
3. Pipeline 잡 생성: **Pipeline script from SCM**, 저장소 `https://lab.ssafy.com/s15-final/S15P31E102.git`, Credentials `eolssu-gitlab`, 브랜치 `*/develop`, Script Path `blog/Jenkinsfile`
4. 잡을 **한 번 직접 실행**해요. 이때 polling 트리거가 등록되고 처음 배포가 돼요. 다음부터는 develop의 `blog/` 변경을 1분 안에 감지해요

빌드는 호스트 에이전트(`edo1-host`)에서 `docker run`으로 Hugo 이미지를 실행하고, 배포는 에이전트의 `git`으로 해요. 실패하면 잡 로그의 `Build`·`Deploy` 단계를 보세요.

### 이름을 바꿀 때
Organization 이름이 `team-ido`가 아니면 아래 두 곳을 바꿔요.
- `blog/Jenkinsfile`: `GH_REPO`, `SITE_URL`
- `blog/hugo.toml`: `baseURL`

## 구조

```
blog/
├── hugo.toml          # 사이트 설정
├── content/posts/     # 글
├── layouts/           # 화면 틀 (직접 만든 간단한 테마)
├── static/            # CSS, 이미지
├── archetypes/        # 새 글 머리말 템플릿
├── scripts/deploy.sh  # GitHub Pages로 결과물 push
└── Jenkinsfile        # 빌드·배포 파이프라인 (develop SCM polling)
```

`hugo server`나 `hugo` 빌드가 만드는 `blog/public/`, `blog/resources/_gen/`, `.hugo_build.lock`과 Jenkins의 `blog-build/`는 Git에서 제외돼요.
