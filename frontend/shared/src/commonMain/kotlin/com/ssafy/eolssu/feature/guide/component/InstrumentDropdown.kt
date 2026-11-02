package com.ssafy.eolssu.feature.guide.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_label
import eolssu.shared.generated.resources.guide_instrument_select
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.label

/**
 * '악기' + 드롭다운(`.fg-select`). 메뉴는 버튼 바로 아래에 겹쳐 열리고, 바깥을 누르면 [onDismiss]로 닫힌다.
 * 운지법 보기와 기초부터 배우기가 함께 쓴다.
 */
@Composable
internal fun InstrumentDropdown(
    selected: GuideInstrument,
    open: Boolean,
    onToggle: () -> Unit,
    onSelect: (GuideInstrument) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val selectedLabel = stringResource(selected.label)
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
                border = BorderStroke(1.dp, colors.lineSoft),
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
                val width = with(LocalDensity.current) { anchorWidth.toDp() }
                Popup(
                    popupPositionProvider = remember(gap) { BelowAnchor(gap) },
                    onDismissRequest = onDismiss,
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier
                            .width(width)
                            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                            .clip(shape)
                            .background(colors.surface)
                            .border(1.dp, colors.lineSoft, shape),
                    ) {
                        GuideInstrument.entries.forEachIndexed { i, instrument ->
                            if (i > 0) HorizontalDivider(color = colors.lineSoft)
                            DropdownItem(stringResource(instrument.label), instrument == selected) { onSelect(instrument) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (selected) colors.primary else colors.surface)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        if (selected) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

/** 메뉴를 기준 영역 왼쪽 아래에 [gap]만큼 띄워 둔다. */
private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(anchorBounds.left, anchorBounds.bottom + gap)
}
