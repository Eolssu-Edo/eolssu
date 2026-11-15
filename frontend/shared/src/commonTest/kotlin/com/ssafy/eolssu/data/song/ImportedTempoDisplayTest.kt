package com.ssafy.eolssu.data.song

import com.ssafy.eolssu.core.model.HanbaeMark
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
    @Test fun decreasingRampShowsGraduallySlowerWithoutChangingStoredPlan() {
        val source = smallScoreData()
        val input = expression(ramp.replace("\"start_bpm\":80", "\"start_bpm\":160").replace("\"bpm\":160", "\"bpm\":80"))
        val draft = source.editingDraft(source.score.title, 160, null).withTempoExpressions(input)
        val beforeScore = draft.toCommonScore()
        val hash = commonSourceSha256(Json.encodeToJsonElement(beforeScore))
        val before = draft.exportExpressions(input, hash, 160)
        val displayed = draft.toSong().score
        assertEquals(mapOf(ScoreUnitKey("c0-j0", 0, 0) to listOf(HanbaeMark.GraduallySlower)), displayed.tempoMarks)
        assertEquals(source.toScore().columns, source.toScore(input).columns)
        assertEquals(source.toScore().events, source.toScore(input).events)
        assertEquals(before, draft.exportExpressions(input, hash, 160))
        assertNull(draft.tempoChanges.single().mark)
        val reentered = beforeScore.editingDraft(source.score.title, 160, null).withTempoExpressions(before)
        assertEquals(displayed.tempoMarks, reentered.toSong().score.tempoMarks)
        val scaled = draft.copy(bpm = 80)
        assertEquals(80.0, scaled.tempoChanges.single().startBpm)
        assertEquals(40.0, scaled.tempoChanges.single().targetBpm)
        assertEquals(displayed.tempoMarks, scaled.toSong().score.tempoMarks)
        assertEquals(displayed.tempoMarks, draft.erase(1).toSong().score.tempoMarks)
    }

    @Test fun decreasingRampUsesExplicitStartThenPreviousTempoOrBaseline() {
        val decreasing = """{"gak":1,"cell":2,"start_bpm":160,"bpm":80,"end_gak":3,"end_cell":1}"""
        val slower = mapOf(ScoreUnitKey("c0-j1", 0, 0) to listOf(HanbaeMark.GraduallySlower))
        val source = smallScoreData()
        assertEquals(slower, source.toScore(expression("""{"gak":1,"cell":1,"bpm":40},$decreasing"""), 40.0).tempoMarks)
        val inherited = decreasing.replace("\"start_bpm\":160,", "")
        assertEquals(slower, source.toScore(expression("""{"gak":1,"cell":1,"bpm":160},$inherited"""), 40.0).tempoMarks)
        assertEquals(slower, source.toScore(expression(inherited), 160.0).tempoMarks)
        assertTrue(source.toScore(expression(inherited)).tempoMarks.isEmpty())
    }
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
        assertEquals(setOf(ScoreUnitKey("c0-j0", 0, 0)), displayed.tempoMarks.keys)
    }

    @Test fun explicitMarksOnAnyUnitOfTheSameCellTakePriority() {
        for (target in listOf(160, 40)) for (mark in HanbaeMark.entries) {
            val input = expression(ramp.replace("\"bpm\":160", "\"bpm\":$target"), """[{"cellId":"c0-j0","row":0,"col":1,"symbols":["${mark.symbol}"]}]""")
            val score = smallScoreData().toScore(input)
            assertEquals(mapOf(ScoreUnitKey("c0-j0", 0, 1) to listOf(mark)), score.tempoMarks)
        }
    }

    @Test fun explicitStartWinsOverPreviousTempoAndMissingStartUsesPreviousOrBaseline() {
        val first = """{"gak":1,"cell":1,"bpm":160}"""
        val second = """{"gak":1,"cell":2,"start_bpm":80,"bpm":160,"end_gak":3,"end_cell":1}"""
        val score = smallScoreData().toScore(expression("$first,$second"), baseBpm = 60.0)
        assertEquals(setOf(ScoreUnitKey("c0-j1", 0, 0)), score.tempoMarks.keys)
        val inherited = second.replace("\"start_bpm\":80,", "").replace("\"bpm\":160", "\"bpm\":200")
        assertEquals(score.tempoMarks, smallScoreData().toScore(expression("$first,$inherited"), 60.0).tempoMarks)
        assertEquals(score.tempoMarks, smallScoreData().toScore(expression(inherited), 60.0).tempoMarks)
        assertTrue(smallScoreData().toScore(expression(inherited)).tempoMarks.isEmpty())
        assertTrue(smallScoreData().toScore(expression("$first,${second.replace("\"start_bpm\":80,", "")}"), 60.0).tempoMarks.isEmpty())
    }

    @Test fun displayUnitAndEditorScaleApplyOnceAndRenderingDoesNotChangeSavePayload() {
        val source = smallScoreData().let { it.copy(music = it.music.copy(ticksPerBeat = 12)) }
        val input = expression(ramp.replace("\"start_bpm\":80", "\"start_bpm\":90").replace("\"bpm\":160", "\"bpm\":360"))
        val loaded = source.editingDraft(source.score.title, 40, null).withTempoExpressions(input)
        val initial = loaded.tempoChanges.single()
        assertEquals(30.0, initial.startBpm)
        assertEquals(120.0, initial.targetBpm)
        val faster = loaded.copy(bpm = 80)
        val beforeScore = faster.toCommonScore()
        val hash = commonSourceSha256(Json.encodeToJsonElement(beforeScore))
        val beforePayload = faster.exportExpressions(input, hash, 80)
        val displayed = faster.toSong().score
        assertEquals(60.0, faster.tempoChanges.single().startBpm)
        assertEquals(240.0, faster.tempoChanges.single().targetBpm)
        assertEquals(loaded.toSong().score.tempoMarks, displayed.tempoMarks)
        assertEquals(beforeScore, faster.toCommonScore())
        assertEquals(beforePayload, faster.exportExpressions(input, hash, 80))
        assertNull(faster.tempoChanges.single().mark)
        val reentered = beforeScore.editingDraft(source.score.title, 80, null).withTempoExpressions(beforePayload)
        assertEquals(faster.tempoChanges, reentered.tempoChanges)
        assertEquals(displayed.tempoMarks, reentered.toSong().score.tempoMarks)
        assertEquals(displayed.tempoMarks, beforeScore.toScore(beforePayload, 80.0).tempoMarks)
    }

    @Test fun unfinishedEditingKeepsStartGlyphAndNumericEditPlan() {
        val source = smallScoreData()
        val draft = source.editingDraft(source.score.title, 80, null).withTempoExpressions(expression(ramp))
        val erased = draft.erase(1)
        assertEquals(draft.tempoChanges, erased.tempoChanges)
        assertEquals(draft.toSong().score.tempoMarks, erased.toSong().score.tempoMarks)
    }

    @Test fun nonRampsAndInvalidOrOverlappingPlansNeverInventGradualTempo() {
        val cases = listOf(
            """{"gak":1,"cell":1,"bpm":160}""",
            ramp.replace("\"bpm\":160", "\"bpm\":80"),
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
            assertTrue(displayed.tempoMarks.isEmpty(), input)
        }
    }
}
