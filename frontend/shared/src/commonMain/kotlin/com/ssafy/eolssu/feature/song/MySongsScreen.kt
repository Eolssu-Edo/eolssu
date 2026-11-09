package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.component.SongEmptyResult
import com.ssafy.eolssu.feature.song.component.SongLevelChips
import com.ssafy.eolssu.feature.song.component.SongSortButton
import com.ssafy.eolssu.feature.song.layout.MySongsTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.common_cancel
import eolssu.shared.generated.resources.mysongs_delete
import eolssu.shared.generated.resources.mysongs_delete_message
import eolssu.shared.generated.resources.mysongs_delete_title
import eolssu.shared.generated.resources.mysongs_no_jangdan
import eolssu.shared.generated.resources.mysongs_practice_only
import eolssu.shared.generated.resources.mysongs_empty_body
import eolssu.shared.generated.resources.mysongs_reset_filter
import eolssu.shared.generated.resources.mysongs_sheet_count
import eolssu.shared.generated.resources.mysongs_songs_hint
import eolssu.shared.generated.resources.mysongs_subtitle
import eolssu.shared.generated.resources.mysongs_title
import org.jetbrains.compose.resources.stringResource

/** SONG-03 화면의 사용자 행동. */
class MySongsActions(
    val onBack: () -> Unit,
    val onSelectTab: (MySongsTab) -> Unit,
    val onSelectFilter: (SongLevelFilter) -> Unit,
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

/** 제공 곡 제목 밑 문구: 장단 이름(목록 응답에 없으면 '장단 없음'), 평가할 수 없는 곡이면 뒤에 '· 연습 전용'. */
@Composable
internal fun providedSongMeta(song: SongSummary): String {
    val jangdan = if (song.jangdanName.isBlank()) stringResource(Res.string.mysongs_no_jangdan) else jangdanLabel(song.jangdanName)
    return if (song.evaluable) jangdan else stringResource(Res.string.mysongs_practice_only, jangdan)
}

@Composable
private fun MySongsCompact(state: MySongsUiState, snackbarHostState: SnackbarHostState, actions: MySongsActions, instrumentPicker: @Composable () -> Unit) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.mysongs_title),
                onBack = actions.onBack,
                subtitle = stringResource(Res.string.mysongs_subtitle),
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
 * SONG-03 본문(`.body`, 간격 12dp): 탭 · 제공 곡(단계 필터 · 안내 · 곡 줄) 또는 내 악보(악보 추가 · 'n개' · 악보 줄).
 * 안내·개수 문구는 왼쪽 4dp 들이고 목록과 8dp 떨어진다. [columns]가 2 이상이면 줄을 격자로 놓는다.
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
        when (state.tab) {
            MySongsTab.Songs -> {
                SongLevelChips(state.filter, actions.onSelectFilter, singleLine = true)
                if (state.loadState != SongLoadState.Ready || state.songs.isEmpty()) {
                    SongLoadNotice(state.loadState, actions.onRetry)
                } else if (state.visibleSongs.isEmpty()) {
                    SongEmptyResult(
                        onReset = { actions.onSelectFilter(SongLevelFilter.All) },
                        body = stringResource(Res.string.mysongs_empty_body),
                        resetLabel = stringResource(Res.string.mysongs_reset_filter),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ListNote(stringResource(Res.string.mysongs_songs_hint), Modifier.weight(1f))
                            SongSortButton(state.sort, actions.onToggleSort, small = true)
                        }
                        CardGrid(state.visibleSongs, columns, itemKey = { it.id }, spacing = rowGap) { song, rowModifier ->
                            ProvidedSongRow(song, providedSongMeta(song), onClick = { actions.onOpenSong(song.id) }, modifier = rowModifier)
                        }
                    }
                }
            }
            MySongsTab.Sheets -> {
                AddSheetButton(actions.onAddSheet)
                val sheets = state.sheets.orEmpty()
                if (state.sheetLoadState != SongLoadState.Ready) SongLoadNotice(state.sheetLoadState, actions.onRetry)
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
private fun ListNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        color = EolssuTheme.colors.textMuted,
    )
}
