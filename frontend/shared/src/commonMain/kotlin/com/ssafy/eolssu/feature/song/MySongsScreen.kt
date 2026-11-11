package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuButton
import com.ssafy.eolssu.core.ui.component.EolssuButtonStyle
import com.ssafy.eolssu.core.ui.component.EolssuDialog
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.component.AddSheetButton
import com.ssafy.eolssu.feature.song.component.CardGrid
import com.ssafy.eolssu.feature.song.component.MySheetRow
import com.ssafy.eolssu.feature.song.component.MySongsTabSwitch
import com.ssafy.eolssu.feature.song.component.ProvidedSongRow
import com.ssafy.eolssu.feature.song.component.providedSongMeta
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.component.SongEmptyResult
import com.ssafy.eolssu.feature.song.component.SongLevelChips
import com.ssafy.eolssu.feature.song.component.SongSearchField
import com.ssafy.eolssu.feature.song.component.SongSortButton
import com.ssafy.eolssu.feature.song.layout.MySongsTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.mysongs_clear_query
import eolssu.shared.generated.resources.mysongs_delete
import eolssu.shared.generated.resources.mysongs_delete_message
import eolssu.shared.generated.resources.mysongs_delete_title
import eolssu.shared.generated.resources.mysongs_empty_body
import eolssu.shared.generated.resources.mysongs_reset_filter
import eolssu.shared.generated.resources.mysongs_sheet_count
import eolssu.shared.generated.resources.mysongs_sheet_empty
import eolssu.shared.generated.resources.mysongs_sheet_empty_body
import eolssu.shared.generated.resources.mysongs_title
import org.jetbrains.compose.resources.stringResource

/** SONG-03 화면의 사용자 행동. */
class MySongsActions(
    val onBack: () -> Unit,
    val onSelectTab: (MySongsTab) -> Unit,
    val onSelectFilter: (SongLevelFilter) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onClearQuery: () -> Unit,
    /** 제공 곡 빈 결과의 '검색 조건 초기화'·'전체 곡 보기': 검색어와 단계를 함께 되돌린다. */
    val onResetSongFilters: () -> Unit,
    val onToggleSort: () -> Unit,
    val onOpenSong: (songId: String) -> Unit,
    val onOpenSheet: (MySheet) -> Unit,
    val onEditSheet: (MySheet) -> Unit,
    val onAddSheet: () -> Unit,
    val onDeleteSheet: (MySheet) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onRetry: () -> Unit,
    /** 변환에 실패한 악보의 '다시 시도'(서버가 다시 시도할 수 있다고 한 악보만 보인다). */
    val onRetryConversion: (MySheet) -> Unit = {},
    /** 변환에 실패한 악보의 '다시 불러오기'. 실패 원인을 알리며 악보 추가로 간다. */
    val onReimportSheet: (MySheet) -> Unit = { onAddSheet() },
)

/**
 * SONG-03 연습곡 고르기: 제공 곡 · 내 악보 탭, 내 악보 삭제 확인 창. 상단 바 오른쪽에 연습 악기 드롭다운([instrumentPicker])을 둔다.
 * 휴대폰·태블릿 모두 같은 본문([MySongsBody])을 쓰고, 태블릿 가로([WindowWidthClass.Expanded])만 줄을 두 열로 놓는다.
 */
@Composable
fun MySongsScreen(
    state: MySongsUiState,
    widthClass: WindowWidthClass,
    snackbarHostState: SnackbarHostState,
    actions: MySongsActions,
    instrumentPicker: @Composable () -> Unit = {},
) {
    if (widthClass == WindowWidthClass.Compact) {
        MySongsCompact(state, snackbarHostState, actions, instrumentPicker)
    } else {
        MySongsTabletLayout(state, expanded = widthClass == WindowWidthClass.Expanded, snackbarHostState, actions, instrumentPicker)
    }
    state.deleting?.let { DeleteSheetDialog(it, actions) }
}

/** 내 악보 삭제 확인(`Song-Practice-Delete`): 붉은 휴지통 고리 · '삭제' · '취소'. 가림막·뒤로 가기도 취소다. */
@Composable
private fun DeleteSheetDialog(sheet: MySheet, actions: MySongsActions) {
    val colors = EolssuTheme.colors
    EolssuDialog(
        title = stringResource(Res.string.mysongs_delete_title, sheet.title),
        message = stringResource(Res.string.mysongs_delete_message),
        icon = EolssuIcons.Trash,
        iconBackground = colors.recSoft,
        iconTint = colors.recText,
        onDismissRequest = actions.onCancelDelete,
    ) {
        EolssuButton(stringResource(Res.string.mysongs_delete), actions.onConfirmDelete, style = EolssuButtonStyle.Danger)
        EolssuButton(stringResource(Res.string.common_cancel), actions.onCancelDelete, style = EolssuButtonStyle.Secondary)
    }
}

@Composable
private fun MySongsCompact(state: MySongsUiState, snackbarHostState: SnackbarHostState, actions: MySongsActions, instrumentPicker: @Composable () -> Unit) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        containerColor = EolssuTheme.colors.groupedBackground,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.mysongs_title),
                onBack = actions.onBack,
                right = { instrumentPicker() },
            )
        },
    ) {
        MySongsBody(
            state,
            actions,
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
    }
}

/**
 * SONG-03 본문(`.body`, 간격 12dp): 탭 · 두 탭이 함께 쓰는 제목 검색칸 · 제공 곡(단계 필터 · 오른쪽 정렬 버튼 · 곡 줄)
 * 또는 내 악보(악보 추가 · 'n개' · 악보 줄, 검색에 맞는 악보가 없으면 '찾는 악보가 없어요.').
 * 개수 문구는 왼쪽 4dp 들이고 목록과 8dp 떨어진다. [columns]가 2 이상이면 줄을 격자로 놓는다.
 */
@Composable
internal fun MySongsBody(
    state: MySongsUiState,
    actions: MySongsActions,
    modifier: Modifier = Modifier,
    columns: Int = 1,
    rowGap: Dp = 8.dp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MySongsTabSwitch(state.tab, state.songs.size, state.sheetCount, actions.onSelectTab, Modifier.padding(top = 4.dp))
        SongSearchField(state.query, actions.onQueryChange, actions.onClearQuery, bordered = false)
        val searching = state.query.isNotBlank()
        when (state.tab) {
            MySongsTab.Songs -> {
                SongLevelChips(state.filter, actions.onSelectFilter, singleLine = true, bordered = false)
                if (state.loadState != SongLoadState.Ready || state.songs.isEmpty()) {
                    SongLoadNotice(state.loadState, actions.onRetry)
                } else if (state.visibleSongs.isEmpty()) {
                    // 검색어가 있으면 평가할 곡 고르기와 같은 문구('다른 제목이나 난이도로…' · '검색 조건 초기화')를 쓴다.
                    if (searching) SongEmptyResult(onReset = actions.onResetSongFilters)
                    else SongEmptyResult(
                        onReset = actions.onResetSongFilters,
                        body = stringResource(Res.string.mysongs_empty_body),
                        resetLabel = stringResource(Res.string.mysongs_reset_filter),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SongSortButton(state.sort, actions.onToggleSort, Modifier.align(Alignment.End), small = true, bordered = false)
                        CardGrid(state.visibleSongs, columns, itemKey = { it.id }, spacing = rowGap) { song, rowModifier ->
                            ProvidedSongRow(song, providedSongMeta(song), onClick = { actions.onOpenSong(song.id) }, modifier = rowModifier, bordered = false)
                        }
                    }
                }
            }
            MySongsTab.Sheets -> {
                AddSheetButton(actions.onAddSheet)
                val sheets = state.visibleSheets
                if (state.sheetLoadState != SongLoadState.Ready) SongLoadNotice(state.sheetLoadState, actions.onRetry)
                if (sheets.isEmpty() && state.sheetCount > 0 && searching) {
                    SongEmptyResult(
                        onReset = actions.onClearQuery,
                        title = stringResource(Res.string.mysongs_sheet_empty),
                        body = stringResource(Res.string.mysongs_sheet_empty_body),
                        resetLabel = stringResource(Res.string.mysongs_clear_query),
                    )
                }
                if (sheets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListNote(stringResource(Res.string.mysongs_sheet_count, sheets.size))
                        CardGrid(sheets, columns, itemKey = { it.id }, spacing = rowGap) { sheet, rowModifier ->
                            MySheetRow(
                                sheet,
                                onOpen = { actions.onOpenSheet(sheet) },
                                onEdit = { actions.onEditSheet(sheet) },
                                onDelete = { actions.onDeleteSheet(sheet) },
                                modifier = rowModifier,
                                conversion = state.conversionOf(sheet),
                                retrying = sheet.id in state.retrying,
                                onRetryConversion = { actions.onRetryConversion(sheet) },
                                onReimport = { actions.onReimportSheet(sheet) },
                                bordered = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 목록 위 작은 회색 문구(`.tiny.muted`, 왼쪽 4dp). */
@Composable
private fun ListNote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        color = EolssuTheme.colors.textMuted,
    )
}
