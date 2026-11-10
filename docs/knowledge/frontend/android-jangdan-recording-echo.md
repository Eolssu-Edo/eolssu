# Android 평가 녹음의 장단 누음 실측

S15P31E102-695에서 평가 중 스피커 장단을 유지하면서 녹음 속 장단을 줄이는 방법을 비교했다. Galaxy S24+ (SM-S926N, Android 17/API 37)의 내장 스피커와 마이크를 사용했다. 임시 APK는 Android 앱과 동일한 `VOICE_RECOGNITION` 44.1kHz 모노 PCM 입력 및 `USAGE_MEDIA`/`CONTENT_TYPE_MUSIC` SoundPool 출력을 사용했다. 타격음은 170ms 감쇠 멀티톤으로 생성했다.

## 기기 에코 제거

녹음 세션에서 AEC가 활성화되었는지 API 응답으로 확인하고, 타격음과 상관이 높은 마이크 입력 성분의 크기를 비교했다. 같은 시스템 볼륨에서 인접한 AEC 끔·켬 구간을 비교했다.

| 녹음 소스 | 시스템 미디어 볼륨 | AEC 끔 | AEC 켬 | 감소량 |
| --- | --- | --- | --- | --- |
| `VOICE_RECOGNITION` | 10/15 | −32.26dBFS | −32.64dBFS | 0.38dB |
| `VOICE_RECOGNITION` | 11/15 | −31.14dBFS | −31.43dBFS | 0.29dB |
| `VOICE_COMMUNICATION` | 11/15 | −57.69dBFS | −57.79dBFS | 0.10dB |

기존 녹음 경로에서 AEC를 켰을 때 누음 감소는 반복 간 편차보다 작았다. `VOICE_COMMUNICATION`에서는 AEC를 끈 상태에서도 입력 크기가 크게 달랐다. 실제 악기 음높이, 약한 음과 시작점의 보존, 채점 점수는 이 측정에서 비교하지 않았다. 임시 APK를 제거했고 마이크 입력 파일은 저장하지 않았다.

통화용 출력과 입력을 모두 선택한 후 한 녹음 세션에서 AEC 끔·켬을 교차 비교했을 때 타격 누음은 크게 줄었다. 켠 구간에서 마이크 입력이 0이 된 구간도 관찰했다. 이 경로에서 실제 악기 소리가 보존되는지 확인되지 않아 채점에 적용하지 않았다.

## 재생 참조로 장단 성분 빼기

모의 실험은 앱 장단 자산 ‘덩’·‘쿵’·‘덕’의 M4A를 [배포 명세](../../../frontend/shared/src/commonMain/composeResources/files/jangdan/manifest.json) SHA-256과 대조하고 FFmpeg에서 44.1kHz 모노 PCM16으로 디코딩했다. 캘리브레이션의 장단만으로 지연과 스피커 응답을 추정하고, 다른 순서와 세기의 타격에 응답을 고정해 적용했다. 평가 타격에는 523.25Hz 합성음을 일부러 겹쳤다.

- 모의 입력에서 장단과 바닥 잡음의 합은 29.27dB 줄었다. 합성음의 알려진 구간에서 스펙트럼 피크는 전후 523.19Hz, RMS 문턱으로 잰 시작점은 전후 434.01ms였다.
- 스피커 응답이 그대로여도 보정하지 않은 시간 차이가 32샘플(약 0.73ms) 생기면 감소량은 0.27dB로 떨어졌다.

```powershell
# 앱의 manifest와 일치하는 세 M4A의 경로를 사용한다.
foreach ($sample in @('deong','kung','deok')) {
    ffmpeg -i "path/to/m4a/$sample.m4a" -ac 1 -ar 44100 -c:a pcm_s16le "path/to/pcm/$sample.wav"
}
python frontend/tools/probe_jangdan_echo_reference.py "path/to/pcm"
```

Galaxy에서는 8회 타격의 녹음으로 에코 파형을 만들고, 별도의 8회 타격에 적용했다. `VOICE_RECOGNITION`과 일반 미디어 출력을 유지하고, 타격마다 재생 참조와 녹음의 파형을 맞춘 뒤 고정한 에코 파형을 뺐다.

| 재생 입력 | 시험한 정렬 | 장단 누음 변화 |
| --- | --- | --- |
| 170ms 합성 타격음 | 타격별 파형 정렬 | 중앙값 14.90dB 감소, 최소 12.13dB 감소 |
| 앱의 ‘덩’ PCM | 타격별 파형 정렬 | 중앙값 9.34dB 감소. 8회 중 7회 8.60~11.92dB 감소, 1회 3.17dB 증가 |
| 앱의 ‘덩’ PCM | 고정 지연 / 녹음 타임스탬프만 사용 | 중앙값 각각 1.27dB / 0.52dB 증가 |

‘덩’ 시험은 미디어 볼륨 11/15와 SoundPool 이득 0.35에서 진행했다. 누음 지표는 재생 PCM의 앞 72.6ms를 기준으로, 약 49.9~147.4ms 지연 범위에서 가장 유사한 마이크 입력 성분을 잰 값이다. 첫 시험에서 관찰한 지연은 주로 84.54ms 또는 144.58ms였다.

반복 시험에서 캘리브레이션으로 얻은 마이크 파형에 직접 맞추면 감소 중앙값은 2.08dB였다. 재생 PCM과 일치하는 성분이 3dB 이상 줄어든 타격에만 적용하면 8타 중 3타에서 3.17~3.79dB 줄었고, 5타는 원본을 썼다. 보정을 적용한 타격 중 지표가 악화된 경우는 없었다. 반복 시험의 정렬 방식과 측정 시점이 달라 감소량 차이의 원인을 분리하지 못했다.

두 시험에서 녹음 후 디지털로 더한 523Hz 음의 크기 오차는 각각 최대 0.36dB와 0.63dB였다. 실제 악기와 장단을 같은 마이크로 녹음했을 때의 음높이·시작점·점수는 검증 범위에 포함되지 않았다.

제품 검증에서는 출력 PCM과 마이크 샘플을 같은 시간축에 맞추고, 정렬 후보가 모호하면 그 구간은 보정을 건너뛰는 방법을 비교한다. 스피커 볼륨과 기기 위치가 달라질 때도 악기 입력과 채점이 보존되는지 실기기에서 확인한다.

- [Android 세션 단위 AEC](https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler)
- [Android 녹음 전처리와 소스별 설정](https://source.android.com/docs/core/audio/implement-pre-processing)
- [WebRTC 재생 참조 및 녹음 프레임 처리 API](https://webrtc.googlesource.com/src/+/refs/heads/main/api/audio/audio_processing.h)
