package com.ssafy.eolssu.core.ui.component

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_label
import eolssu.shared.generated.resources.guide_instrument_select
import org.jetbrains.compose.resources.stringResource

/** 연습 악기 메뉴의 순서. 악기를 아직 확인하지 못했을 때의 기본 표시도 첫 항목(단소)이다. */
val PracticeInstruments: List<Instrument> = listOf(Instrument.Danso, Instrument.Sogeum, Instrument.DaegeumSanjo, Instrument.DaegeumJeongak)

/**
 * 상단 바 오른쪽 '악기 단소 ⌄' 알약(평가할 곡 고르기 · 연습곡 고르기). 누르면 알약 오른쪽 끝에 맞춘 메뉴가 아래로 열리고,
 * 고르면 [onSelect]로 전달한 뒤 닫힌다. 저장 중([enabled]=false)에는 열리지 않는다. 누르는 높이는 48dp를 유지한다.
 */
@Composable
fun PracticeInstrumentDropdown(
    instrument: Instrument?,
    enabled: Boolean,
    onSelect: (Instrument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val shown = instrument ?: PracticeInstruments.first()
    var open by remember { mutableStateOf(false) }
    val menuLabel = stringResource(Res.string.guide_instrument_select)
    val selectedLabel = stringResource(shown.label)
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = { if (enabled) open = !open },
            modifier = Modifier.semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = menuLabel
                stateDescription = selectedLabel
            },
            shape = CircleShape,
            color = colors.surface,
            contentColor = colors.text,
            border = BorderStroke(1.dp, colors.lineSoft),
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                Text(stringResource(Res.string.guide_instrument_label), style = style.copy(fontWeight = FontWeight.Medium), color = colors.textMuted, maxLines = 1)
                Text(selectedLabel, style = style.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(
                    EolssuIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).rotate(if (open) 180f else 0f),
                    tint = colors.textMuted,
                )
            }
        }
        if (open) {
            val shape = RoundedCornerShape(12.dp)
            val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { BelowAnchorEnd(gap) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(168.dp)
                        .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.dp, colors.lineSoft, shape)
                        .selectableGroup(),
                ) {
                    PracticeInstruments.forEachIndexed { i, option ->
                        if (i > 0) HorizontalDivider(color = colors.lineSoft)
                        InstrumentMenuItem(stringResource(option.label), option == shown) {
                            open = false
                            onSelect(option)
                        }
                    }
                }
            }
        }
    }
}

/** 드롭다운 한 줄(48dp). 고른 악기는 연두 바탕에 확인 표시다. */
@Composable
private fun InstrumentMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    val ink = if (selected) colors.onPrimary else colors.text
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
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = ink)
        if (selected) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = ink)
    }
}

/** 메뉴를 기준 알약의 오른쪽 끝에 맞춰 [gap]만큼 아래에 둔다. 화면 왼쪽을 넘으면 0에서 멈춘다. */
private class BelowAnchorEnd(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((anchorBounds.right - popupContentSize.width).coerceAtLeast(0), anchorBounds.bottom + gap)
}
