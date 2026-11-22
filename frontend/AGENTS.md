# Frontend 작업 지침

## 프로젝트 구성

- `frontend/`는 독립된 Gradle 프로젝트다. 설치·실행·빌드·테스트 명령은 [README](README.md)를, 폴더 구조와 각 영역의 역할은 [아키텍처 문서](docs/architecture.md)를, 두 개발자의 협업 규칙은 [협업 문서](docs/collaboration.md)를 기준으로 하고 작업 전에 해당 부분을 확인한다.
- 현재 설정은 Kotlin Multiplatform + Compose Multiplatform 공유 UI이고, 타깃은 Android·iOS·Web(Wasm·JS)이다. 패키지는 `com.ssafy.eolssu`, 버전은 `gradle/libs.versions.toml`에서 관리한다. 악보 엔진 연동 방식처럼 정해지지 않은 사항은 기획과 실제 설정의 확정 상태를 확인한다.
- 모듈 역할: `shared`는 공통 UI·로직·리소스, `androidApp`·`iosApp`·`webApp`은 `App()`을 띄우는 진입점과 앱 설정(권한·아이콘·`Info.plist`·`index.html`)만 둔다. `feature`·`core`·`data`는 `shared` 안의 패키지 구분이며 별도 Gradle 모듈로 만들지 않는다.
- IDE는 Android Studio에서 저장소 루트가 아닌 `frontend/`를 연다. Windows에서는 저장소 경로에 한글 등 ASCII가 아닌 문자가 있으면 Gradle 동기화가 실패한다.

## 코드 위치

- 화면·ViewModel·공통 로직은 `shared/src/commonMain/kotlin/com/ssafy/eolssu/` 아래 고정된 최상위 패키지에 둔다. 진입점 모듈에 화면 로직을 넣지 않는다.

| 패키지 | 두는 것 | 두지 않는 것 |
| --- | --- | --- |
| `app/` | 앱 최상위 구성, 화면 이동(`navigation`), 객체 생성·연결(`di`) | 기능별 API 처리, 화면 세부 로직 |
| `feature/<기능>/` | 기능별 Route·Screen·ViewModel·UiState, 해당 기능 전용 `component/`·`layout/` | HTTP 클라이언트·플랫폼 API 직접 호출, 다른 feature의 내부 구현 참조 |
| `data/<영역>/` (`song`·`practice`·`user`) | API 요청, 응답 DTO, 변환, Repository, 로컬 데이터 처리. feature와 1:1이 아니며 대응은 아키텍처 문서의 표를 따른다 | Compose 화면·ViewModel 의존 |
| `core/` | 여러 기능이 공유하는 UI(`ui/component`·`ui/score`·`ui/adaptive`), `theme`, `network`, `audio`, `storage`, 스토어 업데이트 확인(`update`), 공유 모델(`model`) | 특정 feature·data·app 의존 |

- 기능 폴더명은 `home`, `guide`, `auth`, `song`, `practice`, `result`, `history`, `profile`을 사용하고, 기능 내부 UI 폴더명은 `component`로 통일한다. `pages`, `hooks`, `store`, `styles`, `utils`, `shared` 같은 최상위 패키지를 추가하지 않는다.
- 의존 방향은 `app → feature, data, core`, `feature → data, core`, `data → core`만 허용한다. `core`는 `feature`·`data`·`app`을 참조하지 않고, feature끼리의 연결은 `app`에서 조정한다. `domain` 계층·UseCase는 만들지 않으며 필요해지면 구조 변경 절차로 다룬다.
- 상태와 동작이 있는 화면은 `<기능>Route.kt`(ViewModel 연결·상태 수집·화면 이동), `<기능>Screen.kt`(상태·콜백으로 UI 표시), `<기능>ViewModel.kt`, `<기능>UiState.kt`로 나눈다. Screen은 ViewModel 대신 상태와 콜백을 받는다. 정적 화면까지 모든 파일을 의무적으로 만들지 않고 책임이 생길 때 분리한다.
- 모델 위치: 여러 기능이 쓰는 앱 모델은 `core/model`, 서버 요청·응답 DTO는 해당 `data` 내부, 한 화면만 쓰는 모델은 해당 `feature` 내부에 둔다.
- 새 파일을 만들기 전에 (1) 어느 기능의 책임인지, (2) 다른 기능도 쓰는지, (3) 플랫폼 API가 필요한지를 순서대로 판단해 위치를 정한다. 판단 기준과 예시는 [아키텍처 문서](docs/architecture.md)를 따른다. 기존 `core`·`data`에 같은 역할의 구현이 있는지 먼저 확인하고 재사용한다.
- 플랫폼 API(마이크·오디오·저장소·권한)는 `commonMain`의 `core/audio`·`core/storage` 같은 공통 인터페이스와 `androidMain`·`iosMain`의 `platform/audio`·`platform/storage`·`platform/permission` 구현으로 나누고, 작은 값만 `expect`/`actual`을 쓴다. 공통 코드에서 Android·Apple 전용 API를 import하지 않는다. JS·Wasm 공통 웹 코드는 `webMain`에 둔다. 실제 플랫폼 의존성이 없는 코드를 중복 구현하지 않는다.
- 휴대폰·태블릿은 같은 화면 상태와 로직을 공유하고, 창 크기에 따라 배치만 바꾼다. 배치가 크게 달라질 때만 `feature/<기능>/layout/`에 Compact·Expanded 내용을 나누고, 판정 기준은 `core/ui/adaptive`에 둔다. 기기별 프로젝트·최상위 패키지를 만들지 않는다.
- 이미지·폰트·문구는 `shared/src/commonMain/composeResources/`에 두고 생성된 `Res`(`eolssu.shared.generated.resources`)로 접근한다.
- 라이브러리는 `gradle/libs.versions.toml`에 등록한다. `commonMain`에 넣는 라이브러리는 Android·iOS·JS·Wasm 지원을 먼저 확인하고, 추가 전에 [협업 문서](docs/collaboration.md)의 절차대로 팀에 공유한다. 화면 이동은 `navigation-compose`, DI는 Koin을 쓴다([결정 문서](../docs/decisions/frontend-navigation-di.md)). HTTP는 `core/network`의 Ktor 클라이언트 하나를 쓰고, 로그인 쿠키·토큰 같은 민감한 값은 `KeyValueStore`가 아닌 `SecureStore`에 둔다([HTTP 결정](../docs/decisions/frontend-http-client.md)).
- 각 진입점이 `App`을 호출하므로 이름을 유지한다. Android는 `App(context)`, Web은 `App(onNavHostReady)`(브라우저 뒤로 가기·주소 연결), iOS는 `MainViewController()`를 부르고, 이들이 플랫폼 구현 Koin 모듈(`platform/PlatformModule.kt`)을 공통 `App`에 넘긴다. 공통 `App`은 Koin을 시작하고 테마 안에서 `app/navigation`의 화면 이동 그래프를 그린다.

## 구조와 문서의 정합성

- 최상위 패키지·소스 세트·Gradle 모듈을 추가·이름 변경·분리하는 구조 변경은 팀 합의 후에 하고, 같은 변경에서 이 문서와 [아키텍처 문서](docs/architecture.md)를 함께 고친다.
- 문서와 실제 코드가 다르면 문서를 임의로 코드에 맞추지 말고 차이를 보고한 뒤 어느 쪽이 맞는지 확인한다.
- 구조를 채우기 위한 빈 클래스·빈 ViewModel·동작하지 않는 Repository를 만들지 않는다. 미구현 영역은 `.gitkeep`으로만 추적한다.

## 도메인 표시와 오디오

- 율명·계이름·기호 표시를 변경할 때 원본 악보·음표 식별자·화면 위치의 연결을 보존한다. 운지법·재생·평가 결과가 같은 음표를 가리키는지 확인한다.
- 실시간 음높이 안내와 연주 후 최종 채점 결과를 구분해 표시한다. 점수·음높이·시간 단위를 화면에서 임의로 재해석하지 않는다.
- 녹음·재생·메트로놈 변경 시 권한 거부, 중단·재시작, 화면 이탈과 백그라운드 전환에 따른 자원 해제를 확인한다.
- 누르면 바로 소리가 나는 버튼에는 `elasticSquash`(`EolssuButton`은 `squash = true`)를 쓰고, 화면만 넘어가는 버튼·5초 이동·녹음·중단 버튼에는 쓰지 않는다([결정](../docs/decisions/frontend-sound-button-press.md)).

## 검증과 기록

- Android 에뮬레이터로 실행·검증할 때는 [README](README.md)의 `처음 빌드하기`에 명시한 기기 프로필 기준을 따른다.
- 상태·표시 변환은 `shared` 테스트 같은 작은 자동 검사로 검증한다. 마이크·재생·타이밍 변경은 실제 기기에서 확인하고 기기·OS·관찰 결과를 남긴다. 에뮬레이터 결과를 실기기 검증으로 보고하지 않는다.
- Windows에서는 iOS 코드(`iosMain`, `iosApp`)를 컴파일할 수 없다. Windows 빌드 성공을 iOS 확인으로 보고하지 않고, Mac에서 확인하지 않은 iOS 변경은 미검증으로 보고한다.
- 웹 빌드로 생기는 `kotlin-js-store/`의 lock 파일은 커밋한다. `gradlew`를 교체하면 `git update-index --chmod=+x frontend/gradlew`로 실행 권한을 유지한다.
- UI·악보 엔진·온디바이스 처리 조사에서 확인한 지원 범위·제약·측정 결과를 `docs/knowledge/frontend/`에 남긴다. 가정한 성능을 측정값으로 기록하지 않는다.
- 프로젝트를 초기화하거나 실행 방식을 바꾸면 실제 설치·실행·검증 명령과 필요한 SDK를 이 폴더의 README에 기록한다.
