package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_reading_order_caption
import eolssu.shared.generated.resources.guide_reading_order_column
import eolssu.shared.generated.resources.guide_reading_order_figure
import eolssu.shared.generated.resources.score_column_start
import org.jetbrains.compose.resources.stringResource

/*
 * 1 읽는 순서(`.rd-board`): 예시 세 각(오른쪽 → 왼쪽). 각 안의 실선을 따라 내려가고, 각이 끝나면 점선을 따라 왼쪽 각 위로 넘어간다.
 * 움직일 때는 읽는 순서대로 선이 검게 채워지고 선이 지나는 칸의 율명·기호가 빛난다(GIF처럼 되풀이).
 * 움직이지 않을 때는 순서 선이 모두 채워진 모습이다.
 */

/** 한 바퀴의 구간 시간(초): 각 1 ↓ · 넘어가기 · 각 2 ↓ · 넘어가기 · 각 3 ↓. 끝에 다 읽은 모습을 [OrderHold]초 보이고 마지막 [OrderFade]초에 흐려진다. */
internal val OrderDurations: List<Float> = listOf(2.1f, 0.8f, 2.1f, 0.8f, 2.1f)
internal const val OrderHold: Float = 1.4f
internal const val OrderFade: Float = 0.35f
internal val OrderCycle: Float = OrderDurations.sum() + OrderHold

/**
 * 읽는 순서 그림의 한 순간.
 * [progress]는 구간마다 채워진 정도(0~1, 넘어가기는 부드럽게 빨라졌다 느려진다), [active]는 지금 채워지는 구간(없으면 -1),
 * [tip]은 지금 각 안의 선 끝이 내려온 정도(0~1), [alpha]는 채운 선의 불투명도다.
 */
internal data class OrderFrame(val progress: List<Float>, val active: Int, val tip: Float, val alpha: Float) {
    companion object {
        /** 멈춘 모습: 모두 채운 그림. */
        val Rest = OrderFrame(OrderDurations.map { 1f }, active = -1, tip = 0f, alpha = 1f)
    }
}

private fun easeInOut(x: Float): Float = if (x < 0.5f) 2 * x * x else 1 - (-2 * x + 2).let { it * it } / 2

/** 한 바퀴 안의 [seconds]초(바퀴를 넘으면 처음부터)에서의 그림. */
internal fun orderFrameAt(seconds: Float): OrderFrame {
    val t = ((seconds % OrderCycle) + OrderCycle) % OrderCycle
    var start = 0f
    var active = -1
    var tip = 0f
    val progress = OrderDurations.mapIndexed { k, d ->
        val q = ((t - start) / d).coerceIn(0f, 1f)
        if (t >= start && t < start + d) {
            active = k
            tip = q
        }
        start += d
        if (k % 2 == 1) easeInOut(q) else q
    }
    val fadeAt = OrderCycle - OrderFade
    val alpha = if (t > fadeAt) 1 - (t - fadeAt) / OrderFade else 1f
    return OrderFrame(progress, active, tip, alpha)
}

/* 그림 좌표(viewBox 300 × 256). 각의 왼쪽 x는 오른쪽 각(각 1)부터. */
private val ColumnLeft = listOf(206f, 118f, 30f)
private const val ColumnWidth = 64f
private const val Top = 28f
private const val CellHeight = 56f
private const val Bottom = Top + CellHeight * 3
private const val ViewWidth = 300f
private const val ViewHeight = Bottom + 60f

/** 각마다 위에서부터 칸의 음(한 칸에 둘이면 반씩 나눔). */
private val OrderCells: List<List<List<String>>> = listOf(
    listOf(listOf("黃"), listOf("太"), listOf("ㅡ")),
    listOf(listOf("仲"), listOf("林", "南"), listOf("△")),
    listOf(listOf("潢"), listOf("林"), listOf("ㅡ")),
)

/** 각 안의 세로선 시작 · 끝 높이(화살촉은 각 아래 바깥에 놓아 글자를 가리지 않는다). */
private const val DownFrom = Top + 6
private const val DownTo = Bottom + 4

private fun centerX(c: Int) = ColumnLeft[c] + ColumnWidth / 2

/** 각 [i]의 아래에서 왼쪽 각 [i]+1 위로 넘어가는 곡선. */
private fun jumpPath(i: Int, u: (Float) -> Float): Path {
    val g = (ColumnLeft[i] + ColumnLeft[i + 1] + ColumnWidth) / 2
    val a = centerX(i)
    val b = centerX(i + 1)
    return Path().apply {
        moveTo(u(a), u(Bottom + 14))
        cubicTo(u(a), u(Bottom + 36), u(g), u(Bottom + 36), u(g), u(Bottom - 6))
        lineTo(u(g), u(Top + 16))
        cubicTo(u(g), u(Top - 22), u(b), u(Top - 22), u(b), u(Top + 2))
    }
}

/**
 * 1 읽는 순서 그림. [animate]이면 [OrderCycle]초마다 처음부터 되풀이해 움직이고, 거짓이면 다 채운 모습으로 둔다.
 * 휴대폰은 이 쪽이 보일 때만, 태블릿은 늘 움직인다. 기초부터 배우기에서 빌려 쓸 때는 움직이지 않는다.
 */
@Composable
internal fun ReadOrderBoard(modifier: Modifier = Modifier, animate: Boolean = false) {
    var seconds by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(animate) {
        seconds = -1f
        if (!animate) return@LaunchedEffect
        // 이 쪽에 들어올 때마다 처음부터
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds = (now - start) / 1_000_000_000f }
        }
    }
    val frame = if (seconds < 0f) OrderFrame.Rest else orderFrameAt(seconds)
    val colors = EolssuTheme.colors
    val serif = EolssuTheme.serif.yul
    val label = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val columnLabels = (1..3).map { stringResource(Res.string.guide_reading_order_column, it) }
    val startLabel = stringResource(Res.string.score_column_start)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Figure(ViewWidth, ViewHeight, maxWidth = 300.dp, description = stringResource(Res.string.guide_reading_order_figure)) {
            val d = draw
            val track = colors.line   // `.rd-trk` #D9D3C6
            // 각 상자(흰 바탕 · 옅은 테두리)와 칸 선
            ColumnLeft.forEach { x ->
                d.drawRoundRect(colors.surface, Offset(u(x), u(Top)), Size(u(ColumnWidth), u(CellHeight * 3)), CornerRadius(u(8f)))
                d.drawRoundRect(GuideFaint, Offset(u(x), u(Top)), Size(u(ColumnWidth), u(CellHeight * 3)), CornerRadius(u(8f)), style = Stroke(u(1f)))
                listOf(1, 2).forEach { r -> d.drawLine(colors.lineSoft, Offset(u(x), u(Top + CellHeight * r)), Offset(u(x + ColumnWidth), u(Top + CellHeight * r)), u(1f)) }
            }
            // 회색 길: 넘어가는 점선 · 각 안의 세로선과 화살촉
            val jumps = listOf(0, 1).map { i -> jumpPath(i) { v -> u(v) } }
            jumps.forEach { d.drawPath(it, track, style = Stroke(u(2f), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(4f), u(4f))))) }
            (0..2).forEach { c ->
                d.drawLine(track, Offset(u(centerX(c)), u(DownFrom)), Offset(u(centerX(c)), u(DownTo)), u(3f), cap = StrokeCap.Round)
                arrowHead(centerX(c), DownTo, 7.5f, track)
            }
            // 채워지는 선(읽는 순서: 각 1 ↓ · 넘어가기 · 각 2 ↓ · 넘어가기 · 각 3 ↓)
            val lit = colors.text.copy(alpha = frame.alpha)
            frame.progress.forEachIndexed { k, q ->
                if (q <= 0f) return@forEachIndexed
                if (k % 2 == 0) {
                    val x = centerX(k / 2)
                    d.drawLine(lit, Offset(u(x), u(DownFrom)), Offset(u(x), u(DownFrom + (DownTo - DownFrom) * q)), u(3f), cap = StrokeCap.Round)
                    if (q >= 1f) arrowHead(x, DownTo, 6f, lit)   // 화살촉은 그 각을 다 읽은 뒤에 검게
                } else {
                    val measure = PathMeasure().apply { setPath(jumps[k / 2], false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * q, part, true)
                    d.drawPath(part, lit, style = Stroke(u(2f), cap = StrokeCap.Round))
                }
            }
            // 율명 · 기호. 지금 지나는 칸의 글자가 빛난다
            val tipY = DownFrom + (DownTo - DownFrom) * frame.tip
            OrderCells.forEachIndexed { c, cells ->
                cells.forEachIndexed { r, notes ->
                    val y = Top + CellHeight * r
                    val cy = y + CellHeight / 2
                    val on = { y0: Float, y1: Float -> frame.active == c * 2 && tipY >= y0 && tipY < y1 }
                    val note = { t: String, baseline: Float, size: Float, glow: Boolean ->
                        val symbol = t == "ㅡ" || t == "△"
                        if (t == "△") restFill(centerX(c), baseline, size, colors.surface)
                        text(
                            t, centerX(c), baseline, serif, size,
                            color = if (symbol) colors.textMuted else colors.text,
                            halo = colors.surface, haloUnits = 7f,
                            glow = if (glow) colors.glow else null,
                        )
                    }
                    if (notes.size > 1) {
                        d.drawLine(
                            colors.lineSoft, Offset(u(ColumnLeft[c] + 10), u(cy)), Offset(u(ColumnLeft[c] + ColumnWidth - 10), u(cy)), u(1f),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(3f), u(3f))),
                        )
                        note(notes[0], cy - 12, 17f, on(y, y + CellHeight / 2))
                        note(notes[1], cy + 16, 17f, on(y + CellHeight / 2, y + CellHeight))
                    } else {
                        note(notes[0], cy + 8, 24f, on(y, y + CellHeight))
                    }
                }
            }
            columnLabels.forEachIndexed { c, name ->
                text(name, centerX(c), Bottom + 54, label, 11f, if (frame.active == c * 2) colors.text else colors.textMuted)
            }
            d.drawRoundRect(colors.primary, Offset(u(centerX(0) - 20), u(2f)), Size(u(40f), u(18f)), CornerRadius(u(9f)))
            text(startLabel, centerX(0), 15f, label, 11f, colors.onPrimary)
        }
        GuideText(
            stringResource(Res.string.guide_reading_order_caption),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = colors.textMuted,
            boldColor = colors.text,
        )
    }
}

/**
 * 쉼(△) 글자의 바깥 삼각형 꼭짓점(그림 단위, 위 · 왼쪽 아래 · 오른쪽 아래).
 * 율명 글꼴(Noto Serif KR SemiBold) △ 글리프의 바깥 윤곽이다: 1000 단위 글자 칸에서 (500, 826) · (30, 12) · (970, 12).
 * 글자는 가운데 정렬이라 [x]가 글자 칸 가운데(500)이고, 높이는 [baseline]에서 위로 잰다. [size]는 글자 크기다.
 */
internal fun restTriangle(x: Float, baseline: Float, size: Float): List<Offset> {
    val k = size / 1000f
    return listOf(
        Offset(x, baseline - 826 * k),
        Offset(x - 470 * k, baseline - 12 * k),
        Offset(x + 470 * k, baseline - 12 * k),
    )
}

/** 쉼(△) 글자 안을 [color]로 채워 뒤의 세로선이 비치지 않게 한다. 글자 바로 앞에 그리고, 글자의 선은 그 위에 그대로 그린다. */
private fun FigureScope.restFill(x: Float, baseline: Float, size: Float, color: Color) {
    val (top, left, right) = restTriangle(x, baseline, size)
    val p = Path().apply {
        moveTo(u(top.x), u(top.y))
        lineTo(u(left.x), u(left.y))
        lineTo(u(right.x), u(right.y))
        close()
    }
    draw.drawPath(p, color)
}

/** 아래를 가리키는 화살촉. 끝점 [y]를 가운데로 [half]만큼 앞뒤 · 양옆으로 편다(SVG marker). */
private fun FigureScope.arrowHead(x: Float, y: Float, half: Float, color: Color) {
    val p = Path().apply {
        moveTo(u(x - half), u(y - half))
        lineTo(u(x + half), u(y - half))
        lineTo(u(x), u(y + half))
        close()
    }
    draw.drawPath(p, color)
}
