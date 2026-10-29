package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.ChipRow
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuChip
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.song.SongLevelFilter
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_clear
import eolssu.shared.generated.resources.song_empty
import eolssu.shared.generated.resources.song_load_failed
import eolssu.shared.generated.resources.song_load_retry
import eolssu.shared.generated.resources.song_level_all
import eolssu.shared.generated.resources.song_reset_filters
import eolssu.shared.generated.resources.song_search_placeholder
import org.jetbrains.compose.resources.stringResource

/** SONG-01 곡 제목 검색칸. [large]는 태블릿 세로 배치(높이 52dp, 옆 여백 16dp)다. */
@Composable
internal fun SongSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    val colors = EolssuTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(if (large) 52.dp else 48.dp)
            .background(colors.surface, RoundedCornerShape(14.dp))
            .padding(horizontal = if (large) 16.dp else 14.dp),
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

/** 난이도 필터 칩 줄(전체 · 입문 · 초급 · 중급). */
@Composable
internal fun SongLevelChips(selected: SongLevelFilter, onSelect: (SongLevelFilter) -> Unit, modifier: Modifier = Modifier) {
    ChipRow(modifier) {
        SongLevelFilter.entries.forEach { filter ->
            EolssuChip(songLevelFilterLabel(filter), selected = selected == filter, onClick = { onSelect(filter) })
        }
    }
}

@Composable
private fun songLevelFilterLabel(filter: SongLevelFilter): String =
    filter.level?.let { songLevelLabel(it) } ?: stringResource(Res.string.song_level_all)

/** 곡 목록을 받지 못했을 때의 안내와 '다시 시도'. */
@Composable
internal fun SongLoadFailed(onRetry: () -> Unit, modifier: Modifier = Modifier, verticalPadding: Dp = 56.dp) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.song_load_failed), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
        EolssuButton(stringResource(Res.string.song_load_retry), onRetry, style = EolssuButtonStyle.Secondary, small = true)
    }
}

/** 검색·필터 결과가 없을 때의 안내와 '검색 조건 초기화'. 태블릿 세로는 위아래 여백이 96dp다. */
@Composable
internal fun SongEmptyResult(onReset: () -> Unit, modifier: Modifier = Modifier, verticalPadding: Dp = 56.dp) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.song_empty), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
        EolssuButton(stringResource(Res.string.song_reset_filters), onReset, style = EolssuButtonStyle.Secondary, small = true)
    }
}
