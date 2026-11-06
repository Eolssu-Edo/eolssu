package com.ssafy.eolssu.feature.profile.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.profile.LearningMascot
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.learning_mascot_point
import eolssu.shared.generated.resources.learning_mascot_rhythm
import eolssu.shared.generated.resources.learning_mascot_study
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.imageResource

/** GIF의 30프레임과 원래 70·60·70ms 간격. PNG 시트라 네 타깃에서 같은 그림을 재생한다. */
@Composable
internal fun LearningMascotImage(mascot: LearningMascot, modifier: Modifier = Modifier) {
    val image = imageResource(when (mascot) {
        LearningMascot.Study -> Res.drawable.learning_mascot_study
        LearningMascot.Point -> Res.drawable.learning_mascot_point
        LearningMascot.Rhythm -> Res.drawable.learning_mascot_rhythm
    })
    var frame by remember(mascot) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(mascot, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (currentCoroutineContext().isActive) {
                val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                if (scale == 0f) {
                    frame = 0
                    delay(200)
                } else {
                    delay(((if (frame % 3 == 1) 60L else 70L) * scale).toLong().coerceAtLeast(1L))
                    frame = (frame + 1) % 30
                }
            }
        }
    }
    Image(
        painter = BitmapPainter(image, IntOffset(frame % 6 * 192, frame / 6 * 192), IntSize(192, 192)),
        contentDescription = null,
        modifier = modifier,
        // 원본의 흰 바탕을 카드색으로 연결하고 캐릭터의 윤곽·움직임은 보존한다.
        colorFilter = ColorFilter.tint(EolssuTheme.colors.accentSoft, BlendMode.Multiply),
    )
}
