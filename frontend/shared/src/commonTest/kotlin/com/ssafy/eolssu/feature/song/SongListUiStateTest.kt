package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.data.song.SampleSongs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SongListUiStateTest {

    private val base = SongListUiState(songs = SampleSongs)

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
}
