package com.ssafy.eolssu.feature.guide.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.eolssu.feature.guide.FingeringActions
import com.ssafy.eolssu.feature.guide.FingeringInstrumentNote
import com.ssafy.eolssu.feature.guide.FingeringSource
import com.ssafy.eolssu.feature.guide.FingeringUiState
import com.ssafy.eolssu.feature.guide.component.BlowTabs
import com.ssafy.eolssu.feature.guide.component.FingeringCard
import com.ssafy.eolssu.core.ui.score.HoleLegend
import com.ssafy.eolssu.feature.guide.component.InstrumentDropdown
import com.ssafy.eolssu.core.model.fingering.FingeringNote

/** 휴대폰 배치: 악기 · 취법 · 범례 아래로 운지 카드를 한 줄씩 쌓는다. */
@Composable
internal fun FingeringCompactLayout(
    state: FingeringUiState,
    actions: FingeringActions,
    noteAudioContent: @Composable (FingeringNote) -> Unit,
) {
    val instrument = state.instrument
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            Column {
                InstrumentDropdown(
                    selected = instrument,
                    open = state.isDropdownOpen,
                    onToggle = actions.onToggleDropdown,
                    onSelect = actions.onSelectInstrument,
                    onDismiss = actions.onDismissDropdown,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                BlowTabs(state.blowTabs, actions.onSelectBlow, Modifier.padding(bottom = 10.dp))
                HoleLegend(Modifier.padding(top = 2.dp, bottom = 6.dp))
                FingeringInstrumentNote(instrument)
            }
        }
        items(state.notes, key = { "${instrument.id}-${state.shownBlow}-${it.hanja}" }) { note ->
            FingeringCard(instrument, note, audioContent = { noteAudioContent(note) })
        }
        item(key = "source") {
            FingeringSource(instrument, Modifier.padding(top = 4.dp))
        }
    }
}
