# 프론트엔드 스토어 새 버전 확인과 인앱 업데이트 안내

- 작업: [S15P31E102-1237](https://ssafy.atlassian.net/browse/S15P31E102-1237)
- 상태: **제안** — 2026-11-22 우동균 결정(스토어 직접 조회, 선택 업데이트, Play 라이브러리 추가). 다른 프론트 개발자의 합의를 확인하면 `확정`으로 바꾸고 합의 근거를 붙인다.
- 관련 규칙: [frontend/docs/collaboration.md](../../frontend/docs/collaboration.md) 4절(새 라이브러리 도입 절차)
- 관련 작업: [S15P31E102-1119](https://ssafy.atlassian.net/browse/S15P31E102-1119)(서버 최소 지원 버전과 강제 안내, 별도 진행)

## 선택

| 용도 | 방법 | 버전 | 소스 세트 |
| --- | --- | --- | --- |
| Android: Play 스토어 새 버전 확인, 즉시 업데이트 화면 | `com.google.android.play:app-update` | `2.1.0`(Google Maven 최신, 2026-11-22 확인) | `androidMain` |
| iOS: App Store 현재 버전 확인 | iTunes Lookup API(`https://itunes.apple.com/lookup?bundleId=…&country=kr`), 기존 Ktor Darwin 엔진 | 라이브러리 추가 없음 | `iosMain`, 응답 해석은 `commonMain` |

- 앱을 켤 때 한 번 확인하고, 설치된 버전보다 새 스토어 버전이 있으면 '업데이트'·'나중에' 대화상자를 띄운다(선택 업데이트).
- '나중에'를 누른 스토어 버전은 `KeyValueStore`에 남겨 다시 묻지 않는다. 그보다 새 버전이 올라오면 다시 안내한다.
- Android '업데이트'는 Play의 즉시 업데이트 화면을 띄우고, 띄울 수 없으면 Play 스토어 앱 페이지를 연다. iOS '업데이트'는 App Store 앱 페이지를 연다.
- 확인에 실패하면 안내 없이 평소대로 시작한다. 스토어 확인이 앱 사용을 막지 않는다.

## 근거

- Play 스토어·App Store는 새 버전이 올라와도 개발자가 사용자에게 알림을 보내 주지 않는다. 자동 업데이트를 끈 사용자는 옛 버전을 계속 쓴다. 앱에는 푸시(FCM·APNs)도 버전 확인도 없었다.
- Play 스토어의 최신 버전을 앱이 알 수 있는 공식 방법은 In-App Updates뿐이다. Android 전용 라이브러리이므로 `commonMain`이 아닌 `androidMain`에만 추가했고, 공통 코드는 `core/update`의 `StoreUpdates` 인터페이스만 안다.
- iOS에는 인앱 업데이트 API가 없다. iTunes Lookup은 인증 없이 App Store의 현재 버전과 앱 페이지 주소를 돌려준다. 앱 서버 쿠키가 Apple로 가지 않도록 쿠키를 끈 별도 Darwin 클라이언트를 쓴다.
- 대안
  - 서버 버전 정책 API(앱 서버가 플랫폼별 최신·최소 버전을 내려줌): 강제 업데이트까지 가능하지만 백엔드 API·운영 설정이 필요하다. 최소 지원 버전과 강제 안내는 S15P31E102-1119에서 따로 다룬다.
  - Android도 스토어 페이지만 열기: Play의 최신 버전을 알 방법이 없어 새 버전이 있는지 판단할 수 없다.

## 영향과 남은 일

- 스토어에서 설치하지 않은 앱은 안내하지 않는다. Android QA APK는 Play가 업데이트 없음이나 오류로 답한다. iOS는 App Store 영수증이 있는 설치본만 확인하므로 Ad Hoc IPA·TestFlight·개발 설치본은 같은 Bundle ID라도 안내하지 않는다.
- iOS의 App Store 설치 판정에 쓰는 `appStoreReceiptURL`은 iOS 18부터 지원 중단 예정이다. 대체 API(StoreKit 2 `AppTransaction`)는 Swift 전용이라 지금은 쓰지 않는다. 동작하지 않게 되면 안내가 뜨지 않는 쪽으로 실패한다.
- iTunes Lookup 결과는 App Store 출시 직후 몇 시간 늦게 바뀔 수 있다.
- Android의 실제 동작은 Play에서 설치한 앱으로만 확인할 수 있다(내부 앱 공유 또는 테스트 트랙). iOS는 App Store 설치본에서만 확인할 수 있다. 확인 결과는 S15P31E102-1237에 남긴다.
