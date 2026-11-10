package com.ssafy.eolssu.feature.song.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.core.ui.component.monthDayLabel
import com.ssafy.eolssu.core.ui.component.songLevelLabel
import com.ssafy.eolssu.feature.song.component.SongSearchField
import com.ssafy.eolssu.feature.song.component.SongSortButton
import com.ssafy.eolssu.feature.song.component.songTitleMarquee
import com.ssafy.eolssu.feature.song.edit.component.HintText
import com.ssafy.eolssu.feature.song.edit.component.PillChip
import com.ssafy.eolssu.feature.song.edit.component.PillButton
import com.ssafy.eolssu.feature.song.edit.component.RadioDot
import com.ssafy.eolssu.feature.song.edit.component.SegmentTabs
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_tab_sheets
import eolssu.shared.generated.resources.mysongs_tab_songs
import com.ssafy.eolssu.data.song.PublishedSheetCopyProblem
import eolssu.shared.generated.resources.sheetedit_pick_copy
import eolssu.shared.generated.resources.sheetedit_pick_copying
import eolssu.shared.generated.resources.sheetedit_pick_copy_failed
import eolssu.shared.generated.resources.sheetedit_pick_copy_limit
import eolssu.shared.generated.resources.sheetedit_pick_copy_conversion_in_progress
import eolssu.shared.generated.resources.sheetedit_pick_copy_not_found
import eolssu.shared.generated.resources.sheetedit_pick_hint_copy
import eolssu.shared.generated.resources.sheetedit_pick_confirm
import eolssu.shared.generated.resources.sheetedit_pick_empty
import eolssu.shared.generated.resources.sheetedit_pick_empty_mine
import eolssu.shared.generated.resources.sheetedit_pick_empty_songs
import eolssu.shared.generated.resources.sheetedit_pick_failed
import eolssu.shared.generated.resources.sheetedit_pick_loading
import eolssu.shared.generated.resources.sheetedit_pick_need_instrument
import eolssu.shared.generated.resources.sheetedit_pick_retry
import eolssu.shared.generated.resources.sheetedit_pick_step_instrument
import eolssu.shared.generated.resources.sheetedit_pick_step_sheet
import eolssu.shared.generated.resources.sheetedit_pick_unchecked
import eolssu.shared.generated.resources.sheetedit_pick_hint_mine
import eolssu.shared.generated.resources.sheetedit_pick_hint_picked
import eolssu.shared.generated.resources.sheetedit_pick_hint_songs
import eolssu.shared.generated.resources.sheetedit_pick_kind_jeongganbo
import eolssu.shared.generated.resources.sheetedit_pick_meta
import eolssu.shared.generated.resources.sheetedit_pick_meta_updated
import eolssu.shared.generated.resources.sheetedit_pick_subtitle
import eolssu.shared.generated.resources.sheetedit_pick_title
import org.jetbrains.compose.resources.stringResource

/**
 * 불러올 악보 고르기(EditPick): 1) 편집할 악기 → 2) 그 악기의 내 악보 / 제공 곡 탭, 제공 곡 검색, 고를 수 있는 줄 목록.
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
) {
    EolssuScaffold(
        containerColor = EolssuTheme.colors.surface,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.sheetedit_pick_title),
                onBack = onBack,
                subtitle = stringResource(Res.string.sheetedit_pick_subtitle),
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
                        PillChip(stringResource(instrument.label), selected = instrument == state.instrument, onClick = { onSelectInstrument(instrument) })
                    }
                }
                StepLabel(stringResource(Res.string.sheetedit_pick_step_sheet))
            }
            if (state.load != EditPickLoad.Ready) {
                PickStatus(state.load, onRetry, Modifier.weight(1f))
            } else {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SegmentTabs(
                        labels = listOf(stringResource(Res.string.mysongs_tab_sheets), stringResource(Res.string.mysongs_tab_songs)),
                        selected = state.tab.ordinal,
                        onSelect = { onSelectTab(EditPickTab.entries[it]) },
                        large = true,
                        counts = listOf(state.sheets.size, state.songs.size),
                    )
                    if (state.tab == EditPickTab.Songs) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SongSearchField(state.query, onSearch, onClear = { onSearch("") }, modifier = Modifier.weight(1f))
                            SongSortButton(state.sort, onToggleSort)
                        }
                    }
                    val picked = state.pickedTitle
                    HintText(
                        when {
                            picked != null && state.picked is EditPickTarget.Song ->
                                stringResource(Res.string.sheetedit_pick_hint_copy, picked)
                            picked != null -> stringResource(Res.string.sheetedit_pick_hint_picked, picked)
                            state.tab == EditPickTab.Mine -> stringResource(Res.string.sheetedit_pick_hint_mine)
                            else -> stringResource(Res.string.sheetedit_pick_hint_songs)
                        },
                        size = 12,
                    )
                    state.copyProblem?.let { problem ->
                        val message = when (problem) {
                            PublishedSheetCopyProblem.Limit -> Res.string.sheetedit_pick_copy_limit
                            PublishedSheetCopyProblem.ConversionInProgress -> Res.string.sheetedit_pick_copy_conversion_in_progress
                            PublishedSheetCopyProblem.NotFound -> Res.string.sheetedit_pick_copy_not_found
                            PublishedSheetCopyProblem.Failed -> Res.string.sheetedit_pick_copy_failed
                        }
                        HintText(stringResource(message), size = 12)
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (state.tab) {
                        EditPickTab.Mine -> {
                            if (state.uncheckedSheets > 0) item { HintText(stringResource(Res.string.sheetedit_pick_unchecked, state.uncheckedSheets), size = 12) }
                            if (state.sheets.isEmpty()) item { EmptyRow(stringResource(Res.string.sheetedit_pick_empty_mine)) }
                            items(state.sheets, key = { it.id }) { sheet ->
                                val target = EditPickTarget.Sheet(sheet.id)
                                val meta = listOfNotNull(
                                    sheet.jangdanName?.let { jangdanLabel(it) },
                                    stringResource(Res.string.sheetedit_pick_meta_updated, monthDayLabel(sheet.updatedAt)),
                                ).joinToString(" · ")
                                PickRow(
                                    tile = stringResource(Res.string.sheetedit_pick_kind_jeongganbo),
                                    tileStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    title = sheet.title,
                                    meta = meta,
                                    selected = state.picked == target,
                                    onClick = { onToggle(target) },
                                )
                            }
                        }
                        EditPickTab.Songs -> {
                            items(state.visibleSongs, key = { it.id }) { song ->
                                val target = EditPickTarget.Song(song.id)
                                PickRow(
                                    tile = song.title.take(1),
                                    tileStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                                    title = song.title,
                                    meta = stringResource(Res.string.sheetedit_pick_meta, jangdanLabel(song.jangdanName), songLevelLabel(song.level)),
                                    selected = state.picked == target,
                                    onClick = { onToggle(target) },
                                )
                            }
                            if (state.songs.isEmpty()) {
                                item { EmptyRow(stringResource(Res.string.sheetedit_pick_empty_songs)) }
                            } else if (state.visibleSongs.isEmpty()) {
                                item {
                                    Text(
                                        stringResource(Res.string.sheetedit_pick_empty, state.query.trim()),
                                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                        color = EolssuTheme.colors.textMuted,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
        if (load == EditPickLoad.Failed) PillChip(stringResource(Res.string.sheetedit_pick_retry), selected = false, onClick = onRetry)
    }
}

/** 고를 수 있는 줄(최소 64dp · 모서리 14): 왼쪽 44dp 글자 타일, 이름·보조 문구, 오른쪽 고름 표시. 고르면 연두 바탕. */
@Composable
private fun PickRow(tile: String, tileStyle: TextStyle, title: String, meta: String, selected: Boolean, onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primary else colors.surface,
        contentColor = colors.text,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.lineSoft),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(if (selected) colors.surface else colors.accentTint, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(tile, style = tileStyle)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    modifier = Modifier.songTitleMarquee(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                Text(meta, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = colors.textSub)
            }
            RadioDot(selected, size = 20.dp, background = colors.surface)
        }
    }
}
