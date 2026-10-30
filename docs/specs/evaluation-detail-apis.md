# 오류 음표·취약 구간·구간 재평가 비교 API (EVA-02·03·04)

- Jira: EVA-02·03은 S15P31E102-307, EVA-04는 S15P31E102-310. Story S15P31E102-37, Epic S15P31E102-20. 계획·검증: [307 계획](../plans/S15P31E102-307.md) · [310 계획](../plans/S15P31E102-310.md)
- 현재 구현 계약. 노션 API 명세서의 EVA-02·03·04 계약 초안을 따르고, 저장 데이터로 채울 수 없는 필드는 아래처럼 고정값으로 둔다. 채점을 다시 하거나 결과를 저장하지 않는다.

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
| items[] | noteIdx 오름차순. noteIdx·verdict·pitchErrorCent·onsetErrorMs·durationErrorMs는 `note_results` 저장값, expectedPitchId·detectedPitchId는 STRING(인식 못 하면 null) |
| items[].errorTypes | 저장 판정이 음표당 하나라 오류면 `[verdict]`, CORRECT면 `[]` |
| items[].recordingOffsetMs | 정렬 오프셋을 저장하지 않아 항상 null |
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

비교 구간은 이번 세션의 연습 구간이다. `baselineSessionId`를 생략하면 PRA-01에서 세션을 만들 때 넘긴 `parentSessionId`를 쓴다.

| 이전 세션 상황 | HTTP |
| --- | --- |
| baselineSessionId·parentSessionId 모두 없음, 자기 자신, 다른 곡, 이번 세션보다 나중에 시작함(startedAt), 같은 악보 버전인데 이번 구간을 모두 담지 않음 | 400 |
| 없음·타인·기록 삭제 | 404 |
| 미완료 | 409 |

| result 필드 | 값 |
| --- | --- |
| baselineSessionId | 실제로 비교한 이전 세션 ID(STRING) |
| comparable | 아래 비교 조건을 모두 만족하면 true |
| reason | comparable=false일 때 이유 문장, true면 null |
| startNoteIdx·endNoteIdx, startColumnIdx·endColumnIdx | 이번 세션 구간과 그 첫·끝 음표의 각(0부터) |
| before | 이전 결과. totalScore·pitchScore·timingScore·durationScore는 이전 세션 구간이 이번과 같을 때만 저장 점수, 다르면 null. errorCount는 비교 구간의 저장 판정으로 센다. 이전 결과의 악보 버전이 다르면 음표 순번이 맞지 않아 점수·errorCount 모두 null이고 구간 포함 여부도 검사하지 않는다 |
| after | 이번 저장 점수 4개, 비교 구간 errorCount, bpm·bpmUnit |
| delta | comparable=true일 때만 `{totalScore, errorCount}`(이번 − 이전), 아니면 null |
| correctedNoteIndices | 이전에 오류였고 이번에 CORRECT인 noteIdx. 악보 버전이 다르면 `[]` |
| remainingErrors | 이번에도 틀린 음표 `{noteIdx, verdict}` |

### 비교 조건

다음 순서로 확인해 처음 걸린 하나를 reason으로 쓴다: 악보 버전 같음 → 구간 같음 → `evaluations.model_version` 같음 → bpm·bpmUnit 같음.

점수는 음표별 오차와 AI 허용 범위로 계산되어 저장 판정만으로는 다시 만들 수 없다. 그래서 곡 전체 등 다른 구간의 이전 점수를 이번 구간 점수로 바꾸지 않고(노션 규칙 1·5) 비워 둔다. 판정 정책 버전은 저장하지 않아 비교 조건에 넣지 못했다.

## 미구현·후속

- 복수 오류 유형, DURATION 판정, 녹음 위치(recordingOffsetMs)는 AI 응답과 `note_results` 저장 형식을 바꿔야 한다. 이 Task 범위 밖이다.
- 누락(MISSED) 표시 방식과 '취약 구간 연습' 버튼의 1차 이동 화면은 디자인·기획 결정이 필요하다. 서버 응답은 결정과 관계없이 같다.
- EVA-04를 쓰는 구간 재평가 화면(RESULT-04)과 구간 반복 연습(PRACTICE-02·03)은 와이어프레임 기준 2차 확장이다. 서버 API는 먼저 구현했다.
- 이전 결과와 구간이 다를 때 구간 점수를 보이려면 AI가 구간 재집계 공식을 제공해야 한다(노션 규칙 5).
- 노션 EVA-04 초안과 다른 점: before 점수 4개와 before.errorCount는 초안에서 필수 INT지만, 구현은 이전 결과가 다른 구간이면 점수 4개를, 악보 버전이 다르면 errorCount까지 null로 준다. baselineSessionId도 초안은 필수지만 생략하면 parentSessionId를 쓴다. FE는 이 필드를 nullable로 받아야 하며, 노션 초안 갱신과 FE 공유가 남아 있다.

## 구현 경계·검증

API는 HTTP 매핑·쿼리 검증, application은 소유·상태·범위 판단과 읽기 전용 트랜잭션, domain은 취약 구간 계산과 구간 비교, infrastructure는 MyBatis 조회와 악보 JSON 해석을 맡는다. 단위 테스트로 구간 계산 규칙을, MockMvc로 상태·필터·400을, 실제 HTTP/MySQL 테스트로 V6 시드 악보의 정간 묶음과 404·409를 검증한다.
