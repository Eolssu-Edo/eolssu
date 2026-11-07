package com.ssafy.eolssu.feature.guide.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.guide.FingeringActions
import com.ssafy.eolssu.feature.guide.FingeringInstrumentNote
import com.ssafy.eolssu.feature.guide.FingeringUiState
import com.ssafy.eolssu.feature.guide.component.BlowTabs
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideTabletSidePadding
import com.ssafy.eolssu.core.ui.score.HoleLegend
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.core.model.fingering.FingeringNote

/**
 * 태블릿 세로 배치(와이어프레임 `Tab-GuideFing-Port`): 위에 악기와 취법 탭을 한 줄로 놓고,
 * 운지 카드는 두 칸 격자로 보여준다.
 */
@Composable
internal fun FingeringMediumLayout(
    state: FingeringUiState,
    actions: FingeringActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
) {
    val instrument = state.instrument
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = GuideTabletSidePadding, end = GuideTabletSidePadding, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(
                    Modifier.padding(top = 4.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstrumentDropdown(
                        options = state.instrumentOptions,
                        selected = instrument,
                        open = state.isDropdownOpen,
                        onToggle = actions.onToggleDropdown,
                        onSelect = actions.onSelectInstrument,
                        onDismiss = actions.onDismissDropdown,
                        modifier = Modifier.weight(1f),
                    )
                    BlowTabs(state.blowTabs, actions.onSelectBlow, Modifier.weight(1f), large = true)
                }
                HoleLegend(Modifier.padding(top = 2.dp, bottom = 6.dp))
                FingeringInstrumentNote(instrument)
            }
        }
        items(state.notes, key = { "${instrument.id}-${state.shownBlow}-${it.hanja}" }) { note ->
            FingeringCard(instrument, note, audioContent = { noteAudioContent(note) })
        }
        item(key = "source", span = { GridItemSpan(maxLineSpan) }) {
        }
    }
}
