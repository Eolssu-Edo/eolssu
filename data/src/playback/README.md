# JGB·공통 악보 JSON의 단소 MIDI·CAT 음원 생성

JGB v1·v2 또는 공통 악보 JSON(`gugak-reviewed-score/v1`)을 해석해 MIDI를 저장하고, 그 MIDI를 CAT 단소 샘플로 WAV에 렌더하는 로컬 CLI입니다. [105](https://ssafy.atlassian.net/browse/S15P31E102-105)의 재생 기반에 [166](https://ssafy.atlassian.net/browse/S15P31E102-166)의 JGB 입력 연결을 더했습니다.

담당 범위: 박찬형은 **JGB를 바탕으로 정석 음원을 제작**하고, 사지한은 **채점·AI 평가**를 담당합니다(2026-10-27 사용자 확인). 이 도구는 정석 음원 제작을 위한 재생 기반입니다.

```text
JGB v1·v2 → 선택한 단소 성부의 공통 악보 JSON → 연주 이벤트 → MIDI → CAT 단소 WAV
```

`.jgb.json`을 `--score`에 직접 넣을 수 있습니다. 한 번에 단소 한 성부를 생성하며 사진 인식·웹 API·합주·연주 녹음 채점은 포함하지 않습니다. 출력 HTML은 생성된 음원을 듣는 정적 파일입니다.

## 설치와 빠른 검사

저장소 루트에서 Python 3.10 이상으로 별도 가상환경을 만듭니다. 아래 명령은 PowerShell 기준입니다.

```powershell
python -m venv data/.venv
.\data\.venv\Scripts\python -m pip install -r data/src/playback/requirements.txt
.\data\.venv\Scripts\python -m unittest discover -s data/src/playback/tests -p "test_*.py" -v
```

작은 합성 신호를 사용하는 검사이며 CAT 파일·모델·서버·네트워크가 필요하지 않습니다. 음표/쉼표/붙임줄/이음줄·원본 ID·음역·실제 무음·CC11·Pitch Bend·추가 떨림과 실패 시 기존 산출물 보존을 확인합니다.

## 실제 단소 음원 생성

[CAT 국악 라이브러리](https://www.catseoul.com/gugak-library/)의 단소 Live Sampler v1.3 ZIP을 로컬에 준비합니다. 아래 내용을 Git 밖의 `assets.json`으로 저장하고 `danso` 값을 실제 ZIP 경로로 바꿉니다. 상대 경로는 assets JSON의 폴더 기준입니다. ZIP·원본 악보·생성 음원은 저장소에 넣지 않습니다.

```json
{"danso": "C:/local-gugak/GUgAK-DEMO DanSo Live Installer v.1.3.zip"}
```

```powershell
.\data\.venv\Scripts\python -m data.src.playback --score data/src/playback/examples/seryeongsan-first-jangdan.json --assets C:/local-gugak/assets.json --instrument danso --out data/processed/playback/seryeongsan-fixed25
```

추가 떨림은 **±25cent로 고정**하며 별도 선택 옵션이 없습니다. 이전 `--vibrato` 인자는 명령에서 제거합니다. 원본 CAT 샘플에 녹음된 흔들림은 그대로 남습니다. `--transpose`는 명시적인 반음 이동이고 기본0입니다. 자동 옥타브 보정·편곡은 하지 않습니다. 현재 등록 프로필은 단소 하나이며 `--instrument all`은 등록된 프로필 전체를 뜻합니다.

출력 폴더에는 `danso.mid`, `danso.wav`, 해석·샘플 추적 `danso.json`, 입력 사본 `score.json`, 재현 정보 `manifest.json`, 정적 미리듣기 `comparison.html`이 생성됩니다. WAV는44.1kHz·mono·PCM16이며 렌더 실패 시 이전 출력 파일을 보존합니다.

## JGB 파일에서 생성

같은 설치·CAT 준비를 마친 뒤 저장소 루트에서 실행합니다. 원본과 다른 출력 폴더를 사용합니다.

```powershell
.\data\.venv\Scripts\python -m data.src.playback --score C:/local-gugak/song.jgb.json --assets C:/local-gugak/assets.json --instrument danso --out data/processed/playback/song
```

- v1의 `melody`, v2의 `parts[].melody`를 읽습니다. 여러 성부면 `--part 1`처럼 1부터 번호를 지정해야 합니다. `activePart`는 편집 위치라 자동 선택에 쓰지 않습니다.
- `tempoBpm`은 **정간/분**입니다. `--bpm 60`으로 덮어쓰거나 `--speed 0.75`로 속도를 바꿉니다. 정간을 4분음표로 환산할 때 길이와 BPM에 같은 비율을 적용합니다. `dotted=3/2`, `plain=1`, `eighth=1/2`; `auto`·누락은 기본각이12정간이면 `eighth`, 나머지는 `dotted`입니다.
- 황종은 `hwangPitch`를 사용하며 `--hwang-midi 63`으로 지정할 수 있습니다. BPM·황종이 없으면 자동으로 추측하지 않습니다. `tempoBpmGak`은 표시용이므로 재생 속도로 쓰지 않습니다.
- 한글 율명·청/중청/배/하배, 같은 각 안의 빈칸·`-` 연장, 쉼·쉼표, 숨표`<`를 지원합니다. 같은 율명을 다시 적으면 새로 발음합니다. 각 첫 연장은 쉼이며 각 정간 수·분박 분할이 잘못되면 실패합니다.
- 앞꾸밈: 니레·니라·니로·노네·너녜·노니로·노리노·네로네. 본음 음열: 나니나·나느나. 독립 상대음: 노·니·로·리·니나·느나·노라·느니·같은음표. `{한글 이름}`과 대응 registry ID를 받습니다. 상대음에는 명시한 `joPreset` 또는 `--jo jung-pyeong` 등이 필요하며 음계를 추정하지 않습니다.
- 앞꾸밈은 각각 `1/16 quarter`·velocity80, 음열은 본음 자리를 균등 분할하고 연결합니다. `<`는 그 정간 끝에120ms 숨을 넣습니다. 우물사이 웹 재생의 최대90ms/30% 꾸밈·숨표 무음 미적용과는 다른 프로젝트 재생값입니다.
- **농음·흘림·추성·퇴성·길이 변경·반복 기호의 JGB 해석, 한자 율명, 장구 반주·합주는 미지원**입니다. 기호를 버리거나 옥타브를 자동 보정하지 않고 성부·각·정간 위치를 알려 실패합니다. 고정25cent 떨림이 이 기호들을 해석한 결과는 아닙니다.

JGB 입력에서는 원본 바이트 사본 `source.jgb.json`과 `source-map.json`이 추가됩니다. `score.json`은 변환된 공통 악보이며 원본과 구분합니다. `danso.json`의 `events[].parent_ids`를 대응표의 `notes` 키로 찾으면 성부 원본 ID·각·정간·분박·문자 범위와 연장된 칸을 확인할 수 있습니다. 대응표의 시간은 원본 자리의 시간이고 실제 발음 시간은 `events`에 있습니다. 문자 범위는 **0부터 세는 Unicode 문자 위치·끝 제외**이며 JavaScript UTF-16 위치와 구분합니다. manifest에 원본·변환본·대응표·소스 해시와 선택한 템포·배율을 기록합니다. JGB 결과 폴더를 공통 악보 JSON 입력에 재사용하면 이전 대응표가 남지 않도록 거부합니다.

## 공통 악보 JSON 입력

입력은 단선율 음표·쉼표를 순서대로 나열합니다. 예제는 세령산 첫 장단37개 입력을 꾸밈 확장 후44개 MIDI 음으로 만듭니다. 이 숫자는 인식 정확도가 아닙니다.

| 필드 | 현재 코어의 의미 |
| --- | --- |
| `schema` | `gugak-reviewed-score/v1`. 이름과 별개로 원보 검수 상태를 명시 |
| `source` | 판본 `edition`, `manually-transcribed-unverified`·`machine-transcribed-unverified`·`source-review-unverified` 상태. JGB 입력의 원보 검수 상태는 추정하지 않음 |
| `context` | `sounding` 실음 MIDI, `written` 명시 옥타브 이동, 또는 `yul`의 황종 MIDI+율명/register. 스타일·조율 설명 필수 |
| `quarter_bpm` | 4분음표 기준 BPM. JGB의 주박·정간 단위와 구분 |
| `notes[].id` | 중복 없는 문자열. 장식/붙임줄 처리 후 JSON의 `parent_ids`로 보존 |
| `duration` | 4분음표를1로 하는 양수 유리수. 예: `1`, `1/6`, `3/2`; PPQN4800에서 표현 가능해야 함 |
| `rest` / `tie` / `slur` | 쉼 / 같은 음을 합치는 붙임줄 / 연결 발음. 같은 의미로 처리하지 않음 |
| `ornament` | `grace-before` 또는 `upper-return`. 이웃음 높이 명시. 기본 시가 `1/16 quarter` |
| `breath_seconds` | 음 끝에서 요청 공백+release50ms만큼 차감. 악보 시간은 유지 |
| `expression` | 시작·45%지점·끝의 CC11 값0~127. 기본 `[127,127,127]` |
| `yoseong` | 명시된 음의 amplitude/pitch/mixed 요성. 단소 정악의 기본은 amplitude |

**MIDI 파일 자체에는 원본 음표 ID를 저장하지 않습니다.** 악기별 JSON의 `parent_ids`와 JGB 입력의 `source-map.json`을 함께 보관합니다. 여러 성부 합주·JGB 반복 전개·임의 시김새를 자동 처리하는 기능은 없습니다.

실음 MIDI를 쓰거나 `황종 MIDI + 율명 반음 간격 + register×12`로 음높이를 정합니다. 단소 프로필의 교재 기준 음역은 MIDI66~92입니다. 黃=63이라는 환산 기준과 실제 연주할 옥타브를 구분하며, 범위 밖 음을 자동으로 올리지 않습니다. `tuning_a_hz`는 비교 기준 이동이고 CAT 샘플의 잔여 조율 편차를 자동 교정하지 않습니다.

## 표현 초기값과 한계

- 꾸밈 `1/16 quarter`는 4분음표의1/16입니다. quarter BPM60에서62.5ms이며 일반적인16분음표(`1/4 quarter`)와 다릅니다.
- 명시 요성: 4Hz, ±25cent(pitch/mixed), amplitude depth0.08, 지연250ms, 시작/끝 fade150ms. depth0.08은 충분히 적용된 구간의 CC11 배율92~100%를 뜻합니다.
- 추가 떨림: ±25cent, 650ms 이상 음에4.5Hz·지연220ms·fade-in/out180/160ms. pitch/mixed 요성과는 중복되지 않으며 amplitude 요성에는 겹칠 수 있습니다.
- 숨은 미지정0초, 예제 숨표는120ms입니다. note-off를170ms 앞당겨 release50ms 뒤에 공백을 남깁니다. 짧은 음을 모두 소모하면 오류입니다.
- 연결 attack/release20ms, 일반 release50ms, 단소 연결 시 새 샘플의100ms 지점부터 재생합니다. 동일음 연결은 샘플 위치를 이어갑니다.
- 속도 변경 시 꾸밈은 박 비율을 따르고 숨·떨림 주기·지연·fade는 고정 초입니다. 곡별 빠르기와 음가 단위를 먼저 확인해야 합니다.

이 값은 교재의 표준이나 채점 허용치가 아닌 조절 가능한 실험값입니다. 기호별 적용·근거·남은 검수는 [단소 표현 조사 기록](../../../docs/knowledge/data/score-instrument-playback-pipeline.md)에 있습니다. 추성·퇴성의 곡선, 물리적 호흡·음색 변화, 긴 음의 자동 샘플 반복은 구현하지 않았습니다. 알 수 없는 기호·조율 문맥 누락·불완전 붙임줄·장식 시간 초과·음역 이탈·샘플 소진은 실패합니다.

## 실제 CAT 검증

CAT ZIP을 보유한 개발자가 위 CLI를 실행해 입력37→MIDI44, 원본 ID, manifest/악기별 JSON의 소스·입력·프리셋·샘플 해시, WAV 길이·피크·무음을 확인합니다. 재현할 때는 같은 입력·ZIP·속도를 사용합니다. 작은 합성 검사 통과와 실제 샘플 생성 성공은 전문가의 원보·연주 품질 검수와 구분합니다. 현재 검증 결과와 재현 환경은 [음원과 재현 기록](../../../docs/knowledge/data/score-instrument-playback-pipeline.md#음원과-재현)에 있습니다.
