package com.ssafy.eolssu.feature.practice.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.practice.TempoStep
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 단계 자리마다 점을 찍은 속도 슬라이더. 손잡이는 삼태극 북([DrumThumb])이다.
 * 끄는 동안 손잡이는 손가락을 따라가고 위의 단계 이름은 가장 가까운 단계로 바뀌며, 놓으면 그 단계 자리에 붙는다.
 * 길을 눌러도 그 자리에서 가까운 단계로 간다. 단계 사이 간격은 배율과 관계없이 같다.
 *
 * @param interaction 누르기·끌기 상태를 밖에서도 읽을 때 넘긴다(취약 구간 선택의 BPM 잠깐 보이기).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TempoSlider(
    step: TempoStep,
    onStep: (TempoStep) -> Unit,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val steps = TempoStep.entries
    val position = remember { Animatable(step.ordinal.toFloat()) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    val latestStep by rememberUpdatedState(step)
    // 패널을 다시 열어 다른 단계로 시작하면 그 자리로 옮긴다.
    LaunchedEffect(step) {
        if (!dragging && position.targetValue.roundToInt() != step.ordinal) position.snapTo(step.ordinal.toFloat())
    }
    val held = interaction.collectIsDraggedAsState().value || interaction.collectIsPressedAsState().value
    val label = tempoLabel(step)
    Slider(
        value = position.value,
        onValueChange = { value ->
            dragging = true
            scope.launch { position.snapTo(value) }
            val nearest = steps[value.roundToInt().coerceIn(0, steps.lastIndex)]
            if (nearest != latestStep) onStep(nearest)
        },
        onValueChangeFinished = {
            dragging = false
            val target = position.value.roundToInt().coerceIn(0, steps.lastIndex).toFloat()
            scope.launch { position.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
        },
        valueRange = 0f..steps.lastIndex.toFloat(),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).semantics { stateDescription = label },
        interactionSource = interaction,
        thumb = { DrumThumb(held) },
        track = { TempoTrack(position.value / steps.lastIndex, steps.size) },
    )
}

/** 슬라이더 길: 옅은 선 위에 지난 쪽을 진하게 칠하고, 단계 자리마다 점을 찍는다. */
@Composable
private fun TempoTrack(fraction: Float, stepCount: Int) {
    val colors = EolssuTheme.colors
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val y = size.height / 2
        val thickness = 4.dp.toPx()
        drawLine(colors.lineSoft, Offset(0f, y), Offset(size.width, y), thickness, cap = StrokeCap.Round)
        drawLine(colors.primary, Offset(0f, y), Offset(size.width * fraction, y), thickness, cap = StrokeCap.Round)
        for (i in 0 until stepCount) {
            val at = i / (stepCount - 1f)
            drawCircle(if (at <= fraction + 0.001f) colors.surface else colors.lineStrong, radius = 3.dp.toPx(), center = Offset(size.width * at, y))
        }
    }
}

/** 손잡이: 정면에서 본 북. 끄거나 누르고 있으면([held]) 조금 커진다. */
@Composable
private fun DrumThumb(held: Boolean) {
    val scale by animateFloatAsState(if (held) 1.12f else 1f, label = "drumThumbScale")
    Canvas(Modifier.size(DrumThumbSize).scale(scale).shadow(3.dp, CircleShape)) { drawDrum() }
}

/**
 * 북 면: 단청 빛깔로 칸을 나눈 테두리, 가죽을 고정한 놋쇠 징, 크림빛 가죽, 가운데 삼태극(빨강 위 오른쪽 · 파랑 아래 · 노랑 왼쪽).
 * 삼태극은 반지름의 절반인 반원 셋으로 원을 세 쉼표 모양으로 나눈 것이다.
 */
private fun DrawScope.drawDrum() {
    val c = center
    val outer = size.minDimension / 2
    val face = outer * 0.76f
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
    val line = outer * 0.05f
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
        drawCircle(DrumStud, radius = outer * 0.045f, center = c + polar(face * 0.88f, angle))
    }
    // 삼태극.
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
        drawPath(lobe, color)
    }
}

private fun polar(radius: Float, radians: Double) = Offset((radius * cos(radians)).toFloat(), (radius * sin(radians)).toFloat())

private val DrumThumbSize = 30.dp

/** 삼태극 첫 칸이 시작하는 각도(도, 12시 방향). */
private const val TaegeukStart = -90f

private val TaegeukColors = listOf(Color(0xFFB52F3A), Color(0xFF2E4A9B), Color(0xFFE6B52F))
private val DancheongColors = listOf(Color(0xFFC0392B), Color(0xFF2F5D9E), Color(0xFFE2A83A))
private val DrumSkin = Color(0xFFF5E8CB)
private val DrumStud = Color(0xFFB8902F)
private val DrumInk = Color(0xFF4A3424)
