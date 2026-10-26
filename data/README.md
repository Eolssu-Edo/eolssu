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
│   ├── <datasetId>/         # 데이터셋별 sources.json과 데이터셋 안의 연주·악보 짝(performances.json).
│   └── works/               # 데이터셋이 다른 악보·음원·MIDI를 같은 곡으로 묶는 목록. 형식은 첫 묶음 때 정합니다.
├── annotations/             # 원본에 덧붙인 판단 값: 사람 입력과 도구 추정, 검수 상태.
│   ├── scores/<score-id>/   # 쪽별 악보·설명 영역 좌표.
│   └── audio/<악기 ID>/<audio-id>-<설명>/ # 악기별 원음의 구간·취법·율명·대표 F0 라벨.
├── src/                     # 가공 코드. 결과는 processed/에만 씁니다.
│   ├── scores/              # PDF → 쪽 그림 → 영역 자르기 → 음표 데이터.
│   ├── audio/               # 분석용 변환 → 구간 자르기 → 악보와 시각 맞추기.
│   ├── midi/                # MIDI → 음표 이벤트 목록.
│   └── catalog/             # 목록과 실제 파일의 SHA-256 대조.
├── tests/fixtures/          # 테스트에 쓰는 작은 고정 샘플.
├── mart/                    # 집계 DB 스키마.
├── raw/                     # 받은 원본 그대로. Git 제외.
│   ├── aihub/<데이터셋 번호>/ # AI-Hub에서 받은 구조 그대로. MID·WAV는 01.원천데이터, JSON은 02.라벨링데이터.
│   ├── instruments/<악기>/  # 악기별 원음과 함께 받은 악보 PDF.
│   └── scores/<묶음>/       # 팀원이 모은 정간보 PDF·MusicXML.
└── processed/               # raw·src·annotations로 다시 만드는 결과. Git 제외.
```

- **원본**: `raw/`의 파일은 받은 그대로 두고 수정·덮어쓰기·삭제하지 않습니다. 고친 자료는 새 ID로 추가합니다. 묶음으로 받은 자료(예: AI-Hub)는 받은 폴더 구조를 유지합니다. MID와 JSON처럼 형식이 달라도 따로 옮기지 않고 파일 이름으로 짝을 맞춥니다. 압축을 푼 결과는 `processed/` 아래에 같은 구조로 둡니다.
- **곡 묶음**: 데이터셋 안의 연주·악보 짝은 `performances.json`에, 데이터셋이 다른 자료끼리의 묶음은 `catalog/works/`에 둡니다. 묶음도 원본 ID와 SHA-256으로 가리키고, 같은 판본인지 확인했는지 적습니다.
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

목록 전체를 한 번에 대조할 때는 아래를 실행합니다. 목록에 있는데 없거나 크기·SHA-256이 다른 파일을 알려 주고, 하나라도 있으면 종료 코드 1로 끝납니다. `--dataset`을 생략하면 `data/catalog/` 아래 모든 데이터셋을 대조하므로, 내려받은 데이터셋만 고릅니다.

```bash
python data/src/catalog/verify_sources.py --dataset aihub-71470 aihub-71955 collected-scores
```

`/data/raw`는 웹으로 열지 않고 SSH 키로만 접근합니다.

## 데이터 준비

악기별 라벨 자료는 `data/catalog/<악기 ID>/`와 `data/annotations/audio/<악기 ID>/`에 둡니다. AI-Hub와 팀이 모은 악보는 원본 목록만 있습니다. 확보 범위와 검수 근거는 [데이터 조사 목록](../docs/README.md#데이터-조사검증-기록)에서 확인합니다. 아래 산조대금 예시처럼 공통 JSON 세 종류를 팀 폴더 규칙에 맞춰 나눕니다.

| 위치 | 편집할 내용 |
| --- | --- |
| [catalog/sanjo-daegeum/sources.json](catalog/sanjo-daegeum/sources.json) | 원음 9개·악보집 1개의 원본 ID, rawPath, 출처·이용 조건·SHA-256, 필요한 annotationPath |
| [annotations/audio/sanjo-daegeum/](annotations/audio/sanjo-daegeum/) 아래 `<원본 ID>-<설명>/labels.json` | 악기별 원음의 구간·취법·율명·대표 기본주파수(F0)와 판단 근거. 산조는 8파일·121행 |
| [catalog/sanjo-daegeum/performances.json](catalog/sanjo-daegeum/performances.json) | 연주 원음, 확인된 대응 악보, 별도로 구분한 비교용 악보 참조 |
| [catalog/aihub-71470/sources.json](catalog/aihub-71470/sources.json) | AI-Hub 국악 악보 및 음원 데이터 압축 20개(MID·WAV 원천, JSON 라벨). 압축 안 파일 수는 `contents` |
| [catalog/aihub-71955/sources.json](catalog/aihub-71955/sources.json) | AI-Hub 국악 합주곡 디지털 음원 데이터 중 받은 1개. 음원 압축은 다시 받은 뒤 추가 |
| [catalog/collected-scores/sources.json](catalog/collected-scores/sources.json) | 팀원이 모은 정간보 PDF 18개와 정간보·MusicXML 묶음 2개. 출처 확인 여부는 `sourceVerified` |

`aihub-71470`, `aihub-71955`, `collected-scores`는 원본 목록(`sources.json`)만 있어 `export_dataset.py`의 `--dataset` 대상이 아닙니다. 라벨과 연주 묶음을 만들면 그때 `performances.json`과 annotation을 추가합니다.

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

sources는 schema v2·v3, performances는 v2, 원음별 labels는 v3입니다. 출력 catalog는 v2를 유지합니다. 이전의 `data/datasets/` 3파일 배치와 구형 schema를 읽는 경로는 제거했습니다. 열·행 구조, 라벨 ID·구간·율명·F0·출력 WAV 파일명은 보존합니다.

| 문서 | 주요 필드 |
| --- | --- |
| sources | datasetId, instrument, pathBase=repository_root, sources. v3는 sourceProfiles와 선택적 sources[].profile. 펼친 원본마다 id/kind/rawPath/sha256/title/sourceUrl/license/attribution. 악보는 pdfPageCount, 라벨 대상 원음은 annotationPath |
| 원음별 labels | datasetId, 해당 원음 하나의 sourceHashes, 공통 review·eligibleForNoteScoring, columns, groups. 그룹의 sourceId/category/technique/method/analysisBasis/filenamePrefix/rows 및 선택적 evidence·gestureDefaults |
| performances | datasetId, sourceHashes, performances(id/title/audio/scores), comparisonScores(id/forPerformanceId/sourceId/file/notation/pdfPages/coverage/대응 상태) |

sources v3는 같은 JSON의 `sourceProfiles` 객체에 공통 출처 메타데이터를 두고 원본의 `profile` 문자열로 참조합니다. 단소의 음원 32개는 `ngc-danso-audio`를 공유하며 권리 조건이 다른 PDF는 개별 객체로 둡니다. [공통 로더](src/catalog/source_catalog.py)를 exporter와 원본 검증 CLI가 함께 사용합니다. 읽을 때 프로필을 펼치므로 출력 `catalog.sources`에는 기존 전체 원본 객체만 남고 `profile`은 남지 않습니다. `manifestHashes`는 실제 입력 파일의 해시이므로 입력을 압축하면 바뀝니다.

- 개별 원본에 같은 필드가 있으면 그 값을 우선합니다. 중첩 객체도 통째로 교체하며 명시적 `null`은 프로필 값으로 채우지 않습니다. 원본마다 독립된 객체로 펼칩니다.
- 프로필 허용 필드는 `kind`, `sourceUrl`, `downloadUrl`, `publisher`, `license`, `attribution`, `verifiedOn`, `redistribution`, `instrument`, `instrumentCode`, `channels`, `category`, `collection`, `sourceVerified`, `serviceDatasetApproved`, `notes`입니다. ID·제목·경로·SHA-256·크기·원본 항목 번호·길이·샘플레이트·PDF 쪽수 등 고유 값은 개별 원본에 둡니다.
- 프로필 이름과 참조는 비어 있지 않은 문자열이어야 하며 같은 파일에 정의된 객체만 참조합니다. 재귀 상속, 알 수 없는 프로필·필드, 잘못된 객체, v2의 `sourceProfiles`·`profile` 혼용은 오류입니다. v3의 `sourceProfiles`는 필요하며, 빈 객체로 두고 모든 원본을 개별 작성할 수 있습니다.
- 원본 목록 검증은 기존 AI-Hub·수집 악보의 권리 미확인 `null`을 보존합니다. exporter는 펼친 원본의 필수 `title`·`license`·`sourceUrl`·`attribution`이 비어 있거나 `null`이면 내보내기를 거부합니다.

`rawPath`는 `data/raw/...`, `annotationPath`는 `data/annotations/audio/<datasetId>/<sourceId>-<설명>/labels.json` 하나만 기록합니다. 악기 폴더는 catalog JSON의 `datasetId`와 같아야 합니다. 산조 기본음의 전체 경로는 `data/annotations/audio/sanjo-daegeum/ngc-sanjo-2550-기본음/labels.json`입니다. 숫자를 포함한 원본 ID는 그대로 유지하고 폴더에 읽기 쉬운 설명만 붙입니다. 같은 악기 폴더 안에서는 설명 없는 `<sourceId>/labels.json`도 읽을 수 있지만 악기 폴더를 생략한 이전 경로는 허용하지 않습니다. 폴더명을 ID로 쓰거나 제목에서 경로를 다시 만들지 않고 `annotationPath`를 읽습니다. annotation은 자기 데이터셋·원본 ID·해시만 참조할 수 있습니다. `sourceHashes`는 원음이 바뀌었는데 예전 라벨이나 악보 참조가 적용되는 일을 막습니다.

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

새 악기는 `data/catalog/<악기 ID>/`에 sources·performances를, 라벨 대상 원음마다 `data/annotations/audio/<악기 ID>/<원본 ID>-<설명>/labels.json`을 작성하고 annotationPath로 연결합니다. 악기 ID는 JSON의 datasetId와 맞춥니다. 산조대금은 `sanjo-daegeum`, 소금은 `sogeum`, 단소는 `danso`, 정악대금은 `jeongak-daegeum`을 사용합니다. 악기 ID와 설명은 각각 폴더 한 칸에 들어가는 이름이며 `/`·`\`와 운영체제 예약 문자는 사용할 수 없습니다. 실제 확보한 자료·측정한 구간·검수 상태를 기록하며 산조의 음 개수와 배열을 복사하지 않습니다. 같은 명령의 `--dataset`만 해당 catalog로 바꿉니다. 복합음 표시는 두 기준음 연결이므로 세 단계 이상의 이동 구조는 추가 검토가 필요합니다.

선택 필드 `evidence[id].sequenceDecision.orderedAnchors`는 한 발음 안의 안정음 전체 순서를 보존합니다. 각 원소에는 `register`, `yulmyeong`, 양의 유한수 `f0Hz`, 원음 기준 절대초 `windowsSeconds`를 기록합니다. 창은 라벨 내부에 있어야 하고, 원소 안과 원소 사이에서 시간순·비겹침·반올림 후 비어 있지 않은 실제 프레임 범위를 만족해야 합니다. `labelAnchorIndex`는 0부터 시작하는 정수로, 선택한 안정음의 취법·율명·F0가 행의 `register`·`yulmyeong`·`labelF0Hz`와 같아야 합니다. 행과 파일 표시는 선택한 안정음으로 유지하며 전체 이동 순서를 두 기준음 `gesture`로 축약하지 않습니다. 기존 자유형 문자열이나 `orderedAnchors`가 없는 `sequenceDecision` 객체는 그대로 허용합니다.

## 정간보 OMR

정간보 PDF 한 쪽을 [MALerLab/jeongganbo-omr](https://github.com/MALerLab/jeongganbo-omr)로 읽어 JG-like 텍스트로 쓰고, [jeongganbo-generator](https://github.com/danbinaerinHan/jeongganbo-generator)의 변환 도구로 우물사이 `.jgb.json`을 만듭니다. 시험 결과와 한계는 [정간보 OMR 시험 기록](../docs/knowledge/data/jeongganbo-omr.md)을 확인합니다. 결과는 사람이 `boxes.png`로 잘린 칸을 확인한 뒤 씁니다.

두 저장소는 이 저장소 밖에 받습니다. Windows는 경로가 길면 클론이 실패하므로 짧은 폴더를 씁니다.

```bash
cd ~/omr
git clone https://github.com/MALerLab/jeongganbo-omr.git
git clone https://github.com/danbinaerinHan/jeongganbo-generator.git
cd jeongganbo-omr
curl -L -o checkpoints.tar.gz https://github.com/MALerLab/jeongganbo-omr/releases/download/v1.0.0/checkpoints.tar.gz
tar -xzf checkpoints.tar.gz
uv venv -p 3.12 .venv
uv pip install -p .venv/Scripts/python.exe torch torchvision --index-url https://download.pytorch.org/whl/cpu
uv pip install -p .venv/Scripts/python.exe x-transformers==1.30.20 einops omegaconf hydra-core opencv-contrib-python pandas tqdm matplotlib pdfplumber
```

저장소 루트에서 실행합니다. `--beats`는 한 각의 정간 수, `--dpi`는 정간 너비가 85~110px가 되는 값입니다(e-국악아카데미 교실 악보 150, 국악원 정악보 300). Windows는 `PYTHONUTF8=1`이 있어야 모델 어휘 파일을 읽습니다.

```bash
PYTHONUTF8=1 ~/omr/jeongganbo-omr/.venv/Scripts/python.exe data/src/scores/jeongganbo_omr.py "data/raw/scores/ngc-easy-danso-06/[악보](초급)세상에서가장쉬운단소교실_06차시_아리랑.pdf" 0 data/processed/scores/omr/ngc-easy-danso-06 --omr-dir ~/omr/jeongganbo-omr --dpi 150 --beats 3 --title 아리랑
node ~/omr/jeongganbo-generator/tools/import-ngc-omr.mjs data/processed/scores/omr/ngc-easy-danso-06 data/processed/scores/omr/ngc-easy-danso-06/jgb --verify
```

처음 실행하면 체크포인트의 옛 LayerNorm 이름을 바꾼 `model_weight-names.pt`를 OMR 폴더에 만듭니다. 정간 수가 장단 × 각 수와 다르면 칸 자르기가 실패한 것입니다. 율명이 하나도 없는 열은 장단 열로 보고 뺍니다. 세로선이 연하거나 끊긴 스캔에서 칸이 합쳐지면 `--gap-fill 10 --line-threshold 250`을 더합니다. 칸 찾기 그림에만 적용되고 율명 인식은 원래 그림으로 합니다. 그래도 빠진 정간은 `--fill-grid`로 옆 열의 줄 위치에서 채웁니다(장단 열 제외, 먹이 있는 자리만).

교과서 표준악보집처럼 PDF에 율명이 글자로 들어 있으면 OMR 없이 글자·선 좌표로 읽습니다. 저장소의 Python만 있으면 되고(`pymupdf`), 한 열을 한 각으로 씁니다. 결과는 같은 변환 도구로 `.jgb.json`을 만듭니다. 두 경로의 곡별 결과는 [단소 악보 변환 기록](../docs/knowledge/data/danso-scores-umulsai.md)을 확인합니다.

```bash
python data/src/scores/standard_score_text.py "data/raw/scores/ngc-standard-2022-p176/태평가_정간보_단소_표준악보집-PDF176.pdf" data/processed/scores/danso-115/std/ngc-standard-2022-p176 --title 태평가
```

`report.json`의 `jeonggans`가 열마다 같은 수인지, `ignored`에 시김새 외의 글자가 없는지 확인합니다. 오선보처럼 정간보가 아닌 PDF는 오류로 끝납니다.

### 검토 화면

두 스크립트의 출력 폴더(`page.png`·`cells.json`)와 처음 변환한 `.jgb.json`을 곡마다 HTML 한 장으로 묶습니다. 브라우저로 열면 악보 그림 위의 정간 상자, 고르는 정간의 확대 그림, 결과 입력칸과 미리보기, 곡 전체 각 목록이 한 화면에 있습니다. OMR 신뢰도 0.2 미만은 빨강, 0.5 미만은 주황 테두리입니다. 고친 내용은 브라우저에 자동 저장되고, **.jgb.json 저장**으로 우물사이 문서를, **검토 기록 저장**으로 정간별 처음 결과·고친 결과·확인 여부를 내려받습니다. 빠진 정간은 **뒤에 빈 정간 넣기**로 채웁니다.

편집은 우물사이와 같습니다.
- **문법**: 칸 안에서 띄어 쓰면 위→아래로 줄이 나뉘고, 이어 쓰면 한 줄에 왼→오른쪽으로 들어갑니다(`청황 -청무 청태`). `-` 이음, `쉼` 쉼표, 음 뒤 `{흘림표}` 시김새, 음 뒤 `<` 숨표.
- **팔레트**: 위쪽 **율 | 시김새** 탭. 누르면 입력칸 커서 자리에 들어가고, 선택한 글자가 있으면 바꿉니다. 율 탭은 이 곡에 나온 율명과 5옥타브 × 12율을 한자·한글로, 시김새 탭은 우물사이 기호 그림(`--umulsai` 폴더의 `js/symbols-data.js`, MIT)으로 보여 주어 이름을 몰라도 모양으로 고릅니다.
- **미리보기**: 율명은 한자와 한글, 시김새는 그림으로 보입니다. 글자를 누르면 입력칸에서 그 글자가 선택되어 팔레트로 바로 바꿉니다.
- **이동**: `Enter` 확인하고 다음, `Tab`·`↓` 다음, `Shift+Tab`·`↑` 이전, `Ctrl ←`·`Ctrl →` 옆 각의 같은 순번 정간.

```bash
python data/src/scores/review_page.py data/processed/scores/danso-115/omr/* data/processed/scores/danso-115/std/* --jgb data/processed/scores/danso-115/jgb --umulsai ~/omr/jeongganbo-generator --out data/processed/scores/danso-115/review
```

HTML에 원본 악보 그림이 들어가므로 공유 위치에 올리지 않습니다. 문서의 레이아웃·대강은 처음 변환한 `.jgb.json`을 그대로 쓰고 선율·각 수·각별 정간 수만 바꿉니다.

## 검증

```powershell
python -m unittest discover -s data/tests -p 'test_*.py' -v
```

합성 WAV·MP3와 임시 악보로 포맷·구간 저장·원본 복사·파일별 라벨 병합·선택 번호·참조 누락·중복·분석창·검수 상태·출력 보호를 검사합니다. 서버 경로도 별도 임시 루트로 시험합니다. 이 회귀 검사는 실제 악기 데이터의 수집·라벨링 검증이나 율명의 음악적 정답 검증을 대신하지 않습니다. 실제 원음을 사용한 결과는 각 악기의 검수 기록을 확인합니다.

sources v2·v3의 펼친 원본 및 출력 바이트 동등성, 프로필 참조·개별 값 우선 적용·명시적 `null`·객체 독립성도 검사합니다. 원본 검증 CLI는 임시 원본으로 기존 목록과 프로필 목록의 성공·파일 누락·크기·해시 오류 처리를 확인합니다.
