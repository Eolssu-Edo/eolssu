package com.ssafy.eolssu.backend.feature.auth.application

import java.util.concurrent.*
import kotlin.test.*
import org.junit.jupiter.api.Test

class KakaoEventDeadlineTests {
    @Test fun `four running jobs reject overflow immediately without queuing`() {
        KakaoEventDeadline().use { deadline ->
            val entered=CountDownLatch(4); val release=CountDownLatch(1)
            val callers=Executors.newFixedThreadPool(4)
            try {
                val jobs=(1..4).map{callers.submit{deadline.run{entered.countDown();release.await()}}}
                assertTrue(entered.await(1,TimeUnit.SECONDS))
                assertFailsWith<KakaoEventUnavailable>{deadline.run{error("overflow must never run")}}
                release.countDown()
                jobs.forEach{it.get(1,TimeUnit.SECONDS)}
            } finally {release.countDown();callers.shutdownNow()}
        }
    }
    @Test fun `unresponsive work returns retryable failure and receives cancellation`() {
        KakaoEventDeadline(100).use { deadline ->
            val cancelled=CountDownLatch(1)
            assertFailsWith<KakaoEventUnavailable>{deadline.run {
                try { CountDownLatch(1).await(2,TimeUnit.SECONDS) }
                catch(_:InterruptedException){cancelled.countDown()}
            }}
            assertTrue(cancelled.await(1,TimeUnit.SECONDS))
        }
    }
}
