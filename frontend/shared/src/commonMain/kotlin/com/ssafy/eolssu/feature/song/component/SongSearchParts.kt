package com.ssafy.eolssu.feature.song.component

import com.ssafy.eolssu.core.ui.component.mouseDragScroll
import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import com.ssafy.eolssu.core.ui.component.SongSearchBar
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
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.song.SongLevelFilter
import com.ssafy.eolssu.feature.song.SongListActions
import com.ssafy.eolssu.feature.song.SongListUiState
import com.ssafy.eolssu.feature.song.SongLoadState
import com.ssafy.eolssu.core.model.SongSortOrder
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_clear
import eolssu.shared.generated.resources.song_empty
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

/** 목록 위에 붙은 검색칸과 상단 바 사이 간격. */
private val SearchStickyTopGap = 12.dp

/**
 * SONG-01 본문을 목록에 채운다(휴대폰·태블릿 공통, 보드 `Song-List` 순서):
 * 페이지 머리 · 검색칸 · 난이도 칩 · 곡 수 · 연습곡 고르기와 같은 곡 줄([ProvidedSongRow]), 결과가 없으면 '찾는 곡이 없어요.', 목록을 받지 못했으면 [SongLoadNotice].
 * [twoPane]은 태블릿 가로 두 칸 배치로, 오른쪽에 정보를 보이는 곡 줄을 연두로 표시한다.
 *
 * 페이지 머리는 목록과 함께 올라가고, 검색칸 · 난이도 칩은 목록 위에 닿으면 붙어서 계속 보인다(S15P31E102-1279).
 * 붙은 칸이 아래 곡 줄을 가리도록 세 배치가 함께 쓰는 목록 바탕색(`groupedBackground`)으로 칠한다.
 * 검색어·난이도를 바꿔도 목록 스크롤을 맨 위로 되돌리지 않는다(S15P31E102-1255와 같은 방식).
 */
internal fun LazyListScope.songListItems(state: SongListUiState, actions: SongListActions, twoPane: Boolean = false) {
    val selectedId = if (twoPane) state.selectedSong?.id else null
    // 붙었을 때 검색칸이 상단 바에 닿지 않도록 12dp를 검색칸 위로 옮긴다. 붙기 전 간격(머리 아래 22dp)은 그대로다.
    item(key = "heading") {
        // 홈 AI 평가 버튼과 같은 글씨체로 한 줄에 한 글자씩 적는다(S15P31E102-1280).
        SongListHeading(
            title = stringResource(Res.string.song_list_heading),
            modifier = Modifier.padding(bottom = 22.dp - SearchStickyTopGap),
        )
    }
    val loaded = state.loadState == SongLoadState.Ready && state.songs.isNotEmpty()
    stickyHeader(key = "search") {
        Column(
            Modifier.fillMaxWidth().background(EolssuTheme.colors.groupedBackground).padding(top = SearchStickyTopGap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 정렬 버튼은 목록 위 한 줄 대신 검색칸 오른쪽에 둔다. 곡 수 안내('46곡 · 단소')는 두지 않는다(S15P31E102-768).
            // 검색 결과가 비어도 버튼을 남겨, 입력할 때마다 검색칸 폭이 바뀌지 않게 한다.
            SongSearchBar(
                state.query,
                actions.onQueryChange,
                actions.onClearQuery,
                sortOrder = state.sort.takeIf { loaded },
                onToggleSort = actions.onToggleSort,
                bordered = false,
            )
            SongLevelChips(state.filter, actions.onSelectFilter, Modifier.padding(bottom = 16.dp), bordered = false)
        }
    }
    val songs = state.visibleSongs
    when {
        !loaded -> item(key = "notice") { SongLoadNotice(state.loadState, actions.onRetry) }
        songs.isEmpty() -> item(key = "empty") { SongEmptyResult(actions.onResetFilters) }
        else -> {
            // 연습곡 고르기와 같은 곡 줄(첫 글자 칸 · 제목 · 장단 · 난이도 배지)을 8dp 간격으로 쌓는다.
            items(songs, key = { it.id }) { song ->
                ProvidedSongRow(
                    song,
                    providedSongMeta(song),
                    onClick = UserActionLog.click(QaTarget.SONG_SEARCH_PARTS_01, { actions.onOpenSong(song.id) }),
                    modifier = Modifier.padding(bottom = 8.dp),
                    selected = song.id == selectedId,
                    bordered = false,
                )
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
            if (level == null) EolssuChip(songLevelFilterLabel(filter), selected = selected == filter, onClick = UserActionLog.click(QaTarget.SONG_SEARCH_PARTS_02, { onSelect(filter) }), bordered = bordered)
            else EolssuLevelChip(level, selected = selected == filter, onClick = UserActionLog.click(QaTarget.SONG_SEARCH_PARTS_03, { onSelect(filter) }), bordered = bordered)
        }
    }
    if (singleLine) {
        val scroll = rememberScrollState()
        Row(
            modifier.fillMaxWidth().mouseDragScroll(scroll).horizontalScroll(scroll).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { chips() }
    } else {
        ChipRow(modifier) { chips() }
    }
}

@Composable
private fun songLevelFilterLabel(filter: SongLevelFilter): String =
    filter.level?.let { songLevelLabel(it) } ?: stringResource(Res.string.song_level_all)

/** 검색·필터 결과가 없을 때(`.empty`): 井 · '찾는 곡이 없어요.' · '검색 조건 초기화'. 보조 안내 문구는 두지 않는다(S15P31E102-812). */
@Composable
internal fun SongEmptyResult(
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.song_empty),
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
            modifier = Modifier.padding(top = 14.dp, bottom = 16.dp).semantics { heading() },
            style = EolssuTheme.serif.songTitle.copy(fontSize = 20.sp),
            color = EolssuTheme.colors.text,
            textAlign = TextAlign.Center,
        )
        EolssuButton(resetLabel, onReset, style = EolssuButtonStyle.Secondary, qaTarget = QaTarget.SONG_SEARCH_PARTS_BUTTON_01)
    }
}
