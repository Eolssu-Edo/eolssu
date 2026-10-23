package com.ssafy.eolssu.backend

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
    fun `starts eolssu HTTP server without a database or AI service`() {
        assertEquals("eolssu", environment.getProperty("spring.application.name"))
        assertTrue(context.getBeansOfType(DataSource::class.java).isEmpty())
        assertTrue(context.getBeansOfType(SqlSessionFactory::class.java).isEmpty())
        assertTrue(context.getBeansOfType(Flyway::class.java).isEmpty())

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
