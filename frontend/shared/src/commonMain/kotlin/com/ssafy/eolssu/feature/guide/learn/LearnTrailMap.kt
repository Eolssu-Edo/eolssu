package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_tiger
import eolssu.shared.generated.resources.learn_card_dot
import eolssu.shared.generated.resources.learn_trail_done
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * 9단계를 산길로 그린다. 아래에서 시작해 굽이굽이 올라가 9단계가 꼭대기다.
 * 마친 단계는 연두로 채우고, 지금 아래 카드로 보는 단계에는 호랑이가 서 있고 굵은 테두리다.
 * 단계를 누르면 아래 그림 카드가 그 단계로 넘어간다.
 */
@Composable
internal fun LearnTrailMap(state: LearnUiState, viewedStage: Int, onShowStage: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val stages = state.stages
    val shape = RoundedCornerShape(20.dp)
    val paper = lerp(colors.surface, colors.surfaceStrong, 0.45f)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TrailHeight)
            .clip(shape)
            .background(paper)
            .border(1.dp, colors.lineSoft, shape),
    ) {
        val width = maxWidth
        val points = TrailPoints.take(stages.size)
        val ink = colors.text
        val grass = colors.primary
        val sun = colors.rec
        val trail = colors.text.copy(alpha = 0.35f)
        val walked = colors.primary
        val flag = state.instrument.sealColor(colors)
        Canvas(Modifier.fillMaxSize()) {
            val pixels = points.map { Offset(it.x * size.width, it.y * size.height) }
            drawInkScenery(points.lastOrNull() ?: Offset(0.6f, 0.2f), ink, paper, grass, sun)
            pixels.lastOrNull()?.let { drawSummitFlag(it, NodeSize.toPx() / 2, flag) }
            for (i in 0 until pixels.size - 1) {
                val bothDone = state.isDone(stages[i].number) && state.isDone(stages[i + 1].number)
                drawPath(
                    segment(pixels, i),
                    color = if (bothDone) walked else trail,
                    style = Stroke(
                        width = (if (bothDone) 5 else 3).dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (bothDone) null else PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            }
        }
        stages.forEachIndexed { index, stage ->
            val center = points[index]
            val x = width * center.x
            val y = TrailHeight * center.y
            TrailNode(
                stage = stage,
                done = state.isDone(stage.number),
                viewed = stage.number == viewedStage,
                onClick = { onShowStage(stage.number) },
                modifier = Modifier.offset(x - NodeSize / 2, y - NodeSize / 2),
            )
        }
        // 호랑이는 지금 보는 단계에 서 있고, 다른 단계를 고르면 산길을 따라 한 칸에 한 번씩 깡충 뛰어간다.
        // 단계 동그라미보다 위에 그려 가려지지 않게 하고, 단계 사이에서는 위로 떠올라 지나는 숫자를 덮지 않는다.
        // 그림만 있고 누르는 동작이 없어 아래 단계 동그라미는 그대로 눌린다.
        val viewedIndex = stages.indexOfFirst { it.number == viewedStage }.coerceAtLeast(0)
        // 한 칸마다 한 번 뛰도록, 멀리 갈수록 오래 걷는다(너무 길어지지 않게 상한을 둔다).
        var from by remember { mutableStateOf(viewedIndex) }
        val steps = abs(viewedIndex - from).coerceAtLeast(1)
        // 다 와서 내려앉으면 살짝 눌렸다가 튀어 오른다.
        val landing = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val walk by animateFloatAsState(
            viewedIndex.toFloat(),
            tween((TigerStepMillis * steps).coerceAtMost(TigerWalkMaxMillis), easing = FastOutSlowInEasing),
            label = "tigerWalk",
            finishedListener = {
                if (it.toInt() != from) {
                    scope.launch {
                        landing.snapTo(1f)
                        landing.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
                    }
                }
                from = it.toInt()
            },
        )
        if (points.isNotEmpty()) {
            val at = trailPointAt(points, walk)
            val air = sin(PI.toFloat() * (walk - floor(walk)))
            // 가는 쪽(화면 왼쪽 · 오른쪽)으로 몸을 살짝 기울인다.
            val ahead = trailPointAt(points, (walk + if (viewedIndex >= walk) 0.05f else -0.05f).coerceIn(0f, points.lastIndex.toFloat()))
            val facing = if (ahead.x >= at.x) 1f else -1f
            val feetX = width * at.x
            val feetY = TrailHeight * at.y - NodeSize / 2 + TigerFeetInset
            // 그림자는 땅에 남고, 호랑이가 높이 뜰수록 작고 옅어진다.
            Box(
                Modifier
                    .offset(feetX - TigerShadowWidth / 2, feetY - TigerShadowHeight / 2)
                    .size(TigerShadowWidth, TigerShadowHeight)
                    .graphicsLayer {
                        scaleX = 1f - 0.35f * air
                        scaleY = 1f - 0.35f * air
                        alpha = 1f - 0.5f * air
                    }
                    .background(colors.text.copy(alpha = 0.22f), CircleShape),
            )
            Image(
                painterResource(Res.drawable.home_tiger),
                contentDescription = null,
                Modifier
                    .offset(feetX - TigerWidth / 2, (feetY - TigerHeight - TigerHop * air).coerceAtLeast(0.dp))
                    .size(TigerWidth, TigerHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        rotationZ = TigerLean * air * facing
                        scaleY = 1f + 0.05f * air - 0.12f * landing.value
                        scaleX = 1f - 0.03f * air + 0.08f * landing.value
                    },
            )
        }
    }
}

@Composable
private fun TrailNode(stage: LearnStage, done: Boolean, viewed: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val label = if (done) stringResource(Res.string.learn_trail_done, stage.number, stage.title) else stringResource(Res.string.learn_card_dot, stage.number, stage.title)
    val ring = when {
        viewed -> colors.play
        done -> colors.primary
        else -> colors.lineStrong.copy(alpha = 0.6f)
    }
    Box(
        modifier
            .size(NodeSize)
            .clip(CircleShape)
            .background(if (done) colors.primary else colors.surface)
            .then(if (stage.optional && !done && !viewed) Modifier.dashedRing(ring) else Modifier.border(if (viewed) 3.dp else 1.5.dp, ring, CircleShape))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = viewed
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(EolssuIcons.Check, contentDescription = null, Modifier.size(16.dp), tint = colors.onPrimary)
        } else {
            Text(
                "${stage.number}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = if (viewed) FontWeight.Bold else FontWeight.Medium),
                color = if (viewed) colors.text else colors.textMuted,
            )
        }
    }
}

/** 선택 단계(9단계)는 점선 테두리로 '안 해도 되는 단계'임을 보여 준다. */
private fun Modifier.dashedRing(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.5.dp.toPx()
    drawCircle(
        color,
        radius = size.minDimension / 2 - stroke / 2,
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** [i]번 점에서 다음 점까지 Catmull-Rom 곡선 한 토막. 점을 반드시 지나 굽은 길이 된다. */
/** 산길 위 [t]번째(소수 허용) 자리. [segment]와 같은 곡선을 따라가 호랑이가 길 위로 걷는다. */
private fun trailPointAt(points: List<Offset>, t: Float): Offset {
    if (points.size < 2) return points.first()
    val i = t.toInt().coerceIn(0, points.size - 2)
    val f = (t - i).coerceIn(0f, 1f)
    val p0 = points[(i - 1).coerceAtLeast(0)]
    val p1 = points[i]
    val p2 = points[i + 1]
    val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
    val c1 = p1 + (p2 - p0) / 6f
    val c2 = p2 - (p3 - p1) / 6f
    val u = 1f - f
    return p1 * (u * u * u) + c1 * (3f * u * u * f) + c2 * (3f * u * f * f) + p2 * (f * f * f)
}

/** 호랑이가 한 칸 뛰는 시간과 한 번에 걷는 최대 시간. */
private const val TigerStepMillis = 280
private const val TigerWalkMaxMillis = 1400

/** 단계 사이를 지날 때 호랑이가 떠오르는 높이와 기우는 각도. */
private val TigerHop = 14.dp
private const val TigerLean = 9f

/** 호랑이 발이 단계 동그라미 윗부분에 걸치는 깊이. 그림 아래 투명한 여백만큼 더 내린다. */
private val TigerFeetInset = 7.dp

/** 호랑이 발밑 그림자 크기. */
private val TigerShadowWidth = 30.dp
private val TigerShadowHeight = 7.dp

private fun segment(points: List<Offset>, i: Int): Path {
    val p0 = points[(i - 1).coerceAtLeast(0)]
    val p1 = points[i]
    val p2 = points[i + 1]
    val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
    return Path().apply {
        moveTo(p1.x, p1.y)
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}

/** 꼭대기 단계 오른쪽 위에 꽂은 작은 깃발. 꾸밈이라 읽지 않는다. */
private fun DrawScope.drawSummitFlag(summit: Offset, radius: Float, color: Color) {
    val foot = Offset(summit.x + radius * 0.75f, summit.y - radius * 0.65f)
    val top = Offset(foot.x, foot.y - 26.dp.toPx())
    drawLine(color, foot, top, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    val cloth = Path().apply {
        moveTo(top.x, top.y)
        lineTo(top.x + 16.dp.toPx(), top.y + 5.dp.toPx())
        lineTo(top.x, top.y + 10.dp.toPx())
        close()
    }
    drawPath(cloth, color)
}

private val TrailHeight: Dp = 216.dp
private val NodeSize: Dp = 32.dp
private val TigerWidth: Dp = 60.dp
private val TigerHeight: Dp = 49.dp

/** 카드 안 상대 위치(0~1). 산기슭에서 굽이굽이 올라 9단계가 큰 산 꼭대기다. */
private val TrailPoints: List<Offset> = listOf(
    Offset(0.09f, 0.86f),
    Offset(0.31f, 0.89f),
    Offset(0.55f, 0.82f),
    Offset(0.80f, 0.70f),
    Offset(0.62f, 0.55f),
    Offset(0.37f, 0.52f),
    Offset(0.20f, 0.36f),
    Offset(0.40f, 0.26f),
    Offset(0.60f, 0.19f),
)
