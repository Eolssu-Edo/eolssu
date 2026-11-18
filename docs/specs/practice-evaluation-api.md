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
| tuningOffsetCent | NUMBER 또는 null, AI가 추정한 전체 음높이 편차(cent). 범위 초과일 때도 보정 전 추정값을 보존 |
| tuningOutOfRange | BOOLEAN 또는 null, AI가 해당 분석의 조율 보정 범위를 벗어났다고 판정했는지 여부 |
| notes | ARRAY, noteIdx 오름차순. 악보 NOTE 순번은 원본 순서 그대로 |
| notes[].noteIdx, notes[].verdict | INT·STRING, 저장 순번과 판정 |
| notes[].pitchErrorCent, onsetErrorMs, durationErrorMs | INT 또는 null, 저장 오차 그대로 |

feedbackMessage·tip·previousTotalScore·savedAt·modelVersion·startNoteIdx·endNoteIdx는 이 응답에 포함하지 않는다. mission은 미션을 구현하지 않기로 해 명세에서도 뺄 항목이다(S15P31E102-465). 원본 녹음 키·서명 URL도 노출하지 않는다. 구간·유형별 개수가 필요한 `/evaluation/notes`는 [EVA-02·03 스펙](evaluation-detail-apis.md)에 있다.

## 구현 경계·검증

### 조율 정보 보존 (S15P31E102-304)

2026-10-30 사용자 요청으로 AI 응답의 두 조율 필드를 `evaluations`에 저장하고 이 조회 응답에 전달한다. `tuning_offset_cent DOUBLE NULL`, `tuning_out_of_range BOOLEAN NULL`을 새 마이그레이션으로 추가한다. 기존 평가와 두 필드를 제공하지 않는 이전 AI 응답은 둘 다 null이며, 0·false로 추정하거나 과거 평가를 다시 계산하지 않는다. 제공한 값은 소수점 반올림 없이 보존한다.

AI가 한 필드만 제공하거나 편차가 유한한 수가 아니면 계약 오류로 처리한다. 범위 초과 여부를 백엔드에서 재계산하지 않으며 점수·음표 판정·채점 허용치는 변경하지 않는다. null은 미수집, false는 AI의 범위 내 판정으로 구분한다. 기존 FE는 미지 필드를 무시하므로 이 변경에서 화면 경고는 추가하지 않는다.

완료 조건은 파싱·저장·조회 자동 검사, 기존 행의 null 보존과 마이그레이션 검사, 실제 AI HTTP 분석 결과를 격리 MySQL에 저장한 뒤 DB·평가 GET 응답의 두 값이 일치하는 실행 증거다. 운영 DB 적용·배포는 이 구현 검증 이후 별도 단계다.

API는 HTTP 매핑만, application은 소유 조회·완료 상태 판단과 읽기 전용 트랜잭션, domain은 저장소·결과 계약, infrastructure는 MyBatis와 UTC 변환을 맡는다. config가 구현을 조립한다. 계층 분리로 HTTP·DB·채점 계약을 바꾸지 않았다.

컨트롤러 테스트와 실제 HTTP/MySQL 테스트로 401/404/409/200, 점수·음표 순서·오차·UTC·오디오 길이를 검증한다. 실행 기록은 [257 계획](../plans/S15P31E102-257.md)을 따른다. 운영 AI·R2 및 FE 화면 검증은 범위 밖이다.
