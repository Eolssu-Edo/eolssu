# 오류 음표·취약 구간 조회 API (EVA-02·03)

- Jira: S15P31E102-307, Story S15P31E102-37, Epic S15P31E102-20. 계획·검증: [307 계획](../plans/S15P31E102-307.md)
- 현재 구현 계약. 노션 API 명세서의 EVA-02·03 계약 초안을 따르고, 저장 데이터로 채울 수 없는 필드는 아래처럼 고정값으로 둔다. 채점을 다시 하거나 결과를 저장하지 않는다.

## 공통

`access_token` 쿠키 인증, Body 없음. 인증·소유·상태 규칙은 [EVA-01](practice-evaluation-api.md)과 같다.

| 상황 | HTTP | result.errorCode |
| --- | --- | --- |
| 본인·미삭제·DONE | 200 | 성공 응답 |
| 인증 없음·만료 | 401 | UNAUTHENTICATED |
| 없음·타인·기록 삭제 | 404 | RESOURCE_NOT_FOUND |
| 본인 미완료, (EVA-03만) 세션 이후 곡 악보 버전 변경 | 409 | STATE_CONFLICT |
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

## 미구현·후속

- 복수 오류 유형, DURATION 판정, 녹음 위치(recordingOffsetMs)는 AI 응답과 `note_results` 저장 형식을 바꿔야 한다. 이 Task 범위 밖이다.
- 누락(MISSED) 표시 방식과 '취약 구간 연습' 버튼의 1차 이동 화면은 디자인·기획 결정이 필요하다. 서버 응답은 결정과 관계없이 같다.
- EVA-04(구간 재평가 비교)는 2차 보류다.

## 구현 경계·검증

API는 HTTP 매핑·쿼리 검증, application은 소유·상태·범위 판단과 읽기 전용 트랜잭션, domain은 취약 구간 계산, infrastructure는 MyBatis 조회와 악보 JSON 해석을 맡는다. 단위 테스트로 구간 계산 규칙을, MockMvc로 상태·필터·400을, 실제 HTTP/MySQL 테스트로 V6 시드 악보의 정간 묶음과 404·409를 검증한다.
