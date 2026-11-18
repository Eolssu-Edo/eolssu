package com.ssafy.eolssu.backend

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ssafy.eolssu.backend.feature.growth.api.ScoreViewController
import com.ssafy.eolssu.backend.feature.term.api.TermsController
import org.apache.ibatis.session.SqlSessionFactory
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
        // 운영 .env에 아직 없을 수 있는 인증번호 키. 비어 있어도 기동해야 한다.
        "eolssu.auth.code-hmac-secret=",
        "eolssu.scheduling.enabled=false",
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
        // 운영은 ops만 켠다. 업무 API와 Mapper XML이 ops에서도 있어야 한다.
        assertEquals(1, context.getBeansOfType(TermsController::class.java).size)
        assertEquals(1, context.getBeansOfType(ScoreViewController::class.java).size)
        assertTrue(context.getBean(SqlSessionFactory::class.java).configuration.mappedStatementNames.isNotEmpty())
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
