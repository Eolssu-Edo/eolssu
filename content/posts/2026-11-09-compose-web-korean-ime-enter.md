---
title: "한글을 쳤는데 Enter가 동작하지 않았다"
date: 2026-11-09T00:00:00+09:00
slug: "compose-web-korean-ime-enter"
author: "송가연"
categories: ["frontend"]
tags: ["Compose Multiplatform", "Kotlin/Wasm", "IME"]
description: "Compose 웹의 정간보 편집기에서 한글 조합과 Enter 등록을 다루고, 미리보기와 오류 안내가 나타나는 시점을 분리했습니다."
draft: false
thumbnail: "https://team-edo.github.io/blog-images/2026-11-compose-web-korean-ime-enter.webp"
---

정간보 편집기에서 칸을 고르고 `청태`를 입력한 뒤 Enter를 눌렀습니다. 입력칸에 글자는 보였지만 악보에 음이 적히지 않았습니다. 미리보기까지 사라져 있었으니, 처음 쓰는 사람이라면 입력이 아직 진행 중인지, 이 음을 쓸 수 없는지, Enter가 동작하지 않은 건지 구별하기 어렵습니다.

## 글자가 보이는 시점과 조합이 끝나는 시점

한글 입력기는 자모를 모아 글자를 만들고, 조합 중인 범위도 입력 상태에 남깁니다. Compose의 `TextFieldValue`에는 현재 문자열과 조합 범위가 함께 들어 있습니다. 브라우저에서는 글자 조합 이벤트와 키 이벤트를 따로 다룹니다. Enter를 눌러도 그 키 이벤트가 앱의 완료 콜백으로 이어지는지는 입력기와 UI가 이벤트를 처리하는 방식에 달려 있습니다. [W3C UI Events의 조합 중 키 이벤트](https://www.w3.org/TR/uievents/#keys-during-composition)

당시 웹 미리보기에서 한글 조합이 남아 있는 상태로 Enter를 눌렀을 때 `KeyboardActions.onDone`이 호출되지 않았습니다. 입력칸 옆의 ‘적기’ 버튼과 같은 동작을 실행할 경로를 키보드에도 마련해야 했습니다.

## 등록하는 곳은 하나로 모으기

‘적기’ 버튼, 키보드의 완료 동작, 하드웨어 Enter가 같은 `submit()`을 사용하도록 했습니다. Compose 웹에서 놓치던 Enter는 `onPreviewKeyEvent`에서 키를 놓을 때 받았습니다. 편집기의 이벤트 처리에서 필요한 부분만 옮기면 아래와 같습니다.

```kotlin
Modifier.onPreviewKeyEvent { event ->
    if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
        submit()
        true
    } else {
        false
    }
}
```

`submit()`은 빈 입력이면 바로 끝납니다. 음이나 기호를 성공적으로 적으면 입력칸을 비웁니다. 그래서 완료 콜백과 Enter 처리 경로가 연달아 실행되는 경우에도, 두 번째 호출은 비워진 입력을 보고 돌아갑니다.

## 입력 중에는 미리보기를, 등록할 때는 오류를

`청태`처럼 현재 문자열을 해석할 수 있으면 조합 중에도 적힐 율명을 미리 보여줍니다. 반면 `청`처럼 뒷글자가 들어와야 해석할 수 있는 상태에서 오류를 곧바로 띄우면, 사용자가 계속 입력하는 도중에 경고를 받게 됩니다. 오류는 사용자가 ‘적기’를 누르거나 Enter로 등록을 시도했을 때 표시했습니다. 입력 중에는 완성된 값에 대한 피드백을 주고, 등록할 때는 왜 적을 수 없는지 알려줄 수 있습니다.

수정 후 당시 웹 미리보기에서 `청태`를 입력하고 Enter를 놓으면 선택한 칸에 한 번 적히는 것을 확인했습니다. 율명 해석은 별도 테스트로 다뤘습니다. 키 입력에 관한 관찰은 당시 미리보기 환경에 한정됩니다. 다른 운영체제·브라우저·한글 입력기 조합에서 마지막 글자가 확정되는 순서까지 비교한 기록은 없습니다.
