package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.SheetWorkingCopy
import com.ssafy.eolssu.data.song.smallScoreData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SheetViewingTest {
    private val score = smallScoreData()
    private val draft = SheetWorkingCopy("변환 초안", null, 55, score, "1", 0)
    private val document = SheetDocument("31", "변환 악보", null, 60, "1", score, draft, "NEEDS_REVIEW", "DONE", emptyList())

    @Test
    fun opensConvertedScoreBeforeReviewEvenWhenWorkingCopyExists() {
        val view = assertNotNull(document.viewingContent())
        assertEquals("변환 악보", view.draft.title)
        assertEquals(score.columns.size, view.draft.toSong().score.columns.size)
        assertFalse(view.practiceReady)
    }

    @Test
    fun opensConversionDraftWhenThereIsNoSavedScore() {
        val view = assertNotNull(document.copy(score = null).viewingContent())
        assertEquals("변환 초안", view.draft.title)
        assertEquals(55, view.draft.bpm)
    }

    @Test
    fun letsFreshDocumentStatusOverrideStaleReviewBadge() {
        val view = assertNotNull(document.copy(reviewStatus = "EVALUABLE").viewingContent())
        assertTrue(view.practiceReady)
    }

    @Test
    fun doesNotOpenUnfinishedConversion() {
        assertNull(document.copy(conversionStatus = "RUNNING").viewingContent())
        assertNull(document.copy(conversionStatus = "FAILED").viewingContent())
    }
}
