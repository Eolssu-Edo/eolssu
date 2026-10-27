# JGB 연계를 위한 단소 MIDI·CAT 재생 기반과 표현 근거

작업: [S15P31E102-105](https://ssafy.atlassian.net/browse/S15P31E102-105), Story30 모범 연주 재생. 상태: 로컬 재생 기반·조절 가능한 표현 초기값. 국악의 연주 표준이나 채점 정책을 확정한 문서가 아니다.

## 범위와 입력 경계

2026-10-27 사용자 요청에 따라105는 **JGB → MIDI → CAT의 초석**으로 범위를 정리했다. 현재 구현은 `gugak-reviewed-score/v1` 악보 JSON → 실음·시간·표현 이벤트 → MIDI 저장 → 저장 MIDI 재해석 → CAT 단소 WAV다. JGB 직접 입력과 기호 해석 변환기는 후속 연결 범위다. 사진 인식·웹서비스·다른 악기 프로필·녹음 채점은 이 MR에서 제외했다. 제외한 실험 코드는 기존 커밋 `6c7f94a`와 그 이전 이력에 보존한다.

JGB 연결부는 악보 버전·성부·각·정간·음 위치를 입력 음표 ID와 연결하고, 주박/정간의 시가를4분음표 단위로 변환해야 한다. 코어는 장식 확장·붙임줄 병합 후 JSON의 `parent_ids`를 보존하지만 MIDI 파일 자체에 원본 ID를 넣지는 않는다. 같은 음의 반복 발음·붙임·쉼을 구분하고 다성부 합주는 지원하지 않는다.

실행·입력 규격·검사: [playback README](../../../ai/playback/README.md). develop의 Data JGB 검토 도구와 기존 채점 코드는 변경하지 않는다.

## 단소의 실음·표현 근거

기존 조사에서 국립국악원 『창작을 위한 국악기 이해와 활용2』 단소 편의 음역도(p26), 위음 꾸밈(p33~34), 숨·요성·혀치기(p35), 호흡/음높이 변화 요성(p38)을 대조했다. [단소 PDF](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?dirname=/board/138&filename=kor_danso.pdf&type=F&sistype=D&boardseq=41189&boardid=10928), [단소 데이터 조사](../data/danso.md).

프로필은 실음 MIDI66~92를 검사하고, 실음 입력에 옥타브를 다시 더하지 않는다. 율명 입력에는 황종 MIDI와 register를 요구한다. 장식의 이웃음은 명시해야 하며 악보·기종에 맞는 실제 운지/호흡 가능성은 별도 검수다. A440은 비교용 조율 기준이며 CAT 샘플 전체가 정확한 평균율임을 뜻하지 않는다.

CAT 단소 Live Sampler v1.3의 simple 프리셋은16개 키존과 MIDI66~92 범위를 사용한다. 코어는 원본 샘플을 mono로 혼합하고 선형 보간·velocity/CC11·Pitch Bend·attack/release로 재생한다. Ableton의 전체 필터·FX·물리적 호흡·음색 변화까지 재현하지 않는다. 샘플 소진은 오류이며 임의 루프나 무음으로 채우지 않는다. CAT ZIP/프리셋/샘플 해시는 악기별 JSON에 남긴다.

[CAT 공식 라이브러리](https://www.catseoul.com/gugak-library/)에서 획득한 샘플을 로컬에 두고 사용 표기를 출력에 포함한다. 원본 ZIP/WAV는 Git에 넣지 않는다.

## 2026-10-27 단소 표현의 재생 초기값

Task105 마무리 범위는 악보 표현을 수치로 해석해 소리에 적용한 실행 경로와 재현 근거다. 아래는 `71ed018` 코드의 기본값·예시값을 대조한 **조절 가능한 실험 초기값**이다. 팀의 채점 정책이나 공인된 연주 규칙으로 확정한 값이 아니다. 표현 수치의 런타임 값은 유지하며, 악보에 검수된 개별값이 있으면 그 값을 우선한다.

| 표현 | 기존 값·동작 | 초기 적용안·근거 | 남은 검수 |
| --- | --- | --- | --- |
| 앞꾸밈음 | `grace-before`: 이웃음 → 본음, 기본 `1/16 quarter`를 본음에서 차감 | 기존값 유지. 기존 예시와 호환되고 템포를 따라감 | 기호별 이웃음 높이와 빠른 곡에서의 최소 길이 |
| 위음 후 복귀 | `upper-return`: 본음 → 위음 → 본음, 앞 두 단계 각각 `1/16 quarter` | 기존값 유지. 전체 악보 시간 안에서 배분 | 본음이 남지 않는 짧은 음은 현재처럼 실패시키고 원보 해석 검수 |
| 명시된 음량 요성 | 단소 기본 `amplitude`, 4Hz·depth0.08·지연250ms·시작/끝 fade150ms | 명시된 요성에만 기존값으로 시작. 최초 비교는 추가 피치 떨림 none | 장르·선법별 방식·깊이·시작 위치. 호흡 표현 전체를 음량만으로 재현하지 못함 |
| 음높이·혼합 요성 | `pitch`/`mixed`: ±25cent·4Hz·지연250ms·fade150ms; mixed는 음량 변화도 적용 | 원보 해석상 필요한 경우만 명시적으로 선택 | 추성·퇴성의 방향성 있는 곡선은 현재 사인파 요성과 별개로 미구현 |
| 감상용 추가 떨림 | quarter ±25cent / third ±33⅓cent, 4.5Hz, 650ms 이상 음, 지연220ms, fade-in/out180/160ms | 미리듣기 quarter를 초기 후보로 사용하고 비교용 none·third 제공. 기존 청취 실험을 재현하고 두 후보 중 작은 폭 사용 | 모든 긴 음에 적용할지, CAT 샘플 자체 흔들림과의 결합 |
| 숨표 | 미지정0초. 지정하면 `breath_seconds + 50ms`만큼 note-off를 앞당김 | 명시된 숨표의 예시값 **120ms 공백**, 숨표 없는 곳0. 기존 세령산 입력 재사용 | 음 길이≤170ms에서는 적용 불가. 속도·종지별 자연스러움 |
| 이음·붙임 | 연결 attack/release20ms, 일반 release50ms, 단소 연결 샘플 시작점100ms. 동일음 연결은 샘플 위치를 이어가고 붙임줄은 한 이벤트로 합침 | 기존값 유지. 이전 단소 연결 실험을 재현 | 자연스러움·샘플 길이 한계. 다른 악기에100ms를 일반화하지 않음 |
| 강약 | 기본 본음 velocity90·앞꾸밈80, CC11 `[127,127,127]`, 제어점0%·45%·100% | 기본값 유지, 악보별 명시값 보존. 표현 비교의 변수를 줄임 | 악센트·점점 세게/여리게 기호의 수치화. upper-return 각 음은 본음 velocity 사용 |

구현 근거: [score.py](../../../ai/playback/score.py), [expression.py](../../../ai/playback/expression.py), [midi.py](../../../ai/playback/midi.py), [sampler.py](../../../ai/playback/sampler.py), [단소 프로필](../../../ai/playback/profiles/danso.json). 숨120ms는 엔진의 자동 기본값이 아니라 [세령산 입력](../../../ai/playback/examples/seryeongsan-first-jangdan.json)의 명시값이다.

### 단위와 적용 순서

- `1/16 quarter`는 4분음표의1/16이다. quarter BPM60에서62.5ms, 아래 예시 BPM67.5에서약55.6ms다. 일반적인16분음표는 `1/4 quarter`다. upper-return의 앞 두 단계 합은 이 값의 두 배다.
- 추가 떨림 이름 quarter는 **1/4반음=25cent**이며 길이 단위와 무관하다. 4Hz/4.5Hz는1초당 떨림 횟수다.
- amplitude depth0.08은 ±8%가 아니다. fade가 끝난 구간의 CC11 배율은 **0.92~1.00**이고 기본127은 반올림 후 약117~127이다. 최종 WAV 진폭은 샘플·velocity·출력 정규화에도 영향을 받는다.
- 명시된 pitch/mixed 요성에는 추가 떨림이 겹치지 않는다. amplitude 요성에는 추가 피치 떨림이 겹칠 수 있으므로 최초 검수에서는 none으로 둔다. none도 원본 CAT 샘플의 흔들림을 제거하지는 않는다.
- 숨120ms를 위해 note-off를170ms 앞당기고 release50ms 뒤에 공백을 남긴다. 악보 시간은 늘리지 않는다. 연결20ms는 무음이 아니라 겹치는 fade이며 샘플 시작점100ms는 숨 길이가 아니다.
- 선택 배율은 곡별 quarter BPM에 적용한다. 초기에는 꾸밈은 박 비율, 숨·떨림 주기·지연·fade는 고정 초로 유지한다. 음 길이가650ms보다 짧아지면 추가 떨림이 꺼지므로 속도별 결과를 확인한다. 세령산 BPM67.5를 다른 곡의 기본값으로 쓰지 않는다.
- `apply_expression()`의 기본은 quarter, CLI의 기본은 none이다. 재현 명령에는 `--vibrato`를 명시한다. 요성 fade150ms와 sampler의20/50/100ms는 현재 코드 상수이며 조절하려면 설정 분리가 필요하다.
- JGB 연결부에서도 채택한 시간 배분·숨 규칙을 명시해야 한다. 별도 로컬 변환 실험의 꾸밈 최대90ms/슬롯30%·숨 최대80ms 정책은 이 코드의 기본값과 다르다. 해당 로컬 변환기는 참고 실험이며 이번 MR에 포함하지 않는다. JGB 연결 시 규칙을 선택하고 일치 여부를 검증해야 한다.

### 음악적 근거와 적용 한계

2026-10-27 확인한 [국립국악원 「요성」](https://www.gugak.go.kr/ency/topic/view/2105)은 장르·선법에 따라 폭과 시작 위치가 달라질 수 있음을 설명한다. [단소 호흡법](https://academy.gugak.go.kr/dp/pds/HTML5-1-2-2/HTML5-1-2-2-7/index.htm)은 호흡이 음색·음정에도 영향을 준다고 설명한다. [정간보의 부호와 실제연주법](https://academy.gugak.go.kr/dp/pds/HTML5-2-2-2/HTML5-2-2-2-3/index.htm)은 기호별 음 진행·길이 배분과 쉼표/숨표를 구분한다.

이 자료들은 기호별 해석과 조절 가능성의 근거이며, **4Hz·±25cent·120ms를 표준 수치로 제시하지 않는다.** 수치는 기존 구현·예시를 재현하는 공학적 초기값이다. 기존에 인용한 단소 PDF는 이번 웹 조회에서 접근되지 않아 페이지별 설명을 새로 검증한 결과로 취급하지 않았다. 국악적 자연스러움과 연주 정답 여부는 전문가 검수 대상으로 남긴다.

### 기존 음원 대조와 재현

2026-10-24 생성 기록의 세령산 첫 장단 `danso-quarter.wav`와 `danso-third.wav`를 2026-10-27 다시 확인했다. 둘 다44,100Hz·mono·16-bit PCM·612,255프레임·13.883333초이며 저장 기록과 SHA-256이 일치한다. 입력은 원본37음, upper-return3곳·grace-before1곳·숨120ms1곳이고 MIDI는44음이다. **명시 요성은0곳**이므로 이 음원은 꾸밈·숨·추가 떨림 비교용이다.

| 기존 파일 | SHA-256 |
| --- | --- |
| danso-quarter.wav | `628020cb8d7b99b9fc5c688cf13d04ed6c778d593ed9f3e554b08001bb2e70c9` |
| danso-third.wav | `8e3253526fd603bf306fe96b5117b0227ea7e407b8b1f256d434c8f1ed794b06` |

기존 파일과 `http-runtime-verification.json`은 로컬 `2026-10-24-악보-사진-단소-서비스/` 실험 폴더에 보존한다. 이 두 파일의 대조는 헤더·입력·해시 확인이며 청취 품질 검수를 뜻하지 않는다. 원본 악보·CAT ZIP·WAV·모델은 Git에 넣지 않는다. 출처는 서울예술과학센터 CAT 국악 가상악기 라이브러리다.

현재 소스로 다시 만들려면 `ai/`에서 [실행 안내](../../../ai/playback/README.md)의 환경과 실제 로컬 assets 매핑을 준비하고 실행한다. manifest와 악기별 JSON에 소스·입력·프리셋·샘플 해시를 남기며, 아래 명령의 결과가 과거 서비스 산출물과 바이트 단위로 동일하다고 가정하지 않는다.

```powershell
.\.venv-playback\Scripts\python -m playback --score playback/examples/seryeongsan-first-jangdan.json --assets C:/local-gugak/assets.json --instrument danso --vibrato quarter --out C:/local-gugak/danso-quarter
.\.venv-playback\Scripts\python -m playback --score playback/examples/seryeongsan-first-jangdan.json --assets C:/local-gugak/assets.json --instrument danso --vibrato third --out C:/local-gugak/danso-third
```

명시 요성·짧은 음의 숨/꾸밈·선택 속도별 자연스러움은 추가 청취 검수 대상이다. 이를 연주 녹음 채점이나 전문가 모범 연주의 검증 완료로 확대하지 않는다.

### 범위 축소 후 재생 검증

2026-10-27 사진·웹서비스·다른 악기 프로필을 제외한 코드에서 핵심 unittest23개를 통과했다. 위 CLI와 같은 세령산 예제·단소·quarter 조건으로 실제 CAT 렌더를 다시 실행했다. 환경은 Python3.10.21·NumPy2.2.6·soundfile0.14.0, 산출물 폴더는 로컬 `C:/Users/kingc/Downloads/105-core-verification-mcgwtw01/`이다. 정확한 명령·환경·코드 해시는 그 폴더의 `verification.json`과 manifest에 보존했다.

37개 입력 ID가44개 MIDI 음의 `parent_ids`에 보존됐고 WAV는44,100Hz·mono·PCM16·612,255프레임·13.883333초·peak0.8499756이었다. WAV SHA-256은 위 기존 quarter 음원의 `628020cb...2e70c9`와 전체64자리 모두 일치한다. 단소 하나만 등록되고 정적 미리듣기 파일에 대금 비교 안내가 없음을 확인했다. 이는 실제 샘플 생성 검증이며 브라우저 재생 조작·전문가 청취는 이번 검증에 포함하지 않는다.
