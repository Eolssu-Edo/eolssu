package com.ssafy.eolssu.feature.song.edit

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.ui.draw.alpha
import com.ssafy.eolssu.feature.song.edit.component.sheetAddBottomBar
import com.ssafy.eolssu.feature.song.edit.component.sheetAddContentMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.SongLevel
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.*
import com.ssafy.eolssu.data.song.PublishedSheetCopyProblem
import com.ssafy.eolssu.feature.song.MySongsTab
import com.ssafy.eolssu.feature.song.component.MySongsTabSwitch
import com.ssafy.eolssu.feature.song.component.songTitleMarquee
import com.ssafy.eolssu.feature.song.edit.component.HintText
import eolssu.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** 연습곡 고르기의 탭·검색·목록을 따르며, 고른 악보는 하단 버튼으로 복사하거나 연다. */
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
    val colors = EolssuTheme.colors
    val contentMaxWidth = sheetAddContentMaxWidth()
    EolssuScaffold(
        contentMaxWidth = contentMaxWidth,
        containerColor = colors.groupedBackground,
        bottomDivider = false,
        topBar = { EolssuTopBar(stringResource(Res.string.sheetedit_pick_title), onBack = onBack, maxWidth = contentMaxWidth) },
        bottomBar = sheetAddBottomBar(contentMaxWidth) {
            state.copyProblem?.let { problem ->
                val message = when (problem) {
                    PublishedSheetCopyProblem.Limit -> Res.string.sheetedit_pick_copy_limit
                    PublishedSheetCopyProblem.ConversionInProgress -> Res.string.sheetedit_pick_copy_conversion_in_progress
                    PublishedSheetCopyProblem.NotFound -> Res.string.sheetedit_pick_copy_not_found
                    PublishedSheetCopyProblem.Failed -> Res.string.sheetedit_pick_copy_failed
                }
                Text(stringResource(message), color = colors.recText, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            val action = stringResource(when {
                state.copying -> Res.string.sheetedit_pick_copying
                state.tab == EditPickTab.Songs -> Res.string.sheetedit_pick_copy
                else -> Res.string.sheetedit_pick_confirm
            })
            EolssuButton(action, onConfirm, enabled = state.canSelect, busy = state.copying,
                modifier = Modifier.semantics { stateDescription = action; liveRegion = LiveRegionMode.Polite }, qaTarget = QaTarget.EDIT_PICK_SCREEN_BUTTON_01)
        },
    ) {
        // 악기·탭·검색·정렬도 목록과 함께 스크롤한다. 360×640에서 검색 키보드를 열어도 목록이 보이게 한다(S15P31E102-972).
        // 악기·탭이 바뀌면 처음 위치로 돌아간다.
        key(state.instrument, state.tab) {
            LazyColumn(
                Modifier.fillMaxSize().selectableGroup(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header", contentType = "header") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.instrument == null) {
                            Text(stringResource(Res.string.sheetedit_pick_step_instrument), style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Instrument.entries.forEach { instrument ->
                                    Surface(onClick = UserActionLog.click(QaTarget.EDIT_PICK_SCREEN_01, { onSelectInstrument(instrument) }), shape = RoundedCornerShape(14.dp), color = colors.surface,
                                        modifier = Modifier.weight(1f).heightIn(min = 76.dp).semantics { role = Role.RadioButton; selected = false }) {
                                        Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            InstrumentPicture(instrument)
                                            Text(stringResource(instrument.label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(Res.string.sheetedit_pick_step_instrument), style = MaterialTheme.typography.labelLarge)
                                PracticeInstrumentDropdown(state.instrument, enabled = !state.copying, onSelect = onSelectInstrument, bordered = false)
                            }
                        }
                        // 로딩·오류 중에도 다른 탭으로 이동할 수 있다.
                        MySongsTabSwitch(
                            tab = if (state.tab == EditPickTab.Songs) MySongsTab.Songs else MySongsTab.Sheets,
                            songCount = state.songs.size,
                            sheetCount = state.sheets.size,
                            onSelect = { onSelectTab(if (it == MySongsTab.Songs) EditPickTab.Songs else EditPickTab.Mine) },
                        )
                        if (state.instrument != null && state.tab == EditPickTab.Songs) {
                            SongSearchField(state.query, onSearch, onClear = { onSearch("") }, bordered = false)
                            if (state.load == EditPickLoad.Ready && state.songs.isNotEmpty()) {
                                SongSortButton(state.sort, onToggleSort, Modifier.align(Alignment.End), small = true, bordered = false)
                            }
                        }
                    }
                }
                if (state.load != EditPickLoad.Ready) {
                    item(key = "status", contentType = "status") { PickStatus(state.load, onRetry) }
                } else {
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
                                PickRow(sheet.title, meta, state.picked == target, enabled = !state.copying, onClick = UserActionLog.click(QaTarget.EDIT_PICK_SCREEN_02, { onToggle(target) }))
                            }
                        }
                        EditPickTab.Songs -> {
                            items(state.visibleSongs, key = { it.id }) { song ->
                                val target = EditPickTarget.Song(song.id)
                                PickRow(song.title, jangdanLabel(song.jangdanName), state.picked == target,
                                    level = song.level, enabled = !state.copying, onClick = UserActionLog.click(QaTarget.EDIT_PICK_SCREEN_03, { onToggle(target) }))
                            }
                            if (state.songs.isEmpty()) item { EmptyRow(stringResource(Res.string.sheetedit_pick_empty_songs)) }
                            else if (state.visibleSongs.isEmpty()) item { EmptyRow(stringResource(Res.string.sheetedit_pick_empty, state.query.trim())) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(text, modifier = Modifier.fillMaxWidth().padding(top = 24.dp), style = MaterialTheme.typography.bodyMedium,
        color = EolssuTheme.colors.textMuted, textAlign = TextAlign.Center)
}

/** 탭마다 진행 표시·실패 안내·재시도를 제공한다. */
@Composable
private fun PickStatus(load: EditPickLoad, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (load == EditPickLoad.Loading) CircularProgressIndicator(Modifier.size(28.dp), color = EolssuTheme.colors.text, strokeWidth = 2.dp)
        EmptyRow(stringResource(when (load) {
            EditPickLoad.Loading -> Res.string.sheetedit_pick_loading
            EditPickLoad.Failed -> Res.string.sheetedit_pick_failed
            else -> Res.string.sheetedit_pick_need_instrument
        }))
        if (load == EditPickLoad.Failed) EolssuButton(stringResource(Res.string.sheetedit_pick_retry), onRetry,
            style = EolssuButtonStyle.Secondary, small = true, qaTarget = QaTarget.EDIT_PICK_SCREEN_BUTTON_02)
    }
}

/** 연습곡 줄과 같은 제목·장단·난이도 배치. 선택은 체크와 연두 바탕으로 함께 알린다. */
@Composable
private fun PickRow(title: String, meta: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, level: SongLevel? = null) {
    val colors = EolssuTheme.colors
    Surface(
        selected = selected,
        onClick = UserActionLog.click(QaTarget.EDIT_PICK_SCREEN_04, onClick),
        enabled = enabled,
        // 복사 중에는 누를 수 없음을 앱 버튼과 같은 0.45 흐림으로 알린다(S15P31E102-969).
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).alpha(if (enabled) 1f else 0.45f).semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accentTint else colors.surface,
        contentColor = colors.text,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, modifier = Modifier.songTitleMarquee(),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1)
                Text(meta, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = colors.textSub)
            }
            if (level != null) EolssuLevelBadge(level, small = true)
            Surface(shape = CircleShape, color = if (selected) colors.primary else colors.surface,
                border = if (selected) null else BorderStroke(1.5.dp, colors.lineStrong)) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (selected) Icon(EolssuIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onPrimary)
                }
            }
        }
    }
}
