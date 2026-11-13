package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.ScoreNote
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

    @Test fun splittingHorizontalUnitsKeepsEachOriginalEventReasonAtItsUnit() {
        val score = smallScoreData().let { source -> source.copy(
            columns = source.columns.mapIndexed { index, column -> if (index != 0) column else column.copy(
                cells = column.cells.mapIndexed { row, cell -> if (row != 0) cell else cell.copy(units = cell.units.take(2)) }) },
            events = source.events.map { event -> when (event.id) {
                "c0-j0-r0-u0" -> event.copy(durationTick = 2)
                "c0-j0-r0-u1" -> event.copy(startTick = 2, durationTick = 6)
                else -> event
            } },
        ) }
        val before = SheetEditUiState(EditSource.Mine("31"), score.editingDraft("선택 정보", 60, null))
            .copy(reviewIssues = listOf(
                SheetReviewIssue("first", "c0-j0", "LOW_CONFIDENCE", eventId = "c0-j0-r0-u0", reason = "GAP_IN_VOICE"),
                SheetReviewIssue("second", "c0-j0", "LOW_CONFIDENCE", eventId = "c0-j0-r0-u1", reason = "OTHER_REASON"),
            ))
        assertEquals(listOf("second"), before.reviewIssuesAt(0, 1).map { it.id })

        val split = before.copy(draft = before.draft.split(0))
        assertEquals(3, split.draft.cells[0].size)
        assertEquals(listOf("first"), split.reviewIssuesAt(0, 0).map { it.id })
        assertEquals(listOf("second"), split.reviewIssuesAt(0, 1).map { it.id })
        assertTrue(split.reviewIssuesAt(0, 2).isEmpty(), "새 빈 소박에 원본 검수를 연결하지 않는다")
        assertEquals("", split.draft.toCommonScore().columns[0].cells[1].units[0].eventId)
        assertTrue(split.reviewIssuesAt(1, 0).isEmpty(), "새 빈 소박으로 끊어진 이음의 이벤트를 추정하지 않는다")
        val continued = split.copy(draft = split.draft.putAt(0, 2, ScoreNote.Hold))
        assertEquals(listOf("second"), continued.reviewIssuesAt(1, 0).map { it.id }, "빈 자리를 지속으로 채우면 이어지는 이벤트 검수를 유지한다")
        assertEquals(2, split.completionValidation.pendingReviewCount)
        assertTrue(split.checkedIssueIds.isEmpty())
        assertEquals(before.reviewIssues, split.reviewIssues)

        val mergedAndSplit = before.copy(draft = before.draft.merge(0).split(0))
        assertEquals(listOf("first"), mergedAndSplit.reviewIssuesAt(0, 0).map { it.id })
        assertTrue(mergedAndSplit.reviewIssuesAt(0, 1).isEmpty(), "합친 뒤 새로 나눈 빈 소박에 삭제한 이벤트 검수를 붙이지 않는다")

        val held = before.copy(draft = before.draft.putAt(0, 1, ScoreNote.Hold))
        assertEquals(listOf("first"), held.reviewIssuesAt(0, 1).map { it.id }, "지속으로 바꾼 소박은 현재 이어지는 이벤트의 사유만 보여 준다")
    }
}
