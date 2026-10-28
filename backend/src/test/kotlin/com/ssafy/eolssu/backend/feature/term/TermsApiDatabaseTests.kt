package com.ssafy.eolssu.backend.feature.term

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TermsApiDatabaseTests.FixedClockConfig::class)
class TermsApiDatabaseTests {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Value("\${local.server.port}")
    private var port = 0

    private val json = JsonMapper.builder().build()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @AfterEach
    fun closeClient() = client.close()

    @BeforeEach
    @AfterEach
    fun clearTerms() {
        jdbc.update("DELETE FROM terms")
    }

    @Test
    fun `anonymous GET returns an empty current terms list`() {
        assertBody("""{"httpStatus":"OK","isSuccess":true,"message":"약관 목록 조회 성공","code":200,"result":{"items":[]}}""")
    }

    @Test
    fun `returns only latest effective versions ordered by seq with string IDs and required flags`() {
        jdbc.update(
            """INSERT INTO terms (id, code, version, title, content_url, is_required, seq, effective_at) VALUES
            (9007199254740994, 'SERVICE_TERMS', 'z-old', '이전 약관', 'https://example.test/old', TRUE, 1, '2026-09-01 00:00:00'),
            (9007199254740993, 'SERVICE_TERMS', 'a-current', '서비스 이용약관', 'https://example.test/service', TRUE, 20, '2026-10-01 00:00:00'),
            (13, 'SERVICE_TERMS', 'future', '미래 약관', 'https://example.test/future', TRUE, 1, '2026-10-27 12:00:00.123457'),
            (14, 'PRIVACY_COLLECTION', 'boundary', '개인정보 수집·이용 동의', 'https://example.test/privacy', TRUE, 10, '2026-10-27 12:00:00.123456'),
            (15, 'MICROPHONE_NOTICE', 'notice', '마이크 사용 안내', 'https://example.test/microphone', FALSE, 30, '2026-01-01 00:00:00'),
            (16, 'FUTURE_ONLY', 'future', '시행 전', 'https://example.test/future', FALSE, 0, '2026-10-27 12:00:00.123457')""",
        )
        assertBody(
            """{"httpStatus":"OK","isSuccess":true,"message":"약관 목록 조회 성공","code":200,"result":{"items":[
            {"termId":"14","code":"PRIVACY_COLLECTION","title":"개인정보 수집·이용 동의","required":true,"version":"boundary","contentUrl":"https://example.test/privacy"},
            {"termId":"9007199254740993","code":"SERVICE_TERMS","title":"서비스 이용약관","required":true,"version":"a-current","contentUrl":"https://example.test/service"},
            {"termId":"15","code":"MICROPHONE_NOTICE","title":"마이크 사용 안내","required":false,"version":"notice","contentUrl":"https://example.test/microphone"}
            ]}}""",
        )
    }

    @Test
    fun `equal effective times choose larger ID and equal display order is stable by ID`() {
        jdbc.update(
            """INSERT INTO terms (id, code, version, title, content_url, is_required, seq, effective_at) VALUES
            (21, 'SERVICE_TERMS', 'old', '이전', 'https://example.test/old', TRUE, 1, '2026-01-01'),
            (23, 'SERVICE_TERMS', 'new', '최신', 'https://example.test/new', TRUE, 1, '2026-01-01'),
            (22, 'PRIVACY_COLLECTION', 'v1', '개인정보', 'https://example.test/privacy', TRUE, 1, '2026-01-01')""",
        )
        val response = getTerms()
        assertEquals(200, response.statusCode())
        val items = json.readTree(response.body()).path("result").path("items")
        assertEquals(2, items.size())
        assertEquals("22", items.path(0).path("termId").asString())
        assertEquals("23", items.path(1).path("termId").asString())
    }

    private fun assertBody(expected: String) {
        val response = getTerms()
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""))
        assertEquals(json.readTree(expected), json.readTree(response.body()))
    }

    private fun getTerms(): HttpResponse<String> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/terms")).timeout(Duration.ofSeconds(10))
            .header("Accept", "application/json").GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    @TestConfiguration(proxyBeanMethods = false)
    class FixedClockConfig {
        @Bean
        @Primary
        fun fixedTermsClock(): Clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00.123456Z"), ZoneOffset.UTC)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { requiredEnvironment("TEST_DB_URL") }
            registry.add("spring.datasource.username") { requiredEnvironment("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { requiredEnvironment("TEST_DB_PASSWORD") }
        }

        private fun requiredEnvironment(name: String): String =
            requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
