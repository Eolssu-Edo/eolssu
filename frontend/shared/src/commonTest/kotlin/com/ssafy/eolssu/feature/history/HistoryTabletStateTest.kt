package com.ssafy.eolssu.feature.history

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.feature.history.component.RecordScoreLandscape
import com.ssafy.eolssu.feature.history.component.RecordScorePortrait
import com.ssafy.eolssu.feature.history.component.stripSlots
import com.ssafy.eolssu.feature.history.component.wrapColumns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryTabletStateTest {

    private val records = SamplePracticeRecordRepository().records.value

    @Test
    fun twoPaneSelectsJustSavedThenLatestUntilPicked() {
        val latest = records.first().id
        val older = records.last().id
        assertEquals(latest, RecordListUiState(records = records).selectedRecordId)
        assertEquals(older, RecordListUiState(records = records, justSavedId = older).selectedRecordId)
        assertEquals(latest, RecordListUiState(records = records, justSavedId = older).select(latest).selectedRecordId)
    }

    @Test
    fun twoPaneFallsBackWhenSelectionIsMissing() {
        assertEquals(records.first().id, RecordListUiState(records = records, justSavedId = "gone").select("gone-too").selectedRecordId)
        assertNull(RecordListUiState().select("anything").selectedRecordId)
    }

    @Test
    fun wrapsColumnsByStripCountInReadingOrder() {
        assertEquals(listOf(0..7, 8..15), wrapColumns(List(16) { 1 }, slots = 8))
        assertEquals(listOf(0..2, 3..3), wrapColumns(listOf(2, 1, 1, 2), slots = 4))
        // 한 각이 한 줄보다 넓어도 그 각만으로 한 줄을 만든다.
        assertEquals(listOf(0..0, 1..1), wrapColumns(listOf(3, 1), slots = 2))
        assertEquals(emptyList(), wrapColumns(emptyList(), slots = 8))
    }

    @Test
    fun portraitFitsEightColumnsIn720dp() {
        // 와이어프레임 Tab-RecordDetail-Port: 내용 720 · 각 82 · 사이 6 → 한 줄 8각
        assertEquals(8, stripSlots(720.dp, RecordScorePortrait))
        assertEquals(1, stripSlots(40.dp, RecordScoreLandscape))
    }

    @Test
    fun scoreStripsCanDrawOnlyAColumnRange() {
        val song = SampleSongs.first { it.id == "arirang" }
        val strips = scoreStrips(song.score, song.jangdan, columns = 8..15)
        assertEquals((8..minOf(15, song.score.columns.lastIndex)).toList(), strips.map { it.column }.distinct())
        assertEquals(scoreStrips(song.score, song.jangdan), scoreStrips(song.score, song.jangdan, columns = 0..999))
    }
}
