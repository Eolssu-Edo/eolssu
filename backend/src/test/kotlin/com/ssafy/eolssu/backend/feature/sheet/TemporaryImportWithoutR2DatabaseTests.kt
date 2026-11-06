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
import java.nio.file.Path
import java.time.*
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.*
import org.springframework.web.filter.OncePerRequestFilter
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
                val pending=stalledImport(expected)
                assertTrue(spool().isNotEmpty(),"multipart bytes must exist while the request runs")
                HoldUpload.release.countDown()
                val accepted=pending.get(10,TimeUnit.SECONDS)
                assertEquals(202,accepted.statusCode(),accepted.body())
                awaitEmptySpool()
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
    @Test fun `stalled multipart upload stays in the managed spool and fails after thirty minutes`() {
        val time=MutableClock()
        TemporarySheetSourceStorage(Files.createTempDirectory("sweeper"),"http://backend/x",time,temporary.multipartDirectory).use { sweeper ->
            val pending=stalledImport("stalled")
            val spooled=spool()
            assertTrue(spooled.isNotEmpty(),"multipart bytes must be written to the managed spool")
            sweeper.purgeExpired()
            time.now=time.now.plusSeconds(1800);sweeper.purgeExpired()
            spooled.forEach { assertFalse(Files.exists(it)) }
            assertUnavailable(pending)
        }
    }
    @Test fun `stalled upload that loses only the file part fails as unavailable`() {
        val pending=stalledImport("lost-file")
        Files.delete(spool().single { Files.readAllBytes(it).contentEquals(png) })
        assertTrue(spool().isNotEmpty(),"metadata part must remain until the request ends")
        assertUnavailable(pending)
        awaitEmptySpool()
    }
    @Test fun `only an expired source ends as timeout`() {
        assertEquals("TIMEOUT",failure("expired") { store,time,_ -> time.now=time.now.plusSeconds(1800);store.purgeExpired() })
        assertEquals("SERVER_ERROR",failure("lost") { _,_,path -> Files.delete(path) })
        assertEquals("TIMEOUT",failure("expired-during-download",expireDuringCall=true))
        assertEquals("UNSUPPORTED_FILE",failure("unsupported"))
    }
    /** Runs a real temporary store under a fake clock. The recognizer always answers with an unreadable file. */
    private fun failure(key: String,expireDuringCall: Boolean=false,prepare: (TemporarySheetSourceStorage,MutableClock,Path)->Unit={ _,_,_ -> }): String {
        val accepted=client.send(importRequest(key).build(),HttpResponse.BodyHandlers.ofString())
        assertEquals(202,accepted.statusCode(),accepted.body())
        val id=json.readTree(accepted.body()).path("result").path("sheetId").asString()
        val objectKey=jdbc.queryForObject("SELECT object_key FROM sheet_source_files WHERE sheet_id=?",String::class.java,id)!!
        temporary.delete(objectKey)
        val time=MutableClock()
        val base=Files.createTempDirectory("expiry")
        TemporarySheetSourceStorage(base.resolve("spool"),"http://backend/x",time,base.resolve("multipart")).use { store ->
            val input=base.resolve("input");Files.write(input,png)
            store.put(objectKey,input,"image/png")
            prepare(store,time,assertNotNull(store.readable(store.readUrl(objectKey,Duration.ofMinutes(15)).substringAfterLast('/'))).path)
            val recognizer=RecognitionClient {
                if(expireDuringCall) { time.now=time.now.plusSeconds(1800);store.purgeExpired() }
                json.createObjectNode().put("status","FAILED").put("failReason","UNSUPPORTED_FILE")
            }
            ConversionWorker(queue,store,recognizer,builder,Clock.systemUTC(),json).runOne()
        }
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM sheet_conversions WHERE sheet_id=?",String::class.java,id))
        return jdbc.queryForObject("SELECT failure_code FROM sheet_conversions WHERE sheet_id=?",String::class.java,id)!!
    }
    private fun spool()=Files.list(temporary.multipartDirectory).use { it.toList() }
    /** The container may finish deleting parts just after the response is sent. */
    private fun awaitEmptySpool() {
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while(spool().isNotEmpty() && System.nanoTime()<deadline) Thread.sleep(20)
        assertEquals(emptyList(),spool())
    }
    private fun stalledImport(key: String): CompletableFuture<HttpResponse<String>> {
        HoldUpload.reset()
        val pending=client.sendAsync(importRequest(key).header(HOLD_HEADER,"1").build(),HttpResponse.BodyHandlers.ofString())
        assertTrue(HoldUpload.entered.await(10,TimeUnit.SECONDS))
        return pending
    }
    private fun assertUnavailable(pending: CompletableFuture<HttpResponse<String>>) {
        HoldUpload.release.countDown()
        val response=pending.get(10,TimeUnit.SECONDS)
        assertEquals(503,response.statusCode(),response.body())
        assertEquals("CONVERSION_UNAVAILABLE",json.readTree(response.body()).path("result").path("errorCode").asString())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM user_sheets WHERE user_id=993534",Int::class.java))
    }
    private fun importRequest(key: String): HttpRequest.Builder {
        val boundary="temporary534"
        val body=("--$boundary\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n"+
            "{\"title\":\"temporary\",\"notation\":\"STAFF\",\"instrumentId\":\"1\",\"bpm\":60}\r\n"+
            "--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"score.png\"\r\nContent-Type: image/png\r\n\r\n").toByteArray()+png+"\r\n--$boundary--\r\n".toByteArray()
        val jwt=SignedJWT(JWSHeader(JWSAlgorithm.HS256),JWTClaimsSet.Builder().subject("993534").expirationTime(Date.from(Instant.now().plusSeconds(60))).build())
        jwt.sign(MACSigner(SECRET.toByteArray()))
        return HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/users/me/sheets/imports"))
            .header("Cookie","access_token=${jwt.serialize()}").header("Idempotency-Key",key)
            .header("Content-Type","multipart/form-data; boundary=$boundary").POST(HttpRequest.BodyPublishers.ofByteArray(body))
    }
    class MutableClock: Clock() {
        var now: Instant=Instant.parse("2026-11-06T00:00:00Z")
        override fun instant()=now
        override fun getZone(): ZoneId=ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock=this
    }
    /** Parses the body like the dispatcher does, then holds the request so the spool can be observed mid-flight. */
    object HoldUpload: OncePerRequestFilter() {
        @Volatile var entered=CountDownLatch(1)
        @Volatile var release=CountDownLatch(1)
        fun reset() { entered=CountDownLatch(1);release=CountDownLatch(1) }
        override fun doFilterInternal(request: HttpServletRequest,response: HttpServletResponse,chain: FilterChain) {
            if(request.getHeader(HOLD_HEADER)!=null) { request.parts;entered.countDown();release.await(30,TimeUnit.SECONDS) }
            chain.doFilter(request,response)
        }
    }
    @TestConfiguration(proxyBeanMethods=false)
    class HoldUploadConfig { @Bean fun holdUpload()=FilterRegistrationBean(HoldUpload).apply { addUrlPatterns("/api/users/me/sheets/imports") } }
    companion object {
        private const val HOLD_HEADER="X-Test-Hold-Upload"
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
