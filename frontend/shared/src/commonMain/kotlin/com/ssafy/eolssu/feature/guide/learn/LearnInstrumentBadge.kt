package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.core.theme.EolssuColors
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_select
import org.jetbrains.compose.resources.stringResource

/** 악기마다 다른 낙관(도장) 글자. 위아래 두 글자로 찍는다. */
internal val GuideInstrument.sealText: String
    get() = when (this) {
        GuideInstrument.Danso -> "短簫"
        GuideInstrument.Sogeum -> "小笒"
        GuideInstrument.DaegeumJeongak -> "正笒"
        GuideInstrument.DaegeumSanjo -> "散笒"
    }

/** 악기마다 다른 낙관 색. 달리기 지도 결승선 깃발도 이 색이다. */
internal fun GuideInstrument.sealColor(colors: EolssuColors): Color = when (this) {
    GuideInstrument.Danso -> colors.rec
    GuideInstrument.Sogeum -> colors.rhythmError
    GuideInstrument.DaegeumJeongak -> colors.play
    GuideInstrument.DaegeumSanjo -> colors.pitchError
}

/** 악기 낙관. 글자는 꾸밈이라 읽지 않고, 옆의 악기 이름이 읽힌다. */
@Composable
internal fun InstrumentSeal(instrument: GuideInstrument, size: Dp = 26.dp) {
    val colors = EolssuTheme.colors
    Box(
        Modifier.size(size).clip(RoundedCornerShape(5.dp)).background(instrument.sealColor(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy((-3).dp)) {
            instrument.sealText.forEach {
                Text(
                    it.toString(),
                    style = EolssuTheme.serif.yul.copy(fontSize = (size.value * 0.36f).sp, lineHeight = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * 기초부터 배우기의 작은 악기 고르기: 낙관 · 악기 이름 · 펼침 표시. 누르면 아래에 악기 목록이 열린다.
 * 진도는 악기마다 따로라 낙관 색과 글자로 지금 어느 악기의 길인지 알려 준다.
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
    // 낙관 모서리(5dp)에 칩 안쪽 여백(6dp)을 더해 두 모서리가 같은 중심으로 둥글게 한다.
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
            border = BorderStroke(1.dp, colors.lineSoft),
        ) {
            Row(Modifier.padding(start = 6.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InstrumentSeal(selected)
                Text(selectedLabel, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Icon(EolssuIcons.ChevronDown, contentDescription = null, Modifier.size(12.dp).rotate(if (open) 180f else 0f), tint = colors.textMuted)
            }
        }
        if (open) {
            val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
            val shape = RoundedCornerShape(14.dp)
            Popup(
                popupPositionProvider = remember(gap) { BelowStart(gap) },
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier
                        .width(200.dp)
                        .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.dp, colors.lineSoft, shape),
                ) {
                    GuideInstrument.entries.forEachIndexed { i, instrument ->
                        if (i > 0) HorizontalDivider(color = colors.lineSoft)
                        val isSelected = instrument == selected
                        Row(
                            Modifier
                                .height(52.dp)
                                .width(200.dp)
                                .background(if (isSelected) colors.accentSoft else colors.surface)
                                .clickable(role = Role.RadioButton) { onSelect(instrument) }
                                .semantics { this.selected = isSelected }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            InstrumentSeal(instrument)
                            Text(
                                stringResource(instrument.label),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                            )
                            if (isSelected) Icon(EolssuIcons.Check, contentDescription = null, Modifier.size(16.dp))
                        }
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
