package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SheetDocument
import com.ssafy.eolssu.data.song.smallScoreData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SheetConversionNotesTest {
    @Test fun reportedTempoNoticesAreOneSummarySeparateFromVoiceAndJangdan() {
        // Only the annotation shape from the report; no private score/source content.
        val document = document("""[
          {"type":"LOW_CONFIDENCE","pageIdx":null,"detail":{"reason":"TEMPO_PDF_TEXT","count":1}},
          {"type":"LOW_CONFIDENCE","pageIdx":null,"detail":{"reason":"TEMPO_PDF_TEXT","count":2}},
          {"type":"LOW_CONFIDENCE","pageIdx":null,"detail":{"reason":"TEMPO_PDF_TEXT","count":2}},
          {"type":"VOICE_GUESSED","pageIdx":null,"detail":{"voices":2}},
          {"type":"JANGDAN_GUESSED","pageIdx":null,"detail":{"jangdan":"4박 기본 반주"}}
        ]""")

        val notes = document.editorReviewNotices()

        assertEquals(3, notes.size, "빠르기 보완을 묶고 성부·장단 안내는 따로 남긴다")
        assertEquals(listOf("TEMPO_PDF_TEXT", null, null), notes.map { it.reason })
        assertEquals(5, notes.first().count, "빠르기 표시 1+2+2곳을 합쳐 안내한다")
        assertEquals(3, notes.first().occurrences)
        assertNull(notes.first().page, "원본에 없는 쪽 위치는 추정하지 않는다")
        assertEquals(5, document.notices.size, "표시 요약이 원본 응답을 변경하지 않는다")
    }

    @Test fun distinctPageMeasureBoundsAndUnknownDetailsDoNotMerge() {
        val document = document("""[
          {"type":"LOW_CONFIDENCE","pageIdx":0,"detail":{"reason":"TEMPO_PDF_TEXT","count":1,"measure":8}},
          {"type":"LOW_CONFIDENCE","pageIdx":1,"detail":{"reason":"TEMPO_PDF_TEXT","count":2,"measure":8}},
          {"type":"LOW_CONFIDENCE","pageIdx":1,"detail":{"reason":"TEMPO_PDF_TEXT","count":3,"measure":9}},
          {"type":"LOW_CONFIDENCE","pageIdx":1,"bbox":{"x":0.1},"detail":{"reason":"TEMPO_PDF_TEXT","count":4,"measure":9}},
          {"type":"LOW_CONFIDENCE","pageIdx":1,"bbox":{"x":0.2},"detail":{"reason":"TEMPO_PDF_TEXT","count":5,"measure":9}},
          {"type":"LOW_CONFIDENCE","pageIdx":1,"detail":{"reason":"TEMPO_PDF_TEXT","count":6,"measure":9,"staff":2}}
        ]""")
        val notes = document.editorReviewNotices()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), notes.map { it.count })
        assertEquals(listOf(1, 2, 2, 2, 2, 2), notes.map { it.page })
        assertEquals(listOf("8", "8", "9", "9", "9", "9"), notes.map { it.measure })
        assertEquals(List(6) { 1 }, notes.map { it.occurrences })
    }

    @Test fun unknownAndMissingDetailsStayConservativeAndExactRepeatsKeepTheirCount() {
        val document = document("""[
          {"type":"LOW_CONFIDENCE","detail":{"reason":"TEMPO_PDF_TEXT","count":2}},
          {"type":"LOW_CONFIDENCE","detail":{"reason":"TEMPO_PDF_TEXT"}},
          {"type":"LOW_CONFIDENCE","detail":{"reason":"TEMPO_PDF_TEXT","count":-2}},
          {"type":"OTHER_KIND","detail":{"reason":"UNRECOGNIZED"}},
          {"type":"OTHER_KIND","detail":{"reason":"UNRECOGNIZED"}},
          {"type":"OTHER_KIND","detail":{"reason":"DIFFERENT"}}
        ]""")
        val notes = document.editorReviewNotices()
        assertEquals(5, notes.size)
        assertEquals(listOf(2, null, null, null, null), notes.map { it.count })
        assertEquals(listOf(1, 1, 1, 2, 1), notes.map { it.occurrences })
        assertEquals("UNRECOGNIZED", notes[3].reason)
        assertEquals("DIFFERENT", notes[4].reason)
    }

    private fun document(notices: String): SheetDocument = SheetDocument(
        "31", "격리 검수 입력", null, 60, "1", smallScoreData(), null, "EVALUABLE", "DONE",
        emptyList(), Json.parseToJsonElement(notices).jsonArray.map { it.jsonObject },
    )
}
