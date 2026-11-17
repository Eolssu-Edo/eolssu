package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.data.song.CommonScoreData
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

internal class SheetJangdanCorrectionTest {
    private val danmori = CatalogJangdanSummary("7", "단모리", 120, "QUARTER", 8, 4, 1)
    private val gutgeori = CatalogJangdanSummary("2", "굿거리", 80, "DOTTED_QUARTER", 12, 4, 1)
    private val semachi = CatalogJangdanSummary("1", "세마치", 96, "DOTTED_QUARTER", 9, 3, 1)
    private val jungmori = CatalogJangdanSummary("3", "중모리", 80, "QUARTER", 12, 4, 2)
    private val options = listOf(danmori, gutgeori, semachi, jungmori)

    @Test fun sameRecognizedGridOffersTheSameCorrectionsRegardlessOfInitialJangdan() {
        val fromJungmori = state(recognized("QUARTER", 12, 36, "중모리"))
        val fromGutgeori = state(recognized("DOTTED_QUARTER", 12, 12, "굿거리"))
        fun selectable(state: SheetEditUiState) = options.filter { state.selectJangdan(it.jangdanId).draft.jangdanId == it.jangdanId }.map { it.jangdanId }
        assertEquals(listOf("7", "2"), selectable(fromJungmori))
        assertEquals(selectable(fromJungmori), selectable(fromGutgeori))
    }

    @Test fun correctionPreservesGridPitchesTiesAndSourceAndUndoRestoresTiming() {
        val before = state(recognized("QUARTER", 12, 36, "중모리"))
        val changed = before.selectJangdan(gutgeori.jangdanId)
        val score = changed.draft.toCommonScore()
        assertEquals("2", changed.draft.jangdanId)
        assertEquals("DOTTED_QUARTER", score.music.bpmUnit)
        assertEquals(score.music.ticksPerJeonggan, score.music.ticksPerBeat)
        assertEquals(before.draft.original!!.columns, score.columns)
        assertEquals(before.draft.original.events, score.events)
        assertEquals(before.draft.authoring, changed.draft.authoring)
        assertEquals(before.draft.cells, changed.draft.cells)
        assertEquals(listOf(4, 4), changed.draft.sizes)
        assertTrue(changed.draft.isCompatibleWith(gutgeori))
        score.toScore()
        assertEquals(before.draft, changed.undo().draft)
        assertEquals(changed.draft, changed.undo().redo().draft)
    }

    @Test fun correctionsDoNotCompoundAndRemainAvailableAfterReload() {
        val before = state(recognized("DOTTED_QUARTER", 12, 12, "굿거리"))
        val first = before.selectJangdan(danmori.jangdanId)
        assertEquals("7", first.draft.jangdanId)
        val returned = first.copy(jangdanPicker = SheetEditJangdanPicker.Ready(options)).selectJangdan(gutgeori.jangdanId)
        assertEquals(before.draft.toCommonScore(), returned.draft.toCommonScore())
        val saved = first.draft.toCommonScore().let { it.copy(provenance = it.provenance.copy(converter = "app-editor-1")) }
        val reloaded = state(saved, before.draft.authoring)
        assertEquals("2", reloaded.updateSettings(100, gutgeori.jangdanId).draft.jangdanId)
        assertEquals(100, reloaded.updateSettings(100, gutgeori.jangdanId).draft.bpm)
    }

    @Test fun independentStaffTimingAndTempoPlansAreNotReinterpreted() {
        val staff = recognized("QUARTER", 12, 12, "").let { it.copy(provenance = it.provenance.copy(converter = "staff-omr/audiveris-5.11.0")) }
        val fixed = state(staff)
        assertEquals(fixed.draft, fixed.selectJangdan(gutgeori.jangdanId).draft)
        val planned = state(recognized("QUARTER", 12, 12, "")).copy(hasTempoPlan = true)
        assertEquals(planned.draft, planned.selectJangdan(gutgeori.jangdanId).draft)
        assertEquals("7", planned.selectJangdan(danmori.jangdanId).draft.jangdanId)
    }

    @Test fun incompleteEditedColumnsCannotBeMadeCompatibleByChangingJangdan() {
        val before = state(recognized("QUARTER", 12, 12, ""))
        val shortened = before.copy(draft = before.draft.copy(cells = before.draft.cells.dropLast(1),
            sourceCellIds = before.draft.sourceCellIds!!.dropLast(1), columnSizes = listOf(4, 3)))
        assertEquals(shortened.draft, shortened.selectJangdan(gutgeori.jangdanId).draft)
    }

    @Test fun tickRescalingKeepsTheOriginalPitchIdentityForFurtherAdaptation() {
        val original = recognized("QUARTER", 5, 2, "단모리", grid = 10)
        val option = CatalogJangdanSummary("10", "세령산", 63, "QUARTER", 10, 4, 3)
        val before = state(original).copy(jangdanPicker = SheetEditJangdanPicker.Ready(listOf(option)))
        assertTrue(before.draft.originalPitchLayoutMatches())
        val corrected = before.selectJangdan(option.jangdanId).draft
        assertEquals("10", corrected.jangdanId)
        val score = corrected.toCommonScore()
        assertEquals(6, score.music.ticksPerJeonggan)
        assertEquals(4, score.music.ticksPerBeat)
        assertEquals(original.events.map { it.durationTick * 3 }, score.events.map { it.durationTick })
        assertEquals(original.columns, score.columns)
        score.toScore()
        assertTrue(corrected.originalPitchLayoutMatches())
        assertEquals(before.draft.sourcePitchNotes(), corrected.sourcePitchNotes())
    }

    @Test fun correctionKeepsCurrentPitchEditsInsteadOfRestoringTheRecognitionSnapshot() {
        val initial = state(recognized("QUARTER", 12, 36, "중모리"))
        val edited = initial.copy(draft = initial.draft.copy(cells = initial.draft.cells.mapIndexed { i, notes ->
            if (i == 2) listOf(com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("태", 0)) else notes
        }))
        val changed = edited.selectJangdan(gutgeori.jangdanId)
        assertEquals("2", changed.draft.jangdanId)
        assertEquals(edited.draft.toCommonScore().events, changed.draft.toCommonScore().events)
        assertEquals(initial.draft.authoring, changed.draft.authoring)
    }

    @Test fun legacyRecognizedScoreWorksWithoutMetadataAndUnknownPatternStaysUnavailable() {
        val before = state(recognized("QUARTER", 12, 36, "중모리"), metadata = null)
        assertTrue(before.draft.canSelectJangdan(gutgeori))
        assertFalse(before.draft.canSelectJangdan(gutgeori.copy(patternCellEighths = null)))
        assertEquals("2", before.selectJangdan(gutgeori.jangdanId).draft.jangdanId)
    }

    @Test fun correctionUsesTheTickResolutionOfTheCurrentSplitEdits() {
        val initial = state(recognized("QUARTER", 1, 3, "중모리"))
        val edited = initial.copy(draft = initial.draft.copy(cells = initial.draft.cells.mapIndexed { i, notes ->
            if (i == 2) listOf(com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("황", 0),
                com.ssafy.eolssu.core.model.ScoreNote.SourcePitch("태", 0)) else notes
        }))
        val current = edited.draft.toCommonScore()
        assertEquals(6, current.music.ticksPerJeonggan)
        current.toScore()
        val corrected = edited.selectJangdan(gutgeori.jangdanId).draft.toCommonScore()
        corrected.toScore()
        assertEquals(current.columns, corrected.columns)
        assertEquals(current.events, corrected.events)
        assertEquals(6, corrected.music.ticksPerJeonggan)
        assertEquals(6, corrected.music.ticksPerBeat)
    }

    @Test fun changingJeongganboJangdanUsesTheNewJangdanDefaultTempoNotTheOldNumber() {
        // 4박 기본 반주(160♩)로 올린 정간보를 굿거리로 바꾸면 160♩.이 아니라 얼쑤 기본 빠르기 ♩.=66이 된다(S15P31E102-1054).
        val fromFourBeat = state(recognized("QUARTER", 12, 36, "중모리")).let { it.copy(draft = it.draft.copy(bpm = 160)) }
        val gutgeori66 = gutgeori.copy(defaultBpm = 66)
        val changed = fromFourBeat.copy(jangdanPicker = SheetEditJangdanPicker.Ready(listOf(gutgeori66))).selectJangdan("2").draft
        assertEquals("DOTTED_QUARTER", changed.toCommonScore().music.bpmUnit)
        assertEquals(66, changed.bpm)
        assertEquals(160, fromFourBeat.selectJangdan(danmori.jangdanId).undo().draft.bpm)
        // 박 길이를 바꾸지 않는 장단으로 바꿔도 새 장단의 기본 빠르기를 쓴다.
        assertEquals(120, state(recognized("DOTTED_QUARTER", 12, 12, "굿거리")).selectJangdan(danmori.jangdanId).draft.bpm)
    }

    @Test fun staffScoresAndTempoPlansKeepTheirWrittenTempoWhenJangdanChanges() {
        val planned = state(recognized("QUARTER", 12, 12, "")).copy(hasTempoPlan = true)
        assertEquals(80, planned.selectJangdan(danmori.jangdanId).draft.bpm)
        val staff = smallScoreData().let { it.copy(music = it.music.copy(jangdan = "")) }
        val compatible = CatalogJangdanSummary("4", "굿거리", 66, "DOTTED_QUARTER", 3, 1, 1)
        val fixed = SheetEditUiState(source = EditSource.Mine("31"), draft = staff.editingDraft("오선보", 132, null),
            availableJangdans = listOf(compatible), jangdanPicker = SheetEditJangdanPicker.Ready(listOf(compatible)))
        val chosen = fixed.selectJangdan(compatible.jangdanId).draft
        assertEquals("4", chosen.jangdanId)
        assertEquals(132, chosen.bpm)
    }

    internal fun state(score: CommonScoreData, metadata: JsonObject? = sourceMetadata(score)) = SheetEditUiState(
        source = EditSource.Mine("31"),
        draft = score.editingDraft("인식 악보", 80, null).copy(authoring = metadata),
        availableJangdans = options, jangdanPicker = SheetEditJangdanPicker.Ready(options),
    )

    companion object {
        internal fun sourceMetadata(score: CommonScoreData) = buildJsonObject {
            put("schema", "sheet-authoring/v1")
            putJsonObject("source") {
                put("kind", "RECOGNIZED"); put("scoreSha256", "a".repeat(64)); put("score", Json.encodeToJsonElement(score))
                putJsonArray("notes") { score.events.filter { it.kind == "NOTE" }.forEach { event -> addJsonObject {
                    put("eventId", event.id); put("cellId", event.cellId); put("midi", 88)
                    put("startTick", event.startTick); put("durationTick", event.durationTick)
                } } }
            }
        }

        internal fun recognized(unit: String, beat: Int, cell: Int, name: String, grid: Int = 4): CommonScoreData {
            val base = smallScoreData()
            val columns = (0..1).map { c -> CommonScoreData.Column(c, (0 until grid).map { j ->
                val id = "c$c-j$j"
                CommonScoreData.Cell(id, j, listOf(CommonScoreData.Unit(0, 0,
                    if (j == 1) "-" else if (j == grid - 1) "쉼" else "황", if (j == 1) "c$c-j0-r0-u0" else "$id-r0-u0")))
            }) }
            val events = (0..1).flatMap { c -> (0 until grid).filter { it != 1 }.map { j ->
                CommonScoreData.Event("c$c-j$j-r0-u0", if (j == grid - 1) "REST" else "NOTE", if (j == grid - 1) null else "황",
                    if (j == grid - 1) null else 0, (c.toLong() * grid + j) * cell, (if (j == 0) 2L else 1L) * cell,
                    "c$c-j$j", false, emptyList())
            } }
            return base.copy(music = base.music.copy(jangdan = name, bpmUnit = unit, ticksPerBeat = beat, ticksPerJeonggan = cell),
                columns = columns, events = events,
                provenance = base.provenance.copy(converter = "jeongganbo-text/standard_score_text@3"))
        }
    }
}
