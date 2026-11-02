# 공통 악보 음원의 독립 파형 검증

- 연결 Task: `S15P31E102-338`
- 기준: develop `0fc4c4a`에 추가한 [공통 악보 재생 스펙](../../specs/common-score-playback.md)의 단음·고정 BPM·원조율 재생
- 검증 대상: 검수된 아리랑 공통 악보를 BPM 96으로 변환해 CAT 단소 샘플러로 만든 WAV와 AAC-LC M4A
- 결과: 두 실제 음원에서 아래 검사 통과. 사람의 청음·악기 음색 품질·앱 재생 동작은 이 검사의 결론에 포함하지 않는다.

## 기대값과 관측값을 분리한 방법

[verify_common.py](../../../data/src/playback/verify_common.py)는 원본 `score.json`과 사용자가 지정한 BPM만 기대값으로 읽는다. 재생 어댑터, 컴파일 결과, MIDI, 소스 맵, 렌더 보고서를 가져오지 않는다. PCM의 주파수 추정 함수도 목표 주파수를 입력받지 않는다.

- `hwangHz × 2^((율의 반음 번호 + 12 × octave) / 12)`로 음높이를 구한다. 율 순서는 황·대·태·협·고·중·유·임·이·남·무·응이다.
- 원본 틱과 `ticksPerBeat`, 명시 BPM으로 시각을 구한다. `DOTTED_QUARTER`만 MIDI 4분음표 환산값 1.5를 사용하며, 다른 단위는 1을 사용한다. MIDI 정수 마이크로초 템포의 반올림도 별도로 계산한다.
- 숨표는 120ms 무음과 최대 50ms release를 합쳐 음 길이 절반으로 제한하는 기존 정책에서 구한다. 짧은 숨표의 CC72 release 양자화는 127단계로 계산한다. MIDI 틱 반올림 차이는 무음 경계 2ms 여유 안에서 취급한다.
- 음표마다 발음 구간 30%·50%·70% 지점에서 최대 80ms 창 세 개의 정규화 자기상관 F0를 측정한다. 유효 창 두 개 이상과 중앙 음정 오차 ±50cent를 요구한다. ±50cent는 반음 간 중간 경계이며 채점 알고리즘의 통과 기준을 정하는 값은 아니다.
- 발음 구간 앞·뒤 에너지와 2ms 구간별 에너지 시작·끝을 확인한다. WAV는 예상 경계에서 25ms 이내, M4A는 AAC 여유를 더한 50ms 이내여야 한다. 모든 NOTE 시작의 2ms 에너지 골도 확인해 이어진 같은 음의 재발음 누락을 탐지한다.
- 긴 음의 중간 단절은 별도로 검사한다. 발음 양끝 25ms를 제외한 구간 전체에 20ms RMS 창을 10ms 간격으로 놓으며, 마지막 창도 끝에 맞춰 넣는다. 모든 창은 발음 RMS의 2%와 절대 RMS 0.000001을 모두 넘어야 한다. 20ms는 관측 하한 100Hz의 두 주기이므로 사인파의 정상적인 순간 영점을 무음으로 오판하지 않는다. 이 시간 해상도보다 짧은 끊김까지 보증하지는 않는다.
- 원본 REST 및 숨표의 양 끝을 제외한 내부 구간을 검사한다. WAV는 경계 2ms를 제외하고 PCM이 정확히 0이어야 한다. M4A는 44.1kHz AAC 한 프레임(1024샘플)보다 조금 큰 25ms를 제외하고 무음 RMS가 발음 RMS 중앙값의 1% 이하여야 한다.
- 악보가 끝난 뒤 종료 여유 0.55초와 디코드 패딩도 같은 무음 기준으로 검사한다. 시작 쪽 경계 여유만 적용하고 파일 마지막까지 확인한다. 음표 release는 원본 NOTE 슬롯 안에서 끝나므로 이 구간에 새로운 발음이 있어서는 안 된다.
- 전체 길이는 악보 재생 시간과 기존 MIDI 종료 여유 0.55초를 합친 값과 비교한다. WAV 프레임 수는 ±2샘플, M4A 디코드 프레임은 -2~1025샘플을 허용한다. 컨테이너가 보고하는 길이는 ±3샘플이다. AAC 패딩은 디코더별로 남을 수 있으므로 별도 기록한다.

시김새, 템포 변화, 이조, 다성부, 관측 음역 100~3000Hz 밖의 음, 발음 구간 70ms 미만은 검증 불가로 실패한다. 지원하지 않는 조건을 통과로 처리하지 않는다. 이 도구는 공통 JSON 전체 스키마 검사를 대체하지 않는다.

## 재현

저장소 루트에서 재생 도구의 환경과 실제 생성 음원을 준비한다. 생성 절차는 [재생 도구 README](../../../data/src/playback/README.md)를 따른다. 외부 서비스·브라우저·마이크는 사용하지 않으며, 실행한 FFmpeg·FFprobe 프로세스는 명령 종료와 함께 끝난다. 원본과 음원을 수정하지 않는다. `data/processed/`의 JSON 보고서와 음원은 Git에 넣지 않는다.

```powershell
.\data\.venv\Scripts\python -B -m unittest data.src.playback.tests.test_verify_common -v

.\data\.venv\Scripts\python -B -m data.src.playback.verify_common --score data/tests/fixtures/scores/ngc-standard-2022-p173.score.json --bpm 96 --audio data/processed/playback-338/arirang-wav/danso.wav --out data/processed/playback-338/verify-wav.json

$ffmpegBin = 'C:/Users/kingc/AppData/Local/Eolssu/Tools/ffmpeg-release-20261029/unpacked/ffmpeg-9.0.2-essentials_build/bin'
.\data\.venv\Scripts\python -B -m data.src.playback.verify_common --score data/tests/fixtures/scores/ngc-standard-2022-p173.score.json --bpm 96 --audio data/processed/playback-338/arirang-m4a/danso.m4a --ffmpeg "$ffmpegBin/ffmpeg.exe" --ffprobe "$ffmpegBin/ffprobe.exe" --out data/processed/playback-338/verify-m4a.json
```

`$ffmpegBin`은 해당 컴퓨터의 실행 파일 위치로 바꾼다. M4A는 명시한 FFprobe로 44.1kHz·mono·AAC-LC인지 확인한 뒤, FFmpeg로 리샘플링 없이 float PCM을 추출한다. CLI 종료 코드 0과 JSON의 `passed: true`가 모두 있어야 통과다. 보고서에는 원본·음원·관측기 코드의 SHA-256, BPM, 음표별 기대·관측값, 모든 실패 항목을 남긴다.

## 실제 실행 결과

2026-11-01 실행 환경은 Python 3.10, NumPy 2.2.6, SoundFile 0.14, FFmpeg/FFprobe 9.0.2이다. 검증기 자체 대조 테스트 9개가 통과했다. 대조 파형은 렌더러 없이 직접 만든 사인파이며, 한 반음 오류·쉼에 소리 삽입·파일 절단·발음 지연·같은 음 재발음 누락·미지원 시김새를 각각 검출한다.

독립 리뷰에서 초기 검증기 `371944e`가 종료 여유 구간의 880Hz 발음과 1초 NOTE 내부의 총 480ms 단절을 놓치는 반례를 확인했다. 두 반례를 WAV·AAC 정책 각각에서 실패하는 테스트로 먼저 고정한 뒤, tail 무음 및 연속 RMS 검사를 추가했다. 수정 후 대조 테스트와 아래 실제 두 음원을 모두 다시 실행해 통과했다. 기존 음정·무음 기준을 완화하지 않았다.

원본 `ngc-standard-2022-p173.score.json`에는 NOTE 61개, REST 4개, 숨표 4개가 있다. 144틱 ÷ 3틱/박 ÷ 96박/분 × 60 = 명목 30초이며, MIDI 템포 반올림 후 30.000024초, 종료 여유를 포함해 30.550024초가 기대값이다.

| 관측 | WAV | M4A |
| --- | --- | --- |
| 결과 / 실패 | 통과 / 없음 | 통과 / 없음 |
| 디코드 프레임 / 시간 | 1,347,256 / 30.550022676초 | 1,347,256 / 30.550022676초 |
| 예상 대비 패딩 | 0샘플 | 0샘플 |
| 음표별 중앙 음정 오차의 최대 절댓값 | 17.3334cent | 17.3378cent |
| 쉼·숨표 내부 RMS / 발음 RMS 최댓값 | 0 | 0.000005124 |
| NOTE 시작 에너지 / 발음 RMS 최댓값 | 0.02587 | 0.02570 |
| 발음 에너지 시작의 최대 지연 | 9.98ms | 9.98ms |
| 발음 에너지 끝의 최대 선행 | 1.61ms | 1.61ms |
| 내부 연속성 검사 창 / 단절 검출 | 2,461 / 0 | 2,461 / 0 |
| 내부 창 RMS / 발음 RMS 최솟값 | 0.14864 | 0.14816 |
| 종료 여유 구간 RMS | 0 | 0 |

원본 SHA-256: `51cd874652d705511cfaa0f2c03ba6d3632ae10b4a483036d6a438a8eac73f1b`.

음원 SHA-256:

- WAV: `86fb6234e7210cbe955daaf3bbf6b2254a83d138e6a3aa6e4d17272664c1ad86`
- M4A: `b61b800934077e1e505c04d67eccc6ab8439df7a554362cb4b1a5ab9b9619f50`

에너지 골은 재발음 경계를 관측하는 제한된 신호이며 사람의 혀 동작이나 음색의 자연스러움을 증명하지 않는다. CAT 자산·렌더러·템포/숨표 정책·인코더·원본 fixture가 바뀌면 새 음원으로 재실행한다. 새 입력이 현재 관측 범위를 벗어나면 기준을 완화해 통과시키지 말고 별도 검증 방법과 범위를 정한다.

## 2026-11-02 아리랑 자료 재대조와 대상 정정

사용자의 멜로디·음높이·리듬 지적을 위 아리랑 M4A에 대한 것으로 잘못 이해해 `4d15a52` 기준 아래 조사를 수행했다. 이후 사용자가 지정한 실제 문제 파일은 `preview/danso-m4a/danso.m4a`(SHA `9fb7f0de…`)였다. 이는 별도의 인공 시김새 검증곡이다. **아래 아리랑 결과는 사용자 지적 음원을 검증한 결과가 아니며, 그 파일의 원인 설명에 사용하지 않는다.** [정확한 대상과 검증 한계](common-score-expression-preview.md#2026-11-02-청취-지적-대상-확인)를 별도 기록했다. 원본과 생성 음원을 보존했으며 제품 동작을 바꾸거나 수정본을 만들지 않았다.

### 원보와 이전 생성본 대조

- [국립국악원 교과서 표준악보집](https://www.gugak.go.kr/userfiles/files/textbook_final.pdf)의 PDF 173쪽(인쇄 344–345쪽)을 직접 읽어 별도 전사한 48정간·65이벤트와 공통 fixture를 대조했다. 율명·옥타브·시작 박·길이 차이는 없었다. PDF 157쪽의 단소 기보 설명과 해당 곡의 세마치 범례는 1정간=1박 해석을 뒷받침한다.
- 원보에는 수치 BPM이 없다. 이번 96 BPM은 `data/reference/jangdans.json`의 세마치 서비스 기본값이다. 특정 실연과 같은 속도를 검증한 결과가 아니다.
- 10월 29일 앱 검수용 JGB 생성본과 현재 공통 경로의 65이벤트를 MIDI 음높이·누적 시작초·명목 길이초로 비교한 결과 차이가 없었다. 이전 파일도 전문가가 승인한 기준 연주라는 증거는 없다.
- 이전 JGB의 quarter BPM 48/24 quarter와 현재 공통 경로의 quarter BPM 144/72 quarter는 모두 30초다. BPM 숫자만의 차이를 3배 속도 오류로 해석하면 안 된다.
- 이전 M4A SHA-256은 `4942c7c9c1e58fe58821c39667f8afa9fb724e4b1d4d7393d7cd9aa3de01ab9e`다. 이번 전달 파일은 위 `b61b8009…`이며, 시김새 확장 검증곡과 다른 파일이다. 아리랑 원본의 시김새 배열은 비어 있다.

### 실제 파일에서 확인한 범위

- WAV/AAC를 각각 디코딩한 결과 길이 1,347,256프레임@44.1kHz, 최고 상관 시차 0프레임, 상관계수 0.999996305, SNR 51.24dB다. AAC 인코딩 때문에 속도나 음높이가 달라졌다는 증거는 없다.
- 독립 `mido` 파싱에서 61개 NOTE의 음높이·순서가 원본과 일치했다. 시작 시각의 명목값 오차 최대 22.5µs는 정수 MIDI 템포 반올림 범위였다.
- 실제 WAV/AAC 각각 948개의 40ms 창을 20ms 간격으로 측정했다. 창 시작점을 음 시작+60ms부터 note-off−50ms 미만에 배치했다. 따라서 창 끝은 note-off 약10ms 전까지 도달할 수 있다. 유효하지 않은 창은 없었고 목표 대비 최대 오차는 각각 24.281/24.292cent였다. 기존 자동 비브라토 ±25cent가 이 범위에 포함된다.
- 사용된 6개 CAT 샘플의 RootKey·해시·44.1kHz 샘플레이트를 확인했다. 모두 샘플 기본 재생 비율 1, 샘플 detune 0, 시작 cursor 0, sustain loop 없음이다. 별도 조율 보정·비브라토 pitch bend는 적용된다. 샘플을 잘못 대응하거나 한 옥타브 이조했다는 증거는 없다.

### 확인된 표현 차이와 미확인 사항

이전 생성본은 일반 음의 명목 끝 뒤로 release 50ms가 이어졌고, 이번 공통 경로는 그 감쇠를 명목 음 길이 안에 포함한다. 따라서 일반 음 57개의 note-off가 약 50ms 앞당겨졌고 시작 시점은 유지된다. 음 연결 느낌은 달라질 수 있으나, 이것이 사용자 지적의 원인이라고 확인된 것은 아니다.

긴 단소 음에는 원보의 시김새 표기와 별개로 자동 비브라토가 들어간다. ±50cent 통과 기준은 이를 허용하므로 기보된 표현만 충실하게 재생했다는 증명도 아니다. 샘플마다 어택과 배음 세기가 다르며, 위 F0 조사는 첫 60ms의 과도음이나 자연스러운 장단·호흡·음색을 평가하지 않는다. 사용자 플레이어의 실제 속도 설정도 관측하지 않았다.

사용자는 10월 29일 아리랑 파일은 괜찮다고 확인했고, 이어 실제 문제 파일의 경로를 명시했다. 따라서 아리랑 경로의 추가 원인 조사를 중단했다. 비교용 파일은 이름만 구분해 복사하고 원본과 SHA-256 일치를 확인했다. 이 조사에서 음열·조율·BPM을 바꾸지 않았다.

원보 수동 대조 증거는 로컬 `common-score-preview-audit` worktree의 `data/processed/arirang-source-audit/`에 있다. `manual_original_comparison.py`와 `manual-original-comparison.json`을 보존했으며 원문 PDF·이미지·음원은 Git에 포함하지 않는다.

실제 PCM/MIDI/샘플 검사 스크립트와 결과는 로컬 `common-score-preview-verify` worktree의 `data/processed/arirang-diagnosis/diagnose_arirang.py`, `arirang-2026-11-02.json`에 보존했다. 스크립트 인자는 `--root <현재 worktree> --old-bundle <10월29일 p173 출력 폴더> --ffmpeg <실행 파일> --output <결과 JSON>`이다. 결과 JSON의 SHA-256은 `93e17ce7905fd7a450a42b25b17e758d0b7439a2f3dbb79e434f0b0f846d7648`이다.
