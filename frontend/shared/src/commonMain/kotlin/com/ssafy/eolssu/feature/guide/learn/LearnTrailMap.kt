package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * 7단계를 옛 호숫가의 돌다리로 그린다(S15P31E102-688). 왼쪽 물가에서 오른쪽 건너편 정자까지 징검돌이 한 줄로 같은 간격으로 놓인다.
 * 마친 단계 돌은 이끼 낀 연두로 칠하고, 지금 아래 카드로 보는 단계 돌에는 호랑이가 서 있고 굵은 테두리다.
 * 돌을 누르면 아래 그림 카드가 그 단계로 넘어가고, 호랑이가 돌에서 돌로 한 번씩 깡충 건너가며 내려앉을 때 물결이 퍼진다.
 * 마지막 단계를 마치고 돌아오면([LearnUiState.finishCelebration]) 호랑이가 마지막 돌에서 펄쩍펄쩍 뛴 뒤 [onCelebrated]를 부른다.
 * 뛰는 도중 다른 돌을 고르거나 화면을 떠나 끊겨도 [onCelebrated]를 불러 축하는 한 번만 하고, 호랑이는 바로 돌 위에 내려선다.
 * 호랑이는 그림 밖으로 뛰어올라도 잘리지 않는다.
 */
@Composable
internal fun LearnTrailMap(
    state: LearnUiState,
    viewedStage: Int,
    onShowStage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onCelebrated: () -> Unit = {},
) {
    val colors = EolssuTheme.colors
    val stages = state.stages
    val shape = RoundedCornerShape(20.dp)
    val paper = lerp(colors.surface, colors.surfaceStrong, 0.45f)
    BoxWithConstraints(modifier.fillMaxWidth().height(TrailHeight)) {
        val width = maxWidth
        val points = trailPoints(stages.size)
        val ink = colors.text
        val water = lerp(paper, LakeTeal, 0.42f)
        val grass = colors.primary
        Canvas(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(paper),
        ) {
            val y = TrailRowY * size.height
            drawLakeScenery(y, points.map { it.x * size.width }, ink, paper, water, grass)
        }
        stages.forEachIndexed { index, stage ->
            val center = points[index]
            TrailNode(
                stage = stage,
                done = state.isDone(stage.number),
                viewed = stage.number == viewedStage,
                onClick = { onShowStage(stage.number) },
                modifier = Modifier.offset(width * center.x - StoneWidth / 2, TrailHeight * center.y - NodeSize / 2),
            )
        }
        // 호랑이는 지금 보는 단계 돌에 서 있고, 다른 단계를 고르면 돌에서 돌로 한 번씩 깡충 건너간다.
        // 단계 동그라미보다 위에 그려 가려지지 않게 하고, 그림만 있고 누르는 동작이 없어 아래 단계 동그라미는 그대로 눌린다.
        val viewedIndex = stages.indexOfFirst { it.number == viewedStage }.coerceAtLeast(0)
        // 돌 하나에 한 번 뛰도록, 멀리 갈수록 오래 건넌다(너무 길어지지 않게 상한을 둔다).
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
        // 다 건넌 축하: 마지막 돌에 서 있으면 여러 번 높이 뛰고 내려앉을 때마다 살짝 눌린다. 뛸 때마다 몸을 번갈아 기울인다.
        val jump = remember { Animatable(0f) }
        var jumpSide by remember { mutableStateOf(1f) }
        val celebrating = state.finishCelebration && viewedIndex == stages.lastIndex
        val celebrated by rememberUpdatedState(onCelebrated)
        LaunchedEffect(celebrating) {
            if (!celebrating) {
                // 뛰는 도중 다른 돌을 골라 축하가 끊기면 공중에 뜨거나 눌린 채 남지 않도록 바로 돌 위에 내려놓는다.
                jump.snapTo(0f)
                landing.snapTo(0f)
                return@LaunchedEffect
            }
            try {
                delay(FinishStartDelayMillis)
                repeat(FinishJumps) { i ->
                    jumpSide = if (i % 2 == 0) 1f else -1f
                    jump.animateTo(1f, tween(FinishUpMillis, easing = LinearOutSlowInEasing))
                    jump.animateTo(0f, tween(FinishDownMillis, easing = FastOutLinearInEasing))
                    landing.snapTo(1f)
                    landing.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 900f))
                }
            } finally {
                // 다른 돌을 고르거나 화면을 떠나 끊겨도 축하는 본 것으로 남겨, 7단계 돌에 돌아오거나 화면을 다시 그릴 때 또 뛰지 않는다.
                celebrated()
            }
        }
        if (points.isNotEmpty()) {
            val at = trailPointAt(points, walk)
            val air = maxOf(sin(PI.toFloat() * (walk - floor(walk))), jump.value)
            val facing = when {
                jump.value > 0f -> jumpSide
                viewedIndex >= walk -> 1f
                else -> -1f
            }
            val hop = TigerHop + (FinishJumpHeight - TigerHop) * jump.value
            val feetX = width * at.x
            val feetY = TrailHeight * at.y - NodeSize / 2 + TigerFeetInset
            // 돌에 내려앉으면 둘레 물결이 동그랗게 퍼지며 옅어진다.
            val ripple = landing.value
            if (ripple > 0.01f) {
                Canvas(
                    Modifier
                        .offset(feetX - RippleWidth / 2, TrailHeight * at.y - RippleHeight / 2 + NodeSize / 4)
                        .size(RippleWidth, RippleHeight),
                ) {
                    val spread = 0.55f + 0.45f * (1f - ripple)
                    val ringSize = Size(size.width * spread, size.height * spread)
                    drawOval(
                        ink.copy(alpha = 0.35f * ripple),
                        topLeft = Offset((size.width - ringSize.width) / 2, (size.height - ringSize.height) / 2),
                        size = ringSize,
                        style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
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
                    .offset(feetX - TigerWidth / 2, feetY - TigerHeight - hop * air)
                    .size(TigerWidth, TigerHeight)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        // 돌을 건널 때는 가는 쪽으로 기울고, 다 건넌 축하 때는 좌우로 번갈아 기운다.
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
        else -> colors.lineStrong.copy(alpha = 0.7f)
    }
    // 납작한 징검돌. 마친 돌은 이끼 낀 연두, 아직인 돌은 옅은 돌빛이다.
    val stone = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .size(StoneWidth, NodeSize)
            .clip(stone)
            .background(if (done) colors.primary else lerp(colors.surface, colors.lineStrong, 0.18f))
            .then(if (stage.optional && !done && !viewed) Modifier.dashedRing(ring) else Modifier.border(if (viewed) 3.dp else 1.5.dp, ring, stone))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = viewed
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(EolssuIcons.Check, contentDescription = null, Modifier.size(14.dp), tint = colors.onPrimary)
        } else {
            Text(
                "${stage.number}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = if (viewed) FontWeight.Bold else FontWeight.Medium),
                color = if (viewed) colors.text else colors.textMuted,
            )
        }
    }
}

/** 선택 단계는 점선 테두리로 '안 해도 되는 단계'임을 보여 준다. */
private fun Modifier.dashedRing(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(size.height / 2, size.height / 2),
        style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** 돌다리 위 [t]번째(소수 허용) 자리. 돌 사이를 곧게 잇는다(뛰어오르는 높이는 따로 더한다). */
private fun trailPointAt(points: List<Offset>, t: Float): Offset {
    if (points.size < 2) return points.first()
    val i = t.toInt().coerceIn(0, points.size - 2)
    val f = (t - i).coerceIn(0f, 1f)
    return points[i] + (points[i + 1] - points[i]) * f
}

/**
 * 옛 호숫가 수묵 배경: 옅은 해와 먼 산 능선, 돌 줄 높이 [y] 위아래로 펼쳐진 호수와 잔물결,
 * 왼쪽 갈대 물가와 오른쪽 건너편 언덕의 정자, 징검돌([stonesX]) 아래 물 그림자. 꾸밈이라 읽지 않는다.
 */
private fun DrawScope.drawLakeScenery(y: Float, stonesX: List<Float>, ink: Color, paper: Color, water: Color, grass: Color) {
    val w = size.width
    val h = size.height
    drawCircle(LakeSun.copy(alpha = 0.45f), radius = 10.dp.toPx(), center = Offset(w * 0.62f, h * 0.2f))

    // 먼 산: 위쪽에 옅게 겹친 두 능선
    drawPath(
        ridge { x -> 0.30f + 0.08f * sin(x * 6f + 1.2f) + 0.03f * sin(x * 19f) },
        Brush.verticalGradient(0f to ink.copy(alpha = 0.10f), 1f to ink.copy(alpha = 0f), startY = h * 0.18f, endY = h * 0.5f),
    )
    drawPath(
        ridge { x -> 0.38f + 0.06f * sin(x * 9f + 3f) + 0.02f * sin(x * 23f + 1f) },
        Brush.verticalGradient(0f to ink.copy(alpha = 0.08f), 1f to ink.copy(alpha = 0f), startY = h * 0.3f, endY = h * 0.55f),
    )

    // 호수: 물가 선에서 아래로 차츰 짙어진다.
    val shore = h * LakeTop
    drawRect(
        Brush.verticalGradient(0f to paper, 0.15f to water.copy(alpha = 0.7f), 1f to water, startY = shore, endY = h),
        topLeft = Offset(0f, shore),
        size = Size(w, h - shore),
    )
    drawLine(ink.copy(alpha = 0.18f), Offset(0f, shore + 2.dp.toPx()), Offset(w, shore + 2.dp.toPx()), 1.dp.toPx())
    // 잔물결
    val waveColor = paper.copy(alpha = 0.75f)
    val waves = listOf(Offset(0.22f, 0.86f), Offset(0.47f, 0.92f), Offset(0.70f, 0.86f), Offset(0.34f, 0.52f), Offset(0.60f, 0.54f), Offset(0.83f, 0.93f))
    waves.forEach { wave ->
        val half = 14.dp.toPx()
        drawArc(
            waveColor,
            200f,
            140f,
            false,
            Offset(w * wave.x - half, h * wave.y - 3.dp.toPx()),
            Size(half * 2, 8.dp.toPx()),
            style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    // 징검돌 아래 물 그림자
    stonesX.forEach { x ->
        val shadow = Size(StoneWidth.toPx() + 10.dp.toPx(), 10.dp.toPx())
        drawOval(ink.copy(alpha = 0.12f), Offset(x - shadow.width / 2, y + NodeSize.toPx() / 2 - 4.dp.toPx()), shadow)
    }

    // 왼쪽 물가: 풀 언덕과 갈대
    val bank = Path().apply {
        moveTo(0f, h * 0.48f)
        cubicTo(w * 0.05f, h * 0.5f, w * 0.08f, h * 0.7f, w * 0.06f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(bank, Brush.verticalGradient(0f to grass.copy(alpha = 0.45f), 1f to grass.copy(alpha = 0.2f), startY = h * 0.48f, endY = h))
    val reeds = listOf(Offset(0.015f, 0.34f), Offset(0.03f, 0.28f), Offset(0.045f, 0.38f), Offset(0.06f, 0.46f))
    reeds.forEach { reed ->
        val base = Offset(w * reed.x, h * 0.62f)
        val tip = Offset(w * reed.x + 3.dp.toPx(), h * reed.y)
        drawLine(ink.copy(alpha = 0.45f), base, tip, 1.2.dp.toPx(), StrokeCap.Round)
        drawOval(ink.copy(alpha = 0.5f), Offset(tip.x - 1.5.dp.toPx(), tip.y), Size(3.dp.toPx(), 7.dp.toPx()))
    }

    // 오른쪽 건너편: 언덕과 정자
    val far = Path().apply {
        moveTo(w, h * 0.42f)
        cubicTo(w * 0.93f, h * 0.44f, w * 0.88f, h * 0.62f, w * 0.90f, h)
        lineTo(w, h)
        close()
    }
    drawPath(far, Brush.verticalGradient(0f to grass.copy(alpha = 0.45f), 1f to grass.copy(alpha = 0.2f), startY = h * 0.42f, endY = h))
    val pavilionInk = ink.copy(alpha = 0.7f)
    val baseY = h * 0.46f
    val roofY = baseY - 24.dp.toPx()
    val leftPost = w * 0.905f
    val rightPost = w * 0.965f
    drawLine(pavilionInk, Offset(leftPost, baseY), Offset(leftPost, roofY + 4.dp.toPx()), 1.6.dp.toPx())
    drawLine(pavilionInk, Offset(rightPost, baseY), Offset(rightPost, roofY + 4.dp.toPx()), 1.6.dp.toPx())
    drawLine(pavilionInk, Offset(leftPost - 4.dp.toPx(), baseY), Offset(rightPost + 4.dp.toPx(), baseY), 2.dp.toPx(), StrokeCap.Round)
    // 처마 끝이 위로 들린 기와지붕
    val roof = Path().apply {
        moveTo(leftPost - 9.dp.toPx(), roofY)
        quadraticTo((leftPost + rightPost) / 2, roofY + 7.dp.toPx(), rightPost + 9.dp.toPx(), roofY)
        lineTo(rightPost + 2.dp.toPx(), roofY - 7.dp.toPx())
        lineTo(leftPost - 2.dp.toPx(), roofY - 7.dp.toPx())
        close()
    }
    drawPath(roof, pavilionInk)
}

/** 가로 0~1 → 세로 0~1 함수 [top]을 윗선으로 하고 바닥까지 채운 능선. */
private fun DrawScope.ridge(top: (Float) -> Float): Path = Path().apply {
    val steps = 48
    moveTo(0f, size.height)
    for (i in 0..steps) {
        val x = i / steps.toFloat()
        lineTo(x * size.width, top(x) * size.height)
    }
    lineTo(size.width, size.height)
    close()
}

/** 호랑이가 돌 하나를 건너는 시간과 한 번에 건너는 최대 시간. */
private const val TigerStepMillis = 280
private const val TigerWalkMaxMillis = 1400

/** 돌 사이를 건널 때 떠오르는 높이와 기우는 각도. */
private val TigerHop = 16.dp
private const val TigerLean = 9f

/** 다 건넌 축하: 뛰는 횟수 · 높이 · 오르고 내리는 시간 · 목록에 돌아와 뛰기 시작할 때까지. */
private const val FinishJumps = 5
private val FinishJumpHeight = 30.dp
private const val FinishUpMillis = 230
private const val FinishDownMillis = 210
private const val FinishStartDelayMillis = 450L

/** 호랑이 발이 돌 윗부분에 걸치는 깊이. 그림 아래 투명한 여백만큼 더 내린다. */
private val TigerFeetInset = 7.dp

/** 호랑이 발밑 그림자 크기. */
private val TigerShadowWidth = 30.dp
private val TigerShadowHeight = 7.dp

/** 내려앉을 때 퍼지는 물결 크기. */
private val RippleWidth = 64.dp
private val RippleHeight = 16.dp

private val TrailHeight: Dp = 120.dp
private val NodeSize: Dp = 26.dp
private val StoneWidth: Dp = 31.dp
private val TigerWidth: Dp = 60.dp
private val TigerHeight: Dp = 49.dp

/** 호수 물가 선(카드 높이 비율)과 수묵 빛깔. */
private const val LakeTop = 0.44f
private val LakeTeal = Color(0xFF7FA6A3)
private val LakeSun = Color(0xFFC0503A)

/** 징검돌이 놓이는 줄의 높이(카드 안 비율). 위에 호랑이가 설 자리를 남긴다. */
private const val TrailRowY = 0.66f

/** 처음 [count]단계의 카드 안 상대 위치(0~1). 왼쪽 물가에서 오른쪽 건너편까지 한 줄에 같은 간격으로 놓는다. */
internal fun trailPoints(count: Int): List<Offset> = when (count) {
    0 -> emptyList()
    1 -> listOf(Offset(TrailStartX, TrailRowY))
    else -> List(count) { i -> Offset(TrailStartX + (TrailEndX - TrailStartX) * i / (count - 1), TrailRowY) }
}

private const val TrailStartX = 0.14f
private const val TrailEndX = 0.82f
