package com.ssafy.eolssu.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ssafy.eolssu.platform.audio.WebAudioPlayer

/** HTML media playback stays in the application; no external window or download. */
@Composable
actual fun rememberReferenceAudioPlayer(): ReferenceAudioPlayer = remember { WebAudioPlayer(retainCompletedMedia = true) }
