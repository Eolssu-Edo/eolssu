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

Gradle 프로젝트명은 `eolssu`, Android 앱 표시명은 `얼쑤`입니다. Android applicationId는 `com.ssafy.eolssu`이며, 이전 `com.ssafy.eolsu` 앱과 별개로 설치될 수 있습니다. iOS도 bundle identifier가 변경되므로 Mac에서 개발팀·서명 설정을 확인해야 합니다. 이름 변경으로 기존 앱 데이터가 자동 이전된다고 가정하지 않습니다.

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
7. **성공 확인**: 신규 설치에서는 동의 화면이 먼저 열립니다. 실제 약관·인증을 거쳐 홈으로 이동합니다. 약관 API가 비어 있으면 오류와 재시도 화면이 보이며 로그인 성공으로 간주하지 않습니다.

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

- Android Studio 없이 명령줄만으로 빌드하려면 [Android 명령줄 도구](https://developer.android.com/studio#command-line-tools-only)를 SDK의 `cmdline-tools/` 아래에 설치합니다. `frontend/local.properties`에는 `sdk.dir=C\:/Users/<사용자>/AppData/Local/Android/Sdk`처럼 **콜론을 이스케이프하고 슬래시(`/`) 경로**로 적습니다. Windows 경로를 그대로 쓰면 경로 해석 또는 lint `PropertyEscape` 검사에 실패합니다. 설치 명령은 해당 버전의 `--help`로 확인합니다(cmdline-tools 23은 `android sdk` 제공).
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
│       ├── androidMain/        # Android 전용 코드 (platform/audio·storage·permission, App(context): MainActivity가 부르는 입구)
│       ├── iosMain/            # iOS 전용 코드 (platform/…, MainViewController.kt: iOS에 화면을 넘겨주는 입구)
│       ├── jsMain/             # Web(JS) 전용 코드
│       ├── wasmJsMain/         # Web(Wasm) 전용 코드
│       ├── webMain/            # Web(JS·Wasm) 공통 코드 (platform/storage, App(onNavHostReady): main.kt가 부르는 입구)
│       ├── commonTest/         # 공통 테스트
│       ├── androidHostTest/    # Android 테스트 (PC에서 실행)
│       ├── iosTest/            # iOS 테스트
│       └── webTest/            # Web 테스트 (JS·Wasm 공용)
│
├── androidApp/                 # Android 껍데기
│   ├── build.gradle.kts        # 앱 ID, minSdk, 버전 번호
│   └── src/main/
│       ├── AndroidManifest.xml # 권한, 화면 방향, 앱 아이콘
│       ├── kotlin/.../MainActivity.kt  # App(context)를 띄움
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
| 플랫폼 API가 필요한 기능 (마이크, 저장소, 권한 등) | 인터페이스는 `commonMain`의 `core/audio`·`core/storage` 등, 구현은 `androidMain` · `iosMain` · `webMain`의 `platform/`, 등록은 각 소스 세트의 `platform/PlatformModule.kt` |
| JS·Wasm 둘 다 쓰는 웹 공통 코드 | `shared/src/webMain` |
| 이미지, 폰트, 문구 | `shared/src/commonMain/composeResources/` 아래 `drawable/`, `font/`, `values/` |
| 앱 권한, 아이콘, 앱 이름, 화면 방향 | `androidApp` / `iosApp` / `webApp` |

### 공통 코드와 플랫폼 코드 잇기

- **작은 기능**: `expect` / `actual`을 씁니다. 공통 코드에 `expect`로 선언하고 `androidMain` · `iosMain` · `jsMain` · `wasmJsMain`(또는 `webMain`)에 `actual` 구현을 둡니다.
- **큰 기능**(마이크 입력, 저장소 등): `commonMain`에 인터페이스를 두고, 플랫폼별 클래스가 구현합니다. 구현은 각 소스 세트의 `platform/PlatformModule.kt`(Koin 모듈)에 등록합니다. 테스트할 때 가짜 구현으로 바꾸기 쉽습니다. 연결 방법은 [docs/architecture.md](docs/architecture.md) 8절을 봅니다.

### 리소스 사용

`composeResources/`에 파일을 넣고 빌드하면 접근 코드가 자동으로 생깁니다. 모든 플랫폼에서 같은 방식으로 씁니다.

```kotlin
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_brand

Text(stringResource(Res.string.home_brand))   // composeResources/values/strings.xml의 문구
```

### 테마와 글꼴

색·글꼴·모서리는 `core/theme`의 `EolssuTheme`에 있습니다. 최신 연두 디자인의 primary `#ADBE31`, 바탕 `#F9FAF9`, 먹색 `#27221E`를 사용합니다. 밝은 연두 위에는 먹색, 먹색·재생·녹음 위에는 별도 밝은 전경 토큰을 사용합니다. `EolssuColorsContrastTest`는 실제 주요 텍스트 색 조합 4.5:1과 선택 경계 3:1을 검사하며 모든 화면의 접근성 검증을 대신하지 않습니다.

Web은 기기 글꼴로 한글·한자를 그리지 않기 때문에 글꼴 파일을 `composeResources/font/`에 넣었습니다.

| 파일 | 용도 | 글자 범위 |
| --- | --- | --- |
| `ibm_plex_sans_kr_regular·semibold·bold.ttf` | 본문·버튼·사용자가 입력한 글자 | Google Fonts 공식 IBM Plex Sans KR 원본 |
| `song_myung_regular.ttf` | 고정 제목 | Google Fonts 공식 Song Myung 원본 |
| `noto_serif_kr_semibold·bold.otf` | 율명·곡 제목 | 기존 Noto Serif KR 서브셋, 한글 2,350자·율명 한자·영숫자 |
| `pretendard_regular·semibold·bold.otf` | 기존 리소스 보존, 기본 본문에서는 미사용 | Pretendard 1.3.9 원본 |

- 기존 Noto Serif KR 서브셋에는 한글이 2,350자만 있으므로 **사용자가 입력한 글자는 본문 글꼴로 씁니다.** 율명은 기존 Noto Serif KR와 글리프 처리를 유지합니다.
- 율명 한자는 [`tools/fonts/build_fonts.py`](tools/fonts/build_fonts.py)의 `SERIF_HANJA`에 있는 글자만 들어 있습니다. 새 율명 표기를 쓰려면 여기에 추가하고 다시 만듭니다. Noto Serif KR에 없는 글자(탁황 `僙` 등)는 넣을 수 없습니다.
- 운지표의 청성·탁성·중청 글자 21자(`㑣㑥㑲㒇㒣㴌㳞㶋潕㶐㶂㶃㶄㴩㶌㶍㶝`는 Noto Serif KR(Windows 가변 글꼴로 확인)에 없고, `汏浹洟湳`은 원본에만 있음)는 글꼴에 넣지 않고 `feature/guide`의 `HanjaGlyph`가 변(氵·亻) + 율명으로 나눠 그립니다. 두 글꼴 모두 `♭`가 없어 계이름의 내림표는 그림으로 넣습니다.
- 글꼴 라이선스는 SIL Open Font License 1.1이며 기존 원문은 [`tools/fonts/licenses/`](tools/fonts/licenses/), 앱에 포함하는 원문은 `shared/src/commonMain/composeResources/files/licenses/`에 있습니다.

기존 Pretendard·Noto 서브셋을 다시 만들 때(`frontend/`에서, Python 3.11 이상; IBM Plex Sans KR·Song Myung에는 적용하지 않음):

```bash
python -m pip install --user fonttools==4.60.1
SRC="${TMPDIR:-/tmp}/eolssu-fonts-src"; mkdir -p "$SRC"
for w in Regular SemiBold Bold; do curl -fL -o "$SRC"/Pretendard-$w.otf "https://cdn.jsdelivr.net/npm/pretendard@1.3.9/dist/public/static/Pretendard-$w.otf"; done
for w in SemiBold Bold; do curl -fL -o "$SRC"/NotoSerifKR-$w.otf "https://raw.githubusercontent.com/notofonts/noto-cjk/main/Serif/SubsetOTF/KR/NotoSerifKR-$w.otf"; done
PYTHONUTF8=1 python -P tools/fonts/build_fonts.py "$SRC"
```

스크립트는 원본 파일의 SHA-256을 확인한 뒤 만들고, 같은 원본이면 항상 같은 파일을 만듭니다. 원본 폴더(`$SRC`)는 저장소 밖 임시 폴더라 커밋되지 않습니다. Noto CJK 원본이 바뀌어 해시가 다르면 스크립트가 멈추니, 새 원본을 확인한 뒤 해시를 갱신합니다.

### 시김새 소리

시김새 창(처음 배우는 시김새 11개)의 단소 소리는 `composeResources/files/sigimsae/*.mp3`(모노 44.1kHz 96kbps, 각 2.56초)에 미리 넣어 두고, 모든 플랫폼이 `core/audio`의 `SoundPlayer`로 그대로 틉니다. 녹음이 아니라 와이어프레임의 Web Audio 합성(`playSig`: 단소 녹음에서 잰 배음·숨 세기, 붓끝 높이를 따라가는 음높이)을 브라우저 없이 다시 계산해 만든 파일입니다. 파일 처음이 붓질 처음(0초)이고 붓질은 2.16초입니다.

`feature/guide/Sigimsae.kt`의 그림(`BasicSigimsae`)을 바꾸면 [`tools/sigimsae-sound/build_sigimsae_sounds.mjs`](tools/sigimsae-sound/build_sigimsae_sounds.mjs)의 `SIGIMSAE` 값도 고치고 다시 만듭니다(`frontend/`에서, Node 18 이상과 libmp3lame이 든 ffmpeg 필요).

```bash
node tools/sigimsae-sound/build_sigimsae_sounds.mjs
```

잡음은 고정된 씨앗을 써서 같은 입력이면 같은 소리가 납니다. MP3 바이트는 ffmpeg 판에 따라 조금 다를 수 있습니다(2026-10-29 ffmpeg 9.0.1로 만듦).

### 라이브러리 추가

1. `gradle/libs.versions.toml`에 버전과 라이브러리를 등록합니다.
2. `shared/build.gradle.kts`의 해당 소스 세트(`commonMain.dependencies` 등)에 추가합니다.
3. `commonMain`에 넣는 라이브러리는 **Android · iOS · JS · Wasm을 모두 지원**해야 빌드됩니다. 지원 여부는 [klibs.io](https://klibs.io)에서 확인합니다.
4. `libs.versions.toml`은 모두가 건드리는 파일이므로, 라이브러리를 추가하기 전에 팀에 공유합니다.

## ⚠️ 주의할 점

- **Windows에서는 iOS 코드(`iosMain`, `iosApp`)를 컴파일할 수 없습니다.** iOS 쪽이 깨져도 Windows에서는 알 수 없으니, Mac 사용자가 주기적으로 빌드해서 확인합니다.
- **`gradlew` 실행 권한**: Windows에서 커밋하면 실행 권한이 빠질 수 있습니다. `gradlew`를 새로 추가하거나 교체할 때는 `git update-index --chmod=+x frontend/gradlew`를 실행한 뒤 커밋합니다.
- **커밋하지 않는 파일**: `local.properties`, `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `xcuserdata/` ([`.gitignore`](.gitignore)에 등록됨)
- **커밋하는 파일**: 웹을 처음 빌드하면 생기는 `kotlin-js-store/`(웹 의존성 lock 파일)는 커밋합니다.
- **iOS 번들 ID**: 지금은 마법사 기본값(`com.ssafy.eolssu.eolssu` + 개발팀 ID)입니다. Mac 담당자가 `iosApp/Configuration/Config.xcconfig`에서 개발팀 ID를 넣을 때 함께 정리합니다.

## Android release 검증

곡 목록·상세는 S15P31E102-256의 `/api/songs` 계약을 사용합니다. `score` 원문을 공통 악보로 해석하며 원본 ID·tick·버전을 보존합니다. 활성 악기는 `/api/instruments`의 ID/code로 연결합니다. 조회 실패는 오류·재시도로 표시하며 샘플 곡으로 대체하지 않습니다. [계약·변환·검증 기록](../docs/knowledge/frontend/song-catalog-adapter.md)을 참고하세요.

격리된 로컬 API를 검증할 때만 아래 설정을 사용합니다. `CatalogLocalServerTest`는 환경 변수가 없으면 명시적으로 skip됩니다. 실제 서버 통합 통과를 기록하려면 V6 공개 곡 4개를 갖춘 로컬 서버를 기동하고 환경 변수를 지정해야 합니다.

```powershell
$env:EOLSSU_CATALOG_TEST_URL = 'http://127.0.0.1:18081'
./gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug '-Peolssu.debugApiBaseUrl=http://127.0.0.1:18081' --max-workers=2
adb reverse tcp:18081 tcp:18081
```

debug API 변경은 loopback 주소만 허용합니다. release는 이 속성과 관계없이 운영 HTTPS 주소를 사용하며 debug 전용 cleartext 예외를 포함하지 않습니다. 공개 곡 조회 검사는 로그인·녹음·분석 전체 흐름의 증거가 아닙니다.

현재 설정은 `com.ssafy.eolssu`, minSdk 24, target/compileSdk 37입니다. 아래 PowerShell 스크립트는 실제 Gradle task로 단위 검사·debug/release lint·debug APK·release APK/AAB를 실행합니다. 버전은 Console의 최고 사용 버전을 확인한 뒤 지정해야 하며 기본값 2/1.0.0은 제출 승인을 의미하지 않습니다.

```powershell
# 승인된 키의 경로·암호·별칭을 비밀 저장소에서 환경 변수로 주입한 뒤 실행합니다.
# EOLSSU_STORE_FILE, EOLSSU_STORE_PASSWORD, EOLSSU_KEY_ALIAS, EOLSSU_KEY_PASSWORD
./tools/build-android-release.ps1 -VersionCode 2 -VersionName '1.0.0'
python tools/inspect_android_package.py androidApp/build/outputs/apk/release/androidApp-release.apk androidApp/build/outputs/bundle/release/androidApp-release.aab
```

release 키가 없으면 이 스크립트는 중단하며 debug 키로 대체하지 않습니다. 키·비밀번호를 저장소나 명령 이력에 적지 않습니다. 서명 값이 Gradle configuration cache에 저장되지 않도록 스크립트는 `--no-configuration-cache`를 사용합니다. Compose preview 실행 도구는 Android 앱의 `debugImplementation`에만 둡니다.

최종 APK는 `apksigner verify --verbose --print-certs`, AAB는 `jarsigner -verify`와 `bundletool validate`로 검사합니다. APK의 ZIP 정렬은 `zipalign -c -P 16 -v 4`로, ELF와 패키지 안의 네이티브 라이브러리는 위 Python 도구로 검사합니다. AAB에서 `bundletool build-apks`로 만든 패키지도 설치·실행해야 합니다. CLI 플래그는 설치한 버전의 도움말로 확인하고 암호는 비밀 파일/입력 방식으로 전달합니다.

필수 완료조건은 실제 인증→곡/악보→녹음→업로드→분석→결과/이력, 실패 복구, 폰/태블릿·큰 글꼴·IME/insets·TalkBack, 실제 마이크·오디오 경합 검사입니다. 단위 검사나 APK 설치만으로 제출 준비를 완료하지 않습니다. 정책 조건과 PC/에뮬레이터·실기기 결과는 [Android release 준비 기록](../docs/knowledge/frontend/android-play-release-readiness.md)에서 분리합니다.

## 📚 참고 자료

- [Kotlin Multiplatform 시작하기](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
- [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/)
- [Kotlin/Wasm](https://kotl.in/wasm/)


## 웹 자동 빌드·테스트·배포

운영 Jenkins는 develop의 백엔드 잡이 성공한 뒤 동일 SHA로 프론트 웹 잡을 시작합니다. `:shared:wasmJsTest`, `:shared:jsTest`, `:webApp:wasmJsBrowserDistribution`을 실행하고 JUnit·웹 산출물을 보관한 뒤 후보 자산 검증과 운영 배포를 수행합니다. 프론트 잡 이름은 `eolssu-frontend`이며 Linux·JDK 21·Google Chrome(`CHROME_BIN=/usr/bin/google-chrome`) 환경입니다. Android·iOS 패키지는 이 흐름에 포함하지 않습니다.

동작·실패 복구·인증서 갱신·배포 SHA 확인은 [운영 문서](../infra/runtime/README.md#프론트-웹-자동-배포), 실제 검증 상태는 [Task 73](../docs/plans/S15P31E102-73.md)을 따릅니다. 이 잡의 테스트 통과가 업무 기능 전체를 검증한다는 뜻은 아닙니다.
