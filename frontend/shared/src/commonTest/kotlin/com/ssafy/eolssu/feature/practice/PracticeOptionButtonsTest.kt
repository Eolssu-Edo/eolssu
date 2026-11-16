package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.core.model.Jangdan
import com.ssafy.eolssu.core.model.Score
import com.ssafy.eolssu.core.model.ScoreCell
import com.ssafy.eolssu.core.model.ScoreNote
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.data.practice.PracticeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 연습 화면 제목 줄의 보기 버튼(S15P31E102-1037): 모드와 관계없이 같은 모양·순서이고 맨 왼쪽 한 칸만 모드를 따른다. */
class PracticeOptionButtonsTest {
    private val song = Song(
        id = "option-test", title = "보기 버튼", level = SongLevel.Intro,
        jangdan = Jangdan("세마치", listOf(3, 3, 3), 3, 3), baseBpm = 60, description = "", evaluable = true,
        score = Score(listOf(listOf(ScoreCell(ScoreNote.Pitch(Yul.Hwangjong)), ScoreCell(ScoreNote.Hold), ScoreCell(ScoreNote.Rest)))),
    )

    private fun kinds(state: PracticeUiState, fingering: Boolean = true) = state.optionButtons(fingering).map { it.kind }

    @Test fun evaluationShowsPerfectScoreThenFingeringThenHangul() {
        val evaluation = PracticeUiState(song = song)
        assertEquals(listOf(PracticeOptionKind.LiveMarks, PracticeOptionKind.Fingering, PracticeOptionKind.Hangul), kinds(evaluation))
    }

    @Test fun soloShowsStepInsteadOfPerfectScore() {
        val solo = PracticeUiState(song = song, solo = true)
        assertEquals(listOf(PracticeOptionKind.Step, PracticeOptionKind.Fingering, PracticeOptionKind.Hangul), kinds(solo))
        val noCriteria = solo.copy(step = solo.step.copy(available = false))
        assertEquals(listOf(PracticeOptionKind.Fingering, PracticeOptionKind.Hangul), kinds(noCriteria))
    }

    @Test fun tabletLeavesOutFingeringView() {
        assertEquals(listOf(PracticeOptionKind.LiveMarks, PracticeOptionKind.Hangul), kinds(PracticeUiState(song = song), fingering = false))
    }

    @Test fun buttonsFollowSettings() {
        val off = PracticeUiState(song = song, settings = PracticeSettings(showHangul = false, fingeringView = false, liveMarks = false))
        assertTrue(off.optionButtons(true).none { it.on })
        val on = PracticeUiState(song = song, settings = PracticeSettings(showHangul = true, fingeringView = true, liveMarks = true))
        assertTrue(on.optionButtons(true).all { it.on })
        // 기초부터 배우기 곡의 화면 한정 운지법 값도 버튼에 그대로 보인다.
        val learn = off.copy(fingeringOverride = true)
        assertTrue(learn.optionButtons(true).single { it.kind == PracticeOptionKind.Fingering }.on)
    }

    @Test fun stepButtonLocksWhileStepCannotToggle() {
        val solo = PracticeUiState(song = song, solo = true)
        assertTrue(solo.optionButtons(true).first().enabled)
        val recording = solo.copy(mode = PracticeMode.Recording)
        assertFalse(recording.optionButtons(true).single { it.kind == PracticeOptionKind.Step }.enabled)
        assertTrue(recording.optionButtons(true).filter { it.kind != PracticeOptionKind.Step }.all { it.enabled })
    }
}
