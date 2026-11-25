package com.ssafy.eolssu.feature.song.edit

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetUploadGuideTest {
    @Test
    fun sheetLimitMatchesServer() {
        // backend SheetImports.kt SHEET_LIMIT_PER_MEMBER = 10
        assertEquals(10, MY_SHEET_LIMIT)
    }
}
