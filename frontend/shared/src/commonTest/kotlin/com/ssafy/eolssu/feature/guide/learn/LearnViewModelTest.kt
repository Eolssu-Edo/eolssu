package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.user.LearnProgressRepository
import com.ssafy.eolssu.testing.CatalogSongs
import com.ssafy.eolssu.testing.CatalogViewModelTest
import com.ssafy.eolssu.testing.FakeLessonApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 기초부터 배우기 화면 상태: 서버 단계 목록·상세(LRN-01·02)와 완료 표시(LRN-03) 저장·되돌리기. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class LearnViewModelTest : CatalogViewModelTest() {
    private val api = FakeLessonApi()
    private val store = FakeKeyValueStore()

    private fun model() = keep(LearnViewModel(CatalogSongs(), LearnProgressRepository(api, store)))

    @Test
    fun loadsServerStagesAndDoneMarksForStartingInstrument() = runTest {
        api.completed["me"] = mutableSetOf(api.id("danso", 1))
        val model = model()
        assertEquals(LearnTrackLoad.Loading, model.uiState.value.track)
        runCurrent()

        val state = model.uiState.value
        assertEquals((1..9).toList(), state.stages.map { it.number })
        assertEquals(listOf(9), state.stages.filter { it.optional }.map { it.number })
        assertEquals(setOf(1), state.doneStages)
        assertEquals(2, state.nextStage?.number)
    }

    @Test
    fun deviceMarksLeftBeforeServerSyncAreMergedOnFirstLoad() = runTest {
        api.completed["me"] = mutableSetOf(api.id("danso", 1))
        store.putString("learn_done_danso", "2,3")
        val model = model()
        runCurrent()
        assertEquals(setOf(1, 2, 3), model.uiState.value.doneStages)
        assertEquals(setOf(1, 2, 3), api.doneSeqs("danso"))
    }

    @Test
    fun offlineTrackShowsFailureAndRetryLoads() = runTest {
        api.online = false
        val model = model()
        runCurrent()
        assertEquals(LearnTrackLoad.Failed, model.uiState.value.track)

        api.online = true
        model.retryTrack()
        runCurrent()
        assertIs<LearnTrackLoad.Loaded>(model.uiState.value.track)
    }

    @Test
    fun otherInstrumentLoadsItsOwnTrackWhenChosen() = runTest {
        api.completed["me"] = mutableSetOf(api.id("sogeum", 4))
        val model = model()
        runCurrent()
        model.selectInstrument(GuideInstrument.Sogeum)
        assertEquals(LearnTrackLoad.Loading, model.uiState.value.track)
        runCurrent()
        assertEquals(setOf(4), model.uiState.value.doneStages)
        assertTrue(model.uiState.value.done[GuideInstrument.Danso].orEmpty().isEmpty())
    }

    @Test
    fun markShowsAtOnceAndIsSavedOnServer() = runTest {
        val model = model()
        runCurrent()
        model.confirm(3)
        assertTrue(model.uiState.value.isDone(3))
        runCurrent()
        assertEquals(setOf(3), api.doneSeqs("danso"))

        model.toggleDone(3)
        runCurrent()
        assertFalse(model.uiState.value.isDone(3))
        assertTrue(api.doneSeqs("danso").isEmpty())
        // 이미 완료인 단계를 다시 '확인했어요'로 눌러도 요청하지 않는다.
        model.confirm(1)
        runCurrent()
        val calls = api.completionCalls.size
        model.confirm(1)
        runCurrent()
        assertEquals(calls, api.completionCalls.size)
    }

    @Test
    fun failedMarkIsRevertedAndReported() = runTest {
        val model = model()
        runCurrent()
        api.failing += api.id("danso", 2)

        model.toggleDone(2)
        assertTrue(model.uiState.value.isDone(2))
        runCurrent()

        assertFalse(model.uiState.value.isDone(2))
        assertEquals(LearnSaveError.Mark, model.uiState.value.saveError)
        model.clearSaveError()
        assertNull(model.uiState.value.saveError)
    }

    @Test
    fun resetClearsServerMarksAndRestoresOnesThatFailed() = runTest {
        api.completed["me"] = mutableSetOf(api.id("danso", 1), api.id("danso", 2))
        val model = model()
        runCurrent()
        api.failing += api.id("danso", 2)

        model.resetDone()
        assertTrue(model.uiState.value.doneStages.isEmpty())
        runCurrent()

        assertEquals(setOf(2), model.uiState.value.doneStages)
        assertEquals(setOf(2), api.doneSeqs("danso"))
        assertEquals(LearnSaveError.Reset, model.uiState.value.saveError)
    }

    @Test
    fun detailLoadsOnceAndFailureCanBeRetried() = runTest {
        val model = model()
        runCurrent()
        api.online = false
        model.loadDetail(4)
        runCurrent()
        assertEquals(LearnDetailLoad.Failed, model.uiState.value.detail(4))

        // 실패는 다시 시도할 때만 다시 받는다.
        api.online = true
        model.loadDetail(4)
        runCurrent()
        assertEquals(LearnDetailLoad.Failed, model.uiState.value.detail(4))
        model.loadDetail(4, force = true)
        runCurrent()
        val detail = assertIs<LearnDetailLoad.Loaded>(model.uiState.value.detail(4)).detail
        assertEquals(listOf("汰", "潢", "無", "林", "仲"), detail.pitchNames)
        assertEquals("주의", detail.pitchCaution)
    }

    @Test
    fun unknownStageIsIgnored() = runTest {
        val model = model()
        runCurrent()
        model.toggleDone(42)
        model.loadDetail(42)
        runCurrent()
        assertTrue(api.completionCalls.isEmpty())
        assertEquals(LearnDetailLoad.Loading, model.uiState.value.detail(42))
    }

    /** 연주 결과 화면이 8단계를 완료로 남기면(S15P31E102-611) 뒤에 남아 있던 학습 화면도 로딩 표시 없이 완료를 보인다. */
    @Test
    fun completionSavedElsewhereIsShownWithoutReloadingUi() = runTest {
        val repository = LearnProgressRepository(api, store)
        val model = keep(LearnViewModel(CatalogSongs(), repository))
        runCurrent()
        assertFalse(8 in model.uiState.value.doneStages)

        repository.setCompleted(api.id("danso", 8), true)
        assertIs<LearnTrackLoad.Loaded>(model.uiState.value.track)
        runCurrent()

        assertTrue(8 in model.uiState.value.doneStages)
        assertIs<LearnTrackLoad.Loaded>(model.uiState.value.track)
    }

    /** 이 화면의 완료 표시는 저장 뒤 다시 받아도 그대로다. */
    @Test
    fun ownMarkStaysAfterRefresh() = runTest {
        val repository = LearnProgressRepository(api, store)
        val model = keep(LearnViewModel(CatalogSongs(), repository))
        runCurrent()
        model.toggleDone(3)
        runCurrent()
        assertTrue(3 in model.uiState.value.doneStages)
        assertEquals(setOf(3), api.doneSeqs("danso"))
    }
}
