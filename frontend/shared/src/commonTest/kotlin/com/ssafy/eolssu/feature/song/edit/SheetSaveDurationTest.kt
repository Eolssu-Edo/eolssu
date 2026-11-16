package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.HanbaeMark
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.data.song.smallScoreData
import com.ssafy.eolssu.data.song.estimatedModelDurationMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.math.ln
import com.ssafy.eolssu.data.song.commonSourceSha256
import kotlinx.serialization.json.*

internal class SheetSaveDurationTest {
    private fun draft() = smallScoreData().editingDraft("저장 길이", 60, null)

    @Test fun stepTempoIncludesEveryCellAtItsCurrentTempo() {
        val changed = draft().withTempoChange(SheetTempoChange(1, HanbaeMark.Faster, 120.0))
        assertEquals(2.0, assertNotNull(changed.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(1.0, assertNotNull(changed.estimatedDurationSeconds(120)), 1e-9)
    }

    @Test fun gradualTempoIntegratesTheReciprocalAndKeepsTheTargetAfterItsEnd() {
        val ramp = draft().withTempoChange(SheetTempoChange(0, HanbaeMark.GraduallyFaster, 120.0, 2))
        assertEquals(2 * ln(2.0) + 0.5, assertNotNull(ramp.estimatedDurationSeconds(60)), 1e-9)
        // 60에서 40까지 세 정간 동안 고르게 느려진다: 60·3·ln(60/40)/(60-40) = 9·ln(1.5)초.
        val slower = draft().withTempoChange(SheetTempoChange(0, HanbaeMark.GraduallySlower, 40.0, 3))
        assertEquals(9 * ln(1.5), assertNotNull(slower.estimatedDurationSeconds(60)), 1e-9)
        val afterStep = draft().withTempoChange(SheetTempoChange(0, HanbaeMark.Faster, 120.0))
            .withTempoChange(SheetTempoChange(1, HanbaeMark.GraduallySlower, 60.0, 3))
        assertEquals(0.5 + 2 * ln(2.0), assertNotNull(afterStep.estimatedDurationSeconds(60)), 1e-9)
    }

    @Test fun numericRampHonorsItsExplicitStartAndTheSourceBeatUnit() {
        val source = smallScoreData().let { it.copy(music = it.music.copy(ticksPerBeat = 12)) }
        val initial = source.editingDraft("점4분음표 구간", 60, null)
        val ramp = initial.withTempoChange(SheetTempoChange(1, null, 120.0, 3, startBpm = 30.0))
        assertEquals(1.0 / 3 + 4 * ln(4.0) / 9, assertNotNull(ramp.estimatedDurationSeconds(60)), 1e-9)
        val flat = initial.withTempoChange(SheetTempoChange(0, null, 90.0, 3, startBpm = 90.0))
        assertEquals(2.0 / 3, assertNotNull(flat.estimatedDurationSeconds(60)), 1e-9)
    }

    @Test fun changingDeletingAndUndoingTempoRecalculatesEvenWithAnEmptyPlan() {
        val initial = SheetEditUiState(EditSource.Mine("31"), draft())
        val changed = initial.updateTempoSettings(initial.draft.withTempoChange(SheetTempoChange(1, HanbaeMark.Faster, 120.0)))
        assertEquals(2.0, assertNotNull(changed.draft.estimatedDurationSeconds(60)), 1e-9)
        val edited = changed.updateTempoSettings(changed.draft.withTempoChange(SheetTempoChange(1, HanbaeMark.Faster, 180.0), replacing = 1))
        assertEquals(5.0 / 3, assertNotNull(edited.draft.estimatedDurationSeconds(60)), 1e-9)
        val deleted = edited.updateTempoSettings(edited.draft.withoutTempoChange(1))
        assertEquals(3.0, assertNotNull(deleted.draft.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(5.0 / 3, assertNotNull(deleted.undo().draft.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(3.0, assertNotNull(deleted.undo().redo().draft.estimatedDurationSeconds(60)), 1e-9)
    }

    @Test fun unresolvedCuesKeepThePriorTempoAndOriginalRestoresTheBaseline() {
        val unresolved = draft().withTempoChange(SheetTempoChange(0, HanbaeMark.GraduallySlower, null))
        assertEquals(3.0, assertNotNull(unresolved.estimatedDurationSeconds(60)), 1e-9)
        val changed = draft().withTempoChange(SheetTempoChange(0, HanbaeMark.Faster, 120.0))
            .withTempoChange(SheetTempoChange(1, HanbaeMark.Slower, null))
            .withTempoChange(SheetTempoChange(2, HanbaeMark.Original, 60.0))
        assertEquals(2.0, assertNotNull(changed.estimatedDurationSeconds(60)), 1e-9)
    }

    @Test fun saveReopenAndGlobalTempoApplyTheRatioOnce() {
        val edited = draft().withTempoChange(SheetTempoChange(1, HanbaeMark.Faster, 120.0)).copy(bpm = 90)
        val score = edited.toCommonScore()
        val expressions = assertNotNull(edited.exportExpressions(null, commonSourceSha256(Json.encodeToJsonElement(score)), 60))
        val reopened = score.editingDraft(edited.title, 90, null).withTempoExpressions(expressions, referenceBpm = 60)
        assertEquals(4.0 / 3, assertNotNull(reopened.estimatedDurationSeconds(90)), 1e-9)
        assertEquals(1.0, assertNotNull(reopened.estimatedDurationSeconds(120)), 1e-9)
        assertEquals(edited.tempoChanges, reopened.tempoChanges)
    }

    @Test fun confirmedPlanAndEditorEstimateUseTheSameBeatUnitAndBpmRatio() {
        val source = smallScoreData().let { it.copy(music = it.music.copy(ticksPerBeat = 12)) }
        val edited = source.editingDraft("구간 길이", 60, null)
            .withTempoChange(SheetTempoChange(1, null, 120.0, 3, startBpm = 30.0)).copy(bpm = 90)
        val score = edited.toCommonScore()
        // A confirmed companion has already been scaled by the server to the saved base BPM.
        val confirmed = assertNotNull(edited.exportExpressions(null, commonSourceSha256(Json.encodeToJsonElement(score)), 90))
        val editorSeconds = assertNotNull(edited.estimatedDurationSeconds(90))
        assertEquals(editorSeconds * 1000, assertNotNull(score.estimatedModelDurationMs(confirmed, 90)), 1e-9)
    }

    @Test fun unsupportedLengthSymbolsAndExplicitRepeatsStillHaveNoEstimate() {
        for (symbol in listOf("반복표", "도돌이표", "덧길이표", "반길이표")) {
            val value = draft().copy(sigimsae = mapOf(NoteSlot(0, 0) to listOf(Sigimsae(symbol))))
            assertNull(value.estimatedDurationSeconds(60), symbol)
        }
        val repeated = draft().withTempoExpressions(buildJsonObject {
            put("performancePlan", buildJsonObject {
                put("repeats", buildJsonArray { add(buildJsonObject { put("start_gak", 1); put("end_gak", 1); put("times", 2) }) })
            })
        })
        assertNull(repeated.estimatedDurationSeconds(60))
    }

    @Test fun enteringANewBpmChangesDurationWithoutChangingTheDraft() {
        val draft = draft()
        assertEquals(3.0, assertNotNull(draft.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(1.5, assertNotNull(draft.estimatedDurationSeconds(120)), 1e-9)
        assertEquals(60, draft.bpm)
    }

    @Test fun dottedQuarterUsesTheSourceTickRatioWithoutCountingSubdivisionsTwice() {
        val score = smallScoreData().let { it.copy(music = it.music.copy(ticksPerBeat = 12, ticksPerJeonggan = 4)) }
        val draft = score.editingDraft("점4분음표", 60, null)
        assertEquals("DOTTED_QUARTER", score.music.bpmUnit)
        assertEquals(1.0, assertNotNull(draft.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(0.5, assertNotNull(draft.estimatedDurationSeconds(120)), 1e-9)
    }

    @Test fun blankAndPartiallyFilledCellsStillContributeTheirFullDuration() {
        val draft = draft().copy(cells = listOf(emptyList(), listOf(ScoreNote.Rest, ScoreNote.Blank), emptyList()))
        assertEquals(3.0, assertNotNull(draft.estimatedDurationSeconds(60)), 1e-9)
        val longer = draft.addColumnAfter(0)
        assertEquals(5, longer.cells.size)
        assertEquals(5.0, assertNotNull(longer.estimatedDurationSeconds(60)), 1e-9)
        assertEquals(3.0, assertNotNull(longer.deleteColumn(1).estimatedDurationSeconds(60)), 1e-9)
    }

    @Test fun clearingJangdanKeepsTheOriginalTimingAvailable() {
        val draft = draft().copy(jangdanId = "3")
        val cleared = SheetEditUiState(EditSource.Mine("31"), draft).clearJangdan().draft
        assertNull(cleared.jangdanId)
        assertEquals("", cleared.jangdanName)
        assertEquals(draft.estimatedDurationSeconds(90), cleared.estimatedDurationSeconds(90))
        assertEquals(2.0, assertNotNull(cleared.estimatedDurationSeconds(90)), 1e-9)
    }

    @Test fun legacyDraftUsesJangdanSubdivisionsAndUnknownTimingHasNoEstimate() {
        val legacy = SheetDraft("예시", Jangdan("세마치", listOf(3), 1, 3), 60, List(3) { emptyList() })
        assertEquals(1.0, assertNotNull(legacy.estimatedDurationSeconds(60)), 1e-9)
        assertNull(legacy.copy(jangdan = null, columnSizes = listOf(3)).estimatedDurationSeconds(60))
    }

    @Test fun invalidBpmOrSourceTimingDoesNotProduceAMisleadingDuration() {
        val draft = draft()
        for (bpm in listOf(-1, 0)) assertNull(draft.estimatedDurationSeconds(bpm))
        for (ticks in listOf(0, -1)) {
            val source = assertNotNull(draft.original)
            assertNull(draft.copy(original = source.copy(music = source.music.copy(ticksPerBeat = ticks))).estimatedDurationSeconds(60))
            assertNull(draft.copy(original = source.copy(music = source.music.copy(ticksPerJeonggan = ticks))).estimatedDurationSeconds(60))
        }
    }
}
