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
import java.time.Clock
import java.time.Duration
import java.time.Instant
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
        override fun findState(sessionId: Long, userId: Long) = state
        override fun startRetry(sessionId: Long, userId: Long, attempt: Int, startedAt: LocalDateTime): Boolean {
            state = AnalysisState("PENDING", null, attempt + 1)
            return true
        }
        override fun failStart(sessionId: Long, attempt: Int, startedAt: LocalDateTime) = true
    }

    private val clock = Clock.fixed(Instant.parse("2026-10-29T01:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `a retried attempt goes to the upload analysis executor`() {
        val started = mutableListOf<Long>()
        val retry = config.retryPracticeAnalysis(
            Retries(AnalysisState("FAILED", "TIMEOUT", 1)), repository, AnalysisOnUpload({ started += it }, { it.run() }), AnalysisProperties(), clock,
        )
        assertEquals(RetryOutcome.Started(2), retry.retry(userId = 1, sessionId = 7))
        assertEquals(listOf(7L), started)
    }

    @Test
    fun `a pending retry is checked against the analysis time limit`() {
        val expiredBefore = mutableListOf<LocalDateTime>()
        val analyses = object : PracticeAnalysisRepository by repository {
            override fun expireOverdue(sessionId: Long, startedAfter: LocalDateTime): Boolean {
                expiredBefore += startedAfter
                return false
            }
        }
        val retry = config.retryPracticeAnalysis(
            Retries(AnalysisState("PENDING", null, 1)), analyses, AnalysisOnUpload(null, { it.run() }),
            AnalysisProperties(timeLimit = Duration.ofSeconds(90)), clock,
        )
        assertEquals(RetryOutcome.AlreadyPending(1), retry.retry(userId = 1, sessionId = 7))
        assertEquals(listOf(LocalDateTime.of(2026, 10, 29, 0, 58, 30)), expiredBefore)
    }
}
