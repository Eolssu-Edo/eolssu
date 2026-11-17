package com.ssafy.eolssu

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.ssafy.eolssu.core.diagnostics.*
import kotlin.math.abs

internal class QaInputObserver {
    private var interaction: QaInteraction? = null
    private var downAt = 0L
    private var startX = 0
    private var startY = 0
    private var maxPointers = 1
    private var previousTapAt = 0L
    private var previousX = 0
    private var previousY = 0

    fun touch(event: MotionEvent, view: View, dispatch: () -> Boolean): Boolean {
        if (UserActionLog.sink == null) return dispatch()
        val x = (1000 * event.x / view.width.coerceAtLeast(1)).toInt().coerceIn(0, 1000)
        val y = (1000 * event.y / view.height.coerceAtLeast(1)).toInt().coerceIn(0, 1000)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            interaction = UserActionLog.newInteraction(); downAt = event.eventTime
            startX = x; startY = y; maxPointers = 1
        }
        maxPointers = maxOf(maxPointers, event.pointerCount)
        val id = interaction ?: UserActionLog.newInteraction().copy(estimated = true)
        return UserActionLog.inInteraction(id) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) emit(QaKind.TOUCH_START, id, x, y)
            try { dispatch() } finally {
                when (event.actionMasked) {
                    MotionEvent.ACTION_UP -> {
                        val elapsed = (event.eventTime - downAt).coerceAtLeast(0)
                        val moved = abs(x - startX) + abs(y - startY) > 15
                        val gesture = when {
                            maxPointers > 1 -> QaGesture.MULTI_TOUCH
                            moved -> if (abs(y - startY) > abs(x - startX)) QaGesture.SCROLL else QaGesture.DRAG
                            elapsed >= 500 -> QaGesture.LONG_PRESS
                            event.eventTime - previousTapAt in 1..300 && abs(x - previousX) + abs(y - previousY) < 30 -> QaGesture.DOUBLE_TAP
                            else -> QaGesture.TAP
                        }
                        emit(QaKind.TOUCH_END, id, x, y, gesture, elapsed)
                        previousTapAt = if (moved) 0 else event.eventTime; previousX = x; previousY = y
                        interaction = null
                    }
                    MotionEvent.ACTION_CANCEL -> { emit(QaKind.TOUCH_CANCEL, id, x, y, QaGesture.CANCEL); interaction = null }
                }
            }
        }
    }
    fun key(event: KeyEvent, dispatch: () -> Boolean): Boolean {
        if (UserActionLog.sink == null || event.action != KeyEvent.ACTION_UP) return dispatch()
        // No keycode, Unicode, IME buffer, clipboard or text is inspected or recorded.
        return UserActionLog.inInteraction(UserActionLog.newInteraction()) {
            UserActionLog.event(QaKind.KEY_ACTION, QaTarget.INPUT)
            dispatch()
        }
    }
    private fun emit(kind: QaKind, id: QaInteraction, x: Int, y: Int, gesture: QaGesture? = null, duration: Long? = null) {
        UserActionLog.emit(UserActionEvent(kind, QaTarget.UNIDENTIFIED, id, UserActionLog.screenId,
            UserActionLog.now(), gesture = gesture, durationMs = duration,
            x = x, y = y, dx = x - startX, dy = y - startY, pointers = maxPointers))
    }
}
