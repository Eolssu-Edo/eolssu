# AI: 연주 채점

악보 인식의 임시 원본은 S15P31E102-534부터 요청 종료 시 삭제하며 별도 watchdog이 30분에 다운로드·쪽 그림 디렉터리를 삭제합니다. 삭제 실패는 1초 뒤 재시도합니다. 운영 OMR의 `/tmp` tmpfs를 유지하고 백업·영구 볼륨에 넣지 않습니다. `uv run pytest tests/unit/test_temporary_sources.py tests/unit/test_omr_api.py`로 파일 수명·API 회귀를 검사하며 모델 인식 품질과 구분합니다.

녹음의 음높이를 추정해 정답 음표와 DTW로 맞추고, 음표마다 음정·박자·길이를 판정합니다. 작업 지침은 [AGENTS.md](AGENTS.md)를 따릅니다.

## 설치와 검사

Python 3.12와 [uv](https://docs.astral.sh/uv/)가 필요합니다. 녹음 디코딩과 그 테스트는 `ffmpeg`·`ffprobe`가 `PATH`에 있어야 하며, 없으면 건너뛰지 않고 실패합니다.

```bash
uv sync
```

```bash
uv run pytest
```

Windows에서 저장소가 긴 경로(예: `.claude\worktrees\...` 아래 작업 공간)에 있으면 librosa의 numba가 컴파일 결과를 패키지 옆에 저장하다 경로 길이 한도를 넘어 `FileNotFoundError`나 DLL 로드 오류로 실패할 수 있습니다. 테스트나 평가 도구를 실행하기 전에 같은 PowerShell 창에서 캐시를 짧은 경로로 옮깁니다.

```powershell
$env:NUMBA_CACHE_DIR = 'C:/nbc'
```

`tests/unit/test_scoring.py`는 합성 음계로 만점, 음 바꿈, 늦은 시작, 빠진 음, 쉼, 조율 보정과 상한, 무음·잡음 몇 프레임·짧은 입력 실패를 검사합니다. `tests/unit/test_arirang_score.py`는 공통 악보 데이터의 아리랑 샘플(`data/tests/fixtures/scores/`)을 채점 입력으로 바꿔 곡 전체와 연습 구간을 채점합니다. 실제 녹음이나 모델 검증이 아닙니다. `tests/unit/test_audio.py`는 테스트 중 `ffmpeg`로 만든 2초짜리 사인파 AAC(44.1kHz 스테레오·48kHz 모노)와 헤더·중간이 손상되거나 끝이 잘린 파일(패킷 경계에서 잘린 파일 포함), 오디오가 아닌 파일·오디오 없는 파일, 없는 파일·URL·`:`가 든 상대 경로·시간 초과로 디코딩을 검사합니다. 파일은 커밋하지 않습니다.

## 로컬 기준 채점

검수된 단소 악보와 PC의 녹음을 현재 채점 기준으로 확인합니다. `ai` 폴더의 PowerShell에서 실행합니다. `ffmpeg`와 `ffprobe`가 필요합니다. 점수는 화면에만 출력하고 서버나 DB에 기록하지 않습니다.

```powershell
$env:PYTHONPATH = '.;src'
$env:NUMBA_CACHE_DIR = 'C:/nbc'
.venv/Scripts/python.exe -X utf8 -m evaluation.local_score `
  'C:/녹음/아리랑.m4a' 'ngc-standard-2022-p173' --bpm 96 --start 0
```

악보 ID는 도라지타령 `ngc-standard-2022-p171`, 밀양아리랑 `p172`, 아리랑 `p173`, 늴리리야 `p174`입니다. 뒤 세 곡도 전체 ID를 입력합니다. `--start`는 녹음 앞부분에서 제외할 초이며 기본은 0입니다. BPM은 입력값이고 실제 연주 속도를 자동 측정한 값이 아닙니다. `FAILED`는 성공 점수가 아니며 종료 코드가 1입니다. 손상·없는 파일과 잘못된 입력은 오류로 종료합니다.

### 브라우저에서 선택하기

`ai` 폴더의 PowerShell에서 아래 명령을 실행한 뒤 [로컬 채점 화면](http://127.0.0.1:8765/)을 엽니다. 종료하려면 PowerShell에서 `Ctrl+C`를 누릅니다.

```powershell
$env:PYTHONPATH = '.;src'
$env:NUMBA_CACHE_DIR = 'C:/nbc'
.venv/Scripts/python.exe -X utf8 -m evaluation.local_score_web
```

녹음과 분석용으로 풀어낸 소리는 이 PC의 임시 파일에 두고 요청이 끝나면 지웁니다. 시작 위치부터 최대 3분을 분석하며, 그보다 긴 구간은 거절합니다. 정렬 메모리 한도에 따라 더 짧은 구간도 거절될 수 있습니다. 시작 위치 이전 소리는 임시 파일에 저장하지 않습니다. 한 번에 한 녹음만 분석합니다. 기준 점수를 확인한 뒤 허용 범위, 부분 점수, 큰 오차 감점과 항목별 비중을 조절하고 `비교하기`를 누릅니다. 비교할 때마다 같은 녹음을 다시 분석하므로 시간이 걸립니다. 실험 결과는 서비스에 적용되지 않습니다.

음정 허용을 바꾸면 조율 보정도 달라질 수 있습니다. 부분 점수와 큰 오차 감점은 음정·박자에만 적용하며, 길이 점수의 부분 점수 곡선은 바꾸지 않습니다. 현재 점수 재계산이 실제 채점 결과와 다르면 비교를 중단합니다. 실험에 쓰는 고정밀 음별 측정값은 채점 함수의 내부 계산에서 읽으므로 채점 구현이 바뀌면 일치 검사를 다시 해야 합니다.

1. 단소 녹음과 같은 곡의 표준악보집 악보를 선택합니다. 네 곡 외의 악보는 이 화면에서 지원하지 않습니다.
2. BPM과 시작 초를 확인하고 `기준 점수 확인`을 누릅니다. 앞부분 자동 자르기나 실제 빠르기 추정은 하지 않습니다.
3. 음정·길이 허용, 박자 흔들림 배수, 부분 점수 감점, 큰 오차 감점, 음정·박자·길이 비중을 조절합니다. 부분 점수 감점은 허용 경계에서 깎는 점수입니다.
4. `비교하기`를 누르면 현재 점수와 실험 점수, 실제 박자 허용 시간과 바뀐 음별 판정을 볼 수 있습니다. 입력이나 설정을 바꾸면 이전 비교 결과는 숨겨집니다.
5. `현재 기준으로 되돌리기`로 슬라이더를 초기화할 수 있습니다. 이 도구의 점수는 채점 기준 후보를 비교하는 값이며 연주 정확도가 검증됐다는 뜻은 아닙니다.

화면의 입력 변경과 이전 결과 숨김 검사는 Node.js가 설치된 환경에서 `ai` 폴더의 PowerShell로 실행합니다.

```powershell
node --test tests/unit/test_local_score_web_ui.cjs
```

## 녹음 디코딩

`src/ai_service/pipelines/audio.py`의 `decode_recording()`이 원본(m4a·AAC) 파일을 [녹음 파일 규격(PRA-02)](../docs/specs/recording-file.md)대로 16kHz·모노·PCM16(`int16`) 배열로 바꿉니다. 스테레오는 두 채널 평균이고, 결과는 메모리에만 둡니다. 중간이 손상됐거나 끝이 잘린 파일, 오디오가 없는 파일은 `DecodeError`(`fail_reason` `DECODE_ERROR`)입니다. ffmpeg는 손상 구간을 건너뛰고 정상 종료해 뒤의 소리를 앞당기므로 `-xerror`로 디코딩 오류가 하나라도 나면 실패로 봅니다. 패킷 경계에서 잘린 파일은 오류 없이 일찍 끝나므로, 디코딩 길이가 `ffprobe`로 읽은 컨테이너 길이보다 0.1초 넘게 짧아도 실패로 봅니다. 정상 파일의 차이는 -0.001~+0.043초로 실측했습니다. 길이를 적지 않은 형식이거나 `ffprobe` 출력을 읽지 못하면 이 비교를 건너뜁니다. 조각(fragmented) mp4는 남은 조각으로 길이를 계산하므로 잘려도 짧은 녹음으로 통과할 수 있습니다. 앱 녹음(Android MediaRecorder·iOS)은 조각 mp4가 아니라서 지금은 막지 않습니다. 파일이 없거나 `ffmpeg`가 없으면 `FileNotFoundError`, 시간 안에 끝나지 않으면 `TimeoutError`로, 녹음 문제가 아니라서 `DecodeError`와 구분합니다. 제한 시간(`timeout_s`, 기본 60초)은 `ffprobe`와 `ffmpeg`에 각각 적용합니다. 로컬 파일만 받고(URL은 `FileNotFoundError`, ffmpeg도 `file` 프로토콜만 허용) 오류 메시지에 입력 위치를 남기지 않습니다. 서명 URL 내려받기와 HTTP 진입점은 아래 '분석 API'입니다.

## 분석 API

백엔드가 부르는 `POST /v1/analyses`(`src/ai_service/api.py`)입니다. 요청·응답은 [PRA-03 '분석 요청과 상태 조회 계약'](../docs/specs/practice-analysis-failure.md#분석-요청과-상태-조회-계약-s15p31e102-186)을 따르고, 백엔드는 `ANALYSIS_AI_BASE_URL`에 이 서버 주소를 넣습니다.

```bash
uv run uvicorn ai_service.api:app --host 0.0.0.0 --port 8000
```

1. `audioUrl`(서명 URL)에서 원본을 임시 폴더로 내려받습니다. 연결부터 전체 30초, 25MiB까지 받고 응답을 마치면 지웁니다.
2. 위 '녹음 디코딩'으로 16kHz 모노로 바꾸고, pYIN으로 음높이를 추정해 '채점 방식'대로 채점합니다.
3. `DONE`(`modelVersion` `pyin-5`, `scoringVersion` `note-mean-1`, 점수 4개, 조율 보정, 음표별 판정·부분 점수) 또는 `FAILED`(`NO_SOUND`·`TOO_SHORT`·`DECODE_ERROR`)를 200으로 돌려줍니다. 음표 결과의 `idx`는 요청 음표의 `idx` 그대로입니다.

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
- 결과 음표마다 `detectedSemitone`(조율 보정을 뺀 연주 음의 반음)과 `recordingOffsetMs`(녹음 파일 0ms부터 그 음이 시작된 시각)를 돌려줍니다. 놓친 음은 둘 다 `null`입니다. 정의와 한계는 PRA-03 계약에 있습니다.
- 시도마다 `analysis session=… attempt=… result=… ms=…` 한 줄을 남깁니다. `audioUrl`과 예외 메시지는 남기지 않고, httpx·httpcore 로그는 WARNING 미만을 막습니다(httpx는 INFO에서 URL 전체를 남깁니다).
- OpenAPI 문서(요청·상태별 응답 스키마와 설명)는 코드에 있지만, 내부 API라 `/docs`·`/redoc`·`/openapi.json` 경로는 열지 않습니다(404). 문서는 서버 없이 아래 명령으로 뽑아 [Swagger Editor](https://editor.swagger.io) 등에서 봅니다. 응답 스키마는 문서용 모델이며, 테스트가 실제 응답과 같은 키·값 범위인지 확인합니다.

```bash
uv run python -c "import json; from ai_service.api import app; print(json.dumps(app.openapi(), ensure_ascii=False, indent=2))" > analysis-openapi.json
```

- `tests/unit/test_api.py`는 합성음을 m4a로 만들어 가짜 다운로드로 넘깁니다. 쉼 채점과 DTW 길이 계산 검사는 디코딩을 합성 배열로 바꿔 ffmpeg 없이 돕니다. 응답 모양과 실패 처리 검사이며 채점 품질 검증이 아닙니다.

## 악보 인식 API

backend 변환 작업자가 부르는 `POST /v1/recognitions`(`src/omr_service/api.py`)입니다. 채점 API와 다른 컨테이너(`omr`)로 띄웁니다. 요청·응답·실패 코드는 [악보 인식 서비스 계약](../docs/specs/score-recognition.md)을 따릅니다. 지금은 정간보 글자 층 PDF와 오선보(PDF·이미지)를 읽습니다. 스캔 PDF·사진 정간보는 연결 전이라 `ENGINE_ERROR`로 끝납니다(별도 Task [S15P31E102-458](https://ssafy.atlassian.net/browse/S15P31E102-458)).

```bash
uv run uvicorn omr_service.api:app --host 0.0.0.0 --port 8000
```

1. 서명 URL의 PDF 1개 또는 PNG·JPEG 1~10장을 임시 폴더로 받습니다. 합계 20MiB까지입니다.
2. PDF는 쪽마다 300dpi PNG로 그립니다(10쪽까지). 이미지는 선언한 형식과 실제 형식이 같아야 하고(여러 그림이 든 폰 사진 JPEG 포함), 폰 사진의 회전 정보를 적용해 둡니다. 쪽 그림 하나는 약 8,950만 화소(Pillow 기본 한도)까지입니다. PDFium은 스레드 안전하지 않아 PDF 처리는 한 번에 하나씩 합니다.
3. `notation`에 맞는 엔진을 돌립니다. 요청 시작부터 내려받기·쪽 그림·엔진까지 쪽당 60초 × 쪽 수를 넘으면 `TIMEOUT`입니다. 이미지는 내려받기부터 장 수 기준이고, 쪽 수를 열어 봐야 아는 PDF는 내려받기와 PDFium 대기에 600초까지 씁니다. 내려받기는 읽기 한 번 제한(10초)만큼 넘길 수 있습니다. 마감 전에 저장소가 늦어 받지 못하면 `UNSUPPORTED_FILE`입니다. 마감은 PDF 쪽을 그리기 전마다 보므로, 마감 직전에 시작한 쪽 하나와 이미지 열기는 끝까지 마친 뒤 `TIMEOUT`이 됩니다(빈 A4 300dpi 쪽 0.6초, 복잡한 쪽은 더 걸릴 수 있음).
4. 형식 오류(422) 말고는 예상하지 못한 오류도 200 `ENGINE_ERROR`로 돌려줍니다.

정간보 글자 층 PDF는 `src/omr_service/jeongganbo_text.py`가 `data/src/scores/standard_score_text.py`(pymupdf, 별도 프로세스)와 [jeongganbo-generator](https://github.com/danbinaerinHan/jeongganbo-generator) `9d4a24e`의 `tools/import-ngc-omr.mjs`(Node)로 읽습니다. 클론 위치를 `JGB_GENERATOR_DIR`에 넣고 실행합니다(클론 방법은 [데이터 README](../data/README.md#정간보-omr)). 없으면 정간보 요청은 `ENGINE_ERROR`이고, 변환까지 가는 테스트(`tests/unit/test_omr_text_engine.py`)는 건너뜁니다.

정간보 글자 층 엔진 2(`jeongganbo-text/standard_score_text@2`)는 장단 패턴의 칸 음가를 BPM 박으로 환산합니다. 단모리의 8분 소박 8칸은 4분 박 4개여서 4정간 각이 120 BPM에서 2초입니다. 엇모리·타령·계면가락도드리의 8분 칸과 세령산의 점4분 칸도 구분합니다. `uv run pytest tests/unit/test_omr_text_engine.py tests/unit/test_omr_staff_engine.py`로 장단별 단위와 PDF→공통 악보→합성 시간축을 검사합니다. 기존 저장 악보와 내려받은 음원은 이 코드 변경으로 자동 교정되지 않습니다.

엔진 3(S15P31E102-741)은 이 시간 환산을 유지하고, PDF의 도형 삼각 쉼표·각 경계의 명시적 이음을 복구합니다. 우물사이 전용 옥타브 점 인식은 포함하지 않으며, 점으로 한자의 음역을 추정하지 않습니다. `score_data`의 기존 검수 JGB·오선보 기본 동작은 유지하고 글자 층 PDF만 `continue_explicit_ties=True`로 변환합니다. [실제 PDF 전후 근거와 편집 안내](../docs/knowledge/frontend/sheet-pitch-conversion-corrections.md)를 참고하세요.

오선보는 `src/omr_service/staff_engine.py`가 쪽마다 Audiveris를 별도 프로세스로 돌리고(쪽당 60초와 남은 요청 시간 중 짧은 쪽, 넘으면 끝냄), 쪽들의 MusicXML을 이어 `staff_musicxml.py`로 정간보 줄을 만든 뒤 글자 층과 같은 변환을 거칩니다. 명령은 `AUDIVERIS_CMD`(기본 `audiveris`)이고, 테스트(`tests/unit/test_omr_staff_engine.py`)는 가짜 Audiveris를 씁니다.

### 악보 인식 이미지

`omr.Dockerfile`은 Ubuntu 24.04에 Audiveris 5.11(데스크톱 등록 스크립트를 뺀 deb와 `libgtk-3-0`), Node, jeongganbo-generator `9d4a24e`, 서비스·`omr` 그룹 의존성, `data/src/scores`·`data/reference`를 넣고 사용자 10001로 실행합니다. 빌드 맥락은 저장소 루트이고 보낼 파일은 `omr.Dockerfile.dockerignore`가 정합니다. 저장소 루트에서 실행합니다.

```bash
docker build -f ai/omr.Dockerfile -t eolssu-omr .
```

```bash
docker run --rm --cpus 1.5 --memory 2g -p 127.0.0.1:8000:8000 eolssu-omr
```

2026-11-04 실측(이 PC Docker, `--cpus 1.5 --memory 2g`): 실측 4곡 오선보 쪽 그림은 1쪽 8.2~9.7초이고 결과가 고정 자료 검사 기준과 같습니다. 정간보·오선보가 함께 있는 표준악보집 펼침면 PDF(북천이 맑다커늘 PDF182) 1쪽은 13.5초, 앞에 빈 쪽을 붙인 2쪽 PDF는 16.1초(빈 쪽은 `NO_STAFF`)였습니다. Audiveris는 한 번에 하나만 돌아 동시 요청 2건은 차례로 끝나고(9.5·17.7초), 최대 메모리는 1.02GB였습니다. 이미지는 1.94GB입니다. 기반 이미지 digest·apt 패키지 버전·Audiveris deb 체크섬·생성기 커밋을 고정했습니다. Ubuntu가 보안 업데이트로 고정한 패키지(`libgtk-3-0t64`·`nodejs`·`ca-certificates`)의 이전 버전을 내리면 빌드가 `Version ... not found`로 멈춥니다. 그때는 고정을 빼고 한 번 빌드한 이미지에서 `docker run --rm --entrypoint dpkg-query eolssu-omr -W libgtk-3-0t64 nodejs ca-certificates`로 새 버전을 읽어 `omr.Dockerfile`에 다시 적고, 실측 4곡을 다시 확인합니다. Audiveris 버전을 올릴 때는 deb를 받아 `sha256sum`으로 `--checksum`도 바꿉니다. 운영 Compose 실행과 서버(4코어) 실측 방법은 [실행 문서](../infra/runtime/README.md#omr-악보-인식-서비스)에 있습니다.

PDF·이미지 처리는 `omr` 의존성 그룹(`pypdfium2`·`pillow`·`pymupdf`)에만 있습니다. `uv sync`·`uv run pytest`는 이 그룹도 설치하고, 채점 이미지는 `--no-default-groups`로 받아 넣지 않습니다. 테스트 `tests/unit/test_omr_api.py`는 가짜 내려받기·가짜 엔진으로 요청 검사·쪽 그림·시간 예산·실패 코드·응답 모양을 봅니다. 인식 품질 검증이 아닙니다.

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

작은 고정 f0의 DTW 기준은 `uv run python -m evaluation.dtw_baseline --out ../data/processed/dtw-286/u1-baseline-fixed.json`으로 기록합니다. 경로·비용·판정과 메모리의 측정 범위는 [U1 기준 관찰](../docs/knowledge/ai/dtw-memory-comparison.md)을 따릅니다. 서비스의 채점 알고리즘을 바꾸거나 긴 녹음을 실행하는 도구는 아닙니다.

연구용 후보는 `uv run python -m evaluation.dtw_candidate_check --method A --out ../data/processed/dtw-286/u2-A.json`으로 같은 작은 입력에 연결합니다. `--method B --half-width-seconds 0.5`로 제한 범위 후보도 확인합니다. 기본 서비스는 기존 전체 DTW를 사용하며, 자세한 비교 범위와 한계는 [후보 구현 기록](../docs/knowledge/ai/dtw-memory-comparison.md#u2-후보-구현과-작은-입력-검증)을 따릅니다.

곡별 고정 음높이와 합성 녹음의 음별 판정 비교는 [U3 실행 방법과 한계](../docs/knowledge/ai/dtw-memory-comparison.md#u3-합성-입력-품질-비교-2026-10-30)를 따릅니다. 결과는 연구용이며 실제 연주나 전체 곡의 품질을 보증하지 않습니다.

Windows에서 곡별 DTW와 합성 m4a 전체 처리 시간을 측정할 때는 `ai` 폴더의 PowerShell에서 아래처럼 단일 스레드로 실행합니다. 스레드 설정이 빠지면 1GiB 커밋 메모리 제한 안에서 OpenBLAS 할당 오류가 날 수 있습니다. 출력 이름은 문서가 인용한 기존 원본(`u4-full-rest.json`, `u4-reviewed.json`)과 겹치지 않게 새로 정합니다.

```powershell
$env:OPENBLAS_NUM_THREADS = '1'; $env:OMP_NUM_THREADS = '1'
uv run --locked python -m evaluation.dtw_full_performance --out ../data/processed/dtw-286/u4-rerun.json
uv run --locked python -m evaluation.dtw_u4_table ../data/processed/dtw-286/u4-rerun.json --out ../data/processed/dtw-286/u4-rerun.csv
```

실행 전 메모리·시간 제한을 작은 입력으로 확인하고 각 조건을 별도 프로세스에서 다섯 번 실행합니다. 두 번째 명령은 원본 JSON에서 반복별 결과 표를 만듭니다. 문서의 표 두 개는 같은 명령으로 기존 원본에서 바이트까지 똑같이 다시 만들어집니다. 합성 m4a는 실제 연주나 팀원이 만든 음원이 아닙니다. 자세한 입력과 제한은 [측정 기록](../docs/knowledge/ai/dtw-memory-comparison.md)을 따릅니다.

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
