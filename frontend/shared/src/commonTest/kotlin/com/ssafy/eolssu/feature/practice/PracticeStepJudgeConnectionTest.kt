package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaLoad
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaRepository
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import com.ssafy.eolssu.data.song.smallScoreSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 연습 화면이 곡 악기의 판정 기준을 받아 마이크 판정기로 '한 음씩'을 진행하는지(S15P31E102-341). */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStepJudgeConnectionTest {
    private val criteria = NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, true, true, 50.0, false, 8000)
    private val song = smallScoreSong().copy(instrumentId = "3")

    @Test fun criteriaFoundSwitchesToTheMicrophoneJudgeAndAsksForTheMicFirst() = exercise(NotePracticeCriteriaLoad.Found(criteria)) { vm, recorder, settings ->
        assertFalse(vm.uiState.value.step.manualConfirm)
        vm.toggleStep()
        vm.tapStepPlaying()
        assertEquals(PracticePopup.Permission, vm.uiState.value.popup)
        assertEquals(StepPhase.Idle, vm.uiState.value.step.phase)
        assertEquals(0, recorder.starts)

        vm.grantMicAndStart()
        assertTrue(settings.micGranted.value)
        assertNull(vm.uiState.value.popup)
        assertEquals(StepPhase.Listening, vm.uiState.value.step.phase)
        assertEquals(1, recorder.starts)

        repeat(16) { recorder.send(311.13) }
        runCurrent()
        assertEquals(1, vm.uiState.value.step.target)

        // 다음 음(청중, 시김새는 주음만 판정)을 맞게 불면 끝나고 녹음을 버린다.
        recorder.send(null)
        repeat(16) { recorder.send(311.13 * 2.0.pow(17 / 12.0)) }
        runCurrent()
        assertEquals(StepPhase.Finished, vm.uiState.value.step.phase)
        assertEquals(1, recorder.cancels)
    }

    @Test fun manualConfirmIsIgnoredWithTheMicrophoneJudge() = exercise(NotePracticeCriteriaLoad.Found(criteria)) { vm, _, settings ->
        settings.grantMic()
        vm.toggleStep()
        vm.tapStepPlaying()
        vm.confirmStepNoteManually()
        assertEquals(0, vm.uiState.value.step.target)
    }

    @Test fun deniedMicKeepsTheStepWhereItWas() = exercise(NotePracticeCriteriaLoad.Found(criteria)) { vm, recorder, _ ->
        vm.toggleStep()
        vm.tapStepPlaying()
        vm.micDenied()
        assertEquals(PracticePopup.PermDenied, vm.uiState.value.popup)
        assertTrue(vm.uiState.value.step.on)
        assertEquals(StepPhase.Idle, vm.uiState.value.step.phase)
        assertEquals(0, recorder.starts)
    }

    @Test fun microphoneThatCannotStartPausesWithANotice() = exercise(NotePracticeCriteriaLoad.Found(criteria), StepRecorder(failStart = true)) { vm, _, settings ->
        settings.grantMic()
        vm.toggleStep()
        vm.tapStepPlaying()
        assertEquals(PracticePopup.RecordFailed, vm.uiState.value.popup)
        assertEquals(StepPhase.Paused, vm.uiState.value.step.phase)
    }

    @Test fun instrumentWithoutCriteriaHidesTheMode() = exercise(NotePracticeCriteriaLoad.Unsupported) { vm, _, _ ->
        assertFalse(vm.uiState.value.step.available)
        vm.toggleStep()
        assertFalse(vm.uiState.value.step.on)
    }

    @Test fun criteriaNotReceivedKeepTheManualStandIn() = exercise(NotePracticeCriteriaLoad.Failed) { vm, recorder, _ ->
        assertTrue(vm.uiState.value.step.available)
        assertTrue(vm.uiState.value.step.manualConfirm)
        vm.toggleStep()
        vm.tapStepPlaying()
        assertNull(vm.uiState.value.popup)
        vm.confirmStepNoteManually()
        assertEquals(1, vm.uiState.value.step.target)
        assertEquals(0, recorder.starts)
    }

    @Test fun platformWithoutPitchObservationKeepsTheManualStandIn() =
        exercise(NotePracticeCriteriaLoad.Found(criteria), StepRecorder(observes = false)) { vm, _, _ ->
            assertTrue(vm.uiState.value.step.available)
            assertTrue(vm.uiState.value.step.manualConfirm)
        }

    private fun exercise(
        load: NotePracticeCriteriaLoad,
        recorder: StepRecorder = StepRecorder(),
        block: suspend TestScope.(PracticeViewModel, StepRecorder, PracticeSettingsStore) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val settings = PracticeSettingsStore(storage)
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String) = song
            }
            val asked = mutableListOf<String>()
            val repository = object : NotePracticeCriteriaRepository {
                override suspend fun criteria(instrumentId: String): NotePracticeCriteriaLoad = load.also { asked += instrumentId }
            }
            val vm = PracticeViewModel(song.id, true, songs, settings, PracticeTimeMeter({ 0L }),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope), recorder,
                playbackTimeSource = testScheduler.timeSource, notePractice = repository)
            models.put("practice", vm)
            runCurrent()
            assertEquals(listOf("3"), asked)
            block(vm, recorder, settings)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}
