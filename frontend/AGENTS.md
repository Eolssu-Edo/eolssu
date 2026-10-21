# Frontend 작업 지침

## 프로젝트 구성

- `frontend/`는 독립된 Gradle 프로젝트다. 설치·실행·빌드·테스트 명령, 폴더 구조, 코드 위치 규칙은 [README](README.md)를 기준으로 하고 작업 전에 해당 부분을 확인한다.
- 현재 설정은 Kotlin Multiplatform + Compose Multiplatform 공유 UI이고, 타깃은 Android·iOS·Web(Wasm·JS)이다. 패키지는 `com.ssafy.eolsu`, 버전은 `gradle/libs.versions.toml`에서 관리한다. 악보 엔진 연동 방식처럼 정해지지 않은 사항은 기획과 실제 설정의 확정 상태를 확인한다.
- 모듈 역할: `shared`는 공통 UI·로직·리소스, `androidApp`·`iosApp`·`webApp`은 `App()`을 띄우는 진입점과 앱 설정(권한·아이콘·`Info.plist`·`index.html`)만 둔다.
- IDE는 Android Studio에서 저장소 루트가 아닌 `frontend/`를 연다. Windows에서는 저장소 경로에 한글 등 ASCII가 아닌 문자가 있으면 Gradle 동기화가 실패한다.

## 코드 위치

- 화면·ViewModel·도메인 로직은 `shared/src/commonMain/kotlin`에 둔다. 진입점 모듈에 화면 로직을 넣지 않는다.
- 공통 UI·도메인 로직과 플랫폼별 마이크·오디오·권한 처리를 구분한다. 플랫폼 API는 `commonMain` 인터페이스와 `androidMain`·`iosMain`·`jsMain`·`wasmJsMain` 구현으로 나누고, 작은 값만 `expect`/`actual`을 쓴다. JS·Wasm 공통 웹 코드는 `webMain`에 둔다. 실제 플랫폼 의존성이 없는 코드를 중복 구현하지 않는다.
- 이미지·폰트·문구는 `shared/src/commonMain/composeResources/`에 두고 생성된 `Res`(`eolsu.shared.generated.resources`)로 접근한다.
- 라이브러리는 `gradle/libs.versions.toml`에 등록한다. `commonMain`에 넣는 라이브러리는 Android·iOS·JS·Wasm 지원을 먼저 확인한다.
- 마법사 예제(`Greeting`, `Platform`, `compose-multiplatform.xml`, 샘플 테스트)는 실제 기능으로 대체한다. 각 진입점이 `App()`을 호출하므로 이름을 유지한다.

## 도메인 표시와 오디오

- 율명·계이름·기호 표시를 변경할 때 원본 악보·음표 식별자·화면 위치의 연결을 보존한다. 운지법·재생·평가 결과가 같은 음표를 가리키는지 확인한다.
- 실시간 음높이 안내와 연주 후 최종 채점 결과를 구분해 표시한다. 점수·음높이·시간 단위를 화면에서 임의로 재해석하지 않는다.
- 녹음·재생·메트로놈 변경 시 권한 거부, 중단·재시작, 화면 이탈과 백그라운드 전환에 따른 자원 해제를 확인한다.

## 검증과 기록

- 상태·표시 변환은 `shared` 테스트 같은 작은 자동 검사로 검증한다. 마이크·재생·타이밍 변경은 실제 기기에서 확인하고 기기·OS·관찰 결과를 남긴다. 에뮬레이터 결과를 실기기 검증으로 보고하지 않는다.
- Windows에서는 iOS 코드(`iosMain`, `iosApp`)를 컴파일할 수 없다. Windows 빌드 성공을 iOS 확인으로 보고하지 않고, Mac에서 확인하지 않은 iOS 변경은 미검증으로 보고한다.
- 웹 빌드로 생기는 `kotlin-js-store/`의 lock 파일은 커밋한다. `gradlew`를 교체하면 `git update-index --chmod=+x frontend/gradlew`로 실행 권한을 유지한다.
- UI·악보 엔진·온디바이스 처리 조사에서 확인한 지원 범위·제약·측정 결과를 `docs/knowledge/frontend/`에 남긴다. 가정한 성능을 측정값으로 기록하지 않는다.
- 프로젝트를 초기화하거나 실행 방식을 바꾸면 실제 설치·실행·검증 명령과 필요한 SDK를 이 폴더의 README에 기록한다.
