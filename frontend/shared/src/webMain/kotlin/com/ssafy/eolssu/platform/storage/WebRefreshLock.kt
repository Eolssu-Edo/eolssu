@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ssafy.eolssu.platform.storage

import com.ssafy.eolssu.core.network.SharedRefreshLock
import com.ssafy.eolssu.core.storage.KeyValueStore
import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.js
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 탭 사이의 토큰 갱신 잠금(S15P31E102-1264). 잠금은 브라우저의 Web Locks(`navigator.locks`)를, 갱신 세대는 [store](localStorage)를 쓴다.
 * 같은 주소(origin)의 모든 탭이 같은 잠금을 기다리고, 잠금을 가진 탭이 닫히면 브라우저가 잠금을 푼다.
 * Web Locks가 없는 브라우저(보안 연결이 아닌 주소 포함)나 잠금 요청이 실패하면 잠금 없이 실행한다. 이때도 탭 하나 안의 직렬화는 남는다.
 */
class WebRefreshLock(private val store: KeyValueStore) : SharedRefreshLock {
    override val generation: Long get() = store.getString(GENERATION_KEY)?.toLongOrNull() ?: 0L

    /** 잠금 안에서만 부르므로 다른 탭과 겹쳐 쓰지 않는다. */
    override fun advance() {
        store.putString(GENERATION_KEY, (generation + 1).toString())
    }

    override suspend fun <T> withLock(block: suspend () -> T): T {
        val release = acquire()
        try {
            return block()
        } finally {
            release?.invoke()
        }
    }

    /** 잠금을 받으면 놓는 함수를, 잠금을 쓸 수 없으면 null을 돌려준다. */
    private suspend fun acquire(): (() -> Unit)? {
        if (!locksSupported()) return null
        return suspendCancellableCoroutine { continuation ->
            var settled = false
            val failed = {
                if (!settled) {
                    settled = true
                    continuation.resume(null)
                }
            }
            try {
                requestLock(
                    LOCK_NAME,
                    onGranted = { resolve ->
                        val release = { releaseLock(resolve) }
                        if (settled) {
                            release()
                        } else {
                            settled = true
                            // 기다리는 동안 취소됐으면 받자마자 놓는다. 놓지 않으면 다른 탭이 영영 갱신하지 못한다.
                            continuation.resume(release) { _, _, _ -> release() }
                        }
                    },
                    onFailed = failed,
                )
            } catch (e: Throwable) {
                failed()
            }
        }
    }

    companion object {
        /** 모든 탭이 함께 보는 마지막 갱신 세대(localStorage 키). */
        const val GENERATION_KEY = "auth.refresh_generation"

        /** Web Locks 잠금 이름. */
        const val LOCK_NAME = "eolssu-auth-refresh"
    }
}

private fun locksSupported(): Boolean =
    js("typeof navigator !== 'undefined' && navigator.locks != null && typeof navigator.locks.request === 'function'")

/**
 * 잠금을 받으면 그 잠금을 놓는 `resolve`를 [onGranted]로 넘긴다. 잠금은 `resolve()`를 부를 때까지 유지된다.
 * 본문은 호출 식 하나로 둔다. Kotlin/JS는 `void (...)`처럼 값을 버리는 식을 쓸모없는 코드로 보고 지운다(2026-11-24 확인).
 */
private fun requestLock(name: String, onGranted: (JsAny) -> Unit, onFailed: () -> Unit): Unit =
    js("navigator.locks.request(name, function () { return new Promise(function (resolve) { onGranted(resolve); }); }).catch(function () { onFailed(); })")

private fun releaseLock(resolve: JsAny): Unit = js("resolve()")
