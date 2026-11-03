package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import org.jetbrains.compose.resources.stringResource

/**
 * 악기 고르기(`.au-inst`): 세 칸 76dp, 흰 바탕 · 옅은 테두리이고 고른 칸은 연두 바탕(`.on`)이다. [label]이 있으면 위에, [help]는 아래에 둔다.
 * 가입 프로필 설정(AUTH-07)과 마이페이지 연습 악기(MY-01)가 함께 쓴다.
 */
@Composable
fun InstrumentPicker(
    selected: Instrument?,
    onSelect: (Instrument) -> Unit,
    help: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    instruments: List<Instrument> = Instrument.entries,
) {
    val colors = EolssuTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = colors.text,
            )
        }
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            instruments.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { instrument ->
                        val isSelected = instrument == selected
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp)
                                .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(instrument) }),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) colors.primary else colors.surface,
                            contentColor = if (isSelected) colors.onPrimary else colors.text,
                            border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.lineSoft),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(instrument.label),
                                    style = EolssuTheme.serif.headline.copy(
                                        fontSize = 21.sp,
                                        lineHeight = 28.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(help, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = colors.textMuted)
    }
}
