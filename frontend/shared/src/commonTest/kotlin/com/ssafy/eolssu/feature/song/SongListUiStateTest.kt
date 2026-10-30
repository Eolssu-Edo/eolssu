package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.core.model.toSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SongListUiStateTest {

    private val base = SongListUiState(songs = SampleSongs.map { it.toSummary() })

    @Test
    fun showsOnlyEvaluableSongs() {
        val ids = base.visibleSongs.map { it.id }
        assertEquals(9, ids.size)
        assertTrue("hanobaek" !in ids)
    }

    @Test
    fun filtersByLevel() {
        val titles = base.copy(filter = SongLevelFilter.Elementary).visibleSongs.map { it.title }
        assertEquals(listOf("밀양아리랑", "늴리리야"), titles)
    }

    @Test
    fun searchesByTitlePartAndTrimsQuery() {
        val titles = base.copy(query = " 아리 ").visibleSongs.map { it.title }
        assertEquals(listOf("아리랑", "밀양아리랑"), titles)
    }

    @Test
    fun combinesFilterAndQuery() {
        assertEquals(emptyList(), base.copy(query = "아리", filter = SongLevelFilter.Intermediate).visibleSongs)
    }

    @Test
    fun selectsFirstVisibleSongWhenSongsArrive() {
        val state = SongListUiState().receiveSongs(SampleSongs.map { it.toSummary() })
        assertEquals(state.visibleSongs.first().id, state.selectedSongId)
        assertEquals(state.visibleSongs.first(), state.selectedSong)
    }

    @Test
    fun keepsChosenSongWhenSongsArriveAgain() {
        val chosen = base.visibleSongs[2].id
        val state = base.selectSong(chosen).receiveSongs(SampleSongs.map { it.toSummary() })
        assertEquals(chosen, state.selectedSong?.id)
    }

    @Test
    fun hidesSelectionFilteredOutAndRestoresItAfterReset() {
        val arirang = base.visibleSongs.first { it.title == "아리랑" }.id
        val filtered = base.selectSong(arirang).copy(filter = SongLevelFilter.Elementary)
        assertNull(filtered.selectedSong)
        assertEquals(arirang, filtered.copy(filter = SongLevelFilter.All).selectedSong?.id)
    }

    @Test
    fun firstSongSelectedOnArrivalIsNotPicked() {
        val state = SongListUiState().receiveSongs(SampleSongs.map { it.toSummary() })
        assertFalse(state.songPicked)
        assertNull(state.pickedSong, "자동으로 고른 첫 곡은 세로로 돌려도 상세를 열지 않는다")
    }

    @Test
    fun pickedSongSurvivesSongsArrivingAndOpensOnce() {
        val chosen = base.visibleSongs[3].id
        val picked = SongListUiState().pickSong(chosen).receiveSongs(SampleSongs.map { it.toSummary() })
        assertEquals(chosen, picked.pickedSong?.id)

        val opened = picked.openPickedSong()
        assertNull(opened.pickedSong, "상세에서 목록으로 돌아오면 다시 열지 않는다")
        assertEquals(chosen, opened.selectedSong?.id, "가로로 돌리면 보던 곡이 오른쪽에 남는다")
    }

    @Test
    fun songOpenedFromPortraitListIsNotPicked() {
        val chosen = base.visibleSongs[1].id
        val state = base.pickSong(base.visibleSongs[0].id).selectSong(chosen)
        assertEquals(chosen, state.selectedSong?.id)
        assertNull(state.pickedSong)
    }

    @Test
    fun pickedSongFilteredOutDoesNotOpen() {
        val arirang = base.visibleSongs.first { it.title == "아리랑" }.id
        assertNull(base.pickSong(arirang).copy(filter = SongLevelFilter.Elementary).pickedSong)
    }
}
