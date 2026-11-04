package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.song.api.SongScoreController
import com.ssafy.eolssu.backend.feature.song.application.GetSongScore
import com.ssafy.eolssu.backend.feature.song.domain.*
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.ReviewedJangdanPlayback
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import kotlin.test.*

class JangdanPlaybackTests {
    private val json = JsonMapper.builder().build()
    private val score = requireNotNull(javaClass.getResource("/jangdan-playback/6.json")).readText()
    private val stored = PublishedSongScore(6, 1, 40, 99, "QUARTER", score)
    private val repository = object : SongScoreRepository {
        override fun findPublished(songId: Long) = stored.takeIf { it.songId == songId }
        override fun pitches() = emptyList<ScorePitch>()
    }
    private val decoder = object : SongScoreDecoder {
        override fun decode(score: PublishedSongScore, pitches: List<ScorePitch>) =
            DecodedSongScore(1, emptyList(), ScoreNotationCapabilities(true, true))
    }
    private val manifest = json.readTree(requireNotNull(javaClass.getResourceAsStream("/catalog/jangdan-playback.json")))
    private fun catalog(id: Long): JangdanRow? = manifest.path("patterns").firstOrNull { it.path("jangdanId").asString() == id.toString() }?.let {
        JangdanRow(id, it.path("name").asString(), json.writeValueAsString(it.path("pattern")), 66,
            it.path("bpmUnit").asString(), null, LocalDateTime.MIN, LocalDateTime.MIN)
    }
    private val resolver = ReviewedJangdanPlayback(json, ::catalog)

    private fun fixture(id: Long): PublishedSongScore {
        val review = manifest.path("scores").first { it.path("songId").asString() == id.toString() }
        val body = requireNotNull(javaClass.getResource("/jangdan-playback/$id.json")).readText()
        return PublishedSongScore(id, review.path("scoreVersion").intValue(), 0, review.path("defaultBpm").intValue(), "QUARTER", body)
    }

    @Test fun `reviewed Gud geori receives a score tick cycle even without a song jangdan FK`() {
        val resolver = ReviewedJangdanPlayback(json, { JangdanRow(2, "굿거리", """{"beats":["떵","","기덕","쿵","더러러러","","쿵","","기덕","쿵","더러러러",""],"group":[3,3,3,3]}""",
            66, "DOTTED_QUARTER", null, LocalDateTime.MIN, LocalDateTime.MIN) })
        val mvc = MockMvcBuilders.standaloneSetup(SongScoreController(GetSongScore(repository, decoder, resolver), json)).build()
        mvc.perform(get("/api/songs/6/score"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result.defaultBpm").value(99))
            .andExpect(jsonPath("$.result.bpmUnit").value("QUARTER"))
            .andExpect(jsonPath("$.result.jangdanPlayback.jangdanId").value("2"))
            .andExpect(jsonPath("$.result.jangdanPlayback.startTick").value(0))
            .andExpect(jsonPath("$.result.jangdanPlayback.cycleTicks").value(24))
            .andExpect(jsonPath("$.result.jangdanPlayback.pattern.beats[4]").value("더러러러"))
    }

    @Test fun `different written note scales keep equal musical cycle durations`() {
        fun duration(id: Long): Double {
            val song = fixture(id)
            val ticksPerBeat = json.readTree(song.scoreJson).path("music").path("ticksPerBeat").intValue()
            val playback = assertNotNull(resolver.resolve(song))
            return playback.cycleTicks * 60_000.0 / song.defaultBpm / ticksPerBeat
        }
        assertEquals(3_636.363636, duration(6), 0.000001)
        assertEquals(duration(6), duration(22), 0.000001)
        assertEquals(8_181.818182, duration(19), 0.000001)
        assertEquals(duration(19), duration(81), 0.000001)
        assertEquals(1_875.0, duration(9), 0.000001)
        assertEquals(duration(9), duration(29), 0.000001)
    }

    @Test fun `only the current reviewed revision is enabled for corrected Cheonan Samgeori`() {
        val corrected = fixture(44)
        assertEquals(2, corrected.scoreVersion)
        assertNotNull(resolver.resolve(corrected))
        assertNull(resolver.resolve(corrected.copy(scoreVersion = 1)))
    }

    @Test fun `changed identity version tempo layout notes or source provenance disables accompaniment`() {
        for (different in listOf(stored.copy(songId = 11111), stored.copy(scoreVersion = 2),
            stored.copy(defaultBpm = 100), stored.copy(bpmUnit = "DOTTED_QUARTER"))) {
            assertNull(resolver.resolve(different))
        }
        for (path in listOf("score", "music", "provenance")) {
            val modified = json.readTree(score) as ObjectNode
            (modified.path(path) as ObjectNode).put("changed", true)
            assertNull(resolver.resolve(stored.copy(scoreJson = json.writeValueAsString(modified))))
        }
        val modified = json.readTree(score) as ObjectNode
        (modified.path("events").get(0) as ObjectNode).put("durationTick", 999)
        assertNull(resolver.resolve(stored.copy(scoreJson = json.writeValueAsString(modified))))
    }

    @Test fun `missing or changed catalog pattern fails closed without replacing song timing`() {
        assertNull(ReviewedJangdanPlayback(json, { null }).resolve(stored))
        for (different in listOf(requireNotNull(catalog(2)).copy(name = "다른 장단"),
            requireNotNull(catalog(2)).copy(bpmUnit = "QUARTER"),
            requireNotNull(catalog(2)).copy(pattern = """{"beats":["쿵"],"group":[1]}"""))) {
            assertNull(ReviewedJangdanPlayback(json, { different }).resolve(stored))
        }
    }

    @Test fun `unreviewed Seryeongsan and unspecified rhythm remain disabled with reasons in inventory`() {
        assertNull(resolver.resolve(fixture(63)))
        val reviews = manifest.path("scores").toList()
        assertEquals(107, reviews.size)
        assertEquals(107, reviews.map { it.path("songId").asString() }.toSet().size)
        assertEquals(60, reviews.count { it.path("status").asString() == "READY" })
        assertEquals(45, reviews.count { it.path("reason").asString() == "SOURCE_JANGDAN_UNSPECIFIED" })
        assertEquals(2, reviews.count { it.path("reason").asString() == "ROLL_ARTICULATION_UNREVIEWED" })
    }

    @Test fun `semantic fingerprint ignores property order whitespace and equivalent numbers but preserves types`() {
        fun hash(text: String) = ReviewedJangdanPlayback.fingerprint(json.readTree(text))
        assertEquals(hash("""{"한글":"굴림","n":1,"a":[1.50,true,null]}"""),
            hash("""{ "a": [1.5,true,null], "n": 1.0, "한글": "굴림" }"""))
        assertNotEquals(hash("""[1,23]"""), hash("""[12,3]"""))
        assertNotEquals(hash("""{"n":1}"""), hash("""{"n":"1"}"""))
    }
}
