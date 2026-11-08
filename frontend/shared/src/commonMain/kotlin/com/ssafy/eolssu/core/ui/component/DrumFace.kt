package com.ssafy.eolssu.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * 삼태극 북. 연습 속도 슬라이더·녹음 재생 위치 슬라이더의 손잡이와 3·2·1 카운트다운 테두리에만 쓴다.
 * 스위치·일반 버튼·오류 아이콘·로딩에는 쓰지 않는다.
 */

/**
 * 슬라이더 손잡이: 정면에서 본 북. 끄거나 누르고 있으면([held]) 조금 커지고, 쓸 수 없으면([enabled] false) 흐리게 그린다.
 */
@Composable
fun DrumThumb(held: Boolean, modifier: Modifier = Modifier, size: Dp = DrumThumbSize, enabled: Boolean = true) {
    val scale by animateFloatAsState(if (held && enabled) 1.12f else 1f, label = "drumThumbScale")
    Canvas(
        modifier.size(size).scale(scale)
            .then(if (enabled) Modifier.shadow(3.dp, CircleShape) else Modifier.alpha(0.4f)),
    ) { drawDrum() }
}

/** 손잡이 기본 크기. */
val DrumThumbSize = 30.dp

/**
 * 북 면: 단청 빛깔로 칸을 나눈 테두리, 가죽을 고정한 놋쇠 징, 크림빛 가죽, 가운데 삼태극(빨강 위 오른쪽 · 파랑 아래 · 노랑 왼쪽).
 * 삼태극은 반지름의 절반인 반원 셋으로 원을 세 쉼표 모양으로 나눈 것이다.
 *
 * @param bandRatio 테두리 두께(바깥 반지름 대비).
 * @param taegeukAlpha 삼태극 불투명도. 위에 글자를 올릴 때(카운트다운 숫자) 0이나 아주 옅게 둔다.
 */
fun DrawScope.drawDrum(bandRatio: Float = 0.24f, taegeukAlpha: Float = 1f) {
    val c = center
    val outer = size.minDimension / 2
    val face = outer * (1 - bandRatio)
    val band = outer - face
    // 테두리: 단청 세 빛깔을 번갈아 12칸으로 칠하고 칸 사이와 안팎에 먹선을 긋는다.
    val bandRadius = face + band / 2
    val segments = 12
    for (i in 0 until segments) {
        drawArc(
            DancheongColors[i % DancheongColors.size],
            startAngle = -90f + i * 360f / segments,
            sweepAngle = 360f / segments,
            useCenter = false,
            topLeft = Offset(c.x - bandRadius, c.y - bandRadius),
            size = Size(bandRadius * 2, bandRadius * 2),
            style = Stroke(band),
        )
    }
    // 먹선 굵기는 큰 북(카운트다운)에서도 손잡이와 비슷한 느낌이 되도록 4dp를 넘기지 않는다.
    val line = (outer * 0.05f).coerceAtMost(4.dp.toPx())
    for (i in 0 until segments) {
        val angle = (-90.0 + i * 360.0 / segments) * PI / 180
        drawLine(DrumInk, c + polar(face, angle), c + polar(outer, angle), strokeWidth = line * 0.7f)
    }
    drawCircle(DrumInk, radius = outer - line / 2, center = c, style = Stroke(line))
    drawCircle(DrumSkin, radius = face, center = c)
    drawCircle(DrumInk, radius = face, center = c, style = Stroke(line * 0.8f))
    // 가죽 둘레의 징.
    for (i in 0 until segments) {
        val angle = (-75.0 + i * 360.0 / segments) * PI / 180
        drawCircle(DrumStud, radius = (outer * 0.045f).coerceAtMost(3.dp.toPx()), center = c + polar(face * 0.88f, angle))
    }
    if (taegeukAlpha <= 0f) return
    val r = face * 0.7f
    TaegeukColors.forEachIndexed { k, color ->
        val from = TaegeukStart + k * 120f
        val to = from + 120f
        val fromCenter = c + polar(r / 2, from * PI / 180)
        val toCenter = c + polar(r / 2, to * PI / 180)
        val lobe = Path().apply {
            moveTo(c.x, c.y)
            arcTo(Rect(fromCenter, r / 2), from + 180f, 180f, false)
            arcTo(Rect(c, r), from, 120f, false)
            arcTo(Rect(toCenter, r / 2), to, -180f, false)
            close()
        }
        drawPath(lobe, color, alpha = taegeukAlpha)
    }
}

private fun polar(radius: Float, radians: Double) = Offset((radius * cos(radians)).toFloat(), (radius * sin(radians)).toFloat())

/** 삼태극 첫 칸이 시작하는 각도(도, 12시 방향). */
private const val TaegeukStart = -90f

private val TaegeukColors = listOf(Color(0xFFB52F3A), Color(0xFF2E4A9B), Color(0xFFE6B52F))
private val DancheongColors = listOf(Color(0xFFC0392B), Color(0xFF2F5D9E), Color(0xFFE2A83A))
private val DrumSkin = Color(0xFFF5E8CB)
private val DrumStud = Color(0xFFB8902F)
private val DrumInk = Color(0xFF4A3424)
