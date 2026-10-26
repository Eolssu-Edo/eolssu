# 프론트엔드 아키텍처

- 작업: `S15P31E102-59`
- 기준: Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 9.1.1 (`gradle/libs.versions.toml`)
- 실행 지침은 [AGENTS.md](../AGENTS.md), 협업 규칙은 [collaboration.md](collaboration.md), 빌드·실행 명령은 [README](../README.md)를 따른다.

이 문서는 2026-10-22에 적용한 구조를 설명한다. 구조를 바꾸면 이 문서와 `AGENTS.md`를 같은 변경에서 함께 고친다.

## 1. 전체 모습: 모듈 → 소스 세트 → 패키지

React 프로젝트와 달리 KMP 프로젝트는 세 겹으로 나뉜다. 이름이 비슷해서 헷갈리기 쉬우니 먼저 구분한다.

| 겹 | 무엇인가 | 이 프로젝트에서 | 바꾸려면 |
| --- | --- | --- | --- |
| Gradle 모듈 | 따로 빌드되는 단위. `settings.gradle.kts`의 `include(...)` | `shared`, `androidApp`, `webApp` (+ Xcode 프로젝트 `iosApp`) | 팀 합의 + Gradle 설정 변경 |
| 소스 세트 | 한 모듈 안에서 "어느 플랫폼에서 컴파일되는 코드인가" | `commonMain`, `androidMain`, `iosMain`, `jsMain`, `wasmJsMain`, `webMain`, `commonTest` 등 | `shared/build.gradle.kts`의 타깃 설정 |
| 패키지 | 소스 세트 안의 폴더 = Kotlin `package` 선언 | `app`, `feature`, `data`, `core`, `platform` | 폴더를 만들고 `package` 선언을 맞추면 끝 (Gradle 변경 없음) |

핵심은 **이번에 고정한 `app / feature / data / core`는 `shared` 모듈 안의 패키지 구분**이라는 점이다. 각각을 별도 Gradle 모듈로 만들지 않는다. 패키지는 컴파일러가 의존 방향을 막아 주지 않으므로, 의존 방향은 문서와 리뷰로 지킨다(아래 7절).

```
frontend/
├── shared/        ★ 공통 UI·로직·리소스. 코드 대부분이 여기
├── androidApp/    Android 진입점 (MainActivity가 App() 호출)
├── iosApp/        iOS 진입점 (Xcode 프로젝트, ContentView가 MainViewController() 호출)
└── webApp/        Web 진입점 (main.kt가 App() 호출)
```

## 2. 소스 세트의 역할

| 소스 세트 | 역할 | 넣지 않는 것 |
| --- | --- | --- |
| `commonMain` | 공통 화면, 화면 상태, 사용자 행동 처리, 공통 데이터 처리, 플랫폼 기능의 공통 인터페이스 | Android·Apple 전용 API import |
| `androidMain` | Android 전용 API를 쓰는 구현과 연결 (`platform/`) | 화면 로직 |
| `iosMain` | iOS 전용 API를 쓰는 구현과 연결 (`platform/`), `MainViewController` | 화면 로직 |
| `jsMain` / `wasmJsMain` | 웹 전용 구현. 둘 다 쓰는 코드는 `webMain`을 만들어 둔다 | 화면 로직 |
| `commonTest` | 공통 로직 테스트 | 플랫폼 API가 필요한 테스트 |
| `androidHostTest` / `iosTest` / `webTest` | 플랫폼별 테스트. 기존 설정 유지 | — |

`commonMain`에 라이브러리를 넣으면 **네 타깃(Android·iOS·JS·Wasm) 모두** 지원해야 빌드된다. 모바일 구조를 정리했다고 웹 타깃을 빼지 않는다.

Android 앱 실행부는 `androidApp/`, iOS 앱 실행부는 `iosApp/`이다. `shared/src/androidMain`·`shared/src/iosMain`은 공통 모듈의 플랫폼 구현일 뿐 앱 프로젝트가 아니다.

## 3. 적용한 패키지 구조

`shared/src/commonMain/kotlin/com/ssafy/eolssu/` 기준이다. `.gitkeep`만 있는 폴더는 아직 구현이 없고 위치만 고정한 것이다.

```
com/ssafy/eolssu/
  App.kt                      # 공통 UI 진입점. Koin 시작 → 테마 → 화면 이동 그래프. 각 플랫폼이 App()을 호출하므로 이름 유지

  app/
    navigation/               # 화면 이동 정의. 기능 사이의 연결은 여기서 조정
    di/                       # 객체 생성과 연결 (Repository, 플랫폼 구현 주입)

  core/
    ui/
      component/              # 여러 기능이 쓰는 버튼·카드 같은 공통 UI
      score/                  # 공통 정간보 렌더러
      adaptive/               # 창 크기 판정 (휴대폰/태블릿 배치 전환 기준)
    theme/                    # 색·글꼴·모양 테마
    network/                  # HTTP 클라이언트 설정, 공통 응답 처리
    audio/                    # 녹음·재생의 공통 인터페이스
    storage/                  # 로컬 저장의 공통 인터페이스
    model/                    # 여러 기능이 함께 쓰는 앱 모델 (예: Song, Note)

  data/
    song/                     # 곡 목록·상세 API, DTO, Repository
    practice/                 # 연주 제출·평가 결과·연습 기록 API, DTO, Repository
    user/                     # 인증·프로필 API, DTO, Repository

  feature/
    home/                     # 홈 화면(현재 악기 선택, 주요 기능 진입)
    guide/                    # 운지법 보기·정간보 쉽게 읽기 같은 학습 안내 화면(2차)
    auth/                     # 로그인·회원가입 화면
    song/                     # 곡 목록·상세 화면
    practice/                 # 정간보 표시·녹음·제출 화면
    result/                   # 평가 결과 화면
    history/                  # 연습 기록 화면
    profile/                  # 프로필 화면
```

feature와 data는 1:1이 아니다. 기능마다 같은 이름의 data 폴더를 만들지 않는다.

| feature | 사용하는 data |
| --- | --- |
| `home`, `guide` | 아직 없음. 홈의 악기 선택은 화면 상태로만 둔다 |
| `auth`, `profile` | `data/user` |
| `song` | `data/song` |
| `practice`, `result`, `history` | `data/practice` |

리소스와 플랫폼 구현:

```
shared/src/commonMain/composeResources/
  drawable/                   # 이미지 (snake_case 파일명 → Res.drawable.<이름>)
  font/                       # 폰트 (→ Res.font.<이름>). 첫 폰트를 넣을 때 생성
  values/                     # strings.xml 등 문구 (→ Res.string.<키>). 첫 strings.xml을 넣을 때 생성

shared/src/androidMain/kotlin/com/ssafy/eolssu/platform/
  audio/  storage/  permission/     # core의 인터페이스를 Android API로 구현

shared/src/iosMain/kotlin/com/ssafy/eolssu/platform/
  audio/  storage/  permission/     # core의 인터페이스를 iOS API로 구현
```

`composeResources` 아래 폴더 이름은 Compose Multiplatform이 정한 이름(`drawable`, `font`, `values`, `files`)만 쓴다. 파일명은 소문자·숫자·밑줄만 사용하고, 빌드하면 `eolssu.shared.generated.resources.Res`에 접근자가 생긴다. 리소스 폴더에는 `.gitkeep`을 두지 않는다. 생성기가 `values/.gitkeep`을 리소스로 읽다가 `Unknown resource type: 'values'`로 빌드를 실패시키는 것을 확인했다(2026-10-22, Compose 1.12.1). `font/`·`values/`는 첫 폰트·`strings.xml`을 넣을 때 만든다.

## 4. 각 영역의 책임과 의존 방향

| 영역 | 책임 | 금지 |
| --- | --- | --- |
| `app` | 앱 최상위 구성, 화면 이동, 객체 생성과 연결. 각 기능을 잇는 역할 | 기능별 API 처리, 화면 세부 로직 |
| `feature` | 기능별 화면·상태·ViewModel·전용 UI. 데이터는 Repository를 통해 사용 | HTTP 클라이언트·플랫폼 녹음 API를 화면에서 직접 호출 |
| `data` | 기능별 API 요청, 응답 DTO, 변환, Repository, 로컬 데이터 처리 | Compose 화면·ViewModel 의존 |
| `core` | 여러 기능이 함께 쓰는 UI·테마·기반 도구·공통 인터페이스 | 특정 `feature`·`data`·`app` 의존 |

의존 방향(화살표 방향으로만 import한다). 순서는 `app → feature → data → core`이고, 오른쪽 영역은 왼쪽 영역을 모른다.

```
app     ──► feature, data, core
feature ──► data, core
data    ──► core
core    ──► (없음)
```

`app → data`는 `app/di`가 Repository를 만들어 feature에 넘기기 위해 필요하다.

- feature끼리는 서로의 내부 구현을 참조하지 않는다. "결과 화면에서 곡 상세로 이동" 같은 연결은 `app/navigation`에서 한다.
- `domain` 계층·UseCase는 처음부터 만들지 않는다. 반복 로직이 커지면 별도 구조 변경으로 다룬다.

모델의 위치:

| 모델 | 위치 | 예 |
| --- | --- | --- |
| 여러 기능이 실제로 공유하는 앱 모델 | `core/model` | `Song`, `Note`, `Score` |
| 서버 요청·응답 DTO | 해당 `data/<기능>` 내부 | `SongListResponse`, `EvaluationRequest` |
| 한 화면만 쓰는 모델 | 해당 `feature/<기능>` 내부 | `PracticeUiState`, `RecordingPhase` |

## 5. 화면 파일 구성 규칙

상태와 동작이 있는 화면의 기준 예시:

```
feature/practice/
  PracticeRoute.kt            # ViewModel 연결, 상태 수집, 화면 이동 등 외부 동작 연결
  PracticeScreen.kt           # 전달받은 상태와 콜백으로 UI 표시
  PracticeViewModel.kt        # 화면 상태와 사용자 행동 처리
  PracticeUiState.kt          # 이 화면이 표시할 상태
  component/
    RecordingControls.kt      # 이 기능에서만 쓰는 UI
  layout/
    PracticeCompactContent.kt   # 휴대폰 배치
    PracticeExpandedContent.kt  # 태블릿 배치 (배치가 크게 달라질 때만)
```

- `Screen`은 ViewModel을 직접 받지 않고 상태와 콜백을 받는다. 그래야 Preview와 테스트에서 가짜 상태로 바로 그릴 수 있다.
- 정적 화면까지 네 파일을 의무적으로 만들지 않는다. 상태가 생기면 `UiState`, 행동이 생기면 `ViewModel`, 이동·연결이 생기면 `Route`로 분리한다.
- 이름 규칙: 클래스·파일 `PascalCase`, 함수·변수 `camelCase`, 패키지 소문자. 기능 내부 UI 폴더는 `component`로 통일한다.

## 6. 배치 예시: 주요 기능별로 코드가 가는 곳

| 하려는 일 | 위치 |
| --- | --- |
| 곡 목록 화면과 검색 상태 | `feature/song/SongListRoute.kt`, `SongListScreen.kt`, `SongListViewModel.kt`, `SongListUiState.kt` |
| 곡 목록 API 호출과 응답 DTO | `data/song/SongApi.kt`, `data/song/SongDto.kt`, `data/song/SongRepository.kt` |
| 곡 모델 (목록·연습·결과가 함께 사용) | `core/model/Song.kt` |
| 정간보 그리기 (연습·결과 화면이 함께 사용) | `core/ui/score/JeongganboView.kt` |
| 연습 화면의 녹음 버튼 | `feature/practice/component/RecordingControls.kt` |
| 녹음 시작·정지 인터페이스 | `core/audio/AudioRecorder.kt` (interface) |
| Android 녹음 구현 | `androidMain/.../platform/audio/AndroidAudioRecorder.kt` |
| iOS 녹음 구현 | `iosMain/.../platform/audio/IosAudioRecorder.kt` |
| 녹음 파일을 서버에 제출 | `data/practice/PracticeApi.kt`, `PracticeRepository.kt` |
| 평가 결과 화면 | `feature/result/ResultRoute.kt`, `ResultScreen.kt`, `ResultViewModel.kt`, `ResultUiState.kt` |
| 결과 화면에서 "다시 연습" 눌렀을 때 연습 화면으로 이동 | `app/navigation/` (feature 사이 연결) |
| 연습 기록 목록 | `feature/history/`, 데이터는 `data/practice/` |
| 모든 화면이 쓰는 기본 버튼 | `core/ui/component/EolssuButton.kt` |
| 색·글꼴 | `core/theme/` |

## 7. React와의 역할 비교

두 개발자가 React 경험이 있을 때 대응시켜 보면 다음과 같다. 완전히 같지는 않으니 "비슷한 책임"으로만 받아들인다.

| React | 이 프로젝트 | 차이 |
| --- | --- | --- |
| `pages/` | `feature/<기능>/`의 `Route`·`Screen` | 페이지 하나가 Route(연결)와 Screen(표시)으로 나뉜다 |
| `components/` (공통) | `core/ui/component/` | 여러 기능이 쓰는 것만. 한 기능 전용은 `feature/<기능>/component/` |
| `hooks/` (상태·로직) | `feature/<기능>/<기능>ViewModel.kt` + `UiState` | 화면 상태는 ViewModel이 들고, `StateFlow`로 노출한다 |
| `store/` (전역 상태) | 없음. 필요하면 `data`의 Repository가 상태를 보관 | 전역 store를 먼저 만들지 않는다 |
| `api/`, `services/` | `data/<기능>/` | DTO와 변환도 함께 둔다 |
| `styles/`, 테마 | `core/theme/` | CSS 파일 대신 Compose 테마 객체 |
| `utils/` | 역할에 맞는 `core/<하위>/` | 최상위 `utils`를 만들지 않는다 |
| `react-router` 라우트 정의 | `app/navigation/` | `navigation-compose`(JetBrains 멀티플랫폼판)로 경로를 정의한다 |
| Context/DI | `app/di/` | Koin 모듈에 객체를 등록하고, 화면은 `koinViewModel()`로 ViewModel을 받는다 |

## 8. 공통 코드와 플랫폼 코드 잇는 방법

플랫폼 API가 필요한 기능(녹음·재생·저장·권한)은 **인터페이스 + 플랫폼 구현 + 주입**을 기본으로 한다.

```kotlin
// commonMain: core/audio/AudioRecorder.kt
interface AudioRecorder {
    suspend fun start()
    suspend fun stop(): RecordedAudio
}

// androidMain: platform/audio/AndroidAudioRecorder.kt
class AndroidAudioRecorder(private val context: Context) : AudioRecorder { /* AudioRecord 사용 */ }

// iosMain: platform/audio/IosAudioRecorder.kt
class IosAudioRecorder : AudioRecorder { /* AVAudioRecorder 사용 */ }
```

- ViewModel은 `AudioRecorder` 인터페이스만 받는다. 테스트에서는 가짜 구현을 넣는다.
- 구현 객체를 만들어 넘기는 곳은 `app/di/`다. DI는 Koin을 쓴다([결정 문서](../../docs/decisions/frontend-navigation-di.md)). 각 플랫폼 진입점(`MainActivity`, `MainViewController`, `main.kt`)의 플랫폼 구현을 Koin에 등록하는 방식은 처음 플랫폼 구현을 연결하는 작업에서 정한다.
- `expect`/`actual`은 "플랫폼 이름 문자열"처럼 작은 값이나, 구현 객체를 만드는 팩토리 함수 하나처럼 **연결 지점 한 곳**에만 쓴다. 모든 클래스에 붙이지 않는다. 기존 `Platform.kt`가 `expect`/`actual` 예시다.
- 웹 타깃도 `commonMain` 인터페이스를 컴파일하므로, 인터페이스를 추가하면 `jsMain`·`wasmJsMain`에도 구현이나 "지원 안 함" 구현이 필요한지 확인한다.

## 9. 휴대폰·태블릿 대응

- 같은 화면 상태와 ViewModel을 공유한다. 기기별 프로젝트나 `phone/`·`tablet/` 최상위 패키지를 만들지 않는다.
- 사용 가능한 창 크기에 따라 배치만 바꾼다. 판정 기준은 `core/ui/adaptive/WindowLayout.kt`에 한 곳으로 두고, 각 화면은 `currentWindowLayout()`의 결과만 받아 `layout/`의 Compact·Expanded 내용을 고른다. 기기 종류(휴대폰·태블릿)나 화면 방향으로 판정하지 않는다.
- 배치 차이가 작으면(열 수, 여백 정도) `layout/` 없이 `Screen` 안에서 처리한다.

### 기준값

창 크기는 앱이 쓰는 창 전체(`LocalWindowInfo.containerSize`)를 dp로 바꿔 판정한다. 근거는 태블릿 와이어프레임 Task [S15P31E102-101](https://ssafy.atlassian.net/browse/S15P31E102-101)의 결정이다.

| 이름 (`WindowBreakpoints`) | 값 | 의미 |
| --- | --- | --- |
| 너비 `Compact` | 600dp 미만 | 휴대폰. 와이어프레임의 휴대폰 배치 |
| 너비 `Medium` (`MediumMinWidth`) | 600dp 이상 840dp 미만 | 태블릿 세로. 와이어프레임 세로 아트보드(800×1280) |
| 너비 `Expanded` (`ExpandedMinWidth`) | 840dp 이상 | 태블릿 가로·넓은 웹 창. 와이어프레임 가로 아트보드(1280×800) |
| 내용 최대 너비 (`ContentMaxWidth`) | 1120dp | 넓은 창에서는 내용을 이 너비로 가운데에 둔다 |
| 낮은 높이 (`LandscapeLayoutMinHeight`, `isShortHeight`) | 650dp 미만 | 태블릿 가로 배치에서 12정간 줄을 정간 44dp로 세로 스크롤 없이 넣으려면 약 650dp가 필요하다. 너비는 넓지만 높이가 낮은 창(납작한 웹 창, 태블릿 화면 분할)을 가려낸다 |

- 낮은 높이의 창에서 무엇을 보여줄지(정간을 줄일지, 세로 스크롤을 허용할지, 세로 배치로 돌릴지)는 아직 정하지 않았다. 지금은 `isShortHeight` 판정 값만 제공하고 화면은 이 값을 쓰지 않는다.
- 연습·결과 화면도 같은 기준값을 쓴다. 기준값을 바꾸면 이 표와 `WindowBreakpoints`를 함께 고치고 상대 개발자에게 알린다.

### 화면 방향

- 휴대폰은 연습 화면까지 세로로 고정하고, 태블릿은 세로·가로를 모두 지원한다(권영민 결정, 2026-10-23).
- iOS: `iosApp.xcodeproj`의 `INFOPLIST_KEY_UISupportedInterfaceOrientations_iPhone`을 세로만 두고, iPad 값은 네 방향을 유지한다.
- Android: `MainActivity`가 기기 최소 너비(`smallestScreenWidthDp`)가 600dp 미만일 때만 세로로 고정한다. 매니페스트의 `screenOrientation`은 Android 15 이하 태블릿까지 고정하므로 쓰지 않는다. Android 16(API 36)부터는 최소 너비 600dp 이상 화면에서 앱의 방향 고정이 무시되고, API 37을 대상으로 하면 이를 끌 수 없다([Android 16 동작 변경](https://developer.android.com/about/versions/16/behavior-changes-16)). 그래서 태블릿은 처음부터 고정하지 않는다.
- 방향을 고정해도 창 크기 판정은 그대로 쓴다. 화면 분할·폴더블·웹 창에서는 방향과 관계없이 창 너비가 바뀐다.

## 10. 코드로 강제되는 것과 사람이 지키는 것

| 규칙 | 강제 방식 |
| --- | --- |
| `commonMain`에서 Android·Apple API import 불가 | 컴파일러가 막는다 |
| `commonMain` 라이브러리는 네 타깃 모두 지원해야 함 | Gradle 의존성 해결이 실패한다 |
| 리소스 폴더·파일명 규칙 | Compose 리소스 생성기가 실패한다 |
| `app / feature / data / core` 의존 방향 | **강제되지 않는다.** 문서와 코드 리뷰로 지킨다 |
| feature 사이 직접 참조 금지 | **강제되지 않는다.** 문서와 코드 리뷰로 지킨다 |
| 파일명·폴더명 규칙 | **강제되지 않는다.** 문서와 코드 리뷰로 지킨다 |

같은 패키지 트리 안에서는 어디서든 import가 가능하므로, 문서만으로 구조 변경이 기술적으로 차단되지는 않는다. 의존 방향을 도구로 검사하는 것은 필요해지면 별도로 검토한다.

## 11. 이번 적용에서 결정한 것과 근거

- 기존 모듈 구성(`shared`, `androidApp`, `webApp`, `iosApp`)으로 목적을 충족하므로 모듈을 분리하거나 이름을 바꾸지 않았다.
- Compose Multiplatform 공유 UI와 ViewModel(`lifecycle-viewmodel-compose`) 설정은 이미 있었다. 화면 이동·DI·HTTP 라이브러리는 없었고, 이번 작업 범위가 구조 고정이므로 추가하지 않았다. 해당 패키지는 `.gitkeep`으로 위치만 잡았다.
- 마법사 샘플(`Greeting`, `Platform`, 샘플 테스트)은 제시된 구조에 대응하는 자리가 없고 기존 지침이 "실제 기능으로 대체"로 정하고 있어 루트 패키지에 그대로 두었다. 실행 동작 보존을 위해 삭제하지 않았다. (이후 [S15P31E102-93](../../docs/plans/S15P31E102-93.md)에서 홈 화면으로 대체하고 삭제했다.)
- 웹 타깃(`jsMain`, `wasmJsMain`, `webTest`)과 관련 빌드 설정은 그대로 유지했다.
- 구조 채우기용 빈 클래스는 만들지 않았다.
