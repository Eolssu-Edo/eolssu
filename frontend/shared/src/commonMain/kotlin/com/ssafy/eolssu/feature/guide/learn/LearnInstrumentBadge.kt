package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.ssafy.eolssu.core.ui.component.InstrumentMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.InstrumentPicture
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_select
import org.jetbrains.compose.resources.stringResource

/**
 * 기초부터 배우기의 작은 악기 고르기: 악기 그림 · 악기 이름 · 펼침 표시. 누르면 아래에 악기 목록이 열린다.
 * 진도는 악기마다 따로라 악기 그림과 이름으로 지금 어느 악기의 길인지 알려 준다.
 */
@Composable
internal fun LearnInstrumentChip(
    selected: GuideInstrument,
    open: Boolean,
    onToggle: () -> Unit,
    onSelect: (GuideInstrument) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val selectedLabel = stringResource(selected.label)
    val menuLabel = stringResource(Res.string.guide_instrument_select)
    val pill = RoundedCornerShape(11.dp)
    Box(modifier) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.height(38.dp).semantics {
                role = Role.DropdownList
                contentDescription = menuLabel
                stateDescription = selectedLabel
            },
            shape = pill,
            color = colors.surface,
        ) {
            Row(Modifier.padding(start = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InstrumentPicture(selected)
                Text(selectedLabel, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Icon(EolssuIcons.ChevronDown, contentDescription = null, Modifier.size(12.dp).rotate(if (open) 180f else 0f), tint = colors.textMuted)
            }
        }
        if (open) {
            val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { BelowStart(gap) },
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                EolssuMenuPanel(width = 200.dp) {
                    GuideInstrument.entries.forEach { instrument ->
                        InstrumentMenuItem(instrument, instrument == selected, onClick = { onSelect(instrument) })
                    }
                }
            }
        }
    }
}

/** 메뉴를 기준 영역 왼쪽 아래에 [gap]만큼 띄워 둔다. */
private class BelowStart(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(anchorBounds.left, anchorBounds.bottom + gap)
}
