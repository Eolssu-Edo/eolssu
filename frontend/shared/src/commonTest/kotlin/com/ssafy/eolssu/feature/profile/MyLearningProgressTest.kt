package com.ssafy.eolssu.feature.profile

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.LearnProgressRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MyLearningProgressTest {
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
    fun eachPracticeInstrumentReadsItsOwnLearningRecord() {
        val repository = LearnProgressRepository(FakeKeyValueStore())
        repository.saveDoneStages("danso", setOf(1))
        repository.saveDoneStages("sogeum", setOf(1, 2))
        repository.saveDoneStages("daegeum-jeongak", setOf(1, 2, 3))
        repository.saveDoneStages("daegeum-sanjo", setOf(1, 2, 3, 4))
        assertEquals(1, myLearningProgress(Instrument.Danso, repository).completedCount)
        assertEquals(2, myLearningProgress(Instrument.Sogeum, repository).completedCount)
        assertEquals(3, myLearningProgress(Instrument.DaegeumJeongak, repository).completedCount)
        assertEquals(4, myLearningProgress(Instrument.DaegeumSanjo, repository).completedCount)
    }

    @Test
    fun completingAndResettingLearningIsReflectedWhenReadAgain() {
        val repository = LearnProgressRepository(FakeKeyValueStore())
        repository.saveDoneStages("danso", setOf(1, 2))
        assertEquals(3, myLearningProgress(Instrument.Danso, repository).nextStage)
        repository.saveDoneStages("danso", setOf(1, 2, 3))
        assertEquals(4, myLearningProgress(Instrument.Danso, repository).nextStage)
        repository.saveDoneStages("danso", emptySet())
        assertEquals(0, myLearningProgress(Instrument.Danso, repository).completedCount)
        assertEquals(1, myLearningProgress(Instrument.Danso, repository).nextStage)
    }
}
