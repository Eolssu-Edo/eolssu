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
import kotlinx.coroutines.test.advanceTimeBy
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
        var loudness = 0.6f
        override fun level() = loudness

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

    /** 5단계는 다른 음을 내도 멈추지 않고 지금 음을 계속 기다린다(S15P31E102-749). */
    @Test fun stageFiveKeepsListeningThroughWrongNotes() = runTest(dispatcher) {
        val recorder = Recorder()
        val vm = viewModel(recorder, stage = 5)
        vm.prepare(GuideInstrument.Danso, detail.copy(pitchNames = listOf("仲", "林", "無", "潢", "汰")))
        runCurrent()
        vm.startListening()
        runCurrent()
        repeat(2) {
            hold(recorder, hz("汰"), 1_100)
            hold(recorder, null, 200)
        }
        val wrong = vm.uiState.value
        assertTrue(wrong.listening)
        assertEquals(0, wrong.pitch?.index)
        assertEquals(emptyList(), wrong.pitch?.results)

        hold(recorder, hz("仲"), 1_100)
        assertEquals(listOf(LearnNoteResult.Hit), vm.uiState.value.pitch?.results)
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

        // 7단계는 음높이 대신 '투' 횟수로 판정한다(S15P31E102-749).
        val stageSeven = viewModel(Recorder(), stage = 7)
        stageSeven.prepare(GuideInstrument.Danso, detail)
        runCurrent()
        assertNull(stageSeven.uiState.value.pitch)
        assertTrue(stageSeven.uiState.value.judgesByTongue)
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

    /** 소리를 0.1초 끊었다가 [hz]로 0.1초 '투'를 낸다. 소리 크기는 0.1초마다 잰다. */
    private fun TestScope.tu(recorder: Recorder, hz: Double) {
        recorder.loudness = 0.05f
        hold(recorder, null, 100)
        advanceTimeBy(100)
        runCurrent()
        recorder.loudness = 0.6f
        advanceTimeBy(100)
        runCurrent()
        hold(recorder, hz, 100)
    }

    /** 7단계는 음높이 관측이 있으면 첫 '투'와 같은 음만 세고, 다른 음이면 거기서부터 다시 센다(S15P31E102-845). */
    @Test fun stageSevenCountsOnlyTonguesOnTheFirstNote() = runTest(dispatcher) {
        val recorder = Recorder()
        val vm = viewModel(recorder, stage = 7)
        vm.prepare(GuideInstrument.Danso, detail)
        runCurrent()
        assertTrue(vm.uiState.value.tonguesByPitch)

        vm.startListening()
        runCurrent()
        tu(recorder, hz("汰"))
        tu(recorder, hz("汰"))
        assertEquals(2, vm.uiState.value.tongues)
        tu(recorder, hz("潢"))
        assertEquals(1, vm.uiState.value.tongues)
        repeat(3) { tu(recorder, hz("潢")) }
        assertEquals(TONGUE_TARGET, vm.uiState.value.tongues)
        assertEquals(LearnPracticePhase.Passed, vm.uiState.value.phase)
        vm.stopListening()

        // 처음부터 하면 판정기도 다시 센다
        vm.reset()
        vm.startListening()
        runCurrent()
        tu(recorder, hz("無"))
        assertEquals(1, vm.uiState.value.tongues)
        vm.stopListening()
    }

    /** 음높이 관측이 없는 기기의 7단계는 전처럼 소리 크기만으로 센다. */
    @Test fun stageSevenWithoutPitchReadingsCountsBySoundOnly() = runTest(dispatcher) {
        val vm = viewModel(Recorder(observes = false), stage = 7)
        assertFalse(vm.uiState.value.tonguesByPitch)
        assertTrue(vm.uiState.value.judgesByTongue)
    }
}
