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
class SheetAudioDatabaseTests {
    @org.springframework.beans.factory.annotation.Value("\${local.server.port}") private var port=0
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: SheetAudioMapper
    @Autowired lateinit var sheets: UserSheetRepository
    @Autowired lateinit var scores: SheetScores
    @Autowired lateinit var manager: PlatformTransactionManager
    private val json=JsonMapper.builder().build()
    private val objects=mutableSetOf<String>()
    private var failPut=false
    private var failGeneration=false
    private val storage=object: SheetSourceStorage {
        override val available=true
        override fun put(key: String,file: Path,contentType: String) { objects.add(key); if(failPut && key.endsWith(".mid")) throw SheetSourceStorageException("fixture partial write") }
        override fun delete(key: String) { objects.remove(key) }
        override fun readUrl(key: String,ttl: Duration)="https://fixture.invalid/$key"
    }
    private lateinit var audio: SheetAudios
    private fun fakeClient(generate: (String)->GeneratedSheetAudio)=object: PlaybackClient {
        override fun estimate(requestJson: String)=1000L
        override fun generate(requestJson: String)=generate.invoke(requestJson)
    }

    @BeforeEach fun setup() {
        cleanup()
        failPut=false; failGeneration=false; objects.clear()
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
        jdbc.update("DELETE FROM sheet_audio_objects WHERE sheet_id=994481")
        jdbc.update("DELETE FROM user_sheets WHERE user_id IN(994481,994482)")
        jdbc.update("DELETE FROM users WHERE id IN(994481,994482)")
    }
    @Test fun `owned queued request becomes done and identical request reuses the same pair`() {
        assertEquals("QUEUED",audio.request(994481,994481,null,false,null).body.status)
        assertEquals(ErrorCode.AUDIO_IN_PROGRESS,assertFailsWith<SheetAudioRejection> { audio.request(994481,994481,null,false,null) }.code)
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,assertFailsWith<SheetAudioRejection> { audio.get(994482,994481) }.code)
        audio.runOne()
        assertEquals("DONE",audio.get(994481,994481).status)
        assertEquals(2,objects.size)
        assertTrue(audio.request(994481,994481,null,false,null).reused)
        assertEquals(2,objects.size)
    }
    @Test fun `failed replacement preserves previous urls then successful replacement retires only old pair`() {
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
        assertEquals(2,objects.size)
        assertTrue(objects.intersect(previous).isEmpty())
    }
    @Test fun `partial object upload failure is compensated and deletion clears the successful pair`() {
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
    @Test fun `actual API requires authentication preserves ownership and validates JSON types`() {
        val client=java.net.http.HttpClient.newHttpClient()
        fun send(user: Long?,body: String?,method: String="POST"): java.net.http.HttpResponse<String> {
            val builder=java.net.http.HttpRequest.newBuilder(java.net.URI("http://localhost:$port/api/users/me/sheets/994481/audio"))
                .header("Content-Type","application/json")
            if(user!=null) {
                val token=com.nimbusds.jwt.SignedJWT(com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
                    com.nimbusds.jwt.JWTClaimsSet.Builder().subject(user.toString()).expirationTime(java.util.Date.from(Instant.now().plusSeconds(60))).build())
                token.sign(com.nimbusds.jose.crypto.MACSigner("sheet-audio-api-test-signing-key-481".toByteArray()))
                builder.header("Cookie","access_token=${token.serialize()}")
            }
            builder.method(method,body?.let { java.net.http.HttpRequest.BodyPublishers.ofString(it) } ?: java.net.http.HttpRequest.BodyPublishers.noBody())
            return client.send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString())
        }
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
    private fun audioWith(client: PlaybackClient)=SheetAudios(mapper,sheets,scores,SheetAudioJangdanResolver { null },storage,client,json,
        Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"),ZoneOffset.UTC),manager)
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
    fun `real CAT HTTP generation persists a pair and exposes its signed download metadata`() {
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
        assertEquals(2,objects.size)
    }
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(registry: DynamicPropertyRegistry) {
            registry.add("eolssu.scheduling.enabled") { "false" }
            registry.add("eolssu.security.jwt-secret") { "sheet-audio-api-test-signing-key-481" }
            registry.add("spring.datasource.url") { System.getenv("TEST_DB_URL") ?: error("TEST_DB_URL required") }
            registry.add("spring.datasource.username") { System.getenv("TEST_DB_USERNAME") ?: "root" }
            registry.add("spring.datasource.password") { System.getenv("TEST_DB_PASSWORD") ?: "" }
        }
    }
}
