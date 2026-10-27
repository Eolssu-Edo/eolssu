package com.ssafy.eolssu.feature.song

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.eolssu.core.model.MySheet
import com.ssafy.eolssu.core.theme.EolssuTheme
import com.ssafy.eolssu.core.ui.component.EolssuIcons
import com.ssafy.eolssu.core.ui.component.EolssuScaffold
import com.ssafy.eolssu.core.ui.component.EolssuTopBar
import com.ssafy.eolssu.core.ui.component.jangdanLabel
import com.ssafy.eolssu.feature.song.component.AddSheetBottomSheet
import com.ssafy.eolssu.feature.song.component.AddSheetOption
import com.ssafy.eolssu.feature.song.component.MySheetCard
import com.ssafy.eolssu.feature.song.component.SongCard
import com.ssafy.eolssu.feature.song.component.dashedBorder
import eolssu.shared.generated.resources.Res
import eolssu.shared.generated.resources.mysongs_add
import eolssu.shared.generated.resources.mysongs_practice_only
import eolssu.shared.generated.resources.mysongs_sheet_count
import eolssu.shared.generated.resources.mysongs_subtitle
import eolssu.shared.generated.resources.mysongs_tab_sheets
import eolssu.shared.generated.resources.mysongs_tab_songs
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
)

/** SONG-03 연습곡 고르기: 제공 곡 · 내 악보 탭, 악보 추가 시트. */
@Composable
fun MySongsScreen(state: MySongsUiState, snackbarHostState: SnackbarHostState, actions: MySongsActions) {
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
            TabSwitch(state, actions.onSelectTab, Modifier.padding(horizontal = 20.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state.tab) {
                    MySongsTab.Songs -> items(state.songs, key = { it.id }) { song ->
                        val jangdan = jangdanLabel(song.jangdan)
                        SongCard(
                            song,
                            meta = if (song.evaluable) jangdan else stringResource(Res.string.mysongs_practice_only, jangdan),
                            onClick = { actions.onOpenSong(song.id) },
                            evaluableBadge = song.evaluable,
                        )
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
    if (state.addSheetOpen) {
        AddSheetBottomSheet(onSelect = actions.onSelectAddOption, onDismiss = actions.onCloseAddSheet)
    }
}

/** 두 칸 탭(`role=tablist`): 고른 칸은 흰 바탕에 그림자. */
@Composable
private fun TabSwitch(state: MySongsUiState, onSelect: (MySongsTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = EolssuTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MySongsTab.entries.forEach { tab ->
            val selected = state.tab == tab
            val label = when (tab) {
                MySongsTab.Songs -> stringResource(Res.string.mysongs_tab_songs, state.songs.size)
                MySongsTab.Sheets -> stringResource(Res.string.mysongs_tab_sheets, state.sheetCount)
            }
            val shape = RoundedCornerShape(9.dp)
            Surface(
                onClick = { onSelect(tab) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(if (selected) Modifier.shadow(2.dp, shape, ambientColor = SelectedTabShadow, spotColor = SelectedTabShadow) else Modifier)
                    .semantics {
                        role = Role.Tab
                        this.selected = selected
                    },
                shape = shape,
                color = if (selected) colors.background else Color.Transparent,
                contentColor = if (selected) colors.text else colors.textMuted,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

private val SelectedTabShadow = Color.Black.copy(alpha = 0.12f)

/** 점선 테두리의 '악보 추가' 버튼. */
@Composable
private fun AddSheetButton(onClick: () -> Unit) {
    val colors = EolssuTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp).dashedBorder(colors.lineStrong, 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        contentColor = colors.text,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(EolssuIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.mysongs_add), style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}
