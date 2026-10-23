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

- 소스 코드, 데이터 형식·생성 절차 문서, 사용 가능한 작은 검증용 샘플은 Git으로 관리합니다. 처리 코드는 `data/src/`처럼 추적 가능한 경로에 둡니다.
- `data/raw/`는 로컬 원본 데이터, `data/processed/`는 중간 데이터·생성 데이터셋·대용량 결과물 경로이며 Git에서 제외합니다.
- 검증용 샘플은 `data/tests/fixtures/`처럼 제외되지 않는 경로에 둡니다.
- 공유할 데이터의 저장 위치, 버전, 재현 방법은 문서에 남깁니다. 원본 음원과 대용량 데이터 자체는 커밋하지 않습니다.

## 데이터 준비

악기마다 `data/datasets/<악기 ID>/` 아래 **세 파일**을 관리합니다. 현재 실제 자료는 산조대금만 들어 있습니다.

| 파일 | 편집할 내용 |
| --- | --- |
| [sources.json](datasets/sanjo-daegeum/sources.json) | 원음·악보 파일 위치, 출처, 해시, 이용 조건 |
| [labels.json](datasets/sanjo-daegeum/labels.json) | 기본음·시김새의 시작·끝, 취법·율명·주법, 저장 파일명 |
| [performances.json](datasets/sanjo-daegeum/performances.json) | 곡별 원음·오선보·정간보 연결과 대응 상태 |

공통 [export_dataset.py](src/export_dataset.py)가 세 파일을 읽습니다. 내보낼 때는 `labels.json`에 적힌 구간과 이름을 그대로 사용합니다. 분석 결과·운지표·별도 매핑 파일을 준비하거나 음높이를 다시 추정할 필요가 없습니다. 라벨을 생성·검토한 근거와 미완료 사항은 [산조대금 기록](../docs/knowledge/data/sanjo-daegeum.md)에 둡니다.

## 원본 준비와 실행

검증 환경은 Python 3.12.14·NumPy 2.3.5입니다. 저장소 루트에서 실행하며 필요한 외부 패키지는 NumPy 하나입니다.

```powershell
python -m pip install numpy==2.3.5
```

[국립국악원 단음 다운로드](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)의 관악기 → 산조대금 전체 9개를 받아, ZIP 안 원본을 `sources.json`의 `path`에 둡니다. 태평가 묶음에는 같은 명세의 표준악보집 PDF도 필요합니다. 다운로드 주소·SHA-256·이용 조건은 각 source에 있습니다. 선택한 구간만 저장할 때는 해당 원음만 있으면 됩니다.

전체 내보내기:

```powershell
python data/src/export_dataset.py --dataset data/datasets/sanjo-daegeum --output-dir data/processed/instruments/daegeum/sanjo/prepared-v2
```

저취 황 기본음 하나만 저장:

```powershell
python data/src/export_dataset.py --dataset data/datasets/sanjo-daegeum --label sanjo.jeochwi.hwang --output-dir data/processed/instruments/daegeum/sanjo/selected-hwang
```

주법·취법·율명으로 선택:

```powershell
python data/src/export_dataset.py --dataset data/datasets/sanjo-daegeum --technique '기본음+깊은요성' --register 저취 --yulmyeong 임 --output-dir data/processed/instruments/daegeum/sanjo/selected-deep-im
```

태평가 원음·악보만 묶기:

```powershell
python data/src/export_dataset.py --dataset data/datasets/sanjo-daegeum --performance ngc-sanjo-2625 --output-dir data/processed/instruments/daegeum/sanjo/selected-taepyeongga
```

`--label`과 `--performance`는 여러 번 지정할 수 있습니다. `--category`, `--technique`, `--register`, `--yulmyeong`은 정확히 일치하는 라벨을 고르며 여러 조건은 모두 만족해야 합니다. 선택 옵션이 없으면 전체를 생성합니다. 존재하지 않는 ID나 결과가 0개인 선택은 오류입니다.

**출력은 아직 없는 새 폴더를 지정합니다.** 재생성할 때는 다른 출력 이름을 사용합니다. 기존 폴더·원본은 덮어쓰거나 삭제하지 않습니다. 입력·해시·구간 검증과 임시 폴더의 생성이 모두 끝난 뒤 결과 폴더를 공개하므로 실패한 일부 결과가 완성본처럼 남지 않습니다.

## 결과 사용

- 산조 전체: 기본음계 46개·시김새 75개 WAV, 태평가 원음 1개, 원본 악보집 PDF 1개, 결과 목록 `catalog.json`.
- 개별 구간: 원본 샘플레이트를 유지하고 채널 평균·필요한 경우 피크 감쇠·PCM16 양자화만 적용합니다. 입력은 PCM16 또는 IEEE float32 RIFF/WAVE를 지원합니다. 음높이 보정·리샘플링은 하지 않습니다.
- 연주 음원·악보: **원본 바이트 그대로 복사**합니다. 같은 PDF의 두 쪽을 참조하면 파일은 한 번만 복사합니다.
- 결과 catalog는 선택된 라벨·프레임 경계·변환·해시, 연주 묶음, 출처·이용 조건을 포함합니다. 편집 입력은 세 JSON이며 결과 catalog를 수정해 재사용하지 않습니다.

태평가 악보는 성악 오선보와 단소 정간보 일부입니다. 해당 산조 녹음 전곡과 같은 판본으로 확정하지 않았습니다. 출처의 비상업·무변경 조건과 각 악보의 `coverage`, `samePerformanceEditionVerified`를 확인합니다.

## 세 파일의 형식과 악기 추가

세 파일 모두 `schemaVersion: 1`과 같은 `datasetId`를 사용합니다. 악기별 음 개수·율명 배열·취법 이름은 코드에 고정하지 않습니다.

| 파일 | 주요 필드 |
| --- | --- |
| `sources.json` | `pathBase: "repository_root"`, `instrument`, `sources` 목록. 각 source의 `id`, `kind`(audio/score), `path`, `sha256`, `title`, `sourceUrl`, `license`, `attribution`. PDF는 `pdfPageCount` |
| `labels.json` | `sourceHashes`, `labels` 목록. 각 label의 `id`, `sourceId`, `startSeconds`, `endSeconds`, `category`, `technique`, `register`, `yulmyeong`, `displayLabel`, `file`, `labelStatus` |
| `performances.json` | `sourceHashes`, `performances` 목록. 각 performance의 `id`, `title`, `audio`(sourceId/file), `scores` 목록(sourceId/file/notation/pdfPages/coverage/대응 상태) |

시각은 초 단위, 시작 포함·끝 제외이며 `round(초 × 원본 샘플레이트)`로 프레임을 정합니다. `labels.json`의 시각이 자르는 기준입니다. 같은 음을 두 번 발음하면 서로 다른 ID와 파일명을 유지합니다. 다루치기 같은 복합음은 `displayLabel`·선택 필드 `gesture`에 시작→도착음을 적고 상위 율명의 의미도 남깁니다. 근거·대안·참고 운지는 `evidence`에 선택적으로 기록하며 내보내기에 그대로 전달합니다.

`sourceHashes`는 해당 목록이 참조하는 원본 ID → SHA-256입니다. 원음을 바꾸면 기존 라벨·악보 쪽 참조를 그대로 적용하지 못하도록 버전을 확인합니다. 경로는 `/`로 구분한 상대 경로를 사용합니다. 원본 path는 저장소 루트 기준, 출력 file은 지정한 출력 폴더 기준입니다. 중복 ID·출력 경로, 대소문자 충돌, 상위 폴더 탈출, 잘못된 구간·PDF 쪽을 거부합니다.

새 악기를 추가할 때:

1. `data/datasets/sogeum/`, `danso/`, `jeongak-daegeum/`처럼 폴더를 만들고 같은 세 파일을 작성합니다. 아직 없는 라벨·연주 목록은 빈 배열로 둡니다.
2. 실제 확보한 원음과 악보의 출처·해시를 sources에 기록합니다. 악기마다 측정·검토한 구간과 율명을 labels에 작성하며, 산조의 음 개수·배열을 복사하지 않습니다.
3. 연주와 실제 확보한 악보를 performances에 연결합니다. 두 악보가 모두 없더라도 확보 범위와 대응 상태를 정확히 적습니다.
4. 같은 명령에 `--dataset` 경로만 바꿉니다. 공통 코드를 수정할 필요가 없습니다. 별도 저장 위치를 기준으로 삼을 때는 `--root`로 원본 경로의 기준을 지정합니다.

실제 소금·단소·정악대금 데이터는 이번 변경에 추가하지 않았습니다. 합성 입력으로 악기 ID를 바꾼 경우의 공통 처리만 검증합니다.

## 검증

```powershell
python -m unittest discover -s data/tests -p 'test_*.py' -v
```

검사는 외부 다운로드 없이 작은 합성 WAV·임시 악보를 사용합니다. 다른 악기 ID, 직접 지정한 라벨·구간, 같은 음 반복, 선택 추출, 원음·PDF 무변경 복사, 입력 오류·경로 충돌·출력 보호를 확인합니다. 실제 율명의 음악적 정답 검증과 구분합니다.
