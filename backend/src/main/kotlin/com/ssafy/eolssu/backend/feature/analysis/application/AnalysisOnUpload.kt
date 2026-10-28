package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 업로드 커밋 뒤 세션 분석을 백그라운드에서 시작한다(PRA-03). 업로드 응답은 분석을 기다리지 않는다.
 * 시작하지 못한 세션(연결 전, 대기열 가득 참)은 `PENDING`으로 남고 제한 시간이 지나면 상태 조회가 `TIMEOUT`으로 끝낸다.
 *
 * @param start 세션 하나를 분석한다. null이면 분석 연결에 필요한 구현이 아직 없다([missing]).
 */
class AnalysisOnUpload(
    private val start: ((sessionId: Long) -> Unit)?,
    private val executor: Executor,
    private val missing: List<String> = emptyList(),
) : AutoCloseable {
    /** 업로드 뒤 분석을 실제로 시작하는지. 연결 전이면 false. */
    val ready: Boolean
        get() = start != null

    // 트랜잭션 밖에서 발행하면 처리하지 않는다. 업로드는 PENDING 전환과 같은 트랜잭션에서 발행해야 한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUploaded(event: RecordingUploaded) {
        val start = start ?: return log.warn("Analysis not started: wiring missing {} session={}", missing, event.sessionId)
        try {
            executor.execute {
                // 실행기 밖으로 나간 예외는 앱 로그에 남지 않는다. 세션은 PENDING으로 남아 제한 시간 뒤 TIMEOUT이 된다.
                try {
                    start(event.sessionId)
                } catch (e: Exception) {
                    log.warn("Analysis failed unexpectedly session={} error={}", event.sessionId, e.javaClass.simpleName)
                }
            }
        } catch (e: RejectedExecutionException) {
            log.warn("Analysis not started: queue full session={}", event.sessionId)
        }
    }

    /** 앱 종료 때 Spring이 부른다. 실행기가 스레드를 가졌으면 닫는다. */
    override fun close() {
        (executor as? DisposableBean)?.destroy()
    }

    private companion object {
        val log = LoggerFactory.getLogger(AnalysisOnUpload::class.java)
    }
}
