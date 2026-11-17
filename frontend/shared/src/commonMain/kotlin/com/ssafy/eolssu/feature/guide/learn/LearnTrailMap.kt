package com.ssafy.eolssu.feature.guide.learn

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuColors
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.KoreanParticle
import com.ssafy.eolssu.core.ui.component.withParticle
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_tiger
import eolssu.shared.generated.resources.learn_card_dot
import eolssu.shared.generated.resources.learn_trail_done
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * 7단계를 옛 호숫가의 돌다리로 그린다(S15P31E102-688). 왼쪽 부들 물가에서 오른쪽 언덕 위 정자까지 징검돌이 한 줄로 같은 간격으로 놓인다.
 * 징검돌은 물에 절반쯤 잠긴 바위다(S15P31E102-888). 마친 단계 돌은 이끼 낀 연두로 칠하고, 지금 아래 카드로 보는 단계 돌에는 호랑이가 서 있고 굵은 테두리다.
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
        Canvas(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(paper),
        ) {
            drawLakeScenery(colors, paper, water)
        }
        stages.forEachIndexed { index, stage ->
            val center = points[index]
            TrailNode(
                stage = stage,
                done = state.isDone(stage.number),
                viewed = stage.number == viewedStage,
                paper = paper,
                water = water,
                onClick = UserActionLog.click(QaTarget.LEARN_TRAIL_MAP_01, { onShowStage(stage.number) }),
                modifier = Modifier.offset(width * center.x - StoneWidth / 2, TrailHeight * center.y - NodeSize / 2),
            )
        }
        // 호랑이는 지금 보는 단계 돌에 서 있고, 다른 단계를 고르면 돌에서 돌로 한 번씩 깡충 건너간다.
        // 단계 동그라미보다 위에 그려 가려지지 않게 하고, 그림만 있고 누르는 동작이 없어 아래 단계 동그라미는 그대로 눌린다.
        val viewedIndex = stages.indexOfFirst { it.number == viewedStage }.coerceAtLeast(0)
        // 호랑이 위치(돌 번호, 건너는 중이면 소수). 움직임 값은 배치·그리기 단계에서만 읽어, 건너는 동안 지도 전체를 매 프레임 다시 구성하지 않는다(S15P31E102-1102).
        val walk = remember { Animatable(viewedIndex.toFloat()) }
        // 다 와서 내려앉으면 살짝 눌렸다가 튀어 오른다.
        val landing = remember { Animatable(0f) }
        LaunchedEffect(viewedIndex) {
            val target = viewedIndex.toFloat()
            val distance = abs(target - walk.value)
            if (distance == 0f) return@LaunchedEffect
            // 돌 하나에 한 번 뛰도록 남은 거리만큼 건넌다(너무 길어지지 않게 상한을 둔다). 건너는 도중 목표가 바뀌면
            // 처음부터 천천히 출발하지 않고 지금 속도로 이어 달려, 빠르게 여러 번 넘겨도 누르는 만큼 바로 따라온다.
            val moving = walk.value != floor(walk.value)
            // 내려앉아 눌리던 중에 다시 떠나면 눌림·물결이 남지 않도록 바로 편다.
            landing.snapTo(0f)
            walk.animateTo(
                target,
                tween(
                    (TigerStepMillis * distance).toInt().coerceIn(TigerStepMillis / 2, TigerWalkMaxMillis),
                    easing = if (moving) LinearOutSlowInEasing else FastOutSlowInEasing,
                ),
            )
            landing.snapTo(1f)
            landing.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
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
            // 아래 값은 모두 움직임 값을 읽으므로 offset { }·graphicsLayer { }·그리기 안에서만 부른다.
            val at = { trailPointAt(points, walk.value) }
            val air = { maxOf(sin(PI.toFloat() * (walk.value - floor(walk.value))), jump.value) }
            val feetX = { width * at().x }
            val feetY = { TrailHeight * at().y - NodeSize / 2 + TigerFeetInset }
            // 돌에 내려앉으면 둘레 물결이 동그랗게 퍼지며 옅어진다.
            Canvas(
                Modifier
                    .offset { DpOffset(feetX() - RippleWidth / 2, TrailHeight * at().y - RippleHeight / 2 + NodeSize / 4).toIntOffset(this) }
                    .size(RippleWidth, RippleHeight),
            ) {
                val ripple = landing.value
                if (ripple <= 0.01f) return@Canvas
                val spread = 0.55f + 0.45f * (1f - ripple)
                val ringSize = Size(size.width * spread, size.height * spread)
                drawOval(
                    ink.copy(alpha = 0.35f * ripple),
                    topLeft = Offset((size.width - ringSize.width) / 2, (size.height - ringSize.height) / 2),
                    size = ringSize,
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            // 그림자는 땅에 남고, 호랑이가 높이 뜰수록 작고 옅어진다.
            Box(
                Modifier
                    .offset { DpOffset(feetX() - TigerShadowWidth / 2, feetY() - TigerShadowHeight / 2).toIntOffset(this) }
                    .size(TigerShadowWidth, TigerShadowHeight)
                    .graphicsLayer {
                        val a = air()
                        scaleX = 1f - 0.35f * a
                        scaleY = 1f - 0.35f * a
                        alpha = 1f - 0.5f * a
                    }
                    .background(colors.text.copy(alpha = 0.22f), CircleShape),
            )
            Image(
                painterResource(Res.drawable.home_tiger),
                contentDescription = null,
                Modifier
                    .offset {
                        val hop = TigerHop + (FinishJumpHeight - TigerHop) * jump.value
                        DpOffset(feetX() - TigerWidth / 2, feetY() - TigerHeight - hop * air()).toIntOffset(this)
                    }
                    .size(TigerWidth, TigerHeight)
                    .graphicsLayer {
                        val a = air()
                        // 돌을 건널 때는 가는 쪽으로 기울고, 다 건넌 축하 때는 좌우로 번갈아 기운다.
                        val facing = when {
                            jump.value > 0f -> jumpSide
                            walk.targetValue >= walk.value -> 1f
                            else -> -1f
                        }
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        rotationZ = TigerLean * a * facing
                        scaleY = 1f + 0.05f * a - 0.12f * landing.value
                        scaleX = 1f - 0.03f * a + 0.08f * landing.value
                    },
            )
        }
    }
}

@Composable
private fun TrailNode(stage: LearnStage, done: Boolean, viewed: Boolean, paper: Color, water: Color, onClick: () -> Unit, modifier: Modifier) {
    val colors = EolssuTheme.colors
    val label = if (done) stringResource(Res.string.learn_trail_done, stage.number, stage.title) else stringResource(Res.string.learn_card_dot, stage.number, stage.title.withParticle(KoreanParticle.EuroRo))
    val rock = remember(stage.number) { TrailRock(stage.number) }
    val dashed = stage.optional && !done && !viewed
    Box(
        modifier
            .size(StoneWidth, NodeSize)
            .drawBehind { drawRock(rock, done, viewed, dashed, colors, paper, water) }
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // 숫자·체크는 물 밖으로 나온 바위의 세로 가운데에 둔다.
        val lift = Modifier.offset(y = rock.labelY.dp)
        if (done) {
            Icon(EolssuIcons.Check, contentDescription = null, lift.size(14.dp), tint = colors.onPrimary)
        } else {
            Text(
                "${stage.number}",
                lift,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = if (viewed) FontWeight.SemiBold else FontWeight.Medium),
                color = if (viewed) colors.text else colors.textMuted,
            )
        }
    }
    // 배치에는 돌 칸(31×26dp)만 차지하고, 누르는 영역은 그 둘레 48dp로 넓혀 칸 밖까지 걸친다.
    // 눌림 효과는 그려진 바위 크기의 타원으로 둬 그림보다 작게 번지지 않게 한다(S15P31E102-968).
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .layout { measurable, _ ->
                val width = StoneWidth.roundToPx()
                val height = NodeSize.roundToPx()
                val touch = NodeTouch.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(touch, touch))
                layout(width, height) { placeable.place((width - touch) / 2, (height - touch) / 2) }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = UserActionLog.click(QaTarget.LEARN_TRAIL_MAP_02, onClick))
            .semantics {
                contentDescription = label
                selected = viewed
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(StoneRippleWidth, StoneRippleHeight).clip(CircleShape).indication(interaction, ripple()))
    }
}

/**
 * 단계마다 윤곽이 조금씩 다른 징검돌 바위. 타원 둘레 9점의 반지름을 [seed]로 흔들고 점 사이를 2차 곡선으로 잇는다.
 * 좌표는 dp이고 바위 중심이 원점이다. [labelY]는 누르는 칸 가운데에서 숫자를 내릴 거리다.
 */
private class TrailRock(val seed: Int) {
    val rx = 15f + 0.8f * sin(seed * 2.3f)
    val ry = 12f + 0.6f * cos(seed * 1.9f)
    private val points: List<Offset> = List(RockPoints) { j ->
        val a = j.toFloat() / RockPoints * 2f * PI.toFloat() + 0.3f * sin(seed * 1.3f)
        val r = 1f + 0.10f * sin(j * 2.1f + seed * 1.7f) + 0.05f * sin(j * 3.7f + seed * 0.9f)
        Offset(cos(a) * rx * r, sin(a) * ry * r)
    }

    /** 윤곽에서 가장 높은 곳(2차 곡선 위 최소 y). */
    private val top: Float = points.indices.minOf { j ->
        val p0 = (points[(j + RockPoints - 1) % RockPoints].y + points[j].y) / 2
        val p1 = points[j].y
        val p2 = (points[j].y + points[(j + 1) % RockPoints].y) / 2
        (0..10).minOf { s ->
            val t = s / 10f
            (1 - t) * (1 - t) * p0 + 2 * (1 - t) * t * p1 + t * t * p2
        }
    }

    val labelY: Float = (RockCenterY + top + WaterLineY) / 2

    fun path(center: Offset): Path = Path().apply {
        fun at(i: Int) = center + points[i % RockPoints]
        fun mid(i: Int) = (at(i) + at(i + 1)) / 2f
        val start = mid(RockPoints - 1)
        moveTo(start.x, start.y)
        for (j in 0 until RockPoints) {
            val c = at(j)
            val end = mid(j)
            quadraticTo(c.x, c.y, end.x, end.y)
        }
        close()
    }
}

/**
 * 물에 절반쯤 잠긴 징검돌: 물 위는 위에서 아래로 짙어지는 돌빛·빛·결(마친 돌은 이끼), 물 아래는 물빛에 묻혀 흐리고,
 * 물이 닿는 선에 물결이 돈다. 보는 돌은 짙은 녹색 테두리, 선택 단계는 점선 테두리다. 좌표는 dp로 그린다.
 */
private fun DrawScope.drawRock(rock: TrailRock, done: Boolean, viewed: Boolean, dashed: Boolean, colors: EolssuColors, paper: Color, water: Color) {
    val ink = colors.text
    val cx = size.width / density / 2
    val cy = size.height / density / 2
    val dp = density
    withTransform({ scale(dp, dp, Offset.Zero) }) {
        val rx = rock.rx
        val ry = rock.ry
        val sy = cy + RockCenterY
        val wl = cy + WaterLineY
        val outline = rock.path(Offset(cx, sy))
        val light = if (done) lerp(lerp(colors.surfaceStrong, colors.lineStrong, 0.15f), colors.primary, 0.7f) else lerp(colors.surfaceStrong, colors.lineStrong, 0.18f)
        val dark = if (done) lerp(lerp(colors.surfaceStrong, colors.lineStrong, 0.5f), colors.play, 0.5f) else lerp(colors.surfaceStrong, colors.lineStrong, 0.6f)
        val top = sy - ry * 1.1f

        // 물에 비친 그늘
        oval(ink.copy(alpha = 0.12f), Offset(cx + 1f, wl + 2.5f), rx + 3f, 3.2f)
        // 바위 몸통: 위가 밝고 물 쪽으로 짙어진다.
        drawPath(outline, Brush.verticalGradient(0f to light, 0.55f to light, 1f to dark, startY = top, endY = wl))
        clipPath(outline) {
            // 왼쪽 위로 비치는 빛, 오른쪽 그늘
            val shine = Offset(cx - rx * 0.32f, top + ry * 0.45f)
            rotate(-8.6f, pivot = shine) { oval(paper.copy(alpha = if (done) 0.3f else 0.6f), shine, rx * 0.5f, ry * 0.32f) }
            oval(ink.copy(alpha = 0.08f), Offset(cx + rx * 0.75f, sy), rx * 0.45f, ry * 0.9f)
            if (done) {
                MossPatches.forEach { (dx, dy, r) -> oval(colors.play.copy(alpha = 0.35f), Offset(cx + dx * rx, sy + dy * ry), r * rx, r * ry) }
            } else {
                StoneSpecks.forEach { (dx, dy, r) -> drawCircle(ink.copy(alpha = 0.2f), r, Offset(cx + dx * rx, sy + dy * ry)) }
                val side = if (sin(rock.seed * 2.7f) > 0f) 1f else -1f
                val crack = Path().apply {
                    moveTo(cx + side * rx * 0.6f, top + ry * 0.3f)
                    quadraticTo(cx + side * rx * 0.42f, sy - ry * 0.35f, cx + side * rx * 0.55f, wl)
                }
                drawPath(crack, ink.copy(alpha = 0.22f), style = Stroke(0.7f))
            }
        }
        // 테두리는 물 위만 또렷하게
        clipRect(cx - rx * 2, top - 6f, cx + rx * 2, wl) {
            val edge = if (viewed) colors.play else ink.copy(alpha = 0.55f)
            val dash = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3f, 2.4f)) else null
            drawPath(outline, edge, style = Stroke(if (viewed) 2.4f else 1.1f, pathEffect = dash))
        }
        // 물 아래는 물빛으로 덮어 잠긴 것처럼 흐리게
        clipRect(cx - rx * 2, wl, cx + rx * 2, wl + ry * 2) {
            drawPath(outline, water.copy(alpha = 0.82f))
            drawPath(outline, ink.copy(alpha = 0.12f), style = Stroke(0.9f))
        }
        // 물이 닿는 선의 물결(앞쪽 반)
        arc(paper.copy(alpha = 0.9f), Offset(cx, wl), rx + 3.5f, 2.6f, 8f, 164f, 1.1f)
        arc(paper.copy(alpha = 0.6f), Offset(cx, wl + 1.2f), rx + 4.5f, 3.6f, 45f, 90f, 0.9f)
    }
}

/** 돌다리 위 [t]번째(소수 허용) 자리. 돌 사이를 곧게 잇는다(뛰어오르는 높이는 따로 더한다). */
private fun trailPointAt(points: List<Offset>, t: Float): Offset {
    if (points.size < 2) return points.first()
    val i = t.toInt().coerceIn(0, points.size - 2)
    val f = (t - i).coerceIn(0f, 1f)
    return points[i] + (points[i + 1] - points[i]) * f
}

/**
 * 옛 호숫가 수묵 배경: 옅은 해와 먼 산 능선, 물가 선 없이 위에서부터 서서히 짙어지는 호수와 잔물결,
 * 왼쪽 부들 물가와 오른쪽 언덕 위 정자. 꾸밈이라 읽지 않는다. 좌표는 dp로 그린다.
 */
private fun DrawScope.drawLakeScenery(colors: EolssuColors, paper: Color, water: Color) {
    val ink = colors.text
    val grass = colors.primary
    val w = size.width / density
    val h = size.height / density
    val dp = density
    withTransform({ scale(dp, dp, Offset.Zero) }) {
        drawCircle(LakeSun.copy(alpha = 0.45f), radius = 10f, center = Offset(w * 0.62f, h * 0.2f))

        // 먼 산: 위쪽에 옅게 겹친 두 능선
        drawPath(
            ridge(w, h) { x -> 0.30f + 0.08f * sin(x * 6f + 1.2f) + 0.03f * sin(x * 19f) },
            Brush.verticalGradient(0f to ink.copy(alpha = 0.10f), 1f to ink.copy(alpha = 0f), startY = h * 0.18f, endY = h * 0.5f),
        )
        drawPath(
            ridge(w, h) { x -> 0.38f + 0.06f * sin(x * 9f + 3f) + 0.02f * sin(x * 23f + 1f) },
            Brush.verticalGradient(0f to ink.copy(alpha = 0.08f), 1f to ink.copy(alpha = 0f), startY = h * 0.3f, endY = h * 0.55f),
        )

        // 호수: 물가 조금 위에서 투명하게 시작해 아래로 짙어져, 먼 산 아래가 물에 스며든다.
        val lakeTop = h * LakeTop - 4f
        drawRect(
            Brush.verticalGradient(0f to water.copy(alpha = 0f), 0.22f to water.copy(alpha = 0.75f), 1f to water, startY = lakeTop, endY = h),
            topLeft = Offset(0f, lakeTop),
            size = Size(w, h - lakeTop),
        )
        // 잔물결
        val waves = listOf(Offset(0.22f, 0.86f), Offset(0.47f, 0.92f), Offset(0.70f, 0.86f), Offset(0.34f, 0.52f), Offset(0.60f, 0.54f), Offset(0.83f, 0.93f))
        waves.forEach { wave -> arc(paper.copy(alpha = 0.75f), Offset(w * wave.x, h * wave.y + 1f), 14f, 4f, 200f, 140f, 1.3f) }

        drawBanks(w, h, grass)
        drawCattails(w, h, ink, grass)
        drawPavilion(w, h, colors, paper)
    }
}

/** 왼쪽 물가 언덕과, 정자가 올라설 만큼 위가 평평한 오른쪽 언덕. */
private fun DrawScope.drawBanks(w: Float, h: Float, grass: Color) {
    val left = Path().apply {
        moveTo(0f, h * 0.48f)
        cubicTo(w * 0.06f, h * 0.5f, w * 0.095f, h * 0.7f, w * 0.07f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(left, Brush.verticalGradient(0f to grass.copy(alpha = 0.45f), 1f to grass.copy(alpha = 0.2f), startY = h * 0.48f, endY = h))
    val px = w - PavilionInset
    val gy = h * PavilionGround
    val right = Path().apply {
        moveTo(w, gy - 5f)
        cubicTo(px, gy - 6f, px - 15f, gy - 5f, px - 19f, gy)
        cubicTo(px - 23f, gy + 8f, px - 26f, h * 0.8f, px - 24f, h)
        lineTo(w, h)
        close()
    }
    drawPath(right, Brush.verticalGradient(0f to grass.copy(alpha = 0.5f), 1f to grass.copy(alpha = 0.2f), startY = gy - 6f, endY = h))
}

/** 왼쪽 물가의 부들: 칼 모양 잎, 휘어진 줄기, 갈색 원통 이삭과 가는 수꽃대, 물가 풀포기. */
private fun DrawScope.drawCattails(w: Float, h: Float, ink: Color, grass: Color) {
    val leaf = lerp(grass, ink, 0.38f)
    val leafDark = lerp(grass, ink, 0.55f)
    val spike = lerp(CattailBrown, ink, 0.18f)
    val baseY = h * 0.64f
    CattailBlades.forEach { blade ->
        val x0 = w * blade.x
        val tipX = x0 + blade.bend
        val tipY = h * blade.tipY
        val midY = (baseY + tipY) / 2
        val path = Path().apply {
            moveTo(x0 - 1.3f, baseY)
            quadraticTo(x0 - 1f + blade.bend * 0.15f, midY, tipX, tipY)
            quadraticTo(x0 + 1f + blade.bend * 0.35f, midY, x0 + 1.3f, baseY)
            close()
        }
        drawPath(path, (if (blade.dark) leafDark else leaf).copy(alpha = blade.alpha))
    }
    CattailStalks.forEach { (x, tipY, lean) ->
        val x0 = w * x
        val topY = h * tipY
        val tx = x0 + lean
        val stem = Path().apply {
            moveTo(x0, baseY)
            quadraticTo(x0 + lean * 0.2f, (baseY + topY) / 2, tx, topY)
        }
        drawPath(stem, leafDark.copy(alpha = 0.85f), style = Stroke(1f, cap = StrokeCap.Round))
        drawLine(spike.copy(alpha = 0.7f), Offset(tx, topY + 1f), Offset(tx + lean * 0.12f, topY - 5f), 0.8f, StrokeCap.Round)
        val spikeLeft = tx + lean * 0.03f - 1.8f
        val spikeTop = topY + 2f
        drawRoundRect(spike, Offset(spikeLeft, spikeTop), Size(3.6f, 10f), CornerRadius(1.8f))
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(spikeLeft + 0.7f, spikeTop + 1.2f), Size(1f, 7f), CornerRadius(0.5f))
    }
    GrassTufts.forEach { tuft ->
        val x0 = w * tuft.x
        val y0 = h * tuft.y + 6f
        listOf(Offset(-3f, -5f), Offset(0f, -7f), Offset(3f, -5f)).forEach { d ->
            val blade = Path().apply {
                moveTo(x0, y0)
                quadraticTo(x0 + d.x * 0.3f, y0 + d.y * 0.6f, x0 + d.x, y0 + d.y)
            }
            drawPath(blade, leaf.copy(alpha = 0.7f), style = Stroke(0.9f, cap = StrokeCap.Round))
        }
    }
}

/**
 * 오른쪽 언덕 위의 정자: 돌 기단과 디딤돌, 붉은 기둥 넷, 마루와 난간, 단청 띠, 처마 끝이 들린 기와지붕과 용마루.
 * 카드 오른쪽 끝에서 늘 같은 거리([PavilionInset])에 놓아 화면 너비가 달라도 언덕 밖으로 나가지 않는다.
 */
private fun DrawScope.drawPavilion(w: Float, h: Float, colors: EolssuColors, paper: Color) {
    val ink = colors.text
    val px = w - PavilionInset
    val gy = h * PavilionGround
    val stone = lerp(colors.surfaceStrong, colors.lineStrong, 0.4f)
    val wood = lerp(PavilionWood, ink, 0.25f)
    val pillar = lerp(PavilionPillar, ink, 0.2f)
    val line = ink.copy(alpha = 0.6f)
    val thin = Stroke(0.7f)

    // 기단과 디딤돌
    drawRect(stone, Offset(px - 13f, gy - 4f), Size(26f, 4f))
    drawRect(line, Offset(px - 13f, gy - 4f), Size(26f, 4f), style = thin)
    listOf(-6.5f, 0f, 6.5f).forEach { dx -> drawLine(line, Offset(px + dx, gy - 4f), Offset(px + dx, gy), 0.7f) }
    drawRect(stone, Offset(px - 3.5f, gy), Size(7f, 1.6f))
    drawRect(line, Offset(px - 3.5f, gy), Size(7f, 1.6f), style = thin)
    // 기둥
    listOf(-10f, -3.4f, 3.4f, 10f).forEach { c -> drawRect(pillar, Offset(px + c - 0.8f, gy - 21f), Size(1.6f, 17f)) }
    // 마루와 난간
    drawRect(wood, Offset(px - 11.5f, gy - 7f), Size(23f, 2.2f))
    val rail = wood.copy(alpha = 0.95f)
    drawLine(rail, Offset(px - 11.5f, gy - 10.5f), Offset(px + 11.5f, gy - 10.5f), 0.7f)
    for (i in 0..8) {
        val x = px - 10f + i * 2.5f
        drawLine(rail, Offset(x, gy - 10.5f), Offset(x, gy - 7f), 0.7f)
    }
    // 창방(단청 띠)
    drawRect(lerp(LakeTeal, ink, 0.25f), Offset(px - 11.5f, gy - 23f), Size(23f, 2.4f))
    listOf(-7f, 0f, 7f).forEach { dx -> drawRect(LakeSun.copy(alpha = 0.7f), Offset(px + dx - 1f, gy - 22.6f), Size(2f, 1.6f)) }
    // 지붕: 가운데가 처지고 양 끝이 들린 처마에서 오목하게 올라가 용마루에 닿는다.
    val roof = Path().apply {
        moveTo(px - 18.5f, gy - 28f)
        quadraticTo(px - 13f, gy - 22.7f, px - 6f, gy - 22.5f)
        lineTo(px + 6f, gy - 22.5f)
        quadraticTo(px + 13f, gy - 22.7f, px + 18.5f, gy - 28f)
        quadraticTo(px + 13f, gy - 28.5f, px + 9f, gy - 33f)
        lineTo(px - 9f, gy - 33f)
        quadraticTo(px - 13f, gy - 28.5f, px - 18.5f, gy - 28f)
        close()
    }
    drawPath(roof, Brush.verticalGradient(0f to ink.copy(alpha = 0.82f), 1f to ink.copy(alpha = 0.66f), startY = gy - 33.5f, endY = gy - 22.5f))
    // 기왓골: 처마 쪽으로 퍼지는 줄
    clipPath(roof) {
        for (i in -7..7) {
            val t = i / 7f
            drawLine(paper.copy(alpha = 0.16f), Offset(px + t * 8.5f, gy - 33f), Offset(px + t * 17f, gy - 22f), 0.55f)
        }
    }
    // 처마 끝 서까래 빛
    val eave = Path().apply {
        moveTo(px - 17f, gy - 27.2f)
        quadraticTo(px - 12.5f, gy - 23.6f, px - 6f, gy - 23.4f)
        lineTo(px + 6f, gy - 23.4f)
        quadraticTo(px + 12.5f, gy - 23.6f, px + 17f, gy - 27.2f)
    }
    drawPath(eave, paper.copy(alpha = 0.55f), style = Stroke(0.7f, cap = StrokeCap.Round))
    // 용마루: 양 끝이 살짝 들린다.
    val ridgeLine = Path().apply {
        moveTo(px - 11f, gy - 35.3f)
        quadraticTo(px - 8.8f, gy - 33.1f, px - 6f, gy - 33.1f)
        lineTo(px + 6f, gy - 33.1f)
        quadraticTo(px + 8.8f, gy - 33.1f, px + 11f, gy - 35.3f)
    }
    drawPath(ridgeLine, ink.copy(alpha = 0.9f), style = Stroke(1.8f, cap = StrokeCap.Round))
}

/** 가로 0~1 → 세로 0~1 함수 [top]을 윗선으로 하고 바닥까지 채운 능선. */
private fun ridge(w: Float, h: Float, top: (Float) -> Float): Path = Path().apply {
    val steps = 48
    moveTo(0f, h)
    for (i in 0..steps) {
        val x = i / steps.toFloat()
        lineTo(x * w, top(x) * h)
    }
    lineTo(w, h)
    close()
}

/** 중심 [center]와 반지름으로 그리는 타원. */
private fun DrawScope.oval(color: Color, center: Offset, rx: Float, ry: Float) =
    drawOval(color, Offset(center.x - rx, center.y - ry), Size(rx * 2, ry * 2))

/** 중심 [center]·반지름 타원 둘레의 호. 각은 3시 방향에서 시계 방향 도(°)다. */
private fun DrawScope.arc(color: Color, center: Offset, rx: Float, ry: Float, start: Float, sweep: Float, width: Float) =
    drawArc(color, start, sweep, false, Offset(center.x - rx, center.y - ry), Size(rx * 2, ry * 2), style = Stroke(width, cap = StrokeCap.Round))

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

/** 호랑이 발이 바위 꼭대기에 걸치는 깊이. 더 내리면 발이 지금 단계 바위의 번호를 가리므로 0으로 둔다(S15P31E102-987). */
private val TigerFeetInset = 0.dp

/** 호랑이 발밑 그림자 크기. */
private val TigerShadowWidth = 30.dp
private val TigerShadowHeight = 7.dp

/** 내려앉을 때 퍼지는 물결 크기. */
private val RippleWidth = 64.dp
private val RippleHeight = 16.dp

private val TrailHeight: Dp = 120.dp
private val NodeSize: Dp = 26.dp
private val StoneWidth: Dp = 31.dp

/** 돌을 누르는 정사각형 한 변과, 그려진 바위(물결 포함 약 42×30dp)를 덮는 눌림 타원 크기. */
private val NodeTouch: Dp = 48.dp
private val StoneRippleWidth: Dp = 44.dp
private val StoneRippleHeight: Dp = 32.dp
private val TigerWidth: Dp = 60.dp
private val TigerHeight: Dp = 49.dp

/** 징검돌 바위: 윤곽 점 수, 누르는 칸 가운데에서 바위 중심과 물이 닿는 선까지의 거리(dp). 바위 아래 절반쯤이 잠긴다. */
private const val RockPoints = 9
private const val RockCenterY = -0.5f
private const val WaterLineY = 4.5f

/** 마친 돌의 이끼 얼룩과 아직인 돌의 결(바위 반지름 비율 가로·세로 자리와 크기). */
private val MossPatches = listOf(Triple(-0.55f, -0.15f, 0.26f), Triple(0.45f, -0.55f, 0.22f), Triple(0.2f, 0.05f, 0.18f))
private val StoneSpecks = listOf(Triple(0.55f, -0.45f, 0.7f), Triple(-0.62f, -0.05f, 0.55f), Triple(0.35f, 0f, 0.5f))

/** 부들: 잎(가로 비율 · 휘는 dp · 끝 높이 비율 · 짙은 잎 · 투명도), 줄기(가로 비율 · 끝 높이 비율 · 기우는 dp), 물가 풀포기 자리. */
private class CattailBlade(val x: Float, val bend: Float, val tipY: Float, val dark: Boolean, val alpha: Float)

private val CattailBlades = listOf(
    CattailBlade(0.010f, -5f, 0.30f, true, 0.55f),
    CattailBlade(0.028f, 7f, 0.36f, false, 0.7f),
    CattailBlade(0.050f, -6f, 0.42f, true, 0.6f),
    CattailBlade(0.064f, 9f, 0.50f, false, 0.7f),
    CattailBlade(0.020f, 3f, 0.47f, false, 0.6f),
)
private val CattailStalks = listOf(Triple(0.018f, 0.25f, 2.5f), Triple(0.036f, 0.20f, -1.5f), Triple(0.056f, 0.31f, 3f))
private val GrassTufts = listOf(Offset(0.004f, 0.58f), Offset(0.075f, 0.6f), Offset(0.083f, 0.66f))

/** 정자: 카드 오른쪽 끝에서 가운데까지 거리(dp)와 기단 바닥 높이(카드 높이 비율). */
private const val PavilionInset = 22f
private const val PavilionGround = 0.47f

/** 호수 물가 선(카드 높이 비율)과 수묵 빛깔. */
private const val LakeTop = 0.44f
private val LakeTeal = Color(0xFF7FA6A3)
private val LakeSun = Color(0xFFC0503A)

/** 부들 이삭, 정자 나무·기둥 빛깔. 테마에 없는 그림 전용 색이다. */
private val CattailBrown = Color(0xFF7A5232)
private val PavilionWood = Color(0xFF7A4A33)
private val PavilionPillar = Color(0xFF9A4A35)

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

/** 배치 단계의 [androidx.compose.foundation.layout.offset] { } 안에서 dp 위치를 픽셀로 바꾼다. */
private fun DpOffset.toIntOffset(density: Density): IntOffset = with(density) { IntOffset(x.roundToPx(), y.roundToPx()) }
