package com.ssafy.eolssu.backend

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("ops")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "DB_URL=jdbc:mysql://127.0.0.1:1/unreachable?connectTimeout=250&socketTimeout=250",
        "DB_USERNAME=readiness-test",
        "DB_PASSWORD=not-a-secret",
        "spring.datasource.hikari.connection-timeout=250",
        "spring.datasource.hikari.validation-timeout=250",
    ],
)
class ReadinessDatabaseFailureTests {
    @Autowired
    private lateinit var context: ApplicationContext

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Test
    fun `unavailable database fails readiness while process stays live and no migration runs`() {
        assertEquals(1, context.getBeansOfType(DataSource::class.java).size)
        assertTrue(context.getBeansOfType(Flyway::class.java).isEmpty())
        HttpClient.newHttpClient().use { client ->
            fun get(path: String) = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/actuator/health/$path"))
                    .timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            val ready = get("readiness")
            assertEquals(503, ready.statusCode())
            assertEquals("{\"status\":\"DOWN\"}", ready.body())
            val live = get("liveness")
            assertEquals(200, live.statusCode())
            assertEquals("{\"status\":\"UP\"}", live.body())
        }
    }
}
