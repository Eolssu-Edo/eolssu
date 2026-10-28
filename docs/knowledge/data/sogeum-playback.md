# 소금 JGB·MIDI·M4A 생성

작업: [S15P31E102-108](https://ssafy.atlassian.net/browse/S15P31E102-108), Story30 모범 연주 재생, Epic18. 2026-10-28 기준. 단소 !87 병합 커밋 `64ce7b8`의 공통 변환·AAC 출력에 소금 프로필과 표현 규칙을 통합한다. [MR !44](https://lab.ssafy.com/s15-final/S15P31E102/-/merge_requests/44)의 범위는 단일 JGB/score → MIDI → CAT PCM → M4A/WAV와 필요한 검사·문서다.

## 해석 기준

[국립국악원 「소금」](https://www.gugak.go.kr/ency/topic/view/109)은 정간보 장식기호·요성·혀치기가 정악대금과 거의 같다고 설명한다. 같은 기호 문법을 사용할 수 있지만, 이것이 모든 곡에 같은 요성의 깊이·속도·시작 위치를 적용한다는 뜻은 아니다. [「요성」](https://www.gugak.go.kr/ency/topic/view/2105)은 장르·선법·연주자에 따른 차이를 설명하며 고정 Hz·cent 값을 정하지 않는다.

- 실음 입력은 그대로 재생한다. 대금 음역으로 기보했다고 명시된 IR에만 `pitch_mode=written`, `notation_octave_shift=1`을 사용한다. JGB 율명에 자동으로 한 옥타브를 더하지 않는다.
- 기존 교재 도판 검토를 따른 기술 음역은 MIDI70–96(B♭4–C7), 정악 관행은 MIDI70–92(B♭4–A♭6)다. 관행 밖에는 경고하고 기술 음역 밖은 실패한다. CAT simple 프리셋의 MIDI70–99 지원 범위와 실연 범위를 혼동하지 않는다.
- IR에서 요성을 명시했을 때의 `mixed`는 기존 재생용 근사 기본값이다. JGB 농음·흘림에는 `playback.expression_policy`의 선택 근거와 수치를 별도로 요구한다. 단소의 음량 요성·흘림 수치를 자동으로 복제하지 않는다.
- 단소에 적용했던 자동 ±25cent 떨림은 소금에 추가하지 않는다. 원본 CAT 샘플에 녹음된 변화는 남는다.
- 장식의 상대음은 명시된 조 또는 근거를 적은 음계를 따른다. 관찰한 일부 음만으로 자동 추정하지 않는다. 미지원 기호·음역 이탈·샘플 소진은 생략하거나 무음으로 채우지 않는다.
- 숨표의 가변 길이는 공통 재생 알고리즘이다. 소금의 공인 호흡 시간이 아니며 음 길이 안에서 숨과 감쇠를 배분한 근사다.

국립국악원 『창작을 위한 국악기 이해와 활용2』 [소금 공식 PDF](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?boardid=10929&boardseq=41191&dirname=%2Fboard%2F138&filename=kor_sogeum.pdf&sistype=D&type=F)를 다시 확보해 p61 운지표·오선보 기보 관행, p65–69 장식표, p70 추성/퇴성, p72–75 농음, p78 정악 상한을 렌더링해 확인했다. 분권43쪽·7,180,308bytes, SHA-256 `ee45c86ba7f11ba509e50e1ea5bcfd500b7c026762fd381687e4db5094e7e80c`다. 쪽수는 인쇄 쪽수다. 기존 [소금 원음·라벨 조사](sogeum.md)의 추정 F0를 평균율 조율의 정답으로 바꾸지 않는다.

## 소금 기호 대조와 적용

음계 단계0은 기준음이며 ±1은 선택된 음계의 이웃음이다. 단소와 다른 표를 `expressions.py`에서 선택하고 `source-map.symbol_policy`에 기록한다. 기호 뜻과 재생 수치·시간의 선택을 구분한다.

| 기호·입력 | 소금 처리 | 근거·제약 |
| --- | --- | --- |
| 네로나 / `nerona` | 앞 기준음 대비 `[-1,-2,-3]` | p68 계단형. 단소의 `[-1,-2,0]`과 다름 |
| 니르나니 / 니느라니 / `nineurani` | `[1,0,-1,0]` | p68 누운 S형. 단소의 `[1,0,1,0]`과 다름 |
| 느니르나니 / 느나르나니 / `neunareunani` | `[1,2,1,0,2]` | p68의 예시 선택. 끝음+1 대안도 있어 유일한 해석으로 보지 않음 |
| 나니르노니르 / `nanireunonireu` | 짧은 앞꾸밈 `[-1,1,0,-1,1]` 뒤 긴 본음0 | p68 도판·ㄹ형 SVG 대조. 짧은5음의 동등 길이는 재생 선택 |
| 떠이어표 / `roll` | 임→무→임 | p66 예시. 임 계열에 앞꾸밈 `[본음,본음+3반음]`; 다른 본음은 오류 |
| 느너느 / 나너나 / 나노나 / 너나 / 노나 | 각각 앞꾸밈 `[0,-2]`, 붙임 `[0,2,0]`, `[0,-2,0]`, 독립 `[2,0]`, `[-2,0]` | p65–68. 한글 로컬 입력 확장, 상류 영문 ID를 새로 만들지 않음 |
| 노니노 / 느라 / 나니르 | 기존 `norino` / `nora` / `naniro`와 같은 음열 | 교재 이름·도형 대응 확인 |
| 너녜·너네 / `neonye` | 앞꾸밈 `[-2]` | 이름과 상류 설명 기준. 상류 SVG는 교재 ㅋ형과 달라 글리프 일치 검증으로 보고하지 않음 |
| 느네느 / `neuneneu` | 오류 | 공식 느너느와 그림이 달라 +3음 해석을 복제하지 않음 |
| 느니르·느니-르 / `neunireu` | 오류 | p66 설명과 실제 예시에서 긴 본음의 기준이 달라 판본 확인 필요 |

니레·니라·니로·노네·노니로·네로네·느로니르·로니로·나니나·나느나·노/니/로/리·니나·느나·느니·니로나·니레나·느나니나와 니루-니는 기존 음계 단계와 소금표가 대응한다. 느니라·노라느니의 직접 대응은 미확정이다. 격음 C형·강세 V형·혀치기 ㅎ형은 서로 다르며 격음·혀치기를 기본 특강표로 바꾸지 않는다. 상류 `tongue.svg`도 교재 혀치기와 대응한다고 확정하지 않는다.

농음은 장음·보통·내리떠는·잘게떠는 문맥에 따라 시작·폭·음량이 다르며, 추성/퇴성 설명도 고정 cent·Hz·초를 정하지 않는다. 따라서 소금의 농음·흘림·미는·풀어내림 수치는 별도 선택한다. 뒤 세 이름·글리프를 이 교재의 특정 농음 유형과 일대일 대응시켰다는 뜻은 아니다. 아래는 기능 설명용 입력이지 연주 표준값이 아니다.

```json
{
  "playback": {
    "expression_policy": {
      "name": "연습용 소금 표현 선택",
      "basis": "작성자가 재생을 위해 선택한 값; 전문가 검수 전",
      "status": "audition-policy-not-performance-standard",
      "vibrato": {"mode": "mixed", "hz": 3, "cents": 40, "depth": 0.1, "delay_seconds": 0.1},
      "fall": {"end_cents": -75, "tail_fraction": 0.4, "maximum_seconds": 0.2},
      "unwind": {
        "yoseong": {"mode": "pitch", "hz": 3, "cents": 40, "delay_seconds": 0},
        "pitch_curve": {"end_cents": -100, "tail_fraction": 0.5, "maximum_seconds": 0.4}
      }
    }
  }
}
```

필요한 효과만 적되, 사용된 기호의 정책이 없으면 중단한다. `push`는 양의 곡선을 사용한다. 정책 지연에 지속 중 기호의 위치 지연을 더하며, 실제 음 끝 이후 시작하는 요성은 거부한다. `--expression-profile`의 요성표/겹요성표·추성/퇴성·겹흘림/겹미는 수치도 소금 스키마와 근거를 요구한다. 특강표110·CC11 강약, 꾸밈 최대1/16 quarter·총 길이 절반 이내, 숨+감쇠 절반 이내, 끊기 반길이, 늘임2배는 공통 재생 선택값이다. p69의 늘임2–3배 설명 중2배를 택했으며 숨·끊기의 고정 수치는 교재가 정하지 않는다.

## 실행

공통 의존성·FFmpeg 준비는 [playback 실행 안내](../../../data/src/playback/README.md)를 따른다. `assets.json`에는 `sogeum` 키로 로컬 CAT 소금 ZIP 경로를 지정한다. 기본 출력은 AAC-LC M4A이며 `--audio-format wav`로 PCM16 WAV를 선택할 수 있다.

```powershell
python -m data.src.playback --score data/src/playback/examples/sogeum-jgb.json --instrument sogeum --assets assets.json --out data/processed/playback/sogeum-example
python -m data.src.playback --score data/processed/playback/166-listen/notion-audio-sogeum-m4a/prepared/001.jgb.json --instrument sogeum --assets assets.json --out data/processed/playback/sogeum-single
python -m unittest discover -s data/src/playback/tests -p 'test_*.py'
```

고정 예제는 청황·청태·쉼·청중 4정간을 직접 작성한 검사 자료다. 예제의 60정간/분은 테스트 입력이며 실제 소금 곡의 기본 빠르기나 다른 악기용 연습값 승인을 뜻하지 않는다. `tempoBpmUserSet: true`가 없으면 명시적 `--bpm`이 필요하다. 장단 이름만으로 BPM을 추정하지 않는다. 원본의 악기 선언과 충돌하면 실패한다. JGB에 악기가 없거나 `all`이면 입력을 준비할 때 판본을 확인해 `playback.instrument: "sogeum"`과 `playback.instrument_basis`를 기록한다. 제공한 근거와 원본 바이트를 보존한다.

연주 계획·표현 프로필의 schema는 각각 `sogeum-performance/v1`, `sogeum-expression-profile/v1`이다. 나머지 구조·원본 해시 결합·반복 범위 검사는 공통 안내와 같다. 관련 모듈은 `expressions.py`, `tempo_repeat.py`이며 별도 소금 엔진은 만들지 않는다.

두 번째 명령은 로컬에 이미 준비한 「개구리소리」 JSON을 사용하는 예다. 해당 파일·CAT ZIP은 저장소에 포함되지 않으므로 실제 준비 경로를 지정한다. 현재 기능은 입력 하나의 음원·MIDI·변환/추적 JSON 생성까지다. 목록 처리, 빠르기 조사·보충, 원보 교정과 HTML 생성은 포함하지 않는다.

## 기존 실곡 검증 기록

목록 처리 기능을 제외하기 전에 Notion DATA 소금 목록 22파일 중 빠르기와 변환 근거가 있던 13파일을 검증했다. 원본 JGB·연결 PDF의 SHA-256을 당시 기록과 대조했고, 빠르기 미확정 8파일과 상대음계가 미확정인 「오나라」 1파일은 보류했다. 단소에서 승인된 연습용 60정간/분을 소금 전체에 자동 확대하지 않았다. 이 결과는 과거 검증 기록이며 현재 CLI의 목록 처리 기능을 뜻하지 않는다.

「개구리 소리」는 표준악보집 PDF188(인쇄374–375쪽)의 소금 편이다. 이번에 해당 PDF를 직접 렌더링하고 본문을 추출해 소금 명시·굿거리장단·사용 율명 임·남·청황·청태·청고(㴌)를 확인했다. 사용자 빠르기 표의 굿거리 점4분음표66회/분과 한 번에3정간인 표기를 연결해 198정간/분으로 준비한다. 이는 사용자가 선택한 빠르기이며 해당 PDF가 숫자66을 제시한 것은 아니다. 다른12파일의 판본 연결과 빠르기는 기존 검토 기록을 재사용하며 새 전문가 전수 검수로 보고하지 않는다.

기존 로컬 입력은 `data/processed/playback/166-listen/notion-export/`, 이전 목록은 같은 상위 폴더의 `sogeum-m4a-catalog.json`, 보류 사유는 `sogeum-m4a-deferred.json`에 보존했다. 목록 파일은 현재 단일 CLI의 필수 입력이 아니다. 원본 PDF·CAT ZIP·생성 음원은 Git에 포함하지 않는다. 생성 성공은 실제 운지·편곡·연주 해석의 전문가 승인을 의미하지 않는다.

## 검증 결과

단소 병합 기반과 통합한 뒤 **126개 테스트 통과·건너뜀0개**를 확인했다. 기존67개 기록은 이전 버전이다. 테스트 이름은 `test_jgb.py`, `test_expressions.py` 등 대응 모듈에 맞춰 정리했다. [테스트별 범위](../../../data/src/playback/tests/README.md)에 음역·음열·빠르기·표현 지연·실제 AAC 인코딩과 실패 보존 검사를 구분한다.

Python3.12·FFmpeg9.0.2·CAT Live Sampler v1.3 `SoGeum - DEMO_simple.adv`로 준비 입력13개를 각각 단일 변환하여 **810음·371.675465초·6,936,570bytes**의 M4A를 다시 생성했다. 「개구리소리」는 **40음·18.731837초·358,259bytes**다. 각 입력에서 M4A·MIDI·악기 추적 JSON·score·manifest·원본 JGB 사본·source-map의7파일만 생성되었고 HTML·중간 WAV는0개다. 출력은44,100Hz·mono·AAC-LC(요청192kbps)다.

「개구리소리」 실음은 MIDI70–79, M4A SHA-256은 `107648fe8bef580593bf2bbe5996f52cebc7ba4887a51d57fa8f210634899f86`이다.13개에서 원본 바이트와 음표 ID 연결·음역·추가 PB=0을 확인했고 M4A13개 모두 이전 해시와 같다. 개별 변환의 실행 시간 합계는20.358초였다. 이번 결과는 메인 체크아웃의 `data/processed/playback/166-listen/mr44-sogeum-integrated-check/`와 `verification.json`에 보존한다. 이전 `sogeum-single-pipeline-final/`·`notion-audio-sogeum-m4a/` 결과도 그대로 남긴다.

별도 자체 작성 입력은 소금 전용 음열·5음 꾸밈·농음/상하행/풀어내림·별도 표현 프로필·빠르기 점진 변경·반복·숨·끊기·늘임·12초 지속을 함께 넣었다. **56음·45.425964초·806,761bytes** M4A 생성, 원본/프로필 바이트·해시 보존과 루프 적용을 확인했다. M4A SHA-256 `c6b19227d4fa4e8ffa674988f697dcadeac803e8cbb94a5050981cc86dda9918`이다. 이 입력의 수치는 기능 검사 선택값이다.

실제 CAT18개 존(지원70–99, 샘플4.620–8.413초)에서 안정 구간 탐색을 확인했다. 상관도0.996965–0.999854, 구간 음높이 변동0.917–3.992cent로 기존 단소와 같은 기준을 통과했다. 소금 구현 경로의 기술 음역70–96 전27개 음높이에서15초 지속, 같은음7.5초+7.5초 이음(최대 PCM 차이4.18e-12 이하), 숨표 구간 비영 샘플0을 확인했다. 짧은음의 기존 PCM은 사전 게이트 비교에서 동일했다. 단소용 연결 시작점100ms는 소금에 적용하지 않는다. CAT ZIP SHA-256은 `f79ab3b9fcc78d130780ba221b30c5691cb5d0f873f68026ed56dea9a8c44bd4`다. 안정 구간이 없는 샘플은 계속 실패하며, 이 연장은 호흡을 새로 연주한 녹음이 아닌 합성 근사다.

단소 기존 준비46개는 `score`·대응표·MIDI가 이전과 같고, 강원도아리랑의 WAV/M4A도 기존 해시와 같다. 출력 묶음 교체에서 실제 재현된 Windows 디렉터리 잠금 오류5/32/33만 최대5회(총 대기1.5초) 재시도한다. 다른 오류·지속 잠금은 실패하며 기존 결과 복구 절차를 유지한다.

공통 인코더는 컨테이너 길이·PCM 프레임 수·AAC 패딩·디코딩 peak<0.99를 검사한다. 빠르기 미확정8파일과 상대음계 미확정1파일은 계속 보류다. 브라우저 실제 재생, 전음역 조율 교정, 전문가 청감·원보 전수 대조는 이번 검증에 포함하지 않는다.
