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
    fun unstartedLearningShowsFirstStageAndNoCompletions() {
        val progress = MyLearningProgress(emptySet(), Instrument.Danso)
        assertEquals(0, progress.completedCount)
        assertEquals(7, progress.totalStages)
        assertEquals(1, progress.nextStage)
    }

    @Test
    fun skippedStagesDoNotInflateCurrentLevel() {
        val progress = MyLearningProgress(setOf(1, 3, 7), Instrument.Danso)
        assertEquals(3, progress.completedCount)
        assertEquals(2, progress.nextStage, "7단계를 먼저 표시해도 아직 안 한 2단계가 다음 단계다")
    }

    @Test
    fun completingEveryStageShowsCompletionWithoutInventingStageEight() {
        val progress = MyLearningProgress((1..7).toSet(), Instrument.Danso)
        assertEquals(7, progress.completedCount)
        assertNull(progress.nextStage)
    }

    @Test
    fun unsupportedStageNumbersDoNotCountAsProgress() {
        val progress = MyLearningProgress(setOf(-1, 0, 1, 8, 9, 999), Instrument.Danso)
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
    fun badgeShowsInstrumentOfMostRecentLessonInsteadOfPracticeInstrument() = runTest {
        val api = FakeLessonApi()
        api.completed["me"] = mutableSetOf(api.id("danso", 1), api.id("daegeum-sanjo", 1), api.id("daegeum-sanjo", 2))
        api.completedAt[api.id("danso", 1)] = "2026-11-10T00:00:00Z"
        api.completedAt[api.id("daegeum-sanjo", 2)] = "2026-11-11T00:00:00Z"
        val repository = LearnProgressRepository(api, FakeKeyValueStore())

        val progress = myLearningProgress(Instrument.Danso, repository)

        assertEquals(MyLearningProgress(setOf(1, 2), Instrument.DaegeumSanjo), progress)
        assertEquals(3, progress?.nextStage)
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
