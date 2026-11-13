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
}
