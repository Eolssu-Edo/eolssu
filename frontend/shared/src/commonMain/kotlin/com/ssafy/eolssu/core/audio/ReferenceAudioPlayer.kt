package com.ssafy.eolssu.core.audio

import androidx.compose.runtime.Composable

/** Player owned by the reference-audio dialog, independent of local animation sounds. */
@Composable
expect fun rememberReferenceAudioPlayer(): AudioPlayer
