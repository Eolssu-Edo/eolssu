package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme

/**
 * 버튼 모양(`.btn.primary` · `.secondary` · `.text` · `.danger` · `.ghost`).
 * [Text]는 옛 와이어프레임의 48dp 글자 버튼, [Ghost]는 연두 보드 `.btn.ghost`(52dp, 투명 바탕, `textSub` 500)다.
 */
enum class EolssuButtonStyle { Primary, Secondary, Text, Danger, Ghost }

/**
 * 연두 디자인 버튼. 최소 48dp 터치 영역을 유지하고 글꼴 확대 시 내용에 맞게 높아진다.
 * [containerColor]·[borderColor]·[contentColor]를 주면 모양별 기본 색 대신 쓴다.
 * [busy]이면 글자 대신 도는 표시를 보이고 누를 수 없다. [minHeight]를 주면 모양별 기본 높이 대신 쓴다. 좁은 칸에 두 개를 나란히 둘 때는 [compact]로 옆 여백을 줄인다.
 * [squash]는 누르면 바로 소리가 나는 버튼에만 켠다([elasticSquash], S15P31E102-891).
 * [singleLine]이면 큰 글꼴·좁은 칸에서도 한 줄을 유지하고 넘치는 만큼 글자를 줄인다(S15P31E102-972).
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
    minHeight: Dp? = null,
    containerColor: Color? = null,
    squash: Boolean = false,
    singleLine: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val press = remember { MutableInteractionSource() }
    val (container, content, border) = when (style) {
        EolssuButtonStyle.Primary -> Triple(colors.primary, colors.onPrimary, colors.primary)
        // 보드 `.btn.secondary`는 흰 바탕(`#fff`)에 옅은 테두리다.
        EolssuButtonStyle.Secondary -> Triple(colors.surface, colors.text, colors.line)
        EolssuButtonStyle.Text -> Triple(Color.Transparent, colors.textMuted, Color.Transparent)
        EolssuButtonStyle.Danger -> Triple(colors.rec, colors.onRec, colors.rec)
        EolssuButtonStyle.Ghost -> Triple(Color.Transparent, colors.textSub, Color.Transparent)
    }
    val height = minHeight ?: when {
        style == EolssuButtonStyle.Text -> 48.dp
        small -> 48.dp
        else -> 52.dp
    }
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .then(if (squash) Modifier.elasticSquash(press, widthFactor = ElasticSquashTextWidth) else Modifier)
            .then(if (small) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(if (small) 12.dp else 14.dp),
        color = containerColor ?: container,
        contentColor = contentColor ?: content,
        border = BorderStroke(1.dp, borderColor ?: border),
        interactionSource = press,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(PaddingValues(horizontal = if (small) 14.dp else if (compact) 10.dp else 20.dp, vertical = 12.dp))) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = contentColor ?: content, strokeWidth = 2.dp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    val fontSize = if (small) 14.sp else 15.sp
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = fontSize,
                            fontWeight = if (style == EolssuButtonStyle.Text || style == EolssuButtonStyle.Ghost) FontWeight.Medium else FontWeight.SemiBold,
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        softWrap = !singleLine,
                        autoSize = if (singleLine) TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = fontSize, stepSize = 0.5.sp) else null,
                    )
                }
            }
        }
    }
}
