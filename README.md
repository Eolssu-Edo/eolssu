# 얼쑤

정간보 기반 AI 연주 평가로, 입문자도 혼자 국악기를 연습할 수 있는 앱입니다.
팀 이도(E102)가 **보기 → 듣기 → 연주 → 평가**의 학습 흐름을 하나의 앱으로 제공합니다.

제품 범위와 우선순위는 [프로젝트 기획서](https://app.notion.com/p/3ffa0252aa4580a5a16cc55beadea3ba)를 기준으로 합니다. 기능별 확정 동작은 [문서 목록](docs/README.md)의 스펙을, 영역별 구현·실행 방법은 각 폴더의 README를 따릅니다.

## 제출 산출물

최종 제출 문서는 [`exec/`](exec/README.md)에 있습니다. 소스를 받은 뒤 빌드·배포하려면 포팅 매뉴얼부터 읽습니다.

| 산출물 | 파일 |
| --- | --- |
| 포팅 매뉴얼(빌드·배포 절차, 제품 버전, 환경 변수, 배포 특이사항) | [exec/1-porting-manual.md](exec/1-porting-manual.md) |
| 외부 서비스 정보(소셜 로그인, R2, SMTP, 스토어 등) | [exec/2-external-services.md](exec/2-external-services.md) |
| DB 덤프 최신본(구조 전체와 기준 데이터, 개인정보 행 제외) | [exec/3-eolssu-db-dump.sql](exec/3-eolssu-db-dump.sql) |
| 시연 시나리오 | [exec/4-demo-scenario.md](exec/4-demo-scenario.md) |

## 주요 기능

| MVP 기능 | 내용 |
| --- | --- |
| AI 연주 평가 | 녹음을 정답 악보와 비교해 음 높이·박자·음 길이 점수와 틀린 위치 제공 |
| 쉬운 정간보 | 한자 율명을 한글·계이름으로 전환하고 시김새 등 기호 설명 |
| 운지법 안내 | 악보의 음을 선택하면 악기 운지를 그림으로 표시 |
| 모범 연주 | 악보에 맞춰 재생하고 빠르기 조절 |
| 장단 메트로놈 | 세마치·굿거리·자진모리 등 국악 장단과 기본 메트로놈 제공 |

| 고도화 기능 | 내용 |
| --- | --- |
| 연습 기록·성장 | 연주 기록 목록과 곡별 최근 점수 변화, 항목별 성장 표시([연습 기록](docs/specs/practice-records.md), [점수 변화](docs/specs/growth-score-trend.md)) |
| 한 음씩 연습 | 예시음을 듣고 한 음씩 불어 보며 기기에서 실시간으로 판정([판정 기준](docs/specs/note-practice-criteria.md)) |
| 내 악보 가져오기 | 정간보 PDF(글자 층)·스캔 사진과 오선보 PDF·이미지를 인식해 편집 가능한 정간보 초안으로 변환([악보 인식](docs/specs/score-recognition.md), [정간보 스캔](docs/specs/jeongganbo-scan-recognition.md)) |
| 악보 편집 | 인식 결과 확인, 시김새·장단·구간 빠르기 편집, 악기 음역에 맞춘 이조([내 악보](docs/specs/user-sheets.md), [구간 빠르기](docs/specs/jeonggan-tempo-edit.md)) |
| 내 악보 음원 | 저장한 악보로 모범 연주 음원을 서버에서 생성해 미리 듣기와 연습에 사용([음원 흐름](docs/specs/sheet-audio-app-flow.md)) |

- 대상은 국악기 입문자이며, 현재 단소·소금·정악대금·산조대금을 지원합니다. 악기 확장 순서와 2차 범위는 기획서를 따릅니다.
- 앱은 Android·iOS와 웹(Kotlin/Wasm)으로 빌드합니다([frontend](frontend/README.md)).
- 그 밖의 기능별 동작과 변경 이력은 [문서 목록](docs/README.md)의 기능 스펙에서 확인합니다.

## 연주 평가 흐름

```text
정답:     공통 악보 데이터(정간보) → 음표 시퀀스
연주 후:  앱 녹음 업로드 → backend 검사·저장(R2) → ai 분석 API(pYIN 음높이 추정 → DTW 정렬 → 음표별 채점)
          → backend가 결과 저장 → 앱이 상태 조회 후 점수·음표별 판정 표시
```

요청·실패 코드·제한은 [분석 실패 처리 스펙](docs/specs/practice-analysis-failure.md)과 [ai README](ai/README.md)를 따릅니다.

## 기술 스택

| 영역 | 사용 기술 | 자세히 |
| --- | --- | --- |
| Frontend | Kotlin Multiplatform, Compose Multiplatform, Ktor, Koin (Android·iOS·Web Wasm) | [frontend](frontend/README.md) |
| Backend | Kotlin, Spring Boot, Spring Security(OAuth2 Resource Server), MyBatis, Flyway, springdoc | [backend](backend/README.md) |
| AI | Python 3.12, FastAPI, librosa(pYIN), DTW 채점 / 악보 인식(OMR): 오선보 Audiveris, 정간보 글자 층 jeongganbo-generator, 정간보 스캔 MALerLab jeongganbo-omr 모델(PyTorch CPU·OpenCV) | [ai](ai/README.md) |
| Data | 공통 악보·참고 음원·카탈로그 구축, 연습 집계 마트 | [data](data/README.md) |
| DB·저장소 | MySQL 8.4, Cloudflare R2(녹음·음원·악보 원본) | [DB 스키마](docs/contracts/db-schema.md) |
| Infra | Docker Compose, Nginx(TLS), Jenkins, Prometheus·Grafana, Mattermost 점검·알림 | [infra](infra/README.md) |
| 협업 | GitLab, Jira, Notion, Mattermost | [AGENTS.md](AGENTS.md) |
| 기술 블로그 | Hugo, GitHub Pages, Jenkins 자동 배포 (develop과 분리된 `blog` 브랜치) | https://team-edo.github.io |

## 운영 구성 요약

| 서버 | 역할 | 자세히 |
| --- | --- | --- |
| edo1 | 운영 서비스: Nginx(웹·API·TLS) → backend(블루그린) · ai · omr · playback · MySQL. Jenkins, Prometheus·Grafana | [운영 서버 문서](infra/runtime/README.md) |
| edo2 | DB·설정 암호화 백업과 격리 복원, MySQL 읽기 전용 복제본·대기 환경, Mattermost 점검 중계와 상태 변화 알림 | [인프라 문서 안내](infra/README.md) |

- 배포: backend는 develop 병합 시 Jenkins가 블루그린으로 자동 배포하고, 웹은 수동 잡으로 배포합니다. ai·omr·playback은 해당 이미지 입력이 바뀌면 Jenkins가 이미지를 빌드·검사해 자동 배포합니다.
- 최종 발표용 main 반영·태그·시연 동결은 [릴리스 절차](infra/release/README.md)를, edo1 장애 시 edo2 전환은 [장애 전환 런북](infra/standby/failover-runbook.md)을 따릅니다.
- 구성 요소 간 연결과 실측 수치는 [시스템 아키텍처](docs/knowledge/infra/system-architecture.md)와 [운영·품질 실측 근거](docs/knowledge/infra/ops-quality-evidence.md)에 정리돼 있습니다.

## 저장소 구조

```text
S15P31E102/
├── AGENTS.md     # AI 공통 작업 지침
├── CLAUDE.md     # Claude Code에서 공통 지침 불러오기
├── frontend/     # KMP 앱(Android·iOS·Web)
├── backend/      # 곡·악보·연습·평가·계정 API
├── ai/           # 연주 분석 API, 악보 인식(OMR) 서비스
├── data/         # 악보·음원 데이터 구축, 음원 생성기(playback), 집계 마트
├── infra/        # 운영 Compose·Nginx·Jenkins·백업·점검·릴리스
├── docs/         # 스펙·계약·지식·결정·작업 기록
├── exec/         # 최종 제출 산출물(포팅 매뉴얼·외부 서비스·DB 덤프·시연 시나리오)
├── docker-compose.dev.yml · docker-compose.prod.yml   # 초기 backend·Nginx 예시(운영은 infra/runtime/compose.yml)
├── .env.dev.example · .env.prod.example               # 위 예시용 환경 변수 이름·용도
└── .gitlab/      # MR 템플릿
```

## 브랜치

| 브랜치 | 역할 |
| --- | --- |
| `develop` | 기본·개발 통합 브랜치. 작업 브랜치는 여기서 만들고 여기로 MR하며, 운영 자동 배포의 기준입니다. |
| `main` | 발표 시점의 `develop`을 반영한 제출 기준 브랜치. `exec/` 제출 산출물을 함께 담습니다. |
| `blog` | 기술 블로그 글·설정. 서비스 코드가 아니어서 develop과 기록을 공유하지 않으며, 글 쓰는 방법은 `blog` 브랜치의 README를 따릅니다. |

각 작업 폴더에는 파트별 `AGENTS.md`와 이를 불러오는 `CLAUDE.md`가 있습니다. `data/raw/`, `data/processed/`, `ai/data/`, `ai/models/`는 Git 제외 대상이며, 소스와 작은 검증용 샘플은 추적 가능한 별도 경로에 둡니다.

## 실행과 검증

실행·검증 명령과 필수 환경은 각 영역의 README에 기록합니다.

- 개발 중에는 변경 영역의 관련 테스트를 실행하고, 완료 시 영향받는 영역의 필수 검사와 회귀 검증을 수행합니다.
- 모델 추론·실제 기기·대용량 음원 검증은 빠른 기본 검사와 실행 조건을 구분합니다. 해당 변경에 필요한 검증은 병합 전에 수행합니다.
- 운영 Jenkins 잡이 실행하는 파이프라인 원본은 `infra/jenkins/`에 있습니다([운영 서버 문서](infra/runtime/README.md#jenkins)).
- 문서의 상대 링크는 `python3 -B infra/scripts/check-markdown-links.py`로 검사합니다.

## 협업 규칙

Jira 업무·브랜치·MR 규칙은 [루트 AGENTS.md](AGENTS.md)의 `Jira·Git·MR` 절을 따릅니다. `Task 단위`와 `커밋 규칙` 절의 작업 구분 기준·브랜치와 커밋의 공통 타입은 팀 합의 전 초안으로 관리합니다.

## AI 작업 도구

- Jira 작업은 연결된 MCP로 처리합니다. 팀원별 연결과 요청 예시는 [Jira MCP 사용 안내](docs/knowledge/tooling/jira-mcp.md)를 참고합니다.
- COCO는 [루트 AGENTS.md](AGENTS.md)의 `COCO` 절에 적힌 배포본을 기준으로 사용합니다.
- 팀 기본은 필요한 스킬을 사용자가 선택해 명령어로 실행하는 방식입니다. 고정된 단계 순서를 강제하지 않습니다.
- 자연어에 따른 자동 선택은 개인이 명시적으로 허용한 경우에 적용합니다.
- Codex는 `$coco-build`, Claude Code는 `/coco-build`처럼 각 도구의 명령으로 선택합니다.
- COCO 갱신 알림·QA 앱 다운로드·서버 연결은 [Eolssu Updates 앱](infra/tool-updates/README.md)을 사용합니다.
- 공통 규칙은 [루트 AGENTS.md](AGENTS.md), 파트별 규칙은 각 폴더의 `AGENTS.md`를 따릅니다. `CLAUDE.md`는 같은 지침을 불러옵니다.
- 코드 변경이 없는 조사·학습도 팀이 재사용할 결과가 생기면 [기록 기준](docs/README.md)에 따라 지식·실험·결정을 남깁니다.

## 환경 변수와 비밀 정보

인증 정보, 개인 키, 실제 환경변수 값은 커밋하지 않습니다. 예시 파일에는 이름과 용도만 남기고, 운영 값은 서버 환경 파일과 Jenkins Credentials에서 주입합니다([운영 서버 문서](infra/runtime/README.md)).

## 관련 문서

- [저장소 문서 목록](docs/README.md)
- [인프라 실행과 운영 문서 안내](infra/README.md)
- [프로젝트 기획서](https://app.notion.com/p/3ffa0252aa4580a5a16cc55beadea3ba)
- [팀 Jira·Git 규칙](https://app.notion.com/p/Jira-Git-af8a0252aa45823e9cbf8131a9ac7734)
