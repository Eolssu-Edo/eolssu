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
| [labels.json](datasets/sanjo-daegeum/labels.json) | 주법별 기본음·시김새의 구간·취법·율명·대표 주파수와 예외 검수 근거 |
| [performances.json](datasets/sanjo-daegeum/performances.json) | 곡별 원음·오선보·정간보 연결과 대응 상태 |

공통 [export_dataset.py](src/export_dataset.py)가 세 파일을 읽습니다. `labels.json`의 열·행을 풀어 지정된 구간을 자르고, 취법·율명으로 표시 이름과 파일명을 생성합니다. 음높이를 다시 추정할 필요가 없습니다. 이 형식은 데이터 준비 코드의 내부 입력이며 국립국악원 공식 표준이나 다른 파트와 확정한 API 계약은 아닙니다. 라벨을 생성·검토한 근거와 미완료 사항은 [산조대금 기록](../docs/knowledge/data/sanjo-daegeum.md)에 둡니다.

## 원본 준비와 실행

검증 환경은 Python 3.12.14·NumPy 2.3.5입니다. 저장소 루트에서 실행하며 필요한 외부 패키지는 NumPy 하나입니다.

```powershell
python -m pip install numpy==2.3.5
```

[국립국악원 단음 다운로드](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)의 관악기 → 산조대금 전체 9개를 받아, ZIP 안 원본을 `sources.json`의 `path`에 둡니다. 태평가 묶음에는 같은 명세의 표준악보집 PDF도 필요합니다. 다운로드 주소·SHA-256·이용 조건은 각 source에 있습니다. 선택한 구간만 저장할 때는 해당 원음만 있으면 됩니다.

전체 내보내기:

```powershell
python data/src/export_dataset.py --dataset data/datasets/sanjo-daegeum --output-dir data/processed/instruments/daegeum/sanjo/prepared-v3
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

세 파일은 같은 `datasetId`를 사용합니다. `sources.json`·`performances.json`은 `schemaVersion: 1`, 열·행 구조인 `labels.json`은 `schemaVersion: 2`입니다. 이전 labels v1은 읽지 않습니다. 악기별 음 개수·율명 배열·취법 이름은 코드에 고정하지 않습니다.

| 파일 | 주요 필드 |
| --- | --- |
| `sources.json` | `pathBase: "repository_root"`, `instrument`, `sources` 목록. 각 source의 `id`, `kind`(audio/score), `path`, `sha256`, `title`, `sourceUrl`, `license`, `attribution`. PDF는 `pdfPageCount` |
| `labels.json` | `sourceHashes`, 공통 `labelStatus`·`eligibleForNoteScoring`, `columns`, 주법별 `groups`. 각 그룹의 원본·분류·주법·분석 방법·파일번호 규칙·`rows`, 선택적인 `evidence`·`gestureDefaults` |
| `performances.json` | `sourceHashes`, `performances` 목록. 각 performance의 `id`, `title`, `audio`(sourceId/file), `scores` 목록(sourceId/file/notation/pdfPages/coverage/대응 상태) |

`columns`는 파일 상단에 한 번 적습니다. 아래 여섯 열을 각각 한 번 포함하며 순서는 바꿀 수 있습니다. 각 행의 길이와 값의 자료형을 검사한 뒤 열 이름으로 해석합니다. 원본·주법·방법은 그룹마다 한 번, 기본 상태는 파일마다 한 번 기록합니다.

```json
"columns": ["id", "startSeconds", "endSeconds", "register", "yulmyeong", "labelF0Hz"]
```

깊은요성 그룹의 한 행 예시:

```json
["ngc-sanjo-2552.group-001", 0.6, 7.1, "저취", "임", 269.8]
```

- 시각은 초 단위, 시작 포함·끝 제외입니다. `round(초 × 원본 샘플레이트)`로 프레임을 정하며 길이는 끝−시작으로 계산합니다.
- `labelF0Hz`는 라벨 대응에 사용한 대표 기본주파수(Hz)입니다. 그룹 `method`와 예외 `evidence[id].method`로 방법을 구분합니다. 깊은요성 복구 음은 재분석 중앙값, 다루치기는 도착 안정창의 가중 중앙값입니다. 모든 값이 산술평균이라는 뜻은 아닙니다.
- 표시 이름은 `취법 율명`입니다. 복합음은 `evidence[id].gesture.lowerAnchor`의 아래음과 행의 도착음을 연결합니다. 도착음 이름·F0는 행에서 복원하고, 공통 반복 수·해석은 `gestureDefaults`에 둡니다.
- 파일은 `분류/주법/번호_표시이름.wav`입니다. 공백은 `_`, 화살표는 `-`로 바꿉니다. `filenamePrefix: "none"`이면 번호를 빼고, `"rowNumber3"`이면 원래 그룹 내 행 순번을 최소 세 자리로 붙입니다. **선택 전에 번호를 정하므로 일부만 저장해도 기존 번호를 유지합니다.** 행을 재정렬하거나 중간에 삽입하면 그 뒤의 파일번호가 바뀌므로 기존 순서를 보존합니다. ID는 번호에서 추론하거나 바꾸지 않습니다.
- 같은 음의 별도 발음은 서로 다른 ID·행으로 유지합니다. `evidence`는 행 ID로 찾는 예외 기록이며 병합·반복·음렬 판단·애매한 후보·복합음 선택창만 남깁니다. 상태가 다른 행은 그 기록에서 `labelStatus`·`eligibleForNoteScoring`을 덮어쓸 수 있습니다.
- 실제 운지 확인 상태·참고 운지 필드는 제거했습니다. 주파수·분할·율명 판단의 검토 근거는 유지합니다. `inferred`와 채점 미승인 상태는 그대로이며, 구조 축약이 음악적 정답 검증을 뜻하지 않습니다.

AI에 일부를 검토시킬 때는 공통 열·상태·원본 해시와 해당 그룹만 전달하면 됩니다. 한 행을 한 줄로 저장하되 긴 설명을 매 행에 반복하지 않습니다. 문자·바이트 감소율을 토큰 감소율로 간주하지 않습니다.

`sourceHashes`는 해당 목록이 참조하는 원본 ID → SHA-256입니다. 원음을 바꾸면 기존 라벨·악보 쪽 참조를 그대로 적용하지 못하도록 버전을 확인합니다. 경로는 `/`로 구분한 상대 경로를 사용합니다. 원본 path는 저장소 루트 기준, 출력 file은 지정한 출력 폴더 기준입니다. 중복 ID·열·출력 경로, 알 수 없는 열·행 ID의 근거, 행 길이·자료형 오류, 대소문자 충돌, 상위 폴더 탈출, 잘못된 구간·PDF 쪽을 거부합니다.

새 악기를 추가할 때:

1. `data/datasets/sogeum/`, `danso/`, `jeongak-daegeum/`처럼 폴더를 만들고 같은 세 파일을 작성합니다. 아직 없는 라벨은 `groups: []`, 연주는 `performances: []`로 둡니다. 공통 열·상태는 유지합니다.
2. 실제 확보한 원음과 악보의 출처·해시를 sources에 기록합니다. 악기마다 측정·검토한 구간과 율명을 labels에 작성하며, 산조의 음 개수·배열을 복사하지 않습니다.
3. 연주와 실제 확보한 악보를 performances에 연결합니다. 두 악보가 모두 없더라도 확보 범위와 대응 상태를 정확히 적습니다.
4. 같은 명령에 `--dataset` 경로만 바꿉니다. 공통 코드를 수정할 필요가 없습니다. 별도 저장 위치를 기준으로 삼을 때는 `--root`로 원본 경로의 기준을 지정합니다.

실제 소금·단소·정악대금 데이터는 이번 변경에 추가하지 않았습니다. 합성 입력으로 악기 ID를 바꾼 경우의 공통 처리만 검증합니다.

## 검증

```powershell
python -m unittest discover -s data/tests -p 'test_*.py' -v
```

검사는 외부 다운로드 없이 작은 합성 WAV·임시 악보를 사용합니다. 다른 악기 ID, 열 순서 변경·잘못된 행, 직접 지정한 라벨·구간, 같은 음 반복·선택 후 번호 유지, 복합음·예외 근거, 원음·PDF 무변경 복사, 입력 오류·경로 충돌·출력 보호를 확인합니다. 실제 율명의 음악적 정답 검증과 구분합니다.
