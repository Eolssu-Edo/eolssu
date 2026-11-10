package com.ssafy.eolssu.core.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent

internal val LocalPerformanceVisit = staticCompositionLocalOf<PerformanceVisit?> { null }

@Composable
internal fun Modifier.observeFirstDraw(phase: PerfPhase): Modifier {
    val visit = LocalPerformanceVisit.current ?: return this
    return drawWithContent {
        drawContent()
        visit.milestone(phase)
    }
}
