package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.testing.FakeLessonApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyLearningProgressTest {
    @Test
    fun mascotFollowsNextUnfinishedStageIncludingGroupBoundaries() {
        assertEquals(LearningMascot.Study, MyLearningProgress(emptySet()).mascot)
        assertEquals(LearningMascot.Study, MyLearningProgress(setOf(1, 2)).mascot)
        assertEquals(LearningMascot.Point, MyLearningProgress(setOf(1, 2, 3)).mascot)
        assertEquals(LearningMascot.Point, MyLearningProgress((1..5).toSet()).mascot)
        assertEquals(LearningMascot.Rhythm, MyLearningProgress((1..6).toSet()).mascot)
        assertEquals(LearningMascot.Rhythm, MyLearningProgress((1..9).toSet()).mascot)
        assertEquals(LearningMascot.Study, MyLearningProgress(setOf(1, 3, 7, 8, 9)).mascot)
    }

    @Test
    fun unstartedLearningShowsFirstStageAndNoCompletions() {
        val progress = MyLearningProgress(emptySet())
        assertEquals(0, progress.completedCount)
        assertEquals(9, progress.totalStages)
        assertEquals(1, progress.nextStage)
    }

    @Test
    fun skippedStagesDoNotInflateCurrentLevel() {
        val progress = MyLearningProgress(setOf(1, 3, 9))
        assertEquals(3, progress.completedCount)
        assertEquals(2, progress.nextStage, "9단계를 먼저 표시해도 아직 안 한 2단계가 다음 단계다")
    }

    @Test
    fun completingEveryStageShowsCompletionWithoutInventingStageTen() {
        val progress = MyLearningProgress((1..9).toSet())
        assertEquals(9, progress.completedCount)
        assertNull(progress.nextStage)
    }

    @Test
    fun unsupportedStageNumbersDoNotCountAsProgress() {
        val progress = MyLearningProgress(setOf(-1, 0, 1, 10, 999))
        assertEquals(1, progress.completedCount)
        assertEquals(2, progress.nextStage)
    }

    @Test
    fun eachPracticeInstrumentReadsItsOwnServerProgress() = runTest {
        val api = FakeLessonApi()
        listOf("danso" to 1, "sogeum" to 2, "daegeum-jeongak" to 3, "daegeum-sanjo" to 4).forEach { (track, count) ->
            api.completed.getOrPut("me") { mutableSetOf() } += (1..count).map { api.id(track, it) }
        }
        val repository = LearnProgressRepository(api, FakeKeyValueStore())
        assertEquals(1, myLearningProgress(Instrument.Danso, repository)?.completedCount)
        assertEquals(2, myLearningProgress(Instrument.Sogeum, repository)?.completedCount)
        assertEquals(3, myLearningProgress(Instrument.DaegeumJeongak, repository)?.completedCount)
        assertEquals(4, myLearningProgress(Instrument.DaegeumSanjo, repository)?.completedCount)
    }

    @Test
    fun completingAndResettingIsReflectedWhenReadAgainAndOfflineShowsNothing() = runTest {
        val api = FakeLessonApi()
        val repository = LearnProgressRepository(api, FakeKeyValueStore())
        repository.setCompleted(api.id("danso", 1), true)
        repository.setCompleted(api.id("danso", 2), true)
        assertEquals(3, myLearningProgress(Instrument.Danso, repository)?.nextStage)
        repository.setCompleted(api.id("danso", 1), false)
        repository.setCompleted(api.id("danso", 2), false)
        assertEquals(1, myLearningProgress(Instrument.Danso, repository)?.nextStage)
        api.online = false
        assertNull(myLearningProgress(Instrument.Danso, repository))
    }
}
