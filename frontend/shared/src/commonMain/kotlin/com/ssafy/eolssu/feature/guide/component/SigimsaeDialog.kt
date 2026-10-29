package com.ssafy.eolssu.feature.guide.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ssafy.eolssu.core.audio.SoundPlayer
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIconButton
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.guide.GraceBase
import com.ssafy.eolssu.feature.guide.GraceStep
import com.ssafy.eolssu.feature.guide.SigimsaeFigure
import com.ssafy.eolssu.feature.guide.SigimsaeItem
import com.ssafy.eolssu.feature.guide.gracePath
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_close
import eolssu.shared.generated.resources.guide_sigimsae_figure
import eolssu.shared.generated.resources.guide_sigimsae_legend_blown
import eolssu.shared.generated.resources.guide_sigimsae_legend_written
import eolssu.shared.generated.resources.guide_sigimsae_replay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/*
 * 시김새 창(`.sigm`): 기호 · 이름 · 설명 아래에 부는 모양을 붓으로 먹을 찍어 긋듯 그린다(와이어프레임 sigModalHtml · sigPlay · startWave).
 * 붓질: 끝이 흐린 가리개를 왼쪽 → 오른쪽으로 밀어 드러낸다. 처음엔 천천히 대고 뒤로 갈수록 흘러간다(SMIL keySplines .5 0 .35 1).
 * 먹: 가장자리를 한지 결처럼 살짝 흔들고(feDisplacementMap 대신 고정된 잡음으로 점을 옮긴다) 밑에 옅게 번진 먹 한 겹을 깐다.
 * 붓끝이 지나는 자리만 줄처럼 떨고, 지나간 자리와 다 그은 선은 떨지 않는다. 적힌 음 점선 · 눈금 · 한 음 칸은 먹 효과 없이 늘 보인다.
 * 소리: 붓끝이 지나는 높이를 따라가는 단소 소리(와이어프레임 playSig)를 미리 만든 파일로 붓질과 함께 튼다. 소리가 나기 시작하면 붓질을 시작한다.
 * '다시 듣기'는 소리와 붓질을 처음부터 다시 한다. 창을 닫거나 앱이 뒤로 가면 소리를 멈춘다.
 */

/** 붓질이 끝까지 가는 시간(초). 떨리는 선 · 시김새 소리도 이 시간표를 따른다. */
internal const val SigimsaeDrawSeconds = 2.16f

private val BrushEasing = CubicBezierEasing(0.5f, 0f, 0.35f, 1f)

/** 가리개(폭 80, 끝 12%가 흐림)의 왼쪽 x. 0초에 -80, 다 그으면 0이다. */
internal fun brushMaskX(seconds: Float): Float = -80f + 80f * BrushEasing.transform((seconds / SigimsaeDrawSeconds).coerceIn(0f, 1f))

/** [seconds]초에 붓끝이 닿은 x(가리개가 반쯤 비치는 자리). 다 그었으면 그림 밖이다. */
internal fun brushHead(seconds: Float): Float = if (seconds >= SigimsaeDrawSeconds) 999f else brushMaskX(seconds) + 75f

private const val WaveStep = 0.35f
private const val WaveLength = 2.6f
private const val WaveHz = 6f

/**
 * 떨리는 선에서 [i]번째 점(전체 [count]개, x는 [x])을 법선 방향으로 옮길 거리. 붓끝 둘레(폭 5)만 떨고 양 끝은 묶인 줄처럼 덜 떤다.
 */
internal fun waveOffset(i: Int, count: Int, x: Float, seconds: Float): Float {
    val tie = minOf(1f, i / 8f, (count - 1 - i) / 8f)
    val d = (x - brushHead(seconds)) / 5f
    val amp = 0.5f * exp(-(d * d)) * tie
    return amp * sin(i * WaveStep * 2 * PI.toFloat() / WaveLength - seconds * WaveHz * 2 * PI.toFloat())
}

/**
 * 처음 배우는 시김새의 창. 바깥을 누르거나 뒤로 가기 · Esc로 닫고, 열려 있는 동안 뒤 화면은 누를 수 없다.
 * 처음 초점은 닫기 단추에 두고, 닫은 뒤 초점은 연 쪽이 누른 줄로 돌려놓는다.
 */
@Composable
internal fun SigimsaeDialog(item: SigimsaeItem, hint: String?, onDismiss: () -> Unit) {
    val colors = EolssuTheme.colors
    val player = koinInject<SoundPlayer>()
    DisposableEffect(player) { onDispose { player.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.stop() }
    val closeFocus = remember { FocusRequester() }
    var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { closeFocus.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(20.dp)
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Column {
                Row(Modifier.padding(end = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SigimsaeGlyph(item, box = 56.dp, imageHeight = 32.dp, imageMaxWidth = 42.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            item.name,
                            Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        )
                        if (item.chip != null) SigimsaeChip(item.chip)
                    }
                }
                Text(
                    item.description,
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
                )
                Column(
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .hanjiGrain()
                        .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 12.dp),
                ) {
                    SigimsaeFigureView(item.figure, replay, stringResource(Res.string.guide_sigimsae_figure, item.name, item.description)) {
                        player.play(item.soundPath)
                    }
                    SigimsaeLegend(hint, Modifier.padding(top = 12.dp))
                }
                Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    ReplayButton(onClick = { replay++ })
                }
            }
            EolssuIconButton(
                EolssuIcons.Close,
                stringResource(Res.string.common_close),
                onDismiss,
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).focusRequester(closeFocus),
            )
        }
    }
}

/** 기호 칸(`.sig-glyph`): 회색 둥근 네모에 기호 그림. 기호가 둘(요성·농음)이면 나란히 둔다. */
@Composable
internal fun SigimsaeGlyph(item: SigimsaeItem, box: Dp, imageHeight: Dp, imageMaxWidth: Dp) {
    Row(
        Modifier.size(box).background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp)),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.images.forEach { SigimsaeImage(it, imageHeight, imageMaxWidth) }
    }
}

/** 정악 · 민속악 표시(`.sig-chip`). */
@Composable
internal fun SigimsaeChip(text: String) {
    val colors = EolssuTheme.colors
    Box(
        Modifier.height(20.dp).border(1.dp, colors.line, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 11.sp), color = colors.textMuted)
    }
}

/** 범례(`.sig-legend`): 점선 = 적힌 음, 굵은 선 = 부는 소리, 앞에서 스치는 시김새는 '가는 줄 한 칸 = 한 음'. */
@Composable
private fun SigimsaeLegend(hint: String?, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(18.dp, 2.dp)) {
                drawLine(
                    colors.lineStrong,
                    Offset(0f, size.height / 2),
                    Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
                )
            }
            Text(stringResource(Res.string.guide_sigimsae_legend_written), style = style, color = colors.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(18.dp, 2.5.dp).background(colors.text))
            Text(stringResource(Res.string.guide_sigimsae_legend_blown), style = style, color = colors.textMuted)
        }
        if (hint != null) Text(hint, style = style, color = colors.textMuted)
    }
}

/** '다시 듣기'(`.sigm-btn.play`): 녹색 단추. */
@Composable
private fun ReplayButton(onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = colors.green, contentColor = colors.onPrimary) {
        Row(Modifier.height(44.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(EolssuIcons.Restart, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.guide_sigimsae_replay), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

/* ───────── 부는 모양 그림 ───────── */

/** 그리기 전에 한 번 재 두는 모양: 소리 선의 점들(viewBox 단위)과 꼬리 테두리 점들, 찍을 점. */
private class FigureShape(
    val width: Float,
    val height: Float,
    val line: List<Offset>,
    val tail: List<Offset>?,
    val dots: List<Offset>,
)

private fun figureShape(figure: SigimsaeFigure): FigureShape = when (figure) {
    is SigimsaeFigure.Curve -> FigureShape(72f, 38f, samplePath(figure.curve, WaveStep), figure.tail?.let { samplePath(it, WaveStep) }, emptyList())
    is SigimsaeFigure.Grace -> {
        val (d, dots) = gracePath(figure.steps)
        FigureShape(72f, 48f, samplePath(d, WaveStep), null, dots.map { (x, y) -> Offset(x, y) })
    }
}

/** SVG path를 [step] 간격의 점으로 편다(와이어프레임 samplePath). */
private fun samplePath(d: String, step: Float): List<Offset> {
    val measure = PathMeasure()
    measure.setPath(PathParser().parsePathString(d).toPath(), false)
    val length = measure.length
    val points = mutableListOf<Offset>()
    var s = 0f
    while (s < length) {
        points += measure.getPosition(s)
        s += step
    }
    points += measure.getPosition(length)
    return points
}

/**
 * 부는 모양 그림(`.sig-curve`, 최대 260dp). [replay]가 바뀔 때마다 [onStart](소리 틀기)가 돌아온 뒤 붓질을 처음부터 다시 긋는다.
 * 소리를 틀지 못해도 붓질은 한다.
 */
@Composable
private fun SigimsaeFigureView(figure: SigimsaeFigure, replay: Int, description: String, onStart: suspend () -> Unit) {
    val colors = EolssuTheme.colors
    val shape = remember(figure) { figureShape(figure) }
    val tailPath = remember(figure) { (figure as? SigimsaeFigure.Curve)?.tail?.let { PathParser().parsePathString(it).toPath() } }
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(replay) {
        seconds = 0f
        onStart()
        val start = withFrameNanos { it }
        while (seconds < SigimsaeDrawSeconds) {
            seconds = (withFrameNanos { it } - start) / 1_000_000_000f
        }
        seconds = SigimsaeDrawSeconds
    }
    Figure(shape.width, shape.height, 260.dp, description) {
        val d = draw
        // 늘 보이는 바탕: 한 음 칸(앞에서 스치는 시김새) · 적힌 음 점선 · 적힌 길이 끝 눈금
        val faint = colors.lineStrong
        when (figure) {
            is SigimsaeFigure.Grace -> {
                (1..3).forEach { k ->
                    val y = u(GraceBase - k * GraceStep)
                    d.drawLine(faint, Offset(u(4f), y), Offset(u(68f), y), strokeWidth = u(0.4f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(0.7f), u(0.7f))))
                }
                d.drawLine(faint, Offset(u(4f), u(GraceBase)), Offset(u(68f), u(GraceBase)), strokeWidth = u(0.6f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(1f), u(1f))))
            }
            is SigimsaeFigure.Curve -> {
                val end = figure.end ?: 68f
                d.drawLine(faint, Offset(u(4f), u(24f)), Offset(u(end), u(24f)), strokeWidth = u(0.6f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(1f), u(1f))))
                if (figure.end != null) d.drawLine(colors.textMuted, Offset(u(end), u(18f)), Offset(u(end), u(30f)), strokeWidth = u(0.5f))
            }
        }
        val drawing = seconds < SigimsaeDrawSeconds
        if (drawing) {
            d.drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, d.size), Paint()) }
        }
        inkedStroke(shape, tailPath, if (drawing) seconds else null, colors.text, colors.surface)
        if (drawing) {
            // 붓질 가리개: 왼쪽은 그대로 두고 오른쪽 끝 12%에서 흐려져 그 너머는 가린다
            val x = brushMaskX(seconds)
            d.drawRect(
                Brush.horizontalGradient(0f to Color.White, 0.88f to Color.White, 1f to Color.Transparent, startX = u(x), endX = u(x + 80f)),
                blendMode = BlendMode.DstIn,
            )
            d.drawIntoCanvas { it.restore() }
        }
    }
}

/** 먹으로 그은 소리 선 · 꼬리 · 점. [seconds]가 있으면 붓끝 둘레를 떨게 한다. */
private fun FigureScope.inkedStroke(shape: FigureShape, tailPath: Path?, seconds: Float?, ink: Color, paper: Color) {
    val d = draw
    val line = Path()
    val n = shape.line.size
    shape.line.forEachIndexed { i, p ->
        var q = p + inkGrain(p)
        if (seconds != null) {
            val a = shape.line[maxOf(0, i - 1)]
            val b = shape.line[minOf(n - 1, i + 1)]
            val nx = a.y - b.y
            val ny = b.x - a.x
            val l = hypot(nx, ny).takeIf { it > 0f } ?: 1f
            val o = waveOffset(i, n, p.x, seconds)
            q += Offset(nx / l * o, ny / l * o)
        }
        if (i == 0) line.moveTo(u(q.x), u(q.y)) else line.lineTo(u(q.x), u(q.y))
    }
    val tail = shape.tail?.let { points ->
        Path().apply {
            points.forEachIndexed { i, p ->
                val q = p + inkGrain(p)
                if (i == 0) moveTo(u(q.x), u(q.y)) else lineTo(u(q.x), u(q.y))
            }
            close()
        }
    }
    val tailBrush = tailPath?.getBounds()?.let { b ->
        Brush.horizontalGradient(0f to ink, 0.45f to ink, 1f to ink.copy(alpha = 0.15f), startX = u(b.left), endX = u(b.right))
    }
    // 옅게 번진 먹(가우스 흐림 대신 굵고 옅은 선 두 겹)
    listOf(3.4f to 0.05f, 2.2f to 0.09f).forEach { (width, alpha) ->
        d.drawPath(line, ink.copy(alpha = alpha), style = Stroke(u(width), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (tail != null && tailBrush != null) d.drawPath(tail, tailBrush, alpha = alpha, style = Stroke(u(width - 1.1f), join = StrokeJoin.Round))
    }
    d.drawPath(line, ink, style = Stroke(u(1.1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (tail != null && tailBrush != null) d.drawPath(tail, tailBrush)
    shape.dots.forEach { p ->
        d.drawCircle(ink, u(1.8f), Offset(u(p.x), u(p.y)))
        d.drawCircle(paper, u(1.8f), Offset(u(p.x), u(p.y)), style = Stroke(u(0.5f)))
    }
}

/** 한지 결처럼 거친 먹 가장자리: 자리마다 정해진 잡음으로 점을 ±0.23 단위 안에서 옮긴다(feDisplacementMap scale .65보다 조금 약하게. 점을 옮기면 가장자리뿐 아니라 선 전체가 휘기 때문). */
private fun inkGrain(p: Offset): Offset {
    val dx = (fractalNoise(p.x * 1.1f, p.y * 1.1f, 11) - 0.5f) * 0.45f
    val dy = (fractalNoise(p.x * 1.1f, p.y * 1.1f, 23) - 0.5f) * 0.45f
    return Offset(dx, dy)
}

private fun fractalNoise(x: Float, y: Float, seed: Int): Float = (valueNoise(x, y, seed) + 0.5f * valueNoise(x * 2f, y * 2f, seed + 7)) / 1.5f

private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = smooth(x - x0)
    val fy = smooth(y - y0)
    val top = lerp(lattice(x0, y0, seed), lattice(x0 + 1, y0, seed), fx)
    val bottom = lerp(lattice(x0, y0 + 1, seed), lattice(x0 + 1, y0 + 1, seed), fx)
    return lerp(top, bottom, fy)
}

private fun lattice(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 982451653
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xffff) / 65535f
}

private fun smooth(t: Float): Float = t * t * (3 - 2 * t)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * 먹 붓질 밑에 깐 한지: 바탕색은 그대로 두고 고운 티와 이리저리 구부러진 닥 섬유만 아주 옅게 얹는다.
 * 자리는 고정된 씨앗으로 정해 다시 그려도 같다.
 */
private fun Modifier.hanjiGrain(): Modifier = drawBehind {
    val random = Random(7)
    val speck = Color(0xFF6B6354)
    val fiber = Color(0xFF807866)
    val area = size.width * size.height / (density * density)
    repeat((area / 18f).toInt()) {
        drawCircle(speck.copy(alpha = 0.05f + random.nextFloat() * 0.08f), radius = (0.3f + random.nextFloat() * 0.5f) * density, center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
    }
    repeat((area / 1800f).toInt().coerceAtLeast(6)) {
        val start = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        val reach = (16f + random.nextFloat() * 40f) * density
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(
                start.x + (random.nextFloat() - 0.5f) * reach, start.y + (random.nextFloat() - 0.5f) * reach,
                start.x + (random.nextFloat() - 0.5f) * reach, start.y + (random.nextFloat() - 0.5f) * reach,
                start.x + (random.nextFloat() - 0.5f) * reach, start.y + (random.nextFloat() - 0.5f) * reach,
            )
        }
        drawPath(path, fiber.copy(alpha = 0.10f), style = Stroke(min(0.6f * density, 1.2f)))
    }
}
