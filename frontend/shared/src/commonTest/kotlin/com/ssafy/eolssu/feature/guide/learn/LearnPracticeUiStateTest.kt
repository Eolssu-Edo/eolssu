package com.ssafy.eolssu.feature.guide.learn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LearnPracticeUiStateTest {

    private val loud = 0.6f
    private val quiet = 0.05f

    /** [ms] 동안 [level] 크기를 100ms마다 넣는다. */
    private fun LearnPracticeUiState.feed(level: Float, ms: Int): LearnPracticeUiState =
        (0 until ms / 100).fold(this) { s, _ -> s.recordLevel(level, 100.0) }

    private fun firstSound() = LearnPracticeUiState(stage = 3).startListening()

    /** 5단계는 올라가기·내려오기 박스가 앞 절반·뒤 절반 음을, 6단계는 박스 하나가 모든 음을 맡는다(S15P31E102-749). */
    @Test
    fun segmentNotesSplitStageFiveIntoUpAndDownBoxes() {
        assertEquals(0 until 5, segmentNotes(stage = 5, box = 0, count = 10))
        assertEquals(5 until 10, segmentNotes(stage = 5, box = 1, count = 10))
        assertEquals(0 until 2, segmentNotes(stage = 6, box = 0, count = 2))
    }

    /** 7단계는 소리가 기준 아래로 내려갔다 다시 날 때마다 '투' 하나로 세고, 네 번이면 통과한다(S15P31E102-749). */
    @Test
    fun stageSevenCountsTonguedSoundsUntilFour() {
        val start = LearnPracticeUiState(stage = 7).startListening()
        // 한 소리를 길게 불면 한 번만 센다
        val one = start.feed(loud, 1_000)
        assertEquals(1, one.tongues)
        // 소리를 끊었다 다시 내면 하나씩 늘어난다(측정 한 번만 끊겨도 센다)
        val three = one.feed(quiet, 100).feed(loud, 200).feed(quiet, 100).feed(loud, 200)
        assertEquals(3, three.tongues)
        assertTrue(three.listening)
        val passed = three.feed(quiet, 100).feed(loud, 100)
        assertEquals(TONGUE_TARGET, passed.tongues)
        assertEquals(LearnPracticePhase.Passed, passed.phase)
        assertTrue(passed.autoJudged)
        // 처음부터 하면 다시 센다
        assertEquals(0, passed.reset().tongues)
        assertEquals(0, passed.startListening().tongues)
    }

    @Test
    fun countsASoundHeldForOneSecondOnce() {
        val held = firstSound().feed(loud, 1_000)
        assertEquals(1, held.successes)
        assertEquals(LearnPracticeCue.Counted, held.cue)
        assertEquals(1f, held.holdProgress)

        // 한 소리를 길게 이어도 한 번만 센다
        assertEquals(1, held.feed(loud, 3_000).successes)
    }

    @Test
    fun passesAfterOneSoundHeldForOneSecond() {
        assertEquals(1, FIRST_SOUND_TARGET)
        val passed = firstSound().feed(loud, 1_000)

        assertEquals(LearnPracticePhase.Passed, passed.phase)
        assertEquals(1, passed.successes)
        assertFalse(passed.listening)
        // 통과한 뒤 들어온 크기는 반영하지 않는다
        assertEquals(passed, passed.recordLevel(loud, 100.0))
    }

    @Test
    fun shortBreaksKeepTheSameSound() {
        val state = firstSound().feed(loud, 500).feed(quiet, 200).feed(loud, 500)
        assertEquals(1, state.successes)
    }

    @Test
    fun soundEndingBeforeOneSecondIsTooShortAndResetsHold() {
        val state = firstSound().feed(loud, 700).feed(quiet, 300)

        assertEquals(0, state.successes)
        assertEquals(0.0, state.holdMs)
        assertEquals(LearnPracticeCue.TooShort, state.cue)
    }

    @Test
    fun silenceWithoutSoundIsNotTooShort() {
        val state = firstSound().feed(quiet, 1_000)
        assertNull(state.cue)
        assertFalse(state.sounding)
    }

    @Test
    fun stagesWithoutSoundJudgingNeverCount() {
        val state = LearnPracticeUiState(stage = 4).startListening().feed(loud, 5_000).feed(quiet, 500)

        assertFalse(state.judgesBySound)
        assertEquals(0, state.successes)
        assertNull(state.cue)
        assertTrue(state.listening)
    }

    @Test
    fun ignoresLevelsWhenNotListening() {
        val ready = LearnPracticeUiState(stage = 3)
        assertEquals(ready, ready.recordLevel(loud, 100.0))
    }

    @Test
    fun stoppingBeforeOneSecondKeepsReadyAndRestartingAfterPassCountsAgain() {
        val stopped = firstSound().feed(loud, 500).stopListening()
        assertEquals(0, stopped.successes)
        assertEquals(LearnPracticePhase.Ready, stopped.phase)

        val passed = firstSound().feed(loud, 2_000)
        assertEquals(0, passed.startListening().successes)
        assertEquals(0, passed.reset().successes)
    }

    @Test
    fun micStatusDecidesWhetherListeningCanStart() {
        assertTrue(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Denied).canListen)
        assertTrue(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Failed).canListen)
        assertFalse(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Blocked).canListen)
        assertFalse(LearnPracticeUiState(stage = 3, mic = LearnMicStatus.Unsupported).canListen)

        val denied = firstSound().feed(loud, 500).micUnavailable(LearnMicStatus.Denied)
        assertFalse(denied.listening)
        assertEquals(LearnMicStatus.Available, denied.startListening().mic)
    }

    /** 박스 체크를 풀면 통과를 거두고 낼 음을 그 자리로 되돌린다. 앞 음의 결과는 남긴다(S15P31E102-749). */
    @Test
    fun rewindingToANoteKeepsEarlierResultsAndClearsThePass() {
        val pitch = LearnPitchProgress(
            notes = List(10) { "仲" },
            required = 10,
            holdTargetMs = 300,
            index = 10,
            hits = 10,
            results = List(10) { LearnNoteResult.Hit },
            finished = true,
        )
        val passed = LearnPracticeUiState(stage = 5, pitch = pitch, phase = LearnPracticePhase.Passed)

        val back = passed.rewindTo(5)
        assertEquals(LearnPracticePhase.Ready, back.phase)
        assertEquals(5, back.pitch?.index)
        assertEquals(5, back.pitch?.hits)
        assertEquals(5, back.pitch?.results?.size)
        assertEquals(false, back.pitch?.finished)
    }
}
