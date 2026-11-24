# 웹 브라우저 테스트 자동 실행 경로 (S15P31E102-1270)

2026-11-24 develop(`b72d56156`) 기준으로 웹 테스트(`:shared:wasmJsTest`·`:shared:jsTest`)는 사람이 실행하는 웹 수동 배포 잡(`eolssu-frontend-web-수동배포용`)에서만 돌았다. GitLab CI가 없어 MR에는 파이프라인 상태가 붙지 않는다. 이 문서는 자동 실행 경로를 고른 근거와 로컬 실측, 웹 스모크 자동화 검토 결과를 남긴다. 운영 적용 절차는 [컨트롤러 문서](../../../infra/jenkins/controller/README.md#웹-테스트-잡)를 따른다.

## 고른 경로: develop 병합 뒤 자동

| 선택지 | 장점 | 대가 | 결과 |
| --- | --- | --- | --- |
| develop 병합 뒤 자동(백엔드 잡이 예약하는 테스트 전용 잡) | 기존 develop 감시(`pollSCM`)와 iOS QA 예약 방식을 그대로 쓴다. 배포와 분리돼 운영을 바꾸지 않는다. | 실패를 병합 뒤에야 안다. | 선택(2026-11-23 사용자 결정) |
| MR 브랜치 폴링(`refs/merge-requests/*/head`) | 병합 전에 실패를 안다. | 결과가 Jenkins에만 남고 GitLab MR에 표시되지 않는다. MR마다 빌드해 `edo1-web` 부하가 는다. | 고르지 않음 |
| 웹 수동 배포 잡 자동화 | 잡을 새로 만들지 않는다. | 테스트마다 운영 배포까지 이어진다. | 고르지 않음 |

티켓 제목의 'MR 단계 wasmJsTest'는 이번 범위에서 충족하지 않는다. MR 단계 검사가 필요해지면 GitLab 커밋 상태를 올리는 방식(Jenkins GitLab 연동 또는 API 토큰)을 따로 정해야 한다.

## 예약 조건

`eolssu-backend`의 `Queue web tests` 단계가 iOS QA 예약과 같은 기준(직전 성공 커밋 `IOS_QA_BASE`, 없으면 첫 부모)으로 아래 경로의 변경을 본다.

- `frontend/shared/src/`의 `commonMain`·`commonTest`·`webMain`·`webTest`·`jsMain`·`jsTest`·`wasmJsMain`·`wasmJsTest`
- `frontend/shared/build.gradle.kts`, `frontend/build.gradle.kts`, `frontend/settings.gradle.kts`, `frontend/gradle.properties`, `frontend/gradle/`, `frontend/gradlew`
- `frontend/kotlin-js-store/`(웹 의존성 lock)

`androidMain`·`iosMain`·`webApp/`만 바뀐 커밋은 공유 모듈 웹 테스트 결과를 바꾸지 않으므로 예약하지 않는다. `git diff`가 실패하면 예약하지 않고 백엔드 빌드를 UNSTABLE로 표시한다. 이 단계는 백엔드 검사·배포보다 앞에 있어 백엔드가 실패해도 웹 테스트는 예약된다.

로컬에서 단계의 셸 부분을 `sh -e`로 실제 develop 커밋에 돌려 확인했다(2026-11-24).

| 비교 | 종료 코드 | 동작 |
| --- | ---: | --- |
| infra만 바뀐 `9a0596d61..b72d56156` | 0 | 건너뜀 |
| 기준이 비거나 없는 SHA → 첫 부모로 대체 | 0 | 건너뜀 |
| `commonMain`이 바뀐 `38fc3b2e1..7b5f0a4dc` | 1 | 예약 |
| 없는 HEAD | 128 | UNSTABLE |

## 로컬 실측

2026-11-24, macOS arm64, JDK 21(zulu 21.0.11), Gradle 9.5.1, ChromeHeadless 158, `--max-workers=2`. 같은 worktree에서 순서대로 실행했고 Gradle 데몬·전역 캐시를 재사용했다. 서버 실측이 아니며 운영 Jenkins 첫 실행에서 다시 잰다.

| 실행 | 결과 | 시간(초) |
| --- | --- | ---: |
| `:shared:wasmJsBrowserTest --tests "com.ssafy.eolssu.platform.*"`(새 테스트 컴파일 포함) | 9개 통과 | 43 |
| 같은 명령, `AnalysisViewModel` 정의의 `RecordingFiles` 가드를 임시로 뺌 | 의도대로 1개 실패. 루트 정의 84개를 훑었고 원인 사슬 `AnalysisViewModel ← RecordingUploads ← RecordingFiles 없음`을 보고했다 | 49 |
| `:shared:wasmJsTest :shared:jsTest`(JS 본문·테스트 컴파일 포함) | Wasm·JS 각 2,016개 통과, 실패 0 | 89 |

운영 `edo1-web`의 빈 작업 공간에서는 Wasm·JS 컴파일과 두 브라우저 테스트가 합계 약 9분 걸렸다([프론트 CI 캐시 측정](frontend-ci-cache.md)). 테스트 잡의 20분 제한은 이 첫 실행을 기준으로 잡았다.

## 웹 스모크 자동화 검토

티켓 범위의 '운영·스테이징 웹 최소 스모크(로딩·로그인 화면·곡 목록)'는 검토만 하고 별도 Task [S15P31E102-1274](https://ssafy.atlassian.net/browse/S15P31E102-1274)로 넘겼다(2026-11-24 사용자 결정).

- Compose 웹은 화면을 canvas에 그린다. 화면 문구가 DOM에 없으므로 DOM 문자열로 로그인 화면을 확인할 수 없다. 스크린샷 비교, 접근성(semantics) 트리, 네트워크 신호 중 하나를 골라야 한다.
- 곡 목록 API는 `/api/**` 인증 대상이다(`SecurityConfig`). 곡 목록까지 보려면 스모크 전용 계정과 그 비밀번호를 Jenkins Credentials에 두어야 한다.
- 인증 없이 바로 할 수 있는 범위는 첫 페이지·Wasm·JS 자원의 HTTP 200과 공개 상태 확인(`/api/actuator/health` 계열)이다. 자원 확인은 이미 [deploy-frontend.py](../../../infra/jenkins/deploy-frontend.py)의 `verify_assets`가 배포 직후 모든 자원의 HTTP 200·SHA-256·Wasm 형식으로 한다. 스모크가 더할 가치는 실제 브라우저에서 앱이 그려지는지와 로그인 뒤 곡 목록이다.
- `edo1-web`에는 Google Chrome(`/usr/bin/google-chrome`)이 있어 브라우저 실행 자체는 가능하다. 운영 계정·데이터를 쓰는 자동화이므로 계정 생성·정리 방식과 실행 주기를 먼저 정해야 한다.
