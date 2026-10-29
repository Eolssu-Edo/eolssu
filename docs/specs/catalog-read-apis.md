# 악기·악보·모범 음원·학습 카탈로그 조회 (S15P31E102-288)

상태: 박찬형 담당 CAT-03~10의 로컬 구현 계약. 서버 배포·운영 콘텐츠 승인과 구분한다. 근거는 사용자의 2026-10-29 구현 요청, [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 담당자·상세 페이지, 현재 DB 및 [공통 악보 스키마](../../data/src/scores/score_data.schema.json)다. Notion v0.1의 예시값·논의 필요 항목을 음악적 확정값으로 취급하지 않는다.

모든 경로는 `GET /api` 아래이며 기존 공개 접근 규칙을 사용한다. 성공은 `{httpStatus,isSuccess,message,code,result}`, 오류는 `result.errorCode`로 분기한다. DB ID는 양의 Long 범위 십진 문자열이다. 잘못된 ID는 400 `INVALID_REQUEST`, 존재하지 않거나 노출할 수 없는 리소스는 404 `RESOURCE_NOT_FOUND`다. 숫자 ID가 큰 경우에도 JSON number로 내보내지 않는다. 목록의 빈 배열은 정상 200이다.

| ID | 경로·필터 | 결과 및 공개 조건 |
| --- | --- | --- |
| CAT-03 | `/instruments` | 활성 악기 목록, `instrumentId/code/name/pitchToleranceCent/rangeLowPitchId/rangeHighPitchId`. 기존 등록값만 반환한다. |
| CAT-04 | `/songs/{songId}/score` | 게시·미삭제 곡의 원본 공통 악보와 조회용 타임라인. 세부 구조는 아래 참조. |
| CAT-05 | `/songs/{songId}/model-audio` | 현재 악보 버전·곡 BPM·장단 bpmUnit과 일치하는 게시 음원 메타데이터. 미등록/초안/버전 불일치는 404. |
| CAT-06 | `/pitches?instrumentId=...` | 생략 시 등록 율명, 지정 시 활성 악기의 음역. 음역은 양 끝 pitch의 semitone으로 비교하며 DB ID 순서를 높낮이로 해석하지 않는다. |
| CAT-07 | `/instruments/{instrumentId}/fingerings?pitchId=...` | 해당 활성 악기의 등록 운지, 선택한 음의 운지. 없는 콘텐츠는 빈 목록이다. |
| CAT-08 | `/jangdans/{jangdanId}` | 장단 이름·defaultBpm·bpmUnit·원본 pattern(beats/group)·source·soundUrl. |
| CAT-09 | `/notation-symbols` | symbolId/symbol/name/description/imageUrl. 학습용 설명이며 악보 이벤트의 의미를 재정의하지 않는다. |
| CAT-10 | `/jangdans` | jangdanId/name/defaultBpm/bpmUnit/beatCount/groupCount. 소박·묶음 수는 저장 pattern에서 구한다. |

## 원본 악보와 초안의 차이

CAT-04의 `score`는 CAT-02에서 반환하는 원본 공통 악보와 같은 객체다. `columns`, 이벤트의 `id/cellId/startTick/durationTick/breathAfter/sigimsae`, 조율·장단·틱 기준을 보존한다. `schemaVersion`, `scoreVersion`은 현행 데이터 계약의 정수다. 오래된 Notion의 `"v1"`, `"1"` 문자열 예시로 변환하지 않는다. 기존 CAT-01/02 응답은 변경하지 않는다.

조회용 `events`의 NOTE `noteIdx`는 곡 전체 NOTE만 0부터 센 순서다. REST의 noteIdx/pitchId는 null이다. `pitchId`는 실제 pitches의 (yul,octave)에서 찾고, 박 값은 원본 정수 tick / ticksPerBeat로 투영한다. 원본 ID와 tick을 함께 제공해 박 소수의 반올림을 정본으로 사용하지 않도록 한다. 악보 위치는 원본 columns/cells/units와 연결하며, 이음·빈 정간을 새 NOTE로 만들지 않는다. 저장 악보의 구조·음표 수·버전·참조가 서로 맞지 않으면 409 `STATE_CONFLICT`로 거절한다.

`subIdx`는 시작 정간에서 units를 row·col 오름차순으로 펼친 위치다. 실제 화면 배치는 원본 units의 row·col을 사용한다. `notationCapabilities.hanja/hangul`은 사용된 음의 등록 표기가 모두 있을 때 true다. `solfege=false`는 이 서버 API가 검증된 계이름 표기를 제공하지 않는다는 뜻이며 앱의 별도 표시 기능을 삭제하는 지시가 아니다.

## 모범 연주 메타데이터

V8 `song_model_audios`는 `(song_id,score_version)`마다 기준 음원 한 개를 저장한다. 초기 상태는 DRAFT이며 이번 마이그레이션은 음원 행을 넣지 않는다. 게시·미삭제 곡의 현재 악보 버전, 곡 defaultBpm, 장단 bpmUnit과 일치하는 PUBLISHED 행만 CAT-05에 노출한다. 이전 버전 행은 현재 곡에 재사용하지 않는다.

응답은 `songId`, 정수 `scoreVersion`, `audioUrl`, `bpm`, `bpmUnit`, `durationMs`, `startOffsetMs`, `contentType`, `sha256`이다. Notion 초안의 필드에 버전·시작 위치·해시를 더해 파일과 악보를 구분한다. `durationMs`는 실제 인코딩된 파일 길이이며 잔향이 포함될 수 있다. 악보 총길이로 줄이거나 늘리지 않는다. `startOffsetMs`는 파일에서 악보 0박이 시작하는 위치이며 적재자가 측정해 명시한다. 기본값으로 추정하지 않는다. BPM은 songs 저장 계약(30~400)을 따르며 연습 녹음 세션의 별도 20~240 제한으로 축소하지 않는다.

HTTPS URL, 양의 길이, 범위 안 시작 위치, 지원 MIME(`audio/mp4`, `audio/mpeg`, `audio/ogg`, `audio/wav`), 소문자 SHA-256 64자만 반환한다. URL에 사용자 인증정보나 fragment를 넣지 않는다. 이 GET은 파일을 서버에서 내려받거나 합성하거나 업로드하지 않는다. 실제 파일 유효성·권리·URL 수명은 적재 전에 검증한다. 비밀번호·스토리지 키·일회성 인증 URL을 데이터에 넣지 않는다.

운영 적재 순서: 파일과 원본/악보 버전 연결·권리 확인 → 실제 디코딩/해시/길이/시작 위치 검사 → 운영 저장소에 파일 배치 → HTTPS 재생 확인 → 메타데이터 DRAFT 적재 → 담당자 검수 후 PUBLISHED. 이번 작업은 로컬 저장 구조/API까지이며 배포·운영 적재를 수행하지 않는다. 기존 단소 4곡 M4A는 기술 후보이고 자동 게시하지 않는다.

## 미확보 데이터와 상태 구분

- 현재 기준 시드는 단소 1악기·11율명·3장단·4곡이다. 다른 악기의 JGB 자료 보유가 곧 앱 DB 등록·권리 승인·채점 가능을 뜻하지 않는다.
- 율명 `description/soundUrl`, 운지 `structureImageUrl`, 장단 `soundUrl`은 저장 근거가 없어 null이다. 운지 description은 기존 DB에서 nullable이므로 Notion의 필수 STRING 초안과 달리 null을 보존한다. 가짜 문구나 URL을 채우지 않는다.
- 운지·기호의 운영 시드는 없다. 테스트 fixture의 이미지/설명은 운영 콘텐츠가 아니다. `pitchToleranceCent`는 DB 값이며 별도 AI 채점 정책을 바꾸지 않는다.
- 악기·운지·기호·장단 목록은 ID 오름차순, 율명은 semitone·ID 오름차순이다. 이는 일관된 기술적 순서이며 학습 추천 순서가 아니다. 장단의 pattern과 박 단위는 그대로 유지한다.
- 단위/DB/HTTP 통과는 운영 재생, 마이크 녹음, Android 오디오 포커스, 실기기 검증을 대신하지 않는다.
