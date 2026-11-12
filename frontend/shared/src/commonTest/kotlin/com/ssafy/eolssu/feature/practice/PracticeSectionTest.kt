package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.ScorePosition
import com.ssafy.eolssu.core.model.ScoreSection
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 연습 화면의 ‘구간 연습’(S15P31E102-776): 구간 고르기, 되풀이 회차, 한 음씩·모범 연주의 구간 안 시작. */
class PracticeSectionTest {
    private fun pitch(yul: Yul) = ScoreNote.Pitch(yul)

    /**
     * 연주 순서: 0 黃 · 1 ㅡ · 2 △ (1각) · 3 太 · 4 ㅡ(太와 같은 정간) · 5 林 · 6 △ (2각).
     * 기다리는 음은 0·3·5뿐이다.
     */
    private val song = Song(
        id = "section-test", title = "구간 검사", level = SongLevel.Intro,
        jangdan = Jangdan("세마치", listOf(3, 3, 3), 3, 3), baseBpm = 60, description = "", evaluable = false,
        score = Score(
            listOf(
                listOf(ScoreCell(pitch(Yul.Hwangjong)), ScoreCell(ScoreNote.Hold), ScoreCell(ScoreNote.Rest)),
                listOf(ScoreCell(pitch(Yul.Taeju), ScoreNote.Hold), ScoreCell(pitch(Yul.Imjong)), ScoreCell(ScoreNote.Rest)),
            ),
        ),
    )
    private val secondGak = ScoreSection(ScorePosition(1, 0), ScorePosition(1, 2))
    private val solo = PracticeUiState(song = song, solo = true)

    @Test fun turningOnWithRecommendationStartsAtTheSectionAndLimitsStepTargets() {
        val on = solo.toggleSectionPractice(secondGak)
        assertEquals(3..6, on.sectionEvents)
        assertEquals(3, on.selected)
        assertEquals(3, on.position)
        assertEquals(listOf(3, 5), on.stepTargetsInSection())
        // 추천 구간이 잡혀 있어도 직접 고르기 전에는 시작 정간부터 안내한다.
        assertEquals(SectionSelectStep.Start, on.sectionPractice?.step)
        // 끄면 곡 전체 연습으로 돌아간다.
        val off = on.toggleSectionPractice()
        assertNull(off.sectionPractice)
        assertNull(off.sectionEvents)
        assertEquals(listOf(0, 3, 5), off.stepTargetsInSection())
    }

    @Test fun tappingStartThenEndPicksASectionAndResetReturnsToTheRecommendation() {
        val on = solo.toggleSectionPractice(secondGak)
        val picking = on.tapSection(ScorePosition(0, 0))
        assertEquals(SectionSelectStep.End, picking.sectionPractice?.step)
        // 끝 정간을 기다리는 동안은 구간으로 묶지 않는다.
        assertNull(picking.sectionEvents)

        val picked = picking.tapSection(ScorePosition(0, 1))
        assertEquals(SectionSelectStep.Ready, picked.sectionPractice?.step)
        assertEquals(0..1, picked.sectionEvents)
        assertEquals(0, picked.selected)

        val reset = picked.resetSectionToRecommended()
        assertEquals(secondGak, reset.sectionPractice?.section)
        assertEquals(SectionSelectStep.Start, reset.sectionPractice?.step)
        assertEquals(3, reset.selected)
    }

    @Test fun loopingRepeatsTheChosenTimesThenStopsAtTheSectionStart() {
        var state = solo.toggleSectionPractice(secondGak).selectSectionRepeat(SectionRepeat.Three)
            .copy(mode = PracticeMode.Rhythm, playing = true, position = 6)
        assertTrue(state.passedSectionEnd.not())
        state = state.copy(position = 7)
        assertTrue(state.passedSectionEnd)

        val (second, again2) = state.loopSection()
        assertTrue(again2)
        assertEquals(2, second.sectionPractice?.round)
        assertEquals(3, second.position)
        assertTrue(second.playing)

        val (third, again3) = second.copy(position = 7).loopSection()
        assertTrue(again3)
        assertEquals(3, third.sectionPractice?.round)

        val (done, again4) = third.copy(position = 7).loopSection()
        assertFalse(again4)
        assertFalse(done.playing)
        assertEquals(3, done.position)
        assertEquals(3, done.sectionPractice?.round)
    }

    @Test fun endlessRepeatNeverStops() {
        var state = solo.toggleSectionPractice(secondGak).selectSectionRepeat(SectionRepeat.Endless).copy(mode = PracticeMode.Rhythm, playing = true)
        repeat(10) {
            val (next, again) = state.copy(position = 7).loopSection()
            assertTrue(again)
            state = next
        }
        assertEquals(11, state.sectionPractice?.round)
    }

    @Test fun stepModeWrapsWithinTheSectionForEachRound() {
        var state = solo.toggleSectionPractice(secondGak).selectSectionRepeat(SectionRepeat.Three).toggleStep().tapStepPlaying()
        assertEquals(3, state.step.target)
        state = state.matchStepNote(3)
        assertEquals(5, state.step.target)
        // 구간 마지막 음을 불면 다음 회차의 첫 음으로 돌아간다.
        state = state.matchStepNote(5)
        assertEquals(3, state.step.target)
        assertEquals(2, state.sectionPractice?.round)
        state = state.matchStepNote(3).matchStepNote(5)
        assertEquals(3, state.sectionPractice?.round)
        state = state.matchStepNote(3).matchStepNote(5)
        assertEquals(StepPhase.Finished, state.step.phase)
    }

    @Test fun modelStartsAndRestartsFromTheSectionStart() {
        val on = solo.toggleSectionPractice(secondGak)
        val playing = on.startModel()
        assertEquals(PracticeMode.Model, playing.mode)
        assertEquals(3, playing.position)
        val restarted = playing.copy(position = 5, sectionPractice = playing.sectionPractice?.copy(round = 3)).restartModel()
        assertEquals(3, restarted.position)
        assertEquals(1, restarted.sectionPractice?.round)
    }

    @Test fun sectionEvaluationLocksOnlyTheTempoChosenOnTheSelectScreen() {
        // 구간 재평가 설정 시트·태블릿 상태 패널: 속도는 구간 고르기에서 정한 값으로 보이고 잠기며, 녹음 전에는 연주 중 잠금이 아니다.
        val waiting = SectionLoopUiState(loading = false, song = song, section = secondGak, bpm = 40).asPracticeState(evaluation = true)
        assertEquals(40, waiting.bpm)
        assertTrue(waiting.tempoFixed && waiting.tempoLocked)
        assertFalse(waiting.playbackSettingsLocked)
        assertNull(waiting.openPanel(PracticePanel.Tempo).panel)
        assertEquals(PracticePanel.Jangdan, waiting.openPanel(PracticePanel.Jangdan).panel)
        // 녹음 중에는 연주 평가처럼 연주 중 잠금이다.
        val recording = SectionLoopUiState(loading = false, song = song, section = secondGak, bpm = 40, recording = true).asPracticeState(evaluation = true)
        assertTrue(recording.playbackSettingsLocked)
        // 연습 화면은 속도를 잠그지 않는다.
        assertFalse(solo.tempoLocked)
    }

    @Test fun sectionPicksAreAllowedWhilePausedInModelOrRhythm() {
        val on = solo.toggleSectionPractice(secondGak)
        assertTrue(on.canSelectUnit)
        assertTrue(on.copy(mode = PracticeMode.Model, playing = false).canSelectUnit)
        assertFalse(on.copy(mode = PracticeMode.Model, playing = true).canSelectUnit)
        // 구간 연습이 꺼져 있으면 예전처럼 준비 상태에서만 고른다.
        assertFalse(solo.copy(mode = PracticeMode.Model, playing = false).canSelectUnit)
    }
}
