package com.ssafy.eolssu.feature.result

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.data.practice.AnalysisStatus
import com.ssafy.eolssu.data.practice.SampleEvaluationRepository
import com.ssafy.eolssu.data.practice.UnavailablePracticeRecordRepository
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.user.LearnSongPass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 결과 화면이 곡 전체 결과로 기초부터 배우기 8단계 판정을 한 번 부르고, 통과했을 때만 알린다(S15P31E102-611). */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultLearnSongTest {
    @Test fun passedResultIsAnnouncedOnceAndCanBeDismissed() {
        val calls = mutableListOf<Pair<EvaluationResult, Song>>()
        withJudge({ result, song -> calls += result to song; LearnSongPass.Passed }) { model ->
            assertEquals(1, calls.size)
            assertEquals(model.uiState.value.result, calls.single().first)
            assertEquals(model.uiState.value.song, calls.single().second)
            assertTrue(model.uiState.value.learnSongPassed)
            model.dismissLearnSongPassed()
            assertFalse(model.uiState.value.learnSongPassed)
            model.retrySong()
            runCurrent()
            assertEquals(1, calls.size, "한 결과는 한 번만 판정한다")
        }
    }

    @Test fun otherOutcomesAreNotAnnounced() {
        for (outcome in LearnSongPass.entries - LearnSongPass.Passed) {
            withJudge({ _, _ -> outcome }) { model -> assertFalse(model.uiState.value.learnSongPassed, outcome.name) }
        }
    }

    @Test fun judgeFailureDoesNotBreakTheResult() = withJudge({ _, _ -> error("network") }) { model ->
        assertFalse(model.uiState.value.learnSongPassed)
        assertEquals(SongLookup.Ready, model.uiState.value.songLookup)
    }

    private fun withJudge(
        judge: suspend (EvaluationResult, Song) -> LearnSongPass,
        block: suspend TestScope.(ResultViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val songs = SampleSongRepository()
            val songId = songs.getSongs().first().id
            val evaluations = SampleEvaluationRepository(now = { 0L }, analysisMillis = 0)
            val analysisId = evaluations.requestAnalysis(songId, bpm = 60, recordedSeconds = 20)
            val evaluationId = (evaluations.analysisStatus(analysisId) as AnalysisStatus.Done).evaluationId
            val model = ResultViewModel(evaluationId, evaluations, songs, UnavailablePracticeRecordRepository(), judgeLearnSong = judge)
            store.put("result", model)
            runCurrent()
            block(model)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
