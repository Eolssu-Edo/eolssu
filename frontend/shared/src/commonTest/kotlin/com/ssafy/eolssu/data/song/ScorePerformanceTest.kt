package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.ScoreUnitKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScorePerformanceTest {
    @Test fun attachesExactSourceAndAudioAndKeepsWrittenTicksAcrossStretchAndRepeat() {
        val song = previewSong()
        val audio = previewAudio().withPerformance(previewJson(), song, SourceHash)
        val performance = assertNotNull(audio.performance)
        assertTrue(audio.matches(song))
        assertEquals(6500.0, performance.durationMs)
        assertEquals(3000.0, song.durationMs())
        assertEquals(0.5, performance.at(250.0).sourceTick)
        assertEquals(0.0, performance.at(3250.0).sourceTick)
        assertEquals("repeat-0", performance.at(3250.0).segment.occurrenceId)
        assertEquals(12.0, performance.at(7000.0).sourceTick)
        assertEquals(listOf("늘임표"), performance.annotations[ScoreUnitKey("c0-j0", 0, 0)])
        assertEquals(listOf("노라느니"), performance.annotations[ScoreUnitKey("c0-j1", 0, 0)])
        assertFalse(audio.copy(sha256 = "b".repeat(64)).matches(song))
        assertFalse(audio.matches(song.copy(score = song.score.copy(source = song.score.source!!.copy(sourceId = "other")))))
    }

    @Test fun rejectsEveryIdentityMismatchAndKeepsPreviewOutOfTheNetworkDto() {
        val song = previewSong()
        val mismatches = mapOf("schema" to JsonPrimitive("unknown"), "policy" to JsonPrimitive("unknown"),
            "sourceSha256" to JsonPrimitive("c".repeat(64)), "audioSha256" to JsonPrimitive("c".repeat(64)),
            "scoreVersion" to JsonPrimitive(2), "sourceId" to JsonPrimitive("other"),
            "instrument" to JsonPrimitive("SOGEUM"), "bpm" to JsonPrimitive(61), "bpmUnit" to JsonPrimitive("QUARTER"))
        mismatches.forEach { (key, value) ->
            assertFailsWith<IllegalArgumentException>(key) { previewAudio().withPerformance(editPreview { it[key] = value }, song, SourceHash) }
        }
        assertFailsWith<IllegalArgumentException> { previewAudio().withPerformance(previewJson(), song, "not-a-hash") }
        assertFailsWith<IllegalArgumentException> { previewAudio().copy(durationMs = 6000).withPerformance(previewJson(), song, SourceHash) }
        val attached = previewAudio().withPerformance(previewJson(), song, SourceHash)
        val wire = Json.encodeToString(attached)
        assertFalse(wire.contains("performance"))
        assertEquals(null, Json.decodeFromString<ModelAudio>(wire).performance)
    }

    @Test fun rejectsMalformedTimingReferencesMissingSlotsAndUnknownFields() {
        val mutations = listOf<(MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit>(
            { it["unexpected"] = JsonPrimitive(true) },
            { it["durationMs"] = JsonPrimitive(6499) },
            { it["durationMs"] = JsonPrimitive("6500") },
            { it["segments"] = JsonArray(emptyList()) },
            { it["segments"] = JsonArray(it.getValue("segments").jsonArray.dropLast(1)) },
            { it["segments"] = JsonArray(it.getValue("segments").jsonArray.filterIndexed { i, _ -> i != 4 && i != 9 }) },
        )
        mutations.forEach { mutate -> assertFailsWith<IllegalArgumentException> {
            previewAudio().withPerformance(editPreview(mutate), previewSong(), SourceHash)
        } }
        val segmentMutations = mapOf("occurrenceId" to JsonPrimitive("first-1"), "eventId" to JsonPrimitive("missing"),
            "cellId" to JsonPrimitive("c0-j99"), "row" to JsonPrimitive(4), "col" to JsonPrimitive(4),
            "startTick" to JsonPrimitive(0.1), "endTick" to JsonPrimitive(2),
            "startMs" to JsonPrimitive(1), "endMs" to JsonPrimitive(0), "sigimsae" to JsonArray(listOf(JsonPrimitive(""))))
        segmentMutations.forEach { (key, value) -> assertFailsWith<IllegalArgumentException>(key) {
            previewAudio().withPerformance(editPreview { root ->
                val segments = root.getValue("segments").jsonArray.toMutableList()
                segments[0] = JsonObject(segments[0].jsonObject + (key to value))
                root["segments"] = JsonArray(segments)
            }, previewSong(), SourceHash)
        } }
    }

    @Test fun allFourInstrumentsUseTheirOwnExactIdentity() {
        listOf("DANSO", "SOGEUM", "JEONGAK_DAEGEUM", "SANJO_DAEGEUM").forEach { instrument ->
            val song = previewSong().let { it.copy(score = it.score.copy(source = it.score.source!!.copy(instrument = instrument))) }
            val attached = previewAudio().withPerformance(editPreview { it["instrument"] = JsonPrimitive(instrument) }, song, SourceHash)
            assertTrue(attached.matches(song))
        }
    }

    @Test fun rejectsAddedOmittedOrChangedRawSourceSymbolsEvenAcrossRepeats() {
        listOf(emptyList(), listOf("늘임표", "니레"), listOf("늘임표@50,0,0")).forEach { symbols ->
            assertFailsWith<IllegalArgumentException> {
                previewAudio().withPerformance(editPreview { root ->
                    root["segments"] = JsonArray(root.getValue("segments").jsonArray.mapIndexed { index, segment ->
                        if (index % 5 == 0) JsonObject(segment.jsonObject + ("sigimsae" to JsonArray(symbols.map(::JsonPrimitive)))) else segment
                    })
                }, previewSong(), SourceHash)
            }
        }
    }

    @Test fun acceptsFractionalWrittenUnitsWithoutRoundingToWholeTicks() {
        val base = smallScoreData()
        val id = "c0-j0-r0-u0"
        val source = base.copy(
            music = base.music.copy(ticksPerBeat = 1, ticksPerJeonggan = 1),
            columns = listOf(CommonScoreData.Column(0, listOf(CommonScoreData.Cell("c0-j0", 0,
                (0..2).map { CommonScoreData.Unit(it, 0, if (it == 0) "황" else "-", id) })))),
            events = listOf(base.events.first().copy(durationTick = 1)),
        )
        val song = smallScoreSong(source).copy(id = "42", scoreVersion = 1)
        val json = editPreview { root ->
            root["durationMs"] = JsonPrimitive(900)
            root["segments"] = JsonArray((0..2).map { row -> buildJsonObject {
                put("occurrenceId", "unit-$row"); put("eventId", id); put("cellId", "c0-j0")
                put("row", row); put("col", 0); put("startTick", row / 3.0); put("endTick", (row + 1) / 3.0)
                put("startMs", row * 300); put("endMs", (row + 1) * 300); put("sigimsae", JsonArray(emptyList()))
            } })
        }
        val performance = assertNotNull(previewAudio().withPerformance(json, song, SourceHash).performance)
        assertEquals(0.5, performance.at(450.0).sourceTick)
        assertEquals(2.0 / 3, performance.at(600.0).sourceTick)
        assertEquals(2, performance.at(600.0).segment.unit.unitRow)
        assertEquals(1L, song.timeline.single().durationTick)
    }
}

internal fun editPreview(mutate: (MutableMap<String, kotlinx.serialization.json.JsonElement>) -> Unit): String =
    JsonObject(Json.parseToJsonElement(previewJson()).jsonObject.toMutableMap().apply(mutate)).toString()

internal const val SourceHash = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
internal fun previewSong() = smallScoreSong(smallScoreData().let { data ->
    data.copy(events = data.events.mapIndexed { index, event -> event.copy(sigimsae = when (index) {
        0 -> listOf("늘임표"); 1 -> listOf("니레", "노라느니"); else -> emptyList()
    }) })
}).copy(id = "42", scoreVersion = 1)
internal fun previewAudio() = audioFixture().copy(durationMs = 7050)

internal fun previewJson(): String {
    val units = listOf(
        """{"eventId":"c0-j0-r0-u0","cellId":"c0-j0","row":0,"col":0,"startTick":0,"endTick":1,"sigimsae":["늘임표"]}""",
        """{"eventId":"c0-j0-r0-u1","cellId":"c0-j0","row":0,"col":1,"startTick":1,"endTick":2,"sigimsae":["니레"]}""",
        """{"eventId":"c0-j0-r0-u1","cellId":"c0-j0","row":1,"col":0,"startTick":2,"endTick":4,"sigimsae":[]}""",
        """{"eventId":"c0-j0-r0-u1","cellId":"c0-j1","row":0,"col":0,"startTick":4,"endTick":8,"sigimsae":["노라느니"]}""",
        """{"eventId":"c1-j0-r0-u0","cellId":"c1-j0","row":0,"col":0,"startTick":8,"endTick":12,"sigimsae":[]}""",
    )
    val boundaries = listOf(0, 500, 750, 1250, 2250, 3250)
    val segments = (0..1).flatMap { repeat -> units.mapIndexed { index, text ->
        buildJsonObject {
            Json.parseToJsonElement(text).jsonObject.forEach { (key, value) -> put(key, value) }
            put("occurrenceId", if (repeat == 0) "first-$index" else "repeat-$index")
            put("startMs", repeat * 3250 + boundaries[index])
            put("endMs", repeat * 3250 + boundaries[index + 1])
        }
    } }
    return buildJsonObject {
        put("schema", "common-score-performance/v1"); put("policy", "educational-preview/v1")
        put("sourceSha256", SourceHash); put("audioSha256", "a".repeat(64))
        put("scoreVersion", 1); put("sourceId", "unit-test-score"); put("instrument", "DANSO")
        put("bpm", 60); put("bpmUnit", "DOTTED_QUARTER"); put("durationMs", 6500)
        put("segments", JsonArray(segments))
    }.toString()
}
