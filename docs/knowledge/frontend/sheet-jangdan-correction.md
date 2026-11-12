# 인식 정간보의 장단 변경과 시간 보정

2026-11-12, S15P31E102-822. [스펙](../../specs/sheet-jangdan-correction.md), [작업 기록](../../plans/S15P31E102-822.md).

## 원인과 적용 범위

정간보 인식은 `ai/src/omr_service/jeongganbo_text.py`의 `beats_per_jeonggan`에서 요청 장단의 한 주기 길이를 인식 격자의 정간 수로 나눈다. 편집은 원본의 BPM 단위·tick으로 호환성을 검사했으므로 같은 PDF도 처음 고른 장단에 따라 변경 가능한 후보가 달랐다. 각의 정간 수 자체는 `SheetDocumentEditing.editingDraft`가 원본 `columns[].cells`에서 가져온다. 4정간 원본이 장단 변경 후에도4칸인 것은 격자 보존이다.

`SheetJangdanCorrection`은 보존된 `authoring.source.score`의 인식기 식별자와 각별 정간 수를 사용한다. 식별자가 `jeongganbo-text/` 또는 `jeongganbo-omr/`일 때만 요청 장단에서 유래한 시간값으로 취급한다. 부가정보가 없는 과거 데이터는 현재 악보에 같은 식별자가 남아 있을 때만 처리한다. 출처를 확인할 수 없는 `app-editor-1` 악보나 오선보를 정간보 인식 결과로 간주하지 않는다.

후보는 카탈로그의 `patternCellEighths`·패턴 칸 수·BPM 단위로 계산한다. OMR의 한 각=한 주기, 정간당1·1/2·1/3박과 세령산3/2박 예외를 재사용한다. 같은 정간 수만으로 특정 장단을 자동 확정하지 않는다. 불완전한 각·서로 다른 원본 각 길이·알 수 없는 패턴 음가는 추측하지 않는다. 숫자 구간 빠르기 계획과 오선보는 기존 시간값을 유지한다.

## 보정과 불변 정보

장단을 고르는 두 화면 모두 시간 보정 전에 안내·적용·취소를 제공한다. BPM 숫자는 유지하고 단위는 선택할 장단에 맞춰 표시한다. 적용은 현재의 음고 수정·분할을 먼저 공통 악보에 반영한 뒤 정간/박의 정수 tick 비율을 바꾼다. 필요한 경우 모든 이벤트를 같은 정수 배율로 옮긴다. 칸 수·row/col·ID·음고·이음 참조와 불변 `authoring.source`를 유지한다.

분할 편집은 정수 표현을 위해 tick 배율을 키울 수 있다. 보정할 때 편집 전 시간값을 다시 쓰면 격자와 이벤트 길이가 달라진다. 현재 편집을 반영한 정간 tick으로 보정한다. 원본 음고 연결도 절대 tick 대신 정간에 대한 위치·길이 비율을 정확한 정수 비교로 검사해, 배율만 달라졌다고 원본 MIDI를 잃지 않게 했다.

확정 저장의 `isCompatibleWith`는 계속 실제 보정된 시간값·완전한 주기를 검사한다. 서버의 `SheetJangdanValidation`과 `EditUserSheet`는 새 악보의 이름·BPM 단위·카탈로그 주기를 확인하고 그 단위를 저장한다. 원본 부가정보는 해시 참조로 보낸다. API·스키마·서버 구현 변경은 없다.

## 검증과 한계

- 동일한4정간 원본을 중모리(4분음표, 정간당3박)·굿거리(점4분음표, 정간당1박)로 각각 가져온 재현은 수정 전 후보·보정2건이 실패했고 수정 후 통과했다.
- 정수 tick 배율이 바뀌는 세령산 정정 후 원본 음고 연결, 3tick 정간을2음으로 나눈 뒤 장단 정정은 각각 수정 전 실패를 확인한 회귀다.
- ViewModel 검사로 장단 정정→자동 저장→재조회→다른 단위 장단→확정 저장의 원본 격자·음표·부가정보 보존을 검사한다. 저장소는 격리된 대역이므로 운영 API·DB 검증과 구분한다.
- 실제 공유 Route/ViewModel·두 선택 화면을 로컬 저장소·실제 카탈로그12종과 Wasm 브라우저360×800에서 실행했다. 최초 중모리/굿거리의 후보, 단위 변경, 사전 안내/취소/적용,4정간 유지, 실행 취소를 확인했다. 증거는 로컬 `.scratch/822/confirmation.png`, `applied.png`, `from-gutgeori.png`, `danmori-applied.png`다. 임시 진입점·fixture는 제거하고 서버와 임시 탭을 닫았다.
- 최종 `:shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinWasmJs :shared:compileKotlinJs`가 통과했다. Android host는 1,537건/216 suite, 실패0·오류0·기존 제외4건이다. 신규 장단 정정9건, 장단 ViewModel3건, 음고 원본 연결5건도 모두 통과했다. 로컬 `final-checks-822.log`와 XML 검사 결과에 근거한다. 실기기 청취·iOS·운영 API/DB·정식 리뷰는 수행하지 않았다. 원본 인식기를 교체하거나 장단 자동 인식을 추가한 작업이 아니다.
