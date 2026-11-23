# Android 출시 빌드의 R8 코드 축소·난독화와 측정 빌드

- 작업: [S15P31E102-1120](https://ssafy.atlassian.net/browse/S15P31E102-1120) · 작업 기록: [plans/S15P31E102-1120](../../plans/S15P31E102-1120.md)
- 계기: 2026-11-23 Play Console 프로덕션 화면에 "DEX 코드 최적화가 기준점 미만, 난독화 0%" 확인 필요 항목이 떴다. 비율이 25% 미만이면 공개 상태에 영향이 있을 수 있고 해결 기한은 2027년 2월이다. 원인은 프로젝트 생성(S15P31E102-46) 때 템플릿 기본값으로 들어온 release `isMinifyEnabled = false`였다.

## 적용한 설정

`frontend/androidApp/build.gradle.kts`

- `release`: `isMinifyEnabled = true`, `isShrinkResources = true`. `proguard-android-optimize.txt`와 `proguard-rules.pro`를 쓴다.
- `benchmark`(새 빌드 유형): `initWith(release)`로 R8·리소스 축소·`BuildConfig` 값을 그대로 받고, debug 키로 서명하며 패키지는 `com.ssafy.eolssu.benchmark`다.
    - 업로드 키 없이 로컬에서 출시와 같은 R8 결과물을 기기에 설치해 확인·측정하려고 만들었다.
    - 패키지가 달라 Play에서 받은 앱과 데이터를 지우지 않고 나란히 설치된다.
    - App Links 지문에 등록되지 않은 패키지라 간편 로그인 복귀는 되지 않는다. 기기 확인은 이메일 로그인으로 한다.
- `proguard-rules.pro`: 크래시 스택의 줄 번호를 남기는 `-keepattributes SourceFile,LineNumberTable`과 `-renamesourcefileattribute SourceFile`만 둔다.

빌드 명령(`frontend/`에서 실행):

| 목적 | 명령 | 산출물 |
| --- | --- | --- |
| 출시 AAB(Jenkins와 같은 task) | `./gradlew :androidApp:bundleRelease` | `androidApp/build/outputs/bundle/release/androidApp-release.aab` |
| 기기 확인·측정 APK | `./gradlew :androidApp:assembleBenchmark` | `androidApp/build/outputs/apk/benchmark/androidApp-benchmark.apk` |
| 매핑 파일 | 위 release 빌드에서 함께 생성 | `androidApp/build/outputs/mapping/release/mapping.txt` |

## keep 규칙을 더하지 않은 근거

R8이 지우거나 이름을 바꾸면 깨지는 지점을 코드에서 찾았고, 앱 쪽 규칙은 필요하지 않았다.

| 지점 | 확인 결과 |
| --- | --- |
| kotlinx.serialization(`@Serializable` 33개 파일, Ktor `body<T>()`) | `kotlinx-serialization-core-jvm` 1.11.0 jar의 `META-INF/com.android.tools/r8/*.pro`가 `Companion`·`serializer()`·`$$serializer`를 지킨다. 다형성 직렬화(`polymorphic`)는 쓰지 않는다. |
| 리플렉션 | `Class.forName`·`getDeclared*`·`ServiceLoader`가 앱 코드에 없다. `AudioManager::class.java`는 시스템 클래스다. |
| 화면 이동 | `navigation-compose`에 문자열 경로를 쓴다. 클래스 이름에 의존하지 않는다. |
| Koin | DSL 람다로 객체를 만든다. 리플렉션 생성이 없다. |
| enum `valueOf(name)` | 이름 문자열은 바뀌지 않고, 기본 optimize 규칙이 `values()`·`valueOf()`를 지킨다. |
| JNI·네이티브 | 앱 `.so`가 없다. `libandroidx.graphics.path.so`는 AndroidX 규칙을 따른다. |
| 리소스 이름 조회(`getIdentifier`) | 앱 코드에 없다. Compose 리소스는 `assets/composeResources/`에 있어 리소스 축소 대상이 아니다. |

R8은 참조가 빠진 클래스가 있으면 빌드를 실패시키는데, `minifyReleaseWithR8`·`minifyBenchmarkWithR8`이 경고 없이 통과했다.

## 리소스 축소(`shrinkResources`)를 켠 근거

- 앱 리소스(`androidApp/src/main/res`)는 아이콘·앱 이름·백업 규칙뿐이고, 리소스 축소 보고서(`mapping/release/resources.txt`)에서 모두 Manifest에서 닿는 리소스로 남았다.
- 줄어든 것은 R8이 지운 라이브러리 코드만 쓰던 라이브러리 리소스다. `res/`가 437KB에서 210KB로, `resources.arsc`가 697KB에서 157KB로 줄었다.
- 크기 이득은 약 0.8MB로 작지만 위험도 작다. 이름으로 리소스를 찾는 라이브러리를 추가할 때만 주의하면 된다(아래 "앞으로 확인할 때").

## 매핑 파일(`mapping.txt`)

- AGP가 release AAB의 `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`에 자동으로 넣는다. 11-23 빌드에서 87.7MB(압축 전)가 들어간 것을 확인했다.
- Play Console은 AAB를 올릴 때 이 파일을 함께 받아 Android vitals 크래시 스택을 원래 이름으로 되돌린다. Jenkins `eolssu-android-release`가 보관하는 AAB에도 같이 들어 있으므로 별도 업로드는 필요 없다.
- 로컬에서 스택을 되돌릴 때는 그 빌드의 AAB에서 `proguard.map`을 꺼내 SDK의 `retrace` 도구에 넘긴다. 매핑은 빌드마다 다르므로 다른 빌드의 매핑을 쓰지 않는다.

## 측정 결과

입력: 11-23 `origin/develop` `70c64b1a3` 기준, 같은 PC(Windows)·같은 Gradle 설정. "R8 끔"은 같은 코드에서 `isMinifyEnabled`·`isShrinkResources`만 끈 빌드다.

### 크기

| 산출물 | R8 끔 | R8 켬 | 변화 |
| --- | --- | --- | --- |
| release APK(서명 전) | 33,552,613 B | 20,092,169 B | -40.1% |
| release AAB | 32,994,784 B | 25,772,666 B | -21.9% |
| AAB 중 dex 합계(압축 전) | 45,382,536 B | 6,224,088 B | -86.3% |

- AAB 크기에는 사용자에게 내려가지 않는 매핑 파일이 포함되어 있다. 사용자가 받는 크기는 APK 쪽 변화에 가깝다.
- `assets/composeResources`(25.5MB, 이미지·음원)는 R8과 무관해 그대로다. 남은 APK 크기의 대부분이 이 자산이다.

### 콜드 스타트

기기: Galaxy S10(SM-G973N), Android 12(API 31). 두 빌드 모두 `benchmark` 유형(debug 서명, `com.ssafy.eolssu.benchmark`)으로 같은 위치에 설치했다. 매회 `am force-stop` 뒤 `am start -W`의 `TotalTime`을 쟀고, 첫 실행 1회는 버렸다.

| 조건 | ART 컴파일 상태 | R8 끔 중앙값 | R8 켬 중앙값 |
| --- | --- | --- | --- |
| 강제 `verify`(AOT 없음) | verify | 545ms | 392ms |
| `compile -m speed-profile -f`(참조 프로필이 없어 verify로 됨) | verify | 529ms | 403ms |
| `compile -m speed -f`(전체 AOT, 2회) | speed | 422ms, 415ms | 338ms, 336ms |
| 새로 설치(1차) | R8 끔 verify, R8 켬 speed-profile | 549ms | 314ms |
| 새로 설치(2차) | R8 끔 verify, R8 켬 speed-profile | 539ms | 321ms |

- 같은 컴파일 상태끼리 비교하면 R8을 켠 빌드가 verify에서 약 25%, 전체 AOT에서 약 20% 빠르다.
- 재현: 두 benchmark APK를 차례로 설치하고 `adb shell cmd package compile -f -m <verify|speed> com.ssafy.eolssu.benchmark` 뒤, `am force-stop`과 `am start -W -n com.ssafy.eolssu.benchmark/com.ssafy.eolssu.MainActivity`를 8~10회 반복한다.
- 새로 설치할 때 R8 빌드는 APK에 든 프로필로 `speed-profile` 컴파일되고, R8을 끈 빌드는 `verify`로 남았다. 실제 사용자 설치 상태는 Play의 클라우드 프로필에 따라 달라지므로 이 차이를 그대로 사용자 체감 수치로 쓰지 않는다.
- 측정 시작 직후 `install -r`로 덮어 설치한 R8 끔 빌드가 315ms로 나온 1회 측정은 컴파일 상태를 기록하지 않아 비교에서 뺐다.

## 앞으로 확인할 때

- debug 빌드에는 R8이 꺼져 있어 R8 문제를 잡지 못한다. 새 라이브러리를 넣거나 이름으로 클래스·리소스를 찾는 코드를 쓰면 `assembleBenchmark`로 기기에서 한 번 확인한다.
- 이름으로 리소스를 읽는 라이브러리(예: Firebase가 `google_app_id` 같은 문자열을 이름으로 읽음)를 Gradle 플러그인 없이 값 파일로 넣으면, 리소스 축소가 그 값을 지울 수 있다. 값 파일에 `tools:keep`을 달거나 benchmark 빌드에서 동작을 확인한다.
- 크래시가 R8 때문으로 보이면 `mapping.txt`로 스택을 되돌린 뒤, 실제로 지워지거나 이름이 바뀐 대상에만 좁은 keep 규칙을 더한다. 패키지 전체를 `-keep`하지 않는다.
- Baseline Profile 생성(macrobenchmark 모듈)은 새 Gradle 모듈이 필요해 팀 합의 뒤 별도 Task로 한다.
