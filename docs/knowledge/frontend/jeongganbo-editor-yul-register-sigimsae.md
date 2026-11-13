# 정간보 편집의 악기별 악보 선택·율명 음역·시김새 입력 근거

- 작업: S15P31E102-551. 기준: `develop` 77cd0f34 시점 코드.
- 근거: `backend/.../db/migration/V5·V13·V17`(`pitches`), `CommonSheetScores.kt`·`CommonScoreTimelineDecoder.kt`(저장 검증), `frontend/.../core/model/Score.kt`(`SourcePitch`), `core/ui/score/ScoreNoteGlyph.kt`·`ScoreSigimsae.kt`.

## 악기와 악보의 연결

제공 곡은 CAT-01 `instrumentId`로 거를 수 있다. 내 악보 목록(SHT-01)과 `user_sheets`에는 악기 칸이 없다. 악기는 공통 악보 `score.instrument`(`DANSO` 등 서버 code)와 불러오기 변환 표(`sheet_conversions.instrument_id`)에만 있다.

그래서 불러오기 화면은 고른 악기의 내 악보를 SHT-08 문서의 `score.instrument`로 거른다. 회원당 악보가 최대 10개라 각 문서를 읽는 비용을 받아들였다. 문서를 읽지 못한 악보는 어느 악기 목록에도 넣지 않고 수만 알린다. SHT-01이 `instrumentId`를 주면 이 조회를 없앨 수 있다.

SHT-03(빈 악보·복사본 만들기)은 작업 취소됐다. 제공 곡 복사본은 CAT-04 원본으로 열어 고칠 수 있지만 서버에 저장할 수 없다. 내 악보는 복사하지 않고 그 악보를 직접 고친다(SHT-08·11·04).

## 율명과 음역

앱의 공통 악보 음(`ScoreNote.SourcePitch`)은 기본 12율 × 옥타브 -2~2를 표현한다. 저장 글자는 한글 앞말(`하배`·`배`·`청`·`중청` + 율명)이고 이벤트는 `yul`·`octave`다. 사용자 용어로 탁성은 `배`(옥타브 -1), 배탁성은 `하배`(-2)에 대응한다.

내 악보 저장(SHT-04·11)은 `pitches` 표 전체와 (율명, 옥타브)를 대조하고 없으면 거절한다. 2026-11-07 마이그레이션 기준 등록 음은 다음과 같다.

| 옥타브 | 등록 율명 |
| --- | --- |
| -2 (배탁·하배) | 없음 |
| -1 (탁·배) | 임·이·남·무·응 |
| 0 | 12율 전체 |
| 1 (청) | 12율 전체 |
| 2 (중청) | 황·태·협·고·중·임·남 |

편집기는 CAT-06 `/api/pitches`(악기 생략)로 이 목록을 받아 없는 음의 자판을 막는다. 목록을 받지 못하면 막지 않고 저장 때 서버가 확인한다. 악기별 음역(`instruments.range_*`)으로는 막지 않는다. 기존 곡에 그 범위 밖 음이 있는지 확인하지 않았기 때문이다.

## 글꼴

앱 세리프 글꼴(`noto_serif_kr_semibold`)에는 기본 12율과 潢·汰·淋만 있다. 나머지 음역 한자는 `ScoreNoteGlyph`가 변(氵·亻·彳)과 기본 율명을 겹쳐 그린다. 글꼴에 `彳`가 없어 배탁 표기는 시스템 글꼴에 기대며, 웹(Wasm)에서는 빈 상자로 보일 수 있다. 현재 등록 음에 배탁이 없어 편집기에서는 입력되지 않는다.

## 시김새

공통 악보 `events[].sigimsae`는 이벤트(소박)마다 있는 문자열 목록이고, 서버는 문자열인지만 검사한다. 편집 상태도 소박 자리(정간·소박 순서)마다 원본 목록을 모두 담고, 바뀐 소박만 그 목록으로 바꿔 저장한다. 그래서 나눈 정간에서 고른 소박에 붙인 시김새는 그 소박의 이벤트에 저장된다. 소박을 고르지 않으면 정간의 첫 율명 소박에 붙인다. 이음 자리는 앞 이벤트를 가리키므로 시김새를 두지 않는다. 한 소박에는 3개까지 붙인다. 율명 오른쪽에 세로로 쌓아도 정간 높이 안에 들어가는 수다.

선택 목록은 처음 네 가지(요성표·퇴성·추성·전성) 뒤에 공통 렌더러에 그림이 등록된 한글 이름을 등록 순서대로 둔다. 새 이름을 만들지 않는다. 채점 요청은 시김새가 있는 음의 음높이 점수를 비운다(`ScoreJsonNotes`). 시김새를 붙이면 그 음의 채점 방식도 바뀐다.

## 소박 편집과 정간 높이

편집 상태의 한 정간은 음 목록이고, 표시 자리(row/col)는 `layout`이 같은 순서로 만든다. 악보에서 누른 자리를 이 순서로 바꿔 소박 하나만 고르고 바꾼다. 나눔과 다른 소박은 그대로 둔다. 소박 하나를 지우면 빈 자리로 남겨 나눔을 유지한다.

나눈 정간의 율명은 두 음 14sp, 세 음 13sp로 줄어든다. 정간 높이 44dp를 나눠 쓰면 글자가 위아래로 잘린다. 편집 화면은 악보에서 가장 많이 나눈 줄 수 × 28dp 이상으로 모든 정간을 늘린다. 각마다 높이가 다르면 옆 각과 가로줄이 어긋나므로 정간별로 높이를 다르게 두지 않았다.

## 검증과 미확인

`YulInputTest`, `SheetDraftTest`, `SheetDocumentEditingTest`, `EditPickViewModelTest`, `SheetEditStateTest`로 한글 해석·음역 단계·저장 형식 왕복·여러 시김새·악기별 목록을 확인한다. 가짜 저장소 검증은 실제 서버 저장 성공이 아니다. 실서버 저장, Android·iOS 실기기 표시, 등록 시김새 전체의 정간 안 배치는 확인하지 않았다.

## 산조 시김새의 한글 대체 출력 (847)

2026-11-13, [S15P31E102-847](https://ssafy.atlassian.net/browse/S15P31E102-847). 대상은 사용자가 정정한 산조대금 진도아리랑(songId 85, scoreVersion 1)이다. CAT-04 응답의 시김새는 49개이고, 혀치기 10·반음올림표 16·꺾는음 5개가 앱의 그림 조회에 등록되지 않아 31개가 한글로 출력됐다.

악보 문자열이 서버에 있다는 것과 화면용 그림이 연결됐다는 것은 다르다. 연습 화면은 `CommonScoreData` → `Song.writtenScoreAnnotations` → `PracticeUiState.scoreAnnotations` → `JeongganboView` → `scoreSigimsaeImage` 순서로 읽는다. 이 경로는 CAT 기호 사전을 조회하지 않는다. 그림 조회 결과가 null이면 `JeongganboView`가 원본 이름을 `Text`로 그린다. `@` 뒤의 위치 값은 기존 이름 추출에서 이미 제외되어 이번 누락의 원인이 아니다.

| 원본 이름 | 표시 | 근거·보존 조건 |
| --- | --- | --- |
| 혀치기 | 원과 윗획으로 된 기호 | 국립국악원 『창작을 위한 국악기 이해와 활용』 대금 p164 표4를 로컬 원본에서 렌더·확인. 자체 벡터로 그렸다. 격음·특강표로 치환하지 않는다. |
| 반음올림표 | 올림표(♯) | [산조 판본의 의미 검수](../data/sanjo-playback.md)를 따라 기존 +1반음 의미와 원본 이름을 보존하고 표준 올림표를 자체 벡터로 그렸다. |
| 꺾는음 | 닫는 괄호 모양 | [산조 판본 검수](../data/sanjo-playback.md)와 공식 대금 교재 p158의 모양 설명. 기존 `sig_flow` 그림을 재사용하되 연주 의미를 흘림표로 바꾸지 않는다. |

새 대응은 표시 함수에만 두어 편집 입력 목록·채점·재생을 확장하지 않는다. 원본 이름·변형·ID·tick과 기존 47개 기호 그림을 유지한다. [표시 스펙](../../specs/score-sigimsae-display.md)을 따른다.

`ScoreSigimsaeTest`는 실제 9종 문자열의 기호 해석과 입력 목록 보존을 검사한다. `SanjoJindoCapturedScoreTest`는 Git 제외 CAT-04 캡처를 실제 공통 악보 어댑터와 6개 연습 모드에 넣어 진도아리랑 49개·산조 몽금포타령 5개·소금 몽금포타령 0개의 표시 및 원본 이벤트·tick 보존을 검사한다. 네트워크 없이 재생하며 캡처가 없는 일반 실행에서는 이 검사 하나만 명시적으로 생략한다.

`frontend/`에서 재현한다. 원본 악보 응답은 `build/`에만 두고 커밋하지 않는다.

```powershell
New-Item -ItemType Directory -Force build/sigimsae-847
85,81,94 | ForEach-Object { Invoke-WebRequest "https://k15e102.p.ssafy.io/api/songs/$_/score" -OutFile "build/sigimsae-847/song-$_.json" }
$env:EOLSSU_SIGIMSAE_FIXTURE_DIR = (Resolve-Path build/sigimsae-847).Path
.\gradlew.bat :shared:testAndroidHostTest --tests '*ScoreSigimsaeTest' --tests '*SanjoJindoCapturedScoreTest'
```

같은 입력으로 수정 전 두 검사 실패, 수정 후 세 검사 통과를 확인했다. 전체 `:shared:testAndroidHostTest`는 1,596개 중 1,592개 통과·기존 조건부 검사 4개 생략·실패 0개다. `:androidApp:assembleDebug`, `:shared:compileKotlinJs`, `:shared:compileKotlinWasmJs`도 성공했다. 이는 기호 해석·데이터 경로·컴파일과 자체 벡터 확인 결과이며 앱 화면·실기기·iOS 검증은 아니다.
