package com.ssafy.eolssu.backend

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/** 운영과 같은 ops 프로필만 켜고, 이미 마이그레이션된 MySQL에서 업무 API가 응답하는지 확인한다. */
@Tag("database")
@ActiveProfiles("ops")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpsProfileDatabaseTests {
    @Autowired private lateinit var context: ApplicationContext
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") private var port = 0

    @Test
    fun `ops serves business APIs on a migrated database without running Flyway`() {
        assertTrue(context.getBeansOfType(Flyway::class.java).isEmpty())
        assertEquals("+00:00", jdbc.queryForObject("SELECT @@session.time_zone", String::class.java))

        HttpClient.newHttpClient().use { client ->
            val response = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/terms")).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode(), response.body())
            assertTrue(response.body().contains("\"isSuccess\":true"), response.body())
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            val url = requiredEnvironment("TEST_DB_URL")
            val username = requiredEnvironment("TEST_DB_USERNAME")
            val password = requiredEnvironment("TEST_DB_PASSWORD")
            // 운영처럼 스키마는 앱 밖에서 미리 적용한다. 이미 적용된 버전은 다시 실행하지 않는다.
            Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").load().migrate()
            registry.add("DB_URL") { url }
            registry.add("DB_USERNAME") { username }
            registry.add("DB_PASSWORD") { password }
            registry.add("eolssu.scheduling.enabled") { "false" }
        }

        private fun requiredEnvironment(name: String): String =
            requireNotNull(System.getenv(name)) { "$name must point to a disposable MySQL 8.4 test database" }
    }
}
