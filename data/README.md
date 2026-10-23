# 데이터 수집·정제

얼쑤의 음원·악보 데이터를 수집·정제하고, 분석과 검증에 사용할 데이터셋을 구축합니다.

AI 작업 지침은 [AGENTS.md](AGENTS.md), 코드 없는 조사·학습 결과의 기록 방법은 [문서 기록 기준](../docs/README.md)을 따릅니다. 수집처·악보 표현·정제 방법의 조사 결과는 `docs/knowledge/data/`에, 여러 파트가 공유하는 국악 개념은 `docs/knowledge/domain/`에 남깁니다.

## 담당 범위

- 음원·악보 수집과 출처·사용 조건 기록
- 파일 형식·메타데이터 정리, 누락·중복·품질 확인
- 데이터셋 구축에 필요한 악보 변환과 오디오 전처리
- 평가용 데이터셋 구성과 데이터 버전·생성 절차 관리
- AI가 사용할 정답·메타데이터·검증 샘플 제공

## AI·Backend와의 경계

| 영역 | 담당 |
| --- | --- |
| Data | 미리 데이터를 준비하고 데이터셋·정답·메타데이터를 생성 |
| AI | 연주 요청별 전처리, 음높이 추정, DTW 정렬, 채점 |
| Backend | 앱용 API, 평가 요청과 결과 관리 |

같은 악보 변환이나 오디오 전처리라도 데이터셋을 만드는 작업은 Data, 연주 요청을 처리하는 작업은 AI에 둡니다. 양쪽에서 공통 로직이 필요하면 입출력 형식과 재사용 위치를 합의하고 중복 구현을 피합니다.

데이터를 전달할 때 필요한 악보·음표 식별자, 시간·음높이 단위, 데이터 버전은 사용하는 파트와 맞춥니다. 구체적인 스키마와 전달 방식은 실제 구현에서 정합니다.

## 파일 관리

팀원이 모은 악보 PDF·음원·MIDI는 원본 한 벌을 서버 `/data/raw`에 모으고, Git에는 목록·판단 값·코드·작은 샘플만 둡니다.

```text
data/
├── catalog/                 # 원본 목록: 곡·악보·음원·MIDI의 ID·출처·이용 조건·SHA-256·rawPath.
├── annotations/             # 원본에 덧붙인 판단 값: 사람 입력과 도구 추정, 검수 상태.
│   ├── scores/<score-id>/   # 쪽별 악보·설명 영역 좌표.
│   └── audio/<audio-id>-<설명>/ # 구간·취법·율명·대표 F0 라벨. 원본 ID는 유지.
├── src/                     # 가공 코드. 결과는 processed/에만 씁니다.
│   ├── scores/              # PDF → 쪽 그림 → 영역 자르기 → 음표 데이터.
│   ├── audio/               # 분석용 변환 → 구간 자르기 → 악보와 시각 맞추기.
│   ├── midi/                # MIDI → 음표 이벤트 목록.
│   └── catalog/             # 목록과 실제 파일의 SHA-256 대조.
├── tests/fixtures/          # 사용 조건을 확인한 작은 고정 샘플.
├── mart/                    # 집계 DB 스키마.
├── raw/                     # 받은 원본 그대로. Git 제외.
└── processed/               # raw·src·annotations로 다시 만드는 결과. Git 제외.
```

- **원본**: `raw/`의 파일은 받은 그대로 두고 수정·덮어쓰기·삭제하지 않습니다. 고친 자료는 새 ID로 추가합니다. 묶음으로 받은 자료(예: AI-Hub)는 받은 폴더 구조를 유지합니다.
- **참조 기준**: 목록·판단 값·가공 결과는 원본을 파일 이름이 아니라 원본 ID와 SHA-256으로 가리키고, 구간은 초 단위 시간으로 적습니다. 소수점 자릿수는 저장 형식이며 측정 정확도를 뜻하지 않습니다.
- **원본 경로**: 목록에는 `rawPath`로 `data/raw/...` 하나만 적습니다. 서버 경로는 앞에 `/`를 붙인 `/data/raw/...`입니다.
- **annotations와 processed의 경계**: 사람이 적었거나, 도구가 추정했더라도 검수하거나 다른 곳에서 참조하는 값은 `annotations/`에 둡니다. 코드를 다시 실행하면 같게 나오는 큰 결과(프레임별 F0, 분석용 WAV, 잘라 낸 그림)는 `processed/`에 둡니다.
- **출처와 검수 표시**: `annotations/`의 값마다 `method`(`manual` 또는 `tool:도구@버전`)와 `review`(`inferred`·`reviewed`·`rejected`)를 적습니다. 채점 정답에는 `reviewed`만 씁니다.
- **판본**: 악보·음원·MIDI는 이름이 같아도 판본·편곡·청을 확인하기 전에는 같은 곡으로 묶지 않습니다.
- **MIDI**: 정답이 아니라 참고 자료입니다. MIDI 번호를 율명에 고정해 바꾸지 않고, 율명은 악보에서 정합니다.
- **재현**: `processed/`를 지워도 `raw/`·`src/`·`annotations/`로 같은 결과를 다시 만들 수 있어야 합니다. 원본 음원과 대용량 결과는 커밋하지 않습니다.

## 서버 원본 공유

서버 `edo1`의 `/data/raw`가 팀 원본 보관 위치입니다. 저장소 루트에서 실행하며, 하위 경로는 `data/raw/` 아래와 똑같이 맞춥니다.

올리기 전에 서버 여유 공간을 확인합니다. `/data`는 운영 DB와 같은 디스크입니다.

```bash
ssh edo1 "df -h /"
```

올리기: 목록에 항목을 추가한 뒤 폴더째 보냅니다. 서버에는 추가만 하고 기존 파일을 덮어쓰지 않습니다. `scp`는 같은 이름의 파일을 경고 없이 덮어쓰므로, 먼저 서버에 같은 폴더가 없는지 확인합니다. `No such file or directory`가 나올 때만 올립니다.

```bash
ssh edo1 "ls /data/raw/<하위경로>/<폴더>"
```

```bash
ssh edo1 "mkdir -p /data/raw/<하위경로>"
```

```bash
scp -r data/raw/<하위경로>/<폴더> edo1:/data/raw/<하위경로>/
```

받기: `develop`의 최신 목록을 받은 뒤 필요한 폴더를 내려받습니다.

```bash
scp -r edo1:/data/raw/<하위경로>/<폴더> data/raw/<하위경로>/
```

확인: 받은 파일의 SHA-256이 목록의 값과 같아야 같은 원본입니다. 하위 폴더 안의 파일까지 모두 확인합니다.

```bash
find data/raw/<하위경로>/<폴더> -type f -exec sha256sum {} +
```

`/data/raw`는 웹으로 열지 않고 SSH 키로만 접근합니다.

## 데이터 준비

현재 실제 자료는 산조대금입니다. 공통 JSON 세 종류를 팀 폴더 규칙에 맞춰 나눕니다.

| 위치 | 편집할 내용 |
| --- | --- |
| [catalog/sanjo-daegeum/sources.json](catalog/sanjo-daegeum/sources.json) | 원음 9개·악보집 1개의 원본 ID, rawPath, 출처·이용 조건·SHA-256, 필요한 annotationPath |
| [annotations/audio/](annotations/audio/) 아래 `<원본 ID>-<설명>/labels.json` | 원음별 구간·취법·율명·대표 기본주파수(F0)와 판단 근거. 산조는 8파일·121행 |
| [catalog/sanjo-daegeum/performances.json](catalog/sanjo-daegeum/performances.json) | 연주 원음, 확인된 대응 악보, 별도로 구분한 비교용 악보 참조 |

공통 [export_dataset.py](src/audio/export_dataset.py)는 목록에서 선언한 라벨 파일을 모두 읽고 검증합니다. 파일을 검색해서 발견한 것만 사용하는 방식이 아니므로, 선언된 annotation이 없으면 오류입니다. 라벨의 명시적 구간을 잘라 저장하며 율명을 새로 추정하지 않습니다. [산조대금 기록](../docs/knowledge/data/sanjo-daegeum.md)에 판단 근거와 검수 범위를 남깁니다.

## 원본 준비와 실행

검증 환경은 Python 3.12.14·NumPy 2.3.5·SoundFile 0.13.1·libsndfile 1.2.2입니다. WAV 처리에는 NumPy, MP3 처리와 전체 테스트에는 SoundFile도 필요합니다. SoundFile Windows wheel에는 libsndfile이 포함됩니다. 다른 플랫폼에서도 MP3를 지원하는 libsndfile을 사용합니다([공식 설치 안내](https://python-soundfile.readthedocs.io/en/latest/#installation)).

```powershell
python -m pip install numpy==2.3.5 soundfile==0.13.1
```

[국립국악원 단음 다운로드](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)의 관악기 → 산조대금 전체 9개와 비교용 표준악보집 PDF를 sources의 `rawPath`에 준비합니다. 주소·해시·이용 조건은 목록에 있습니다. 한 구간만 저장할 때는 해당 원음만 있으면 됩니다. 메타데이터는 해당 데이터셋 전체가 필요합니다.

저장소 루트에서 전체 저장:

```powershell
python data/src/audio/export_dataset.py --dataset data/catalog/sanjo-daegeum --output-dir data/processed/instruments/daegeum/sanjo/prepared-v5
```

저취 황 기본음 하나 저장:

```powershell
python data/src/audio/export_dataset.py --dataset data/catalog/sanjo-daegeum --label sanjo.jeochwi.hwang --output-dir data/processed/instruments/daegeum/sanjo/selected-hwang
```

주법·취법·율명으로 선택:

```powershell
python data/src/audio/export_dataset.py --dataset data/catalog/sanjo-daegeum --technique '기본음+깊은요성' --register 저취 --yulmyeong 임 --output-dir data/processed/instruments/daegeum/sanjo/selected-deep-im
```

태평가 원음과 비교용 악보 저장:

```powershell
python data/src/audio/export_dataset.py --dataset data/catalog/sanjo-daegeum --performance ngc-sanjo-2625 --output-dir data/processed/instruments/daegeum/sanjo/selected-taepyeongga
```

`--label`과 `--performance`는 반복할 수 있습니다. `--category`, `--technique`, `--register`, `--yulmyeong`은 정확히 일치하는 값을 고르고 여러 조건은 모두 만족해야 합니다. 선택 옵션이 없으면 전체를 생성합니다. 없는 ID나 빈 선택은 오류입니다.

**출력은 `data/processed/` 아래의 아직 없는 폴더로 지정합니다.** 기존 폴더나 원본은 덮어쓰지 않습니다. 검증과 임시 폴더 생성이 끝나면 결과 폴더를 공개하므로 실패한 일부 결과가 완성본처럼 남지 않습니다.

raw·processed·annotations 폴더 자체의 심볼릭 링크·junction 재지정과 하위 링크를 통한 각 영역 밖 접근은 거부합니다. 다른 위치의 원음은 폴더 링크 대신 `--root`로 지정합니다. 원본 ID는 전체 sources 목록에서 대소문자를 무시해도 유일해야 합니다.

서버 원음 `/data/raw/...`를 사용할 때는 `--root /`를 지정하고 출력도 `/data/processed/...` 아래로 지정합니다. `--dataset`은 저장소의 실제 catalog 폴더를 가리킵니다. annotation은 그 catalog와 같은 저장소에서 읽으므로 서버 원음 루트에 메타데이터를 복제할 필요가 없습니다. `--root /data/raw`는 경로를 중복시키므로 사용하지 않습니다.

## 결과 사용

- 산조 전체: 기본음계 46개·시김새 75개 WAV, 태평가 원음 1개, 비교용 원본 악보집 PDF 1개, 결과 목록 `catalog.json`.
- 개별 구간: PCM16/IEEE float32 RIFF/WAVE와 MP3 Layer III를 내용으로 판별합니다. 원래 샘플레이트로 디코딩해 채널 평균·필요한 피크 감쇠·PCM16 양자화를 적용합니다. 리샘플링·음높이 보정은 하지 않습니다.
- 연주 음원·악보: 원본 바이트 그대로 복사합니다. 같은 PDF의 두 쪽을 참조하면 파일은 한 번만 복사합니다.
- 결과 catalog v2에는 펼친 라벨·method/review·프레임 경계·변환·해시, 출처, 연주와 비교용 악보를 기록합니다. `manifestHashes`는 catalog 2파일과 선언된 모든 annotation 파일을 포함합니다. 결과 catalog를 편집 입력으로 쓰지 않습니다.
- `audioDecoders[sourceId]`는 포맷·디코더·샘플레이트·채널·전체 프레임 수를 기록하고, MP3에는 SoundFile/libsndfile 버전도 남깁니다. MP3 시각 0은 디코더가 반환한 첫 PCM 프레임이며 별도 지연·패딩 보정은 없습니다. 라벨 분석도 같은 디코더 버전의 PCM을 기준으로 합니다.

태평가의 `scores`는 현재 비어 있습니다. 성악 오선보·단소 정간보 일부는 최상위 `comparisonScores`에 별도로 두고 `forPerformanceId`로 어떤 연주를 비교할 때 함께 제공하는지만 표시합니다. 이 참조는 같은 판본·편곡·청이나 채점 정답의 대응 관계가 아닙니다. PDF의 비상업·무변경 조건과 확보 범위를 확인합니다.

## JSON 형식과 악기 추가

sources·performances는 schema v2, 원음별 labels는 v3입니다. 이전의 `data/datasets/` 3파일 배치와 구형 schema를 읽는 경로는 제거했습니다. 열·행 구조, 라벨 ID·구간·율명·F0·출력 WAV 파일명은 보존합니다.

| 문서 | 주요 필드 |
| --- | --- |
| sources | datasetId, instrument, pathBase=repository_root, sources. 원본마다 id/kind/rawPath/sha256/title/sourceUrl/license/attribution. 악보는 pdfPageCount, 라벨 대상 원음은 annotationPath |
| 원음별 labels | datasetId, 해당 원음 하나의 sourceHashes, 공통 review·eligibleForNoteScoring, columns, groups. 그룹의 sourceId/category/technique/method/analysisBasis/filenamePrefix/rows 및 선택적 evidence·gestureDefaults |
| performances | datasetId, sourceHashes, performances(id/title/audio/scores), comparisonScores(id/forPerformanceId/sourceId/file/notation/pdfPages/coverage/대응 상태) |

`rawPath`는 `data/raw/...`, `annotationPath`는 `data/annotations/audio/<sourceId>-<설명>/labels.json` 하나만 기록합니다. 예를 들어 기본음은 `ngc-sanjo-2550-기본음/labels.json`, 기본음+요성은 `ngc-sanjo-2551-기본음+요성/labels.json`입니다. 숫자를 포함한 원본 ID는 그대로 유지하고 폴더에 읽기 쉬운 설명만 붙입니다. 기존 `<sourceId>/labels.json`도 읽을 수 있습니다. 폴더명을 ID로 쓰거나 제목에서 경로를 다시 만들지 않고 `annotationPath`를 읽습니다. annotation은 자기 원본 ID·해시만 참조할 수 있습니다. `sourceHashes`는 원음이 바뀌었는데 예전 라벨이나 악보 참조가 적용되는 일을 막습니다.

```json
"columns": ["id", "startSeconds", "endSeconds", "register", "yulmyeong", "labelF0Hz"]
```

위 여섯 열은 각각 한 번 포함하며 순서를 바꿀 수 있습니다. 깊은요성 한 행 예시:

```json
["ngc-sanjo-2552.group-001", 0.6, 7.1, "저취", "임", 269.8]
```

- 시각은 원음 기준 초, 시작 포함·끝 제외입니다. `round(초 × 원본 샘플레이트)`로 샘플 프레임을 정합니다. 대표 F0의 의미·대응 방법은 `analysisBasis`와 근거에 기록합니다.
- 각 행은 그룹 `method`와 파일 `review`를 상속하며 `evidence[id]`에서 예외를 덮어쓸 수 있습니다. `method`는 `manual` 또는 `tool:이름@버전`, `review`는 inferred/reviewed/rejected입니다. 같은 값을 매 행에 반복하지 않아도 펼친 결과에는 적용됩니다.
- 현재 산조의 method는 `tool:prepare_sanjo_assets@c163f2f`이며 기존 분석·준비 코드가 보존된 Git 버전을 가리킵니다. 과거 대응 방법 이름은 `analysisBasis`로 보존했습니다. 이번 이동에서 율명을 다시 추정하거나 reviewed로 승격하지 않았습니다.
- `eligibleForNoteScoring=true`는 reviewed일 때만 허용합니다. 현재 산조는 전부 inferred·false입니다. rejected 행은 전체/조건 선택에서 제외하고 ID로 직접 요청하면 오류로 알립니다.
- 복합음은 아래 안정음과 행의 도착음을 연결합니다. `gesture.analysisReview`에는 분석창 선택 이유를 두며 annotation 검수 상태인 `review`와 구분합니다. 기존 창·대안은 보존합니다.
- 파일은 `분류/주법/번호_표시이름.wav`입니다. filenamePrefix=none이면 번호를 빼고 rowNumber3이면 원래 그룹의 행 순번을 최소 세 자리로 붙입니다. 선택 전에 번호를 정하므로 일부만 저장해도 같습니다. 기존 행 순서를 보존합니다.
- 같은 분류·주법에 여러 원본이 있으면 해당 주법을 `분류/주법/sourceId/번호_표시이름.wav`로 구분합니다. 서로 다른 annotation 파일의 행도 합쳐 검사합니다. 두 번째 원본을 추가하면 기존 원본도 하위 폴더로 이동하므로 결과 catalog의 file을 사용합니다. 같은 원본·주법은 한 그룹에 모으고 반복음은 rowNumber3로 구분합니다.
- 같은 SHA의 동일 초 구간 또는 반올림 후 동일 프레임 구간은 ID·율명·주법이 달라도 오류입니다. 선택 원음의 미선택 라벨도 검사합니다. 다른 시각의 반복음과 서로 경계가 다른 일부 겹침은 자동 삭제하지 않습니다.
- lowerAnchor/destination의 windowsSeconds와 analysisReview.rejectedWindow는 원음 기준 절대초입니다. 숫자·길이·라벨 내부 범위·창 순서·겹침·반올림 후 빈 창을 검사합니다. 아래음과 도착음이 반복 중 교차하는 순서는 허용합니다.

새 악기는 `data/catalog/<악기 ID>/`에 sources·performances를, 라벨 대상 원음마다 `data/annotations/audio/<원본 ID>-<설명>/labels.json`을 작성하고 annotationPath로 연결합니다. 설명은 폴더 한 칸에 들어가는 이름이며 `/`·`\`와 운영체제 예약 문자는 사용할 수 없습니다. 실제 확보한 자료·측정한 구간·검수 상태를 기록하며 산조의 음 개수와 배열을 복사하지 않습니다. 같은 명령의 `--dataset`만 해당 catalog로 바꿉니다. 복합음 표시는 두 기준음 연결이므로 세 단계 이상의 이동 구조는 추가 검토가 필요합니다.

## 검증

```powershell
python -m unittest discover -s data/tests -p 'test_*.py' -v
```

합성 WAV·MP3와 임시 악보로 포맷·구간 저장·원본 복사·파일별 라벨 병합·선택 번호·참조 누락·중복·분석창·검수 상태·출력 보호를 검사합니다. 서버 경로도 별도 임시 루트로 시험합니다. 실제 다른 세 악기의 수집·라벨링이나 율명의 음악적 정답 검증을 뜻하지 않습니다.
