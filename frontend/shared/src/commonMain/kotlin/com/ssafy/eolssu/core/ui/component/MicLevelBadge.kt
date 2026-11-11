package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.practice_mic_off
import eolssu.shared.generated.resources.practice_mic_on
import org.jetbrains.compose.resources.stringResource

/**
 * 마이크 입력 뱃지(`.badge.lite`, 30dp): 마이크 아이콘 · 소리 크기만큼 오르내리는 3dp 막대 5개 · ‘입력 없음’/‘입력 감지’.
 * 연주 평가와 기초부터 배우기 연습(S15P31E102-749)이 같은 모양을 쓴다. [level]은 0~1이고 0이면 입력 없음이다.
 */
@Composable
fun MicLevelBadge(level: Float, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    val off = level <= 0f
    Row(
        modifier
            .height(30.dp)
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.lineSoft, CircleShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(EolssuIcons.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.textMuted)
        Row(Modifier.height(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(0.4f, 0.8f, 1f, 0.6f, 0.3f).forEach { f ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((level.coerceIn(0f, 1f) * f * 14).coerceAtLeast(2f).dp)
                        .background(if (off) colors.textMuted else colors.rec, RoundedCornerShape(1.dp)),
                )
            }
        }
        Text(
            stringResource(if (off) Res.string.practice_mic_off else Res.string.practice_mic_on),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = colors.textMuted,
        )
    }
}
