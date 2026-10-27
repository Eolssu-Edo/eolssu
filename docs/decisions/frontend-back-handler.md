# 프론트엔드 기기 뒤로 가기 처리 라이브러리

- 작업: [S15P31E102-165](https://ssafy.atlassian.net/browse/S15P31E102-165)
- 상태: **제안** — 2026-10-27 권영민 결정(라이브러리 추가). 프론트 개발자 송가연의 합의를 확인하면 `확정`으로 바꾸고 합의 근거를 붙인다.
- 관련 규칙: [frontend/docs/collaboration.md](../../frontend/docs/collaboration.md) 4절(새 라이브러리 도입 절차)

## 선택

| 용도 | 라이브러리 | 버전 | 소스 세트 |
| --- | --- | --- | --- |
| 기기·브라우저 뒤로 가기 가로채기 | `org.jetbrains.compose.ui:ui-backhandler` | `1.12.1`(`composeMultiplatform`) | `commonMain` |

## 근거

- 연주 중 기기 뒤로 가기가 화면 이동 그래프에 바로 넘어가 확인 창 없이 연습 화면을 벗어났다. 화면의 뒤로 가기 버튼과 같게 '나가면 이번 연주는 저장되지 않아요' 확인 창을 띄우려면 공통 코드에서 뒤로 가기를 가로채야 한다.
- Compose Multiplatform이 함께 배포하는 모듈이라 버전을 `composeMultiplatform` 하나로 맞춘다. Android·iOS(arm64·simulatorArm64)·JS·Wasm 모듈이 모두 배포된다(Gradle 캐시의 `.module` 확인, 2026-10-27).
- 대안
  - 화면을 경로로 쪼개 뒤로 가기를 그래프에 맡기기(S15P31E102-149의 PDF 올리기·미리보기 방식): 연주 중 확인 창은 경로로 나눌 수 없다.
  - `androidx.navigationevent`의 `NavigationEventHandler`: 1.12에서 `BackHandler`를 대신하도록 안내되지만 지금 의존성에 없어 라이브러리를 하나 더 들여야 한다.

## 영향과 남은 일

- `BackHandler`는 1.12에서 `@Deprecated("Use NavigationEventHandler instead")`, `@ExperimentalComposeUiApi`다. 쓰는 곳(`PracticeRoute`)에서 경고를 막고 이유를 적었다. `navigationevent`를 들일 때 바꾼다.
- Web에서 브라우저 뒤로 가기와의 관계는 확인하지 않았다(Web은 `App(onNavHostReady)`로 브라우저 주소·뒤로 가기를 화면 이동 그래프에 잇는다).
