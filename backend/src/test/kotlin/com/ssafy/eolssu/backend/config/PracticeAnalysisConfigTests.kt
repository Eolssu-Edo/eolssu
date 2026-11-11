package com.ssafy.eolssu.backend.config

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
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        override fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime) = false
    }
    private val scoreNotes = PracticeScoreNotes { emptyList() }
    private val readUrls = RecordingReadUrls { "https://storage.test/a.m4a?sig=x" }
    private val config = PracticeAnalysisConfig()

    private fun onUpload(properties: AnalysisProperties, vararg beans: Pair<String, Any>) = StaticListableBeanFactory(beans.toMap()).let {
        config.analysisOnUpload(
            properties, repository, jacksonMapperBuilder().build(),
            it.getBeanProvider(PracticeScoreNotes::class.java), it.getBeanProvider(RecordingReadUrls::class.java),
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
    }

    private class Retries(var state: AnalysisState) : AnalysisRetryRepository {
        val startedAt = mutableListOf<LocalDateTime>()
        override fun findState(sessionId: Long, userId: Long) = state
        override fun startRetry(sessionId: Long, userId: Long, attempt: Int, startedAt: LocalDateTime): Boolean {
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
        val retry = config.retryPracticeAnalysis(retries, repository, AnalysisOnUpload({ sessionId, _ -> started += sessionId }, { it.run() }), AnalysisProperties())
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
            override fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime): Boolean {
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
