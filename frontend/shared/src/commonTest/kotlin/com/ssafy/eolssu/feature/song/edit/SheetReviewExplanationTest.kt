package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SheetReviewExplanationTest {
    private fun document(detail: String) = SheetDocument(
        "31", "인식 검수", null, 60, "1", smallScoreData(), null, "NEEDS_REVIEW", "DONE",
        listOf(Json.parseToJsonElement("""{"issueId":"gap","cellId":"c1-j0","type":"LOW_CONFIDENCE","detail":$detail}""").jsonObject),
    )

    @Test fun selectingAGapRestPreservesItsReasonWithoutCheckingOrEditingIt() {
        val document = document("""{"reason":"GAP_IN_VOICE","measure":114}""")
        val score = requireNotNull(document.score)
        val before = SheetEditUiState(EditSource.Mine("31"), score.editingDraft("인식 검수", 60, null))
            .copy(reviewIssues = document.editorReviewIssues(score))
        val selected = before.select(2)

        assertEquals("GAP_IN_VOICE", selected.selectedReviewIssue?.reason)
        assertEquals("LOW_CONFIDENCE", selected.selectedReviewIssue?.kind)
        assertTrue(selected.pitchCorrectionsAt(2).isEmpty())
        assertEquals(1, selected.completionValidation.pendingReviewCount)
        assertTrue(selected.checkedIssueIds.isEmpty())
        assertEquals(before.draft, selected.draft)
        assertFalse(selected.canUndo)
    }

    @Test fun missingAndDifferentReasonsAreNotReplacedByAGapReason() {
        for (detail in listOf("{}", "null")) {
            val document = document(detail)
            assertNull(document.editorReviewIssues(requireNotNull(document.score)).single().reason)
        }
        val document = document("""{"reason":"OTHER_REASON"}""")
        assertEquals("OTHER_REASON", document.editorReviewIssues(requireNotNull(document.score)).single().reason)
    }

    @Test fun clickedUnitOnlyIncludesItsEventAndWholeCellReviews() {
        val state = SheetEditUiState(EditSource.Mine("31"), smallScoreData().editingDraft("선택 정보", 60, null))
            .copy(reviewIssues = listOf(
                SheetReviewIssue("cell", "c0-j0", "LOW_CONFIDENCE"),
                SheetReviewIssue("first", "c0-j0", "LOW_CONFIDENCE", eventId = "c0-j0-r0-u0"),
                SheetReviewIssue("second", "c0-j0", "LOW_CONFIDENCE", eventId = "c0-j0-r0-u1"),
                SheetReviewIssue("rest", "c1-j0", "LOW_CONFIDENCE", reason = "GAP_IN_VOICE"),
            ))
        assertEquals(listOf("cell", "first"), state.reviewIssuesAt(0, 0).map { it.id })
        assertEquals(listOf("cell", "second"), state.reviewIssuesAt(0, 1).map { it.id })
        assertEquals(listOf("second"), state.reviewIssuesAt(1, 0).map { it.id }, "이음은 실제로 가리키는 음의 검수만 보여 준다")
        assertEquals(listOf("rest"), state.reviewIssuesAt(2, 0).map { it.id })
        assertTrue(state.reviewIssuesAt(0, 99).isEmpty())
        assertTrue(state.reviewIssuesAt(99, 0).isEmpty())
    }
}
