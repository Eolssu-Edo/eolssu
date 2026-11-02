package com.ssafy.eolssu.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ssafy.eolssu.platform.audio.AndroidAudioPlayer

@Composable
actual fun rememberReferenceAudioPlayer(): ReferenceAudioPlayer {
    val context = LocalContext.current
    return remember(context) { AndroidAudioPlayer(context, retainCompletedMedia = true) }
}
