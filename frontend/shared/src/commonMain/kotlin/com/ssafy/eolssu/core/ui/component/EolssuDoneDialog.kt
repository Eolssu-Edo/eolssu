package com.ssafy.eolssu.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.home_tiger
import org.jetbrains.compose.resources.painterResource

/**
 * '완료했어요!' 창. 호랑이가 통통 튀어 오르고 [body]로 익힌 것을 알린다. 주 버튼([action])을 누르면 창을 닫고 [onAction]을 부른다.
 * 기초부터 배우기 따라 배우기·단계 연습 자동 통과와 연주 결과의 8단계 통과가 같은 창을 쓴다.
 */
@Composable
fun EolssuDoneDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onAction: () -> Unit) {
    val colors = EolssuTheme.colors
    val jump = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        jump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 250f))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).background(colors.accentSoft, CircleShape))
                Image(
                    painterResource(Res.drawable.home_tiger),
                    contentDescription = null,
                    modifier = Modifier
                        .size(132.dp, 108.dp)
                        .graphicsLayer {
                            scaleX = 0.6f + 0.4f * jump.value
                            scaleY = 0.6f + 0.4f * jump.value
                            translationY = (1f - jump.value) * 24.dp.toPx()
                        },
                )
            }
            Text(
                title,
                style = EolssuTheme.serif.yul.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            EolssuButton(
                action,
                onClick = {
                    onDismiss()
                    onAction()
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            )
        }
    }
}
