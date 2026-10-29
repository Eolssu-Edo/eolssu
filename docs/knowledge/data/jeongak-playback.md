# 정악대금 JGB·MIDI·CAT·M4A 변환

- 작업: [S15P31E102-106](https://ssafy.atlassian.net/browse/S15P31E102-106), Story S15P31E102-30.
- 확인일: 2026-10-29. 단소 !87·소금 !44가 병합된 develop `e113e34`의 공통 엔진에 통합했다. [MR !42](https://lab.ssafy.com/s15-final/S15P31E102/-/merge_requests/42)는 정악대금의 단일 JGB → score → MIDI → CAT PCM → M4A 변환과 필요한 테스트·실행 문서를 다룬다.
- 범위: 악기별 악보 해석과 검사용 합성. 실제 정악 연주의 음색·주법·편곡을 검증한 결과가 아니다.

## CAT는 무엇을 구분하는가

[CAT 공식 배포 페이지](https://www.catseoul.com/gugak-library/)에는 대금(통합)·정악대금·산조대금 다운로드가 따로 있다. 확보한 **Live Sampler v1.3** ZIP의 내부를 비교한 결과는 다음과 같다. 다른 포맷·버전까지 일반화하지 않는다.

| 비교 대상 | 정악대금 | 산조대금 |
| --- | --- | --- |
| simple 프리셋 | `DaeGeum (JeongAk) - DEMO_simple.adv` | `DaeGeum (SanJo) - DEMO_simple.adv` |
| 키존 범위 | MIDI58–89, 32개 | MIDI60–91, 32개 |
| WAV 파일 | 67개 | 67개, 정악 파일과 이름·SHA256 전체 동일 |
| 공통 MIDI60–89 | sample/root/detune/volume/trim/loop 동일 | 동일 |
| 전역 설정 | attack0.1ms, release50ms, gain−12dB | 사용자 표시 이름 외 동일 |

modal 프리셋도 각각 18개 키존이며 두 악기의 공통 구간 설정이 같았다. 따라서 현재 엔진에서 같은 MIDI·컨트롤로 공통 음역을 재생하면 같은 음색이 나온다. 별도 정악·산조 녹음 음색을 확보했다는 뜻으로 설명하지 않는다. 악기별 기보·음역·관종 판단은 여전히 구분한다.

- 정악 ZIP SHA256: `ca1f9388e55611e3766611409ca293bee4352a3960f367bb9c718aee5659f540`
- 산조 ZIP SHA256: `b27d2247543a019a45f8aba593e3aece425d66a59cbb14d156eca294d36d65b4`
- 정악 simple XML SHA256: `95904fa129e4b15805c72861716c59d78d46fe5126fa11283af9acb952b1ee2b`

비교는 ZIP을 실행하지 않고 WAV 해시와 gzip XML의 키존·전역 값을 읽었다. 원격 링크의 현 시점 파일 내용과 로컬 ZIP이 같다는 추가 검증은 하지 않았다. 실행 시 실제 ZIP·프리셋·사용 샘플의 해시는 manifest와 악기별 JSON에 다시 남는다.

## 악기별로 달라지는 규칙

[국립국악원 대금](https://www.gugak.go.kr/ency/topic/view/108)은 실음 기보와 입술·취구 조절에 따른 농음을 설명한다. [요성](https://www.gugak.go.kr/ency/topic/view/2105)은 장르·선법·연주자에 따라 폭과 시작 위치가 달라짐을 설명한다. [시김새](https://www.gugak.go.kr/ency/topic/view/1728)는 음높이뿐 아니라 주법·음색을 함께 다룬다. 이 자료들은 4Hz·25cent·120ms 같은 통일 수치를 제시하지 않는다.

| 대상 | 파이프라인의 처리 |
| --- | --- |
| 악기/기종 | 명시된 성부 또는 판본 근거가 있는 `playback.instrument`만 허용. 일반 `daegeum`·`all`·누락만으로 정악/산조를 고르지 않음 |
| 황종·옥타브 | JGB의 `hwangPitch`와 율명 register를 사용. 자동 청·관종 추정이나 옥타브 이동 없음 |
| 농음·흘림 | 비단소 JGB는 명시된 `playback.expression_policy` 요구. 선택 근거와 비표준 청취 설정 상태를 기록 |
| 표시 없는 떨림 | 비단소에는 추가 ±25cent를 적용하지 않음. CAT에 녹음된 흔들림은 남음 |
| 상대음 꾸밈 | 명시된 조 또는 `relative_scale.pitch_classes`와 근거 필요. 출현 음급을 단소 조로 자동 보충하지 않음 |
| 숨표 | JGB의 무음+감쇠를 앞 음 길이 절반 이내로 제한하는 공학적 정책. 악기별 표준 호흡 시간이라는 주장은 하지 않음 |
| 직접 작성 IR | 명시한 숨표·감쇠를 보존 |
| 빠르기 | 비단소 JGB의 미확정 편집기 기본 BPM을 거부. 판본 근거를 기록한 준비 입력 또는 명시적 BPM 필요 |

[율자보](https://www.gugak.go.kr/ency/topic/view/2089)의 황종 C/E♭ 구분은 음악 갈래의 기준이다. 악기 이름만 보고 바꾸지 않는다. 정악대금의 기존 교재 기준 기술58–92·관행58–87을 보존하되, 실제 CAT simple 지원58–89는 별도로 검사한다. 교재 범위 안이라도 CAT에 키존이 없으면 실패하며 음을 이동하지 않는다.

소금의 유사한 장식 문법은 [소금](https://www.gugak.go.kr/ency/topic/view/109), 산조의 유파별 퇴성 차이는 [강백천류 대금산조](https://www.gugak.go.kr/ency/topic/view/1045)를 참고했다. 소금 실음에 무조건 +12를 하거나 산조 관종별 음역을 하나로 고정하지 않는다.

### 정악대금 기호표

[국립국악원 대금정악보 2021 수정본](https://archive.gugak.go.kr/portal/detail/searchEtcDetail?clipid=T014947&recording_type_code=T&system_id=AI)의 PDF10–14쪽을 직접 렌더링해 표·음열을 대조했다. 확보 원본 SHA-256은 `e91c7811e1bbc7d26f3f3c83d08fc33e1473eff6bbd142b03de33390b9657117`이다. `expressions.py`의 정악대금 전용 표를 선택하며, 단소 이외를 모두 소금표로 처리하지 않는다.

| 기호 | 정악대금 처리 | 제약 |
| --- | --- | --- |
| 네로나 | 앞 기준음 대비 `[-1,-2,-3]` | 단소의 마지막0과 다름 |
| 니느라니 | `[1,0,-1,0]` | 단소의 `[1,0,1,0]`과 다름 |
| 느니-르·니루-니 | 본음 앞뒤에 각각 아래·위 이웃음 | 긴 가운데는 기보 본음, 짧은 앞뒤 길이는 엔진 선택 |
| 나니르노니르 | 앞꾸밈 `[-1,1,0,-1,1]` 뒤 본음0 | 나니로+느로니르 합성. 동등한 꾸밈 길이는 재생 선택 |
| 떠이어표 | 임→무→임 | 실용교재 p163의 임 계열 예시만 지원. 다른 본음은 거부 |
| 느네느·느나르나니 | 오류 | 공식 표의 설명 문장과 예시 음열이 달라 하나로 확정하지 않음 |
| 느너느·나너나·나노나·너나·노나·노니노·느라 | 미지원 | 소금 전용으로 확인한 이름을 정악대금에 자동 복제하지 않음 |

나머지 표에서 확인한 기본 상대음·앞꾸밈·붙임음은 명시된 조의 음계 단계로 해석한다. 음계의 인접음과 고정 반음을 혼동하지 않는다. 표현 수치와 앞꾸밈 최대1/16 quarter·자리 절반 이내, 숨+감쇠 절반 이내 등의 시간 배분은 재생 선택값이다. 농음과 풀어내림은 다른 표기이며, 풀어내림에 요성·하행 곡선을 모두 명시해야 한다. 진폭·주기·곡선·빠르기는 공식 기호표에서 임의 추출하지 않는다.

`jeongak-daegeum-expression-profile/v1`과 `jeongak-daegeum-performance/v1`을 사용한다. 다른 악기의 schema, 원본 해시 불일치, 불명확한 대금 기종은 거부한다. 일반 `daegeum`도 판본을 확인한 `playback.instrument: "jeongak-daegeum"`과 `instrument_basis`가 있으면 선택할 수 있지만, 원본이 산조대금·단소·소금이면 정악대금으로 덮어쓰지 않는다.

## 실행·이전 검증

[공통 CLI 안내](../../../data/src/playback/README.md)의 의존성과 FFmpeg·ffprobe를 준비하고, Git 밖 assets JSON에 정악 CAT ZIP을 연결한다.

```powershell
.\data\.venv\Scripts\python -m data.src.playback --score data/src/playback/examples/jeongak-daegeum-jgb.json --assets C:/local-gugak/assets.json --instrument jeongak-daegeum --out data/processed/playback/jeongak-jgb
.\data\.venv\Scripts\python -m unittest discover -s data/src/playback/tests -q
```

예제는 기존 곡을 옮긴 악보가 아니라 직접 작성한 4정간이다. 60정간/분·농음3Hz/40cent·흘림−75cent는 입력 경로를 검사하기 위한 작성값이며 정악의 연주 표준이 아니다.

- 전체 **67개 검사 통과**(실제 AAC 통합2개 포함). 기종 충돌·미확정 속도·농음 정책 누락·상대음 음계 충돌·MIDI 시간·오류 시 결과 보존과 AAC 실제 인코딩을 포함한다.
- 실제 정악 CAT: **3음, 악보4초 + 출력 여유0.55초, 200,655프레임, M4A55,372bytes**, 생성0.777초. AAC-LC·44.1kHz·mono, 목표192kbps, 이 짧은 쉼 포함 예제의 실제 스트림94,074bps.
- 원본 사본 SHA·MIDI 음높이63/65/68·표시 길이4.55초·디코딩 끝 패딩0·peak0.850024 확인. FFmpeg/ffprobe9.0.2 사용.
- 단소 기존001 WAV를 재생성한 결과 기존 SHA256 `962d41469567733fdbd6ed6de150f62986050a5491eff2076a087f4fc0365662`와 동일. 공통 확장으로 기존 단소 소리가 달라지지 않았다.
- 사용자 요청에 따라 목록 처리·입력 교정용 배치와 HTML 생성을 제외하고 단일 변환 CLI만 남겼다. 새 출력 `data/processed/playback/166-listen/jeongak-m4a-cli-only/`에 HTML·중간 WAV가 없고, M4A·MIDI·원본 사본의 SHA256이 이전 생성 결과와 모두 같음을 확인했다. M4A SHA256은 `734dc0c0f00b5891af1f13bcb5db23e1e7c259cd43706235628ddeed1ac823fe`다.

실제 원본 목록에서는 정악대금이 명시된 판본을 확보하지 못했다. 따라서 정악 실제 곡의 자동 변환이나 전문가 청감 검증을 완료했다고 보고하지 않는다. 물리적 취구·청 울림 변화, 전음역 조율, 미지원 시김새·관행별 편곡은 남은 검토 범위다. 원본 ZIP·생성 음원·로컬 검증 JSON은 Git에 포함하지 않는다.

## 최신 공통 엔진 통합 검증

2026-10-29, Python3.12·FFmpeg/ffprobe9.0.2에서 **135개 테스트 통과·건너뜀0개**를 확인했다. 이전67개 검사는 위의 과거 기록이다. 테스트는 `test_jgb.py`, `test_expressions.py`, `test_score.py`, `test_midi.py`, `test_sampler.py`, `test_tempo_repeat.py`, `test_pipeline.py`에 대응 구현별로 모았다. 별도 `policy.py`·악기별 테스트 파일·테스트끼리 import는 제거했다.

동일 작성 입력을 실제 CAT으로 다시 생성한 결과는 **3음·4.55초·55,372bytes**이며, M4A와 MIDI 해시는 이전과 같다. M4A SHA-256은 `734dc0c0f00b5891af1f13bcb5db23e1e7c259cd43706235628ddeed1ac823fe`, MIDI는 `62571d8ea02f47b4b8580d30a782421bf1a1e6bb73939386c49c326756ec1883`이다. 입력 사본·음표 ID·설정과 정책을 보존하고 HTML·중간 WAV 없이7파일을 생성한다. 결과는 메인 체크아웃의 `data/processed/playback/166-listen/jeongak106-integrated-20261029/`에 보존한다.

긴 음은 단소·소금의 검증된 안정 구간 탐색을 사용하되, 원본 샘플에서 조건을 통과할 때만 연장한다. CAT 정악32존 중 MIDI86은 안정 구간을 찾지 못하므로 샘플 길이를 넘는 지속을 거부한다. 안정 구간 연장은 청의 울림이나 호흡을 새로 녹음한 소리가 아니다. 단소용 연결 시작점100ms도 정악대금에 복제하지 않는다.

실제 CAT MIDI58–89를 각각15초로 렌더링하여 **31개 성공·MIDI86 예상 실패**를 확인했다. MIDI86의2초 음은 정상이며, MIDI90은 기술 음역 안이어도 샘플 키존 부재로 실패한다. MIDI63의15초와7.5초+7.5초 이음은 최대 PCM 차이1.74e-18 이하이며, 숨 구간5,248프레임은 모두0이었다. 기존 단소46·소금13 준비 입력은 병합된 MR44 공통 엔진과 score·source-map·MIDI가 같았다. 단소 강원도아리랑과 소금 개구리소리 M4A도 각각 재생성해 기존 SHA와 일치했다. 실행30.54초, 결과·버전·소스 해시는 메인 체크아웃의 `data/processed/playback/166-listen/jeongak106-validation-20261029/verification.json`에 보존한다. 전문가 청감·실연 음색·전음역 조율 검증은 포함하지 않는다.
