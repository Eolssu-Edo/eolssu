package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 버튼 모양(`.btn.primary` · `.secondary` · `.text` · `.danger`). */
enum class EolssuButtonStyle { Primary, Secondary, Text, Danger }

/**
 * 연두 디자인 버튼. 최소 48dp 터치 영역을 유지하고 글꼴 확대 시 내용에 맞게 높아진다.
 * [busy]이면 글자 대신 도는 표시를 보이고 누를 수 없다. 좁은 칸에 두 개를 나란히 둘 때는 [compact]로 옆 여백을 줄인다.
 */
@Composable
fun EolssuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: EolssuButtonStyle = EolssuButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    small: Boolean = false,
    busy: Boolean = false,
    compact: Boolean = false,
    contentColor: Color? = null,
    borderColor: Color? = null,
) {
    val colors = EolssuTheme.colors
    val (container, content, border) = when (style) {
        EolssuButtonStyle.Primary -> Triple(colors.primary, colors.onPrimary, colors.primary)
        EolssuButtonStyle.Secondary -> Triple(colors.background, colors.text, colors.line)
        EolssuButtonStyle.Text -> Triple(Color.Transparent, colors.textMuted, Color.Transparent)
        EolssuButtonStyle.Danger -> Triple(colors.rec, colors.onRec, colors.rec)
    }
    val height = when {
        style == EolssuButtonStyle.Text -> 48.dp
        small -> 48.dp
        else -> 52.dp
    }
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .then(if (small) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(if (small) 12.dp else 14.dp),
        color = container,
        contentColor = contentColor ?: content,
        border = BorderStroke(1.dp, borderColor ?: border),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(PaddingValues(horizontal = if (small) 14.dp else if (compact) 10.dp else 20.dp, vertical = 12.dp))) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = contentColor ?: content, strokeWidth = 2.dp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = if (small) 14.sp else 15.sp,
                            fontWeight = if (style == EolssuButtonStyle.Text) FontWeight.Medium else FontWeight.SemiBold,
                        ),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
