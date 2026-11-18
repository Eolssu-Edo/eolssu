package com.ssafy.eolssu.backend.config

import com.ssafy.eolssu.backend.feature.analysis.application.AnalysisOnUpload
import com.ssafy.eolssu.backend.feature.analysis.application.AnalyzePracticeSession
import com.ssafy.eolssu.backend.feature.analysis.application.RetryPracticeAnalysis
import com.ssafy.eolssu.backend.feature.analysis.domain.AnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.domain.PracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.domain.RecordingReadUrls
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.ai.HttpAnalysisClient
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.MyBatisAnalysisRetryRepository
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.MyBatisPracticeAnalysisRepository
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.MyBatisPracticeScoreNotes
import com.ssafy.eolssu.backend.feature.analysis.infrastructure.persistence.PracticeAnalysisMapper
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper

// 분석 결과 저장은 쓰기 작업이라 db 프로필에서만 만든다. 업로드 뒤 분석 시작은 악보 음표 변환(PracticeScoreNotes, 공통 악보 데이터)과
// 녹음 서명 URL(RecordingReadUrls, 업로드 Task) 구현이 Bean으로 있고 AI 주소가 설정됐을 때만 켜진다. 없으면 업로드된 세션은 시간 초과로 끝난다.
@Configuration(proxyBeanMethods = false)
@Profile("db | ops")
class PracticeAnalysisConfig {
    @Bean
    fun practiceAnalysisRepository(mapper: PracticeAnalysisMapper, transactionManager: PlatformTransactionManager): PracticeAnalysisRepository =
        MyBatisPracticeAnalysisRepository(mapper, TransactionTemplate(transactionManager))

    @Bean
    fun analysisRetryRepository(mapper: PracticeAnalysisMapper): AnalysisRetryRepository = MyBatisAnalysisRetryRepository(mapper)

    // 새 시도는 업로드 뒤 분석과 같은 실행기로 넘긴다. 분석 연결 전이면 넘기지 못해 그 시도는 SERVER_ERROR로 끝난다.
    // 마감 판정은 AI 없이 저장소로 하므로 분석 연결과 상관없이 동작한다(상태 조회와 같다).
    // 시도 시작 시각과 마감은 업로드·분석·상태 조회와 같은 서버 UTC 시계로 정한다. 공용 Clock Bean은 쓰지 않는다.
    @Bean
    fun retryPracticeAnalysis(
        retries: AnalysisRetryRepository,
        analyses: PracticeAnalysisRepository,
        analysisOnUpload: AnalysisOnUpload,
        properties: AnalysisProperties,
    ): RetryPracticeAnalysis {
        val clock = Clock.systemUTC()
        return RetryPracticeAnalysis(
            retries,
            { sessionId -> analyses.expireOverdue(sessionId, LocalDateTime.ofInstant(clock.instant().minus(properties.timeLimit), ZoneOffset.UTC)) },
            analysisOnUpload::submit,
            clock,
        )
    }

    @Bean
    fun practiceScoreNotes(mapper: PracticeAnalysisMapper, jsonMapper: JsonMapper): PracticeScoreNotes = MyBatisPracticeScoreNotes(mapper, jsonMapper)

    @Bean
    fun analysisOnUpload(
        properties: AnalysisProperties,
        repository: PracticeAnalysisRepository,
        jsonMapper: JsonMapper,
        scoreNotes: ObjectProvider<PracticeScoreNotes>,
        readUrls: ObjectProvider<RecordingReadUrls>,
        meterRegistry: MeterRegistry,
    ): AnalysisOnUpload {
        val metrics = AnalysisExecutorMetrics(meterRegistry)
        val notes = scoreNotes.ifAvailable
        val urls = readUrls.ifAvailable
        val missing = listOfNotNull(
            "PracticeScoreNotes".takeIf { notes == null },
            "RecordingReadUrls".takeIf { urls == null },
            "eolssu.analysis.ai-base-url".takeIf { properties.aiBaseUrl.isBlank() },
        )
        metrics.ready(missing.isEmpty())
        if (notes == null || urls == null || missing.isNotEmpty()) return AnalysisOnUpload(null, { it.run() }, missing)

        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(properties.aiResponseTimeout)
        }
        val client = HttpAnalysisClient(RestClient.builder().baseUrl(properties.aiBaseUrl).requestFactory(requestFactory).build(), jsonMapper)
        val analysis = AnalyzePracticeSession(repository, client, notes, urls, Clock.systemUTC(), properties.timeLimit)
        return AnalysisOnUpload(
            { sessionId, attempt, timing -> metrics.finished(analysis.analyze(sessionId, attempt, timing).code) },
            analysisExecutor(properties, metrics),
        )
    }

    // Executor Bean으로 등록하면 Spring Boot 기본 작업 실행기가 만들어지지 않으므로, 분석 연결 안에서만 만들고 함께 닫는다.
    // 종료가 시작되면(AnalysisOnUpload.onContextClosed) 새 분석은 받지 않고, 이미 받은 분석(실행 중·대기열)은 shutdownWait까지 끝낸다.
    // 그 뒤에도 남은 PENDING은 예전처럼 상태 조회가 TIMEOUT으로 끝내고, 사용자는 다시 시도할 수 있다.
    // 종료 중에도 녹음 URL·악보 음표 구현이 살아 있다: 이 Bean을 만들 때 ObjectProvider로 먼저 만들어져 더 늦게 닫힌다.
    internal fun analysisExecutor(properties: AnalysisProperties, metrics: AnalysisExecutorMetrics) = ThreadPoolTaskExecutor().apply {
        corePoolSize = properties.workers
        maxPoolSize = properties.workers
        queueCapacity = properties.queueCapacity
        setThreadNamePrefix("analysis-")
        setTaskDecorator(metrics.decorator)
        setRejectedExecutionHandler(metrics.rejection)
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationMillis(properties.shutdownWait.toMillis())
        initialize()
        metrics.bind(threadPoolExecutor)
    }
}
