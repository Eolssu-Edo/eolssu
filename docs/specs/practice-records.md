# 연습 기록 저장·목록·녹음 재생·삭제 API (REC-01~04)

- Jira: [S15P31E102-342](https://ssafy.atlassian.net/browse/S15P31E102-342)(이 API), 앱 연결 [S15P31E102-369](https://ssafy.atlassian.net/browse/S15P31E102-369), 녹음 보관 정책 [S15P31E102-375](https://ssafy.atlassian.net/browse/S15P31E102-375). 결정·진행: [342 계획](../plans/S15P31E102-342.md).
- 상태: 사용자 결정(2026-11-02, [342 계획](../plans/S15P31E102-342.md)의 13항목). REC-01~04와 PRA-03 변경을 S15P31E102-342에서 구현했고 develop 병합 전이다. 노션 API 명세 REC-01~04 초안을 기준으로 하고, 다른 점은 아래 '노션 초안과 다른 점'에 적는다. PRA-03 변경은 [PRA-03 계약](practice-analysis-failure.md)에도 반영했다.

## 목적

회원이 결과 화면에서 '연습 기록 저장'을 누른 연주를 곡별로 모아 보고, 메모를 남기고, 녹음을 다시 듣고, 필요 없으면 지울 수 있게 한다. 지금 앱은 기록 API가 없어 운영에서 저장·목록이 동작하지 않는다(`UnavailablePracticeRecordRepository`).

## 용어

- **기록**: 본인 세션 중 `saved_at`이 있고 `record_deleted_at`이 없는 것. 저장은 분석이 끝난(DONE) 세션만 할 수 있고, DONE은 다른 상태로 바뀌지 않는다.
- **지운 기록**: `record_deleted_at`이 있는 세션. 회원의 모든 조회에서 없는 세션과 같다(404). 평가 점수는 성장 집계에 남는다.
- **재생 가능**(`recordingAvailable`): `recording_url`이 있고 지운 기록이 아니다. 업로드 후 지난 시간은 보지 않는다(PRA-03과 같은 정의).

## 공통

- `access_token` 쿠키 인증(`/api/**` 공통). 경로의 `sessionId`는 양의 정수다.
- PUT·DELETE 본문은 없거나 `application/json`이어야 한다(그 밖은 공통 필터의 415).
- 응답은 공통 envelope이다. ID는 문자열, 시각은 UTC ISO8601이다.

| 상황 | HTTP | result.errorCode |
| --- | --- | --- |
| 인증 없음·만료 | 401 | UNAUTHENTICATED |
| 형식·범위 위반 | 400 | INVALID_REQUEST |
| 없음·타인·지운 기록, (REC-04) 저장하지 않은 세션, (REC-03) 재생 불가 | 404 | RESOURCE_NOT_FOUND |
| (REC-01) DONE이 아닌 세션 | 409 | STATE_CONFLICT |
| (REC-03·04) 녹음 저장소를 쓸 수 없음 | 503 | SERVICE_UNAVAILABLE |

503에는 업로드용 `RECORDING_STORAGE_UNAVAILABLE`을 쓰지 않는다. 그 문구("녹음을 저장하지 못했어요. 잠시 후 다시 올려 주세요")가 재생·삭제와 맞지 않기 때문이다.

### 기록 칸의 공통 의미 (REC-01 응답, REC-02 items)

| 필드 | 값 |
| --- | --- |
| isFullSong | `start_note_idx` = 0이고 `end_note_idx` = 곡 `note_count` − 1. 성장 집계의 전체 곡 연주와 같은 정의다 |
| startColumnIdx·endColumnIdx | 구간 첫·끝 음표의 각(0부터, 화면은 +1). EVA-03·04와 같은 규칙(`songs.score_json` NOTE 순서의 cellId)으로 읽는다. 세션 악보 버전이 지금 곡 악보 버전과 다르면 null이다 |
| recordingDurationMs | 업로드 때 저장한 녹음 길이(ms). 업로드하지 않은 세션이면 null이다. 녹음 파일이 정리돼 다시 들을 수 없어도 길이는 남는다(다시 들을 수 있는지는 `recordingAvailable`) |
| bpm·bpmUnit | 세션 값. bpm은 bpmUnit 단위로 센 값 |
| totalScore | 저장된 평가 총점(0~100) |
| startedAt | 세션 연주 시작 시각 |

## REC-01 `PUT /api/practice-sessions/{sessionId}/record`

연습 기록으로 저장하고 메모를 고친다.

| 본문 | 동작 |
| --- | --- |
| 없음, `{}`, `memo` 생략 | 저장만 한다. 이미 저장한 기록이면 아무것도 바꾸지 않는다 |
| `"memo": null` | 저장하고 메모를 지운다 |
| `"memo": "<문자열>"` | 앞뒤 공백을 자른다. 비면 메모를 지운다. 자른 뒤 500자(유니코드 문자 수, 이모지 1자)를 넘으면 400 |
| JSON 객체가 아님, `memo`가 문자열·null이 아님 | 400 |

- 처음 저장할 때 `saved_at`에 서버 시각을 넣는다. 다시 보내도 `saved_at`은 그대로다(멱등).
- 녹음 파일이 이미 없어도(재생 불가) 저장할 수 있다. 점수 기록으로 남는다.
- 응답 200 `result`: `sessionId`, `savedAt`, `memo`(STRING 또는 null), `songId`, `songTitle`, `startColumnIdx`, `endColumnIdx`, `isFullSong`, `startedAt`, `recordingDurationMs`, `bpm`, `bpmUnit`, `totalScore`.

## REC-02 `GET /api/users/me/practice-records`

저장한 기록을 곡별로 묶어 보여 준다(곡별 아코디언).

| Query | 의미 |
| --- | --- |
| songId | 선택. 펼칠 곡(양의 정수). 생략하면 가장 최근에 저장한 곡 |
| sort | `NEWEST`(기본) 또는 `OLDEST` |
| page | 0 이상, 기본 0 |
| size | 1~100, 기본 20 |

형식·범위를 벗어나면 400이다.

| result 필드 | 값 |
| --- | --- |
| totalRecordCount | 본인 기록 수 |
| songs[] | 기록이 있는 모든 곡(페이지 없음). `lastSavedAt` 내림차순, 같으면 `songId` 내림차순 |
| songs[].songId·title | 곡 ID(STRING)·제목 |
| songs[].recordCount | 그 곡의 기록 수 |
| songs[].lastSavedAt | 그 곡 기록 중 가장 최근 `saved_at` |
| songs[].bestScore | 그 곡 기록 중 최고 총점 |
| songId | items의 곡. 생략하면 `songs[0]`의 곡, 기록이 하나도 없으면 null. 지정한 곡에 기록이 없으면 그 값 그대로 두고 items는 `[]` |
| items[] | 그 곡의 기록. `NEWEST`는 `startedAt`·`sessionId` 내림차순, `OLDEST`는 오름차순. 페이지 밖이면 `[]` |
| items[] 필드 | `sessionId`, `songId`, `title`, `isFullSong`, `startColumnIdx`, `endColumnIdx`, `recordingDurationMs`, `bpm`, `bpmUnit`, `totalScore`, `startNoteIdx`, `endNoteIdx`, `startedAt`, `recordingAvailable`, `memo`, `isNew` |
| items[].isNew | `viewed_at`이 비어 있음. 기록 상세(PRA-03)를 처음 조회하면 false가 된다 |
| page·size | 요청 값 |
| totalElements·totalPages | 그 곡의 기록 수와 `ceil(totalElements / size)`. 기록이 없으면 0·0 |

목록 조회는 `viewed_at`을 바꾸지 않는다. 목록 머리의 주간 연습 횟수·최고점은 GRO-01이 맡는다.

## REC-03 `GET /api/practice-sessions/{sessionId}/recording`

녹음을 다시 듣는 임시 주소를 발급한다.

- 본인 세션이고 재생 가능하면 발급한다. 저장 여부와는 관계없다. 녹음이 없거나 지운 기록이면 404다.
- 응답 200 `result`: `sessionId`, `playbackUrl`(서명 URL), `urlExpiresAt`(발급 시각을 초 단위로 내린 값 + 15분. 실제 서명 만료보다 늦지 않다), `durationMs`(`recording_duration_ms`, 없으면 null), `contentType`(`audio/mp4`).
- 유효 시간은 분석 요청과 같은 15분이다. 만료되면 앱이 다시 받아 같은 위치부터 잇는다(노션 REC-03 규칙 7).
- R2에 파일이 실제로 있는지는 확인하지 않는다. 주소는 로그에 남기지 않는다.
- 저장소가 설정되지 않은 서버이거나 주소를 만들지 못하면 503이다.
- 로컬 녹음 저장소(`RECORDING_STORAGE_TYPE=local`)로 운영하는 서버도 503이다. 로컬 저장소의 읽기 주소는 AI가 쓰는 Compose 내부 주소라 앱에 내주지 않는다([녹음 저장 운영](../../infra/storage/README.md)). 앱에서 다시 들으려면 S3 호환 저장소(R2)를 쓴다.

## REC-04 `DELETE /api/practice-sessions/{sessionId}/record`

기록과 녹음을 지운다.

- 대상은 본인 기록이다. 저장하지 않은 세션, 지운 기록, 없음, 타인은 404다. 같은 요청을 다시 보내도 404이므로, 앱은 삭제 재시도에서 받은 404를 '이미 지워짐'으로 처리한다.
- 순서:
  1. 본인 기록인지 먼저 확인한다. 아니면 저장소를 건드리지 않고 404다.
  2. 녹음 파일을 지운다. R2는 없는 키 삭제도 성공으로 처리한다.
  3. DB를 조건부로 갱신한다: `record_deleted_at` = 서버 시각, `recording_url`·`memo` = NULL. `saved_at`, 평가, 음표 판정, `viewed_at`은 남긴다.
  - 2가 실패하면 503이고 DB는 바뀌지 않는다. 3에서 바뀐 행이 없으면(동시 삭제 등) 404다.
- 응답 200 `result`: `sessionId`, `deleted`(true), `recordingDeleted`(녹음 경로가 있어 지웠으면 true, 없었으면 false), `deletedAt`(저장한 `record_deleted_at`과 같은 값, 초 단위).
- 지운 뒤에는 PRA-03, EVA-01~04, REC-01~04가 모두 404다. 성장 집계(GRO-01~03)는 점수를 그대로 센다.

## PRA-03 변경 (`GET /api/practice-sessions/{sessionId}`)

- 지운 기록은 404다(지금은 200이고 `recordingAvailable`만 false).
- 기록이고 `viewed_at`이 비어 있으면, 응답 전에 `viewed_at`을 서버 시각으로 한 번 기록한다(조건부 갱신). 응답 필드는 바뀌지 않는다. 기록 상세가 아닌 화면이 PRA-03을 불러도 기록되며, 이는 받아들였다.
- `recordingAvailable`의 정의, 분석 대기 조회·시간 초과 처리는 그대로다.

## 녹음 보관 정책과의 관계 (S15P31E102-375)

- 이 API는 업로드 후 지난 시간을 보지 않는다. 저장한 기록의 녹음은 회원이 지우거나 탈퇴할 때까지 보관하고, 저장하지 않은 녹음은 업로드 후 30일에 지운다(2026-11-02 사용자 결정). 처리방침·R2 규칙·정리 작업은 375가 바꾼다.
- 375 전까지는 R2가 업로드 30일 뒤 파일을 지운다. 그래서 30일이 지난 녹음은 `recordingAvailable`이 true여도 재생 주소에서 R2 404가 날 수 있다. 해당 녹음은 테스트 데이터뿐이고, 375에서 `recording_url`을 한 번 비워 맞춘다.

## 유지하는 동작

- 성장 집계는 저장·삭제와 관계없이 DONE 평가를 센다([연습 시간·성취도](practice-time.md)).
- 업로드·분석 흐름, PRA-03의 분석 대기 조회와 시간 초과 처리, 탈퇴 일괄 삭제(녹음 포함)는 바꾸지 않는다.
- EVA-01~04 계약(지운 기록 404 포함)은 그대로다.

## 노션 초안과 다른 점

노션 명세를 이 문서에 맞게 고쳐야 한다(열린 항목 참고).

- REC-01 규칙 2·REC-03 규칙 3: '업로드 30일 뒤 자동 삭제, 그 뒤 404' → 저장한 기록의 녹음은 계속 보관한다. 재생 가능 판단에 30일을 쓰지 않는다.
- REC-02 `lastSavedAt`: '최근 저장한 연주의 시작 시각' → 그 곡 기록 중 가장 최근 `saved_at`.
- REC-02 규칙 4(`isNew`): 기록 상세(PRA-03) 첫 조회 때 `viewed_at`을 기록하는 것으로 확정. 규칙 7(메모·저장 시각 출처): PRA-03 응답의 `memo`·`savedAt`.
- REC-03 규칙 5: `durationMs`는 업로드 때 저장한 메타데이터를 쓴다.
- REC-04 규칙 3: 분석 중인 기록은 생기지 않으므로 409를 쓰지 않는다. 규칙 5: 1차에 포함한다. `recordingDeleted`의 저장소는 MinIO가 아니라 R2다. 저장소 장애는 503이다.
- 메모 공백 정리, 지운 기록에 다시 보낸 요청의 404, 지정한 곡에 기록이 없을 때의 응답, 악보 버전이 다를 때 각 번호 null, PRA-03의 지운 기록 404를 새로 정했다.

## 구현 경계·검증

- api는 HTTP 매핑과 입력 검증, application은 소유·상태 판단과 트랜잭션·저장소 호출 순서, domain은 메모 정리와 목록 규칙, infrastructure는 MyBatis 조회·갱신을 맡는다. 녹음 저장소(PRA-02 `RecordingStorage`의 서명 URL·삭제)와 악보 각 읽기(EVA `EvaluationScoreCells`)는 `PracticeRecordConfig`에서 함수로 연결해 복제하지 않는다. 삭제는 저장소를 부르는 동안 DB 잠금을 잡지 않도록 트랜잭션 없이 마지막 조건부 갱신으로 동시 요청을 가른다. Flyway 변경은 없다(V2 칸 사용).

| 수준 | 확인하는 것 | 놓치는 것 |
| --- | --- | --- |
| 단위 | 메모 정리(공백·빈 값·500자·이모지), 목록 정렬·페이지 계산 | 실제 SQL·트랜잭션 |
| MockMvc(가짜 저장소) | 401·400·404·409·503 매핑, 본문 없음·`null`·문자열 메모, 응답 필드 | 실제 DB 조건부 갱신 |
| 실제 MySQL(`databaseTest`) | 저장 멱등(`savedAt` 유지), 저장·삭제 동시 요청의 조건부 갱신, 곡 묶음·정렬·페이지, PRA-03 첫 조회와 `isNew`, 지운 기록의 PRA-03·EVA-01·REC-01~04 404, 삭제 뒤 성장 요약 유지 | 실제 R2 동작 |
| R2 테스트 버킷(가능할 때) | 서명 URL로 녹음 받기(200, `audio/mp4`), 삭제 뒤 같은 키 404 | 운영 수명 주기(375), 앱 재생 |

명령은 `backend/`에서 `./gradlew test`와 `./gradlew databaseTest`다([backend README](../../backend/README.md)). 앱 화면과 재생은 369, 디자인 보드와의 일치는 이 검증 범위가 아니다.

## 성공 기준

- 저장한 세션이 목록에 곡별로 나타나고, 다시 저장해도 `savedAt`이 바뀌지 않는다.
- 메모가 위 규칙대로 저장되고 지워진다.
- 기록 상세를 처음 조회한 뒤 그 기록의 `isNew`가 false다.
- 서명 URL로 녹음을 받을 수 있다(R2 테스트 버킷, 확인하지 못하면 미검증으로 남긴다).
- 삭제 뒤 목록·상세·재생·평가 조회가 404이고, 성장 요약 값은 그대로다.
- 저장소 장애 때 삭제는 503이고 기록은 그대로 남는다.
- `./gradlew test`·`./gradlew databaseTest`가 통과한다.

## 열린 항목

| 항목 | 정할 사람·필요한 것 | 막는 작업 |
| --- | --- | --- |
| 노션 REC-01~04·PRA-03 행을 이 문서에 맞게 고치기 | 쓰기 주체. 노션 연결 계정이 은서님 것이다 | 없음. 이 문서가 기준이다 |
| 디자인 보드(S15P31E102-359)의 NEW 배지·삭제 화면 여부 | 369 담당의 보드 확인. 이 세션에서는 보드를 열지 못했다 | 369 화면. 서버는 막지 않는다 |
| 30일 지난 녹음의 재생 실패 | 375의 정책 전환과 `recording_url` 정리 | 30일 뒤 다시 듣기의 실제 확인 |
