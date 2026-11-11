package com.ssafy.eolssu.feature.history

import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.ui.score.scoreStrips
import com.ssafy.eolssu.data.practice.SamplePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.core.ui.score.readingScoreMetrics
import com.ssafy.eolssu.feature.history.component.stripSlots
import com.ssafy.eolssu.feature.history.component.wrapColumns
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ssafy.eolssu.data.practice.RecordSongSummary
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTabletStateTest {

    private val records = SamplePracticeRecordRepository().records.value

    @Test
    fun twoPaneSelectsLinkedRecordEvenBeforeItsPageIsLoaded() {
        val arirang = records.filter { it.songId == "arirang" }
        val group = RecordSongSummary("arirang", "아리랑", arirang.size, arirang.first().recordedAt, 79, null)
        val state = RecordListUiState(summaries = listOf(group), pages = mapOf("arirang" to RecordPages(arirang, nextPage = null)))
        assertEquals(arirang.first().id, state.selectedRecordId)
        assertEquals("linked", state.copy(linkedId = "linked").selectedRecordId)
        assertEquals("picked", state.copy(linkedId = "linked", selectedId = "picked").selectedRecordId)
        assertNull(RecordListUiState().selectedRecordId)
    }

    @Test
    fun twoPaneDoesNotSelectAnOlderRecordFromAnIncompleteOldestPage() {
        val arirang = records.filter { it.songId == "arirang" }
        val newest = arirang.first()
        val group = RecordSongSummary("arirang", "아리랑", arirang.size, newest.recordedAt, 79, null)
        val partial = RecordListUiState(
            sort = RecordSort.Oldest,
            summaries = listOf(group),
            pages = mapOf("arirang" to RecordPages(arirang.drop(1).asReversed(), nextPage = 1)),
        )
        assertNull(partial.selectedRecordId)
        assertEquals(arirang.last().id, partial.copy(selectedId = arirang.last().id).selectedRecordId)
        assertEquals(newest.id, partial.copy(pages = mapOf("arirang" to RecordPages(arirang.asReversed(), nextPage = null))).selectedRecordId)
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
        // 평가와 같은 각 78dp · 사이 6dp. 720dp에서는 8각을 읽고 나머지는 다음 줄로 잇는다.
        assertEquals(8, stripSlots(720.dp, readingScoreMetrics(SampleSongs.first())))
        assertEquals(1, stripSlots(40.dp, readingScoreMetrics(SampleSongs.first())))
    }

    @Test
    fun enlargedTextKeepsEveryColumnInReachableRows() {
        for (width in listOf(320, 720, 1000)) for (scale in listOf(1f, 1.3f, 2f)) {
            val metrics = readingScoreMetrics(SampleSongs.first()).forFontScale(scale)
            val rows = wrapColumns(List(28) { 1 }, stripSlots(width.dp, metrics))
            assertEquals((0 until 28).toList(), rows.flatMap { it.toList() })
            for (row in rows) {
                val used = metrics.columnWidth * row.count() + metrics.columnGap * (row.count() - 1) + 4.dp
                assertTrue(used <= width.dp, "$width dp / $scale: $row exceeds available width")
            }
        }
    }

    @Test
    fun scoreStripsCanDrawOnlyAColumnRange() {
        val song = SampleSongs.first { it.id == "arirang" }
        val strips = scoreStrips(song.score, song.jangdan, columnRange = 8..15)
        assertEquals((8..minOf(15, song.score.columns.lastIndex)).toList(), strips.map { it.column }.distinct())
        assertEquals(scoreStrips(song.score, song.jangdan), scoreStrips(song.score, song.jangdan, columnRange = 0..999))
    }
}
