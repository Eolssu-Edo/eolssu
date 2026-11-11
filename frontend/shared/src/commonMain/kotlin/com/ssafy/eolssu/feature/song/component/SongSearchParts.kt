package com.ssafy.eolssu.feature.song.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.ChipRow
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuChip
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuLevelChip
import com.ssafy.eolssu.core.ui.component.PageHeading
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.song.SongLevelFilter
import com.ssafy.eolssu.feature.song.SongListActions
import com.ssafy.eolssu.feature.song.SongListUiState
import com.ssafy.eolssu.feature.song.SongLoadState
import com.ssafy.eolssu.feature.song.SongSortOrder
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_clear
import eolssu.shared.generated.resources.song_empty
import eolssu.shared.generated.resources.song_empty_body
import eolssu.shared.generated.resources.song_level_all
import eolssu.shared.generated.resources.song_list_heading
import eolssu.shared.generated.resources.song_reset_filters
import eolssu.shared.generated.resources.song_search_label
import eolssu.shared.generated.resources.song_sort_ascending
import eolssu.shared.generated.resources.song_sort_ascending_description
import eolssu.shared.generated.resources.song_sort_descending
import eolssu.shared.generated.resources.song_sort_descending_description
import eolssu.shared.generated.resources.song_search_placeholder
import org.jetbrains.compose.resources.stringResource

/** 빈 결과의 井 글자색(보드 `--faint`). 테마에 없는 장식 색이라 이 화면에만 둔다. */
private val EmptySymbolColor = Color(0xFFB9B3A6)

/**
 * SONG-01 본문을 목록에 채운다(휴대폰·태블릿 공통, 보드 `Song-List` 순서):
 * 페이지 머리 · 검색칸 · 난이도 칩 · 곡 수 · 연습곡 고르기와 같은 곡 줄([ProvidedSongRow]), 결과가 없으면 '찾는 곡이 없어요.', 목록을 받지 못했으면 [SongLoadNotice].
 * [twoPane]은 태블릿 가로 두 칸 배치로, 오른쪽에 정보를 보이는 곡 줄을 연두로 표시한다.
 */
internal fun LazyListScope.songListItems(state: SongListUiState, actions: SongListActions, twoPane: Boolean = false) {
    val selectedId = if (twoPane) state.selectedSong?.id else null
    item(key = "heading") {
        PageHeading(
            title = stringResource(Res.string.song_list_heading),
            modifier = Modifier.padding(bottom = 22.dp),
        )
    }
    item(key = "search") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SongSearchField(state.query, actions.onQueryChange, actions.onClearQuery, bordered = false)
            SongLevelChips(state.filter, actions.onSelectFilter, Modifier.padding(bottom = 16.dp), bordered = false)
        }
    }
    val songs = state.visibleSongs
    when {
        state.loadState != SongLoadState.Ready || state.songs.isEmpty() ->
            item(key = "notice") { SongLoadNotice(state.loadState, actions.onRetry) }
        songs.isEmpty() -> item(key = "empty") { SongEmptyResult(actions.onResetFilters) }
        else -> {
            // 곡 수 안내('46곡 · 단소')는 두지 않고 정렬 버튼만 오른쪽에 둔다(S15P31E102-768).
            item(key = "count") {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                    SongSortButton(state.sort, actions.onToggleSort, small = true, bordered = false)
                }
            }
            // 연습곡 고르기와 같은 곡 줄(첫 글자 칸 · 제목 · 장단 · 난이도 배지)을 8dp 간격으로 쌓는다.
            items(songs, key = { it.id }) { song ->
                ProvidedSongRow(
                    song,
                    providedSongMeta(song),
                    onClick = { actions.onOpenSong(song.id) },
                    modifier = Modifier.padding(bottom = 8.dp),
                    selected = song.id == selectedId,
                    bordered = false,
                )
            }
        }
    }
}

/** SONG-01 곡 제목 검색칸(`.search-box`): 높이 46dp, 흰 바탕 · 옅은 테두리 · 모서리 10dp, 13sp. 앱 안에서 제목으로 거른다. */
@Composable
internal fun SongSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
) {
    val colors = EolssuTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val label = stringResource(Res.string.song_search_label)
    val focusManager = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(colors.surface, shape)
            .then(if (bordered) Modifier.border(1.dp, colors.lineSoft, shape) else Modifier)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(EolssuIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textMuted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = colors.text)
            if (query.isEmpty()) {
                Text(
                    stringResource(Res.string.song_search_placeholder),
                    modifier = Modifier.clearAndSetSemantics {},
                    style = textStyle,
                    color = colors.textMuted,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.text),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // 입력할 때마다 거르므로 검색 키는 키보드만 닫는다.
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
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

/** 난이도 필터 칩 줄(전체 · 입문 · 초급 · 중급). '전체'는 고르면 연두 바탕이고, 난이도 칩은 배지 그림과 색을 따른다. */
@Composable
internal fun SongLevelChips(
    selected: SongLevelFilter,
    onSelect: (SongLevelFilter) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    bordered: Boolean = true,
) {
    val chips: @Composable () -> Unit = {
        SongLevelFilter.entries.forEach { filter ->
            val level = filter.level
            if (level == null) EolssuChip(songLevelFilterLabel(filter), selected = selected == filter, onClick = { onSelect(filter) }, bordered = bordered)
            else EolssuLevelChip(level, selected = selected == filter, onClick = { onSelect(filter) }, bordered = bordered)
        }
    }
    if (singleLine) {
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    } else {
        ChipRow(modifier) { chips() }
    }
}

/**
 * 곡 이름 정렬 버튼: 정렬 아이콘과 지금 순서('ㄱ→ㅎ'·'ㅎ→ㄱ')를 보이고, 누르면 반대 순서로 바꾼다.
 * 화면 읽기에는 화살표 표기 대신 '가나다순'·'가나다 역순'으로 읽힌다.
 * 고르기 칩과 같은 36dp(15sp) 흰 알약이고, [small]이면 목록 위 안내 줄에 맞춘 28dp(13sp)다.
 */
@Composable
internal fun SongSortButton(order: SongSortOrder, onToggle: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false, bordered: Boolean = true) {
    val colors = EolssuTheme.colors
    val ascending = order == SongSortOrder.Ascending
    val description = stringResource(if (ascending) Res.string.song_sort_ascending_description else Res.string.song_sort_descending_description)
    Surface(
        onClick = onToggle,
        modifier = modifier.heightIn(min = if (small) 28.dp else 36.dp),
        shape = CircleShape,
        color = colors.surface,
        contentColor = colors.textSub,
        border = if (bordered) BorderStroke(1.dp, colors.lineSoft) else null,
    ) {
        Row(
            Modifier.padding(horizontal = if (small) 10.dp else 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(EolssuIcons.Sort, contentDescription = null, modifier = Modifier.size(if (small) 14.dp else 16.dp))
            Text(
                stringResource(if (ascending) Res.string.song_sort_ascending else Res.string.song_sort_descending),
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                // 낱자(ㄱ·ㅎ)는 글꼴에서 작게 그려져 아이콘보다 1sp 크게 둔다.
                style = MaterialTheme.typography.labelLarge.copy(fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun songLevelFilterLabel(filter: SongLevelFilter): String =
    filter.level?.let { songLevelLabel(it) } ?: stringResource(Res.string.song_level_all)

/** 검색·필터 결과가 없을 때(`.empty`): 井 · '찾는 곡이 없어요.' · 안내 · 너비를 채운 '검색 조건 초기화'. */
@Composable
internal fun SongEmptyResult(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.song_empty),
    body: String = stringResource(Res.string.song_empty_body),
    resetLabel: String = stringResource(Res.string.song_reset_filters),
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 40.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "井",
            modifier = Modifier.clearAndSetSemantics {},
            style = EolssuTheme.serif.yul.copy(fontSize = 55.sp, lineHeight = 55.sp, fontWeight = FontWeight.Normal),
            color = EmptySymbolColor,
        )
        Text(
            title,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp).semantics { heading() },
            style = EolssuTheme.serif.songTitle.copy(fontSize = 20.sp),
            color = EolssuTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            modifier = Modifier.padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = EolssuTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        EolssuButton(resetLabel, onReset, style = EolssuButtonStyle.Secondary)
    }
}
