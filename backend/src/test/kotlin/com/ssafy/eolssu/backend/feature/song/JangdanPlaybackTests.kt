package com.ssafy.eolssu.backend.feature.song

import com.ssafy.eolssu.backend.feature.song.domain.*
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.ScoreGridJangdanPlayback
import com.ssafy.eolssu.backend.feature.song.infrastructure.catalog.scoreJangdanName
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
    private fun catalog(name: String): JangdanRow? = if (name == "4박 기본 반주")
        JangdanRow(12, name, """{"beats":["덩","덕","궁","덕"],"group":[1,1,1,1]}""", 160,
            "QUARTER", null, LocalDateTime.MIN, LocalDateTime.MIN)
    else production.path("patterns").firstOrNull { it.path("name").asString() == name }?.let {
        JangdanRow(it.path("jangdanId").asString().toLong(), name, it.path("pattern").toString(), 66,
            it.path("bpmUnit").asString(), null, LocalDateTime.MIN, LocalDateTime.MIN)
    }
    private val resolver = ScoreGridJangdanPlayback(json, ::catalog)

    private val fourBeatName = "4박 기본 반주"
    private val blueValentineHash = "971be444d01a313ce5bdfd3f03915ea9b01b314430743d5d4172ab1241e818a4"
    private val fourBeatResolver = ScoreGridJangdanPlayback(json) { name ->
        if (name == fourBeatName) JangdanRow(12, name,
            """{"beats":["덩","덕","궁","덕"],"group":[1,1,1,1]}""", 160, "QUARTER", null,
            LocalDateTime.MIN, LocalDateTime.MIN) else catalog(name)
    }
    private fun blueValentine(): PublishedSongScore = PublishedSongScore(135, 3, 1, 160, "QUARTER", """{
        "schemaVersion":1,"score":{"sourceId":"user-blue-valentine-20261111-sogeum","instrument":"SOGEUM","scoreVersion":3},
        "music":{"jangdan":"$fourBeatName","ticksPerBeat":4,"ticksPerJeonggan":4,"bpmUnit":"QUARTER"},
        "provenance":{"jgbSha256":"$blueValentineHash"},"columns":[{"index":0,"cells":[
          {"id":"c0-j0","index":0,"units":[{"row":0,"col":0,"symbol":"쉼","eventId":"rest"}]},
          {"id":"c0-j1","index":1,"units":[{"row":0,"col":0,"symbol":"쉼","eventId":"rest"},{"row":1,"col":0,"symbol":"청대","eventId":"note"}]},
          {"id":"c0-j2","index":2,"units":[{"row":0,"col":0,"symbol":"-","eventId":"note"}]},
          {"id":"c0-j3","index":3,"units":[{"row":0,"col":0,"symbol":"-","eventId":"note"}]}]}],
        "events":[{"id":"rest","cellId":"c0-j0","kind":"REST","startTick":0,"durationTick":6},
          {"id":"note","cellId":"c0-j1","kind":"NOTE","yul":"대","octave":1,"startTick":6,"durationTick":10}]}""")

    @Test fun `reviewed Blue Valentine starts four beat accompaniment on its opening rest`() {
        val playback = assertNotNull(fourBeatResolver.resolve(blueValentine()))
        assertEquals("12", playback.jangdanId)
        assertEquals(0L, playback.startTick)
        assertEquals(16L, playback.cycleTicks)
        assertEquals(listOf("덩", "덕", "궁", "덕"), playback.pattern.beats)
        assertEquals(listOf(1, 1, 1, 1), playback.pattern.group)
    }

    @Test fun `opening rest exception does not leak to unreviewed identities or incomplete bars`() {
        val source = blueValentine()
        for (changed in listOf(source.scoreJson.replace(blueValentineHash, "0".repeat(64)),
            source.scoreJson.replace("user-blue-valentine-20261111-sogeum", "another-song"),
            source.scoreJson.replace("SOGEUM", "DANSO"), source.scoreJson.replace(fourBeatName, "단모리"))) {
            assertNull(fourBeatResolver.resolve(source.copy(scoreJson = changed)))
        }
        assertNull(fourBeatResolver.resolve(source.copy(scoreVersion = 2)))
        assertNull(fourBeatResolver.resolve(source.copy(scoreVersion = 4)))
        val partial = json.readTree(source.scoreJson) as ObjectNode
        (partial.path("columns").path(0) as ObjectNode).withArray("cells").remove(3)
        assertNull(fourBeatResolver.resolve(source.copy(scoreJson = partial.toString())))
    }

    private val airplaneSources = listOf(
        Triple("danso", "DANSO", "893185e48fc87c300d591f5e4f0774291f21da17828fdaea5f0f865705ae4b18"),
        Triple("sogeum", "SOGEUM", "b6dd52ce1fe0a3349ceb7453975f88db84c9bfb7192b9bdb4bd22bf2ab41a972"),
        Triple("sanjo-daegeum", "SANJO_DAEGEUM", "b6dd52ce1fe0a3349ceb7453975f88db84c9bfb7192b9bdb4bd22bf2ab41a972"),
    )

    private fun airplane(source: String, instrument: String, hash: String, version: Int = 1, ticks: Int = 1,
        sourceId: String = "airplane-beginner-$source-v1"): PublishedSongScore {
        val cells = (0..3).joinToString { """{"id":"c0-j$it","index":$it,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j0-r0-u0"}]}""" }
        return PublishedSongScore(132, version, 1, 77, "QUARTER", """{"schemaVersion":1,
            "score":{"sourceId":"$sourceId","instrument":"$instrument","scoreVersion":$version},
            "music":{"jangdan":"","ticksPerBeat":$ticks,"ticksPerJeonggan":$ticks,"bpmUnit":"QUARTER"},
            "provenance":{"jgbSha256":"$hash"},"columns":[{"index":0,"cells":[$cells]}],
            "events":[{"id":"c0-j0-r0-u0","cellId":"c0-j0","kind":"NOTE","startTick":0,"durationTick":${4 * ticks}}]}""")
    }

    @Test fun `Danmori eighth note cells can fall between integer score ticks`() {
        val source = airplaneSources.first().let { (source, instrument, hash) -> airplane(source, instrument, hash) }
        val declared = source.scoreJson.replace("\"jangdan\":\"\"", "\"jangdan\":\"단모리\"")
        val playback = assertNotNull(resolver.resolve(source.copy(scoreJson = declared)))
        assertEquals("7", playback.jangdanId)
        assertEquals(4L, playback.cycleTicks)
        assertEquals(listOf("덩", "", "따", "따", "궁", "따", "궁", ""), playback.pattern.beats)
        assertEquals(0.5, playback.cycleTicks.toDouble() / playback.pattern.beats.size)
        assertEquals(240_000.0 / 77, playback.cycleTicks * 60_000.0 / source.defaultBpm, 0.000001)
    }

    @Test fun `only reviewed airplane source instrument hash and version receive four beat accompaniment`() {
        for ((slug, instrument, hash) in airplaneSources) {
            val source = airplane(slug, instrument, hash)
            val playback = assertNotNull(resolver.resolve(source))
            assertEquals("12", playback.jangdanId)
            assertEquals("4박 기본 반주", playback.name)
            assertEquals(4L, playback.cycleTicks)
            assertEquals(listOf("덩", "덕", "궁", "덕"), playback.pattern.beats)
            assertEquals(listOf(1, 1, 1, 1), playback.pattern.group)
            assertEquals(3_116.883116883, playback.cycleTicks * 60_000.0 / source.defaultBpm, 0.000001)
            assertNull(resolver.resolve(source.copy(scoreVersion = 2)))
            for (changed in listOf(source.scoreJson.replace(hash, "0".repeat(64)),
                source.scoreJson.replace("\"instrument\":\"$instrument\"", "\"instrument\":\"JEONGAK_DAEGEUM\""),
                source.scoreJson.replace("airplane-beginner-$slug-v1", "unreviewed-airplane-$slug"))) {
                assertNull(resolver.resolve(source.copy(scoreJson = changed)))
            }
            val partial = json.readTree(source.scoreJson) as ObjectNode
            (partial.path("columns").path(0) as ObjectNode).withArray("cells").remove(3)
            assertNull(resolver.resolve(source.copy(scoreJson = partial.toString())))
        }
    }

    @Test fun `Jeongak airplane preview uses only its corrected second revision at six ticks per quarter`() {
        val hash = "b8960383360b471a20214c05b2a37b3f511911aaa38f8db358e5e03a28a77e02"
        val source = airplane("jeongak-daegeum", "JEONGAK_DAEGEUM", hash, version = 2, ticks = 6,
            sourceId = "mr203-fixed-jeongak-daegeum-006-p1").copy(songId = 56)
        val playback = assertNotNull(resolver.resolve(source))
        assertEquals("12", playback.jangdanId)
        assertEquals(24L, playback.cycleTicks)
        assertEquals(listOf("덩", "덕", "궁", "덕"), playback.pattern.beats)
        assertEquals(listOf(1, 1, 1, 1), playback.pattern.group)
        assertEquals(6.0, playback.cycleTicks.toDouble() / playback.pattern.beats.size)
        assertEquals(3_116.883116883, playback.cycleTicks * 60_000.0 / 77 / 6, 0.000001)
        assertNull(resolver.resolve(source.copy(scoreVersion = 1)))
        assertNull(resolver.resolve(source.copy(scoreVersion = 3)))
        assertNull(resolver.resolve(source.copy(scoreJson = source.scoreJson.replace(hash, "0".repeat(64)))))
        assertNull(resolver.resolve(source.copy(scoreJson = source.scoreJson.replace("JEONGAK_DAEGEUM", "SANJO_DAEGEUM"))))
    }

    @Test fun `four beat accompaniment follows quarter notes rather than the jeonggan scale`() {
        val source = airplaneSources.first().let { (slug, instrument, hash) -> airplane(slug, instrument, hash) }
        val changed = source.scoreJson.replace("\"ticksPerJeonggan\":1", "\"ticksPerJeonggan\":2")
        assertEquals(4L, assertNotNull(resolver.resolve(source.copy(scoreJson = changed))).cycleTicks)
        assertNull(resolver.resolve(source.copy(bpmUnit = "DOTTED_QUARTER")))
    }

    @Test fun `existing separately reviewed sources keep first revision matching`() {
        val revisions = listOf(
            Triple("mr203-fixed-danso-003-p1", "e0a5b4f02b9378865d2a6fbc59be65d8883276dfdb97e0f6444ce19a179063bc", "자진타령"),
            Triple("mr203-fixed-danso-030-p1", "46620fa702b0d5f52264841ac0d28493bf17920b0cfbdbbe885ca3aa7976ce77", "자진모리"),
        )
        for ((sourceId, hash, name) in revisions) {
            val source = json.readTree(airplane("danso", "DANSO", hash, sourceId = sourceId).scoreJson)
            assertEquals(name, scoreJangdanName(source, 1, requirePlayback = true))
            assertNull(scoreJangdanName(source, 2, requirePlayback = true))
        }
    }

    private fun review(id: Long) = production.path("scores").first { it.path("songId").asString() == id.toString() }
    private fun fixture(id: Long): PublishedSongScore {
        val row = review(id)
        val version = row.path("scoreVersion").intValue()
        val jangdan = row.path("sourceJangdan").asString()
        val (beat, cell, cycle) = scales[id] ?: Triple(4, 2, 2)
        val cells = (0 until cycle / cell).joinToString { """{"id":"c0-j$it","index":$it,"units":[{"row":0,"col":0,"symbol":"-","eventId":"c0-j0-r0-u0"}]}""" }
        val body = """{"schemaVersion":1,"score":{"sourceId":"test-$id","scoreVersion":$version},
            "music":{"jangdan":"$jangdan","ticksPerBeat":$beat,"ticksPerJeonggan":$cell,"bpmUnit":"QUARTER"},"provenance":{"review":"test"},
            "columns":[{"index":0,"cells":[$cells]}],"events":[{"id":"c0-j0-r0-u0","cellId":"c0-j0","kind":"NOTE",
            "startTick":0,"durationTick":$cycle,"yul":"黃","octave":0,"breathAfter":false,"sigimsae":[]}]}"""
        return PublishedSongScore(id, version, 1, row.path("defaultBpm").intValue(), "QUARTER", body)
    }

    @Test fun `different written note scales keep equal musical cycle durations`() {
        fun duration(id: Long): Double {
            val song = examples.getValue(id)
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

    @Test fun `title pitch and BPM edits preserve the current score grid accompaniment`() {
        val changed = json.readTree(score) as ObjectNode
        (changed.path("score") as ObjectNode).put("title", "제목 수정").put("scoreVersion", 2)
        (changed.path("events").get(0) as ObjectNode).put("yul", "太")
        val edited = stored.copy(scoreVersion = 2, defaultBpm = 100, scoreJson = changed.toString())
        assertEquals(24L, assertNotNull(resolver.resolve(edited)).cycleTicks)
    }

    @Test fun `the cycle follows current ticks and also works for a new song id`() {
        val changed = json.readTree(score) as ObjectNode
        (changed.path("music") as ObjectNode).put("ticksPerBeat", 2)
        assertEquals(12L, assertNotNull(resolver.resolve(stored.copy(songId = 11111, scoreJson = changed.toString()))).cycleTicks)
        (changed.path("music") as ObjectNode).put("ticksPerBeat", 3).put("bpmUnit", "DOTTED_QUARTER")
        assertEquals(12L, assertNotNull(resolver.resolve(stored.copy(bpmUnit = "DOTTED_QUARTER", scoreJson = changed.toString()))).cycleTicks)
    }

    @Test fun `an ambiguous downbeat or partial column does not attach accompaniment`() {
        val pickup = json.readTree(score) as ObjectNode
        (pickup.path("events").get(0) as ObjectNode).put("kind", "REST")
        assertNull(resolver.resolve(stored.copy(scoreJson = pickup.toString())))

        val partial = json.readTree(score) as ObjectNode
        val cells = (partial.path("columns").get(0) as ObjectNode).withArray("cells")
        cells.remove(cells.size() - 1)
        assertNull(resolver.resolve(stored.copy(scoreJson = partial.toString())))

        val nonCanonicalName = json.readTree(score) as ObjectNode
        (nonCanonicalName.path("music") as ObjectNode).put("jangdan", " 굿거리 ")
        assertNull(resolver.resolve(stored.copy(scoreJson = nonCanonicalName.toString())))
    }

    @Test fun `the current valid catalog pattern plays and an unusable pattern returns null`() {
        assertNull(ScoreGridJangdanPlayback(json) { null }.resolve(stored))
        val row = requireNotNull(catalog("굿거리"))
        val changed = row.copy(pattern = row.pattern.replaceFirst("떵", "쿵"))
        assertEquals("쿵", assertNotNull(ScoreGridJangdanPlayback(json) { changed }.resolve(stored)).pattern.beats.first())
        val invalid = row.copy(pattern = """{"beats":["쿵"],"group":[2]}""")
        assertNull(ScoreGridJangdanPlayback(json) { invalid }.resolve(stored))
        assertNull(ScoreGridJangdanPlayback(json) { row.copy(pattern = "{") }.resolve(stored))
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
}
