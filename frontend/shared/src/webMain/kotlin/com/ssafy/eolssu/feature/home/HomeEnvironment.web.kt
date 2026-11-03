package com.ssafy.eolssu.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import org.w3c.dom.events.Event

@Composable
internal actual fun rememberHomeEnvironment(): HomeEnvironment {
    val hover = remember { window.matchMedia("(hover: hover)") }
    val motion = remember { window.matchMedia("(prefers-reduced-motion: reduce)") }
    var environment by remember { mutableStateOf(HomeEnvironment(hover.matches, motion.matches)) }
    DisposableEffect(hover, motion) {
        val update: (Event) -> Unit = { environment = HomeEnvironment(hover.matches, motion.matches) }
        hover.addEventListener("change", update)
        motion.addEventListener("change", update)
        onDispose {
            hover.removeEventListener("change", update)
            motion.removeEventListener("change", update)
        }
    }
    return environment
}
