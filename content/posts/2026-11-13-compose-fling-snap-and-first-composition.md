---
title: "조금 밀었을 뿐인데 홈 화면이 중간으로 순간이동했다"
date: 2026-11-13T00:00:00+09:00
slug: "compose-fling-snap-and-first-composition"
author: "권영민"
categories: ["frontend"]
tags: ["Compose Multiplatform", "스크롤", "FlingBehavior", "애니메이션", "Android"]
description: "홈 첫 화면 스크롤이 중간에서 멈췄다가 다시 움직이던 원인을 관성과 스냅이 따로 움직인 것과 스크롤 도중 처음 구성되는 카드 두 가지로 나눠 찾고, 한 번의 움직임으로 고친 과정입니다."
draft: false
thumbnail: "https://team-edo.github.io/blog-images/2026-11-compose-fling-snap-and-first-composition.webp"
---

얼쑤의 홈 첫 화면은 일반적인 목록이 아닙니다. 위로 400dp를 미는 동안 그림이 작아지고 제목과 시작 버튼이 올라가며, 다 펼치면 그 아래에 기능 카드가 나타납니다. 이 400dp 구간을 코드에서는 무대라고 부릅니다. 무대 중간에 멈춰 있지 않도록 손을 떼면 닫힌 쪽(0dp)이나 펼친 쪽(400dp)으로 맞춰 주는 스냅이 있습니다.

```kotlin
internal fun homeSnapTarget(scroll: Float, wasOpen: Boolean): Float = when {
    scroll >= HomeScrollRange -> scroll
    scroll <= 0f -> 0f
    scroll > HomeScrollRange * if (wasOpen) .85f else .12f -> HomeScrollRange
    else -> 0f
}
```

닫힌 상태에서는 12%(48dp)만 넘겨도 펼치고, 펼친 상태에서는 85% 아래로 내려와야 닫힙니다. 문제는 첫 화면에서 살짝 밀었을 때였습니다. 화면이 손가락을 따라오다가 중간 과정을 건너뛰고 무대 중간으로 순간이동한 뒤, 다시 끝까지 움직였습니다. 목표는 손가락의 움직임에서 스냅까지 하나로 이어지게 만드는 것이었습니다.

구현과 에뮬레이터 확인은 AI 코딩 에이전트와 함께 했고, 저는 원인 후보 중 무엇을 먼저 볼지 정하고 에이전트가 보고한 결과를 검토했습니다.

## 스크롤 값을 로그로 찍어 보기

눈으로는 "순간이동"으로만 보여서 값을 직접 봤습니다. Android 에뮬레이터(Medium Phone, API 37)에 임시 로그를 넣어 스크롤 값이 바뀔 때마다 dp 값과 상태(끄는 중인지, 스크롤 중인지, 스냅 중인지)를 출력했습니다. 입력은 `adb shell input swipe`로 넣었습니다.

빠르게 짧게 튕긴 경우의 로그를 줄이면 이렇습니다.

| 시각(초) | 스크롤 값 | 상태 |
| --- | --- | --- |
| 56.263 | 37.7dp | 손을 뗌 |
| 56.294 ~ 56.775 | 52 → 205dp | 관성으로 미끄러지며 점점 느려짐 |
| 56.810 | 205.0dp | 관성 끝, 스냅 시작 |
| 56.844 ~ 56.895 | 205.3 → 207.2 → 209.9 → 214.9dp | 스냅이 거의 멈춘 상태에서 다시 출발 |

느리게 64dp쯤 밀고 놓은 경우에는 관성이 거의 없어 스냅이 바로 시작됐습니다. 그런데 스냅 도중 로그 하나가 비었습니다. 217.5dp 다음 값이 141ms 뒤 270.1dp였고, 그다음 값은 379.4dp였습니다. 다른 구간의 간격은 대부분 15~20ms였습니다. 같은 동작을 다시 해도 그 근처에서 간격이 60ms 넘게 벌어졌습니다.

정리하면 몇 차례 반복에서 한 프레임이 60~140ms 걸렸고, 그사이 스크롤 값이 50~110dp씩 건너뛰었습니다. 이 시간은 프레임 측정 도구가 아니라 로그 출력 시각의 간격으로 본 값입니다.

## 원인 1: 관성과 스냅이 따로 움직였다

첫 번째 로그가 "멈췄다가 다시 움직이는" 모습 그대로였습니다. 기존 구조는 이랬습니다.

1. 손을 떼면 `verticalScroll`의 기본 fling이 감쇠 곡선대로 미끄러집니다. 이 fling은 스냅 목표를 모릅니다.
2. 별도 `LaunchedEffect`가 `isScrollInProgress`를 지켜보다가 스크롤이 멈추면 `animateScrollTo(target, tween(500))`로 스냅합니다.

그래서 38dp에서 튕기면 관성이 205dp에서 멈추고, 스냅이 속도 0에서 400dp까지 다시 출발했습니다. 관성 구간이 빠르게 지나가니 사용자 눈에는 중간으로 순간이동한 것처럼 보였습니다.

## 원인 2: 진행률 0.6에서 카드를 처음 구성했다

두 번째 로그에서 비는 지점은 약 240dp, 무대 진행률 0.6 근처였습니다. 레이아웃 코드에서 그 숫자를 찾으니 `val openFeatures = frame.progress > .6f`가 있었고, 기능 카드 영역이 `if (openFeatures) { FeatureSection(...) }` 안에 있었습니다. 조건이 처음 참이 되는 프레임에서 카드 묶음과 그 안의 그림이 한꺼번에 구성·측정됩니다. 그 프레임이 길어져도 애니메이션 시간은 흐르므로, 다음 프레임의 스크롤 값은 그만큼 앞으로 건너뜁니다.

## 고친 방법 1: 착지점을 먼저 계산하고 속도를 이어받기

`FlingBehavior`를 직접 만들었습니다. 손을 뗄 때 관성이 어디서 멈출지를 먼저 계산하고, 그 착지점으로 스냅 목표를 정한 다음, 손가락 속도를 이어받는 스프링 하나로 목표까지 갑니다.

```kotlin
val start = position()
val landing = decay.calculateTargetValue(start, initialVelocity)
val target = homeFlingTarget(landing / pxPerDp, wasOpen())?.let { (it * pxPerDp).roundToInt().toFloat() }
    ?: return with(free) { performFling(initialVelocity) }
val distance = target - start
// … 거리 0·동작 줄이기 처리 생략
var applied = 0f
AnimationState(0f, initialVelocity).animateTo(distance, HomeSnapSpring) {
    // 손을 뗀 속도로 목표를 지나치면 되튀지 않고 목표에서 멈춘다.
    val next = if (distance > 0f) value.coerceAtMost(distance) else value.coerceAtLeast(distance)
    val delta = next - applied
    val consumed = scrollBy(delta)
    applied += consumed
    if (next == distance || abs(delta - consumed) > .5f) cancelAnimation()
}
```

손을 뗀 위치가 아니라 착지점으로 목표를 정한 이유는 손의 의도를 따르기 위해서입니다. 같은 30dp에서 놓아도 세게 튕기면 펼치고, 살살 놓으면 돌아가야 자연스럽습니다. `AnimationState`에 `initialVelocity`를 넘기지 않으면 출발 순간 속도가 0으로 끊겨 다시 멈칫합니다.

나머지 경계는 이렇게 정했습니다.

- 착지점이 400dp 이상이면 `homeFlingTarget`이 null을 돌려주고, 기본 fling(`ScrollableDefaults.flingBehavior()`)이 그대로 카드 구간까지 미끄러집니다.
- 스프링은 튀지 않는 감쇠(`DampingRatioNoBouncy`)와 낮은 강성(`StiffnessLow`)을 썼습니다. 목표를 넘으면 그 자리에서 멈춥니다.
- 시스템의 동작 줄이기 설정이 켜져 있으면 애니메이션 없이 한 번에 옮깁니다.

기존 "스크롤이 멈추면 스냅" 로직에서는 손가락으로 끈 경우를 뺐습니다. 손을 뗀 뒤 fling이 시작되기 전의 짧은 틈에 그 로직이 끼어들면 다시 두 번 움직이기 때문입니다.

```kotlin
if (dragGesture) {
    dragGesture = false
    return@collectLatest
}
// 휠·키보드처럼 관성이 없는 스크롤만 이벤트 사이를 기다린 뒤 가까운 쪽으로 맞춘다.
delay(160)
```

## 고친 방법 2: 카드를 미리 구성하고 숨겨 두기

카드는 처음부터 구성해 두고, 진행률 0.6 전에는 보이지 않게만 했습니다.

```kotlin
FeatureSection(onFeatureClick,
    columns = HomeFeatureColumns, cardMinHeight = 132.dp,
    large = medium || expanded,
    enabled = openFeatures,
    modifier = Modifier.offset(
        x = (if (expanded) contentLeft + finalHeroW + 40f else contentLeft).dp,
        y = featureTop.dp,
    ).width((if (expanded) contentW - finalHeroW - 40f else contentW).dp)
        .onSizeChanged { featureH = it.height / density.density }
        .then(if (openFeatures) Modifier else Modifier.clearAndSetSemantics {})
        .graphicsLayer { alpha = featureAlpha },
)
```

alpha만 0으로 두면 보이지 않는 카드가 여전히 눌리고 TalkBack에도 읽힙니다. 그래서 `FeatureSection`에 `enabled` 인자를 추가해 `clickable(enabled = false)`로 입력을 막고, `clearAndSetSemantics {}`로 화면 읽기에서도 뺐습니다. 대신 홈에 처음 들어올 때 카드 구성 비용이 앞당겨집니다. 이번에는 그 비용을 재지 않았습니다.

## 단위 테스트로 움직임 고정하기

움직임을 테스트하려고 가짜 `ScrollScope`와 16ms마다 프레임을 넘기는 `MonotonicFrameClock`을 만들고, `scrollBy`가 받은 값을 프레임별로 기록했습니다. 화면 밀도는 에뮬레이터와 같은 2.625로 두었습니다. 앱에서 쓰는 `rememberSplineBasedDecay`는 Android 호스트 단위 테스트에서 쓸 수 없어서, 테스트에서는 `exponentialDecay`로 감쇠를 대신했습니다.

| 테스트 | 확인하는 것 |
| --- | --- |
| 착지점으로 목표 정하기 | 착지 205dp면 펼침, 400dp 이상이면 null(기본 관성) |
| 느린 64dp 슬라이드 | 400dp까지 한 방향으로 가고, 첫 프레임 10dp 미만, 한 프레임 40dp 미만 |
| 38dp에서 1500dp/s 튕김 | 관성만으론 중간에 멈추는 속도인데 400dp에서 끝나고, 첫 프레임이 15dp 넘게 이어받음, 목표를 넘지 않음 |
| 30dp 슬라이드 | 첫 화면으로 돌아감 |
| 펼친 상태에서 아래로 튕김 | 0dp로 닫힘 |
| 6000dp/s로 강하게 튕김 | 기본 관성에 그대로 넘김 |
| 동작 줄이기 | 한 번에 목표로 이동 |

처음에는 느린 슬라이드의 첫 프레임 기준을 5dp로 잡았는데, 실제로는 7.4dp가 나와 실패했습니다. 속도 0에서 출발한 스프링의 첫 프레임이고, 직전에 느리게 밀던 손가락 이동(프레임당 약 4.5dp)과 크게 다르지 않아 결함이 아니라고 판단했습니다. 기준은 10dp로 바꿨습니다.

## 확인한 결과

수정한 빌드에서 같은 방식으로 로그를 찍었습니다. 65.5dp에서 952dp/s로 손을 뗐고, 관성만이라면 244dp에서 멈출 움직임이었습니다. 착지점이 12%를 넘으므로 목표는 400dp가 됐고, 중간에 멈추지 않고 400dp까지 이어졌습니다. 이 한 번의 로그에서 값 사이 간격은 가장 길어도 42ms였고, 0.6 지점 근처는 39ms였습니다. 수정 전처럼 100ms를 넘는 간격은 없었고, 화면에서도 그 지점의 끊김이 보이지 않았습니다.

다른 동작도 확인했습니다.

- 30dp 미만으로 느리게 밀면 첫 화면으로 돌아오고, 64dp쯤 밀면 400dp로 펼쳐졌습니다.
- 펼친 뒤 더 밀면 카드 구간(558.9dp)에 그대로 머물렀고, 세게 내리면 0dp로 닫혔습니다.
- 펼친 상태에서 30dp쯤 살짝 내리면 400dp로 되돌아왔습니다.
- 아래 화살표를 누르면 끝까지 펼쳐졌고, 펼친 뒤 카드를 누르면 해당 화면으로 이동했습니다.

공유 모듈 Android 호스트 테스트 1,609개가 통과했고, Android 디버그 빌드와 Wasm·JS 컴파일도 성공했습니다.

한계도 분명합니다. 확인은 에뮬레이터와 `adb` 스와이프로만 했고, 실기기 손가락 입력, iOS, 웹 터치는 확인하지 않았습니다. 수정 전후 비교도 각 동작을 몇 번 반복한 로그를 본 것이지 통계를 낸 측정이 아닙니다.

## 남은 과제

- **스냅 속도감.** 새 스프링이 예전 500ms 스냅과 비슷하게 느껴지는지는 실기기에서 봐야 합니다.
- **다른 플랫폼.** 공통 코드라 iOS·웹에도 같은 동작이 적용되지만 직접 밀어 보지 않았습니다.
- **미리 구성하는 비용.** 카드를 처음부터 구성하면서 늘어난 첫 진입 비용은 재지 않았습니다.
- **AnimatedVisibility.** 등장 시점에 처음 구성한다는 점에서 같은 문제가 남을 수 있다고 추측하지만, 측정하지는 않았습니다.
