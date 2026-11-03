package com.ssafy.eolssu.feature.home

import androidx.compose.runtime.Composable

internal data class HomeEnvironment(val hasHover: Boolean = false, val reduceMotion: Boolean = false)

@Composable
internal expect fun rememberHomeEnvironment(): HomeEnvironment
