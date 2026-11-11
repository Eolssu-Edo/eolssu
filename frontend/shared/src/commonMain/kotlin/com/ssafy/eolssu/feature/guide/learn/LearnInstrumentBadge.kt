package com.ssafy.eolssu.feature.guide.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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

/**
 * 낙관 칸에 그릴 악기 모양(S15P31E102-749). 한자 대신 생김새로 악기를 구별한다.
 * [length]는 칸 대각선 대비 길이, [thickness]는 칸 한 변 대비 굵기, [holes]는 앞 지공 수.
 * 단소는 짧고 굵으며 위 끝이 U자로 파였고, 소금은 짧고 가늘며 취구가 있다. 대금은 길고 취구와 청공이 있으며, 산조대금은 정악대금보다 조금 짧다.
 */
private class FluteShape(val length: Float, val thickness: Float, val holes: Int, val mouth: Boolean, val cheong: Boolean, val notch: Boolean)

private val GuideInstrument.flute: FluteShape
    get() = when (this) {
        GuideInstrument.Danso -> FluteShape(length = 0.78f, thickness = 0.17f, holes = 4, mouth = false, cheong = false, notch = true)
        GuideInstrument.Sogeum -> FluteShape(length = 0.74f, thickness = 0.11f, holes = 6, mouth = true, cheong = false, notch = false)
        GuideInstrument.DaegeumJeongak -> FluteShape(length = 0.98f, thickness = 0.13f, holes = 6, mouth = true, cheong = true, notch = false)
        GuideInstrument.DaegeumSanjo -> FluteShape(length = 0.88f, thickness = 0.13f, holes = 6, mouth = true, cheong = true, notch = false)
    }

/** 악기마다 다른 낙관 색. 달리기 지도 결승선 깃발도 이 색이다. */
internal fun GuideInstrument.sealColor(colors: EolssuColors): Color = when (this) {
    GuideInstrument.Danso -> colors.rec
    GuideInstrument.Sogeum -> colors.rhythmError
    GuideInstrument.DaegeumJeongak -> colors.play
    GuideInstrument.DaegeumSanjo -> colors.pitchError
}

/** 악기 낙관: 악기 색 칸에 흰 악기 모양을 비스듬히 그린다. 그림은 꾸밈이라 읽지 않고, 옆의 악기 이름이 읽힌다. */
@Composable
internal fun InstrumentSeal(instrument: GuideInstrument, size: Dp = 26.dp) {
    val colors = EolssuTheme.colors
    val seal = instrument.sealColor(colors)
    val flute = instrument.flute
    Canvas(Modifier.size(size).clip(RoundedCornerShape(5.dp)).background(seal)) {
        val side = this.size.minDimension
        val diagonal = side * 1.4142f
        val length = diagonal * flute.length * 0.82f
        val thick = side * flute.thickness
        val left = center.x - length / 2
        val top = center.y - thick / 2
        rotate(-45f) {
            drawRoundRect(Color.White, topLeft = Offset(left, top), size = Size(length, thick), cornerRadius = CornerRadius(thick / 2))
            val hole = thick * 0.26f
            // 위 끝(오른쪽 위): 단소는 U자 취구 홈, 소금·대금은 둥근 취구.
            val head = left + length - thick * 0.9f
            if (flute.notch) drawCircle(seal, radius = thick * 0.32f, center = Offset(left + length, center.y))
            if (flute.mouth) drawCircle(seal, radius = hole * 1.15f, center = Offset(head, center.y))
            if (flute.cheong) drawRect(seal, topLeft = Offset(head - thick * 1.5f - hole, center.y - hole * 0.7f), size = Size(hole * 2, hole * 1.4f))
            // 앞 지공은 아래 끝 쪽 절반에 고르게 놓는다.
            val start = left + thick * 1.1f
            val span = length * 0.48f
            repeat(flute.holes) { i ->
                val x = start + span * (i + 0.5f) / flute.holes
                drawCircle(seal, radius = hole, center = Offset(x, center.y))
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
