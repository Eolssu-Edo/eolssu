# 정간보 한배 지원 인수인계 (S15P31E102-882)

2026-11-13 사용자 지시로 기능 추가를 중단하고 현재 구현을 Draft MR로 넘긴다. **구현 완료·병합 가능 상태가 아니다.** [지원 스펙](../../specs/jeonggan-tempo-edit.md), [숫자 빠르기 기존 구현](score-recognition-corrections.md), [재생 계약과 근거](../data/score-authoring-tempo.md).

## 합의한 동작

정간보의 `ㅈㄴ/ㅈㅅ/ㅈㅈㄴ/ㅈㅈㅅ/ㅂㅅ`를 인식·표시한다. 숫자가 없으면 기호와 위치를 보존하고 기존 빠르기 창에서 목표를 지정한다. 임의 0.8/1.2 배율을 적용하지 않는다. 입력판의 탭·정간을 추가하지 않는다. 공통 악보 v1의 기호와 기존 expressions/performancePlan을 재사용한다.

## 반영한 구현

- 이전882: 오선보 숫자 빠르기와 명시 시작 BPM, 서버 저장·생성 시간표 연결. Blue Valentine 실제 PDF 검증 근거는 기존 기록에 있다.
- AI: 글자층·인쇄 스캔 한배 후처리, 위치/검토 정보, 기호 배치와 명시 빈 숫자 계획. 새 모델 학습 없음.
- FE: 기존 빠르기 창 안 구간 추가·수정·삭제, 목표/끝 위치 입력, Draft·undo/redo·저장 연결, 악보의 세로 자모 표시. **아래 실패와 미검증이 남아 있다.**
- DATA: 기존 sourceSlots가 있는 편집본의 converter가 app-editor-1로 바뀌어 차단되던 연결을 보완했다. inferred 조건·음높이/anchor/기호/전체 위치/길이 검사는 유지한다.

## 확인된 검증

- AI 관련249검사 및 subtest2 통과. 실제 원본 PDF의48쪽 별곡 돌장 `ㅈㄴ`→c0-j3,134쪽 수제천4장 `ㅈㅅ`→c6-j9. FastAPI HTTP 각각5.69/7.84초 DONE. 동일 입력의 이전 코드와 비교해 JGB·음표·정간·ID·tick 보존(한배 기호와 converter 버전만 추가).
- DATA 전체269검사(인코더 조건부3제외) 통과는 sourceSlots 보완 **이전** 스냅샷이다. 그 보완 후 common preview+JGB import49검사는 통과했다. 최종 전체 회귀는 다시 실행해야 한다.
- 5기호×4악기의 목표 미지정은 실제 생성기에서4박4,000ms 유지. 미지정 기호+기존120BPM·2회 반복은5,000ms이며 입력을 보존했다.
- BE 빠르기7검사 통과. 실제 수제천 인식 결과를 JVM RecognitionResultBuilder에 넣어 서버 식별자 변경 뒤 SHA·기호/위치·미정 문제 보존을 확인했다. 고정 등록 음 목록을 사용한 코드 경계 검사이며 운영 DB 검증이 아니다.
- FE 최신 `:shared:testAndroidHostTest --tests '*SheetTempoEditingTest'`: 컴파일 성공, **9개 중8통과·1실패**. git diff --cached --check는 각 구현 커밋에서 통과.

## 이어서 할 일

1. **반복 보존 보완 우선:** `SheetTempoEditing.kt:120`에서 기존 명시 계획이 없는 악보에 최초 추가하면 repeats=[]를 만든다. 원본의 반복표/도돌이표가 기존 교육용 자동 반복으로 재생되던 경우를 보존하도록 고치고 검사해야 한다. 기존 명시 repeats 보존 검사는 통과했다.
2. **FE 실패 확인:** `frontend/shared/src/commonTest/kotlin/com/ssafy/eolssu/feature/song/edit/SheetTempoEditingTest.kt`의 `existingNumericRampRetainsExplicitStartAndDoesNotInventAMark`(79행). 작업자가 XML에서 int160/80과 double160.0/80.0의 JSON 문자열 비교 실패를 확인했다(수치는 동일). 중단 지시로 수정하지 않았다. 실제 start_bpm/목표/끝과 기호 부재가 보존되는지 검증하고 테스트를 정리한다.
2. 최종 합본의 FE 전체 host 회귀·Android APK·실제 UI 실행을 확인한다. iOS·실기기 청취·스크린샷은 미검증이다. 임시 고정 SheetDraft를 공유 모듈에서 SheetEditSettingsDialog에 연결하면 로그인/운영 데이터 없이 UI 확인 가능하다. onApply는 `(SheetDraft)->Unit`, selectedCell 인자가 있다. 검증용 우회는 제품에 넣지 않는다.
3. FE가 직렬화한 실제 요청을 BE 확정 저장의 prepare→DATA preflight까지 연결한다. 초안60→90 재진입 후 구간 배율이 한 번만 적용되는지, sourceSlots/relative 음열·기존 start_bpm·반복이 보존되는지 확인한다.
4. DATA 최종 전체 회귀와 실제 CommonSheetScores.prepare의 app-editor-1 결과+sourceSlots 사전검사를 실행한다. 작은 회귀는 green이나 실제 BE→DATA 통합 증거는 아직 없다.
5. 실제 PDF HTTP 응답의 전체곡 생성 사전검사와 인식 품질을 확인한다. 앞선150dpi 중간 JSON은 기존 음가1/27의 MIDI 해상도 한계로 실패했다. 정식300dpi HTTP 결과의 전체 생성은 확인하지 않았다. 한배 인식 성공을 전곡 채보·재생 정확도로 확대하지 않는다.
6. 실제 곡의 `ㅈㅈㄴ/ㅈㅈㅅ/ㅂㅅ` 사용 입력과 사진·다른 인쇄체는 미검증이다. 150dpi PNG48의 기존 격자 누락에서는 위치 미정 notice로 남긴다.

## 작업 위치·자료

주 worktree는 `staff-tempo-882`, FE 원본 작업은 `hanbae-fe-882`, BE/DATA 검사는 `hanbae-be-882`다(모두 로컬 Codex worktrees 아래). 부모 합본에 소유 커밋을 모았다. 원본 스캔·모델·runtime JSON은 Git에 넣지 않았다.

로컬 근거: 주 worktree `.scratch/882/hanbae-runtime/verification.json`, `http-{before-,}{48,134}.json`, `omr-tests.xml`; BE worktree `.scratch/hanbae-882/`의 playback/source-slot/server-actual 로그와 요청 JSON. FE worktree의 Gradle test-results를 보존했다. 공유할 때는 이 자료가 커밋에 없음을 유의한다.

실제 FE 요청 export는 FE worktree `.scratch/882/hanbae-fe-request.json`에 original/score/expressions/oldBpm/newBpm으로 있다. 다음 BE→DATA 검사에 사용한다.

Jira는 Story319 Relates·스프린트59321·진행 중으로 유지한다. 사용자 원본·계정 악보·운영 DB·배포를 바꾸지 않았으며 병합은 사람이 판단한다.
