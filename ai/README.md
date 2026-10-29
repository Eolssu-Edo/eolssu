# AI: 연주 채점

녹음의 음높이를 추정해 정답 음표와 DTW로 맞추고, 음표마다 음정·박자·길이를 판정합니다. 작업 지침은 [AGENTS.md](AGENTS.md)를 따릅니다.

## 설치와 검사

Python 3.12와 [uv](https://docs.astral.sh/uv/)가 필요합니다. 녹음 디코딩과 그 테스트는 `ffmpeg`·`ffprobe`가 `PATH`에 있어야 하며, 없으면 건너뛰지 않고 실패합니다.

```bash
uv sync
```

```bash
uv run pytest
```

`tests/unit/test_scoring.py`는 합성 음계로 만점, 음 바꿈, 늦은 시작, 빠진 음, 쉼, 조율 보정과 상한, 무음·잡음 몇 프레임·짧은 입력 실패를 검사합니다. `tests/unit/test_arirang_score.py`는 공통 악보 데이터의 아리랑 샘플(`data/tests/fixtures/scores/`)을 채점 입력으로 바꿔 곡 전체와 연습 구간을 채점합니다. 실제 녹음이나 모델 검증이 아닙니다. `tests/unit/test_audio.py`는 테스트 중 `ffmpeg`로 만든 2초짜리 사인파 AAC(44.1kHz 스테레오·48kHz 모노)와 헤더·중간이 손상되거나 끝이 잘린 파일(패킷 경계에서 잘린 파일 포함), 오디오가 아닌 파일·오디오 없는 파일, 없는 파일·URL·`:`가 든 상대 경로·시간 초과로 디코딩을 검사합니다. 파일은 커밋하지 않습니다.

## 녹음 디코딩

`src/ai_service/pipelines/audio.py`의 `decode_recording()`이 원본(m4a·AAC) 파일을 [녹음 파일 규격(PRA-02)](../docs/specs/recording-file.md)대로 16kHz·모노·PCM16(`int16`) 배열로 바꿉니다. 스테레오는 두 채널 평균이고, 결과는 메모리에만 둡니다. 중간이 손상됐거나 끝이 잘린 파일, 오디오가 없는 파일은 `DecodeError`(`fail_reason` `DECODE_ERROR`)입니다. ffmpeg는 손상 구간을 건너뛰고 정상 종료해 뒤의 소리를 앞당기므로 `-xerror`로 디코딩 오류가 하나라도 나면 실패로 봅니다. 패킷 경계에서 잘린 파일은 오류 없이 일찍 끝나므로, 디코딩 길이가 `ffprobe`로 읽은 컨테이너 길이보다 0.1초 넘게 짧아도 실패로 봅니다. 정상 파일의 차이는 -0.001~+0.043초로 실측했습니다. 길이를 적지 않은 형식이거나 `ffprobe` 출력을 읽지 못하면 이 비교를 건너뜁니다. 조각(fragmented) mp4는 남은 조각으로 길이를 계산하므로 잘려도 짧은 녹음으로 통과할 수 있습니다. 앱 녹음(Android MediaRecorder·iOS)은 조각 mp4가 아니라서 지금은 막지 않습니다. 파일이 없거나 `ffmpeg`가 없으면 `FileNotFoundError`, 시간 안에 끝나지 않으면 `TimeoutError`로, 녹음 문제가 아니라서 `DecodeError`와 구분합니다. 제한 시간(`timeout_s`, 기본 60초)은 `ffprobe`와 `ffmpeg`에 각각 적용합니다. 로컬 파일만 받고(URL은 `FileNotFoundError`, ffmpeg도 `file` 프로토콜만 허용) 오류 메시지에 입력 위치를 남기지 않습니다. 서명 URL 내려받기와 HTTP 진입점은 아래 '분석 API'입니다.

## 분석 API

백엔드가 부르는 `POST /v1/analyses`(`src/ai_service/api.py`)입니다. 요청·응답은 [PRA-03 '분석 요청과 상태 조회 계약'](../docs/specs/practice-analysis-failure.md#분석-요청과-상태-조회-계약-s15p31e102-186)을 따르고, 백엔드는 `ANALYSIS_AI_BASE_URL`에 이 서버 주소를 넣습니다.

```bash
uv run uvicorn ai_service.api:app --host 0.0.0.0 --port 8000
```

1. `audioUrl`(서명 URL)에서 원본을 임시 폴더로 내려받습니다. 연결부터 전체 30초, 25MiB까지 받고 응답을 마치면 지웁니다.
2. 위 '녹음 디코딩'으로 16kHz 모노로 바꾸고, pYIN으로 음높이를 추정해 '채점 방식'대로 채점합니다.
3. `DONE`(`modelVersion` `pyin-2`, 점수 4개, 조율 보정, 음표별 판정) 또는 `FAILED`(`NO_SOUND`·`TOO_SHORT`·`DECODE_ERROR`)를 200으로 돌려줍니다. 음표 결과의 `idx`는 요청 음표의 `idx` 그대로입니다.

| 상황 | 응답 | 백엔드 처리 |
| --- | --- | --- |
| 채점 완료·녹음 탓 실패 | 200 `DONE`·`FAILED` | 결과·실패 코드 저장 |
| 요청이 계약에 맞지 않음 | 422 `{"errors":[{loc, type}]}`. 입력값은 돌려주지 않음 | `SERVER_ERROR` |
| 녹음을 내려받지 못함(만료·네트워크·크기 초과) | 502 `{"error":"DOWNLOAD_FAILED"}` | `SERVER_ERROR` |
| 채점 메모리 한도 초과·그 밖의 예외(디코딩 시간 초과 등) | 500 `{"error":"ANALYSIS_ERROR"}` | `SERVER_ERROR` |

- 정답 길이(첫 음 시작부터 마지막 음 끝까지, 쉼 포함) × 녹음 길이로 계산한 DTW 메모리(칸당 24바이트)가 4GiB를 넘으면 음높이 추정 전에 500으로 거절합니다. 120초 × 120초까지 받습니다. 넘으면 프로세스가 메모리 부족으로 죽기 때문이며, 녹음 길이 상한은 [PRA-03 미결 사항](../docs/specs/practice-analysis-failure.md#미결-사항)입니다.
- 인증이 없고 `audioUrl`의 호스트를 가리지 않습니다. 백엔드만 닿는 내부 네트워크에 두고 포트를 외부에 열지 않습니다.
- 기준음은 요청에 없어 `HWANG_HZ`(황 = E♭4, 311.13Hz)로 둡니다. 기기 차이는 조율 보정이 흡수하고, 보정 상한은 곡마다 계산합니다(아래 채점 방식).
- 박 길이는 `60 / bpm`초이고 `beats`는 백엔드가 `bpmUnit` 단위로 센 값으로 봅니다. `bpmUnit`은 DB의 4종만 받습니다.
- `notes[].startBeat`(곡 처음부터 센 시작 박, 0 이상이고 앞 음이 끝난 뒤 시작해야 함)로 기대 시각을 정합니다. 첫 음을 0초로 보고, 음 사이 빈 시간은 쉼으로 채점합니다. 음표 `idx`는 채점에 넘겨 결과에 그대로 돌려받습니다.
- 시도마다 `analysis session=… attempt=… result=… ms=…` 한 줄을 남깁니다. `audioUrl`과 예외 메시지는 남기지 않고, httpx·httpcore 로그는 WARNING 미만을 막습니다(httpx는 INFO에서 URL 전체를 남깁니다).
- `tests/unit/test_api.py`는 합성음을 m4a로 만들어 가짜 다운로드로 넘깁니다. 쉼 채점과 DTW 길이 계산 검사는 디코딩을 합성 배열로 바꿔 ffmpeg 없이 돕니다. 응답 모양과 실패 처리 검사이며 채점 품질 검증이 아닙니다.

## 실행 이미지

`Dockerfile`은 분석 API를 사용자 10001로 실행하는 이미지입니다. 아래 명령은 이 폴더(`ai/`)에서 실행합니다.

| 항목 | 고정 값 | 이유 |
| --- | --- | --- |
| 기반 | `ubuntu:26.04`(다이제스트 고정) | 백엔드 이미지(`eclipse-temurin:21`, Ubuntu 26.04)와 같은 ffmpeg 패키지를 쓰기 위해 |
| ffmpeg·ffprobe | `ffmpeg`와 디코딩 라이브러리 7개(`libavcodec62` 등) 모두 `7:8.0.1-3ubuntu2`(ffmpeg 8.0.1) | 백엔드 업로드 검사와 AI 디코딩의 판정을 맞춘다(PRA-02). `ffmpeg` 패키지는 라이브러리를 '이 버전 이상'으로만 요구해서, 라이브러리를 따로 고정하지 않으면 보안 업데이트 뒤 다시 빌드할 때 디코딩 라이브러리만 바뀐다 |
| Python | 3.12(`.python-version`, uv가 받는 관리형 Python) | Ubuntu 26.04의 시스템 Python은 3.14 |
| uv | `ghcr.io/astral-sh/uv:0.13.6`(다이제스트 고정) | 의존성은 `uv.lock` 그대로(`--locked`), dev·eval 그룹은 넣지 않음 |

```bash
docker build -t eolssu-ai:check .
```

기본 명령은 `uvicorn ai_service.api:app --host 0.0.0.0 --port 8000`입니다. 인증이 없으므로 백엔드와 같은 내부 Docker 네트워크에만 붙이고 `-p`로 호스트에 공개하지 않습니다. 백엔드는 `ANALYSIS_AI_BASE_URL=http://<AI 컨테이너 이름>:8000`으로 부릅니다.

- librosa가 쓰는 numba는 첫 호출 때 컴파일 결과를 파일로 저장합니다. 실행 사용자는 `/app`에 쓸 수 없어 `NUMBA_CACHE_DIR=/tmp/numba-cache`로 둡니다. 이 값이 없으면 채점 첫 호출이 `RuntimeError`(500)로 실패합니다. 컨테이너 파일 시스템을 읽기 전용(`read_only`)으로 띄우면 `/tmp`에 tmpfs를 붙입니다.
- 이미지는 약 1.65GB입니다(numba·scipy·ffmpeg). 빌드에 Docker 디스크 여유가 2GB 넘게 필요합니다.

이미지 안에서 테스트를 돌리려면 일회용 컨테이너에 dev 의존성(pytest)을 넣고 테스트 폴더를 읽기 전용으로 붙입니다. 실행 이미지에는 테스트를 넣지 않습니다.

```bash
docker run --rm --user 0 --tmpfs /tmp:rw,exec,size=512m --mount "type=bind,src=$(pwd)/tests,dst=/app/tests,readonly" --mount "type=bind,src=$(pwd)/evaluation,dst=/app/evaluation,readonly" eolssu-ai:check sh -c 'uv sync --locked --no-editable && uv run --locked --no-editable pytest -p no:cacheprovider'
```

pytest를 받으므로 인터넷이 닿는 네트워크가 필요합니다. 이 검사는 root로 돌아 실행 사용자 권한 문제를 잡지 못합니다. 기본 명령으로 띄운 컨테이너에 요청 1건을 보내는 확인을 함께 합니다([S15P31E102-213 작업 기록](../docs/plans/S15P31E102-213.md#검증)).

## 음높이 추정 비교

pYIN과 torchcrepe(full·tiny)를 같은 합성 입력으로 채점해 표로 출력합니다. torchcrepe는 `eval` 의존성 그룹에 있고 PyTorch를 함께 설치합니다. 모델 가중치는 torchcrepe 패키지에 들어 있어 따로 받지 않습니다.

```bash
uv sync --group eval
```

```bash
uv run --group eval python -m evaluation.run_evaluation
```

입력은 `evaluation/synthetic.py`의 합성 음계와 변형본 30종입니다. `--methods`로 방법을 고르고 `--out`으로 결과 표를 저장합니다. 새 방법을 추가하는 법과 최근 결과는 [음높이 추정 방법 비교](../docs/knowledge/ai/pitch-estimator-comparison.md)에 있습니다. 실제 녹음 비교는 아직 없습니다.

## 처리 시간 측정

녹음 길이별(기본 15~600초)로 음높이 추정·채점 시간과 최대 메모리를 잽니다. 길이·방법마다 새 프로세스에서 실행하고, 채점 DTW의 예상 메모리가 한도(`--memory-gib`, 기본 6)를 넘으면 채점은 건너뜁니다.

```bash
uv run --group eval python -m evaluation.measure_latency
```

`--seconds`·`--methods`로 범위를 줄이고 `--out`으로 표를 저장합니다. 최근 결과는 [녹음 길이별 분석 처리 시간과 메모리](../docs/knowledge/ai/analysis-latency.md)에 있습니다.

## 채점 방식

작은 고정 f0의 DTW 기준은 `uv run python -m evaluation.dtw_baseline --out ../data/processed/dtw-286/u1-baseline.json`으로 기록합니다. 경로·비용·판정과 메모리의 측정 범위는 [U1 기준 관찰](../docs/knowledge/ai/dtw-memory-comparison.md)을 따릅니다. 서비스의 채점 알고리즘을 바꾸거나 긴 녹음을 실행하는 도구는 아닙니다.

`src/ai_service/pipelines/scoring.py`

| 항목 | 현재 기준 |
| --- | --- |
| 입력 음표 | `Note(semitone, beats, sigimsae, idx, start_beat)`. `idx`는 결과에 그대로 돌려주는 음 번호, `start_beat`는 곡 처음부터 센 시작 박. `start_beat`가 있으면 음 사이 빈 시간을 쉼으로 보고, 없으면 음을 이어 붙인다 |
| 음높이 단위 | 기준음 대비 센트. 서비스 기준음은 `HWANG_HZ`(황 = E♭4, 311.13Hz)이고 반음 번호는 `pitches.semitone` |
| 음높이 추정 | pYIN, 10ms 간격. 기본 설정의 음높이 해상도는 10센트 |
| 조율 보정 | 정렬된 음의 가운데 구간에서 잰 어긋남의 중앙값. 상한 `min(100, 가장 가까운 두 음 간격 − 50 − 10)`센트를 넘으면 보정하지 않고 `tuning_out_of_range`로 표시 |
| 시김새 음표 | 음정 판정 제외, 박자·길이만 판정 |
| 음 시작 | 앞 음보다 이 음의 높이에 가까워진 첫 프레임. 앞 음과 높이가 같으면 음량(`frame_rms`)이 크게 줄었다 다시 올라온 첫 프레임 |
| 판정 | `MISSED` → `PITCH` → `TIMING` → `CORRECT` 순으로 먼저 걸리는 것 |
| 실패 | 소리가 없거나 잡음 몇 프레임만 잡혀 모든 음표가 `MISSED`면 `NO_SOUND`, 녹음이 정답 길이의 절반 미만이면 `TOO_SHORT`. 0점과 구분 |

허용 범위 기본값(음정 ±50센트, 시작 박의 1/8, 길이 ±25%)과 조율 보정 상한은 녹음으로 검증하기 전의 제안 값입니다. 입력 규칙, 상한 식의 근거와 한계는 [채점 입력과 기준음·조율 보정 상한](../docs/knowledge/ai/scoring-input-and-tuning.md)에 있습니다. 1분이 넘는 녹음은 아직 다루지 않습니다.


## 정상·오류 녹음 평가

[오류 검출 평가 실행과 결과 해석](../docs/knowledge/ai/scoring-error-evaluation.md)을 따라 합성 묶음 생성, 허용값 16개 조합 평가, 같은 녹음의 전후 비교를 실행합니다. PowerShell 명령과 검출률·오탐률, 윌슨 구간, 맥니마 검정의 뜻과 한계가 있습니다. 단소 단음의 조율 결과는 [김 세기·취법별 측정](../docs/knowledge/ai/danso-tuning-variation.md)에 있습니다. 합성 결과로 실제 입문자 허용값을 확정하지 않습니다.
