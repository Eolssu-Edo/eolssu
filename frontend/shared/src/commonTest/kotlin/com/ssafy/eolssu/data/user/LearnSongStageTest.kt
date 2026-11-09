package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.EvaluationMetric
import com.ssafy.eolssu.core.model.EvaluationResult
import com.ssafy.eolssu.core.model.KoreanDateTime
import com.ssafy.eolssu.core.model.MetricScore
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.song.SampleSongs
import com.ssafy.eolssu.testing.FakeLessonApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 기초부터 배우기 8단계 자동 통과(S15P31E102-611): 곡 전체 결과의 음정·박자 각 70점 이상이면 그 악기 트랙 8단계를 완료로 남긴다. */
class LearnSongStageTest {
    private val api = FakeLessonApi()
    private val progress = LearnProgressRepository(api, FakeKeyValueStore())
    private val codes = mapOf("10" to "DANSO", "20" to "SOGEUM", "30" to "HAEGEUM")
    private val stage = LearnSongStage(progress) { codes[it] }

    /** FakeLessonApi의 8단계 후보 곡은 '아리랑'이다. */
    private val song = SampleSongs.first().copy(title = "아리랑", instrumentId = "10")

    private fun result(pitch: Int, rhythm: Int, sample: Boolean = false, songId: String = song.id) = EvaluationResult(
        "eval", songId, (pitch + rhythm) / 2, "",
        listOf(MetricScore(EvaluationMetric.Pitch, pitch), MetricScore(EvaluationMetric.Rhythm, rhythm), MetricScore(EvaluationMetric.Length, 0)),
        emptyList(), 60, 48, KoreanDateTime(2026, 11, 9, 10, 0), sample,
    )

    @Test fun bothScoresAtSeventyMarkTheInstrumentsStageEight() = runTest {
        assertEquals(LearnSongPass.Passed, stage.judge(result(70, 70), song))
        assertEquals(listOf(api.id("danso", 8) to true), api.completionCalls)
        assertEquals(setOf(8), api.doneSeqs("danso"))
    }

    @Test fun eitherScoreBelowSeventyDoesNotPass() = runTest {
        assertEquals(LearnSongPass.BelowScore, stage.judge(result(69, 100), song))
        assertEquals(LearnSongPass.BelowScore, stage.judge(result(100, 69), song))
        assertTrue(api.completionCalls.isEmpty())
    }

    @Test fun missingRhythmScoreDoesNotPass() = runTest {
        val pitchOnly = result(90, 90).copy(metrics = listOf(MetricScore(EvaluationMetric.Pitch, 90)))
        assertEquals(LearnSongPass.BelowScore, stage.judge(pitchOnly, song))
    }

    @Test fun usesTheTrackOfTheSongsInstrument() = runTest {
        assertEquals(LearnSongPass.Passed, stage.judge(result(80, 80), song.copy(instrumentId = "20")))
        assertEquals(setOf(8), api.doneSeqs("sogeum"))
        assertTrue(api.doneSeqs("danso").isEmpty())
    }

    @Test fun alreadyDoneStageIsNotSentAgain() = runTest {
        api.completed["me"] = mutableSetOf(api.id("danso", 8))
        assertEquals(LearnSongPass.AlreadyDone, stage.judge(result(90, 90), song))
        assertTrue(api.completionCalls.isEmpty())
    }

    @Test fun otherSongsSamplesAndUnknownInstrumentsAreNotJudged() = runTest {
        assertEquals(LearnSongPass.NotApplicable, stage.judge(result(90, 90), song.copy(title = "도라지")))
        assertEquals(LearnSongPass.NotApplicable, stage.judge(result(90, 90, sample = true), song))
        assertEquals(LearnSongPass.NotApplicable, stage.judge(result(90, 90, songId = "other"), song))
        assertEquals(LearnSongPass.NotApplicable, stage.judge(result(90, 90), song.copy(instrumentId = null)))
        assertEquals(LearnSongPass.NotApplicable, stage.judge(result(90, 90), song.copy(instrumentId = "30")))
        assertEquals(LearnSongPass.NotApplicable, stage.judge(result(90, 90), song.copy(instrumentId = "99")))
        assertTrue(api.completionCalls.isEmpty())
    }

    @Test fun offlineOrFailedSaveIsReportedAsFailed() = runTest {
        api.online = false
        assertEquals(LearnSongPass.Failed, stage.judge(result(90, 90), song))
        api.online = true
        api.failing += api.id("danso", 8)
        assertEquals(LearnSongPass.Failed, stage.judge(result(90, 90), song))
    }

    @Test fun catalogFailureIsReportedAsFailed() = runTest {
        val broken = LearnSongStage(progress) { error("catalog down") }
        assertEquals(LearnSongPass.Failed, broken.judge(result(90, 90), song))
    }
}
