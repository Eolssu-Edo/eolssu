package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.data.practice.ScoreTrend
import com.ssafy.eolssu.data.practice.TrendPlay
import com.ssafy.eolssu.data.practice.TrendScope
import com.ssafy.eolssu.data.practice.TrendSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 연습 기록지 ‘점수 변화’ 카드 상태(GRO-03 → 카드 값, `docs/specs/growth-score-trend.md`). */
class ScoreTrendStateTest {

    private val arirang = TrendSong("1", "아리랑", fullPlayCount = 5, sectionPlayCount = 2)
    private val sectionOnly = TrendSong("2", "도라지타령", fullPlayCount = 0, sectionPlayCount = 3)

    /** 2026-10-29 12:00 UTC = 21:00 KST. */
    private val base = 1_793_275_200_000L

    private fun play(score: Int, dayOffset: Int = 0) = TrendPlay("s$score", base + dayOffset * 86_400_000L, 60, 0, 3, score)

    private fun trend(
        plays: List<TrendPlay>,
        comparable: Boolean = true,
        scope: TrendScope = TrendScope.Full,
        songId: String? = "1",
        songs: List<TrendSong> = listOf(arirang, sectionOnly),
    ) = ScoreTrend(songId, scope, plays, comparable, songs)

    @Test
    fun startsLoadingWithoutView() {
        val state = ScoreTrendState()
        assertTrue(state.loading)
        assertNull(state.view)
        assertNull(state.songId)
        assertEquals(TrendScope.Full, state.scope)
    }

    @Test
    fun noPlaysAtAllShowsEmptySongList() {
        val state = ScoreTrendState().received(trend(emptyList(), comparable = false, songId = null, songs = emptyList()))

        val view = state.view!!
        assertFalse(state.loading)
        assertNull(state.songId)
        assertNull(view.song)
        assertEquals(emptyList(), view.songs)
        assertEquals(emptyList(), view.points)
        assertFalse(view.onlyOtherScope)
    }

    @Test
    fun onePlayIsNeverMixed() {
        val view = ScoreTrendState().received(trend(listOf(play(80)), comparable = false)).view!!

        assertEquals(listOf(80), view.points.map { it.total })
        assertFalse(view.mixedConditions)
    }

    @Test
    fun fivePlaysKeepServerOrderAndKstDates() {
        val plays = listOf(68, 71, 76, 79, 84).mapIndexed { i, s -> play(s, i) }
        val view = ScoreTrendState().received(trend(plays)).view!!

        assertEquals(listOf(68, 71, 76, 79, 84), view.points.map { it.total })
        assertEquals(KoreanDateTime(2026, 10, 29, 21, 0), view.points.first().playedAt)
        assertEquals(KoreanDateTime(2026, 11, 2, 21, 0), view.points.last().playedAt)
        assertEquals(arirang, view.song)
        assertFalse(view.mixedConditions)
        assertTrue(view.points.none { it.section })
    }

    @Test
    fun notComparableTrendIsMarkedMixed() {
        val view = ScoreTrendState().received(trend(listOf(play(70), play(75, 1)), comparable = false)).view!!
        assertTrue(view.mixedConditions)
    }

    @Test
    fun sectionScopeLabelsEveryPointAsSection() {
        val view = ScoreTrendState().received(trend(listOf(play(60), play(65, 1)), scope = TrendScope.Section)).view!!

        assertEquals(TrendScope.Section, view.scope)
        assertTrue(view.points.all { it.section })
    }

    @Test
    fun fullViewOfSectionOnlySongPointsToSectionChip() {
        val view = ScoreTrendState().received(trend(emptyList(), comparable = false, songId = "2")).view!!

        assertEquals(sectionOnly, view.song)
        assertTrue(view.onlyOtherScope)
    }

    @Test
    fun switchingSongMovesToScopeThatHasPlays() {
        assertEquals(TrendScope.Section, trendScopeFor(sectionOnly, TrendScope.Full))
        assertEquals(TrendScope.Section, trendScopeFor(arirang, TrendScope.Section))
        assertEquals(TrendScope.Full, trendScopeFor(TrendSong("3", "밀양아리랑", 2, 0), TrendScope.Section))
    }

    @Test
    fun failureKeepsPreviousViewAndRequestedParameters() {
        val loaded = ScoreTrendState().received(trend(listOf(play(80))))
        val failed = loaded.requesting("2", TrendScope.Section).received(null)

        assertTrue(failed.failed)
        assertFalse(failed.loading)
        assertEquals("2" to TrendScope.Section, failed.songId to failed.scope)
        assertEquals(loaded.view, failed.view)
    }

    @Test
    fun requestingKeepsControlsVisible() {
        val loaded = ScoreTrendState().received(trend(listOf(play(80))))
        val requesting = loaded.requesting("2", TrendScope.Section)

        assertTrue(requesting.loading)
        assertFalse(requesting.failed)
        assertEquals(loaded.view, requesting.view)
    }
}
