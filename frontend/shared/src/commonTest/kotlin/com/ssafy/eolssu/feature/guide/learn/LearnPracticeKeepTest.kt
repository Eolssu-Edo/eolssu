package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.PitchGate
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * 태블릿을 돌려 단계 화면과 목록 2단 배치가 바뀌어도 단계 연습 상태가 이어진다(S15P31E102-1030).
 * 두 배치는 같은 [LearnPracticeViewModel]을 받으므로, 통과·'완료했어요!' 상태가 화면이 아니라 ViewModel에 있어야 한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LearnPracticeKeepTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** 큰 소리로 같은 높이를 내는 녹음기. [send]마다 20ms 관측 하나를 낸다. */
    private class LoudRecorder : AudioRecorder {
        private val flow = MutableSharedFlow<PitchReading>(extraBufferCapacity = 256)
        private var sequence = 0L
        override val pitchReadings = flow
        override val measuresLevel = true
        override var pitchGate: PitchGate = PitchGate.Default
        override fun start() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop(): RecordedAudio? = null
        override fun cancel() = Unit
        override fun level() = 0.6f

        fun send(hz: Double) {
            val end = sequence * 20 + 46
            flow.tryEmit(PitchReading(1, 1, sequence++, end - 46, end, 1000, hz, 0, 0.05))
        }
    }

    /** 3단계를 통과한 뒤 새 배치가 같은 ViewModel에 다시 붙어도 통과 상태가 남고, 진행 막대는 연습 박스까지 마친 것으로 센다. */
    @Test fun stageThreePassSurvivesReattach() = runTest(dispatcher) {
        val recorder = LoudRecorder()
        val vm = LearnPracticeViewModel(3, recorder, null, timeSource = testScheduler.timeSource)
        vm.prepare(GuideInstrument.Danso, LearnStageDetail(goal = "", why = "", source = ""))
        vm.startListening()
        runCurrent()
        advanceTimeBy(1_000)
        repeat(55) {
            recorder.send(466.0)
            runCurrent()
        }
        assertEquals(LearnPracticePhase.Passed, vm.uiState.value.phase)

        // 회전 뒤 다른 배치가 그리며 통과 상태를 다시 알린다. 3단계는 '완료했어요!'를 띄우지 않는다.
        vm.onPassChanged(passed = true, celebrates = false)
        assertEquals(LearnPracticePhase.Passed, vm.uiState.value.phase)
        assertFalse(vm.celebrating.value)

        val tour = LearnTour()
        repeat(3) { tour.complete(it, count = 4) }
        tour.passPractice(count = 4)
        assertTrue(tour.allDone(count = 4))
    }

    /** '완료했어요!'는 한 번 통과에 한 번만 뜬다. 닫은 뒤 배치가 바뀌어 다시 알려도 뜨지 않고, 처음부터 다시 통과하면 다시 뜬다. */
    @Test fun celebrationShowsOncePerPassAcrossReattach() {
        val vm = LearnPracticeViewModel(4, null, null)
        vm.onPassChanged(passed = true, celebrates = true)
        assertTrue(vm.celebrating.value)

        // 창을 띄운 채 돌리면 새 배치에서도 그대로 보인다.
        vm.onPassChanged(passed = true, celebrates = true)
        assertTrue(vm.celebrating.value)

        vm.dismissCelebration()
        vm.onPassChanged(passed = true, celebrates = true)
        assertFalse(vm.celebrating.value)

        vm.onPassChanged(passed = false, celebrates = true)
        vm.onPassChanged(passed = true, celebrates = true)
        assertTrue(vm.celebrating.value)
    }

    /** 스스로 확인으로 마칠 때도 '완료했어요!' 상태가 ViewModel에 남는다. */
    @Test fun selfCheckCelebrationIsKeptInViewModel() {
        val vm = LearnPracticeViewModel(7, null, null)
        vm.celebrate()
        assertTrue(vm.celebrating.value)
        vm.dismissCelebration()
        assertFalse(vm.celebrating.value)
    }

    /** 연습 통과는 마친 할 일 수를 줄이지 않고, 펼친 연습 박스는 그대로 둔다. */
    @Test fun passPracticeFillsProgressWithoutClosingTheBox() {
        val tour = LearnTour()
        repeat(3) { tour.complete(it, count = 4) }
        assertEquals(3, tour.open)
        tour.passPractice(count = 4)
        assertEquals(4, tour.doneCount)
        assertEquals(3, tour.open)

        tour.passPractice(count = 2)
        assertEquals(4, tour.doneCount)
    }
}
