package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learn_step_all_done
import eolssu.shared.generated.resources.learn_step_count
import eolssu.shared.generated.resources.learn_step_progress
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.sin

/**
 * 단계 설명 카드 오른쪽의 원형 할 일 진행(S15P31E102-1281). 마친 만큼 원 아래부터 물이 차오르고, 가운데에 '2/4'를 적는다.
 * 일부만 찼을 때는 앞뒤 두 겹의 물결이 천천히 흐르고, 비었거나 다 찼으면 물결을 멈춘다. 다 마치면 원 전체를 연두로 채운다.
 * 화면 읽기에는 '할 일 n / 전체', 다 마치면 축하 문구로 읽힌다.
 */
@Composable
internal fun StepProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val allDone = done >= total
    val level by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, tween(900, easing = FastOutSlowInEasing), label = "stepWaterLevel")
    val moving = done in 1 until total
    val waveHeight by animateFloatAsState(if (moving) 1f else 0f, tween(600), label = "stepWaveHeight")
    val phase = wavePhase(moving)
    val front by animateColorAsState(if (allDone) colors.primary else WaterFront, label = "stepWaterFront")
    val back by animateColorAsState(if (allDone) colors.primary else WaterBack, label = "stepWaterBack")
    val description = if (allDone) stringResource(Res.string.learn_step_all_done) else stringResource(Res.string.learn_step_progress, done, total)
    val count = stringResource(Res.string.learn_step_count, done, total)
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val background = colors.surfaceStrong
    val textColor = colors.text
    Canvas(modifier.size(StepProgressSize).clearAndSetSemantics { contentDescription = description }) {
        val circle = Path().apply { addOval(Rect(center, size.minDimension / 2)) }
        clipPath(circle) {
            drawRect(background)
            if (level > 0.005f) {
                val surface = size.height * (1f - level)
                val amplitude = WaveAmplitude.toPx() * waveHeight
                drawPath(wavePath(surface - 1.5.dp.toPx(), amplitude, size.width * 0.6f, -phase.value + 1.4f), back)
                drawPath(wavePath(surface, amplitude, size.width * 0.5f, phase.value), front)
            }
        }
        val text = measurer.measure(count, style)
        drawText(text, color = textColor, topLeft = Offset((size.width - text.size.width) / 2f, (size.height - text.size.height) / 2f))
    }
}

/** 물결이 흐르는 위상(0~2π). 물결이 멈춘 동안에는 무한 애니메이션을 돌리지 않는다. */
@Composable
private fun wavePhase(moving: Boolean): State<Float> =
    if (moving) {
        rememberInfiniteTransition(label = "stepWave").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(WaveMillis, easing = LinearEasing)),
            label = "stepWavePhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

/** 높이 [y]를 수면으로 하는 물결 모양. 아래는 원 밖까지 채우고, 원으로 잘라 그린다. */
private fun DrawScope.wavePath(y: Float, amplitude: Float, length: Float, phase: Float): Path = Path().apply {
    val step = 2.dp.toPx()
    moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + step) {
        lineTo(x, y + amplitude * sin(2f * PI.toFloat() * x / length + phase))
        x += step
    }
    lineTo(size.width + step, size.height)
    close()
}

private val StepProgressSize = 52.dp
private val WaveAmplitude = 2.5.dp

/** 물결이 한 번 흐르는 시간. 빠르면 산만해 4초로 느리게 둔다. */
private const val WaveMillis = 4000

/** 물 색(앞·뒤 물결). 앱 색에 없는 옅은 보랏빛으로, 다 마친 연두([com.ssafy.eolssu.core.theme.EolssuColors.primary])와 구분한다. */
private val WaterFront = Color(0xFF9189E6)
private val WaterBack = Color(0xFFC9C4F3)
