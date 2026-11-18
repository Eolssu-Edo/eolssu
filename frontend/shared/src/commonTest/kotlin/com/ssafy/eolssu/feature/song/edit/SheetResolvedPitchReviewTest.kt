package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.data.song.*
import kotlin.test.*

class SheetResolvedPitchReviewTest {
    private val capability = CatalogAuthoringCapabilities("2", "SOGEUM", "v1", 63,
        AuthoringPhysicalRange(verificationStatus = "UNKNOWN"), listOf("황" to 0, "중" to 0).map { (yul, octave) ->
            val semitone = "황대태협고중유임이남무응".indexOf(yul) + 12 * octave
            AuthoringPitchCapability(yul, yul, octave, yul, yul, semitone, 63 + semitone,
                "UNKNOWN", fingering = "REGISTERED", notation = "SUPPORTED", synthesis = "SUPPORTED",
                selectable = true, reasonCodes = emptyList())
        })

    private fun state(): SheetEditUiState {
        val score = smallScoreData().let { it.copy(score = it.score.copy(instrument = "SOGEUM"), music = it.music.copy(jangdan = "")) }
        return SheetEditUiState(EditSource.Mine("31"), score.editingDraft("검사", 60, null),
            authoringCapabilities = capability, completionCatalogState = SheetCompletionCatalogState.Ready,
            availableJangdans = emptyList(), reviewIssues = listOf(
                SheetReviewIssue("range", "c0-j0", "OUT_OF_RANGE", eventId = "c0-j0-r0-u1"),
                SheetReviewIssue("tie", "c0-j1", "UNREGISTERED_PITCH", eventId = "c0-j0-r0-u1"),
                SheetReviewIssue("manual", "c0-j0", "ORNAMENT_DROPPED", eventId = "c0-j0-r0-u0"),
            ))
    }

    private fun SheetEditUiState.adapt(): SheetEditUiState {
        val plans = planPitchAdaptation(draft.sourcePitchNotes(), capability.items.map { AdaptationPitch(it.midi, it.yul, it.octave) })
        return applyAdaptation(plans.first { it.transpose == 0 }, capability)
    }

    @Test fun adaptationClearsResolvedPitchMarksButKeepsManualReview() {
        val before = state()
        assertEquals(3, before.completionValidation.pendingReviewCount)
        val after = before.adapt()
        assertEquals(0, after.completionValidation.invalidPitchCount)
        assertEquals(1, after.completionValidation.pendingReviewCount)
        assertEquals(setOf("range", "tie"), after.validatedCheckedIssueIds)
        assertEquals(setOf(ScoreUnitKey("c0-j0", 0, 0)), after.completionValidation.correctionUnitKeys)
        assertEquals("manual", after.nextReviewIssue?.id)
        assertTrue(after.checkedIssueIds.isEmpty(), "자동 판정을 사람의 확인으로 저장하지 않는다")
        assertEquals(before.reviewIssues, after.reviewIssues, "원본 검수 목록을 삭제하지 않는다")
    }

    @Test fun undoRestoresPitchErrorsAndReopeningRecomputesResolvedIds() {
        val before = state()
        val after = before.adapt().copy(checkedIssueIds = setOf("manual"))
        assertTrue(after.completionValidation.canComplete)
        val undone = after.undo()
        assertTrue(undone.completionValidation.invalidPitchCount > 0)
        assertTrue("range" !in undone.validatedCheckedIssueIds)
        assertTrue("tie" !in undone.validatedCheckedIssueIds)
        assertFalse(undone.completionValidation.canComplete)
        val reopened = state().copy(draft = after.draft.toCommonScore().editingDraft("검사", 60, null))
        assertEquals(setOf("range", "tie"), reopened.validatedCheckedIssueIds)
        assertEquals(1, reopened.completionValidation.pendingReviewCount)
    }

    @Test fun unavailableCatalogOrAnUnresolvedPitchKeepsRangeReviewPending() {
        val before = state()
        assertEquals(3, before.completionValidation.pendingReviewCount)
        for (catalog in listOf(SheetCompletionCatalogState.Loading, SheetCompletionCatalogState.Failed)) {
            val waiting = before.adapt().copy(completionCatalogState = catalog)
            assertTrue(waiting.validatedCheckedIssueIds.isEmpty())
            assertEquals(3, waiting.completionValidation.pendingReviewCount)
        }
    }

    @Test fun fiveUnresolvedNotesKeepOnlyTheirOwnFiveCellsRed() {
        val original = smallScoreData()
        val events = (0..5).map { index -> CommonScoreData.Event("c0-j$index-r0-u0", "NOTE",
            if (index == 0) "황" else "태", 0, index * 4L, 4, "c0-j$index", false, emptyList()) }
        val score = original.copy(score = original.score.copy(instrument = "SOGEUM"),
            music = original.music.copy(jangdan = ""),
            columns = listOf(CommonScoreData.Column(0, events.mapIndexed { index, event ->
                CommonScoreData.Cell(event.cellId, index, listOf(CommonScoreData.Unit(0, 0, event.yul!!, event.id))) })),
            events = events)
        val before = state().copy(draft = score.editingDraft("미해결5음", 60, null),
            reviewIssues = events.mapIndexed { index, event -> SheetReviewIssue("range$index", event.cellId, "OUT_OF_RANGE", eventId = event.id) })
        val plan = planPitchAdaptation(before.draft.sourcePitchNotes(), capability.items.map { AdaptationPitch(it.midi, it.yul, it.octave) })
            .first { it.transpose == 0 }
        assertEquals(5, plan.unresolved)
        val after = before.applyAdaptation(plan, capability)
        assertEquals(5, after.completionValidation.pendingReviewCount)
        assertEquals((1..5).toSet(), after.completionValidation.correctionCellIndices)
        assertEquals(setOf("range0"), after.validatedCheckedIssueIds)
        assertFalse(after.completionValidation.canComplete)
    }

    @Test fun brokenStructureDoesNotAutomaticallyResolveRangeReview() {
        val adapted = state().adapt()
        val score = adapted.draft.toCommonScore().let { original -> original.copy(events = original.events.map {
            if (it.id == "c0-j0-r0-u1") it.copy(durationTick = 6) else it
        }) }
        val broken = adapted.copy(draft = score.editingDraft("끊긴 이음", 60, null))
        assertTrue(broken.completionValidation.hasInvalidStructure)
        assertTrue(broken.validatedCheckedIssueIds.isEmpty())
        assertEquals(3, broken.completionValidation.pendingReviewCount)
    }

    @Test fun globalPitchListOrAnotherInstrumentsCapabilityCannotAutomaticallyResolveRangeReview() {
        val adapted = state().adapt()
        val unverified = listOf(
            adapted.copy(authoringCapabilities = null, availablePitches = capability.selectablePitches),
            adapted.copy(authoringCapabilities = capability.copy(instrumentCode = "DANSO")),
        )
        for (current in unverified) {
            assertTrue(current.validatedCheckedIssueIds.isEmpty())
            assertEquals(3, current.completionValidation.pendingReviewCount)
        }
    }
}
