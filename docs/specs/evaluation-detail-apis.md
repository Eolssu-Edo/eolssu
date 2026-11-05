# 오류 음표·취약 구간·구간 재평가 비교 API (EVA-02·03·04)

- Jira: EVA-02·03은 S15P31E102-307, EVA-04는 S15P31E102-310·S15P31E102-494, EVA-02의 인식 음·녹음 위치는 S15P31E102-480([계획](../plans/S15P31E102-480.md)). Story S15P31E102-321, Epic S15P31E102-20. 계획·검증: [307 계획](../plans/S15P31E102-307.md) · [310 계획](../plans/S15P31E102-310.md) · [494 계획](../plans/S15P31E102-494.md)
- 현재 구현 계약. 노션 API 명세서의 EVA-02·03·04 계약을 따르고, 저장 데이터로 채울 수 없는 필드는 아래처럼 고정값으로 둔다. 채점을 다시 하거나 결과를 저장하지 않는다.
- EVA-04는 2차 계약(S15P31E102-382)의 응답 모양과 이전 세션 선택까지 구현했다. 점수 비교만 음표별 부분 점수가 필요해 남아 있다([남은 2차 계약](#eva-04-남은-2차-계약)).

## 공통

`access_token` 쿠키 인증, Body 없음. 인증·소유·상태 규칙은 [EVA-01](practice-evaluation-api.md)과 같다.

| 상황 | HTTP | result.errorCode |
| --- | --- | --- |
| 본인·미삭제·DONE | 200 | 성공 응답 |
| 인증 없음·만료 | 401 | UNAUTHENTICATED |
| 없음·타인·기록 삭제 | 404 | RESOURCE_NOT_FOUND |
| 본인 미완료, (EVA-03·04) 세션 이후 곡 악보 버전 변경 | 409 | STATE_CONFLICT |
| 쿼리 형식·범위 위반 | 400 | INVALID_REQUEST |

## EVA-02 `GET /api/practice-sessions/{sessionId}/evaluation/notes`

| Query | 의미 |
| --- | --- |
| startNoteIdx, endNoteIdx | 생략하면 세션 연습 구간의 양 끝. 양 끝 포함이며 세션 구간 밖이거나 시작 > 끝이면 400 |
| errorsOnly | 기본 false. true면 CORRECT가 아닌 음표만 items에 담는다 |

| result 필드 | 값 |
| --- | --- |
| evaluationId | STRING |
| items[] | noteIdx 오름차순. noteIdx·verdict·pitchErrorCent·onsetErrorMs·durationErrorMs는 `note_results` 저장값, expectedPitchId·detectedPitchId는 STRING |
| items[].detectedPitchId | AI가 인식한 음의 `pitches.id`. MISSED, `pitches`에 없는 음, 인식 음을 주지 않은 AI로 채점한 평가면 null |
| items[].errorTypes | 저장 판정이 음표당 하나라 오류면 `[verdict]`, CORRECT면 `[]` |
| items[].recordingOffsetMs | 업로드 원본 녹음의 재생 0ms부터 잰 음표 시작 시각(ms, 0 이상 정수). MISSED이거나 녹음 위치를 주지 않은 AI로 채점한 평가면 null |
| errorCount | 조회 구간의 오류 음표 수. errorsOnly와 관계없다 |
| errorCounts.pitch·timing·missed | 조회 구간의 저장 판정별 개수 |
| errorCounts.duration | DURATION 판정이 저장 값에 없어 항상 0 |

개수는 음표 단위다. 화면의 '틀린 칸'은 FE가 CAT-04 cellId로 묶어 센다.

## EVA-03 `GET /api/practice-sessions/{sessionId}/weak-sections`

`limit`은 1~10, 기본 1이다. 범위 밖은 400이다.

| result 필드 | 값 |
| --- | --- |
| sessionId | STRING |
| totalErrorCellCount | 세션 구간에서 오류 음표가 하나라도 있는 정간 수 |
| items[] | 추천 순서. 모두 맞으면 `[]` |
| items[].startNoteIdx·endNoteIdx | 고른 각에 걸친 세션 구간 안 첫·끝 음표(포함) |
| items[].startColumnIdx·endColumnIdx | 0부터 센 각 번호. 화면의 각 번호는 +1 |
| items[].errorCount | 그 각들 안의 틀린 칸(정간) 수 |
| items[].dominantError | 가장 많은 저장 판정. 같으면 MISSED > PITCH > TIMING |
| items[].reason | 보조 설명 문장. 화면 문구는 FE가 숫자로 조합한다 |

### 고르는 규칙 (초안)

1. 세션 악보 버전의 `songs.score_json`에서 NOTE 순서대로 음표의 정간(cellId)과 각을 읽는다. 순번 규칙은 분석 요청·CAT-04와 같다. 현재 곡 악보 버전이 세션과 다르면 409다.
2. 세션 구간 안에서 이어진 2각 창마다 틀린 칸 수를 센다. 가장 많은 창을 고르고, 같으면 앞 창을 고른다. 창 양 끝의 틀린 칸 없는 각은 뺀다.
3. 이미 고른 구간과 각이 겹치지 않는 창 중에서 limit개가 될 때까지 반복한다. 틀린 칸이 없는 창은 고르지 않는다.

창 길이 2각, 정렬 기준, 판정 우선순위는 노션 초안의 검토 필요 항목이다. AI 임계값 검증 결과에 따라 바뀔 수 있다.

## EVA-04 `GET /api/practice-sessions/{sessionId}/comparison`

비교 구간은 이번 세션의 연습 구간이다. 비교 구간 음표의 저장 판정으로 오류 수·고친 음표·남은 오류를 비교한다. 이전 결과가 곡 전체 등 더 넓은 구간이어도 같은 음표끼리 센다.

### 이전 세션 선택

1. `baselineSessionId`(`baselineSource=QUERY`)
2. 이번 세션의 `parentSessionId`(`PARENT`)
3. 같은 곡·본인·미삭제·DONE이고 이번보다 먼저 시작했으며 이번 구간을 모두 담은 세션 중 가장 최근 것(`PREVIOUS`). `started_at`·`id` 내림차순이고, 시작 시각이 같으면 id가 작은 쪽을 먼저 시작한 세션으로 본다. 기록 저장 여부는 따지지 않는다.

3에서 찾지 못하면 오류가 아니라 200과 `baselineSessionId: null`을 준다(첫 연주). 악보 버전이 다른 세션은 구간 번호가 다른 음을 가리키므로 3에서도 구간 포함을 보지 않는다.

| 고른 이전 세션 상황 (1·2 공통) | HTTP |
| --- | --- |
| 자기 자신, 다른 곡, 이번 세션보다 나중에 시작함(startedAt), 같은 악보 버전인데 이번 구간을 모두 담지 않음 | 400 |
| 없음·타인·기록 삭제 | 404 |
| 미완료 | 409 |

`parentSessionId`의 세션을 지운 경우도 3으로 넘어가지 않고 404다. 첫 연주 처리는 3에서 찾지 못했을 때만 한다.

### 응답

| result 필드 | 값 |
| --- | --- |
| baselineSessionId·baselineStartedAt·baselineSource | 실제로 비교한 이전 세션 ID(STRING)·시작 시각·고른 방법. 비교 대상이 없으면 모두 null |
| startNoteIdx·endNoteIdx, startColumnIdx·endColumnIdx | 이번 세션 구간과 그 첫·끝 음표의 각(0부터) |
| isFullSong | 이번 세션이 곡 전체(0~noteCount−1)면 true |
| scoreComparable | 음표별 부분 점수를 저장하기 전까지 항상 false |
| errorComparable | 비교 대상이 있고 악보 버전이 같으면 true |
| notComparableReasons | SCORE_VERSION_CHANGED / MODEL_CHANGED / NO_NOTE_SCORES 중 해당하는 것 모두. 현재는 비교 대상이 있으면 NO_NOTE_SCORES가 항상 들어 있다. 비교 대상이 없으면 `[]`. SCORING_VERSION_CHANGED는 합산 규칙을 저장한 뒤에 쓴다 |
| bpmChanged | 이전과 bpm 또는 bpmUnit이 다르면 true. BPM이 달라도 비교한다. 비교 대상이 없으면 false |
| before | 비교 대상이 없으면 null. 점수 4개는 null, errorCount는 비교 구간의 이전 저장 판정 수(악보 버전이 다르면 null), bpm·bpmUnit |
| after | 이번 세션의 저장 점수 4개, 비교 구간 errorCount, bpm·bpmUnit |
| delta | 비교 대상이 없으면 null. 점수 4개는 null, errorCount는 이번 − 이전(악보 버전이 다르면 null) |
| correctedNoteIndices | 이전에 오류였고 이번에 CORRECT인 noteIdx. 없거나 errorComparable=false면 `[]` |
| remainingErrors | 이번에도 틀린 음표 `{noteIdx, verdict}`. noteIdx 오름차순 |

채점 모델이 달라도 오류 수는 비교한다. 판정 정책 버전은 저장하지 않아 조건에 넣지 못했다.

### EVA-04 남은 2차 계약

2026-11-03 사용자 결정(S15P31E102-382). 상세는 [Notion EVA-04](https://app.notion.com/p/401a0252aa45814980a6f98e0f191dfe)를 따른다. AI 담당 합의와 Task가 없어 아래는 구현하지 않았다.

- 구간 점수는 서버가 공식을 만들지 않고, 평가 때 AI가 돌려준 음표별 부분 점수(`pitchScore`·`timingScore`·`durationScore`·`weight`)를 비교 구간 음표만 모아 AI가 정한 합산 규칙(`scoringVersion`·`scoreWeights`)으로 합친다. 곡 전체를 합친 값은 AI가 준 총점·항목 점수와 같아야 한다. 이 응답 변경은 [Notion /v1/analyses](https://app.notion.com/p/408a0252aa4581528abee5ac1bdfd1a7)의 2차 변경 제안이다.
- 저장: `note_results`에 부분 점수·weight, `evaluations`에 `scoring_version`·`score_weights`를 더한다(새 Flyway). 기존 평가 행은 NULL이며 `NO_NOTE_SCORES`로 처리한다.
- 그때 `scoreComparable`을 실제 조건으로 바꾸고 `SCORING_VERSION_CHANGED`를 더하며, `before`·`delta`의 점수를 채운다. 응답 필드 모양은 바뀌지 않는다.

## 미구현·후속

- 복수 오류 유형과 DURATION 판정은 AI 응답과 `note_results` 저장 형식을 바꿔야 한다. 이 Task 범위 밖이다.
- 인식 음·녹음 위치는 S15P31E102-480에서 저장·응답을 구현했다. 값의 기준은 [PRA-03 분석 계약](practice-analysis-failure.md#분석-요청과-상태-조회-계약-s15p31e102-186)에 있고, AI가 두 필드를 보내기 전까지는 null이다.
- 누락(MISSED) 표시 방식은 디자인·기획 결정이 필요하다. 서버 응답은 결정과 관계없이 같다.
- '취약 구간 연습' 버튼은 2차에서 구간 선택(PRACTICE-02)으로 이동한다(2026-11-03 확정). 구간 반복(PRACTICE-03)은 서버를 부르지 않고, '이 구간 재평가'가 PRA-01 구간 세션(`parentSessionId` 포함)을 만든다.
- 디자인 RESULT-04의 점수 비교 문구는 위 남은 2차 계약이 구현된 뒤에 쓸 수 있다. 그전에는 `scoreComparable=false`이고 점수 비교 값이 없다.

## 구현 경계·검증

API는 HTTP 매핑·쿼리 검증, application은 소유·상태·범위 판단과 읽기 전용 트랜잭션, domain은 취약 구간 계산과 구간 비교, infrastructure는 MyBatis 조회와 악보 JSON 해석을 맡는다. 단위 테스트로 구간 계산 규칙을, MockMvc로 상태·필터·400을, 실제 HTTP/MySQL 테스트로 V6 시드 악보의 정간 묶음, 직전 세션 조회와 404·409를 검증한다.
