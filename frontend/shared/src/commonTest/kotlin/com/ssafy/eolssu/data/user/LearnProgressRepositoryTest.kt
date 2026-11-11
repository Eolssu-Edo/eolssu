package com.ssafy.eolssu.data.user

import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.testing.FakeLessonApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun recentProgressFollowsInstrumentOfLatestCompletion() = runTest {
        api.completed["me"] = mutableSetOf(api.id("danso", 1), api.id("danso", 2), api.id("sogeum", 1))
        api.completedAt[api.id("danso", 1)] = "2026-11-01T00:00:00Z"
        api.completedAt[api.id("danso", 2)] = "2026-11-05T10:00:00Z"
        // 문자열로 비교하면 '.'이 'Z'보다 앞이라 단소가 이긴다. 시각으로 비교해 0.5초 뒤인 소금을 고른다.
        api.completedAt[api.id("sogeum", 1)] = "2026-11-05T10:00:00.500Z"

        val recent = repository().recentProgress(fallback = Instrument.Danso)

        assertEquals(RecentLearning(Instrument.Sogeum, setOf(1)), recent)
    }

    @Test
    fun recentProgressUsesFallbackInstrumentWithoutAnyCompletion() = runTest {
        assertEquals(RecentLearning(Instrument.DaegeumJeongak, emptySet()), repository().recentProgress(Instrument.DaegeumJeongak))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun recentProgressLoadsAllTracksAtOnce() = runTest {
        api.trackDelayMs = 300

        repository().recentProgress(Instrument.Danso)

        // 다섯 트랙(단소·소금·대금 정악·산조…)을 차례로 받으면 300ms의 몇 배가 걸린다. 한꺼번에 받아 한 번만 기다린다.
        assertEquals(300, testScheduler.currentTime)
    }

    @Test
    fun recentProgressAsksFallbackOnlyWithoutAnyCompletion() = runTest {
        api.completed["me"] = mutableSetOf(api.id("sogeum", 1))
        api.completedAt[api.id("sogeum", 1)] = "2026-11-05T10:00:00Z"
        var asked = 0

        val recent = repository().recentProgress { asked++; Instrument.Danso }

        assertEquals(RecentLearning(Instrument.Sogeum, setOf(1)), recent)
        assertEquals(0, asked, "완료 기록이 있으면 연습 악기(내 정보)를 기다리지 않는다")
    }

    @Test
    fun lastRecentProgressStaysOnDeviceForNextVisit() = runTest {
        api.completed["me"] = mutableSetOf(api.id("sogeum", 1), api.id("sogeum", 2))
        api.completedAt[api.id("sogeum", 2)] = "2026-11-05T10:00:00Z"
        assertNull(repository().cachedRecent(), "받은 적이 없으면 남긴 진도가 없다")

        repository().recentProgress(Instrument.Danso)
        api.online = false

        // 서버에 닿지 않아도 새로 만든 저장소가 마지막 진도를 바로 돌려준다(마이페이지 뱃지가 늦게 뜨지 않게).
        assertEquals(RecentLearning(Instrument.Sogeum, setOf(1, 2)), repository().cachedRecent())
    }

    @Test
    fun recentProgressIsNullWhenTracksCannotBeLoaded() = runTest {
        api.online = false

        assertNull(repository().recentProgress(Instrument.Danso))
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

    /** 단계 안 할 일 진행(마친 박스 수)은 트랙·단계마다 기기에 남아 화면을 다시 열어도 이어진다(S15P31E102-749). */
    @Test
    fun stepsDoneAreKeptPerTrackAndStageUntilCleared() {
        val repository = repository()
        assertEquals(0, repository.stepsDone("danso", 3))

        repository.saveStepsDone("danso", 3, 2)
        repository.saveStepsDone("danso", 5, 1)
        repository.saveStepsDone("sogeum", 3, 4)

        val reopened = repository()
        assertEquals(2, reopened.stepsDone("danso", 3))
        assertEquals(1, reopened.stepsDone("danso", 5))
        assertEquals(0, reopened.stepsDone("danso", 4))
        assertEquals(4, reopened.stepsDone("sogeum", 3))

        reopened.clearSteps("danso")
        assertEquals(0, reopened.stepsDone("danso", 3))
        assertEquals(0, reopened.stepsDone("danso", 5))
        assertEquals(4, reopened.stepsDone("sogeum", 3))
    }
}
