package com.ssafy.eolssu.feature.result

import com.ssafy.eolssu.core.ui.score.ErrorFilter
import com.ssafy.eolssu.core.model.*
import com.ssafy.eolssu.data.practice.*
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
internal class ResultErrorsLoadingTest : CatalogViewModelTest() {
    private val song = SampleSongs.first().copy(scoreVersion = 1)
    private fun source(id: String, total: Int = 73, row: Int = 1) = WeakSectionSource(song,
        EvaluationResult(id, song.id, total, "", listOf(MetricScore(EvaluationMetric.Pitch, total)),
            listOf(ScoreError(0, row, EvaluationMetric.Pitch)), 60, 48,
            KoreanDateTime(2026, 11, 5, 10, 0), false, scoreVersion = 1))

    private class Sources(val find: suspend (String) -> WeakSectionSource?) : WeakSectionRepository {
        override suspend fun source(sourceId: String) = find(sourceId)
        override suspend fun weakSections(sessionId: String): List<WeakSectionPick>? = emptyList()
        override suspend fun sectionResult(sessionId: String): SectionEvaluation? = error("unused")
        override suspend fun comparison(recordId: String): RecordComparison? = error("comparison must not be used")
        override suspend fun previous(currentId: String, previousId: String): PreviousRecord? = error("unused")
    }

    @Test fun differentRecordsKeepTheirOwnScoresAndErrors() = runTest {
        val sources = mapOf("41" to source("41", 73, 1), "42" to source("42", 91, 3))
        val repository = Sources { sources[it] }
        val first = keep(ResultErrorsViewModel("41", repository))
        val second = keep(ResultErrorsViewModel("42", repository))
        runCurrent()
        assertEquals(73, first.uiState.value.result?.total)
        assertEquals(1, first.uiState.value.result?.errors?.single()?.row)
        assertEquals(91, second.uiState.value.result?.total)
        assertEquals(3, second.uiState.value.result?.errors?.single()?.row)
    }

    @Test fun rejectsAnotherSessionEvenWhenSongMatches() = runTest {
        val model = keep(ResultErrorsViewModel("41", Sources { source("42") }))
        runCurrent()
        assertNull(model.uiState.value.result)
        assertNull(model.uiState.value.song)
        assertFalse(model.uiState.value.loading)
    }

    @Test fun rejectsAnotherSongAndScoreVersion() = runTest {
        val wrongSong = keep(ResultErrorsViewModel("41", Sources { source("41").let { it.copy(evaluation = it.evaluation.copy(songId = "other")) } }))
        val wrongVersion = keep(ResultErrorsViewModel("42", Sources { source("42").copy(song = song.copy(scoreVersion = 2)) }))
        runCurrent()
        assertNull(wrongSong.uiState.value.result)
        assertNull(wrongVersion.uiState.value.result)
    }

    @Test fun missingRecordNeverFallsBackToSample() = runTest {
        val model = keep(ResultErrorsViewModel("404", Sources { null }))
        runCurrent()
        assertFalse(model.uiState.value.loading)
        assertNull(model.uiState.value.result)
    }
    @Test fun instrumentsComeFromEachRecordedSong() = runTest {
        val catalog = listOf(
            com.ssafy.eolssu.data.song.CatalogInstrument("8", "DANSO", "단소"),
            com.ssafy.eolssu.data.song.CatalogInstrument("3", "SOGEUM", "소금"),
        )
        val sources = mapOf(
            "41" to source("41").copy(song = song.copy(instrumentId = "8")),
            "42" to source("42").copy(song = song.copy(instrumentId = "3")),
        )
        val repository = Sources { sources[it] }
        val first = keep(ResultErrorsViewModel("41", repository, { catalog }))
        val second = keep(ResultErrorsViewModel("42", repository, { catalog }))
        runCurrent()
        assertEquals("${song.title} · 단소", first.uiState.value.songTitle)
        assertEquals("${song.title} · 소금", second.uiState.value.songTitle)
    }

    @Test fun optionalLookupFailuresKeepSingleEvaluation() = runTest {
        val repository = object : WeakSectionRepository by Sources({ source("41").copy(song = song.copy(instrumentId = "8")) }) {
            override suspend fun weakSections(sessionId: String): List<WeakSectionPick>? = error("weak sections unavailable")
        }
        val model = keep(ResultErrorsViewModel("41", repository, { error("catalog unavailable") }))
        runCurrent()
        assertEquals(73, model.uiState.value.result?.total)
        assertEquals(song.title, model.uiState.value.songTitle)
        assertNull(model.uiState.value.instrumentName)
        assertNull(model.uiState.value.section)
        assertFalse(model.uiState.value.loading)
        assertFalse(model.uiState.value.failed)
    }

    @Test fun sourceFailureCanRetryWithoutKeepingCellOrFilter() = runTest {
        var fail = true
        val model = keep(ResultErrorsViewModel("41", Sources { if (fail) error("offline") else source("41") }))
        runCurrent()
        assertTrue(model.uiState.value.failed)
        assertFalse(model.uiState.value.loading)
        assertNull(model.uiState.value.result)
        model.selectCell(ScoreCellPosition(4, 4))
        model.selectFilter(ErrorFilter.Rhythm)
        fail = false
        model.retry()
        assertTrue(model.uiState.value.loading)
        assertNull(model.uiState.value.selectedCell)
        assertEquals(ErrorFilter.All, model.uiState.value.filter)
        runCurrent()
        assertEquals("41", model.uiState.value.result?.id)
        assertFalse(model.uiState.value.failed)
    }

    @Test fun unknownInstrumentDoesNotUseDefaultDanso() = runTest {
        val model = keep(ResultErrorsViewModel("41", Sources { source("41").copy(song = song.copy(instrumentId = "unknown")) }, {
            listOf(com.ssafy.eolssu.data.song.CatalogInstrument("8", "DANSO", "단소"))
        }))
        runCurrent()
        assertEquals(song.title, model.uiState.value.songTitle)
        assertNull(model.uiState.value.instrumentName)
    }

    @Test fun accountChangeClearsResultAndCancelsPendingMetadata() = runTest {
        val member = com.ssafy.eolssu.core.network.SignedInMember(com.ssafy.eolssu.core.storage.FakeKeyValueStore()).apply { set("A") }
        val name = kotlinx.coroutines.CompletableDeferred<List<com.ssafy.eolssu.data.song.CatalogInstrument>>()
        val model = keep(ResultErrorsViewModel("41", Sources { source("41").copy(song = song.copy(instrumentId = "8")) }, { name.await() }, member))
        runCurrent()
        assertNotNull(model.uiState.value.result)
        member.changing { member.set("B") }
        runCurrent()
        name.complete(listOf(com.ssafy.eolssu.data.song.CatalogInstrument("8", "DANSO", "단소")))
        runCurrent()
        assertNull(model.uiState.value.result)
        assertNull(model.uiState.value.song)
        assertNull(model.uiState.value.instrumentName)
    }

}
