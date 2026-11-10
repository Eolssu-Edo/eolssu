package com.ssafy.eolssu.backend.feature.sheet

import com.ssafy.eolssu.backend.common.error.ErrorCode
import com.ssafy.eolssu.backend.feature.sheet.application.*
import com.ssafy.eolssu.backend.feature.sheet.domain.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.*
import com.ssafy.eolssu.backend.feature.sheet.infrastructure.persistence.SheetAudioMapper
import com.ssafy.eolssu.backend.feature.song.domain.SongJangdanPlaybackResolver
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*
import kotlin.test.assertNotNull
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import tools.jackson.databind.json.JsonMapper

@Tag("database")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(SheetAudioDatabaseTests.RoutedStorageConfig::class)
class SheetAudioDatabaseTests {
    @org.springframework.beans.factory.annotation.Value("\${local.server.port}") private var port=0
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: SheetAudioMapper
    @Autowired lateinit var sheets: UserSheetRepository
    @Autowired lateinit var scores: SheetScores
    @Autowired lateinit var copies: CopyPublishedSheet
    @Autowired lateinit var manager: PlatformTransactionManager
    @Autowired lateinit var jangdanResolver: SheetAudioJangdanResolver
    @Autowired lateinit var purge: com.ssafy.eolssu.backend.feature.auth.application.WithdrawalPurgeService
    private val json=JsonMapper.builder().build()
    private val objects=mutableSetOf<String>()
    private val downloadTtls=mutableListOf<Duration>()
    private var failPut=false
    private var failGeneration=false
    private var failDelete: (String)->Boolean={ false }
    private var deleteCrash=false
    private val storage=object: SheetSourceStorage {
        override val available=true
        override fun put(key: String,file: Path,contentType: String) { objects.add(key); if(failPut) throw SheetSourceStorageException("fixture partial write $key") }
        override fun delete(key: String) { if(deleteCrash) throw IllegalStateException("fixture crash"); if(failDelete(key)) throw SheetSourceStorageException("fixture delete failure"); objects.remove(key) }
        override fun readUrl(key: String,ttl: Duration)="https://fixture.invalid/$key"
        override fun downloadUrl(key: String,ttl: Duration,fileName: String): String {
            downloadTtls += ttl
            return readUrl(key,ttl)
        }
    }
    private lateinit var audio: SheetAudios
    private fun fakeClient(generate: (String)->GeneratedSheetAudio)=object: PlaybackClient {
        override fun estimate(requestJson: String)=1000L
        override fun generate(requestJson: String)=generate.invoke(requestJson)
    }

    @BeforeEach fun setup() {
        cleanup()
        failPut=false; failGeneration=false; failDelete={ false }; deleteCrash=false; objects.clear(); downloadTtls.clear(); routed=storage
        jdbc.update("INSERT INTO users(id,nickname,status,instrument_id) VALUES(994481,'음원 검사','ACTIVE',1),(994482,'다른 회원','ACTIVE',1)")
        jdbc.update("""INSERT INTO user_sheets(id,user_id,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,score_version,score_json,note_count,idempotency_key,request_hash)
            SELECT 994481,994481,'음원 검사','JEONGGANBO','MANUAL','EVALUABLE',s.jangdan_id,s.default_bpm,
            COALESCE(j.bpm_unit,JSON_UNQUOTE(JSON_EXTRACT(s.score_json,'$.music.bpmUnit'))),JSON_LENGTH(s.score_json,'$.columns'),
            s.score_version,s.score_json,s.note_count,'audio-fixture',REPEAT('0',64) FROM songs s LEFT JOIN jangdans j ON j.id=s.jangdan_id WHERE s.id=1""")
        audio=SheetAudios(mapper,sheets,scores,SheetAudioJangdanResolver { null },storage,fakeClient {
            if(failGeneration) throw PlaybackFailure("TIMEOUT")
            GeneratedSheetAudio(ByteArray(20),ByteArray(14),1234)
        },json,Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),ZoneOffset.UTC),manager)
    }
    @AfterEach fun cleanup() {
        jdbc.update("DELETE FROM sheet_audio_objects WHERE sheet_id IN(994481,994482)")
        jdbc.update("DELETE FROM user_sheets WHERE user_id IN(994481,994482)")
        jdbc.update("DELETE FROM users WHERE id IN(994481,994482)")
    }
    @Test fun `a published copy can request audio with its own score version`() {
        val copy = assertIs<SheetCopyResult.Created>(copies.copy(994481, 1, "audio-copy-key"))
        try {
            val queued = audio.request(994481, copy.sheetId, null, false, null)
            assertEquals("QUEUED", queued.body.status)
            assertEquals("1", queued.body.scoreVersion)
            audio.runOne()
            val result = audio.get(994481, copy.sheetId)
            assertEquals("DONE", result.status)
            assertNotNull(result.audio)
            assertNull(result.midi)
        } finally {
            audio.delete(994481, copy.sheetId)
        }
    }

    @Test fun `owned queued request publishes only M4A and identical request reuses it`() {
        assertEquals("QUEUED",audio.request(994481,994481,null,false,null).body.status)
        assertEquals(ErrorCode.AUDIO_IN_PROGRESS,assertFailsWith<SheetAudioRejection> { audio.request(994481,994481,null,false,null) }.code)
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,assertFailsWith<SheetAudioRejection> { audio.get(994482,994481) }.code)
        audio.runOne()
        assertEquals("DONE",audio.get(994481,994481).status)
        assertTrue(objects.single().endsWith("/audio.m4a"))
        assertTrue(audio.request(994481,994481,null,false,null).reused)
        assertTrue(objects.single().endsWith("/audio.m4a"))
    }

    @Test fun `download expires at a fixed deadline and only a new request renders again`() {
        val clock = MovingClock(Instant.parse("2026-11-05T00:00:00Z"))
        var renders = 0
        val worker = audioWith(fakeClient { renders++; made() }, clock)
        worker.request(994481, 994481, null, false, null)
        clock.now = clock.now.plusSeconds(5)
        worker.runOne()
        val deadline = clock.now.plus(Duration.ofMinutes(10))
        val first = worker.get(994481, 994481)
        assertEquals(deadline, assertNotNull(first.audio).urlExpiresAt)
        assertNull(first.midi)
        val oldKey = objects.single()

        clock.now = deadline.minusSeconds(40)
        assertEquals(deadline, assertNotNull(worker.get(994481, 994481).audio).urlExpiresAt)
        assertTrue(worker.request(994481, 994481, null, false, null).reused)
        assertEquals(1, renders)
        assertEquals(listOf(Duration.ofMinutes(10), Duration.ofSeconds(40), Duration.ofSeconds(40)), downloadTtls)

        clock.now = deadline
        val expired = worker.get(994481, 994481)
        assertEquals("FAILED", expired.status)
        assertEquals("AUDIO_EXPIRED", expired.failureCode)
        assertNull(expired.audio)
        worker.runOne()
        assertTrue(objects.isEmpty())
        assertEquals(1, renders)

        assertFalse(worker.request(994481, 994481, null, false, null).reused)
        worker.runOne()
        assertEquals(2, renders)
        assertNotEquals(oldKey, objects.single())
        assertNotNull(worker.get(994481, 994481).audio)
    }
    @Test fun `cleanup retries deletion and preserves a newer queued request when its previous download expires`() {
        val clock = MovingClock(Instant.parse("2026-11-05T00:00:00Z"))
        val worker = audioWith(fakeClient { made() }, clock)
        worker.request(994481, 994481, null, false, null)
        worker.runOne()
        val oldKey = objects.single()
        clock.now = clock.now.plusSeconds(590)
        worker.request(994481, 994481, null, false, 90)
        assertNotNull(worker.get(994481, 994481).audio)

        clock.now = clock.now.plusSeconds(10)
        failDelete = { it == oldKey }
        worker.cleanup()
        assertEquals(setOf(oldKey), objects)
        val queued = worker.get(994481, 994481)
        assertEquals("QUEUED", queued.status)
        assertNull(queued.failureCode)
        assertNull(queued.audio)
        assertEquals(listOf(oldKey), mapper.retiredForSheet(994481))

        failDelete = { false }
        worker.runOne()
        assertNotEquals(oldKey, objects.single())
        assertEquals("DONE", worker.get(994481, 994481).status)
        assertEquals(90, worker.get(994481, 994481).bpm)
        assertTrue(mapper.retiredForSheet(994481).isEmpty())
    }

    @Test fun `legacy files receive no new links and retire when their generation is replaced`() {
        audio.request(994481, 994481, null, false, null)
        audio.runOne()
        val row = assertNotNull(mapper.find(994481, 994481))
        val oldAudioKey = objects.single()
        val oldMidiKey = oldAudioKey.substringBeforeLast('/') + "/score.mid"
        mapper.objectPending(oldMidiKey, 994481, row.generation)
        mapper.resolveObjects(row.generation, "CURRENT")
        objects.add(oldMidiKey)
        jdbc.update("""UPDATE sheet_audios SET result_expires_at=NULL,
            result_json=JSON_SET(result_json,'$.midi',JSON_OBJECT('key',?,'byteSize',14,'fileName','old.mid'))
            WHERE sheet_id=994481""", oldMidiKey)

        val legacy = audio.get(994481, 994481)
        assertEquals("AUDIO_EXPIRED", legacy.failureCode)
        assertNull(legacy.audio)
        assertNull(legacy.midi)
        assertTrue(downloadTtls.isEmpty())
        audio.cleanup()
        assertEquals(setOf(oldAudioKey, oldMidiKey), objects)

        assertFalse(audio.request(994481, 994481, null, false, null).reused)
        audio.runOne()
        assertNotEquals(oldAudioKey, objects.single())
        assertNull(audio.get(994481, 994481).midi)
    }

    @Test fun `failed replacement preserves the previous unexpired download until successful replacement`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        val previous=objects.toSet()
        failGeneration=true
        audio.request(994481,994481,null,false,90); audio.runOne()
        val failed=audio.get(994481,994481)
        assertEquals("FAILED",failed.status)
        assertEquals("TIMEOUT",failed.failureCode)
        assertNotNull(failed.audio)
        assertEquals(previous,objects)
        failGeneration=false
        audio.request(994481,994481,null,false,90); audio.runOne()
        assertEquals(1,objects.size)
        assertTrue(objects.intersect(previous).isEmpty())
    }
    @Test fun `partial object upload failure is compensated and deletion clears the completed download`() {
        failPut=true
        audio.request(994481,994481,null,false,null); audio.runOne()
        assertEquals("FAILED",audio.get(994481,994481).status)
        assertTrue(objects.isEmpty())
        failPut=false
        audio.request(994481,994481,null,false,null); audio.runOne()
        audio.delete(994481,994481)
        assertTrue(objects.isEmpty())
        assertEquals(ErrorCode.STATE_CONFLICT,assertFailsWith<SheetAudioRejection> { audio.request(994481,994481,null,false,null) }.code)
    }
    @Test fun `deleting during rendering never publishes a completed file and restart clears running work`() {
        audio.request(994481,994481,null,false,null)
        val deleting=SheetAudios(mapper,sheets,scores,SheetAudioJangdanResolver { null },storage,fakeClient {
            audio.delete(994481,994481)
            GeneratedSheetAudio(ByteArray(20),ByteArray(14),1234)
        },json,Clock.systemUTC(),manager)
        deleting.runOne()
        assertTrue(objects.isEmpty())
        assertEquals("FAILED",audio.get(994481,994481).status)
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_audio_objects WHERE sheet_id=994481",Int::class.java))
    }
    @Test fun `rate cap counts rejected duplicate requests and changed sheet versions are outdated`() {
        audio.request(994481,994481,null,false,null)
        repeat(9) { assertEquals(ErrorCode.AUDIO_IN_PROGRESS,assertFailsWith<SheetAudioRejection> { audio.request(994481,994481,null,false,null) }.code) }
        assertEquals(ErrorCode.TOO_MANY_REQUESTS,assertFailsWith<SheetAudioRejection> { audio.request(994481,994481,null,false,null) }.code)
        audio.runOne()
        jdbc.update("UPDATE user_sheets SET score_version=score_version+1 WHERE id=994481")
        assertEquals(true,audio.get(994481,994481).outdated)
    }
    @Test fun `restart recovery retains successful files and retires interrupted pending objects`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        val previous=objects.toSet()
        audio.request(994481,994481,null,false,90)
        val row=mapper.find(994481,994481)!!
        jdbc.update("UPDATE sheet_audios SET status='RUNNING' WHERE sheet_id=994481")
        mapper.objectPending("sheet-audio/994481/interrupted",994481,row.generation)
        objects.add("sheet-audio/994481/interrupted")
        audio.recover()
        assertEquals(previous,objects)
        assertEquals("FAILED",audio.get(994481,994481).status)
        assertNotNull(audio.get(994481,994481).audio)
    }
    private fun send(user: Long?,body: String?,method: String="POST",path: String="/api/users/me/sheets/994481/audio"): java.net.http.HttpResponse<String> {
        val builder=java.net.http.HttpRequest.newBuilder(java.net.URI("http://localhost:$port$path"))
            .header("Content-Type","application/json")
        if(user!=null) {
            val token=com.nimbusds.jwt.SignedJWT(com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                com.nimbusds.jwt.JWTClaimsSet.Builder().subject(user.toString()).expirationTime(java.util.Date.from(Instant.now().plusSeconds(60))).build())
            token.sign(com.nimbusds.jose.crypto.MACSigner("sheet-audio-api-test-signing-key-481".toByteArray()))
            builder.header("Cookie","access_token=${token.serialize()}")
        }
        builder.method(method,body?.let { java.net.http.HttpRequest.BodyPublishers.ofString(it) } ?: java.net.http.HttpRequest.BodyPublishers.noBody())
        return java.net.http.HttpClient.newHttpClient().send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString())
    }
    private fun deleteSheet()=send(994481,null,"DELETE","/api/users/me/sheets/994481")
    private fun remoteStatus()=json.readTree(send(994481,null,"GET").body()).path("result")
    private fun deleting()=jdbc.queryForObject("SELECT deleting FROM sheet_audios WHERE sheet_id=994481",Boolean::class.java)
    private fun sheetCount()=jdbc.queryForObject("SELECT COUNT(*) FROM user_sheets WHERE id=994481",Int::class.java)
    private fun made()=GeneratedSheetAudio(ByteArray(20),ByteArray(14),1234)

    @Test fun `accepted and reused responses say what happened`() {
        val jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg","none").subject("994481").build()
        val controller=com.ssafy.eolssu.backend.feature.sheet.api.SheetAudioController(audio)
        val accepted=controller.request(jwt,"994481",null)
        assertEquals(202,accepted.statusCode.value())
        assertContains(json.writeValueAsString(accepted.body),"음원 생성 요청 접수")
        audio.runOne()
        val reused=controller.request(jwt,"994481",null)
        assertEquals(200,reused.statusCode.value())
        assertContains(json.writeValueAsString(reused.body),"음원 상태 조회 성공")
    }
    @Test fun `a retained file from an older version stays outdated while its replacement runs and after it fails`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        val latest=jdbc.queryForObject("SELECT score_version+1 FROM user_sheets WHERE id=994481",Int::class.java)
        jdbc.update("UPDATE user_sheets SET score_version=?,score_json=JSON_SET(score_json,'$.score.scoreVersion',?) WHERE id=994481",latest,latest)
        failGeneration=true
        val queued=audio.request(994481,994481,null,false,null).body
        assertEquals("QUEUED",queued.status)
        assertTrue(queued.outdated)
        assertNotNull(queued.audio)
        audio.runOne()
        val failed=audio.get(994481,994481)
        assertEquals("FAILED",failed.status)
        assertEquals(latest.toString(),failed.scoreVersion)
        assertTrue(failed.outdated)
        assertNotNull(failed.audio)
        failGeneration=false
        audio.request(994481,994481,null,false,null); audio.runOne()
        assertFalse(audio.get(994481,994481).outdated)
    }
    @Test fun `a failed first generation of the current version has no file and is not outdated`() {
        failGeneration=true
        audio.request(994481,994481,null,false,null); audio.runOne()
        val failed=audio.get(994481,994481)
        assertNull(failed.audio)
        assertFalse(failed.outdated)
    }
    @Test fun `files being deleted are never signed again`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        jdbc.update("UPDATE sheet_audios SET deleting=TRUE WHERE sheet_id=994481")
        val view=audio.get(994481,994481)
        assertNull(view.audio)
        assertNull(view.midi)
    }
    @Test fun `a failed audio file deletion answers 503 and leaves the sheet usable and deletable`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        failDelete={ true }
        assertEquals(503,deleteSheet().statusCode())
        failDelete={ false }
        assertEquals(1,sheetCount())
        assertEquals(false,deleting())
        assertFalse(remoteStatus().path("audio").isObject)
        audio.request(994481,994481,null,false,null); audio.runOne()
        assertEquals("DONE",audio.get(994481,994481).status)
        assertEquals(1,objects.size)
        assertEquals(204,deleteSheet().statusCode())
        assertEquals(0,sheetCount())
        assertTrue(objects.isEmpty())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_audio_objects WHERE sheet_id=994481",Int::class.java))
    }
    @Test fun `a failed source file deletion after the audio is cleared leaves the sheet usable`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        val source="user-sheets/994481/source.pdf"
        jdbc.update("INSERT INTO sheet_source_files(sheet_id,file_idx,object_key,content_type,byte_size,file_name) VALUES(994481,0,?,'application/pdf',1,'a.pdf')",source)
        failDelete={ it==source }
        assertEquals(503,deleteSheet().statusCode())
        failDelete={ false }
        assertEquals(1,sheetCount())
        assertEquals(false,deleting())
        assertTrue(objects.isEmpty())
        assertFalse(remoteStatus().path("audio").isObject)
        assertEquals(204,deleteSheet().statusCode())
        assertEquals(0,sheetCount())
    }
    @Test fun `deleting while an upload is finishing answers 503 and succeeds once it settles`() {
        audio.request(994481,994481,null,false,null)
        val generation=mapper.find(994481,994481)!!.generation
        mapper.objectPending("sheet-audio/994481/uploading",994481,generation)
        assertEquals(503,deleteSheet().statusCode())
        assertEquals(false,deleting())
        assertEquals(1,sheetCount())
        mapper.resolveObjects(generation,"RETIRED")
        assertEquals(204,deleteSheet().statusCode())
        assertEquals(0,sheetCount())
    }
    @Test fun `a busy generator keeps the job queued instead of failing it`() {
        var busy=true
        val worker=audioWith(fakeClient { if(busy) throw PlaybackFailure("BUSY"); made() })
        worker.request(994481,994481,null,false,null)
        worker.runOne()
        val waiting=worker.get(994481,994481)
        assertEquals("QUEUED",waiting.status)
        assertNull(waiting.failureCode)
        assertNull(mapper.find(994481,994481)!!.startedAt)
        busy=false
        worker.runOne()
        assertEquals("DONE",worker.get(994481,994481).status)
    }
    @Test fun `a running job left behind past its deadline is failed so generation resumes`() {
        audio.request(994481,994481,null,false,null)
        val generation=mapper.find(994481,994481)!!.generation
        jdbc.update("UPDATE sheet_audios SET status='RUNNING',started_at='2026-11-04 23:40:00' WHERE sheet_id=994481")
        mapper.objectPending("sheet-audio/994481/left",994481,generation); objects.add("sheet-audio/994481/left")
        audio.runOne()
        assertEquals("FAILED",audio.get(994481,994481).status)
        assertEquals("SERVER_ERROR",audio.get(994481,994481).failureCode)
        audio.runOne()
        assertTrue(objects.isEmpty())
        audio.request(994481,994481,null,false,null); audio.runOne()
        assertEquals("DONE",audio.get(994481,994481).status)
    }
    @Test fun `a running job within its deadline is left alone and still holds the slot`() {
        jdbc.update("""INSERT INTO user_sheets(id,user_id,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,score_version,score_json,note_count,idempotency_key,request_hash)
            SELECT 994482,994482,title,kind,source,status,jangdan_id,bpm,bpm_unit,column_count,score_version,score_json,note_count,'audio-fixture-2',request_hash FROM user_sheets WHERE id=994481""")
        audio.request(994481,994481,null,false,null)
        audio.request(994482,994482,null,false,null)
        jdbc.update("UPDATE sheet_audios SET status='RUNNING',started_at='2026-11-04 23:55:00' WHERE sheet_id=994481")
        audio.runOne()
        assertEquals("RUNNING",audio.get(994481,994481).status)
        assertEquals("QUEUED",audio.get(994482,994482).status)
    }
    @Test fun `a generator that stays busy past the deadline fails the job instead of holding the member forever`() {
        val clock=MovingClock(Instant.parse("2026-11-05T00:00:00Z"))
        val worker=audioWith(fakeClient { throw PlaybackFailure("BUSY") },clock)
        worker.request(994481,994481,null,false,null)
        worker.runOne()
        clock.now=clock.now.plus(Duration.ofMinutes(9))
        worker.runOne()
        assertEquals("QUEUED",worker.get(994481,994481).status)
        clock.now=clock.now.plus(Duration.ofMinutes(1))
        worker.runOne()
        assertEquals("FAILED",worker.get(994481,994481).status)
        assertEquals("SERVER_ERROR",worker.get(994481,994481).failureCode)
    }
    @Test fun `deleting during a busy answer does not put the cancelled job back in the queue`() {
        val worker=audioWith(fakeClient { audio.delete(994481,994481); throw PlaybackFailure("BUSY") })
        worker.request(994481,994481,null,false,null)
        worker.runOne()
        assertEquals("FAILED",worker.get(994481,994481).status)
    }
    @Test fun `a job finishing after a failed deletion cannot replace the request made since`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        val worker=audioWith(fakeClient {
            failDelete={ true }
            assertEquals(503,deleteSheet().statusCode())
            failDelete={ false }
            audio.request(994481,994481,null,false,91)
            made()
        })
        worker.request(994481,994481,null,false,90)
        worker.runOne()
        val waiting=audio.get(994481,994481)
        assertEquals("QUEUED",waiting.status)
        assertEquals(91,waiting.bpm)
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_audio_objects WHERE state='CURRENT' AND sheet_id=994481",Int::class.java))
        audio.runOne(); audio.runOne()
        assertEquals("DONE",audio.get(994481,994481).status)
        assertEquals(1,objects.size)
    }
    @Test fun `an unexpected deletion failure also leaves the sheet usable`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        deleteCrash=true
        assertEquals(500,deleteSheet().statusCode())
        deleteCrash=false
        assertEquals(1,sheetCount())
        assertEquals(false,deleting())
        assertFalse(remoteStatus().path("audio").isObject)
    }
    @Test fun `a second deletion cannot release the first deletion while it owns the sheet`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val calls=java.util.concurrent.atomic.AtomicInteger()
        failDelete={
            if(calls.incrementAndGet()==1) {
                entered.countDown()
                check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))
                true
            } else false
        }
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        val first=executor.submit<java.net.http.HttpResponse<String>> { deleteSheet() }
        try {
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(503,deleteSheet().statusCode())
            assertEquals(true,deleting())
            val response=send(994481,"""{"bpm":91}""")
            assertEquals(409,response.statusCode(),response.body())
            assertEquals("STATE_CONFLICT",json.readTree(response.body()).path("result").path("errorCode").asString())
        } finally {
            release.countDown()
            first.get(10,java.util.concurrent.TimeUnit.SECONDS)
            executor.shutdownNow()
        }
        failDelete={ false }
        assertEquals(1,sheetCount())
        assertEquals(false,deleting())
        assertEquals(202,send(994481,"""{"bpm":91}""").statusCode())
    }
    @Test fun `deleting a sheet without an audio row still blocks its first generation`() {
        val source="sheet-sources/994481/deleting.png"
        objects.add(source)
        jdbc.update("INSERT INTO sheet_source_files(sheet_id,file_idx,object_key,content_type,byte_size,file_name) VALUES(994481,0,?,'image/png',1,'deleting.png')",source)
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        failDelete={ key ->
            if(key==source) { entered.countDown(); check(release.await(10,java.util.concurrent.TimeUnit.SECONDS)) }
            false
        }
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        val deletion=executor.submit<java.net.http.HttpResponse<String>> { deleteSheet() }
        try {
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val response=send(994481,"{}")
            assertEquals(409,response.statusCode(),response.body())
            assertEquals("STATE_CONFLICT",json.readTree(response.body()).path("result").path("errorCode").asString())
        } finally {
            release.countDown()
            deletion.get(10,java.util.concurrent.TimeUnit.SECONDS)
            executor.shutdownNow()
        }
        assertEquals(0,sheetCount())
        assertTrue(objects.isEmpty())
    }
    @Test fun `restart releases a deletion that never finished`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        audio.delete(994481,994481)
        assertEquals(true,deleting())
        audio.recover()
        assertEquals(false,deleting())
        assertNull(audio.get(994481,994481).audio)
        assertEquals("QUEUED",audio.request(994481,994481,null,false,null).body.status)
    }
    @Test fun `unexpected generation failures log the error type without its message`() {
        val logger=org.slf4j.LoggerFactory.getLogger(SheetAudios::class.java) as ch.qos.logback.classic.Logger
        val appender=ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            failPut=true
            audio.request(994481,994481,null,false,null); audio.runOne()
        } finally { logger.detachAppender(appender) }
        val event=appender.list.single()
        assertContains(event.formattedMessage,"SheetSourceStorageException")
        assertFalse(event.formattedMessage.contains("sheet-audio/"))
        assertNull(event.throwableProxy)
    }
    @Test fun `generation failures record the reason without score content`() {
        val logger=org.slf4j.LoggerFactory.getLogger(SheetAudios::class.java) as ch.qos.logback.classic.Logger
        val appender=ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            failGeneration=true
            audio.request(994481,994481,null,false,null); audio.runOne()
        } finally { logger.detachAppender(appender) }
        val message=appender.list.single().formattedMessage
        assertContains(message,"TIMEOUT")
        assertContains(message,"994481")
        assertFalse(message.contains("schemaVersion"))
    }
    @Test fun `with jangdan sends the catalog pattern of the sheet to the generator`() {
        jdbc.update("UPDATE user_sheets SET jangdan_id=(SELECT MIN(id) FROM jangdans) WHERE id=994481")
        var sent=""
        val worker=SheetAudios(mapper,sheets,scores,jangdanResolver,storage,fakeClient { sent=it; made() },json,Clock.systemUTC(),manager)
        val accepted=worker.request(994481,994481,null,true,null).body
        assertEquals(true,accepted.withJangdan)
        worker.runOne()
        assertEquals("DONE",worker.get(994481,994481).status)
        val request=json.readTree(sent)
        assertEquals(true,request.path("withJangdan").booleanValue())
        val expected=jdbc.queryForMap("SELECT j.name,j.bpm_unit FROM jangdans j JOIN user_sheets s ON s.jangdan_id=j.id WHERE s.id=994481")
        assertEquals(expected["name"],request.path("jangdan").path("name").asString())
        assertEquals(expected["bpm_unit"],request.path("jangdan").path("bpmUnit").asString())
        assertFalse(request.path("jangdan").path("pattern").path("beats").isEmpty)
        assertTrue(worker.request(994481,994481,null,true,null).reused)
        assertFalse(worker.request(994481,994481,null,false,null).reused)
    }
    private fun dueForPurge()=jdbc.update("UPDATE users SET status='WITHDRAWAL_PENDING',withdrawal_requested_at='2026-01-01 00:00:00',purge_scheduled_at='2026-01-02 00:00:00' WHERE id=994481")
    @Test fun `withdrawal removes stored audio and the rate row before the member is purged`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        dueForPurge()
        assertTrue(purge.purgeDue().purged>=1)
        assertTrue(objects.isEmpty())
        assertEquals(0,sheetCount())
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_audio_objects WHERE sheet_id=994481",Int::class.java))
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_audio_requests WHERE user_id=994481",Int::class.java))
    }
    @Test fun `withdrawal is deferred while stored audio cannot be deleted`() {
        audio.request(994481,994481,null,false,null); audio.runOne()
        dueForPurge()
        failDelete={ true }
        assertTrue(purge.purgeDue().deferred>=1)
        assertEquals(1,sheetCount())
        assertEquals("WITHDRAWAL_PENDING",jdbc.queryForObject("SELECT status FROM users WHERE id=994481",String::class.java))
        failDelete={ false }
        assertTrue(purge.purgeDue().purged>=1)
        assertTrue(objects.isEmpty())
    }
    @Test fun `actual API requires authentication preserves ownership and validates JSON types`() {
        assertEquals(401,send(null,"{}").statusCode())
        assertEquals(404,send(994482,"{}").statusCode())
        assertEquals(400,send(994481,"""{"bpm":"60"}""").statusCode())
        assertEquals(400,send(994481,"""{"withJangdan":"false"}""").statusCode())
        audio.request(994481,994481,null,false,null)
        val status=send(994481,null,"GET")
        assertEquals(200,status.statusCode(),status.body())
        assertEquals("QUEUED",json.readTree(status.body()).path("result").path("status").asString())
        assertTrue(status.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
    }
    private fun audioWith(client: PlaybackClient,clock: Clock=Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),ZoneOffset.UTC))=
        SheetAudios(mapper,sheets,scores,SheetAudioJangdanResolver { null },storage,client,json,clock,manager)
    private class MovingClock(var now: Instant): Clock() {
        override fun getZone()=ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId)=this
        override fun instant()=now
    }
    @Test fun `playback HTTP calls run outside the member and sheet locks`() {
        val inTransaction=mutableListOf<Boolean>()
        fun record()=inTransaction.add(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
        val observed=audioWith(object: PlaybackClient {
            override val available: Boolean get() { record(); return true }
            override fun estimate(requestJson: String): Long { record(); return 1000L }
            override fun generate(requestJson: String)=GeneratedSheetAudio(ByteArray(20),ByteArray(14),1234)
        })
        assertEquals("QUEUED",observed.request(994481,994481,null,false,null).body.status)
        assertEquals(listOf(false,false),inTransaction)
    }
    @Test fun `a sheet saved during the estimate is not queued under the measured snapshot`() {
        val raced=audioWith(object: PlaybackClient {
            override fun estimate(requestJson: String): Long {
                jdbc.update("UPDATE user_sheets SET bpm=bpm+1 WHERE id=994481")
                return 1000L
            }
            override fun generate(requestJson: String)=GeneratedSheetAudio(ByteArray(20),ByteArray(14),1234)
        })
        assertEquals(ErrorCode.STATE_CONFLICT,assertFailsWith<SheetAudioRejection> { raced.request(994481,994481,null,false,null) }.code)
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sheet_audios WHERE sheet_id=994481",Int::class.java))
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PLAYBACK_TEST_BASE_URL",matches=".+")
    fun `real CAT HTTP generation stores only M4A and exposes its signed download metadata`() {
        val real=SheetAudios(mapper,sheets,scores,SheetAudioJangdanResolver { null },storage,
            HttpPlaybackClient(System.getenv("PLAYBACK_TEST_BASE_URL"),Duration.ofSeconds(100),json),json,Clock.systemUTC(),manager)
        assertFalse(real.request(994481,994481,null,false,null).reused)
        real.runOne()
        val result=real.get(994481,994481)
        assertEquals("DONE",result.status,result.toString())
        val file=assertNotNull(result.audio)
        assertEquals("음원 검사.m4a",file.fileName)
        assertTrue(assertNotNull(file.durationMs)>0)
        assertTrue(assertNotNull(file.sha256).matches(Regex("[0-9a-f]{64}")))
        assertEquals(1,objects.size)
        assertNull(result.midi)
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="PLAYBACK_TEST_BASE_URL",matches=".+")
    fun `real CAT HTTP generation mixes the catalog jangdan of the sheet`() {
        jdbc.update("UPDATE user_sheets SET jangdan_id=(SELECT MIN(id) FROM jangdans) WHERE id=994481")
        val real=SheetAudios(mapper,sheets,scores,jangdanResolver,storage,
            HttpPlaybackClient(System.getenv("PLAYBACK_TEST_BASE_URL"),Duration.ofSeconds(100),json),json,Clock.systemUTC(),manager)
        real.request(994481,994481,null,true,null)
        real.runOne()
        val result=real.get(994481,994481)
        assertEquals("DONE",result.status,result.toString())
        assertEquals(true,result.withJangdan)
        assertNotNull(result.audio)
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    class RoutedStorageConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        fun routedPlaybackClient()=object: PlaybackClient {
            override fun estimate(requestJson: String)=1000L
            override fun generate(requestJson: String)=GeneratedSheetAudio(ByteArray(20),ByteArray(14),1234)
        }
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        fun routedStorage()=object: SheetSourceStorage {
            override val available=true
            override fun put(key: String,file: Path,contentType: String)=routed.put(key,file,contentType)
            override fun delete(key: String)=routed.delete(key)
            override fun readUrl(key: String,ttl: Duration)=routed.readUrl(key,ttl)
        }
    }
    companion object {
        lateinit var routed: SheetSourceStorage
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { "sheet-audio-api-test-signing-key-481" }
            registry.add("spring.datasource.url") { System.getenv("TEST_DB_URL") ?: error("TEST_DB_URL required") }
            registry.add("spring.datasource.username") { System.getenv("TEST_DB_USERNAME") ?: "root" }
            registry.add("spring.datasource.password") { System.getenv("TEST_DB_PASSWORD") ?: "" }
        }
    }
}
