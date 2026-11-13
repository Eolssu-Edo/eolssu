---
title: "멈춘 모범 연주가 다시 재생됐다: 재생 상태를 플레이어 보고에 맡기지 않기"
date: 2026-11-13T00:00:00+09:00
slug: "model-audio-state-and-media-clock"
author: "권영민"
categories: ["frontend"]
tags: ["Kotlin Multiplatform", "Android", "MediaPlayer", "미디어 재생", "Compose"]
description: "구간 끝에서 멈춘 모범 연주가 다시 재생되던 문제와 악보 진행 표시가 흔들리던 문제를, 플레이어가 늦게·띄엄띄엄 알려 주는 상태 대신 재생 세션이 직접 상태와 위치를 쥐도록 바꿔 고친 과정입니다."
draft: false
---

얼쑤의 연습 화면에는 모범 연주가 있습니다. 서버에서 받은 음원을 틀고, 정간보 위에서 지금 연주하는 칸을 칠해 따라가게 합니다. 화면과 Android `MediaPlayer` 사이에는 `ModelAudioSession`이라는 공통 코드가 있습니다. 플랫폼 플레이어가 알려 주는 상태와 위치를 받아 화면이 쓸 상태로 바꾸는 계층입니다.

이 계층에서 서로 다른 시기에 두 문제를 겪었습니다. 하나는 구간 연습에서 멈춰야 할 모범 연주가 다시 재생된 것이고, 다른 하나는 악보의 진행 표시가 위아래로 흔들린 것입니다. 원인을 따라가 보니 둘 다 같은 질문으로 모였습니다. 화면이 보여 줄 재생 상태를 플레이어의 보고에 맡겨도 되는가 하는 질문입니다.

구현은 AI 코딩 에이전트와 함께 했고, 저는 증상 재현과 방향 결정, 확인을 맡았습니다.

## 문제 1: 구간 끝에서 멈춘 모범 연주가 다시 재생됐다

구간 연습에서는 고른 구간만 모범 연주로 들을 수 있습니다. 구간 끝에 닿으면 멈추고, 다시 누르면 구간 처음부터 들려야 합니다. 그런데 끝에 닿으면 저절로 구간 처음부터 다시 재생됐습니다.

처음에는 재생 완료 콜백이나 반복 재생 상태를 의심했습니다. 둘 다 아니었습니다. 구간 끝에서 화면 쪽 코드가 하는 일은 이 두 줄이었습니다.

```kotlin
modelAudio.pause()
modelAudio.seekToTick(tick)
```

멈춘 뒤 위치를 구간 처음으로 옮깁니다. 순서만 보면 맞습니다. 문제는 `pause()`가 끝났을 때 실제로는 아무것도 멈추지 않았다는 점입니다.

Android 플레이어 구현은 `MediaPlayer` 조작을 모두 `dispatch { … }`로 감싸 별도 `HandlerThread`에 `post`합니다. `pause()`도 마찬가지라서, 호출은 바로 돌아오고 실제 멈춤과 "멈췄다"는 상태 보고는 나중에 옵니다. 바로 다음 줄의 `seekToTick`이 세션 상태를 볼 때는 아직 `Playing`입니다. 세션의 위치 이동은 재생 중이면 "그 자리부터 이어 틀기", 멈춰 있으면 "위치만 바꾸기"로 갈립니다. 그래서 재생 중 분기를 타고 구간 처음부터 음원을 다시 불러 재생했습니다.

이 증상은 구간 끝이 곡 중간일 때만 나타났습니다. 구간 끝이 곡 끝과 같으면 플레이어가 먼저 '재생 끝'을 보고해 세션이 이미 재생 중이 아니었습니다. 재생 중에 +5초로 구간 끝을 넘을 때도 같은 원인으로 다시 재생됐습니다.

### 보고를 기다리지 않고 세션이 상태를 정한다

`pause()` 뒤에 짧은 `delay`를 넣는 방법도 떠올렸지만 쓰지 않았습니다. 플레이어 스레드가 언제 처리할지는 기기와 상황마다 다르기 때문입니다. 대신 "멈추고 이 자리에 둔다"를 한 동작으로 만들고, 세션이 그 결과를 바로 기록하게 했습니다.

```kotlin
fun pauseAtTick(tick: Double) {
    val current = _state.value
    val audio = current.audio
    val song = currentSong
    if (audio == null || song == null) return pause()
    park(current, scoreClamp(song, audio, song.modelMediaPosition(audio, tick)))
}
```

실제 일은 `park`가 합니다.

```kotlin
private fun park(current: ModelAudioState, positionMs: Long) {
    ++requestId
    loadJob?.cancel()
    active = false
    player.stop()
    sourceId = null
    _state.value = current.copy(status = ModelAudioStatus.Paused, mediaPositionMs = positionMs, anchor = null, seeking = false,
        playbackAnchor = null, playbackClockReady = true)
}
```

`park`는 플레이어를 멈추게 요청하는 동시에 세션 상태를 `Paused`와 새 위치로 바로 바꿉니다. 다음 재생은 이 위치에서 새로 불러 시작합니다. 요청 번호(`requestId`)를 올리는 이유는 늦게 도착한 이전 불러오기 결과가 새 상태를 덮어쓰지 못하게 하려는 것입니다. 세션은 플레이어 보고를 받을 때 지금 음원 번호(`sourceId`)와 다르면 무시하는데, `park`에서 이 번호도 비웁니다. 그래서 뒤늦게 오는 '재생 중'이나 '멈춤' 보고가 상태를 되돌리지 않습니다. 화면 쪽 두 줄은 `pauseAtTick(tick)` 한 줄이 됐습니다.

같은 작업에서 재생 중 ±5초를 누르면 첫 재생과 똑같은 로딩 표시가 뜨던 문제도 고쳤습니다. 이동으로 다시 불러오는 상태는 `seeking` 값으로 따로 표시하고, 로딩 표시는 처음 재생을 시작할 때만 띄웁니다.

### 수정 전 코드에서 실패하는 테스트

가짜 플레이어로 ViewModel 테스트를 만들었습니다. 가짜 플레이어가 바꾼 상태도 세션의 수집 코루틴이 돌아야 세션에 반영되므로, 수정 전 코드라면 같은 순서 문제가 그대로 재현됩니다. 재생 위치를 50ms씩 보고하다 구간 끝에서 멈추면, 음원을 다시 불러오지 않았는지(불러오기 횟수 1회 유지)와 구간 처음에 `Paused`로 멈췄는지를 봅니다.

수정을 되돌린 코드에서 돌리면 두 테스트가 실패했습니다.

| 테스트 | 수정 전 결과 |
| --- | --- |
| 구간 끝에서 다시 불러오지 않고 구간 처음에 멈춤 | 불러오기 횟수 기대 1, 실제 2 |
| 재생 중 이동에는 로딩 표시 없음 | 로딩 표시가 뜸 |

수정한 코드에서는 둘 다 통과했습니다. 멈춘 뒤 옮기고 다시 들을 때는 로딩 표시가 뜨는지 보는 테스트도 함께 넣었습니다.

## 문제 2: 악보 진행 표시가 위아래로 흔들렸다

이보다 앞서 겪은 문제입니다. 연습 화면에서 악보를 재생하면 진행 막대가 매끄럽게 차오르지 않고 위아래로 요동쳤습니다. 특히 다음 음으로 넘어가는 순간 칸 아래로 튀었다가 돌아왔습니다.

원인은 두 가지였습니다.

1. **띄엄띄엄 오는 위치.** 플레이어는 위치를 50ms마다 알려 주고, 그 값이 앞뒤로 조금씩 흔들립니다. 보고값을 그대로 그리면 계단처럼 끊기거나 뒤로 튑니다.
2. **따로 바뀌는 두 값.** 진행 표시는 "몇 번째 음인가"와 "그 음 안에서 얼마나 지났는가"로 그립니다. 둘을 따로 갱신하니 음이 바뀌는 프레임에 새 음을 옛 진행 비율(거의 끝)로 그려 칸 아래로 튀었습니다.

### 기준점에서 이어 그리고, 차이는 속도로 따라잡기

보고값을 그리지 않고 세션이 자기 시계를 갖게 했습니다. 기준점(`MediaAnchor`)은 위치, 그 위치를 잰 시각, 흐르는 속도입니다. 매 프레임 "기준 위치 + 지난 시간 × 속도"로 지금 위치를 예측합니다. 새 보고가 오면 기준점을 이렇게 옮깁니다.

```kotlin
internal fun MediaAnchor?.follow(reportedMs: Double, speed: Double, now: TimeMark): MediaAnchor {
    if (this == null) return MediaAnchor(reportedMs, now, if (reportedMs == 0.0) 0.0 else speed)
    val predicted = positionNow()
    if (rate == 0.0) return MediaAnchor(maxOf(predicted, reportedMs), now, if (reportedMs > predicted) speed else 0.0)
    val drift = reportedMs - predicted
    if (drift < -ANCHOR_SNAP_MS) return MediaAnchor(predicted, now, 0.0)
    if (abs(drift) > ANCHOR_SNAP_MS) return MediaAnchor(reportedMs, now, speed)
    return MediaAnchor(predicted, now, speed * (1 + drift / ANCHOR_CATCH_UP_MS).coerceIn(0.9, 1.1))
}
```

`ANCHOR_SNAP_MS`는 250ms, `ANCHOR_CATCH_UP_MS`는 1,000ms입니다.

| 보고값과 예측의 차이 | 처리 |
| --- | --- |
| 250ms 이내 | 그리는 위치는 그대로 두고 속도만 ±10% 안에서 바꿔 약 1초에 걸쳐 따라잡음 |
| 250ms 넘게 앞섬 | 그 위치로 바로 옮김 |
| 250ms 넘게 뒤처짐 | 이동이 아니라 늦게 온 보고로 보고, 시계가 따라올 때까지 멈춤 |

처음 버전에는 기준점이 없을 때 새로 만드는 줄과 250ms 점프, 속도 보정만 있었습니다. 이후 팀원이 장단 타이밍을 고치면서 두 가지를 더했습니다. `MediaPlayer`가 디코더가 움직이기 전에 0 위치로 '재생 중'을 먼저 알리는 경우가 있어, 처음 0 보고에서는 시계를 세워 두고 실제 진행 보고를 기다립니다. 크게 뒤처진 보고에는 뒤로 점프하지 않고 멈춥니다. 사용자가 일부러 위치를 옮긴 경우에는 세션이 기준점을 먼저 지우므로 이 규칙과 섞이지 않습니다.

### 음 번호와 진행 비율은 한 번에

정간보 쪽에서는 커서 하나에 두 값을 함께 담고, 프레임마다 같이 바꿉니다.

```kotlin
@Composable
fun rememberScoreCursor(playback: ScorePlayback?): ScoreCursorState {
    val cursor = remember { ScoreCursorState(playback?.at()) }
    val latest = rememberUpdatedState(playback)
    LaunchedEffect(cursor) {
        snapshotFlow { latest.value }.collectLatest { p ->
            cursor.set(p?.at())
            if (p?.running == true) while (true) withFrameNanos { cursor.set(p.at()) }
        }
    }
    return cursor
}
```

진행 비율은 매 프레임 바뀌므로 구성 단계에서 읽지 않고 그리기 단계에서만 읽게 했습니다. 구성에서 읽으면 매 프레임 재구성이 일어납니다.

이 동작도 테스트로 고정했습니다. 실제 위치는 50ms마다 50ms씩 가지만 보고는 ±20~30ms 흔들리게 넣고, 그려지는 위치가 한 번도 뒤로 가지 않는지, 속도가 0.9~1.1배 안에 있는지, 끝 위치가 500ms ±30ms인지 봅니다. 멀리 이동하면 그 자리로 바로 가는지, 녹음 중 진행 표시에서 음이 바뀔 때 번호와 비율이 함께 바뀌는지도 확인합니다.

## 두 문제의 공통점

정리하면 두 경우 모두 플레이어의 보고를 화면 상태의 원본으로 썼던 것이 문제였습니다.

| | 플레이어가 주는 것 | 세션이 대신 쥔 것 |
| --- | --- | --- |
| 구간 끝 멈춤 | 다른 스레드에서 늦게 오는 상태 보고 | 멈춤 요청 즉시 `Paused`와 위치를 확정 |
| 진행 표시 | 50ms 간격으로 흔들리는 위치 | 기준점과 속도로 매 프레임 이어지는 위치 |

플레이어 보고는 세션이 자기 상태를 맞춰 가는 입력으로만 씁니다. 플랫폼마다 플레이어가 동기인지 비동기인지 다를 수 있어서, 이 판단은 플랫폼 코드가 아니라 공통 세션 계층에 두었습니다.

## 확인한 결과와 한계

- 구간 끝 수정: 공유 모듈 Android 호스트 테스트 1,491개가 통과했고(4개 건너뜀), Wasm 컴파일도 통과했습니다. 새 테스트 두 개는 수정 전 코드에서 실패하는 것을 확인했습니다.
- 진행 표시 수정: 위 테스트를 추가했고, Galaxy S10에서 모범 연주를 틀어 진행 막대가 튀지 않는 것을 눈으로 확인했습니다.

정량 측정은 하지 않았습니다. 실기기에서 예측 위치와 실제 소리의 차이를 잰 적은 없고, 진행 표시는 눈으로만 봤습니다. 구간 끝 멈춤과 ±5초 연속 누르기는 단위 테스트로만 확인했고 실기기·에뮬레이터·iOS·웹에서는 보지 않았습니다.

## 남은 과제

- **실기기 확인.** 구간 끝에서 멈추는지, ±5초를 연달아 눌러도 되는지 휴대폰에서 직접 봐야 합니다.
- **장단 소리를 켠 모범 연주.** 위치를 옮긴 뒤 장단이 다시 준비되는 짧은 동안 로딩 표시가 잠깐 보일 수 있습니다. 확인하지 못했고 고치지도 않았습니다.
- **iOS 플레이어.** 세션 계층에서 상태를 확정하므로 같은 규칙이 적용되지만, iOS에서 실행해 보지 않았습니다.
- **오차 측정.** 기준점 시계가 실제 소리와 얼마나 어긋나는지 수치로 재 보면 250ms·±10% 값의 근거를 더 단단히 할 수 있습니다.
