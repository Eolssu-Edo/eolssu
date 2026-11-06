package com.ssafy.eolssu.feature.history

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.data.practice.ScoreTrend
import com.ssafy.eolssu.data.practice.TrendPlay
import com.ssafy.eolssu.data.practice.TrendScope
import com.ssafy.eolssu.data.practice.TrendSong
import com.ssafy.eolssu.data.song.CatalogInstrument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 연습 기록지 ‘점수 변화’ 카드 상태(GRO-03 → 카드 값, `docs/specs/growth-score-trend.md`). 기본 평가(`FULL`)만 그린다. */
class ScoreTrendStateTest {

    private val arirang = TrendSong("1", "아리랑", fullPlayCount = 5, sectionPlayCount = 2)
    private val reEvalOnly = TrendSong("2", "도라지타령", fullPlayCount = 0, sectionPlayCount = 3)
    private val sogeumSong = TrendSong("3", "밀양아리랑", fullPlayCount = 2, sectionPlayCount = 0)
    private val deletedSong = TrendSong("4", "지운 곡", fullPlayCount = 1, sectionPlayCount = 0)

    private val catalog = TrendCatalog.of(
        listOf(CatalogInstrument("10", "DANSO", "단소"), CatalogInstrument("20", "SOGEUM", "소금"), CatalogInstrument("30", "PIRI", "피리")),
        listOf(summary("1", "10"), summary("2", "10"), summary("3", "20")),
    )

    /** 2026-10-29 12:00 UTC = 21:00 KST. */
    private val base = 1_793_275_200_000L

    private fun summary(id: String, instrumentId: String) = SongSummary(id, "곡$id", 1, "세마치", 60, true, instrumentId)

    private fun play(score: Int, dayOffset: Int = 0) = TrendPlay("s$score", base + dayOffset * 86_400_000L, 60, 0, 3, score)

    private fun trend(
        plays: List<TrendPlay>,
        comparable: Boolean = true,
        songId: String? = "1",
        songs: List<TrendSong> = listOf(arirang, reEvalOnly, sogeumSong),
    ) = ScoreTrend(songId, TrendScope.Full, plays, comparable, songs)

    @Test
    fun startsLoadingWithoutView() {
        val state = ScoreTrendState()
        assertTrue(state.loading)
        assertNull(state.view)
        assertNull(state.songId)
        assertEquals(emptyList(), state.basicSongs)
    }

    @Test
    fun noPlaysAtAllShowsNoSongs() {
        val state = ScoreTrendState().received(trend(emptyList(), comparable = false, songId = null, songs = emptyList()))

        val view = state.view!!
        assertFalse(state.loading)
        assertNull(state.songId)
        assertNull(view.song)
        assertEquals(emptyList(), view.points)
        assertEquals(emptyList(), state.basicSongs)
        assertNull(state.fallbackSongId)
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
    }

    @Test
    fun notComparableTrendIsMarkedMixed() {
        val view = ScoreTrendState().received(trend(listOf(play(70), play(75, 1)), comparable = false)).view!!
        assertTrue(view.mixedConditions)
    }

    @Test
    fun songsWithOnlyReEvaluationsAreNotPickable() {
        val state = ScoreTrendState().received(trend(listOf(play(80))))

        assertEquals(listOf(arirang, sogeumSong), state.basicSongs)
    }

    @Test
    fun onlyReEvaluationsMeansNoBasicSongs() {
        val state = ScoreTrendState().received(trend(emptyList(), comparable = false, songId = "2", songs = listOf(reEvalOnly)))

        assertEquals(emptyList(), state.basicSongs)
        assertNull(state.fallbackSongId)
    }

    @Test
    fun serverSongWithOnlyReEvaluationsFallsBackToLatestBasicSong() {
        val state = ScoreTrendState().received(trend(emptyList(), comparable = false, songId = "2", songs = listOf(reEvalOnly, sogeumSong, arirang)))

        assertEquals("3", state.fallbackSongId)
        assertNull(ScoreTrendState().received(trend(listOf(play(80)))).fallbackSongId)
    }

    @Test
    fun instrumentsAreThoseWithBasicSongsInCatalogOrder() {
        val state = ScoreTrendState(catalog = catalog).received(trend(listOf(play(80)), songs = listOf(sogeumSong, reEvalOnly, arirang)))

        assertEquals(listOf("10", "20"), state.instruments.map { it.instrumentId })
        assertEquals(listOf(Instrument.Danso, Instrument.Sogeum), state.instruments.map { it.instrument })
        assertEquals("10", state.selectedInstrument?.instrumentId)
    }

    @Test
    fun songPickerShowsOnlySelectedInstrumentBasicSongs() {
        val songs = listOf(arirang, reEvalOnly, sogeumSong)
        val danso = ScoreTrendState(catalog = catalog).received(trend(listOf(play(80)), songs = songs))
        val sogeum = danso.requesting("3")

        assertEquals(listOf(arirang), danso.instrumentSongs)
        assertEquals("20", sogeum.selectedInstrument?.instrumentId)
        assertEquals(listOf(sogeumSong), sogeum.instrumentSongs)
    }

    @Test
    fun instrumentPicksItsMostRecentBasicSong() {
        val state = ScoreTrendState(catalog = catalog).received(trend(listOf(play(80)), songs = listOf(reEvalOnly, sogeumSong, arirang)))

        assertEquals(arirang, state.songFor("10"))
        assertEquals(sogeumSong, state.songFor("20"))
        assertNull(state.songFor("30"))
    }

    @Test
    fun songsMissingFromCatalogAreHiddenAndReplaced() {
        val state = ScoreTrendState(catalog = catalog).received(trend(listOf(play(80)), songId = "4", songs = listOf(deletedSong, arirang)))

        assertEquals(listOf(arirang), state.basicSongs)
        assertEquals(listOf("10"), state.instruments.map { it.instrumentId })
        assertNull(state.selectedInstrument)
        assertEquals("1", state.fallbackSongId)
    }

    @Test
    fun onlyMissingSongsMeansNothingToPick() {
        val state = ScoreTrendState(catalog = catalog).received(trend(listOf(play(80)), songId = "4", songs = listOf(deletedSong)))

        assertEquals(emptyList(), state.basicSongs)
        assertEquals(emptyList(), state.instruments)
        assertNull(state.fallbackSongId)
    }

    @Test
    fun instrumentsWithoutRecordsAreNotShown() {
        val state = ScoreTrendState(catalog = catalog).received(trend(listOf(play(80)), songs = listOf(arirang)))

        assertEquals(listOf("10"), state.instruments.map { it.instrumentId })
    }

    @Test
    fun withoutCatalogAllBasicSongsArePickable() {
        val state = ScoreTrendState().received(trend(listOf(play(80))))

        assertEquals(emptyList(), state.instruments)
        assertNull(state.selectedInstrument)
        assertEquals(listOf(arirang, sogeumSong), state.instrumentSongs)
    }

    @Test
    fun failureKeepsPreviousViewAndRequestedSong() {
        val loaded = ScoreTrendState(catalog = catalog).received(trend(listOf(play(80))))
        val failed = loaded.requesting("3").received(null)

        assertTrue(failed.failed)
        assertFalse(failed.loading)
        assertEquals("3", failed.songId)
        assertEquals(loaded.view, failed.view)
        assertEquals(catalog, failed.catalog)
    }

    @Test
    fun receivingKeepsCatalog() {
        val state = ScoreTrendState(catalog = catalog).requesting("1").received(trend(listOf(play(80))))
        assertEquals(catalog, state.catalog)
    }

    @Test
    fun requestingKeepsControlsVisible() {
        val loaded = ScoreTrendState().received(trend(listOf(play(80))))
        val requesting = loaded.requesting("3")

        assertTrue(requesting.loading)
        assertFalse(requesting.failed)
        assertEquals(loaded.view, requesting.view)
    }
}
