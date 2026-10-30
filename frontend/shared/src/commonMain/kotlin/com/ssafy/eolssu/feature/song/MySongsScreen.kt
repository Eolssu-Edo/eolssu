package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.ssafy.eolssu.feature.song.component.MySheetCard
import com.ssafy.eolssu.feature.song.component.MySongsTabSwitch
import com.ssafy.eolssu.feature.song.component.SongCard
import com.ssafy.eolssu.feature.song.component.SongLoadNotice
import com.ssafy.eolssu.feature.song.layout.MySongsTabletLayout
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_practice_only
import eolssu.shared.generated.resources.mysongs_sheet_count
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
)

/**
 * SONG-03 연습곡 고르기: 제공 곡 · 내 악보 탭, 악보 추가 시트.
 * 태블릿은 카드를 세로([WindowWidthClass.Medium]) 2열, 가로([WindowWidthClass.Expanded]) 3열로 놓는다.
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
        Column(Modifier.fillMaxSize()) {
            MySongsTabSwitch(state.tab, state.songs.size, state.sheetCount, actions.onSelectTab, Modifier.padding(horizontal = 20.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state.tab) {
                    MySongsTab.Songs -> {
                        if (state.loadState != SongLoadState.Ready || state.songs.isEmpty()) {
                            item { SongLoadNotice(state.loadState, actions.onRetry) }
                        } else items(state.songs, key = { it.id }) { song ->
                        SongCard(
                            song,
                            meta = providedSongMeta(song),
                            onClick = { actions.onOpenSong(song.id) },
                            evaluableBadge = song.evaluable,
                        )
                        }
                    }
                    MySongsTab.Sheets -> {
                        item(key = "add") { AddSheetButton(actions.onOpenAddSheet) }
                        val sheets = state.sheets.orEmpty()
                        if (sheets.isNotEmpty()) {
                            item(key = "count") {
                                Text(
                                    stringResource(Res.string.mysongs_sheet_count, sheets.size),
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = EolssuTheme.colors.textMuted,
                                )
                            }
                        }
                        items(sheets, key = { it.id }) { sheet ->
                            MySheetCard(sheet, onOpen = { actions.onOpenSheet(sheet) }, onEdit = { actions.onEditSheet(sheet) })
                        }
                    }
                }
            }
        }
    }
}
