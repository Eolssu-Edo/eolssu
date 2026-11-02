package com.ssafy.eolssu.backend.feature.song

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** Opt-in corpus test: external catalog snapshot only; no media or production credentials in Git. */
@Tag("database")
@EnabledIfEnvironmentVariable(named="MODEL_AUDIO_CORPUS_SNAPSHOT", matches=".+")
@ActiveProfiles("db")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModelAudioCorpusApiDatabaseTests {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Value("\${local.server.port}") var port=0
    private val json=JsonMapper.builder().build()
    private val client=HttpClient.newHttpClient()
    private val songs=mutableListOf<Pair<Long,JsonNode>>()
    private val expectedHashes=mutableMapOf<Long,Pair<String,String>>()
    private val instruments=mutableListOf<Long>()
    private val pitches=mutableListOf<Long>()

    @BeforeAll fun insertCorpus() {
        val tables=json.readTree(Files.readString(Path.of(System.getenv("MODEL_AUDIO_CORPUS_SNAPSHOT")))).path("tables")
        val pitchIds=tables.path("pitches").associate { row ->
            val known=jdbc.queryForList("SELECT id FROM pitches WHERE yul=? AND octave=?",Long::class.java,row.path("yul").asString(),row.path("octave").asInt())
            val id=known.firstOrNull() ?: run {
                jdbc.update("INSERT INTO pitches(yul,octave,hanja,hangul,semitone) VALUES(?,?,?,?,?)",row.path("yul").asString(),row.path("octave").asInt(),row.path("hanja").asString(),row.path("hangul").asString(),row.path("semitone").asInt())
                jdbc.queryForObject("SELECT id FROM pitches WHERE yul=? AND octave=?",Long::class.java,row.path("yul").asString(),row.path("octave").asInt())!!.also { pitches+=it }
            }
            row.path("id").asLong() to id
        }
        val instrumentIds=tables.path("instruments").associate { row ->
            val code=row.path("code").asString()
            val known=jdbc.queryForList("SELECT id FROM instruments WHERE code=?",Long::class.java,code)
            val id=known.firstOrNull() ?: run {
                jdbc.update("INSERT INTO instruments(code,name,range_low_pitch_id,range_high_pitch_id,pitch_tolerance_cent,is_active) VALUES(?,?,?,?,?,FALSE)",code,row.path("name").asString(),pitchIds.getValue(row.path("range_low_pitch_id").asLong()),pitchIds.getValue(row.path("range_high_pitch_id").asLong()),row.path("pitch_tolerance_cent").asInt())
                jdbc.queryForObject("SELECT id FROM instruments WHERE code=?",Long::class.java,code)!!.also { instruments+=it }
            }
            row.path("id").asLong() to id
        }
        val audios=tables.path("song_model_audios").associateBy { it.path("song_id").asLong() }
        for (row in tables.path("songs").filter { it.path("source").asString().startsWith("mr203-fixed-") }) {
            val id=990_000_000L+row.path("id").asLong()
            val metadata=json.readTree(row.path("description").asString())
            val score=row.path("score_json")
            val audio=audios.getValue(row.path("id").asLong())
            expectedHashes[id]=audio.path("sha256").asString() to metadata.path("sourceCommonSha256").asString()
            val key="model-audio/"+audio.path("audio_url").asString().substringAfter("/model-audio/")
            jdbc.update("""INSERT INTO songs(id,instrument_id,jangdan_id,title,description,difficulty,default_bpm,score_json,score_version,note_count,source,is_scorable,status)
                VALUES(?,?,?,?,NULL,?,?,CAST(? AS JSON),?,?,?,FALSE,'DRAFT')""",id,instrumentIds.getValue(row.path("instrument_id").asLong()),
                row.path("jangdan_id").takeUnless { it.isNull }?.asLong(),row.path("title").asString(),row.path("difficulty").asInt(),row.path("default_bpm").asInt(),score.toString(),row.path("score_version").asInt(),row.path("note_count").asInt(),row.path("source").asString())
            songs+=id to score
            jdbc.update("""INSERT INTO song_model_audios(song_id,score_version,object_key,performance_object_key,source_common_sha256,generation_metadata,bpm,bpm_unit,duration_ms,start_offset_ms,content_type,sha256,status)
                VALUES(?,?,?,?,?,CAST(? AS JSON),?,?,?,?,?,?,'DRAFT')""",id,audio.path("score_version").asInt(),key,key.substringBeforeLast('/')+"/performance.common.json",metadata.path("sourceCommonSha256").asString(),metadata.toString(),audio.path("bpm").asInt(),audio.path("bpm_unit").asString(),audio.path("duration_ms").asInt(),audio.path("start_offset_ms").asInt(),audio.path("content_type").asString(),audio.path("sha256").asString())
        }
        assertEquals(107,songs.size)
    }

    @AfterAll fun cleanup() {
        songs.forEach { (id,_) -> jdbc.update("DELETE FROM song_model_audios WHERE song_id=?",id);jdbc.update("DELETE FROM songs WHERE id=?",id) }
        instruments.forEach { jdbc.update("DELETE FROM instruments WHERE id=?",it) }
        pitches.forEach { jdbc.update("DELETE FROM pitches WHERE id=?",it) }
        client.close()
    }

    @Test fun `all imported scores and audio bind correctly before and after publication`() {
        assertTrue(songs.all { get("/api/songs/${it.first}/model-audio").statusCode()==404 })
        songs.forEach { (id,_) -> jdbc.update("UPDATE songs SET status='PUBLISHED' WHERE id=?",id);jdbc.update("UPDATE song_model_audios SET status='PUBLISHED' WHERE song_id=?",id) }
        val failures=mutableListOf<String>()
        for ((id,score) in songs) {
            val common=get("/api/songs/$id/score")
            if(common.statusCode()!=200) failures+="$id score ${common.statusCode()}: ${common.body()}"
            else assertEquals(score,json.readTree(common.body()).path("result").path("score"))
            val media=get("/api/songs/$id/model-audio")
            if(media.statusCode()!=200) failures+="$id audio ${media.statusCode()}: ${media.body()}"
            else {
                val result=json.readTree(media.body()).path("result")
                assertEquals("/api/songs/$id/model-audio/play",result.path("audioUrl").asString())
                assertEquals("/api/songs/$id/model-audio/performance",result.path("performanceUrl").asString())
                assertEquals(expectedHashes.getValue(id).first,result.path("sha256").asString())
                assertEquals(expectedHashes.getValue(id).second,result.path("sourceCommonSha256").asString())
            }
        }
        assertTrue(failures.isEmpty(),failures.joinToString("\n"))
        val items=json.readTree(get("/api/songs").body()).path("result").path("items")
        assertEquals(111,items.size())
        assertEquals(103,items.count { it.path("jangdan").isNull })
        assertTrue(items.filter { it.path("songId").asLong()>=990_000_000L }.all { it.path("description").isNull })
        System.getenv("MODEL_AUDIO_HTTP_EXPORT")?.let { export ->
            val directory=Path.of(export)
            Files.createDirectory(directory)
            val paths=listOf("/api/songs")+items.flatMap { row ->
                val prefix="/api/songs/${row.path("songId").asString()}"
                listOf(prefix,"$prefix/score","$prefix/model-audio")
            }
            val index=paths.map { path ->
                val response=get(path)
                val relative=path.removePrefix("/")+".json"
                val target=directory.resolve(relative)
                Files.createDirectories(target.parent)
                Files.writeString(target,response.body())
                mapOf("path" to path,"status" to response.statusCode(),"file" to relative)
            }
            Files.writeString(directory.resolve("index.json"),json.writeValueAsString(index))
        }
    }

    private fun get(path: String)=client.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),HttpResponse.BodyHandlers.ofString())
    companion object {
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("eolssu.scheduling.enabled") { "false" }
            r.add("spring.datasource.url") { requireNotNull(System.getenv("TEST_DB_URL")) }
            r.add("spring.datasource.username") { requireNotNull(System.getenv("TEST_DB_USERNAME")) }
            r.add("spring.datasource.password") { requireNotNull(System.getenv("TEST_DB_PASSWORD")) }
        }
    }
}
