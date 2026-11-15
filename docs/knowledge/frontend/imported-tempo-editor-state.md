# 악기 맞춤 적용 뒤 구간 빠르기 소실 원인과 수정

- 작업: S15P31E102-882. 2026-11-15 초기 진단 뒤 사용자 요청으로 수정·검증·Draft MR 범위까지 확장했다. 초기 진단과 아래 수정 검증을 구분한다.
- 소스: 최신 develop `88f81e37`; 실제 Galaxy S24+ SM-S926N의 QA 앱 `qa-20261115.031107-bac2acf1`. 관련 편집·저장 코드에는 두 revision 사이 차이가 없다.
- 입력: 제공 Blue Valentine 4쪽 PDF, 74,641bytes, SHA256 `5aa7f7af037acdb7540071b187cdec5923a4d51f4521861828b129ec98321f1d`. 원본은 수정하지 않았다.

## 관측과 결론

최근 변환 악보45(`알림확인-1115`)의 운영 DB를 제보 계정에 한정해 SELECT로 확인했다. 변환은 2026-11-15 14:39:13 KST 완료, 저장 판본2·기본160, 초안 없음이며 `expressions_json.performancePlan.tempos`에는 다음5항목이 남아 있다.

| 시작 | 빠르기 | 끝 |
| --- | --- | --- |
| 1각1정간 | 160 | 즉시 |
| 26각1정간 | 명시 시작80 → 목표160 | 30각1정간 |
| 30각1정간 | 160 | 즉시 |
| 58각1정간 | 명시 시작80 → 목표160 | 62각1정간 |
| 62각1정간 | 160 | 즉시 |

같은 제목의 실제 폰 편집기는 이조·음역 맞춤 화면에서 기본160·예상 길이2:57을 표시했다. 빠르기 연필을 열자 **“구간 변경이 없어요”**가 보였다. 창만 열고 뒤로 닫았으며 적용·저장·재변환은 하지 않았다. 이 최근 변환본은 OMR의 숫자 검출 누락과 구분되는 **편집 상태 소실**이다.

운영 OMR/playback의 현재 이미지도 읽기 전용 `docker inspect`로 확인했다. 각각 `b75f30cb…`/`529d5859…`, revision `24273759`로, [904의 최종 운영 반영 기록](../../plans/S15P31E102-904.md)과 일치한다. 882의 옛 운영 미반영 기록을 현재 상태로 해석하면 안 된다. 모델 전체 재실행 대신 동일 입력의 기존 실제 운영 HTTP 검증과 이번 실제 저장 결과를 대조했다.

## 첫 소실 지점

[SheetPitchAdaptation.applyAdaptation](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/song/edit/SheetPitchAdaptation.kt)은 음고를 바꾼 뒤 `adapted.editingDraft(...).copy(...)`로 편집 상태를 새로 만든다. 이때 `tempoExpressions`와 `tempoReferenceBpm`을 전달하지 않는다. 새 `SheetDraft`의 두 필드는 기본 null이므로, 적용 전 존재하던 숫자 구간이 `tempoChanges`에서 사라진다.

[SheetTempoEditing.exportExpressions](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/song/edit/SheetTempoEditing.kt)은 draft의 `tempoExpressions`가 null이면 ViewModel에 남아 있는 원래 template을 반환한다. 이 때문에 **DB에5개가 보존되면서 화면은0개**인 상태가 가능하다. 예상 길이는 draft의 `tempoChanges`를 사용하므로 같은 영향을 받는다. 이번 진단에서는 폰의 저장·음원 생성·재생을 실행하지 않았으며 실제 소리의 빠르기까지 검증했다고 확대하지 않는다.

동일한 재생성 패턴이 [개별 음 교정](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/song/edit/SheetPitchCorrection.kt)에도 있다. 장단 재설정도 같은 필드가 빠지지만 정상 `hasTempoPlan` 가드가 있어 이번 실제 경로로 확정하지 않았다. 후속 수정에서는 공통 악보를 다시 편집 상태로 만드는 경계의 companion·기준 BPM 보존과 undo/redo·저장 재진입을 확인해야 한다. 템포 해석·공유 계약 변경이 필요하다는 근거는 없다.

## 재현과 검증 범위

최소 입력은 작은 기존 `smallScoreData()`에 명시 시작80·목표160·끝2각의 숫자 ramp 하나를 부착한 것이다. `tempoChanges.size == 1`을 확인한 뒤 실제 `applyAdaptation`을 +4반음 계획으로 호출하고 동일 구간 보존을 검사했다. 외부 API·모델·전체 앱 기동 없이 원인 함수를 실행하는 진단이다.

일회용 `TempoAdaptationProbeTest`를 commonTest에 두고 `frontend/`에서 실행했다.

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests '*TempoAdaptationProbeTest' --max-workers=2 --no-configuration-cache
```

JDK21의 Android host test에서 **1검사·1실패·제외0**, 종료1이었다. Android API37 기기에서 이 단위 검사를 실행한 것은 아니다. 실패는 인프라 오류가 아니라 다음 보존 assertion이다.

```text
expected: [SheetTempoChange(cell=0, mark=null, targetBpm=160.0, endCell=2, startBpm=80.0)]
actual: []
```

실행 후 commonTest의 일회용 소스를 제거하고 진단용 사본·`adaptation-red.log`·`adaptation-red.xml`을 제외 경로 `.scratch/tempo-recheck-882/`에 보존했다. 실제 폰 캡처도 같은 경로의 `device-current.png`, `device-tempo-dialog.png`에 있다. 이 검사는 결함을 재현한 것이며 수정 완료·전체 회귀 통과를 뜻하지 않는다. 원본 PDF·사용자 악보 전체·인증값·DB 비밀값은 문서에 복사하지 않는다.

## 후속 수정과 회귀 검증

`applyAdaptation`과 실제 이벤트를 다시 만드는 `correctPitch`에서 `tempoExpressions`·`tempoReferenceBpm` 두 필드를 새 draft에 그대로 복사한다. 음고 변경 시 시간 격자는 그대로이므로 구간 위치·반복·기호와 기준을 재해석하지 않는다. SHA와 악기별 계획 schema는 기존 BE 저장 경계에서 재결합하며 이 helper에서 새로 만들지 않는다. 장단 retiming 경로는 이번 수정 범위가 아니다.

새 `SheetPitchTempoPreservationTest`는 전체120·기준80·원래80→160 ramp를 사용한다. 이조·악기 변경과 실제 이벤트 교정 두 경로는 수정 전 모두 빈 목록으로 실패했다. 예상 길이 검사도 수정 전 `ln(2)+0.25 = 0.943147…`초 대신 고정120 기준1.5초로 실패했다. 수정 후 표시120→240, 저장 기준80→160, 확정120에서120→240 한 번 조정, 반복·위치·원본 SHA 계약, undo/redo와 export 재진입이 통과했다.

| 검사 | 결과 |
| --- | --- |
| 신규3개 및 음고·빠르기·예상 길이 관련5클래스 | 50개 통과, 실패·제외0 |
| 전체 `:shared:testAndroidHostTest` | 242클래스·1753개 중1748통과, 실패0·외부/캡처 환경용5개 제외 |
| 정상 `:androidApp:assembleDebug` | 성공 |
| 독립 코드 검토 | 수정 필요 결함 없음. 두 음고 경계와 새 테스트, export·BE 재결합 코드 확인 |

JDK21·로컬 Android SDK API37로 실행했으며 host 검사는 JVM 검사다. 정상 APK SHA256은 `9b4a62c21094042daf63172a7ce4910dba1184e743954c465f0753a5c1e1f34c`다. 신규3개는 제외되지 않았다. 실제 운영 HTTP 저장·실기기 청취·iOS 실행을 이번 host 검사로 확인했다고 확대하지 않는다. 앱 화면의 추가 검증·게시 상태는 [작업 계획](../../plans/S15P31E102-882.md)에 기록한다.

격리 UI는 별도 앱 ID `com.ssafy.eolssu.tempo882qa`, Galaxy S10 규격1440×3040·560dpi의 Android API37 에뮬레이터에서 확인했다. 자작64각 악보에5개 숫자 구간을 부착하고 버튼으로 실제 `SheetEditUiState.applyAdaptation(+4)`를 실행했다. 적용 후에도5구간과 두80→160이 남았고, 제품 `SheetEditSettingsDialog`에1·26·30·58·62각 항목이 표시됐다. 실행 취소/다시 실행과 앱 전용 로컬 파일의 export·재진입도 각각5구간을 유지했다. PNG/XML은 `.scratch/tempo882-ui/`에 보존했다. 사용자의 기존 QA 앱·계정·악보는 수정하지 않았다. 이 검증은 실제 제품 컴포넌트와 상태 변환을 사용하는 격리 UI이며 인증 API·운영 DB 저장까지의 E2E나 실기기 검증은 아니다. 소유 에뮬레이터와 공유 잠금은 종료·해제했다.

## 원문 빠르기 표시와 숫자 수행 계획의 차이

2026-11-15 S15P31E102-931 후속 조사에서 BlueVal의 `molto accel.` 표시와 실제 가속 재생을 분리했다. [오선보 빠르기 명세](../../specs/staff-tempo-import.md)는80→160의 선형 가속을 다음 숫자까지 적용하도록 확정했지만, 원문 숫자·단어를 악보 위에 표시하는 UI는 당시 범위에서 제외했다. [한배 명세](../../specs/jeonggan-tempo-edit.md)의5종 기호 보존도 서양어 원문을 한배 기호로 자동 번역한다는 합의와는 다르다.

아래 경로는 공식390과 표시 수정 전 `ff65c430` 기준이다. 후속956의 표시 모델과 구분한다.

| 경로 | 실제 보존 범위 |
| --- | --- |
| [PDF 보완](../../../ai/src/omr_service/staff_pdf_tempo.py), [MusicXML 정규화](../../../ai/src/omr_service/staff_musicxml.py) | 숫자BPM과가속 여부를 읽는다. `molto accel.`·`accel.`·`accelerando`를 읽기 전용 메모리 실행에 넣으면 모두같은 `{bpm:80, accel:true}`로 축약된다. 원문 단어와 문자 배치는 저장되지 않는다 |
| [오선보 인식 결과](../../../ai/src/omr_service/staff_engine.py) | 시작80·목표160·끝 정간은 `performancePlan`에 남지만 원문에서 한배 `placements`를 만들지 않는다 |
| [편집 구간 목록](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/feature/song/edit/SheetTempoEditing.kt) | 숫자 구간은 편집 가능하지만 해당 기호가 없으면 mark는null이며 숫자 변경으로 표시된다 |
| [악보 표시 변환](../../../frontend/shared/src/commonMain/kotlin/com/ssafy/eolssu/data/song/CommonScoreData.kt) | 저장된 한배 기호를 표시한다. 숫자 가속 계획에서 기호를 자동 도출하지 않는다 |

따라서 저장된 숫자 램프만으로 `molto accel.`인지 `accel.`인지 정확히 복원할 수 없다. 일반적인 ‘점점 빠르게’ 기호를 도출하는 기능과 원문 보존 기능은 서로 다른 선택이다. `molto`의 별도 강도 배율을 추정하지 않는 것은 기존 확정 해석이며, 문구 유실의 근거로 재생 의미도 사라졌다고 판단해서는 안 된다. 반대로 실제 가속이 들리거나 수행 계획이 있다는 사실도 표시 보존의 통과 근거가 아니다.

같은 날 후속 질의에서 사용자는 **정간보에 `ㅈㅈㅅ`와 시작·목표 속도를 표시**하는 방식을 선택했다. 이는 저장된 가속 의미를 표시하는 선택이며 원문 단어를 복원한다는 뜻은 아니다. S15P31E102-956은 숫자 구간을 표시용 기호·속도 범위로 도출하되 원본 이벤트·저장 payload·수행 계획은 유지한다. 수동 한배가 우선하며 편집과 휴대폰·공통 연습 경로에 적용한다. 실제 입력의 제품 컴포넌트 표시·저장 불변성 검사와 사용자 휴대폰 인수를 구분하며, 게시·검증 상태는 [931 작업 기록](../../plans/S15P31E102-931.md#선택한-자동-표시의-구현검증)에 연결한다.

[931 실제 검사](../../plans/S15P31E102-931.md)에서 공식390의 새 AuClair 악보에 직접 입력한5종은 편집 표시·저장·재진입·음원 메타데이터에 유지됐다. 연습 화면에서 직접 확인한 표시는2·4·6·10각의4종이며14각 `ㅂㅅ`는 방전으로 확인하지 못했다. 기존 BlueVal 다운로드 파일의 두 가속 구간은52개 실제 파형 공격음이 점점 줄어드는 간격과 대응했다. 이 결과는 직접 입력의 확인된 동작과 기존 음원의 가속을 증명하며, 원문 `molto accel.`의 자동 표시나 수정 OMR로 재생성한 BlueVal을 증명하지 않는다.
