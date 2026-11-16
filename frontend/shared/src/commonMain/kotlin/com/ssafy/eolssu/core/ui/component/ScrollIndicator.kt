package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 세로로 스크롤되는 칸 오른쪽에 얇은 막대를 그려, 내용이 더 있고 지금 어디쯤인지 보이게 한다(S15P31E102-996).
 * 넘치는 내용이 없으면 그리지 않는다. [state]의 스크롤과 같은 칸(스크롤되지 않는 바깥 크기)에 붙인다.
 */
fun Modifier.verticalScrollIndicator(state: ScrollState, color: Color): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
    val viewport = size.height
    val width = 3.dp.toPx()
    val inset = 2.dp.toPx()
    val track = viewport - inset * 2
    val thumb = (track * viewport / (viewport + max)).coerceAtLeast(16.dp.toPx()).coerceAtMost(track)
    val top = inset + (track - thumb) * state.value / max
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width - inset, top),
        size = Size(width, thumb),
        cornerRadius = CornerRadius(width / 2),
    )
}

/** [verticalScrollIndicator]에 쓰는 기본 막대 색(먹색 40%). */
internal fun scrollIndicatorColor(text: Color): Color = text.copy(alpha = 0.4f)
