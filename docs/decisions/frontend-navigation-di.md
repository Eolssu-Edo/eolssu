# 프론트엔드 화면 이동·DI 라이브러리

- 작업: [S15P31E102-91](https://ssafy.atlassian.net/browse/S15P31E102-91)
- 상태: **제안** — 2026-10-23 권영민 선택. 프론트 개발자 송가연의 합의를 확인하면 `확정`으로 바꾸고 합의 근거를 붙인다.
- 관련 규칙: [frontend/docs/collaboration.md](../../frontend/docs/collaboration.md) 4절(새 라이브러리 도입 절차)

## 선택

| 용도 | 라이브러리 | 버전 | 소스 세트 |
| --- | --- | --- | --- |
| 화면 이동 | `org.jetbrains.androidx.navigation:navigation-compose` | `2.10.0-rc01` | `commonMain` |
| DI | `io.insert-koin:koin-core`, `koin-compose`, `koin-compose-viewmodel` | `4.2.2` | `commonMain` |
| HTTP | Ktor | — | [HTTP 클라이언트 결정](frontend-http-client.md)(2026-10-29) |

버전은 `frontend/gradle/libs.versions.toml`의 `androidx-navigation`, `koin`에서 관리한다. 두 라이브러리 모두 Apache License 2.0이다(Maven Central POM 확인).

## 근거

### 화면 이동: navigation-compose

- JetBrains가 Android Navigation을 멀티플랫폼으로 옮긴 라이브러리라 Android 공식 방식과 사용법이 같다. 두 개발자 모두 KMP 경험이 적어 자료가 가장 많은 방식을 골랐다.
- Kotlin Multiplatform 공식 문서의 [화면 이동 설정](https://kotlinlang.org/docs/multiplatform/compose-navigation-routing.html)이 `2.10.0-rc01`을 안내한다(2026-10-23 확인).
- `2.10.0-rc01`은 이 프로젝트가 이미 쓰는 `lifecycle` `2.11.0`에 맞춰 배포됐다. 정식 버전인 `2.9.2`는 `lifecycle` `2.9.6`·Compose `1.8` 계열에 맞춰져 있어 프로젝트 버전과 어긋난다(Maven Central POM 확인).
- 정식 출시 전 버전(RC)이라는 위험이 있다. `2.10.0` 정식 버전이 나오면 교체한다.

### DI: Koin

- 코드 생성 없이 모듈에 등록하고 꺼내 쓰는 방식이라 설정이 짧고, Compose·ViewModel 연결(`koinViewModel()`)을 제공한다.
- 연결을 빠뜨리면 컴파일이 아니라 실행 중에 오류가 난다. 모듈 등록을 확인하는 테스트로 보완한다.
- `koin-compose-viewmodel-navigation`은 넣지 않았다. `navigation-compose` `2.9.2`에 맞춰져 있고, 화면 이동 그래프 안에서도 `koinViewModel()`로 충분하다.

### 지원 타깃

Maven Central의 Gradle 모듈 메타데이터에서 각 라이브러리가 이 프로젝트 타깃용으로 배포됐는지 확인했다(2026-10-23).

| 라이브러리 | Android | iOS (`iosArm64`·`iosSimulatorArm64`) | JS | Wasm |
| --- | --- | --- | --- | --- |
| `navigation-compose` `2.10.0-rc01` | 있음 | 있음 | 있음 | 있음 |
| `koin-core`·`koin-compose`·`koin-compose-viewmodel` `4.2.2` | 있음 | 있음 | 있음 | 있음 |

실제 빌드·테스트 결과는 [S15P31E102-91](https://ssafy.atlassian.net/browse/S15P31E102-91)에 남긴다.

## 검토한 대안

| 대안 | 판단 |
| --- | --- |
| DI 없이 `App()` 근처에서 직접 생성·전달 | 처음 화면 몇 개에는 충분하지만, 플랫폼별 녹음·재생 구현과 Repository가 늘면 연결 코드가 반복된다. 팀이 처음부터 Koin을 쓰기로 했다. |
| kotlin-inject, Metro | 연결 실수를 컴파일러가 잡아 주지만 코드 생성 설정이 필요하고 자료가 적어 입문 부담이 크다. |
| Voyager, Decompose | 화면 이동 방식이 독자적이라 Android 방식과 다르게 새로 익혀야 한다. |
| `navigation-compose` `2.9.2` | 정식 버전이지만 프로젝트의 `lifecycle` `2.11.0`과 맞지 않는다. |

## 영향 범위

- `frontend/shared`의 `commonMain` 의존성이 늘어난다. 웹 배포본 크기는 화면에서 실제로 사용하기 시작하는 작업에서 확인한다.
- 화면 이동 경로(`app/navigation`)와 Koin 모듈·시작 위치(`app/di`)의 구체적인 구성은 [S15P31E102-93](https://ssafy.atlassian.net/browse/S15P31E102-93)에서 정한다.
- 플랫폼 구현(녹음·재생 등)을 Koin에 등록하는 방식은 처음 플랫폼 구현을 연결하는 작업에서 정한다.
