# frontend

> Kotlin Multiplatform + Compose Multiplatform으로 만드는 **얼쑤** 앱 (Android · iOS · Web)

화면과 로직은 대부분 `shared` 모듈에 **한 번만** 작성합니다. `androidApp` · `iosApp` · `webApp`은 각 플랫폼에서 `shared`의 `App()`을 띄워 주는 껍데기 역할만 합니다.

## 📌 기본 정보

| 항목 | 값 |
| --- | --- |
| 패키지 | `com.ssafy.eolssu` |
| Kotlin | 2.4.20 |
| Compose Multiplatform | 1.12.1 |
| Android Gradle Plugin | 9.1.1 |
| Gradle | 9.5.1 (Wrapper로 자동 설치) |
| Android | minSdk 24 (Android 7.0), targetSdk 37 |
| iOS | 18.2 이상 |
| Web | Kotlin/Wasm (최신 브라우저), Kotlin/JS (구형 브라우저 대비) |

버전은 모두 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)에서 관리합니다.

Gradle 프로젝트명과 앱 표시명은 `eolssu`입니다. Android applicationId는 `com.ssafy.eolssu`이며, 이전 `com.ssafy.eolsu` 앱과 별개로 설치될 수 있습니다. iOS도 bundle identifier가 변경되므로 Mac에서 개발팀·서명 설정을 확인해야 합니다. 이름 변경으로 기존 앱 데이터가 자동 이전된다고 가정하지 않습니다.

## 🛠 필요한 도구

프론트엔드는 **Android Studio**로 개발합니다. Android · Web · 공통 코드는 Windows의 Android Studio에서, iOS는 Mac에서 작업합니다.

| 대상 | 도구 |
| --- | --- |
| 공통 | **Android Studio** 최신 안정 버전 (2026.2.1에서 확인) + `Kotlin Multiplatform` 플러그인 |
| 공통 | JDK 21 — 없으면 Gradle이 첫 빌드 때 자동으로 내려받습니다 ([`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties)) |
| Android | Android SDK API 37, 에뮬레이터 (Android Studio 설치 시 함께 설치) |
| Web | Chrome, Firefox, Safari, Edge 최신 버전 |
| iOS | **Mac + Xcode** + Mac용 Android Studio — Windows에서는 iOS 빌드·실행이 불가능합니다 |

## 🚀 처음 빌드하기 (Android Studio)

> ⚠️ **저장소 경로에 한글이 있으면 안 됩니다.** Windows에서는 경로에 영어가 아닌 글자가 있으면 Android Studio가 Gradle 동기화를 막습니다 (`Your project path contains non-ASCII characters`).
> 예: `C:\Users\SSAFY\Desktop\자율\S15P31E102` ❌ → `C:\Users\SSAFY\Desktop\jayul\S15P31E102` ✅

1. **Android Studio 설치**: [developer.android.com/studio](https://developer.android.com/studio)에서 최신 안정 버전을 받습니다. 처음 실행할 때 설정 마법사는 **Standard**로 진행하고, 라이선스는 항목마다 모두 **Accept**합니다. Android SDK와 에뮬레이터가 함께 설치됩니다.
2. **플러그인 설치**: **File → Settings → Plugins → Marketplace**에서 `Kotlin Multiplatform`(JetBrains)을 설치하고 재시작합니다.
3. **프로젝트 열기**: **File → Open**에서 **저장소 루트가 아니라 `frontend/` 폴더**를 고르고 **Trust**를 누릅니다.
   - 저장소 루트를 열면 Gradle 프로젝트로 인식되지 않아 실행 목록에 `androidApp`이 나오지 않습니다.
   - 왼쪽 Project 창은 처음에 **Android** 보기(모듈·파일 종류별로 묶음, `iosApp`은 안 보임)로 열립니다. 실제 폴더 구조로 보려면 창 맨 위 드롭다운에서 **Project**를 고릅니다.
4. **동기화(Sync) 기다리기**: 처음에는 Gradle, JDK, 라이브러리를 내려받아 **7~8분** 정도 걸립니다. 화면 아래 진행 표시줄이 끝날 때까지 기다립니다.
   - `local.properties`(내 PC의 Android SDK 경로)가 자동으로 생깁니다. 커밋하지 않습니다.
   - `Failed to find target android-37` 오류가 나면 **Tools → SDK Manager**에서 **Android API 37**을 설치하고 다시 동기화합니다.
5. **에뮬레이터 만들기**: 오른쪽 **Device Manager → +(Create Virtual Device)** → 폰 기기(예: Medium Phone) → API 37 시스템 이미지 → **Finish**.
6. **실행**: 상단 실행 목록에서 `androidApp`과 에뮬레이터를 고르고 ▶ **Run**(`Shift+F10`)을 누릅니다. 첫 빌드는 몇 분 걸립니다.
   - 에뮬레이터만 켜지고 앱이 뜨지 않으면 Run이 실행되지 않은 것입니다. 하단 **Build** 창에서 진행 상황과 오류를 확인합니다.
7. **성공 확인**: 예제 화면에서 **Click me!**를 누르면 `Compose: Hello, Android 37!`이 보입니다.

## 🌐 Web 실행 (Android Studio)

실행 목록에는 처음에 `androidApp`만 있습니다. 웹은 아래 둘 중 하나로 실행합니다.

- **Gradle 창**: 오른쪽 **Gradle** 창 → `webApp` → `Tasks` → `kotlin browser` → `wasmJsBrowserDevelopmentRun`을 더블클릭합니다. 한 번 실행하면 실행 목록에 추가되어 다음부터 ▶로 실행할 수 있습니다.
- **Terminal 탭**: 아래 **Terminal**에서 실행합니다 (Android Studio 터미널 기본값은 PowerShell).

  ```powershell
  .\gradlew.bat :webApp:wasmJsBrowserDevelopmentRun
  ```

빌드가 끝나면 터미널에 `Loopback: http://localhost:8080/`이 나옵니다. 브라우저에서 이 주소를 열면 앱이 보입니다. 화면이 깨지거나 동작하지 않으면 브라우저 개발자 도구(F12)의 Console을 확인합니다. 멈출 때는 실행 창의 ■(Stop)이나 `Ctrl+C`를 누릅니다.

## 🍎 iOS 실행 (Mac)

Windows에서는 iOS 코드(`iosMain`)를 작성할 수만 있고, 컴파일·실행은 Mac에서 합니다.

1. Mac에 **Xcode**를 설치하고 **한 번 실행**합니다. Xcode를 업데이트한 뒤에도 한 번 실행합니다.
2. Mac에 **Android Studio**와 `Kotlin Multiplatform` 플러그인을 설치합니다.
3. 같은 저장소의 `frontend/` 폴더를 엽니다. 동기화가 끝나면 실행 목록에 iOS 실행 구성이 생깁니다.
4. iOS 시뮬레이터를 고르고 ▶ Run을 누릅니다. Android Studio가 내부에서 Xcode를 불러 빌드합니다.
5. Swift 파일이나 `Info.plist`(마이크 권한 문구 등)를 고칠 때는 `iosApp`을 Xcode로 엽니다.

iOS 담당은 develop을 주기적으로 받아 iOS 빌드가 깨지지 않았는지 확인합니다. 공통 코드만 바뀌어도 iOS 빌드가 깨질 수 있습니다.

## 🔨 명령어로 빌드·실행

`frontend/`에서 실행합니다. Git Bash는 `./gradlew`, PowerShell·cmd는 `.\gradlew.bat`을 씁니다.
Android Studio 밖에서 실행할 때는 `JAVA_HOME`이 JDK 17 이상을 가리켜야 합니다 (Gradle 9 요구 사항).

| 하려는 것 | 명령 | 결과 |
| --- | --- | --- |
| Android 디버그 APK 빌드 | `./gradlew :androidApp:assembleDebug` | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |
| 연결된 기기·에뮬레이터에 설치 | `./gradlew :androidApp:installDebug` | 설치만 합니다. 앱 아이콘을 눌러 실행합니다 |
| Web 개발 서버 (Wasm, 권장) | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` | `http://localhost:8080/`에 개발 서버를 띄웁니다 |
| Web 개발 서버 (JS, 구형 브라우저) | `./gradlew :webApp:jsBrowserDevelopmentRun` | 로컬 개발 서버를 띄웁니다 |
| Web 배포용 빌드 (Wasm) | `./gradlew :webApp:wasmJsBrowserDistribution` | `webApp/build/dist/wasmJs/productionExecutable/` (압축 전 약 13MB) |
| iOS | Mac에서만 가능 — 위의 "iOS 실행 (Mac)" 참고 | — |

- Android Studio 없이 명령줄만으로 빌드하려면 [Android 명령줄 도구](https://developer.android.com/studio#command-line-tools-only)를 `C:\Users\<사용자>\AppData\Local\Android\Sdk\cmdline-tools\latest`에 풀고 `sdkmanager --licenses`로 라이선스를 수락한 뒤, `frontend/local.properties`에 `sdk.dir=C:/Users/<사용자>/AppData/Local/Android/Sdk`처럼 **슬래시(`/`) 경로**로 적습니다. 백슬래시를 그대로 쓰면 경로가 깨져 `파일 이름, 디렉터리 이름 또는 볼륨 레이블 구문이 잘못되었습니다` 오류가 납니다. 플랫폼·빌드 도구는 첫 빌드 때 AGP가 자동으로 내려받습니다 (2026-10-22 Windows에서 확인).
- 처음 하는 웹 빌드는 Node.js와 웹 의존성을 내려받아 오래 걸립니다 (확인한 PC에서 약 6분).
- 첫 웹 빌드가 `An input file was expected to be present but it doesn't exist` 오류로 실패한 적이 있습니다. 같은 명령을 다시 실행하면 성공했습니다.
- 웹을 처음 빌드하면 `kotlin-js-store/wasm/yarn.lock`이 생깁니다. 모두가 같은 웹 의존성 버전을 쓰도록 **커밋합니다**.

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
│       │   ├── kotlin/com/ssafy/eolssu/   # 공통 코드. app / feature / data / core 패키지로 고정
│       │   └── composeResources/         # 공통 리소스 (drawable, font, values)
│       ├── androidMain/        # Android 전용 코드 (platform/audio·storage·permission)
│       ├── iosMain/            # iOS 전용 코드 (platform/…, MainViewController.kt: iOS에 화면을 넘겨주는 입구)
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

공통 코드 안의 패키지 구조(`app / feature / data / core`), 각 영역의 역할, 화면 파일 이름 규칙은 [docs/architecture.md](docs/architecture.md)에, 두 개발자가 담당을 나누고 공동 파일을 조율하는 규칙은 [docs/collaboration.md](docs/collaboration.md)에 있습니다. 새 파일을 만들기 전에 두 문서를 먼저 봅니다.

## ✍️ 코드는 어디에 쓰나요?

| 하려는 것 | 위치 |
| --- | --- |
| 화면, 화면 상태(ViewModel), 비즈니스 로직 | `shared/src/commonMain/kotlin`의 `feature/<기능>`, `data/<기능>`, `core` — 세부 기준은 [docs/architecture.md](docs/architecture.md) |
| 플랫폼 API가 필요한 기능 (마이크, 권한 등) | 인터페이스는 `commonMain`의 `core/audio` 등, 구현은 `androidMain` · `iosMain`의 `platform/` (웹은 `jsMain` · `wasmJsMain`) |
| JS·Wasm 둘 다 쓰는 웹 공통 코드 | `shared/src/webMain` 폴더를 만들어서 작성 |
| 이미지, 폰트, 문구 | `shared/src/commonMain/composeResources/` 아래 `drawable/`, `font/`, `values/` |
| 앱 권한, 아이콘, 앱 이름, 화면 방향 | `androidApp` / `iosApp` / `webApp` |

### 공통 코드와 플랫폼 코드 잇기

- **작은 기능**: `expect` / `actual`을 씁니다. 예제의 `Platform.kt`(공통 선언)와 `Platform.android.kt` · `Platform.ios.kt` 등(플랫폼별 구현)이 이 방식입니다.
- **큰 기능**(마이크 입력 등): `commonMain`에 인터페이스를 두고, 플랫폼별 클래스가 구현합니다. 테스트할 때 가짜 구현으로 바꾸기 쉽습니다.

### 리소스 사용

`composeResources/`에 파일을 넣고 빌드하면 접근 코드가 자동으로 생깁니다. 모든 플랫폼에서 같은 방식으로 씁니다.

```kotlin
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.compose_multiplatform

Image(painterResource(Res.drawable.compose_multiplatform), null)
```

### 테마와 글꼴

색·글꼴·모서리는 `core/theme`의 `EolssuTheme`에 있습니다. `App()` 전체가 이 테마로 감싸져 있어 Material 컴포넌트는 와이어프레임 색을 씁니다. 재생(파랑)·녹음(빨강)·현재 위치(노랑) 같은 전용 색은 `EolssuTheme.colors`, 율명·곡 제목 세리프 글자는 `EolssuTheme.serif`로 꺼냅니다. 글자로 쓰는 색 조합은 `EolssuColorsContrastTest`가 대비 4.5:1 이상인지 확인합니다.

Web은 기기 글꼴로 한글·한자를 그리지 않기 때문에 글꼴 파일을 `composeResources/font/`에 넣었습니다.

| 파일 | 용도 | 글자 범위 |
| --- | --- | --- |
| `pretendard_regular·semibold·bold.otf` | 본문·버튼·사용자가 입력한 글자 | Pretendard 1.3.9 원본, 한글 11,172자 |
| `noto_serif_kr_semibold·bold.otf` | 율명·앱 이름·곡 제목 | Noto Serif KR에서 자주 쓰는 한글 2,350자·율명 한자·영숫자만 남김 |

- 세리프 글꼴에는 한글이 2,350자만 있으므로 **사용자가 입력한 글자는 세리프로 쓰지 않습니다.** Pretendard에는 한자가 없으므로 **율명은 세리프로 씁니다.**
- 율명 한자는 [`tools/fonts/build_fonts.py`](tools/fonts/build_fonts.py)의 `SERIF_HANJA`에 있는 글자만 들어 있습니다. 새 율명 표기를 쓰려면 여기에 추가하고 다시 만듭니다. Noto Serif KR에 없는 글자(탁황 `僙` 등)는 넣을 수 없습니다.
- 라이선스는 둘 다 SIL Open Font License 1.1이며 원문은 [`tools/fonts/licenses/`](tools/fonts/licenses/)에 있습니다.

글꼴을 다시 만들 때(`frontend/`에서, Python 3.11 이상):

```bash
python -m pip install --user fonttools==4.60.1
SRC="${TMPDIR:-/tmp}/eolssu-fonts-src"; mkdir -p "$SRC"
for w in Regular SemiBold Bold; do curl -fL -o "$SRC"/Pretendard-$w.otf "https://cdn.jsdelivr.net/npm/pretendard@1.3.9/dist/public/static/Pretendard-$w.otf"; done
for w in SemiBold Bold; do curl -fL -o "$SRC"/NotoSerifKR-$w.otf "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Serif/SubsetOTF/KR/NotoSerifKR-$w.otf"; done
PYTHONUTF8=1 python -P tools/fonts/build_fonts.py "$SRC"
```

스크립트는 원본 파일의 SHA-256을 확인한 뒤 만들고, 같은 원본이면 항상 같은 파일을 만듭니다. 원본 폴더(`$SRC`)는 저장소 밖 임시 폴더라 커밋되지 않습니다. Noto CJK 원본이 바뀌어 해시가 다르면 스크립트가 멈추니, 새 원본을 확인한 뒤 해시를 갱신합니다.

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
- **iOS 번들 ID**: 지금은 마법사 기본값(`com.ssafy.eolssu.eolssu` + 개발팀 ID)입니다. Mac 담당자가 `iosApp/Configuration/Config.xcconfig`에서 개발팀 ID를 넣을 때 함께 정리합니다.

## 📚 참고

- [Kotlin Multiplatform 시작하기](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/)
- [Kotlin/Wasm](https://kotl.in/wasm/)


## 웹 자동 빌드·테스트·배포

운영 Jenkins는 develop의 백엔드 잡이 성공한 뒤 동일 SHA로 프론트 웹 잡을 시작합니다. `:shared:wasmJsTest`, `:shared:jsTest`, `:webApp:wasmJsBrowserDistribution`을 실행하고 JUnit·웹 산출물을 보관한 뒤 후보 자산 검증과 운영 배포를 수행합니다. 프론트 잡 이름은 `eolssu-frontend`이며 Linux·JDK 21·Google Chrome(`CHROME_BIN=/usr/bin/google-chrome`) 환경입니다. Android·iOS 패키지는 이 흐름에 포함하지 않습니다.

동작·실패 복구·인증서 갱신·배포 SHA 확인은 [운영 문서](../infra/runtime/README.md#프론트-웹-자동-배포), 실제 검증 상태는 [Task 73](../docs/plans/S15P31E102-73.md)을 따릅니다. 초기 샘플 테스트 4개의 통과가 업무 기능 전체를 검증한다는 뜻은 아닙니다.
