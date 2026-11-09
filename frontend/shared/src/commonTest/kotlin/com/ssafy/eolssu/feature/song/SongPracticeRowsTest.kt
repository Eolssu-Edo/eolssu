package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.feature.song.component.songTileLetter
import kotlin.test.Test
import kotlin.test.assertEquals

class SongPracticeRowsTest {

    @Test
    fun tileShowsFirstLetterOfTitle() {
        assertEquals("아", songTileLetter("아리랑"))
        assertEquals("밀", songTileLetter("  밀양아리랑"), "앞 공백은 건너뛴다")
        assertEquals("", songTileLetter(""))
        assertEquals("", songTileLetter("   "))
    }

    @Test
    fun sheetCountIsZeroUntilSheetsArrive() {
        assertEquals(0, MySongsUiState().sheetCount)
    }
}
