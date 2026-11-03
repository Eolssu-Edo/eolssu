package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.model.SongSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MySongsLevelFilterTest {
    private val songs = listOf(
        song("intro-practice", difficulty = 1, evaluable = false),
        song("elementary", difficulty = 2),
        song("intermediate", difficulty = 3),
        song("intro-ai", difficulty = 1),
        song("unknown", difficulty = 5),
    )

    @Test
    fun eachLevelShowsOnlyMatchingSongsIncludingPracticeOnlySongs() {
        val state = MySongsUiState(songs = songs)
        assertEquals(listOf("intro-practice", "intro-ai"), state.copy(filter = SongLevelFilter.Intro).visibleSongs.map { it.id })
        assertEquals(listOf("elementary"), state.copy(filter = SongLevelFilter.Elementary).visibleSongs.map { it.id })
        assertEquals(listOf("intermediate"), state.copy(filter = SongLevelFilter.Intermediate).visibleSongs.map { it.id })
        assertEquals(songs, state.songs, "필터링해도 원본 목록을 보존한다")
    }

    @Test
    fun allIsDefaultAndRestoresOriginalOrderAndUnknownDifficulty() {
        val initial = MySongsUiState(songs = songs)
        assertEquals(SongLevelFilter.All, initial.filter)
        assertEquals(songs, initial.visibleSongs)
        val filtered = initial.copy(filter = SongLevelFilter.Intermediate)
        assertEquals(songs, filtered.copy(filter = SongLevelFilter.All).visibleSongs)
    }

    @Test
    fun emptyLevelKeepsSuccessfulCatalogAndCanReturnToAll() {
        val state = MySongsUiState(
            loadState = SongLoadState.Ready,
            songs = songs.filter { it.difficulty == 1 },
            filter = SongLevelFilter.Intermediate,
        )
        assertTrue(state.visibleSongs.isEmpty())
        assertFalse(state.songs.isEmpty(), "해당 단계에 곡이 없는 경우와 서버의 빈 목록을 구분한다")
        assertEquals(SongLoadState.Ready, state.loadState)
        assertEquals(listOf("intro-practice", "intro-ai"), state.copy(filter = SongLevelFilter.All).visibleSongs.map { it.id })
    }

    private fun song(id: String, difficulty: Int, evaluable: Boolean = true) = SongSummary(
        id = id,
        title = id,
        difficulty = difficulty,
        jangdanName = "",
        baseBpm = 60,
        evaluable = evaluable,
    )
}
