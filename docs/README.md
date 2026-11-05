# 프로젝트 문서

- [서비스 장단 카탈로그 확장 스펙](specs/jangdan-catalog-extension.md) · [추가 근거·시작 빠르기·검증](knowledge/backend/jangdan-catalog-extension.md)
- [장단 음원·곡별 검수 자료 전달과 재생 공통 스펙 (160·340)](specs/jangdan-practice-playback.md)
- [장단 음원 생성·출처·빠르기 조사 (160)](knowledge/data/jangdan-preview-audio.md) · [107개 곡별 검수 결과 (160)](knowledge/data/jangdan-song-review.md)

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
| Jenkins 실패 Mattermost 알림·짧은 로그·복구 | [운영 문서](../infra/jenkins/notifications/README.md) · [스펙](specs/jenkins-failure-notifications.md) · [진단 근거](knowledge/infra/jenkins-failure-notifications.md) · [509 실행 기록](plans/S15P31E102-509.md) |
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

- [S15P31E102-257 평가 결과 조회](plans/S15P31E102-257.md)

## 기능 스펙

- [한 음씩 연습 판정 기준 조회 (493)](specs/note-practice-criteria.md) · [계획](plans/S15P31E102-493.md)
- [V25 삭제 전 호환 백엔드 릴리스](specs/mission-migration-compat.md) · [499 계획·검증](plans/S15P31E102-499.md) · [배포 순서와 제약](knowledge/infra/mission-migration-compat.md)

- [휴대폰 연습 화면의 짧은 각 묶음](specs/practice-short-score-stacks.md) · [473 계획·검증](plans/S15P31E102-473.md)
- [연습 악보 높이 테스트의 JS 부동소수점 비교 수정 (497)](plans/S15P31E102-497.md)

- [계정 전환과 개인 데이터 격리 (452)](specs/account-session-isolation.md) · [구현 전후 수치·검증 기록](knowledge/frontend/account-session-isolation.md)
- [화면 개선 적용 범위·담당 API 제외 목록](knowledge/frontend/screen-improvement-scope.md)

- [4악기 내장 운지 카탈로그 연결 (409)](specs/builtin-fingering-catalog.md) · [구현·검증 기록](knowledge/backend/builtin-fingering-catalog.md)
- [운지법 화면 음원 목록 로딩 개선 (423)](specs/guide-audio-loading.md) · [원인·검증 기록](knowledge/frontend/guide-audio-loading.md)
- [HOME 전체 그림·스크롤·먹 버튼](specs/home-scroll-ink.md) · [412 계획·검증](plans/S15P31E102-412.md) · [428 카드 배치 개편](plans/S15P31E102-428.md) · [437 상단 바·2x3 카드](plans/S15P31E102-437.md)

- [R2 공통 파일·폴더 업로드](specs/r2-upload.md) · [392 계획·검증](plans/S15P31E102-392.md) · [실행 안내](../infra/r2/README.md)
- [운영 DB V15·V16 적용과 모범 음원 audio_url 삭제 (395)](knowledge/infra/model-audio-ops-migration.md)
- [기본음·시김새 운영 게시](specs/reference-audio-publication.md) · [368 계획](plans/S15P31E102-368.md) · [배포 통합·게시 기록](knowledge/infra/reference-audio-publication.md)

- [기본음·시김새 음원 카탈로그 DB/API](specs/reference-audio-catalog.md) · [검증 기록](knowledge/backend/reference-audio-catalog.md) · [364 관계 보완 계획](plans/S15P31E102-364.md)

- [탈퇴 유예 만료와 삭제 경계](specs/withdrawal-expiry.md)

- [악기·악보·모범 음원·학습 카탈로그 조회 API](specs/catalog-read-apis.md)
- [곡 목록 악기 필터](specs/song-instrument-filter.md) · [구현·검증 기록](knowledge/backend/song-instrument-filter.md)
- [카탈로그 공통 악보 원본과 가사 복원](specs/common-score-catalog-source.md) · [JGB 대조 및 복원 기록](knowledge/data/catalog-notation-restoration.md)
- [공통 악보·모범 음원 적재와 재생 연결](specs/model-audio-catalog-load.md) · [객체 키 전환·재생 검증](knowledge/backend/model-audio-publication.md)
- [카탈로그 API 구현·검증 기록](knowledge/backend/catalog-api-verification.md)
- [장단 음원 API와 현재 악보 기준 반주 계산 (BE491)](knowledge/backend/jangdan-practice-links.md)
- [4악기 카탈로그·회원 선택 정렬](specs/four-instrument-catalog.md) · [407 검증](knowledge/backend/four-instrument-catalog.md)
- [4악기 카탈로그 운영 V17 적용](knowledge/infra/four-instrument-ops-migration.md)

- [연주 평가 결과 조회 API](specs/practice-evaluation-api.md)
- [오류 음표·취약 구간·구간 재평가 비교 API (EVA-02·03·04)](specs/evaluation-detail-apis.md) · [307 계획](plans/S15P31E102-307.md) · [310 계획](plans/S15P31E102-310.md) · [502 구간 점수 계획](plans/S15P31E102-502.md) · [494 EVA-04 2차 개편](plans/S15P31E102-494.md) · [480 인식 음·녹음 위치](plans/S15P31E102-480.md)
- [실제 연습을 위한 곡·평가 조회 API](specs/practice-read-apis.md)
- [연습 기록 저장·목록·녹음 재생·삭제 API (REC-01~04)](specs/practice-records.md) · [342 계획](plans/S15P31E102-342.md)
- [내 악보 생성·조회·목록 API (SHT-01·03·08)](specs/user-sheets.md) · [390 계획](plans/S15P31E102-390.md)
- [내 악보 음원 생성·재생 앱 흐름 (SHT-12·13 FE)](specs/sheet-audio-app-flow.md) · [484 계획](plans/S15P31E102-484.md)
- [악보 인식 서비스 계약 (SHT-07 내부, omr)](specs/score-recognition.md) · [변환 가능성 조사](knowledge/ai/score-omr-feasibility.md)
- [저장한 기록의 녹음 보관과 미저장 녹음 30일 정리](specs/recording-retention.md) · [375 계획](plans/S15P31E102-375.md)
- [기초부터 배우기 학습 단계·진도 API (LRN-01·02·03)](specs/learning-progress-apis.md) · [344 계획](plans/S15P31E102-344.md)

- [최신 운영 구성의 수동 서버 점검](specs/server-inspection-current-runtime.md)
- [운영 테스트 계정의 인증 만료 예외](specs/non-expiring-test-account.md)

- [구현된 업무 API Swagger 문서화](specs/api-documentation.md)
- [간편 로그인과 계정 연결](specs/social-auth.md)
- [내 정보 비밀번호 보유 여부 계약·검증](plans/S15P31E102-385.md)
- [카카오 서명형 연결 해제 웹훅](specs/kakao-webhook.md)

- [채점 품질 통계 대시보드](specs/scoring-quality-dashboard.md)
- [녹음 파일 규격 (PRA-02)](specs/recording-file.md)
- [녹음 미지원 환경의 평가 시작 제한](specs/recording-support-gate.md)
- [S15P31E102-373 iOS 녹음·재생과 App Store 출시 설정](plans/S15P31E102-373.md)
- [연습 시간 집계 기준 (GRO-01·02·04)](specs/practice-time.md)
- [연주 분석 실패 사유와 화면 안내 (PRA-03)](specs/practice-analysis-failure.md)
- [모니터링 Metabase 제거와 Prometheus 도입](specs/monitoring-prometheus.md)
- [백엔드 성능 대시보드 p95와 서버 자원](specs/monitoring-p95-node.md)

## 결정 기록

- [프론트엔드 화면 이동·DI 라이브러리](decisions/frontend-navigation-di.md) (제안)
- [프론트엔드 기기 뒤로 가기 처리 라이브러리](decisions/frontend-back-handler.md) (제안)
- [프론트엔드 HTTP 클라이언트와 로그인 쿠키 보관](decisions/frontend-http-client.md) (확정)

- [S15P31E102-256 곡·악보 조회](plans/S15P31E102-256.md)
- [S15P31E102-345 곡 목록 서버 검색·페이지 보류와 재개 조건](plans/S15P31E102-345.md)

## 도구 조사·검증 기록

- [정간보 기호 사전 연결 계약](specs/notation-symbol-catalog.md) · [자료 대조와 검증 기록](knowledge/backend/notation-symbol-catalog.md)

- [Jira MCP 사용·검증 기록](knowledge/tooling/jira-mcp.md)
- [Notion API 명세 행 생성의 제목 필드](knowledge/tooling/notion-mcp.md)
- [Notion 테스트 시나리오 작성 프롬프트](knowledge/tooling/notion-test-scenario-prompt.md)
- [서버 인프라 구성의 근거와 제약](knowledge/infra/server-configuration.md)
- [운영 Flyway 마이그레이션 자동화와 버전 중복 검사 (426)](knowledge/infra/ops-flyway-auto-migration.md)
- [R2 서명 URL·비공개 접근·30일 삭제 규칙·CORS 정책 검증](knowledge/infra/r2-storage-verification.md)
- [운영 DB·설정 백업 현황과 보관 권고](knowledge/infra/backup-restore-assessment.md)
- [Mattermost 서버 점검 중계의 선택과 제약](knowledge/infra/mattermost-server-inspection.md)
- [MyBatis·Flyway 초기 구성과 검증](knowledge/backend/mybatis-flyway-bootstrap.md)
- [평가 조율 정보 저장·실제 AI 연동 검증](knowledge/backend/evaluation-tuning-verification.md)
- [리프레시 토큰·이메일 인증번호 저장 방식](knowledge/backend/auth-token-storage.md)
- [운영 AI 장애·복구 후 같은 세션 재시도 검증](knowledge/backend/analysis-fault-retry-verification.md)

## 백엔드 조사·검증 기록

- [화면 흐름 개선 검토 결과와 내 악보 후속 조건 (434)](knowledge/backend/screen-flow-improvement-integrated.md)

## 프론트엔드 조사·검증 기록

- [정간보 쉽게 읽기의 박·음높이 듣기 (439)](plans/S15P31E102-439.md)

- [마이페이지 프로필·학습 진도·계정 UI 개편 (438)](plans/S15P31E102-438.md)
- [연습곡 선택 화면 단계 필터 (436)](plans/S15P31E102-436.md)
- [기본음·시김새 녹음 선택과 앱 내부 재생](knowledge/frontend/reference-audio-playback.md) · [367 계획](plans/S15P31E102-367.md)

- [CAT03~10·녹음/평가 앱 통합과 MR 검증](knowledge/frontend/app-catalog-api-integration.md)
- [율명·운지·장단·기호 카탈로그 앱 연결](knowledge/frontend/guide-catalog-integration.md)
- [공개 곡·악기 조회와 공통 악보 앱 변환·검증](knowledge/frontend/song-catalog-adapter.md)
- [회원 악기 선택·인증 실패 복구와 곡 목록 자동 연결](knowledge/frontend/member-instrument-selection.md)
- [모범 연주 API와 Android 실제 재생·중단·위치 연결](knowledge/frontend/model-audio-playback.md)
- [Android 연두 디자인·회원 API와 Play release 준비·검증 한계](knowledge/frontend/android-play-release-readiness.md)
- [Compose Web의 한글·율명 글꼴](knowledge/frontend/web-korean-fonts.md)
- [Android 휴대폰 세로 고정과 태블릿 회전](knowledge/frontend/android-orientation-lock.md)
- [Android 간편 로그인: 외부 브라우저와 App Links 복귀](knowledge/frontend/android-social-auth-app-links.md)

## 도메인 조사 기록

- [관악기 '기초부터 배우기' 단계와 지도 근거](knowledge/domain/wind-beginner-curriculum.md)
- [논문·공식 자료와 설계 결정의 근거 표](knowledge/domain/design-evidence-map.md)
- [정간보 시김새 기호의 뜻과 출처](knowledge/domain/sigimsae-symbols.md)

## 데이터 조사·검증 기록

- [기종 미표기 대금 악보의 출처별 분류·황응남임 편집 교정 근거](knowledge/data/common/daegeum-score-types.md)
- [공통 악보 v1 재생 연결 스펙](specs/common-score-playback.md) · [S15P31E102-338 실행·검증 기록](plans/S15P31E102-338.md)
- [공통 악보 음원의 독립 파형 검증](knowledge/data/common-score-audio-verification.md)
- [악기별 시김새 교육용 미리듣기와 표현 시간표](knowledge/data/common-score-expression-preview.md)
- [라벨링 기본음·시김새 M4A 생성·검증](knowledge/data/reference-audio-m4a.md)

- [Android 운영 자료·단소 M4A 후보와 앱 악보 대조](knowledge/data/android-catalog-audio-readiness.md)
- [악기별 악보 분류와 추가 확보](knowledge/data/score-collection.md) · [계획](plans/S15P31E102-111.md)
- [산조대금 라벨·연주 자료와 검수 근거](knowledge/data/sanjo-daegeum.md)
- [산조대금 JGB·MIDI·M4A 변환과 CAT 공통 샘플 확인](knowledge/data/sanjo-playback.md)
- [정악대금 라벨·연주 자료와 검수 근거](knowledge/data/jeongak-daegeum.md)
- [정악대금 JGB·M4A 연결과 CAT 대금 샘플 비교](knowledge/data/jeongak-playback.md)
- [소금 라벨·연주 자료와 검수 근거](knowledge/data/sogeum.md)
- [소금 JGB·MIDI·M4A 생성과 실음·표현 정책](knowledge/data/sogeum-playback.md)
- [단소 라벨·연주 자료와 판단 근거](knowledge/data/danso.md)
- [정간보 OMR로 악보 PDF를 JSON으로 바꾸는 시험](knowledge/data/jeongganbo-omr.md)
- [단소 정간보 PDF의 우물사이 문서 변환](knowledge/data/danso-scores-umulsai.md)
- [정간보 OMR 대조 결과와 율명 인식 개선](knowledge/data/jeongganbo-omr-accuracy.md)
- [소금 정간보 우물사이 문서의 검토 화면과 대조 결과](knowledge/data/sogeum-scores-review.md)
- [단소 1차 AI 평가 4곡 채점 정답 검수](knowledge/data/danso-scoring-answers.md)
- [단소 4곡 공통 악보와 V6 시드 일치 검수](knowledge/data/danso-seed-consistency-audit.md)
- [소금 서비스 4곡 채점 정답 검수와 채점 켜기](knowledge/data/sogeum-service-scores.md)
- [단소 339 적재곡 채점 정답 검수와 채점 켜기 계획](knowledge/data/danso-loaded-scores.md) · [404 계획](plans/S15P31E102-404.md)
- [악보 MIDI·CAT 재생 엔진·단소 표현 초기값과 근거](knowledge/data/score-instrument-playback-pipeline.md)
- [단소 JGB 표현·빠르기 해석과 M4A 출력 근거](knowledge/data/danso-expanded-auditions.md) · [구현 스펙](specs/danso-playback-expansion.md) · [자동 테스트별 확인 범위](../data/src/playback/tests/README.md)
- [대금·단소·소금 악보의 기준음·박·빠르기·시김새 근거](knowledge/data/score-reference-standards.md) · [계획](plans/S15P31E102-129.md)

## AI 실험 기록

- [단소 채점 오류 검출 평가: 실행과 결과 해석](knowledge/ai/scoring-error-evaluation.md)
- [짧은 옥타브 위 음 채점 오류: 원인과 수정](knowledge/ai/short-octave-error.md)
- [입문자 정상 연주 채점: 박자 판정과 소금 정답](knowledge/ai/beginner-normal-performance.md)
- [일부러 틀린 실제 녹음 요청서 (아리랑, 단소·소금, 보류)](knowledge/ai/real-error-recording-request.md)
- [DTW 메모리 절감 비교: 기준 관찰과 연구용 후보](knowledge/ai/dtw-memory-comparison.md)

- [음높이 추정 방법 비교: 합성 입력](knowledge/ai/pitch-estimator-comparison.md)
- [녹음 길이별 분석 처리 시간과 메모리: 합성 입력](knowledge/ai/analysis-latency.md)
- [채점 입력과 기준음·조율 보정 상한](knowledge/ai/scoring-input-and-tuning.md)
- [단소 단음 녹음의 조율 흔들림: 김 세기·취법별 보정량](knowledge/ai/danso-tuning-variation.md)
- [정간보·오선보 이미지·PDF의 공통 악보 변환 가능성 조사](knowledge/ai/score-omr-feasibility.md)

- [S15P31E102-471 내 악보 불러오기·원본 보관·변환 상태](plans/S15P31E102-471.md)

- [S15P31E102-472 인식 작업자·결과 저장·재시도](plans/S15P31E102-472.md) · [시간 제한·사진 방향·재시도 근거](knowledge/backend/sheet-conversion-worker.md)

## COCO 산출물

- [S15P31E102-306 탈퇴 유예 만료 후 취소 차단과 녹음 삭제 경합 수정](plans/S15P31E102-306.md)

- [S15P31E102-261 Android 디자인·release 검증 및 제출 준비](plans/S15P31E102-261.md) (직접 구현 작업)
- [S15P31E102-178 서버 녹음·분석 실패 복구 검증](plans/S15P31E102-178.md)
- [S15P31E102-60 백엔드 기본 구조 구성](plans/S15P31E102-60.md)
- [S15P31E102-65 서버 인프라 설정 파일 관리](plans/S15P31E102-65.md)
- [S15P31E102-71 프로젝트명·패키지·앱 식별자 변경](plans/S15P31E102-71.md)
- [S15P31E102-73 프론트 웹 자동 빌드·테스트·배포](plans/S15P31E102-73.md)
- [S15P31E102-74 ERD 기반 MyBatis·Flyway 초기 구조](plans/S15P31E102-74.md)
- [S15P31E102-75 HTTP 요청의 HTTPS 자동 전환](plans/S15P31E102-75.md)
- [S15P31E102-76 Jenkins 컨테이너 전환과 기존 빌드·배포 보존 검증](plans/S15P31E102-76.md)
- [S15P31E102-95 Mattermost 서버 점검 명령·HTTPS 중계 배포](plans/S15P31E102-95.md)
- [S15P31E102-112 운영 DB·설정 백업과 복구 검증](plans/S15P31E102-112.md)
- [S15P31E102-293 edo2 백업 복구 실패와 실험 중지 오탐 수정](plans/S15P31E102-293.md)
- [S15P31E102-294 edo2 Android 실험 자원과 점검 예외 제거](plans/S15P31E102-294.md)
- [S15P31E102-128 기술 블로그 GitHub Pages 자동 배포 설정](plans/S15P31E102-128.md)
- [S15P31E102-133 edo2 Mattermost 서버 점검](plans/S15P31E102-133.md)
- [S15P31E102-137 기술 블로그 develop 분리(blog 브랜치·이미지 저장소)](plans/S15P31E102-137.md)
- [S15P31E102-142 1차 MVP ERD 보완과 V2 마이그레이션](plans/S15P31E102-142.md)
- [S15P31E102-143 이메일·소셜 로그인과 회원 정보 API](plans/S15P31E102-143.md)
- [S15P31E102-154 단소 AI 평가 4곡 채점 정답 검수](plans/S15P31E102-154.md)
- [S15P31E102-155 단소 기준 데이터 표 작성](plans/S15P31E102-155.md)
- [S15P31E102-156 정간보 jgb를 공통 악보 데이터로 바꾸는 변환기](plans/S15P31E102-156.md)
- [S15P31E102-157 채점 입력을 공통 악보 데이터와 기준음 E♭4로 변경](plans/S15P31E102-157.md)
- [S15P31E102-167 분석 실패 사유 코드와 화면 안내 명세](plans/S15P31E102-167.md)
- [S15P31E102-168 녹음 파일 규격(PRA-02) 구현 계획](plans/S15P31E102-168.md)
- [S15P31E102-169 연습 시간 집계 기준 스펙](plans/S15P31E102-169.md)
- [S15P31E102-173 분석 시도 저장 방식과 V4 마이그레이션](plans/S15P31E102-173.md)
- [S15P31E102-180 녹음 파일 ffprobe 검사기와 ffmpeg 실행 환경](plans/S15P31E102-180.md)
- [S15P31E102-181 연습 녹음 업로드 API](plans/S15P31E102-181.md)
- [S15P31E102-182 녹음 업로드 만료 세션 정리 배치](plans/S15P31E102-182.md)
- [S15P31E102-189 악보 화면 체류 기록 저장(GRO-04)](plans/S15P31E102-189.md)
- [S15P31E102-190 일 마트 악기별 총 연습 시간 칸](plans/S15P31E102-190.md)
- [S15P31E102-193 기간별 연습 시간 합산 조회(GRO-01·02)](plans/S15P31E102-193.md)
- [S15P31E102-197 edo1 Jenkins·운영 이미지에 녹음 검사용 ffmpeg 반영](plans/S15P31E102-197.md)
- [S15P31E102-206 Android 연주 녹음·마이크 권한과 10분 자동 정지](plans/S15P31E102-206.md)
- [S15P31E102-207 이메일 가입·로그인과 토큰 재발급·로그아웃 API](plans/S15P31E102-207.md)
- [S15P31E102-208 내 정보 조회·수정과 비밀번호 변경 API](plans/S15P31E102-208.md)
- [S15P31E102-209 회원 탈퇴와 30일 뒤 일괄 삭제](plans/S15P31E102-209.md)
- [S15P31E102-210 비밀번호 재설정 API 구현](plans/S15P31E102-210.md)
- [S15P31E102-253 구현된 API Swagger 문서화와 운영 조회](plans/S15P31E102-253.md)
- [S15P31E102-211 간편 로그인·계정 연결과 마이페이지 연결·해제](plans/S15P31E102-211.md)
- [S15P31E102-296 카카오 연결 해제 웹훅](plans/S15P31E102-296.md)
- [S15P31E102-311 Google 간편 로그인 콘솔·서버 연동 준비](plans/S15P31E102-311.md)
- [S15P31E102-260 운영 테스트 계정과 인증 만료 예외](plans/S15P31E102-260.md)
- [S15P31E102-219 연습 기록지 요약의 곡별 최고점·항목별 성장](plans/S15P31E102-219.md)
- [S15P31E102-217 연습 세션 생성 API(PRA-01)](plans/S15P31E102-217.md)
- [S15P31E102-342 연습 기록 저장·목록·녹음 재생·삭제 API(REC-01~04)](plans/S15P31E102-342.md)
- [S15P31E102-375 저장한 기록의 녹음 계속 보관과 미저장 녹음 30일 삭제](plans/S15P31E102-375.md)
- [S15P31E102-221 운영 backend에서 업무 API 활성화](plans/S15P31E102-221.md)
- [S15P31E102-229 자동 배포 검사를 health 경로로](plans/S15P31E102-229.md)
- [S15P31E102-242 채점 오류 검출 평가 도구와 단소 단음 조율 흔들림 측정](plans/S15P31E102-242.md)
- [S15P31E102-213 AI 분석 서비스 실행 이미지와 ffmpeg 버전 고정](plans/S15P31E102-213.md)
- [S15P31E102-228 AI 분석 서비스 서버 배포와 백엔드 AI 주소 연결](plans/S15P31E102-228.md)
- [S15P31E102-249 단소 4곡과 악기·장단 기준 데이터 DB 적재](plans/S15P31E102-249.md)
- [S15P31E102-281 단소 4곡 공통 악보와 운영 V6 시드 일치 검수](plans/S15P31E102-281.md)

- [S15P31E102-272 R2 접근·보관 정책 검증](plans/S15P31E102-272.md)
- [S15P31E102-263 마이페이지 내 정보 응답에 이메일 추가](plans/S15P31E102-263.md)

COCO 스킬이 만드는 문서도 위 위치를 따릅니다.

- 스펙은 `docs/specs`에 둡니다. 확정된 공통 계약은 `docs/contracts`에 두고 스펙에서 링크합니다.
- 작업 계획이 필요하면 Task마다 `docs/plans/<Jira Key>.md` 하나에 작업 목록·진행 상태·결정 로그를 함께 둡니다. 로컬 티켓 파일이나 별도 로그 파일은 만들지 않습니다.
- ADR은 `docs/decisions`의 결정 문서 형식을 따르고 상태를 반드시 적습니다. 기각한 대안은 관련 결정 문서에 이유와 함께 남기고, 독립적으로 기각한 결정은 상태를 `기각`으로 둡니다.
- 품질 기준·필수 검사는 각 구현 폴더의 `README.md`에 두고 `CONSTRAINTS.md` 같은 별도 정책 파일을 만들지 않습니다.
- `coco-askme` 요약, `coco-learn` 학습 노트 같은 개인 학습 기록은 루트 `TIL/`에 둡니다.

- [S15P31E102-282 긴 녹음·분석 제한 판단 근거 갱신](plans/S15P31E102-282.md)

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

- [S15P31E102-262 Jenkins 단계와 배포 순서](plans/S15P31E102-262.md) · [스펙](specs/jenkins-deploy-order.md)

- [Android QA 약관 임시 우회](specs/android-qa-consent-bypass.md) · [S15P31E102-309 검증·제거 안내](plans/S15P31E102-309.md)

- [Android QA 실제 API 검증 결과와 미제공 콘텐츠](knowledge/frontend/android-qa-api-verification.md)
- [공개 약관과 삭제 안내 준비](knowledge/backend/terms-publication.md)
- [약관 공개·동의·출시 검증](knowledge/backend/terms-release-verification.md)
- [S15P31E102-300 약관 제공 준비](plans/S15P31E102-300.md)
- [공개 약관 및 녹음 저장 계약(R2 복귀 변경 포함)](specs/terms-release.md)
- [S15P31E102-333 녹음 저장소 R2 복귀와 국외 이전 고지](plans/S15P31E102-333.md)
- [녹음 저장 이관(국내 디스크·R2 복귀)·Naver 메일 준비](../infra/storage/README.md)
- [약관 본문 공개와 버전 등록](../infra/terms/README.md)
- [개인정보 로그·문의·감사 보유기간 운영](../infra/privacy/README.md)

- [S15P31E102-314 네이버 간편 로그인 연동 준비](plans/S15P31E102-314.md)

- [S15P31E102-337 QA 앱 App Links 수정·검증](plans/S15P31E102-337.md)

- [S15P31E102-356 OAuth 신규 가입·소셜 탈퇴](plans/S15P31E102-356.md) · [API 계약](specs/social-sign-up.md)

- [S15P31E102-377 Mac 무료 개발 서명과 iPhone API 테스트 실행 환경](plans/S15P31E102-377.md)
- [S15P31E102-346 비교 가능한 최근 점수 추이 조회(GRO-03)](plans/S15P31E102-346.md) · [API 계약](specs/growth-score-trend.md)
- [S15P31E102-429 마이페이지 테스터 의견 보내기](plans/S15P31E102-429.md)
- [API 명세서 68페이지 코드 정합성·내용 품질 검토](knowledge/backend/api-spec-quality-review.md) · [S15P31E102-379 계획](plans/S15P31E102-379.md)
