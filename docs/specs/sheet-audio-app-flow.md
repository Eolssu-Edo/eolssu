# 내 악보 음원 생성·재생 앱 흐름 (SHT-12·13 FE 반영)

- Jira: [S15P31E102-484](https://ssafy.atlassian.net/browse/S15P31E102-484), Story [S15P31E102-319](https://ssafy.atlassian.net/browse/S15P31E102-319). 진행: [484 계획](../plans/S15P31E102-484.md). 서버 구현: [S15P31E102-481](https://ssafy.atlassian.net/browse/S15P31E102-481)(미구현). 편집·저장 전체 연결: [S15P31E102-351](https://ssafy.atlassian.net/browse/S15P31E102-351).
- 근거: [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 SHT-12·SHT-13(2026-11-04 개편본, 2026-11-04 브라우저로 읽음). 이전 설계(10분 다운로드 링크, 쿠키 없는 토큰, 서버 비저장, `sheet_export_links`)는 더 이상 쓰지 않는다. [내 악보 스펙](user-sheets.md)의 '2차 결정' SHT-12·13 줄은 이전 초안이며 백엔드 481에서 고친다.
- 상태: FE 구현 기준. 서버가 없어 실호출·기기 검증은 하지 않았다.

## 범위

- 포함: `data/song` 저장소·DTO(SHT-12 POST, SHT-13 GET), 편집 미리보기(EDIT-04)의 음원 상태·동작·문구, 폴링·만료 재조회·앱 안 알림(스낵바).
- 제외: 악보 저장(SHT-04·11) 연결, SHT-10 변환 알림, 내 악보 목록에서 음원 재생, 기기 파일 저장소 관리(sha256 대조), 알림 센터.

## 서버 계약 요약

| 호출 | 성공 | 오류(`result.errorCode`) |
| --- | --- | --- |
| `POST /api/users/me/sheets/{sheetId}/audio` 본문 `{instrumentId?, withJangdan?, bpm?}` | 202 접수(`status` QUEUED) / 200 같은 버전·설정의 DONE 음원. `result`는 SHT-13과 같은 모양 | 400 `INVALID_REQUEST`·`EXPORT_TOO_LONG`(600초 초과), 404 `RESOURCE_NOT_FOUND`, 409 `STATE_CONFLICT`(저장 전·빈 칸·변환 중), 409 `AUDIO_IN_PROGRESS`(회원당 생성 중 1건), 429 `TOO_MANY_REQUESTS`, 503 `AUDIO_UNAVAILABLE` |
| `GET /api/users/me/sheets/{sheetId}/audio` | 200 `status`(QUEUED/RUNNING/DONE/FAILED), `scoreVersion`, `currentScoreVersion`, `outdated`, `instrumentId`, `withJangdan`, `bpm`, `failureCode`(FAILED: TIMEOUT/TOO_LARGE/SERVER_ERROR), `pollAfterMs`(QUEUED·RUNNING), `audio`·`midi`(`url`, `urlExpiresAt`, `contentType`, `byteSize`, `fileName`, audio는 `sha256`·`durationMs`) | 404 `RESOURCE_NOT_FOUND`(악보 없음 또는 음원을 요청한 적 없음), 503 `AUDIO_UNAVAILABLE` |

- 새 생성이 진행·실패 중이어도 이전 파일이 있으면 `audio`·`midi`에 남는다.
- 서명 URL은 15분 유효. 만료되면 GET을 다시 불러 새 URL을 받는다. URL은 로그에 남기지 않는다.
- 악보 버전이 오르면 `outdated: true`. 다시 만들면 DONE 뒤 이전 파일을 지운다.

## 앱 동작

- 상태(미리보기 화면 `SheetAudioUi`): 없음 → 요청 중 → 만드는 중(QUEUED·RUNNING) → 준비됨(DONE) / 실패(FAILED). 준비됨·실패에는 서버가 돌려준 `SheetAudio`(이전 파일 포함)를 함께 둔다.
- '음원 만들기'(저장 악보가 있을 때): POST에 `withJangdan`(장단 소리 함께 체크), `bpm`(악보 bpm), `instrumentId`(회원이 고른 악기, 없으면 생략)를 보낸다. 202·200 모두 응답 `result`를 상태로 받고, QUEUED·RUNNING이면 `pollAfterMs`(없으면 3초) 간격으로 GET을 반복한다.
- 끝나면 앱 안 알림(스낵바): DONE '음원이 준비됐어요', FAILED '음원을 만들지 못했어요'. 편집기 화면에 있어도 보인다.
- 준비됨: '재생'(M4A, 화면 플레이어), 'M4A 저장'·'MIDI 저장'(서명 URL을 기기 브라우저로 연다). 누를 때 URL 만료 30초 전이면 GET으로 새 URL을 받은 뒤 쓴다.
- `outdated`: '악보가 바뀌었어요 · 다시 만들기'. 다시 만드는 동안 이전 파일은 계속 재생·저장할 수 있다.
- 실패: 사유 문구와 '다시 시도'(POST 재요청).
- 오류 안내: 400 EXPORT_TOO_LONG '연주 길이가 10분을 넘어요', 409 AUDIO_IN_PROGRESS '만들고 있는 음원이 끝난 뒤 다시', 409 STATE_CONFLICT '악보를 저장한 뒤', 404 '악보를 찾지 못했어요', 429·503·응답 없음 '잠시 후 다시'.
- 폴링 중 응답을 5번 연속 받지 못하면 멈추고 '상태를 확인하지 못했어요' 안내와 '다시 확인'을 보인다.
- 화면에 들어올 때(내 악보 편집) GET으로 현재 음원 상태를 읽고, 진행 중이면 폴링을 잇는다. 404는 '없음'이다.
- 서버 ID가 없는 예시 악보(저장 API 연결 전)와 저장한 적 없는 악보: 기존처럼 '저장한 악보가 있어야' / '준비 중' 안내만 띄운다.

## 수용 조건

- SHT-12·13 응답을 MockEngine으로 넣었을 때 상태·파일·만료 시각·오류 분기가 위 표대로 나뉜다. http URL·모르는 상태는 '사용 불가'로 거절한다.
- 상태 전이·폴링·만료 재조회·outdated 재생성·실패 재시도를 자동 검사로 확인한다.
- `:shared:testAndroidHostTest` 통과. Android·iOS 실기기·실서버 검증은 BE 481 뒤 별도.
