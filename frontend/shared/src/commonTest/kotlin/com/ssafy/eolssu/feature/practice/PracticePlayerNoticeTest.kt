package com.ssafy.eolssu.feature.practice

import kotlin.test.Test
import kotlin.test.assertEquals

class PracticePlayerNoticeTest {
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
