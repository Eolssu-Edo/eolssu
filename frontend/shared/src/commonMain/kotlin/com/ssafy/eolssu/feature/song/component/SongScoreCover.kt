package com.ssafy.eolssu.feature.song.component

import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.score.JeongganboMetrics
import com.ssafy.eolssu.core.ui.score.JeongganboView
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.seal_brush
import eolssu.shared.generated.resources.song_cover_seal
import eolssu.shared.generated.resources.song_preview_label
import org.jetbrains.compose.resources.stringResource

/** 세로 제목이 [TitleFitChars]자를 넘으면 글자를 줄여 표지가 제목 때문에 지나치게 길어지지 않게 한다. */
private const val TitleFitChars = 5

private val CoverColumnGap: Dp = 4.dp


/** 표지 각 너비 상한(보드 태블릿 연습 악보 `.ms-col` 96px). */
private val CoverMaxColumnWidth: Dp = 96.dp

/**
 * SONG-02 악보 표지(`.score-cover`): 흰 카드 왼쪽에 앞 [columns]각 · 각마다 [rows]정간의 미니 정간보,
 * 오른쪽 위에 세로 제목, 그 바로 아래에 붉은 바탕 · 흰 붓글씨 '얼쑤' 도장(S15P31E102-768, 제목과 겹치지 않게 제목 아래에 붙인다).
 * 정간보는 카드 안쪽 너비의 [scoreFraction]만 쓰고 각 너비를 그에 맞춘다. 정간 그리기는 `core/ui/score`를 그대로 쓴다.
 * [large]는 태블릿(제목 36sp, 정간 46dp)이다.
 */
@Composable
internal fun SongScoreCover(
    song: Song,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    rows: Int = 5,
    scoreFraction: Float = 0.64f,
    large: Boolean = false,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val previewLabel = stringResource(Res.string.song_preview_label)
    val titleChars = song.title.filterNot { it.isWhitespace() }
    val titleSize = fittedTitleSize(titleChars.length, if (large) 36.sp else 30.sp)
    val sealSize = if (large) 20.sp else 17.sp
    // 악보가 짧아도 세로 제목(위 25dp)과 그 아래 도장이 표지 안에 들어오도록 높이의 하한을 둔다.
    val minHeight = with(LocalDensity.current) {
        25.dp + titleSize.toDp() * titleChars.length + 7.dp * (titleChars.length - 1).coerceAtLeast(0) +
            14.dp + sealSize.toDp() + 10.dp + 22.dp
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(colors.surface, shape)
            .border(1.dp, colors.lineSoft, shape),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(23.dp)) {
            // 곡의 각 수가 [columns]보다 적으면 있는 각만 나눠 쓰고, 넓은 태블릿 칸에서는 각을 96dp까지만 키운다.
            val shown = columns.coerceAtMost(song.score.columns.size).coerceAtLeast(1)
            // JeongganboView는 줄 양옆에 2dp씩 여백을 둔다.
            val columnWidth = ((maxWidth * scoreFraction - 4.dp - CoverColumnGap * (shown - 1)) / shown).coerceAtMost(CoverMaxColumnWidth)
            JeongganboView(
                song = song,
                modifier = Modifier
                    .width(columnWidth * shown + CoverColumnGap * (shown - 1) + 4.dp)
                    .semantics { contentDescription = previewLabel },
                metrics = JeongganboMetrics(
                    columnWidth = columnWidth,
                    // 보드는 정간 30dp지만, 서버 악보는 한 정간을 셋까지 나눠 30dp에서 율명이 겹친다. 연습 화면 휴대폰 크기(38dp)를 쓴다.
                    cellHeight = if (large) 46.dp else 38.dp,
                    yulSize = if (large) 20.sp else 17.sp,
                    auxSize = if (large) 9.5.sp else 8.5.sp,
                    headHeight = 20.dp,
                    columnGap = CoverColumnGap,
                ),
                scrollState = null,
                maxColumns = shown,
                maxRows = rows,
            )
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(top = 25.dp, end = 24.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VerticalTitle(titleChars, titleSize, Modifier)
            CoverSeal(sealSize)
        }
    }
}

/** 붉은 낙관: 붉은 바탕(모서리 3dp)에 앱 붓글씨 로고 '얼쑤'를 흰색으로 새긴다. 장식이라 읽지 않는다. */
@Composable
private fun CoverSeal(size: TextUnit) {
    // 나눔손글씨 붓(OFL)에서 '얼쑤' 두 글자만 남긴 4.5KB 글꼴이라 불러오기 부담이 없다(S15P31E102-768).
    val brush = FontFamily(Font(Res.font.seal_brush))
    Box(
        Modifier
            .rotate(-4f)
            .background(EolssuTheme.colors.rec, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 5.dp)
            .clearAndSetSemantics {},
    ) {
        Text(
            stringResource(Res.string.song_cover_seal),
            style = TextStyle(fontFamily = brush, fontSize = size, lineHeight = size, color = Color.White),
            maxLines = 1,
        )
    }
}

/** [TitleFitChars]자를 넘는 제목은 글자를 줄인다. */
private fun fittedTitleSize(length: Int, size: TextUnit): TextUnit =
    if (length > TitleFitChars) size * (TitleFitChars.toFloat() / length) else size

/** 세로쓰기 제목(`writing-mode: vertical-rl`, 글자 사이 7dp). 제목은 표지 아래 큰 제목이 읽으므로 여기서는 읽지 않는다. */
@Composable
private fun VerticalTitle(chars: String, size: TextUnit, modifier: Modifier) {
    Column(
        modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        chars.forEach { char ->
            Text(
                char.toString(),
                style = EolssuTheme.serif.songTitle.copy(fontSize = size, lineHeight = size),
                color = EolssuTheme.colors.text,
            )
        }
    }
}
