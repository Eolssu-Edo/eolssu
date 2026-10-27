package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuBadgeStyle
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_evaluable_badge
import org.jetbrains.compose.resources.stringResource

/**
 * 곡 카드(SONG-01 · SONG-03 제공 곡): 제목 · 난이도 배지 · 보조 문구 · 오른쪽 화살표.
 * [evaluableBadge]이면 제목 옆에 'AI 평가' 배지를 붙인다.
 */
@Composable
fun SongCard(song: Song, meta: String, onClick: () -> Unit, modifier: Modifier = Modifier, evaluableBadge: Boolean = false) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.background,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        song.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EolssuBadge(songLevelLabel(song.level))
                    if (evaluableBadge) EolssuBadge(stringResource(Res.string.mysongs_evaluable_badge), style = EolssuBadgeStyle.Dark)
                }
                Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textMuted)
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = 12.dp).size(18.dp), tint = colors.lineStrong)
        }
    }
}
