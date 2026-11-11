package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.testing.FakeLessonApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 기초부터 배우기 진도: 서버(LRN-01·03) 진도와 이 기기에 남은 예전 완료 표시 올리기(S15P31E102-370). */
class LearnProgressRepositoryTest {

    private val store = FakeKeyValueStore()
    private val api = FakeLessonApi()

    /** 회원 없이 만든 저장소는 기기 키 `learn_done_<트랙>`을 쓴다. */
    private fun repository() = LearnProgressRepository(api, store)

    @Test
    fun deviceMarksAreUploadedOnceThenCleared() = runTest {
        store.putString("learn_done_danso", "1,2")
        val repository = repository()

        val track = repository.track("danso")!!

        assertEquals(setOf(1, 2), track.items.filter { it.completed }.map { it.seq }.toSet())
        assertEquals(2, track.completedCount)
        assertEquals(setOf(1, 2), api.doneSeqs("danso"))
        assertTrue(repository.deviceStages("danso").isEmpty())

        repository.track("danso")
        assertEquals(2, api.completionCalls.size, "올린 표시는 다시 올리지 않는다")
    }

    @Test
    fun marksAlreadyOnServerAreNotSentAgainAndUnknownStagesAreDropped() = runTest {
        api.completed["me"] = mutableSetOf(api.id("danso", 3))
        store.putString("learn_done_danso", "3,4,42")

        repository().track("danso")

        assertEquals(listOf(api.id("danso", 4) to true), api.completionCalls)
        assertEquals(setOf(3, 4), api.doneSeqs("danso"))
        assertTrue(repository().deviceStages("danso").isEmpty())
    }

    @Test
    fun partialUploadFailureKeepsOnlyFailedMarksForNextLoad() = runTest {
        store.putString("learn_done_sogeum", "1,2,3")
        api.failing += api.id("sogeum", 2)
        val repository = repository()

        val track = repository.track("sogeum")!!

        assertEquals(setOf(1, 3), track.items.filter { it.completed }.map { it.seq }.toSet())
        assertEquals(setOf(2), repository.deviceStages("sogeum"))

        api.failing.clear()
        repository.track("sogeum")
        assertEquals(setOf(1, 2, 3), api.doneSeqs("sogeum"))
        assertTrue(repository.deviceStages("sogeum").isEmpty())
    }

    @Test
    fun offlineKeepsDeviceMarksAndReportsNoProgress() = runTest {
        store.putString("learn_done_danso", "1")
        api.online = false
        val repository = repository()

        assertNull(repository.track("danso"))
        assertNull(repository.doneStages(Instrument.Danso))
        assertEquals(setOf(1), repository.deviceStages("danso"))
        assertTrue(api.completionCalls.isEmpty())
    }

    @Test
    fun reinstallOrOtherDeviceRestoresServerProgress() = runTest {
        repository().setCompleted(api.id("danso", 5), true)

        // 기기 저장소가 빈 새 설치에서도 서버 진도가 그대로 보인다.
        val restored = LearnProgressRepository(api, FakeKeyValueStore()).track("danso")!!
        assertEquals(listOf(5), restored.items.filter { it.completed }.map { it.seq })
    }

    @Test
    fun repeatedMarkAndUnmarkFollowServer() = runTest {
        val repository = repository()
        val id = api.id("danso", 1)
        assertTrue(repository.setCompleted(id, true))
        assertTrue(repository.setCompleted(id, true))
        assertEquals(setOf(1), api.doneSeqs("danso"))
        assertTrue(repository.setCompleted(id, false))
        assertTrue(repository.setCompleted(id, false))
        assertTrue(api.doneSeqs("danso").isEmpty())
    }

    @Test
    fun doneStagesFollowPracticeInstrumentUpToLastStage() = runTest {
        api.completed["me"] = mutableSetOf(api.id("danso", 1), api.id("danso", 7), api.id("daegeum-sanjo", 3))
        val repository = repository()

        assertEquals(setOf(1, 7), repository.doneStages(Instrument.Danso))
        assertEquals(setOf(3), repository.doneStages(Instrument.DaegeumSanjo))
        assertEquals(emptySet(), repository.doneStages(Instrument.DaegeumJeongak))
    }

    @Test
    fun onlySavedChangesTellOtherScreensToCountAgain() = runTest {
        val repository = repository()
        val before = repository.saves.value
        api.failing += api.id("danso", 1)

        assertEquals(false, repository.setCompleted(api.id("danso", 1), true))
        assertEquals(before, repository.saves.value)

        assertTrue(repository.setCompleted(api.id("danso", 2), true))
        assertEquals(before + 1, repository.saves.value)
    }

    @Test
    fun trackCodesMatchServer() {
        assertEquals(setOf("DANSO", "SOGEUM", "DAEGEUM_JEONGAK", "DAEGEUM_SANJO"), GuideInstrument.entries.map { lessonTrackCode(it.id) }.toSet())
        assertEquals(7, LearnProgressRepository.STAGE_COUNT)
    }
}
