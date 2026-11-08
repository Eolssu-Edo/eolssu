# Android 녹음 시작 직후 밀린 입력과 시각 기준

- Task: S15P31E102-559 (원래 검사는 [S15P31E102-515](../../plans/S15P31E102-515.md)에서 추가)
- 환경: Windows, Android API 37 Medium Phone 에뮬레이터(1080×2400), 호스트 마이크 켬(`adb emu avd hostmicon`), 디버그 APK, 구아리랑.

## 확인한 동작

`AndroidAudioRecorder`는 읽기마다 `AudioRecord.getTimestamp`의 `framePosition`과 앱이 읽은 프레임 수를 비교한다. 위치가 `읽은 양 + bufferSizeInFrames`를 넘으면 `Microphone input overrun`으로 녹음을 실패 처리했다. 그래서 평가 연주 시작 직후 '연주가 중단되었어요'가 떴다.

임시 로그로 잰 값(44.1kHz, 읽기 882프레임, 버퍼 3,528프레임):

| 시점 | framePosition | 읽은 양 | 차이 |
| --- | --- | --- | --- |
| 첫 읽기 | 14,790 | 882 | 13,908 |
| 약 0.13초 뒤 | 20,637 | 20,286 | 351 |
| 이후 10초 | — | — | 114~730 |

시작 시점의 차이는 장치가 먼저 잡아 둔 프레임이다(같은 때 HAL 로그에 `producerThread was late delivering frames`). 끊지 않고 읽으면 이 프레임도 모두 읽혀 차이가 버퍼 안으로 줄었다. 따라서 프레임 유실이 아니라 시작 직후 밀린 입력이었다.

## 적용한 판단

위치가 읽은 양과 맞지 않아도 녹음은 계속한다. 맞지 않는 동안에는 `CaptureBoundary.timestampValid`와 `PitchReading.analysisAvailable`을 false로 두어 실시간 음높이 표시만 쉰다. 다시 맞으면 시각 기준을 갱신한다. 녹음 파일과 서버 채점은 영향을 받지 않는다.

에뮬레이터에서 0:30 곡의 평가 녹음이 끝까지 진행되어 '연주를 마쳤어요'가 떴다. 실기기의 시작 직후 차이와 실시간 음높이 표시 시점은 확인하지 않았다.
