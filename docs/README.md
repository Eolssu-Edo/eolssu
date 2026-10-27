# 프로젝트 문서

## 문서 위치

| 내용 | 위치 |
| --- | --- |
| 제품 범위·기술 현황 | [루트 README](../README.md) |
| 공통 작업 지침·Jira/Git/커밋/MR 규칙 | [루트 AGENTS.md](../AGENTS.md) |
| 파트별 작업 지침 | [Frontend](../frontend/AGENTS.md) · [Backend](../backend/AGENTS.md) · [Data](../data/AGENTS.md) · [AI](../ai/AGENTS.md) · [Infra](../infra/AGENTS.md) |
| 프론트엔드 구조·협업 규칙 | [frontend/docs/architecture.md](../frontend/docs/architecture.md) · [frontend/docs/collaboration.md](../frontend/docs/collaboration.md) |
| 문서 작성 지침 | [docs/AGENTS.md](AGENTS.md) |
| 데이터 담당 범위·파일 관리 | [data/README.md](../data/README.md) |
| Docker·Compose·Nginx·백엔드 Jenkins 실행 | [infra/README.md](../infra/README.md) |
| edo1 운영 서버·eolssu 이름·Jenkins 접속 | [infra/runtime/README.md](../infra/runtime/README.md) |
| Jenkins 컨테이너·호스트 에이전트·복구 | [Jenkins 컨트롤러 운영 문서](../infra/jenkins/controller/README.md) |
| Jira MCP 연결·사용 방법 | [Jira MCP 사용 안내](knowledge/tooling/jira-mcp.md) |
| 도메인 지식·기술 조사·실험 | `docs/knowledge/<영역>/<주제>.md` |
| 용어집 | `docs/knowledge/domain/glossary.md` |
| 팀의 설계·기술 결정(ADR·기각 포함) | `docs/decisions/<주제>.md` |
| 확정된 공통 데이터·API 계약 | `docs/contracts/<주제>.md` |
| 기능 스펙 | `docs/specs/<주제>.md` |
| Task별 작업 계획·진행·결정 기록 | `docs/plans/<Jira Key>.md` |
| 설치·실행·검증 명령과 필수 검사 기준 | 각 구현 폴더의 `README.md` |

영역 이름은 `frontend`, `backend`, `data`, `ai`, `infra`를 사용합니다. 국악 개념처럼 여러 파트가 공유하는 지식은 `domain`, 개발 도구는 `tooling`에 둡니다. 아직 없는 폴더는 첫 문서를 작성할 때 만듭니다.

## 공통 계약

- [DB 스키마와 ERD](contracts/db-schema.md)

## 기능 스펙

- [녹음 파일 규격 (PRA-02)](specs/recording-file.md)
- [연습 시간 집계 기준 (GRO-01·02·04)](specs/practice-time.md)
- [연주 분석 실패 사유와 화면 안내 (PRA-03)](specs/practice-analysis-failure.md)

## 결정 기록

- [프론트엔드 화면 이동·DI 라이브러리](decisions/frontend-navigation-di.md) (제안)
- [프론트엔드 기기 뒤로 가기 처리 라이브러리](decisions/frontend-back-handler.md) (제안)

## 도구 조사·검증 기록

- [Jira MCP 사용·검증 기록](knowledge/tooling/jira-mcp.md)

- [서버 인프라 구성의 근거와 제약](knowledge/infra/server-configuration.md)
- [Mattermost 서버 점검 중계의 선택과 제약](knowledge/infra/mattermost-server-inspection.md)
- [MyBatis·Flyway 초기 구성과 검증](knowledge/backend/mybatis-flyway-bootstrap.md)
- [리프레시 토큰·이메일 인증번호 저장 방식](knowledge/backend/auth-token-storage.md)

## 프론트엔드 조사·검증 기록

- [Compose Web의 한글·율명 글꼴](knowledge/frontend/web-korean-fonts.md)
- [Android 휴대폰 세로 고정과 태블릿 회전](knowledge/frontend/android-orientation-lock.md)

## 도메인 조사 기록

- [관악기 '기초부터 배우기' 단계와 지도 근거](knowledge/domain/wind-beginner-curriculum.md)

## 데이터 조사·검증 기록

- [산조대금 라벨·연주 자료와 검수 근거](knowledge/data/sanjo-daegeum.md)
- [정악대금 라벨·연주 자료와 검수 근거](knowledge/data/jeongak-daegeum.md)
- [소금 라벨·연주 자료와 검수 근거](knowledge/data/sogeum.md)
- [단소 라벨·연주 자료와 판단 근거](knowledge/data/danso.md)
- [정간보 OMR로 악보 PDF를 JSON으로 바꾸는 시험](knowledge/data/jeongganbo-omr.md)
- [단소 정간보 PDF의 우물사이 문서 변환](knowledge/data/danso-scores-umulsai.md)
- [정간보 OMR 대조 결과와 율명 인식 개선](knowledge/data/jeongganbo-omr-accuracy.md)
- [소금 정간보 우물사이 문서의 검토 화면과 대조 결과](knowledge/data/sogeum-scores-review.md)
- [단소 1차 AI 평가 4곡 채점 정답 검수](knowledge/data/danso-scoring-answers.md)
- [악보 MIDI·CAT 재생 엔진·단소 표현 초기값과 근거](knowledge/data/score-instrument-playback-pipeline.md)

## AI 실험 기록

- [음높이 추정 방법 비교: 합성 입력](knowledge/ai/pitch-estimator-comparison.md)
- [녹음 길이별 분석 처리 시간과 메모리: 합성 입력](knowledge/ai/analysis-latency.md)

## COCO 산출물

- [S15P31E102-196 채점 품질 통계 대시보드 스펙](specs/scoring-quality-dashboard.md)
- [S15P31E102-76 Jenkins 컨테이너 전환과 기존 빌드·배포 보존 검증](plans/S15P31E102-76.md)

- [S15P31E102-60 백엔드 기본 구조 구성](plans/S15P31E102-60.md)
- [S15P31E102-65 서버 인프라 설정 파일 관리](plans/S15P31E102-65.md)
- [S15P31E102-71 프로젝트명·패키지·앱 식별자 변경](plans/S15P31E102-71.md)
- [S15P31E102-73 프론트 웹 자동 빌드·테스트·배포](plans/S15P31E102-73.md)
- [S15P31E102-74 ERD 기반 MyBatis·Flyway 초기 구조](plans/S15P31E102-74.md)
- [S15P31E102-75 HTTP 요청의 HTTPS 자동 전환](plans/S15P31E102-75.md)
- [S15P31E102-95 Mattermost 서버 점검 명령·HTTPS 중계 배포](plans/S15P31E102-95.md)
- [S15P31E102-128 기술 블로그 GitHub Pages 자동 배포 설정](plans/S15P31E102-128.md)
- [S15P31E102-154 단소 AI 평가 4곡 채점 정답 검수](plans/S15P31E102-154.md)
- [S15P31E102-137 기술 블로그 develop 분리(blog 브랜치·이미지 저장소)](plans/S15P31E102-137.md)
- [S15P31E102-142 1차 MVP ERD 보완과 V2 마이그레이션](plans/S15P31E102-142.md)
- [S15P31E102-168 녹음 파일 규격(PRA-02) 구현 계획](plans/S15P31E102-168.md)
- [S15P31E102-180 녹음 파일 ffprobe 검사기와 ffmpeg 실행 환경](plans/S15P31E102-180.md)
- [S15P31E102-182 녹음 업로드 만료 세션 정리 배치](plans/S15P31E102-182.md)
- [S15P31E102-189 악보 화면 체류 기록 저장(GRO-04)](plans/S15P31E102-189.md)
- [S15P31E102-190 일 마트 악기별 총 연습 시간 칸](plans/S15P31E102-190.md)
- [S15P31E102-169 연습 시간 집계 기준 스펙](plans/S15P31E102-169.md)
- [S15P31E102-167 분석 실패 사유 코드와 화면 안내 명세](plans/S15P31E102-167.md)

COCO 스킬이 만드는 문서도 위 위치를 따릅니다.

- 스펙은 `docs/specs`에 둡니다. 확정된 공통 계약은 `docs/contracts`에 두고 스펙에서 링크합니다.
- 작업 계획이 필요하면 Task마다 `docs/plans/<Jira Key>.md` 하나에 작업 목록·진행 상태·결정 로그를 함께 둡니다. 로컬 티켓 파일이나 별도 로그 파일은 만들지 않습니다.
- ADR은 `docs/decisions`의 결정 문서 형식을 따르고 상태를 반드시 적습니다. 기각한 대안은 관련 결정 문서에 이유와 함께 남기고, 독립적으로 기각한 결정은 상태를 `기각`으로 둡니다.
- 품질 기준·필수 검사는 각 구현 폴더의 `README.md`에 두고 `CONSTRAINTS.md` 같은 별도 정책 파일을 만들지 않습니다.
- `coco-askme` 요약, `coco-document` 학습 모드 결과 같은 개인 학습 기록은 루트 `TIL/`에 둡니다.

## 코드 변경이 없는 작업

다른 팀원이 같은 조사를 반복하지 않아도 되거나, 구현·데이터 해석·기술 선택에 영향을 주는 결과가 생겼을 때 문서를 남깁니다. 공부했다는 기록만을 위한 문서는 `docs`에 만들지 않고 루트 `TIL/`에 둡니다.

| 작업 결과 | 남길 내용 |
| --- | --- |
| 조율 기준·율명·정간보 표현 조사 | 핵심 정의, 출처, 앱·데이터·AI에서 주의할 점, 미확인 사항 |
| 수집처·라이브러리 비교 | 접근·사용 조건, 비교 기준, 확인한 제약, 검토 결론 |
| 음높이 추정·채점·성능 PoC | 입력·버전·환경·설정·재현 명령, 결과, 한계 |
| 기술·계약 선택 | 선택한 내용, 이유, 영향받는 파트, 결정 상태와 근거 |

이 표는 기록 기준이며 이미 수행된 조사나 확정된 결정을 뜻하지 않습니다. 같은 주제는 기존 문서에 이어 쓰고, 여러 파트에서 쓰는 내용은 하나의 문서를 링크합니다.

## 조사와 결정의 구분

조사 문서는 핵심 결론, 출처, 프로젝트 적용점, 남은 질문을 중심으로 자유롭게 씁니다. 외부 자료의 주장과 직접 실험한 결과는 구분합니다. 대용량 산출물은 저장 위치·버전을 링크합니다.

결정 문서는 상태(`제안`·`확정`·`대체됨`·`기각`), 선택, 근거, 영향 범위를 남깁니다. `확정`에는 누가 언제 합의했는지 확인할 수 있는 근거를 붙입니다. 검토만 끝난 문서를 팀의 확정 사항으로 표시하지 않습니다. 사소한 구현 선택마다 별도 결정 문서를 만들 필요는 없습니다.

기존 업무에 필요한 조사·문서는 같은 Task에 포함합니다. 독립적인 조사·문서 업무는 [루트 지침의 Jira 연결 절차](../AGENTS.md)를 따릅니다. 기록을 남기는 데 코드 변경이나 Jira 댓글은 필수 조건이 아닙니다. 커밋·MR·Notion 게시 등 팀에 전달하는 작업은 현재 요청 범위에 따라 진행합니다.
