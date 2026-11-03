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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.model.SongSummary
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.adaptive.WindowWidthClass
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.component.AddSheetBottomSheet
import com.ssafy.eolssu.feature.song.component.AddSheetButton
import com.ssafy.eolssu.feature.song.component.AddSheetOption
import com.ssafy.eolssu.feature.song.component.CardGrid
import com.ssafy.eolssu.feature.song.component.MySheetRow
import com.ssafy.eolssu.feature.song.component.MySongsTabSwitch
import com.ssafy.eolssu.feature.song.component.ProvidedSongRow
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.component.SongInstrumentFilter
import com.ssafy.eolssu.feature.song.layout.MySongsTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_practice_only
import eolssu.shared.generated.resources.mysongs_sheet_count
import eolssu.shared.generated.resources.mysongs_songs_hint
import eolssu.shared.generated.resources.mysongs_subtitle
import eolssu.shared.generated.resources.mysongs_title
import org.jetbrains.compose.resources.stringResource

/** SONG-03 화면의 사용자 행동. */
class MySongsActions(
    val onBack: () -> Unit,
    val onSelectTab: (MySongsTab) -> Unit,
    val onOpenSong: (songId: String) -> Unit,
    val onOpenSheet: (MySheet) -> Unit,
    val onEditSheet: (MySheet) -> Unit,
    val onOpenAddSheet: () -> Unit,
    val onCloseAddSheet: () -> Unit,
    val onSelectAddOption: (AddSheetOption) -> Unit,
    val onRetry: () -> Unit,
    val onSelectInstrument: (String?) -> Unit,
)

/**
 * SONG-03 연습곡 고르기: 제공 곡 · 내 악보 탭, 악보 추가 시트.
 * 휴대폰·태블릿 모두 같은 본문([MySongsBody])을 쓰고, 태블릿 가로([WindowWidthClass.Expanded])만 줄을 두 열로 놓는다.
 */
@Composable
fun MySongsScreen(state: MySongsUiState, widthClass: WindowWidthClass, snackbarHostState: SnackbarHostState, actions: MySongsActions) {
    if (widthClass == WindowWidthClass.Compact) {
        MySongsCompact(state, snackbarHostState, actions)
    } else {
        MySongsTabletLayout(state, expanded = widthClass == WindowWidthClass.Expanded, snackbarHostState, actions)
    }
    // 시트 너비는 Material 기본값(최대 640dp, 가운데)이라 태블릿 와이어프레임과 같다.
    if (state.addSheetOpen) {
        AddSheetBottomSheet(onSelect = actions.onSelectAddOption, onDismiss = actions.onCloseAddSheet)
    }
}

/** 제공 곡의 보조 문구: 평가 곡은 장단, 아니면 '장단 · 연습 전용'. */
@Composable
internal fun providedSongMeta(song: SongSummary): String {
    val jangdan = jangdanLabel(song.jangdanName)
    return if (song.evaluable) jangdan else stringResource(Res.string.mysongs_practice_only, jangdan)
}

@Composable
private fun MySongsCompact(state: MySongsUiState, snackbarHostState: SnackbarHostState, actions: MySongsActions) {
    EolssuScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            EolssuTopBar(
                stringResource(Res.string.mysongs_title),
                onBack = actions.onBack,
                subtitle = stringResource(Res.string.mysongs_subtitle),
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
 * SONG-03 본문(`.body`, 간격 12dp): 탭 · 제공 곡(안내 · 곡 줄) 또는 내 악보(악보 추가 · 'n개' · 악보 줄).
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
                SongInstrumentFilter(state.instruments, state.instrumentId, actions.onSelectInstrument)
                if (state.loadState != SongLoadState.Ready || state.songs.isEmpty()) {
                    SongLoadNotice(state.loadState, actions.onRetry)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListNote(stringResource(Res.string.mysongs_songs_hint))
                        CardGrid(state.songs, columns, itemKey = { it.id }, spacing = rowGap) { song, rowModifier ->
                            ProvidedSongRow(song, providedSongMeta(song), onClick = { actions.onOpenSong(song.id) }, modifier = rowModifier)
                        }
                    }
                }
            }
            MySongsTab.Sheets -> {
                AddSheetButton(actions.onOpenAddSheet)
                val sheets = state.sheets.orEmpty()
                if (sheets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListNote(stringResource(Res.string.mysongs_sheet_count, sheets.size))
                        CardGrid(sheets, columns, itemKey = { it.id }, spacing = rowGap) { sheet, rowModifier ->
                            MySheetRow(sheet, onOpen = { actions.onOpenSheet(sheet) }, onEdit = { actions.onEditSheet(sheet) }, modifier = rowModifier)
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
