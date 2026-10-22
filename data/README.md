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

## 실행과 검증

[산조대금 자료 안내](../docs/knowledge/data/instruments/daegeum/sanjo/README.md)에 입력 9개, 율명 연결 근거와 이용 조건을 정리했습니다. [국립국악원 단음 다운로드](https://www.gugak.go.kr/digitaleum/front/monotone/list.do)에서 관악기 → 산조대금 전체 9개를 선택하여 다운로드하고 ZIP 안 원본 WAV를 `collection-manifest.json`의 `rawPath`에 배치합니다. 압축 파일·원음·PDF는 커밋하지 않습니다.

Python 환경에 필요한 NumPy를 설치한 뒤 아래 순서로 실행합니다. 예: `python -m pip install numpy==2.3.5`(Python 3.12 검증 환경). 합성 입력 단위 테스트는 원음 다운로드 없이 실행할 수 있습니다.

### 산조대금 기본음 탐색 분석

`src/analyze_basic_tones.py`는 로컬 WAV의 SHA-256을 manifest와 대조한 뒤 발음 후보 구간과 F0를 자동 추정합니다. Python 3.10 이상과 NumPy가 필요합니다. 이번 실행 환경은 Python 3.12.14·NumPy 2.3.5이며, 프로젝트 전체의 패키지·추론 방식 채택을 뜻하지 않습니다.

저장소 루트에서 실행합니다. 원본은 Git에 포함되지 않으므로 [산조대금 원본 명세](../docs/knowledge/data/instruments/daegeum/sanjo/collection-manifest.json)의 파일을 manifest의 `rawPath`에 준비합니다. 현재 도구는 단일 샘플 manifest, 48kHz RIFF/WAVE의 float32 또는 PCM16 입력만 처리합니다.

```powershell
python data/src/analyze_basic_tones.py --manifest docs/knowledge/data/instruments/daegeum/sanjo/sample-manifest.json --output-dir data/processed/instruments/daegeum/sanjo/basic-tone-v1
python -m unittest discover -s data/tests -p 'test_*.py' -v
```

`python`이 PATH에 없으면 설치된 Python 실행 파일의 절대 경로로 실행합니다. 이 기본음 분석에는 NumPy 외의 디코더가 필요하지 않습니다.

결과는 `frames.csv`(시간·RMS·F0·주기성), `segments.csv`(후보 구간 요약), `analysis.json`(원본 해시·출처·환경·설정·결과·한계)입니다. 출력 폴더의 동명 결과는 재실행 시 갱신되며 입력 음원은 수정하지 않습니다. 기본 출력 경로는 Git 제외 대상입니다. 공유 결과와 해석은 [산조대금 분석](../docs/knowledge/data/instruments/daegeum/sanjo/basic-tone-analysis.md)에 둡니다.

합성음 검사는 알려진 기본주파수·강한 배음·무음·짧은 잡음 구간과 float WAV 읽기를 확인합니다. 실제 음원의 정확도 검사는 구간 청취와 율명·취법·F0 전문가 검수가 필요하며, 자동 결과를 정답 데이터로 쓰지 않습니다.

정제·변환은 작은 고정 샘플로 먼저 검증하고, 전체 데이터셋 처리는 입력·처리량·실행 시간·결과 버전을 확인할 수 있게 구성합니다.

### 산조대금 전체 9개 분석

[원본 전체 명세](../docs/knowledge/data/instruments/daegeum/sanjo/collection-manifest.json)의 9개 파일을 준비하고 위의 산조 기본음 분석을 먼저 실행합니다. 전체 분석은 그 기본음의 실측 결과를 참조합니다. Python·NumPy로 산조 9개를 처리하며 44.1kHz PCM16도 원래 샘플레이트로 분석합니다.

```powershell
python data/src/analyze_digital_collection.py --manifest docs/knowledge/data/instruments/daegeum/sanjo/collection-manifest.json --output-dir data/processed/instruments/daegeum/sanjo/probe --only ngc-sanjo-2625
python data/src/analyze_digital_collection.py --manifest docs/knowledge/data/instruments/daegeum/sanjo/collection-manifest.json --output-dir data/processed/instruments/daegeum/sanjo/collection-v1
```

첫 명령은 작은 범위 검증용입니다. 전체 실행은 9개 약 899초·310.2MiB 입력에서 이번 환경 기준 약 7.288초였습니다. 원본 해시 불일치·형식 오류를 항목별 실패로 기록하고 한 항목이라도 실패하면 종료 코드 1을 반환합니다. 프레임·발음 구간·짧은 발음·RMS·상세 JSON과 summary.json을 생성합니다. 출력은 재실행 시 갱신됩니다.

MP3를 별도로 분석하는 경우에만 miniaudio 1.71이 필요합니다. 이번 산조 9개에는 쓰지 않습니다. 조사 중 디코더를 시험한 환경은 Git 제외 경로 data/processed/instruments/daegeum/analysis-runtime에 격리했으며 프로젝트·사용자의 공통 Python 설정을 변경하지 않았습니다. 실제 데이터 확보·측정값·방법·한계는 [전체 보고서](../docs/knowledge/data/instruments/daegeum/sanjo/collection-analysis.md)를 따릅니다.

한글 율명 매핑의 근거가 된 기본음↔요성 음군 대응은 아래 명령으로 재생성합니다. 전체 분석 결과를 읽고 대응 JSON만 갱신합니다. 검토된 율명 매핑 파일은 자동 변경하지 않습니다. 한글 라벨은 `note-fingering-map.json`에 기록하며 추정 상태를 유지합니다.

```powershell
python data/src/link_sanjo_basic_notes.py --reference-analysis data/processed/instruments/daegeum/sanjo/collection-v1/ngc-sanjo-2551/analysis.json --mapping docs/knowledge/data/instruments/daegeum/sanjo/note-fingering-map.json --output docs/knowledge/data/instruments/daegeum/sanjo/basic-note-correspondence.json
```

### 산조대금 사용 자료 준비

원본 9개와 기존 전체 분석 결과, [악보 명세](../docs/knowledge/data/instruments/daegeum/score-manifest.json)의 `ngc-textbook-standard-score-2022` PDF를 `rawPath`에 준비한 뒤 실행합니다. 입력 악보는 원문 출처에서 받아 SHA-256을 대조합니다. 앱이나 화면을 생성하지 않으며 기본음계·시김새·연주 폴더, 재생용 WAV 122개, 원본 악보집 PDF 1개, 연결 정보 `catalog.json`, 안내 `README.md`를 생성합니다.

```powershell
python data/src/prepare_sanjo_assets.py
python -m unittest discover -s data/tests -p 'test_prepare_sanjo_assets.py' -v
```

기본 출력은 `data/processed/instruments/daegeum/sanjo/ready-v1/`입니다. `--output-dir`과 `--analysis-dir`로 변경할 수 있습니다. 쓰기 전에 입력 9개 원본의 해시, 분석의 원본 ID·해시, 기본음 매핑의 원본 버전, 악보 PDF 해시를 확인합니다. 원본은 수정하지 않습니다. 태평가를 제외한 기본음계·시김새는 원본 프레임 구간을 추출합니다. 요성은 인접한 동일 음높이 구간을 묶은 18음군, 스타카토는 짧은 발음 후보 17개, 나머지는 기존 RMS 발음 구간을 사용합니다. 이전 catalog에 포함된 오래된 생성 파일은 삭제 후보 전체의 경로와 해시를 먼저 확인하고 제거하므로 전체 녹음이 새 패키지에 남지 않습니다. 삭제 후보에 사용자가 수정한 파일이 있으면 후보를 제거하지 않고 오류를 반환합니다. 음원은 채널 평균·필요한 경우 피크 감쇠·PCM16 양자화만 수행하며 원래 샘플레이트를 유지합니다. 감쇠 계수와 변환 전후 해시·프레임 수를 항목에 기록합니다. 기존 출력의 같은 이름은 재실행 시 갱신됩니다.

율명과 분할 경계는 추정이며 채점 정답 검수 전입니다. 태평가 오선보는 성악용, 정간보는 단소용 일부로 확보했으며 해당 녹음 전곡과의 일치 판본은 미확보입니다. PDF는 KOGL4 원본 전체를 무변경 복사하고 페이지 번호를 연결합니다.

기본음계·시김새121개 모두 취법·율명으로 연결합니다. 요성18음 기준으로 기본음13개에 없는 무·고·역취 황을 포함하고, 넓은 요성은 중심음으로 비교합니다. [율명 검토 기록](../docs/knowledge/data/instruments/daegeum/sanjo/ornament-label-review.json)은 음렬 예외2곳과 다루치기10개 안정창, 깊은요성 첫 음의 분할 수정을 명시합니다. 다루치기 원본10.05초를40ms 창·10ms 간격으로 재분석해 아래 안정음→도착음 파일명을 생성합니다. 이름은 실측 음고 대응이며 주법 의도와 다를 수 있어 후보와 근거를 보존합니다. 기록의 원본 해시·유효 프레임 수를 검사하고, 측정값이 없거나 기준에서120cent를 넘으면 임의 이름을 붙이지 않고 재검토 오류로 중단합니다. catalog schema4에는18음 기준·방법·후보·음고 궤적·생성 코드 해시·반복 음의 검토 결과를 포함합니다. 원본은 보존하며 깊은요성 첫 음만 두 조각에서 원본0.6–7.1초의 한 연주로 복구했습니다. 이후 깊은요성 파일 순번은 하나씩 당겨지고 실제원본ID는 보존합니다.

### 중복 율명 원본 재검증

```powershell
python data/src/audit_sanjo_repeated_notes.py
```

Python·NumPy만 사용해 원본2개의 해시를 검사하고 깊은요성0–9초·노니로107–124초를 직접 재분석합니다. `data/processed/instruments/daegeum/sanjo/repeat-audit-v1/`에 audit.json과 비교용 원본 문맥 음원2개(채널 평균·PCM16 변환)를 생성합니다. 소리를 합성하거나 음높이를 바꾸지 않습니다.40ms YIN과 별도FFT, 버려진 틈·끝음·두 발음 사이의 RMS를 확인합니다. 원본 연속성이 확인된 깊은요성 첫 두 조각은 준비 단계에서 병합하고, 소음 수준의 간격이 있는 노니로 두 발음은 유지합니다. 전체 분석기에서 임계값을 일괄 낮추거나 같은 율명이라는 이유로 자동 병합하지 않습니다.
