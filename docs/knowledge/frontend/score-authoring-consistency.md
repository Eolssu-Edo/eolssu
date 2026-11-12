# 악보 추가·편집 경로 조사

이 문서는 재분할 전 770~773 통합 구현의 조사·검증 이력을 보존한다. 현재 결과별 담당 범위와 검증은 [9개 Task 재분할 계획](../../plans/score-authoring-partition.md) 및 각 Task 계획을 따른다. 아래 통합 검증을 각 분할 MR의 독립 검증 완료로 간주하지 않는다.

통합 검증에서 추가로 확인한 경계: 이전 BE 디코더는 NOTE의 이벤트 음고와 시작 칸의 율명 일치를 검사하지 않았지만 FE 연습과 CAT는 거절했다. 새 작성 검증은 양쪽을 `NOTATION_PITCH_MISMATCH`로 확인한다. 전역 미등록 높은 음도 일반 형식 오류로 사라지지 않고 위치/사유를 반환한다. 표기 없는 원시 NOTE를 고칠 때는 이벤트 음고와 시작 표기만 바꾸고 길이·지속·숨표·시김새를 보존한다. 작은 회귀와 실제 HTTP 저장으로 검증했다.

320dp 에뮬레이터의 저장 거절 경로에서는 개별 대체음 모달이 전곡 변환 진입점을 가리는 문제가 드러났다. 모달에 `악기·음역 맞추기` 동작 하나를 추가해 사용자가 전곡 이조 검토를 먼저 선택할 수 있게 했다. 긴 안내와 자동 변환은 추가하지 않는다.

기준 origin/develop `929641c7`, 2026-11-11. Task770~773. 조사 결과이며 실물 악기의 연주 가능 여부를 확정하는 자료가 아니다.

| 경계 | 관찰한 사실 | 근거 |
| --- | --- | --- |
| 지원음 | CAT06은 악기 low/high 사이 전역 등록음을 반환하며 운지와 결합하지 않는다. 저장 디코더도 전역 음만 검사한다. | `CatalogReferenceQueries.kt`, `CommonSheetScores.kt`, `UserSheetConfig.kt` |
| 운지 | 최신 DB 고정 검사는 단소17·소금20·정악30·산조31행이다. 운지 없음은 미확인이다. | `BuiltinFingeringApiDatabaseTests.kt` |
| 입력 | 정간 입력과 오선 위아래 이동의 목록이 다르고 음원 악기 변경은 ID만 바꾼다. | `SheetEditUiState.kt`, `SheetEditViewModel.kt` |
| 원본 | 오선 변환기가 5율/연속 범위로 이조를 고르고 표기 밖 octave를 clamp한다. MIDI99가 중청황으로 바뀌는 재현을 확인했다. | `ai/src/omr_service/staff_musicxml.py` |
| 장단 | FE 이름 목록에 ID12가 없다. OMR는 ID12를 받지만 beats_per_jeonggan에서 KeyError. 개인 연습은 단모리 한 칸을8분음표 대신4분음표로 계산한다. | `SheetJangdanGuidance.kt`, `SheetPracticeRepository.kt`, `jeongganbo_text.py` |
| 저장 | null 장단 ID에 이름이 남아도 저장 가능하며 주기 검사가 없다. FE는 마지막 짧은 각을 허용하나 서버 반주 생성은 완전 주기를 요구한다. | `EditUserSheet.kt`, `SheetCompletionValidation.kt`, `ScoreGridJangdanPlayback.kt` |
| 빠르기 | UI/import/save/copy/audio는30~160, 공통 조회는30~400. DB V26/V30도30~160. 이 상수의 음악적/합성 한계 근거는 찾지 못했다. | 관련 서비스·Flyway, 481계획·음원 스펙 조사 |
| 변속 | 60BPM4박, 둘째칸 조금느리게·셋째칸 본래속도로의 선율 시작은0/1000/2250/3250ms·총4250ms. 현재 장구는0/1000/2000/3000/4000ms. | 실제 `common_preview` 컴파일과 `render_request.jangdan_events` 대조 |
| 수행 계획 | 숫자 tempos는 공통 v1 밖 companion에 있고 정간/분 단위다. CLI는 전달하지만 내부 HTTP는 받지 않는다. | `common_preview.py`, `tempo_repeat.py`, `service.py`, `render_request.py` |

물리·합성·운지는 다르다. DATA profiles의 소금 물리 가이드는70~96·전통 관행70~92, 단소 기술 가이드66~92, 정악58~92·CAT 확보58~89, 산조 물리 범위는 null이다. 서비스의 synthesis_extension은 원음을 유지한 합성이며 운지 증명이 아니다. 기존 DB range는 검증된 물리 한계로 재해석하지 않는다.

수치 BPM 제한 대안은 MIDI Set Tempo 범위, 컴파일된 전체 변속/반복/꾸밈의 MIDI tick과44.1kHz PCM frame, 생성 길이600초·M4A15MiB·측정된 실행시간 제약이다. 합성기는29/161/300/1200 BPM 컴파일을 통과했고 HTTP30~160은29/161을 거부했다. 실제 CAT 검증은 이 컴파일 결과와 별도다.

초기 검사: BE CommonSheetScores/EditUserSheet/JangdanPlayback/SongScoreController51개 통과. DATA playback service9개 통과(가짜 renderer). 새 컴퓨터에는 확인한 main/worktree의 raw/processed/CAT 자산과 FFmpeg가 없고 mido도 미설치다. 실제 CAT 실행은 아직 미검증이다. 이전 Downloads나 Git 제외 출력의 존재를 가정하지 않았다.


## 통합 결과와 재현 경계

최신 develop `de674ac8`까지 통합했다. 실제 음원 생성 회귀는 [고정8박 악보](../../../backend/src/test/resources/tempo-authoring/supported-first-rest-id12.json)와 [인증 API 테스트](../../../backend/src/test/kotlin/com/ssafy/eolssu/backend/feature/sheet/SheetAuthoringCatDatabaseTests.kt)에 남겼다. 단소75/77·첫 쉼1.5박·ID12·60→30 구간 빠르기에서 저장 판본2·12초 수행표·12.55초 M4A와 원본/음원 SHA를 확인했다. DB는 MySQL8.4, CAT/FFmpeg는 실제 실행, 객체 저장소만 대역이다. [전체 검증 기록](../../plans/S15P31E102-770.md#최종-통합-검증-2026-11-11).

Android 에뮬레이터의320×568dp/글꼴100·130%에서 긴 변환 목록과 설정 목록을 검사했다. 제목·적용·취소를 고정하고, 변환 세부 사항은 펼쳐 본다. 음 확인 모달은 한 줄 사유·전곡 변환 주동작·명시적 개별 수정 목록이다. 원본 NOTE를 표시할 수 없을 때 빈칸처럼 보이던 문제는 표시 모델에서 원본 율명/옥타브를 보존하여 수정했다. 화면용 범위를 확정 저장 허용에 사용하지 않는다.

구간 빠르기 보호 안내의 실제 UI 검사에서 `LaunchedEffect(message)`가 안내 표시 전에 message를 소비하여 스스로 취소되는 문제를 재현했다. snackbar 완료 후 소비하도록 수정했다. 미확인 음·빠르기 거절은 초안과 입력을 보존하며 사용자가 수정 후 재시도할 수 있다.

실제 UI 검사는 localhost 고정 API fixture이며 실제 API·CAT 결과와 분리한다. 실물 악기 연주·실기기 청취·iOS·운영 배포는 검수하지 않았다. 원래 게시 악보와4판 음원/R2 객체를 변경하지 않았다.
