# 이도 기술 블로그

Hugo로 만든 팀 기술 블로그예요. `develop`에 `blog/` 변경이 머지되면 Jenkins가 빌드해서 **결과물(HTML)만** GitHub Pages에 올려요. 소스와 커밋 기록은 GitLab 밖으로 나가지 않아요.

```
blog/ 수정 → MR → develop 머지 → GitLab webhook → Jenkins
  → Hugo 빌드 (blog/public) → GitHub Pages 저장소에 push → https://team-ido.github.io
```

## 글 쓰기

1. 브랜치 만들기: `feature/docs/blog-<주제>`
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
1. 플러그인: GitLab, Docker Pipeline, Credentials Binding
2. Credentials에 토큰 등록: 종류 **Secret text**, ID `github-blog-token`
3. Pipeline 잡 생성: SCM은 이 GitLab 저장소, 브랜치 `develop`, Script Path `blog/Jenkinsfile`
4. GitLab 저장소 Settings → Webhooks에 Jenkins 잡의 GitLab webhook 주소 등록 (Push events)

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
└── Jenkinsfile        # 빌드·배포 파이프라인
```
