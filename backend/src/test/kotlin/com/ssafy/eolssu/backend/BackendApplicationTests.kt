package com.ssafy.eolssu.backend

import com.ssafy.eolssu.backend.feature.practice.application.RecordingUploadExpiryService
import jakarta.servlet.MultipartConfigElement
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.ibatis.session.SqlSessionFactory
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendApplicationTests {
    @Autowired
    private lateinit var environment: Environment

    @Autowired
    private lateinit var context: ApplicationContext

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Test
    fun `readiness returns only status and management data stays private`() {
        HttpClient.newHttpClient().use { client ->
            fun get(path: String) = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
                    .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            val ready = get("/api/actuator/health/readiness")
            assertEquals(200, ready.statusCode())
            assertEquals("{\"status\":\"UP\"}", ready.body())
            for (path in listOf("/api/actuator/env", "/api/actuator/beans", "/api/actuator/configprops")) {
                assertEquals(404, get(path).statusCode())
            }
        }
    }

    @Test
    fun `prometheus scrape returns jvm and http request metrics`() {
        HttpClient.newHttpClient().use { client ->
            fun get(path: String) = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
                    .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            get("/api/actuator/health/readiness") // 요청 수치가 한 건 이상 생기게 한다
            val scrape = get("/api/actuator/prometheus")
            assertEquals(200, scrape.statusCode())
            assertTrue(scrape.body().contains("jvm_memory_used_bytes"), scrape.body().take(300))
            assertTrue(scrape.body().contains("http_server_requests_seconds_count"), scrape.body().take(300))
        }
    }

    @Test
    fun `multipart limits leave room above the 20 MiB recording limit`() {
        val multipart = context.getBean(MultipartConfigElement::class.java)

        // 413 경계(20MiB)는 녹음 검사기가 정한다. multipart는 스펙(PRA-02 요청 크기 한도)대로 파일·요청 25MiB.
        assertEquals(25L * 1024 * 1024, multipart.maxFileSize)
        assertEquals(25L * 1024 * 1024, multipart.maxRequestSize)
    }

    @Test
    fun `starts eolssu HTTP server without a database or AI service`() {
        assertEquals("eolssu", environment.getProperty("spring.application.name"))
        assertTrue(context.getBeansOfType(DataSource::class.java).isEmpty())
        assertTrue(context.getBeansOfType(SqlSessionFactory::class.java).isEmpty())
        assertTrue(context.getBeansOfType(Flyway::class.java).isEmpty())
        assertTrue(context.getBeansOfType(RecordingUploadExpiryService::class.java).isEmpty())

        HttpClient.newHttpClient().use { client ->
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:$port/__bootstrap_test_missing__"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.discarding())

            assertEquals(404, response.statusCode())
        }
    }
}
