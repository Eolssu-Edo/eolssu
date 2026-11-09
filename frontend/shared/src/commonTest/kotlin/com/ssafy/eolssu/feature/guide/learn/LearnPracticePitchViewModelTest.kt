package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.audio.AudioRecorder
import com.ssafy.eolssu.core.audio.PitchReading
import com.ssafy.eolssu.core.audio.RecordedAudio
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.practice.NotePracticeCriteria
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaLoad
import com.ssafy.eolssu.data.practice.NotePracticeCriteriaRepository
import com.ssafy.eolssu.data.song.CatalogInstrument
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** 단계 연습 ViewModel의 음높이 판정 연결(S15P31E102-611). 관측은 1초에 1,000샘플, 20ms씩이다. */
@OptIn(ExperimentalCoroutinesApi::class)
class LearnPracticePitchViewModelTest {
    private val criteria = NotePracticeCriteria("note-step-1", 50.0, 300, 0.8, -45.0, octaveMustMatch = true,
        firstNoteTuning = true, maxTuningOffsetCent = 50.0, skipSigimsae = false, hintAfterMs = 8000)
    private val detail = LearnStageDetail(goal = "", why = "", source = "", pitchNames = listOf("汰", "潢"))
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class Recorder(observes: Boolean = true) : AudioRecorder {
        private val flow = MutableSharedFlow<PitchReading>(extraBufferCapacity = 256)
        override val pitchReadings: Flow<PitchReading>? = flow.takeIf { observes }
        override val measuresLevel = true
        var starts = 0
        var cancels = 0
        private var recording = 0L
        private var sequence = 0L

        override fun start() {
            starts++
            recording++
            sequence = 0
        }

        override fun pause() = Unit
        override fun resume() = Unit
        override fun stop(): RecordedAudio? = null
        override fun cancel() { cancels++ }
        override fun level() = 0.6f

        fun send(hz: Double?) {
            val end = sequence * 20 + 46
            flow.tryEmit(PitchReading(recording, 1, sequence++, end - 46, end, 1000, hz, 0, 0.05))
        }
    }

    private fun TestScope.hold(recorder: Recorder, hz: Double?, ms: Int) = repeat(ms / 20) {
        recorder.send(hz)
        runCurrent()
    }

    private fun hz(hanja: String) = learnTargetHz(hanja, GuideInstrument.Danso)!!

    private fun viewModel(recorder: AudioRecorder?, stage: Int = 4, load: NotePracticeCriteria? = criteria) =
        LearnPracticeViewModel(stage, recorder, null, loadCriteria = { load })

    @Test fun stageFourPassesByItselfWhenTheNotesAreHeld() = runTest(dispatcher) {
        val recorder = Recorder()
        val vm = viewModel(recorder)
        vm.prepare(GuideInstrument.Danso, detail)
        runCurrent()
        assertTrue(vm.uiState.value.autoJudged)

        vm.startListening()
        runCurrent()
        assertTrue(vm.uiState.value.listening)
        hold(recorder, hz("汰"), 2_100)
        hold(recorder, null, 200)
        assertEquals(1, vm.uiState.value.pitch?.hits)
        assertEquals(LearnPracticeCue.NoteHit, vm.uiState.value.cue)
        hold(recorder, hz("潢"), 2_100)

        val state = vm.uiState.value
        assertEquals(LearnPracticePhase.Passed, state.phase)
        assertFalse(state.listening)
        assertTrue(recorder.cancels >= 1)
    }

    @Test fun wrongNotesAndSilenceDoNotPass() = runTest(dispatcher) {
        val recorder = Recorder()
        val vm = viewModel(recorder)
        vm.prepare(GuideInstrument.Danso, detail)
        runCurrent()
        vm.startListening()
        runCurrent()
        hold(recorder, hz("無"), 4_000)
        hold(recorder, null, 4_000)

        val state = vm.uiState.value
        assertTrue(state.listening)
        assertEquals(0, state.pitch?.hits)
        vm.stopListening()
    }

    @Test fun stageFiveStopsWhenItCanNoLongerPass() = runTest(dispatcher) {
        val recorder = Recorder()
        val vm = viewModel(recorder, stage = 5)
        vm.prepare(GuideInstrument.Danso, detail.copy(pitchNames = listOf("仲", "林", "無", "潢", "汰")))
        runCurrent()
        vm.startListening()
        runCurrent()
        // 첫 두 음을 다른 음(汰)으로 내면 10음 중 9음을 맞힐 수 없다.
        repeat(2) {
            hold(recorder, hz("汰"), 400)
            hold(recorder, null, 200)
        }

        val state = vm.uiState.value
        assertFalse(state.listening)
        assertEquals(LearnPracticeCue.NotPassed, state.cue)
        assertEquals(listOf(LearnNoteResult.Miss, LearnNoteResult.Miss), state.pitch?.results)

        // 다시 들으면 처음부터 잰다
        vm.startListening()
        runCurrent()
        assertEquals(0, vm.uiState.value.pitch?.index)
        assertEquals(emptyList(), vm.uiState.value.pitch?.results)
        vm.stopListening()
    }

    @Test fun staysSelfConfirmedWithoutPitchReadingsOrCriteria() = runTest(dispatcher) {
        val noPitch = viewModel(Recorder(observes = false))
        noPitch.prepare(GuideInstrument.Danso, detail)
        runCurrent()
        assertNull(noPitch.uiState.value.pitch)
        assertFalse(noPitch.uiState.value.autoJudged)

        val noCriteria = viewModel(Recorder(), load = null)
        noCriteria.prepare(GuideInstrument.Danso, detail)
        runCurrent()
        assertFalse(noCriteria.uiState.value.autoJudged)

        val stageSeven = viewModel(Recorder(), stage = 7)
        stageSeven.prepare(GuideInstrument.Danso, detail)
        runCurrent()
        assertFalse(stageSeven.uiState.value.autoJudged)
    }

    @Test fun criteriaAreLookedUpByTheServerInstrumentCode() = runTest(dispatcher) {
        val asked = mutableListOf<String>()
        val repository = object : NotePracticeCriteriaRepository {
            override suspend fun criteria(instrumentId: String): NotePracticeCriteriaLoad {
                asked += instrumentId
                return if (instrumentId == "7") NotePracticeCriteriaLoad.Found(criteria) else NotePracticeCriteriaLoad.Unsupported
            }
        }
        val catalog = listOf(CatalogInstrument("3", "DANSO", "단소"), CatalogInstrument("7", "JEONGAK_DAEGEUM", "정악대금"))

        assertEquals(criteria, loadLearnCriteria(GuideInstrument.DaegeumJeongak, { catalog }, repository))
        assertNull(loadLearnCriteria(GuideInstrument.Danso, { catalog }, repository))
        // 목록에 없는 악기·목록 실패는 기준을 묻지 않는다
        assertNull(loadLearnCriteria(GuideInstrument.Sogeum, { catalog }, repository))
        assertNull(loadLearnCriteria(GuideInstrument.Danso, { error("목록 실패") }, repository))
        assertEquals(listOf("7", "3"), asked)
    }
}
