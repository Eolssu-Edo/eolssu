package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import java.util.concurrent.TimeUnit.NANOSECONDS
import org.slf4j.LoggerFactory

/** Measures time within one backend process. Wall time is used separately for the analysis deadline. */
class EvaluationAnalysisTiming internal constructor(
    private val id: EvaluationTimingId,
    private val attempt: Int,
    queuedAt: Long,
    private val workerStartedAt: Long,
    private val nanoTime: () -> Long,
) {
    private val queueMs = NANOSECONDS.toMillis(workerStartedAt - queuedAt)

    internal fun finished(outcome: String) {
        if (outcome == AnalysisOutcome.Stale.code) {
            log.info("event=evaluation_analysis_timing id={} attempt={} outcome=STALE", id.value, attempt)
            return
        }
        val processingMs = NANOSECONDS.toMillis(nanoTime() - workerStartedAt)
        log.info(
            "event=evaluation_analysis_timing id={} attempt={} queue_ms={} processing_ms={} outcome={}",
            id.value, attempt, queueMs, processingMs, outcome,
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(EvaluationAnalysisTiming::class.java)
    }
}
