package com.ssafy.eolssu.feature.practice.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_loading_mascot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt

// 사용자 제공 ‘06_장단 타기_움짤.gif’의 30프레임과 원본 지연(총 2초)을 보존한 세로 PNG atlas.
private val LoadingFrameDurations = longArrayOf(
    70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70,
    70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70, 70, 60, 70,
)

@Composable
internal fun PracticeLoadingMascot(modifier: Modifier = Modifier) {
    val bitmap = imageResource(Res.drawable.practice_loading_mascot)
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(LoadingFrameDurations[frame])
            frame = (frame + 1) % LoadingFrameDurations.size
        }
    }
    Canvas(modifier) {
        if (bitmap.height >= 160 * LoadingFrameDurations.size) drawImage(
            image = bitmap, srcOffset = IntOffset(0, frame * 160), srcSize = IntSize(160, 160),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
    }
}
