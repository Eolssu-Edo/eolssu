# Android 휴대폰 세로 고정과 태블릿 회전

- 작업: [S15P31E102-110](https://ssafy.atlassian.net/browse/S15P31E102-110)
- 적용 위치: `frontend/androidApp/.../MainActivity.kt`, 정책은 [아키텍처 문서](../../../frontend/docs/architecture.md) 9장

## 결론

- 휴대폰만 세로로 고정하려면 매니페스트의 `screenOrientation` 대신 실행 중에 기기 최소 너비(`Configuration.smallestScreenWidthDp`)로 판단해 `requestedOrientation`을 정한다. 600dp 미만은 `SCREEN_ORIENTATION_PORTRAIT`, 그 외는 `SCREEN_ORIENTATION_UNSPECIFIED`로 둔다.
- 매니페스트로 고정하면 Android 15 이하 태블릿도 세로로 묶인다.
- Android 16(API 36)을 대상으로 하는 앱은 최소 너비 600dp 이상 화면에서 `screenOrientation`·`setRequestedOrientation()` 같은 방향 제한이 무시된다. API 36에서는 `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`로 잠시 끌 수 있지만 API 37 이상을 대상으로 하면 끌 수 없다. 얼쑤는 `targetSdk` 37이다.
- 액티비티를 다시 만들어도 이전에 요청한 방향이 남으므로, 태블릿에서는 `UNSPECIFIED`로 명시적으로 풀어야 화면 분할을 끝내거나 폴더블을 편 뒤에도 회전된다.

출처: [Android 16 동작 변경](https://developer.android.com/about/versions/16/behavior-changes-16), [앱 방향·화면 비율·크기 조절](https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability)

## 에뮬레이터 확인 (2026-10-23)

- 환경: 태블릿 AVD `Eolssu_Tablet_API_37`(2560×1600, 320dpi, 최소 너비 800dp, `google_apis_playstore` x86_64, Android 17 / API 37), 디버그 APK.
- 태블릿: 자동 회전을 끄고 `settings put system user_rotation 0·1·3`으로 돌렸다. 앱 구성이 `w1280dp h800dp land` ↔ `w800dp h1280dp port`로 바뀌고 `requestedOrientation=SCREEN_ORIENTATION_UNSPECIFIED`였다.
- 휴대폰: 같은 AVD에서 `wm size 1080x2400`, `wm density 420`(최소 너비 411dp)으로 바꾸고 앱을 다시 실행했다. `requestedOrientation=SCREEN_ORIENTATION_PORTRAIT`였고, 자동 회전을 켠 채 `adb emu rotate`로 네 번 돌려도 앱은 `w411dp h914dp port`, 화면은 `1080x2400`으로 유지됐다. 같은 상태에서 설정 앱은 `2400x1080`으로 회전했다.

### 태블릿 에뮬레이터로 휴대폰을 흉내 낼 때 주의

- 태블릿 시스템 이미지는 디스플레이 설정 `ignoreOrientationRequest=true`를 켜 둔다(`adb shell wm get-ignore-orientation-request`). 이 상태에서는 `wm size`로 휴대폰 크기를 만들어도 화면 전체가 회전하고 앱만 세로 레터박스로 남는다. 실제 휴대폰과 휴대폰 AVD는 이 값이 `false`다.
- 휴대폰 동작을 보려면 `adb shell wm set-ignore-orientation-request false`로 끄고 확인한 뒤, `wm size reset`, `wm density reset`, `wm set-ignore-orientation-request true`로 되돌린다.

## 미확인

- Android 15 이하 태블릿에서 고정이 풀리는지. API 37 이미지에서는 큰 화면의 방향 고정이 어차피 무시되므로 확인할 수 없다.
- 실제 휴대폰·태블릿 기기, 폴더블 접기·펴기, 화면 분할 종료 직후의 회전.
- iOS(`UISupportedInterfaceOrientations_iPhone`을 세로만 둠)는 Mac이 없어 확인하지 않았다.
