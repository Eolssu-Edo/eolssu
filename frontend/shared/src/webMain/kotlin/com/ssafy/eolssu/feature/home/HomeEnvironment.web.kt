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
internal actual fun PrepareHomeFonts() {
    val family = MaterialTheme.typography.bodyLarge.fontFamily as? FontListFontFamily ?: return
    val resolver = LocalFontFamilyResolver.current
    LaunchedEffect(family, resolver) {
        if (family.fonts.none { it.weight == FontWeight.Bold }) return@LaunchedEffect
        withFrameNanos { }
        for (weight in listOf(FontWeight.Bold, FontWeight.SemiBold, FontWeight.Normal)) {
            awaitBrowserIdle()
            resolver.resolve(family, fontWeight = weight)
        }
    }
}

/** Safari에는 `requestIdleCallback`이 없어 짧은 `setTimeout`으로 대신 기다린다. 지원 여부는 한 번만 확인한다. */
private val idleCallbackSupported: Boolean by lazy { supportsIdleCallback() }

private const val IDLE_FALLBACK_DELAY_MS = 100

private suspend fun awaitBrowserIdle() = suspendCancellableCoroutine<Unit> { continuation ->
    val resume = { continuation.resume(Unit) }
    if (idleCallbackSupported) {
        val handle = requestIdle(resume)
        continuation.invokeOnCancellation { cancelIdle(handle) }
    } else {
        val handle = requestTimeout(resume, IDLE_FALLBACK_DELAY_MS)
        continuation.invokeOnCancellation { cancelTimeout(handle) }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun supportsIdleCallback(): Boolean = js("typeof window.requestIdleCallback === 'function'")

@OptIn(ExperimentalWasmJsInterop::class)
private fun requestIdle(action: () -> Unit): Int = js("window.requestIdleCallback(action)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelIdle(handle: Int): Unit = js("window.cancelIdleCallback(handle)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun requestTimeout(action: () -> Unit, delayMs: Int): Int = js("window.setTimeout(action, delayMs)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun cancelTimeout(handle: Int): Unit = js("window.clearTimeout(handle)")
