# 연주 분석 실패 사유와 화면 안내 (PRA-03)

- Jira: [S15P31E102-167](https://ssafy.atlassian.net/browse/S15P31E102-167), Story S15P31E102-38, Epic S15P31E102-20
- 상태: 사용자 합의(2026-10-27, 결정 1~15). 같은 날 실패 코드를 [녹음 파일 규격(PRA-02)](recording-file.md)에 맞춰 고쳤다(아래 '변경 이력'). AI·BE·FE 담당자 검토 전이다. 구현은 [작업 계획](../plans/S15P31E102-167.md)의 Task별로 진행하며 진행 상태는 Jira가 기준이다.
- 기준 코드: 처음 작성은 `origin/develop` `d60d8e0`, 2026-10-28 [S15P31E102-203](https://ssafy.atlassian.net/browse/S15P31E102-203)에서 `c2c627e` 기준으로 고쳤다. 현재 동작은 [채점 파이프라인](../../ai/src/ai_service/pipelines/scoring.py)과 [DB 스키마](../contracts/db-schema.md)를 따른다.
- 관련 스펙: [녹음 파일 규격(PRA-02)](recording-file.md). 업로드 검사·만료·변환은 PRA-02가 정하고, 이 스펙은 실패 코드 전체 목록과 화면 안내·재시도를 정한다.

## 목적

사용자가 녹음을 보낸 뒤 결과를 받지 못하는 경우를 **분석 실패**로 0점과 구분한다. 실패하면 사유에 맞는 안내와 다음 행동(다시 연주 또는 분석 다시 시도) 하나를 보여 준다. 사용자 잘못이 아닌 실패는 사용자 탓으로 읽히지 않게 안내한다. AI·BE·FE는 같은 실패 코드와 상태 전환을 사용한다.

## 범위

포함:

- 실패 코드 6종의 목록, 판정 주체, 화면 안내
- 분석 상태 조회 간격과 시간 초과
- '분석 다시 시도'와 '다시 연주하기'의 동작
- 늦게 도착한 결과와 중복 요청 처리
- 업로드 실패 때 녹음을 기기에 보관하고 다시 보내는 앱 동작
- 처리 시간 측정

유지하는 동작:

- 세션 상태는 `RECORDING → PENDING → DONE | FAILED`이고 실패 사유는 `practice_sessions.fail_reason`에 코드로 저장한다.
- 음표 판정은 `MISSED → PITCH → TIMING → CORRECT` 순서를 그대로 쓴다. 일부 정간이 무음이면 그 음표는 `MISSED`로 판정하고 세션은 `DONE`이다.
- 조율 범위 초과(`tuning_out_of_range`)와 옥타브 의심(`octave_suspect`)은 실패가 아니다. `DONE`으로 두고 결과 화면에서 따로 안내한다.
- 곡 길이만큼 녹음하지 않고 중단한 연주는 서버로 보내지 않는다(기획 1-1).
- 업로드 검사(413·415·422 등), 세션 만료 기준, 16kHz 모노 변환은 PRA-02를 따른다. 업로드 거부는 요청 오류로 즉시 응답하고 세션 실패 코드를 쓰지 않는다.

제외:

- 기본음 녹음이 무음인 경우(기본음 녹음 기능에서 다룬다)
- 푸시 알림, 분석 취소, 재시도 횟수 제한
- 만료된 녹음을 새 세션으로 옮겨 보내는 기능
- 재채점 이력 정책([DB 스키마 미결 사항](../contracts/db-schema.md))

## 용어

| 용어 | 뜻 |
| --- | --- |
| 분석 실패 | 결과 점수를 만들 수 없는 경우. 세션 `FAILED`와 실패 코드로 기록하며 점수·음표 결과가 없다. 집계에 넣지 않는다. |
| 0점 | 분석은 끝났고(`DONE`) 판정 결과 점수가 0인 경우. 집계에 넣는다. |
| 분석 시도 | 같은 세션의 같은 녹음을 한 번 분석하는 단위. 재시도마다 번호가 1씩 늘어난다. |

## 실패 코드

| 코드 | 판정 주체 | 조건 | 다음 행동 |
| --- | --- | --- | --- |
| `NO_SOUND` | AI | 모든 음표가 `MISSED`. 즉 녹음 전체에서 연주 소리를 찾지 못함 | 다시 연주하기 |
| `TOO_SHORT` | AI | 녹음이 정답 길이의 절반보다 짧음(현재 판정 유지) | 다시 연주하기 |
| `DECODE_ERROR` | AI | 원본 변환·디코딩 실패(PRA-02). 중간이 손상됐거나 끝이 잘린 파일도 포함 | 다시 연주하기 |
| `UPLOAD_TIMEOUT` | BE 배치 | `RECORDING`인 채 `started_at` + 40분이 지남(PRA-02) | 화면에 보이지 않음 |
| `TIMEOUT` | BE | 분석 시도가 시작된 뒤 제한 시간(기본 60초) 안에 결과가 오지 않음 | 분석 다시 시도 |
| `SERVER_ERROR` | BE | AI 서비스 연결 실패, AI 처리 중 예외, 형식에 맞지 않는 AI 결과, 결과 저장 실패 | 분석 다시 시도 |

- `NO_SOUND` 판정은 지금의 "모든 프레임 무음"에서 "모든 음표 `MISSED`"로 넓힌다. 잡음 몇 프레임 때문에 실패 대신 0점이 되는 일을 막는다. 틀린 음을 분 연주는 `PITCH`로 남으므로 실패가 되지 않는다.
- `TOO_SHORT`는 파일은 끝까지 읽히는데 녹음 자체가 정답 길이의 절반보다 짧을 때 생긴다. 중단한 연주는 보내지 않으므로(1-1) 앱 오류처럼 드문 경우다. 업로드 중 끝이 잘리거나 중간이 손상된 파일은 디코딩 단계에서 `DECODE_ERROR`가 된다(S15P31E102-183). 손상 구간을 건너뛰고 채점하면 뒤의 소리가 앞당겨져 박자가 틀어지기 때문이다. 둘 다 사용자 탓으로 안내하지 않는다.
- `UPLOAD_TIMEOUT` 세션은 녹음도 점수도 없다. 분석 대기·결과 화면과 연습 기록 목록에 보이지 않는다. 앱은 업로드 실패를 이 코드가 아니라 아래 '녹음 다시 보내기'로 다룬다.
- `fail_reason`에는 코드만 저장한다. 상세 원인은 서버 로그에 남긴다.
- 앱은 모르는 코드나 빈 실패 코드를 `SERVER_ERROR`로 처리한다.

## 상태 조회와 시간 초과

1. 업로드가 끝나 분석 시도가 시작되면 세션은 `PENDING`이다. 서버는 시도 시작 시각과 시도 번호를 `practice_sessions.analysis_started_at`·`analysis_attempt`에 기록한다([DB 스키마](../contracts/db-schema.md) V4, S15P31E102-173).
2. 앱은 대기 화면에서 2초마다 상태를 조회하고, `DONE` 또는 `FAILED`를 받으면 조회를 멈춘다. 화면은 서버가 준 상태를 그대로 따른다.
3. 서버는 시도 시작 뒤 제한 시간이 지나면 세션을 `FAILED`/`TIMEOUT`으로 바꾼다. 제한 시간은 설정값이며 기본 60초다.
4. 서버가 재시작돼도 `PENDING`은 끝나야 한다. 상태 조회 때 `PENDING`이 제한 시간을 넘었으면 그 자리에서 `TIMEOUT`으로 바꾼 뒤 응답한다.
5. 현재 시도 번호와 다른 결과나 `TIMEOUT` 뒤에 도착한 결과는 버린다. 세션 상태와 평가 결과를 바꾸지 않는다.
6. 앱이 70초 동안 상태 응답을 하나도 받지 못하면(네트워크 단절) 앱만 `TIMEOUT` 화면을 보여 준다. 서버 상태는 바꾸지 않는다.
7. 앱이 백그라운드로 가거나 대기 화면을 벗어나도 분석은 서버에서 계속한다. 돌아오면 상태를 한 번 조회하고, `PENDING`이면 2초 조회를 다시 시작한다.

## 분석 요청과 상태 조회 계약 (S15P31E102-186)

2026-10-28 사용자가 추천안으로 확정했다. 호출 방식(업로드 커밋 뒤 백그라운드 HTTP, 큐 없음)은 [PRA-02](recording-file.md)를 따른다.

**백엔드 → AI 분석 요청.** AI가 같은 HTTP 응답으로 결과를 돌려준다. 백엔드는 백그라운드에서 응답을 기다린다.

```text
POST {AI}/v1/analyses
{ "sessionId": 1, "attempt": 1, "audioUrl": "<15분 읽기 전용 서명 URL>", "bpm": 60, "bpmUnit": "JEONGGAN",
  "notes": [{ "idx": 0, "semitone": 5, "startBeat": 0.0, "beats": 1.0, "sigimsae": false }] }

200 { "status": "DONE", "modelVersion": "pyin-1", "total": 87, "pitch": 90, "timing": 85, "duration": 86,
      "tuningOffsetCent": -12.5, "tuningOutOfRange": false,
      "notes": [{ "idx": 0, "verdict": "CORRECT", "pitchErrorCent": 3.1, "onsetErrorMs": 20, "durationErrorMs": -15 }] }
200 { "status": "FAILED", "failReason": "NO_SOUND" | "TOO_SHORT" | "DECODE_ERROR" }
```

- `notes`는 백엔드가 세션의 곡·연습 구간을 음표로 풀어 보낸다. AI는 DB를 읽지 않는다. `idx`는 `songs.score_json` 기준 순번이고 결과의 `idx`도 같다.
- **음표 번호.** `songs.score_json`(공통 악보 데이터)의 `events` 중 `kind = NOTE`만 연주 순서대로 0부터 센 번호다. 쉼표(`REST`)는 세지 않는다. 쉼표에는 정답 음이 없어 `note_results.expected_pitch_id`(NOT NULL)를 채울 수 없기 때문이다. 이음표는 변환기가 앞 음의 `durationTick`에 합쳐 따로 세지 않는다. 따라서 `songs.note_count`는 `NOTE` 이벤트 수이고, 연습 구간 `start_note_idx`~`end_note_idx`(양 끝 포함, 전체 곡이면 0 ~ `note_count` − 1)도 이 번호다. 백엔드는 구간의 `NOTE`만 `notes`로 보낸다. 데이터(곡 적재의 `note_count`)·AI 채점(S15P31E102-157)은 이 번호로 맞추기로 했고, FE 연습 구간 선택은 확인 대기다.
- **음표 값.** 악보 `music.ticksPerBeat`로 나눠 박으로 바꾼다. `startBeat` = `startTick` ÷ `ticksPerBeat`(곡 처음부터 센 시작 박), `beats` = `durationTick` ÷ `ticksPerBeat`, `sigimsae`는 이벤트의 `sigimsae` 목록이 비어 있지 않으면 true다. 쉼을 빼고 보내므로 `beats`만으로는 쉼 뒤 음의 시각이 앞당겨진다. AI는 `startBeat`로 기대 시각을 정하고, 연습 구간이면 첫 음을 0초로 본다. `semitone`은 `pitches.semitone`(옥타브 × 12 + 율 순서, 황 0 … 응 11. 0은 옥타브 0의 황 = E♭4 311.13Hz)이고 `expected_pitch_id`는 같은 `pitches` 행의 `id`다. 이벤트의 `yul`·`octave`로 찾는다(`UNIQUE (yul, octave)`).
- 악보를 음표로 풀 수 없으면(곡이 없거나 세션의 `score_version`과 다름, 구간이 `NOTE` 수를 넘음, `pitches`에 없는 율명·옥타브) AI를 부르지 않고 `SERVER_ERROR`다.
- `attempt`는 늦게 도착한 결과를 버리는 데 쓴다. 백엔드는 현재 시도 번호와 같은 결과만 저장한다.
- `audioUrl`은 녹음 접근 토큰이라 백엔드·AI 로그에 남기지 않는다.
- `modelVersion`(30자 이하)은 `evaluations.model_version`에 저장한다. 점수는 0~100이고, 결과의 음표 `idx`는 요청한 음표와 같아야 한다. 하나라도 어긋나면 형식 오류다.
- 결과를 저장할 때 백엔드는 음표마다 `expected_pitch_id`를 악보에서 채운다. `detected_pitch_id`는 AI가 주지 않으므로 비운다. `pitchErrorCent`는 정수로 반올림해 저장하고 `tuningOffsetCent`·`tuningOutOfRange`는 저장할 칸이 없어 아직 쓰지 않는다.
- 응답 형식이 맞지 않거나 연결이 실패하면 `SERVER_ERROR`, 제한 시간을 넘으면 `TIMEOUT`이다(S15P31E102-174). AI 오류 응답의 본문은 요청을 되돌려 줄 수 있어 예외·로그에 남기지 않고 상태 코드만 남긴다.
- 결과·실패 코드 저장은 같은 시도이고 마감 전(`analysis_started_at` ≥ 지금 − 제한 시간)일 때만 한다. 마감을 넘겨 거절되면 그 시도를 `TIMEOUT`으로 바꾼다. 분석을 시작할 때 이미 마감을 넘겼으면 AI를 부르지 않고 `TIMEOUT`이다. 마감과 같은 시각은 아직 제한 시간 안이다.
- 악보 음표를 만들지 못하거나 결과 저장이 실패해도(예: 칸 범위를 넘는 값) `SERVER_ERROR`다. 시도마다 결과 코드와 처리 시간(ms)을 로그에 남기고, 예외 메시지는 남기지 않는다. `TIMEOUT`으로 바꿀 때는 분석 중이든 상태 조회 때든 따로 한 줄 남겨 시간 초과 횟수를 센다.
- 업로드(S15P31E102-181)는 `PENDING`으로 바꾸는 같은 갱신에서 `analysis_attempt`를 1 올리고 `analysis_started_at`을 채운다. 시작 시각은 DB `NOW()`가 아니라 마감 판정과 같은 서버의 UTC 시계로 넣는다(분석 다시 시도도 같다). 시도 번호가 0인 `PENDING`은 분석 대기로 보지 않는다.

**앱의 상태 조회.** 노션 API 명세의 `GET /api/practice-sessions/{sessionId}`(PRA-03 연습 세션·분석 상태 조회)를 쓴다. 처음 적은 `GET /api/practice-sessions/{id}/analysis`·`{status, failReason, attempt}`는 명세와 맞춰 2026-10-28 대체했다(`attempt`는 응답에 없다).

- `result`: `sessionId`·`songId`(문자열), `bpm`·`bpmUnit`, `startNoteIdx`·`endNoteIdx`, `status`, `evaluationId`(문자열, 없으면 null), `failReason`, `recordingAvailable`(녹음 원본이 있고 기록을 지우지 않음), `startedAt`·`endedAt`(UTC ISO8601), `retryable`(`FAILED`이고 `TIMEOUT`·`SERVER_ERROR`), `savedAt`, `memo`.
- 없거나 다른 회원의 세션은 404 `RESOURCE_NOT_FOUND`(공통 오류 형식). 다른 회원의 세션은 상태도 바꾸지 않는다.
- 점수·음표별 결과는 `DONE`일 때 결과 조회 API(`GET /api/practice-sessions/{sessionId}/evaluation`)로 받는다.

**구현 상태(2026-10-28).** S15P31E102-186은 분석 실행 흐름, AI HTTP 클라이언트, 결과 저장까지, S15P31E102-174는 시간 초과·`SERVER_ERROR`·늦은 결과 무시와 저장소의 시간 초과 전환(`expireOverdue`)까지 구현했다. 상태 조회 API는 인증(S15P31E102-143)이 들어온 뒤 S15P31E102-186에서 붙였고, 조회한 시도가 마감을 넘겼을 때만 `GetPracticeSession`이 `expireOverdue`를 부른다. 업로드 뒤 분석 시작은 S15P31E102-186이 연결했고, 업로드(S15P31E102-181)가 이벤트 발행과 녹음 서명 URL을 구현했다.

- 업로드 저장소(`MyBatisRecordingUploadRepository`)는 `PENDING`으로 바꾸는 트랜잭션 안에서 `RecordingUploaded(sessionId)`를 발행한다. 트랜잭션 밖에서 발행하면 오류 없이 버려지므로 발행 위치를 옮기지 않는다. 커밋 뒤 `AnalysisOnUpload`가 백그라운드 실행기(기본 2개 동시·대기 50개)에서 분석한다. 롤백된 업로드는 분석하지 않는다. 대기열이 가득 차면 시작하지 않고 그 세션은 제한 시간 뒤 `TIMEOUT`이 된다.
- AI에 넘기는 녹음 주소는 업로드 설정의 `RecordingReadUrls` Bean(15분 읽기 전용 서명 URL) 하나를 쓴다. 같은 타입 Bean을 따로 만들지 않는다.
- 음표는 `MyBatisPracticeScoreNotes`가 세션의 곡 악보와 `pitches`로 만든다(위 '음표 번호'·'음표 값'). `pitches`는 V5 마이그레이션이 `data/reference/pitches.json`의 단소 율명 11개를 넣는다.
- 설정 `eolssu.analysis.time-limit`(기본 60초)은 분석과 상태 조회가 함께 쓴다. AI HTTP 응답 대기 `ai-response-timeout`(기본은 제한 시간 + 5초)은 제한 시간보다 길어야 기동한다. 두 시간은 숫자만 쓰면 초로 읽는다. 짧으면 느린 응답이 마감 전에 끊겨 `TIMEOUT` 대신 `SERVER_ERROR`가 된다.

S15P31E102-186의 코드는 여기까지다. 배포 환경에 AI 서비스를 띄우고 `ANALYSIS_AI_BASE_URL`을 넣는 일, V5 적용 전 운영·공유 DB의 `pitches` 기존 행 확인은 S15P31E102-228이 맡는다. 주소가 없으면 업로드마다 경고 로그만 남고 세션은 제한 시간 뒤 `TIMEOUT`이 된다.

- AI 쪽 `POST /v1/analyses`는 S15P31E102-212에서 구현했다([실행 방법](../../ai/README.md#분석-api)). 기준음은 S15P31E102-157 전까지 E♭4 고정이고, 채점이 `startBeat`로 기대 시각을 정하는 것도 157이 맡는다.

S15P31E102-175는 분석 다시 시도의 판단(`RetryPracticeAnalysis`)과 새 시도를 시작하는 조건부 갱신까지 구현했다. 마감을 넘긴 `PENDING`은 먼저 `TIMEOUT`으로 끝낸 뒤 다시 시도하고, 업로드 전(`RECORDING`) 세션도 거절한다. 새 시도는 읽은 시도 번호를 조건으로 시작하고, 분석을 백그라운드 작업으로 넘기지 못하면 그 시도를 `SERVER_ERROR`로 끝낸다. 다른 시도가 계속 끼어들어 몇 번 다시 읽어도 시작하지 못하면 거절이 아니라 '잠시 후 다시'(`Busy`)로 답한다. API에서는 이 결과도 거절 응답과 구분해야 한다. 유스케이스는 트랜잭션 밖에서 불러야 한다(새 시도가 커밋된 뒤 분석이 세션을 읽는다). 다시 시도 API(경로·응답 코드·거절 오류 형식)와 유스케이스의 Bean 연결은 아래 미결 사항의 API 명세가 정해진 뒤 붙인다. 이때 새 시도를 위의 분석 실행기로 넘긴다.

## 다음 행동

**녹음 다시 보내기** (업로드 실패, 실패 코드 아님)

- 업로드가 성공할 때까지 앱은 녹음 파일을 기기에 보관한다.
- 네트워크 오류나 503이면 '녹음 다시 보내기'로 같은 세션에 다시 올린다. 앱을 다시 열어도 만료 전이면 보낼 수 있다. 성공하면 파일을 지운다.
- 410(만료)을 받으면 파일을 지우고 사용자 탓이 아닌 문구로 다시 연주를 안내한다. 413·415·422는 같은 파일로 다시 보내도 거부되므로 다시 보내기를 보이지 않는다.

**분석 다시 시도** (`TIMEOUT`·`SERVER_ERROR`)

- 같은 세션과 같은 녹음으로 새 분석 시도를 시작한다. 세션은 `PENDING`이 되고, 시도 번호가 늘고, 제한 시간을 처음부터 다시 센다.
- 세션이 `FAILED`이고 코드가 `TIMEOUT`·`SERVER_ERROR`일 때만 새 시도를 만든다.
- 세션이 아직 `PENDING`이면 새 시도를 만들지 않고 현재 상태를 돌려준다. 앱은 조회를 이어 간다. 버튼을 여러 번 눌러도 분석은 하나만 돈다.
- `NO_SOUND`·`TOO_SHORT`·`DECODE_ERROR`·`UPLOAD_TIMEOUT`·`DONE` 세션의 재시도 요청은 거절한다. 오류 형식은 API 명세를 따른다.
- 실패한 세션에는 평가 행이 없으므로 재시도 성공은 첫 평가 저장이다. 재채점 정책과 충돌하지 않는다.
- 횟수 제한은 두지 않는다.

**다시 연주하기** (`NO_SOUND`·`TOO_SHORT`·`DECODE_ERROR`)

- 같은 곡·구간·BPM으로 연습 화면에 돌아가 새로 녹음하고, 새 세션을 만든다. 실패한 세션은 기록으로 남긴다.
- 실패한 세션은 `parent_session_id`의 기준 세션(본인·`DONE`·같은 곡)이 될 수 없다. 새 세션의 부모 세션은 PRA-01 규칙을 따른다.
- 새 세션은 새 Idempotency-Key로 만든다.

## 화면 안내 문구 (초안)

기획·디자인에서 다듬을 수 있다. 코드와 버튼의 대응은 위 표가 기준이다. 사용자 잘못이 아닌 실패(`TOO_SHORT`·`DECODE_ERROR`·`TIMEOUT`·`SERVER_ERROR`, 업로드 만료)는 연주를 탓하는 문구를 쓰지 않는다.

| 상황 | 안내 문구 | 버튼 |
| --- | --- | --- |
| `NO_SOUND` | 소리가 들리지 않았어요. 마이크 가까이에서 다시 불어 주세요. | 다시 연주하기 |
| `TOO_SHORT` | 녹음이 끝까지 저장되지 않았어요. 다시 연주해 주세요. | 다시 연주하기 |
| `DECODE_ERROR` | 녹음 파일을 읽지 못했어요. 다시 연주해 주세요. | 다시 연주하기 |
| `TIMEOUT` | 분석이 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요. | 분석 다시 시도 |
| `SERVER_ERROR` | 분석 중 문제가 생겼어요. 다시 시도해 주세요. | 분석 다시 시도 |
| 업로드 실패(네트워크·503) | 녹음을 보내지 못했어요. 녹음은 기기에 남아 있어요. | 녹음 다시 보내기 |
| 업로드 410(만료) | 시간이 지나 이 녹음을 보낼 수 없게 됐어요. 불편을 드려 죄송해요. | 다시 연주하기 |

## 처리 시간 측정

60초는 임시 값이다. 다음 두 측정으로 고친다.

- 출시 전: 합성 입력으로 녹음 길이별(15~600초), pYIN·CREPE별 처리 시간과 메모리를 쟀다([측정 기록](../knowledge/ai/analysis-latency.md), S15P31E102-172). 개발 노트북 측정이며 서버에서는 아직 재지 않았다.
- 운영 중: 분석 시도마다 처리 시간(ms)과 결과 코드를 서버 로그에 남긴다.

측정 결론:

- pYIN과 torchcrepe tiny는 120초 녹음까지 60초 제한 안에 든다. 서버에서 다시 재기 전에는 60초를 유지한다. torchcrepe full은 CPU 서버 후보에서 뺀다.
- 지금 채점의 DTW는 정답 프레임 수 × 녹음 프레임 수만큼 메모리를 써서 600초 녹음은 분석할 수 없다(실측 칸당 약 20바이트, 300초 약 17GiB, 600초 약 67GiB). PRA-02는 600초까지 받으므로 긴 녹음은 지금 상태로는 분석 중 메모리 부족(`SERVER_ERROR` 또는 `TIMEOUT`)이 된다.
- 녹음 길이 상한을 서비스 곡의 최대 연습 구간 길이로 낮출지, DTW에 폭 제한을 넣을지는 아래 미결 사항이다. 이 스펙은 결정하지 않는다.

## 완료 확인

| 확인 항목 | 검증 위치 |
| --- | --- |
| 전부 무음, 잡음 몇 프레임만 있는 녹음 → `FAILED`/`NO_SOUND` | AI 단위 테스트(작은 고정 배열) |
| 일부 정간만 무음 → `DONE`, 그 음표만 `MISSED` | AI 단위 테스트 |
| 정답 길이의 절반보다 짧은 녹음 → `FAILED`/`TOO_SHORT` | AI 단위 테스트(기존) |
| 틀린 음만 분 녹음 → `DONE`, `PITCH`. 실패 아님 | AI 단위 테스트 |
| 손상 원본(헤더·중간 손상, 끝 잘림) → `FAILED`/`DECODE_ERROR` | AI 테스트(PRA-02 S15P31E102-183) |
| 조율 범위 초과 → `DONE` 유지 | AI 단위 테스트(기존) |
| 제한 시간 초과 → `TIMEOUT`, 그 뒤 결과는 버림 | BE 단위 테스트(조정 가능한 시계, AI 대역) |
| 서버 재시작 뒤 오래된 `PENDING` → 조회 시 `TIMEOUT` | BE 테스트(시계 고정) |
| AI 연결 실패·예외·형식 오류 → `SERVER_ERROR` | BE 단위 테스트(AI 대역) |
| 재시도: `TIMEOUT`·`SERVER_ERROR`만 새 시도, `PENDING`은 현재 상태 반환, `NO_SOUND`·`TOO_SHORT`·`DECODE_ERROR`·`UPLOAD_TIMEOUT`·`DONE`은 거절 | BE 단위 테스트 |
| 이전 시도 번호의 결과는 버림 | BE 단위 테스트 |
| 상태·시도 정보 저장과 조회 | BE DB 통합 테스트 |
| 코드 5종(`UPLOAD_TIMEOUT` 제외)의 안내·버튼 대응, 모르는 코드 → `SERVER_ERROR` | FE 단위 테스트 |
| 2초 조회, `DONE`·`FAILED`에서 멈춤, 응답 없이 70초 → `TIMEOUT` 화면 | FE 단위 테스트(가짜 시계, 고정 sleep 없음) |
| 백그라운드 복귀 시 한 번 조회 후 `PENDING`이면 조회 재개 | FE 단위 테스트 |
| 업로드 실패 → 녹음 다시 보내기 성공, 앱 재시작 후 다시 보내기, 410이면 파일 삭제와 안내 | FE 단위 테스트(업로드 대역)와 에뮬레이터·시뮬레이터 실행 |
| `UPLOAD_TIMEOUT` 세션이 연습 기록 목록에 보이지 않음 | 연습 기록 목록 API 테스트 |
| 실제 녹음 업로드부터 결과·실패 화면까지 | 실행 검증. AI 대역 테스트를 실제 모델 검증으로 보지 않는다 |

## 영향받는 기존 문서·코드

- `ai/src/ai_service/pipelines/scoring.py`, `ai/tests/unit/test_scoring.py`: `NO_SOUND` 기준 변경. `TOO_SHORT`는 그대로 둔다.
- V1 마이그레이션 주석의 실패 코드 예시는 Flyway checksum 때문에 고치지 않는다. 코드 6종 목록은 [DB 스키마 문서](../contracts/db-schema.md)에 적는다.
- [S15P31E102-147](https://ssafy.atlassian.net/browse/S15P31E102-147) 분석 대기·결과 화면: 예시 데이터를 이 코드와 문구에 맞춘다.

## 미결 사항

| 항목 | 정할 사람·필요한 것 | 막는 작업 |
| --- | --- | --- |
| 기록을 지운 세션(REC-04)을 상태 조회에서 404로 할지 | PRA API 명세 담당. 지금은 200으로 상세·메모를 주고 `recordingAvailable`만 false다(V2 주석은 목록·재생에서 뺀다고만 적음) | 상태 조회 API의 지운 기록 처리 |
| 재시도 API의 거절 응답 형식 | PRA API 명세 담당. 경로는 명세의 `POST /api/practice-sessions/{sessionId}/retry` | 다시 시도 API 구현 |
| FE 연습 구간 선택이 음표 번호(`NOTE`만 0부터)로 `startNoteIdx`·`endNoteIdx`를 보내는지 | FE 담당 확인 | 구간 연습의 올바른 채점(전체 곡 연주는 막지 않음) |
| 녹음 길이 상한(PRA-02 600초)을 낮출지, DTW 폭 제한을 넣을지 | 팀(PRA-02·PRA-03). 서비스 곡의 최대 연습 구간 길이 확인 필요. [측정 기록](../knowledge/ai/analysis-latency.md)은 상한을 연습 구간 길이로 낮추고 120초를 넘으면 DTW 폭 제한을 먼저 넣자고 제안한다 | 120초를 넘는 녹음의 분석. 정하기 전 긴 녹음은 메모리 부족으로 실패한다 |
| 제한 시간 60초와 앱 대기 70초의 최종 값 | 운영 서버에서 같은 명령으로 다시 잰 결과 | 값 조정(구현은 막지 않음) |
| 연습 기록 목록이 저장한 세션만 보이는지 | REC-02 목록 명세. 저장한 세션만 보이면 `UPLOAD_TIMEOUT`은 따로 처리할 필요 없다 | 목록 API 구현 |
| 안내 문구 최종안 | 기획·디자인 | FE 문구 확정(구현은 초안으로 진행 가능) |

## 변경 이력

| 날짜 | 원래 결정 | 바뀐 결정 | 이유·영향 |
| --- | --- | --- | --- |
| 2026-10-27 | 실패 코드 4종(`NO_SOUND`·`DECODE_FAILED`·`TIMEOUT`·`SERVER_ERROR`). `TOO_SHORT`는 없애고 짧은 녹음은 채점 후 뒤쪽 `MISSED` | 실패 코드 6종. `TOO_SHORT` 유지, `DECODE_FAILED` → `DECODE_ERROR`, `UPLOAD_TIMEOUT` 추가 | 사용자가 PRA-02 기준으로 맞추기로 결정. AI 판정 Task는 `NO_SOUND` 기준만 바꾸고, 디코딩 실패는 PRA-02 S15P31E102-183이 맡는다 |
| 2026-10-27 | 업로드 실패는 범위 밖 | 앱이 녹음을 보관하고 '녹음 다시 보내기'를 제공하며, `UPLOAD_TIMEOUT`은 화면에 보이지 않음 | 사용자 잘못이 아닌 실패에 다시 연주를 요구하지 않기 위해. FE Task S15P31E102-187 추가 |
| 2026-10-27 | `TOO_SHORT` 문구 "녹음이 너무 짧아요" 계열 | "녹음이 끝까지 저장되지 않았어요" | 중단한 연주는 보내지 않으므로 짧은 녹음은 대개 사용자 잘못이 아니다 |
| 2026-10-28 | 업로드 잘림으로 짧아진 녹음은 `TOO_SHORT` | 끝이 잘리거나 중간이 손상된 파일은 `DECODE_ERROR`. `TOO_SHORT`는 끝까지 읽히는 짧은 녹음만 | 사용자 결정(S15P31E102-183 리뷰). ffmpeg는 손상 구간을 건너뛰고 정상 종료해 뒤의 소리가 앞당겨지므로 디코딩 오류가 하나라도 나면 실패로 본다. 두 경우 모두 '다시 연주하기'라 사용자 행동은 같다 |
| 2026-10-28 | 분석 요청 음표는 `idx`·`semitone`·`beats`·`sigimsae` | 음표마다 `startBeat` 추가 | 쉼을 빼고 `beats`만 보내면 쉼 뒤 음이 앞당겨진다(아리랑 61음 중 45음, 최대 약 1초). 데이터 담당 지적. 백엔드(S15P31E102-186)·AI 엔드포인트(S15P31E102-212)·채점(S15P31E102-157)이 함께 바꾼다 |
