---
title: "SwiftUI로 Compose를 띄울 때 iPhone의 안전 영역은 누가 처리할까요?"
date: 2026-11-14
slug: "swiftui-compose-ios-safe-area"
categories: ["frontend"]
tags: ["iOS", "SwiftUI", "Compose Multiplatform"]
description: "얼쑤의 마이페이지를 따라가며 SwiftUI의 화면 범위와 Compose의 인셋이 만나는 지점을 살펴봅니다."
author: "오은서"
thumbnail: "https://team-edo.github.io/blog-images/2026-11-swiftui-compose-ios-safe-area.webp"
draft: false
---

얼쑤의 마이페이지는 Android와 iOS에서 같은 Compose 코드로 그립니다. iPhone에서는 화면 위쪽의 노치와 아래쪽의 홈 인디케이터를 피해야 하는데, 마이페이지 자체에는 iOS 전용 여백 코드가 없습니다. 제목과 뒤로 가기 버튼의 위치는 어디서 정해질까요? SwiftUI 진입점부터 공용 화면 틀까지 따라가 봤습니다.

## SwiftUI가 Compose에 화면을 내어 주는 방식

iOS 진입점의 `ComposeView`는 `UIViewControllerRepresentable`로 Compose의 뷰 컨트롤러를 SwiftUI에 연결합니다. 바깥의 `ContentView`는 이 뷰에 `.ignoresSafeArea()`를 붙입니다.

~~~swift
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
~~~

SwiftUI는 기본적으로 시스템의 안전 영역을 고려해 뷰를 배치합니다. 여기서는 `.ignoresSafeArea()` 덕분에 Compose를 담는 뷰가 그 영역까지 넓어집니다. 이 한 줄을 보면 Compose가 사용할 수 있는 바깥 범위를 알 수 있습니다. 제목과 버튼의 실제 배치 위치를 알려면 공용 `EolssuScaffold`를 더 따라가야 합니다. [Apple SwiftUI 문서](https://developer.apple.com/documentation/swiftui/view/ignoressafearea%28_%3Aedges%3A%29)

## 상단 바와 하단 버튼은 각자의 여백을 갖습니다

`EolssuScaffold`는 여백이 필요한 요소를 기준으로 인셋을 나눠 적용합니다.

| 방향 | 인셋 | 적용 위치 |
| --- | --- | --- |
| 위 | `WindowInsets.statusBars` | 상단 바를 감싸는 `Box` |
| 아래 | `WindowInsets.navigationBars` | 하단 고정 콘텐츠가 있으면 버튼을 담는 영역, 없으면 빈 `Spacer` |
| 좌우 | `WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)` | `Scaffold` 바깥 틀 |

예를 들어 하단 고정 버튼이 없는 화면에서 코드는 `bottomBar` 전체를 생략하는 대신 다음과 같이 빈 공간을 둡니다.

~~~kotlin
bottomBar = {
    if (bottomBar != null) {
        EolssuBottomBar(maxWidth = contentMaxWidth, content = bottomBar)
    } else {
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}
~~~

이 `Spacer`도 하단 슬롯의 높이를 차지합니다. 그래서 버튼이 없어도 `Scaffold`가 본문에 넘기는 여백에 하단 인셋이 반영됩니다. 버튼이 있는 경우에는 `EolssuBottomBar`가 버튼을 담는 영역에 직접 인셋을 적용합니다.

## 본문은 상단과 하단 사이에 놓입니다

공용 틀은 `Scaffold`의 `contentWindowInsets`에 0을 넘깁니다. 위쪽과 아래쪽 인셋을 각 슬롯에서 처리하고, 본문은 슬롯의 배치 결과를 담은 `padding`을 한 번 적용합니다.

~~~kotlin
contentWindowInsets = WindowInsets(0, 0, 0, 0)

// Scaffold의 본문에서
Modifier
    .fillMaxSize()
    .padding(padding)
    .consumeWindowInsets(padding)
~~~

`padding(padding)`이 본문을 안쪽으로 배치하고, `consumeWindowInsets(padding)`은 그 공간이 이미 반영됐음을 하위 레이아웃에 전달합니다. 하위 요소에서 인셋 modifier를 쓰더라도 이미 사용한 공간을 고려할 수 있습니다. [Compose 인셋 적용과 소비](https://developer.android.com/develop/ui/compose/system/insets-ui)

좌우 여백을 바깥 틀에 둔 이유도 배치 경로에서 드러납니다. 가로 방향에서 노치가 화면 옆에 오는 경우에는 상단 바의 위쪽 여백만으로 본문을 보호하기 어렵습니다. 화면 전체에 적용되는 가로 인셋이 있어야 상단 바와 본문이 같은 좌우 경계를 씁니다.

## 화면 여백을 추적하는 순서

iOS 마이페이지의 제목이나 버튼 위치를 살펴볼 때는 먼저 SwiftUI의 `ContentView`에서 Compose에 주는 화면 범위를 확인합니다. 그다음 공용 `EolssuScaffold`에서 상단 바, 하단 슬롯, 좌우 인셋 중 어느 곳이 해당 여백을 만드는지 보면 됩니다.
