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

`src/ai_service/pipelines/audio.py`의 `decode_recording()`이 원본(m4a·AAC) 파일을 [녹음 파일 규격(PRA-02)](../docs/specs/recording-file.md)대로 16kHz·모노·PCM16(`int16`) 배열로 바꿉니다. 스테레오는 두 채널 평균이고, 결과는 메모리에만 둡니다. 중간이 손상됐거나 끝이 잘린 파일, 오디오가 없는 파일은 `DecodeError`(`fail_reason` `DECODE_ERROR`)입니다. ffmpeg는 손상 구간을 건너뛰고 정상 종료해 뒤의 소리를 앞당기므로 `-xerror`로 디코딩 오류가 하나라도 나면 실패로 봅니다. 패킷 경계에서 잘린 파일은 오류 없이 일찍 끝나므로, 디코딩 길이가 `ffprobe`로 읽은 컨테이너 길이보다 0.1초 넘게 짧아도 실패로 봅니다. 정상 파일의 차이는 -0.001~+0.043초로 실측했습니다. 길이를 적지 않은 형식이거나 `ffprobe` 출력을 읽지 못하면 이 비교를 건너뜁니다. 조각(fragmented) mp4는 남은 조각으로 길이를 계산하므로 잘려도 짧은 녹음으로 통과할 수 있습니다. 앱 녹음(Android MediaRecorder·iOS)은 조각 mp4가 아니라서 지금은 막지 않습니다. 파일이 없거나 `ffmpeg`가 없으면 `FileNotFoundError`, 시간 안에 끝나지 않으면 `TimeoutError`로, 녹음 문제가 아니라서 `DecodeError`와 구분합니다. 제한 시간(`timeout_s`, 기본 60초)은 `ffprobe`와 `ffmpeg`에 각각 적용합니다. 로컬 파일만 받고(URL은 `FileNotFoundError`, ffmpeg도 `file` 프로토콜만 허용) 오류 메시지에 입력 위치를 남기지 않습니다. 서명 URL 내려받기와 HTTP 진입점은 [S15P31E102-213](https://ssafy.atlassian.net/browse/S15P31E102-213)에서 붙입니다.

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
