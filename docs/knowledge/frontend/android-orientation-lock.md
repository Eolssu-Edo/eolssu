# Android 휴대폰 세로 고정과 태블릿 회전

- 작업: [S15P31E102-110](https://ssafy.atlassian.net/browse/S15P31E102-110), 회전 시 재생성 방지 [S15P31E102-1033](https://ssafy.atlassian.net/browse/S15P31E102-1033)
- 적용 위치: `frontend/androidApp`의 `AndroidManifest.xml`(`configChanges`)과 `MainActivity.kt`, 정책은 [아키텍처 문서](../../../frontend/docs/architecture.md) 9장

## 결론

- 휴대폰만 세로로 고정하려면 매니페스트의 `screenOrientation` 대신 실행 중에 기기 최소 너비(`Configuration.smallestScreenWidthDp`)로 판단해 `requestedOrientation`을 정한다. 600dp 미만은 `SCREEN_ORIENTATION_PORTRAIT`, 그 외는 `SCREEN_ORIENTATION_UNSPECIFIED`로 둔다.
- 매니페스트로 고정하면 Android 15 이하 태블릿도 세로로 묶인다.
- Android 16(API 36)을 대상으로 하는 앱은 최소 너비 600dp 이상 화면에서 `screenOrientation`·`setRequestedOrientation()` 같은 방향 제한이 무시된다. API 36에서는 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`로 잠시 끌 수 있지만 API 37 이상을 대상으로 하면 끌 수 없다. 얼쑤는 `targetSdk` 37이다.
- 이전에 요청한 방향이 남으므로, 태블릿에서는 `UNSPECIFIED`로 명시적으로 풀어야 화면 분할을 끝내거나 폴더블을 편 뒤에도 회전된다.

출처: [Android 16 동작 변경](https://developer.android.com/about/versions/16/behavior-changes-16), [앱 방향·화면 비율·크기 조절](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability)

## 회전할 때 액티비티를 다시 만들지 않는다 (2026-11-16 결정)

- 결정: `MainActivity`에 `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize"`를 둔다. 회전·화면 분할·폴더블 접기·펴기처럼 창 크기만 바뀌면 액티비티가 다시 만들어지지 않고 `onConfigurationChanged`가 온다.
- 근거: 액티비티를 다시 만들면 Compose 화면에 ON_STOP이 온다. 연습 화면(`PracticeRoute`)은 ON_STOP을 화면 이탈로 보고 `onLeaveScreen()`에서 모범 연주를 해제하고 녹음을 끊는다. 그래서 태블릿에서 모범 연주 재생 중 돌리면 재생이 멈췄고, 평가 녹음 중 돌리면 '연주가 중단되었어요' 창이 떴다. 함께 `App`의 `remember` 상태가 초기화되어 로딩 화면이 깜빡이고 회원 정보를 다시 받았으며, 연습 시간 측정(`practiceTimeQueue.onAppStart()`)도 다시 시작됐다.
- 다른 방법: ON_STOP에서 `isChangingConfigurations`를 확인해 회전을 거르는 방법은 플랫폼 값을 공통 코드에 넘겨야 하고, 녹음(MediaRecorder) 세션을 새 액티비티로 넘길 수 없어 채택하지 않았다.
- 배치: 공통 코드의 창 크기 판정(`core/ui/adaptive`의 `currentWindowLayout()`)은 `LocalWindowInfo.containerSize`를 읽으므로 액티비티를 다시 만들지 않아도 새 창 크기로 다시 계산된다. `rememberSaveable(widthClass)`처럼 너비 구간을 키로 쓰는 상태도 키가 바뀌면 다시 만들어진다. 화면 이동 상태와 ViewModel은 그대로 남는다.
- 방향 재판정: `onConfigurationChanged`에서 `lockPortraitOnPhone()`을 다시 부른다. 폴더블을 펴거나 화면 분할을 시작·끝내 최소 너비가 600dp를 넘나들면 고정 여부가 바뀐다. 같은 값은 다시 요청하지 않는다. 화면 분할 중에는 Android가 방향 요청을 따르지 않는다.
- 넣지 않은 설정: 다크 모드(`uiMode`), 글꼴 크기(`fontScale`), 언어(`locale`), 화면 밀도(`density`), 키보드 연결은 기본 동작(재생성)으로 둔다. 이 변경은 보통 설정 앱으로 나갔다 올 때 일어나 그때 이미 ON_STOP으로 재생·녹음이 멈춘다. 다시 만들면 Compose 밖의 Android 리소스(앱 이름·테마·시스템 바)까지 확실히 새 값으로 바뀌므로, 회전 문제를 고치려고 처리 범위를 넓히지 않는다.
- 주의: 이 액티비티에서 Compose 밖의 Android 리소스(`res/values-land` 같은 한정자 리소스, XML View)를 쓰면 회전 때 갱신되지 않는다. 현재는 쓰지 않는다. 회전에 따라 달라질 값은 Compose에서 창 크기로 판정한다.
- 영향 범위: Android 앱의 모든 화면. 기초 배우기의 단계 통과 상태가 가로 2단 배치로 바뀔 때 사라지는 문제는 화면 이동(목록·단계 화면의 ViewModel 소유자 변경) 때문이라 이 결정으로 해결되지 않는다([S15P31E102-1030](https://ssafy.atlassian.net/browse/S15P31E102-1030)). iOS·Web에는 해당하지 않는다.

## 에뮬레이터 확인 (2026-10-23)

- 환경: 태블릿 AVD `Eolssu_Tablet_API_37`(2560×1600, 320dpi, 최소 너비 800dp, `google_apis_playstore` x86_64, Android 17 / API 37), 디버그 APK.
- 태블릿: 자동 회전을 끄고 `settings put system user_rotation 0·1·3`으로 돌렸다. 앱 구성이 `w1280dp h800dp land` ↔ `w800dp h1280dp port`로 바뀌고 `requestedOrientation=SCREEN_ORIENTATION_UNSPECIFIED`였다.
- 휴대폰: 같은 AVD에서 `wm size 1080x2400`, `wm density 420`(최소 너비 411dp)으로 바꾸고 앱을 다시 실행했다. `requestedOrientation=SCREEN_ORIENTATION_PORTRAIT`였고, 자동 회전을 켠 채 `adb emu rotate`로 네 번 돌려도 앱은 `w411dp h914dp port`, 화면은 `1080x2400`으로 유지됐다. 같은 상태에서 설정 앱은 `2400x1080`으로 회전했다.

### 태블릿 에뮬레이터로 휴대폰을 흉내 낼 때 주의

- 태블릿 시스템 이미지는 디스플레이 설정 `ignoreOrientationRequest=true`를 켜 둔다(`adb shell wm get-ignore-orientation-request`). 이 상태에서는 `wm size`로 휴대폰 크기를 만들어도 화면 전체가 회전하고 앱만 세로 레터박스로 남는다. 실제 휴대폰과 휴대폰 AVD는 이 값이 `false`다.
- 휴대폰 동작을 보려면 `adb shell wm set-ignore-orientation-request false`로 끄고 확인한 뒤, `wm size reset`, `wm density reset`, `wm set-ignore-orientation-request true`로 되돌린다.

## 실기기 확인 (2026-11-16, S15P31E102-1033)

- 환경: 디버그 APK(md5 `babba26c…`), Galaxy Tab S9 FE(SM-X510), Galaxy S10(SM-G973N, Android 12). 자동 회전을 끄고 `user_rotation 0·1`로 돌렸다.
- 태블릿 연습 화면 '장단으로 연습' 재생: 세로 0:02 → 가로 0:06 → 다시 세로 0:11로 재생이 이어졌고, 가로에서 곡 정보 칸이 붙은 2단 배치로 바뀌었다. 앱 프로세스 ID가 그대로였다.
- 태블릿 연주 평가 녹음: 세로 0:04에서 가로로 돌린 뒤에도 '입력 감지'·'연주 중단' 상태로 0:23까지 녹음이 이어졌고 '연주가 중단되었어요' 창 없이 '연주를 마쳤어요'로 끝났다.
- 태블릿 홈: 세로·가로·세로 회전에서 배치가 각각 바뀌었다.
- S10: 앱을 띄운 채 `user_rotation 1`을 요청해도 화면 회전이 `ROTATION_0`(세로)으로 유지됐다.
- 모범 연주는 이 시각 '재생에 실패했어요'가 떠서 재생 자체를 확인하지 못했다. 같은 `onLeaveScreen()` 경로를 타는 장단 재생으로 대신 확인했다.

## 미확인

- Android 15 이하 태블릿에서 고정이 풀리는지. API 37 이미지에서는 큰 화면의 방향 고정이 어차피 무시되므로 확인할 수 없다.
- 폴더블 접기·펴기, 화면 분할 시작·종료 때 `onConfigurationChanged`의 세로 고정 재판정(기기 없음).
- 곡 목록·정간보 읽기·운지법 보기 화면의 회전, 모범 연주 재생 중 회전.
- iOS(`UISupportedInterfaceOrientations_iPhone`을 세로만 둠)는 Mac이 없어 확인하지 않았다.
