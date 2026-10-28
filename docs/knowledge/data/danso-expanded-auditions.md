# 단소 JGB 표현과 M4A 출력 근거

작업: [S15P31E102-166](https://ssafy.atlassian.net/browse/S15P31E102-166), Story S15P31E102-30. 현행 MR 범위는 입력 악보 하나의 JGB→score→MIDI→CAT PCM→M4A/WAV 변환이다. 목록 일괄 처리·HTML·개별 판본 교정 도구는 제외한다. 기존에 생성한 로컬 음원과 조사 자료는 그대로 보존한다.

## 표현 의미와 재생 선택값

| 표현 | 반영 방법 | 근거와 한계 |
| --- | --- | --- |
| 숨표 | 무음120ms+release50ms를 목표로 하되 합계를 마지막 음 길이의 절반 이내로 제한. 다음 음 시작은 유지 | 국립국악원은 숨표의 의미를 설명하지만120ms라는 표준은 제시하지 않는다. 고정값을 짧은 음에 강제하지 않는 엔진 선택 |
| 빈지속 | 분박 자리를 유지하며 같은 각의 앞 음을 연장. 각 첫칸·쉼 뒤는 쉼 | 우물사이의 빈 자리·연장 의미와 실제 뱃노래25개·베틀가5개의 자리 대조. 삭제해서 음가를 바꾸지 않음 |
| 농음표·떠는표 | 단소 음량 요성4Hz·depth0.08·시작지연0. 긴 기호는 원보 범위의 각 본음에 적용 | 기존105의 amplitude 방식 재사용. 장르·음에 따라 달라지는 실제 농음을 완전히 재현하는 수치는 아님 |
| 흘림표 | 마지막30%(최대200ms)에 최대50cent 하강. 음표 자체의 MIDI높이·박 길이는 유지 | 소리를 아래로 흘리는 방향을 구현한 청취용 근사. 목표율명·하강폭을 원보의 확정값으로 주장하지 않음 |
| 미는표 / `push` | 같은 말미 구간에50cent 상승 | 공식 자료의 상행 방향에 근거한 근사. 추성/겹미는의 동의어로 처리하지 않음 |
| 풀어내림표 / `vib-long` |4Hz·±25cent 음높이 농음+말미60%(최대600ms)의50cent 하행 |2024 점자규정의 단소표 p131와 농음하며 하행하는 관악기표 p149의 동일 점형을 연결한 해석. 폭·시점은 서비스 재생 선택값. 실제 목표음까지 내리거나 물리적 운지·음색을 재현한 것은 아님 |
| 끊는표 / `staccato` | release를 포함해 절반 길이만 발음 |2020 해설 p35의 반길이 설명. 숨과 단축량을 중복 차감하지 않음. 이전 음의 release도 실제 무음 경계를 넘지 않도록 fade |
| 늘임표 / `fermata` | 표시된 자리의 연주시간2배, 이후 음도 이동 | 국악원의2~3배 설명에서2배를 선택. 해당 자리만 늘리고 원문 시간은 별도 보존. 긴 CAT 샘플이 부족하면 실패 |
| 덧길이·반길이 | 몽금포의 짝 기호를3:1로 배분하고 정간 총길이 보존 | 짝이 맞지 않아 정간 시간이 달라지는 입력은 실패 |
| 느니르·니루니 뒤 지속 | 마지막 본음을 연장하고 짧은 뒤꾸밈은 끝으로 이동 |2020 해설 p33와 기존105의 긴 가운데음을 본음으로 잡는 해석. 다른 교재의 기준음 설명 차이가 있어 모든 판본의 정답으로 일반화하지 않음 |
| 원문점 | 원본 주석·위치 보존, 소리 효과는 미적용 | 몽금포의 점을 반길이라고 확정할 근거가 부족함. 미확정 상태를 기록 |

숨+release 계산은 MIDI tick으로 내림 제한해 발음 절반 이상을 남긴다. CC72의0~127을0~50ms release로 해석하는 **프로젝트 전용 환산**을 사용한다. MIDI 표준은 CC72를 Release Time으로 정의하지만 이 절대시간 환산까지 정하지 않는다. 일반 MIDI 플레이어의 동일 음색·release는 보장하지 않는다.

기호와 물리적 의미의 일차 출처:

- [국립국악원 정간보의 부호와 실제연주법](https://academy.gugak.go.kr/dp/pds/HTML5-2-2-2/HTML5-2-2-2-3/index.htm): 농음·흘림·풀어내림의 서로 다른 글리프, 길이와 꾸밈.
- [국립국악원 요성](https://www.gugak.go.kr/ency/topic/view/2105), [퇴성](https://www.gugak.go.kr/ency/topic/view/2111): 음을 떠는 것과 아래로 흘리는 표현의 의미. 장르별 차이가 있어 고정 수치의 근거로 쓰지 않는다.
- [우물사이 기호 사전](https://github.com/danbinaerinHan/jeongganbo-generator/blob/9d4a24e54bf36093d4d264d0271d214500d36db7/js/symbols-registry.js): 고정 커밋의 기호 ID와 상대음열.
- [국립국어원2024 개정 한국 점자 규정](https://www.korean.go.kr/common/download.do?c_file_name=2024+%EA%B0%9C%EC%A0%95+%ED%95%9C%EA%B5%AD+%EC%A0%90%EC%9E%90+%EA%B7%9C%EC%A0%95.pdf&file_path=about&o_file_name=2024+%EA%B0%9C%EC%A0%95+%ED%95%9C%EA%B5%AD+%EC%A0%90%EC%9E%90+%EA%B7%9C%EC%A0%95.pdf): 인쇄129~131쪽 제19항 단소 기호 이름·글리프;148~149쪽 제36항 관악기 표현.
- [국립국어원 한국 음악 점자 규정 해설2020](https://www.korean.go.kr/common/download.do?c_file_name=korean_music_finger_alphabet.pdf&file_path=about&o_file_name=%ED%95%9C%EA%B5%AD+%EC%9D%8C%EC%95%85+%EC%A0%90%EC%9E%90+%EA%B7%9C%EC%A0%95+%ED%95%B4%EC%84%A4%EC%84%9C.pdf): 인쇄31·33·35쪽 대용부호·장식음·숨/끊기.
- [국립국악원 단소 교재](https://www.gugak.go.kr/site/inc/file/fileDownloadLog?dirname=/board/138&filename=kor_danso.pdf&type=F&sistype=D&boardseq=41189&boardid=10928): 인쇄33~35쪽 기호,38~48쪽 요성·퇴성·격음 관련 연주 설명. 관측된 개별 연주의 수치를 보편 상수로 쓰지 않음.
- [MIDI 공식 Control Change 표](https://midi.org/midi-1-0-control-change-messages): CC72의 정의.
- [Dorico의 숨표 재생](https://www.steinberg.help/r/dorico-pro/6.1/en/dorico/topics/notation_reference/notation_reference_holds_pauses/notation_reference_holds_pauses_playback_r.html): 앞 음에서 호흡 시간을 확보하는 구현 비교 자료. 국악 연주 표준으로 인용하지 않는다.

## 단소 기호 대조

2026-10-28 국악원 교육 페이지의12개 장식부호·12개 율명 대용부호를 점자규정의 이름·그림과 대조했다. 아래0은 본음, ±1은 선택한 조의 이웃음 한 칸이다. 장식의 `앞` 음들은 각각1/16 quarter를 사용하고 나머지 시가를 본음에 준다. `균등`은 전체 자리를 같은 길이로 나눈다. 독립 대용부호는 직전 음열의 마지막 본음을 기준으로 하며 뒤꾸밈을 기준음으로 삼지 않는다.

| 장식부호 / 입력 ID | 음열 | 대용부호 / 입력 ID | 균등 음열 |
| --- | --- | --- | --- |
| 니레 / nire | 앞[1] →0 | 노 / no |[-1] |
| 니라 / nina | 앞[2] →0 | 로 / ro |[-2] |
| 노네 / none | 앞[-1] →0 | 니 / ni |[1] |
| 너녜·너네 / neonye | 앞[-2] →0 | 리 / ri |[2] |
| 노니로 / noniro | 앞[0,1] →0 | 니나 / nina-dur |[1,0] |
| 네로네 / nerone | 앞[0,-1] →0 | 느니 / neuni |[1,2] |
| 나니나 / nanina | 균등[0,1,0] | 노라 / nora |[-1,-2] |
| 나느나 / naneuna | 균등[0,-1,0] | 니로나 / nirona |[1,0,-1] |
| 느니르·느니-르 / neunireu | 짧은-1 →긴0 →짧은-1 | 니레나 / nirena |[2,1,0] |
| 나니로·나니르 / naniro | 앞[-1,1] →0 | 느나르나니·느니르나니 / neunareunani |[1,2,1,0,2] |
| 로니로(한글 입력) | 앞[-2,1] →0 | 니느라니 / nineurani |[1,0,1,0] |
| 느로니르 / neuronireu | 앞[0,-1,1] →0 | 느나니나 / neunanina |[-1,0,1,0] |

`nina`는 장식 니라, `nina-dur`는 독립 니나이므로 서로 바꾸지 않는다. 새로 지원한 `nineurani`·`neunareunani`는 pinned registry의 음열과 공식 교육자료가 다르다. 이번 구현은 위 공식 음열을 선택하고 `source-map.symbol_policy.upstream_differences`에 기록한다. 원본 JSON을 교정하거나 덮어쓰지 않는다. 로니로는 공식 자료에 근거한 한글 입력 확장이며 상류 registry ID를 임의로 만들지 않았다.

추가 호환 입력은 니로/niro(앞[3]), 노리노/norino(앞[0,2]), 느네느/neuneneu(앞[0,3]), 니루니·니루-니/niruni(짧은1→긴0→짧은1), 느나/neuna([-1,0]), 네로나/nerona([-1,-2,0]), 같은음표/repeat([0])다. 떠이어표/roll은2020 해설의 **임→무→임** 예시에 한정한다. 무는 임보다3반음 높으며 조의 다음 칸으로 대체하지 않는다. 다른 본음에서는 적용 범위를 확인하도록 실패한다.

2024 목록의 격음은 지공을 순간적으로 열어 경쾌하게 발음하는 의미까지 확인했다. 겹미는·겹흘림·요성표·겹요성표·내림/하성은 단소 기호로 존재하지만, 현재 자료만으로 해당 글리프의 구체적 재생을 확정하지 못했다. 피리의 요성1회/2회 설명이나 단순 velocity 증가를 단소 연주법으로 전용하지 않는다. 퇴성·추성도 이름만으로 흘림·미는의 별칭으로 합치지 않는다. 이들은 **알려진 기호/재생 미확정**으로 위치와 함께 실패한다.

느니라/neunira·나니르노니르/nanireunonireu는 상류도 소리 미상, 노라느니/noraneuni는 추정이다. 공식 의미를 확인하기 전에는 실패한다. 반복표는 돌아갈 구간을 나타내는 JGB 문법이 없어 거부한다. `{repeat}`는 같은음 대용부호이고 `||`는 각 구분이므로 구간 반복으로 해석하지 않는다. 우물사이 `all` 팔레트 노출도 단소용 기호 인증이 아니다.

이 표는 조사한 교육 자료와 JGB 입력 범위의 지원표다. 모든 시대·지역·판본의 시김새를 포괄한다는 의미는 아니다. 꾸밈과 끊기, 지속과 끊기, 중복된 하행/상행 등 적용 범위가 모호한 조합은 묵살하지 않고 명시 음으로 분리하도록 오류를 낸다.

## 빠르기와 입력 경계

JGB의 `tempoBpm`은 정간/분이다. `staffUnit`의 quarter 비율을 음가와 `quarter_bpm`에 함께 곱하므로 정간 길이는 `60/tempoBpm`초로 유지된다. source-map에 원본 정간 BPM·quarter 비율·실제 quarter BPM을 기록한다. 1/18 quarter 분박을 표현하기 위해 PPQN14400을 사용한다.

변환 전에 **최상위 `tempoBpmUserSet === true` 또는 명시적인 `--bpm`**을 요구한다. 기본 숫자60·장단명·표시용 `tempoBpmGak`·`--speed`만으로 변환하지 않는다. 숫자 범위 검사도 별도로 수행한다. controls 안의 같은 이름 필드는 확정 근거가 아니다. 원본 플래그가 false/누락인 유효 악보도 보수적으로 중단할 수 있으므로, 사용자가 재생값을 확인한 뒤 `--bpm`으로 선택한다.

이 플래그는 [상류 재생 설정](https://github.com/danbinaerinHan/jeongganbo-generator/blob/9d4a24e54bf36093d4d264d0271d214500d36db7/js/app.js#L2410)에서 재생 BPM과 표기값의 자동 연동을 끊은 상태이며 역사적 빠르기 인증이 아니다. 대응표의 `tempo_selection`은 선택 경로와 정간/분 단위를 기록하고 음악적 근거 상태를 `not-verified`로 유지한다. 빠르기 출처 조사는 입력 준비 단계의 책임이다. 늘임표가 있으면 `written_total_quarters`와 연주 `total_quarters`, 각 위치의 원문/연주 시각을 구분한다.

곡별 빠르기 조사·원보 교정·악기 분류·조 추정은 변환 엔진에서 하지 않는다. 입력의 확정된 값이나 `--bpm`, `--hwang-midi`, `--jo`를 사용한다. 이전 단소 목록에서 사용자가 승인한 연습용60정간/분은 그 자료 준비의 선택값이며, 모든 입력에 자동 적용하는 규칙이 아니다.

## M4A 출력과 재현

기본 출력은 **44.1kHz·mono·AAC-LC, 목표192kbps M4A**다. 합성 PCM을 float32 little-endian으로 FFmpeg stdin에 전달하며 중간 WAV를 저장하지 않는다. `--audio-format wav`를 선택하면 기존 PCM16 WAV를 쓴다. 출력 코덱에 따라 JGB·템포·조율·표현·MIDI 해석을 바꾸지 않는다.

`audio.py`는 인코딩과 ffprobe/FFmpeg 재디코딩 검사를 맡는다. 시작 시각0·표시 길이 오차2샘플 이내·디코딩 끝 패딩1024샘플 미만·유한값·피크를 검사한다. `audio.frames/seconds`는 합성 PCM, `container_seconds`는 컨테이너 표시 길이, `decoded_frames/end_padding_frames`는 디코딩 길이와 끝 패딩이다. FFmpeg·ffprobe 버전, 실제 비트레이트·파일 크기·해시를 기록한다.

`__main__.py`는 임시 폴더에서 검증한 산출물을 저장한다. 인코더 누락·이전 형식 출력 충돌은 사전 거부하고 인코딩 실패 시 기존 음원·JSON을 보존한다. 원본 JGB 사본·source-map·score·MIDI·해석 결과·manifest가 재현과 오류 추적에 사용된다.

AAC는 손실 압축이며 목표와 실제 비트레이트는 다를 수 있다. 자료: [MDN 오디오 코덱](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Formats/Audio_codecs), [Android 지원 형식](https://developer.android.com/media/platform/supported-formats#audio-support), [FFmpeg AAC 인코더](https://ffmpeg.org/ffmpeg-codecs.html#aac). 기존 조사일2026-10-28. 채점이 JGB를 사용할 수 있다는 사용자 설명은 출력 선택의 배경이며 다른 파트의 확정 계약으로 취급하지 않는다.

## 검증

실행법과 검사 명령은 [CLI 안내](../../../data/src/playback/README.md)를 따른다. 검증 환경은 Windows·Python3.12·FFmpeg/ffprobe9.0.2다. 작은 합성 신호로 음높이·분박 시간·숨의 실제 무음·지속 위 농음·흘림·원본 대응·AAC 시간과 실패 시 기존 산출물 보존을 검사한다.

목록 도구를 제외하기 전 단소47입력의 MIDI·PCM 길이와 M4A 인코딩을 검증했다. 이 기록은 실제 자료 검증 이력이며 일괄 처리 기능의 제공을 뜻하지 않는다. 범위를 줄인 뒤 단일 변환 검사57개(실제 AAC 통합2개 포함)가 통과했다. 같은 준비 JGB로 단일 CLI를 실행해 강원도아리랑 M4A와 선택 WAV가 기존 SHA256과 같음을 확인했다. 두 출력 폴더에는 각각 음원·MIDI·JSON 7개만 생성됐고 HTML은 생성되지 않았다. 합성 성공은 전문가의 연주 품질 승인이나 AAC 청감 무차이 판정이 아니다.

이번 기호·빠르기 검증 보강 후72개 검사(실제 AAC2개 포함)가 통과했다. 공식24음형·별칭·본음 연장/뒤꾸밈 이동·풀어내림/미는 방향·늘임 원문/연주 시간·빠른2/9분박 끊기와 실제 무음·빠르기 미선택 거부·실패 시 원본/기존 산출물 보존을 검사했다. 리뷰에서 떠이어표의 평조 음높이, IR 장식과 지연 곡선의0나눔, 앞음 잔향 누수를 발견해 회귀 검사로 고정했다. 현재 로컬 prepared 폴더의46개 JGB도 수정 없이 해석·score 검사를 통과했다.

실제 CAT 단소로 자체 작성한30각60정간 입력을 변환했다. 장식/대용24종과 새 표현을 포함해100음·61.550초 M4A 생성과 디코딩 검사를 통과했다. 악보 시가90 quarter와 늘임을 반영한91.5 quarter를 구분했다. 기존 강원도아리랑은81음·25.813초이며 MIDI·M4A·WAV 모두 이전 SHA256과 같다. 각 출력은7파일·HTML0이다. 로컬 재현 자료는 `data/processed/playback/166-listen/mr87-notation-check/verification.json`에 있으며 원본 악보·CAT·생성 음원은 MR에 넣지 않는다.
