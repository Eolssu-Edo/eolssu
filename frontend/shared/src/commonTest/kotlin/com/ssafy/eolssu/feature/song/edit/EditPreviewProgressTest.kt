package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreBpmUnit
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScoreTiming
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditPreviewProgressTest {
    private val rest = ScoreCell(ScoreNote.Rest)
    private val song = Song(
        id = "test",
        title = "미리듣기",
        level = SongLevel.Intro,
        jangdan = null,
        baseBpm = 120,
        description = "",
        evaluable = false,
        score = Score(
            columns = listOf(listOf(rest, rest), listOf(rest)),
            timing = ScoreTiming(311.13, "", ScoreBpmUnit.DOTTED_QUARTER, 480, 240),
        ),
    )

    @Test
    fun currentCellUsesWrittenTimingAndStopsAtTheScoreEnd() {
        assertEquals(0 to 0, previewCellAt(song, 249))
        assertEquals(0 to 1, previewCellAt(song, 250))
        assertEquals(1 to 0, previewCellAt(song, 500))
        assertNull(previewCellAt(song, 750))
        assertNull(previewCellAt(song, -1))
    }

    @Test
    fun cursorIsSuppressedForMarksThatChangeWrittenTiming() {
        assertTrue(hasLinearPreviewTiming(listOf("흘림표")))
        for (mark in listOf("도돌이표", "점점느리게", "len-half@0.5")) {
            assertFalse(hasLinearPreviewTiming(listOf(mark)), mark)
        }
    }
}
