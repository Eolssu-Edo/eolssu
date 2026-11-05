package com.ssafy.eolssu.backend.feature.sheet

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset
import java.time.Duration
import java.nio.file.Path
import com.ssafy.eolssu.backend.feature.sheet.application.SheetScores
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetSourceStorage
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConversionQueueDatabaseTests {
    @org.junit.jupiter.api.io.TempDir lateinit var directory: Path
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var queue: ConversionQueue
    @Autowired lateinit var scores: SheetScores
    private val now = Instant.parse("2026-11-05T00:00:00Z")

    @BeforeEach fun setup() {
        cleanup()
        jdbc.update("INSERT INTO users(id,nickname,status,instrument_id) VALUES(992472,'변환 테스트','ACTIVE',1),(992473,'다른 회원','ACTIVE',1)")
        for (id in 992472L..992473L) {
            jdbc.update("""INSERT INTO user_sheets(id,user_id,title,kind,source,status,bpm,bpm_unit,column_count,idempotency_key,request_hash)
                VALUES(?,?,'악보','JEONGGANBO','STAFF_OMR','NEEDS_REVIEW',60,'QUARTER',1,'queue',REPEAT('0',64))""", id,id)
            jdbc.update("""INSERT INTO sheet_conversions(sheet_id,user_id,notation,instrument_id,status,page_count,accepted_at)
                VALUES(?,?,'STAFF',1,'QUEUED',1,'2026-11-04 00:00:00')""",id,id)
        }
    }
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM user_sheets WHERE user_id IN(992472,992473)")
        jdbc.update("DELETE FROM users WHERE id IN(992472,992473)")
    }
    @Test fun `claim is FIFO and keeps only one global running conversion`() {
        assertEquals(992472L, assertNotNull(queue.claim(now)).sheetId)
        assertNull(queue.claim(now))
        queue.fail(992472,1,"TIMEOUT",now)
        assertEquals(992473L,assertNotNull(queue.claim(now)).sheetId)
    }
    @Test fun `retry is owned idempotent and remembers timeouts`() {
        queue.claim(now)
        queue.fail(992472,1,"TIMEOUT",now)
        assertEquals("NOT_FOUND", queue.retry(992473,992472,now).status)
        val retry = queue.retry(992472,992472,now)
        assertEquals("QUEUED",retry.status)
        assertEquals(2,retry.attempt)
        assertEquals(2,queue.retry(992472,992472,now).attempt)
        // 다른 회원 작업을 끝내고 재시도 작업을 선택한다.
        val first=assertNotNull(queue.claim(now)); queue.fail(first.sheetId,first.attempt,"ENGINE_ERROR",now)
        val second=assertNotNull(queue.claim(now)); queue.fail(second.sheetId,second.attempt,"TIMEOUT",now)
        assertEquals("STATE_CONFLICT",queue.retry(992472,992472,now).status)
    }
    @Test fun `restart recovery makes running work retryable server failure`() {
        queue.claim(now)
        queue.recover(now)
        assertEquals("QUEUED",queue.retry(992472,992472,now).status)
    }
    @Test fun `worker stores a real decoded score and engine errors cannot retry`() {
        val json=JsonMapper.builder().build()
        val server=com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1",0),0)
        var calls=0
        server.createContext("/v1/recognitions") { exchange ->
            val request=json.readTree(exchange.requestBody.readBytes())
            calls++
            assertEquals(1,request.path("files").size())
            assertEquals("DANSO",request.path("instrument").asString())
            val response=json.createObjectNode().put("conversionId",request.path("conversionId").asString()).put("status","DONE")
            response.set("score",json.readTree(jdbc.queryForObject("SELECT score_json FROM songs WHERE id=1",String::class.java)))
            response.putObject("jgb").put("v",1); response.putArray("issues"); response.putArray("notices")
            val bytes=json.writeValueAsBytes(response)
            exchange.sendResponseHeaders(200,bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        val local=LocalSheetSourceStorage(directory.resolve("objects"))
        val storage=object:SheetSourceStorage by local {
            override fun readUrl(key:String,ttl:Duration)="http://fixture.invalid/source"
        }
        val source=directory.resolve("original.png")
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(3,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"PNG",source.toFile())
        val key="sheet-sources/992472/original.png"
        local.put(key,source,"image/png")
        jdbc.update("INSERT INTO sheet_source_files(sheet_id,file_idx,object_key,content_type,byte_size,file_name) VALUES(992472,0,?,'image/png',?,'original.png')",key,java.nio.file.Files.size(source))
        server.start()
        try {
            val client=HttpRecognitionClient("http://127.0.0.1:${server.address.port}",json)
            val worker=ConversionWorker(queue,storage,client,RecognitionResultBuilder(json,scores),SourcePageRenderer(storage),Clock.fixed(now,ZoneOffset.UTC),json)
            worker.runOne()
        } finally { server.stop(0) }
        assertEquals(1,calls)
        assertEquals("DONE",jdbc.queryForObject("SELECT status FROM sheet_conversions WHERE sheet_id=992472",String::class.java))
        assertEquals("NEEDS_REVIEW",jdbc.queryForObject("SELECT status FROM user_sheets WHERE id=992472",String::class.java))
        assertNotNull(jdbc.queryForObject("SELECT score_json FROM user_sheets WHERE id=992472",String::class.java))
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_source_pages WHERE sheet_id=992472",Int::class.java))
        val failed=ConversionWorker(queue,storage,RecognitionClient { json.readTree("""{"status":"FAILED","failReason":"ENGINE_ERROR"}""") },RecognitionResultBuilder(json,scores),ConversionPageRenderer { _,_,_ -> emptyList() },Clock.fixed(now,ZoneOffset.UTC),json)
        failed.runOne()
        assertEquals("STATE_CONFLICT",queue.retry(992473,992473,now).status)
    }
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("spring.datasource.url") { System.getenv("TEST_DB_URL") }
            registry.add("spring.datasource.username") { System.getenv("TEST_DB_USERNAME") }
            registry.add("spring.datasource.password") { System.getenv("TEST_DB_PASSWORD") }
        }
    }
}
