# 악기·악보·모범 음원·학습 카탈로그 조회 (S15P31E102-288)

상태: 박찬형 담당 CAT-03~10의 로컬 구현 계약. 서버 배포·운영 콘텐츠 승인과 구분한다. 근거는 사용자의 2026-10-29 구현 요청, [Notion API 명세](https://app.notion.com/p/API-3fea0252aa45804cb9f0f896ec0310ad)의 담당자·상세 페이지, 현재 DB 및 [공통 악보 스키마](../../data/src/scores/score_data.schema.json)다. Notion v0.1의 예시값·논의 필요 항목을 음악적 확정값으로 취급하지 않는다.

모든 경로는 `GET /api` 아래이며 기존 공개 접근 규칙을 사용한다. 성공은 `{httpStatus,isSuccess,message,code,result}`, 오류는 `result.errorCode`로 분기한다. DB ID는 양의 Long 범위 십진 문자열이다. 잘못된 ID는 400 `INVALID_REQUEST`, 존재하지 않거나 노출할 수 없는 리소스는 404 `RESOURCE_NOT_FOUND`다. 숫자 ID가 큰 경우에도 JSON number로 내보내지 않는다. 목록의 빈 배열은 정상 200이다.

2026-11-03 사용자 후속 요청의 [4악기 계약](four-instrument-catalog.md)은 V17에서 CAT-03·06을 네 악기에 공개하고 회원 선택도 정악·산조를 나누어4종으로 통일한다. 새 DB의 전역 율명36개, 단소26·소금24·두 대금 각34개이며 아래22/15개는 V13 직후의 이전 기준이다. 기존 ID·반음·표기는 보존한다. 실제 운영 배포 여부와 구분한다.

| ID | 경로·필터 | 결과 및 공개 조건 |
| --- | --- | --- |
| CAT-03 | `/instruments` | 활성 악기 목록, `instrumentId/code/name/pitchToleranceCent/rangeLowPitchId/rangeHighPitchId`. 기존 등록값만 반환한다. |
| CAT-04 | `/songs/{songId}/score` | 게시·미삭제 곡의 원본 공통 악보와 조회용 타임라인. 세부 구조는 아래 참조. |
| CAT-05 | `/songs/{songId}/model-audio` | 현재 악보 버전·곡 BPM·기준 bpmUnit과 일치하는 게시 음원 메타데이터. 미등록/초안/버전 불일치는 404. |
| CAT-06 | `/pitches?instrumentId=...` | 생략 시 등록 율명, 지정 시 활성 악기의 음역. 음역은 양 끝 pitch의 semitone으로 비교하며 DB ID 순서를 높낮이로 해석하지 않는다. |
| CAT-07 | `/instruments/{instrumentId}/fingerings?pitchId=...` | 해당 활성 악기의 등록 운지, 선택한 음의 운지. 없는 콘텐츠는 빈 목록이다. |
| CAT-08 | `/jangdans/{jangdanId}` | 장단 이름·defaultBpm·bpmUnit·원본 pattern(beats/group)·source·soundUrl. |
| CAT-09 | `/notation-symbols` | symbolId/symbol/name/description/imageUrl. 학습용 설명이며 악보 이벤트의 의미를 재정의하지 않는다. |
| CAT-10 | `/jangdans` | jangdanId/name/defaultBpm/bpmUnit/beatCount/groupCount. 소박·묶음 수는 저장 pattern에서 구한다. |

## 내장 운지 자료

S15P31E102-409의 [내장 운지 계약](builtin-fingering-catalog.md)은 V18에서 CAT-07에 4악기 92개 참고 운지를 등록한다. 항목의 nullable `pattern`에 취법·원본 표기·구멍 상태·출처·명목상 황종을 추가하며, 이미지가 없으면 `imageUrl`은 null이다. 기존 이미지형 자료는 그대로 반환하고 pattern은 null일 수 있다. 단소·소금·정악·산조의 행 수는 12·20·30·30이며, 산조는 정악 기본틀 공유와 유파별 차이를 원본 설명에 남긴다. 서버 `pitchId`는 전역 (yul,octave) 기준이며 실제 조율·채점 승인과 다르다.

## 원본 악보와 초안의 차이

S15P31E102-408은 CAT-04·05의 선행 곡 선택을 위해 `GET /songs?instrumentId=...`를 추가한다. 생략 시 기존 전체 조회, 지정 시 해당 활성 악기의 게시·미삭제 곡만 반환한다. 잘못된 ID는400, 없는/비활성 악기는404, 활성 악기의 빈 목록은200이다. [FE 선택·검증 범위](song-instrument-filter.md)를 따르며 기존 songId 기반 상세·악보·음원 계약은 보존한다.

CAT-04의 `score`는 CAT-02에서 반환하는 원본 공통 악보와 같은 객체다. `columns`, 이벤트의 `id/cellId/startTick/durationTick/breathAfter/sigimsae`, 조율·장단·틱 기준을 보존한다. `schemaVersion`, `scoreVersion`은 현행 데이터 계약의 정수다. 오래된 Notion의 `"v1"`, `"1"` 문자열 예시로 변환하지 않는다. S15P31E102-419부터 CAT-01 목록 항목은 `score`를 싣지 않는다(111곡 기준 약 1.76MB 중 약 99%가 악보 본문). 원본 악보는 CAT-02 상세와 CAT-04에서만 반환하며 CAT-02 응답과 나머지 목록 필드는 유지한다.

조회용 `events`의 NOTE `noteIdx`는 곡 전체 NOTE만 0부터 센 순서다. REST의 noteIdx/pitchId는 null이다. `pitchId`는 실제 pitches의 (yul,octave)에서 찾고, 박 값은 원본 정수 tick / ticksPerBeat로 투영한다. 원본 ID와 tick을 함께 제공해 박 소수의 반올림을 정본으로 사용하지 않도록 한다. 악보 위치는 원본 columns/cells/units와 연결하며, 이음·빈 정간을 새 NOTE로 만들지 않는다. 저장 악보의 구조·음표 수·버전·참조가 서로 맞지 않으면 409 `STATE_CONFLICT`로 거절한다.

`subIdx`는 시작 정간에서 units를 row·col 오름차순으로 펼친 위치다. 실제 화면 배치는 원본 units의 row·col을 사용한다. `notationCapabilities.hanja/hangul`은 사용된 음의 등록 표기가 모두 있을 때 true다. `solfege=false`는 이 서버 API가 검증된 계이름 표기를 제공하지 않는다는 뜻이며 앱의 별도 표시 기능을 삭제하는 지시가 아니다.

## 모범 연주 메타데이터

V8 `song_model_audios`는 `(song_id,score_version)`마다 기준 음원 한 개를 저장한다. 초기 상태는 DRAFT이며 이번 마이그레이션은 음원 행을 넣지 않는다. 게시·미삭제 곡의 현재 악보 버전, 곡 defaultBpm, 장단 bpmUnit과 일치하는 PUBLISHED 행만 CAT-05에 노출한다. 이전 버전 행은 현재 곡에 재사용하지 않는다.

S15P31E102-339는 장단 연결을 선택값으로 확장한다. 장단이 연결돼 있으면 기존 장단의 `bpmUnit`과 공통 악보의 일치 검증을 유지한다. 연결이 없으면 공통 악보 `music.bpmUnit`을 기준으로 사용하며 허용된 대문자 단위만 받는다. 원본 장단명만으로 장단 pattern을 추정하지 않는다. [검수대기 적재 계약](model-audio-catalog-load.md)은 기존 공개 곡과 새 DRAFT 자료를 구분한다.

응답은 `songId`, 정수 `scoreVersion`, `audioUrl`, `bpm`, `bpmUnit`, `durationMs`, `startOffsetMs`, `contentType`, `sha256`과 선택적 `performanceUrl`, `sourceCommonSha256`이다. `durationMs`는 실제 인코딩된 파일 길이이며 잔향이 포함될 수 있다. 악보 총길이로 줄이거나 늘리지 않는다. `startOffsetMs`는 파일에서 악보 0박이 시작하는 위치이며 적재자가 측정해 명시한다. 기본값으로 추정하지 않는다. BPM은 songs 저장 계약(30~400)을 따르며 연습 녹음 세션의 별도 20~240 제한으로 축소하지 않는다.

S15P31E102-339에서 DB의 위치 식별자는 `object_key`다. `audioUrl`은 같은 서버의 `/api/songs/{songId}/model-audio/play` 경로이며 앱은 설정된 API 서버 주소를 기준으로 해석한다. 이 경로는 게시 상태·판본을 다시 확인하고 비공개 객체에 대한 짧은 GET 서명 URL로302 이동한다. `Cache-Control: no-store`이며 DB나 장기 캐시에 만료 URL을 저장하지 않는다. 새 요청은 DRAFT/HIDDEN 또는 버전 불일치에서404, 저장소 준비/읽기 실패에서503이다.

수행 시간표가 있는 경우 `/api/songs/{songId}/model-audio/performance`는 같은 서버에서 크기·시간 제한 아래 원본 JSON을 제공한다. `sourceCommonSha256`은 그 수행 시간표의 정본 공통 JSON 원문 바이트 해시다. CAT-04 응답을 다시 직렬화한 해시로 대체하지 않는다. FE는 곡·판본·공통 원문 해시·음원 해시·길이·시작 위치를 대조해 연결한다. 제공된 수행 자료가 잘못됐을 때 정본 시간표로 조용히 대체하지 않는다. 수행 자료가 아예 없는 기존 계약에서는 정본 시간표를 사용할 수 있다.

운영 적재 순서와 상태는 [339 적재·재생 계약](model-audio-catalog-load.md)을 따른다. 현재107개는 DRAFT 적재까지 완료했고, 사용자 후속 결정에 따라 전문가 청음 승인 없이 먼저 사용할 수 있도록 연결한다. 기존 공개4곡은 덮어쓰지 않는다. 실제 공개 전환에는 새 BE와 NULL 장단을 받는 Android 앱의 배포·재생 확인이 필요하다. Web/iOS의 기존 플랫폼 플레이어 미지원은 이 계약만으로 해결되지 않는다. 곡을 숨기면 새 재생 요청은404이지만 이미 발급된 서명 URL은 최대15분 유효할 수 있다.

## 미확보 데이터와 상태 구분

- S15P31E102-411: V19는 기존 장단1~3을 보존하며 출처가 확인된 자진모리4·중중모리5를 추가한다. 두 기본형은 DOTTED_QUARTER·12칸·4묶음이며 시작 BPM은 각각96·80이다. QUARTER인 서비스 곡에는 이름만으로 연결하지 않는다. [추가 근거와 보류5종](../knowledge/backend/jangdan-catalog-extension.md)을 따른다. V19 적용 후 장단 카탈로그는5개이며 아래3개는 기존 시드의 범위다.

- 현재 기준 시드는 단소1악기·전역22율명·3장단·4곡이다. S15P31E102-364의 최종 V13은 기본음 라벨에 필요한 기준11행을 추가하며 기존11개 ID/반음은 보존한다. 사용자 선택에 따라 기존 음역 필터를 유지하므로 단소 범위 `[5,29]`에는15음이 표시된다. 별도 운지표·악기별 허용 목록을 자동 등록하지 않는다. 다른 악기의 JGB 자료 보유가 곧 앱 DB 등록·권리 승인·채점 가능을 뜻하지 않는다.
- 율명 `description/soundUrl`, 운지 `structureImageUrl`, 장단 `soundUrl`은 저장 근거가 없어 null이다. 운지 description은 기존 DB에서 nullable이므로 Notion의 필수 STRING 초안과 달리 null을 보존한다. 가짜 문구나 URL을 채우지 않는다.
- V18 전에는 운지 시드가 없었다. V18의 앱 내장 참고 자료 등록과 실제 운영 적용 여부는 구분한다. 기호는 이 Task에서 변경하지 않는다. 테스트 fixture의 이미지/설명은 운영 콘텐츠가 아니다. `pitchToleranceCent`는 DB 값이며 별도 AI 채점 정책을 바꾸지 않는다.
- 악기·운지·기호·장단 목록은 ID 오름차순, 율명은 semitone·ID 오름차순이다. 이는 일관된 기술적 순서이며 학습 추천 순서가 아니다. 장단의 pattern과 박 단위는 그대로 유지한다.
- 단위/DB/HTTP 통과는 운영 재생, 마이크 녹음, Android 오디오 포커스, 실기기 검증을 대신하지 않는다.
