package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.storage.FakeKeyValueStore
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
}
