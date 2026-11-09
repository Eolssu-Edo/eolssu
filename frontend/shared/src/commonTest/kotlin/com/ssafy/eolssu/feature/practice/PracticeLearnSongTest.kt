package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.PracticeSettings
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 기초부터 배우기 8단계 곡 연습(S15P31E102-626): 평가 없이 '운지법과 함께 보기'로 시작하고 저장한 설정은 건드리지 않는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeLearnSongTest {
    @Test fun learnSongPracticeStartsWithFingeringViewWithoutChangingSavedSetting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage)
            val vm = PracticeViewModel("db-42", true, SampleSongRepository(), settings, PracticeTimeMeter(now = { 0L }),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), learn = true)
            store.put("practice", vm)
            val state = vm.uiState.value
            assertTrue(state.learn)
            assertTrue(state.solo)
            assertTrue(state.fingeringView)
            assertFalse(settings.settings.value.fingeringView)

            vm.toggleFingeringView()
            assertFalse(vm.uiState.value.fingeringView)
            assertFalse(settings.settings.value.fingeringView)
            vm.toggleFingeringView()
            assertTrue(vm.uiState.value.fingeringView)
            assertFalse(settings.settings.value.fingeringView)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun ordinaryPracticeKeepsSavedFingeringSetting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage)
            val vm = PracticeViewModel("db-42", true, SampleSongRepository(), settings, PracticeTimeMeter(now = { 0L }),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope))
            store.put("practice", vm)
            assertFalse(vm.uiState.value.fingeringView)
            vm.toggleFingeringView()
            assertTrue(settings.settings.value.fingeringView)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    /** 연습 설정에 다른 악기를 골라 두었어도 기초부터 배우기 곡 연습은 곡 악보의 악기를 따른다. */
    @Test fun learnSongPracticeIgnoresSavedPracticeInstrument() {
        val saved = PracticeSettings(instrumentId = GuideInstrument.Sogeum.id)
        val song = smallScoreSong()
        val scoreInstrument = PracticeUiState(song = song, settings = PracticeSettings()).instrument
        assertEquals(GuideInstrument.Sogeum, PracticeUiState(song = song, settings = saved).instrument)
        assertEquals(scoreInstrument, PracticeUiState(song = song, settings = saved, learn = true).instrument)
    }
}
