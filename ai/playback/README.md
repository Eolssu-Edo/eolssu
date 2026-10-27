# JGB 연계를 위한 단소 MIDI·CAT 재생 기반

공통 악보 JSON(`gugak-reviewed-score/v1`)을 해석해 MIDI를 저장하고, 그 MIDI를 CAT 단소 샘플로 WAV에 렌더하는 로컬 CLI입니다. Task [S15P31E102-105](https://ssafy.atlassian.net/browse/S15P31E102-105)의 범위는 이 재생 기반과 단소 표현의 수치·근거입니다.

담당 범위: 박찬형은 **JGB를 바탕으로 정석 음원을 제작**하고, 사지한은 **채점·AI 평가**를 담당합니다(2026-10-27 사용자 확인). 이 도구는 정석 음원 제작을 위한 재생 기반입니다.

```text
JGB → [후속: 기호·시간·원본 위치를 해석하는 변환기]
    → 공통 악보 JSON → 연주 이벤트 → MIDI → CAT 단소 WAV
```

**`.jgb.json`을 `--score`에 직접 넣는 기능은 없습니다.** 사진 인식·웹 API·검수 화면·다른 악기 프로필·연주 녹음 채점도 이 MR에 포함하지 않습니다. 출력 HTML은 생성된 음원을 듣는 정적 파일입니다.

## 설치와 빠른 검사

소스 checkout의 `ai/`에서 Python 3.10 이상으로 별도 가상환경을 만듭니다. 이 CLI는 채점 서비스의 wheel/기본 의존성에 포함되지 않습니다. 아래 명령은 PowerShell 기준입니다.

```powershell
python -m venv .venv-playback
.\.venv-playback\Scripts\python -m pip install -r playback/requirements.txt
.\.venv-playback\Scripts\python -m unittest discover -s playback/tests -p "test_*.py" -v
```

작은 합성 신호를 사용하는 검사이며 CAT 파일·모델·서버·네트워크가 필요하지 않습니다. 음표/쉼표/붙임줄/이음줄·원본 ID·음역·실제 무음·CC11·Pitch Bend·추가 떨림과 실패 시 기존 산출물 보존을 확인합니다.

## 실제 단소 음원 생성

[CAT 국악 라이브러리](https://www.catseoul.com/gugak-library/)의 단소 Live Sampler v1.3 ZIP을 로컬에 준비합니다. 아래 내용을 Git 밖의 `assets.json`으로 저장하고 `danso` 값을 실제 ZIP 경로로 바꿉니다. 상대 경로는 assets JSON의 폴더 기준입니다. ZIP·원본 악보·생성 음원은 저장소에 넣지 않습니다.

```json
{"danso": "C:/local-gugak/GUgAK-DEMO DanSo Live Installer v.1.3.zip"}
```

```powershell
.\.venv-playback\Scripts\python -m playback --score playback/examples/seryeongsan-first-jangdan.json --assets C:/local-gugak/assets.json --instrument danso --out C:/local-gugak/danso-quarter
```

추가 떨림은 **±25cent로 고정**하며 별도 선택 옵션이 없습니다. 이전 `--vibrato` 인자는 명령에서 제거합니다. 원본 CAT 샘플에 녹음된 흔들림은 그대로 남습니다. `--transpose`는 명시적인 반음 이동이고 기본0입니다. 자동 옥타브 보정·편곡은 하지 않습니다. 현재 등록 프로필은 단소 하나이며 `--instrument all`은 등록된 프로필 전체를 뜻합니다.

출력 폴더에는 `danso.mid`, `danso.wav`, 해석·샘플 추적 `danso.json`, 입력 사본 `score.json`, 재현 정보 `manifest.json`, 정적 미리듣기 `comparison.html`이 생성됩니다. WAV는44.1kHz·mono·PCM16이며 렌더 실패 시 이전 출력 파일을 보존합니다.

## 입력과 JGB 연결 경계

입력은 단선율 음표·쉼표를 순서대로 나열합니다. 예제는 세령산 첫 장단37개 입력을 꾸밈 확장 후44개 MIDI 음으로 만듭니다. 이 숫자는 인식 정확도가 아닙니다.

| 필드 | 현재 코어의 의미 |
| --- | --- |
| `schema` | `gugak-reviewed-score/v1`. 이름과 별개로 원보 검수 상태를 명시 |
| `source` | 판본 `edition`, `manually-transcribed-unverified` 또는 `machine-transcribed-unverified` 상태 |
| `context` | `sounding` 실음 MIDI, `written` 명시 옥타브 이동, 또는 `yul`의 황종 MIDI+율명/register. 스타일·조율 설명 필수 |
| `quarter_bpm` | 4분음표 기준 BPM. JGB의 주박·정간 단위와 구분 |
| `notes[].id` | 중복 없는 문자열. 장식/붙임줄 처리 후 JSON의 `parent_ids`로 보존 |
| `duration` | 4분음표를1로 하는 양수 유리수. 예: `1`, `1/6`, `3/2`; PPQN4800에서 표현 가능해야 함 |
| `rest` / `tie` / `slur` | 쉼 / 같은 음을 합치는 붙임줄 / 연결 발음. 같은 의미로 처리하지 않음 |
| `ornament` | `grace-before` 또는 `upper-return`. 이웃음 높이 명시. 기본 시가 `1/16 quarter` |
| `breath_seconds` | 음 끝에서 요청 공백+release50ms만큼 차감. 악보 시간은 유지 |
| `expression` | 시작·45%지점·끝의 CC11 값0~127. 기본 `[127,127,127]` |
| `yoseong` | 명시된 음의 amplitude/pitch/mixed 요성. 단소 정악의 기본은 amplitude |

JGB 변환기에서는 문서 버전·성부·각·정간·음 위치를 입력 ID와 연결하는 대응표를 보존해야 합니다. **MIDI 파일 자체에는 원본 음표 ID를 저장하지 않습니다.** 현재 대응 정보는 악기별 JSON에 있습니다. 여러 성부 합주·JGB 반복 전개·임의 시김새를 자동 처리하는 기능은 없습니다.

실음 MIDI를 쓰거나 `황종 MIDI + 율명 반음 간격 + register×12`로 음높이를 정합니다. 단소 프로필의 교재 기준 음역은 MIDI66~92입니다. 黃=63이라는 환산 기준과 실제 연주할 옥타브를 구분하며, 범위 밖 음을 자동으로 올리지 않습니다. `tuning_a_hz`는 비교 기준 이동이고 CAT 샘플의 잔여 조율 편차를 자동 교정하지 않습니다.

## 표현 초기값과 한계

- 꾸밈 `1/16 quarter`는 4분음표의1/16입니다. quarter BPM60에서62.5ms이며 일반적인16분음표(`1/4 quarter`)와 다릅니다.
- 명시 요성: 4Hz, ±25cent(pitch/mixed), amplitude depth0.08, 지연250ms, 시작/끝 fade150ms. depth0.08은 충분히 적용된 구간의 CC11 배율92~100%를 뜻합니다.
- 추가 떨림: ±25cent, 650ms 이상 음에4.5Hz·지연220ms·fade-in/out180/160ms. pitch/mixed 요성과는 중복되지 않으며 amplitude 요성에는 겹칠 수 있습니다.
- 숨은 미지정0초, 예제 숨표는120ms입니다. note-off를170ms 앞당겨 release50ms 뒤에 공백을 남깁니다. 짧은 음을 모두 소모하면 오류입니다.
- 연결 attack/release20ms, 일반 release50ms, 단소 연결 시 새 샘플의100ms 지점부터 재생합니다. 동일음 연결은 샘플 위치를 이어갑니다.
- 속도 변경 시 꾸밈은 박 비율을 따르고 숨·떨림 주기·지연·fade는 고정 초입니다. 곡별 빠르기와 음가 단위를 먼저 확인해야 합니다.

이 값은 교재의 표준이나 채점 허용치가 아닌 조절 가능한 실험값입니다. 기호별 적용·근거·남은 검수는 [단소 표현 조사 기록](../../docs/knowledge/ai/score-instrument-playback-pipeline.md)에 있습니다. 추성·퇴성의 곡선, 물리적 호흡·음색 변화, 긴 음의 자동 샘플 반복은 구현하지 않았습니다. 알 수 없는 기호·조율 문맥 누락·불완전 붙임줄·장식 시간 초과·음역 이탈·샘플 소진은 실패합니다.

## 실제 CAT 검증

CAT ZIP을 보유한 개발자가 위 CLI를 실행해 입력37→MIDI44, 원본 ID, manifest/악기별 JSON의 소스·입력·프리셋·샘플 해시, WAV 길이·피크·무음을 확인합니다. 재현할 때는 같은 입력·ZIP·속도를 사용합니다. 작은 합성 검사 통과와 실제 샘플 생성 성공은 전문가의 원보·연주 품질 검수와 구분합니다. 현재 검증 결과와 재현 환경은 [Task105 계획](../../docs/plans/S15P31E102-105.md)에 기록합니다.
