package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.MySheetStatus
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.feature.song.component.providedSongBadgeStyle
import com.ssafy.eolssu.feature.song.component.sheetBadgeStyle
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
    fun providedSongBadgeFollowsEvaluable() {
        assertEquals(EolssuBadgeStyle.Play, providedSongBadgeStyle(evaluable = true))
        assertEquals(EolssuBadgeStyle.Outline, providedSongBadgeStyle(evaluable = false))
    }

    @Test
    fun sheetBadgeFollowsStatus() {
        assertEquals(EolssuBadgeStyle.Play, sheetBadgeStyle(MySheetStatus.Evaluable))
        assertEquals(EolssuBadgeStyle.Soft, sheetBadgeStyle(MySheetStatus.NeedsReview))
        assertEquals(EolssuBadgeStyle.Outline, sheetBadgeStyle(MySheetStatus.ViewOnly))
    }

    @Test
    fun sheetCountIsZeroUntilSheetsArrive() {
        assertEquals(0, MySongsUiState().sheetCount)
    }
}
