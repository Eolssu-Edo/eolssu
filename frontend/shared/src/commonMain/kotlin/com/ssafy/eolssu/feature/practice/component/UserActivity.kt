package com.ssafy.eolssu.feature.practice.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 안쪽에서 일어난 누름·끌기·휠 스크롤과 키 입력을 가로채지 않고 [onActivity]로 알린다(연습 시간의 '활동').
 * 마우스를 올려 움직이기만 하는 것은 활동으로 보지 않는다.
 */
fun Modifier.reportUserActivity(onActivity: () -> Unit): Modifier = this
    .pointerInput(onActivity) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val active = when (event.type) {
                    PointerEventType.Press, PointerEventType.Release, PointerEventType.Scroll -> true
                    PointerEventType.Move -> event.changes.any { it.pressed }
                    else -> false
                }
                if (active) onActivity()
            }
        }
    }
    .onPreviewKeyEvent {
        onActivity()
        false
    }
