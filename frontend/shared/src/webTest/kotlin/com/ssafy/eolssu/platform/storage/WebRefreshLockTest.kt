package com.ssafy.eolssu.platform.storage

import kotlinx.browser.localStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 탭 사이 토큰 갱신 잠금(S15P31E102-1264). 실제 브라우저의 Web Locks를 쓴다. 같은 주소의 잠금은 페이지 안의 두 객체 사이에서도
 * 탭 사이와 똑같이 겨루므로 두 [WebRefreshLock]을 두 탭으로 본다. 잠금 응답은 브라우저가 주므로 실제 시간으로 기다린다.
 */
class WebRefreshLockTest {
    @AfterTest
    fun clear() {
        localStorage.removeItem(WebRefreshLock.GENERATION_KEY)
    }

    @Test
    fun secondTabWaitsUntilTheFirstTabReleases() = runTest {
        withContext(Dispatchers.Default) {
            val tabA = WebRefreshLock(WebKeyValueStore())
            val tabB = WebRefreshLock(WebKeyValueStore())
            val events = mutableListOf<String>()
            val finishA = CompletableDeferred<Unit>()

            val a = async { tabA.withLock { events += "A 시작"; finishA.await(); events += "A 끝" } }
            withTimeout(5_000) { while ("A 시작" !in events) delay(10) }
            val b = async { tabB.withLock { events += "B" } }
            delay(100)
            assertEquals(listOf("A 시작"), events, "A가 잠금을 쥐고 있는 동안 B는 기다린다")

            finishA.complete(Unit)
            withTimeout(5_000) { awaitAll(a, b) }
            assertEquals(listOf("A 시작", "A 끝", "B"), events)
        }
    }

    @Test
    fun cancelledWaiterReleasesTheLockAsSoonAsItIsGranted() = runTest {
        withContext(Dispatchers.Default) {
            val tabA = WebRefreshLock(WebKeyValueStore())
            val tabB = WebRefreshLock(WebKeyValueStore())
            val finishA = CompletableDeferred<Unit>()
            val holding = CompletableDeferred<Unit>()

            val a = async { tabA.withLock { holding.complete(Unit); finishA.await() } }
            holding.await()
            val waiting = async { tabB.withLock { error("취소된 대기는 실행하지 않는다") } }
            delay(50)
            waiting.cancel()
            finishA.complete(Unit)
            a.await()

            // 취소된 대기가 잠금을 놓지 않으면 여기서 영영 기다린다.
            assertEquals("다음 탭", withTimeout(5_000) { tabA.withLock { "다음 탭" } })
        }
    }

    @Test
    fun generationIsSharedThroughLocalStorage() {
        val tabA = WebRefreshLock(WebKeyValueStore())
        val tabB = WebRefreshLock(WebKeyValueStore())
        assertEquals(0L, tabB.generation)

        tabA.advance()
        tabA.advance()

        assertEquals(2L, tabB.generation, "다른 탭이 올린 세대를 본다")
        assertEquals("2", localStorage.getItem(WebRefreshLock.GENERATION_KEY))
    }
}
