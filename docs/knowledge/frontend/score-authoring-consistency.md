# 악보 추가·편집 경로 조사

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
