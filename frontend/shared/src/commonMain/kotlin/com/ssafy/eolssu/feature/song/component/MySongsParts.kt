package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.feature.song.MySongsTab
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_add
import eolssu.shared.generated.resources.mysongs_tab_sheets
import eolssu.shared.generated.resources.mysongs_tab_songs
import org.jetbrains.compose.resources.stringResource

/** SONG-03 두 칸 탭(`.tabs`, `role=tablist`): 흰 바탕 옅은 테두리 틀 안에서 고른 칸만 연두 바탕. 개수는 회색으로 붙인다. */
@Composable
internal fun MySongsTabSwitch(
    tab: MySongsTab,
    songCount: Int,
    sheetCount: Int,
    onSelect: (MySongsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    val frame = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, frame)
            .border(1.dp, colors.lineSoft, frame)
            .padding(4.dp),
    ) {
        MySongsTab.entries.forEach { entry ->
            val selected = tab == entry
            val (label, count) = when (entry) {
                MySongsTab.Songs -> stringResource(Res.string.mysongs_tab_songs) to songCount
                MySongsTab.Sheets -> stringResource(Res.string.mysongs_tab_sheets) to sheetCount
            }
            Surface(
                onClick = { onSelect(entry) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                shape = RoundedCornerShape(11.dp),
                color = if (selected) colors.primary else Color.Transparent,
                contentColor = if (selected) colors.onPrimary else colors.textSub,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)
                    Text(label, style = style.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium))
                    Text("$count", style = style.copy(fontWeight = FontWeight.Medium), color = colors.textMuted)
                }
            }
        }
    }
}

/** '악보 추가 · PDF, 음원, 직접 만들기'(`.btn.outline`): 52dp, 흰 바탕에 테두리, 모서리 14dp, 더하기 20dp. */
@Composable
internal fun AddSheetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EolssuIcons.Plus, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(Res.string.mysongs_add), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
