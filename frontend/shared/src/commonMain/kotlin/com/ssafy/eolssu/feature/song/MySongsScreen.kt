package com.ssafy.eolssu.feature.song

import com.ssafy.eolssu.core.diagnostics.UserActionLog
import com.ssafy.eolssu.core.diagnostics.QaTarget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.InstrumentPicture
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
import com.ssafy.eolssu.core.model.Instrument
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.component.AddSheetButton
import com.ssafy.eolssu.feature.song.component.CardGrid
import com.ssafy.eolssu.feature.song.component.DeleteSheetDialog
import com.ssafy.eolssu.feature.song.component.MySheetRow
import com.ssafy.eolssu.feature.song.component.MySongsTabSwitch
import com.ssafy.eolssu.feature.song.component.ProvidedSongRow
import com.ssafy.eolssu.feature.song.component.providedSongMeta
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.component.SongEmptyResult
import com.ssafy.eolssu.feature.song.component.SongLevelChips
import com.ssafy.eolssu.core.ui.component.SongSearchField
import com.ssafy.eolssu.core.ui.component.SongSortButton
import com.ssafy.eolssu.feature.song.layout.MySongsTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_reset_filter
import eolssu.shared.generated.resources.mysongs_sheet_count
import eolssu.shared.generated.resources.mysongs_sheet_limit_note
import com.ssafy.eolssu.feature.song.edit.MY_SHEET_LIMIT
import eolssu.shared.generated.resources.mysongs_title
import eolssu.shared.generated.resources.mysongs_other_instrument
import eolssu.shared.generated.resources.mysongs_other_sheets_hide
import eolssu.shared.generated.resources.mysongs_other_sheets_show
import eolssu.shared.generated.resources.mysongs_own_sheets
import eolssu.shared.generated.resources.mysongs_own_sheets_empty
import eolssu.shared.generated.resources.mysongs_own_sheets_no_match
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
    /** 변환 중·실패한 악보 줄을 누르면 변환 대기 화면을 다시 연다(S15P31E102-998). null이면 눌리지 않는다. */
    val onOpenConversion: ((MySheet) -> Unit)? = null,
    /** 내 악보의 '다른 악기 악보 보기'·'접기'(S15P31E102-1094). */
    val onToggleOtherSheets: () -> Unit = {},
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
    state.deleting?.let { DeleteSheetDialog(it, actions.onConfirmDelete, actions.onCancelDelete) }
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
                // 좁은 휴대폰에서는 제목을 다 보이게 하고 악기 이름을 ‘정악…’처럼 줄인다(S15P31E102-924).
                shrinkRight = true,
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
 * 또는 내 악보(악보 추가 · 'n개' · 악보 줄, 검색에 맞는 악보가 없으면 '찾는 곡이 없어요.').
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
        // 목록을 받지 못했으면(불러오는 중·연결 끊김·오류) '제공 곡 0'·'내 악보 0'으로 보이지 않게 개수를 숨긴다.
        MySongsTabSwitch(
            state.tab,
            state.songs.size.takeIf { state.loadState == SongLoadState.Ready },
            state.sheetCount.takeIf { state.sheetLoadState == SongLoadState.Ready || it > 0 },
            actions.onSelectTab,
            Modifier.padding(top = 4.dp),
        )
        SongSearchField(state.query, actions.onQueryChange, actions.onClearQuery, bordered = false)
        val searching = state.query.isNotBlank()
        when (state.tab) {
            MySongsTab.Songs -> {
                SongLevelChips(state.filter, actions.onSelectFilter, singleLine = true, bordered = false)
                if (state.loadState != SongLoadState.Ready || state.songs.isEmpty()) {
                    SongLoadNotice(state.loadState, actions.onRetry)
                } else if (state.visibleSongs.isEmpty()) {
                    // 검색어가 있으면 평가할 곡 고르기와 같은 '검색 조건 초기화'를, 단계 필터만 걸었으면 '전체 곡 보기'를 둔다.
                    if (searching) SongEmptyResult(onReset = actions.onResetSongFilters)
                    else SongEmptyResult(
                        onReset = actions.onResetSongFilters,
                        resetLabel = stringResource(Res.string.mysongs_reset_filter),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SongSortButton(state.sort, actions.onToggleSort, Modifier.align(Alignment.End), small = true, bordered = false)
                        CardGrid(state.visibleSongs, columns, itemKey = { it.id }, spacing = rowGap) { song, rowModifier ->
                            ProvidedSongRow(song, providedSongMeta(song), onClick = UserActionLog.click(QaTarget.MY_SONGS_SCREEN_01, { actions.onOpenSong(song.id) }), modifier = rowModifier, bordered = false)
                        }
                    }
                }
            }
            MySongsTab.Sheets -> {
                AddSheetButton(actions.onAddSheet)
                if (state.sheetLoadState != SongLoadState.Ready) SongLoadNotice(state.sheetLoadState, actions.onRetry)
                if (state.sheetLoadState == SongLoadState.Ready && state.visibleSheets.isEmpty() && state.sheetCount > 0 && searching) {
                    SongEmptyResult(onReset = actions.onClearQuery)
                }
                if (state.visibleSheets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 나누거나 찾아도 저장 한도는 전체 개수로 센다(S15P31E102-1052).
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ListNote(stringResource(Res.string.mysongs_sheet_count, state.sheetCount, MY_SHEET_LIMIT))
                            ListNote(stringResource(Res.string.mysongs_sheet_limit_note, MY_SHEET_LIMIT))
                        }
                        MySheetGroups(state, actions, columns, rowGap, searching)
                    }
                }
            }
        }
    }
}

/**
 * 내 악보 줄을 상단 연습 악기 기준으로 나눠 놓는다(S15P31E102-1094).
 * 연습 악기 악보를 먼저 두고, 다른 악기 악보는 '다른 악기 악보 n개 보기' 아래에 악기 이름별 묶음으로 접어 둔다.
 * 연습 악기 악보가 (검색 결과에) 없으면 그 사실을 한 줄로 알리고 다른 악기 묶음을 처음부터 보인다.
 * 다른 악기 악보가 없거나 연습 악기를 아직 모르면 나누지 않고 예전처럼 한 목록으로 둔다.
 */
@Composable
private fun MySheetGroups(state: MySongsUiState, actions: MySongsActions, columns: Int, rowGap: Dp, searching: Boolean) {
    val own = state.ownSheets
    val practice = state.practiceInstrument
    val split = practice != null && state.otherSheetCount > 0
    if (split) {
        val name = stringResource(practice.label)
        SheetGroupTitle(
            practice,
            when {
                own.isNotEmpty() -> stringResource(Res.string.mysongs_own_sheets, name, own.size)
                searching && state.hasOwnSheets -> stringResource(Res.string.mysongs_own_sheets_no_match, name)
                else -> stringResource(Res.string.mysongs_own_sheets_empty, name)
            },
        )
    }
    if (own.isNotEmpty()) MySheetRows(own, state, actions, columns, rowGap)
    if (split && own.isNotEmpty()) OtherSheetsToggle(state.otherSheetCount, state.otherSheetsOpen, actions.onToggleOtherSheets)
    if (state.otherSheetsShown) {
        state.otherSheetGroups.forEach { group ->
            OtherInstrumentTitle(group.instrument)
            MySheetRows(group.sheets, state, actions, columns, rowGap)
        }
    }
}

@Composable
private fun MySheetRows(sheets: List<MySheet>, state: MySongsUiState, actions: MySongsActions, columns: Int, rowGap: Dp) {
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
            bordered = false,
            onOpenConversion = actions.onOpenConversion?.let { open -> { open(sheet) } },
            conversionDetailed = state.mine.hasConversionDetail(sheet),
        )
    }
}

/** 연습 악기 악보 묶음의 제목: 악기 그림 · 진한 연두 글씨(왼쪽 4dp). 다른 악기 묶음 제목과 같은 그림을 둔다(S15P31E102-1137). */
@Composable
private fun SheetGroupTitle(instrument: Instrument, text: String) {
    Row(
        Modifier.padding(start = 4.dp, top = 4.dp).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstrumentPicture(instrument, size = 20.dp)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.accentText,
        )
    }
}

/** 다른 악기 묶음의 제목: 악기 그림 · 악기 이름. 앱이 모르는 악기는 '기타 악기'로 적는다. */
@Composable
private fun OtherInstrumentTitle(instrument: Instrument?) {
    Row(
        Modifier.padding(start = 4.dp, top = 8.dp).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (instrument != null) InstrumentPicture(instrument, size = 20.dp)
        Text(
            stringResource(instrument?.label ?: Res.string.mysongs_other_instrument),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            color = EolssuTheme.colors.textMuted,
        )
    }
}

/** '다른 악기 악보 n개 보기 ⌄' / '접기 ⌃'. 흰 바탕 알약 줄로 악보 줄과 구분되게 가운데 글씨를 둔다. */
@Composable
private fun OtherSheetsToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = UserActionLog.click(QaTarget.MY_SONGS_SCREEN_02, onToggle),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        contentColor = colors.accentText,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (open) Res.string.mysongs_other_sheets_hide else Res.string.mysongs_other_sheets_show, count),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            )
            Icon(EolssuIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(14.dp).rotate(if (open) 180f else 0f))
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
