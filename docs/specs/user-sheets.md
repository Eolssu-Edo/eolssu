# 내 악보 생성·조회·목록·편집 저장·삭제 API (SHT-01·02·03·04·08·11)

- Jira: [S15P31E102-390](https://ssafy.atlassian.net/browse/S15P31E102-390), Story [S15P31E102-319](https://ssafy.atlassian.net/browse/S15P31E102-319). 진행: [390 계획](../plans/S15P31E102-390.md). 편집 저장·자동 저장·삭제: [S15P31E102-413](https://ssafy.atlassian.net/browse/S15P31E102-413), [413 계획](../plans/S15P31E102-413.md).
- 근거: [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 SHT-01·03·08(2026-11-02 디자인 보정 반영본). 2026-11-02 회의 안건(PDF 방향, 초안 연습 진입, 삭제 시 연습 기록, 확인 필요 칸 복사)은 이 범위에서 정하지 않는다. 그중 PDF 방향·삭제 시 연습 기록은 2026-11-03 결정으로 정리됐다([2차 결정](#2차-결정-2026-11-03-미구현)).
- 상태: 백엔드 결정 초안. 아래 '노션 초안과 다른 점'은 FE(351)와 공유해야 한다.

## 범위

- 포함: `user_sheets` 저장 모델(V16), SHT-03 빈 악보·복사 생성, SHT-08 본인 악보 조회, SHT-01 본인 악보 목록, 회원 탈퇴 일괄 삭제에 내 악보 추가.
- 413에서 추가: V18 초안 칸, SHT-04 확정 저장, SHT-11 자동 저장, SHT-08 `events`·`draft`, SHT-01 `hasDraft`, SHT-02 삭제(아래 [편집 저장·자동 저장·삭제](#편집-저장자동-저장삭제-s15p31e102-413)).
- 제외: PDF·이미지·오선보 변환(SHT-07·10)과 확인 필요 칸, MIDI·M4A 다운로드(SHT-12·13), 악보 수 상한(미정). 2026-11-03에 범위가 정해졌으나 미구현이다([2차 결정](#2차-결정-2026-11-03-미구현)). 음원 변환(SHT-09)·서버 미리듣기(SHT-06)·서버 오선보(SHT-05)는 작업 취소됐다.

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
| `note_count`, `pending_cell_count` | NOTE 수, 확인 필요 칸 수(현재 항상 0). |
| `idempotency_key`, `request_hash` | SHT-03 중복 방지. 회원·키 유일. |
| `draft_json`, `draft_revision`, `draft_base_version`, `draft_saved_at` | V18. SHT-11 자동 저장 초안(`{title, jangdanId, bpm, score}`)과 순번·시작 버전·저장 시각. 확정 저장하면 NULL·0으로 돌아간다. |

## SHT-03 `POST /api/users/me/sheets`

헤더 `Idempotency-Key`(출력 가능한 ASCII 1~100자) 필수. 본문 타입이 명세와 다르면 자동 변환하지 않고 400.

| 필드 | 규칙 |
| --- | --- |
| `title` | 앞뒤 공백 제거 후 1~50자 |
| `startType` | `BLANK` / `COPY`. `PDF`는 400(2026-11-03부터 영구. PDF·이미지는 SHT-07로 만든다) |
| `jangdanId` | BLANK 필수. COPY는 생략 가능하며, 주면 원본 장단과 같아야 한다(다르거나 원본에 장단이 없으면 400) |
| `bpm` | 30~160 필수 |
| `columnCount` | BLANK 필수 1~40. COPY는 생략 가능하며, 주면 원본 각 수와 같아야 한다(다르면 400). 복사본은 40을 넘을 수 있다 |
| `copyFromSongId` / `copyFromSheetId` | COPY에서 정확히 하나. BLANK에서는 둘 다 없어야 한다 |
| `pdfSheetId` | 받지 않는다(값이 있으면 400) |

- BLANK: 악보 내용 없음, `NEEDS_REVIEW`, 각 수는 요청값.
- COPY 제공 곡: 게시·미삭제 곡만(그 외 404). 곡의 현재 `score_json`을 복사하고 `score.scoreVersion`만 1로 바꾼다. 장단·각 수(`columns` 길이)는 원본을 따른다. 원본과 연결하지 않는다. 곡이 채점 가능(`is_scorable`)이면 `EVALUABLE`, 아니면 `NEEDS_REVIEW`.
- COPY 내 악보: 본인 악보만(그 외 404). 내용·장단·각 수·상태를 그대로 복사한다. 지금은 정간보만 있어 확인 필요 칸이 없다. MIDI 등 확인 필요 칸이 생기면 회의 ④ 결과를 따른다.
- 없는 장단 404. 같은 키 재요청은 처음 만든 악보로 201, 같은 키에 다른 본문은 409 `IDEMPOTENCY_CONFLICT`.
- 복사 원본의 저장 악보가 CAT-04 검증(구조·음표 수·버전·단위)을 통과하지 못하면 409 `STATE_CONFLICT`(CAT-04 조회와 같은 처리).
- 응답 201 `result`: `sheetId`, `kind`, `status`, `scoreVersion`(문자열), `events`, `updatedAt`. `events`는 CAT-04 조회용 타임라인(`eventId`, `kind`, `noteIdx`, `pitchId`, 위치, tick·박)이며 빈 악보는 `[]`.

## SHT-08 `GET /api/users/me/sheets/{sheetId}/score`

200 `result`: `sheetId`, `title`, `kind`, `jangdanId`(장단 없는 곡 복사본은 null), `bpm`, `bpmUnit`, `columnCount`, `scoreVersion`(문자열), `reviewStatus`(`status`와 같은 값), `scoreData`(공통 악보 원본, 빈 악보는 null), `source`(null), `issues`(`[]`), `updatedAt`. 잘못된 ID 400, 없음·타인 404.

## SHT-01 `GET /api/users/me/sheets`

- 쿼리: `kind`(`JEONGGANBO`/`MIDI`/`PDF`, 그 외 400), `page`(0 이상, 기본 0), `size`(1~100, 기본 20).
- `result.items[]`: `sheetId`, `kind`, `title`, `status`, `jangdanId`·`jangdanName`(장단 없는 곡 복사본은 둘 다 null), `source`, `pendingCellCount`, `sourcePageCount`(null), `updatedAt`. `page`, `size`, `totalElements`, `totalPages`도 반환한다.
- 본인 악보만, `updatedAt` 내림차순·`sheetId` 내림차순. 없으면 빈 배열.

## 노션 초안과 다른 점

- SHT-03: COPY의 `jangdanId`·`columnCount`를 생략 가능하게 했다(원본 값을 따름). `startType=PDF`·`pdfSheetId`는 400(2026-11-03에 노션도 같은 규칙으로 정리).
- SHT-03 상태: 노션은 "복사본은 EVALUABLE"이지만, 채점 불가 곡(`is_scorable=false`)의 복사본은 `NEEDS_REVIEW`이고 내 악보 복사본은 원본 상태를 따른다(빈 악보 복사본은 `NEEDS_REVIEW`).
- SHT-03 오류: 노션에 없는 409 `STATE_CONFLICT`(복사 원본 악보 검증 실패)를 더했다.
- SHT-08: `scoreData`는 빈 악보에서 null이고, `jangdanId`는 장단 없는 곡 복사본에서 null이다. 편집 화면에 필요한 `title`·`jangdanId`·`bpm`·`bpmUnit`·`columnCount`를 더했다. `source`는 null, `issues`는 빈 배열이다.
- SHT-01: `source`는 지금 `MANUAL`만, `sourcePageCount`는 null이다. 장단 없는 곡 복사본은 `jangdanId`·`jangdanName`이 null이다.

## 편집 저장·자동 저장·삭제 (S15P31E102-413)

요청·응답 필드는 Notion SHT-02·04·08·11을 따른다. 구현에서 정한 점과 Notion과 다른 점:

- SHT-04 `PUT /api/users/me/sheets/{sheetId}/score`: `baseScoreVersion`(문자열)이 현재 버전과 같을 때만 저장하고 버전을 1 올린다(DB `WHERE score_version = ?`로도 막는다). 미입력 칸은 400 `UNFILLED_CELLS`(`details.cellIds`, ID 없는 빈 칸은 400 `INVALID_REQUEST`). 요청 본문이 1MiB를 넘으면 JSON을 읽기 전에 413 `PAYLOAD_TOO_LARGE`(SHT-11도 같다, 받은 바이트 수 기준, Content-Length 없는 본문·인코딩한 경로 포함). `resolvedIssueIds: null`은 생략과 같다. `jangdanId`를 주면 `score.music.jangdan`(장단 이름)·`bpmUnit`이 그 장단과 같아야 한다. 검증·타임라인은 CAT-04 디코더를 쓴다. 성공하면 초안을 지우고 `draft_revision`을 0으로 되돌린다.
- 서버는 `score.scoreVersion`만 바꾸고 `provenance`는 받은 값을 그대로 저장한다. 사용자 악보용 값이 DATA와 정해지지 않아서다(Notion '서버 값으로 덮어쓴다'와 다름).
- 확인 필요 칸은 변환 기능과 함께 생긴다. 지금은 `resolvedIssueIds`에 값이 있으면 400이고, 저장 결과는 항상 `EVALUABLE`·`pendingIssueCount` 0이다.
- SHT-11 `PUT .../score/draft`: 버전과 `draftRevision`이 서버와 같을 때만 덮어쓰고 순번을 1 올린다. 칸 안은 검증하지 않고 최상위 모양만 본다. `updated_at`(목록 정렬)은 바꾸지 않는다.
- SHT-08: `events`(저장 악보의 CAT-04 타임라인, 빈 악보는 `[]`)와 `draft`를 더했다. `draft`에는 Notion 필드(`score`·`baseScoreVersion`·`draftRevision`·`savedAt`)에 더해 초안의 `title`·`jangdanId`·`bpm`이 들어 있다(편집 화면 복원용). 저장 악보가 CAT-04 검증을 통과하지 못하면 409.
- SHT-01: `hasDraft`를 더했다. `conversionStatus`·`source` 확장은 변환 기능에서 한다.
- SHT-02 `DELETE /api/users/me/sheets/{sheetId}`: 204 빈 본문, 없거나 다른 회원·이미 지운 악보는 404. 초안은 같은 행이라 함께 지워진다. 원본 파일·다운로드 링크는 아직 없다.

## 탈퇴

탈퇴 일괄 삭제의 회원 행 삭제(3단계)에 `user_sheets`를 더한다.

## 2차 결정 (2026-11-03, 미구현)

사용자 결정(S15P31E102-382). 요청·응답·오류의 상세는 [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 SHT-01~13을 따른다. 아래는 이 문서의 현재 구현과 달라지는 점이다.

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
- SHT-12·13: MIDI·M4A 다운로드 링크(10분, 여러 번 사용, 쿠키 없이 토큰). 파일은 요청마다 공통 악보 음원 생성기로 만들고 서버에 저장하지 않는다.
- 작업 취소: SHT-05(오선보 보기는 앱이 공통 악보로 그림), SHT-06(미리듣기는 앱이 재생), SHT-09(음원 변환).
- 저장 모델에 필요한 것(새 Flyway): `user_sheets`의 `source` CHECK 확장·초안 칸(`draft_json` 등), `sheet_source_files`, `sheet_conversions`, `sheet_issues`, `sheet_export_links`. 탈퇴 일괄 삭제와 R2 원본 삭제도 함께 넓힌다.
- 구현 전 정할 것: 변환·합성 작업자 위치와 처리 시간 상한, 사용자 악보의 `provenance` 값(DATA).
