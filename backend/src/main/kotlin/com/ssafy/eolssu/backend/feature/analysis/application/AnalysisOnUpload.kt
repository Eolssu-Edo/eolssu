package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.common.EvaluationTimingId
import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 업로드 커밋 뒤 세션 분석을 백그라운드에서 시작한다(PRA-03). 업로드 응답은 분석을 기다리지 않는다.
 * 시작하지 못한 세션(연결 전, 대기열 가득 참, 앱 종료 중)은 `PENDING`으로 남고 제한 시간이 지나면 상태 조회가 `TIMEOUT`으로 끝낸다.
 *
 * @param start 세션 하나를 분석한다. null이면 분석 연결에 필요한 구현이 아직 없다([missing]).
 */
class AnalysisOnUpload(
    private val start: ((sessionId: Long, attempt: Int, timing: EvaluationAnalysisTiming?) -> Unit)?,
    private val executor: Executor,
    private val missing: List<String> = emptyList(),
    private val nanoTime: () -> Long = System::nanoTime,
) : AutoCloseable {
    /** 업로드 뒤 분석을 실제로 시작하는지. 연결 전이면 false. */
    val ready: Boolean
        get() = start != null

    // 트랜잭션 밖에서 발행하면 처리하지 않는다. 업로드는 PENDING 전환과 같은 트랜잭션에서 발행해야 한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUploaded(event: RecordingUploaded) {
        try {
            // The transition from RECORDING starts the session's first analysis attempt.
            submit(event.sessionId, attempt = 1, timingId = event.timingId)
        } catch (e: RejectedExecutionException) {
            if (start == null) log.warn("Analysis not started: wiring missing {} session={}", missing, event.sessionId)
            else log.warn("Analysis not started: {} session={}", e.rootMessage(), event.sessionId)
        }
    }

    /**
     * 앱 종료가 시작되면 Spring이 부른다. 실행기가 새 분석을 받지 않게 하고, 이미 받은 분석은 계속 처리하게 한다.
     * 웹 서버의 요청 마무리와 같은 때 시작해야, 실행기를 닫을 때([close]) 기다리는 시간이 컨테이너 종료 대기 안에 든다.
     */
    @EventListener(ContextClosedEvent::class)
    fun onContextClosed() {
        (executor as? ExecutorConfigurationSupport)?.initiateShutdown()
    }

    /**
     * 세션 분석을 실행기에 넘기기만 하고 돌아온다. 분석 다시 시도([RetryPracticeAnalysis])도 같은 실행기를 쓴다.
     * 넘기지 못하면(연결 전, 대기열 가득 참) [RejectedExecutionException]을 던져 다시 시도가 그 시도를 끝낼 수 있게 한다.
     */
    fun submit(sessionId: Long, attempt: Int, timingId: EvaluationTimingId? = null) {
        val queuedAt = timingId?.let { nanoTime() }
        try {
            val start = start ?: throw RejectedExecutionException("analysis wiring missing $missing")
            executor.execute {
                val timing = if (timingId == null || queuedAt == null) null
                    else EvaluationAnalysisTiming(timingId, attempt, queuedAt, nanoTime(), nanoTime)
                // 세션은 PENDING으로 남아 제한 시간 뒤 TIMEOUT이 된다.
                try {
                    start(sessionId, attempt, timing)
                } catch (e: Exception) {
                    timing?.finished("UNEXPECTED_ERROR")
                    log.warn("Analysis failed unexpectedly session={} error={}", sessionId, e.javaClass.simpleName)
                }
            }
        } catch (e: RejectedExecutionException) {
            if (timingId != null) log.info(
                "event=evaluation_analysis_timing id={} attempt={} outcome=NOT_ENQUEUED", timingId.value, attempt,
            )
            throw e
        }
    }

    /** 앱 종료 때 Spring이 부른다. 실행기가 스레드를 가졌으면 받은 분석을 정해진 시간까지 기다린 뒤 닫는다. */
    override fun close() {
        (executor as? DisposableBean)?.destroy()
    }

    private companion object {
        val log = LoggerFactory.getLogger(AnalysisOnUpload::class.java)

        // Spring 실행기는 거절을 TaskRejectedException으로 감싸므로, 실행기가 붙인 원래 이유(대기열 가득 참·종료 중)를 꺼낸다.
        fun Throwable.rootMessage(): String? = generateSequence(this) { it.cause }.last().message
    }
}
