package com.ssafy.eolssu.testing

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * 실제로 멈추지 않는 suspend 함수(예시 Repository 등)를 테스트에서 바로 실행한다.
 * 코루틴 테스트 라이브러리를 들이지 않으려고 둔다. 중간에 멈추면 실패한다.
 */
fun <T> runImmediately(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return checkNotNull(outcome) { "suspend 함수가 끝나지 않았다" }.getOrThrow()
}
