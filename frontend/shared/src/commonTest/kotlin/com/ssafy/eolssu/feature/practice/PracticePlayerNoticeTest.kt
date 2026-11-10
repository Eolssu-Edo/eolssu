package com.ssafy.eolssu.feature.practice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PracticePlayerNoticeTest {
    @Test fun missingPersonalAudioReplacesTheModelControlsButNotRhythm() {
        val state = PracticeUiState(mine = true, personalAudio = PracticePersonalAudio.Missing)
        assertEquals(PracticePersonalAudio.Missing, state.personalAudioControl)
        assertEquals(PracticePlayerNotice.None, state.playerNotice)
        assertEquals(PracticePlayerNotice.PersonalDownloadFailed, state.copy(personalAudio = PracticePersonalAudio.Failed).playerNotice)
        assertNull(state.copy(personalAudio = PracticePersonalAudio.Ready).personalAudioControl)
        assertNull(state.copy(mine = false).personalAudioControl)
        assertNull(state.copy(mode = PracticeMode.Rhythm).personalAudioControl)
    }

    @Test fun readyPlayingAndCompletedNeverShowRoutinePlaybackText() {
        for (status in listOf(ModelAudioStatus.Idle, ModelAudioStatus.Playing, ModelAudioStatus.Paused, ModelAudioStatus.Completed)) {
            assertEquals(PracticePlayerNotice.None, PracticeUiState(modelAudioStatus = status).playerNotice)
        }
        assertEquals(PracticePlayerNotice.None, PracticeUiState(mode = PracticeMode.Rhythm,
            jangdanSupported = true, jangdanStatus = JangdanStatus.Playing).playerNotice)
    }

    @Test fun bothAudioSourcesUseTheSameLoadingState() {
        assertEquals(PracticePlayerNotice.Loading, PracticeUiState(modelAudioStatus = ModelAudioStatus.Loading).playerNotice)
        assertEquals(PracticePlayerNotice.Loading, PracticeUiState(mode = PracticeMode.Rhythm, jangdanStatus = JangdanStatus.Loading).playerNotice)
        val accompaniment = PracticeUiState(jangdanSupported = true, jangdanStatus = JangdanStatus.Loading)
        assertEquals(PracticePlayerNotice.Loading, accompaniment.copy(settings = accompaniment.settings.copy(jangdanSound = true)).playerNotice)
    }

    @Test fun errorsRemainVisibleButStaleModelErrorsDoNotLeakIntoRhythm() {
        assertEquals(PracticePlayerNotice.Failed, PracticeUiState(modelAudioStatus = ModelAudioStatus.Failed).playerNotice)
        assertEquals(PracticePlayerNotice.Interrupted, PracticeUiState(mode = PracticeMode.Rhythm,
            jangdanSupported = true, jangdanStatus = JangdanStatus.Interrupted).playerNotice)
        assertEquals(PracticePlayerNotice.None, PracticeUiState(mode = PracticeMode.Rhythm,
            jangdanSupported = true, modelAudioStatus = ModelAudioStatus.Failed).playerNotice)
    }
}
