# 제공 곡 불러오기 목록의 대기 분리

2026-11-13, S15P31E102-886. [스펙](../../specs/edit-pick-list.md)에 따라 EditPick의 조회와 선택 화면을 수정했다.

## 원인과 변경

기존 EditPickViewModel은 공개 곡을 받은 뒤에도 내 악보 목록 갱신과 악기 코드가 없는 악보의 상세 조회를 순서대로 기다린 다음 Ready를 표시했다. 따라서 공개 곡 자체의 응답이 끝나도 목록이 나타나지 않았다. 화면에는 글자 타일·작은 라디오·상단 알약 버튼이 남아 있었다.

제공 곡과 내 악보를 별도 Job과 로딩 상태로 조회한다. 현재 탭의 상태만 화면에 반영하며 재시도도 해당 탭에 한정한다. 악기 변경은 두 Job을 취소하고, 응답 반영 직전 취소 여부를 확인해 단소→소금→단소처럼 되돌아온 경우의 이전 응답도 막는다. 공개 API나 내 악보 저장소 계약을 바꾸지 않았다.

선택 화면은 연습곡 고르기의 MySongsTabSwitch·SongSearchField·SongSortButton·난이도 배지와 앱의 악기 그림 메뉴를 사용한다. 글자 타일을 없애고 체크와 연두 배경으로 선택을 표시한다. 복사·선택 버튼은 하단 고정 EolssuButton으로 옮겼다. 오은서 담당 881의 악보 추가 첫 화면과 347의 서버 흐름은 수정하지 않았다.

## 검증

- Android host 전체 검사: 1,655건, 실패 0, 환경 연동 검사 5건 skipped. :androidApp:assembleDebug 통과.
- 추가한 대기 분리·실패 격리 검사는 기존 코드에서 실패한 뒤 변경 후 통과했다. 내 악보 응답을 CompletableDeferred로 보류해도 공개 곡 Ready·선택 가능을 확인한다.
- 기존 악기 코드/초안 조회·목록 갱신 실패·검색/정렬·복사 멱등성·계정 변경 회귀 검사를 유지했다. 악기를 되돌린 뒤 이전 응답과 최신 응답의 순서를 뒤집는 검사도 포함한다.
- 취소 확인과 계정 초기화 가드를 제거한 임시 코드에서 해당 새 검사 2건이 모두 실패했다. 검증본을 해시 대조로 복원한 뒤 EditPickViewModelTest·SheetEditStateTest·SongSortOrderTest를 재실행해 통과했다. 임시 변경은 포함하지 않았다.
- 실행 명령: frontend에서 .\gradlew.bat :shared:testAndroidHostTest :androidApp:assembleDebug --max-workers=2 --console=plain.
- 웹 실행 화면: 390×844·320×640·1024×768에서 실제 EditPickRoute·ViewModel·Screen과 고정 Repository를 연결해 확인했다. 탭·악기 메뉴·제목 검색·역순 정렬·선택/해제·하단 버튼을 확인했다. 복사 대역의 개인 ID 998886이 편집 진입 콜백에 전달됐다. 실제 서버 복사·편집기 저장을 검증한 것은 아니다.
- 소금 첫 조회 실패→다시 시도→8곡 복구, 산조대금 빈 결과, 정악대금 조회 보류 중 진행 표시→내 악보 탭 1곡 사용을 확인했다. 좁은 화면에서 목록만 스크롤되고 버튼은 하단에 남는다.
- 웹 명령: .\gradlew.bat :webApp:compileDevelopmentExecutableKotlinWasmJs :webApp:wasmJsBrowserDevelopmentWebpack --max-workers=2 --console=plain. 고정 Repository를 연결한 임시 진입점으로 빌드했고 원래 main.kt를 복원했다. 새 의존성은 추가하지 않았다.
- 실행 캡처: Codex 세션 산출물 edit-pick-886의 selected-phone.png·selected-narrow.png·selected-tablet.png·retry-phone.png·loading-tablet.png. 메뉴가 닫힌 뒤 자동화 접근성 목록이 남는 현상은 실제 캡처와 좌표로 대조했다. 콘솔에 Kotlin/Wasm 의존성의 wasmExports.memory 폐기 예고가 있었으나 조회·선택·복사 동작 오류는 관찰되지 않았다.
- Android 실기기·iOS·운영 HTTP 왕복 시간·속도 향상률은 미검증이다. 웹 키보드 자동화는 가상 접근성 노드 press 단계에서 도구가 실패해 검증하지 못했고, TalkBack 실사용도 남아 있다. 정식 독립 리뷰는 별도다.
