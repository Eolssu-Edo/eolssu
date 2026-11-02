# 실제 연습을 위한 곡·평가 조회 API

- 사용자 승인(2026-10-29): 245 앱 업로드 연결의 선행 API를 별도 Task·브랜치로 구현한다.
- 기존 DB와 채점 의미·인증 정책을 보존하는 추가 조회다. 새 채점·총평·저장 정책은 만들지 않는다.

## 곡·악보 (S15P31E102-256)

- 기존 공개 GET 정책 아래 `GET /api/songs`는 `result.items`, `GET /api/songs/{songId}`는 `result`에 곡을 제공한다.
- `songId`, `instrumentId`, `title`, `description`, `difficulty`(DB 1~3), `defaultBpm`, `isScorable`, `scoreVersion`, `noteCount`, `jangdan{jangdanId,name,bpmUnit,pattern}`, `score`(기존 score_json의 JSON 객체).
- ID는 문자열이다. 게시 상태 PUBLISHED·deleted_at NULL만 반환한다. 목록은 ID 오름차순이고 없으면 빈 목록이다. 다른 상태·삭제·없는 상세는 같은 404 RESOURCE_NOT_FOUND다.
- 목록에는 검색·필터·페이지 조건이 없다. 앱이 받아서 거른다. 서버 조건은 S15P31E102-345에서 보류했고 재개 조건은 [345 계획](../plans/S15P31E102-345.md)에 있다.
- 기존 악보의 events.id·NOTE만 센 idx·cellId·틱·율명·옥타브를 변경하지 않는다. 앱은 이 데이터로 표시·연주·오류 위치를 함께 연결한다. 이름에 의한 예시 곡 대응은 금지한다.
- 앱(2026-10-29, S15P31E102-285): `HttpSongRepository`가 목록·상세를 받아 정간 글자(한글 율명·`-`·`쉼`·빈 정간)를 연주 순서대로 앱 악보로 옮긴다. 율명 글자 순서가 NOTE 이벤트 순서라 평가 `noteIdx`는 율명 음만 센 번호로 칸을 찾는다. 한 박의 정간 수는 `ticksPerBeat / ticksPerJeonggan`이고, 각(원본 악보 줄)이 장단 주기와 다르면 장단 한 주기의 정간 수로 대강을 나눈다. 한 줄을 다시 나눈 정간(`col` > 0)이나 모르는 율명이 있는 곡은 목록에서 뺀다. 계이름은 곡·악기 기준 데이터가 없어 표시하지 않는다. 연습 기록·내 악보가 아직 예시 데이터라 숫자가 아닌 곡 ID는 예시 곡으로 찾는다.

## 평가 (S15P31E102-257)

- 인증된 `GET /api/practice-sessions/{sessionId}/evaluation`. 소유자이며 미삭제 DONE인 세션만 조회한다. 없는/타인/삭제 세션은 404, 소유자 미완료는 409 STATE_CONFLICT다.
- `result`는 `sessionId`, `evaluationId`, `songId`, `scoreVersion`, `bpm`, `bpmUnit`, `startedAt`, `endedAt`, `evaluatedAt`, `totalScore`, `pitchScore`, `timingScore`, `durationScore`, `notes`다.
- `notes`는 저장된 noteIdx 오름차순의 `noteIdx`, `verdict`, `pitchErrorCent`, `onsetErrorMs`, `durationErrorMs`다. 점수와 판정을 재계산하지 않는다. 앱은 noteIdx를 같은 악보의 NOTE 이벤트에 연결한다. 임의 총평은 생성하지 않는다.
- 오류와 성공은 기존 공통 응답 형식이다. 타인 세션의 점수·존재·녹음 주소를 노출하지 않는다.

## 완료 확인

공개 곡 필터, 없는 곡, 소유자 평가, 미완료, 타인·삭제 세션, 음표 순서를 HTTP·MySQL로 확인한다. 앱 HTTP 대역 검사는 245에서 수행한다.
