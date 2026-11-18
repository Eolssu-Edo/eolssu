package com.ssafy.eolssu.feature.practice.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.DrumThumb
import com.ssafy.eolssu.feature.practice.TempoStep
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 단계 자리마다 점을 찍은 속도 슬라이더. 손잡이는 공용 삼태극 북([DrumThumb])이다.
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
        onValueChange = UserActionLog.change(QaTarget.TEMPO_SLIDER_01, { value ->
            dragging = true
            scope.launch { position.snapTo(value) }
            val nearest = steps[value.roundToInt().coerceIn(0, steps.lastIndex)]
            if (nearest != latestStep) onStep(nearest)
        }),
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
