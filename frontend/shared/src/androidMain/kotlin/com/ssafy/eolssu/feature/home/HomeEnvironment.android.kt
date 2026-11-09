package com.ssafy.eolssu.feature.home

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberHomeEnvironment() = HomeEnvironment()

@Composable
internal actual fun rememberHomeAppExit(): (() -> Unit)? {
    val activity = LocalActivity.current
    return remember(activity) { activity?.let { { it.finish() } } }
}
