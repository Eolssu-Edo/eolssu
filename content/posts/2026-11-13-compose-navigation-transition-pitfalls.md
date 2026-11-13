---
title: "확인 창이 0.7초 늦게 닫히던 이유"
date: 2026-11-13T00:00:00+09:00
slug: "compose-navigation-transition-pitfalls"
author: "송가연"
categories: ["frontend"]
tags: ["Compose Multiplatform", "Navigation", "Android", "뒤로 가기"]
description: "Navigation Compose의 기본 전환이 확인 창과 제스처 뒤로 가기에 남긴 흔적을 찾고, 진입 직후 동작과 두 번 눌러 종료를 화면 이동 흐름 안에서 정리했습니다."
draft: true
thumbnail: "https://team-edo.github.io/blog-images/2026-11-compose-navigation-transition-pitfalls.webp"
---

연주를 중단하고 확인 창에서 "홈으로 가기"를 누르면 홈 화면은 바로 나타나는데, 확인 창만 잠깐 남아 있다가 사라졌습니다. 버튼 처리 코드가 느린 것처럼 보였지만 원인은 화면 이동 애니메이션이었습니다.

얼쑤 앱의 화면 이동은 navigation-compose 2.10의 `NavHost`로 구성합니다. 이 글은 화면 이동 흐름에서 겪은 네 가지 문제를 정리합니다.

## 나가는 화면은 전환이 끝날 때까지 남는다

`NavHost`에 전환을 따로 지정하지 않으면 0.7초 동안 페이드인·페이드아웃하는 기본 전환을 씁니다. 이동은 바로 시작되지만, 전환이 끝날 때까지 나가는 화면도 계속 그려집니다. 그 화면 안에서 띄운 Dialog도 함께 남습니다.

Dialog는 별도 창으로 맨 위에 그려지므로 뒤 화면이 흐려지는 동안에도 선명하게 보입니다. 그래서 "창이 늦게 닫힌다"처럼 보였습니다. 당시 "홈으로 가기"는 재생을 멈추고 홈으로 이동만 했고, 창 상태를 따로 닫지 않았습니다. 이 증상이 화면을 옮기는 모든 창에서 나타나는지 먼저 확인했고, 버튼 하나의 문제가 아니라 공통 전환의 문제임을 알 수 있었습니다.

`NavHost`의 전환을 `EnterTransition.None`과 `ExitTransition.None`으로 두자 이전 화면과 확인 창이 함께 즉시 사라졌습니다.

## 제스처 뒤로 가기는 다른 전환 인자를 쓴다

처음에는 enter, exit, popEnter, popExit 네 가지만 껐습니다. 뒤로 가기 버튼은 바로 바뀌었지만, 실기기 영상에서는 화면 가장자리를 밀어 뒤로 갈 때 나가는 화면이 여전히 줄어들었습니다.

navigation-compose 2.10의 `NavHost`에는 Android 제스처 뒤로 가기(predictive back)용 인자 `predictivePopEnterTransition`·`predictivePopExitTransition`이 따로 있습니다. Android 기본값은 플랫폼별 구현에 있고, 나가는 화면을 `scaleOut(targetScale = 0.7f)`으로 줄입니다. 버튼은 popExit를, 제스처는 predictive 전환을 쓰므로 증상이 뒤로 가는 방법에 따라 달라 무작위처럼 보였습니다.

```kotlin
NavHost(
    navController = navController,
    startDestination = start,
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None },
    predictivePopEnterTransition = { EnterTransition.None },
    predictivePopExitTransition = { ExitTransition.None },
) { /* 화면들 */ }
```

여섯 인자를 모두 지정한 뒤 제스처 뒤로 가기에서도 줄어드는 화면이 사라졌습니다. 줄어드는 화면 뒤로 이전 앱 화면이 보이면 앱 내부 전환이고, 홈 화면이 보이면 시스템의 앱 종료 애니메이션이므로 둘을 구분해서 봤습니다. 전환을 전체적으로 끄면 앱의 화면 이동 모션이 모두 사라집니다. 효과가 필요한 화면이 생기면 그 화면의 `composable`에만 전환을 지정할 계획입니다.

## 들어오자마자 하는 동작은 이동 인자로 요청하고 한 번만 실행한다

악보 추가 화면에서 '사진·PDF 선택하기'를 누르면, 다음 화면인 악보 올리기 화면에 들어가자마자 파일 선택 창이 열려야 합니다. 앞 화면이 다음 화면의 동작까지 요청하는 경우입니다.

선택 인자 경로(`...?pick={pick}`)로 "바로 고르기" 입장을 넘겼습니다. 같은 화면을 평소 입장과 바로 동작하는 입장으로 나눠 쓸 수 있습니다. 하지만 `LaunchedEffect(Unit)`만으로는 부족합니다. 다음 화면에 갔다 돌아오거나 화면을 회전하면 효과가 다시 실행돼, 파일을 고르고 돌아왔을 때 선택 창이 또 열립니다.

| 동작 | 한 번만 실행하는 방법 |
| --- | --- |
| 파일 선택 창 열기(UI 동작) | `rememberSaveable` 플래그. `pickOnStart && !autoPicked`일 때만 엽니다 |
| 인식 실패 안내 띄우기(화면 상태) | ViewModel의 처리 여부 플래그. 닫은 안내가 다시 뜨지 않습니다 |

플래그는 동작보다 먼저 켰습니다. 동작이 화면을 떠나게 하면 뒤에 켠 플래그가 저장되기 전에 화면이 사라질 수 있습니다. 이동 인자는 문자열이라 `"null"` 같은 값이 들어올 수 있으므로 받는 쪽에서 모르는 값은 "없음"으로 바꿨습니다. 자동으로 선택 창을 여는 동작은 Android와 웹에서 확인했고, iOS에서는 확인하지 않았습니다.

## 두 번 눌러 종료는 홈이 맨 아래일 때만 켠다

홈에서 뒤로 가기를 한 번 누르면 안내를 띄우고, 2초 안에 한 번 더 누르면 앱을 닫습니다. 공통 코드와 플랫폼 코드를 이렇게 나눴습니다.

- Compose의 `BackHandler`는 Android에서 버튼과 제스처 뒤로 가기를 모두 받으므로 공통 코드에 둡니다.
- 앱 종료만 expect/actual로 나눴습니다. Android는 `LocalActivity.current?.finish()`, iOS와 웹은 null을 돌려주고, null이면 핸들러를 끕니다.
- `navController.previousBackStackEntry == null`일 때만 켭니다. 홈 위에 다른 화면이 쌓여 있으면 원래 뒤로 가기가 동작해야 하기 때문입니다.
- 2초 판정은 시각을 인자로 받는 작은 클래스(`press(nowMillis)`)로 분리해 Compose 없이 테스트했습니다.

안내는 `withTimeoutOrNull(2000) { showSnackbar(..., Indefinite) }`로 띄웠습니다. 스낵바 기본 길이(Short)는 2초보다 길어서, 판정 시간이 끝난 뒤에도 안내가 남기 때문입니다. 실기기에서 한 번 누르면 안내만 뜨고, 2초 안에 두 번 누르면 런처로 나가는 것을 확인했습니다.

## 정리

화면 이동의 문제는 버튼 처리 코드보다 이동 사이의 시간에서 생기는 경우가 많았습니다. 나가는 화면이 얼마나 오래 남는지, 다시 그려질 때 효과가 다시 실행되는지, 지금이 back stack의 어느 위치인지를 먼저 확인하면 원인을 빨리 좁힐 수 있었습니다.
