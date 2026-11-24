package com.ssafy.eolssu.feature.guide.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.guide.FingeringActions
import com.ssafy.eolssu.feature.guide.FingeringUiState
import com.ssafy.eolssu.feature.guide.component.BlowTabs
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideTabletSidePadding
import com.ssafy.eolssu.core.ui.score.HoleLegend
import com.ssafy.eolssu.core.model.fingering.FingeringNote

/**
 * 태블릿 세로 배치(와이어프레임 `Tab-GuideFing-Port`): 위에 취법 탭을 고정하고(악기는 상단 바 오른쪽),
 * 그 아래 범례와 두 칸 운지 카드 격자만 스크롤한다(S15P31E102-1242). 악기나 취법을 바꿔도 격자를 맨 위로 되돌리지 않는다(S15P31E102-1255).
 */
@Composable
internal fun FingeringMediumLayout(
    state: FingeringUiState,
    actions: FingeringActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
) {
    val instrument = state.instrument
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    Column(Modifier.fillMaxSize()) {
        BlowTabs(
            state.blowTabs,
            actions.onSelectBlow,
            Modifier.fillMaxWidth().padding(start = GuideTabletSidePadding, end = GuideTabletSidePadding, top = 4.dp, bottom = 10.dp),
            large = true,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = gridState,
            contentPadding = PaddingValues(start = GuideTabletSidePadding, end = GuideTabletSidePadding, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "legend", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth()) {
                    HoleLegend(Modifier.align(Alignment.CenterEnd).padding(top = 2.dp, bottom = 6.dp))
                }
            }
            items(state.notes, key = { "${instrument.id}-${state.shownBlow}-${it.hanja}" }) { note ->
                FingeringCard(instrument, note, audioContent = { noteAudioContent(note) })
            }
            item(key = "source", span = { GridItemSpan(maxLineSpan) }) {
            }
        }
    }
}
