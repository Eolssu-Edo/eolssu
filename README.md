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
categories: ["ai"]        # backend, frontend, ai, data, infra, 회고
tags: ["단소", "음높이"]
description: "목록에 보일 한 줄 요약"
draft: false              # true면 배포되지 않아요
---

본문은 일반 마크다운으로 써요. 코드 블록·표·링크 모두 돼요.

![그림 설명](https://team-edo.github.io/blog-images/2026-10-pitch-result.webp)
```

- `date`는 **오늘이나 지난 날짜**로 써요. 미래 날짜 글은 Hugo가 건너뛰어서, 머지해도 그 날짜가 지난 뒤 다음 배포 때까지 안 보여요.

## 블로그 글감과 게시 기준

후보는 넓게 모으고, 게시 전에는 직접 한 일·선택한 이유·결과의 근거를 확인합니다. 성과·설계·문제 예방·자동화도 글감이며, 숫자는 여러 근거 중 하나입니다. 구체적인 기준은 [Team Edo 블로그 작성 기준](docs/blog-editorial-guidelines.md)을 따릅니다.

## develop의 TIL을 선별해서 가져오기

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

빌드는 호스트 에이전트에서 `docker run`으로 Hugo 이미지를 실행하고, 배포는 에이전트의 `git`으로 해요. 실패하면 잡 로그의 `Build`·`Deploy` 단계를 보세요.

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
├── layouts/           # 랜딩·블로그·팀 소개·글 상세 템플릿
├── data/              # 로고·캐릭터·프로젝트·팀 소개 데이터
├── static/            # CSS·사이트 JS (글 이미지는 GitHub blog-images 저장소)
├── archetypes/        # 새 글 머리말 템플릿
├── scripts/deploy.sh  # GitHub Pages로 결과물 push
└── Jenkinsfile        # 빌드·배포 파이프라인 (blog 브랜치 SCM polling)
```

`hugo server`·`hugo`가 만드는 `public/`, `resources/_gen/`, `.hugo_build.lock`은 Git에서 제외돼요.

## 기록

- 2026-10-20 develop의 `blog/`로 시작 → 2026-10-26 GitHub Pages 자동 배포 완성 (S15P31E102-128)
- 2026-10-26 "develop에는 관리 대상 코드만" 팀 의견에 따라 이 `blog` 브랜치로 분리, 이미지는 `blog-images`로 (S15P31E102-137). 자세한 기록은 develop의 `docs/plans/S15P31E102-137.md`

## 화면 디자인과 유지보수

2026-10-27 팀이 선택한 당근 콘텐츠 페이지의 구성을 참고해, 첫 화면을 프로젝트 랜딩으로 두고 블로그·팀 소개를 연결했다. 기존 가로형 목록 시안을 대체한다. 색상과 로고·캐릭터는 당근의 자산을 복사하지 않고 Team Edo의 자산을 사용한다.

| 주소 | 화면 |
| --- | --- |
| `/` | 얼쑤 프로젝트 소개, 확정 캐릭터, 학습 흐름, 최근 글과 팀 소개 이동 |
| `/posts/` | 추천 글 슬라이드, 카테고리·검색, 2열 카드 목록 |
| `/about/` | 팀 소개, 분야별 역할, 팀원과 확정 로고·캐릭터 소개 |
| `/posts/연/월/slug/` | 기존 글 본문·목차, 왼쪽 글 제목 목록·카테고리 |

### 팀과 프로젝트 표기

- 팀은 **Team Edo**, 프로젝트는 **얼쑤**로 표기한다. Edo는 세종대왕의 이름인 이도에서 가져왔다.
- 페이지·게시글·검색 설명·RSS 등 공개 콘텐츠에는 교육기관·기수·반 표기를 사용하지 않는다.
- 팀 표시는 `data/brand.yaml`의 `name`, 글 작성자 기본값은 `hugo.toml`의 `params.team`으로 관리한다.
- GitHub 공개 주소와 Jenkins 배포 대상은 `team-edo`로 통일한다. 원본 GitLab 저장소 주소는 기존 연결을 유지한다.

### 로고·캐릭터와 소개 내용 바꾸기

- `data/brand.yaml`의 `logo`, `character`에 `assets/` 기준 원본 이미지 경로를 넣는다. Hugo가 웹용 WebP를 만든다. 공개 URL·static 기준 경로도 대체 경로로 지원한다. 로고는 상단에, 캐릭터는 랜딩과 팀 소개에 표시된다. 팀 소개에는 로고를 소개하는 자리도 있다. 비어 있으면 준비 중 표시를 사용한다.
- `data/project.yaml`은 프로젝트 이름·설명·학습 흐름을 관리한다. 실제 공개 서비스 주소가 준비되면 `serviceURL`에 입력한다. 비어 있는 동안에는 랜딩 안의 프로젝트 소개로 이동한다. 랜딩 제목과 섹션 문구는 `layouts/index.html`에서 수정한다.
- `data/team.yaml`의 `members`에는 공개에 동의한 팀원 정보만 추가한다. 필드는 `name`, `role`, `description`, `photo`(선택)다. 현재 실제 팀원 정보는 비어 있으며 화면에 준비 중으로 표시한다.
- 팀 소개 본문은 `content/about.md`, 분야별 설명은 `data/team.yaml`의 `areas`에서 수정한다.
- 프로젝트 설명은 서비스 저장소 README의 국악기 입문자·정간보·보기→듣기→연주→평가 흐름에 근거한다. 아직 개발 중인 기능을 이용 가능한 기능처럼 표현하지 않는다.

### 글의 썸네일과 추천 글

- 머리말의 선택 필드 `thumbnail: "https://team-edo.github.io/blog-images/2026-10-example.webp"`로 썸네일을 지정한다. 이미지가 없으면 기본 표시를 사용한다. 기존 글의 필수 머리말·주소는 유지한다.
- `featured: true`인 글 중 최신 3편을 추천 영역에 표시한다. 지정된 글이 없으면 최신 3편을 사용한다.
- 글이 여러 편이면 이전·다음 버튼과 키보드 좌우 방향키로 넘긴다. 자동 재생은 하지 않는다. 글이 한 편이면 이동 버튼을 표시하지 않는다.
- 실제 게시글은 한 편이다. 여러 글을 사용하는 슬라이드·검색 검증은 저장소 밖의 임시 글로 수행했으며 임시 글을 이 브랜치에 넣지 않았다.

### 화면과 동작

- 홈·블로그·팀 소개는 사이드바 없이 넓게 사용한다. 블로그 카드는 2열이며 600px 이하에서는 1열로 바뀐다.
- 왼쪽 글 목록은 상세 화면에만 표시한다. 글 제목이 카테고리보다 먼저 나오고 현재 글을 강조한다. 980px 이하에서는 기본 HTML `details` 메뉴로 바뀐다.
- `static/js/site.js`는 슬라이드와 로컬 검색·분류 필터를 담당한다. 검색 대상은 제목·요약·카테고리·태그다. 검색어를 공백으로 나누어 모두 일치하는 글을 표시하며, 카테고리 필터와 함께 적용한다. 검색 결과·빈 결과·초기화를 제공한다.
- JavaScript를 사용할 수 없어도 첫 추천 글과 전체 글·분류·본문 링크를 이용할 수 있다. 검색·슬라이드 제어는 JavaScript가 준비된 뒤에 표시한다.
- `static/css/style.css`에서 색상·간격·카드·본문·반응형·흰 배경·동작 줄이기 대응을 관리한다. 서체는 제공된 앱 와이어프레임과 같은 Pretendard·Noto Serif KR·Nanum Brush Script를 사용하며 CDN에서 읽는다. 연결되지 않을 때에는 시스템 글꼴로 대체한다. UI 라이브러리·외부 검색 서비스는 추가하지 않았다.

### 흰 배경과 프로젝트 포인트

제공받은 `얼쑤-와이어프레임-API정리.html`의 HOME 화면을 디자인 참고로 사용했다. 앱 디자인은 진행 중이며, 화면 구성을 확정된 앱 명세로 취급하지 않는다. 사용자의 피드백에 따라 블로그는 흰 배경을 유지하며 한지 질감·미색·넓은 옥색 면을 사용하지 않는다. HTML 전체나 API 정리·프로토타입 코드는 블로그에 넣지 않았다.

| 요소 | 현재 기준 |
| --- | --- |
| 기본 배경 | `#ffffff` |
| 본문 먹색 | `#191c1a` |
| 작은 초록 포인트 | `#244d3c` |
| 작은 붉은 포인트 | `#a83a30` |
| 구분선 | `#e8ece9` |
| 본문 | Pretendard |
| 주요 제목 | Noto Serif KR |
| 앱 화면 예시의 붓글씨 | Nanum Brush Script |

- `static/css/style.css`의 `White blog canvas with restrained Eolssu accents`에서 흰 배경·서체·버튼·화면 예시를 관리한다. 시스템 테마와 무관하게 흰 배경을 유지한다. 프로젝트 색은 활성 메뉴·작은 라벨·구분 요소에 제한하고, 로고와 캐릭터가 정체성을 전달한다.
- 랜딩에 확정 팀 로고와 앱 홈 화면 예시를 배치한다. `layouts/partials/app-preview.html`은 제공된 홈 화면의 읽기 전용 표현이며 실제 평가·마이크 기능은 없다. 확정 백호 캐릭터를 앱 화면 옆에 배치한다.
- 썸네일이 없는 글은 실제 카테고리·날짜를 사용한 흰 표지를 표시한다. 썸네일이 지정된 글은 업로드한 이미지를 그대로 사용한다.
- 2026-10-27 사용자가 첨부한 붓글씨 로고와 백호 캐릭터 시트를 **확정 팀 자산**으로 반영했다. `assets/images/eolssu-team-logo.png`, `assets/images/eolssu-character-sheet.png`에 원본 바이트를 그대로 보존한다. 사용하지 않는 이전 시안의 중복 원본 파일은 저장소에 포함하지 않는다.
- 상단과 랜딩·팀 소개에 같은 확정 로고를 사용한다. 캐릭터는 `characterCrop` 설정으로 시트의 정면 부분만 표시한다. 원본 시트와 캐릭터 그림을 재생성하거나 변경하지 않았다.
- `layouts/partials/brand-image.html`에서 실제 이미지 크기를 넣고 웹용 WebP를 만든다. `hugo.toml`의 이미지 캐시 `:cacheDir/images`는 기존 Jenkins 읽기 전용 소스 빌드를 유지한다.
- 앱 디자인은 여전히 진행 중이다. 앱 화면 예시는 확정 디자인으로 표현하지 않으며 최종 앱 디자인이 전달되면 갱신한다.

### 검증 기록 (2026-10-27)

- Hugo `ghcr.io/gohugoio/hugo:v0.145.0`으로 실제 글 기준 16페이지 빌드 성공. 소스는 읽기 전용이고 빌드 컨테이너 네트워크는 차단했다.
- 저장소 밖에 임시 글 6편을 추가한 7편·36페이지 빌드 성공. 긴 제목·이미지·목차·코드 블록·표를 포함했다.
- 실제 Chromium 브라우저에서 1440·1024·768·390·320px의 홈·블로그·팀 소개·본문·태그·분류 화면을 확인했다(75개 화면). 가로 넘침·제목 구조·사이드바 적용 범위·모바일 전환·카드 열 수·이미지 로딩·앵커 검사 통과.
- 추천 글 다음·이전·순환·좌우 방향키, 검색 결과 1건, 카테고리 단독 필터, 검색과 카테고리 교집합의 빈 결과, 필터 초기화를 실제 입력으로 확인했다.
- 모바일 글 목록 펼치기·Enter 닫기를 확인했다. `node --check static/js/site.js`와 `git diff --check` 통과.
- 새 웹폰트 로딩이 끝난 상태에서 75개 화면 검사를 다시 통과했다. 최종 흰 배경·확정 자산·Team Edo 표기를 적용한 뒤에도 실제 글 35개 화면 검사를 통과했다. 로고 로딩·앱 예시 경계·작은 화면의 내부 잘림도 확인했다. 원본 로고와 저장소 복사본의 SHA-256 일치를 확인했다.
- 첫 글은 Team Edo·얼쑤 표기로 정리하되 기존 글 URL을 유지한다. Jenkins는 `blog` 브랜치 polling과 기존 자격증명 ID를 유지하고 GitHub 배포 대상·사이트 주소를 `team-edo`로 변경한다. 디자인의 운영 반영과 Jenkins 토큰 검증은 `blog` 병합 후 확인한다.

재확인할 때는 `hugo server -D`로 미리보기를 열어 위 주소와 화면 너비를 확인한다. 배포와 같은 빌드는 `Jenkinsfile`의 Build 단계 옵션을 기준으로 실행한다. 산출물과 임시 검증 데이터는 소스 worktree 밖에 둔다.
