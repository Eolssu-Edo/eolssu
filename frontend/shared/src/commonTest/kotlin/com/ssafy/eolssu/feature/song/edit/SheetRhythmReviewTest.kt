package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.*
import kotlin.test.*

class SheetRhythmReviewTest {
    @Test fun keepsTheRecognizedSignatureAndSpecificMeasureReviewReason() {
        val notice = Json.parseToJsonElement("""{"type":"LOW_CONFIDENCE","pageIdx":2,"detail":{"reason":"TIME_SIGNATURE_REVIEW_REQUIRED","timeSignature":"6/8","grade":0.547}}""").jsonObject
        val issue = Json.parseToJsonElement("""{"issueId":"i1","type":"LOW_CONFIDENCE","cellId":"c1-j0","detail":{"reason":"REPEATED_UNDERFULL_MEASURE","expectedQuarters":"3","actualQuarters":"5/2"}}""").jsonObject
        val score = smallScoreData()
        val document = SheetDocument("31", "곡", null, 60, "1", score, null, "NEEDS_REVIEW", "DONE", listOf(issue), listOf(notice))
        val signature = assertNotNull(document.editorReviewNotices().single().rhythmReview)
        assertEquals("TIME_SIGNATURE_REVIEW_REQUIRED", signature.reason)
        assertEquals("6/8", signature.timeSignature)
        assertEquals(3, document.editorReviewNotices().single().page)
        assertEquals("REPEATED_UNDERFULL_MEASURE", document.editorReviewIssues(score).single().rhythmReview?.reason)
        assertEquals("REPEATED_UNDERFULL_MEASURE", document.editorReviewIssues(score).single().reason)
        val empty = Json.parseToJsonElement("""{"type":"LOW_CONFIDENCE","detail":{"reason":"EMPTY_MEASURE_REVIEW_REQUIRED","measure":4}}""").jsonObject
        val blank = assertNotNull(document.copy(notices = listOf(empty)).editorReviewNotices().single().rhythmReview)
        assertEquals("EMPTY_MEASURE_REVIEW_REQUIRED", blank.reason)
        assertEquals("4", blank.measure)
    }
}
