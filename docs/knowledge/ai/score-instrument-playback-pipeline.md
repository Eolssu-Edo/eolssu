# 악보를 네 국악기의 MIDI·음원으로 재생하는 파이프라인

> 현재 범위(2026-10-27): 105는 악보를 MIDI·CAT 음원으로 만들고 단소 표현의 재생 초기값과 한계를 정리한 로컬 실험이다. 후속 사진 인식·미리듣기 도구와 106·107·108의 기존 프로필은 2026-10-25에 통합됐다. 아래 2026-10-23 기록은 초기 CLI 실험이며, 후속 서비스 범위는 [사진 서비스 기록](score-photo-service.md), 현행 표현 수치는 이 문서의 마지막 절을 따른다. 연주 녹음 채점·평가 API는 후속 작업이다.

2026-10-23. Jira [S15P31E102-105](https://ssafy.atlassian.net/browse/S15P31E102-105) 단소·공통, [106](https://ssafy.atlassian.net/browse/S15P31E102-106) 정악대금, [107](https://ssafy.atlassian.net/browse/S15P31E102-107) 산조대금, [108](https://ssafy.atlassian.net/browse/S15P31E102-108) 소금. 모두 Story 30 ‘모범 연주 재생’ 관련 작업입니다. 기존 Task 78의 단소 세령산 실험을 재사용했습니다.

## 2026-10-23 초기 CLI의 선택과 구현 범위

사용자는 악보를 단소·정악대금·산조대금·소금으로 재생하는 파이프라인과 악기별 해석 조사를 요청했고, Jira는 악기별로 분리하도록 지시했습니다. 공통 엔진은 단소 Task 105에 두고, 나머지는 프로필·자료 근거·검증 결과를 독립 관리합니다. 팀의 서비스 계약이나 전문가의 연주 해석을 확정한 기록은 아닙니다.

선택한 구조는 **수동 원보 대조 JSON → 명시적 실음/장식 이벤트 → MIDI 저장 → 저장 MIDI 재해석 → CAT 샘플 WAV**입니다. 기보법별 해석과 재생기를 분리해 정간보·MusicXML 어댑터를 나중에 붙일 수 있습니다. 자동 OMR, 이미지→MusicXML, 임의 MusicXML 가져오기는 이번 구현에 없습니다. 단소 악보를 다른 악기용으로 자동 편곡하지도 않습니다. 현재는 같은 실음 선율의 음색 미리듣기입니다.

악기명만으로 황종·옥타브·요성을 결정하는 방식은 제외했습니다. 기보음/실음과 판본을 입력에 남기며, 모호한 장식과 범위 밖 음은 조용히 바꾸지 않고 실패합니다. 악기별 서비스에 넣기 전 전문가 검수와 악보/기종별 조율 확인이 필요합니다.

구현과 재현 명령: [ai/playback/README.md](../../../ai/playback/README.md). 공통 서비스 의존성·서버 API는 변경하지 않았습니다.

## 악기별 자료와 적용 규칙

국립국악원 교재의 쪽수는 인쇄 쪽수입니다. 음역은 MIDI 번호를 병기하되, 샘플러 지원 범위와 실연 가능성을 혼동하지 않습니다.

| 악기 | 확인한 근거 | 이번 프로필의 처리 |
| --- | --- | --- |
| 정악단소 | 교재 p26 음역도: 8위 음자리표를 적용하면 Gb4(괄호 G4)–Ab6. p33–34 위음 꾸밈, p35 숨·요성·혀치기, p38 호흡에 의한 요성과 음높이 변화 요성 구분 | 교재 범위 66–92. 실음 입력에는 +12를 다시 하지 않음. 위음은 입력에서 지정. 정악 요성을 요청할 때 음량 떨림 근사를 제안 기본값으로 제공 |
| 정악대금 | 대금 교재 p143–144 전통 Bb3–Eb6, 현대 확장 E6–Ab6. 국악사전 대금 항목은 오선보 실음 표기 설명 | 교재 범위 58–92, 전통 관행 58–87. 관행 밖은 검토 경고. 황종 기준과 농음/퇴성 의미는 곡 문맥에서 지정 |
| 산조대금 | 대금 교재 p145 C관에서 시작, 현재 C#관이 흔함. 악조 항목은 조율 기준인 청 설명 | 관 이름만으로 전체 선율을 이조하지 않음. 관종별 기술 음역 미확정이므로 CAT 60–91만 확인. 농음 모드도 명시 입력 필요 |
| 전통 소금 | 소금 교재 p61 대금 음역 기보 시 실음 한 옥타브 위. 운지표 실음 Bb4–C7. p78 정악은 보통 Ab6까지. p72–74 농음은 음량·음정·시작 위치가 문맥에 따라 다름 | 교재 범위 70–96, 정악 관행 70–92. 기보 문맥에서만 +12. 실음 입력에는 적용하지 않음. 요성 요청에 mixed를 제안 기본값으로 제공 |

출처:

- 국립국악원 『창작을 위한 국악기 이해와 활용2』 [단소 PDF](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?dirname=/board/138&filename=kor_danso.pdf&type=F&sistype=D&boardseq=41189&boardid=10928), [소금 PDF](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?boardid=10929&boardseq=41191&dirname=%2Fboard%2F138&filename=kor_sogeum.pdf&sistype=D&type=F).
- 국립국악원 [국악기 이해와 활용 자료 목록](https://www.gugak.go.kr/site/program/board/basicboard/list?boardtypeid=116&menuid=001003003006002&lang=ko)의 대금 편. 로컬 도판 확인 파일: 별도 데이터 조사 worktree `data/raw/instruments/daegeum/ngc-practical-guide-ch04-daegeum.pdf`.
- 국악사전 [율자보](https://www.gugak.go.kr/ency/topic/view/2089): 아악·당악 황종 C, 향악 황종 Eb 문맥 구분. [청성](https://www.gugak.go.kr/ency/topic/view/1585): 옥타브 구분. [악조](https://www.gugak.go.kr/ency/topic/view/2028): 조율 기준과 청. [대금](https://www.gugak.go.kr/ency/topic/view/108), [시김새](https://www.gugak.go.kr/ency/topic/view/1728).

단소/소금 사전과 교재의 일부 옥타브 숫자가 일치하지 않아 교재의 음역 도판·음자리표를 직접 대조했습니다. 본 프로필은 지정된 기종·교재에 한정하며, 평조단소·향제단소·개량단소·개량소금까지 자동 포괄하지 않습니다. 음역 안의 반음 운지와 빠른 진행이 모두 같은 난도로 가능하다는 뜻도 아닙니다.

기존 데이터 조사와 중복 구축하지 않습니다: [단소](../data/danso.md), [정악대금](../data/jeongak-daegeum.md), [산조대금](../data/sanjo-daegeum.md), [소금](../data/sogeum.md). 그 문서의 녹음 라벨과 이번 악보 실음 입력은 서로 다른 출처이며, 전문가 정답으로 간주하지 않습니다.

## CAT 배포본에서 확인한 제약

[CAT 공식 GUGAK Library](https://www.catseoul.com/gugak-library/)의 Live Sampler v1.3를 읽었습니다. 공식 페이지가 연결한 배포: [단소](https://www.dropbox.com/sh/9bdei2cq8sftjfg/AADaOEkV9dyY7t08Pt8vx8_5a?dl=1), [정악대금](https://www.dropbox.com/sh/mtin8b718eas9rk/AADfQ8oh4fkh6OKhJzENQLHma?dl=1), [산조대금](https://www.dropbox.com/sh/sm2plii46wipkej/AAAdWKT77el4Uln37NuRIlama?dl=1), [소금](https://www.dropbox.com/sh/ajk3hh8mbwh5b9i/AABcOMZ4fYc8FhPtsyGJI2kva?dl=1).

| simple 프리셋 | 키존 | MIDI 지원 범위 | 처리 |
| --- | ---: | --- | --- |
| 단소 | 16 | 66–92 | 전용 DanSo 샘플 |
| 정악대금 | 32 | 58–89 | 키마다 root=key |
| 산조대금 | 32 | 60–91 | 키마다 root=key |
| 소금 | 18 | 70–99 | 인접 키를 root 샘플에서 이조 |

정악·산조 대금 패키지의 **전체 WAV 67/67개 이름·SHA256가 동일**합니다. simple의 32개 중 30개를 공유하며 공통 MIDI 60–89에서 file/root/detune/trim/volume과 글로벌 재생 설정이 같습니다. modal도 공통 음역에서 매핑 차이가 없었습니다. 네 프로필을 구현했다고 네 종류의 독립 녹음 음색을 확보한 것은 아닙니다. simple은 요성/장식 샘플을 자동 선택하는 프로그램도 아닙니다.

확인한 세 신규 패키지의 simple은 44.1kHz stereo PCM24 원본, sustain loop 0, attack 약 0.1ms, release 50ms, preset gain −12dB입니다. 정악·산조 zone volume은 약 0.5012, 소금은 1입니다. 원본 샘플 길이는 정악 8.403–12.365초, 산조 5.832–12.365초, 소금 4.620–8.413초입니다. 이조 후 가용 길이는 달라지므로 렌더 시 실제 소진 여부를 확인합니다.

직접 읽은 v1.3 ZIP SHA256:

| ZIP | SHA256 |
| --- | --- |
| DaeGeum_JeongAk | `ca1f9388e55611e3766611409ca293bee4352a3960f367bb9c718aee5659f540` |
| DaeGeum_SanJo | `b27d2247543a019a45f8aba593e3aece425d66a59cbb14d156eca294d36d65b4` |
| SoGeum | `f79ab3b9fcc78d130780ba221b30c5691cb5d0f873f68026ed56dea9a8c44bd4` |

단소 ZIP·전체 소스·입력·각 WAV 해시는 결과 manifest/악기 JSON에 남겼습니다. raw ZIP/WAV를 Git에 포함하지 않았습니다. 이전 Sori 공식 32bit 바이너리는 기존 64bit 호스트에서 로드 실패한 기록이 있어 이번 런타임으로 사용하지 않았습니다. 샘플 사용 표기를 출력에 포함하며, 서비스 배포 조건은 배포 단계에서 별도로 확인해야 합니다.

## 실험 입력과 실제 결과

입력: [세령산 첫 장단 JSON](../../../ai/playback/examples/seryeongsan-first-jangdan.json). 본음 37개, 위음 꾸밈 parent 8·14·27, F5 앞꾸밈 parent 26 → 44개 이벤트. 실음 MIDI 70–89. 점4분음표 45(4분음표 67.5). 첫 장단에는 긴 요성 기호가 없어 기존의 임의 ±12cent 반복 흔들림은 이 입력에서 제외했습니다. 악보가 지시하지 않는 추가 표현을 항상 넣지 않습니다.

꾸밈 길이 1/16박, 숨 무음 120ms, 20ms 샘플 연결, 단소 지속부 100ms 시작점, velocity 90/80/106은 **재생용 선택값**입니다. 이음줄의 세부 음색·중첩 슬러·물리적 호흡·모든 시김새를 완전히 옮긴 결과는 아닙니다. 악보 전반의 완전 변환을 주장하지 않습니다.

실행 환경: 기존 격리 Python 3.10.21, NumPy 1.26.4, mido 1.3.3, soundfile 0.14.0, Windows. AI 채점 서비스의 Python 3.12 환경과 별도입니다.

출력 위치: `C:/Users/kingc/Downloads/2026-10-23-국악-4종-악보-재생/preview/`. 입력 경로와 ZIP 매핑은 상위 `assets.json`에 있습니다. CLI 인자는 실행 안내의 예제와 같고 실제 `--assets`/`--out`만 해당 로컬 경로로 지정했습니다.

| 출력 | MIDI 음 수 | WAV | peak / RMS |
| --- | ---: | --- | --- |
| 단소 | 44 | 44.1kHz PCM16 mono, 612,255프레임, 13.88333초 | 0.79123 / 0.18467 |
| 정악대금 | 44 | 동일 길이 | 0.41599 / 0.08727 |
| 산조대금 | 44 | 동일 길이 | 정악대금과 동일 |
| 소금 | 44 | 동일 길이 | 0.85001 / 0.20822 |

공통 출력 게인 약 5.43547. 4개 모두 샘플 범위 이탈·소진·clipping 없음. 저장 MIDI→재렌더 trace 일치, WAV와 차이 PCM16 1 LSB 미만. 정악/산조 WAV 전체 PCM 최대 차이 0, SHA256 `0505818d65707ec3e7a88ff6cf84b09a2d8017de93a683d097483d51346f2c53`로 동일했습니다.

## 음높이 수치 점검과 검증 범위

대표 3음의 attack 이후 구간에서 100–3000Hz autocorrelation과 FFT의 기본음·배음을 교차 점검했습니다. MIDI 75는 시작 후 0.2–0.5초, 82는 0.3–0.7초, 89는 0.15–0.35초를 사용했습니다. 단소 75의 가장 강한 FFT 피크는 2배음이므로 최강 피크만 기본음으로 취급하지 않았습니다.

| 악기 | MIDI75/Eb5 622.25Hz 대비 | MIDI82/Bb5 932.33Hz 대비 | MIDI89/F6 1396.91Hz 대비 |
| --- | ---: | ---: | ---: |
| 단소 | −0.5cent | −0.2cent | −1.2cent |
| 정악/산조대금 | +5.3cent | −2.7cent | +23.7cent |
| 소금 | +19.7cent | +9.7cent | +16.1cent |

대금 89와 소금 75의 편차는 원본 샘플 같은 구간에도 있습니다. 소금 82는 원본 약 +14.71cent에 프리셋 −5cent를 적용한 출력이며 기대 변환과 관측의 차이는 약 0.011cent였습니다. 대표 구간에서 큰 매핑 오류를 찾지 못했지만 **A440 완전 조율·전 음역 검증은 아닙니다**. 자동 샘플 음높이 보정은 현재 구현하지 않았습니다.

18개 격리 unittest 통과. 음표 ID와 꾸밈 확장, 실음 이중 이조 방지, 율명 기준 누락, 쉼/붙임/슬러 구분, 음량·Pitch Bend의 실제 PCM 효과, 숨 무음, 샘플 범위/소진 실패, 입력 오류, 이전 출력 보존을 확인했습니다. 독립 리뷰에서 발견한 붙임줄 중간 슬러/velocity 손실은 명시적 오류로 바꾸었고, 다음 음 CC11이 이전 음에 미리 보간되는 문제는 held CC 적용으로 수정해 회귀 검사를 추가했습니다.

브라우저 청취·화면·키보드 조작 검증은 하지 않았습니다. 이전 로컬 file:// 페이지 접근이 도구 보안 제한에 걸려 우회하지 않았으며, 이번에는 파일·MIDI·PCM·정적 링크를 확인했습니다. 국악 전문가의 청감·실연·원보 정확성 검수도 미완료입니다.

## 서비스로 옮길 때 남은 일

1. 악보 수입: MusicXML/정간보 각각의 파서에서 원보 기호·위치·판본·미해석 표시를 유지하고 검수 화면으로 연결.
2. 악기 편곡: 관/청·조율·운지·숨·옥타브 배치와 장식의 장르별 의미를 검수. 현재 같은 실음 미리듣기와 구분.
3. 샘플 품질: 전체 키존/음길이/주법별 F0와 연결음 검증, 실제 정악/산조 음색 차이가 필요한 별도 녹음 확보, 긴 음용 검증된 loop/샘플 전략.
4. 서버 통합: 업로드·작업 큐·취소·캐시·경로/리소스 제한·출처/사용 조건을 별도 서비스 설계로 다룸. 현재 로컬 CLI를 그대로 외부 업로드 API에 노출하지 않음.

표현 가능성을 악보의 완전성이나 연주 정답으로 바꾸지 않습니다. 이번 확인 범위는 ‘명시한 입력 규칙대로 MIDI·WAV를 생성하는가’입니다.

## 2026-10-27 단소 표현의 재생 초기값

Task105 마무리 범위는 악보 표현을 수치로 해석해 소리에 적용한 실행 경로와 재현 근거다. 아래는 `71ed018` 코드의 기본값·예시값을 대조한 **조절 가능한 실험 초기값**이다. 팀의 채점 정책이나 공인된 연주 규칙으로 확정한 값이 아니다. 이번 정리는 런타임 값을 바꾸지 않으며, 악보에 검수된 개별값이 있으면 그 값을 우선한다.

| 표현 | 기존 값·동작 | 초기 적용안·근거 | 남은 검수 |
| --- | --- | --- | --- |
| 앞꾸밈음 | `grace-before`: 이웃음 → 본음, 기본 `1/16 quarter`를 본음에서 차감 | 기존값 유지. 기존 예시와 호환되고 템포를 따라감 | 기호별 이웃음 높이와 빠른 곡에서의 최소 길이 |
| 위음 후 복귀 | `upper-return`: 본음 → 위음 → 본음, 앞 두 단계 각각 `1/16 quarter` | 기존값 유지. 전체 악보 시간 안에서 배분 | 본음이 남지 않는 짧은 음은 현재처럼 실패시키고 원보 해석 검수 |
| 명시된 음량 요성 | 단소 기본 `amplitude`, 4Hz·depth0.08·지연250ms·시작/끝 fade150ms | 명시된 요성에만 기존값으로 시작. 최초 비교는 추가 피치 떨림 none | 장르·선법별 방식·깊이·시작 위치. 호흡 표현 전체를 음량만으로 재현하지 못함 |
| 음높이·혼합 요성 | `pitch`/`mixed`: ±25cent·4Hz·지연250ms·fade150ms; mixed는 음량 변화도 적용 | 원보 해석상 필요한 경우만 명시적으로 선택 | 추성·퇴성의 방향성 있는 곡선은 현재 사인파 요성과 별개로 미구현 |
| 감상용 추가 떨림 | quarter ±25cent / third ±33⅓cent, 4.5Hz, 650ms 이상 음, 지연220ms, fade-in/out180/160ms | 미리듣기 quarter, 비교용 none, third 선택. 기존 서비스·청취 실험을 재현하고 두 후보 중 작은 폭 사용 | 모든 긴 음에 적용할지, CAT 샘플 자체 흔들림과의 결합 |
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
- 서비스와 `apply_expression()`의 기본은 quarter, CLI의 기본은 none이다. 재현 명령에는 `--vibrato`를 명시한다. 요성 fade150ms와 sampler의20/50/100ms는 현재 코드 상수이며 조절하려면 설정 분리가 필요하다.
- JGB 연결부에서도 채택한 시간 배분·숨 규칙을 명시해야 한다. 별도 로컬 변환 실험의 꾸밈 최대90ms/슬롯30%·숨 최대80ms 정책은 이 코드의 기본값과 다르며, 해당 변환기와 곡별 산출물은 이번 MR의 추가 게시 범위에 포함하지 않는다.

### 음악적 근거와 적용 한계

2026-10-27 확인한 [국립국악원 「요성」](https://www.gugak.go.kr/ency/topic/view/2105)은 장르·선법에 따라 폭과 시작 위치가 달라질 수 있음을 설명한다. [단소 호흡법](https://academy.gugak.go.kr/dp/pds/HTML5-1-2-2/HTML5-1-2-2-7/index.htm)은 호흡이 음색·음정에도 영향을 준다고 설명한다. [정간보의 부호와 실제연주법](https://academy.gugak.go.kr/dp/pds/HTML5-2-2-2/HTML5-2-2-2-3/index.htm)은 기호별 음 진행·길이 배분과 쉼표/숨표를 구분한다.

이 자료들은 기호별 해석과 조절 가능성의 근거이며, **4Hz·±25cent·120ms를 표준 수치로 제시하지 않는다.** 수치는 기존 구현·예시를 재현하는 공학적 초기값이다. 기존에 인용한 단소 PDF는 이번 웹 조회에서 접근되지 않아 페이지별 설명을 새로 검증한 결과로 취급하지 않았다. 국악적 자연스러움과 연주 정답 여부는 전문가 검수 대상으로 남긴다.

### 기존 음원 대조와 재현

2026-10-24 생성 기록의 세령산 첫 장단 `danso-quarter.wav`와 `danso-third.wav`를 2026-10-27 다시 확인했다. 둘 다44,100Hz·mono·16-bit PCM·612,255프레임·13.883333초이며 저장 기록과 SHA-256이 일치한다. 입력은 원본37음, upper-return3곳·grace-before1곳·숨120ms1곳이고 MIDI는44음이다. **명시 요성은0곳**이므로 이 음원은 꾸밈·숨·추가 떨림 비교용이다.

| 기존 파일 | SHA-256 |
| --- | --- |
| danso-quarter.wav | `628020cb8d7b99b9fc5c688cf13d04ed6c778d593ed9f3e554b08001bb2e70c9` |
| danso-third.wav | `8e3253526fd603bf306fe96b5117b0227ea7e407b8b1f256d434c8f1ed794b06` |

기존 파일과 `http-runtime-verification.json`은 로컬 `2026-10-24-악보-사진-단소-서비스/` 실험 폴더에 보존한다. 이번에는 헤더·입력·해시를 대조했으며 새 합성이나 청취 품질 검수를 수행한 것은 아니다. 원본 악보·CAT ZIP·WAV·모델은 Git에 넣지 않는다. 출처는 서울예술과학센터 CAT 국악 가상악기 라이브러리다.

현재 소스로 다시 만들려면 `ai/`에서 [실행 안내](../../../ai/playback/README.md)의 환경과 실제 로컬 assets 매핑을 준비하고 실행한다. manifest와 악기별 JSON에 소스·입력·프리셋·샘플 해시를 남기며, 아래 명령의 결과가 과거 서비스 산출물과 바이트 단위로 동일하다고 가정하지 않는다.

```powershell
.\.venv-playback\Scripts\python -m playback --score playback/examples/seryeongsan-first-jangdan.json --assets C:/local-gugak/assets.json --instrument danso --vibrato quarter --out C:/local-gugak/danso-quarter
.\.venv-playback\Scripts\python -m playback --score playback/examples/seryeongsan-first-jangdan.json --assets C:/local-gugak/assets.json --instrument danso --vibrato third --out C:/local-gugak/danso-third
```

명시 요성·짧은 음의 숨/꾸밈·선택 속도별 자연스러움은 추가 청취 검수 대상이다. 이를 연주 녹음 채점이나 전문가 모범 연주의 검증 완료로 확대하지 않는다.
