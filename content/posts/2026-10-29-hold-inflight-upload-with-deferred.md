---
title: "업로드 응답을 멈춰 놓고 재전송 버튼을 두 번 눌렀다"
date: 2026-10-29T00:00:00+09:00
slug: "hold-inflight-upload-with-deferred"
author: "우동균"
categories: ["frontend"]
tags: ["Kotlin", "코루틴", "테스트", "녹음 업로드"]
description: "가짜 업로더를 CompletableDeferred에서 멈춰 재전송 중의 화면 상태와 중복 호출 방지를 같은 테스트로 확인했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-hold-inflight-upload-with-deferred.webp"
draft: false
---

얼쑤에서는 녹음이 서버에 도착하지 않으면 같은 화면에서 다시 보낼 수 있습니다. 전송이 끝나기 전 버튼을 한 번 더 눌러도 요청은 하나만 진행해야 합니다. 성공과 실패를 즉시 반환하는 업로더 대역만으로는 이 조건을 검사할 수 없었습니다. 첫 번째 재전송이 테스트의 다음 줄로 돌아오기 전에 끝나면, 두 번째 누름은 이미 끝난 화면을 검사하게 됩니다.

## 재전송 한 건만 원하는 지점에 멈췄다

가짜 업로더에 선택적으로 `CompletableDeferred<Unit>`를 넣었습니다. 업로더는 호출을 기록하고 이번 호출에 돌려줄 값을 정한 뒤 `await()`에서 멈춥니다. 결과를 먼저 꺼내면 같은 테스트에서 다른 호출을 끼워 넣어도 대기 중인 호출의 결과가 달라지지 않습니다. 테스트는 그 상태에서 화면을 읽고, 재전송을 한 번 더 호출한 다음 `complete(Unit)`으로 첫 호출을 이어 갑니다.

```kotlin
val gate = CompletableDeferred<Unit>()
uploader.gate = gate

uploadSession.resend()
assertTrue(uploadState.resending)
uploadSession.resend()
assertEquals(2, uploader.calls.size)

gate.complete(Unit)
assertFalse(uploadState.resending)
assertNull(uploadState.upload)
```

여기서 **두 건**은 연결 실패를 반환한 최초 전송과 진행 중인 재전송을 합한 수입니다. 버튼을 다시 누른 뒤에도 호출이 두 건이면 새 업로드가 생기지 않았다는 뜻입니다. 첫 재전송의 응답을 풀어 준 뒤에는 진행 표시와 업로드 오류 상태가 함께 사라졌습니다.

## 정지 지점과 실행 환경을 함께 고정했다

화면 테스트는 `Dispatchers.Unconfined`로 코루틴을 시작해 가짜 업로더의 정지 지점까지 진행시켰습니다. 멈추지 않는 대역만 쓰는 별도의 공통 테스트에는 `startCoroutine`으로 결과를 받아, 함수가 예상대로 끝났는지도 검사했습니다. 정지 상태가 검증 대상인 이 사례에서는 테스트가 직접 응답을 풀고 최종 상태까지 확인하는 쪽이 중요했습니다.

확인한 범위는 공통 화면 로직에 두 번째 재전송 요청이 들어오는 순서입니다. 실제 네트워크 지연이나 두 스레드에서의 경쟁을 측정하는 대신, 검사하려는 순서를 가짜 업로더의 정지 지점으로 고정했습니다.
