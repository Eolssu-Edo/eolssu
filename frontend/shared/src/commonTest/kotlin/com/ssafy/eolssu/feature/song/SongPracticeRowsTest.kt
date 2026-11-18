package com.ssafy.eolssu.feature.song

import kotlin.test.Test
import kotlin.test.assertEquals

class SongPracticeRowsTest {

    @Test
    fun sheetCountIsZeroUntilSheetsArrive() {
        assertEquals(0, MySongsUiState().sheetCount)
    }
}
