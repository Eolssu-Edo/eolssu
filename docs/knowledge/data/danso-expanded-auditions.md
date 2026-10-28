# 단소 JGB 표현과 M4A 출력 근거

작업: [S15P31E102-166](https://ssafy.atlassian.net/browse/S15P31E102-166), Story S15P31E102-30. 현행 MR 범위는 입력 악보 하나의 JGB→score→MIDI→CAT PCM→M4A/WAV 변환이다. 목록 일괄 처리·HTML·개별 판본 교정 도구는 제외한다. 기존에 생성한 로컬 음원과 조사 자료는 그대로 보존한다.

## 표현 의미와 재생 선택값

| 표현 | 반영 방법 | 근거와 한계 |
| --- | --- | --- |
| 숨표 | 무음120ms+release50ms를 목표로 하되 합계를 마지막 음 길이의 절반 이내로 제한. 다음 음 시작은 유지 | 국립국악원은 숨표의 의미를 설명하지만120ms라는 표준은 제시하지 않는다. 고정값을 짧은 음에 강제하지 않는 엔진 선택 |
| 빈지속 | 분박 자리를 유지하며 같은 각의 앞 음을 연장. 각 첫칸·쉼 뒤는 쉼 | 우물사이의 빈 자리·연장 의미와 실제 뱃노래25개·베틀가5개의 자리 대조. 삭제해서 음가를 바꾸지 않음 |
| 농음표·떠는표 | 단소 음량 요성4Hz·depth0.08·시작지연0. 긴 기호는 원보 범위의 각 본음에 적용 | 기존105의 amplitude 방식 재사용. 장르·음에 따라 달라지는 실제 농음을 완전히 재현하는 수치는 아님 |
| 흘림표 | 마지막30%(최대200ms)에 최대50cent 하강. 음표 자체의 MIDI높이·박 길이는 유지 | 소리를 아래로 흘리는 방향을 구현한 청취용 근사. 목표율명·하강폭을 원보의 확정값으로 주장하지 않음 |
| 덧길이·반길이 | 몽금포의 짝 기호를3:1로 배분하고 정간 총길이 보존 | 짝이 맞지 않아 정간 시간이 달라지는 입력은 실패 |
| 느니르·니로나 | 기호 사전의 앞/본/뒤 음과 상대음열 적용. 꾸밈은 기존1/16 quarter | 기호 사전의 느니르=`neunireu`, 니로나=`nirona`; 임의로 낮은 음을 삭제하지 않음 |
| 원문점 | 원본 주석·위치 보존, 소리 효과는 미적용 | 몽금포의 점을 반길이라고 확정할 근거가 부족함. 미확정 상태를 기록 |

숨+release 계산은 MIDI tick으로 내림 제한해 발음 절반 이상을 남긴다. CC72의0~127을0~50ms release로 해석하는 **프로젝트 전용 환산**을 사용한다. MIDI 표준은 CC72를 Release Time으로 정의하지만 이 절대시간 환산까지 정하지 않는다. 일반 MIDI 플레이어의 동일 음색·release는 보장하지 않는다.

기호와 물리적 의미의 일차 출처:

- [국립국악원 정간보의 부호와 실제연주법](https://academy.gugak.go.kr/dp/pds/HTML5-2-2-2/HTML5-2-2-2-3/index.htm): 농음·흘림·풀어내림의 서로 다른 글리프, 길이와 꾸밈.
- [국립국악원 요성](https://www.gugak.go.kr/ency/topic/view/2105), [퇴성](https://www.gugak.go.kr/ency/topic/view/2111): 음을 떠는 것과 아래로 흘리는 표현의 의미. 장르별 차이가 있어 고정 수치의 근거로 쓰지 않는다.
- [우물사이 기호 사전](https://github.com/danbinaerinHan/jeongganbo-generator/blob/9d4a24e54bf36093d4d264d0271d214500d36db7/js/symbols-registry.js): 고정 커밋의 기호 ID와 상대음열.
- [MIDI 공식 Control Change 표](https://midi.org/midi-1-0-control-change-messages): CC72의 정의.
- [Dorico의 숨표 재생](https://www.steinberg.help/r/dorico-pro/6.1/en/dorico/topics/notation_reference/notation_reference_holds_pauses/notation_reference_holds_pauses_playback_r.html): 앞 음에서 호흡 시간을 확보하는 구현 비교 자료. 국악 연주 표준으로 인용하지 않는다.

## 빠르기와 입력 경계

JGB의 `tempoBpm`은 정간/분이다. `staffUnit`의 quarter 비율을 음가와 `quarter_bpm`에 함께 곱하므로 정간 길이는 `60/tempoBpm`초로 유지된다. source-map에 원본 정간 BPM·quarter 비율·실제 quarter BPM을 기록한다. 1/18 quarter 분박을 표현하기 위해 PPQN14400을 사용한다.

곡별 빠르기 조사·원보 교정·악기 분류·조 추정은 변환 엔진에서 하지 않는다. 입력의 확정된 값이나 `--bpm`, `--hwang-midi`, `--jo`를 사용한다. 이전 단소 목록에서 사용자가 승인한 연습용60정간/분은 그 자료 준비의 선택값이며, 모든 입력에 자동 적용하는 규칙이 아니다.

## M4A 출력과 재현

기본 출력은 **44.1kHz·mono·AAC-LC, 목표192kbps M4A**다. 합성 PCM을 float32 little-endian으로 FFmpeg stdin에 전달하며 중간 WAV를 저장하지 않는다. `--audio-format wav`를 선택하면 기존 PCM16 WAV를 쓴다. 출력 코덱에 따라 JGB·템포·조율·표현·MIDI 해석을 바꾸지 않는다.

`audio.py`는 인코딩과 ffprobe/FFmpeg 재디코딩 검사를 맡는다. 시작 시각0·표시 길이 오차2샘플 이내·디코딩 끝 패딩1024샘플 미만·유한값·피크를 검사한다. `audio.frames/seconds`는 합성 PCM, `container_seconds`는 컨테이너 표시 길이, `decoded_frames/end_padding_frames`는 디코딩 길이와 끝 패딩이다. FFmpeg·ffprobe 버전, 실제 비트레이트·파일 크기·해시를 기록한다.

`__main__.py`는 임시 폴더에서 검증한 산출물을 저장한다. 인코더 누락·이전 형식 출력 충돌은 사전 거부하고 인코딩 실패 시 기존 음원·JSON을 보존한다. 원본 JGB 사본·source-map·score·MIDI·해석 결과·manifest가 재현과 오류 추적에 사용된다.

AAC는 손실 압축이며 목표와 실제 비트레이트는 다를 수 있다. 자료: [MDN 오디오 코덱](https://developer.mozilla.org/en-US/docs/Web/Media/Guides/Formats/Audio_codecs), [Android 지원 형식](https://developer.android.com/media/platform/supported-formats#audio-support), [FFmpeg AAC 인코더](https://ffmpeg.org/ffmpeg-codecs.html#aac). 기존 조사일2026-10-28. 채점이 JGB를 사용할 수 있다는 사용자 설명은 출력 선택의 배경이며 다른 파트의 확정 계약으로 취급하지 않는다.

## 검증

실행법과 검사 명령은 [CLI 안내](../../../data/src/playback/README.md)를 따른다. 검증 환경은 Windows·Python3.12·FFmpeg/ffprobe9.0.2다. 작은 합성 신호로 음높이·분박 시간·숨의 실제 무음·지속 위 농음·흘림·원본 대응·AAC 시간과 실패 시 기존 산출물 보존을 검사한다.

목록 도구를 제외하기 전 단소47입력의 MIDI·PCM 길이와 M4A 인코딩을 검증했다. 이 기록은 실제 자료 검증 이력이며 일괄 처리 기능의 제공을 뜻하지 않는다. 범위를 줄인 뒤 단일 변환 검사57개(실제 AAC 통합2개 포함)가 통과했다. 같은 준비 JGB로 단일 CLI를 실행해 강원도아리랑 M4A와 선택 WAV가 기존 SHA256과 같음을 확인했다. 두 출력 폴더에는 각각 음원·MIDI·JSON 7개만 생성됐고 HTML은 생성되지 않았다. 합성 성공은 전문가의 연주 품질 승인이나 AAC 청감 무차이 판정이 아니다.
