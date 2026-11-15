package com.ssafy.eolssu.feature.home

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberHomeEnvironment() = HomeEnvironment()

@Composable
internal actual fun PrepareHomeFonts(expanded: Boolean) = Unit

@Composable
internal actual fun rememberHomeAppExit(): (() -> Unit)? = null
