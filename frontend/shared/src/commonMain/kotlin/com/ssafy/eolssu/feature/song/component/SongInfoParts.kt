package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import eolssu.shared.generated.resources.Res
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import eolssu.shared.generated.resources.song_bpm_value
import eolssu.shared.generated.resources.song_info_bpm
import eolssu.shared.generated.resources.song_info_instrument
import eolssu.shared.generated.resources.song_info_jangdan
import eolssu.shared.generated.resources.song_info_level
import eolssu.shared.generated.resources.song_preview_label
import eolssu.shared.generated.resources.song_preview_sample
import org.jetbrains.compose.resources.stringResource

/** SONG-02 곡 제목(명조)과 난이도 배지. 태블릿 세로는 제목 36sp, 배지 사이 12dp다. */
@Composable
internal fun SongTitleRow(song: Song, modifier: Modifier = Modifier, titleSize: TextUnit = TextUnit.Unspecified, large: Boolean = false) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val style = EolssuTheme.serif.songTitle
        Text(song.title, modifier = Modifier.align(Alignment.CenterVertically), style = if (titleSize.isSpecified) style.copy(fontSize = titleSize) else style)
        EolssuBadge(songLevelLabel(song.level), modifier = Modifier.align(Alignment.CenterVertically))
    }
}

/**
 * 곡 정보 칸(악기 · 장단 · 기준 속도 · 난이도). 휴대폰은 2열, 태블릿은 4열 한 줄([columns]).
 * [large]는 태블릿 세로 크기(칸 사이 10dp, 글자 12/15sp)다.
 */
@Composable
internal fun SongInfoGrid(song: Song, modifier: Modifier = Modifier, columns: Int = 2, large: Boolean = false) {
    val items = listOf(
        stringResource(Res.string.song_info_instrument) to songInstrumentLabel(song),
        stringResource(Res.string.song_info_jangdan) to jangdanLabel(song.jangdan),
        stringResource(Res.string.song_info_bpm) to stringResource(Res.string.song_bpm_value, song.baseBpm),
        stringResource(Res.string.song_info_level) to songLevelLabel(song.level),
    )
    val gap = if (large) 10.dp else 8.dp
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { (label, value) -> InfoCell(label, value, Modifier.weight(1f), large) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier, large: Boolean) {
    Column(
        modifier
            .background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = if (large) 16.dp else 14.dp, vertical = if (large) 14.dp else 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (large) 12.sp else 11.sp),
            color = EolssuTheme.colors.textMuted,
        )
        Text(
            value,
            modifier = Modifier.padding(top = if (large) 4.dp else 3.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (large) 15.sp else 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/** 악보 미리보기 머리글: '악보 일부 미리보기 · 오른쪽 각부터 읽어요'와 '예시 악보' 배지. 태블릿 세로는 13sp다. */
@Composable
internal fun SongPreviewHeader(modifier: Modifier = Modifier, large: Boolean = false, sample: Boolean = true) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.song_preview_label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = if (large) 13.sp else 12.sp),
            color = EolssuTheme.colors.textMuted,
        )
        if (sample) EolssuBadge(stringResource(Res.string.song_preview_sample))
    }
}
