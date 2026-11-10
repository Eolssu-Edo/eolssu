package com.ssafy.eolssu.feature.practice

import com.ssafy.eolssu.data.song.smallScoreSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PracticePlayerNoticeTest {
    @Test fun audioShortcutRequiresMissingPersonalAudioAndAConfirmedVersion() {
        val state = PracticeUiState(mine = true, mode = PracticeMode.Model,
            song = smallScoreSong().copy(scoreVersion = 3), modelAudioStatus = ModelAudioStatus.Unavailable)

        assertEquals(3, state.practiceAudioShortcutVersion)
        assertNull(state.copy(mine = false).practiceAudioShortcutVersion)
        assertNull(state.copy(song = state.song?.copy(scoreVersion = null)).practiceAudioShortcutVersion)
        assertNull(state.copy(mode = PracticeMode.Rhythm).practiceAudioShortcutVersion)
        assertNull(state.copy(modelAudioStatus = ModelAudioStatus.Playing).practiceAudioShortcutVersion)
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
