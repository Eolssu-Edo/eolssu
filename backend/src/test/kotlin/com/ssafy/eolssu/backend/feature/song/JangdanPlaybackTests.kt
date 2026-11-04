package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.song.domain.*
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.ReviewedJangdanPlayback
import com.ssafy.eolssu.backend.feature.song.infrastructure.persistence.CommonScoreTimelineDecoder
import com.ssafy.eolssu.backend.feature.jangdan.infrastructure.persistence.JangdanRow
import java.time.LocalDateTime
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import kotlin.test.*

class JangdanPlaybackTests {
    private val json = JsonMapper.builder().build()
    private val production = json.readTree(requireNotNull(javaClass.getResourceAsStream("/catalog/jangdan-playback.json")))
    // Independent reviewed ticksPerBeat / ticksPerJeonggan / cycleTicks, not derived from the manifest under test.
    private val scales = mapOf(6L to Triple(4, 2, 24), 22L to Triple(4, 6, 24), 19L to Triple(4, 4, 48),
        81L to Triple(4, 2, 24), 9L to Triple(2, 3, 9), 29L to Triple(6, 3, 9), 44L to Triple(4, 6, 24))
    private val examples = (scales.keys + setOf(63L, 7L)).associateWith(::fixture)
    private val stored = examples.getValue(6)
    private val score = stored.scoreJson
    // Synthetic one-cycle scores exercise the reviewed timing without copying entire melodies.
    private val manifest = (json.readTree(production.toString()) as ObjectNode).apply {
        val rows = putArray("scores")
        examples.forEach { (id, song) ->
            rows.add((json.readTree(review(id).toString()) as ObjectNode).put("sourceId", "test-$id")
                .put("scoreFingerprint", ReviewedJangdanPlayback.fingerprint(json.readTree(song.scoreJson))))
        }
    }
    private fun catalog(id: Long): JangdanRow? = manifest.path("patterns").firstOrNull { it.path("jangdanId").asString() == id.toString() }?.let {
        JangdanRow(id, it.path("name").asString(), json.writeValueAsString(it.path("pattern")), 66,
            it.path("bpmUnit").asString(), null, LocalDateTime.MIN, LocalDateTime.MIN)
    }
    private val resolver = ReviewedJangdanPlayback(json, ::catalog, manifest)

    private fun review(id: Long) = production.path("scores").first { it.path("songId").asString() == id.toString() }
    private fun fixture(id: Long): PublishedSongScore {
        val row = review(id)
        val version = row.path("scoreVersion").intValue()
        val (beat, cell, cycle) = scales[id] ?: Triple(4, 2, 2)
        val cells = (0 until cycle / cell).joinToString { """{"id":"c0-j$it","index":$it,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j0-r0-u0"}]}""" }
        val body = """{"schemaVersion":1,"score":{"sourceId":"test-$id","scoreVersion":$version},
            "music":{"ticksPerBeat":$beat,"ticksPerJeonggan":$cell,"bpmUnit":"QUARTER"},"provenance":{"review":"test"},
            "columns":[{"index":0,"cells":[$cells]}],"events":[{"id":"c0-j0-r0-u0","cellId":"c0-j0","kind":"REST",
            "startTick":0,"durationTick":$cycle,"yul":null,"octave":null,"breathAfter":false,"sigimsae":[]}]}"""
        return PublishedSongScore(id, version, 0, row.path("defaultBpm").intValue(), "QUARTER", body)
    }

    @Test fun `different written note scales keep equal musical cycle durations`() {
        fun duration(id: Long): Double {
            val song = examples.getValue(id)
            CommonScoreTimelineDecoder(json).decode(song, emptyList())
            val ticksPerBeat = json.readTree(song.scoreJson).path("music").path("ticksPerBeat").intValue()
            val playback = assertNotNull(resolver.resolve(song))
            assertEquals(scales.getValue(id).third.toLong(), playback.cycleTicks)
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
        val corrected = examples.getValue(44)
        assertEquals(2, corrected.scoreVersion)
        assertEquals(24L, assertNotNull(resolver.resolve(corrected)).cycleTicks)
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
        assertNull(ReviewedJangdanPlayback(json, { null }, manifest).resolve(stored))
        for (different in listOf(requireNotNull(catalog(2)).copy(name = "다른 장단"),
            requireNotNull(catalog(2)).copy(bpmUnit = "QUARTER"),
            requireNotNull(catalog(2)).copy(pattern = """{"beats":["쿵"],"group":[1]}"""))) {
            assertNull(ReviewedJangdanPlayback(json, { different }, manifest).resolve(stored))
        }
    }

    @Test fun `unreviewed Seryeongsan and unspecified rhythm remain disabled with reasons in inventory`() {
        assertNull(resolver.resolve(examples.getValue(63)))
        assertNull(resolver.resolve(examples.getValue(7)))
        val reviews = production.path("scores").toList()
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
