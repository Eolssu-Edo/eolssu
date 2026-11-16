package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.ui.component.InstrumentMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.ui.component.EolssuMenuPopup
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.InstrumentPicture
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_label
import eolssu.shared.generated.resources.guide_instrument_select
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.feature.guide.FingeringInstrumentOption

/**
 * '악기' + 드롭다운(`.fg-select`). 메뉴는 버튼 바로 아래에 열리고(아래가 모자라면 위로), 바깥을 누르면 [onDismiss]로 닫힌다.
 * 운지법 보기와 기초부터 배우기가 함께 쓴다. 선택 칸과 목록에 기초부터 배우기처럼 악기 그림을 함께 둔다(S15P31E102-768).
 */
@Composable
internal fun InstrumentDropdown(
    selected: GuideInstrument,
    open: Boolean,
    onToggle: () -> Unit,
    onSelect: (GuideInstrument) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    options: List<FingeringInstrumentOption>? = null,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val selectedLabel = options?.firstOrNull { it.instrument == selected }?.name ?: stringResource(selected.label)
    val menuLabel = stringResource(Res.string.guide_instrument_select)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(Res.string.guide_instrument_label),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textMuted,
        )
        var anchorWidth by remember { mutableIntStateOf(0) }
        Box(Modifier.weight(1f).onSizeChanged { anchorWidth = it.width }) {
            Surface(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .semantics {
                        role = Role.DropdownList
                        contentDescription = menuLabel
                        stateDescription = selectedLabel
                    },
                shape = shape,
                color = colors.surface,
            ) {
                Row(Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstrumentPicture(selected)
                    Text(
                        selectedLabel,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Icon(
                        EolssuIcons.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).rotate(if (open) 180f else 0f),
                        tint = colors.textMuted,
                    )
                }
            }
            if (open) {
                val width = with(LocalDensity.current) { anchorWidth.toDp() }
                EolssuMenuPopup(onDismissRequest = onDismiss) {
                    EolssuMenuPanel(width = width) {
                        (options?.map { it.instrument } ?: GuideInstrument.entries).forEach { instrument ->
                            val label = options?.firstOrNull { it.instrument == instrument }?.name ?: stringResource(instrument.label)
                            InstrumentMenuItem(instrument, instrument == selected, onClick = { onSelect(instrument) }, label = label)
                        }
                    }
                }
            }
        }
    }
}
