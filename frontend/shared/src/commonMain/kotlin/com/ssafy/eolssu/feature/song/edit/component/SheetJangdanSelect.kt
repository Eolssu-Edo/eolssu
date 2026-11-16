package com.ssafy.eolssu.feature.song.edit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuMenuItem
import com.ssafy.eolssu.core.ui.component.EolssuMenuPanel
import com.ssafy.eolssu.core.ui.component.EolssuMenuPopup
import com.ssafy.eolssu.data.song.CatalogJangdanSummary
import com.ssafy.eolssu.feature.song.edit.defaultTempoLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.sheet_upload_jangdan_placeholder
import eolssu.shared.generated.resources.sheet_upload_jangdan_unsure
import org.jetbrains.compose.resources.stringResource

/**
 * 악보 올리기의 장단 고르기 칸(S15P31E102-997). 장단 12개를 칩으로 늘어놓으면 카드가 휑하게 길어져
 * 고른 장단 한 줄만 보이고, 누르면 악기 선택과 같은 공통 메뉴가 칸 아래로 펼쳐진다. 목록이 길어 메뉴 안에서 스크롤한다.
 */
@Composable
internal fun SheetJangdanSelect(
    jangdans: List<CatalogJangdanSummary>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
    unsure: Boolean = false,
    onUnsure: (() -> Unit)? = null,
) {
    val colors = EolssuTheme.colors
    var open by remember { mutableStateOf(false) }
    val selected = jangdans.firstOrNull { it.jangdanId == selectedId }
    val unsureLabel = stringResource(Res.string.sheet_upload_jangdan_unsure)
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.groupedBackground)
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (unsure && selected != null) unsureLabel else selected?.name ?: stringResource(Res.string.sheet_upload_jangdan_placeholder),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = if (selected != null) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = when {
                    selected != null -> colors.text
                    isError -> colors.recText
                    else -> colors.textMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textSub)
        }
        if (open) EolssuMenuPopup(onDismissRequest = { open = false }, gap = 6.dp) {
            EolssuMenuPanel(width = 240.dp) {
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 장단을 모르는 사람을 위해 맨 위에 둔다. 고르면 추천 장단 굿거리로 올린다(S15P31E102-1054).
                    if (onUnsure != null) EolssuMenuItem(unsureLabel, selected = unsure, onClick = { open = false; onUnsure() })
                    jangdans.forEach { item ->
                        // 정간보는 장단마다 기본 빠르기가 달라 이름 옆에 얼쑤 기본 빠르기를 함께 보인다(예: 굿거리 · ♩.=66).
                        val label = item.defaultTempoLabel()?.let { "${item.name} · $it" } ?: item.name
                        EolssuMenuItem(label, selected = !unsure && item.jangdanId == selectedId, onClick = { open = false; onSelect(item.jangdanId) })
                    }
                }
            }
        }
    }
}
