package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.feature.song.edit.EditPickSong
import com.ssafy.eolssu.feature.song.edit.EditPickUiState
import com.ssafy.eolssu.core.model.SongLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class SongSortOrderTest {

    private fun song(id: String, title: String, evaluable: Boolean = true) =
        SongSummary(id, title, difficulty = 1, jangdanName = "세마치", baseBpm = 60, evaluable = evaluable)

    private val songs = listOf(song("1", "아리랑"), song("2", "도라지타령"), song("3", "취타"), song("4", " 늴리리야"), song("5", "도라지타령"))

    @Test
    fun titlesAscendByDefaultAndKeepReceivedOrderForSameTitle() {
        assertEquals(SongSortOrder.Ascending, MySongsUiState().sort)
        assertEquals(listOf("4", "2", "5", "1", "3"), MySongsUiState(songs = songs).visibleSongs.map { it.id }, "앞 공백은 무시한다")
    }

    @Test
    fun descendingReversesTitlesButKeepsReceivedOrderForSameTitle() {
        val state = MySongsUiState(songs = songs, sort = SongSortOrder.Ascending.toggled())
        assertEquals(listOf("3", "1", "2", "5", "4"), state.visibleSongs.map { it.id })
        assertEquals(SongSortOrder.Ascending, state.sort.toggled())
    }

    @Test
    fun evaluationListSortsAfterFiltering() {
        val state = SongListUiState(songs = songs + song("6", "가야금", evaluable = false))
        assertEquals(listOf("4", "2", "5", "1", "3"), state.visibleSongs.map { it.id }, "평가할 수 없는 곡은 정렬 전에 빠진다")
        assertEquals(listOf("3", "1", "2", "5", "4"), state.copy(sort = SongSortOrder.Descending).visibleSongs.map { it.id })
    }

    @Test
    fun receivingSongsPicksFirstSortedSong() {
        assertEquals("4", SongListUiState().receiveSongs(songs).selectedSongId)
    }

    @Test
    fun editPickSongsSortAfterSearch() {
        val picks = listOf(
            EditPickSong("a", "아리랑", SongLevel.Intro, "세마치"),
            EditPickSong("b", "밀양아리랑", SongLevel.Intro, "세마치"),
            EditPickSong("c", "도라지타령", SongLevel.Intro, "세마치"),
        )
        val state = EditPickUiState(songs = picks, query = "아리랑")
        assertEquals(listOf("b", "a"), state.visibleSongs.map { it.id })
        assertEquals(listOf("a", "b"), state.copy(sort = SongSortOrder.Descending).visibleSongs.map { it.id })
    }
}
