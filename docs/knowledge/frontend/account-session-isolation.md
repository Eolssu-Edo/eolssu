# 계정 격리: 구현 전후와 검증

- 작업: S15P31E102-452 · Story S15P31E102-22 · [스펙](../../specs/account-session-isolation.md)
- 기준: `0dd0d15a2d6a9a784e76b3bc27167ca6be1ed105` (2026-11-04 구현 시작 시 develop)
- 범위: 한 앱에서 로그아웃·다른 회원 로그인·같은 회원 재로그인을 할 때 개인 데이터와 진행 중 작업을 분리한다.

## 무엇을 바꿨는가

| 기능과 사용 위치 | 이전 구현 | 변경 후 | 얻는 이익 |
| --- | --- | --- | --- |
| 연습 세션 만들기·기록 저장·메모·삭제 | 일부 요청만 인증 Mutex를 사용했고, 잠금을 기다린 뒤의 현재 회원을 읽었다. A가 시작한 작업이 B의 로그인으로 전송될 수 있었다. | 작업 시작 때 회원과 로그인 세대를 잡고 HTTP 전송 직전 잠금 안에서 확인한다. 같은 ID로 재로그인해도 이전 작업은 취소한다. | 이전 화면의 저장·삭제가 새 로그인에 실행되지 않는다. |
| 기록 목록·상세·평가 결과·성장 통계 | 저장소와 화면이 계정 변경을 함께 감지하지 않았다. 응답이 늦게 도착하면 이전 데이터가 남거나 다시 채워질 수 있었다. | 응답 파싱 후와 결과 반영 전에도 세대를 확인한다. 계정 변경 시 저장소 캐시와 화면 상태를 지우고 이전 작업을 취소한다. | 다른 회원의 메모·기록·점수·성장 수치를 표시하지 않는다. |
| 기록 상세의 녹음 다시 듣기 | 화면이 서명 URL을 보관하고 다시 재생할 수 있었다. | 보관한 URL·기록·재생 위치를 지우고 플레이어를 멈춘다. 이전 화면은 재생을 다시 시작할 수 없다. | 계정 변경 뒤 이전 회원의 녹음 재생이 이어지지 않는다. |
| 기초부터 배우기의 완료 표시 | `learn_done_<악기>`라는 기기 공용 키를 읽었다. | 회원별 키를 사용한다. 소유자를 모르는 기존 키는 그대로 보관하고 어느 계정에도 자동 배정하지 않는다. | A의 완료 표시가 B의 진도로 보이지 않는다. A가 재로그인하면 A의 진도는 다시 읽는다. |
| 내 악보·PDF 업로드 | 앱의 공용 메모리에 악보·업로드 중 파일이 남았다. | 계정 변경 때 메모리 목록과 업로드를 비우고 진행률·저장 반영을 차단한다. production 등록에서는 개인 목록을 빈 값으로 시작한다. | 이전 회원이 올리던 PDF를 다음 회원이 저장하거나 보는 것을 막는다. |
| 내 정보·악기·닉네임·비밀번호·간편 로그인 연결·탈퇴 | API별로 서로 다른 일부 세대 검사가 있었다. 화면 입력은 살아 있었다. | 공통 로그인 세대를 사용하고 화면 입력·OAuth 상태를 비운다. Route에서 부르는 suspend 저장도 화면 생성 당시 계정을 유지한다. | 카탈로그 조회나 외부 브라우저 복귀를 기다리던 이전 작업이 새 회원에게 적용되지 않는다. |
| 연습 중 녹음·분석 대기 | 진행 중 화면과 연습 종료 콜백이 계정 변경 후에도 남을 수 있었다. | 계정 변경 때 녹음·재생 작업과 분석 상태를 정리한다. 이전 화면의 종료 콜백으로 새 회원 명의의 연습 시간을 남기지 않는다. | 새 계정으로 잘못 업로드하거나 시간을 기록하지 않는다. 소유자가 있는 기존 pending 녹음 파일은 기존 복구 정책을 유지한다. |

## 실제 측정한 전후 수치

네 검사는 **구현 전에 기준 코드에 먼저 추가해 실행**했다. 모두 기대값 0에 실제값 1로 실패한 것을 확인한 뒤 구현했다. HTTP는 Ktor MockEngine, 스케줄은 `runTest`와 `CompletableDeferred`로 고정했다. 운영 서버 요청·고정 sleep·추정 수치는 사용하지 않았다.

| 고정 입력·순서 | 측정 대상과 분모 | 구현 전 | 구현 후 |
| --- | --- | ---: | ---: |
| A의 세션 생성 1건이 인증 잠금을 기다리는 동안 B로 변경 | 이전 작업이 보낸 POST 수 / 대기 작업 1건 | 1 / 1 | 0 / 1 |
| A의 세션 생성 1건 대기 → 로그아웃 → 같은 A 재로그인 | 이전 로그인 작업이 보낸 POST 수 / 대기 작업 1건 | 1 / 1 | 0 / 1 |
| A의 기록 1건 조회 → B로 변경 | 캐시에 남은 A 기록 수 / 기존 기록 1건 | 1 / 1 | 0 / 1 |
| A의 기록 응답 지연 → A→B→A → 응답 반환 | 이전 응답에서 캐시에 반영된 기록 수 / 응답 기록 1건 | 1 / 1 | 0 / 1 |

마지막 검사는 HTTP 진행 중 직접 세대를 변경해 늦은 응답이 도착하는 조건을 강제로 재현한다. 정상 인증 API의 Mutex 순서만을 흉내 낸 검사는 아니다. 실제 인증 변경이 페이지 사이에 들어가는 경우는 별도 회귀로 검증한다.

분모가 각 1인 결정적 재현 사례다. 운영 사고율이나 평균 응답 시간의 개선율로 일반화하지 않는다. 성능 시간은 측정하지 않았다.

기준 실행 XML은 `tests="4" failures="4" errors="0"`, 시각 `2026-11-04T00:59:20.777Z`였다. 출력은 다음과 같았다.

```text
ACCOUNT_METRIC stale_records=1 denominator=1
ACCOUNT_METRIC queued_create_A=1 denominator=1
ACCOUNT_METRIC queued_create_B=1 denominator=1
ACCOUNT_METRIC cached_records=1 denominator=1
```

개선 후 동일 네 출력의 값은 모두 0이다. fixture·순서·assertion은 유지했고, 기록 저장소를 만들 때 새 의존성 `member`를 연결한 차이가 있다. 비교 검사는 `AccountIsolationRegressionTest`의 네 해당 메서드다. 후속 회귀는 같은 파일과 `AccountStateIsolationTest`, `RecordDetailPlaybackTest`에 추가했다.

## 구현 경계와 재검증

- `SignedInMember`는 앱 DI의 단일 객체다. `AccountSession(memberId, epoch)`를 관리한다. 로그인·가입·병합·로그아웃·탈퇴의 기존 `set()` 경로는 모두 세대를 갱신하고 토큰 refresh는 갱신하지 않는다. 새 앱 실행은 저장된 회원 ID에서 시작하며 이전 프로세스의 작업을 이어 받지 않는다.
- 저장소의 `inSession`과 화면의 `screenScope`가 작업 소유자를 코루틴 컨텍스트로 전달한다. `sendCatching`은 개별 HTTP 요청과 그 토큰 갱신 동안 기존 인증 Mutex를 사용한다. 페이지 전체를 한 번에 잠그지 않는다.
- 연속 두 페이지 fixture에서 첫 요청 중 인증 변경을 대기시킨 뒤 첫 응답을 반환하면 인증 변경이 실행되고, 다음 페이지 요청은 0건이다. 메모·삭제·탈퇴의 대기 요청도 새 계정에서 전송되지 않는다.
- 화면 scope는 처음 로그인에 고정된다. 계정 변경 뒤 같은 화면을 새 계정에 재사용하지 않는다. `EolssuNavHost`가 이전 signed-in 세대의 backstack을 비우고 로그인 또는 홈을 새로 만든다. 첫 로그인·가입의 기존 완료 이동은 유지한다.
- 탈퇴 화면은 자신이 성공시킨 인증 변경 뒤 완료 처리를 할 수 있게 scope 자체를 즉시 취소하지 않는다. 상태는 지우며, 이후 요청과 OAuth 복귀는 고정된 이전 세대 검사로 막는다.
- 학습 진도 legacy 키와 소유자 없는 녹음 파일은 삭제·자동 이관하지 않는다. 학습·내 악보의 서버 API 연결, 녹음 판본 저장은 이 작업에 포함하지 않는다.

Windows에서 Android Studio JBR와 설치된 SDK를 사용했다. `frontend/`에서 실행한다.

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_HOME='C:/Users/SSAFY/AppData/Local/Android/Sdk'
$env:EOLSSU_QA_LIVE_CATALOG='false'
.\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinWasmJs :shared:compileKotlinJs --console=plain
```

- 기준 실패 실행과 개선 후 회귀 출력: `frontend/shared/build/test-results/testAndroidHostTest/TEST-com.ssafy.eolssu.data.practice.AccountIsolationRegressionTest.xml`. 빌드 때 갱신되므로 기준 원본은 로컬 작업 기록 `docs/plans/S15P31E102-452-baseline.xml`에도 보존했다.
- HTML 보고서: `frontend/shared/build/reports/tests/testAndroidHostTest/index.html`.
- 최종 실행(2026-11-04, 1m24): **전체 730건 중 726 통과, 4 조건부 통합 검사 건너뜀, 실패·오류 0**. Android debug APK 및 공통 JS·Wasm 컴파일 성공.
- 건너뛴 검사는 `CatalogLocalServerTest`, `GeneratedCatalogBundleTest`, `JangdanLocalServerTest`, `QaLiveCatalogTest`다. 서버·별도 생성 bundle·운영 QA opt-in 조건이 없는 환경이며, 해당 검사를 통과한 것으로 세지 않는다.
- 신규 격리 회귀는 13건(요청·캐시 7, 로컬 상태·오래된 화면 5, 녹음 재생 1) 모두 통과했다. 기존 정상 로그인·가입·탈퇴, 녹음 소유권·pending 복구·토큰 갱신 회귀도 포함한다.
- 마지막 비교 XML 시각은 `2026-11-04T01:16:46.899Z`; `tests="7" failures="0" errors="0"`이며 원래 비교용 네 metric은 모두0이다.

## 정상 로그인 경계의 후속 수정

교차 점검에서 첫 구현 `50eaf91d`의 회귀를 발견했다. 로그인·소셜 로그인·계정 병합·가입 완료 화면은 이전 프로필을 비우려고 `UserRepository.logOut()`을 호출했는데, 계정 격리 구현 뒤에는 이 호출이 방금 받은 회원 ID와 로그인 세대까지 무효화했다. 인증 저장소와 화면을 따로 검증한 기존 테스트만으로는 이 연결 오류를 잡지 못했다.

`clearProfile()`은 표시할 프로필·악기 캐시만 비우며 로그인 세대를 유지한다. 네 성공 분기를 이 메서드로 바꿨다. 실제 로그아웃, 앱 시작 시 인증 만료, 탈퇴는 기존 세션 무효화를 유지한다.

| 같은 고정 입력·순서 | 첫 구현 `50eaf91d` | 후속 수정 |
| --- | ---: | ---: |
| 회원 7의 이메일 로그인 성공 → 프로필 초기화 → 새 개인 연습 세션 요청 1건: 남은 회원 ID | null | 7 |
| 같은 흐름에서 로그인 세대(epoch), 초기값 0 | 2 | 1 |
| 같은 흐름에서 실제 Ktor MockEngine에 도달한 세션 POST 수 / 요청 1건 | 0 / 1 | 1 / 1 |

`AuthSessionLifecycleRegressionTest.successfulLoginKeepsItsOwnerForTheNextPersonalRequest`를 먼저 기존 구현에 실행해 실패를 관측했다. 실제 LoginViewModel, ApiAuthRepository, ApiUserRepository, HttpPracticeSessionRepository를 연결하며, HTTP 응답만 고정했다. assertion 전에 출력한 전후 값은 각각 `member=null epoch=2 personalCreates=0`, `member=7 epoch=1 personalCreates=1`이다. 운영 서버나 DB의 삽입 건수를 측정한 값은 아니다.

후속 회귀 4건은 위 이메일 로그인, 소셜 로그인·병합·이메일 가입·소셜 가입 각각의 다음 개인 요청, 실제 로그아웃 후 같은 ID 재로그인과 토큰 갱신, 프로필 초기화·startup401·탈퇴의 세대 경계를 확인한다. 마지막 focused 실행은 **46건 통과, 실패·오류·건너뜀 0**이다.

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests '*AuthSessionLifecycleRegressionTest' --tests '*ApiAuthRepositoryTest' --tests '*ApiUserRepositoryTest' --tests '*OAuthViewModelTest' --tests '*AuthFormViewModelTest' --tests '*AccountIsolationRegressionTest' --tests '*PracticeTimeMemberTest' --no-daemon --max-workers=1
```

위 730건 결과는 후속 수정 전 결과다. 후속 수정과 담당 범위 제외 지시를 적용한 **452 단독 브랜치 `2827b671`**에서 전체 검사를 다시 수행했다(2026-11-04, 23초): **734건 중 730 통과·기존 조건부 4건 건너뜀·실패/오류 0**. Android debug APK 빌드, 공통 JS·Wasm 컴파일도 성공했다. 비교 XML 시각은 `2026-11-04T01:38:30.816Z`이며 네 계정 격리 metric은 모두 0이다. 이 실행 시간은 빌드 캐시가 있는 검증 소요이며 앱 성능 수치가 아니다.

사용자 후속 지시에 따라 Joseph Woo 담당 API와 확장은 이번 적용에서 제외했다. 453·454·456의 구현은 최종 브랜치에 포함하지 않았다. 읽기 전용 교차 점검에서 452의 백엔드·AI 변경 파일 **0개**, HTTP 메서드·요청 본문·응답 DTO·상태 코드 계약 변경 없음과 제외 작업 의존성 없음을 확인했다. [담당 API 20개와 개선안별 제외 이유](screen-improvement-scope.md)를 함께 본다.

## 확인하지 못한 범위

실제 계정으로 Android/iOS/Web 화면을 왕복하며 로그인·로그아웃한 E2E와 실제 녹음 기기의 재생 중단은 실행하지 않았다. 플레이어는 fake를 사용해 중단·URL 재사용 금지를 확인했다. Windows에서 iOS 컴파일·실기기 검증은 불가능하다. Android APK 빌드와 JS/Wasm 컴파일 성공을 이 검증의 대체 근거로 쓰지 않는다. 정식 독립 리뷰와 기기 검증은 남아 있다.
