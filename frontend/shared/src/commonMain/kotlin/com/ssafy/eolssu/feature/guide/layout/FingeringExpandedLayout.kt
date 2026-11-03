package com.ssafy.eolssu.feature.guide.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.guide.FingeringActions
import com.ssafy.eolssu.feature.guide.FingeringInstrumentNote
import com.ssafy.eolssu.feature.guide.FingeringSource
import com.ssafy.eolssu.feature.guide.FingeringUiState
import com.ssafy.eolssu.feature.guide.component.BlowTabs
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.feature.guide.component.GuideTabletSidePadding
import com.ssafy.eolssu.core.ui.score.HoleLegend
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.core.model.fingering.FingeringNote

/**
 * 태블릿 가로·넓은 웹 창 배치(와이어프레임 `Tab-GuideFing-Land`): 왼쪽 300dp 칸에 악기 · 세로 취법 탭 · 범례 · 안내,
 * 맨 아래에 출처를 두고, 오른쪽 카드 격자만 따로 스크롤한다.
 * 카드 칸은 최소 300dp로, 내용 너비 1120dp에서는 와이어프레임처럼 두 칸, 좁은 창에서는 한 칸이 된다.
 */
@Composable
internal fun FingeringExpandedLayout(
    state: FingeringUiState,
    actions: FingeringActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
) {
    val instrument = state.instrument
    Row(
        Modifier.fillMaxSize().padding(horizontal = GuideTabletSidePadding),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Column(Modifier.width(300.dp).fillMaxHeight().padding(bottom = 24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
                InstrumentDropdown(
                    options = state.instrumentOptions,
                    selected = instrument,
                    open = state.isDropdownOpen,
                    onToggle = actions.onToggleDropdown,
                    onSelect = actions.onSelectInstrument,
                    onDismiss = actions.onDismissDropdown,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                BlowTabs(
                    state.blowTabs,
                    actions.onSelectBlow,
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    large = true,
                    vertical = true,
                )
                HoleLegend(Modifier.padding(top = 2.dp, bottom = 6.dp))
                FingeringInstrumentNote(instrument)
            }
            FingeringSource(instrument, Modifier.padding(top = 12.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.notes, key = { "${instrument.id}-${state.shownBlow}-${it.hanja}" }) { note ->
                FingeringCard(instrument, note, audioContent = { noteAudioContent(note) })
            }
        }
    }
}
