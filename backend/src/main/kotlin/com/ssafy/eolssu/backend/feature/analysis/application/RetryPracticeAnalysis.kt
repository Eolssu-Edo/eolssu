package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import com.ssafy.eolssu.backend.feature.analysis.domain.RETRYABLE_FAIL_REASONS
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory

/**
 * `TIMEOUT`·`SERVER_ERROR`로 실패한 세션을 같은 녹음으로 다시 분석한다(PRA-03 '분석 다시 시도', S15P31E102-175).
 * 새 시도는 읽은 시도 번호를 조건으로 한 조건부 갱신으로 하나만 시작하고 횟수 제한은 없다.
 * 이전 시도의 늦은 결과는 시도 번호가 달라 저장되지 않는다.
 *
 * [retry]는 트랜잭션 밖에서 부른다. 새 시도의 `PENDING`이 커밋된 뒤에 분석이 세션을 읽어야 하기 때문이다(업로드 뒤 실행과 같다).
 */
class RetryPracticeAnalysis(
    private val repository: AnalysisRetryRepository,
    /** 마감을 넘긴 `PENDING`을 `TIMEOUT`으로 바꾼다([AnalyzePracticeSession.expireIfOverdue]). */
    private val expireIfOverdue: (Long) -> Boolean,
    /**
     * 새 시도의 분석([AnalyzePracticeSession.analyze])을 백그라운드 작업으로 넘기기만 하고 바로 돌아온다.
     * 넘기지 못해 예외가 나면 그 시도를 `SERVER_ERROR`로 끝내 사용자가 마감까지 기다리지 않고 다시 시도할 수 있게 한다.
     */
    private val startAnalysis: (Long) -> Unit,
    /** 시도 시작 시각은 마감 판정과 같은 시계로 정한다. */
    private val clock: Clock,
) {
    fun retry(userId: Long, sessionId: Long): RetryOutcome {
        // 읽은 뒤 다른 요청이 새 시도를 시작했다가 곧바로 실패하면 시도 번호가 달라 갱신이 0행이다. 몇 번만 다시 읽어 본다.
        repeat(MAX_START_TRIES) {
            val state = currentState(userId, sessionId) ?: return RetryOutcome.NotFound
            if (!state.retryable()) return state.unchangedOutcome()

            val startedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            if (repository.startRetry(sessionId, userId, state.attempt, startedAt)) {
                val attempt = state.attempt + 1
                launch(sessionId, attempt, startedAt)
                return RetryOutcome.Started(attempt)
            }
        }
        val state = currentState(userId, sessionId) ?: return RetryOutcome.NotFound
        // 아직 다시 시도할 수 있는데 시작하지 못했으면 거절이 아니다. 잠시 후 다시 요청하게 한다.
        return if (state.retryable()) RetryOutcome.Busy else state.unchangedOutcome()
    }

    // 서버가 재시작돼 마감이 지나도 PENDING으로 남은 시도는 TIMEOUT으로 끝낸 뒤 다시 시도한다.
    // 다른 요청(상태 조회 등)이 먼저 끝냈을 수도 있으니 결과와 상관없이 다시 읽는다.
    private fun currentState(userId: Long, sessionId: Long): AnalysisState? {
        val state = repository.findState(sessionId, userId) ?: return null
        if (state.status != "PENDING") return state
        expireIfOverdue(sessionId)
        return repository.findState(sessionId, userId)
    }

    private fun launch(sessionId: Long, attempt: Int, startedAt: LocalDateTime) {
        try {
            startAnalysis(sessionId)
        } catch (e: Exception) {
            log.warn("Analysis retry could not be started session={} attempt={} error={}", sessionId, attempt, e.javaClass.simpleName)
            // 실패 기록마저 실패해도(DB 장애 등) 원래 원인을 잃지 않는다. 그 시도는 마감 뒤 TIMEOUT으로 끝난다.
            runCatching { repository.failStart(sessionId, attempt, startedAt) }.onFailure(e::addSuppressed)
            throw e
        }
    }

    private fun AnalysisState.retryable() = status == "FAILED" && failReason in RETRYABLE_FAIL_REASONS

    private fun AnalysisState.unchangedOutcome() =
        if (status == "PENDING") RetryOutcome.AlreadyPending(attempt) else RetryOutcome.NotRetryable(this)

    private companion object {
        const val MAX_START_TRIES = 3
        val log = LoggerFactory.getLogger(RetryPracticeAnalysis::class.java)
    }
}

/** HTTP 응답으로 바꾸는 것은 `PracticeAnalysisRetryController`가 한다(S15P31E102-222). */
sealed interface RetryOutcome {
    /** 새 시도를 시작했다. 세션은 이 번호의 `PENDING`이다. */
    data class Started(val attempt: Int) : RetryOutcome

    /** 이미 분석 중이라 새 시도를 만들지 않았다. */
    data class AlreadyPending(val attempt: Int) : RetryOutcome

    /** 새로 녹음해야 하는 실패(`NO_SOUND`·`TOO_SHORT`·`DECODE_ERROR`·`UPLOAD_TIMEOUT`)이거나 이미 끝났거나 아직 업로드 전이다. */
    data class NotRetryable(val state: AnalysisState) : RetryOutcome

    /** 세션이 없거나 다른 회원의 것이다. */
    data object NotFound : RetryOutcome

    /** 다시 시도할 수 있지만 다른 시도가 계속 끼어들어(시작 직후 실패 등) 새 시도를 시작하지 못했다. 잠시 후 다시 요청한다. */
    data object Busy : RetryOutcome
}
