package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuLevelBadge
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.component.songInstrumentLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.song_bpm_value
import eolssu.shared.generated.resources.song_info_bpm
import eolssu.shared.generated.resources.song_info_instrument
import eolssu.shared.generated.resources.song_info_jangdan
import eolssu.shared.generated.resources.song_info_level
import eolssu.shared.generated.resources.song_start_evaluation
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-02 본문(휴대폰·태블릿 공통, 보드 `Song-Detail` 순서): 악보 표지 · 제목과 난이도 · 곡 소개 · 정보 격자 · '연주 평가 시작'.
 * '연주 평가 시작'([onStartPractice])은 연주 평가 화면의 대기 상태로 가고, 평가는 그 화면의 '평가 시작'으로 시작한다(S15P31E102-747).
 * 태블릿은 표지에 [coverColumns]각을 보이고([large]면 정간을 키운다) 정보 격자를 4열([infoColumns])로 둔다.
 */
@Composable
internal fun SongDetailBody(
    song: Song,
    onStartPractice: () -> Unit,
    modifier: Modifier = Modifier,
    coverColumns: Int = 2,
    coverRows: Int = 5,
    coverFraction: Float = 0.64f,
    infoColumns: Int = 2,
    large: Boolean = false,
) {
    Column(modifier.fillMaxWidth()) {
        SongScoreCover(song, columns = coverColumns, rows = coverRows, scoreFraction = coverFraction, large = large)
        SongTitleRow(song, Modifier.padding(top = 24.dp, bottom = 10.dp))
        if (song.description.isNotBlank()) {
            Text(
                song.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 22.sp),
                color = EolssuTheme.colors.textSub,
            )
        }
        SongInfoGrid(song, Modifier.padding(vertical = 20.dp), columns = infoColumns)
        EolssuButton(stringResource(Res.string.song_start_evaluation), onStartPractice, Modifier.fillMaxWidth().padding(top = 12.dp), compact = true)
    }
}

/** SONG-02 곡 제목(`.page-title`, Song Myung 25sp)과 난이도 배지. */
@Composable
internal fun SongTitleRow(song: Song, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            song.title,
            modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() },
            style = EolssuTheme.serif.songTitle.copy(fontSize = 25.sp, lineHeight = 37.5.sp, letterSpacing = (-0.5).sp),
            color = EolssuTheme.colors.text,
        )
        EolssuLevelBadge(song.level, modifier = Modifier.align(Alignment.CenterVertically), small = true)
    }
}

/**
 * 곡 정보 격자(`.meta-grid`, 악기 · 장단 · 기준 속도 · 난이도): 위아래 구분선 사이 16dp 여백, 칸 사이 16dp.
 * 휴대폰은 2열, 태블릿은 4열 한 줄([columns]).
 */
@Composable
internal fun SongInfoGrid(song: Song, modifier: Modifier = Modifier, columns: Int = 2) {
    val items = listOf(
        stringResource(Res.string.song_info_instrument) to songInstrumentLabel(song),
        stringResource(Res.string.song_info_jangdan) to jangdanLabel(song.jangdanName),
        stringResource(Res.string.song_info_bpm) to stringResource(Res.string.song_bpm_value, song.baseBpm),
        stringResource(Res.string.song_info_level) to songLevelLabel(song.level),
    )
    val line = EolssuTheme.colors.surfaceStrong
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = line)
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (label, value) -> InfoCell(label, value, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        HorizontalDivider(color = line)
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = EolssuTheme.colors.textMuted)
        Text(
            value,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.text,
        )
    }
}
