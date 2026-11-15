package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.analysis.application.AnalysisOutcome
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisFailReason
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.springframework.core.task.TaskDecorator

/**
 * 연주 분석 실행기(업로드 뒤 분석·다시 시도)의 상태를 Prometheus로 내보낸다(S15P31E102-947).
 * 실행기는 Bean이 아니라서 Spring Boot가 executor 지표를 자동으로 붙이지 않으므로 여기서 직접 붙인다.
 *
 * - `executor_*{name="analysis"}`: Micrometer 기본 실행기 지표(실행 중·대기열·남은 자리·완료 수·스레드 수)
 * - `eolssu_analysis_ready`: 분석 연결(AI 주소·악보 음표·녹음 URL)이 갖춰졌으면 1, 아니면 0
 * - `eolssu_analysis_queue_wait_seconds`: 실행기에 넘긴 뒤 작업자가 잡기까지 기다린 시간
 * - `eolssu_analysis_run_seconds`: 작업자 한 명이 세션 하나를 분석·저장하는 데 쓴 시간
 * - `eolssu_analysis_rejected_total{reason}`: 실행기가 받지 못한 작업. `queue_full`(대기열 가득 참)·`shutdown`(종료 중).
 *   Prometheus는 활성 슬롯만 수집하므로 배포로 내려가는 이전 슬롯의 `shutdown`·결과 수는 수집되지 않는다.
 * - `eolssu_analysis_outcomes_total{outcome}`: 분석 결과 코드(DONE·SERVER_ERROR·TIMEOUT·NO_SOUND 등) 수
 */
internal class AnalysisExecutorMetrics(
    private val registry: MeterRegistry,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val queueWait = Timer.builder("eolssu.analysis.queue.wait")
        .description("Time an analysis waited in the executor queue").register(registry)
    private val run = Timer.builder("eolssu.analysis.run")
        .description("Time one analysis task ran on a worker").register(registry)

    init {
        // 슬롯이 바뀌면 새 JVM은 카운터를 처음부터 센다. 첫 사건 때 처음 생긴 계열은 increase()가 그 1건을 놓치므로 0으로 미리 만든다.
        REJECT_REASONS.forEach(::rejected)
        OUTCOME_CODES.forEach(::outcome)
    }

    fun ready(wired: Boolean) {
        val value = if (wired) 1.0 else 0.0
        Gauge.builder("eolssu.analysis.ready") { value }.strongReference(true)
            .description("1 when the analysis wiring is complete").register(registry)
    }

    fun bind(executor: ThreadPoolExecutor) = ExecutorServiceMetrics(executor, EXECUTOR_NAME, emptyList()).bindTo(registry)

    /** 넘긴 시각을 기억했다가 작업자가 잡을 때 대기 시간을, 끝날 때 실행 시간을 잰다. */
    val decorator = TaskDecorator { task ->
        val queuedAt = nanoTime()
        Runnable {
            val startedAt = nanoTime()
            queueWait.record(startedAt - queuedAt, TimeUnit.NANOSECONDS)
            try {
                task.run()
            } finally {
                run.record(nanoTime() - startedAt, TimeUnit.NANOSECONDS)
            }
        }
    }

    /** 받지 못한 작업을 이유별로 센다. 메시지는 업로드·다시 시도 로그에 이유로 남는다. */
    val rejection = RejectedExecutionHandler { _, pool ->
        val reason = if (pool.isShutdown) "shutdown" else "queue_full"
        rejected(reason).increment()
        throw RejectedExecutionException("analysis executor $reason")
    }

    fun finished(outcome: String) = outcome(outcome).increment()

    private fun outcome(code: String) = Counter.builder("eolssu.analysis.outcomes").tag("outcome", code)
        .description("Finished analyses by outcome code").register(registry)

    private fun rejected(reason: String) = Counter.builder("eolssu.analysis.rejected").tag("reason", reason)
        .description("Analyses the executor did not accept").register(registry)

    companion object {
        const val EXECUTOR_NAME = "analysis"
        private val REJECT_REASONS = listOf("queue_full", "shutdown")
        // 저장된 결과의 코드는 DONE 또는 AI 실패 사유(AnalysisOutcome.Saved.code와 같다).
        private val OUTCOME_CODES = listOf(
            "DONE",
            AnalysisOutcome.ServerError.code,
            AnalysisOutcome.TimedOut.code,
            AnalysisOutcome.NotPending.code,
            AnalysisOutcome.Stale.code,
            AnalysisOutcome.Discarded.code,
        ) + AnalysisFailReason.entries.map { it.name }
    }
}
