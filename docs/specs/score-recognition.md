# 악보 인식 서비스 계약 (omr, SHT-07 내부)

- Jira: [S15P31E102-313](https://ssafy.atlassian.net/browse/S15P31E102-313)(인식, 사지한). 소비자: [S15P31E102-347](https://ssafy.atlassian.net/browse/S15P31E102-347)(BE 변환 작업자·검수·저장).
- 상태: 초안(2026-11-03, 313 설계 결정). BE(347) 검토 전이다. `hwangPitch` 생략 동작은 382 결정을 바꾸는 **제안**이다(아래 '382와 다른 점').
- 상위 계약: [내 악보](user-sheets.md) '2차 결정(382)'의 SHT-07·08·10. 이 문서는 backend 작업자와 악보 인식 서비스 사이만 정한다. 앱이 보는 API는 382와 Notion API 명세를 따른다.
- 근거: [변환 가능성 조사와 실측](../knowledge/ai/score-omr-feasibility.md).

## 범위

포함:

- 내부 API `POST /v1/recognitions`의 요청·응답·실패 코드·시간 상한
- 엔진 선택 규칙, 초안 결과의 `provenance`, 확인 필요 칸(`issues`)과 악보 전체 알림(`notices`)의 종류
- 오선보 → 정간보 변환 기본값

제외:

- 업로드·임시 원본 수명·상태(QUEUED/RUNNING/DONE/FAILED)·검수 화면·저장(382, 347)
- 사용자가 올리는 MusicXML·MIDI 파일 가져오기([S15P31E102-349](https://ssafy.atlassian.net/browse/S15P31E102-349), 2차 보류)
- 정간보 → 오선보 파일 내보내기(앱이 공통 악보로 그린다, 382)

## 실행 위치

- 악보 인식 전용 컨테이너 `omr`. 채점 AI(`ai`)와 따로 두어 메모리·CPU를 나눠 쓰지 않는다. 내부망에서 backend만 호출하고 외부에 열지 않는다.
- 동시 처리 1건(Audiveris 잠금). 메모리 2GiB·CPU 2개(`infra/runtime/compose.yml`, 이 PC 실측 최대 1.31GiB, 운영 서버 0.76GB). 그림 정간보 OMR(S15P31E102-458)의 torch를 붙이면 다시 잰다.
- Audiveris(AGPL-3.0)는 수정 없이 별도 프로세스로 실행한다.

## 요청

backend 변환 작업자가 SHT-07로 접수한 변환 하나마다 한 번 호출하고 응답을 기다린다(동기).

```json
POST /v1/recognitions
{
  "conversionId": "c-123",
  "notation": "STAFF",
  "files": [{"url": "https://...signed", "contentType": "application/pdf"}],
  "instrument": "DANSO",
  "jangdan": "굿거리",
  "hwangPitch": null
}
```

| 필드 | 뜻 |
| --- | --- |
| `conversionId` | backend 변환 식별자. 응답에 그대로 돌려주고 로그에 남긴다. 같은 값으로 다시 와도 서비스는 상태를 저장하지 않고 새로 인식한다(중복 처리는 backend 책임) |
| `notation` | `JEONGGANBO` 또는 `STAFF` |
| `files` | HTTP 읽기 URL 목록. 534부터 backend의 내부 임시 원본 URL(만료 토큰, 외부 공개 없음)을 사용한다. 요청·응답 구조는 유지하며 기존 URL 계약도 엔진에서 처리한다. PDF 1개 또는 PNG·JPEG 1~10장(382). 파일·합계 20MiB, PDF 10쪽 |
| `instrument` | `instruments.code`. 율명 표 적합과 음역 검사에 쓴다 |
| `jangdan` | 장단 이름. 정간보는 필수(382). 오선보는 생략 가능(아래 기본값) |
| `hwangPitch` | 오선보에서 황으로 둘 음(MIDI 번호). 생략하면 자동 조 맞춤 |

## 응답

항상 HTTP 200에 결과 상태를 담는다(채점 분석 API와 같은 방식). 요청 형식 오류만 422다.

```json
{
  "conversionId": "c-123",
  "status": "DONE",
  "failReason": null,
  "engine": "audiveris",
  "engineVersion": "5.11.0",
  "pages": [{"index": 0, "width": 4300, "height": 3040}],
  "jgb": {"v": 1, "melody": "..."},
  "score": {"schemaVersion": 1, "provenance": {"review": "inferred", "converter": "staff-omr/audiveris-5.11.0"}},
  "issues": [{"kind": "OUT_OF_RANGE", "eventId": null, "cellId": "c6-j0", "page": null, "bbox": null, "detail": {"measure": 7, "semitone": 3}}],
  "notices": [{"kind": "TRANSPOSED", "eventId": null, "cellId": null, "page": null, "bbox": null, "detail": {"semitones": 8, "candidates": [8]}}]
}
```

- `score`: 공통 악보 v1([score_data.schema.json](../../data/src/scores/score_data.schema.json)). 언제나 초안이라 `provenance.review`는 `inferred`다. 내 악보는 사용자가 검수해 저장한 뒤에도 `inferred`로 둔다(`reviewed`는 전문가가 검수한 채점 정답용). 저장할 때 backend가 `converter`·`jgbSha256`·`sourceId`를 다시 채운다(S15P31E102-447).
- `provenance.converter` 값: `jeongganbo-text/standard_score_text`(글자 층), `jeongganbo-omr/malerlab`, `staff-omr/audiveris-<버전>`. `jgbSha256`·`sourceId`는 변환한 `.jgb.json`과 `conversionId` 기준으로 채운다.
- `jgb`: 중간 결과 `.jgb.json`(382). 저장 여부는 backend가 정한다.
- `bbox`: `pages[index]` 그림 픽셀 좌표 `[x, y, 너비, 높이]`. 검수 화면이 원본 쪽 이미지에 표시한다.

### issues와 notices (S15P31E102-469)

- `issues`: 사용자가 고칠 칸이 있는 것. 항목마다 결과 `score`의 음(`eventId`)이나 칸(`cellId`, `c{각}-j{정간}`)을 언제나 가리킨다. 한 칸에 여러 개가 올 수 있으므로(예: 한 정간에 몰린 짧은 음마다 `OFF_GRID_RHYTHM`), backend는 `cellId`(없으면 `eventId`)가 같은 것을 한 칸으로 세어 확인할 칸 수로 쓴다.
- `notices`: 칸이 없는 악보 전체·쪽·열 단위 알림. `eventId`·`cellId`는 언제나 null이고 모양은 issue와 같다. 확인할 칸 수에 넣지 않는다.
- 엔진이 결과 악보에 없는 음·칸을 가리키면(엔진 잘못) 그 id를 비우고 로그를 남긴다. 그래서 가리키는 id가 하나도 남지 않으면 그 항목은 `notices`로 간다.

| kind | 놓이는 곳 | 칸 |
| --- | --- | --- |
| `OUT_OF_RANGE` | issues | 그 음이 놓인 정간 |
| `LOW_CONFIDENCE` `OFF_GRID_RHYTHM`·`COLLIDED` | issues | 리듬을 옮기거나 겹친 정간 |
| `LOW_CONFIDENCE` `GAP_IN_VOICE` | issues | 빈 시간이 시작하는 쉼표 정간 |
| `ORNAMENT_DROPPED` | issues | 꾸밈음이 붙어 있던 음의 정간. 반복을 펼치면 펼친 각마다 하나. 뒤따르는 음이 없으면 notices |
| `UNREADABLE` 읽지 못한 글자(`detail.text`) | issues | 그 글자를 덮는 정간 |
| `UNREADABLE` `NO_TEXT`·`NO_GRID`·`NO_STAFF`·`NOT_SCORE_COLUMN` | notices | 쪽·열 전체 |
| `LOW_CONFIDENCE` `TIME_SIGNATURE_CHANGED`·`VOLTA_NUMBER` | notices | 마디 구조 |
| `TRANSPOSED`·`VOICE_GUESSED`·`JANGDAN_GUESSED`·`JANGDAN_MISMATCH` | notices | 악보 전체 |

### issues 종류

| kind | 뜻 | 기준 |
| --- | --- | --- |
| `LOW_CONFIDENCE` | 읽었지만 확신이 낮음 | 정간보 OMR `cells.json`의 신뢰도가 기준 아래, 또는 오선보 변환에서 박자표 바뀜·격자 밖 리듬·칸 충돌·성부 안 빈 시간·volta 번호 없음(`detail.reason`) |
| `UNREADABLE` | 칸이나 기호를 읽지 못해 비움 | 빈 결과·모르는 글자 |
| `OUT_OF_RANGE` | 악기 음역 밖 율명 | 율명 표·`instrument` 음역 |
| `TRANSPOSED` | 조를 자동으로 옮김 | `detail`에 옮긴 반음과 후보(동률이면 2개) |
| `VOICE_GUESSED` | 여러 성부 중 하나를 골랐음 | 오선보 파트·성부가 2개 이상, 또는 정간보 글자 층의 2중주 이상 쪽 |
| `ORNAMENT_DROPPED` | 꾸밈음을 버림 | 오선보 꾸밈음 |
| `JANGDAN_GUESSED` | 요청에 장단이 없어 정했음 | 오선보. `detail` `{"source": "timeSignature"(9/8·12/8) \| "rhythm"(박자표를 못 읽어 리듬으로) \| "default"(굿거리), "jangdan"}` |
| `JANGDAN_MISMATCH` | 고른 장단과 각의 정간 수가 맞지 않음 | 정간보 글자 층: 정간당 박이 1·1/2·1/3이 아님(세령산의 3/2박은 예외). 오선보: 한 각(한 마디) 길이가 장단 한 번과 다름 |

## 스캔·사진 정간보 경로 (S15P31E102-458)

- PDF 글자 층이 없거나 PNG/JPEG 입력이면 `jeongganbo_scan`이 MALerLab `e95045f`와 v1.0.0 가중치를 사용하는 기존 DATA 리더를 별도 프로세스로 호출한다. 글자 층 검사에 쓴 시간을 스캔 예산에서 차감한다. 쪽당 60초·동시 무거운 추론 1건을 유지한다.
- 검출한 격자의 열/칸을 보존하고, 빠진 칸은 이웃 격자로 보완해 `LOW_CONFIDENCE`(`GRID_FILLED`)를 남긴다. 모델 신뢰도 0.8 미만도 확인 표시를 남기며, 이 값은 정확도로 보정된 확률이 아니다. 원본 쪽 인덱스와 픽셀 상자를 결과의 `c{각}-j{정간}`에 연결한다.
- 첫 해상도에서 격자를 못 찾으면 같은 시간 예산 안에서 1/2 크기로 한 번 재시도한다. 상자는 원본 크기로 되돌린다. 모든 쪽에서 격자나 음을 읽지 못하면 `NO_GRID_FOUND`, 실행 오류는 `ENGINE_ERROR`, 시간 초과는 `TIMEOUT`이다.
- 지원하는 율명·이음·쉼표와 율명에 붙은 시김새는 공통 변환기를 재사용한다. 지원하지 않는 독립 시김새나 잘못된 토큰만 제외해 `UNREADABLE`(`UNSUPPORTED_SYMBOL`)로 표시하고, 같은 칸에서 읽힌 율명은 보존한다. 먹이 있는데 결과가 비면 `EMPTY_RECOGNITION`으로 표시한다.
- 검출한 인쇄 각의 최빈 정간 수로 줄을 구성한다. 한 열에 여러 장단이 이어진 인쇄물은 장단별 각 구분을 확정하지 못한다. `JANGDAN_MISMATCH`가 남으면 각 구분·속도를 검수한다. 모델이 높은 신뢰도로 틀릴 수도 있어 스캔 결과는 `inferred` 초안으로 반환한다.
- 세부 실행·관측 결과와 이용 조건은 [458 기록](../knowledge/ai/jeongganbo-scan-runtime.md)에 둔다. 오선보 806의 코드에 의존하지 않는 별도 MR로 제출한다.

### 실패 코드

| failReason | 뜻 |
| --- | --- |
| `UNSUPPORTED_FILE` | 파일 형식·크기·쪽 수 위반, 내려받기 실패 |
| `NO_GRID_FOUND` | 정간보에서 정간 격자를 찾지 못함 |
| `NO_STAFF_FOUND` | 오선보에서 오선·음표를 찾지 못함 |
| `TIMEOUT` | 시간 상한 초과 |
| `ENGINE_ERROR` | 엔진·변환 프로그램의 비정상 종료와 그 밖의 오류(악보 문제가 아닌 실패). 오선보는 Audiveris가 오선을 못 찾았다고 알린(StepException) 쪽만 `NO_STAFF`로 보고, 그 밖에 악보 없이 끝나면 이 코드다 |

실패해도 가짜 음표를 만들지 않는다. 일부 쪽만 실패하면 그 쪽을 `UNREADABLE` notice(쪽 전체 `bbox`)로 남기고 `DONE`이다.

## 시간 상한

- 쪽마다 60초, 요청 전체 `60초 × 쪽 수`, 최대 600초. backend의 호출 제한은 이보다 길게 둔다(630초 이상).
- 실측(2026-11-04, 이 PC Docker, CPU 1.5·메모리 2GiB): 오선보 쪽 그림 1쪽 8.5~10초, 정간보·오선보 펼침면 PDF 1쪽 13.5초, 정간보 글자 층 1쪽 1.0초. 운영 서버(edo1, 4코어, 2026-11-04): 같은 오선보 1쪽이 CPU 1.5에서 25.1~29.5초(4곡 중 1곡 30초 제한으로 `TIMEOUT`), CPU 3에서 18.6~22.3초라 쪽당 60초·CPU 2로 정했다(S15P31E102-468).
- 참고(BE 연결 제안, 합의 전): Audiveris는 한 번에 한 건이고 기다리는 시간도 요청 시간에 든다. 동시에 부르면 뒤 요청이 기다리다 `TIMEOUT`이 되므로, backend가 변환 작업을 큐로 한 건씩 보내고 앱에는 "인식 중" 상태로 두는 비동기 흐름을 권한다. `TIMEOUT`·`ENGINE_ERROR`는 같은 파일로 다시 보내면 된다(omr은 이전 요청을 기억하지 않는다).

## 엔진 선택

| notation·입력 | 엔진 |
| --- | --- |
| `JEONGGANBO`, PDF에 글자 층이 있음 | `standard_score_text.py`(표준악보집 글자·선 좌표) |
| `JEONGGANBO`, 그 밖 | `jeongganbo_omr.py`(MALerLab/jeongganbo-omr). [S15P31E102-458](https://ssafy.atlassian.net/browse/S15P31E102-458)에서 연결한다(2차 일정 포함, 2026-11-04 결정). 그 전에는 `ENGINE_ERROR` |
| `STAFF` | Audiveris → MusicXML → 정간보 변환 |

어느 경로든 JG-like 텍스트 → jeongganbo-generator `import-ngc-omr.mjs` → `.jgb.json` → `score_data.convert` 순서다.

### 오선보 경로 (313 U5)

- 쪽마다 Audiveris(`-batch -transcribe -export`)를 돌린다. 쪽당 60초와 남은 요청 시간 중 짧은 쪽을 넘으면 끝내고 `TIMEOUT`이다. Audiveris는 한 번에 하나만 돌리고(동시 1건, 기다리는 시간도 요청 시간에 든다), 신호를 받아 죽으면(메모리 부족 등) `ENGINE_ERROR`다.
- 오선을 못 찾은 쪽(Audiveris 5.11은 종료 코드 1로 끝나고 표준 출력에 StepException을 남긴다)은 `UNREADABLE`(`detail.reason` `NO_STAFF`, `bbox` 쪽 전체)로 남기고, 모든 쪽이 그러면 `NO_STAFF_FOUND`다. 쪽들의 MusicXML은 첫 파트 뒤에 마디로 이어 붙여 한 곡으로 바꾼다.
- 장단은 요청 `jangdan` → 박자표(9/8 세마치·12/8 굿거리) → 박자표를 못 읽었으면 리듬으로 고른 박 구성(점4분 4박 굿거리·3박 세마치) → 굿거리 순이다. 요청에 장단이 없으면 어떻게 정했든 `JANGDAN_GUESSED`를 한 번 남긴다. 정간 하나의 박은 오선보 1박(점4분·4분·8분)을 장단의 `bpmUnit` 박으로 옮긴 값이다. 한 각 길이가 장단 한 번과 다르면 `JANGDAN_MISMATCH`(`detail` `{"jangdan", "jeongganPerGak"}`)다(예: 4/4 곡을 굿거리로 둘 때).
- `engine` `audiveris`, `engineVersion`·`provenance.converter`의 버전은 컨테이너의 Audiveris 버전(`staff-omr/audiveris-5.11.0`)이다.

### 정간보 글자 층 경로 (313 U2)

- 쪽마다 글자 층을 읽어 각을 이어 붙인다(위 '실패 코드'의 일부 쪽 실패 규칙과 같다).
  - 율명 글자가 없는 쪽(표지·설명이거나 글자 층 없이 스캔한 쪽)은 건너뛰고 `UNREADABLE`(`detail.reason` `NO_TEXT`, `bbox` 쪽 전체)로 알린다. 모든 쪽이 그러면 그림(OMR) 경로로 넘긴다.
  - 악보다운 쪽만 받는다. 각이 2개 이상이고, 절반 넘는 각의 정간 수가 같고, 읽은 율명이 읽지 않은 글자보다 많아야 한다. 설명문 상자에 섞인 율명 한자로 가짜 음표를 만들지 않기 위해서다.
  - 율명은 있지만 정간 격자를 못 읽었거나 악보다운 쪽이 아닌 쪽은 `UNREADABLE`(`detail.reason` `NO_GRID`, `bbox` 쪽 전체)로 남긴다. 읽은 쪽이 하나도 없으면 `NO_GRID_FOUND`다.
  - 글자 읽기·변환 프로그램이 비정상 종료하면 `ENGINE_ERROR`다.
- 정간 수가 대부분의 각과 다른 열(옆 쪽 장식음 표 등)은 빼고 `UNREADABLE`(`detail.reason` `NOT_SCORE_COLUMN`, `bbox` 그 열)로 알린다. 곡 끝 각은 짧을 수 있어, 같은 단에서 바로 앞 열 옆에 붙어 있거나 다른 각과 같은 x 열이면 둔다.
- 각마다 정간 수가 다른 악보(예: 끝 각만 짧은 소금 취타)는 각별 정간 수(`gakBeats`)로 그 각만 짧게 읽는다(S15P31E102-404의 `score_data`). 정간 하나의 박은 기본 각의 정간 수(`controls.beats`)로 정한다. `score_data`가 받지 않는 기호(율명 없는 독립 시김새 등)가 있으면 `ENGINE_ERROR`다(원인은 로그에 남김).
- 2중주 이상(열 위 '단소1'·'단소Ⅱ'·'1단소' 같은 표시)은 1 성부 열만 읽고 `VOICE_GUESSED`(`detail` `{"voices": 성부 수, "kept": 1}`, `bbox` 쪽 전체)를 남긴다. 표시로 열을 모두 가르지 못하면 그 쪽은 격자를 못 읽은 쪽과 같다.
- 정간 하나의 박 = 장단 한 번의 박(`bpmUnit` 기준) ÷ 각 하나의 정간 수. 한 각을 장단 한 번으로 본다. 세마치 3정간 → 1박, 굿거리 12정간 → 1/3박(검수 정답 8곡과 같음). 결과가 1·1/2·1/3박이 아니면 고른 장단이 악보와 다를 수 있어 `JANGDAN_MISMATCH`(`detail` `{"jangdan", "jeongganPerGak"}`)를 남긴다. 점4분음표 칸을 쓰는 세령산 10정간 각의 정간당 3/2박만 추가로 허용한다.
- 읽지 않은 글자(시김새 글자 등)는 `UNREADABLE` issue로, `detail.text`에 글자, `bbox`에 쪽 그림 위치(글자 크기의 정사각형), `cellId`에 그 글자를 덮는 정간을 둔다(`eventId`는 비운다).
- `engine` `jeongganbo-text`, `engineVersion` `3`, `provenance.converter` `jeongganbo-text/standard_score_text@3`. 버전 2의 장단 패턴 음가 환산을 유지하며, 버전 3은 PDF의 벡터 삼각 쉼표와 명시적 각 경계 이음을 보존한다. 우물사이 전용 옥타브 점 인식은 제외하며, 글자 위아래의 점으로 음역을 추정하지 않는다. PDF 가져오기의 명시적 `-`만 앞 각의 음에 이어 붙이며 새 각 첫 빈칸은 기존대로 쉼이다. 기존 검수 JGB·오선보 경로는 각 경계 규칙을 유지한다. `score.title`·`sourceId`는 `conversionId`다(곡 제목은 backend가 저장할 때 정한다).
- 요청 `jangdan`은 `data/reference/jangdans.json`의 `name`이어야 한다(아니면 422). 정간보는 꼭 보내고, 오선보는 생략할 수 있다.

## 오선보 → 정간보 기본값

| 항목 | 기본값 | issue |
| --- | --- | --- |
| 조 | 원래 실음 MIDI를 유지한다. `hwangPitch`(생략63)는 율명 기준만 바꾸며 실음을 이조하지 않는다. 악기 지원음에 맞추는 전곡 이조·옥타브 조정은 [작성 계약](score-authoring-consistency.md)에 따라 편집 화면에서 확인 후 적용한다 | 신규 `TRANSPOSED` 없음(과거 결과 읽기 유지) |
| 장단 | 요청 `jangdan`. 없으면 9/8 → 세마치, 12/8 → 굿거리. 그 밖은 실패하지 않고 굿거리 격자로 둔 뒤 검수 화면에서 사용자가 고른다. 박자표를 못 읽었으면 각 경계를 넘는 음이 가장 적은 박 구성(12/8·4/4·9/8 … 순)을 고른다 | `JANGDAN_GUESSED` |
| 정간 하나 | 1박. 겹박자(6/8·9/8·12/8)는 점4분, 마디가 4분음표로 나뉘면 4분음표, 그 밖(5/8·7/8)은 8분음표. 한 각 = 첫 박자표 한 마디의 박 수(3+2처럼 더한 박자는 합). 마디선이 빠진 인식 결과가 있어 마디가 아니라 시간 순서로 각을 자른다. 곡 중간에 박자표가 바뀌면 첫 박자표를 쓰고 알린다 | `LOW_CONFIDENCE`(`detail.reason` `TIME_SIGNATURE_CHANGED`) |
| 정간 안 | 3행(1/3박)·2행(1/2박)과 각 행 좌우(1/6·1/4박)로 나눈다. 그 칸에 안 맞는 리듬은 가까운 칸으로 옮기고, 두 음이 한 칸에 겹치면 나중 음을 쓴다 | `LOW_CONFIDENCE`(`detail.reason` `OFF_GRID_RHYTHM`·`COLLIDED`) |
| 각 경계 | 각을 넘는 음은 다음 각 처음에 같은 음을 다시 적는다(공통 악보는 각을 넘어 잇지 않는다). 못갖춘마디는 앞을, 끝 각은 뒤를 쉼표로 채운다 | — |
| 성부 | 첫 파트에서 곡 길이 절반 이상을 소리로 덮는 성부 중 평균 음이 가장 높은 성부를 기준으로 한다. 본음 시작 수가 그 성부의 두 배 이상인 성부가 있으면 지속음 반주 대신 가장 활동적인 성부를 고른다(동률은 높은 성부). 붙임줄·화음은 시작 하나로 센다. 후보가 없으면 가장 많이 덮는 성부를 쓴다. 인식이 잘못 낸 한두 음짜리 성부를 거른다. 화음은 맨 위 음, 붙임줄 화음은 이어진 하나로, 쉼표에 붙은 화음 표시는 쉼으로 본다. 성부 안의 빈 시간은 쉼표이고, 4분음표 하나보다 길게 비면 `LOW_CONFIDENCE`(`GAP_IN_VOICE`)로 알린다(마디마다 성부 번호가 바뀐 인식 결과일 수 있다). 이조 파트는 실음으로 읽는다 | `VOICE_GUESSED` |
| 음역 | MIDI·옥타브를 보존한다. 표기할 수 없는 음은 초안의 빈 표기와 원래 event 음고로 남긴다. JGB 구조 중간값을 최종 음으로 사용하지 않는다. 물리 음역을 추정하거나 5율·최저/최고 범위로 지원을 판단하지 않는다 | `UNREGISTERED_PITCH`(`detail.reason=NOTATION_UNSUPPORTED`) |
| 반복 | 반복 기호(`times` 포함)와 volta(여러 마디, `1, 2`·`1.`·`1 2`처럼 숫자만 읽음. 숫자가 없으면 1번으로 보고 `LOW_CONFIDENCE` `VOLTA_NUMBER`)를 펼친다. 앞 반복이 끝난 뒤의 반복 끝 기호는 그 다음 마디로 돌아간다. 겹친 반복과 D.S.·D.C.는 다루지 않는다 | — |
| 꾸밈음 | 버림 | `ORNAMENT_DROPPED` |
| 가사 | 버림(공통 악보에 칸 없음) | — |

MusicXML 읽기는 우리 Audiveris 출력만 받는다고 보고 만들었다. 사용자가 올리는 MusicXML(S15P31E102-349)에 다시 쓰려면 외부 엔티티·압축 폭탄을 막는 `defusedxml`과 압축 해제 크기 상한을 더해야 한다.

## 원본 실음 전달 계약 (2026-11-12, S15P31E102-788)

- 이전 자동 5율 이조/옥타브 제한은 원본 음고를 잃고 운지 미등록음을 확정시킬 수 있었다. 인식기는 원본 실음을 반환하고, 작성 화면은 등록 운지·표기·CAT의 교집합으로 후보를 계산한다. 원본 전체와 위치별 MIDI는 SHT `authoring.source`로 보존하고 반복 적용도 그 기준을 사용한다. 당시 실측은 인식 정확도의 과거 근거이며 새 지원음 정책·실물 연주 검수를 대체하지 않는다.

## 미결 사항

- CPU 2·쪽당 60초에서의 운영 서버 쪽당 시간(재반영 때 사람이 잼, [실행 문서](../../infra/runtime/README.md#omr-악보-인식-서비스)), 서버에서 PDF 처리 시간
- 폰 사진·다른 판형 정확도(실물 없어 미측정)
- MALerLab 체크포인트 이용 조건(README에 MIT, LICENSE 파일 없음)
- 347 합의: 위 '382와 다른 점', issue 종류, backend 호출 제한

### 오선보 인식 오류 교정 (806)

작은 음표·빔 인식을 켜 꾸밈음을 분리하고, legacy 영어 OCR로 확인한 빠르기 지시문에 일반 음표가 섞였을 때 해당 영역만 별도 그림에서 제외해 다시 읽는다. 초과 마디는 쪽당 60초 예산 안에서 1.25배 그림으로 한 번 재인식한다. 한 선율의 본음·꾸밈음 순서를 유일하게 대응할 수 있고 음고가 보존될 때만 초과 마디의 음가를 교정하며, 정상 마디는 보존한다. 적용은 `LOW_CONFIDENCE` notice의 `TEMPO_MARK_REMOVED`·`RHYTHM_RECOGNITION_RETRY`로 알린다. 남는 초과 마디는 해당 음의 정간에 `LOW_CONFIDENCE`(`OVERFULL_MEASURE`)를 남긴다. [상세 기준](score-recognition-corrections.md)과 [실제 대조](../knowledge/ai/score-recognition-corrections.md)를 따른다.
