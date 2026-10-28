# JGB 연계를 위한 단소 MIDI·CAT 재생 기반과 표현 근거

작업: [S15P31E102-105](https://ssafy.atlassian.net/browse/S15P31E102-105) 재생 기반, [S15P31E102-166](https://ssafy.atlassian.net/browse/S15P31E102-166) JGB 입력 연결. Story30 모범 연주 재생. 상태: 로컬 재생 기반·조절 가능한 표현 초기값. 국악의 연주 표준이나 채점 정책을 확정한 문서가 아니다.

후속 사용자 요청으로 JGB 숨표를 가변 길이로 바꾸고 빈지속·농음·흘림 등을 지원했다. 2026-10-28에는 기본 저장 형식을 AAC-LC M4A로 확장하고 WAV는 선택 출력으로 남겼다. 이 문서의105·초기166 수치와 WAV 검증은 당시 기록이다. 현행 정책과 단소41항목의 빠르기·원보 교정·M4A 출력 검증은 [후속 변환 기록](danso-expanded-auditions.md)을 따른다.

## 범위와 입력 경계

2026-10-27 사용자 확인: **정석 음원 제작은 박찬형, 채점·AI 평가는 사지한 담당**이다. 이 문서는 정석 음원을 만들기 위한 악보 해석·표현·재생 근거를 다루며, 채점 구현을105의 후속 완료 조건에 넣지 않는다.

105에서 공통 악보 JSON → MIDI → CAT 단소 WAV 경로를 만들었고, 166에서 JGB v1·v2의 단소 한 성부를 직접 읽는 연결부를 추가했다. 사진 인식·웹서비스·다성부 합주·녹음 채점은 이 도구의 범위 밖이다.

JGB 연결부는 악보 버전·입력 해시·원본 성부 ID·각·정간·분박을 `source-map.json`에 기록하고 정간의 시가를4분음표 단위로 변환한다. 코어는 장식 확장·붙임줄 병합 후 JSON의 `parent_ids`를 보존하지만 MIDI 파일 자체에 원본 ID를 넣지는 않는다. 같은 음의 반복 발음·붙임·쉼을 구분한다.

실행·입력 규격·검사: [playback README](../../../data/src/playback/README.md). develop의 Data JGB 검토 도구와 기존 채점 코드는 변경하지 않는다.

## 단소의 실음·표현 근거

기존 조사에서 국립국악원 『창작을 위한 국악기 이해와 활용2』 단소 편의 음역도(p26), 위음 꾸밈(p33~34), 숨·요성·혀치기(p35), 호흡/음높이 변화 요성(p38)을 대조했다. [단소 PDF](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?dirname=/board/138&filename=kor_danso.pdf&type=F&sistype=D&boardseq=41189&boardid=10928), [단소 데이터 조사](../data/danso.md).

프로필은 실음 MIDI66~92를 검사하고, 실음 입력에 옥타브를 다시 더하지 않는다. 율명 입력에는 황종 MIDI와 register를 요구한다. 장식의 이웃음은 명시해야 하며 악보·기종에 맞는 실제 운지/호흡 가능성은 별도 검수다. A440은 비교용 조율 기준이며 CAT 샘플 전체가 정확한 평균율임을 뜻하지 않는다.

CAT 단소 Live Sampler v1.3의 simple 프리셋은16개 키존과 MIDI66~92 범위를 사용한다. 코어는 원본 샘플을 mono로 혼합하고 선형 보간·velocity/CC11·Pitch Bend·attack/release로 재생한다. Ableton의 전체 필터·FX·물리적 호흡·음색 변화까지 재현하지 않는다. 샘플 소진은 오류이며 임의 루프나 무음으로 채우지 않는다. CAT ZIP/프리셋/샘플 해시는 악기별 JSON에 남긴다.

[CAT 공식 라이브러리](https://www.catseoul.com/gugak-library/)에서 획득한 샘플을 로컬에 두고 사용 표기를 출력에 포함한다. 원본 ZIP/WAV는 Git에 넣지 않는다.

## 2026-10-27 단소 표현의 재생 초기값

Task105 마무리 범위는 악보 표현을 수치로 해석해 소리에 적용한 실행 경로와 재현 근거다. 기존 코드·예시를 대조해 재생 초기값을 정리했다. 2026-10-27 사용자 요청에 따라 **추가 떨림은 ±25cent로 고정**하고 선택 옵션을 없앴다. 악보에 명시된 요성은 별도 해석하며 pitch/mixed에는 추가 떨림을 중복 적용하지 않는다. 이 재생 설정을 팀의 채점 정책이나 공인된 연주 규칙으로 취급하지 않는다.

| 표현 | 기존 값·동작 | 초기 적용안·근거 | 남은 검수 |
| --- | --- | --- | --- |
| 앞꾸밈음 | `grace-before`: 이웃음 → 본음, 기본 `1/16 quarter`를 본음에서 차감 | 기존값 유지. 기존 예시와 호환되고 템포를 따라감 | 기호별 이웃음 높이와 빠른 곡에서의 최소 길이 |
| 위음 후 복귀 | `upper-return`: 본음 → 위음 → 본음, 앞 두 단계 각각 `1/16 quarter` | 기존값 유지. 전체 악보 시간 안에서 배분 | 본음이 남지 않는 짧은 음은 현재처럼 실패시키고 원보 해석 검수 |
| 명시된 음량 요성 | 단소 기본 `amplitude`, 4Hz·depth0.08·지연250ms·시작/끝 fade150ms | 명시된 요성에 적용. 추가 ±25cent 피치 떨림과 함께 재생 | 장르·선법별 방식·깊이·시작 위치. 호흡 표현 전체를 음량만으로 재현하지 못함 |
| 음높이·혼합 요성 | `pitch`/`mixed`: ±25cent·4Hz·지연250ms·fade150ms; mixed는 음량 변화도 적용 | 원보 해석상 필요한 경우만 명시적으로 선택 | 추성·퇴성의 방향성 있는 곡선은 현재 사인파 요성과 별개로 미구현 |
| 추가 떨림 | **±25cent 고정**, 4.5Hz, 650ms 이상 음, 지연220ms, fade-in/out180/160ms | 사용자 선택에 따라 기존25cent 값을 고정 적용. CLI 선택 옵션 제거 | CAT 샘플 자체 흔들림과 결합한 실제 청취 품질 |
| 숨표 | 미지정0초. 지정하면 `breath_seconds + 50ms`만큼 note-off를 앞당김 | 명시된 숨표의 예시값 **120ms 공백**, 숨표 없는 곳0. 기존 세령산 입력 재사용 | 음 길이≤170ms에서는 적용 불가. 속도·종지별 자연스러움 |
| 이음·붙임 | 연결 attack/release20ms, 일반 release50ms, 단소 연결 샘플 시작점100ms. 동일음 연결은 샘플 위치를 이어가고 붙임줄은 한 이벤트로 합침 | 기존값 유지. 이전 단소 연결 실험을 재현 | 자연스러움·샘플 길이 한계. 다른 악기에100ms를 일반화하지 않음 |
| 강약 | 기본 본음 velocity90·앞꾸밈80, CC11 `[127,127,127]`, 제어점0%·45%·100% | 기본값 유지, 악보별 명시값 보존. 표현 비교의 변수를 줄임 | 악센트·점점 세게/여리게 기호의 수치화. upper-return 각 음은 본음 velocity 사용 |

구현 근거: [score.py](../../../data/src/playback/score.py), [midi.py](../../../data/src/playback/midi.py), [sampler.py](../../../data/src/playback/sampler.py), [단소 프로필](../../../data/src/playback/profiles/danso.json). 숨120ms는 엔진의 자동 기본값이 아니라 [세령산 입력](../../../data/src/playback/examples/seryeongsan-first-jangdan.json)의 명시값이다.

### 단위와 적용 순서

- `1/16 quarter`는 4분음표의1/16이다. quarter BPM60에서62.5ms, 아래 예시 BPM67.5에서약55.6ms다. 일반적인16분음표는 `1/4 quarter`다. upper-return의 앞 두 단계 합은 이 값의 두 배다.
- 추가 떨림 **25cent는1/4반음**이며 길이 단위 quarter와 무관하다. 4Hz/4.5Hz는1초당 떨림 횟수다.
- amplitude depth0.08은 ±8%가 아니다. fade가 끝난 구간의 CC11 배율은 **0.92~1.00**이고 기본127은 반올림 후 약117~127이다. 최종 WAV 진폭은 샘플·velocity·출력 정규화에도 영향을 받는다.
- 명시된 pitch/mixed 요성에는 추가 떨림이 겹치지 않는다. amplitude 요성에는 추가 피치 떨림이 함께 적용된다. 원본 CAT 샘플에 이미 녹음된 흔들림은 제거하지 않는다.
- 숨120ms를 위해 note-off를170ms 앞당기고 release50ms 뒤에 공백을 남긴다. 악보 시간은 늘리지 않는다. 연결20ms는 무음이 아니라 겹치는 fade이며 샘플 시작점100ms는 숨 길이가 아니다.
- 선택 배율은 곡별 quarter BPM에 적용한다. 초기에는 꾸밈은 박 비율, 숨·떨림 주기·지연·fade는 고정 초로 유지한다. 음 길이가650ms보다 짧아지면 추가 떨림이 꺼지므로 속도별 결과를 확인한다. 세령산 BPM67.5를 다른 곡의 기본값으로 쓰지 않는다.
- 추가 떨림 설정은 `midi.py`의 `PLAYBACK_VIBRATO`에 두고 manifest/악기별 JSON에도 기록한다. CLI의 `--vibrato` 선택과 별도 expression 모듈은 제거했다. 요성 fade150ms와 sampler의20/50/100ms는 기존 코드 상수다.
- JGB 연결부는 앞꾸밈 각각1/16 quarter·velocity80·연결 발음, 숨표120ms를 사용한다. 별도 로컬 변환 실험의 꾸밈 최대90ms/슬롯30%·숨 최대80ms·꾸밈 velocity90과는 다르다. JGB의 나니나·나느나는 기호 사전의 `seq`처럼 본음 자리를 균등 분할하며, 짧게 위음을 스치는 공통 IR의 `upper-return`과 구분한다.

### 음악적 근거와 적용 한계

2026-10-27 확인한 [국립국악원 「요성」](https://www.gugak.go.kr/ency/topic/view/2105)은 장르·선법에 따라 폭과 시작 위치가 달라질 수 있음을 설명한다. [단소 호흡법](https://academy.gugak.go.kr/dp/pds/HTML5-1-2-2/HTML5-1-2-2-7/index.htm)은 호흡이 음색·음정에도 영향을 준다고 설명한다. [정간보의 부호와 실제연주법](https://academy.gugak.go.kr/dp/pds/HTML5-2-2-2/HTML5-2-2-2-3/index.htm)은 기호별 음 진행·길이 배분과 쉼표/숨표를 구분한다.

이 자료들은 기호별 해석과 조절 가능성의 근거이며, **4Hz·±25cent·120ms를 표준 수치로 제시하지 않는다.** 수치는 기존 구현·예시를 재현하는 공학적 초기값이다. 기존에 인용한 단소 PDF는 이번 웹 조회에서 접근되지 않아 페이지별 설명을 새로 검증한 결과로 취급하지 않았다. 국악적 자연스러움과 연주 정답 여부는 전문가 검수 대상으로 남긴다.

### 음원과 재현

세령산 첫 장단 예제는 원본37음, 위음 후 복귀3곳·앞꾸밈1곳·숨120ms1곳이며 MIDI44음으로 펼쳐진다. 명시 요성은0곳이다. 기존 ±25cent 음원은44,100Hz·mono·PCM16·612,255프레임·13.883333초이며 SHA-256은 `628020cb8d7b99b9fc5c688cf13d04ed6c778d593ed9f3e554b08001bb2e70c9`다.

2026-10-27 Python 3.10.21·NumPy 2.2.6·SoundFile 0.14.0에서 격리 검사22개가 통과했다. Data 경로에서 실제 CAT 음원을 다시 생성해 입력37음의 ID 보존과 이동 전후 WAV 해시 일치를 확인했다.

실행 명령과 CAT 경로 JSON 예시는 [playback README](../../../data/src/playback/README.md)를 따른다. 생성 manifest와 악기별 JSON에 소스·입력·프리셋·샘플 해시를 보존한다. 이전 none/third 비교와 사진 서비스 실험은 기존 커밋 이력에 보존하고 현행 기능으로 안내하지 않는다.

원본 악보·CAT ZIP·생성 WAV는 Git에 넣지 않는다. 음원 출처는 서울예술과학센터 CAT 국악 가상악기 라이브러리다. 생성 성공을 전문가의 원보·연주 품질 검수로 확대하지 않는다.

## JGB 입력 연결과 검증 (166)

문법 근거는 우물사이 `9d4a24e54bf36093d4d264d0271d214500d36db7`의 [app.js](https://github.com/danbinaerinHan/jeongganbo-generator/blob/9d4a24e54bf36093d4d264d0271d214500d36db7/js/app.js) `parseMelodyOffsets`·`groupRowTokens`·`realizeMelody`·`staffScoreOf`, [기호 사전](https://github.com/danbinaerinHan/jeongganbo-generator/blob/9d4a24e54bf36093d4d264d0271d214500d36db7/js/symbols-registry.js), [음가 환산](https://github.com/danbinaerinHan/jeongganbo-generator/blob/9d4a24e54bf36093d4d264d0271d214500d36db7/js/staff-core.js)이다. 로컬 소스와 실제 함수를 확인했다. [jgb.py](../../../data/src/playback/jgb.py)는 그 문법의 단소 재생용 부분을 Python으로 구현하며 MIT 고지를 포함한다. 실행에 우물사이 clone·Node·브라우저는 필요하지 않다.

- `tempoBpm`은 정간/분이고 `staffUnit` 비율을 길이·BPM 양쪽에 곱한다. 누락/auto는 기본각12정간이면1/2, 나머지는3/2 quarter다. `tempoBpmGak`은 표기용이다.
- 같은 각의 빈칸·`-`는 앞 음을 연장하고 각 첫 연장은 쉼이다. 쉼 뒤의 연장도 쉼으로 유지한다. 상대음은 명시한 조와 직전 본음을 사용하며 원본 앱의 음계 자동 추정·미지원 기호 무음 처리는 사용하지 않는다.
- 지원 시김새와 표현값은 [실행 안내](../../../data/src/playback/README.md#jgb-파일에서-생성)에 명시한다. 농음·흘림·길이 변경·반복 기호의 자동 해석은 남아 있다. 추가 떨림25cent를 이 기호의 해석으로 간주하지 않는다.
- 원본 바이트·해시를 함께 보존하고 `events[].parent_ids → source-map.json의 notes`로 원본 위치를 찾는다. 대응표의 시간은 원본 자리 기준이며 실제 전개음 시간은 events에 있다. 원본과 출력 경로가 겹치거나 일반 IR 출력에 이전 JGB 대응표가 남는 경우는 거부한다.

2026-10-27 Python3.10.21·NumPy2.2.6·SoundFile0.14.0에서 **기존22개+JGB19개=41개 검사 통과**. 우물사이와 비교한7개 작은 사례에서 음높이·길이 불일치0건(분박·상대음·반복음·쉼/각 경계 연장)이다. 앞꾸밈·숨은 위에 명시한 프로젝트 정책을 따르므로 원본 웹 재생과 완전 동일하다고 주장하지 않는다.

실제 입력은 로컬 변환 자료의 단소 표준악보집 새야새야(PDF161), 도라지타령(PDF171) JGB다. 두 문서의 `tempoBpm=40`, `hwangPitch=63`, `staffUnit=dotted`를 사용했다. WAV는44.1kHz·mono·PCM16이며 아래 길이에는 MIDI 종료 여유0.55초가 포함된다.

| 입력·설정 | 정간 / MIDI음 | WAV 길이 | WAV SHA-256 |
| --- | --- | --- | --- |
| 새야새야 기본 속도 | 40 / 32 | 60.55초 | `f12a4050c497319b6ef366df448096736f13292c7f585e6e45e078b004faaf63` |
| 새야새야1.25배속 | 40 / 32 | 48.55초 | `4d2b0183302d70c21623a7d874d5ecc659dfc5f8149257e1e264e58fc28e8b6d` |
| 도라지타령 기본 속도·숨표120ms | 84 / 91 | 126.55초 | `97915f8ff4dac7350a5d6ae2b1a80cd7640c638f7812434096624f8df2043b7c` |

새야새야 기본 속도는 이전 로컬 변환 WAV와 해시가 같았다. 원본 JGB 불변·입력 사본 바이트 일치·모든 전개음의 원본 대응을 확인했다. 결과는 Git 제외 경로 `data/processed/playback/166-saeya/`, `166-saeya-speed125/`, `166-doraji/`에 있고 각 `verification.json`에 실행 명령·입력 해시·소스 해시·음원 속성을 기록했다. 이 검증은 코드와 변환의 재현성에 대한 것이며 곡의 기본 빠르기·판본·정석 연주 품질 승인은 별도 검수다.
