package com.ssafy.eolssu.feature.home.component

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
import androidx.compose.ui.graphics.Shape
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
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_select
import eolssu.shared.generated.resources.home_current_instrument
import eolssu.shared.generated.resources.home_my_page
import eolssu.shared.generated.resources.instrument_unknown
import org.jetbrains.compose.resources.stringResource

/** 태블릿 홈 '현재 악기' 표시. 바꾸기는 마이페이지(MY-01)로 옮겨 누를 수 없다. */
@Composable
internal fun InstrumentLabel(instrument: Instrument?, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(Res.string.home_current_instrument),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.weight(1f, fill = false),
        )
        Surface(shape = CircleShape, color = colors.surface, contentColor = colors.text) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(EolssuIcons.Flute, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(stringResource(instrument?.label ?: Res.string.instrument_unknown), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** 대표 그림 위 판(`.plate`): 그림이 비치는 반투명 흰 바탕, 모서리 12dp, 옅은 그림자. [onClick]이 있으면 누를 수 있다. */
@Composable
private fun HeroPlate(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = EolssuTheme.colors
    val color = colors.background.copy(alpha = 0.62f)
    if (onClick == null) {
        Surface(modifier, shape = shape, color = color, contentColor = colors.text, shadowElevation = 1.dp, content = content)
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, contentColor = colors.text, shadowElevation = 1.dp, content = content)
    }
}

/**
 * 휴대폰 홈 '현재 악기 단소' 판: 회색 제목과 굵은 악기 이름(13sp), 펼침 화살표를 한 줄로 읽는다.
 * 누르면 판 아래에 [instruments] 드롭다운이 열리고, 고르면 [onSelect]로 전달한 뒤 닫힌다. 저장 중([enabled]=false)에는 열리지 않는다.
 */
@Composable
internal fun InstrumentPlate(
    instrument: Instrument,
    instruments: List<Instrument>,
    enabled: Boolean,
    onSelect: (Instrument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    var open by remember { mutableStateOf(false) }
    val menuLabel = stringResource(Res.string.guide_instrument_select)
    val selectedLabel = stringResource(instrument.label)
    Box(modifier) {
        HeroPlate(
            Modifier.semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = menuLabel
                stateDescription = selectedLabel
            },
            onClick = { if (enabled) open = !open },
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                Text(stringResource(Res.string.home_current_instrument), style = style.copy(fontWeight = FontWeight.Medium), color = colors.textMuted)
                Text(selectedLabel, style = style.copy(fontWeight = FontWeight.SemiBold))
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
                    instruments.forEachIndexed { i, option ->
                        if (i > 0) HorizontalDivider(color = colors.lineSoft)
                        InstrumentMenuItem(stringResource(option.label), option == instrument) {
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

/** 메뉴를 기준 판의 오른쪽 끝에 맞춰 [gap]만큼 아래에 둔다. 화면 왼쪽을 넘으면 0에서 멈춘다. */
private class BelowAnchorEnd(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((anchorBounds.right - popupContentSize.width).coerceAtLeast(0), anchorBounds.bottom + gap)
}

/** 휴대폰 홈 마이페이지 판: 38dp 원, 사람 아이콘 16dp. 누르는 영역은 Material 최소 크기(48dp)를 유지한다. */
@Composable
internal fun MyPagePlate(onClick: () -> Unit, modifier: Modifier = Modifier) {
    HeroPlate(modifier.size(38.dp), CircleShape, onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(EolssuIcons.User, contentDescription = stringResource(Res.string.home_my_page), modifier = Modifier.size(16.dp))
        }
    }
}

/** 태블릿 홈 오른쪽 위 마이페이지 입구: 48dp 터치 영역. */
@Composable
internal fun MyPageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = colors.background,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Icon(EolssuIcons.User, contentDescription = stringResource(Res.string.home_my_page), modifier = Modifier.padding(12.dp))
    }
}
