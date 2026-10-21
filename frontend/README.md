# frontend

> Kotlin Multiplatform + Compose Multiplatform으로 만드는 **얼쑤** 앱 (Android · iOS · Web)

화면과 로직은 대부분 `shared` 모듈에 **한 번만** 작성합니다. `androidApp` · `iosApp` · `webApp`은 각 플랫폼에서 `shared`의 `App()`을 띄워 주는 껍데기 역할만 합니다.

## 📌 기본 정보

| 항목 | 값 |
| --- | --- |
| 패키지 | `com.ssafy.eolsu` |
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.5.1 (Wrapper로 자동 설치) |
| Android | minSdk 24 (Android 7.0), targetSdk 37 |
| iOS | 18.2 이상 |
| Web | Kotlin/Wasm (최신 브라우저), Kotlin/JS (구형 브라우저 대비) |

버전은 모두 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)에서 관리합니다.

## 🛠 필요한 도구

| 대상 | 도구 |
| --- | --- |
| 공통 | Android Studio 최신 버전(또는 IntelliJ IDEA + Kotlin Multiplatform 플러그인) |
| 공통 | JDK 21 — 없으면 Gradle이 첫 빌드 때 자동으로 내려받습니다 ([`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties)) |
| Android | Android SDK (Android Studio 설치 시 함께 설치) |
| iOS | **Mac + Xcode** — Windows에서는 iOS 빌드·실행이 불가능합니다 |
| Web | Chrome, Firefox, Safari, Edge 최신 버전 |

## 🚀 처음 열기

1. Android Studio에서 **저장소 루트가 아니라 `frontend/` 폴더**를 엽니다. Gradle 프로젝트의 루트가 `frontend/`입니다.
2. 첫 Gradle 동기화(Sync)에서 Gradle, JDK, 라이브러리를 내려받으므로 시간이 걸립니다.
3. `local.properties`(내 PC의 Android SDK 경로)는 Android Studio가 자동으로 만듭니다. 커밋하지 않습니다.
4. 상단 실행 목록에서 `androidApp`을 골라 에뮬레이터에서 실행합니다. "Click me!" 버튼이 있는 예제 화면이 뜨면 준비 완료입니다.

## ▶️ 실행 방법

IDE 상단의 실행 목록을 쓰거나, `frontend/`에서 아래 명령을 실행합니다.

| 대상 | 명령 |
| --- | --- |
| Android | `./gradlew :androidApp:assembleDebug` |
| Web (Wasm, 권장) | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` |
| Web (JS, 구형 브라우저) | `./gradlew :webApp:jsBrowserDevelopmentRun` |
| iOS | Mac에서 [`iosApp`](iosApp)을 Xcode로 열고 실행 |

## ✅ 테스트

| 대상 | 명령 |
| --- | --- |
| Android | `./gradlew :shared:testAndroidHostTest` |
| Web (Wasm) | `./gradlew :shared:wasmJsTest` |
| Web (JS) | `./gradlew :shared:jsTest` |
| iOS (Mac에서만) | `./gradlew :shared:iosSimulatorArm64Test` |

## 📁 폴더 구조

```
frontend/
├── settings.gradle.kts         # 모듈 등록 (shared, androidApp, webApp)
├── build.gradle.kts            # 루트 빌드 설정 (플러그인 선언만)
├── gradle.properties           # 빌드 옵션 (메모리, 캐시)
├── gradle/
│   ├── libs.versions.toml      # 라이브러리·플러그인 버전 관리
│   ├── gradle-daemon-jvm.properties  # Gradle이 쓸 JDK 버전 (21)
│   └── wrapper/                # Gradle 버전 고정
├── gradlew, gradlew.bat        # Gradle 실행 스크립트 (Mac·Linux / Windows)
│
├── shared/                     # ★ 공통 UI·로직 — 코드 대부분이 여기
│   ├── build.gradle.kts        # 빌드 대상 플랫폼, 소스 세트별 라이브러리
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/ssafy/eolsu/   # 공통 코드 (화면, 로직)
│       │   └── composeResources/         # 공통 리소스 (이미지, 폰트, 문구)
│       ├── androidMain/        # Android 전용 코드
│       ├── iosMain/            # iOS 전용 코드 (MainViewController.kt: iOS에 화면을 넘겨주는 입구)
│       ├── jsMain/             # Web(JS) 전용 코드
│       ├── wasmJsMain/         # Web(Wasm) 전용 코드
│       ├── commonTest/         # 공통 테스트
│       ├── androidHostTest/    # Android 테스트 (PC에서 실행)
│       ├── iosTest/            # iOS 테스트
│       └── webTest/            # Web 테스트 (JS·Wasm 공용)
│
├── androidApp/                 # Android 껍데기
│   ├── build.gradle.kts        # 앱 ID, minSdk, 버전 번호
│   └── src/main/
│       ├── AndroidManifest.xml # 권한, 화면 방향, 앱 아이콘
│       ├── kotlin/.../MainActivity.kt  # App()을 띄움
│       └── res/                # 앱 아이콘, 앱 이름
│
├── iosApp/                     # iOS 껍데기 (Xcode 프로젝트, Mac 전용)
│   ├── iosApp.xcodeproj/
│   ├── Configuration/Config.xcconfig   # 번들 ID, 개발팀 ID
│   └── iosApp/
│       ├── iOSApp.swift        # 앱 시작점
│       ├── ContentView.swift   # MainViewController를 띄움
│       ├── Info.plist          # 권한 문구 등 앱 설정
│       └── Assets.xcassets/    # 앱 아이콘
│
└── webApp/                     # Web 껍데기
    ├── build.gradle.kts
    └── src/webMain/
        ├── kotlin/.../main.kt  # 브라우저에 App()을 띄움
        └── resources/
            ├── index.html      # 페이지 제목, 로딩 화면
            └── styles.css
```

## ✍️ 코드는 어디에 쓰나요?

| 하려는 것 | 위치 |
| --- | --- |
| 화면, 화면 상태(ViewModel), 비즈니스 로직 | `shared/src/commonMain/kotlin` |
| 플랫폼 API가 필요한 기능 (마이크, 권한 등) | 인터페이스는 `commonMain`, 구현은 `androidMain` · `iosMain` · `jsMain` · `wasmJsMain` |
| JS·Wasm 둘 다 쓰는 웹 공통 코드 | `shared/src/webMain` 폴더를 만들어서 작성 |
| 이미지, 폰트, 문구 | `shared/src/commonMain/composeResources/` 아래 `drawable/`, `font/`, `values/` |
| 앱 권한, 아이콘, 앱 이름, 화면 방향 | `androidApp` / `iosApp` / `webApp` |

### 공통 코드와 플랫폼 코드 잇기

- **작은 기능**: `expect` / `actual`을 씁니다. 예제의 `Platform.kt`(공통 선언)와 `Platform.android.kt` · `Platform.ios.kt` 등(플랫폼별 구현)이 이 방식입니다.
- **큰 기능**(마이크 입력 등): `commonMain`에 인터페이스를 두고, 플랫폼별 클래스가 구현합니다. 테스트할 때 가짜 구현으로 바꾸기 쉽습니다.

### 리소스 사용

`composeResources/`에 파일을 넣고 빌드하면 접근 코드가 자동으로 생깁니다. 모든 플랫폼에서 같은 방식으로 씁니다.

```kotlin
import eolsu.shared.generated.resources.Res
import eolsu.shared.generated.resources.compose_multiplatform

Image(painterResource(Res.drawable.compose_multiplatform), null)
```

### 라이브러리 추가

1. `gradle/libs.versions.toml`에 버전과 라이브러리를 등록합니다.
2. `shared/build.gradle.kts`의 해당 소스 세트(`commonMain.dependencies` 등)에 추가합니다.
3. `commonMain`에 넣는 라이브러리는 **Android · iOS · JS · Wasm을 모두 지원**해야 빌드됩니다. 지원 여부는 [klibs.io](https://klibs.io)에서 확인합니다.
4. `libs.versions.toml`은 모두가 건드리는 파일이므로, 라이브러리를 추가하기 전에 팀에 공유합니다.

## 🧹 지금 들어 있는 예제 파일

마법사가 만든 샘플입니다. 실제 화면을 만들면서 지우거나 바꿉니다.

| 파일 | 내용 |
| --- | --- |
| `shared/src/commonMain/.../App.kt` | 버튼 하나 있는 샘플 화면 (함수 `App()`은 각 플랫폼이 부르므로 이름은 유지) |
| `Greeting.kt`, `GreetingUtil.kt` | 인사 문구 샘플 |
| `Platform.kt`, `Platform.*.kt` | `expect` / `actual` 샘플 |
| `composeResources/drawable/compose-multiplatform.xml` | 샘플 이미지 |
| `Shared*Test.kt`, `SharedLogic*Test.kt` | 샘플 테스트 |

## ⚠️ 주의할 점

- **Windows에서는 iOS 코드(`iosMain`, `iosApp`)를 컴파일할 수 없습니다.** iOS 쪽이 깨져도 Windows에서는 알 수 없으니, Mac 사용자가 주기적으로 빌드해서 확인합니다.
- **`gradlew` 실행 권한**: Windows에서 커밋하면 실행 권한이 빠질 수 있습니다. `gradlew`를 새로 추가하거나 교체할 때는 `git update-index --chmod=+x frontend/gradlew`를 실행한 뒤 커밋합니다.
- **커밋하지 않는 파일**: `local.properties`, `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `xcuserdata/` ([`.gitignore`](.gitignore)에 등록됨)
- **커밋하는 파일**: 웹을 처음 빌드하면 생기는 `kotlin-js-store/`(웹 의존성 lock 파일)는 커밋합니다.
- **iOS 번들 ID**: 지금은 마법사 기본값(`com.ssafy.eolsu.eolsu` + 개발팀 ID)입니다. Mac 담당자가 `iosApp/Configuration/Config.xcconfig`에서 개발팀 ID를 넣을 때 함께 정리합니다.

## 📚 참고

- [Kotlin Multiplatform 시작하기](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/)
- [Kotlin/Wasm](https://kotl.in/wasm/)
