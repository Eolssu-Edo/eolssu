# 내 악보 조회·목록·편집 저장·삭제·불러오기 API (SHT-01·02·04·07·08·10·11)

## 작성 원본과 변환 기록 (2026-11-11, S15P31E102-771)

SHT-08·초안은 공통 v1 밖의 선택적 `authoring`을 반환한다. `schema=sheet-authoring/v1`, `source={kind,instrument,scoreVersion,scoreSha256,score,notes}`이며 notes는 eventId·cellId·startTick·durationTick·원래 실음 MIDI다. `RECOGNIZED`는 인식 결과 원본, `EXISTING_SCORE`는 과거 원본이 없는 현재 판본 기준이다. 인식 결과 JSON과 V41 `user_sheets.authoring_json`에 보존하고 기존 행은 일괄 변환하지 않는다. 원본 PDF 비보관 정책은 유지한다.

SHT-04·11 요청은 `authoring={schema,source:{scoreSha256},adaptation?}`만 보내 원본 전체 중복으로 1MiB 본문 제한을 넘기지 않는다. 전체 source를 보낼 때도 서버 보존본과 동일해야 하며 클라이언트가 원본을 교체할 수 없다. 생략하는 기존 클라이언트는 원본을 보존한다. `adaptation`은 instrument·전곡 transpose(-6..6)·변경 event의 fromMidi/toMidi/octaveShift를 남긴다. 음 선택·확정 여부는 이 기록을 신뢰하지 않고 실제 공통 악보와 작성 카탈로그로 다시 검사한다.

변환 순위·원본 옥타브 유지·미해결 초안·취소/실행 취소 UX는 [작성 일관성](score-authoring-consistency.md)을 따른다. 위치·길이가 달라진 새 입력은 현 입력을 기준으로 변환하고, 기존 원본 snapshot은 바꾸지 않는다.

- Jira: [S15P31E102-390](https://ssafy.atlassian.net/browse/S15P31E102-390), Story [S15P31E102-319](https://ssafy.atlassian.net/browse/S15P31E102-319). 진행: [390 계획](../plans/S15P31E102-390.md). 편집 저장·자동 저장·삭제: [S15P31E102-413](https://ssafy.atlassian.net/browse/S15P31E102-413), [413 계획](../plans/S15P31E102-413.md). SHT-03 제거: [S15P31E102-462](https://ssafy.atlassian.net/browse/S15P31E102-462), [462 계획](../plans/S15P31E102-462.md). 불러오기 접수·변환 상태: [S15P31E102-471](https://ssafy.atlassian.net/browse/S15P31E102-471), [471 계획](../plans/S15P31E102-471.md).
- 근거: [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 SHT-01·03·08(2026-11-02 디자인 보정 반영본). 2026-11-02 회의 안건(PDF 방향, 초안 연습 진입, 삭제 시 연습 기록, 확인 필요 칸 복사)은 이 범위에서 정하지 않는다. 그중 PDF 방향·삭제 시 연습 기록은 2026-11-03 결정으로 정리됐다([2차 결정](#2차-결정-2026-11-03-미구현)).
- 상태: 백엔드 결정 초안. 아래 '노션 초안과 다른 점'은 FE(351)와 공유해야 한다.

## 범위

- 포함: `user_sheets` 저장 모델(V16), SHT-03 빈 악보·복사 생성(2026-11-03 결정으로 작업 취소, S15P31E102-462에서 API와 전용 코드 제거), SHT-08 본인 악보 조회, SHT-01 본인 악보 목록, 회원 탈퇴 일괄 삭제에 내 악보 추가.
- 413에서 추가: V20 초안 칸, SHT-04 확정 저장, SHT-11 자동 저장, SHT-08 `events`·`draft`, SHT-01 `hasDraft`, SHT-02 삭제(아래 [편집 저장·자동 저장·삭제](#편집-저장자동-저장삭제-s15p31e102-413)).
- 471에서 추가: SHT-07 불러오기 접수(원본 보관, 회원당 악보 10개), SHT-10 변환 상태, SHT-01·08 변환 필드, SHT-02·탈퇴의 원본 삭제(아래 [불러오기 접수·변환 상태](#불러오기-접수변환-상태-s15p31e102-471)).
- 472에서 추가: 인식(omr) 호출·결과 저장·확인 필요 칸·재시도(SHT-14). [인식 작업자·결과·재시도](#인식-작업자결과재시도-s15p31e102-472)를 따른다.
- 670에서 추가: [제공 곡을 개인 악보로 복사](#제공-곡을-개인-악보로-복사-s15p31e102-670)하는 전용 경로와 편집 진입.
- 제외: 음원 생성·임시 M4A 다운로드(SHT-12·13)는 [내 악보 음원 스펙](sheet-audio.md)을 따른다. 기존 범용 악보 만들기(SHT-03)·음원 변환(SHT-09)·서버 미리듣기(SHT-06)·서버 오선보(SHT-05)는 작업 취소됐다.

## 공통

- `access_token` 쿠키 인증, 공통 envelope, 오류는 `result.errorCode`. 경로·응답 ID는 양의 Long 십진 문자열, 시각은 UTC ISO8601.
- 다른 회원의 악보는 없는 악보와 같게 404 `RESOURCE_NOT_FOUND`다.

## 저장 모델

`user_sheets` 한 행이 악보 하나다. 악보 내용은 제공 곡과 같은 공통 악보 v1 JSON(`score_json`)이며, 아직 한 번도 저장하지 않은 빈 악보는 NULL이다. 미입력 칸(`units: []`)은 자동 저장 초안(`draft_json`)에만 둘 수 있고 확정 저장(`score_json`)에는 둘 수 없다.

| 칸 | 의미 |
| --- | --- |
| `kind` | `JEONGGANBO`만 만든다. MIDI·PDF는 해당 기능에서 CHECK를 넓힌다. |
| `source` | 만든 방식. 직접 작성·복사는 모두 `MANUAL`. |
| `status` | `NEEDS_REVIEW`(평가 불가) / `EVALUABLE`(평가 가능). 변환 상태는 SHT-10에서 추가한다. |
| `jangdan_id`, `bpm`, `bpm_unit`, `column_count` | 편집 화면 설정. `bpm_unit`은 만들 때의 장단 단위이며, 장단 없는 곡(V15에서 `songs.jangdan_id`가 nullable)을 복사하면 `jangdan_id`는 NULL, 단위는 원본 악보의 `music.bpmUnit`이다(CAT-04와 같다). |
| `score_version` | 1부터. JSON의 `score.scoreVersion`과 같게 유지한다. |
| `note_count`, `pending_cell_count` | NOTE 수, 확인 필요 칸 수(472에서 변환 결과와 검수에 따라 갱신). |
| `idempotency_key`, `request_hash` | 회원·키 유일. 파일 불러오기(SHT-07)와 제공 곡 복사에서 재전송을 확인한다. |
| `draft_json`, `draft_revision`, `draft_base_version`, `draft_saved_at` | V20. SHT-11 자동 저장 초안(`{title, jangdanId, bpm, score}`)과 순번·시작 버전·저장 시각. 확정 저장하면 NULL·0으로 돌아간다. |

## SHT-03 `POST /api/users/me/sheets` (제거)

> 작업 취소(2026-11-03 결정, [2차 결정](#2차-결정-2026-11-03-미구현)). 390에서 구현했던 엔드포인트와 그것만 쓰던 코드를 S15P31E102-462에서 지웠다. 이 경로의 POST는 이제 405 `METHOD_NOT_ALLOWED`다.

- 하던 일: `Idempotency-Key` 필수로 빈 악보(`BLANK`, 장단·각 수 지정, 내용 없음·`NEEDS_REVIEW`)나 게시 곡·내 악보 복사본(`COPY`, `score.scoreVersion`만 1로 바꾼 사본)을 만들었다. 모두 `source` `MANUAL`이다.
- 그때 만든 행은 그대로 SHT-01·08·04·11·02로 읽고 고치고 지울 수 있다. 빈 악보는 `scoreData`가 null, 장단 없는 곡 복사본은 `jangdanId`가 null이다.

## SHT-08 `GET /api/users/me/sheets/{sheetId}/score`

200 `result`: `sheetId`, `title`, `kind`, `jangdanId`(장단 없는 곡 복사본은 null), `bpm`, `bpmUnit`, `columnCount`, `scoreVersion`(문자열), `reviewStatus`(`status`와 같은 값), `scoreData`(공통 악보 원본, 빈 악보는 null), `source`(null), `issues`(`[]`), `updatedAt`. 잘못된 ID 400, 없음·타인 404.

## SHT-01 `GET /api/users/me/sheets`

- 쿼리: `kind`(`JEONGGANBO`/`MIDI`/`PDF`, 그 외 400), `page`(0 이상, 기본 0), `size`(1~100, 기본 20).
- `result.items[]`: `sheetId`, `kind`, `title`, `status`, `jangdanId`·`jangdanName`(장단 없는 곡 복사본은 둘 다 null), `source`, `pendingCellCount`, `sourcePageCount`(null), `updatedAt`, `instrumentCode`(문자열 또는 null). `page`, `size`, `totalElements`, `totalPages`도 반환한다.
- 모든 목록 항목에 `instrumentCode`를 포함한다. 초안이 있으면 초안 공통 악보의 `score.instrument`, 그 외에는 확정 공통 악보의 `score.instrument`를 반환한다. 코드가 비어 있거나 확정본·초안의 JSON을 편집기가 읽을 수 없으면 null이다. 항목에 코드를 받은 편집기는 해당 항목을 목록에서 바로 거른다. null 또는 필드가 없는 응답은 이전 상세 조회 경로를 사용한다. [S15P31E102-765 작업 기록](../plans/S15P31E102-765.md)
- 본인 악보만, `updatedAt` 내림차순·`sheetId` 내림차순. 없으면 빈 배열.

## 노션 초안과 다른 점

- SHT-03: 엔드포인트를 지웠다(462). 노션과 달랐던 점(COPY의 장단·각 수 생략, 상태·409 규칙)은 더 이상 해당하지 않는다.
- SHT-08: `scoreData`는 빈 악보에서 null이고, `jangdanId`는 장단 없는 곡 복사본에서 null이다. 편집 화면에 필요한 `title`·`jangdanId`·`bpm`·`bpmUnit`·`columnCount`를 더했다. `source`는 null, `issues`는 빈 배열이다.
- SHT-01: `source`는 지금 `MANUAL`만, `sourcePageCount`는 null이다. 장단 없는 곡 복사본은 `jangdanId`·`jangdanName`이 null이다.

## 편집 저장·자동 저장·삭제 (S15P31E102-413)

요청·응답 필드는 Notion SHT-02·04·08·11을 따른다. 구현에서 정한 점과 Notion과 다른 점:

- SHT-04 `PUT /api/users/me/sheets/{sheetId}/score`: `baseScoreVersion`(문자열)이 현재 버전과 같을 때만 저장하고 버전을 1 올린다(DB `WHERE score_version = ?`로도 막는다). 미입력 칸은 400 `UNFILLED_CELLS`(`details.cellIds`, ID 없는 빈 칸은 400 `INVALID_REQUEST`). 요청 본문이 1MiB를 넘으면 JSON을 읽기 전에 413 `PAYLOAD_TOO_LARGE`(SHT-11도 같다, 받은 바이트 수 기준, Content-Length 없는 본문·인코딩한 경로 포함). `resolvedIssueIds: null`은 생략과 같다. `jangdanId`를 주면 `score.music.jangdan`(장단 이름)·`bpmUnit`이 그 장단과 같아야 한다. 검증·타임라인은 CAT-04 디코더를 쓴다. 성공하면 초안을 지우고 `draft_revision`을 0으로 되돌린다.
- 서버는 `score.scoreVersion`을 새 버전으로 바꾸고 `provenance`를 서버 값으로 덮어쓴다(S15P31E102-447). DATA `score_data.schema.json`·`validate_common`(SHT-12 합성)이 요구하는 형식이다: `sourceId`는 `score.sourceId`(비어 있지 않은 문자열이어야 하며 아니면 400), `jgbSha256`은 provenance를 뺀 저장 문서의 SHA-256(편집기 악보에는 .jgb.json이 없다), `review`는 `inferred`, `converter`는 `app-editor-1`. 클라이언트가 보낸 provenance는 쓰지 않는다. 초안(SHT-11)은 받은 그대로 둔다.
- 472에서 확인 필요 칸을 연결한다. `resolvedIssueIds`는 현재 악보의 issue ID만 허용한다. 확인한 항목을 제거한 뒤 cellId(없으면 eventId)를 중복 제거해 센다. 남은 칸이 있으면 NEEDS_REVIEW, 없으면 EVALUABLE이다. 악보·변환 결과 갱신은 하나의 트랜잭션이다. 변환이 DONE이 되기 전 확정 저장·초안 저장은 409다.
- SHT-11 `PUT .../score/draft`: 버전과 `draftRevision`이 서버와 같을 때만 덮어쓰고 순번을 1 올린다. 칸 안은 검증하지 않고 최상위 모양만 본다. `updated_at`(목록 정렬)은 바꾸지 않는다.
- SHT-08: `events`(저장 악보의 CAT-04 타임라인, 빈 악보는 `[]`)와 `draft`를 더했다. `draft`에는 Notion 필드(`score`·`baseScoreVersion`·`draftRevision`·`savedAt`)에 더해 초안의 `title`·`jangdanId`·`bpm`이 들어 있다(편집 화면 복원용). 저장 악보가 CAT-04 검증을 통과하지 못하면 409.
- SHT-01: `hasDraft`를 더했다. `conversionStatus`·`source` 확장은 변환 기능에서 한다.
- SHT-02 `DELETE /api/users/me/sheets/{sheetId}`: 204 빈 본문, 없거나 다른 회원·이미 지운 악보는 404. 초안은 같은 행이라 함께 지워진다. 원본 파일·다운로드 링크는 아직 없다.

## 불러오기 접수·변환 상태 (S15P31E102-471)

FE 접수 흐름(S15P31E102-539, 사용자 2026-11-06 결정): 정간보의 장단 선택을 **업로드 전**에 한다. 이전 화면의 인식 후 선택은 이 결정으로 대체한다. SHT-07은 실제 파일·카탈로그 ID로 접수하고 202 뒤 접수 완료와 목록 이동까지만 연결한다. SHT-10 상태 조회·인식 결과 연결과 SHT-14는 후속이다. [539 계획](../plans/S15P31E102-539.md)·[파일 선택·접수 경계](../knowledge/frontend/sheet-import-submission.md).

- 저장: V26 `sheet_conversions`(악보당 변환 한 건, 이 표가 큐다. 회원당 `QUEUED`·`RUNNING`은 생성 칸 `active_user_id` UNIQUE로 1건), `sheet_source_files`(원본 객체 키·형식·크기·파일 이름). 둘 다 `user_sheets` 삭제에 따라 지워진다. `user_sheets.source`에 `JEONGGANBO_OMR`·`STAFF_OMR`.
- 원본은 악보 전용 비공개 버킷(`eolssu.sheet-storage.*`, 환경변수 `SHEET_STORAGE_*`)의 `sheet-sources/{userId}/{무작위}/{순서}.{확장자}`에 둔다. 연결값이 하나라도 비면 SHT-07은 503 `CONVERSION_UNAVAILABLE`이고 기동은 막지 않는다. `local`은 개발·테스트용 디렉터리 저장소다.
- SHT-07 `POST /api/users/me/sheets/imports`(multipart `files`·`metadata`, `Idempotency-Key` 필수): 개수 → 크기 → 형식(파일 앞부분) → PDF·이미지 구성 → PDF 쪽 수·암호(PDFBox) → metadata 범위 → 같은 키 → 저장소 → 악기·장단 → 상한 순으로 본다. 원본을 올린 뒤 회원 행을 잠근 한 트랜잭션에서 키·상한·진행 중을 다시 보고 행을 만든다. 거절되면 올린 객체를 지운다. 202 `result`는 `sheetId`·`notation`·`source`·`conversionStatus`·`pageCount`·`acceptedAt`.
  - 요청 해시는 metadata 값과 파일마다 형식·크기·SHA-256이다(파일 이름은 넣지 않는다). 같은 키·같은 해시는 처음 악보의 현재 상태로 202, 다른 해시는 409 `IDEMPOTENCY_CONFLICT`.
  - 장단·bpm을 생략하면 악보 행에는 장단 기본 BPM(장단도 없으면 60·`QUARTER` 자리값)을 둔다. 472가 인식 결과의 장단으로 바꾼다.
  - 25MB를 넘는 multipart는 컨트롤러 전에 413 `FILE_TOO_LARGE`(녹음 경로는 그대로 `RECORDING_TOO_LARGE`).
- SHT-10 `GET /api/users/me/sheets/{sheetId}/conversion`: 불러오기 악보만(MANUAL·다른 회원은 404). `pollAfterMs`는 진행 중 2000, `jangdanAuto`는 STAFF에서 장단을 생략했을 때, `bpmAuto`는 bpm을 생략했을 때 true. `retryable`은 472 전까지 false, `issueCount`는 DONE이면 0(472가 채운다). `Cache-Control: no-store`.
- SHT-01은 `conversionStatus`·`sourcePageCount`, SHT-08은 `conversionStatus`·`source`(`type`·`fileName`·`hwangPitch`·`pages: []`·`pagesExpireAt: null`)·`notices: []`를 더한다. 변환이 끝나기 전 `columnCount`는 0이다(행에는 CHECK 때문에 1).
- SHT-02는 원본 객체를 먼저 지우고 행을 지운다. 저장소가 실패하면 503 `SERVICE_UNAVAILABLE`이고 행이 남아 다시 지울 수 있다. 탈퇴 일괄 삭제는 녹음 키와 함께 `sheet-sources/` 키를 지우며, 키 앞부분으로 악보 전용 저장소를 고른다.


## 인식 작업자·결과·재시도 (S15P31E102-472)

- V27은 변환의 내부 실패 원인·누적 TIMEOUT 횟수·결과 JSON과 원본 쪽 PNG 표 `sheet_source_pages`를 추가한다. issues·notices·쪽 메타데이터는 변환 결과 JSON에 함께 보관한다. 별도 issue 표 대신 악보와 함께 원자적으로 갱신한다.
- DB 접수 순으로 한 건씩 선점하고 전역 RUNNING 슬롯 UNIQUE로 동시 인식을 막는다. OMR HTTP 호출 동안 DB 잠금을 유지하지 않는다. 호출 제한은 전체 본문 수신까지 630초이고 자동 HTTP 재시도는 없다.
- 공통 악보는 sourceId `sheet-{id}`, review `inferred`, 원본 jgb 해시·엔진 converter를 보관한다. 빈 칸이 없으면 score_json, 있으면 SHT-11 형식 초안에 저장한다. 결과는 NEEDS_REVIEW이며 BPM 생략 시 인식된 장단의 기본 BPM을 쓴다.
- SHT-08은 issues·notices와 15분 서명 URL의 source.pages를 준다. bbox는 OMR 쪽 크기로 나눈 0~1 좌표다. PDF는 144dpi PNG, 사진은 OMR과 같은 EXIF 방향으로 만든다. 응답은 no-store다.
- SHT-10은 남은 확인 칸 수와 원인별 retryable을 반환한다. SHT-14는 본인 FAILED만 재접수하며 같은 QUEUED·RUNNING 재요청은 기존 시도를 반환한다. 전체 재시도 3회(attempt 4), TIMEOUT 재시도 1회, ENGINE_ERROR·악보 없음·입력 오류는 재시도하지 않는다.
- 삭제·탈퇴 정리 전 진행 중 작업을 중단 상태로 바꾼다. 늦게 온 결과는 저장하지 않고 만든 PNG를 정리한다. 원본과 PNG는 같은 sheet-sources/ 앞부분 아래에 두며 정리 실패는 행을 보존해 다시 삭제할 수 있다.
- 시작 시 남은 RUNNING은 재시도 가능한 서버 실패로 정리한다. 단일 backend 인스턴스에서 실행하며 롤링 중 중복 인스턴스 운영은 이 범위에서 지원하지 않는다.
- 실행·검증: [472 계획](../plans/S15P31E102-472.md). 실제 OMR 엔진 품질·운영 R2·기기 확인은 로컬 HTTP 대역·폐기용 MySQL 검사와 구분한다.

## 탈퇴

탈퇴 일괄 삭제의 회원 행 삭제(3단계)에 `user_sheets`를 더한다.

## 미등록 율명의 검수 초안 (S15P31E102-669)

- 인식 결과의 타임라인이 유효하고 일부 율명만 목록에 없으면 변환은 `DONE`으로 끝난다. 내 악보는 `NEEDS_REVIEW` 상태에서 원래 음높이와 이벤트를 담은 초안을 연다.
- SHT-08에서 `scoreData`는 null이며 `draft.score`에 악보가 들어간다. `issues`에 해당 음의 `cellId`와 `eventId`를 가리키는 `UNREADABLE` 항목을 반환한다. `detail.reason`은 `UNREGISTERED_PITCH`이며 `detail.yul`과 `detail.octave`로 원래 음을 확인할 수 있다.
- 확정 저장은 등록된 음만 받는다. 사용자가 해당 음을 고치고 확인 항목을 해결하면 SHT-04의 기존 절차로 저장한다.

## 2차 결정 (2026-11-03, 미구현)

사용자 결정(S15P31E102-382)의 당시 미구현 기록이다. 요청·응답·오류의 상세는 [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 SHT-01~13을 따른다. 이후 구현 여부는 위 작업별 절을 우선한다.

- 내 악보는 연습(기기에서 따라 부르기·한 음씩 연습)만 하고 AI 평가는 하지 않는다. PRA-01은 `sheetId`를 받지 않는다. `EVALUABLE`은 '빈 칸·확인 필요 칸이 없어 연습·내보내기를 할 수 있음'이라는 뜻으로 쓴다(값은 그대로).
- SHT-07 `POST /api/users/me/sheets/imports`(경로 변경): 정간보·오선보 PDF 1개 또는 PNG·JPEG 1~10장, 파일·합계 20MiB, PDF 10쪽, 회원당 동시 변환 1건. 202와 함께 `user_sheets` 행을 바로 만든다. 결과는 공통 악보 v1로 저장하고 `.jgb.json`은 변환 중간 결과다.
  - 정간보: OMR → `.jgb.json` → 우물사이 엔진 → 공통 악보 v1. `jangdanId` 필수.
  - 오선보: Audiveris → MusicXML → `.jgb.json` → 우물사이 엔진 → 공통 악보 v1. 사용자가 고른 `hwangPitch`(생략하면 악기 기본, 단소 Eb4)를 황으로 놓고 율명을 정한다.
  - 원본은 R2 비공개로 악보 삭제·탈퇴까지 보관하고, 검수 화면은 SHT-08 `source.pages`의 서명 URL로 쪽 이미지를 띄운다.
- SHT-10: 변환 상태 QUEUED / RUNNING / DONE / FAILED. 자동 변환 결과는 `NEEDS_REVIEW`이고 불확실한 칸은 SHT-08 `issues`로 알린다.
- SHT-04 `PUT .../score`: 공통 악보 v1 문서 전체 교체, 저장마다 `scoreVersion` + 1, 미입력 칸이 있으면 400 `UNFILLED_CELLS`. SHT-11 `PUT .../score/draft`: 버전을 올리지 않는 자동 저장 초안(악보당 하나, 미입력 칸 `units: []` 허용). 빈 칸 표현은 초안에만 둔다.
- SHT-08: `events`(CAT-04 형식)·`draft`·`conversionStatus`·`source.pages`·`issues`를 더한다. 변환 중·실패도 200이다.
- SHT-01: `conversionStatus`·`hasDraft`를 더하고 `source`에 `JEONGGANBO_OMR`·`STAFF_OMR`을 더한다.
- SHT-02 `DELETE`: 204. 초안·확인 필요 칸·변환 상태·다운로드 링크·R2 원본까지 지운다. 변환 중에도 지운다. 내 악보는 연습 세션을 만들지 않으므로 연습 기록 처리는 필요 없다(회의 ③ 해소).
- SHT-12·13(2026-11-04 변경, 여러 기기에서 같은 음원을 쓰려고): SHT-12 `POST .../{sheetId}/audio`는 202로 접수하고 공통 악보 음원 생성기로 M4A·MIDI를 비동기로 만들어 악보 전용 R2 `sheet-audio/`에 보관한다. SHT-13 `GET .../{sheetId}/audio`는 상태(QUEUED/RUNNING/DONE/FAILED)·`outdated`(악보 버전이 바뀜)와 15분 서명 URL을 준다. 악보당 1벌(설정·버전이 바뀌면 새로 만들고 DONE 뒤 이전 파일 삭제), 길이 600초·M4A 15MB 상한, 회원당 생성 중 1건. 이전의 10분 토큰 링크·서버 비저장 설계와 `sheet_export_links`는 쓰지 않는다.
- 회원당 내 악보 10개(2026-11-04). 변환 중·실패도 센다. 넘으면 SHT-07이 409 `SHEET_LIMIT_EXCEEDED`.
- 작업 취소: SHT-03(악보는 SHT-07 불러오기로만 만든다. 빈 악보·복사 시작은 기획에 없다. 2026-11-04 Notion 정리, 390 구현은 S15P31E102-462에서 제거), SHT-05(오선보 보기는 앱이 공통 악보로 그림), SHT-06(미리듣기는 앱이 재생), SHT-09(음원 변환).
- 저장 모델에 필요한 것(새 Flyway): `user_sheets`의 `source` CHECK 확장·초안 칸(`draft_json` 등), `sheet_source_files`, `sheet_conversions`, `sheet_issues`, `sheet_audios`. 탈퇴 일괄 삭제와 R2 원본 삭제도 함께 넓힌다.
- 인식 연결(2026-11-04 313 답): omr는 쪽당 60초·최대 600초, backend 호출 제한 630초, 한 번에 한 건. SHT-08은 칸이 있는 `issues`와 악보 전체·쪽 단위 `notices`를 나눠 준다([악보 인식 서비스 계약](score-recognition.md), S15P31E102-469). 재시도(SHT-14)는 원인별로 TIMEOUT 1회, 인식 엔진 오류 없음, 서버 쪽 실패 최대 3회(합계 attempt 4까지). 그림 정간보 OMR(S15P31E102-458)은 2차 MVP에 포함한다.
- 음원 생성기는 내부 컨테이너 `playback`(`http://playback:8000`, 2026-11-04 인프라)에서 돈다. HTTP API 계약과 생성 시간 상한은 미정이다(S15P31E102-481).
- SHT-07 변환 결과에는 `score.sourceId`를 넣어야 한다. SHT-04가 이 값으로 `provenance`를 채우고, 없으면 400이다(S15P31E102-447).

## 업로드 원본 비보관 정책 (2026-11-06 확정, S15P31E102-534)

이 절은 앞의 471·472 원본 보관·미리보기·재시도 규칙을 대체한다. 구현 기준은 534 브랜치이며 develop·운영 반영 여부와 구분한다.

- 업로드 이미지·PDF는 backend의 비공개 임시 디렉터리에만 두며 R2에 올리지 않는다. 인식기만 추측할 수 없는 단기 URL로 내부망에서 읽는다. 외부 Nginx는 이 내부 경로를 거절한다.
- 성공·실패·사용자 삭제 시 즉시 지운다. 사용자 확정대로 최대 30분이며, 대기 중·진행 중이어도 기한 초과 시 파일 삭제와 FAILED(TIMEOUT) 처리 후 재업로드한다. 정리 주기는 최대 1초이며 잠금·DB 장애 시 정리는 재시도한다.
- Spring multipart 임시 파일도 같은 30분 수명 안에 둔다. 정체한 요청의 파일이 정리되면 SHT-07은 503 `CONVERSION_UNAVAILABLE`이다.
- Nginx가 본문을 받는 동안의 임시 파일은 이 정책의 승인된 예외다(2026-11-06 사용자 결정). 요청이 끝나면 지워지지만, 30분 넘게 조금씩 보내는 업로드를 막지는 않는다. 버퍼링을 끄면 느린 업로드가 backend 스레드를 오래 붙잡으므로 끄지 않는다.
- 요청 복사본의 30분은 컨트롤러 진입부터 센다. Spring이 파일을 받은 뒤 진입 전에 멈추면 합계가 30분을 넘을 수 있지만, 이 구간에는 대기 지점이 없다.
- backend 재시작 시 남은 임시 파일을 지우고 QUEUED·RUNNING을 FAILED(SERVER_ERROR)로 끝낸다. 인식기 종료·요청 실패 때도 원본을 재사용하지 않는다.
- SHT-10 retryable은 항상 false다. 기존 SHT-14 경로는 인증·소유권·ID 검증을 유지하고 본인 변환에 409 STATE_CONFLICT를 돌려준다. 새 파일은 SHT-07과 새 Idempotency-Key로 다시 올린다. 같은 키·같은 파일의 멱등 재전송은 기존 상태를 돌려주며 인식을 다시 시작하지 않는다.
- SHT-08 source.pages는 항상 [], pagesExpireAt은 항상 null이다. 과거 result_json의 원본 페이지도 서명하거나 노출하지 않는다. 확인 필요 칸의 eventId·cellId와 DB 정간보·초안은 유지한다.
- 신규 변환은 원본 쪽 이미지 렌더링·보관을 하지 않는다. 이 원본 비보관 정책은 M4A·MIDI 보관에 영향을 주지 않는다. SHEET_STORAGE_*는 생성 음원과 사용자가 명시적으로 삭제한 과거 원본 정리에만 사용한다.
- 기존 R2 원본의 일괄 삭제·운영 배포는 별도다. 이미 발급된 기존 서명 URL은 만료 전까지 유효할 수 있다.

검증: 임시 파일 실제 생성·만료·삭제·재시작, 인식 성공/실패/삭제 경합, retry 409와 원본 URL 미반환, HTTP 인증 및 내부 경로 외부 차단, backend clean build·databaseTest. 실제 OMR는 가능한 범위의 고정 입력으로 별도 증거를 남기며 기기 E2E를 자동 검사로 대체하지 않는다.

## 제공 곡을 개인 악보로 복사 (S15P31E102-670)

작업 브랜치의 복사 전용 계약이다. EDIT-01의 기존 악보 불러오기에서 제공 곡을 선택한 회원이 `내 악보로 복사`를 누른다. 앱은 응답의 `sheetId`로 기존 내 악보 편집을 연다. 내 악보 탭에서 고른 악보는 해당 ID로 편집한다.

| 구분 | 계약 |
| --- | --- |
| 요청 | `POST /api/users/me/sheets/copies`, JSON `{"songId":"1"}`. 로그인 access 쿠키와 공백 없는 가시 ASCII 1~100자의 `Idempotency-Key`가 필수다. `songId`는 양의 정수를 나타내는 1~18자리 문자열이다. |
| 성공 | `201`, `result.sheetId`는 새 개인 악보 ID 문자열이다. 사본이 남아 있을 때 같은 회원이 같은 키와 곡으로 재전송하면 기존 개인 ID를 반환한다. |
| 오류 | 잘못된 요청 400, 미인증 401, 없거나 게시되지 않은 곡 404, 본문 1MiB 초과 413, 공통 악보를 읽을 수 없거나 원본의 각이 비었거나 편집 속도 30~160 BPM 밖이면 409 `STATE_CONFLICT`, 같은 키의 다른 요청 409 `IDEMPOTENCY_CONFLICT`, 변환 중 409 `CONVERSION_IN_PROGRESS`, 내 악보 10개 409 `SHEET_LIMIT_EXCEEDED`. |
| 저장 | 제공 곡의 게시 악보와 설정을 읽어 버전 1의 개인 정간보를 만든다. 제목 뒤에 ` (사본)`을 붙여 50자 이내로 제한한다. 서버가 작성 근거를 채우며, 음표·정간 ID·tick·악기·빠르기·장단을 보존한다. 장단 없는 곡의 `jangdanId`는 null이다. |
| 동시성 | 파일 불러오기와 같은 회원 행을 잠근 뒤 재전송과 한도를 검사한다. 인증 후 탈퇴가 먼저 처리됐다면 활성 상태를 잃은 회원의 새 사본은 404로 거절한다. 재전송은 현재 한도보다 먼저 처리한다. |

편집, 저장과 음원 생성은 복사한 악보의 ID로 기존 SHT-08·04·11·12·13을 호출한다. 복사 요청은 원본 게시 악보를 수정하지 않는다. 과거 SHT-03 `POST /api/users/me/sheets`는 405이며 빈 악보와 개인 악보의 복사 경로를 제공하지 않는다.


<a id="내-악보-검토-전-열람"></a>

## 내 악보 편집 진입과 저장

S15P31E102-725의 사용자 요청으로 698의 중간 열람 화면을 대체한다. 변환이 완료된 검토 대기 악보는 목록에서 바로 정간보 편집기로 연다. 저장된 초안이 있으면 이어서 편집한다. 편집 화면에 정간보·오선보 전환을 두지 않고 왼쪽에 빠르기와 편집 버튼을 표시한다.

검토를 마쳐 `EVALUABLE`인 악보는 목록에서 기존 연습 화면을 연다. 변환 진행·실패 상태와 저장·연습 API는 기존 계약을 따른다. 편집기의 ‘저장하기’는 페이지 이동 없이 제목·빠르기(BPM)·예상 음원 길이를 보여주는 대화상자를 연다. 예상 길이는 입력한 빠르기에 맞춰 즉시 갱신한다. 이 창의 ‘저장’으로 현재 내용을 확정하고, 서버 성공 응답을 받은 뒤 창을 닫는다. 실패하면 입력값과 오류를 유지한다.

확정 저장은 현재의 빈 정간·잘못된 음·미확인 항목·장단 불일치를 확인한다. 저장할 수 없는 이유와 수정할 위치·동작을 화면에 표시한다. 장단 불일치는 장단 변경 또는 명시적인 장단 해제로 해결하고 음표 길이와 배치를 자동 변경하지 않는다. 자동·임시 저장은 계속 사용할 수 있다. 변환 때의 전조·추정 장단·페이지 인식 안내 자체를 영구 저장 차단 조건으로 삼지 않는다. 실제 저장 결과는 서버 응답의 검토 상태를 따른다.

### 편집 도구와 수정할 정간 표시 (S15P31E102-736)

배포 앱 사용 후의 사용자 요청으로 725의 미리듣기 버튼과 연결 페이지를 제거한다. 편집 화면에는 빠르기·장단과 오른쪽 편집 버튼을 간격을 두고 표시한다. 편집 창은 제목 없이 빠르기·장단을 다루며, 적용 전 취소는 두 값을 보존한다. 제목은 저장 창에서 변경한다.

수정할 정간은 화면을 열 때부터 빨간 표시와 색상 외 식별 단서로 강조한다. 현재 오류 수정·인식 확인·실행 취소에 따라 표시를 다시 계산한다. 기본 화면에 검토 안내나 오류 알림 패널을 계속 띄우지 않는다. 저장을 시도하면 오류 정간을 선택해 보이게 하고, 저장·편집 창에서 이유와 확인·수정 동작을 제공한다. 오류가 해결되기 전 확정 저장은 계속 막는다.
