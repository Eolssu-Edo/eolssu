# 내 악보 생성·조회·목록 API (SHT-01·03·08)

- Jira: [S15P31E102-390](https://ssafy.atlassian.net/browse/S15P31E102-390), Story [S15P31E102-319](https://ssafy.atlassian.net/browse/S15P31E102-319). 진행: [390 계획](../plans/S15P31E102-390.md).
- 근거: [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 SHT-01·03·08(2026-11-02 디자인 보정 반영본). 2026-11-02 회의 안건(PDF 방향, 초안 연습 진입, 삭제 시 연습 기록, 확인 필요 칸 복사)은 이 범위에서 정하지 않는다.
- 상태: 백엔드 결정 초안. 아래 '노션 초안과 다른 점'은 FE(351)와 공유해야 한다.

## 범위

- 포함: `user_sheets` 저장 모델(V16), SHT-03 빈 악보·복사 생성, SHT-08 본인 악보 조회, SHT-01 본인 악보 목록, 회원 탈퇴 일괄 삭제에 내 악보 추가.
- 제외: 편집 저장(SHT-04, 이벤트 형식 정합화 필요), 삭제(SHT-02, 회의 ③), PDF 시작·업로드(회의 ①), 음원 변환·미리듣기(SHT-06·09·10, AI·DATA 대기), 악보 수 상한(미정).

## 공통

- `access_token` 쿠키 인증, 공통 envelope, 오류는 `result.errorCode`. 경로·응답 ID는 양의 Long 십진 문자열, 시각은 UTC ISO8601.
- 다른 회원의 악보는 없는 악보와 같게 404 `RESOURCE_NOT_FOUND`다.

## 저장 모델

`user_sheets` 한 행이 악보 하나다. 악보 내용은 제공 곡과 같은 공통 악보 v1 JSON(`score_json`)이며, 아직 한 번도 저장하지 않은 빈 악보는 NULL이다. 빈 칸 표현을 공통 악보에 새로 정하지 않기 위해서다(SHT-04에서 정한다).

| 칸 | 의미 |
| --- | --- |
| `kind` | `JEONGGANBO`만 만든다. MIDI·PDF는 해당 기능에서 CHECK를 넓힌다. |
| `source` | 만든 방식. 직접 작성·복사는 모두 `MANUAL`. |
| `status` | `NEEDS_REVIEW`(평가 불가) / `EVALUABLE`(평가 가능). 변환 상태는 SHT-10에서 추가한다. |
| `jangdan_id`, `bpm`, `bpm_unit`, `column_count` | 편집 화면 설정. `bpm_unit`은 만들 때의 장단 단위이며, 장단 없는 곡(V15에서 `songs.jangdan_id`가 nullable)을 복사하면 `jangdan_id`는 NULL, 단위는 원본 악보의 `music.bpmUnit`이다(CAT-04와 같다). |
| `score_version` | 1부터. JSON의 `score.scoreVersion`과 같게 유지한다. |
| `note_count`, `pending_cell_count` | NOTE 수, 확인 필요 칸 수(현재 항상 0). |
| `idempotency_key`, `request_hash` | SHT-03 중복 방지. 회원·키 유일. |

## SHT-03 `POST /api/users/me/sheets`

헤더 `Idempotency-Key`(출력 가능한 ASCII 1~100자) 필수. 본문 타입이 명세와 다르면 자동 변환하지 않고 400.

| 필드 | 규칙 |
| --- | --- |
| `title` | 앞뒤 공백 제거 후 1~50자 |
| `startType` | `BLANK` / `COPY`. `PDF`는 회의 ① 전까지 400 |
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

- SHT-03: COPY의 `jangdanId`·`columnCount`를 생략 가능하게 했다(원본 값을 따름). `startType=PDF`·`pdfSheetId`는 회의 전까지 400.
- SHT-03 상태: 노션은 "복사본은 EVALUABLE"이지만, 채점 불가 곡(`is_scorable=false`)의 복사본은 `NEEDS_REVIEW`이고 내 악보 복사본은 원본 상태를 따른다(빈 악보 복사본은 `NEEDS_REVIEW`).
- SHT-03 오류: 노션에 없는 409 `STATE_CONFLICT`(복사 원본 악보 검증 실패)를 더했다.
- SHT-08: `scoreData`는 빈 악보에서 null이고, `jangdanId`는 장단 없는 곡 복사본에서 null이다. 편집 화면에 필요한 `title`·`jangdanId`·`bpm`·`bpmUnit`·`columnCount`를 더했다. `source`는 null, `issues`는 빈 배열이다.
- SHT-01: `source`는 지금 `MANUAL`만, `sourcePageCount`는 null이다. 장단 없는 곡 복사본은 `jangdanId`·`jangdanName`이 null이다.

## 탈퇴

탈퇴 일괄 삭제의 회원 행 삭제(3단계)에 `user_sheets`를 더한다.
