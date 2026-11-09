package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * 산길 지도 뒤의 수묵 산수 배경. 한지 위에 붉은 해, 먼 산, 큰 산, 안개, 앞 언덕 순서로 겹친다.
 * 큰 산 꼭대기는 [summit](카드 안 상대 위치) 바로 위에 두어 마지막 단계가 산 정상에 선다. 꾸밈이라 읽지 않는다.
 *
 * @param ink 먹색. 겹마다 옅게 써서 농담을 낸다.
 * @param paper 바탕 한지 색. 안개도 이 색으로 칠한다.
 * @param grass 앞 언덕에 살짝 섞는 연두.
 * @param sun 해 색.
 */
internal fun DrawScope.drawInkScenery(summit: Offset, ink: Color, paper: Color, grass: Color, sun: Color) {
    val w = size.width
    val h = size.height
    drawCircle(sun.copy(alpha = 0.55f), radius = 13.dp.toPx(), center = Offset(w * 0.87f, h * 0.20f))

    // 먼 산: 낮고 길게 이어진 옅은 능선
    drawPath(
        ridge(size) { x -> 0.44f + 0.09f * sin(x * 7f + 0.8f) + 0.05f * sin(x * 17f) + brush(x) },
        Brush.verticalGradient(0f to ink.copy(alpha = 0.10f), 1f to ink.copy(alpha = 0f), startY = h * 0.3f, endY = h * 0.8f),
    )

    // 큰 산: 꼭대기와 양 어깨의 곁봉우리. 세 봉우리 중 가장 높은 선이 능선이 된다.
    val peakX = summit.x
    val peakY = summit.y - 0.08f
    val peaks = listOf(Offset(peakX, peakY), Offset(peakX - 0.36f, peakY + 0.26f), Offset(peakX + 0.27f, peakY + 0.20f))
    val mountain: (Float) -> Float = { x ->
        peaks.minOf { it.y + 1.1f * abs(x - it.x).pow(1.15f) } + 2.2f * brush(x) * (0.3f + abs(x - peakX))
    }
    drawPath(
        ridge(size, mountain),
        Brush.verticalGradient(0f to ink.copy(alpha = 0.22f), 0.5f to ink.copy(alpha = 0.07f), 1f to ink.copy(alpha = 0.02f), startY = h * peakY, endY = h),
    )
    drawPath(edge(size, mountain), ink.copy(alpha = 0.30f), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))

    // 안개: 산허리를 가로지르며 양 끝이 번지는 한지 색 띠
    mist(paper, Offset(w * 0.32f, h * 0.36f), w * 0.34f, h * 0.06f)
    mist(paper, Offset(w * 0.72f, h * 0.63f), w * 0.40f, h * 0.07f)

    // 앞 언덕
    drawPath(
        ridge(size) { x -> 0.82f + 0.05f * sin(x * 5f + 2f) + 0.4f * brush(x) },
        Brush.verticalGradient(0f to grass.copy(alpha = 0.30f), 1f to ink.copy(alpha = 0.06f), startY = h * 0.76f, endY = h),
    )

}

/** 붓으로 그은 듯 능선이 고르지 않게 떨리는 정도(카드 높이 비율). */
private fun brush(x: Float): Float = 0.012f * sin(x * 43f) + 0.007f * sin(x * 97f + 1.3f)

/** [top] 함수(가로 0~1 → 세로 0~1)를 윗선으로 하고 바닥까지 채운 영역. */
private fun ridge(size: Size, top: (Float) -> Float): Path = Path().apply {
    moveTo(0f, size.height)
    var x = 0f
    while (x <= 1.0001f) {
        lineTo(x * size.width, top(x).coerceIn(0f, 1f) * size.height)
        x += RidgeStep
    }
    lineTo(size.width, size.height)
    close()
}

/** [top]의 윗선만. 화면 아래로 내려간 부분은 그리지 않는다. */
private fun edge(size: Size, top: (Float) -> Float): Path = Path().apply {
    var started = false
    var x = 0f
    while (x <= 1.0001f) {
        val y = top(x)
        if (y < 0.95f) {
            if (started) lineTo(x * size.width, y * size.height) else moveTo(x * size.width, y * size.height)
            started = true
        } else {
            started = false
        }
        x += RidgeStep
    }
}

private fun DrawScope.mist(paper: Color, center: Offset, halfWidth: Float, halfHeight: Float) {
    scale(scaleX = halfWidth / halfHeight, scaleY = 1f, pivot = center) {
        drawCircle(
            Brush.radialGradient(0f to paper.copy(alpha = 0.92f), 0.6f to paper.copy(alpha = 0.6f), 1f to paper.copy(alpha = 0f), center = center, radius = halfHeight),
            radius = halfHeight,
            center = center,
        )
    }
}

private const val RidgeStep = 0.01f
