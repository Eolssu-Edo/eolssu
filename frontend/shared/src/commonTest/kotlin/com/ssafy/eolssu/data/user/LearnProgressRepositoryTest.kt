package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.feature.guide.GuideInstrument
import com.ssafy.eolssu.feature.guide.learn.LearnStages
import kotlin.test.Test
import kotlin.test.assertEquals

class LearnProgressRepositoryTest {

    @Test
    fun nothingDoneAtFirst() {
        assertEquals(emptySet(), LearnProgressRepository(FakeKeyValueStore()).doneStages("danso"))
    }

    @Test
    fun doneStagesAreKeptPerInstrumentAfterRestart() {
        val store = FakeKeyValueStore()
        LearnProgressRepository(store).apply {
            saveDoneStages("danso", setOf(3, 1, 2))
            saveDoneStages("sogeum", setOf(1))
        }
        // 앱을 다시 켜면 같은 기기 저장소로 새로 만든다.
        val restarted = LearnProgressRepository(store)
        assertEquals(setOf(1, 2, 3), restarted.doneStages("danso"))
        assertEquals(setOf(1), restarted.doneStages("sogeum"))
    }

    @Test
    fun clearingStagesLeavesNothing() {
        val repository = LearnProgressRepository(FakeKeyValueStore())
        repository.saveDoneStages("danso", setOf(1, 2))
        repository.saveDoneStages("danso", emptySet())
        assertEquals(emptySet(), repository.doneStages("danso"))
    }

    @Test
    fun doneStageCountFollowsPracticeInstrument() {
        val repository = LearnProgressRepository(FakeKeyValueStore())
        repository.saveDoneStages("danso", setOf(1, 2, 3, 4, 5))
        repository.saveDoneStages("sogeum", setOf(1))

        assertEquals(5, repository.doneStageCount(Instrument.Danso))
        assertEquals(1, repository.doneStageCount(Instrument.Sogeum))
        assertEquals(0, repository.doneStageCount(Instrument.Daegeum))
    }

    @Test
    fun daegeumCountsStagesDoneInJeongakOrSanjo() {
        val repository = LearnProgressRepository(FakeKeyValueStore())
        repository.saveDoneStages("daegeum-jeongak", setOf(1, 2, 3))
        repository.saveDoneStages("daegeum-sanjo", setOf(3, 4))

        assertEquals(4, repository.doneStageCount(Instrument.Daegeum))
    }

    @Test
    fun savingTellsOtherScreensToCountAgain() {
        val repository = LearnProgressRepository(FakeKeyValueStore())
        val before = repository.saves.value

        repository.saveDoneStages("danso", setOf(1))

        assertEquals(before + 1, repository.saves.value)
    }

    @Test
    fun stageCountMatchesLearnStages() {
        // 연습 기록지는 학습 화면을 참조하지 않으므로 분모가 학습 단계 목록과 같은지 여기서 확인한다.
        assertEquals(LearnStages.size, LearnProgressRepository.STAGE_COUNT)
        assertEquals(GuideInstrument.entries.map { it.id }.toSet(), setOf("danso", "sogeum", "daegeum-jeongak", "daegeum-sanjo"))
    }
}
