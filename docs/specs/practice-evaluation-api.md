# 연주 평가 결과 조회 API

- Jira: S15P31E102-257, Story S15P31E102-37, Epic S15P31E102-20.
- 현재 구현 계약. 평가를 재실행하거나 점수를 재계산하지 않는다.

## 요청·응답

`GET /api/practice-sessions/{sessionId}/evaluation`, `access_token` 쿠키 인증. Query·Body 없음. sessionId는 서버의 Long ID이며 JSON의 ID는 문자열이다.

| 상황 | HTTP | 공통 오류 result.errorCode |
| --- | --- | --- |
| 본인·미삭제·DONE | 200 | 성공 응답 |
| 인증 없음·만료·비활성 회원 | 401 | UNAUTHENTICATED |
| 없음·타인·기록 삭제 | 404 | RESOURCE_NOT_FOUND |
| 본인 미완료(RECORDING/PENDING/FAILED) | 409 | STATE_CONFLICT |

성공 envelope는 `httpStatus=OK`, `isSuccess=true`, `code=200`, `message=연주 평가 결과 조회 성공`, `result`다.

| result 필드 | 타입·의미 |
| --- | --- |
| sessionId, evaluationId, songId | STRING, 저장 ID. DONE 성공의 evaluationId는 null이 아니다 |
| scoreVersion | INT, 세션에 고정한 악보 버전 |
| bpm, bpmUnit | INT·STRING, 세션의 연주 속도·단위 |
| startedAt, evaluatedAt | UTC ISO8601 STRING |
| endedAt | UTC ISO8601 STRING 또는 null |
| totalScore, pitchScore, timingScore, durationScore | INT, DB 저장 점수 그대로 |
| recordingDurationMs | INT 또는 null, 검증된 업로드 오디오 길이(ms). 메타데이터 없는 기존 행은 null |
| notes | ARRAY, noteIdx 오름차순. 악보 NOTE 순번은 원본 순서 그대로 |
| notes[].noteIdx, notes[].verdict | INT·STRING, 저장 순번과 판정 |
| notes[].pitchErrorCent, onsetErrorMs, durationErrorMs | INT 또는 null, 저장 오차 그대로 |

feedbackMessage·tip·previousTotalScore·savedAt·mission·modelVersion·startNoteIdx·endNoteIdx는 이 응답에 포함하지 않는다. 원본 녹음 키·서명 URL도 노출하지 않는다. 구간·유형별 개수가 필요한 `/evaluation/notes`는 [EVA-02·03 스펙](evaluation-detail-apis.md)에 있다.

## 구현 경계·검증

API는 HTTP 매핑만, application은 소유 조회·완료 상태 판단과 읽기 전용 트랜잭션, domain은 저장소·결과 계약, infrastructure는 MyBatis와 UTC 변환을 맡는다. config가 구현을 조립한다. 계층 분리로 HTTP·DB·채점 계약을 바꾸지 않았다.

컨트롤러 테스트와 실제 HTTP/MySQL 테스트로 401/404/409/200, 점수·음표 순서·오차·UTC·오디오 길이를 검증한다. 실행 기록은 [257 계획](../plans/S15P31E102-257.md)을 따른다. 운영 AI·R2 및 FE 화면 검증은 범위 밖이다.
