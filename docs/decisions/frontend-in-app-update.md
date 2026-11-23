# 프론트엔드 스토어 새 버전 확인과 인앱 업데이트 안내

- 작업: [S15P31E102-1237](https://ssafy.atlassian.net/browse/S15P31E102-1237)(처음 도입), [S15P31E102-1257](https://ssafy.atlassian.net/browse/S15P31E102-1257)(실행마다 안내, 앱 안 업데이트, FCM 토픽 푸시)
- 상태: **제안**
  - 2026-11-22 우동균 결정: 스토어 직접 조회, 선택 업데이트, Play 라이브러리 추가.
  - 2026-11-23 우동균 결정: 구버전이면 실행마다 안내, Android 즉시 업데이트 유지, 푸시는 FCM 토픽과 Firebase 콘솔 발송, Firebase 설정 파일은 저장소에 둔다.
  - 다른 프론트 개발자의 합의를 확인하면 `확정`으로 바꾸고 합의 근거를 붙인다.
- 관련 규칙: [frontend/docs/collaboration.md](../../frontend/docs/collaboration.md) 4절(새 라이브러리 도입 절차)
- 관련 작업: [S15P31E102-1119](https://ssafy.atlassian.net/browse/S15P31E102-1119)(서버 최소 지원 버전과 강제 안내, 별도 진행)

## 선택

| 용도 | 방법 | 버전 | 위치 |
| --- | --- | --- | --- |
| Android: Play 스토어 새 버전 확인, 즉시 업데이트 화면 | `com.google.android.play:app-update` | `2.1.0`(2026-11-22 확인) | `shared` `androidMain` |
| iOS: App Store 현재 버전 확인 | iTunes Lookup API(`https://itunes.apple.com/lookup?bundleId=…&country=kr`), 기존 Ktor Darwin 엔진 | 라이브러리 추가 없음 | `iosMain`, 응답 해석은 `commonMain` |
| iOS: 앱 안 App Store 시트 | StoreKit `SKStoreProductViewController` | 시스템 프레임워크 | `iosMain` |
| Android: 새 버전 푸시 | `com.google.firebase:firebase-messaging` | `26.0.2`(Google Maven 최신, 2026-11-23 확인) | `shared` `androidMain`, 설정은 `androidApp` |
| iOS: 새 버전 푸시 | Firebase iOS SDK `FirebaseMessaging`(Swift Package) | `13.2.0` 이상 13.x(GitHub 최신, 2026-11-23 확인) | `iosApp` |

### 업데이트 안내

- 구버전 앱을 켤 때마다 확인한다. 앱을 처음 그릴 때와 백그라운드에서 다시 앞으로 가져올 때(`ON_START`) 스토어에 설치 버전보다 새 버전이 있으면 '업데이트하기'·'나중에' 대화상자를 띄운다.
- '나중에'는 이번에만 닫는다. 1.3.0(S15P31E102-1237)은 '나중에'를 누른 버전을 다시 묻지 않았지만, S15P31E102-1257부터는 저장하지 않는다.
- Android '업데이트하기'는 Play 즉시(immediate) 업데이트 화면을 띄워 앱 안에서 내려받고 설치한다. 즉시 업데이트 도중 앱을 벗어났다 돌아오면 안내 없이 Play 화면을 이어서 띄운다. 띄울 수 없으면 Play 스토어 앱 페이지를 연다.
- iOS '업데이트하기'는 앱 안에 App Store 시트를 띄워 그 자리에서 '업데이트'를 누르게 한다. 시트를 띄울 수 없거나 불러오지 못하면 App Store 앱 페이지를 연다.
- 확인에 실패하면 안내 없이 평소대로 시작한다. 스토어 확인이 앱 사용을 막지 않는다.

### 새 버전 푸시

- 앱은 켤 때마다 설치된 버전의 FCM 토픽을 구독한다.
  - Android: `update-android-<versionCode>`(예: `update-android-11`)
  - iOS: `update-ios-<CFBundleShortVersionString>`(예: `update-ios-1.0.3`)
- 업데이트한 뒤 처음 켤 때 이전 버전 토픽을 해제한다. 해제에 실패하면 다음 실행에서 다시 해제한다(`core/update/UpdateTopics.kt`).
- 알림 권한은 처음 한 번만 묻는다. Android 13 이상은 실행 중 권한 창, iOS는 시스템 권한 창이다. 거부하면 설정에서 켜야 한다.
- 앱이 백그라운드일 때 온 푸시는 시스템이 배너로 띄운다. 앱을 쓰는 중에 온 푸시도 배너로 띄운다(Android `UpdateNotificationService`, iOS `willPresent`). 배너를 누르면 앱이 열리고 업데이트 안내가 뜬다.
- Firebase 설정은 저장소에 둔다. Android는 `google-services.json`의 값을 `androidApp/src/main/res/values/firebase.xml`에 옮겨 Gradle 플러그인 없이 초기화하고, iOS는 `iosApp/iosApp/GoogleService-Info.plist`를 그대로 둔다. 두 값은 앱을 식별하는 공개 설정이며 비밀값이 아니다.

### 발송 절차(출시할 때마다)

1. 새 버전이 Play 프로덕션·App Store에 게시된 것을 확인한다. 게시 전에 보내면 사용자가 스토어에서 새 버전을 찾지 못한다.
2. [Firebase 콘솔](https://console.firebase.google.com) → 얼쑤 프로젝트 → Messaging → 새 캠페인 → 알림을 연다.
3. 제목·본문을 쓴다. 예: 제목 `얼쑤 새 버전이 나왔어요`, 본문 `업데이트하고 새 기능을 만나 보세요.`
4. 타깃에서 '주제(Topic)'를 고르고 구버전 토픽을 입력한다. 새 Android 버전이 `versionCode` 12라면 `update-android-11`처럼 이전 버전 토픽에 보낸다. 지원할 구버전이 여러 개이면 토픽마다 한 번씩 보낸다.
5. 예약은 '지금'으로 두고 검토 → 게시한다.
6. 보낸 날짜와 토픽을 출시 Jira 티켓에 남긴다.

푸시 기능은 S15P31E102-1257이 들어간 버전부터 동작한다. 그 전 버전(Android `versionCode` 10, 1.3.0 이하)은 토픽을 구독하지 않으므로 푸시를 받지 못하고, 앱을 켤 때 안내만 받는다.

## 근거

- Play 스토어·App Store는 새 버전이 올라와도 개발자가 사용자에게 알림을 보내 주지 않는다. 자동 업데이트를 끈 사용자는 옛 버전을 계속 쓴다.
- Play 스토어의 최신 버전을 앱이 알 수 있는 공식 방법은 In-App Updates뿐이다. Android 전용 라이브러리이므로 `commonMain`이 아닌 `androidMain`에만 추가했고, 공통 코드는 `core/update`의 `StoreUpdates` 인터페이스만 안다.
- 즉시 업데이트는 Play가 내려받기·설치·재시작까지 맡아서, 진행 표시와 재시작 안내 UI를 따로 만들 필요가 없다. 유연(flexible) 업데이트는 앱을 쓰면서 내려받을 수 있지만 진행 표시와 '다시 시작' 안내를 직접 만들어야 한다.
- iOS에는 인앱 업데이트 API가 없다. iTunes Lookup은 인증 없이 App Store의 현재 버전, 앱 페이지 주소, 앱 번호를 돌려준다. 앱 서버 쿠키가 Apple로 가지 않도록 쿠키를 끈 별도 Darwin 클라이언트를 쓴다. 앱 번호가 있으면 `SKStoreProductViewController`로 앱을 벗어나지 않고 App Store 화면을 띄울 수 있다.
- 푸시 발송 수단은 Android·iOS 모두에 보낼 수 있는 FCM이 사실상 표준이다. 업데이트 공지는 출시 뒤 한 번 보내는 공지형이라, 서버가 기기 토큰을 관리하지 않고 토픽으로 보낸다. 토픽을 버전마다 나눠서 이미 업데이트한 기기에는 보내지 않는다.
- Firebase Analytics를 넣으면 콘솔에서 "앱 버전 미만" 조건으로 보낼 수 있지만, 수집 항목이 늘어난다. 버전 토픽으로 같은 효과를 낸다.
- Android에서 `com.google.gms.google-services` Gradle 플러그인을 쓰지 않은 이유: QA 빌드의 패키지 이름(`com.ssafy.eolssu.qa`)이 Firebase에 등록되지 않아 플러그인이 빌드를 멈추고, AGP 9와의 호환도 확인되지 않았다. 플러그인이 만드는 것과 같은 리소스(`google_app_id` 등)를 직접 두면 Firebase가 같은 방식으로 초기화된다.
- 대안
  - 서버 버전 정책 API(앱 서버가 플랫폼별 최신·최소 버전을 내려줌): 강제 업데이트까지 가능하지만 백엔드 API·운영 설정이 필요하다. 최소 지원 버전과 강제 안내는 S15P31E102-1119에서 따로 다룬다.
  - Android도 스토어 페이지만 열기: Play의 최신 버전을 알 방법이 없어 새 버전이 있는지 판단할 수 없다.
  - FCM + 백엔드 발송 API(기기 토큰 저장): 개인별 알림에 맞는 방식이라 업데이트 공지에는 과하고, 백엔드에 관리자 권한부터 필요하다.
  - Jenkins 발송 잡: 출시마다 버튼 한 번으로 보낼 수 있지만 서비스 계정 키 발급과 Jenkins 자격 증명 등록이 필요하다. 출시가 잦아지면 앱 코드 변경 없이 추가할 수 있다.
  - 기기 로컬 알림(주기적 백그라운드 확인): 서버가 필요 없지만 iOS는 확인 시점을 OS가 정해 공지가 늦거나 오지 않을 수 있다.

## 영향과 남은 일

- 스토어에서 설치하지 않은 앱은 업데이트 안내를 하지 않는다. Android QA APK는 Play가 업데이트 없음이나 오류로 답한다. iOS는 App Store 영수증이 있는 설치본만 확인하므로 Ad Hoc IPA·TestFlight·개발 설치본은 같은 Bundle ID라도 안내하지 않는다.
- iOS의 App Store 설치 판정에 쓰는 `appStoreReceiptURL`은 iOS 18부터 지원 중단 예정이다. 대체 API(StoreKit 2 `AppTransaction`)는 Swift 전용이라 지금은 쓰지 않는다. 동작하지 않게 되면 안내가 뜨지 않는 쪽으로 실패한다.
- iTunes Lookup 결과는 App Store 출시 직후 몇 시간 늦게 바뀔 수 있다.
- iOS 푸시에는 Apple Developer의 Push Notifications 기능, Firebase에 올린 APNs 인증 키, `aps-environment` 권한이 필요하다. 출시 잡은 App Store Connect API 키로 클라우드 서명하므로, App ID에 Push Notifications가 켜져 있어야 프로파일에 권한이 들어간다.
- Firebase Messaging은 Firebase 설치 ID를 쓴다. Play Console 데이터 보안 양식과 App Store 개인정보 항목에 기기 식별자 수집이 필요한지 출시 전에 확인한다.
- Android의 실제 업데이트 동작은 Play에서 설치한 앱으로만 확인할 수 있다(내부 앱 공유 또는 테스트 트랙). iOS는 App Store 설치본에서만 확인할 수 있다. 확인 결과는 각 작업 티켓에 남긴다.
