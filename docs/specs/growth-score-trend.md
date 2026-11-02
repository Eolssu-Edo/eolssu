# 곡별 최근 점수 변화 조회 API (GRO-03)

- Jira: [S15P31E102-346](https://ssafy.atlassian.net/browse/S15P31E102-346), Story S15P31E102-42. 결정·진행: [346 계획](../plans/S15P31E102-346.md)
- 상태: 사용자 승인 계약(2026-11-02, clarify 결정과 스펙 추가 항목 포함). 구현 중 보정 2건(같은 시각일 때 기본 곡 기준, 앞자리 0·`+` 거부)은 사용자 확인 대기다. 구현·자동 검사·독립 리뷰 반영을 마쳤고 결과는 [346 계획](../plans/S15P31E102-346.md)의 '검증'·'리뷰 처리'에 있다. Notion API 명세 GRO-03 초안과 다른 점은 아래 'Notion 초안과 다른 점'에 모았다.
- 소비자: 연습 기록지(GROWTH-01) '점수 변화' 그래프. 앱 연결은 [S15P31E102-372](https://ssafy.atlassian.net/browse/S15P31E102-372)에서 한다.

## 목적

연습 기록지에서 사용자가 한 곡을 얼마나 나아지게 연주하고 있는지 최근 점수로 보여 준다. 연습 조건(BPM·악보 버전·채점 모델)마다 처음부터 나누면 연습이 모두 따로 기록돼 그래프가 쓸모없어지므로, 곡으로 먼저 묶고 조건은 점마다 실어 '자세히 보기'에서 보여 줄 수 있게 한다. 서로 다른 곡이나 전체 연주와 구간 연습을 한 그래프에 섞지 않는다.

## 범위

포함:

- 한 곡의 최근 전체 곡 연주 점수(기본)와, 따로 보는 구간 연습 점수
- 곡을 지정하지 않을 때 보여 줄 곡 고르기와 연주한 곡 목록
- 점별 연주 조건과 조건이 모두 같은지(`comparable`)

유지하는 동작:

- GRO-01·02·04 계약과 GRO-01 `bestBySong`·`metricGrowth`. 항목별 성장 카드는 계속 GRO-01 `metricGrowth`를 쓴다.
- '전체 곡 연주'와 '세는 연주'의 정의([연습 시간 집계 스펙](practice-time.md) '성취도'와 같다)
- 데이터 저장 구조. 이 API는 읽기만 하고 마이그레이션이 없다.

제외:

| 항목 | 이유 |
| --- | --- |
| 항목별 첫 기록 → 최근(`categoryGrowth`) | GRO-01 `metricGrowth`가 이미 같은 카드를 채운다. 두 기준이 같은 화면에 생기지 않게 한다 |
| 여러 곡을 한 그래프에 섞는 보기 | 곡이 다르면 쉬운 곡에서 어려운 곡으로 넘어갈 때 하락처럼 보인다(2026-10-28 성취도 결정과 같은 이유) |
| 정확한 범위만 고르는 구간 조회(`startNoteIdx`·`endNoteIdx`) | 쓰는 화면이 없다. 필요해지면 추가한다 |
| 그래프 점에서 기록 상세로 이동 | 지운 기록도 점으로 남는다. 1차 화면은 이동하지 않는다 |
| 앱 화면·문구, Notion 페이지 수정 | 화면은 372·359, Notion 반영은 사용자 확인 뒤 따로 한다 |

## 용어

| 용어 | 뜻 |
| --- | --- |
| 세는 연주 | 회원의 연습 세션 중 분석이 끝나고(`status = DONE`) 평가 행이 있는 것. 연습 기록 저장 여부(`saved_at`)와 기록 삭제(`record_deleted_at`)는 따지지 않는다 |
| 전체 곡 연주 | 세는 연주 중 `start_note_idx = 0`이고 `end_note_idx = songs.note_count - 1`인 것. 지금의 `note_count`로 판정한다 |
| 구간 연습 | 세는 연주 중 전체 곡 연주가 아닌 것 |

## 요청

`GET /api/users/me/growth/scores`. 로그인(`access_token` 쿠키)이 필요하고 본문은 없다. 회원은 토큰에서 정한다.

| Query | 필수 | 뜻 |
| --- | --- | --- |
| `songId` | 아니요 | 보여 줄 곡. 양의 정수 문자열. 생략하면 서버가 고른다(아래 '곡 고르기') |
| `scope` | 아니요 | `FULL`(전체 곡 연주, 기본) 또는 `SECTION`(구간 연습). 대문자만 받는다 |
| `limit` | 아니요 | 점 개수 1~100, 기본 5 |

## 응답

공통 형식 `{httpStatus, isSuccess, message, code, result}`. 성공은 200, `message`는 `최근 점수 변화 조회 성공`.

| `result` 칸 | 값 |
| --- | --- |
| `songId` | 보여 준 곡 ID(문자열). 요청한 `songId`를 그대로 돌려주고, 생략했으면 고른 곡. 세는 연주가 하나도 없어 고를 곡이 없으면 `null` |
| `scope` | 적용한 `FULL`·`SECTION` |
| `items[]` | 고른 곡·범위의 세는 연주 중 최근 `limit`개를 오래된 순서로. 없으면 `[]` |
| `items[].sessionId` | 연습 세션 ID(문자열) |
| `items[].startedAt` | 연주 시작 시각, UTC ISO-8601 |
| `items[].bpm`, `items[].bpmUnit` | 세션 BPM과 단위(`QUARTER`·`DOTTED_QUARTER`·`JEONGGAN`·`JANGDAN_BEAT`) |
| `items[].startNoteIdx`, `items[].endNoteIdx` | 연주 범위 음표 번호(0부터, 양 끝 포함) |
| `items[].startColumnIdx`, `items[].endColumnIdx` | 연주 범위의 각 번호(0부터, 화면은 +1). 세션 악보 버전이 지금 곡 악보 버전과 같을 때만 값이 있고, 다르면 `null` |
| `items[].scoreVersion` | 세션 악보 버전 |
| `items[].modelVersion` | 채점 모델 버전 |
| `items[].totalScore`, `pitchScore`, `timingScore`, `durationScore` | 저장된 평가 점수(0~100) |
| `comparable` | `items`가 2개 이상이고 모두 `scoreVersion`·`modelVersion`·`bpm`·`bpmUnit`이 같으면 `true`. `SECTION`은 `startNoteIdx`·`endNoteIdx`까지 같아야 `true`. 그 밖에는 `false` |
| `songs[]` | 세는 연주가 하나라도 있는 곡. 그 곡의 가장 최근 세는 연주 시각이 늦은 곡 먼저, 같으면 곡 ID가 큰 곡 먼저. 요청 `songId`·`scope`와 관계없이 같다 |
| `songs[].songId`, `songs[].title` | 곡 ID(문자열)와 제목 |
| `songs[].lastPlayedAt` | 그 곡의 가장 최근 세는 연주 시작 시각, UTC ISO-8601 |
| `songs[].fullPlayCount`, `songs[].sectionPlayCount` | 그 곡의 전체 곡 연주 수와 구간 연습 수. 앱이 빈 그래프·구간 토글을 미리 정하는 데 쓴다 |

### 고르는 규칙

1. **곡 고르기:** `songId`가 없으면 `songs[]`의 첫 곡, 곧 세는 연주(전체·구간 무관)를 가장 최근에 한 곡이다. 두 곡의 가장 최근 연주 시각이 같으면 곡 ID가 큰 곡이다. 그 곡을 구간으로만 연습했다면 `FULL`의 `items`는 비고, 앱은 구간 보기로 안내한다.
2. **점 고르기:** 그 곡의 세는 연주 중 `scope`에 맞는 것을 `started_at` 내림차순(같으면 세션 ID 내림차순)으로 `limit`개 골라 순서를 뒤집는다. BPM·악보 버전·채점 모델이 달라도 모두 넣는다.
3. **각 번호:** 곡의 지금 `score_json`에서 NOTE 순서대로 음표의 각을 읽는다. 순번 규칙은 분석 요청·CAT-04·EVA-03과 같다. 세션 악보 버전이 다르거나 범위가 지금 악보의 음표 수를 넘으면 두 칸 모두 `null`이다.
4. 연주한 적 없는 곡이나 없는 곡 ID를 주면 404 대신 `items=[]`다. `songs`는 그대로 준다.
5. `songId`를 생략했는데 세는 연주가 하나도 없으면 `songId=null`, `items=[]`, `comparable=false`, `songs=[]`다. `songId`를 줬으면 규칙 4처럼 그 값을 그대로 돌려준다.

### 오류

| 상황 | HTTP | `result.errorCode` |
| --- | --- | --- |
| 인증 없음·만료 | 401 | `UNAUTHENTICATED` |
| `songId`가 앞자리 0 없는 양의 정수가 아님(빈 값·`05`·Long 범위 초과 포함), `scope`가 `FULL`·`SECTION`이 아님(빈 값 포함), `limit`이 앞자리 0 없는 1~100 정수가 아님 | 400 | `INVALID_REQUEST` |

없는 곡·연주한 적 없는 곡은 오류가 아니다(규칙 4).

## Notion 초안과 다른 점

| Notion GRO-03 초안 | 이 계약 | 이유 |
| --- | --- | --- |
| `songId` 생략 시 모든 곡, 응답 `songId=null` | 생략 시 가장 최근에 연주한 곡을 고르고 그 ID를 돌려준다. `songs[]`로 곡을 바꾼다 | 곡별 그래프(2026-11-02 사용자 결정) |
| 규칙 5: 여러 곡·구간을 한 그래프에 섞을지 논의 | 곡별로만 묶고 구간은 `scope=SECTION`으로 따로 본다 | 같음 |
| `startNoteIdx`·`endNoteIdx` 쿼리 | 1차 제외 | 쓰는 화면 없음 |
| `items[].isFullSong`, `songTitle` | `scope`와 `songs[]`로 대신하고 범위·각 번호·악보 버전을 더한다 | 자세히 보기에 조건을 보여 주기 위해 |
| `categoryGrowth` | 제외 | GRO-01 `metricGrowth` 유지 |
| `comparable`: 비교 가능한 악보·모델·속도 집단이 아니면 false | 2개 이상이고 위 조건이 모두 같을 때만 true | 판정 기준을 고정 |

## 테스트 전략

| 확인 위치 | 잡는 것 | 못 잡는 것 |
| --- | --- | --- |
| 응용 계층 단위 테스트(가짜 저장소) | 곡 고르기, 최근 N개 뒤집기, `comparable` 판정, 각 번호 `null` 조건 | 실제 SQL 조건 |
| 컨트롤러 테스트(보안 필터 없는 MockMvc) | 쿼리 검증 400, 응답 칸 이름·형식 | 실제 데이터 선택, 401 |
| 실제 MySQL 통합 테스트(`GrowthApiDatabaseTests` 계열, 실제 HTTP·JWT) | 상태·평가 유무·전체/구간·다른 회원·지운 기록·정렬·`limit`·`songs` 개수·구간만 연습한 곡, 인증 없는 401. 조건을 하나씩 지우면 실패하는지 확인 | 앱 화면 표시 |
| 접근 규칙 테스트(`ApiAccessRulesTests`) | 새 경로가 로그인 필요 목록에 있음 | |

명령은 [backend README](../../backend/README.md)의 `./gradlew test`, `./gradlew databaseTest`(backend 폴더)를 따른다. 앱 그래프 표시는 이 Task가 아니라 372에서 확인한다.

## 완료 확인

| 확인 항목 | 위치 |
| --- | --- |
| `songId` 없이 부르면 가장 최근 세는 연주의 곡이 나오고, 그 연주가 구간 연습이어도 그 곡이다. `FULL` `items`는 그 곡의 전체 곡 연주만 | DB 통합 |
| 같은 곡의 BPM·BPM 단위·악보 버전·채점 모델이 다른 전체 곡 연주가 모두 `items`에 들어가고 `comparable=false`. 모두 같으면 `true`, 1건이나 0건이면 `false` | 단위·DB 통합 |
| `FAILED`(평가 있음 포함)·평가 없는 `DONE`·구간 연습(끝 음까지 간 것 포함)·다른 회원의 연주는 `FULL`에 없고, 지운 기록·저장하지 않은 연주는 있다 | DB 통합 |
| `SECTION`은 구간 연습만 범위 구분 없이 주고, 범위가 다르면 `comparable=false` | DB 통합 |
| 7건 중 `limit=5`면 최근 5건이 오래된 순. 같은 시각이면 세션 ID 순 | 단위·DB 통합 |
| `songs`는 최근 연주 순이고 곡별 전체·구간 개수가 맞다. 구간만 연습한 곡도 들어간다 | DB 통합 |
| 세션 악보 버전이 지금과 다르면 각 번호가 `null`, 같으면 각 번호가 있다 | 단위 |
| `songId` 없이 불렀는데 세는 연주가 없으면 `songId=null`·`items=[]`·`songs=[]`. 없는 곡·연주 안 한 곡은 200과 빈 `items`이고 요청한 `songId`를 그대로 돌려준다 | 컨트롤러·DB 통합 |
| `limit` 0·101·`abc`·`05`·`+5`, `scope=full`·`ALL`, `songId` 0·`-1`·`abc`·`05`·`+1`은 400 | 컨트롤러 |
| 인증 없으면 401 | 접근 규칙·DB 통합 |
| Swagger 계약(`OpenApiContracts`)에 GRO-03이 '성장' 묶음으로 있다 | `OpenApiDatabaseTests`(구현 메서드 수가 하나 는다) |

## 미결

| 항목 | 정할 사람·필요한 것 | 막는 작업 |
| --- | --- | --- |
| '점수 변화' 영역의 곡 선택·구간 토글·`comparable=false` 안내 문구와 배치 | 디자인(359)·FE(372) | 372 화면. 이 계약은 막지 않는다 |
| Notion GRO-03 페이지를 이 계약으로 고치기 | 사용자 확인 | 372의 계약 공유 |
| 삭제(`deleted_at`)·비공개 곡을 성장 화면에서 뺄지. 지금은 GRO-01과 같이 넣는다 | 기획·FE([연습 시간 집계 스펙](practice-time.md) 미결과 같음) | 성장 화면 표시 |
