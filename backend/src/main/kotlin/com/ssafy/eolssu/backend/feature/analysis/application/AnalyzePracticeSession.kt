package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisClient
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRequest
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisResult
import com.ssafy.eolssu.backend.feature.analysis.domain.DEFAULT_ANALYSIS_TIME_LIMIT
import com.ssafy.eolssu.backend.feature.analysis.domain.PendingAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.domain.RecordingReadUrls
import com.ssafy.eolssu.backend.feature.analysis.domain.SERVER_ERROR
import com.ssafy.eolssu.backend.feature.analysis.domain.ScoreNote
import com.ssafy.eolssu.backend.feature.analysis.domain.TIMEOUT
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory

/**
 * 업로드가 끝나 `PENDING`이 된 세션의 현재 시도를 AI로 분석하고 결과를 저장한다(PRA-03).
 * 업로드 커밋 뒤 백그라운드에서 부른다. 모든 시도는 `DONE`·`FAILED`로 끝난다:
 * 실패하면 `SERVER_ERROR`, 제한 시간을 넘기면 `TIMEOUT`이고 마감 뒤에 온 결과는 저장하지 않는다.
 */
class AnalyzePracticeSession(
    private val repository: PracticeAnalysisRepository,
    private val client: AnalysisClient,
    private val scoreNotes: PracticeScoreNotes,
    private val readUrls: RecordingReadUrls,
    private val clock: Clock,
    private val timeLimit: Duration = DEFAULT_ANALYSIS_TIME_LIMIT,
) {
    fun analyze(sessionId: Long, expectedAttempt: Int, timing: EvaluationAnalysisTiming? = null): AnalysisOutcome {
        val pending = repository.findPending(sessionId, startedAfter()) ?: run {
            val outcome = if (expireIfOverdue(sessionId, expectedAttempt)) AnalysisOutcome.TimedOut else AnalysisOutcome.NotPending
            timing?.finished(outcome.code)
            return outcome
        }
        if (pending.attempt != expectedAttempt) {
            timing?.finished(AnalysisOutcome.Stale.code)
            return AnalysisOutcome.Stale
        }
        val began = clock.instant()

        val requested = try {
            val notes = scoreNotes.notesFor(pending)
            Requested(notes, client.analyze(AnalysisRequest(pending.sessionId, pending.attempt, readUrls.readUrlFor(pending), pending.bpm, pending.bpmUnit, notes, pending.instrument)))
        } catch (e: Exception) {
            // 예외 메시지는 남기지 않는다. 외부 응답이나 서명 URL이 섞일 수 있다.
            log.warn("Analysis request failed session={} attempt={} error={}", pending.sessionId, pending.attempt, e.javaClass.simpleName)
            null
        }

        val outcome = finish(pending, requested)
        timing?.finished(outcome.code)
        log.info(
            "Analysis finished session={} attempt={} outcome={} elapsedMs={}",
            pending.sessionId, pending.attempt, outcome.code, Duration.between(began, clock.instant()).toMillis(),
        )
        return outcome
    }

    /**
     * 상태 조회 때 부른다. 제한 시간을 넘긴 `PENDING`을 `TIMEOUT`으로 바꿨으면 true.
     * 누가 바꿨든 전환마다 한 줄 남겨 시간 초과 횟수를 셀 수 있게 한다.
     */
    fun expireIfOverdue(sessionId: Long, expectedAttempt: Int? = null): Boolean {
        val expired = repository.expireOverdue(sessionId, startedAfter(), expectedAttempt)
        if (expired) log.info("Analysis timed out session={}", sessionId)
        return expired
    }

    private fun finish(pending: PendingAnalysis, requested: Requested?): AnalysisOutcome {
        val saved = try {
            when (val result = requested?.result) {
                is AnalysisResult.Done -> repository.saveDone(pending, result, requested.notes, startedAfter())
                is AnalysisResult.Failed -> repository.saveFailed(pending, result.reason.name, startedAfter())
                null -> repository.saveFailed(pending, SERVER_ERROR, startedAfter())
            }
        } catch (e: RuntimeException) {
            log.warn("Analysis result could not be stored session={} attempt={} error={}", pending.sessionId, pending.attempt, e.javaClass.simpleName)
            return if (repository.saveFailed(pending, SERVER_ERROR, startedAfter())) AnalysisOutcome.ServerError else afterRejectedSave(pending)
        }
        return when {
            !saved -> afterRejectedSave(pending)
            requested == null -> AnalysisOutcome.ServerError
            else -> AnalysisOutcome.Saved(requested.result)
        }
    }

    // 저장이 거절되면 마감을 넘긴 것인지(TIMEOUT으로 바꾼다), 세션이 다른 시도·상태로 바뀐 것인지(버린다) 가린다.
    private fun afterRejectedSave(pending: PendingAnalysis) =
        if (expireIfOverdue(pending.sessionId, pending.attempt)) AnalysisOutcome.TimedOut else AnalysisOutcome.Discarded

    private fun startedAfter(): LocalDateTime = LocalDateTime.ofInstant(clock.instant().minus(timeLimit), ZoneOffset.UTC)

    private class Requested(val notes: List<ScoreNote>, val result: AnalysisResult)

    private companion object {
        val log = LoggerFactory.getLogger(AnalyzePracticeSession::class.java)
    }
}

sealed interface AnalysisOutcome {
    /** 로그에 남기는 결과 코드. */
    val code: String

    data class Saved(val result: AnalysisResult) : AnalysisOutcome {
        override val code
            get() = when (result) {
                is AnalysisResult.Done -> "DONE"
                is AnalysisResult.Failed -> result.reason.name
            }
    }

    /** AI 연결 실패·예외·형식 오류·저장 실패를 `SERVER_ERROR`로 기록했다. */
    data object ServerError : AnalysisOutcome {
        override val code = SERVER_ERROR
    }

    /** 제한 시간을 넘겨 `TIMEOUT`으로 기록했다. */
    data object TimedOut : AnalysisOutcome {
        override val code = TIMEOUT
    }

    /** 세션이 분석 대기가 아니라 AI를 부르지 않았다. */
    data object NotPending : AnalysisOutcome {
        override val code = "NOT_PENDING"
    }

    /** 큐에 넣은 시도보다 새로운 시도가 이미 접수되어 AI를 부르지 않았다. */
    data object Stale : AnalysisOutcome {
        override val code = "STALE"
    }

    /** 결과가 오는 사이 세션이 다른 시도로 바뀌었거나 상태 조회가 먼저 `TIMEOUT`으로 끝내 버렸다. */
    data object Discarded : AnalysisOutcome {
        override val code = "DISCARDED"
    }
}
