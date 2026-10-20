# S15P31E102

> 프로젝트 한 줄 소개를 작성해주세요.

## 📌 프로젝트 개요

- **기간**: 2026.10 ~
- **주제**:
- **목표**:

## 👥 팀원

| 이름 | 역할 | 담당 |
| --- | --- | --- |
|  | 팀장 |  |
|  | BE |  |
|  | FE |  |
|  | AI |  |
|  | Infra |  |

## 🛠 기술 스택

| 분야 | 기술 |
| --- | --- |
| Backend | Java, Spring Boot, Gradle |
| Frontend |  |
| AI | Python |
| Infra | Docker, Jenkins, Nginx |
| Collaboration | GitLab, Jira, Mattermost, Notion |

## 📁 폴더 구조

```
S15P31E102
├── backend/      # Spring Boot 서버
├── frontend/     # 프론트엔드
├── ai/           # AI / 데이터 처리
├── infra/        # Docker, Jenkins, Nginx 설정
├── docs/         # ERD, API 명세, 회의록 등
└── .gitlab/      # 이슈 · MR 템플릿
```

## 🌿 브랜치 전략

| 브랜치 | 용도 |
| --- | --- |
| `main` | 배포 브랜치 |
| `develop` | 개발 통합 브랜치 |
| `feature/{part}/{기능}` | 기능 개발 (예: `feature/be/login`) |
| `fix/{part}/{내용}` | 버그 수정 |
| `hotfix/{내용}` | 배포 중 긴급 수정 |

- `feature/*` → `develop` 으로 MR, 리뷰 1명 이상 승인 후 머지
- `develop` → `main` 은 배포 시점에만 머지

## ✍️ 커밋 컨벤션

```
타입: 제목 (#이슈번호)
```

| 타입 | 설명 |
| --- | --- |
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅 (동작 변화 없음) |
| `test` | 테스트 코드 |
| `chore` | 빌드, 설정, 패키지 등 |

예시: `feat: 소셜 로그인 구현 (#12)`

## 🚀 실행 방법

### Backend
```bash
cd backend
./gradlew bootRun
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

### AI
```bash
cd ai
python -m venv .venv
pip install -r requirements.txt
```

## 🔐 환경 변수

민감 정보(`.env`, `application-secret.yml` 등)는 커밋하지 않습니다.
`*.example` 파일을 복사해 로컬에서 값을 채워 사용하세요.
