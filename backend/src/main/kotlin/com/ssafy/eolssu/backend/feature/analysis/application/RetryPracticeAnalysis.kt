package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisState
import com.ssafy.eolssu.backend.feature.analysis.domain.RETRYABLE_FAIL_REASONS
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * `TIMEOUT`·`SERVER_ERROR`로 실패한 세션을 같은 녹음으로 다시 분석한다(PRA-03 '분석 다시 시도', S15P31E102-175).
 * 새 시도는 조건부 갱신으로 하나만 시작하고 횟수 제한은 없다. 이전 시도의 늦은 결과는 시도 번호가 달라 저장되지 않는다.
 * 분석 실행은 업로드 뒤 실행과 같은 백그라운드 경로(`startAnalysis`)로 넘긴다.
 */
class RetryPracticeAnalysis(
    private val repository: AnalysisRetryRepository,
    /** 마감을 넘긴 `PENDING`을 `TIMEOUT`으로 바꾼다([AnalyzePracticeSession.expireIfOverdue]). */
    private val expireIfOverdue: (Long) -> Boolean,
    /** 새 시도의 분석을 백그라운드에서 실행한다([AnalyzePracticeSession.analyze]). */
    private val startAnalysis: (Long) -> Unit,
    /** 시도 시작 시각은 마감 판정과 같은 시계로 정한다. */
    private val clock: Clock,
) {
    fun retry(userId: Long, sessionId: Long): RetryOutcome {
        var state = repository.findState(sessionId, userId) ?: return RetryOutcome.NotFound
        // 서버가 재시작돼 마감이 지나도 PENDING으로 남은 시도는 TIMEOUT으로 끝낸 뒤 다시 시도한다.
        if (state.status == "PENDING" && expireIfOverdue(sessionId)) {
            state = repository.findState(sessionId, userId) ?: return RetryOutcome.NotFound
        }
        if (!state.retryable()) return state.unchangedOutcome()

        if (!repository.startRetry(sessionId, userId, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
            // 다른 요청이 먼저 새 시도를 시작했거나 그 사이 상태가 바뀌었다.
            return repository.findState(sessionId, userId)?.unchangedOutcome() ?: RetryOutcome.NotFound
        }
        startAnalysis(sessionId)
        return RetryOutcome.Started(state.attempt + 1)
    }

    private fun AnalysisState.retryable() = status == "FAILED" && failReason in RETRYABLE_FAIL_REASONS

    private fun AnalysisState.unchangedOutcome() =
        if (status == "PENDING") RetryOutcome.AlreadyPending(attempt) else RetryOutcome.NotRetryable(this)
}

/** API 응답 형식(경로·HTTP 상태·오류 코드)은 PRA API 명세가 정해지면 컨트롤러에서 붙인다. */
sealed interface RetryOutcome {
    /** 새 시도를 시작했다. 세션은 이 번호의 `PENDING`이다. */
    data class Started(val attempt: Int) : RetryOutcome

    /** 이미 분석 중이라 새 시도를 만들지 않았다. */
    data class AlreadyPending(val attempt: Int) : RetryOutcome

    /** 새로 녹음해야 하는 실패(`NO_SOUND`·`TOO_SHORT`·`DECODE_ERROR`·`UPLOAD_TIMEOUT`)이거나 이미 끝났거나 아직 업로드 전이다. */
    data class NotRetryable(val state: AnalysisState) : RetryOutcome

    /** 세션이 없거나 다른 회원의 것이다. */
    data object NotFound : RetryOutcome
}
