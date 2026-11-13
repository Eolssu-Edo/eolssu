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

## 오선보 4/4 가져오기의 장단 후보 누락 (880)

2026-11-13, S15P31E102-880. 소스 기준 `a6be2db7`. 운영에 이미 추가된 오선보 `VB`의 확정본·자동 저장 초안과 공개 장단 API를 읽기 전용으로 대조했다. 새 장단 등록은 필요하지 않다. 장단 ID12 `4박 기본 반주`는 공개 목록에 있으며 `QUARTER`, 패턴4칸, `patternCellEighths=2`다.

| 대상 | BPM | BPM 단위 | 정간/박 tick | 각 구성 | 장단 |
| --- | --- | --- | --- | --- | --- |
| 오선보32 확정본 | 66 | DOTTED_QUARTER | 12/18 | 119각 모두4칸 | 굿거리2 |
| 오선보32 초안 | 66 | DOTTED_QUARTER | 12/18 | 119각 모두4칸 | 선택 해제 |
| 비교용 정간보20 초안 | 160 | QUARTER | 4/4 | 118각 모두4칸 | 4박 기본 반주12 |

오선보의 첫 이벤트는 `c0-j0`, 시작0의 쉼표다. 시작 위치나 불완전한 마지막 각이 이번 후보 누락의 원인은 아니다. 각 길이는 `4 × 12/18 = 8/3` 점4분박이므로 굿거리의4박 주기에도 맞지 않는다. ID12는 `QUARTER`와 저장된 `DOTTED_QUARTER`의 불일치에서 먼저 제외된다. 오선보는822의 정간보 재시간보정 대상이 아니므로 초안에서 장단만 해제해도 후보가 복구되지 않는다.

원인은 가져오기 경로의 기본값과 편집의 단위 검사가 연결되는 데 있다.

- [staff_musicxml.py](../../../ai/src/omr_service/staff_musicxml.py)는4/4에서 장단을 지정하지 않는다. 이 변환기는 PDF의 메트로놈·구간 템포를 읽지 않는다.
- [staff_engine.py](../../../ai/src/omr_service/staff_engine.py)는 장단 미지정 시 굿거리를 선택하고,4분박1칸을 굿거리의 점4분박 단위인2/3박으로 환산한다.
- [ConversionWorker](../../../backend/src/main/kotlin/com/ssafy/eolssu/backend/feature/sheet/infrastructure/ConversionWorker.kt)와 [RecognitionResultBuilder](../../../backend/src/main/kotlin/com/ssafy/eolssu/backend/feature/sheet/infrastructure/RecognitionResultBuilder.kt)는 별도 요청 BPM이 없으면 선택된 장단의 기본 BPM을 사용한다. 따라서66은 원본 시작160을 환산한 값이 아니라 굿거리 기본값이다.
- [SheetJangdanCorrection](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/song/edit/SheetJangdanCorrection.kt)과 [SheetCompletionValidation](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/song/edit/SheetCompletionValidation.kt)은 오선보의 저장된 단위를 유지하면서 모든 각의 완전한 장단 주기를 검사한다.

운영 DB 조회는 `START TRANSACTION READ ONLY`와 `ROLLBACK`으로 수행했다. 공개 `/api/jangdans`는12개 후보와 ID12의 정상 메타데이터를 반환했다. 원본 계정·악보·초안·운영 설정은 변경하지 않았다. 원본 파일을 다시 OMR에 넣거나 앱 화면을 직접 조작한 검증은 하지 않았다.

로컬에서 실제 저장값에 동일한 수치 판정을 적용해 오선보32의 원본·초안 모두 후보0개를 재현했다. ID1·2·4·5·11은 주기 불일치, ID3·6·7·8·9·10·12는 단위 불일치다. 비교 정간보20은 `isCompatibleWith` 기준 ID7·12가 통과했다. 실제 `staff_musicxml.convert`에4/4·4분음표160 BPM의 최소 MusicXML을 넣으면 한 각4칸·한 칸4분음표1개·장단 미결정이 반환된다. 소스의 굿거리 기본값을 적용한2/3박과 실제12/18 tick 비율이 같다. 이 검사는 전체 OMR·Kotlin UI 실행과 구분한다.

초기 조사는 제품 수정·기존 악보 복구를 포함하지 않았다. 이후 사용자 수정 요청의 범위와 결과는 다음 절 및 [작업 기록](../../plans/S15P31E102-880.md)이 대체한다.

### 수정·복구 결과

4분음표4박의 박 구성을 읽으면 기존 ID12를 제안하도록 `staff_musicxml.convert`를 수정했다. 명시 장단 우선·9/8·12/8·`JANGDAN_GUESSED`는 유지한다. 반주 기본160 BPM과 원본 숫자 템포·가속 구간 자동 인식은 구분한다. API·DB 스키마와 앱 코드는 변경하지 않았다. [수정 스펙](../../specs/staff-four-beat-import.md).

2026-11-13 16:11 KST, 운영 오선보32의 최신 초안을6→7revision으로 복구하고 새 DB 연결에서 커밋 결과를 재조회했다. 초안의 BPM160·장단12·QUARTER·박tick12를 확인했다. 모든 이벤트531개·NOTE503개의 편곡·칸·원본·adaptation은 같고 변경 경로는 `bpm`, `jangdanId`, `score.music.bpmUnit`, `ticksPerBeat`, `jangdan` 다섯 개뿐이다. 확정본·scoreVersion1·검수 상태·인식 결과는 유지했다. 원본 SHA는 `5241e467…c97`로 동일하다. 현재 열린 편집기는 닫고 다시 열어 revision7을 받아야 한다.

복구 전에 계정·대상·버전·초안 SHA와 원본을 확인했고, 구간 빠르기 계획이 양쪽 모두null임을 확인했다. 버전·revision·저장 JSON 해시를 조건으로 한 트랜잭션에서1행만 갱신했으며, 커밋 전에는 전체 초안과 원본 보존을, 커밋 후에는 전체 조회값의 일치를 검사했다. 서버 root 전용 `/opt/eolssu/ops880-staff-four-beat-20261113T070659Z/`에 변경 전 JSON과 커밋 확인 기록을 보관한다. 복구 준비 도구는 [prepare_staff_four_beat_repair.py](../../../backend/tools/prepare_staff_four_beat_repair.py)이며 직접 DB에 쓰지 않는다.

검증은 순수 변환·가짜 Audiveris와 실제 JGB 생성기의 HTTP 회귀96개 통과, 복구 준비 도구6개 통과, 실제 복구 초안의 공통 악보 JSON Schema 통과다. 넓힌 OMR 검사189개에서는186개가 통과하고 기존 로그 수집 검사3개가 실패했다. 같은3개는 변경 전 `a6be2db7` 소스에서도 똑같이 실패하는 것을 확인했으며 로그가 stderr에는 있지만 caplog에 잡히지 않는 문제다. 관련 소스를 이번 수정에 편입하거나 검사를 생략하지 않았다. 환경은 기존 Python3.12 가상환경과 묶음 PDF 의존성·기존 이미지의 JGB 생성기를 사용했으며 개인 설정·설치 의존성은 변경하지 않았다.

사용자가 이조한 초안만 복구했고 확정 저장은 대신하지 않았다. 새 인식 로직은 구현 커밋 이후 정식 MR·병합·서비스 배포가 필요하다. 실제 Audiveris로 PDF 전체 재인식·실기기 UI·iOS·정식 리뷰는 수행하지 않았다.
