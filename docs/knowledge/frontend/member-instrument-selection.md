# 회원 악기 선택과 곡 목록 연결

작업: S15P31E102-408. 2026-11-03 병합본 `7c57baa8`의 Android QA 확인 뒤 보고된 후속 수정이다. 확정 동작은 [곡 목록 악기 필터](../../specs/song-instrument-filter.md)를 따른다.

## 실패를 구분한 근거

사용자는 마이페이지의 닉네임이 비어 있고 어느 악기를 눌러도 단소를 선택하라는 안내가 나온다고 보고했다. 같은 시간대의 Nginx 접근 기록에서 `/api/instruments`는 200, `/api/users/me`와 `PATCH /api/users/me/preferences`는 401이었다. 확인에는 시간·고정 경로·HTTP 상태만 사용했다. 공유 서버의 기록이므로 개별 요청의 계정 귀속이나 쿠키 누락 원인까지 확정하지 않는다.

`MyPageRoute`는 회원 조회 실패 뒤에도 설정 화면을 유지했고, `MyPageViewModel.selectInstrument`의 모든 실패를 Boolean으로 줄였다. 그래서 인증 실패도 악기 미지원으로 표시했다. 실제 네 악기 카탈로그, Koin의 `InstrumentCatalog` 주입, 악기만 보내는 PATCH 계약에는 불일치가 없었다. 쿠키 발급·저장 문제와 서버 세션 만료 중 어느 쪽인지는 이 증거만으로 구분할 수 없다.

## Android 로그인 브라우저 진단

설치된 QA 앱의 공개 서명과 App Links 도메인은 일치했고, 기본 핸들러 조건의 콜백 URI 해석도 QA MainActivity였다. 반면 05:39:48·05:40:03 UTC 기기 이벤트에서 Google 로그인 ACTION_VIEW가 네이버 앱의 `SchemeProcessActivity`와 `InAppBrowserActivity`로 전달됐다. 같은 시각 QA 앱으로 콜백 ACTION_VIEW가 전달된 기록은 없었다. Google 오류 화면은 관찰하지 못했으므로 특정 OAuth 오류 코드까지 단정하지 않는다.

기기의 Custom Tabs 서비스 제공자는 Chrome과 Samsung Internet이었다. Android ExternalBrowser는 해당 서비스와 실제 HTTPS VIEW 처리 능력을 함께 가진 패키지를 선택하도록 고친다. 지원되는 기본 브라우저를 우선하고, 후보가 없으면 실행 실패를 돌려준다. 새 라이브러리나 수동 Custom Tabs 프로토콜은 추가하지 않는다. 판별 방법과 Android 패키지 조회 범위는 [Chrome 공식 안내](https://developer.chrome.com/docs/android/custom-tabs/howto-custom-tab-check)를 따른다.

## 수정한 경계

- 첫 실행 완료 표시는 로그인 증거가 아니다. 공통 `App` 진입에서 회원 정보를 한 번 복구하고, 대기·일시 실패 재시도·401 로그인 복귀를 구분한다. 화면마다 복구하거나 OAuth 브라우저 복귀마다 다시 실행하지 않는다.
- 마이페이지의 조회·저장 401은 기존 프로필·악기 상태를 비우고 로그인 화면으로 연결한다. 저장 결과의 `MemberResult`를 보존해 인증 실패·요청 제한·잘못된 악기·일시 장애를 구분한다. 저장 중에는 선택을 비활성화하고 서버가 수락한 ID만 반영한다.
- 두 곡 목록은 `InstrumentRepository.instrument`를 구독한다. 선택한 enum의 서버 code를 카탈로그의 실제 ID로 변환하며, 미확인 악기를 전체 조회로 대체하지 않는다. 목록별 악기 칩을 없애고 검색 초기화에서도 회원 악기를 유지한다.
- 웹 `onNavHostReady`는 브라우저 탐색을 계속 수집하는 suspend 함수다. 완료를 기다린 뒤 로그인으로 이동하면 도달하지 못한다. 초기 URL 적용을 먼저 시작하는 `UNDISPATCHED` 자식 코루틴과 401 이동을 분리해 검증한다.

서버 인증·쿠키·약관 정책은 바꾸지 않는다. 인증되지 않은 요청을 허용하거나 서버 저장 실패를 로컬 성공으로 대체하지 않는다. 정상 저장, 401, 일시 실패, 요청 중복과 늦은 응답을 서로 다른 검증 사례로 둔다.

## 검증 범위

`MyPageInstrumentTest`는 실제 `ApiUserRepository`·`InstrumentRepository`·ViewModel에 합성 HTTP 응답을 연결한다. 서버 ID 전달·수락 후 반영·저장 중복 차단·401 상태 정리·429/503 기존 선택 보존을 확인한다. `SongInstrumentFilterTest`는 공유 악기 변경·미확인/없는 카탈로그·검색 초기화·재시도·취소를 무시하는 이전 응답을 검사한다.

첫 수정본의 Android host 660건 중 657건 통과·기존 opt-in 3건 제외, JS와 Wasm 각 646건 통과, Android debug APK 및 JS 개발 번들 빌드 성공을 확인했다. 외부 브라우저 선택은 기본 앱 미지원·지원 기본 앱 우선·VIEW 미지원 제외·후보 없음 4건을 RED→GREEN으로 검증했다. 최신 develop `026e4970`(409·412·416 포함) 통합본 `221425a3`에서 Android host 673건 중 670건 통과·기존 opt-in 3건 제외, JS와 Wasm 각각 655건 통과를 확인했다. 같은 통합본의 Android debug APK와 JS 개발 번들도 빌드에 성공했다. 독립 읽기 검토에서 기능 결함이나 의미 충돌은 발견하지 못했고, 416의 뒤로가기 가드를 보존했다.

로컬 합성 회원으로 소금 저장 뒤 홈의 현재 악기와 두 곡 목록이 소금으로 바뀌는 것을 확인했다. 연습 목록은 2곡, 평가 목록은 평가 가능한 1곡만 표시했고 악기 선택 버튼은 없었다. 360×800 화면에서 가로 넘침 없이 표시된다. 회원 조회 503에서는 빈 마이페이지 대신 재시도 화면을 보였고, 200으로 회복시킨 뒤 버튼으로 목록 복구를 확인했다. 401 상태에서 곡 목록 URL을 새로 고치면 `#auth/login`으로 이동했다. 로그아웃 확인 뒤 로그인 화면으로 이동하는 것도 확인했다. 이 fixture에는 실제 세션 쿠키가 없어 운영 로그아웃의 쿠키 무효화를 검증한 것은 아니다. 최신 홈 통합 뒤 산조대금 저장 중 안내, 홈의 현재 악기, 연습 2곡·평가 1곡 표시도 확인했다. 키보드 Enter 검증은 Compose 웹 버튼 자동화가 시간 초과하여 통과로 계산하지 않았다. 로컬 합성 회원의 화면 검증은 운영 Google 로그인이나 Android 실기기 검증을 대신하지 않는다. Windows에서는 iOS 빌드를 확인하지 않는다.

## QA 설치 후 확인할 경계

기존 `eolssu-android-qa` 잡은 `REVISION`의 전체 SHA가 `origin/develop` 조상인지 검사한다. feature 브랜치의 미병합 SHA는 받지 않으며, backend 파이프라인이 병합 SHA의 QA 빌드를 자동 예약한다. MR 병합 뒤 성공한 표준 산출물의 commit·SHA256·`com.ssafy.eolssu.qa`·기존 공개 서명 일치를 확인하고 앱 데이터를 유지한 업데이트를 진행한다. 설정 우회나 별도 서명 경로를 만들지 않는다.

현재 기기 증거는 기존 `7c57baa8` QA 앱의 실패 경로다. 수정 QA 앱에서 Google 계정 선택→앱 복귀→회원 조회 200→악기 저장→로그아웃 후 재실행까지는 아직 확인하지 않았다. 이 실기기 확인 전에는 로그인 장애 전체가 해결됐다고 판단하지 않는다.

## 브라우저를 열지 못하는 후속 회귀

`6212d717` QA 설치 뒤 사용자가 “브라우저를 열지 못했어요”를 보고했다. 기존 선택 테스트는 전역 HTTPS 처리 목록에 Chrome·Samsung도 들어 있다고 가정해 Android의 웹 주소 기본 처리 앱 필터를 놓쳤다. `run-as com.ssafy.eolssu.qa /system/bin/cmd package`로 같은 앱 UID에서 조회하면 Custom Tabs 서비스는 Chrome·Samsung 두 개지만, 전역 ACTION_VIEW+BROWSABLE/MATCH_DEFAULT_ONLY 목록에는 네이버만 남았다. Chrome·Samsung을 각각 패키지로 지정해 resolve하면 정상 Activity가 반환됐다. 고정 공개 Google 주소만 사용했고 OAuth code/state·쿠키는 읽지 않았다.

전역 목록과 서비스 목록의 교집합을 제거하고, 서비스 제공자마다 `Intent(intent).setPackage(candidate)`를 resolve한다. `exported`와 패키지 일치를 확인한 제공자 중 지원 기본 브라우저를 우선한다. 후보가 없을 때 기본 내장 브라우저로 되돌아가는 동작은 추가하지 않는다. [Android 웹 주소 조회 설명](https://developer.android.com/training/package-visibility/use-cases#open-urls)과 [AOSP의 패키지 미지정 웹 Intent 필터](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/CrossProfileIntentResolverEngine.java#411)를 확인했다.

선택 함수의 5개 테스트와 실제 앱 UID의 조회 결과를 구분한다. 전자는 제공자별 판별·기본값 우선·중복 제거·후보 없음 경계이며, 후자는 기존 방식의 후보 0개와 패키지 지정 방식의 후보 2개를 재현한다. 둘 다 실제 브라우저 launch나 Google 로그인 성공 자체를 입증하지는 않는다.

## 로컬 QA 빌드와 기존 서명 유지

APK 빌드는 로컬에서도 가능하다. 서버 QA init과 같은 `.qa` applicationIdSuffix·`얼쑤 QA` 이름으로 로컬 APK를 만들고, 서버의 기존 QA 키를 사용한 `apksigner --out` 재서명만 수행하면 같은 앱을 데이터 삭제 없이 업데이트할 수 있다. 키 파일은 PC로 복사하거나 내용·비밀값을 읽지 않는다. 서명 전후 앱 payload 일치, AADE 공개 인증서, 패키지·버전·APK SHA256을 확인한 뒤 설치한다. `6212d717`의 이 경로는 로컬 빌드 16초, 설치 및 앱 링크 검증 성공으로 확인했지만 위 브라우저 로직 회귀가 남아 있었다.

서버의 최신 빌드 폴더도 원하는 최신 코드라는 보장은 없다. 당시 최신 보관 APK는 `4ca30d81`로 더 이전 버전이었다. `/opt/eolssu/apks/<폴더>/build.json`의 전체 commit과 원하는 수정 포함 여부를 확인한 뒤 선택해야 한다.

후속 전체 Android host 검사: 679건 중 676건 통과, 기존 opt-in 3건 제외. Android 전용 변경이므로 JS/Wasm의 변경 없는 공통 경로 검사를 추가 반복하지 않았다.

`72a641ca` 수정 QA를 15:55 KST에 기존 앱 데이터 유지 업데이트했다. AADE 서명·도메인 verified·앱 UID의 Chrome resolve를 확인했고, 사용자가 Google 로그인 뒤 “앱으로 돌아오고 닉네임도 보임”을 확인했다. 이는 수정 APK의 실제 로그인 복귀·회원 정보 표시 성공을 사용자 확인으로 입증한 것이며, 자동 UI 검사 결과와 구분한다.
