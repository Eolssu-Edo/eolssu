package com.ssafy.eolssu.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.events.Event
import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

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

@Composable
internal actual fun rememberHomeAppExit(): (() -> Unit)? = null

@Composable
internal actual fun PrepareHomeFonts(expanded: Boolean) {
    if (!expanded) return
    val family = MaterialTheme.typography.bodyLarge.fontFamily as? FontListFontFamily ?: return
    val resolver = LocalFontFamilyResolver.current
    LaunchedEffect(family, resolver) {
        if (!supportsIdleCallback() || family.fonts.none { it.weight == FontWeight.Bold }) return@LaunchedEffect
        withFrameNanos { }
        delay(200)
        for (weight in listOf(FontWeight.Bold, FontWeight.SemiBold, FontWeight.Normal)) {
            awaitBrowserIdle()
            resolver.resolve(family, fontWeight = weight)
        }
    }
}

private suspend fun awaitBrowserIdle() = suspendCancellableCoroutine<Unit> { continuation ->
    val handle = requestIdle { continuation.resume(Unit) }
    continuation.invokeOnCancellation { cancelIdle(handle) }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun supportsIdleCallback(): Boolean = js("typeof window.requestIdleCallback === 'function'")

@OptIn(ExperimentalWasmJsInterop::class)
private fun requestIdle(action: () -> Unit): Int = js("window.requestIdleCallback(action)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelIdle(handle: Int): Unit = js("window.cancelIdleCallback(handle)")
