package com.ssafy.eolssu.feature.result.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * 연주 분석 대기 그림의 붓 일원상(S15P31E102-760). 미리 그린 그림을 돌리지 않고, 붓털 여러 가닥이 각자 먹을 머금고
 * 7시에서 시계 방향으로 돌며 화면에 그려 간다. 시작은 둥근 붓머리로 눌러 앉히고, 꼬리 구간(4시 → 6시)에서 붓을 들며
 * 가장자리 털부터 먹이 말라 갈라진다. 값은 웹 시안에서 고른 값(끝 6시 · 꼬리 시작 4시 · 굵기 1.40배 · 0.9초)이다.
 */

private const val StartClockDeg = 210.0 // 7시: 12시에서 시계 방향 210°
private const val BristleCount = 46
private const val InkThreshold = 0.2
private const val SampleStepPx = 0.7

/** 웹 시안과 같은 붓결을 내려고 쓰는 32비트 의사 난수(mulberry32). */
internal class Mulberry32(private var seed: Int) {
    fun next(): Double {
        seed += 0x6D2B79F5
        var t = (seed xor (seed ushr 15)) * (1 or seed)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
    }
}

/** 붓 일원상 한 획의 모양. 그림 한 변이 [sizePx]일 때 붓털마다 위치·먹 양을 계산한다. 화면과 무관한 계산만 둔다. */
internal class EnsoBrush(
    endClockDeg: Double = 180.0,
    tailDeg: Double = 60.0,
    private val weight: Double = 1.4,
    seed: Int = 11,
) {
    class Bristle(
        val u: Double,
        val load: Double,
        val drain: Double,
        val coarse: DoubleArray,
        val fine: DoubleArray,
        val jit: DoubleArray,
    )

    data class Point(val x: Double, val y: Double, val nx: Double, val ny: Double, val halfWidth: Double)

    val bristles: List<Bristle>
    private val wobA: Double
    private val wobB: Double
    private val wobC: Double

    /** 7시에서 시계 방향으로 끝 위치까지 도는 각도. */
    val sweepDeg = 360.0 + endClockDeg - StartClockDeg
    private val tailStart = 1.0 - tailDeg / sweepDeg

    init {
        val r = Mulberry32(seed * 7919)
        bristles = List(BristleCount) { i ->
            val u = -1.0 + 2.0 * (i + 0.5) / BristleCount + (r.next() - 0.5) * (1.4 / BristleCount)
            val load = 1.0 - r.next() * 0.15
            val drain = 0.55 + 0.75 * abs(u) + r.next() * 0.4 // 가장자리 털일수록 먹이 빨리 마른다
            Bristle(
                u = u,
                load = load,
                drain = drain,
                coarse = DoubleArray(34) { r.next() }, // 획 방향으로 길게 이어지는 갈라짐
                fine = DoubleArray(150) { r.next() }, // 잔 거칠기
                jit = DoubleArray(90) { r.next() },
            )
        }
        wobA = r.next() * 6.28
        wobB = r.next() * 6.28
        wobC = r.next() * 6.28
    }

    private fun tailProgress(s: Double) = ((s - tailStart) / (1.0 - tailStart)).coerceIn(0.0, 1.0)

    /** 필압: 둥글게 눌러 앉히고, 몸통은 고르게, 꼬리에서 붓을 든다. */
    private fun pressure(s: Double): Double {
        val land = 1.0 + 0.14 * smooth(0.0, 0.05, s)
        val settle = 1.0 - 0.14 * smooth(0.05, 0.16, s)
        val body = 1.0 + 0.06 * sin(s * 8.5 + wobA) + 0.03 * sin(s * 23 + wobC)
        val lift = 1.0 - 0.72 * smooth(0.0, 1.0, tailProgress(s))
        return land * settle * body * lift
    }

    /** 획의 [s](0~1) 지점. 법선은 바깥쪽이고 붓털은 법선 방향으로 퍼진다. */
    fun point(s: Double, sizePx: Double): Point {
        val th = (StartClockDeg + sweepDeg * s) * PI / 180.0
        val r0 = sizePx * 0.43
        val radius = r0 * (1.0 + 0.012 * sin(s * 7 + wobB) + 0.03 * smooth(0.3, 1.0, tailProgress(s)))
        val sn = sin(th)
        val cs = cos(th)
        val angle = 0.84 + 0.16 * abs(sin(th - 0.7)) // 붓을 비스듬히 쥔 느낌: 방향마다 굵기가 조금 다르다
        return Point(
            x = sizePx / 2 + radius * sn,
            y = sizePx / 2 - radius * cs,
            nx = sn,
            ny = -cs,
            halfWidth = sizePx * 0.026 * weight * pressure(s) * angle,
        )
    }

    /** 붓털 [b]가 [s] 지점에 남긴 먹의 양. [InkThreshold]보다 작으면 비어 갈라진다. */
    fun ink(b: Bristle, s: Double): Double {
        val pool = if (s < 0.07) 0.25 * (1 - s / 0.07) else 0.0 // 처음 누른 자리에 먹이 고인다
        val left = b.load - (0.22 * s + 0.85 * tailProgress(s).pow(1.2)) * b.drain + pool
        val n = 0.62 * valueNoise(b.coarse, s) + 0.38 * valueNoise(b.fine, s)
        return left + 0.55 * (n - 0.5)
    }

    /** 가장자리 털일수록 조금 늦게 종이에 닿아 시작이 반원처럼 둥글다. 붓털이 닿기 시작하는 [s]. */
    fun headStart(b: Bristle, sizePx: Double): Double {
        val head = point(0.0, sizePx).halfWidth * 1.05
        val arc = sweepDeg * PI / 180.0 * sizePx * 0.43
        return head * (1 - sqrt(max(0.0, 1 - b.u * b.u))) / arc
    }

    /** 붓털마다 선분을 이어 그릴 곳. [line]으로 선분을 받고, 그린 선분이 있으면 [finish]로 진하기·굵기를 받는다. */
    interface Sink {
        fun line(x0: Float, y0: Float, x1: Float, y1: Float)
        fun finish(alpha: Float, width: Float)
    }

    /** 붓털마다 앞 프레임까지 그린 끝점. */
    class Trail(count: Int) {
        val on = BooleanArray(count)
        val x = DoubleArray(count)
        val y = DoubleArray(count)
        fun reset() = on.fill(false)
    }

    /** [s0]에서 [s1]까지 새로 그릴 부분을 [sink]에 넘긴다. 프레임마다 이어서 부르면 한 획이 된다. */
    fun trace(s0: Double, s1: Double, sizePx: Double, trail: Trail, sink: Sink) {
        if (s1 <= s0) return
        val arc = sweepDeg * PI / 180.0 * sizePx * 0.43
        val steps = max(1, ceil((s1 - s0) * arc / SampleStepPx).toInt())
        val width = max(0.7, 2 * point(s1, sizePx).halfWidth / BristleCount * 2.0)
        val samples = DoubleArray(steps) { k -> s0 + (s1 - s0) * (k + 1) / steps }
        val points = Array(steps) { point(samples[it], sizePx) }
        bristles.forEachIndexed { i, b ->
            var drew = false
            var lastInk = 0.0
            val headS = headStart(b, sizePx)
            for (k in 0 until steps) {
                val s = samples[k]
                val p = points[k]
                val spread = 1 + 0.35 * smooth(0.25, 1.0, tailProgress(s)) // 꼬리에서 털이 벌어진다
                val off = b.u * p.halfWidth * spread + (valueNoise(b.jit, s) - 0.5) * p.halfWidth * 0.12
                val x = p.x + p.nx * off
                val y = p.y + p.ny * off
                val v = ink(b, s)
                val on = v > InkThreshold && s >= headS
                if (on && trail.on[i]) {
                    sink.line(trail.x[i].toFloat(), trail.y[i].toFloat(), x.toFloat(), y.toFloat())
                    drew = true
                    lastInk = v
                }
                trail.on[i] = on
                trail.x[i] = x
                trail.y[i] = y
            }
            if (drew) sink.finish((0.25 + 0.7 * lastInk).coerceIn(0.22, 0.9).toFloat(), width.toFloat())
        }
    }

    companion object {
        /** 시작은 천천히 눌러 앉히고, 끝으로 갈수록 빨라져 휙 빠진다. */
        fun ease(t: Double): Double = t.coerceIn(0.0, 1.0).pow(1.3)
    }
}

private fun smooth(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t)
}

private fun valueNoise(tab: DoubleArray, s: Double): Double {
    val x = s.coerceIn(0.0, 0.9999) * (tab.size - 1)
    val i = floor(x).toInt()
    val f = x - i
    val k = f * f * (3 - 2 * f)
    return tab[i] * (1 - k) + tab[i + 1] * k
}

private const val DrawMillis = 900
private const val HoldMillis = 900L
private const val FadeMillis = 400
private const val FadeGapMillis = 50L

/**
 * 붓 일원상을 그려 가는 애니메이션. 0.9초 동안 그리고, 잠시 멈췄다가 흐려진 뒤 다시 그린다.
 * 그린 부분은 비트맵에 쌓아 두고 프레임마다 새로 지나간 부분만 더 그린다.
 */
@Composable
internal fun BrushEnso(color: Color, modifier: Modifier = Modifier) {
    val brush = remember { EnsoBrush() }
    val trail = remember { EnsoBrush.Trail(brush.bristles.size) }
    val layer = remember { EnsoLayer() }
    var progress by remember { mutableFloatStateOf(0f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            fade.snapTo(1f)
            progress = 0f
            val start = withFrameNanos { it }
            var elapsed = 0.0
            while (elapsed < DrawMillis) {
                elapsed = withFrameNanos { (it - start) / 1_000_000.0 }
                progress = EnsoBrush.ease(elapsed / DrawMillis).toFloat()
            }
            delay(HoldMillis)
            fade.animateTo(0f, tween(FadeMillis))
            delay(FadeGapMillis)
        }
    }
    Canvas(modifier) {
        val side = min(size.width, size.height).roundToInt()
        if (side <= 0) return@Canvas
        val target = progress.toDouble()
        // 새 바퀴(진행이 줄어듦)이거나 크기가 바뀌면 빈 비트맵에서 다시 그린다.
        if (layer.side != side || target < layer.drawn) {
            layer.reset(side)
            trail.reset()
        }
        if (target > layer.drawn) {
            layer.sink.color = color
            brush.trace(layer.drawn, target, side.toDouble(), trail, layer.sink)
            layer.drawn = target
        }
        drawImage(layer.image, topLeft = Offset((size.width - side) / 2, (size.height - side) / 2), alpha = fade.value)
    }
}

/** 지금까지 그린 붓 획을 담는 비트맵. */
private class EnsoLayer {
    var side = 0
        private set
    var image = ImageBitmap(1, 1)
        private set
    private var canvas = BitmapCanvas(image)
    var drawn = 0.0
    val sink = PathSink { canvas }

    fun reset(newSide: Int) {
        side = newSide
        image = ImageBitmap(newSide, newSide)
        canvas = BitmapCanvas(image)
        drawn = 0.0
    }
}

/** 붓털 한 가닥의 선분을 하나의 경로로 모아 한 번에 칠한다. */
private class PathSink(private val canvas: () -> BitmapCanvas) : EnsoBrush.Sink {
    var color: Color = Color.Black
    private val path = Path()
    private val paint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeCap = StrokeCap.Round
        isAntiAlias = true
    }

    override fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
    }

    override fun finish(alpha: Float, width: Float) {
        paint.color = color.copy(alpha = alpha)
        paint.strokeWidth = width
        canvas().drawPath(path, paint)
        path.reset()
    }
}
