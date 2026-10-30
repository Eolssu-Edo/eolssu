package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.model.Yul
import com.ssafy.eolssu.core.theme.EolssuTheme

/** 곡 줄 왼쪽 율명 기호. 보드처럼 목록 순서대로 黃 · 太 · 仲 · 林을 돌려 쓰는 장식이다(곡의 실제 첫 음과 무관). */
private val RowSymbols = listOf(Yul.Hwangjong, Yul.Taeju, Yul.Jungnyeo, Yul.Imjong)

/** 목록에서 [index]번째 곡 줄의 율명 기호. */
internal fun songRowSymbol(index: Int): String = RowSymbols[index.mod(RowSymbols.size)].symbol

/**
 * SONG-01 곡 줄(`.list-row`): 율명 기호 칸 · 제목(15sp) · '입문 · 세마치장단'(11sp) · ↗, 아래 구분선.
 * [selected]는 태블릿 가로 두 칸 배치에서 오른쪽에 정보를 보이는 곡이다(옅은 연두 바탕). 그 배치는 바탕이 글자에 붙지 않도록 모든 줄에 [inset]을 준다.
 */
@Composable
internal fun SongListRow(
    song: SongSummary,
    symbol: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    inset: Dp = 0.dp,
) {
    val colors = EolssuTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(colors.accentSoft).semantics { this.selected = true } else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongSymbol(symbol)
            Column(Modifier.weight(1f).padding(start = 13.dp, end = 13.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meta,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = colors.textMuted,
                )
            }
            Text(
                "↗",
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp),
                color = colors.textDisabled,
            )
        }
        HorizontalDivider(color = colors.surfaceStrong)
    }
}

/** 율명 기호 칸(`.song-symbol`): 45×52dp 흰 칸에 가운데 십자선, 명조 22sp 율명. 꾸밈이라 읽지 않는다. */
@Composable
private fun SongSymbol(symbol: String) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(width = 45.dp, height = 52.dp)
            .background(colors.surface, shape)
            .drawBehind {
                drawLine(colors.surfaceStrong, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), size.width * 0.02f)
                drawLine(colors.surfaceStrong, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height * 0.02f)
            }
            .border(1.dp, colors.lineSoft, shape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = EolssuTheme.serif.yul.copy(fontSize = 22.sp), color = colors.text)
    }
}
