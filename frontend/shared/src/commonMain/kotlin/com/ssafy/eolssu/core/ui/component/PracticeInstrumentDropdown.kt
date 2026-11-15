package com.ssafy.eolssu.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import eolssu.shared.generated.resources.guide_instrument_select
import org.jetbrains.compose.resources.stringResource

/** 연습 악기 메뉴의 순서. 악기를 아직 확인하지 못했을 때의 기본 표시도 첫 항목(단소)이다. */
val PracticeInstruments: List<Instrument> = listOf(Instrument.Danso, Instrument.Sogeum, Instrument.DaegeumSanjo, Instrument.DaegeumJeongak)

/**
 * 상단 바 오른쪽 '(악기 그림) 단소 ⌄' 알약(평가할 곡 고르기 · 연습곡 고르기). 선택값과 목록 모두 악기 그림을 함께 둔다(S15P31E102-831).
 * 그림이 '악기' 글자를 대신해 알약 폭을 전과 비슷하게 두고(가운데 제목이 잘리지 않게), 읽기 도구에는 '악기 선택'으로 읽힌다.
 * 누르면 알약 오른쪽 끝에 맞춘 메뉴가 아래로 열리고,
 * 고르면 [onSelect]로 전달한 뒤 닫힌다. 저장 중([enabled]=false)에는 열리지 않는다. 누르는 높이는 48dp를 유지한다.
 * [bordered]가 false면 테두리 없는 흰 알약이다(연습곡 고르기의 회색 바탕).
 * 창 너비가 [NarrowPhoneWidth]보다 좁으면 선택값의 악기 그림을 빼고 글자만 두어 알약을 줄인다(S15P31E102-924, 가운데 제목이 잘리지 않게). 메뉴 목록은 그대로다.
 */
@Composable
fun PracticeInstrumentDropdown(
    instrument: Instrument?,
    enabled: Boolean,
    onSelect: (Instrument) -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val colors = EolssuTheme.colors
    val shown = instrument ?: PracticeInstruments.first()
    var open by remember { mutableStateOf(false) }
    val menuLabel = stringResource(Res.string.guide_instrument_select)
    val selectedLabel = stringResource(shown.label)
    val narrow = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } < NarrowPhoneWidth
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
            border = if (bordered) BorderStroke(1.dp, colors.lineSoft) else null,
        ) {
            Row(
                Modifier.padding(start = if (narrow) 12.dp else 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!narrow) InstrumentPicture(shown, size = InstrumentPictureCompactSize)
                Text(
                    selectedLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    EolssuIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).rotate(if (open) 180f else 0f),
                    tint = colors.textMuted,
                )
            }
        }
        if (open) {
            val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { BelowAnchorEnd(gap) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                EolssuMenuPanel(width = 168.dp) {
                    PracticeInstruments.forEach { option ->
                        InstrumentMenuItem(option, option == shown, onClick = {
                            open = false
                            onSelect(option)
                        })
                    }
                }
            }
        }
    }
}

/** 이 너비보다 좁은 휴대폰 창에서는 선택값 알약에서 악기 그림을 뺀다. 360dp 휴대폰은 그림을 둔 채로 제목이 들어간다. */
private val NarrowPhoneWidth = 360.dp

/** 메뉴를 기준 알약의 오른쪽 끝에 맞춰 [gap]만큼 아래에 둔다. 화면 왼쪽을 넘으면 0에서 멈춘다. */
private class BelowAnchorEnd(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset((anchorBounds.right - popupContentSize.width).coerceAtLeast(0), anchorBounds.bottom + gap)
}
