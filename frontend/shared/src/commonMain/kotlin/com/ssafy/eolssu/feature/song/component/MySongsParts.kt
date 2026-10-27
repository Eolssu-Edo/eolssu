package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

/** SONG-03 두 칸 탭(`role=tablist`): 고른 칸은 흰 바탕에 그림자. */
@Composable
internal fun MySongsTabSwitch(
    tab: MySongsTab,
    songCount: Int,
    sheetCount: Int,
    onSelect: (MySongsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EolssuTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MySongsTab.entries.forEach { entry ->
            val selected = tab == entry
            val label = when (entry) {
                MySongsTab.Songs -> stringResource(Res.string.mysongs_tab_songs, songCount)
                MySongsTab.Sheets -> stringResource(Res.string.mysongs_tab_sheets, sheetCount)
            }
            val shape = RoundedCornerShape(9.dp)
            Surface(
                onClick = { onSelect(entry) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (selected) Modifier.shadow(2.dp, shape, ambientColor = SelectedTabShadow, spotColor = SelectedTabShadow) else Modifier)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                shape = shape,
                color = if (selected) colors.background else Color.Transparent,
                contentColor = if (selected) colors.text else colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

private val SelectedTabShadow = Color.Black.copy(alpha = 0.12f)

/** 점선 테두리의 '악보 추가' 버튼. 휴대폰 56dp, 태블릿 세로 60dp, 태블릿 가로(탭 오른쪽) 48dp·모서리 14dp. */
@Composable
internal fun AddSheetButton(onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp, cornerRadius: Dp = 16.dp) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height).dashedBorder(colors.lineStrong, cornerRadius),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent,
        contentColor = colors.text,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(EolssuIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.mysongs_add), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
