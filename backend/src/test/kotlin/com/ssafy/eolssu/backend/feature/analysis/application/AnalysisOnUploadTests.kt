package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class AnalysisOnUploadTests {
    private val started = mutableListOf<Long>()
    private val direct = Executor { it.run() }

    @Test
    fun `starts the analysis of the uploaded session on the executor`() {
        AnalysisOnUpload({ sessionId, _, _ -> started += sessionId }, direct).onUploaded(RecordingUploaded(7))
        assertEquals(listOf(7L), started)
    }

    @Test
    fun `does nothing while the analysis is not wired`() {
        AnalysisOnUpload(null, direct, missing = listOf("PracticeScoreNotes")).onUploaded(RecordingUploaded(7))
        assertEquals(emptyList(), started)
    }

    @Test
    fun `a full queue does not fail the upload`() {
        AnalysisOnUpload({ sessionId, _, _ -> started += sessionId }, { throw RejectedExecutionException("full") }).onUploaded(RecordingUploaded(7))
        assertEquals(emptyList(), started)
    }

    @Test
    fun `a failing analysis does not escape the background task`() {
        AnalysisOnUpload({ _, _, _ -> throw IllegalStateException("db down") }, direct).onUploaded(RecordingUploaded(7))
    }

    @Test
    fun `submit hands a retried session to the same executor`() {
        AnalysisOnUpload({ sessionId, _, _ -> started += sessionId }, direct).submit(9, attempt = 2)
        assertEquals(listOf(9L), started)
    }

    @Test
    fun `submit fails while the analysis is not wired so the retry can end the attempt`() {
        assertFailsWith<RejectedExecutionException> { AnalysisOnUpload(null, direct, missing = listOf("RecordingReadUrls")).submit(9, attempt = 2) }
    }

    @Test
    fun `submit lets a full queue fail the retry`() {
        assertFailsWith<RejectedExecutionException> { AnalysisOnUpload({ sessionId, _, _ -> started += sessionId }, { throw RejectedExecutionException("full") }).submit(9, attempt = 2) }
        assertEquals(emptyList(), started)
    }

    @Test
    fun `a failing retried analysis does not escape the background task`() {
        AnalysisOnUpload({ _, _, _ -> throw IllegalStateException("db down") }, direct).submit(9, attempt = 2)
    }

    /** 트랜잭션만 흉내 내는 관리자. DB 없이 커밋·롤백·커밋 실패 뒤 이벤트 처리를 본다. */
    class NoResourceTransactionManager : AbstractPlatformTransactionManager() {
        var failCommit = false
        override fun doGetTransaction() = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) {}
        override fun doCommit(status: DefaultTransactionStatus) {
            if (failCommit) throw TransactionSystemException("commit failed")
        }
        override fun doRollback(status: DefaultTransactionStatus) {}
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class Wiring {
        val started = mutableListOf<Long>()

        @Bean
        fun transactionManager(): PlatformTransactionManager = NoResourceTransactionManager()

        @Bean
        fun analysisOnUpload() = AnalysisOnUpload({ sessionId, _, _ -> started += sessionId }, Executor { it.run() })
    }

    @Test
    fun `starts only after the upload transaction commits`() {
        AnnotationConfigApplicationContext(Wiring::class.java).use { context ->
            val transactions = TransactionTemplate(context.getBean(PlatformTransactionManager::class.java))
            val recorded = context.getBean(Wiring::class.java).started

            transactions.executeWithoutResult {
                context.publishEvent(RecordingUploaded(7))
                assertEquals(emptyList(), recorded, "must not start before commit")
            }
            assertEquals(listOf(7L), recorded)

            transactions.executeWithoutResult {
                context.publishEvent(RecordingUploaded(8))
                it.setRollbackOnly()
            }
            assertEquals(listOf(7L), recorded, "a rolled back upload must not start an analysis")

            // 커밋 직전(BEFORE_COMMIT)에 시작하면 이 경우를 놓친다: 업로드는 실패했는데 분석이 돈다.
            context.getBean(NoResourceTransactionManager::class.java).failCommit = true
            assertFailsWith<TransactionSystemException> {
                transactions.executeWithoutResult { context.publishEvent(RecordingUploaded(9)) }
            }
            assertEquals(listOf(7L), recorded, "an upload whose commit failed must not start an analysis")
        }
    }
}
