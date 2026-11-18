package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import com.ssafy.eolssu.backend.feature.analysis.application.AnalysisOnUpload
import com.ssafy.eolssu.backend.feature.analysis.application.RetryOutcome
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.domain.RecordingReadUrls
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import tools.jackson.module.kotlin.jacksonMapperBuilder

class PracticeAnalysisConfigTests {
    private val repository = object : PracticeAnalysisRepository {
        override fun findPending(sessionId: Long, startedAfter: LocalDateTime): PendingAnalysis? = null
        override fun saveDone(analysis: PendingAnalysis, result: AnalysisResult.Done, notes: List<ScoreNote>, startedAfter: LocalDateTime) = false
        override fun saveFailed(analysis: PendingAnalysis, failReason: String, startedAfter: LocalDateTime) = false
        override fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime, expectedAttempt: Int?) = false
    }
    private val scoreNotes = PracticeScoreNotes { emptyList() }
    private val readUrls = RecordingReadUrls { "https://storage.test/a.m4a?sig=x" }
    private val config = PracticeAnalysisConfig()

    private fun onUpload(
        properties: AnalysisProperties,
        vararg beans: Pair<String, Any>,
        registry: MeterRegistry = SimpleMeterRegistry(),
    ) = StaticListableBeanFactory(beans.toMap()).let {
        config.analysisOnUpload(
            properties, repository, jacksonMapperBuilder().build(),
            it.getBeanProvider(PracticeScoreNotes::class.java), it.getBeanProvider(RecordingReadUrls::class.java), registry,
        )
    }

    @Test
    fun `stays off until score notes, read URLs and the AI address all exist`() {
        val configured = AnalysisProperties(aiBaseUrl = "http://ai.test")
        assertFalse(onUpload(configured).ready)
        assertFalse(onUpload(configured, "notes" to scoreNotes).ready)
        assertFalse(onUpload(configured, "urls" to readUrls).ready)
        assertFalse(onUpload(AnalysisProperties(), "notes" to scoreNotes, "urls" to readUrls).ready)
        onUpload(configured, "notes" to scoreNotes, "urls" to readUrls).use { assertTrue(it.ready) }
    }

    @Test
    fun `reports whether the analysis is wired and exposes the executor state`() {
        val off = SimpleMeterRegistry()
        onUpload(AnalysisProperties(aiBaseUrl = "http://ai.test"), registry = off)
        assertEquals(0.0, off.get("eolssu.analysis.ready").gauge().value())
        // 연결 전에는 실행기가 없으므로 실행기 지표도 없다.
        assertNull(off.find("executor.active").tag("name", "analysis").gauge())

        val on = SimpleMeterRegistry()
        onUpload(AnalysisProperties(aiBaseUrl = "http://ai.test", workers = 3, queueCapacity = 7), "notes" to scoreNotes, "urls" to readUrls, registry = on).use {
            assertEquals(1.0, on.get("eolssu.analysis.ready").gauge().value())
            assertEquals(0.0, on.get("executor.active").tag("name", "analysis").gauge().value())
            assertEquals(0.0, on.get("executor.queued").tag("name", "analysis").gauge().value())
            assertEquals(7.0, on.get("executor.queue.remaining").tag("name", "analysis").gauge().value())
            assertEquals(3.0, on.get("executor.pool.max").tag("name", "analysis").gauge().value())
            // 새 슬롯의 첫 거절·실패도 increase()로 잡히게 카운터를 0으로 미리 만든다.
            for (reason in listOf("queue_full", "shutdown")) assertEquals(0.0, on.get("eolssu.analysis.rejected").tag("reason", reason).counter().count())
            for (code in listOf("DONE", "SERVER_ERROR", "TIMEOUT", "NO_SOUND", "DECODE_ERROR")) {
                assertEquals(0.0, on.get("eolssu.analysis.outcomes").tag("outcome", code).counter().count())
            }
        }
    }

    @Test
    fun `counts each finished analysis by its outcome code`() {
        val registry = SimpleMeterRegistry()
        // 시도가 PENDING이 아니면 AI를 부르지 않고 NOT_PENDING으로 끝난다(저장소 대역이 늘 null을 돌려준다).
        onUpload(AnalysisProperties(aiBaseUrl = "http://ai.test"), "notes" to scoreNotes, "urls" to readUrls, registry = registry).use {
            it.submit(7, attempt = 1)
            it.submit(8, attempt = 1)
        } // close가 받은 분석을 끝까지 기다린다.
        assertEquals(2.0, registry.get("eolssu.analysis.outcomes").tag("outcome", "NOT_PENDING").counter().count())
        assertEquals(2L, registry.get("eolssu.analysis.run").timer().count())
        assertEquals(2L, registry.get("eolssu.analysis.queue.wait").timer().count())
    }

    @Test
    fun `waits for the AI a little longer than the analysis time limit`() {
        val defaults = AnalysisProperties()
        assertEquals(Duration.ofSeconds(60), defaults.timeLimit)
        assertTrue(defaults.aiResponseTimeout > defaults.timeLimit)
        // 응답 대기가 제한 시간보다 짧으면 느린 응답이 마감 전에 끊겨 TIMEOUT 대신 SERVER_ERROR가 된다.
        assertFailsWith<IllegalArgumentException> { AnalysisProperties(timeLimit = Duration.ofSeconds(60), aiResponseTimeout = Duration.ofSeconds(60)) }
    }

    private fun bind(vararg values: Pair<String, String>) =
        Binder(MapConfigurationPropertySource(values.toMap())).bindOrCreate("eolssu.analysis", AnalysisProperties::class.java)

    @Test
    fun `tuning only the time limit keeps the AI wait just longer`() {
        val tuned = bind("eolssu.analysis.time-limit" to "90s")
        assertEquals(Duration.ofSeconds(90), tuned.timeLimit)
        assertEquals(Duration.ofSeconds(95), tuned.aiResponseTimeout)
    }

    @Test
    fun `bare numbers are seconds, not milliseconds`() {
        val bound = bind("eolssu.analysis.time-limit" to "30", "eolssu.analysis.ai-response-timeout" to "40")
        assertEquals(Duration.ofSeconds(30), bound.timeLimit)
        assertEquals(Duration.ofSeconds(40), bound.aiResponseTimeout)
    }

    @Test
    fun `explains which values are invalid`() {
        val error = assertFailsWith<IllegalArgumentException> { AnalysisProperties(queueCapacity = -1) }
        assertTrue(error.message!!.contains("queue-capacity"), error.message)
        val wait = assertFailsWith<IllegalArgumentException> { AnalysisProperties(shutdownWait = Duration.ofSeconds(-1)) }
        assertTrue(wait.message!!.contains("shutdown-wait"), wait.message)
    }

    // 종료 전에 시작된 시도는 제한 시간 안에 모두 마감되므로, 그만큼(+저장 여유)만 기다린다.
    // 운영 Compose의 stop_grace_period(250초)와 웹 서버 마무리(120초)를 합쳐도 넘지 않는다.
    @Test
    fun `waits for running analyses about as long as the time limit on shutdown`() {
        assertEquals(Duration.ofSeconds(65), AnalysisProperties().shutdownWait)
        assertEquals(Duration.ofSeconds(95), bind("eolssu.analysis.time-limit" to "90").shutdownWait)
        assertEquals(Duration.ofSeconds(20), bind("eolssu.analysis.shutdown-wait" to "20").shutdownWait)
    }

    private class Retries(var state: AnalysisState) : AnalysisRetryRepository {
        val startedAt = mutableListOf<LocalDateTime>()
        override fun findState(sessionId: Long, userId: Long) = state
        override fun startRetry(sessionId: Long, userId: Long, attempt: Int, startedAt: LocalDateTime, timingId: EvaluationTimingId?): Boolean {
            this.startedAt += startedAt
            state = AnalysisState("PENDING", null, attempt + 1)
            return true
        }
        override fun failStart(sessionId: Long, attempt: Int, startedAt: LocalDateTime) = true
    }

    // 벽시계 범위로 본다. 같은 시계인지 직접 증명하지는 못하지만, 고정 시각으로 바꾼 공용 Clock이나 지역 시각(KST, 9시간 차이)을 쓰면 범위를 벗어난다.
    private fun utcNow() = LocalDateTime.now(ZoneOffset.UTC)

    // 업로드·분석·상태 조회처럼 서버의 UTC 시계를 쓴다. 공용 Clock Bean(테스트가 고정 시각으로 바꾸는 것)을 쓰면
    // 시도 시작 시각과 분석의 마감 판정이 다른 시계가 되어 새 시도가 곧바로 TIMEOUT이 될 수 있다.
    @Test
    fun `a retried attempt starts on the server UTC clock and goes to the upload analysis executor`() {
        val started = mutableListOf<Long>()
        val retries = Retries(AnalysisState("FAILED", "TIMEOUT", 1))
        val retry = config.retryPracticeAnalysis(retries, repository, AnalysisOnUpload({ sessionId, _, _ -> started += sessionId }, { it.run() }), AnalysisProperties())
        val before = utcNow()
        assertEquals(RetryOutcome.Started(2), retry.retry(userId = 1, sessionId = 7))
        val after = utcNow()
        assertEquals(listOf(7L), started)
        assertTrue(retries.startedAt.single() in before..after, "${retries.startedAt} not within $before..$after")
    }

    @Test
    fun `a pending retry expires attempts started more than the time limit before UTC now`() {
        val expiredBefore = mutableListOf<LocalDateTime>()
        val analyses = object : PracticeAnalysisRepository by repository {
            override fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime, expectedAttempt: Int?): Boolean {
                expiredBefore += startedAfter
                return false
            }
        }
        val retry = config.retryPracticeAnalysis(
            Retries(AnalysisState("PENDING", null, 1)), analyses, AnalysisOnUpload(null, { it.run() }), AnalysisProperties(timeLimit = Duration.ofSeconds(90)),
        )
        val before = utcNow()
        assertEquals(RetryOutcome.AlreadyPending(1), retry.retry(userId = 1, sessionId = 7))
        val after = utcNow()
        assertTrue(expiredBefore.single() in before.minusSeconds(90)..after.minusSeconds(90), "$expiredBefore not 90 seconds before $before..$after")
    }
}
