package com.ssafy.eolssu.feature.practice

import androidx.lifecycle.ViewModelStore
import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.network.SignedInMember
import com.ssafy.eolssu.core.storage.FakeKeyValueStore
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.data.practice.PracticeSettings
import com.ssafy.eolssu.data.practice.PracticeSettingsStore
import com.ssafy.eolssu.data.practice.PracticeTimeQueue
import com.ssafy.eolssu.data.song.SampleSongRepository
import com.ssafy.eolssu.data.song.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2차 '한 음씩 따라 불기'의 진행 규칙과 판정 연결 지점. */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeStepTest {
    private fun pitch(yul: Yul) = ScoreNote.Pitch(yul)

    /**
     * 연주 순서: 0 黃 · 1 ㅡ · 2 △ · 3 太 · 4 ㅡ(太와 같은 정간) · 5 林 · 6 △.
     * 기다리는 음은 0·3·5뿐이다.
     */
    private val song = Song(
        id = "step-test", title = "한 음씩 검사", level = SongLevel.Intro,
        jangdan = Jangdan("세마치", listOf(3, 3, 3), 3, 3), baseBpm = 60, description = "", evaluable = false,
        score = Score(
            listOf(
                listOf(ScoreCell(pitch(Yul.Hwangjong)), ScoreCell(ScoreNote.Hold), ScoreCell(ScoreNote.Rest)),
                listOf(ScoreCell(pitch(Yul.Taeju), ScoreNote.Hold), ScoreCell(pitch(Yul.Imjong)), ScoreCell(ScoreNote.Rest)),
            ),
        ),
    )
    private val solo = PracticeUiState(song = song, solo = true)

    @Test fun targetsSkipHoldAndRestCells() {
        assertEquals(listOf(0, 3, 5), stepTargets(song.timeline))
        assertFalse(ScoreNote.Hold.isStepTarget)
        assertFalse(ScoreNote.Rest.isStepTarget)
        assertFalse(ScoreNote.Blank.isStepTarget)
    }

    @Test fun toggleOnlyInSoloPractice() {
        val evaluation = PracticeUiState(song = song).toggleStep()
        assertFalse(evaluation.step.on)
        assertFalse(evaluation.stepUi)
        val on = solo.toggleStep()
        assertTrue(on.stepUi)
        assertEquals(StepPhase.Idle, on.step.phase)
        // '내 연주'를 누르기 전에도 첫 음에 연두 테두리를 둔다(S15P31E102-1006).
        assertEquals(0, on.stepCurrentIndex)
        assertEquals(0, on.stepShownIndex)
        assertFalse(on.toggleStep().stepUi)
    }

    /** '현재 정간' 칸을 누르면 보이는 정간 음의 운지 예시음을 낸다. 한 음씩이 꺼져 있으면 낼 음이 없다(S15P31E102-717). */
    @Test fun currentCellPlaysFingeringNoteOfShownStep() {
        val on = solo.toggleStep()
        assertEquals("黃", on.stepNoteToPlay?.hanja)
        assertEquals("太", on.tapStepPlaying().matchStepNote(0).stepNoteToPlay?.hanja)
        assertNull(solo.stepNoteToPlay)
    }

    /** 서버 공통 악보의 율명·옥타브(청고선 등)도 운지표의 같은 음으로 찾아 예시음을 낸다(S15P31E102-717). */
    @Test fun currentCellPlaysSourcePitchFromCommonScore() {
        val common = song.copy(score = Score(listOf(listOf(ScoreCell(ScoreNote.SourcePitch("고", 1)), ScoreCell(ScoreNote.Rest), ScoreCell(ScoreNote.Rest)))))
        val sogeum = PracticeUiState(song = common, solo = true, settings = PracticeSettings(instrumentId = GuideInstrument.Sogeum.id)).toggleStep()
        assertEquals("㴌", sogeum.stepNoteToPlay?.hanja)
    }

    @Test fun matchedNoteAdvancesToNextNoteSkippingHoldAndRest() {
        var s = solo.toggleStep().tapStepPlaying()
        assertEquals(StepPhase.Listening, s.step.phase)
        assertEquals(0, s.stepCurrentIndex)
        assertEquals(1 to 3, s.stepProgress)

        s = s.matchStepNote(0)
        assertEquals(3, s.step.target) // ㅡ(1)·△(2)를 건너뜀
        assertEquals(2 to 3, s.stepProgress)

        // 늦게 도착한 앞 음 판정은 무시한다.
        assertEquals(s, s.matchStepNote(0))

        s = s.matchStepNote(3)
        assertEquals(5, s.step.target) // 같은 정간의 ㅡ(4)를 건너뜀

        s = s.matchStepNote(5)
        assertEquals(StepPhase.Finished, s.step.phase)
        assertNull(s.stepCurrentIndex)

        // 끝난 뒤 ‘내 연주’를 누르면 첫 음부터 다시.
        s = s.tapStepPlaying()
        assertEquals(StepPhase.Listening, s.step.phase)
        assertEquals(0, s.step.target)
    }

    @Test fun pauseThenResumeContinuesFromSameNoteWithoutCountdown() {
        val listening = solo.toggleStep().tapStepPlaying().matchStepNote(0)
        val paused = listening.tapStepPlaying()
        assertEquals(StepPhase.Paused, paused.step.phase)
        assertEquals(3, paused.stepCurrentIndex)
        // 멈춘 동안 들어온 판정은 무시한다.
        assertEquals(paused, paused.matchStepNote(3))

        // 녹음 버튼을 다시 누르면 3·2·1 없이 멈춘 음부터 바로 듣는다(S15P31E102-645).
        val resumed = paused.tapStepPlaying()
        assertEquals(StepPhase.Listening, resumed.step.phase)
        assertEquals(3, resumed.step.target)
        assertEquals(3, resumed.stepCurrentIndex)
        assertTrue(resumed.stepBusy)
    }

    /** 한 음씩에서 악보를 누르면 운지법을 열지 않고 지금 음을 그 자리로 옮긴다(S15P31E102-1006). */
    @Test fun tappingScoreMovesStepTargetInsteadOfOpeningFingering() {
        val listening = solo.toggleStep().tapStepPlaying()
        assertEquals(setOf(ContinuousActivity.RECORDING), listening.runningActivities)
        val units = requireNotNull(listening.song).displayUnits
        fun unitAt(index: Int) = units.first { it.event.index == index }

        val moved = listening.selectUnit(unitAt(5))
        assertEquals(StepPhase.Listening, moved.step.phase)
        assertEquals(5, moved.step.target)
        assertNull(moved.panel)
        assertEquals(listening.selected, moved.selected)

        // 쉼(△)을 누르면 다음 음, 이음(ㅡ)을 누르면 이어 내는 앞 음으로 옮긴다.
        assertEquals(3, listening.moveStepTo(2).step.target)
        assertEquals(0, listening.moveStepTo(1).step.target)

        // 시작 전에 누르면 그 음에서 멈춤으로 두고, '내 연주'를 누르면 그 음부터 듣는다.
        val idle = solo.toggleStep().selectUnit(unitAt(5))
        assertEquals(StepPhase.Paused, idle.step.phase)
        assertEquals(5, idle.stepCurrentIndex)
        assertEquals(5, idle.tapStepPlaying().step.target)

        val paused = listening.pauseStep()
        assertEquals(emptySet(), paused.runningActivities)
        assertEquals(3, paused.selectUnit(unitAt(3)).stepCurrentIndex)
    }

    /** 앞과 같은 음을 기다리면 한 번 끊고 불라는 안내를 둔다(S15P31E102-1006). */
    @Test fun repeatedSameNoteIsFlaggedWhileListening() {
        val repeated = song.copy(
            score = Score(listOf(listOf(ScoreCell(pitch(Yul.Hwangjong)), ScoreCell(pitch(Yul.Hwangjong)), ScoreCell(pitch(Yul.Taeju))))),
        )
        val on = PracticeUiState(song = repeated, solo = true).toggleStep()
        assertFalse(on.stepRepeatsPrevious)
        val listening = on.tapStepPlaying()
        assertFalse(listening.stepRepeatsPrevious)
        val second = listening.matchStepNote(0)
        assertTrue(second.stepRepeatsPrevious)
        assertFalse(second.pauseStep().stepRepeatsPrevious)
        assertFalse(second.matchStepNote(1).stepRepeatsPrevious)
    }

    @Test fun turningOffWhileListeningKeepsPositionPausedForLater() {
        val off = solo.toggleStep().tapStepPlaying().matchStepNote(0).toggleStep()
        assertFalse(off.stepUi)
        assertEquals(StepPhase.Paused, off.step.phase)
        val on = off.toggleStep()
        assertEquals(3, on.stepCurrentIndex)
        assertEquals(StepPhase.Listening, on.tapStepPlaying().step.phase)
    }

    @Test fun turningOnDuringModelStopsModelAndPausesAtHeardNote() {
        val model = solo.startModel().copy(position = 4)
        val on = model.toggleStep()
        assertEquals(PracticeMode.Ready, on.mode)
        assertFalse(on.playing)
        assertEquals(StepPhase.Paused, on.step.phase)
        assertEquals(5, on.step.target)
    }

    @Test fun viewModelDrivesJudgeAndResumesImmediately() {
        val judge = FakeStepNoteJudge()
        stepTest(judge) { vm ->
            vm.toggleStep()
            vm.tapStepPlaying()
            assertEquals(StepTarget(0, pitch(Yul.Hwangjong)), judge.listening)
            judge.match(0)
            runCurrent()
            assertEquals(3, vm.uiState.value.step.target)
            assertEquals(3, judge.listening?.index)

            vm.tapStepPlaying() // 멈춤: 마이크를 놓는다
            assertNull(judge.listening)
            judge.matchLate(3)
            runCurrent()
            assertEquals(StepPhase.Paused, vm.uiState.value.step.phase)

            vm.tapStepPlaying() // 카운트다운 없이 바로 멈춘 음부터 듣는다.
            assertEquals(StepPhase.Listening, vm.uiState.value.step.phase)
            assertEquals(3, judge.listening?.index)
            judge.match(3)
            runCurrent()
            assertEquals(5, vm.uiState.value.step.target)
            assertEquals(5, judge.listening?.index)

            // 화면을 벗어나면 멈추고 판정기를 끈다.
            vm.onLeaveScreen()
            assertEquals(StepPhase.Paused, vm.uiState.value.step.phase)
            assertNull(judge.listening)
        }
    }

    @Test fun manualStandInAdvancesOnlyWhenJudgeCannotDecide() {
        stepTest(ManualStepNoteJudge()) { vm ->
            assertTrue(vm.uiState.value.step.manualConfirm)
            vm.toggleStep()
            vm.tapStepPlaying()
            vm.confirmStepNoteManually()
            assertEquals(3, vm.uiState.value.step.target)
        }
        stepTest(FakeStepNoteJudge()) { vm ->
            assertFalse(vm.uiState.value.step.manualConfirm)
            vm.toggleStep()
            vm.tapStepPlaying()
            vm.confirmStepNoteManually()
            assertEquals(0, vm.uiState.value.step.target)
        }
    }

    @Test fun mineFlagReachesState() = stepTest(ManualStepNoteJudge(), mine = true) { vm ->
        assertTrue(vm.uiState.value.mine)
        assertTrue(vm.uiState.value.solo)
        assertEquals(song.title, vm.uiState.value.title)
        vm.showSheetTitle("아리랑 (내 편곡)")
        assertEquals("아리랑 (내 편곡)", vm.uiState.value.title)
        vm.showSheetTitle("")
        assertEquals(song.title, vm.uiState.value.title)
    }

    private fun stepTest(judge: StepNoteJudge, mine: Boolean = false, testSong: Song = song, block: suspend TestScope.(PracticeViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val storage = FakeKeyValueStore()
            val songs = object : SongRepository by SampleSongRepository() {
                override suspend fun getSong(songId: String) = testSong
            }
            val vm = PracticeViewModel(testSong.id, true, songs, PracticeSettingsStore(storage), PracticeTimeMeter({ 0L }),
                PracticeTimeQueue(storage, null, SignedInMember(storage), backgroundScope),
                playbackTimeSource = testScheduler.timeSource, mine = mine, stepJudge = judge,
                loadPersonalSheet = { id -> testSong.takeIf { it.id == id } })
            models.put("practice", vm)
            runCurrent()
            block(vm)
        } finally {
            models.clear()
            Dispatchers.resetMain()
        }
    }
}

/** 실제 음높이 판정 대신 '맞게 불었다'를 직접 보내는 판정기. */
private class FakeStepNoteJudge : StepNoteJudge {
    override val manualConfirm = false
    var listening: StepTarget? = null
        private set
    private var callback: ((Int) -> Unit)? = null

    override fun listen(target: StepTarget, onMatched: (index: Int) -> Unit) {
        listening = target
        callback = onMatched
    }

    override fun stop() {
        listening = null
    }

    fun match(index: Int) = requireNotNull(callback)(index)

    /** 멈춘 뒤 늦게 도착한 판정. */
    fun matchLate(index: Int) = callback?.invoke(index)
}
