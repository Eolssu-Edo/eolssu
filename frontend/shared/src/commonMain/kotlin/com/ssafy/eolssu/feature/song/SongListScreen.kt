package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Song
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.ChipRow
import com.ssafy.eolssu.core.ui.component.EolssuBadge
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuChip
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.feature.song.component.jangdanLabel
import com.ssafy.eolssu.feature.song.component.songLevelFilterLabel
import com.ssafy.eolssu.feature.song.component.songLevelLabel
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_clear
import eolssu.shared.generated.resources.song_count
import eolssu.shared.generated.resources.song_empty
import eolssu.shared.generated.resources.song_list_title
import eolssu.shared.generated.resources.song_reset_filters
import eolssu.shared.generated.resources.song_search_placeholder
import org.jetbrains.compose.resources.stringResource

/** SONG-01 곡 목록. 검색·난이도 필터, 결과가 없으면 조건 초기화 안내. */
@Composable
fun SongListScreen(
    state: SongListUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelectFilter: (SongLevelFilter) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSong: (songId: String) -> Unit,
) {
    EolssuScaffold(topBar = { EolssuTopBar(stringResource(Res.string.song_list_title), onBack = onBack) }) {
        val songs = state.visibleSongs
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    SearchField(state.query, onQueryChange, onClearQuery)
                    ChipRow(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                        SongLevelFilter.entries.forEach { filter ->
                            EolssuChip(songLevelFilterLabel(filter), selected = state.filter == filter, onClick = { onSelectFilter(filter) })
                        }
                    }
                }
            }
            if (songs.isEmpty() && state.songs.isNotEmpty()) {
                item { EmptyResult(onResetFilters) }
            } else if (songs.isNotEmpty()) {
                item {
                    Text(
                        stringResource(Res.string.song_count, songs.size),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = EolssuTheme.colors.textMuted,
                    )
                }
                items(songs, key = { it.id }) { song -> SongCard(song, onClick = { onOpenSong(song.id) }) }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val colors = EolssuTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EolssuIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = colors.text)
            if (query.isEmpty()) {
                Text(stringResource(Res.string.song_search_placeholder), style = textStyle, color = colors.textMuted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Surface(onClick = onClear, shape = CircleShape, color = colors.surfaceStrong, modifier = Modifier.size(24.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(EolssuIcons.Close, stringResource(Res.string.common_clear), Modifier.size(12.dp), tint = colors.text)
                }
            }
        }
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.background,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
                    EolssuBadge(songLevelLabel(song.level))
                }
                Text(
                    jangdanLabel(song.jangdan),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textMuted,
                )
            }
            Icon(EolssuIcons.Chevron, contentDescription = null, modifier = Modifier.padding(start = 12.dp).size(18.dp), tint = colors.lineStrong)
        }
    }
}

@Composable
private fun EmptyResult(onReset: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.song_empty), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
        EolssuButton(stringResource(Res.string.song_reset_filters), onReset, style = EolssuButtonStyle.Secondary, small = true)
    }
}
