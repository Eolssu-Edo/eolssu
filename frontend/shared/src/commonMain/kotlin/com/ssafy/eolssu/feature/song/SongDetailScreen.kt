package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.InfoHint
import com.ssafy.eolssu.core.ui.component.fadeOutBottom
import com.ssafy.eolssu.core.ui.score.JeongganboView
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.instrument_daegeum
import eolssu.shared.generated.resources.song_bpm_value
import eolssu.shared.generated.resources.song_detail_title
import eolssu.shared.generated.resources.song_info_bpm
import eolssu.shared.generated.resources.song_info_instrument
import eolssu.shared.generated.resources.song_info_jangdan
import eolssu.shared.generated.resources.song_info_level
import eolssu.shared.generated.resources.song_preview_hint
import eolssu.shared.generated.resources.song_preview_label
import eolssu.shared.generated.resources.song_preview_sample
import eolssu.shared.generated.resources.song_start_practice
import org.jetbrains.compose.resources.stringResource

/** SONG-02 곡 정보와 정간보 앞 3각 미리보기. */
@Composable
fun SongDetailScreen(
    state: SongDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onStartPractice: () -> Unit,
) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = { EolssuTopBar(stringResource(Res.string.song_detail_title), onBack = onBack) },
        bottomBar = { EolssuButton(stringResource(Res.string.song_start_practice), onStartPractice, enabled = state.song != null) },
    ) {
        val song = state.song ?: return@EolssuScaffold
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(song.title, style = EolssuTheme.serif.songTitle)
                EolssuBadge(songLevelLabel(song.level))
            }
            InfoGrid(song, Modifier.padding(top = 18.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.song_preview_label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = EolssuTheme.colors.textMuted,
                )
                EolssuBadge(stringResource(Res.string.song_preview_sample))
            }
            JeongganboView(
                song = song,
                modifier = Modifier.heightIn(max = 250.dp).fadeOutBottom(fadeStart = 0.78f),
                scrollState = null,
                maxColumns = 3,
            )
            InfoHint(stringResource(Res.string.song_preview_hint), Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun InfoGrid(song: Song, modifier: Modifier = Modifier) {
    val items = listOf(
        stringResource(Res.string.song_info_instrument) to stringResource(Res.string.instrument_daegeum),
        stringResource(Res.string.song_info_jangdan) to jangdanLabel(song.jangdan),
        stringResource(Res.string.song_info_bpm) to stringResource(Res.string.song_bpm_value, song.baseBpm),
        stringResource(Res.string.song_info_level) to songLevelLabel(song.level),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) -> InfoCell(label, value, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(EolssuTheme.colors.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = EolssuTheme.colors.textMuted)
        Text(
            value,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}
