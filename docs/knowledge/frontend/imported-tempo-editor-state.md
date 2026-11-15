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

## 숫자 가속과 표시용 한배의 분리 (956)

숫자 계획이 있어도 `placements=[]`이면 기존 한배 렌더러는 표시할 기호를 받지 않는다. 이는 가속 재생이나 계획의 소실과 다른 표시 문제다. 2026-11-15 사용자의 최신 선택은 **악보에 `ㅈㅈㅅ`만 표시하고 숫자는 기존 편집에서 확인**하는 것이다. 최초 MR의 악보 위 숫자 카드는 이 지시로 제거한다. 원문 `molto`의 정도를 숫자 계획만으로 복원하지 않는다.

956은 `CommonScoreData.toScore`에서 유효한 상승 구간의 시작에 표시용 한배만 투영한다. 명시 한배가 같은 정간의 어느 소박에든 있으면 이를 우선하며, 파생 기호를 `tempoAnnotations`나 export에 추가하지 않는다. 저장은 여전히 `orderedPlacements → scoreWithTempo/exportExpressions`를 사용한다. 가속 판정의 명시 시작값은 앞 구간 목표보다 우선한다. 카드 전용 `ScoreTempoRange`·`Score.tempoRanges`·레이아웃과 리소스는 필요 없어 제거한다.

휴대폰 연습은 `PracticeScoreArea`가 아니라 `PracticeScreen → PracticeBoardScore`를 사용한다. 두 연습 경로와 `EditScoreBoard`에서 숫자 카드와 이를 위한 wrapper를 모두 제거하고 원래 악보 높이/배치를 사용한다. `ScorePerformance.matches()`가 Score 전체를 비교하므로 기호 투영은 Song 최초 생성 때 수행하며 sidecar 결합 후 표시만 별도로 바꾸지 않는다.

기존 `SheetEditSettingsDialog`는 숫자 구간의 목표·끝 위치만 출력하고 명시 `startBpm`은 내부 상태로 보존했다. 악보 숫자 카드를 지우기만 하면 편집에서도 시작80을 읽지 못한다. 따라서 기존 구간 목록 설명에 명시 시작이 있을 때 `80 → 160`을 표시한다. 단위 변환과 전체 BPM 배율은 기존 `draft.tempoChanges`에 맡긴다. 목표/끝 위치 수정은 기존 동작을 사용하고 새 시작 속도 입력은 추가하지 않는다.

원본 BlueVal 응답의 투영·export·재진입 검사에서 26→30각과 58→62각의 80→160, 492음·24쉼·472박, 저장 payload/SHA·181.6355초 추정이 보존됐다. 소스 기호·음가를 새로 인식하거나 음원을 다시 합성한 검사는 아니다. 실제 화면·게시 상태와 환경 실패/회복의 근거는 [956 계획](../../plans/S15P31E102-956.md)에 기록한다.

최초 게시 당시 숫자 카드의 Android 캡처는 대체된 UI의 이력이다. 최신 지시 후속 증거는 [956 계획](../../plans/S15P31E102-956.md)의 별도 기록을 따른다. 입력별 packing 결과를 확인해야 하며 모든 휴대폰 캡처를 2행으로 일반화하면 안 된다. 인증 저장·실제 음원·실기기 검증과도 구분한다.

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
