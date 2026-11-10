package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.data.practice.AnalysisStatus

/** 분석 상태를 다시 묻는 간격. */
internal const val StatusPollMillis = 2_000L
private const val FastStatusPollMillis = 1_000L
private const val FastStatusWindowMillis = 8_000L

/** 상태 응답이 이만큼 하나도 없으면 앱만 시간 초과로 본다. 서버 상태는 바꾸지 않는다. */
internal const val NoResponseTimeoutMillis = 70_000L

/** 상태 조회 뒤 할 일. */
internal sealed interface PollStep {
    data class Wait(val millis: Long) : PollStep

    data class Done(val evaluationId: String) : PollStep

    data class Failed(val reason: AnalysisFailure) : PollStep
}

/**
 * 분석 상태 조회의 다음 단계를 정한다(PRA-03 상태 조회와 시간 초과).
 * `PENDING`이면 시작 직후에는 더 자주 묻고, `DONE`·`FAILED`면 멈춘다. 70초 동안 응답이 없으면 [AnalysisFailure.Timeout]이다.
 *
 * @param clock 밀리초 단위 단조 시계. 테스트는 가짜 시계를 넣는다.
 */
internal class AnalysisPolling(private val clock: () -> Long) {
    private var lastResponseAt = clock()
    private var fastUntil = lastResponseAt + FastStatusWindowMillis

    fun start() {
        resume()
        fastUntil = lastResponseAt + FastStatusWindowMillis
    }

    /** 백그라운드에서 복귀할 때 무응답 제한만 다시 센다. */
    fun resume() {
        lastResponseAt = clock()
    }

    /** 이번 조회에 기다릴 수 있는 남은 시간. */
    fun remainingMillis(): Long = (lastResponseAt + NoResponseTimeoutMillis - clock()).coerceAtLeast(0)

    fun onStatus(status: AnalysisStatus): PollStep {
        lastResponseAt = clock()
        return when (status) {
            AnalysisStatus.Pending -> {
                val fastRemaining = fastUntil - lastResponseAt
                PollStep.Wait(if (fastRemaining > 0) minOf(FastStatusPollMillis, fastRemaining) else StatusPollMillis)
            }
            is AnalysisStatus.Done -> PollStep.Done(status.evaluationId)
            is AnalysisStatus.Failed -> PollStep.Failed(status.reason)
        }
    }

    /** 조회가 실패했거나 남은 시간 안에 응답이 없었다. */
    fun onNoResponse(): PollStep {
        val remaining = remainingMillis()
        return if (remaining == 0L) PollStep.Failed(AnalysisFailure.Timeout) else PollStep.Wait(minOf(StatusPollMillis, remaining))
    }
}
