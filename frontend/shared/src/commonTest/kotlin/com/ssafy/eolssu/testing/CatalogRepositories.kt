package com.ssafy.eolssu.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.eolssu.core.model.AnalysisFailure
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.toSummary
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.EvaluationRepository
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

internal class CatalogSongs : SongRepository {
    var songCalls = 0
    var listCalls = 0
    var song: suspend (String) -> Song? = { id -> SampleSongs.firstOrNull { it.id == id } }
    var list: suspend () -> List<Song> = { SampleSongs }
    override suspend fun getSong(songId: String): Song? { songCalls++; return song(songId) }
    override suspend fun getSongs(): List<SongSummary> { listCalls++; return list().map { it.toSummary() } }
}

internal class CatalogEvaluations : EvaluationRepository {
    var requests = 0
    var cached: EvaluationResult? = EvaluationResult(
        id = "result", songId = "arirang", total = 70, feedback = "test", metrics = emptyList(), errors = emptyList(),
        bpm = 60, recordedSeconds = 10, recordedAt = KoreanDateTime(2026, 10, 29, 12, 0), sample = true,
    )
    override suspend fun requestAnalysis(songId: String, bpm: Int, recordedSeconds: Int): String { requests++; return "analysis" }
    override suspend fun analysisStatus(analysisId: String): AnalysisStatus = AnalysisStatus.Failed(AnalysisFailure.NoSound)
    override suspend fun retryAnalysis(analysisId: String) = Unit
    override fun result(evaluationId: String): EvaluationResult? = cached
}

@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class CatalogViewModelTest {
    private val models = mutableListOf<ViewModel>()
    @BeforeTest fun mainDispatcher() { Dispatchers.setMain(StandardTestDispatcher()) }
    protected fun <T : ViewModel> keep(model: T): T = model.also { models += it }
    @AfterTest fun clearModels() {
        models.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }
}
