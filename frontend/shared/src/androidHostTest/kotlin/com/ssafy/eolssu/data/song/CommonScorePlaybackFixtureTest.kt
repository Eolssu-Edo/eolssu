package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.displayUnitAt
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Actual UI mapper evidence, independent of the MIDI/audio adapter's output. */
class CommonScorePlaybackFixtureTest {
    @Test fun arirangDisplayAndPlaybackKeepEverySourcePitchRestAndDurationAt96Bpm() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/scores/ngc-standard-2022-p173.score.json"))
            .use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("51cd874652d705511cfaa0f2c03ba6d3632ae10b4a483036d6a438a8eac73f1b", hash)
        // Read expected values from the reviewed source, not the DTO or generated audio source-map.
        val raw = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        val sourceEvents = raw.getValue("events").jsonArray.map { it.jsonObject }
        val music = raw.getValue("music").jsonObject
        val ticksPerBeat = music.getValue("ticksPerBeat").jsonPrimitive.int
        val ticksPerCell = music.getValue("ticksPerJeonggan").jsonPrimitive.int
        val bpm = 96
        val millisPerTick = 60_000.0 / bpm / ticksPerBeat
        val data = CommonScoreData.decode(bytes.toString(Charsets.UTF_8))
        val song = Song("arirang-playback-proof", "아리랑", SongLevel.Intro,
            Jangdan("세마치", listOf(3, 3, 3), 3, 3), bpm, "", true, null, data.toScore())

        assertEquals("DOTTED_QUARTER", music.string("bpmUnit"))
        assertEquals(music.string("bpmUnit"), song.score.timing?.bpmUnit?.name)
        assertEquals(65, song.timeline.size)
        assertEquals(61, song.timeline.count { it.note is ScoreNote.SourcePitch })
        assertEquals(4, song.timeline.count { it.note == ScoreNote.Rest })
        assertEquals(4, song.timeline.count { it.breathAfter })
        assertEquals(30_000.0, song.durationMs(), Epsilon)
        assertEquals(30_000.0, song.timeline.sumOf { song.eventDurationMs(it) }, Epsilon)
        var expectedNoteIndex = 0
        sourceEvents.zip(song.timeline).forEachIndexed { index, (source, actual) ->
            val id = source.string("id")
            val start = source.getValue("startTick").jsonPrimitive.long
            val duration = source.getValue("durationTick").jsonPrimitive.long
            assertEquals(index, actual.index, id)
            assertEquals(id, actual.id)
            assertEquals(source.string("cellId"), actual.cellId, id)
            assertEquals(start, actual.startTick, id)
            assertEquals(duration, actual.durationTick, id)
            assertEquals(start * millisPerTick, actual.unitStart * song.cellDurationMs(), Epsilon, id)
            assertEquals(duration * millisPerTick, song.eventDurationMs(actual), Epsilon, id)
            assertEquals(source.getValue("breathAfter").jsonPrimitive.boolean, actual.breathAfter, id)
            assertEquals(source.getValue("sigimsae").jsonArray.map { it.jsonPrimitive.content }, actual.sigimsae, id)
            if (source.string("kind") == "REST") {
                assertEquals(ScoreNote.Rest, actual.note, id)
                assertEquals(null, actual.noteIndex, id)
            } else {
                val pitch = actual.note as ScoreNote.SourcePitch
                assertEquals(source.string("yul"), pitch.yul, id)
                assertEquals(source.getValue("octave").jsonPrimitive.int, pitch.octave, id)
                assertEquals(expectedNoteIndex++, actual.noteIndex, id)
                assertEquals(Glyphs.getValue(pitch.yul to pitch.octave), pitch.symbol, id)
            }
        }

        var cellIndex = 0
        var displayIndex = 0
        raw.getValue("columns").jsonArray.forEachIndexed { columnIndex, column ->
            column.jsonObject.getValue("cells").jsonArray.forEachIndexed { rowIndex, cell ->
                val units = cell.jsonObject.getValue("units").jsonArray.map { it.jsonObject }
                val rowCount = units.map { it.getValue("row").jsonPrimitive.int }.distinct().size
                for (source in units) {
                    val actual = song.displayUnits[displayIndex++]
                    val unitRow = source.getValue("row").jsonPrimitive.int
                    val unitCol = source.getValue("col").jsonPrimitive.int
                    val columnsInRow = units.count { it.getValue("row").jsonPrimitive.int == unitRow }
                    val startTick = ticksPerCell * (cellIndex + (unitRow + unitCol.toDouble() / columnsInRow) / rowCount)
                    val durationTick = ticksPerCell.toDouble() / rowCount / columnsInRow
                    assertEquals(listOf(columnIndex, rowIndex, unitRow, unitCol),
                        listOf(actual.column, actual.row, actual.unitRow, actual.unitCol))
                    assertEquals(source.string("symbol"), actual.symbol)
                    assertEquals(source.string("eventId"), actual.event.id)
                    assertSame(song.timeline[actual.event.index], actual.event)
                    assertEquals(startTick * millisPerTick, actual.unitStart * song.cellDurationMs(), Epsilon)
                    assertEquals(durationTick * millisPerTick, actual.unitDuration * song.cellDurationMs(), Epsilon)
                    when (source.string("symbol")) {
                        "" -> assertEquals(ScoreNote.Blank, actual.note)
                        "-" -> assertEquals(ScoreNote.Hold, actual.note)
                        "쉼" -> assertEquals(ScoreNote.Rest, actual.note)
                        else -> assertEquals(source.string("symbol"), (actual.note as ScoreNote.SourcePitch).hangul)
                    }
                    // The production playback cursor must land on every tied or subdivided display slot.
                    val middle = actual.unitStart + actual.unitDuration / 2
                    val fraction = ((middle - actual.event.unitStart) / actual.event.unitDuration).toFloat()
                    assertSame(actual, song.displayUnitAt(actual.event.index, fraction))
                    assertTrue(middle >= actual.event.unitStart && middle < actual.event.unitStart + actual.event.unitDuration)
                }
                cellIndex++
            }
        }
        assertEquals(96, displayIndex)
        assertEquals(displayIndex, song.displayUnits.size)
        assertEquals(48, cellIndex)
        assertEquals(3, song.displayUnits.count { it.event.id == "c0-j0-r0-u0" })

        // Opt-in external artifact for comparing the real Kotlin mapper to independently rendered audio.
        System.getenv("EOLSSU_SCORE_PLAYBACK_EVIDENCE")?.takeIf(String::isNotBlank)?.let { path ->
            val evidence = buildJsonObject {
                put("fixtureSha256", hash)
                put("sourceId", data.score.sourceId)
                put("bpm", song.baseBpm)
                put("bpmUnit", requireNotNull(song.score.timing).bpmUnit.name)
                put("durationMs", song.durationMs())
                put("events", buildJsonArray {
                    song.timeline.forEach { event -> add(buildJsonObject {
                        val pitch = event.note as? ScoreNote.SourcePitch
                        put("id", event.id)
                        put("kind", if (pitch == null) "REST" else "NOTE")
                        put("yul", pitch?.yul?.let(::JsonPrimitive) ?: JsonNull)
                        put("octave", pitch?.octave?.let(::JsonPrimitive) ?: JsonNull)
                        put("symbol", event.note.symbol)
                        put("startTick", requireNotNull(event.startTick))
                        put("durationTick", requireNotNull(event.durationTick))
                        put("startMs", event.unitStart * song.cellDurationMs())
                        put("durationMs", song.eventDurationMs(event))
                        put("breathAfter", event.breathAfter)
                    }) }
                })
                put("displayUnits", buildJsonArray {
                    song.displayUnits.forEach { unit -> add(buildJsonObject {
                        put("column", unit.column); put("row", unit.row)
                        put("unitRow", unit.unitRow); put("unitCol", unit.unitCol)
                        put("symbol", unit.symbol); put("glyph", unit.note.symbol)
                        put("eventId", unit.event.id)
                        put("startMs", unit.unitStart * song.cellDurationMs())
                        put("durationMs", unit.unitDuration * song.cellDurationMs())
                    }) }
                })
            }
            File(path).absoluteFile.apply { parentFile?.mkdirs() }
                .writeText(EvidenceJson.encodeToString(JsonObject.serializer(), evidence))
        }
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private companion object {
        const val Epsilon = 0.000001
        val EvidenceJson = Json { prettyPrint = true }
        val Glyphs = mapOf(("중" to 0) to "仲", ("임" to 0) to "林", ("무" to 0) to "無",
            ("황" to 1) to "潢", ("태" to 1) to "汰", ("중" to 1) to "㳞")
    }
}
