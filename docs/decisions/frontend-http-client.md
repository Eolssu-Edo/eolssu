# 프론트엔드 HTTP 클라이언트와 로그인 쿠키 보관

- 작업: [S15P31E102-247](https://ssafy.atlassian.net/browse/S15P31E102-247)
- 상태: **확정** — 2026-10-29 권영민·송가연 합의(권영민이 전달). 연습 세션·녹음 업로드 연결 작업은 이 클라이언트 위에서 한다.
- 관련 규칙: [frontend/docs/collaboration.md](../../frontend/docs/collaboration.md) 4절(새 라이브러리 도입 절차), [화면 이동·DI 결정](frontend-navigation-di.md)의 'HTTP 보류'를 이 문서가 대신한다.

## 선택

| 용도 | 라이브러리 | 버전 | 소스 세트 |
| --- | --- | --- | --- |
| HTTP 클라이언트 | `io.ktor:ktor-client-core`, `ktor-client-content-negotiation` | `3.6.0` | `commonMain` |
| JSON | `io.ktor:ktor-serialization-kotlinx-json`(kotlinx.serialization), Kotlin `plugin.serialization` | `3.6.0`, Kotlin 버전 | `commonMain` |
| 플랫폼 엔진 | `ktor-client-okhttp` / `ktor-client-darwin` / `ktor-client-js` | `3.6.0` | `androidMain` / `iosMain` / `webMain` |
| 테스트 | `ktor-client-mock`, `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `3.6.0`, `1.11.0` | `commonTest` |

버전은 `frontend/gradle/libs.versions.toml`의 `ktor`, `kotlinx-coroutines`에서 관리한다. `kotlinx-coroutines-test`는 Ktor 3.6.0이 쓰는 코루틴 버전(1.11.0)에 맞췄다. 모두 Apache License 2.0이다.

## 근거

- Kotlin Multiplatform에서 Android·iOS·JS·Wasm을 한 코드로 쓰는 대표 HTTP 클라이언트이고, JetBrains가 만든다. 플랫폼마다 그 플랫폼의 HTTP 구현(OkHttp, `NSURLSession`, 브라우저 `fetch`)을 엔진으로 쓴다.
- Ktor 3.6.0의 Gradle 모듈 메타데이터에서 이 프로젝트 타깃용 배포를 확인했다(2026-10-29). Kotlin 2.3.21로 빌드됐고 프로젝트의 Kotlin 2.4.20에서 컴파일된다.
- `MockEngine`으로 서버 없이 응답 코드·쿠키·재시도를 단위 테스트할 수 있다.

| 라이브러리 | Android | iOS (`iosArm64`·`iosSimulatorArm64`) | JS | Wasm |
| --- | --- | --- | --- | --- |
| `ktor-client-core`·`content-negotiation`·`serialization-kotlinx-json`·`mock` | 있음 | 있음 | 있음 | 있음 |
| 엔진 | `okhttp`(JVM) | `darwin` | `js` | `js` |

## 구성

- `core/network/HttpClients.kt`의 `createHttpClient`가 앱 전체의 클라이언트 하나를 만든다(`app/di`). 응답 코드는 부르는 쪽이 판단한다(`expectSuccess = false`). 서버 공통 응답 `{httpStatus, isSuccess, message, code, result}`은 `ApiResponse`로 읽는다.
- 서버 주소: Android·iOS는 `https://k15e102.p.ssafy.io`, 웹은 페이지와 같은 주소(`window.location.origin`)다. 운영 Nginx가 `/api`를 백엔드로 넘긴다. 각 플랫폼 모듈이 `ServerAddress`를 등록한다.
- 로그인 쿠키(`access_token` Path `/api`, `refresh_token` Path `/api/auth`, 모두 HttpOnly):
  - 웹은 브라우저가 보관하고 같은 주소 요청에 붙인다. 앱 코드는 쿠키를 다루지 않는다.
  - Android는 Keystore의 AES-GCM 열쇠로 암호화해 `SharedPreferences`(`eolssu_secure`)에, iOS는 Keychain(`AfterFirstUnlockThisDeviceOnly`)에 둔다(`SecureStore`, `SecureCookiesStorage`). 앱을 다시 켜도 리프레시 토큰이 살아 있는 동안(최대 14일) 로그인이 이어진다. 암호화하지 않은 `KeyValueStore`에는 두지 않는다.
- 로그인이 필요한 요청이 401이면 `/api/auth/refresh`로 갱신하고 같은 요청을 한 번 더 보낸다. 리프레시 토큰은 쓸 때마다 바뀌므로 갱신은 한 번에 하나만 하고, 기다리는 동안 다른 요청이 갱신했으면 그 결과를 쓴다. 로그인·가입·갱신 자체의 401은 갱신하지 않는다.
  - 웹은 모든 탭이 같은 쿠키를 쓰므로 탭 사이에서도 한 번에 하나만 갱신한다(Web Locks와 localStorage 갱신 세대, [S15P31E102-1264 인계 문서](../plans/web-parity/S15P31E102-1264-handoff.md) 3.1절). 웹은 쿠키를 앱이 지울 수 없어, 서버에 닿지 못한 로그아웃은 다음 시작 때 다시 보낸다.

## 다른 API를 붙일 때

2026-10-29 연습 세션·녹음 업로드 담당(우동균)의 요청을 반영했다. 모두 `HttpClientsTest`가 확인한다.

- **응답 코드**: 클라이언트는 오류를 예외로 바꾸지 않는다. 503·410·413 같은 코드는 `response.status`로 보고, 본문은 `apiResponse<ApiError>()`로 `httpStatus`(상태 이름)·`code`(번호)·`errorCode`를 읽는다.
- **응답을 받지 못함**: `sendCatching { ... }`이 `NetworkResult.Received`·`ConnectionFailed`·`TimedOut`을 돌려준다. 시간 초과는 서버가 요청을 받았을 수도 있다. 둘을 나눌 필요가 없으면 `sendOrNull`을 쓴다.
- **시간 제한**: 기본값은 연결 10초, 요청 전체 30초다. 요청마다 `timeout { requestTimeoutMillis = ... }`로 바꾼다.
- **401 뒤 다시 보내기**: 같은 요청을 본문까지 다시 보낸다. multipart 파일 칸은 바이트 배열이나 매번 새 스트림을 여는 `ChannelProvider { ... }`로 넣는다(이미 연 스트림을 넘기면 두 번째에 비어 있다). 큰 파일은 401이면 한 번 더 올리게 된다.

## 검토한 대안

| 대안 | 판단 |
| --- | --- |
| 플랫폼별로 직접 구현(OkHttp·`NSURLSession`·`fetch`를 `expect`/`actual`로) | 요청·JSON·쿠키·재시도를 플랫폼마다 세 번 만들어야 하고 테스트도 나뉜다. |
| 앱 쿠키를 메모리에만 두기 | 안전하지만 앱을 켤 때마다 다시 로그인해야 한다. |
| `androidx.security:security-crypto`(`EncryptedSharedPreferences`) | 더 이상 권장하지 않는(deprecated) API라 Keystore를 직접 쓴다. |
| iOS Keychain용 라이브러리(multiplatform-settings, KVault) | 함수 세 개(읽기·쓰기·지우기)만 필요해 의존성을 늘리지 않았다. |

## 영향 범위와 남은 일

- `frontend/shared`의 의존성이 늘고, 웹 엔진이 npm `ws`를 추가해 `kotlin-js-store`의 lock 파일이 바뀐다.
- iOS 코드(`IosSecureStore`, Darwin 엔진)는 Windows에서 컴파일할 수 없어 Mac에서 확인해야 한다.
- 웹 개발 서버(`localhost:8080`)는 백엔드로 `/api`를 넘기지 않아 로그인·전송이 연결 실패가 된다. 개발 중 서버 연결은 프록시나 개발용 주소를 정할 때 다룬다.
- 리프레시 토큰까지 만료돼 갱신이 실패했을 때 로그인 화면으로 보내는 처리는 아직 없다.
