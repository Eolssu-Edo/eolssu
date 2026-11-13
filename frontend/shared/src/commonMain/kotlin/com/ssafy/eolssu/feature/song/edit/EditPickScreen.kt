package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ssafy.eolssu.feature.song.MySongsTab
import com.ssafy.eolssu.feature.song.SongLevelFilter
import com.ssafy.eolssu.feature.song.component.MySheetPickRow
import com.ssafy.eolssu.feature.song.component.MySongsTabSwitch
import com.ssafy.eolssu.feature.song.component.ProvidedSongRow
import com.ssafy.eolssu.feature.song.component.SongEmptyResult
import com.ssafy.eolssu.feature.song.component.SongLevelChips
import com.ssafy.eolssu.feature.song.component.providedSongMeta
import eolssu.shared.generated.resources.mysongs_sheet_count
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.SongSearchField
import com.ssafy.eolssu.core.ui.component.SongSortButton
import com.ssafy.eolssu.feature.song.edit.component.HintText
import com.ssafy.eolssu.feature.song.edit.component.PillChip
import com.ssafy.eolssu.feature.song.edit.component.PillButton
import eolssu.shared.generated.resources.Res
import com.ssafy.eolssu.data.song.PublishedSheetCopyProblem
import eolssu.shared.generated.resources.sheetedit_pick_copy
import eolssu.shared.generated.resources.sheetedit_pick_copying
import eolssu.shared.generated.resources.sheetedit_pick_copy_failed
import eolssu.shared.generated.resources.sheetedit_pick_copy_limit
import eolssu.shared.generated.resources.sheetedit_pick_copy_conversion_in_progress
import eolssu.shared.generated.resources.sheetedit_pick_copy_not_found
import eolssu.shared.generated.resources.sheetedit_pick_confirm
import eolssu.shared.generated.resources.sheetedit_pick_empty_mine
import eolssu.shared.generated.resources.sheetedit_pick_empty_songs
import eolssu.shared.generated.resources.sheetedit_pick_failed
import eolssu.shared.generated.resources.sheetedit_pick_loading
import eolssu.shared.generated.resources.sheetedit_pick_need_instrument
import eolssu.shared.generated.resources.sheetedit_pick_retry
import eolssu.shared.generated.resources.sheetedit_pick_step_instrument
import eolssu.shared.generated.resources.sheetedit_pick_step_sheet
import eolssu.shared.generated.resources.sheetedit_pick_unchecked
import eolssu.shared.generated.resources.sheetedit_pick_title
import org.jetbrains.compose.resources.stringResource

/**
 * 불러올 악보 고르기(EditPick): 1) 편집할 악기 → 2) 그 악기의 제공 곡 / 내 악보 탭, 제공 곡 검색, 고를 수 있는 줄 목록.
 * 악기를 고르기 전에는 악보를 보이지 않는다. 줄을 누르면 고르기만 하고 오른쪽 위 '선택'으로 편집에 간다(고르기 전에는 흐린 '선택').
 */
@Composable
fun EditPickScreen(
    state: EditPickUiState,
    onBack: () -> Unit,
    onSelectInstrument: (Instrument) -> Unit,
    onRetry: () -> Unit,
    onSelectTab: (EditPickTab) -> Unit,
    onSearch: (String) -> Unit,
    onToggleSort: () -> Unit,
    onToggle: (EditPickTarget) -> Unit,
    onConfirm: () -> Unit,
    onSelectFilter: (SongLevelFilter) -> Unit = {},
) {
    // 연습곡 고르기처럼 회색 바탕 · 테두리 없는 흰 칸(S15P31E102-768).
    EolssuScaffold(
        containerColor = EolssuTheme.colors.groupedBackground,
        bottomDivider = false,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.sheetedit_pick_title),
                onBack = onBack,
            ) {
                val action = when {
                    state.copying -> Res.string.sheetedit_pick_copying
                    state.tab == EditPickTab.Songs -> Res.string.sheetedit_pick_copy
                    else -> Res.string.sheetedit_pick_confirm
                }
                PillButton(stringResource(action), onConfirm, enabled = state.canSelect, square = true)
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepLabel(stringResource(Res.string.sheetedit_pick_step_instrument))
                // 악기 이름이 길어도 잘리지 않게 가로로 민다. 연습 악기 설정은 바꾸지 않는다.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Instrument.entries.forEach { instrument ->
                        PillChip(stringResource(instrument.label), selected = instrument == state.instrument, onClick = { onSelectInstrument(instrument) },
                            containerColor = EolssuTheme.colors.surface)
                    }
                }
                StepLabel(stringResource(Res.string.sheetedit_pick_step_sheet))
            }
            if (state.load != EditPickLoad.Ready) {
                PickStatus(state.load, onRetry, Modifier.weight(1f))
            } else {
                PickLists(state, onSelectTab, onSearch, onSelectFilter, onToggleSort, onToggle, Modifier.weight(1f))
            }
        }
    }
}

/**
 * 연습곡 고르기 목록(SONG-03 본문)과 같은 모양: 탭 · 두 탭이 함께 쓰는 검색칸 · 제공 곡(단계 칩 · 오른쪽 정렬 버튼 · 곡 줄)
 * 또는 내 악보('n개' · 악보 줄). 줄을 누르면 고르기만 하고 고른 줄은 연한 연두 바탕이다(S15P31E102-889).
 */
@Composable
private fun PickLists(
    state: EditPickUiState,
    onSelectTab: (EditPickTab) -> Unit,
    onSearch: (String) -> Unit,
    onSelectFilter: (SongLevelFilter) -> Unit,
    onToggleSort: () -> Unit,
    onToggle: (EditPickTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MySongsTabSwitch(
            tab = if (state.tab == EditPickTab.Songs) MySongsTab.Songs else MySongsTab.Sheets,
            songCount = state.songs.size,
            sheetCount = state.sheets.size,
            onSelect = { onSelectTab(if (it == MySongsTab.Songs) EditPickTab.Songs else EditPickTab.Mine) },
        )
        SongSearchField(state.query, onSearch, onClear = { onSearch("") }, bordered = false)
        state.copyProblem?.let { problem ->
            val message = when (problem) {
                PublishedSheetCopyProblem.Limit -> Res.string.sheetedit_pick_copy_limit
                PublishedSheetCopyProblem.ConversionInProgress -> Res.string.sheetedit_pick_copy_conversion_in_progress
                PublishedSheetCopyProblem.NotFound -> Res.string.sheetedit_pick_copy_not_found
                PublishedSheetCopyProblem.Failed -> Res.string.sheetedit_pick_copy_failed
            }
            HintText(stringResource(message), size = 12)
        }
        when (state.tab) {
            EditPickTab.Songs -> {
                SongLevelChips(state.filter, onSelectFilter, singleLine = true, bordered = false)
                val songs = state.visibleSongs
                when {
                    state.songs.isEmpty() -> EmptyRow(stringResource(Res.string.sheetedit_pick_empty_songs))
                    songs.isEmpty() -> SongEmptyResult(onReset = { onSearch(""); onSelectFilter(SongLevelFilter.All) })
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SongSortButton(state.sort, onToggleSort, Modifier.align(Alignment.End), small = true, bordered = false)
                        songs.forEach { song ->
                            val target = EditPickTarget.Song(song.id)
                            ProvidedSongRow(song, providedSongMeta(song), onClick = { onToggle(target) },
                                selected = state.picked == target, bordered = false)
                        }
                    }
                }
            }
            EditPickTab.Mine -> {
                if (state.uncheckedSheets > 0) HintText(stringResource(Res.string.sheetedit_pick_unchecked, state.uncheckedSheets), size = 12)
                val sheets = state.visibleSheets
                when {
                    state.sheets.isEmpty() -> EmptyRow(stringResource(Res.string.sheetedit_pick_empty_mine))
                    sheets.isEmpty() -> SongEmptyResult(onReset = { onSearch("") })
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListNote(stringResource(Res.string.mysongs_sheet_count, sheets.size))
                        sheets.forEach { sheet ->
                            val target = EditPickTarget.Sheet(sheet.id)
                            MySheetPickRow(sheet, onClick = { onToggle(target) }, selected = state.picked == target, bordered = false)
                        }
                    }
                }
            }
        }
    }
}

/** 목록 위 작은 회색 문구(내 악보 목록의 'n개'와 같음). */
@Composable
private fun ListNote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
        color = EolssuTheme.colors.textMuted,
    )
}

@Composable
private fun StepLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = EolssuTheme.colors.text)
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
        color = EolssuTheme.colors.textMuted,
        textAlign = TextAlign.Center,
    )
}

/** 악기를 고르기 전·불러오는 중·실패 안내. 실패하면 '다시 시도'를 둔다. 예시 악보로 대신하지 않는다. */
@Composable
private fun PickStatus(load: EditPickLoad, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyRow(
            stringResource(
                when (load) {
                    EditPickLoad.Loading -> Res.string.sheetedit_pick_loading
                    EditPickLoad.Failed -> Res.string.sheetedit_pick_failed
                    else -> Res.string.sheetedit_pick_need_instrument
                },
            ),
        )
        if (load == EditPickLoad.Failed) PillChip(stringResource(Res.string.sheetedit_pick_retry), selected = false, onClick = onRetry, containerColor = EolssuTheme.colors.surface)
    }
}
