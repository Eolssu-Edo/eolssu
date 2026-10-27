# 악보 → 국악기 MIDI·음원 실험

사진에서 음표를 읽어 단소·정악대금·산조대금·소금의 MIDI·CAT WAV를 만드는 **로컬 작업실**입니다. 오선보는 Audiveris, 정간보는 칸/기호 분할과 Ollama 비전 모델을 사용합니다. 사진 업로드 뒤 자동 미리듣기를 생성하며, 같은 음표로 악기만 바꾸거나 원보 대조·수정 후 다시 생성할 수도 있습니다. 실제 교재로 시험했지만 복잡한 기호·연음·낮은 화질의 오류가 남아 있어 전문가 인증 모범 연주로 취급하지 않습니다. 구체적인 입력과 성공·실패 수치는 [실제 인식 기록](../../docs/knowledge/ai/score-photo-service.md)에 있습니다.

Task 105는 공통 엔진과 단소를 제공하고, 정악대금 106·산조대금 107·소금 108은 각각 독립 브랜치에서 `profiles/<악기>.json`을 추가합니다. CLI의 사용 가능한 악기는 `python -m playback --help`로 확인합니다. 채점 서비스·Compose 앱·운영 배포와 분리한 로컬 실행 경로입니다.

`score.py`가 악보의 실음·시간·장식을 해석하고, `midi.py`가 MIDI를 저장합니다. `sampler.py`는 **저장한 MIDI를 다시 읽은 결과**와 CAT Live Sampler ZIP의 WAV·키존·미세조율 정보를 이용해 WAV를 만듭니다. Ableton·Kontakt·Sori 플러그인 실행은 아닙니다.

## 설치와 실행

명령은 `ai/`에서 실행합니다. Python 3.10 이상과 격리된 실험 환경을 사용합니다. 서비스의 Python 3.12/uv 환경·기본 의존성은 변경하지 않았습니다.

```powershell
python -m venv .venv-playback
.\.venv-playback\Scripts\python -m pip install -r playback/requirements.txt
.\.venv-playback\Scripts\python -m unittest playback.tests.test_pipeline playback.tests.test_expression playback.tests.test_recognition -v
```

CAT [공식 GUGAK Library](https://www.catseoul.com/gugak-library/)에서 **각 악기의 Live Sampler** 배포본을 구합니다. 외부 다운로드 ZIP 안의 `GUgAK-DEMO ... Live Installer v.1.3.zip`을 사용합니다. 설치 프로그램은 실행하지 않습니다. ZIP은 데이터로만 읽으며 샘플을 파일 시스템에 추출하지 않습니다. 실행할 악기의 ZIP만 필요합니다. 네 악기 프로필을 모두 병합한 뒤 네 ZIP으로 통합 비교할 수 있습니다. raw ZIP/WAV는 저장소에 넣지 않습니다.

`examples/assets.template.json`을 저장소 밖에 복사하고 ZIP 경로를 채웁니다. 상대 경로는 assets JSON이 있는 폴더 기준입니다.

```powershell
.\.venv-playback\Scripts\python -m playback `
  --score playback/examples/seryeongsan-first-jangdan.json `
  --assets C:/local-gugak/assets.json `
  --out C:/local-gugak/preview
```

하나만 생성하려면 `--instrument danso`처럼 현재 브랜치에 등록된 악기 키를 지정합니다. 기본 `all`은 등록된 프로필만 실행합니다. `--transpose -12`는 사용자가 선택한 전체 반음 이조이며, 악기 음역에 맞춰 자동 이조하지 않습니다. 출력 폴더에는 MIDI, WAV, 음표·샘플 추적 JSON, manifest, 로컬 청취 페이지가 생깁니다. 입력·렌더 실패 시 기존 결과를 보존하고 임시 산출물을 정리합니다. 최종 파일 복사 단계의 OS/디스크 장애까지 원자적 저장을 보장하지는 않습니다.

## 사진 입력·재생 작업실

`ai/`에서 다음 명령을 실행합니다. CAT assets JSON은 위 CLI와 같습니다. `--storage`에는 Git 밖의 본인 전용 출력 폴더를 지정합니다. 기존 채점 의존성·uv.lock은 변경하지 않습니다.

```powershell
.\.venv-playback\Scripts\python -m pip install --only-binary=:all: -r playback/requirements-service.txt
.\.venv-playback\Scripts\python -m unittest discover -s playback/tests -p "test_*.py" -v
.\.venv-playback\Scripts\python -m playback.service --assets C:/local-gugak/assets.json --storage C:/local-gugak/photo-jobs
```

[로컬 작업실](http://127.0.0.1:8765)을 열고 악기·표기법·추가 흔들림을 고른 뒤 사진을 업로드합니다. 인식기가 설정되어 있으면 음표 입력·확인란 없이 자동 미리듣기까지 생성합니다. 이후 악기를 바꾸고 **선택 악기로 다시 듣기**를 누르면 저장된 음표로 재합성하며 사진 인식을 반복하지 않습니다. 원래 음높이·기보 옥타브·황종은 유지하고 자동 편곡/이조하지 않습니다. 자동 결과는 `machine-transcribed-unverified`와 `review_mode=automatic-preview`로 남습니다. 원본과 대조해 확인한 요청만 `user-confirmed`와 `reviewed_revision`을 기록하며 기계 전사 출처를 수동 전사로 바꾸지 않습니다.

`examples/assets.template.json`의 네 키를 실제 로컬 CAT ZIP에 연결합니다: `danso`, `jeongak-daegeum`, `sanjo-daegeum`, `sogeum`. 미설정 악기는 화면에서 사용 불가 사유를 표시합니다. 현재 simple 배포본에서 두 대금은 공통 MIDI60–89의 샘플과 설정이 같아 동일 선율·표현의 음원이 같을 수 있습니다. 악기 네 개를 선택할 수 있다는 것이 서로 독립적인 녹음 음색 네 개를 확보했다는 뜻은 아닙니다.

로컬 API는 `POST /api/scores?notation=...&instrument=...`로 사진을 만들고, `/automatic`으로 인식·합성합니다. `/preview`에는 `{revision, instrument, vibrato}`를 보내 저장된 악보를 미검수 상태로 다시 듣습니다. `/render`의 `approved:true`는 실제 원보 대조를 한 경우에만 사용합니다. `/draft`도 선택 악기를 받아 그 가이드 음역으로 검사합니다. 악기 필드를 생략한 기존 작업·요청은 단소로 호환되며 기존 다운로드 주소를 유지합니다. 캐시는 revision·악기·흔들림·검수 모드를 구분합니다. 새 악기의 음역/샘플 실패가 기존 성공 음원을 덮지 않으며 실패 악기를 기록합니다.

작은 API/음원 회귀는 위 unittest로, 악기 선택·기존 음원 보존·확인 재생 실패 시 불필요한 draft 저장 방지는 `node playback/tests/test_web.cjs`로 검사합니다(Node.js 필요, ai/ 기준).

오선보 출력은 **실음 MIDI**, 정간보 출력은 **황종 실음 MIDI와 율명/register**입니다. 자동 정간보 미리듣기는 중성 黃=MIDI63(E♭4), 한 정간=점4분, 오른쪽 열부터 아래로 읽는 가정을 표시합니다. 이 값은 사진에서 추정한 조율이 아닙니다. 빠르기가 없으면 quarter BPM60을 표시된 가정으로 씁니다. 다른 조율·박 단위는 API context에서 명시하거나 편집 화면에서 수정해야 합니다. 표로 입력하거나 기존 JSON을 불러오는 경로도 유지합니다.

음표 행의 위치 버튼을 누르고 사진에서 영역을 드래그하면 해당 음표 ID와 연결됩니다. 사진과 데이터를 대조하고 확인란을 선택한 뒤 생성하면 WAV 재생·MIDI 다운로드·현재 음표 표시를 사용할 수 있습니다. 주소의 `#작업ID`로 저장된 작업을 다시 엽니다. 수정 후에는 다시 확인해야 하며, 생성 실패가 이전 성공 음원을 덮어쓰지 않습니다.

새 화면/API의 기본 추가 피치 흔들림은 **반음의 1/4(±25cent)**, 선택값은 **1/3(±100/3cent)**·없음입니다. 0.65초 이상 음에 4.5Hz, 지연 0.22초, fade-in/out 0.18/0.16초를 적용합니다. 악보에 명시된 pitch/mixed 요성에는 중복 적용하지 않습니다. 이는 이전 청취 실험의 사용자 선호 설정이며 국악 기보 규정이 아닙니다. CLI는 기존처럼 기본 `none`이며 `--vibrato quarter` 또는 `--vibrato third`를 명시해 선택합니다.

자동 인식 설정:

- [Ollama Windows standalone](https://docs.ollama.com/windows): 확인한 버전 `0.40.10`. NVIDIA 포함 ZIP을 별도 로컬 폴더에 풉니다. 모델은 `ollama pull qwen3-vl:4b-instruct-q4_K_M`으로 설치합니다. 단순 `4b` 태그와 Instruct 태그는 다릅니다. 확인한 모델 digest는 `84f7ed8042fa7cdbd902f7dcb7d0370e032095047cc3244179f263b77b5bc318`입니다.
- [Audiveris 5.11.0 Windows Console](https://github.com/Audiveris/audiveris/releases/tag/5.11.0): MSI를 관리 추출하면 포함 JRE로 실행할 수 있습니다. `msiexec /a <MSI절대경로> /qn TARGETDIR=<새폴더절대경로>`를 사용합니다. 공식 asset SHA256은 `5f1b4e96a12c53c7da426814b76e599363c4181e291855996e0a6878dda95f71`입니다. [Tesseract eng.traineddata](https://github.com/tesseract-ocr/tessdata/blob/main/eng.traineddata)를 실행 파일 옆 `tessdata/`에 둡니다.
- 서버는 설치·모델 다운로드를 자동 수행하지 않습니다. `--ollama-url`은 loopback HTTP만 허용하며 로컬 모델을 설정합니다. 모델/OMR 실패는 원인과 함께 반환하고 가짜 음표·무음으로 대체하지 않습니다.

```powershell
# ai/에서. 위에서 설치한 가상환경과 실제 로컬 경로 사용
.\.venv-playback\Scripts\python -m playback.service `
  --assets C:/local-gugak/assets.json --storage C:/local-gugak/photo-jobs `
  --model qwen3-vl:4b-instruct-q4_K_M `
  --audiveris C:/local-tools/Audiveris/Audiveris.exe
```

이 PC에서는 루트 `.venv`와 `%LOCALAPPDATA%/score-playback/`에 실행 환경을 구성했습니다. 루트 `.venv`를 사용하는 편의 실행기는 `playback/start-local.ps1 -Assets <assets.json> -Storage <Git밖폴더>`입니다. 필요하면 `-OllamaExe`, `-Models`, `-Audiveris` 경로를 바꿉니다. 기존 Ollama 서버가 없을 때만 숨김 실행하며 사용자 환경 변수·자동 시작 등록은 바꾸지 않습니다.

오선보는 Audiveris가 생성한 MusicXML을 읽고, 실제 오선 위치에서 잘라낸 조표·음자리표·빠르기를 Qwen3-VL로 보조 확인합니다. 단일 오선 구간에서는 누락된 3·5·6잇단음표의 숫자와 곡선 범위를 연결하고, 짧은 위음 장식과 단일 앞꾸밈을 복원합니다. 원본 OMR/MusicXML, 보정본·기호 crop·모델 응답·기하학 근거를 작업 폴더에 함께 보존합니다. 여러 오선 줄에서는 이 추가 보정을 생략하고 Audiveris 결과와 경고를 유지합니다. 모든 장식·불균등/중첩 잇단음표를 지원하는 것은 아닙니다.

정간보는 짧은 격자 구간과 페이지의 격자·음악 열·칸·기호 행을 자동 분리합니다. 원본 해상도에서 율명·청성·지속선·반복·쉼표와 지원하는 상대음 기호를 읽고, 빈 정간은 앞 음을 연장합니다. 한 페이지는 최대256칸·512행이며, 범례 경로는 추가로 분리 기호512개·300초 추론으로 제한합니다. 미확인 본음/기호나 음악 열의 불완전한 칸이 있으면 오류를 반환합니다. 아래 범례 경로로 세령산40칸/97행 전체의 합성 입력을 생성했으며, atlas를 지정하지 않으면 이전 판독 경로를 사용합니다. 이 개발 악보의 수정 후 결과와 다른 자료의 성공·실패를 별도로 기록합니다. 설치에 포함한 [OpenCV headless](https://pypi.org/project/opencv-python-headless/4.11.0.86/)는 격자·연결 요소 계산에 사용합니다.

자동 오선보의 국악 위음 장식은 黃=E♭ 기준 黃太仲林無 음계로 해석하는 미리듣기 가정입니다. 다른 청·서양식 장식은 `context.ornament_pitch_classes`에 중복 없는 MIDI pitch class 목록(0~11)을 명시합니다. 인식 API에서 이 힌트를 생략하면 조표의 온음계 이웃음을 사용합니다. 힌트는 공통 악보 IR의 `context`에 추가하지 않고 `recognition_context`에 보존하며 재인식에도 유지합니다. 장식의 1/16 quarter 길이는 실제 기보의 절대값이 아닌 기존 합성 정책입니다.

복합 정간보의 범례 대조 경로는 별도 로컬 atlas를 사용합니다. 교재·glyph PNG는 저장소에 포함하지 않습니다. 범례 생성에는 `pypdfium2==5.14.0`과 Poppler `pdftoppm`이 필요하며, 평상시 인식은 기존 서비스 환경으로 실행합니다. 각 범례의 원래 렌더러·해상도를 유지해 픽셀을 재현합니다. 국립국악원 `eng_danso.pdf`의 인쇄35–37쪽과 국립국어원2024 한국 점자 규정의 국악 반복표 범례를 로컬에 준비한 뒤 빈 출력 폴더를 지정합니다. 대상 곡의 정답 음열이나 대상 페이지 glyph를 atlas에 추가하지 않습니다.

```powershell
# ai/에서, pypdfium2가 설치된 Python으로 범례 생성
python -m playback.build_jeonggan_atlas `
  --danso-pdf C:/local-gugak/eng_danso.pdf `
  --braille-pdf C:/local-gugak/korean-music-braille-2024.pdf `
  --pdftoppm (Get-Command pdftoppm).Source `
  --output C:/local-gugak/jeonggan-atlas
# 이 PC의 root .venv를 사용하는 서버 실행기
./playback/start-local.ps1 -Assets C:/local-gugak/assets.json `
  -Storage C:/local-gugak/photo-jobs -JeongganAtlas C:/local-gugak/jeonggan-atlas/manifest.json
```

CLI 인식에서는 `PLAYBACK_JEONGGAN_ATLAS`에 같은 manifest 절대 경로를 지정합니다. 프로세스 환경 설정이며 개인 영구 설정을 바꾸지 않습니다. manifest와 실제 glyph 파일의 해시는 trace에 기록합니다. 범례 글꼴·획에 맞춘 개발 경로로, 임의 글씨체·흐린 촬영 사진에 대한 일반 인식률을 보장하지 않습니다. 정간 길이·조율·빠르기와 짧은 꾸밈의 시가는 화면에 표시하는 미리듣기 가정입니다.

실제 모델 평가에는 사진과 별도로 원보 대조 정답 JSON을 준비합니다. 정답은 추론이 끝난 다음에만 읽으며 모델에 전달하지 않습니다. `cases.json`은 다음 객체들의 배열입니다. `image`와 `reference`에는 실제 절대 경로를 사용합니다.

```json
[
  {
    "id": "staff-excerpt",
    "image": "C:/local-gugak/staff.png",
    "notation": "staff",
    "context": {
      "pitch_mode": "sounding", "notation_octave_shift": 0,
      "tuning_a_hz": 440, "style": "danso",
      "tuning_description": "A440 equal temperament preview"
    },
    "reference": "C:/local-gugak/staff-reference.json"
  }
]
```

정간보는 `notation=jeonggan`, `context.pitch_mode=yul`로 바꾸고 `huangjong_midi=63`, `cell_quarters=1.5`, `reading_direction="top-to-bottom,right-to-left"`를 명시합니다. 이 값은 평가 조건이지 사진에서 인식한 결과가 아닙니다. 다른 악보라면 해당 문맥을 확인해 바꿉니다.

```powershell
# ai/에서, 위 환경 설치 후 실행. 이 PC의 루트 .venv라면 ..\.venv\Scripts\python 사용
.\.venv-playback\Scripts\python -m playback.evaluate `
  --cases C:/local-gugak/cases.json --out C:/local-gugak/evaluation `
  --model qwen3-vl:4b-instruct-q4_K_M `
  --audiveris C:/local-tools/Audiveris/Audiveris.exe
```

`report.json`에 삽입·누락을 포함한 음높이 편집 거리, 음높이+길이 일치, 장식의 본음·종류·음높이·길이 일치, 템포, 전체 박 길이와 시간이 기록됩니다. 소리 나는 음과 쉼표는 별도 지표로도 집계합니다. 실패한 사진은 정답 이벤트 수를 분모에 유지하고 복원 0개로 기록합니다. `summary.json`은 `split`별로 집계하며 전체 페이지와 같은 내용의 crop은 독립 표본으로 합산하지 않아야 합니다. `run.json`의 실제 소스 해시와 `*-recognized.json`·`*-trace.json`의 초안·응답·입력 해시·추론 설정을 함께 보존합니다. 개발 악보와 별도 평가 악보, 수정 후 재검증 수치를 구분한 결과는 아래 서비스 기록에 있습니다.

`expanded_*` 지표는 장식 객체를 실제 순서의 음들로 풀고 붙임줄을 합쳐, 명시적 음표열로 표현한 같은 결과와 비교합니다. `expanded_timed_matches`는 음높이·시작 박·길이가 모두 같은 경우만 셉니다. 쉼표는 시간축에 반영하고 호흡에 의한 무음·음량 포락선·템포는 이 지표에서 제외합니다. 객체 단위 장식 인식 지표와 함께 보고하며 전문가 실연 일치율로 해석하지 않습니다. 사진 전처리·수동 crop·비교 조율 조건과 정답을 읽을 수 없는 제외 사례를 별도로 기록하세요.

촬영 사진의 오선보에서 첫 OMR에 화음·성부 겹침이 검출되면, 오선1개·유효 선간격·음영이 확인된 입력에 한해 밝기와 선간격을 보정하여 한 번 재시도합니다. [Audiveris 입력 지침](https://audiveris.github.io/audiveris/_pages/guides/advanced/scanning/)의 회색조·약20px 선간격을 참고한 경로입니다. 두 OMR 실행은 같은 시간 예산을 쓰며 보정 후에도 단선율 조건을 통과해야 합니다. 원본 OMR·보정 이미지·정역 좌표변환·실패를 trace에 보존합니다. 자동 화음 삭제·정답 음표 채우기·원근 추정은 하지 않습니다. 보정 초안이 생성되어도 원본과 비교해야 합니다.

서비스는 **한 프로세스·한 사용자·loopback 전용**입니다. 업로드는 8MiB/1600만 화소, 악보는 1000음/10분, 생성·인식은 동시 1건으로 제한합니다. Audiveris와 정간보는 정규화한 원본 PNG를 사용하고, 범용 VLM 경로만 긴 변 2500px 이내 JPEG를 사용합니다. 정간보 원본 디코딩은 JPEG→PNG 팽창을 고려한 64MiB/16MP 제한이며 모델에 보내는 각 crop의 10MiB 제한은 유지합니다. normalized bbox는 같은 상대 좌표입니다. 작업·실패 원인은 `--storage`에 보존하며 자동 삭제하지 않습니다. 운영 서비스의 인증·소유자 격리·보관 정책·백엔드/모바일 연결은 아직 없습니다. 서버를 외부에 노출하지 않고 기존 앱에 통합할 때 별도로 설계해야 합니다.

로컬 API·검증 범위는 [사진 재생 서비스 기록](../../docs/knowledge/ai/score-photo-service.md)에 있습니다.

## 악보 JSON 입력 범위

예제는 세령산 단소 첫 장단의 37개 본음을 원보 대조 후 수동 입력한 것입니다. 3개 위음 꾸밈과 F5 앞꾸밈을 확장하면 44개 MIDI 이벤트가 됩니다. 음표 수는 정확도 지표가 아닙니다.

| 입력 | 계약 |
| --- | --- |
| `source` | 판본·출처·전사 상태. `manually-transcribed-unverified` 또는 `machine-transcribed-unverified`. 둘 다 전문가 미검수이며 자동 재생은 별도 `review_mode`에 남김 |
| `pitch_mode=sounding` | 조표·임시표·옥타브를 이미 해결한 정수 MIDI 실음. `notation_octave_shift=0` 필수 |
| `pitch_mode=written` | 조표·임시표를 해결한 정수 MIDI 기보음 + 명시한 `notation_octave_shift`. 이 모드는 옥타브 이조만 지원 |
| `pitch_mode=yul` | `huangjong_midi` 기준과 `{yul: "黃", register: 1}`. register는 중성 기준 옥타브 차이. 청/관 조율과 청성을 혼동하지 않음 |
| `tuning_a_hz` | 비교용 전체 기준음 이동. CAT 샘플의 실제 조율을 자동 교정하지 않음 |
| `duration` | 4분음표 단위의 양수 유리수. 예: `1`, `1/6`, `3/2`. PPQN 4800에서 표현 가능한 값 |
| `rest` / `tie` / `slur` | 쉼표 / 같은 음을 합치는 붙임줄 / 연결 발음. 서로 다른 의미. 붙임줄 중간의 주법·음량·슬러 변경은 지원하지 않으면 실패 |
| `ornament` | `upper-return` 또는 `grace-before`; 이웃음/앞꾸밈의 음높이를 반드시 명시. 기본 길이 1/16박은 합성 설정값 |
| `breath_seconds` | 직전 음에서 요청 무음+50ms release를 차감. 박 길이는 늘리지 않음 |
| `expression` | 음표 시작·중간·끝의 CC11(0–127). 기본 127 고정 |
| `yoseong` | 명시된 음에만 적용. mode=amplitude/pitch/mixed. 단소 정악은 음량 떨림 근사, 소금은 mixed를 제안 기본값으로 사용. 대금은 mode 직접 선택 필수 |

요성 수치 기본값은 4Hz, 25cent, amplitude depth0.08, 지연250ms, 시작/끝 fade150ms이며 **교재의 규정이 아닌 작성자 선택값**입니다. depth0.08은 ±8%가 아니라 fade가 끝난 구간에서 CC11을 기준의92~100%로 변화시키는 값입니다. `amplitude`는 음량 떨림 근사입니다. 세령산 첫 장단 예제에는 명시된 요성이 없으며, CAT 원 샘플의 변화는 남습니다.

꾸밈의 기본 `1/16 quarter`는 4분음표의1/16으로, quarter BPM60에서62.5ms입니다. 일반적인16분음표와 다릅니다. 숨은 미지정 시0이고 예시의 숨표는 `breath_seconds: 0.12`로120ms 공백을 지정합니다. release50ms를 포함해 note-off를170ms 앞당깁니다. 속도 변경 시 꾸밈은 박 비율을 따르지만 숨·떨림 주기·지연·fade는 고정 초입니다. 표현별 초기값·중복 적용·예외·근거는 [재생 초기값 기록](../../docs/knowledge/ai/score-instrument-playback-pipeline.md#2026-10-27-단소-표현의-재생-초기값)을 따릅니다. 이 값은 연주 채점 허용치가 아닙니다.

알 수 없는 필드·기호, 출처/조율 문맥 누락, 불완전 붙임줄, 장식 시간 초과, 기술 음역 이탈, 키존 누락/중복, 샘플 소진은 오류입니다. 1회 입력은 최대 10분/1만 음입니다. `technical_range`는 해당 프로필의 교재 기준이며 모든 개량악기를 포함하지 않습니다. 산조대금은 관종별 음역 미확정으로 CAT 범위만 검사합니다. 운지·빠른 반음 진행·호흡 가능성은 별도 검토입니다.

## 출력과 표현의 한계

- MIDI RPN ±2반음, CC11, CC68, Pitch Bend를 기록하고 WAV에서 적용합니다. 음표 ID는 JSON `parent_ids`에 보존합니다.
- 한 악기·한 단선율 채널씩 출력합니다. 4중주나 임의 외부 MIDI의 범용 재생기가 아닙니다.
- PCM 44.1kHz mono, 선형 샘플 보간, 선형 attack/release, 20ms 교차페이드, 선형 velocity/CC11 게인 근사입니다. CC11 값은 다음 메시지까지 유지해 다음 음의 강약을 이전 음에 미리 적용하지 않습니다. 단소만 이전 실험의 100ms 지속부 시작점을 적용합니다. 동일음 슬러는 샘플 위치를 이어갑니다.
- 샘플러 필터·FX·음색 변화·녹음된 실제 연결 주법은 재현하지 않습니다. 매우 긴 음을 임의로 반복하거나 무음으로 채우지 않습니다.
- 선택된 출력에 공통 게인을 사용합니다. 음량이 같게 정규화된 음색 비교는 아닙니다.
- **정악·산조 CAT simple은 공통 MIDI 60–89에서 같은 샘플과 설정입니다.** 악기명만 바꿔 서로 다른 녹음 음색이 생기지 않습니다.
- 공식 프리셋의 detune을 적용해도 샘플 자체의 잔여 조율 편차가 있습니다. 전음역 F0 교정은 미구현입니다.

## 검증

작은 고정 신호를 사용한 단위 검사는 CC11 음량비, 실제 주파수 이동, 음표/쉼표/붙임줄/슬러 구분, 원본 ID, 이중 옥타브 이동 방지, 음역/길이/미지원 기호 실패, 실제 숨 무음, 연속음 위상, 실패 시 이전 산출물 보존을 확인합니다. 기본 채점 서비스의 pytest 대상과 분리했습니다.

실제 CAT 검증은 ZIP 4개를 확보한 개발자가 위 CLI를 실행해 `manifest.json`·각 악기 JSON의 프리셋 해시, 음표 수, 길이, 피크, 샘플 추적, 경고를 확인합니다. 다른 악보·요성·장음을 추가하면 해당 음의 샘플 소진과 음높이 윤곽을 다시 검사해야 합니다. 단위 검사 통과는 국악적 연주 품질·전체 샘플 조율·브라우저 재생 검증을 뜻하지 않습니다.

출처·악기별 해석 결정과 실제 실행 결과는 [조사 기록](../../docs/knowledge/ai/score-instrument-playback-pipeline.md)에 있습니다. Jira: 단소·공통 S15P31E102-105, 정악대금 106, 산조대금 107, 소금 108.
