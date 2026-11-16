package com.ssafy.eolssu.feature.home.component

import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_ink_brush
import eolssu.shared.generated.resources.home_start_evaluation
import eolssu.shared.generated.resources.home_ink_griun
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 원본 SVG는 고정하고 붓털 모양의 클립만 전진시킨다. 글자 반전도 같은 경계를 쓴다.
 * 누르는 동안에는 같은 붓털 경계로 짙은 먹이 왼쪽에서 오른쪽으로 번지며 글자를 덮고, 손을 떼면 되돌아간다.
 * 키보드 포커스는 네모 테두리 대신 붓 모양을 따라 두른 테두리로 보인다(S15P31E102-969).
 */
@Composable
internal fun HomeInkButton(
    onClick: () -> Unit,
    darkBackground: Boolean,
    scrollProgress: Float,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val fontFamily = FontFamily(Font(Res.font.home_ink_griun, FontWeight.Normal))
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val targetReveal = if (reduceMotion) {
        if (scrollProgress >= .5f) 1f else 0f
    } else scrollProgress.coerceIn(0f, 1f)
    // 스크롤·자동 스냅이 목표를 연속 갱신해도 진행 중인 붓의 속도를 이어받는다.
    val reveal by animateFloatAsState(targetReveal, homeInkRevealSpec(reduceMotion))
    val ink by animateColorAsState(if (darkBackground) Color.White else Color(0xFF0B1020), tween(if (reduceMotion) 0 else 250))
    val rest by animateColorAsState(if (darkBackground) Color.White else Color(0xFF171714), tween(if (reduceMotion) 0 else 250))
    val onInk by animateColorAsState(if (darkBackground) Color(0xFF171714) else Color(0xFFFFFEFA), tween(if (reduceMotion) 0 else 250))
    val pressInk by animateColorAsState(if (darkBackground) Color.White else Color.Black, tween(if (reduceMotion) 0 else 250))
    val press = rememberInkPress(interactions, reduceMotion)
    val label = stringResource(Res.string.home_start_evaluation)
    val brush = painterResource(Res.drawable.home_ink_brush)
    Box(
        modifier.fillMaxWidth().heightIn(min = 44.dp)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (focused) InkFocusRing(brush, Modifier.matchParentSize().padding(vertical = 2.dp))
        Image(
            brush, contentDescription = null,
            modifier = Modifier.matchParentSize().padding(vertical = 2.dp).inkReveal(reveal),
            contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(ink),
        )
        Box(Modifier.matchParentSize().inkHide(press.value), contentAlignment = Alignment.Center) {
            InkLabel(label, rest, fontFamily)
            Box(Modifier.matchParentSize().inkReveal(reveal).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                InkLabel(label, onInk, fontFamily)
            }
        }
        if (press.value > 0f) {
            Image(
                brush, contentDescription = null,
                modifier = Modifier.matchParentSize().padding(vertical = 2.dp).inkReveal(press.value),
                contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(lerp(ink, pressInk, .6f)),
            )
        }
    }
}

/**
 * 누르는 동안 0→1로 붓을 끌고, 손을 떼면 1→0으로 되돌린다. 짧게 톡 눌러도 끝까지 한 번 번진 뒤 되돌아가 반응이 보이게 한다.
 * 동작 줄이기면 애니메이션 없이 바로 바뀐다.
 */
@Composable
private fun rememberInkPress(interactions: MutableInteractionSource, reduceMotion: Boolean): Animatable<Float, AnimationVector1D> {
    val press = remember { Animatable(0f) }
    LaunchedEffect(interactions, reduceMotion) {
        var job: Job? = null
        interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    job?.cancel()
                    job = launch { press.animateTo(1f, tween(if (reduceMotion) 0 else 220, easing = FastOutSlowInEasing)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val forward = job
                    job = launch {
                        forward?.join()
                        press.animateTo(0f, tween(if (reduceMotion) 0 else 260, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
    }
    return press
}

/** 붓 모양을 바깥으로 [InkFocusWidth]만큼 키운 뒤 원래 붓 모양을 도려내, 붓 둘레만 남긴 포커스 테두리. */
@Composable
private fun InkFocusRing(brush: Painter, modifier: Modifier) {
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        Image(
            brush, contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer {
                val grow = InkFocusWidth.toPx() * 2
                if (size.width > 0f && size.height > 0f) {
                    scaleX = 1f + grow / size.width
                    scaleY = 1f + grow / size.height
                }
            },
            contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(InkFocusColor),
        )
        Image(
            brush, contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer { blendMode = BlendMode.DstOut },
            contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(Color.Black),
        )
    }
}

private val InkFocusWidth = 3.dp
private val InkFocusColor = Color(0xFF686250)

@Composable
private fun InkLabel(label: String, color: Color, fontFamily: FontFamily?) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.None, fontSize = 20.sp, lineHeight = 28.sp))
    }
}

private fun Modifier.inkReveal(progress: Float) = drawWithCache {
    val edge = inkRevealPath(size, progress)
    onDrawWithContent { clipPath(edge) { this@onDrawWithContent.drawContent() } }
}

/** [inkReveal]의 반대: 붓이 지나간 자리의 내용(글자)을 지운다. */
private fun Modifier.inkHide(progress: Float) = drawWithCache {
    val edge = inkRevealPath(size, progress)
    onDrawWithContent {
        if (progress <= 0f) drawContent() else clipPath(edge, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
    }
}

internal fun homeInkRevealSpec(reduceMotion: Boolean): AnimationSpec<Float> =
    if (reduceMotion) tween(0) else spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 200f,
        visibilityThreshold = .001f,
    )
