package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.ssafy.eolssu.core.diagnostics.*
import kotlin.math.abs

/** Dialogs own a separate Android window. Observe its Compose passes without consuming any change. */
internal fun Modifier.qaModalInput(): Modifier = if (UserActionLog.sink == null) this else pointerInput(Unit) {
    var interaction: QaInteraction? = null
    var startX = 0
    var startY = 0
    var maxPointers = 1
    var start = 0L
    try {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.any { it.pressed }
                val point = event.changes.firstOrNull()?.position
                val x = ((point?.x ?: 0f) * 1000 / size.width.coerceAtLeast(1)).toInt()
                val y = ((point?.y ?: 0f) * 1000 / size.height.coerceAtLeast(1)).toInt()
                if (pressed && interaction == null) {
                    interaction = UserActionLog.newInteraction()
                    startX = x; startY = y; start = UserActionLog.now(); maxPointers = 1
                    UserActionLog.event(QaKind.TOUCH_START, QaTarget.MODAL_INPUT, interaction = interaction!!)
                }
                maxPointers = maxOf(maxPointers, event.changes.count { it.pressed })
                UserActionLog.pointerScope(interaction)
                // Child click handlers execute between Initial and Final; never consume or redispatch input.
                awaitPointerEvent(PointerEventPass.Final)
                UserActionLog.pointerScope(null)
                if (!pressed && interaction != null) {
                    val elapsed = UserActionLog.now() - start
                    val gesture = when {
                        maxPointers > 1 -> QaGesture.MULTI_TOUCH
                        abs(y - startY) > 15 -> QaGesture.SCROLL
                        abs(x - startX) > 15 -> QaGesture.DRAG
                        elapsed >= 500 -> QaGesture.LONG_PRESS
                        else -> QaGesture.TAP
                    }
                    UserActionLog.emit(UserActionEvent(QaKind.TOUCH_END, QaTarget.MODAL_INPUT, interaction!!,
                        UserActionLog.screenId, UserActionLog.now(), durationMs = elapsed, gesture = gesture,
                        x = x, y = y, dx = x - startX, dy = y - startY, pointers = maxPointers))
                    interaction = null
                }
            }
        }
    } finally {
        UserActionLog.pointerScope(null)
        interaction?.let { UserActionLog.event(QaKind.TOUCH_CANCEL, QaTarget.MODAL_INPUT, QaOutcome.CANCELLED, it) }
    }
}

@androidx.compose.runtime.Composable
internal fun QaModalObservation() {
    if (UserActionLog.sink == null) return
    androidx.compose.runtime.DisposableEffect(Unit) {
        UserActionLog.event(QaKind.MODAL_OPEN, QaTarget.DIALOG)
        onDispose { UserActionLog.event(QaKind.MODAL_CLOSE, QaTarget.DIALOG) }
    }
}
