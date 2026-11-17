package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreUnitKey
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 신뢰도 낮음 검수는 저장을 막지 않는 노란 표시이고 눌러서 지울 수 있다(S15P31E102-1100). */
internal class SheetAdvisoryReviewTest {
    private fun state(vararg issues: SheetReviewIssue) =
        SheetEditUiState(EditSource.Mine("31"), smallScoreData().editingDraft("검사", 60, null), reviewIssues = issues.toList())

    @Test fun lowConfidenceIsMarkedYellowWithoutBlockingCompletion() {
        val state = state(SheetReviewIssue("low", "c1-j0", "LOW_CONFIDENCE"))
        val validation = state.completionValidation
        assertEquals(0, validation.pendingReviewCount)
        assertTrue(validation.correctionCellIndices.isEmpty())
        assertEquals(setOf(2), validation.advisoryCellIndices)
        assertEquals(listOf("low"), state.reviewIssuesAt(2).map { it.id }, "노란 칸을 누르면 이유 창을 띄울 수 있다")
        assertEquals(0, state.pendingChecks)
        assertEquals(null, state.nextReviewIssue, "다시 볼 곳 이동은 고쳐야 할 칸만 따라간다")
    }

    @Test fun blockingIssueOnTheSameCellStaysRedAndHidesTheYellowMark() {
        val state = state(SheetReviewIssue("low", "c1-j0", "LOW_CONFIDENCE"), SheetReviewIssue("orn", "c1-j0", "ORNAMENT_DROPPED"))
        assertEquals(setOf(2), state.completionValidation.correctionCellIndices)
        assertTrue(state.completionValidation.advisoryCellIndices.isEmpty())
        assertEquals("orn", state.select(2).selectedReviewIssue?.id, "'이대로 둘게요'는 저장을 막는 항목을 확인한다")
    }

    @Test fun eventLevelLowConfidenceMarksOnlyItsUnit() {
        val state = state(SheetReviewIssue("low", "c0-j0", "LOW_CONFIDENCE", eventId = "c0-j0-r0-u0"))
        assertEquals(setOf(ScoreUnitKey("c0-j0", 0, 0)), state.completionValidation.advisoryUnitKeys)
    }

    @Test fun dismissingRemovesTheMarkAndSurvivesLaterEdits() {
        val state = state(SheetReviewIssue("low", "c1-j0", "LOW_CONFIDENCE"))
        val dismissed = state.dismissAdvisoryIssues(2)
        assertTrue(dismissed.completionValidation.advisoryCellIndices.isEmpty())
        assertTrue(dismissed.reviewIssuesAt(2).isEmpty())
        val edited = dismissed.select(2).putYul(Yul.Taeju)
        assertTrue(edited.completionValidation.advisoryCellIndices.isEmpty(), "지운 표시는 칸을 고쳐도 다시 뜨지 않는다")
    }

    @Test fun dismissingDoesNotConfirmBlockingIssues() {
        val state = state(SheetReviewIssue("low", "c1-j0", "LOW_CONFIDENCE"), SheetReviewIssue("orn", "c1-j0", "ORNAMENT_DROPPED"))
        val dismissed = state.dismissAdvisoryIssues(2)
        assertEquals(setOf("low"), dismissed.checkedIssueIds)
        assertEquals(1, dismissed.completionValidation.pendingReviewCount)
    }

    @Test fun confirmedSaveResolvesRemainingLowConfidenceButNotUncheckedBlockers() {
        val state = state(SheetReviewIssue("low", "c1-j0", "LOW_CONFIDENCE"), SheetReviewIssue("orn", "c0-j1", "ORNAMENT_DROPPED"))
        assertEquals(listOf("low"), state.confirmedResolvedIssueIds)
        assertEquals(listOf("low", "orn"), state.copy(checkedIssueIds = setOf("orn")).confirmedResolvedIssueIds)
    }
}
