package com.ssafy.eolssu.feature.auth.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.CompactContentMaxWidth
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.auth.SignupPath
import com.ssafy.eolssu.feature.auth.SignupProgress
import com.ssafy.eolssu.feature.auth.SignupStep
import com.ssafy.eolssu.feature.auth.SignupStepEntry
import com.ssafy.eolssu.feature.auth.SignupStepStatus
import com.ssafy.eolssu.feature.auth.SignupStepTransitions
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.auth_signup_step_next
import eolssu.shared.generated.resources.practice_beat_janggu
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 회원가입·비밀번호 찾기 각 단계 화면의 공통 머리: 상단 바(제목은 경로의 [SignupPath.title], 오른쪽 `n / N`)와
 * 그 아래 [SignupStepIndicator]. 단계는 화면이 넘기는 [progress]로만 정하므로, 앞뒤로 이동하거나 다시 방문해도
 * 떠 있는 화면과 표시가 같다.
 */
@Composable
fun SignupTopBar(progress: SignupProgress, onBack: () -> Unit) {
    Column {
        AuthTopBar(
            stringResource(progress.path.title),
            onBack = onBack,
            right = { AuthStepLabel(progress.number, progress.total) },
        )
        SignupStepIndicator(progress)
    }
}

/**
 * 회원가입 단계 표시. 단계마다 점을 한 줄로 잇고, 지금 단계 점에 장단 표시와 같은 장구 아이콘을 둔다.
 * - 마친 단계 점은 짙은 연두, 남은 단계 점은 빈 원이다. 지나온 선은 연두에서 장구 가까이 갈수록 장구색(붉은색)으로 바뀌고,
 *   장구 주변 선에는 옅은 붉은 빛이 남는다.
 * - 다음 단계로 가면 장구가 앞 점에서 새 점으로 미끄러지며 선을 채우고(뒤로 가기는 반대로), 도착하면 붉은 고리가 퍼진다.
 *   출발점은 [transitions]가 기억한 직전 단계다. 직전 단계를 모르면(가입 시작·앱 재시작·새로고침) 움직이지 않고 도착 표시만 한다.
 * 점 아래 단계명은 휴대폰에서 지금 단계와 다음 단계만, 태블릿에서 점마다 보인다. 한 줄로 두고 넘치면 말줄임한다.
 * 화면 읽기 프로그램은 '회원가입 4단계 중 2단계, 이메일 인증'처럼 경로의 [SignupPath.progressDescription]으로 한 번에 읽는다.
 */
@Composable
fun SignupStepIndicator(
    progress: SignupProgress,
    modifier: Modifier = Modifier,
    transitions: SignupStepTransitions = SignupStepTransitions.Shared,
) {
    val frame = LocalAuthFrame.current
    val colors = EolssuTheme.colors
    val description = stringResource(
        progress.path.progressDescription,
        progress.number,
        progress.total,
        stringResource(progress.current.label),
    )
    val entry = remember(progress) { transitions.enter(progress) }
    // 장구 자리(0부터 세는 단계 자리, 소수면 두 점 사이)와 도착 고리(0 → 1로 퍼지며 사라짐, 1이면 없음).
    val position = remember(progress) {
        Animatable((if (entry is SignupStepEntry.Moved) entry.fromIndex else progress.index).toFloat())
    }
    val arrival = remember(progress) { Animatable(if (entry == SignupStepEntry.Same) 1f else 0f) }
    LaunchedEffect(progress) {
        if (entry is SignupStepEntry.Moved) position.animateTo(progress.index.toFloat(), tween(MoveMillis, easing = FastOutSlowInEasing))
        if (entry != SignupStepEntry.Same) arrival.animateTo(1f, tween(ArrivalMillis, easing = LinearOutSlowInEasing))
    }
    // 움직이는 동안 장구 뒤 붉은 꼬리가 길고 진해졌다가, 멈추면 옅게 남는다.
    val motion by animateFloatAsState(if (position.isRunning) 1f else 0f, tween(if (position.isRunning) 120 else 420))

    val janggu = painterResource(Res.drawable.practice_beat_janggu)
    val track = TrackColors(
        base = colors.surfaceStrong,
        passed = colors.accent,
        janggu = colors.rec,
        doneNode = colors.play,
        upcomingNode = colors.line,
        nodeFill = colors.background,
    )
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = if (frame.isTablet) AuthTabletColumnWidth + 40.dp else CompactContentMaxWidth)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Canvas(Modifier.fillMaxWidth().height(TrackHeight)) {
                drawTrack(progress.total, position.value, motion, arrival.value, track) { size ->
                    with(janggu) { draw(size) }
                }
            }
            if (frame.isTablet) {
                Row(Modifier.fillMaxWidth()) {
                    progress.steps.forEach { step -> StepLabel(step, progress.statusOf(step), Modifier.weight(1f), centered = true) }
                }
            } else {
                // 두 단계명이 각자 줄 너비의 절반까지만 차지하고 양 끝에 붙는다. 넘치면 말줄임한다.
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StepLabel(progress.current, SignupStepStatus.Current, Modifier.weight(1f, fill = false))
                    progress.next?.let { next ->
                        Text(
                            stringResource(Res.string.auth_signup_step_next, stringResource(next.label)),
                            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
                            style = labelStyle(),
                            color = colors.textMuted,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private const val MoveMillis = 650
private const val ArrivalMillis = 560

/** 장구가 놓이는 선 영역 높이. 장구(30×24dp)와 도착 고리가 잘리지 않는 높이다. */
private val TrackHeight = 34.dp
private val JangguWidth = 30.dp
private val LineWidth = 3.dp
private val NodeRadius = 4.5.dp

private class TrackColors(
    val base: Color,
    val passed: Color,
    val janggu: Color,
    val doneNode: Color,
    val upcomingNode: Color,
    val nodeFill: Color,
)

/**
 * 점은 칸(전체 너비 ÷ 단계 수)의 가운데에 둔다. [position]은 장구 자리, [motion]은 움직임 정도(0~1), [arrival]은 도착 고리 진행(0~1).
 * 그리는 순서: 바탕 선 → 장구 주변 붉은 빛 → 지나온 선(연두→붉은색) → 점 → 장구 뒤 빛 → 도착 고리 → 장구.
 */
private fun DrawScope.drawTrack(
    total: Int,
    position: Float,
    motion: Float,
    arrival: Float,
    colors: TrackColors,
    drawJanggu: DrawScope.(Size) -> Unit,
) {
    val slot = size.width / total
    val y = size.height / 2
    fun xOf(index: Float) = slot * (index + 0.5f)
    val first = xOf(0f)
    val last = xOf((total - 1).toFloat())
    val jx = xOf(position)
    val line = LineWidth.toPx()

    drawLine(colors.base, Offset(first, y), Offset(last, y), line, StrokeCap.Round)

    // 장구 주변에 옅게 남는 붉은 빛. 지나온 쪽은 길게, 앞쪽은 짧게 번진다. 움직이는 동안 더 길고 진하다.
    val glowWidth = 9.dp.toPx()
    val behind = (36.dp + 44.dp * motion).toPx()
    val ahead = 18.dp.toPx()
    val glowAlpha = 0.16f + 0.22f * motion
    val glowStart = (jx - behind).coerceAtLeast(first)
    if (jx - glowStart > 1f) {
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, colors.janggu.copy(alpha = glowAlpha)), startX = glowStart, endX = jx),
            Offset(glowStart, y), Offset(jx, y), glowWidth, StrokeCap.Round,
        )
    }
    val glowEnd = (jx + ahead).coerceAtMost(last)
    if (glowEnd - jx > 1f) {
        drawLine(
            Brush.horizontalGradient(listOf(colors.janggu.copy(alpha = glowAlpha * 0.6f), Color.Transparent), startX = jx, endX = glowEnd),
            Offset(jx, y), Offset(glowEnd, y), glowWidth, StrokeCap.Round,
        )
    }

    // 지나온 선: 연두로 오다가 장구 앞 구간에서 장구색으로 바뀐다.
    if (jx - first > 0.5f) {
        val redSpan = (40.dp + 24.dp * motion).toPx()
        drawLine(
            Brush.horizontalGradient(listOf(colors.passed, colors.janggu), startX = (jx - redSpan).coerceAtLeast(first), endX = jx),
            Offset(first, y), Offset(jx, y), line, StrokeCap.Round,
        )
    }

    // 점: 장구가 지나간 점은 마친 단계로 채우고, 장구 아래 점은 그리지 않는다.
    val radius = NodeRadius.toPx()
    val jangguHalf = JangguWidth.toPx() / 2
    for (i in 0 until total) {
        val x = xOf(i.toFloat())
        if (abs(x - jx) < jangguHalf) continue
        if (x < jx) {
            drawCircle(colors.doneNode, radius, Offset(x, y))
        } else {
            drawCircle(colors.nodeFill, radius, Offset(x, y))
            drawCircle(colors.upcomingNode, radius, Offset(x, y), style = Stroke(1.5.dp.toPx()))
        }
    }

    // 장구 뒤 은은한 붉은 빛.
    val halo = 20.dp.toPx()
    drawCircle(
        Brush.radialGradient(listOf(colors.janggu.copy(alpha = 0.18f + 0.12f * motion), Color.Transparent), Offset(jx, y), halo),
        halo, Offset(jx, y),
    )

    // 도착 고리: 장구 둘레에서 퍼지며 사라진다.
    if (arrival < 1f) {
        val ring = jangguHalf * 0.75f + 10.dp.toPx() * arrival
        drawCircle(colors.janggu.copy(alpha = 0.55f * (1f - arrival)), ring, Offset(jx, y), style = Stroke(1.5.dp.toPx()))
    }

    // 장구: 도착할 때 살짝 커졌다 돌아온다.
    val w = JangguWidth.toPx()
    val h = w * JangguAspect
    val pulse = if (arrival < 1f) 1f + 0.12f * sin(PI.toFloat() * arrival) else 1f
    scale(pulse, Offset(jx, y)) {
        translate(jx - w / 2, y - h / 2) { drawJanggu(Size(w, h)) }
    }
}

/** 장구 그림(`practice_beat_janggu.png`, 256×207)의 세로÷가로. */
private const val JangguAspect = 207f / 256f

@Composable
private fun labelStyle() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp)

/** 단계명 한 칸. 마친 단계는 체크, 지금 단계는 붉은 점과 굵은 글씨, 남은 단계는 흐린 글씨. [centered]면 칸 가운데(점 아래)에 둔다. */
@Composable
private fun StepLabel(step: SignupStep, status: SignupStepStatus, modifier: Modifier = Modifier, centered: Boolean = false) {
    val colors = EolssuTheme.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
    ) {
        when (status) {
            SignupStepStatus.Done -> Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(12.dp), tint = colors.accentText)
            SignupStepStatus.Current -> Box(Modifier.size(6.dp).background(colors.rec, CircleShape))
            SignupStepStatus.Upcoming -> Unit
        }
        Text(
            stringResource(step.label),
            modifier = Modifier.weight(1f, fill = false),
            style = labelStyle().copy(fontWeight = if (status == SignupStepStatus.Current) FontWeight.SemiBold else FontWeight.Normal),
            color = when (status) {
                SignupStepStatus.Done -> colors.textSub
                SignupStepStatus.Current -> colors.text
                SignupStepStatus.Upcoming -> colors.textMuted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
