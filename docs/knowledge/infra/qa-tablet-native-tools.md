# Android16 QA 밝기·한글 도구 호환성

2026-11-05 · S15P31E102-498 · 실제 조건: Galaxy Tab S9 FE SM-X510, Android16/API36; 비교 휴대폰 Galaxy S10 SM-G973N, Android12/API31; ADB37.0.1; scrcpy5.0.1.

## 원인과 선택

기존 밝기 도구는 태블릿에서 종료137/Killed를 반환했다. Android crash buffer에서 `IPowerManager.acquireWakeLock` 7인자 메서드의 `NoSuchMethodException`을 확인했다. 같은 태블릿에서 공식 scrcpy의 인코더 조회가 정상 동작하고 `c2.exynos.h264.encoder`가 확인돼, 일반적인 ADB/보안 정책 차단으로 판단하지 않았다.

[AOSP Android16 IPowerManager](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/core/java/android/os/IPowerManager.aidl)는 `IWakeLockCallback`이 추가된8인자 계약이다. 기존7인자 조회가 없을 때만8인자로 전환하고 callback은 null로 전달한다. 잠금 종류·15초 기기 lease·stdin EOF 해제는 유지한다. 전역 화면 타임아웃/밝기/보안 설정은 변경하지 않는다.

[AOSP Android16 IClipboard](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/core/java/android/content/IClipboard.aidl)의 `setPrimaryClip`은 attributionTag와 deviceId가 추가된5인자다. 기존3인자가 없을 때5인자로 전환한다. attributionTag=null, 실제 userId, deviceId=0을 사용한다. 입력 텍스트만 클립보드에 쓰며 기존 클립보드를 읽거나 외부로 보내지 않는다.

## 재현과 실제 확인

`infra/qa-phone/setup-screen.ps1`로 EDO1의 기존 JDK21/Android SDK를 사용해 도구를 빌드한다. 시작 도구는 소스와 바이너리 SHA256을 검증한다. 검증된 동일 도구를 두 중계의 명시적인 ToolRoot로 사용한다. Android31/36에서 각각 READY/active와 오류 없음, 종료 시 자체 lease 해제를 확인했다. 휴대폰의 기존 화면 타임아웃86400000ms는 유지됐다.

태블릿의 OS 설정 검색 입력칸을 웹에서 누르고 `태블릿 한글 입력 확인`을 전송했다. 실제 화면과 UIAutomator에서 문자열을 확인했다. 가로 회전 후 웹 영상/좌표가 갱신됐고 새 제어권에서 실제 설정 앱 터치가 동작했다. 실험 후 accelerometer_rotation=1, user_rotation=0을 복원했다.

검증 도구 메타데이터: textSourceSha256 `258c0a30f823a98c94d0a62ebce9c6e7334d6c2acaaf51453a103b4aac4553b6`, sourceSha256 `2fe1f0a9581a37221185b527542c3bf49e87e0bdf083b81ed43582881b78a0c6`, binary SHA256 `df31e3adfeae9db895331546058361ef87d3662d58cf5e8f1258c98e25cf4148`.

## 한계

숨김 AIDL은 모든 Android 버전에 대한 공개 호환 계약이 아니다. 실제 확인 범위는 API31과36이다. 도구 메타데이터의 기존 verifiedApi31 값이 API36 지원 전체를 증명하는 것은 아니며 위 실기기 기록을 함께 본다. 제품 앱의 녹음·재생·AI 평가 연속성은 이번 작업에서 검증하지 않았다. 실제 Wi-Fi OFF/ON 뒤 포트 광고가 사라지는 경우의 완전 무인 복구도 보장하지 않는다.

[Task498 계획·검증](../../plans/S15P31E102-498.md) · [운영·복구 안내](../../../infra/qa-phone/OPERATIONS.md)
