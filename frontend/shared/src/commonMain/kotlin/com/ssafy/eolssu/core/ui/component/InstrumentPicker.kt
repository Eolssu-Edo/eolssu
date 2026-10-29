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
 * 악기 고르기(`.au-inst`): 세 칸 76dp, 고른 칸은 흰 바탕에 굵은 테두리. [label]이 있으면 위에, [help]는 아래에 둔다.
 * 가입 프로필 설정(AUTH-07)과 마이페이지 연습 악기(MY-01)가 함께 쓴다.
 */
@Composable
fun InstrumentPicker(
    selected: Instrument,
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
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            instruments.forEach { instrument ->
                val isSelected = instrument == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(76.dp)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(instrument) }),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) colors.background else colors.surface,
                    contentColor = colors.text,
                    border = if (isSelected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.line),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(instrument.label), style = EolssuTheme.serif.headline.copy(fontSize = 21.sp, lineHeight = 28.sp))
                    }
                }
            }
        }
        Text(help, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = colors.textMuted)
    }
}
