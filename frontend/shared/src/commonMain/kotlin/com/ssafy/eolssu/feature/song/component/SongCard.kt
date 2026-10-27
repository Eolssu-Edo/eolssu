package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
 * [selected]는 태블릿 가로 두 칸 배치에서 오른쪽에 정보를 보이는 곡(굵은 테두리·회색 바탕)이다.
 * [large]는 태블릿 카드 격자(여백 18dp, 제목 18sp)이고, 카드 높이가 [minHeight]보다 크면 제목과 보조 문구를 위아래 반씩에 나눠 둔다.
 */
@Composable
fun SongCard(
    song: Song,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    evaluableBadge: Boolean = false,
    selected: Boolean = false,
    large: Boolean = false,
    minHeight: Dp = 0.dp,
) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().then(if (selected) Modifier.semantics { this.selected = true } else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.surface else colors.background,
        border = if (selected) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.line),
    ) {
        val padding = if (large) 18.dp else 16.dp
        Row(Modifier.padding(padding), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).heightIn(min = (minHeight - padding * 2).coerceAtLeast(0.dp)),
                verticalArrangement = if (large) StretchedRows else Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        song.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = if (large) 18.sp else 17.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EolssuBadge(songLevelLabel(song.level))
                    if (evaluableBadge) EolssuBadge(stringResource(Res.string.mysongs_evaluable_badge), style = EolssuBadgeStyle.Dark)
                }
                Text(meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = if (large) 13.sp else 12.sp), color = colors.textMuted)
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = 12.dp).size(18.dp), tint = colors.lineStrong)
        }
    }
}

/**
 * 와이어프레임 카드의 두 줄 격자(`gap: 6px`, 줄 높이 늘림)처럼, 남는 높이를 줄마다 똑같이 나눠 주고 각 줄 안에서는 가운데에 둔다.
 */
private object StretchedRows : Arrangement.Vertical {
    override val spacing: Dp = 6.dp

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        if (sizes.isEmpty()) return
        val gap = spacing.roundToPx()
        val used = sizes.sum() + gap * (sizes.size - 1)
        val extra = (totalSize - used).coerceAtLeast(0) / sizes.size
        var y = 0
        sizes.forEachIndexed { index, size ->
            outPositions[index] = y + extra / 2
            y += size + extra + gap
        }
    }
}
