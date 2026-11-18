# 내 악보 음원 생성·재생 앱 흐름 (SHT-12·13 FE 반영)

## 앱 전체 완료 알림 (S15P31E102-905)

이 절은 아래 484 기록의 화면 한정 성공 안내를 대체한다. [905 계획·검증](../plans/S15P31E102-905.md).

표시 방식은 후속 사용자 요청의 [927·955 상단 완료 알림바](sheet-conversion-completion.md)가 대체한다. 음원도 상단 전용 알림바에서 잠깐 표시 후 자동으로 닫으며, 표시 중 열기·닫기를 제공한다. pause로 취소된 표시는 미처리 상태로 보존한다. 아래의 음원 완료 감지·이동 규칙은 유지한다.

- 기존 SHT-12 접수 또는 SHT-13 조회에서 QUEUED/RUNNING을 관찰한 작업을 현재 로그인 세대 안에서 추적한다. 앱이 활성 상태이면 화면을 옮겨도 조회를 이어간다.
- 같은 악보·악보 버전·악기·BPM·장단 설정의 DONE 전환은 공통 스낵바로 한 번만 알린다. 최초 DONE 조회, 기존 완료 파일 재사용, 반복 조회·화면 전환은 새 완료로 알리지 않는다. 서명 URL 변경은 새 작업이 아니다.
- 열기 버튼은 기기 저장 지원 앱에서 해당 내 악보 연습, 웹에서는 해당 악보 편집기로 이동한다. 현재 웹 편집기에는 음원 재생·저장 UI가 없어 웹 동작은 악보로 이동하는 범위다. 자동 재생·다운로드는 하지 않는다.
- FAILED·삭제/미존재·취소된 조회는 성공으로 알리지 않는다. 연속 조회 실패 5회이면 자동 추적을 멈추며, 해당 화면에서 상태를 다시 조회하면 진행 중 작업을 재등록한다. 실패 후 새 생성은 다시 추적한다.
- 로그아웃·계정 전환·같은 계정 재로그인 시 추적과 대기/표시 알림을 지우고 과거 알림 동작을 무효화한다. 새 계정에 결과를 넘기지 않는다.
- 알림·추적은 메모리에만 둔다. 앱 종료 시 사라진다. 백그라운드에서는 lifecycle이 멈추면 조회와 표시를 중단하고 복귀 시 잇는다. OS 푸시·알림 권한·알림 센터·서버 계약 변경은 포함하지 않는다.

S15P31E102-684부터 다운로드와 재생 동작은 [임시 M4A 다운로드와 기기 재생](sheet-audio.md#임시-m4a-다운로드와-기기-재생-s15p31e102-684)을 따릅니다. 아래는 484의 구현 기록입니다.

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
