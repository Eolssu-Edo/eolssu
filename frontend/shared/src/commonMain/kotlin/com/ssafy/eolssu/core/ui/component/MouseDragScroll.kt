package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 마우스로 끌어 가로 스크롤하기(Web · 데스크톱). Compose의 스크롤은 손가락 밀기만 받으므로 마우스 끌기를 [state]에 직접 옮긴다.
 * 손가락·펜 입력은 건드리지 않아 Android·iOS 터치 스크롤은 그대로다.
 *
 * 가로로 터치 슬롭을 넘게 끌면 시작하고, 세로로 먼저 넘으면 이번 끌기는 맡지 않는다. 끌기 시작하면 안쪽 단추의 누름은 취소되고,
 * 놓을 때의 속도로 관성 스크롤을 이어간다.
 *
 * @param reverseDirection `horizontalScroll(reverseScrolling = true)`처럼 오른쪽부터 읽는 스크롤이면 true.
 * @param enabled false면 아무것도 하지 않는다(사용자 스크롤을 막은 악보).
 * @param onDragStart 끌기를 시작할 때 부른다. 재생 따라가기를 멈추는 데 쓴다.
 */
fun Modifier.mouseDragScroll(
    state: ScrollableState,
    reverseDirection: Boolean = false,
    enabled: Boolean = true,
    onDragStart: () -> Unit = {},
): Modifier = if (!enabled) this else composed {
    val fling = ScrollableDefaults.flingBehavior()
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val sign = if (reverseDirection) 1f else -1f
    pointerInput(state, reverseDirection, fling) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // 오른쪽·가운데 단추 끌기는 맡지 않는다.
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val velocity = VelocityTracker().apply { addPointerInputChange(down) }
                var totalX = 0f
                var totalY = 0f
                var lastDx = 0f
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (change.isConsumed) break
                    // 놓는 순간도 기록해야 멈췄다가 놓을 때 관성이 남지 않는다.
                    velocity.addPointerInputChange(change)
                    if (!change.pressed) break
                    val dx = change.position.x - change.previousPosition.x
                    if (dx != 0f) lastDx = dx
                    if (!dragging) {
                        totalX += dx
                        totalY += change.position.y - change.previousPosition.y
                        val slop = viewConfiguration.touchSlop
                        if (abs(totalY) > slop && abs(totalY) > abs(totalX)) return@awaitEachGesture
                        if (abs(totalX) > slop) {
                            dragging = true
                            latestOnDragStart()
                            // 재생 따라가기 같은 진행 중인 스크롤을 멈추고 마우스를 따른다.
                            launch { state.stopScroll(MutatePriority.UserInput) }
                        }
                    }
                    if (dragging) {
                        state.dispatchRawDelta(sign * dx)
                        change.consume()
                    }
                }
                if (dragging) {
                    // 움직임 표본이 적으면 속도 추정이 끈 방향과 반대로 나올 수 있다. 그때는 반대쪽 끝까지 튀지 않게 관성을 주지 않는다.
                    val released = velocity.calculateVelocity().x
                    val speed = if (released * lastDx > 0f) sign * released else 0f
                    launch { state.scroll(MutatePriority.UserInput) { with(fling) { performFling(speed) } } }
                }
            }
        }
    }
}
