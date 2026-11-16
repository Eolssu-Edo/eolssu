package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.data.song.SheetNotation
import com.ssafy.eolssu.feature.song.ConversionStep
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_conversion_building_body
import eolssu.shared.generated.resources.sheet_conversion_reading_pages_body
import eolssu.shared.generated.resources.sheet_conversion_reading_total_body
import eolssu.shared.generated.resources.sheet_conversion_staff_hint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SheetConversionWaitingBodyTest {
    @Test
    fun readingShowsAtMostOneShortLine() {
        // 제목이 이미 '악보를 읽고 있어요'라 설명은 한 줄만 둔다(사용자 지적: 글이 너무 많음).
        assertEquals(WaitingBody(Res.string.sheet_conversion_staff_hint),
            waitingBody(ConversionStep.Reading(pageCount = 1, pagesRead = null), SheetNotation.Staff, done = false))
        assertEquals(WaitingBody(Res.string.sheet_conversion_reading_total_body, listOf(3)),
            waitingBody(ConversionStep.Reading(pageCount = 3, pagesRead = null), SheetNotation.Jeongganbo, done = false))
        assertNull(waitingBody(ConversionStep.Reading(pageCount = 1, pagesRead = null), SheetNotation.Jeongganbo, done = false),
            "1쪽 정간보는 금방 끝나 덧붙일 말이 없다")
        assertEquals(WaitingBody(Res.string.sheet_conversion_reading_pages_body, listOf(2, 4)),
            waitingBody(ConversionStep.Reading(pageCount = 4, pagesRead = 2), SheetNotation.Staff, done = false),
            "서버가 읽은 쪽 수를 주면 걸리는 시간보다 그것을 보인다")
        assertEquals(WaitingBody(Res.string.sheet_conversion_building_body), waitingBody(ConversionStep.Building, SheetNotation.Staff, done = false))
    }
}
