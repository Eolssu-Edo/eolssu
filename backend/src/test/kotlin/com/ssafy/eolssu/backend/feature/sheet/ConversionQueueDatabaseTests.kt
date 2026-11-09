package com.ssafy.eolssu.backend.feature.sheet

import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionQueue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
import com.ssafy.eolssu.backend.feature.sheet.domain.UserSheetRepository
import com.ssafy.eolssu.backend.feature.sheet.domain.SheetScoreUpdate
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConversionQueueDatabaseTests {
    @org.junit.jupiter.api.io.TempDir lateinit var directory: Path
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var queue: ConversionQueue
    @Autowired lateinit var mapper: com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.ConversionWorkerMapper
    @Autowired lateinit var scores: SheetScores
    @Autowired lateinit var sheets: UserSheetRepository
    @Autowired lateinit var dataSource: javax.sql.DataSource
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
    @Test fun `retry never reuses discarded source and preserves ownership`() {
        assertEquals("NOT_FOUND",queue.retry(992473,992472).status)
        assertEquals("STATE_CONFLICT",queue.retry(992472,992472).status)
        queue.claim(now);queue.fail(992472,1,"TIMEOUT",now)
        assertEquals("STATE_CONFLICT",queue.retry(992472,992472).status)
        assertEquals(1,jdbc.queryForObject("SELECT attempt FROM sheet_conversions WHERE sheet_id=992472",Int::class.java))
    }
    @Test fun `restart recovery fails queued and running work without retry`() {
        queue.claim(now);queue.recover(now)
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_conversions WHERE status='FAILED' AND sheet_id IN (992472,992473)",Int::class.java))
        assertEquals("STATE_CONFLICT",queue.retry(992472,992472).status)
    }
    @Test fun `two slots recover and claim only their own conversion rows`() {
        for ((id, slot) in listOf(992472L to "backend", 992473L to "backend-green")) {
            jdbc.update("INSERT INTO sheet_source_files(sheet_id,file_idx,object_key,content_type,byte_size,file_name) VALUES(?,0,?,'image/png',3,'raw')",
                id, "sheet-import-temp/$slot/$id/raw")
        }
        val json = JsonMapper.builder().build()
        val manager = org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource)
        com.ssafy.eolssu.backend.config.DeploymentSlot("backend", directory).use { blue ->
            com.ssafy.eolssu.backend.config.DeploymentSlot("backend-green", directory).use { green ->
                val old = ConversionQueue(mapper, manager, json, blue)
                val next = ConversionQueue(mapper, manager, json, green)
                assertEquals(992472L, assertNotNull(old.claim(now)).sheetId)
                next.recover(now)
                assertEquals("RUNNING", jdbc.queryForObject("SELECT status FROM sheet_conversions WHERE sheet_id=992472", String::class.java))
                assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM sheet_conversions WHERE sheet_id=992473", String::class.java))
                old.fail(992472L, 1, "TIMEOUT", now)
                jdbc.update("UPDATE sheet_conversions SET status='QUEUED',failure_code=NULL,failure_cause=NULL,finished_at=NULL WHERE sheet_id=992473")
                assertNull(old.claim(now))
                assertEquals(992473L, assertNotNull(next.claim(now)).sheetId)
            }
        }
    }

    @Test fun `cleanup fails expired queued and running jobs and deletes temporary bytes`() {
        val json=JsonMapper.builder().build()
        val storage=LocalSheetSourceStorage(directory.resolve("expired"))
        val file=directory.resolve("raw");java.nio.file.Files.writeString(file,"raw")
        for(id in listOf(992472L,992473L)) {
            val key="sheet-import-temp/$id/raw"
            storage.put(key,file,"image/png")
            jdbc.update("INSERT INTO sheet_source_files(sheet_id,file_idx,object_key,content_type,byte_size,file_name) VALUES(?,0,?,'image/png',3,'raw')",id,key)
        }
        queue.claim(now)
        val worker=ConversionWorker(queue,storage,RecognitionClient { error("expired jobs must not invoke recognition") },RecognitionResultBuilder(json,scores),Clock.fixed(now.plusSeconds(1800),ZoneOffset.UTC),json)
        worker.cleanup()
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_conversions WHERE failure_code='TIMEOUT' AND sheet_id IN (992472,992473)",Int::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_source_files WHERE sheet_id IN (992472,992473)",Int::class.java))
        for(id in listOf(992472L,992473L)) kotlin.test.assertFalse(java.nio.file.Files.exists(directory.resolve("expired/sheet-import-temp/$id/raw")))
    }

    @Test fun `saving a converted score does not hold the sheet while waiting for its conversion`() {
        jdbc.update("UPDATE sheet_conversions SET status='DONE',finished_at=accepted_at,result_json='{\"issues\":[],\"pages\":[],\"notices\":[]}' WHERE sheet_id=992472")
        val json=JsonMapper.builder().build()
        val score=jdbc.queryForObject("SELECT score_json FROM songs WHERE id=1",String::class.java)!!
        val root=json.readTree(score)
        val update=SheetScoreUpdate(992472,992472,1,"검수 저장",null,60,root.path("music").path("bpmUnit").asString(),
            root.path("columns").size(),score,root.path("events").count { it.path("kind").asString()=="NOTE" },now,0,
            """{"issues":[],"pages":[],"notices":[]}""")
        java.util.concurrent.Executors.newSingleThreadExecutor().use { executor ->
            dataSource.connection.use { blocker ->
                blocker.autoCommit=false
                val connectionId=blocker.createStatement().use { statement -> statement.executeQuery("SELECT CONNECTION_ID()").use { it.next();it.getLong(1) } }
                blocker.createStatement().use { statement -> statement.executeQuery("SELECT sheet_id FROM sheet_conversions WHERE sheet_id=992472 FOR UPDATE").use { assertTrue(it.next()) } }
                val saved=executor.submit<Boolean> { sheets.updateScore(update) }
                try {
                    val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
                    var waiting=false
                    while(System.nanoTime()<deadline) {
                        waiting=jdbc.queryForObject("""SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                            JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID
                            JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID
                            WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='sheet_conversions' AND t.PROCESSLIST_ID=?""",Int::class.java,connectionId)!!>0
                        if(waiting) break
                        Thread.yield()
                    }
                    assertTrue(waiting,"저장 요청이 변환 행을 기다리는 지점을 실제 DB에서 관찰해야 한다")
                    // 재시도 조회의 c → s 순서를 재현한다. 저장이 먼저 s를 잡았으면 즉시 오류 3572다.
                    blocker.createStatement().use { statement -> statement.executeQuery("SELECT id FROM user_sheets WHERE id=992472 FOR UPDATE NOWAIT").use { assertTrue(it.next()) } }
                } finally { blocker.rollback() }
                assertTrue(saved.get(5,java.util.concurrent.TimeUnit.SECONDS))
            }
        }
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
        val key="sheet-import-temp/992472/original.png"
        local.put(key,source,"image/png")
        jdbc.update("INSERT INTO sheet_source_files(sheet_id,file_idx,object_key,content_type,byte_size,file_name) VALUES(992472,0,?,'image/png',?,'original.png')",key,java.nio.file.Files.size(source))
        server.start()
        try {
            val client=HttpRecognitionClient("http://127.0.0.1:${server.address.port}",json)
            val worker=ConversionWorker(queue,storage,client,RecognitionResultBuilder(json,scores),Clock.fixed(now,ZoneOffset.UTC),json)
            worker.runOne()
        } finally { server.stop(0) }
        assertEquals(1,calls)
        assertEquals("DONE",jdbc.queryForObject("SELECT status FROM sheet_conversions WHERE sheet_id=992472",String::class.java))
        assertEquals("NEEDS_REVIEW",jdbc.queryForObject("SELECT status FROM user_sheets WHERE id=992472",String::class.java))
        assertNotNull(jdbc.queryForObject("SELECT score_json FROM user_sheets WHERE id=992472",String::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_source_pages WHERE sheet_id=992472",Int::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_source_files WHERE sheet_id=992472",Int::class.java))
        kotlin.test.assertFalse(java.nio.file.Files.exists(directory.resolve("objects").resolve(key)))
        val failed=ConversionWorker(queue,storage,RecognitionClient { json.readTree("""{"status":"FAILED","failReason":"ENGINE_ERROR"}""") },RecognitionResultBuilder(json,scores),Clock.fixed(now,ZoneOffset.UTC),json)
        failed.runOne()
        assertEquals("STATE_CONFLICT",queue.retry(992473,992473).status)
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
