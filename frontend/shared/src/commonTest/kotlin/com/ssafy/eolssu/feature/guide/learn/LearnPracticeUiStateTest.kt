package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.model.fingering.GuideInstrument
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

    /** 높이로 같은 음을 볼 때는 소리 크기로 '투'를 세지 않고, 판정기가 센 횟수를 그대로 쓴다(S15P31E102-845). */
    @Test
    fun stageSevenByPitchTakesTheJudgedCount() {
        val start = LearnPracticeUiState(stage = 7, tonguesByPitch = true).startListening()
        val sounded = start.feed(loud, 200).feed(quiet, 100).feed(loud, 100)
        assertEquals(0, sounded.tongues)
        assertFalse(sounded.quiet)

        val three = sounded.recordTongue(3)
        assertEquals(3, three.tongues)
        assertTrue(three.listening)
        // 다른 음으로 새로 세면 줄어든다
        assertEquals(1, three.recordTongue(1).tongues)
        val passed = three.recordTongue(TONGUE_TARGET)
        assertEquals(LearnPracticePhase.Passed, passed.phase)
        // 듣는 중이 아니거나 7단계가 아니면 그대로다
        assertEquals(passed, passed.recordTongue(1))
        val four = LearnPracticeUiState(stage = 4).startListening()
        assertEquals(four, four.recordTongue(2))
    }

    /** 3단계는 소리 크기만으로 통과하지 않는다. 음높이 판정 계획이 없으면 스스로 확인한다(S15P31E102-1216). */
    @Test
    fun stageThreeNeverPassesByLoudnessAlone() {
        val state = firstSound().feed(loud, 5_000).feed(quiet, 500).feed(loud, 5_000)

        assertEquals(LearnPracticePhase.Listening, state.phase)
        assertFalse(state.autoJudged)
        assertNull(state.cue)
        assertTrue(state.sounding)
    }

    /** 3단계 음높이 판정 계획을 받으면 앱이 판정하고, 판정기가 통과를 알리면 통과한다. */
    @Test
    fun stageThreeWithAPitchPlanPassesByThePitchJudge() {
        val ready = LearnPracticeUiState(stage = 3).withPitchPlan(learnFirstSoundPlan(GuideInstrument.Danso, null))
        assertTrue(ready.autoJudged)
        assertEquals(FIRST_SOUND_HOLD_MS, ready.pitch?.holdTargetMs)

        val listening = ready.startListening().feed(loud, 3_000)
        assertEquals(LearnPracticePhase.Listening, listening.phase)
        val holding = listening.recordPitch(0, 0, 640.0, null, passed = false, finished = false)
        assertEquals(0.6f, holding.pitch?.holdProgress)
        val passed = holding.recordPitch(1, 1, 0.0, LearnNoteResult.Hit, passed = true, finished = true)
        assertEquals(LearnPracticePhase.Passed, passed.phase)
    }

    /** 통과 기준 문구의 초는 끝의 0을 뺀다(S15P31E102-857). */
    @Test
    fun holdSecondsDropTrailingZeros() {
        assertEquals("0.3", holdSecondsLabel(300.0))
        assertEquals("0.2", holdSecondsLabel(200.0))
        assertEquals("1", holdSecondsLabel(1_000.0))
        assertEquals("1.25", holdSecondsLabel(1_250.0))
    }

    @Test
    fun ignoresLevelsWhenNotListening() {
        val ready = LearnPracticeUiState(stage = 3)
        assertEquals(ready, ready.recordLevel(loud, 100.0))
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
