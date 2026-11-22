package com.ssafy.eolssu.feature.guide.component

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.ui.component.InstrumentMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuAlign
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.ui.component.EolssuMenuPopup
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.InstrumentPicture
import com.ssafy.eolssu.core.ui.component.InstrumentPictureCompactSize
import com.ssafy.eolssu.core.model.fingering.GuideInstrument
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.guide_instrument_select
import org.jetbrains.compose.resources.stringResource
import com.ssafy.eolssu.core.model.fingering.label
import com.ssafy.eolssu.feature.guide.FingeringInstrumentOption

/**
 * 운지법 보기 상단 바 오른쪽의 '(악기 그림) 단소 ⌄' 알약. 곡 고르기 화면의 연습 악기 알약
 * ([com.ssafy.eolssu.core.ui.component.PracticeInstrumentDropdown])과 모양을 맞춘다(S15P31E102-1245).
 * 메뉴는 알약 오른쪽 끝에 맞춰 아래로 열리고(아래가 모자라면 위로), 바깥을 누르면 [onDismiss]로 닫힌다.
 * 악기 목록을 받기 전에는 [enabled]를 끄고, 받는 중이면 [loading]으로 꺽쇠 대신 작은 로딩 표시를 둔다.
 * 놓인 자리가 좁으면 그림과 꺽쇠는 두고 악기 이름만 말줄임한다. 누르는 높이는 48dp를 유지한다.
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
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val selectedLabel = options?.firstOrNull { it.instrument == selected }?.name ?: stringResource(selected.label)
    val menuLabel = stringResource(Res.string.guide_instrument_select)
    Box(modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = UserActionLog.click(QaTarget.INSTRUMENT_DROPDOWN_01, onToggle),
            enabled = enabled,
            modifier = Modifier.semantics(mergeDescendants = true) {
                role = Role.DropdownList
                contentDescription = menuLabel
                stateDescription = selectedLabel
            },
            shape = CircleShape,
            // 회색 바탕 위에 놓이므로 연습곡 고르기처럼 테두리 없는 흰 알약으로 둔다.
            color = colors.surface,
            contentColor = colors.text,
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InstrumentPicture(selected, size = InstrumentPictureCompactSize)
                Text(
                    selectedLabel,
                    // 이름을 나중에 재서 꺽쇠 자리를 먼저 남긴다.
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loading) CircularProgressIndicator(
                    Modifier.size(12.dp),
                    color = colors.textMuted,
                    trackColor = colors.lineSoft,
                    strokeWidth = 2.dp,
                ) else Icon(
                    EolssuIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp).rotate(if (open) 180f else 0f),
                    tint = colors.textMuted,
                )
            }
        }
        if (open) {
            EolssuMenuPopup(onDismissRequest = onDismiss, align = EolssuMenuAlign.End) {
                EolssuMenuPanel(width = 168.dp) {
                    (options?.map { it.instrument } ?: GuideInstrument.entries).forEach { instrument ->
                        val label = options?.firstOrNull { it.instrument == instrument }?.name ?: stringResource(instrument.label)
                        InstrumentMenuItem(instrument, instrument == selected, onClick = UserActionLog.click(QaTarget.INSTRUMENT_DROPDOWN_02, { onSelect(instrument) }), label = label)
                    }
                }
            }
        }
    }
}
