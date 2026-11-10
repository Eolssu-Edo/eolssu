package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import org.jetbrains.compose.resources.stringResource

/**
 * 악기 고르기(`.au-inst`): 악기 네 개를 한 줄에 같은 너비 · 56dp 높이 · 14dp 모서리(주 버튼과 같은 모양)로 8dp 간격을 두고 놓는다.
 * 고르지 않은 칸은 흰 바탕 · 보조 버튼과 같은 테두리, 고른 칸은 연두 바탕 · 흰 굵은 글자(`.on`)라 한눈에 구분된다.
 * 좁은 휴대폰에서도 '산조대금'이 한 줄에 들어가도록 글자는 칸 너비에 맞춰 12~17sp 사이로 줄어든다. [label]이 있으면 위에, [help]는 아래에 둔다.
 * 가입 프로필 설정(AUTH-07)이 쓴다.
 */
@Composable
fun InstrumentPicker(
    selected: Instrument?,
    onSelect: (Instrument) -> Unit,
    help: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    instruments: List<Instrument> = Instrument.entries,
    enabled: Boolean = true,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
        }
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            instruments.forEach { instrument ->
                val isSelected = instrument == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(instrument) }),
                    shape = shape,
                    color = if (isSelected) colors.primary else colors.surface,
                    contentColor = if (isSelected) colors.onPrimary else colors.text,
                    border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.line),
                ) {
                    Box(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        BasicText(
                            stringResource(instrument.label),
                            style = EolssuTheme.serif.headline.copy(
                                fontSize = 17.sp,
                                lineHeight = 24.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = LocalContentColor.current,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            softWrap = false,
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 17.sp, stepSize = 0.5.sp),
                        )
                    }
                }
            }
        }
        Text(help, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = colors.textMuted)
    }
}
