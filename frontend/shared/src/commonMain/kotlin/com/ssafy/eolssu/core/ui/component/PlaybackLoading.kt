package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.practice_player_loading
import org.jetbrains.compose.resources.stringResource
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
fun LoadingMascot(modifier: Modifier = Modifier) {
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

/**
 * 음원을 불러오는 동안 화면 가운데에 띄우는 ‘로딩 중’ 막: 옅은 바탕 막 위에 150dp 흰 원 안 장단 타기 그림과 ‘로딩 중’.
 * 누름은 막지 않아 뒤로 가기 등을 그대로 쓸 수 있다. 연습 화면과 이전 연주 결과의 처음 재생이 같이 쓴다.
 */
@Composable
fun PlaybackLoadingOverlay(modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val message = stringResource(Res.string.practice_player_loading)
    Column(
        modifier.fillMaxSize().background(colors.background.copy(alpha = 0.74f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                contentDescription = message
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 3·2·1 북과 같은 150dp 원·그림자 안에 그림을 둔다(그림의 흰 바탕이 네모로 보이지 않게).
            Box(
                Modifier.size(150.dp)
                    .shadow(14.dp, CircleShape, ambientColor = colors.text.copy(alpha = 0.14f), spotColor = colors.text.copy(alpha = 0.14f))
                    .background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LoadingMascot(Modifier.size(124.dp))
            }
            Text(
                message,
                modifier = Modifier.padding(horizontal = 24.dp).clearAndSetSemantics {},
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
        }
    }
}
