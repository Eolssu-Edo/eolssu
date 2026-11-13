package com.ssafy.eolssu.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/*
 * Elastic squash(S15P31E102-891): 누르면 바로 소리가 나는 버튼에만 쓴다. 누르는 동안 세로로 납작하고 가로로 넓어졌다가,
 * 떼면 스프링으로 살짝 튕기며 제자리로 돌아온다. 화면만 넘어가는 버튼, 5초 이동, 녹음·중단 버튼에는 쓰지 않는다.
 */

/** 세로로 눌리는 비율. */
const val ElasticSquashAmount: Float = 0.10f

/** 한 화면에 많고 차례로 빠르게 누르는 버튼(운지법 기본음·요성)은 기본의 60%만 눌린다. */
const val ElasticSquashSoftAmount: Float = ElasticSquashAmount * 0.6f

/** 아이콘 버튼의 가로 늘림 = 세로 비율 × 이 값. */
const val ElasticSquashIconWidth: Float = 0.8f

/** 글자 버튼은 가로 늘림을 절반으로 줄여 글자가 퍼져 보이지 않게 한다. */
const val ElasticSquashTextWidth: Float = ElasticSquashIconWidth / 2

/** 누를 때는 빠르게 눌린다. */
private val PressSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 1500f)

/** 뗄 때만 덜 감쇠한 스프링으로 튕긴다. */
private val ReleaseSpring = spring<Float>(dampingRatio = 0.4f, stiffness = 400f)

/** 톡 친 탭도 눌린 모양이 보이도록 이만큼은 눌린 채로 둔 뒤 돌아온다. 스크롤 안 버튼은 빠른 탭에서 누름·뗌이 함께 오기 때문이다. */
private val MinPressed = 70.milliseconds

/**
 * [interactionSource]의 누름에 맞춰 내용을 납작하게 눌렀다가 튕기며 되돌린다. 같은 [interactionSource]를 버튼(`Surface`의 `interactionSource`)에도 넘긴다.
 * 크기는 그대로 두고 그릴 때만 바꾸므로 옆 요소가 밀리지 않는다.
 */
@Composable
fun Modifier.elasticSquash(
    interactionSource: InteractionSource,
    amount: Float = ElasticSquashAmount,
    widthFactor: Float = ElasticSquashIconWidth,
): Modifier {
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    LaunchedEffect(interactionSource, amount, widthFactor) {
        var pressedAt = TimeSource.Monotonic.markNow()
        var release: Job? = null
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    release?.cancel()
                    pressedAt = TimeSource.Monotonic.markNow()
                    launch { sx.animateTo(1f + amount * widthFactor, PressSpring) }
                    launch { sy.animateTo(1f - amount, PressSpring) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    release = launch {
                        delay(MinPressed - pressedAt.elapsedNow())
                        launch { sx.animateTo(1f, ReleaseSpring) }
                        sy.animateTo(1f, ReleaseSpring)
                    }
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = sx.value
        scaleY = sy.value
    }
}
