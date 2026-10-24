# 악보 → 국악기 MIDI·음원 실험

원보를 대조한 단선율 악보를 MIDI/WAV로 만듭니다. 사진 업로드·정간보/오선보 입력·수정·확인·재생을 연결한 **단소 로컬 작업실**도 제공합니다. 자동 인식은 별도로 설치한 Ollama 비전 모델이 있어야 하며, 현재 실제 모델의 악보 인식 품질은 미검증입니다. 모델 없이 사진을 보며 음표를 입력하거나 기존 검수용 JSON을 불러와 실행할 수 있습니다. MusicXML 가져오기·자동 편곡·전문가 인증은 지원하지 않습니다.

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

[로컬 작업실](http://127.0.0.1:8765)을 열고 표기법을 고른 뒤 사진을 업로드합니다. 오선보는 조표·임시표·옥타브를 반영한 **실음 MIDI**, 정간보는 직접 확인한 **황종 실음 MIDI와 율명/register**를 입력합니다. 음표 길이 1은 4분음표이며 한 정간의 박 길이를 자동 가정하지 않습니다. 표로 입력하거나 `examples/seryeongsan-first-jangdan.json` 같은 기존 JSON을 불러올 수 있습니다. 장식·붙임줄·이음줄·숨표는 상세 JSON에서 보존·수정합니다.

음표 행의 위치 버튼을 누르고 사진에서 영역을 드래그하면 해당 음표 ID와 연결됩니다. 사진과 데이터를 대조하고 확인란을 선택한 뒤 생성하면 WAV 재생·MIDI 다운로드·현재 음표 표시를 사용할 수 있습니다. 주소의 `#작업ID`로 저장된 작업을 다시 엽니다. 수정 후에는 다시 확인해야 하며, 생성 실패가 이전 성공 음원을 덮어쓰지 않습니다.

새 화면/API의 기본 추가 피치 흔들림은 **반음의 1/4(±25cent)**, 선택값은 **1/3(±100/3cent)**·없음입니다. 0.65초 이상 음에 4.5Hz, 지연 0.22초, fade-in/out 0.18/0.16초를 적용합니다. 악보에 명시된 pitch/mixed 요성에는 중복 적용하지 않습니다. 이는 이전 청취 실험의 사용자 선호 설정이며 국악 기보 규정이 아닙니다. CLI는 기존처럼 기본 `none`이며 `--vibrato quarter` 또는 `--vibrato third`를 명시해 선택합니다.

자동 초안을 쓰려면 본인이 설치한 Ollama 비전 모델 이름을 `--model 모델이름`으로 전달합니다. 모델 자동 설치·외부 API 전송은 하지 않습니다. `--ollama-url`은 loopback HTTP만 허용합니다. 현재 모델이 없어 실제 인식 품질은 검증하지 않았으며, 모델이 없거나 실패하면 503과 입력 안내를 반환합니다. 정간보 읽기 방향·황종과 템포를 명시하고, 초안의 누락 기호·길이를 원보와 대조해야 합니다.

서비스는 **한 프로세스·한 사용자·loopback 전용**입니다. 업로드는 8MiB/1600만 화소, 악보는 1000음/2분, 생성·인식은 동시 1건으로 제한합니다. 원본 표시용 PNG와 긴 변 2500px 이내의 로컬 인식용 JPEG를 구분합니다. normalized bbox는 두 이미지에서 같은 상대 좌표입니다. 작업 파일은 `--storage`에 보존하며 자동 삭제하지 않습니다. 운영 서비스의 인증·소유자 격리·보관 정책·백엔드/모바일 연결은 아직 없습니다. 서버를 외부에 노출하지 않고 기존 앱에 통합할 때 별도로 설계해야 합니다.

로컬 API·검증 범위는 [사진 재생 서비스 기록](../../docs/knowledge/ai/score-photo-service.md)에 있습니다.

## 악보 JSON 입력 범위

예제는 세령산 단소 첫 장단의 37개 본음을 원보 대조 후 수동 입력한 것입니다. 3개 위음 꾸밈과 F5 앞꾸밈을 확장하면 44개 MIDI 이벤트가 됩니다. 음표 수는 정확도 지표가 아닙니다.

| 입력 | 계약 |
| --- | --- |
| `source` | 판본·출처·검수 상태. 현재 `manually-transcribed-unverified`: 원보 대조 수동 입력, 전문가 미검수 |
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

요성 수치 기본값은 4Hz, 25cent, 음량 깊이 8%, 지연 250ms이며 **교재의 규정이 아닌 작성자 선택값**입니다. `amplitude`는 음량 떨림 근사입니다. 세령산 첫 장단 예제에는 연속 요성 표기가 없어 추가 요성을 사용하지 않습니다. CAT 원 샘플에 포함된 변화는 남습니다.

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
