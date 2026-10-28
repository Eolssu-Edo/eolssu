package com.ssafy.eolssu.feature.song.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.feature.song.MySongsActions
import com.ssafy.eolssu.feature.song.MySongsTab
import com.ssafy.eolssu.feature.song.MySongsUiState
import com.ssafy.eolssu.feature.song.component.AddSheetButton
import com.ssafy.eolssu.feature.song.component.CardGrid
import com.ssafy.eolssu.feature.song.component.MySheetCard
import com.ssafy.eolssu.feature.song.component.MySongsTabSwitch
import com.ssafy.eolssu.feature.song.component.SongCard
import com.ssafy.eolssu.feature.song.providedSongMeta
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_sheet_count
import eolssu.shared.generated.resources.mysongs_subtitle
import eolssu.shared.generated.resources.mysongs_tab_solo_hint
import eolssu.shared.generated.resources.mysongs_title
import org.jetbrains.compose.resources.stringResource

/**
 * SONG-03 태블릿 배치(와이어프레임 `Tab-MySongs`). 세로는 탭을 내용 너비 전체로, 카드 2열(높이 96dp 이상).
 * 가로·넓은 웹 창([expanded])은 탭을 왼쪽 360dp로 줄이고 내 악보 탭에서는 그 오른쪽에 '악보 추가'(420dp)를 두며, 카드 3열(제공 곡 120dp 이상).
 * 제공 곡 탭 아래에는 연습 모드로 열린다는 안내를 붙인다.
 */
@Composable
internal fun MySongsTabletLayout(
    state: MySongsUiState,
    expanded: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: MySongsActions,
) {
    SongTabletScaffold(
        expanded = expanded,
        title = stringResource(Res.string.mysongs_title),
        onBack = actions.onBack,
        subtitle = stringResource(Res.string.mysongs_subtitle),
        snackbarHostState = snackbarHostState,
    ) { gutter ->
        val columns = if (expanded) 3 else 2
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter)
                .padding(top = if (expanded) 4.dp else 0.dp, bottom = if (expanded) 28.dp else 32.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MySongsTabSwitch(
                    state.tab,
                    state.songs.size,
                    state.sheetCount,
                    actions.onSelectTab,
                    if (expanded) Modifier.width(360.dp) else Modifier.weight(1f),
                )
                if (expanded && state.tab == MySongsTab.Sheets) {
                    Spacer(Modifier.weight(1f))
                    AddSheetButton(actions.onOpenAddSheet, Modifier.width(420.dp), height = 48.dp, cornerRadius = 14.dp)
                }
            }
            when (state.tab) {
                MySongsTab.Songs -> {
                    CardGrid(state.songs, columns, itemKey = { it.id }) { song, modifier ->
                        SongCard(
                            song,
                            meta = providedSongMeta(song),
                            onClick = { actions.onOpenSong(song.id) },
                            modifier = modifier,
                            evaluableBadge = song.evaluable,
                            large = true,
                            minHeight = if (expanded) 120.dp else 96.dp,
                        )
                    }
                    Text(
                        stringResource(Res.string.mysongs_tab_solo_hint),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
                        color = EolssuTheme.colors.textMuted,
                    )
                }
                MySongsTab.Sheets -> {
                    if (!expanded) AddSheetButton(actions.onOpenAddSheet, height = 60.dp)
                    val sheets = state.sheets.orEmpty()
                    if (sheets.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.mysongs_sheet_count, sheets.size),
                            modifier = Modifier.padding(top = if (expanded) 0.dp else 16.dp, bottom = 10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = EolssuTheme.colors.textMuted,
                        )
                    }
                    CardGrid(sheets, columns, itemKey = { it.id }) { sheet, modifier ->
                        MySheetCard(
                            sheet,
                            onOpen = { actions.onOpenSheet(sheet) },
                            onEdit = { actions.onEditSheet(sheet) },
                            modifier = modifier,
                            large = true,
                        )
                    }
                }
            }
        }
    }
}
