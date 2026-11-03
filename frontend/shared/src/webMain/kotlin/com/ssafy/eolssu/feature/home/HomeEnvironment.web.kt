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
    val motion = remember { window.matchMedia("(prefers-reduced-motion: reduce)") }
    var environment by remember { mutableStateOf(HomeEnvironment(motion.matches)) }
    DisposableEffect(motion) {
        val update: (Event) -> Unit = { environment = HomeEnvironment(motion.matches) }
        motion.addEventListener("change", update)
        onDispose {
            motion.removeEventListener("change", update)
        }
    }
    return environment
}
