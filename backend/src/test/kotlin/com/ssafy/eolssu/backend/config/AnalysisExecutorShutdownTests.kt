package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.analysis.application.AnalysisOnUpload
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * 배포(블루그린 이전 슬롯 종료) 때 분석 실행기가 이미 받은 분석을 끝까지 처리하는지 본다(S15P31E102-947).
 * 종료 순서는 Spring과 같다: 종료 시작(ContextClosedEvent) → 웹 서버 마무리 → Bean 닫기([AnalysisOnUpload.close]).
 */
class AnalysisExecutorShutdownTests {
    private val registry = SimpleMeterRegistry()
    private val metrics = AnalysisExecutorMetrics(registry)
    private val gate = CountDownLatch(1)
    private val finished: MutableList<Long> = Collections.synchronizedList(mutableListOf())

    // 첫 분석은 문이 열릴 때까지 작업자를 붙잡는다. 다른 분석은 그 뒤 대기열에서 차례를 기다린다.
    private fun onUpload(properties: AnalysisProperties) = AnalysisOnUpload(
        { sessionId, _, _ ->
            gate.await(10, TimeUnit.SECONDS)
            finished += sessionId
        },
        PracticeAnalysisConfig().analysisExecutor(properties, metrics),
    )

    @AfterEach
    fun release() = gate.countDown()

    private fun rejected(reason: String) = registry.find("eolssu.analysis.rejected").tag("reason", reason).counter()?.count() ?: 0.0

    @Test
    fun `finishes running and queued analyses but refuses new ones once shutdown starts`() {
        val analysis = onUpload(AnalysisProperties(workers = 1, queueCapacity = 5, shutdownWait = Duration.ofSeconds(10)))
        analysis.submit(1, attempt = 1)
        analysis.submit(2, attempt = 1)

        analysis.onContextClosed()
        // 종료가 시작된 슬롯은 새 분석을 받지 않는다. 업로드는 PENDING으로 남고, 다시 시도는 그 시도를 끝낸다.
        assertFailsWith<RejectedExecutionException> { analysis.submit(3, attempt = 2) }
        analysis.onUploaded(RecordingUploaded(4))
        assertEquals(2.0, rejected("shutdown"))

        val closing = thread { analysis.close() }
        closing.join(300)
        assertTrue(closing.isAlive, "close must wait for the analysis still running")
        gate.countDown()
        closing.join(10_000)
        assertEquals(false, closing.isAlive)
        assertEquals(listOf(1L, 2L), finished.toList())
        assertEquals(2L, registry.get("eolssu.analysis.run").timer().count())
    }

    @Test
    fun `stops waiting after the shutdown wait so the container still exits in time`() {
        val analysis = onUpload(AnalysisProperties(workers = 1, queueCapacity = 5, shutdownWait = Duration.ofMillis(200)))
        analysis.submit(1, attempt = 1)
        analysis.onContextClosed()

        val began = System.nanoTime()
        analysis.close()
        val waited = Duration.ofNanos(System.nanoTime() - began)

        // 끝나지 않은 시도는 저장되지 않은 채 PENDING으로 남고, 제한 시간 뒤 상태 조회가 TIMEOUT으로 끝낸다.
        assertTrue(waited >= Duration.ofMillis(150) && waited < Duration.ofSeconds(5), "waited $waited")
        assertEquals(emptyList(), finished.toList())
    }

    @Test
    fun `counts a full queue apart from shutdown`() {
        val analysis = onUpload(AnalysisProperties(workers = 1, queueCapacity = 0))
        analysis.submit(1, attempt = 1)
        // 작업자는 붙잡혀 있고 대기열은 0칸이라 넘기지 못한다. 업로드 응답은 실패하지 않는다.
        analysis.onUploaded(RecordingUploaded(2))
        assertFailsWith<RejectedExecutionException> { analysis.submit(3, attempt = 2) }

        assertEquals(2.0, rejected("queue_full"))
        assertEquals(0.0, rejected("shutdown"))
        gate.countDown()
        analysis.close()
        assertEquals(listOf(1L), finished.toList())
    }
}
