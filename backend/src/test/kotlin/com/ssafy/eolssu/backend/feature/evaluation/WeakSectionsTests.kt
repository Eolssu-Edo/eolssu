package com.ssafy.eolssu.backend.feature.evaluation

import com.ssafy.eolssu.backend.feature.evaluation.domain.NoteCell
import com.ssafy.eolssu.backend.feature.evaluation.domain.PracticeEvaluationNote
import com.ssafy.eolssu.backend.feature.evaluation.domain.WeakSection
import com.ssafy.eolssu.backend.feature.evaluation.domain.WeakSections
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class WeakSectionsTests {
    // 각 0~4에 정간 2개씩, 정간마다 음표 1개: 음표 순번 n은 각 n/2, 정간 n%2.
    private val cells = (0 until 10).map { NoteCell(it / 2, "c${it / 2}-j${it % 2}") }
    private fun notes(vararg wrong: Pair<Int, String>) = (0 until 10).map { idx ->
        PracticeEvaluationNote(idx, wrong.toMap()[idx] ?: "CORRECT", null, null, null, 1, null)
    }

    @Test fun `all correct recommends nothing`() {
        val report = WeakSections.recommend(notes(), cells, 0, 9, 3)
        assertEquals(0, report.totalErrorCellCount)
        assertEquals(emptyList(), report.sections)
    }

    @Test fun `densest two column window wins and counts error cells not notes`() {
        val report = WeakSections.recommend(notes(1 to "TIMING", 4 to "PITCH", 5 to "PITCH", 6 to "MISSED"), cells, 0, 9, 1)
        assertEquals(4, report.totalErrorCellCount)
        assertEquals(listOf(WeakSection(4, 7, 2, 3, 3, "PITCH")), report.sections)
    }

    @Test fun `several sections do not overlap and edge columns without errors are trimmed`() {
        val report = WeakSections.recommend(notes(0 to "TIMING", 4 to "PITCH", 5 to "PITCH", 9 to "MISSED"), cells, 0, 9, 10)
        assertEquals(listOf(
            WeakSection(4, 5, 2, 2, 2, "PITCH"),
            WeakSection(0, 1, 0, 0, 1, "TIMING"),
            WeakSection(8, 9, 4, 4, 1, "MISSED"),
        ), report.sections)
    }

    @Test fun `ties prefer the earlier window and missed over pitch over timing`() {
        val report = WeakSections.recommend(notes(2 to "PITCH", 3 to "MISSED", 6 to "TIMING", 7 to "PITCH"), cells, 0, 9, 1)
        assertEquals(listOf(WeakSection(2, 3, 1, 1, 2, "MISSED")), report.sections)
    }

    @Test fun `length loses a tie with every older error`() {
        val report = WeakSections.recommend(notes(2 to "LENGTH", 3 to "TIMING"), cells, 0, 9, 1)
        assertEquals("TIMING", report.sections.single().dominantError)
        assertEquals("LENGTH", WeakSections.recommend(notes(2 to "LENGTH", 3 to "LENGTH", 6 to "PITCH"), cells, 0, 9, 1).sections.single().dominantError)
    }

    @Test fun `pitch wins a tie with timing`() {
        val report = WeakSections.recommend(notes(2 to "TIMING", 3 to "PITCH"), cells, 0, 9, 1)
        assertEquals("PITCH", report.sections.single().dominantError)
    }

    @Test fun `section practice stays inside the session range`() {
        val report = WeakSections.recommend(notes(0 to "PITCH", 3 to "TIMING", 4 to "TIMING"), cells, 3, 6, 1)
        assertEquals(2, report.totalErrorCellCount)
        assertEquals(listOf(WeakSection(3, 5, 1, 2, 2, "TIMING")), report.sections)
    }
}
