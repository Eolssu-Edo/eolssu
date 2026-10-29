# Android release 준비와 검증 한계

- 조사·변경일: 2026-10-29. Task [S15P31E102-261](https://ssafy.atlassian.net/browse/S15P31E102-261), Story 109 관련 연결, 활성 스프린트 59139, 진행 중.
- 기준: `origin/develop` `50afef0485533059c4b15e4fa68409dc77535c8c`와 미커밋 변경. 최초 versionCode2 증거의 기준은 a4219b4이며 별도 보존한다. 직접 구현했으며 커밋·Push·MR·Play 업로드·제출은 하지 않았다.
- 최신 계획·기능/검증 표: [Task 계획](../../plans/S15P31E102-261.md). 로컬 결과 묶음은 작업 worktree의 `.scratch/release-catalog-20261029/README.md` (versionCode3 후속 묶음), 최초 묶음은 `.scratch/release-20261029/README.md`에 있다. 이 경로는 Git 제외 대상이며 worktree 정리 전에 별도로 보존해야 한다.

## 결론

앱 이름·런처·공통 연두 테마·글꼴·홈과 인증 화면을 보완하고 실제 회원 API를 연결했다. 서명 release APK/AAB와 AAB 유래 패키지의 PC/에뮬레이터 검증을 준비했지만 **Play 제출 준비는 미완료**다. 운영 약관의 빈 목록, 곡·악기 API의 운영 배포 미확인, 평가·이력·PDF 대역, 계정별 전송 큐와 담당 작업의 미통합이 실제 사용자 흐름을 막는다. 실제 회원 API의 단위 검사 통과는 운영 계정으로 로그인·가입·탈퇴한 증거가 아니다.

현재 키는 새로 만든 **로컬 검증 전용 키**다. 공식 Play upload key 또는 Play app signing key로 승인/등록됐다는 증거가 없다. 공개 인증서 식별자만 결과 묶음에 기록하고, 개인키와 암호는 저장소 밖 Windows 사용자 전용 보관소에 둔다. 제출용 키와 Console의 사용 버전 확인 후 승인된 키·새 버전으로 다시 빌드해야 한다.

이번 PC에 연결 가능한 Android 휴대폰이 없어 실기기 검사는 전부 미검증이다. 이는 사용자가 정한 필수 품질 검사 한계이며, 그 사실만으로 Google의 정책 위반이나 모든 앱에 적용되는 별도 실기기 제출 의무라고 단정하지 않는다. Console 계정별 조건은 별도로 미확인이다.

## 입력과 적용

| 입력 | 확인·역할 |
| --- | --- |
| 최신 연두 HTML | SHA256 `670617dca12ac5b2faec65f6a36add4cd6c4f3e9c44a754c08d83d9a3ecd2e1b`, 3,121,045 bytes, 2026-10-29 11:59:10 KST. 117개 보드의 시각 기준 |
| 구버전 태블릿 ZIP | SHA256 `07883370a6451e1682972f913fa9005360641400f25df432ffc989e5e3883904`, 7,247,022 bytes, 11:17:16 KST. 111개 선언의 영역·배치만 참고, 덮어쓰기 반영 |
| 제공 이미지 | PNG 6개·GIF 2개 원본 보존. 모음판 전체를 앱 아이콘으로 쓰지 않음 |
| 데이터·동작 | 저장소 specs/contracts와 실제 서버 DTO/Controller. HTML 고정 점수·예시 악보를 출시 데이터로 채택하지 않음 |

HTML 원문·내장 이미지·CSS는 읽었으나 브라우저의 `file://` 실행 접근이 차단되어 원본 HTML 렌더링/클릭은 미검증이다. 우회 서버나 다른 브라우저로 실행하지 않았다. 실제 Android 화면 캡처는 별도 증거다.

공통 리소스는 `shared/src/commonMain/composeResources`, 런처는 Android 리소스에 배치했다. 색은 연두 `#ADBE31`, 바탕 `#F9FAF9`, 먹색 `#27221E`, 홈 그림 바탕 `#EFF2D6`이다. 본문은 IBM Plex Sans KR, 고정 제목은 Song Myung, 율명은 기존 Noto Serif KR와 합성 글리프 처리를 유지했다. OFL 원문을 앱 리소스에 포함했다. 제공 로고·일러스트·단일 호랑이의 사용권은 팀 확인이 필요하다.

홈은 `WindowLayout`의 기존 600/840dp 경계와 공통 상태를 유지한다. 별도 로고·악기·마이페이지 판, 카드 화살표, 연두 기본 CTA와 누름/hover 먹색 효과를 구현했다. 휴대폰 390×844 프레임이나 가짜 48/34px 안전영역은 이식하지 않았다. Insets/IME와 동적 높이를 적용했으나 모든 화면의 TalkBack·분할 화면·회전 중 녹음 연속성은 검증되지 않았다.

## 실제 회원 연결과 남은 계약

- 이메일 인증·가입·로그인·재설정은 `ApiAuthRepository`, 내 정보·닉네임·비밀번호·탈퇴는 `ApiUserRepository`에서 실제 API로 요청한다. 성공 응답의 필수 결과를 검사하며 실패를 예시 성공으로 바꾸지 않는다.
- 동의는 서버의 term ID·version과 사용자 선택을 전송한다. 기본은 미선택이며 빈/유효하지 않은 약관 목록은 재시도로 남긴다. 가입 임시 비밀값은 메모리에만 둔다. 성공한 로그인은 이전 익명 가입의 동의를 포함한 `AccountDraft` 전체를 지운다.
- 소셜 로그인/계정 연결의 실제 플랫폼 연동은 미완료다. 악기는 활성 목록의 실제 ID/code를 가입·내 정보·preferences와 연결했고 지연 응답의 덮어쓰기를 방지했다. 모르는 code를 단소로 대체하지 않는다.
- 2026-10-29 비인증 공개 조회: `/api/terms` 200 + `items: []`, `/api/instruments` 및 `/api/songs` 404. 서버 기준 주소는 `https://k15e102.p.ssafy.io`다. 계정·토큰·개인 데이터 없이 조회했다.
- 사용자 후속 확인으로 256의 MR !151 병합을 검증하고 실제 SongItem/score 응답으로 연결했다. 서버의 문자열 Long ID, NOTE 전용 index, `c-j-r-u` 식별자와 tick/`bpmUnit`을 보존한다. 누락된 활성 악기 조회는 기존 DB 모델·필터로 261에서 보완했다. [후속 계약·검증](song-catalog-adapter.md)을 참고한다.
- Task 245(우동균)의 세션·녹음 업로드·분석, 250/259/191/238 등 다른 담당 업무는 중복 구현하지 않았다. 병합된 247의 공통 HTTP·쿠키/갱신과 187의 파일 보존, 234의 태블릿 곡 화면 수정은 기반으로 사용했다.

## 검증과 재현

실제 빌드 파일 기준은 JDK 21, Gradle 9.5.1, Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 9.1.1, SDK 37, minSdk 24다. 기존 도구를 확인하고 API24·태블릿 AVD를 추가했다. 다른 프로젝트 SDK·키는 덮어쓰지 않았다. 명령은 [frontend README](../../../frontend/README.md#android-release-검증)에 있다.

최종 결과 묶음에는 다음 원시 증거를 보존한다.

1. `evidence/final-gradle.log`, JUnit·lint 집계와 보고서: 단위 검사·lint·debug/release 빌드 결과.
2. APK/AAB manifest·서명·인증서·bundletool validate·16KB ELF/ZIP 검사. 최종 release는 `debuggable` 기본 false이며 preview 실행 도구는 debug 전용으로 제한한다. launcher는 필요한 exported 진입점을 유지한다.
3. API24 폰, API37 16KB 폰, API37 16KB 태블릿의 설치·시작·화면·설정 및 AAB 유래 설치 기록.
4. `source.patch`와 SHA256, HEAD·dirty 상태·소스 입력 파일 해시. 소스 HEAD만으로 미커밋 변경을 설명하지 않는다.
5. 117보드 추적표, 111태블릿 대응표, 자산 목록, 공식 정책 감사 원본/한계, 스토어/심사 접근 초안과 실기기 18개 절차.

최초 versionCode2의 debug 탐색은 홈·대역 곡·권한 UI 확인이었다. 후속 debug는 `first_run_completed`만 설정하고 격리된 실제 서버의 공개 곡 4개를 조회한다. 실제 로그인·회원 동의·실제 분석 성공의 증거가 아니다. 해당 debug 앱을 제거하고 최종 release를 새 설치했다. 현재 운영 약관이 없어 release의 정상 경로는 동의 오류 화면에서 멈춘다. 파일 입력 분석/호스트 마이크 녹음/실제 휴대폰 녹음 성공으로 보고하지 않는다.

## 공식 Android skill 감사

공식 [android/skills](https://github.com/android/skills/tree/45fd1739a4dbca5452c3e936570797f7f6261cd7) revision `45fd1739a4dbca5452c3e936570797f7f6261cd7`의 play-policy-insights, android-permissions-security, android-intent-security, edge-to-edge, testing-setup, anr-audit-and-fix를 적용했다. 설치 파일과 Apache-2.0 원문은 로컬 `.agents/skills`에 보존하고 해시 목록을 남긴다.

최종 Play 감사 디렉터리는 `.scratch/play_policy_insights_2b53f908-b8f7-46c7-9695-432fd7a6cd1d`다. 정식 init → 5개 worker → aggregate → 2개 critic → report가 완료됐다. **감사 절차 완료는 정책 준수 통과를 뜻하지 않는다.** 도구는 Gradle plugin alias를 해석하지 못해 package를 `frontend`, target을 null로 추정했다. 따라서 `id=frontend`의 Play 404는 실제 `com.ssafy.eolssu`의 미게시 증거가 아니다. 원시 결과를 고치지 않고 실제 산출물과 차이를 한계 문서에 기록했다. Console Data safety 선언과의 대조는 미검증이다.

이후 동의 임시값 초기화·홈 장식·release preview 의존성 제거는 검토 delta로 별도 명시한다. 분석 절차를 실행하지 않은 범위까지 자동 감사 통과로 확장하지 않는다. Recorder의 prepare/start/stop·파일 작업이 메인 스레드 경로에 있는 정적 ANR 위험은 남아 있고, ANR 발생을 실제 관찰했다는 뜻은 아니다.

## 남은 차단과 담당

| 우선순위 | 항목·사용자 영향 | 다음 행동·담당 |
| --- | --- | --- |
| P0 | 운영 약관 비어 신규 설치 인증 진입 불가 | 실제 약관 ID/버전/공개 본문 운영 반영, BE·정책 담당 미정 |
| P0 | 곡·악기 로컬 구현 완료/운영 배포 미확인, 평가·기록 대역으로 핵심 실사용 불가 | BE/배포 담당 미정, FE245 우동균·250/259 권영민과 통합 후 실제 계정 검사 |
| P0 | 공개 개인정보 URL·웹 삭제 경로·심사용 계정·Console 입력 미확인 | 서비스 운영/정책·Play 계정 소유자 미정, 사실 확인 후 제출 자료 확정 |
| P1 | Task191 계정별 연습 큐 격리: A의 미전송 기록이 탈퇴 뒤 B로 전송될 위험 | 권영민, 실패 큐/열린 구간 포함 계정 경계 회귀 검사. 다른 담당 업무 편입 승인 없이 수정하지 않음 |
| P1 | 영구 거부/재시도·학습 재생·PDF·소셜 연결 등 확정 노출 기능 미완료 | 238/250/232/237/239 담당과 남은 계약/기능 범위 정리, 임의 출시 제외 금지 |
| P1 | 전체 디자인·TalkBack·분할 화면/녹음 보존·오디오 경합·실기기 검사 미검증 | FE/QA 담당 미정, 실행 가능한 서버·기기를 확보한 후 기록된 절차 수행 |
| P1 | Recorder 메인 스레드 I/O 정적 ANR 위험 | FE 녹음 담당, dispatcher/자원 생명주기 검토 및 측정 후 수정 |
| 미확인 | 공식 키·최고 versionCode·연령/Families·사용권·계정별 사전 테스트 | Play 계정/제품 담당, 조건별 확인. 가상의 승인·기간·권리 선언 금지 |

Task191 재현: 계정 A에서 네트워크 실패로 미전송/열린 연습 구간을 남김 → 서버가 탈퇴를 수락 → B 로그인 → 앱 재진입 시 기존 큐가 B의 쿠키로 전송될 수 있음. 실제 계정 실험은 미검증이며 소스의 계정 경계 정리 누락으로 확인한 결함이다. 독립 검토에서도 출시 P1로 유지했다.

Android [core quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)·[Adaptive Tier 3](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-3)는 품질 권고와 팀 완료조건의 근거다. Play의 [심사 준비](https://support.google.com/googleplay/android-developer/answer/9859455), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469), [계정 삭제](https://support.google.com/googleplay/android-developer/answer/13327111)는 적용 정책 근거이며 계정별 실제 조건을 함께 확인해야 한다.
