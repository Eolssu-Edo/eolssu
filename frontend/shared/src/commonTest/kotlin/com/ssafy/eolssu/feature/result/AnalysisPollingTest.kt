package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.data.practice.AnalysisStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/** 분석 상태 조회 간격·멈춤·시간 초과를 가짜 시계로 검사한다(PRA-03 상태 조회와 시간 초과). */
class AnalysisPollingTest {
    private var now = 0L
    private val polling = AnalysisPolling(clock = { now })

    /** 응답이 없을 때 [PollStep.Wait]만큼 시계를 넘기며 실패가 나올 때까지 돌리고, 그때의 시각을 돌려준다. */
    private fun timeoutAtWithoutResponse(): Long {
        while (true) {
            when (val step = polling.onNoResponse()) {
                is PollStep.Wait -> now += step.millis
                is PollStep.Failed -> {
                    assertEquals(AnalysisFailure.Timeout, step.reason)
                    return now
                }
                is PollStep.Done -> error("응답 없이 끝날 수 없다")
            }
        }
    }

    @Test
    fun pendingWaitsTwoSecondsBeforeNextQuery() {
        assertEquals(PollStep.Wait(2_000), polling.onStatus(AnalysisStatus.Pending))
    }

    @Test
    fun doneStopsWithEvaluation() {
        assertEquals(PollStep.Done("evaluation-1"), polling.onStatus(AnalysisStatus.Done("evaluation-1")))
    }

    @Test
    fun serverFailureStopsWithItsReason() {
        assertEquals(PollStep.Failed(AnalysisFailure.NoSound), polling.onStatus(AnalysisStatus.Failed(AnalysisFailure.NoSound)))
        assertEquals(PollStep.Failed(AnalysisFailure.Timeout), polling.onStatus(AnalysisStatus.Failed(AnalysisFailure.Timeout)))
    }

    @Test
    fun seventySecondsWithoutAnyResponseIsTimeout() {
        assertEquals(70_000, timeoutAtWithoutResponse())
    }

    @Test
    fun aResponseRestartsTheSeventySeconds() {
        now = 60_000
        polling.onStatus(AnalysisStatus.Pending)
        assertEquals(130_000, timeoutAtWithoutResponse())
    }

    @Test
    fun timeInBackgroundDoesNotCountAfterResume() {
        now = 300_000 // 백그라운드에 5분 있다가 돌아옴
        polling.resume()
        assertEquals(PollStep.Wait(2_000), polling.onNoResponse())
        assertEquals(370_000, timeoutAtWithoutResponse())
    }

    @Test
    fun queryTimeLimitIsWhatIsLeftOfTheSeventySeconds() {
        assertEquals(70_000, polling.remainingMillis())
        now = 69_000
        assertEquals(1_000, polling.remainingMillis())
        now = 80_000
        assertEquals(0, polling.remainingMillis())
    }
}
