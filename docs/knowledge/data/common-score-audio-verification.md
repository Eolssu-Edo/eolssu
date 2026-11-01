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
- 원본 REST 및 숨표의 양 끝을 제외한 내부 구간을 검사한다. WAV는 경계 2ms를 제외하고 PCM이 정확히 0이어야 한다. M4A는 44.1kHz AAC 한 프레임(1024샘플)보다 조금 큰 25ms를 제외하고 무음 RMS가 발음 RMS 중앙값의 1% 이하여야 한다.
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

2026-11-01 실행 환경은 Python 3.10, NumPy 2.2.6, SoundFile 0.14, FFmpeg/FFprobe 9.0.2이다. 검증기 자체 대조 테스트 7개가 통과했다. 대조 파형은 렌더러 없이 직접 만든 사인파이며, 한 반음 오류·쉼에 소리 삽입·파일 절단·발음 지연·같은 음 재발음 누락·미지원 시김새를 각각 검출한다.

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

원본 SHA-256: `51cd874652d705511cfaa0f2c03ba6d3632ae10b4a483036d6a438a8eac73f1b`.

음원 SHA-256:

- WAV: `86fb6234e7210cbe955daaf3bbf6b2254a83d138e6a3aa6e4d17272664c1ad86`
- M4A: `b61b800934077e1e505c04d67eccc6ab8439df7a554362cb4b1a5ab9b9619f50`

에너지 골은 재발음 경계를 관측하는 제한된 신호이며 사람의 혀 동작이나 음색의 자연스러움을 증명하지 않는다. CAT 자산·렌더러·템포/숨표 정책·인코더·원본 fixture가 바뀌면 새 음원으로 재실행한다. 새 입력이 현재 관측 범위를 벗어나면 기준을 완화해 통과시키지 말고 별도 검증 방법과 범위를 정한다.
