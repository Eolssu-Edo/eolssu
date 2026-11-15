package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.feature.song.edit.editingDraft
import com.ssafy.eolssu.feature.song.edit.withTempoExpressions
import com.ssafy.eolssu.feature.song.edit.exportExpressions
import com.ssafy.eolssu.feature.song.edit.toCommonScore
import com.ssafy.eolssu.feature.song.edit.tempoChanges
import kotlinx.serialization.json.*
import kotlin.test.*

class ImportedTempoDisplayTest {
    private fun expression(tempos: String, placements: String = "[]") = Json.parseToJsonElement(
        """{"placements":$placements,"performancePlan":{"tempos":[$tempos]}}""").jsonObject
    private val ramp = """{"gak":1,"cell":1,"start_bpm":80,"bpm":160,"end_gak":2,"end_cell":1}"""
    @Test fun numericRampWithEmptyPlacementsShowsAtItsStartWithoutChangingSource() {
        val source = smallScoreData()
        val expression = Json.parseToJsonElement("""{"placements":[],"performancePlan":{"tempos":[
            {"gak":1,"cell":1,"start_bpm":80,"bpm":160,"end_gak":2,"end_cell":1}
        ]}}""").jsonObject
        val plain = source.toScore()
        val displayed = source.toScore(expression)
        assertEquals(listOf(HanbaeMark.GraduallyFaster), displayed.tempoMarks.values.flatten())
        assertEquals(plain.columns, displayed.columns)
        assertEquals(plain.events, displayed.events)
        assertEquals(plain.source, displayed.source)
        assertTrue(expression.getValue("placements").jsonArray.isEmpty())
        assertEquals(ScorePosition(0, 0), displayed.tempoRanges.single().start)
        assertEquals(ScorePosition(1, 0), displayed.tempoRanges.single().end)
        assertEquals(80.0, displayed.tempoRanges.single().startBpm)
        assertEquals(160.0, displayed.tempoRanges.single().targetBpm)
    }

    @Test fun explicitMarksOnAnyUnitOfTheSameCellTakePriority() {
        for (mark in HanbaeMark.entries) {
            val input = expression(ramp, """[{"cellId":"c0-j0","row":0,"col":1,"symbols":["${mark.symbol}"]}]""")
            val score = smallScoreData().toScore(input)
            assertEquals(mapOf(ScoreUnitKey("c0-j0", 0, 1) to listOf(mark)), score.tempoMarks)
            assertEquals(1, score.tempoRanges.size)
        }
    }

    @Test fun explicitStartWinsOverPreviousTempoAndMissingStartUsesPreviousOrBaseline() {
        val first = """{"gak":1,"cell":1,"bpm":160}"""
        val second = """{"gak":1,"cell":2,"start_bpm":80,"bpm":160,"end_gak":3,"end_cell":1}"""
        val score = smallScoreData().toScore(expression("$first,$second"), baseBpm = 60.0)
        assertEquals(80.0, score.tempoRanges.single().startBpm)
        assertNull(score.tempoRanges.single().end)
        val inherited = second.replace("\"start_bpm\":80,", "").replace("\"bpm\":160", "\"bpm\":200")
        assertEquals(160.0, smallScoreData().toScore(expression("$first,$inherited"), 60.0).tempoRanges.single().startBpm)
        assertEquals(60.0, smallScoreData().toScore(expression(inherited), 60.0).tempoRanges.single().startBpm)
        assertTrue(smallScoreData().toScore(expression(inherited)).tempoRanges.isEmpty())
    }

    @Test fun displayUnitAndEditorScaleApplyOnceAndRenderingDoesNotChangeSavePayload() {
        val source = smallScoreData().let { it.copy(music = it.music.copy(ticksPerBeat = 12)) }
        val input = expression(ramp.replace("\"start_bpm\":80", "\"start_bpm\":90").replace("\"bpm\":160", "\"bpm\":360"))
        val loaded = source.editingDraft(source.score.title, 40, null).withTempoExpressions(input)
        val initial = loaded.toSong().score.tempoRanges.single()
        assertEquals(30.0, initial.startBpm)
        assertEquals(120.0, initial.targetBpm)
        val faster = loaded.copy(bpm = 80)
        val beforeScore = faster.toCommonScore()
        val hash = commonSourceSha256(Json.encodeToJsonElement(beforeScore))
        val beforePayload = faster.exportExpressions(input, hash, 80)
        val displayed = faster.toSong().score
        assertEquals(60.0, displayed.tempoRanges.single().startBpm)
        assertEquals(240.0, displayed.tempoRanges.single().targetBpm)
        assertEquals(beforeScore, faster.toCommonScore())
        assertEquals(beforePayload, faster.exportExpressions(input, hash, 80))
        assertNull(faster.tempoChanges.single().mark)
        val reentered = beforeScore.editingDraft(source.score.title, 80, null).withTempoExpressions(beforePayload)
        assertEquals(displayed.tempoRanges, reentered.toSong().score.tempoRanges)
        assertEquals(displayed.tempoRanges, beforeScore.toScore(beforePayload, 80.0).tempoRanges)
    }

    @Test fun unfinishedEditingKeepsRangeAndStartGlyph() {
        val source = smallScoreData()
        val draft = source.editingDraft(source.score.title, 80, null).withTempoExpressions(expression(ramp))
        val erased = draft.erase(1)
        assertEquals(draft.toSong().score.tempoRanges, erased.toSong().score.tempoRanges)
        assertEquals(draft.toSong().score.tempoMarks, erased.toSong().score.tempoMarks)
    }

    @Test fun nonRampsAndInvalidOrOverlappingPlansNeverInventAcceleration() {
        val cases = listOf(
            """{"gak":1,"cell":1,"bpm":160}""",
            ramp.replace("\"bpm\":160", "\"bpm\":80"),
            ramp.replace("\"bpm\":160", "\"bpm\":60"),
            ramp.replace("\"end_gak\":2", "\"end_gak\":1"),
            ramp.replace("\"end_gak\":2", "\"end_gak\":4"),
            ramp.replace("\"start_bpm\":80", "\"start_bpm\":0"),
            ramp.replace("\"bpm\":160", "\"bpm\":\"NaN\""),
            ramp.replace("\"end_cell\":1", "\"end_cell\":0"),
            "$ramp,$ramp",
            ramp + "," + """{"gak":1,"cell":2,"bpm":100}""",
        )
        for (input in cases) {
            val displayed = smallScoreData().toScore(expression(input), 80.0)
            assertTrue(displayed.tempoRanges.isEmpty(), input)
            assertTrue(displayed.tempoMarks.isEmpty(), input)
        }
    }
}
