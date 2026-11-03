# 악보 인식 서비스 계약 (omr, SHT-07 내부)

- Jira: [S15P31E102-313](https://ssafy.atlassian.net/browse/S15P31E102-313)(인식, 사지한). 소비자: [S15P31E102-347](https://ssafy.atlassian.net/browse/S15P31E102-347)(BE 변환 작업자·검수·저장).
- 상태: 초안(2026-11-03, 313 설계 결정). BE(347) 검토 전이다. `hwangPitch` 생략 동작은 382 결정을 바꾸는 **제안**이다(아래 '382와 다른 점').
- 상위 계약: [내 악보](user-sheets.md) '2차 결정(382)'의 SHT-07·08·10. 이 문서는 backend 작업자와 악보 인식 서비스 사이만 정한다. 앱이 보는 API는 382와 Notion API 명세를 따른다.
- 근거: [변환 가능성 조사와 실측](../knowledge/ai/score-omr-feasibility.md).

## 범위

포함:

- 내부 API `POST /v1/recognitions`의 요청·응답·실패 코드·시간 상한
- 엔진 선택 규칙, 초안 결과의 `provenance`, 확인 필요 칸(`issues`)의 종류
- 오선보 → 정간보 변환 기본값

제외:

- 업로드·원본 보관·상태(QUEUED/RUNNING/DONE/FAILED)·검수 화면·저장(382, 347)
- 사용자가 올리는 MusicXML·MIDI 파일 가져오기([S15P31E102-349](https://ssafy.atlassian.net/browse/S15P31E102-349), 2차 보류)
- 정간보 → 오선보 파일 내보내기(앱이 공통 악보로 그린다, 382)

## 실행 위치

- 악보 인식 전용 컨테이너 `omr`. 채점 AI(`ai`)와 따로 두어 메모리·CPU를 나눠 쓰지 않는다. 내부망에서 backend만 호출하고 외부에 열지 않는다.
- 동시 처리 1건. 메모리·CPU 상한은 313 구현 실측으로 정한다(정간보 OMR의 torch 때문에 2GiB가 부족할 수 있음).
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
| `files` | 서명 URL 목록. PDF 1개 또는 PNG·JPEG 1~10장(382). 파일·합계 20MiB, PDF 10쪽 |
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
  "issues": [{"kind": "LOW_CONFIDENCE", "eventId": "c0-j2-r0-u0", "cellId": null, "page": 0, "bbox": [10, 20, 30, 40], "detail": null}]
}
```

- `score`: 공통 악보 v1([score_data.schema.json](../../data/src/scores/score_data.schema.json)). 언제나 초안이라 `provenance.review`는 `inferred`다. 사용자가 검수해 저장하면 backend가 `reviewed`로 바꾼다.
- `provenance.converter` 값: `jeongganbo-text/standard_score_text`(글자 층), `jeongganbo-omr/malerlab`, `staff-omr/audiveris-<버전>`. `jgbSha256`·`sourceId`는 변환한 `.jgb.json`과 `conversionId` 기준으로 채운다.
- `jgb`: 중간 결과 `.jgb.json`(382). 저장 여부는 backend가 정한다.
- `bbox`: `pages[index]` 그림 픽셀 좌표 `[x, y, 너비, 높이]`. 검수 화면이 원본 쪽 이미지에 표시한다.

### issues 종류

| kind | 뜻 | 기준 |
| --- | --- | --- |
| `LOW_CONFIDENCE` | 읽었지만 확신이 낮음 | 엔진 신뢰도가 기준 아래(정간보 OMR `cells.json`) |
| `UNREADABLE` | 칸이나 기호를 읽지 못해 비움 | 빈 결과·모르는 글자 |
| `OUT_OF_RANGE` | 악기 음역 밖 율명 | 율명 표·`instrument` 음역 |
| `TRANSPOSED` | 조를 자동으로 옮김 | `detail`에 옮긴 반음과 후보(동률이면 2개) |
| `VOICE_GUESSED` | 여러 성부 중 하나를 골랐음 | 오선보 파트·성부가 2개 이상 |
| `ORNAMENT_DROPPED` | 꾸밈음을 버림 | 오선보 꾸밈음 |
| `JANGDAN_GUESSED` | 박자표로 장단을 정함 | 오선보 9/8·12/8 |
| `JANGDAN_MISMATCH` | 고른 장단과 각의 정간 수가 맞지 않음 | 정간당 박이 1·1/2·1/3이 아님(정간보 글자 층) |

### 실패 코드

| failReason | 뜻 |
| --- | --- |
| `UNSUPPORTED_FILE` | 파일 형식·크기·쪽 수 위반, 내려받기 실패 |
| `NO_GRID_FOUND` | 정간보에서 정간 격자를 찾지 못함 |
| `NO_STAFF_FOUND` | 오선보에서 오선·음표를 찾지 못함 |
| `TIMEOUT` | 시간 상한 초과 |
| `ENGINE_ERROR` | 엔진 비정상 종료·그 밖의 오류 |

실패해도 가짜 음표를 만들지 않는다. 일부 쪽만 실패하면 그 쪽의 정간을 `UNREADABLE` issue로 남기고 `DONE`이다.

## 시간 상한

- 쪽마다 30초, 요청 전체 `30초 × 쪽 수`, 최대 300초. backend의 호출 제한은 이보다 길게 둔다(예: 330초).
- 실측(2026-11-03, 20코어 PC·Docker): 오선보 쪽당 7~11초. 서버(4코어)·CPU 상한에서 313 구현 때 다시 잰다.

## 엔진 선택

| notation·입력 | 엔진 |
| --- | --- |
| `JEONGGANBO`, PDF에 글자 층이 있음 | `standard_score_text.py`(표준악보집 글자·선 좌표) |
| `JEONGGANBO`, 그 밖 | `jeongganbo_omr.py`(MALerLab/jeongganbo-omr) |
| `STAFF` | Audiveris → MusicXML → 정간보 변환 |

어느 경로든 JG-like 텍스트 → jeongganbo-generator `import-ngc-omr.mjs` → `.jgb.json` → `score_data.convert` 순서다.

### 정간보 글자 층 경로 (313 U2)

- 쪽마다 글자 층을 읽어 각을 이어 붙인다(위 '실패 코드'의 일부 쪽 실패 규칙과 같다).
  - 율명 글자가 없는 쪽(표지·설명이거나 글자 층 없이 스캔한 쪽)은 건너뛰고 `UNREADABLE`(`detail.reason` `NO_TEXT`, `bbox` 쪽 전체)로 알린다. 모든 쪽이 그러면 그림(OMR) 경로로 넘긴다.
  - 악보다운 쪽만 받는다. 각이 2개 이상이고, 절반 넘는 각의 정간 수가 같고, 읽은 율명이 읽지 않은 글자보다 많아야 한다. 설명문 상자에 섞인 율명 한자로 가짜 음표를 만들지 않기 위해서다.
  - 율명은 있지만 정간 격자를 못 읽었거나 악보다운 쪽이 아닌 쪽은 `UNREADABLE`(`detail.reason` `NO_GRID`, `bbox` 쪽 전체)로 남긴다. 읽은 쪽이 하나도 없으면 `NO_GRID_FOUND`다.
  - 글자 읽기·변환 프로그램이 비정상 종료하면 `ENGINE_ERROR`다.
- 정간 수가 대부분의 각과 다른 열(옆 쪽 장식음 표 등)은 빼고 `UNREADABLE`(`detail.reason` `NOT_SCORE_COLUMN`, `bbox` 그 열)로 알린다. 곡 끝 각은 짧을 수 있어, 같은 단에서 바로 앞 열 옆에 붙어 있거나 다른 각과 같은 x 열이면 둔다.
- 각마다 정간 수가 다른 악보(예: 끝 각만 짧은 소금 취타)는 `score_data`가 각별 정간 수(`gakBeats`)를 아직 받지 않아 `ENGINE_ERROR`다(원인은 로그에 남김). 지원은 별도 작업이다.
- 2중주 이상(열 위 '단소1'·'단소Ⅱ'·'1단소' 같은 표시)은 1 성부 열만 읽고 `VOICE_GUESSED`(`detail` `{"voices": 성부 수, "kept": 1}`, `bbox` 쪽 전체)를 남긴다. 표시로 열을 모두 가르지 못하면 그 쪽은 격자를 못 읽은 쪽과 같다.
- 정간 하나의 박 = 장단 한 번의 박(`bpmUnit` 기준) ÷ 각 하나의 정간 수. 한 각을 장단 한 번으로 본다. 세마치 3정간 → 1박, 굿거리 12정간 → 1/3박(검수 정답 8곡과 같음). 결과가 1·1/2·1/3박이 아니면 고른 장단이 악보와 다를 수 있어 `JANGDAN_MISMATCH`(`detail` `{"jangdan", "jeongganPerGak"}`)를 남긴다.
- 읽지 않은 글자(시김새 글자 등)는 `UNREADABLE` issue로, `detail.text`에 글자, `bbox`에 쪽 그림 위치(글자 크기의 정사각형)를 둔다. `eventId`·`cellId`는 비운다.
- `engine` `jeongganbo-text`, `provenance.converter` `jeongganbo-text/standard_score_text`. `score.title`·`sourceId`는 `conversionId`다(곡 제목은 backend가 저장할 때 정한다).
- 정간보 요청의 `jangdan`은 `data/reference/jangdans.json`의 `name`이어야 한다(아니면 422). 오선보는 U5에서 정한다.

## 오선보 → 정간보 기본값

| 항목 | 기본값 | issue |
| --- | --- | --- |
| 조 | `hwangPitch`가 있으면 그 값. 없으면 반음씩 옮겨 악기 기본 5율(단소 황·태·중·임·무, 소금 임·남·황·태·고)과 음역(단소 중~중청중, 소금 임~중청남)에 가장 많이 들어가는 양. 동률이면 작게 옮기는 쪽, 후보 2개까지 `detail.candidates` | `TRANSPOSED` |
| 장단 | 요청 `jangdan`. 없으면 9/8 → 세마치, 12/8 → 굿거리. 그 밖은 실패하지 않고 굿거리 격자로 둔 뒤 검수 화면에서 사용자가 고른다. 박자표를 못 읽었으면 각 경계를 넘는 음이 가장 적은 박 구성(12/8·4/4·9/8 … 순)을 고른다 | `JANGDAN_GUESSED` |
| 정간 하나 | 1박. 겹박자(6/8·9/8·12/8)는 점4분, 그 밖은 4분음표. 한 각 = 박자표 한 마디의 박 수. 마디선이 빠진 인식 결과가 있어 마디가 아니라 시간 순서로 각을 자른다 | — |
| 정간 안 | 3행(1/3박)·2행(1/2박)과 각 행 좌우(1/6·1/4박)로 나눈다. 그 칸에 안 맞는 리듬은 가까운 칸으로 옮긴다 | `LOW_CONFIDENCE`(`detail.reason` `OFF_GRID_RHYTHM`) |
| 각 경계 | 각을 넘는 음은 다음 각 처음에 같은 음을 다시 적는다(공통 악보는 각을 넘어 잇지 않는다). 못갖춘마디는 앞을, 끝 각은 뒤를 쉼표로 채운다 | — |
| 성부 | 첫 파트에서 평균 음이 가장 높은 성부, 화음은 맨 위 음 | `VOICE_GUESSED` |
| 음역 | 악기 음역 밖 음은 그대로 적고 알린다. 하배~중청 밖은 그 안으로 옥타브를 옮긴다 | `OUT_OF_RANGE`(`detail` `{"measure", "semitone"}`) |
| 반복·volta | 펼침 | — |
| 꾸밈음 | 버림 | `ORNAMENT_DROPPED` |
| 가사 | 버림(공통 악보에 칸 없음) | — |

## 382와 다른 점 (347 합의 대기)

- 오선보 `hwangPitch`를 생략하면 382는 "악기 기본(단소 E♭4)"이다. 이 계약은 **자동 조 맞춤**을 제안한다. 표준악보집 오선보는 정간보보다 6~15반음 다른 조로 적혀 있어 고정 기본값이면 거의 모든 음이 어긋나고, 자동 맞춤은 실측 8건이 모두 맞았다. 사용자가 고른 값은 언제나 우선한다.

## 미결 사항

- `omr` 메모리·CPU 상한, 서버에서의 쪽당 시간(313 구현 실측)
- 폰 사진·다른 판형 정확도(실물 없어 미측정)
- MALerLab 체크포인트 이용 조건(README에 MIT, LICENSE 파일 없음)
- 347 합의: 위 '382와 다른 점', issue 종류, backend 호출 제한
