package com.ssafy.eolssu.backend.feature.sheet

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.*
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import com.sun.net.httpserver.HttpServer
import java.net.*
import java.net.http.*
import java.nio.file.Files
import java.time.*
import java.util.Date
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class TemporaryImportWithoutR2DatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var queue: ConversionQueue
    @Autowired lateinit var builder: RecognitionResultBuilder
    @Autowired lateinit var audioStorage: SheetSourceStorage
    @Autowired @Qualifier("temporarySheetSourceStorage") lateinit var temporary: TemporarySheetSourceStorage
    @Value("\${local.server.port}") var port=0
    private val json=JsonMapper.builder().build()
    private val client=HttpClient.newHttpClient()
    private val png=sheetImageBytes()
    @BeforeEach fun setup() {
        cleanup()
        jdbc.update("INSERT INTO users(id,nickname,status,instrument_id) VALUES(993534,'temporary-source-test','ACTIVE',1)")
    }
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM user_sheets WHERE user_id=993534")
        jdbc.update("DELETE FROM users WHERE id=993534")
    }
    @Test fun `upload and HTTP recognition finish without R2 and remove source on success and failure`() {
        assertFalse(audioStorage.available)
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        var outcome="DONE"
        var downloads=0
        server.createContext("/v1/recognitions") { exchange ->
            val request=json.readTree(exchange.requestBody.readBytes())
            val bytes=client.send(HttpRequest.newBuilder(URI(request.path("files").get(0).path("url").asString())).GET().build(),HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200,bytes.statusCode())
            assertContentEquals(png,bytes.body());downloads++
            val response=json.createObjectNode().put("conversionId",request.path("conversionId").asString()).put("status",outcome)
            if(outcome=="DONE") {
                response.set("score",json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=1",String::class.java)))
                response.putObject("jgb").put("v",1);response.putArray("issues");response.putArray("notices")
            } else response.put("failReason","ENGINE_ERROR")
            val body=json.writeValueAsBytes(response)
            exchange.sendResponseHeaders(200,body.size.toLong());exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val source=object: SheetSourceStorage by temporary {
                override fun readUrl(key: String,ttl: Duration)=temporary.readUrl(key,ttl).replace("http://backend:8080","http://127.0.0.1:$port")
            }
            val worker=ConversionWorker(queue,source,HttpRecognitionClient("http://127.0.0.1:${server.address.port}",json,Duration.ofSeconds(10)),builder,Clock.systemUTC(),json)
            for(expected in listOf("DONE","FAILED")) {
                outcome=expected
                val boundary="temporary534"
                val body=("--$boundary\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n"+
                    "{\"title\":\"temporary\",\"notation\":\"STAFF\",\"instrumentId\":\"1\",\"bpm\":60}\r\n"+
                    "--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"score.png\"\r\nContent-Type: image/png\r\n\r\n").toByteArray()+png+"\r\n--$boundary--\r\n".toByteArray()
                val jwt=SignedJWT(JWSHeader(JWSAlgorithm.HS256),JWTClaimsSet.Builder().subject("993534").expirationTime(Date.from(Instant.now().plusSeconds(60))).build())
                jwt.sign(MACSigner(SECRET.toByteArray()))
                val accepted=client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/users/me/sheets/imports"))
                    .header("Cookie","access_token=${jwt.serialize()}").header("Idempotency-Key",expected)
                    .header("Content-Type","multipart/form-data; boundary=$boundary").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString())
                assertEquals(202,accepted.statusCode(),accepted.body())
                val id=json.readTree(accepted.body()).path("result").path("sheetId").asString()
                val key=jdbc.queryForObject("SELECT object_key FROM sheet_source_files WHERE sheet_id=?",String::class.java,id)!!
                val token=temporary.readUrl(key,Duration.ofMinutes(15)).substringAfterLast('/')
                val path=assertNotNull(temporary.readable(token)).path
                worker.runOne()
                assertEquals(expected,jdbc.queryForObject("SELECT status FROM sheet_conversions WHERE sheet_id=?",String::class.java,id))
                assertFalse(Files.exists(path));assertNull(temporary.readable(token))
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_source_files WHERE sheet_id=?",Int::class.java,id))
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_source_pages WHERE sheet_id=?",Int::class.java,id))
            }
            assertEquals(2,downloads)
        } finally { server.stop(0) }
    }
    companion object {
        private const val SECRET="temporary-source-534-test-signing-key"
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { SECRET }
            registry.add("eolssu.sheet-conversion.omr-base-url") { "http://127.0.0.1:1" }
            registry.add("eolssu.sheet-storage.type") { "s3" }
            for(key in listOf("endpoint","bucket","access-key-id","secret-access-key")) registry.add("eolssu.sheet-storage.$key") { "" }
            registry.add("spring.datasource.url") { System.getenv("TEST_DB_URL") }
            registry.add("spring.datasource.username") { System.getenv("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { System.getenv("TEST_DB_PASSWORD") }
        }
    }
}
