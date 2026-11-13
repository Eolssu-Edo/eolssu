package com.ssafy.eolssu.core.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * 로그인 보드의 겹친 산 능선(`.mtn-wrap`, 보드 390×210 좌표)을 이 영역 바닥에 210dp 높이로 그린다. 꾸밈이라 읽지 않는다.
 * 능선마다 위에서 아래로 옅어지는 [color] 먹색이고 전체 불투명도는 0.7이다. 보드의 흐림 효과는 넣지 않았다.
 * 로그인 화면(휴대폰 배치)과 홈 카드 화면 아래에서 쓴다.
 * [lifts]는 뒤에서 앞 순서로 각 능선을 보드 좌표만큼 위로 올린다. 비우면 보드 그대로다.
 */
internal fun Modifier.mountainRidges(color: Color, lifts: List<Float> = emptyList()): Modifier = drawBehind {
    val height = MountainHeight.toPx()
    val bottom = size.height
    val sx = size.width / ViewWidth
    val sy = height / ViewHeight
    Ridges.forEachIndexed { ridge, (heights, opacity) ->
        val originY = bottom - height - lifts.getOrElse(ridge) { 0f } * sy
        val path = Path().apply {
            moveTo(0f, bottom)
            heights.forEachIndexed { index, y -> lineTo(index * RidgeStep * sx, originY + y * sy) }
            lineTo(size.width, bottom)
            close()
        }
        // SVG 그라디언트는 능선마다 자기 높이(꼭대기~바닥)에 맞춰진다.
        drawPath(
            path,
            Brush.verticalGradient(
                0f to color.copy(alpha = 0.55f),
                0.55f to color.copy(alpha = 0.12f),
                1f to color.copy(alpha = 0f),
                startY = originY + heights.min() * sy,
                endY = bottom,
            ),
            alpha = opacity * 0.7f,
        )
    }
}

private val MountainHeight = 210.dp
private const val ViewWidth = 390f
private const val ViewHeight = 210f
private const val RidgeStep = 6f

/** 뒤에서 앞으로: x는 0부터 6씩, y는 보드 좌표. 앞 능선일수록 진하다. */
private val Ridges: List<Pair<FloatArray, Float>> = listOf(
    floatArrayOf(72.7f, 73.3f, 73.6f, 73.9f, 74.8f, 76.3f, 78.3f, 80f, 81f, 80.9f, 79.8f, 78.3f, 77.2f, 77.4f, 79.2f, 82.7f, 87.3f, 92.2f, 96.6f, 100.1f, 102.6f, 104.4f, 106.1f, 108f, 110f, 111.8f, 112.6f, 112f, 109.7f, 105.7f, 100.9f, 96.1f, 92.1f, 89.5f, 88.3f, 88f, 87.9f, 87.5f, 86.5f, 84.9f, 83.2f, 81.9f, 81.4f, 81.5f, 81.9f, 81.7f, 80.3f, 77.6f, 73.8f, 69.5f, 65.6f, 63f, 62.2f, 63f, 65.1f, 67.7f, 70.2f, 72.5f, 74.7f, 77.3f, 80.6f, 84.8f, 89.8f, 94.8f, 99f, 101.7f) to 0.4f,
    floatArrayOf(153.6f, 154.1f, 154.1f, 154.3f, 155f, 156.2f, 157.6f, 158.3f, 157.8f, 155.4f, 151.3f, 145.8f, 139.9f, 134.4f, 130.1f, 127.2f, 125.3f, 124f, 122.7f, 121.3f, 119.8f, 118.7f, 118.5f, 119.6f, 122f, 125.1f, 128.2f, 130.5f, 131.3f, 130.7f, 129.1f, 127.3f, 126.1f, 126.1f, 127.2f, 129.2f, 131.5f, 133.7f, 135.4f, 137f, 138.9f, 141.6f, 145.5f, 150.3f, 155.5f, 160.2f, 163.5f, 164.9f, 164.4f, 162.3f, 159.6f, 156.8f, 154.5f, 152.8f, 151.2f, 149.4f, 146.9f, 143.8f, 140.5f, 137.6f, 135.7f, 135.2f, 136f, 137.5f, 138.8f, 139.2f) to 0.64f,
    floatArrayOf(173.7f, 175.8f, 178.7f, 182.7f, 187.1f, 191.3f, 194.2f, 195.4f, 194.5f, 191.9f, 188.3f, 184.5f, 181.2f, 178.6f, 176.4f, 174.2f, 171.6f, 168.4f, 164.9f, 161.7f, 159.4f, 158.5f, 159f, 160.5f, 162.3f, 163.3f, 163.1f, 161.4f, 158.7f, 155.6f, 152.8f, 150.8f, 149.8f, 149.5f, 149.4f, 149.1f, 148.4f, 147.8f, 147.8f, 149.2f, 152.2f, 156.9f, 162.7f, 168.8f, 174.1f, 178.1f, 180.5f, 181.6f, 182.1f, 182.5f, 183.2f, 184.1f, 184.9f, 185.1f, 184.2f, 182.4f, 180f, 177.7f, 176.4f, 176.5f, 177.9f, 180.1f, 182.3f, 183.7f, 183.7f, 182.2f) to 0.88f,
)
