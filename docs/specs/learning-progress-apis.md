# 기초부터 배우기 학습 단계·진도 API (LRN-01·02·03)

- Jira: S15P31E102-344(Story S15P31E102-316, Epic S15P31E102-17). 결정·진행: [344 계획](../plans/S15P31E102-344.md). 앱 연결은 S15P31E102-370.
- 상태: 계약 확정(2026-11-02), S15P31E102-344에서 구현. 노션 LRN-01·02·03 계약 초안을 2026-11-02 사용자 결정으로 바꾼 부분은 아래 '노션 초안과 다른 점'에 모았다.
- 목적: 기기에만 남던 '기초부터 배우기' 완료 표시를 회원 계정에 저장해 재설치·다른 기기에서도 이어 보게 하고, 단계 문구를 서버 한 곳에서 고칠 수 있게 한다.

## 학습 트랙과 단계

| track | 앱 트랙 id(`GuideInstrument`) | 악기 |
| --- | --- | --- |
| DANSO | danso | 단소 |
| SOGEUM | sogeum | 소금 |
| DAEGEUM_JEONGAK | daegeum-jeongak | 정악대금 |
| DAEGEUM_SANJO | daegeum-sanjo | 산조대금 |

- 트랙마다 같은 9단계(1 악기 알기·감상 → 2 자세·호흡 → 3 첫 소리 → 4 기본음 → 5 음 잇기 → 6 음역 넓히기 → 7 혀치기·박 나누기 → 8 악보와 곡 → 9 장식음·시김새)를 둔다. 단계 사이 잠금은 없다.
- judgeType은 1·2단계 READ, 3~6단계 PITCH, 7·8단계 BEAT, 9단계 LISTEN이다. 9단계만 선택 단계(optional)이고 필수 단계는 1~8이다.
- 트랙은 `instruments` 테이블과 연결하지 않는다. 악기 행을 추가하지 않고도 네 트랙의 진도를 저장한다.
- 공개(PUBLISHED) 단계만 보인다. 초안(DRAFT) 단계는 목록에서 빠지고 상세·완료 표시는 404다.
- 완료는 사용자가 누른 표시만 저장한다. 통과 기준은 안내 문구이며 서버가 자동으로 완료하지 않는다.

## 공통

`access_token` 쿠키 인증, 공통 응답 envelope. 진도는 로그인한 회원의 것만 읽고 바꾸며 다른 회원의 표시는 응답에 섞이지 않는다.

| 상황 | HTTP | result.errorCode |
| --- | --- | --- |
| 성공 | 200 | 성공 응답 |
| 인증 없음·만료·탈퇴 대기 회원 | 401 | UNAUTHENTICATED |
| track 누락·모르는 코드, 숫자가 아닌 lessonId, 본문 형식 위반 | 400 | INVALID_REQUEST |
| 없는 단계·초안 단계(0·음수 lessonId 포함) | 404 | RESOURCE_NOT_FOUND |
| (LRN-03) JSON이 아닌 본문 | 415 | UNSUPPORTED_MEDIA_TYPE |

## LRN-01 `GET /api/lessons?track={track}`

| Query | 의미 |
| --- | --- |
| track | 필수. 위 네 코드 중 하나를 대문자 그대로 보낸다 |

| result 필드 | 값 |
| --- | --- |
| track | 요청한 트랙 코드 |
| completedCount | 본인이 완료 표시한 공개 필수 단계 수. 선택 단계는 세지 않는다 |
| requiredCount | 공개 필수 단계 수(현재 8) |
| nextLessonId | 완료하지 않은 가장 앞 공개 필수 단계의 lessonId. 모두 완료했거나 없으면 null |
| items[] | 공개 단계, seq 오름차순 |
| items[].lessonId | STRING(양의 bigint) |
| items[].seq | INT |
| items[].title | STRING |
| items[].judgeType | READ·PITCH·BEAT·LISTEN |
| items[].optional | BOOLEAN |
| items[].completedAt | 본인 완료 시각(UTC ISO8601). 미완료면 null |

## LRN-02 `GET /api/lessons/{lessonId}`

(트랙, 단계)마다 공통 문구와 트랙별 내용을 합친 완성본을 돌려준다. 해당하지 않는 배열은 `[]`, 객체는 null이다.

| result 필드 | 값 |
| --- | --- |
| lessonId, track, seq, title, optional, judgeType | LRN-01 항목과 같다 |
| goal, why, source | STRING. 단계 목표·이 순서로 배우는 이유·근거 자료 |
| tasks[] | `{title, description}` 순서대로. description은 STRING 또는 null. 2단계 '악기 잡기'의 description은 트랙별 잡는 법이다. 4·5·6단계는 `[]`(앱이 pitchNames·ranges로 할 일을 만든다) |
| passCriteria | `{title, description}` 또는 null(1·2·9단계) |
| pitchNames | 율명 한자 배열(옥타브 표시 포함, 예: 汰). 3단계는 첫 소리 1개, 4단계는 익힐 순서, 5·8단계는 기본 5음(낮은 음부터). 그 밖은 `[]` |
| pitchNote | 4단계 익힐 순서 설명 `{text, caution}`. caution은 STRING 또는 null. 그 밖은 null |
| parts | 1단계 악기 부분 이름 배열 |
| ranges[] | 6단계 음역 `{blow, label, description, firstPitch}`. blow는 HIGH·LOW, firstPitch는 율명 한자 |
| songs[] | 8단계 연습곡 후보 `{title, meta}`. 곡 연결은 앱이 같은 제목의 평가 가능한 곡을 찾는다 |
| completedAt | 본인 완료 시각 또는 null |

## LRN-03 `PUT /api/lessons/{lessonId}/completion`

Body `{"completed": true}` 또는 `{"completed": false}`. completed는 JSON boolean이며 누락·null·문자열은 400이다.

| 요청 | 결과 |
| --- | --- |
| completed=true, 처음 | 지금 시각(UTC)으로 완료 |
| completed=true, 이미 완료 | 처음 완료 시각 유지 |
| completed=false | 완료 표시를 지운다. 표시가 없어도 200 |

| result 필드 | 값 |
| --- | --- |
| lessonId | STRING |
| completed | BOOLEAN, 요청한 값 |
| completedAt | 완료 시각, 해제하면 null |
| completedCount, nextLessonId | 그 단계 트랙 기준으로 LRN-01과 같은 규칙 |

같은 회원이 같은 단계에 동시에 true를 보내도 완료 기록은 하나이고 시각은 먼저 저장된 값이다. 일괄 동기화 API는 두지 않는다. 기기에 남은 기존 표시를 올릴지는 S15P31E102-370이 정한다.

## 데이터

- `lessons`: track(네 코드)·seq·title·judge_type·is_optional·content_json(LRN-02 상세)·status. (track, seq)는 유일하다. 기존 kind·instrument_id는 없앤다.
- `lesson_progress`: user_id·lesson_id·completed_at. (user_id, lesson_id)는 유일하다. 행이 있으면 완료이고 해제하면 행을 지운다. 기존 score는 없앤다.
- Flyway 마이그레이션(현재 develop 최신 V12 다음, 병합 직전 번호 재확인): 두 테이블에 행이 있으면 구조를 바꾸기 전에 실패한다. 이어서 36행을 PUBLISHED로 넣는다. 문구 출처는 develop cfad44c3의 `LearnCurriculum.kt`(`LearnStages`·`learnContent`·`LearnSongs`)다.
- 단계 문구 수정은 새 Flyway 마이그레이션으로 한다. 화면 구성·섹션 제목과 4·5·6단계 할 일·패턴을 만드는 규칙(`strings.xml`·`LearnPattern`)은 앱에 남는다.
- 탈퇴 일괄 삭제는 지금처럼 회원의 `lesson_progress`를 지운다.
- LRN-01은 (track, seq) 인덱스로 트랙의 단계를 순서대로 읽고 (user_id, lesson_id) 인덱스로 본인 표시를 붙인다. content_json은 LRN-02에서만 읽는다.

## 노션 초안과 다른 점

- LRN-01 Query와 LRN-01·02 응답의 `instrumentId`를 `track`으로 바꿨다. 악기 테이블에 단소만 있고, 정악·산조대금은 같은 악기라 악기 ID로 나눌 수 없다.
- `thumbnailUrl`을 뺐다. 카드 그림은 앱에 든 9장을 단계 번호로 쓴다.
- LRN-02 `songIds`를 `songs[{title, meta}]`로 바꾸고 `pitchNote`·`parts`·`ranges`를 더했다. 앱 단계 화면이 트랙별로 쓰는 내용이다.
- LRN-01 규칙 4(1차 포함 여부)는 1차 포함, LRN-02 규칙 3(문구 위치)은 서버 보관, LRN-03 규칙 3(자동 판정)은 사용자 표시만으로 정했다.
- 노션 명세 갱신과 S15P31E102-370 범위 갱신(앱이 LRN-02 문구를 쓰고 `LearnCurriculum.kt` 문구를 지움)이 남아 있다.

## 범위 밖

앱 연결·기기에 남은 표시 이관(S15P31E102-370), 통과 기준 자동 판정, 단계 그림 서버 제공, `instruments` 행 추가, 연습 기록지(GRO-01)의 '완료한 단계' 서버 집계.

## 구현 경계·검증

- 기존 `feature/lesson` 패키지에 둔다. api는 HTTP 매핑·입력 검증, application은 본인 진도와 트랜잭션, domain은 트랙 코드와 완료 수·다음 단계 계산, infrastructure는 MyBatis 조회와 content_json 해석을 맡는다.
- 단위 테스트: completedCount·nextLessonId(선택 단계 제외, 모두 완료면 null, 초안 단계 제외), 트랙 코드 해석.
- MockMvc: track 누락·모르는 코드·숫자가 아닌 lessonId·본문 누락·문자열 completed의 400, 미인증 401, JSON이 아닌 본문 415.
- 실제 HTTP/MySQL(`databaseTest`):
  - 마이그레이션 뒤 36행, 트랙마다 9행, 모두 PUBLISHED, seq별 judgeType, 9단계만 optional인지 확인한다.
  - 트랙 분리와 다른 회원 표시 불변, 초안 단계의 목록 제외·상세 404·완료 404를 확인한다.
  - LRN-02 표본: DANSO 4단계 pitchNames, SOGEUM 1단계 parts, DAEGEUM_SANJO 2단계 잡는 법, DAEGEUM_JEONGAK 6단계 ranges 2개, 8단계 songs 6개.
  - LRN-03: true 두 번이면 첫 시각 유지, false로 해제, 같은 단계 동시 true에도 한 행.
  - 탈퇴 일괄 삭제 뒤 진도가 남지 않는다.
  - LRN-01 조회의 실행 계획(EXPLAIN)이 두 유일 인덱스를 쓰고 filesort가 없다.
  - Swagger 검사(`OpenApiDatabaseTests`)가 새 경로 3개를 포함해 통과한다.
- 명령: `./gradlew clean build`, `./gradlew databaseTest`(`TEST_DB_*`를 폐기용 MySQL 8.4에 연결).
- 이 검사로 알 수 없는 것: 앱이 응답만으로 같은 화면을 그리는지(370), 운영 DB의 두 테이블이 비어 있는지(배포 때 마이그레이션 사전 확인이 실패로 알린다), 옮긴 문구 전체의 일치(표본 검사와 MR 검토로 확인).

## 열린 항목

- pitchNames·firstPitch는 앱 운지표와 같은 율명 한자를 쓴다. 서버 율명 테이블(CAT-06)은 단소만 있어 대조하지 않는다. 370 착수 때 앱 표기와 맞는지 확인한다.
