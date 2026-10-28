package com.ssafy.eolssu.backend.feature.analysis.application

import com.ssafy.eolssu.backend.feature.practice.domain.RecordingUploaded
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class AnalysisOnUploadTests {
    private val started = mutableListOf<Long>()
    private val direct = Executor { it.run() }

    @Test
    fun `starts the analysis of the uploaded session on the executor`() {
        AnalysisOnUpload({ started += it }, direct).onUploaded(RecordingUploaded(7))
        assertEquals(listOf(7L), started)
    }

    @Test
    fun `does nothing while the analysis is not wired`() {
        AnalysisOnUpload(null, direct, missing = listOf("PracticeScoreNotes")).onUploaded(RecordingUploaded(7))
        assertEquals(emptyList(), started)
    }

    @Test
    fun `a full queue does not fail the upload`() {
        AnalysisOnUpload({ started += it }, { throw RejectedExecutionException("full") }).onUploaded(RecordingUploaded(7))
        assertEquals(emptyList(), started)
    }

    /** 트랜잭션만 흉내 내는 관리자. DB 없이 커밋·롤백 뒤 이벤트 처리를 본다. */
    private class NoResourceTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction() = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) {}
        override fun doCommit(status: DefaultTransactionStatus) {}
        override fun doRollback(status: DefaultTransactionStatus) {}
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class Wiring {
        val started = mutableListOf<Long>()

        @Bean
        fun transactionManager(): PlatformTransactionManager = NoResourceTransactionManager()

        @Bean
        fun analysisOnUpload() = AnalysisOnUpload({ started += it }, Executor { it.run() })
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
        }
    }
}
