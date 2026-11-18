package com.ssafy.eolssu.backend.feature.analysis

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 분석 연결이 갖춰진 실제 앱(db 프로필)이 분석 실행기 지표를 Prometheus 수집 주소로 내보내는지 본다(S15P31E102-947).
 * AI 주소는 연결 판정에만 쓰고 부르지 않는다. 이 시험은 업로드를 하지 않는다.
 */
@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalysisMetricsDatabaseTests {
    @Value("\${local.server.port}") private var port = 0

    @Test
    fun `prometheus scrape shows the analysis executor state`() {
        val body = HttpClient.newHttpClient().use { client ->
            val response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/actuator/prometheus")).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())
            response.body()
        }
        for (line in listOf(
            "eolssu_analysis_ready 1.0",
            "executor_active_threads{name=\"analysis\"}",
            "executor_queued_tasks{name=\"analysis\"}",
            "executor_queue_remaining_tasks{name=\"analysis\"}",
            "eolssu_analysis_queue_wait_seconds_count",
            "eolssu_analysis_run_seconds_bucket",
        )) {
            assertTrue(body.lines().any { it.startsWith(line) }, "missing $line")
        }
    }

    companion object {
        @JvmStatic @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.auth.code-hmac-secret") { "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" }
            registry.add("eolssu.security.jwt-secret") { "analysis-metrics-test-signing-key-947" }
            registry.add("eolssu.analysis.ai-base-url") { "http://127.0.0.1:9" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }
        private fun requiredEnvironment(name: String) = requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
