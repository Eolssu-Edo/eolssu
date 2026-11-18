package com.ssafy.eolssu.feature.practice.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.practice.StepPitchGauge
import com.ssafy.eolssu.feature.practice.StepPitchState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/*
 * 한 음씩 따라 불기의 디버그 음높이 막대(S15P31E102-666). 글자 없이 바늘·맞음 띠·유지 막대만 그린다.
 * 판정 결과를 바꾸지 않는 개발용 화면이며 실시간 안내·최종 채점과 다르다. 바늘은 화면에서만 다듬어 판정 값과 다르게 움직일 수 있다.
 */

/** 디버그 빌드에서만 [com.ssafy.eolssu.feature.practice.PracticeRoute]가 넣는 막대 값. 릴리스는 null이라 막대를 그리지 않는다. */
val LocalStepPitchGauge = staticCompositionLocalOf<StateFlow<StepPitchGauge?>?> { null }

/** 막대 한쪽 끝의 센트. 허용 센트의 두 배(최소 100)로, 첫 음을 넓게 받아도 듣는 동안 눈금이 바뀌지 않는다. */
internal fun gaugeRangeCents(toleranceCent: Double): Double = maxOf(100.0, toleranceCent * 2)

/** 막대 위 [cents]의 가로 위치(0~1, 가운데 0.5). 범위를 넘으면 끝에 붙인다. */
internal fun gaugeFraction(cents: Double, rangeCents: Double): Float =
    (0.5 + cents / rangeCents / 2).coerceIn(0.0, 1.0).toFloat()

/** 바늘을 움직이는 상태. 높이를 구했어도 미확정·신뢰도 낮음 창은 튀는 값이라 바늘에 쓰지 않는다. */
private val NeedleStates = setOf(StepPitchState.OutOfRange, StepPitchState.WrongOctave, StepPitchState.Holding, StepPitchState.Matched,
    StepPitchState.Releasing)

/** 높이를 못 구한 창이 이 시간 넘게 이어지면 바늘을 감춘다. 짧은 끊김에는 바늘이 제자리에 남는다. */
private const val NeedleLingerMs = 250L

/**
 * 바늘 위치를 다듬는다. 최근 3창의 중앙값으로 한 창짜리 튐을 버리고, 그 값을 지수 평균으로 따라가 떨림을 줄인다.
 * 바늘을 쓸 수 없는 창은 null을 주고 평균은 남겨 둔다. 막대 값이 null(멈춤)이면 처음부터 다시 잰다.
 */
internal class GaugeNeedle(private val follow: Double = 0.3) {
    private val recent = ArrayDeque<Double>()
    private val octaves = ArrayDeque<Int>()
    private var smoothed: Double? = null

    /** 다듬은 바늘 센트가 같은 율명의 다른 옥타브를 가리키는지. 옥타브도 최근 3창의 중앙값으로 정한다. */
    var wrongOctave = false
        private set

    fun update(gauge: StepPitchGauge?): Double? {
        if (gauge == null) {
            reset()
            return null
        }
        val cents = gauge.cents?.takeIf { gauge.state in NeedleStates } ?: return null
        recent.push(cents)
        octaves.push(gauge.octaves)
        // 창이 둘뿐이면 앞쪽(작은 쪽) 값을 써서, 새 값 한 창만으로는 바늘이 옮겨 가지 않게 한다.
        val middle = recent.sorted()[(recent.size - 1) / 2]
        wrongOctave = octaves.sorted()[(octaves.size - 1) / 2] != 0
        val next = smoothed?.let { it + follow * (middle - it) } ?: middle
        smoothed = next
        return next
    }

    /** 바늘이 사라진 뒤 다시 들리면 예전 자리에서 미끄러지지 않고 새 자리에서 시작한다. */
    fun reset() {
        recent.clear()
        octaves.clear()
        smoothed = null
        wrongOctave = false
    }

    private fun <T> ArrayDeque<T>.push(value: T) {
        addLast(value)
        if (size > 3) removeFirst()
    }
}

/** 막대 값이 있으면(디버그 빌드에서 듣는 중) 막대를 그린다. */
@Composable
fun PracticeStepGauge(modifier: Modifier = Modifier) {
    val flow = LocalStepPitchGauge.current ?: return
    val gauge by flow.collectAsStateWithLifecycle()
    val colors = EolssuTheme.colors
    val needle = remember { GaugeNeedle() }
    val value = gauge
    val heard = remember(value) { needle.update(value) }
    var shown by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(heard, value == null) {
        when {
            heard != null -> shown = heard
            value == null -> shown = null
            else -> {
                delay(NeedleLingerMs)
                shown = null
                needle.reset()
            }
        }
    }
    val range = gaugeRangeCents(value?.toleranceCent ?: 50.0)
    val tolerance = value?.toleranceCent ?: 50.0
    val low by animateFloatAsState(gaugeFraction(value?.lowCent ?: -tolerance, range), tween(300), label = "gaugeLow")
    val high by animateFloatAsState(gaugeFraction(value?.highCent ?: tolerance, range), tween(300), label = "gaugeHigh")
    // 바늘이 사라지는 동안에도 마지막 자리에서 흐려지게 둔다. 그리기 상태가 아니라 값만 기억한다.
    val lastX = remember { floatArrayOf(0.5f) }
    shown?.let { lastX[0] = gaugeFraction(it, range) }
    val x by animateFloatAsState(lastX[0], spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "gaugeNeedle")
    val alpha by animateFloatAsState(if (shown != null) 1f else 0f, tween(200), label = "gaugeNeedleAlpha")
    val inside = shown?.let { cents -> cents in (value?.lowCent ?: -tolerance)..(value?.highCent ?: tolerance) } ?: false
    val needleColor by animateColorAsState(
        when {
            needle.wrongOctave || !inside -> colors.pitchError
            else -> colors.green
        },
        tween(200),
        label = "gaugeNeedleColor",
    )
    val hold by animateFloatAsState((value?.hold ?: 0.0).toFloat(), tween(100), label = "gaugeHold")
    val band = colors.green.copy(alpha = 0.22f)
    val track = colors.surfaceStrong
    val center = colors.lineStrong
    Column(
        modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val barTop = size.height * 0.25f
            val barHeight = size.height * 0.5f
            drawRoundRect(track, Offset(0f, barTop), Size(size.width, barHeight), CornerRadius(barHeight / 2))
            drawRect(band, Offset(low * size.width, barTop), Size((high - low) * size.width, barHeight))
            drawLine(center, Offset(size.width / 2, barTop), Offset(size.width / 2, barTop + barHeight), 1.dp.toPx())
            if (alpha > 0f) {
                val needleX = x * size.width
                val width = 4.dp.toPx()
                drawRoundRect(needleColor.copy(alpha = alpha), Offset(needleX - width / 2, 0f), Size(width, size.height), CornerRadius(width / 2))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
            if (hold > 0f) drawRoundRect(colors.green, size = Size(size.width * hold, size.height), cornerRadius = CornerRadius(size.height / 2))
        }
    }
}
